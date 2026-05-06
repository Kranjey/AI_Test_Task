Промпт 1

Проведи детальный code review двух классов: `UserApiClient.java` и `BaseTest.java`. 
Сфокусируйся на:
1. Потокобезопасности и возможности параллельного запуска тестов
2. Архитектурных решениях (паттерны, разделение ответственности)
3. Практиках тест-автоматизации (ожидания, стабильность, читаемость)
4. Потенциальных багах и edge cases

Для каждой найденной проблемы укажи:
- Уровень критичности (Critical/High/Medium/Low)
- Объяснение, почему это проблема
- Конкретное решение с примером кода

**Файл: `UserApiClient.java`**

```java
public class UserApiClient {
    private static UserApiClient instance;
    private final String baseUrl;
    private int timeout = 5000;

    private UserApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public static UserApiClient getInstance(String baseUrl) {
        if (instance == null) {
            instance = new UserApiClient(baseUrl);
        }
        return instance;
    }

    public Response createUser(Map<String, Object> payload) {
        return given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/api/v1/users");
    }

    public Response getUserById(String userId) {
        return given()
            .baseUri(baseUrl)
            .pathParam("id", userId)
            .when()
            .get("/api/v1/users/{id}");
    }

    public void deleteUser(String userId) {
        given()
            .baseUri(baseUrl)
            .when()
            .delete("/api/v1/users/" + userId);
    }
}
```

**Файл: `BaseTest.java`**

```java
@ExtendWith(SeleniumExtension.class)
public class BaseTest {

    protected WebDriver driver;
    protected UserApiClient apiClient;

    @BeforeEach
    void setUp() {
        System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");
        driver = new ChromeDriver();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        apiClient = UserApiClient.getInstance("http://localhost:8080");
    }

    @AfterEach
    void tearDown() {
        driver.quit();
    }
}
```
## Ответ агента

🚨 Критические проблемы (Critical)
1. Race condition в Singleton (UserApiClient.getInstance)
// ❌ ПРОБЛЕМА: Небезопасный lazy initialization
```java
public static UserApiClient getInstance(String baseUrl) {
    if (instance == null) {  // ← Race condition здесь
        instance = new UserApiClient(baseUrl);
    }
    return instance;
}
```
Уровень: Critical
Почему проблема: При параллельном запуске тестов несколько потоков могут одновременно пройти проверку instance == null и создать разные экземпляры. Это нарушает инварианты теста и приводит к недетерминированному поведению.
✅ Решение: Отказаться от Singleton в тестах (рекомендуется) ИЛИ использовать thread-safe реализацию:
```java
// Вариант А: Отказ от Singleton (лучшая практика для тестов)
public class UserApiClient {
    private final String baseUrl;
    private final RequestSpecification requestSpec;
    
    public UserApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.requestSpec = new RequestSpecBuilder()
            .setBaseUri(baseUrl)
            .setContentType(ContentType.JSON)
            .build();
    }
    
    // Factory method вместо getInstance
    public static UserApiClient create(String baseUrl) {
        return new UserApiClient(baseUrl);
    }
}

// Вариант Б: Thread-safe Singleton (если действительно нужен)
public class UserApiClient {
    private static volatile UserApiClient instance;
    private static final Object lock = new Object();
    
    private UserApiClient(String baseUrl) { /* ... */ }
    
    public static UserApiClient getInstance(String baseUrl) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new UserApiClient(baseUrl);
                }
            }
        }
        return instance;
    }
}
```
2. Shared mutable state (timeout field)
// ❌ ПРОБЛЕМА: Изменяемое поле в разделяемом экземпляре
```java
private int timeout = 5000;  // ← Если один тест изменит — сломаются все

public void setTimeout(int timeout) {
    this.timeout = timeout;  // ← Не потокобезопасно!
}
```
Уровень: High
Почему проблема: В параллельных тестах изменение timeout в одном тесте влияет на все остальные, вызывая flaky-тесты и сложные для отладки таймауты.
✅ Решение: Сделать состояние иммутабельным или использовать builder:
```java
public class UserApiClient {
    private final String baseUrl;
    private final Duration timeout;  // immutable
    private final RequestSpecification requestSpec;
    
    private UserApiClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.timeout = builder.timeout;
        this.requestSpec = new RequestSpecBuilder()
            .setBaseUri(builder.baseUrl)
            .setContentType(ContentType.JSON)
            .setTimeout(builder.timeout.toMillis())
            .build();
    }
    
    public static class Builder {
        private String baseUrl;
        private Duration timeout = Duration.ofSeconds(5);
        
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }
        
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }
        
        public UserApiClient build() {
            return new UserApiClient(this);
        }
    }
}
```
// Использование в тесте:
```java
apiClient = new UserApiClient.Builder()
    .baseUrl("http://localhost:8080")
    .timeout(Duration.ofSeconds(10))
    .build();
```
3. Shared apiClient в BaseTest при параллельном запуске
// ❌ ПРОБЛЕМА: Все тесты получают один и тот же экземпляр
```java
@BeforeEach
void setUp() {
    apiClient = UserApiClient.getInstance("http://localhost:8080");  // ← Shared state!
}
```
Уровень: Critical
Почему проблема: При параллельном выполнении тесты могут конфликтовать: один тест меняет конфигурацию клиента, другой читает устаревшие данные. Особенно критично при тестировании разных окружений (staging/prod).
✅ Решение: Создавать новый экземпляр для каждого теста:
```java
@BeforeEach
void setUp() {
    // Каждый тест получает изолированный клиент
    apiClient = new UserApiClient.Builder()
        .baseUrl(getConfigProperty("api.base.url"))  // из config
        .build();
    
    driver = WebDriverManager.chromedriver().create();  // см. ниже
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
}
```
🎯 Итоговые рекомендации по рефакторингу
Приоритет 1 (исправить немедленно):
Убрать Singleton из UserApiClient — создать новый экземпляр на тест
Разделить BaseTest на BaseApiTest / BaseUiTest
Вынести конфигурацию в properties/env variables
Добавить cleanup тестовых данных

### Оценка ответа
Хорошо: ИИ правильно определил ключевые проблемы с thread-safety и hardcoded values. Предложенные решения технически корректны.
Упущено: Не затронул вопрос конфигурации через properties/env-variables, не предложил стратегию управления тестовыми данными.
Решение: Задам уточняющий промпт про конфигурацию и управление данными.


## Промпт 2
Спасибо за ревью. Уточни, пожалуйста:
Как лучше организовать конфигурацию (baseUrl, timeout, browser type) — properties, env variables, или что-то ещё?
Как обеспечить очистку тестовых данных между запусками, особенно если тест падает на середине?


## Ответ агента
🔧 1. Как лучше организовать конфигурацию?
🎯 Рекомендуемый подход: Иерархическая конфигурация с приоритетами
Приоритет (от высшего к низшему):
1. CLI аргументы (-Dapi.url=...)
2. Environment variables (API_BASE_URL=...)
3. Файл конфигурации (config/test.properties)
4. Дефолтные значения в коде
✅ Реализация: Type-safe Config Helper
// config/AppConfig.java
```java
public class AppConfig {
    
    // === API Configuration ===
    private static String apiBaseUrl;
    private static Duration apiTimeout;
    
    // === UI Configuration ===
    private static String browserType;
    private static Duration uiTimeout;
    private static boolean headless;
    
    // === Database Configuration (для cleanup) ===
    private static String dbUrl;
    private static String dbUser;
    private static String dbPassword;
    
    static {
        load();
    }
    
    private static void load() {
        // 1. Загружаем дефолты из properties-файла
        Properties defaults = loadProperties("config/defaults.properties");
        
        // 2. Переопределяем из environment-specific файла
        String env = System.getenv("TEST_ENV") != null 
            ? System.getenv("TEST_ENV") 
            : System.getProperty("test.env", "local");
        Properties envProps = loadProperties("config/" + env + ".properties");
        defaults.putAll(envProps);
        
        // 3. Переопределяем из environment variables
        applyEnvOverrides(defaults);
        
        // 4. Переопределяем из system properties (CLI args)
        applySystemPropertyOverrides(defaults);
        
        // 5. Маппим в типизированные поля
        apiBaseUrl = getRequired(defaults, "api.base.url");
        apiTimeout = Duration.ofSeconds(getLong(defaults, "api.timeout.seconds", 30));
        
        browserType = get(defaults, "browser.type", "chrome");
        uiTimeout = Duration.ofSeconds(getLong(defaults, "ui.timeout.seconds", 15));
        headless = getBoolean(defaults, "browser.headless", false);
        
        dbUrl = get(defaults, "db.url", null);
        dbUser = get(defaults, "db.user", null);
        dbPassword = get(defaults, "db.password", null);
    }
    
    // === Public getters ===
    public static String getApiBaseUrl() { return apiBaseUrl; }
    public static Duration getApiTimeout() { return apiTimeout; }
    public static String getBrowserType() { return browserType; }
    public static Duration getUiTimeout() { return uiTimeout; }
    public static boolean isHeadless() { return headless; }
    public static Optional<DbConfig> getDbConfig() {
        if (dbUrl == null) return Optional.empty();
        return Optional.of(new DbConfig(dbUrl, dbUser, dbPassword));
    }
    
    // === Helpers ===
    private static Properties loadProperties(String path) {
        Properties props = new Properties();
        try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream(path)) {
            if (is != null) props.load(is);
        } catch (IOException e) {
            // Файл опционален, не фейлим если нет
        }
        return props;
    }
    
    private static void applyEnvOverrides(Properties props) {
        Map<String, String> env = System.getenv();
        env.forEach((key, value) -> {
            // Конвертируем ENV_VAR_NAME → env.var.name
            String propKey = key.toLowerCase().replace("_", ".");
            if (props.containsKey(propKey)) {
                props.setProperty(propKey, value);
            }
        });
    }
    
    private static void applySystemPropertyOverrides(Properties props) {
        System.getProperties().forEach((key, value) -> {
            if (props.containsKey(key)) {
                props.setProperty((String) key, (String) value);
            }
        });
    }
    
    private static String get(Properties props, String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }
    
    private static String getRequired(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("Required config property missing: " + key);
        }
        return value;
    }
    
    private static long getLong(Properties props, String key, long defaultValue) {
        String value = props.getProperty(key);
        return value != null ? Long.parseLong(value) : defaultValue;
    }
    
    private static boolean getBoolean(Properties props, String key, boolean defaultValue) {
        String value = props.getProperty(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }
    
    // Вложенный класс для БД-конфига
    public static class DbConfig {
        public final String url, user, password;
        public DbConfig(String url, String user, String password) {
            this.url = url; this.user = user; this.password = password;
        }
    }
}
```
// Кастомное исключение для конфигурации
```java
public class ConfigurationException extends RuntimeException {
    public ConfigurationException(String message) { super(message); }
}
```
📁 Структура файлов конфигурации
src/test/resources/config/
├── defaults.properties          # Базовые дефолты
├── local.properties           # Локальная разработка
├── ci.properties              # CI/CD окружение
├── staging.properties         # Staging environment
└── prod.properties            # Production (если нужны smoke-тесты)

defaults.properties:

# API
api.base.url=http://localhost:8080
api.timeout.seconds=30

# Browser
browser.type=chrome
browser.headless=false
ui.timeout.seconds=15

# Database (опционально, для cleanup)
# db.url=jdbc:postgresql://localhost:5432/testdb
# db.user=test
# db.password=test

ci.properties:

# Переопределяем для CI
api.base.url=${API_BASE_URL:http://staging-api:8080}
browser.headless=true
browser.type=chrome

🚀 Использование в тестах
// BaseTest.java
```java
@BeforeEach
void setUp() {
    // API Client с конфигурацией
    apiClient = new UserApiClient.Builder()
        .baseUrl(AppConfig.getApiBaseUrl())
        .timeout(AppConfig.getApiTimeout())
        .build();
    
    // WebDriver с конфигурацией
    if (AppConfig.getBrowserType().equals("chrome")) {
        ChromeOptions options = new ChromeOptions();
        if (AppConfig.isHeadless()) options.addArguments("--headless");
        driver = new ChromeDriver(options);
    }
    // ... другие браузеры
}
```
🔄 Запуск с разными конфигурациями
# Локально (использует defaults + local.properties)
mvn test

# CI/CD (переопределяет через env vars)
TEST_ENV=ci API_BASE_URL=https://staging.example.com mvn test

# Override через CLI
mvn test -Dtest.env=staging -Dapi.timeout.seconds=60

# Docker/CI пример
docker run -e TEST_ENV=ci \
           -e API_BASE_URL=https://api.staging.com \
           -e BROWSER_HEADLESS=true \
           my-test-image


🧹 2. Как обеспечить очистку тестовых данных?
🎯 Стратегия: Многоуровневый подход к cleanup
Уровень 1: Изоляция (предотвращение конфликтов)
Уровень 2: Автоматический cleanup (после каждого теста)
Уровень 3: Fallback cleanup (перед запуском тестов)
Уровень 4: Транзакции/rollback (для БД-тестов)

✅ Уровень 1: Изоляция данных (предотвращение проблем)
```java
// TestDataProvider.java — генерация уникальных данных
public class TestDataProvider {
    
    private static final String TIMESTAMP = String.valueOf(System.currentTimeMillis());
    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);
    
    public static String uniqueEmail() {
        return "test_" + RUN_ID + "_" + TIMESTAMP + "@example.com";
    }
    
    public static String uniqueUsername() {
        return "user_" + RUN_ID + "_" + TIMESTAMP;
    }
    
    public static String uniquePrefix() {
        return "test_" + RUN_ID;
    }
}

// Использование в тесте:
@Test
void testUserCreation() {
    UserCreateRequest request = UserCreateRequest.builder()
        .email(TestDataProvider.uniqueEmail())  // ← Уникальный для этого запуска
        .username(TestDataProvider.uniqueUsername())
        .build();
    
    // Даже если тест упадёт — данные не конфликтуют с другими тестами
}
```
✅ Уровень 2: AutoCloseable паттерн для автоматического cleanup
```java
// TestResource.java — базовый интерфейс для ресурсов с cleanup
public interface TestResource extends AutoCloseable {
    @Override
    void close(); // cleanup логика
}

// CreatedUser.java — пример ресурса
public class CreatedUser implements TestResource {
    
    private final UserApiClient apiClient;
    private final String userId;
    private final boolean autoCleanup;
    private boolean cleaned = false;
    
    private CreatedUser(UserApiClient client, String userId, boolean autoCleanup) {
        this.apiClient = client;
        this.userId = userId;
        this.autoCleanup = autoCleanup;
    }
    
    // Factory method
    public static CreatedUser create(UserApiClient client, UserCreateRequest request) {
        Response response = client.createUser(request);
        String id = response.then().extract().path("id");
        return new CreatedUser(client, id, true);
    }
    
    public String getId() { return userId; }
    
    // Отключаем авто-очистку если нужно проверить данные после теста
    public CreatedUser disableAutoCleanup() {
        this.autoCleanup = false;
        return this;
    }
    
    @Override
    public void close() {
        if (!cleaned && autoCleanup) {
            try {
                apiClient.deleteUser(userId);
                cleaned = true;
            } catch (Exception e) {
                // Логируем, но не фейлим тест на cleanup
                System.err.println("⚠️  Failed to cleanup user " + userId + ": " + e.getMessage());
            }
        }
    }
}
```
Использование в тесте:
```java
@Test
void testUserFlow() {
    // try-with-resources гарантирует cleanup даже при падении теста
    try (CreatedUser user = CreatedUser.create(apiClient, createRequest())) {
        
        // Тестовая логика
        UserDto retrieved = apiClient.getUserById(user.getId());
        assertThat(retrieved.getEmail()).isEqualTo(createRequest().getEmail());
        
        // ← Здесь автоматически вызовется user.close() и удалит пользователя
        
    } // ← cleanup выполняется здесь, даже если тест упал с исключением
}

@Test
void testUserRetention() {
    // Если нужно проверить данные после теста — отключаем авто-очистку
    try (CreatedUser user = CreatedUser.create(apiClient, createRequest())
                                        .disableAutoCleanup()) {
        // ... тестовая логика
    }
    // Пользователь останется в БД для ручной проверки
}
```
✅ Уровень 3: Fallback cleanup перед запуском тестов
```java
// TestCleanupService.java — очистка "мусора" от предыдущих запусков
public class TestCleanupService {
    
    private final UserApiClient apiClient;
    private final String testPrefix; // например: "test_abc12345"
    
    public TestCleanupService(UserApiClient client, String testPrefix) {
        this.apiClient = client;
        this.testPrefix = testPrefix;
    }
    
    // Вызывается @BeforeAll один раз для всего тестового класса
    public void cleanupOrphanedData() {
        // Ищем и удаляем данные с нашим префиксом, созданные > 1 часа назад
        List<String> orphanedUsers = apiClient.searchUsersByPrefix(testPrefix)
            .stream()
            .filter(u -> u.getCreatedAt().isBefore(Instant.now().minus(1, ChronoUnit.HOURS)))
            .map(UserDto::getId)
            .collect(Collectors.toList());
        
        for (String userId : orphanedUsers) {
            try {
                apiClient.deleteUser(userId);
                System.out.println("🧹 Cleaned up orphaned user: " + userId);
            } catch (Exception e) {
                System.err.println("⚠️  Failed to cleanup " + userId + ": " + e.getMessage());
            }
        }
    }
}
```
// В базовом тесте:
public class BaseApiTest {
    
    protected static TestCleanupService cleanupService;
    
    @BeforeAll
    static void initCleanup() {
        UserApiClient adminClient = createAdminClient();
        cleanupService = new TestCleanupService(
            adminClient, 
            TestDataProvider.uniquePrefix()
        );
        cleanupService.cleanupOrphanedData();
    }
}

✅ Уровень 4: Транзакции и rollback (для тестов с прямым доступом к БД)

```java
// Если тесты работают напрямую с БД — используем транзакции

// TestDatabaseExtension.java — JUnit 5 extension для транзакций
public class TestDatabaseExtension implements BeforeEachCallback, AfterEachCallback {
    
    private final DataSource dataSource;
    private final ThreadLocal<Connection> connectionHolder = new ThreadLocal<>();
    
    public TestDatabaseExtension(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(false);  // Начинаем транзакцию
        connectionHolder.set(conn);
        
        // Инжектим коннект в тест если нужно
        Object testInstance = context.getRequiredTestInstance();
        if (testInstance instanceof DatabaseAware) {
            ((DatabaseAware) testInstance).setTestConnection(conn);
        }
    }
    
    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        Connection conn = connectionHolder.get();
        if (conn != null) {
            try {
                conn.rollback();  // ← Откатываем все изменения!
            } finally {
                conn.close();
                connectionHolder.remove();
            }
        }
    }
}

// Интерфейс для тестов, которым нужен доступ к БД
public interface DatabaseAware {
    void setTestConnection(Connection conn);
}

// Использование:
@ExtendWith(TestDatabaseExtension.class)
public class UserDatabaseTest implements DatabaseAware {
    
    private Connection testConnection;
    
    @Override
    public void setTestConnection(Connection conn) {
        this.testConnection = conn;
    }
    
    @Test
    void testUserPersistence() throws SQLException {
        // Создаём данные напрямую в БД
        try (PreparedStatement stmt = testConnection.prepareStatement(
                "INSERT INTO users (email, username) VALUES (?, ?)")) {
            stmt.setString(1, TestDataProvider.uniqueEmail());
            stmt.setString(2, TestDataProvider.uniqueUsername());
            stmt.executeUpdate();
        }
        
        // Проверяем через API
        // ...
        
        // ← После теста всё автоматически откатится, данные не останутся
    }
}
```
🎯 Итоговые рекомендации
Для конфигурации:
✅ Используйте иерархию: defaults → env-specific properties → env vars → CLI
✅ Сделайте конфигурацию type-safe с валидацией обязательных полей
✅ Храните секреты только в env vars / secret managers
✅ Документируйте все доступные параметры в README
Для cleanup тестовых данных:
✅ Основной паттерн: AutoCloseable + try-with-resources
✅ Всегда генерируйте уникальные данные (timestamp/UUID)
✅ Добавляйте fallback cleanup @BeforeAll для "зомби-данных"
✅ Для БД-тестов используйте транзакции с rollback
✅ Логируйте неудачные попытки очистки, но не фейлите тесты на cleanup
✅ В CI/CD настраивайте периодическую полную очистку тестовой БД

### Оценка ответа
Отлично: Предложены практичные гибридные подходы к конфигурации с правильным приоритетом источников. Стратегия очистки через try-finally — надёжная.

