package service.sync;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import model.Client;
import repository.ClientRepository;
import utils.DBConnection;
import utils.ClientIdentifiers;
import utils.PhoneValidator;

public class PhoneValidationTest {

    private static String dbPath;
    private ClientRepository repo = new ClientRepository();

    @BeforeAll
    public static void setup() throws Exception {
        dbPath = TestDatabaseHelper.createIsolatedDb("PhoneValidationTest");
        DBConnection.setUrl(dbPath);
    }

    @AfterAll
    public static void tearDown() {
        TestDatabaseHelper.cleanupTestDir();
    }

    @BeforeEach
    public void resetDb() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = OFF;");
            stmt.execute("DELETE FROM clients;");
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
    }

    @Test
    public void testValidMobileNumbers() {
        assertTrue(PhoneValidator.isValidMobile("9876543210"));
        assertTrue(PhoneValidator.isValidMobile("8123456789"));
        assertTrue(PhoneValidator.isValidMobile("7012345678"));
        assertTrue(PhoneValidator.isValidMobile("6999999999"));
    }

    @Test
    public void testInvalidLength() {
        // Short length
        assertFalse(PhoneValidator.isValidMobile("987654321"));
        // Long length
        assertFalse(PhoneValidator.isValidMobile("98765432101"));
    }

    @Test
    public void testInvalidStartingDigit() {
        assertFalse(PhoneValidator.isValidMobile("5876543210"));
        assertFalse(PhoneValidator.isValidMobile("1234567890"));
        assertFalse(PhoneValidator.isValidMobile("0876543210"));
    }

    @Test
    public void testAlphabetsAndSpecialCharacters() {
        assertFalse(PhoneValidator.isValidMobile("987654321a"));
        assertFalse(PhoneValidator.isValidMobile("98765-43210"));
        assertFalse(PhoneValidator.isValidMobile("987654321@"));
        assertFalse(PhoneValidator.isValidMobile("+9198765432"));
    }

    @Test
    public void testBlankValueHandling() {
        assertFalse(PhoneValidator.isValidMobile(""));
        assertFalse(PhoneValidator.isValidMobile(null));
        assertFalse(PhoneValidator.isValidMobile("   "));
        
        // Landline blank is accepted as optional
        assertTrue(PhoneValidator.isValidLandline(""));
        assertTrue(PhoneValidator.isValidLandline(null));
        assertTrue(PhoneValidator.isValidLandline("  "));
    }

    @Test
    public void testLandlineValidation() {
        // Minimum length 8
        assertTrue(PhoneValidator.isValidLandline("12345678"));
        // Max length 15
        assertTrue(PhoneValidator.isValidLandline("123456789012345"));
        // In-between
        assertTrue(PhoneValidator.isValidLandline("01123456789"));
        
        // Invalid lengths
        assertFalse(PhoneValidator.isValidLandline("1234567"));
        assertFalse(PhoneValidator.isValidLandline("1234567890123456"));
        
        // Invalid characters
        assertFalse(PhoneValidator.isValidLandline("1234567a"));
        assertFalse(PhoneValidator.isValidLandline("1234-5678"));
    }

    @Test
    public void testDuplicateMobileNumber() throws Exception {
        String mobile = "9876543210";
        
        // Save first client
        Client c1 = new Client("First Business", "John First", mobile, "", "", "", "", "Delhi", "Delhi", "");
        c1.setClientUuid(ClientIdentifiers.newUuidV7String());
        assertTrue(repo.save(c1));

        // Verify duplicate check finds it
        assertTrue(repo.duplicateMobileExists(mobile, null));

        // Exclude the first client itself (should be false)
        assertFalse(repo.duplicateMobileExists(mobile, c1.getClientUuid()));

        // Save a second client with a different mobile
        String otherMobile = "8765432109";
        assertFalse(repo.duplicateMobileExists(otherMobile, null));
    }
}
