package tests;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.*;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import java.time.Duration;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class BaseTest {

    protected WebDriver driver;
    protected UserApiClient apiClient;

    // Конфигурационные значения (в реальном проекте лучше вынести в отдельный класс Config)
    private static final String BASE_URL = System.getenv("BASE_URL") != null 
            ? System.getenv("BASE_URL") 
            : System.getProperty("base.url", "http://localhost:8080");
    
    private static final int IMPLICIT_WAIT_SECONDS = Integer.parseInt(
            System.getProperty("implicit.wait.seconds", "10")
    );

    @BeforeEach
    void setUp() {
        // 1. Инициализация WebDriver через WebDriverManager (потокобезопасно)
        WebDriverManager.chromedriver().setup();
        
        ChromeOptions options = new ChromeOptions();
        // Опции для стабильности в CI/Headless режиме
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        // options.addArguments("--headless"); // Раскомментировать для headless режима
        
        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(IMPLICIT_WAIT_SECONDS));
        driver.manage().window().maximize();

        // 2. Инициализация API клиента (новый инстанс для каждого теста)
        apiClient = new UserApiClient(BASE_URL);
    }

    @AfterEach
    void tearDown() {
        // Гарантированное закрытие браузера даже при падении теста
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                System.err.println("Error during driver quit: " + e.getMessage());
            }
        }
    }
}