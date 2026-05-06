package tests;

import config.TestConfig;
import io.qameta.allure.Description;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SeleniumExtension.class)
public class UserFlowTest extends BaseTest {

    private static final String ADMIN_USERS_PATH = "/admin/users";
    
    @Test
    @Description("E2E: Создание пользователя через API → проверка в UI → удаление через API")
    public void testCreateAndDeleteUserViaApi_VerifyInUI() {
        // Arrange: Prepare test data
        String testEmail = "test_" + System.currentTimeMillis() + "@example.com";
        Map<String, Object> payload = Map.of(
            "name", "Test User",
            "email", testEmail,
            "role", "user"
        );
        
        // Act Step 1: Create user via API
        var createResponse = apiClient.createUser(payload);
        assertEquals(201, createResponse.getStatusCode(), 
                    "API should return 201 on user creation");
        String userId = createResponse.jsonPath().getString("id");
        assertNotNull(userId, "Response should contain user id");
        
        // Act Step 2: Verify user appears in UI
        driver.get(TestConfig.getBaseUrl() + ADMIN_USERS_PATH);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        
        By emailCellLocator = By.xpath("//td[contains(text(), '" + testEmail + "')]");
        wait.until(ExpectedConditions.presenceOfElementLocated(emailCellLocator));
        assertTrue(driver.findElement(emailCellLocator).isDisplayed());
        
        // Act Step 3: Delete user via API
        apiClient.deleteUser(userId);
        
        // Assert Step 4: Verify user disappears from UI
        driver.navigate().refresh();
        wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        wait.until(ExpectedConditions.invisibilityOfElementLocated(emailCellLocator));
        
        assertTrue(driver.findElements(emailCellLocator).isEmpty(), 
                  "User should be removed from UI after deletion");
    }
}