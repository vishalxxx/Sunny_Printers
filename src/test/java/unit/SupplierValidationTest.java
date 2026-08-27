package unit;


import org.junit.jupiter.api.Tag;
import utils.FakeSupabaseRestClient;
import utils.TestDatabaseHelper;
import utils.CleanupUtility;
import utils.ClearAllExceptSettings;
import utils.ClearRemoteDatabase;
import utils.MetricsExtractor;
import utils.SchemaAndSyncChecker;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import model.Supplier;
import service.SupplierService;
import utils.DBConnection;
import utils.ClientIdentifiers;
import utils.GSTINValidator;
import utils.PhoneValidator;

@Tag("unit")
public class SupplierValidationTest {

    private static String dbPath;
    private SupplierService service = new SupplierService();

    @BeforeAll
    public static void setup() throws Exception {
        dbPath = TestDatabaseHelper.createIsolatedDb("SupplierValidationTest");
        DBConnection.setTestDatabaseUrl(dbPath);
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
            stmt.execute("DELETE FROM suppliers;");
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
    }

    @Test
    public void testSupplierDuplicateGstin() {
        String validGstin = "07AAACR5055K1Z9"; // Delhi
        String uuid = ClientIdentifiers.newUuidV7String();
        
        Supplier s1 = new Supplier();
        s1.setUuid(uuid);
        s1.setName("Supplier One");
        s1.setbusinessName("Trade One");
        s1.setGstNumber(validGstin);
        s1.setType("Paper");
        
        service.addSupplier(s1);
        
        // Should find duplicate
        assertTrue(service.duplicateGstinExists(validGstin, null));
        // Should exclude s1 itself
        assertFalse(service.duplicateGstinExists(validGstin, uuid));
    }

    @Test
    public void testSupplierDuplicateMobile() {
        String mobile = "9876543210";
        String uuid = ClientIdentifiers.newUuidV7String();
        
        Supplier s1 = new Supplier();
        s1.setUuid(uuid);
        s1.setName("Supplier Two");
        s1.setbusinessName("Trade Two");
        s1.setMobile(mobile);
        s1.setType("CTP");
        
        service.addSupplier(s1);
        
        // Should find duplicate
        assertTrue(service.duplicateMobileExists(mobile, null));
        // Should exclude s1 itself
        assertFalse(service.duplicateMobileExists(mobile, uuid));
    }

    @Test
    public void testStateComboMapping() {
        String expectedStateStr = GSTINValidator.getStateByCode("07"); // Delhi (07)
        assertNotNull(expectedStateStr);
        
        // Simulate stripping logic
        int idx = expectedStateStr.indexOf(" (");
        String cleanState = idx != -1 ? expectedStateStr.substring(0, idx) : expectedStateStr;
        assertEquals("Delhi", cleanState);
    }
}

