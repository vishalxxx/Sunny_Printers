package service.sync;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.supabase.SupabaseGate;
import api.supabase.SupabaseReachability;
import model.Client;
import repository.ClientRepository;
import utils.DBConnection;

public class OfflineRecoveryTest {

    private static String dbPath;
    private static FakeSupabaseRestClient fakeSupabase;

    @BeforeAll
    public static void setup() throws Exception {
        dbPath = TestDatabaseHelper.createIsolatedDb("OfflineRecoveryTest");
        fakeSupabase = new FakeSupabaseRestClient();
        SupabaseGate.setOverrideClient(fakeSupabase);
        DBConnection.setUrl(dbPath);
    }

    @AfterAll
    public static void tearDown() {
        TestDatabaseHelper.cleanupTestDir();
    }

    @BeforeEach
    public void resetFake() throws Exception {
        fakeSupabase.clear();
        SupabaseReachability.invalidateCache();
        
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM clients");
        }
    }

    @Test
    public void testOfflineEditsSyncOnRecovery() throws Exception {
        ClientRepository repo = new ClientRepository();

        // 1. Simulate Offline State
        fakeSupabase.setOnline(false);
        SupabaseReachability.invalidateCache();

        // Create client locally while offline
        Client c1 = new Client("Offline Corp", "Jane Doe", "999", "", "", "", "", "Delhi", "", "");
        c1.setClientUuid("uuid-offline-1");
        c1.setClientCode("CL-OFF1");
        c1.setSyncStatus("PENDING");
        repo.save(c1);

        // Verify local state is PENDING and remote has 0 records (offline)
        Client localC1 = repo.findByUuid("uuid-offline-1");
        assertNotNull(localC1);
        assertEquals("PENDING", localC1.getSyncStatus());
        
        // 2. Go online (Recovery)
        fakeSupabase.setOnline(true);
        SupabaseReachability.invalidateCache();

        // Trigger synchronization manually
        UniversalSyncEngine.syncAllPending();

        // Verify local status is updated to SYNCED
        localC1 = repo.findByUuid("uuid-offline-1");
        assertEquals("SYNCED", localC1.getSyncStatus());

        // Verify remote got the record
        assertEquals(1, fakeSupabase.getTableData(api.supabase.SupabaseEndpoints.CLIENTS).size());
        assertEquals("Offline Corp", fakeSupabase.getTableData(api.supabase.SupabaseEndpoints.CLIENTS).get(0).get("business_name").getAsString());
    }
}
