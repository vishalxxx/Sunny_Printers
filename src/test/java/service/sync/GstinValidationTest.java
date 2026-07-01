package service.sync;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import model.Client;
import repository.ClientRepository;
import utils.DBConnection;
import utils.ClientIdentifiers;
import utils.GSTINValidator;

public class GstinValidationTest {

    private static String dbPath;
    private ClientRepository repo = new ClientRepository();

    @BeforeAll
    public static void setup() throws Exception {
        dbPath = TestDatabaseHelper.createIsolatedDb("GstinValidationTest");
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
    public void testValidGstin() {
        // Standard valid GSTIN: Delhi (07)
        String validGstin = "07AAAAA0000A1Z4";
        
        assertTrue(GSTINValidator.isFormatValid(validGstin));
        assertTrue(GSTINValidator.isStateCodeValid(validGstin));
        assertTrue(GSTINValidator.isPANValidFromGstin(validGstin));
        assertTrue(GSTINValidator.isChecksumValid(validGstin));
        assertTrue(GSTINValidator.isValid(validGstin));
        
        assertEquals("Delhi (07)", GSTINValidator.getStateByCode("07"));
    }

    @Test
    public void testInvalidFormat() {
        // 14 characters
        String tooShort = "07AAAAA0000A1Z";
        assertFalse(GSTINValidator.isFormatValid(tooShort));
        assertFalse(GSTINValidator.isValid(tooShort));

        // Wrong characters pattern
        String badPattern = "071AAAA0000A1Z5";
        assertFalse(GSTINValidator.isFormatValid(badPattern));
        assertFalse(GSTINValidator.isValid(badPattern));
    }

    @Test
    public void testInvalidChecksum() {
        // Correct format but incorrect checksum digit (should be '4' for this GSTIN)
        String badChecksum = "07AAAAA0000A1Z0";
        assertTrue(GSTINValidator.isFormatValid(badChecksum));
        assertFalse(GSTINValidator.isChecksumValid(badChecksum));
        assertFalse(GSTINValidator.isValid(badChecksum));
    }

    @Test
    public void testInvalidStateCode() {
        // State code 99 is invalid
        String invalidState = "99AAAAA0000A1Z0";
        assertFalse(GSTINValidator.isStateCodeValid(invalidState));
        assertFalse(GSTINValidator.isValid(invalidState));
    }

    @Test
    public void testStateMismatch() {
        String delhiGstin = "07AAAAA0000A1Z5";
        String selectedState = "Punjab (03)";
        String expectedStateStr = GSTINValidator.getStateByCode("07"); // Delhi (07)
        
        assertNotEquals(expectedStateStr, selectedState);
    }

    @Test
    public void testDuplicateGstin() throws Exception {
        String gstin = "07AAAAA0000A1Z5";
        
        // Save first client
        Client c1 = new Client("First Business", "John First", "1234", "", "", gstin, "AAAAA0000A", "Delhi", "Delhi", "");
        c1.setClientUuid(ClientIdentifiers.newUuidV7String());
        c1.setState("Delhi (07)");
        assertTrue(repo.save(c1));

        // Verify duplicate check finds it
        assertTrue(repo.duplicateGstinExists(gstin, null));

        // Exclude the first client itself (should be false)
        assertFalse(repo.duplicateGstinExists(gstin, c1.getClientUuid()));

        // Save a second client with a different GSTIN
        String otherGstin = "03BBBBB1111B2ZW";
        assertFalse(repo.duplicateGstinExists(otherGstin, null));
    }

    @Test
    public void testRegistrationTypeExtraction() {
        // Regular taxpayer with Z at 14th position
        String regularGstin = "07AAAAA0000A1Z4";
        String type1 = GSTINValidator.getRegistrationType(regularGstin);
        assertTrue(type1.contains("Regular Taxpayer"), "Expected Regular Taxpayer for standard Z format");

        // Special taxpayer with different character at 14th position
        String specialGstin = "07AAAAA0000A11A";
        String type2 = GSTINValidator.getRegistrationType(specialGstin);
        assertTrue(type2.contains("Special Taxpayer"), "Expected Special Taxpayer for non-Z 14th position");

        // Invalid length
        assertEquals("Unknown", GSTINValidator.getRegistrationType("07AAAAA"));
    }

    @Test
    public void testPanAndStateExtraction() {
        String sampleGstin = "07AAAAA0000A1Z4";
        
        // State extraction
        String stateCode = sampleGstin.substring(0, 2);
        assertEquals("07", stateCode);
        assertEquals("Delhi (07)", GSTINValidator.getStateByCode(stateCode));

        // PAN extraction
        String pan = sampleGstin.substring(2, 12);
        assertEquals("AAAAA0000A", pan);
        assertTrue(GSTINValidator.isPANValidFromGstin(sampleGstin));
    }

    @Test
    public void testBlankGstinBehavior() {
        assertFalse(GSTINValidator.isValid(""));
        assertFalse(GSTINValidator.isValid(null));
        assertFalse(GSTINValidator.isFormatValid(""));
        assertFalse(GSTINValidator.isFormatValid(null));
    }

    @Test
    public void testRealWorldValidGstins() {
        // Real-world formatted valid GSTIN samples
        String[] samples = {
            "07AAACR5055K1Z9", // Delhi
            "27AABCU9603R1ZN", // Maharashtra
            "29GGGGG1314R9ZA", // Karnataka
            "33ABCDE1234F1Z7"  // Tamil Nadu
        };
        for (String sample : samples) {
            assertTrue(GSTINValidator.isChecksumValid(sample), "Checksum should be valid for " + sample);
            assertTrue(GSTINValidator.isValid(sample), "GSTIN should be fully valid for " + sample);
        }
    }
}
