## Промпт 1
Напиши тест на JUnit 5 для сценария:
1. Создать нового пользователя через API (используя UserApiClient)
2. Проверить, что пользователь появился на странице `/admin/users` в браузере (через Selenium)
3. Удалить пользователя через API
4. Убедиться, что пользователь исчез со страницы

Требования:
- Наследуется от BaseTest
- Использует явные ожидания (WebDriverWait) для UI-проверок
- Обрабатывает возможные ошибки (таймауты, элементы не найдены)
- Логично разделён на шаги (можно с комментариями или методами)
- Идемпотентен: можно запускать многократно

### Ответ агента

package tests;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class UserFlowTest extends BaseTest {

    @Test
    public void testCreateAndDeleteUserViaApi_VerifyInUI() {
        // Step 1: Create user via API
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Test User");
        payload.put("email", "test_" + System.currentTimeMillis() + "@example.com");
        payload.put("role", "user");
        
        var createResponse = apiClient.createUser(payload);
        assertEquals(201, createResponse.getStatusCode());
        String userId = createResponse.jsonPath().getString("id");
        assertNotNull(userId);
        
        // Step 2: Verify user appears in UI
        driver.get("http://localhost:8080/admin/users");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        
        // Ждём, пока таблица загрузится и появится строка с email
        By userRowLocator = By.xpath("//tr[contains(td, '" + payload.get("email") + "')]");
        wait.until(ExpectedConditions.presenceOfElementLocated(userRowLocator));
        assertTrue(driver.findElement(userRowLocator).isDisplayed());
        
        // Step 3: Delete user via API
        apiClient.deleteUser(userId);
        
        // Step 4: Verify user disappears from UI
        driver.navigate().refresh(); // Обновляем страницу
        wait.until(ExpectedConditions.invisibilityOfElementLocated(userRowLocator));
        assertFalse(driver.findElements(userRowLocator).isEmpty(), 
                   "User should be removed from UI");
    }
}
### Оценка ответа
Хорошо: Тест логически структурирован, использованы явные ожидания, уникальные данные через timestamp.
Плохо:
1. driver.get("http://localhost:8080/admin/users") — hardcoded URL, должен брать из конфигурации
2. //tr[contains(td, '...')] — невалидный XPath, contains() не работает с узлами напрямую
3. Нет обработки случая, если пользователь не удалился (ошибка API)
4. Нет логирования шагов для дебага
5. assertFalse(...isEmpty()) — логическая ошибка: isEmpty() возвращает true, если список пуст, значит пользователь НЕ найден — это ожидаемое поведение, но сообщение вводит в заблуждение

## Промпт 2

Спасибо! Нашёл несколько проблем в сгенерированном тесте:
1. XPath ошибка: //tr[contains(td, 'email')] — невалидно. contains() работает со строками, а не с узлами. Как исправить?
2. Hardcoded URL: Страница /admin/users открывается с жёстко прописанным URL. Нужно использовать конфигурацию.
3. Логика проверки удаления: assertFalse(elements.isEmpty(), "User should be removed") — сообщение противоречит логике. Если список пуст — пользователь удалён, значит проверка прошла. Но сообщение говорит "должен быть удалён", как будто это ошибка.
4. Обработка ошибок: Если deleteUser вернёт ошибку, тест упадёт без понятного сообщения. Добавь обработку.
5. Логирование: Добавь простые логи через System.out или logger для трассировки шагов.
Исправь тест с учётом этих замечаний.

### Ответ агента
package tests;

import io.qameta.allure.Description;
import io.qameta.allure.Step;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class UserFlowTest extends BaseTest {

    private static final String ADMIN_USERS_PATH = "/admin/users";
    
    @Test
    @Description("Создание пользователя через API, проверка в UI, удаление через API")
    public void testCreateAndDeleteUserViaApi_VerifyInUI() {
        System.out.println("[TEST] Starting user flow test");
        
        // Step 1: Create user via API
        String testEmail = "test_" + System.currentTimeMillis() + "@example.com";
        Map<String, Object> payload = Map.of(
            "name", "Test User",
            "email", testEmail,
            "role", "user"
        );
        
        System.out.println("[STEP 1] Creating user with email: " + testEmail);
        var createResponse = apiClient.createUser(payload);
        
        assertEquals(201, createResponse.getStatusCode(), 
                    "API should return 201 on user creation");
        String userId = createResponse.jsonPath().getString("id");
        assertNotNull(userId, "Response should contain user id");
        System.out.println("[STEP 1] User created with ID: " + userId);
        
        // Step 2: Verify user appears in UI
        System.out.println("[STEP 2] Verifying user in UI at " + ADMIN_USERS_PATH);
        driver.get(apiClient.getBaseUrl() + ADMIN_USERS_PATH); // Используем baseUrl из клиента
        
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        
        // Исправленный XPath: ищем td с нужным email, затем поднимаемся к tr
        By emailCellLocator = By.xpath("//td[contains(text(), '" + testEmail + "')]");
        wait.until(ExpectedConditions.presenceOfElementLocated(emailCellLocator));
        assertTrue(driver.findElement(emailCellLocator).isDisplayed(), 
                  "User email should be visible in the table");
        System.out.println("[STEP 2] User found in UI");
        
        // Step 3: Delete user via API
        System.out.println("[STEP 3] Deleting user via API");
        try {
            apiClient.deleteUser(userId);
            System.out.println("[STEP 3] Delete request sent");
        } catch (Exception e) {
            fail("Failed to delete user via API: " + e.getMessage());
        }
        
        // Step 4: Verify user disappears from UI
        System.out.println("[STEP 4] Verifying user removal from UI");
        driver.navigate().refresh();
        
        // Ждём, что элемент с этим email больше не появится в течение 5 секунд
        wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        wait.until(ExpectedConditions.invisibilityOfElementLocated(emailCellLocator));
        
        // Финальная проверка: элементов с таким email быть не должно
        List<?> remainingElements = driver.findElements(emailCellLocator);
        assertTrue(remainingElements.isEmpty(), 
                  "User should be removed from UI after deletion");
        System.out.println("[STEP 4] User successfully removed from UI");
        System.out.println("[TEST] Test completed successfully");
    }
}

### Оценка ответа

Отлично исправлено:
1. XPath теперь корректный: //td[contains(text(), '...')]
2. URL берётся из apiClient.getBaseUrl() (предполагается, что метод добавлен)
3. Логика assertTrue(remainingElements.isEmpty()) теперь соответствует сообщению
4. Добавлены логи и обработка исключений
5. Использован Map.of() для иммутабельности