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