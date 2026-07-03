package integration;
import service.sync.RemoteToLocalSync;


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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseGate;
import api.supabase.SupabaseReachability;
import repository.ClientRepository;
import utils.DBConnection;

@Tag("integration")
public class IncrementalPullTest {

    private static String dbPath;
    private static FakeSupabaseRestClient fakeSupabase;

    @BeforeAll
    public static void setup() throws Exception {
        dbPath = TestDatabaseHelper.createIsolatedDb("IncrementalPullTest");
        fakeSupabase = new FakeSupabaseRestClient();
        SupabaseGate.setOverrideClient(fakeSupabase);
        DBConnection.setTestDatabaseUrl(dbPath);
    }

    @AfterAll
    public static void tearDown() {
        TestDatabaseHelper.cleanupTestDir();
    }

    @BeforeEach
    public void resetFake() throws Exception {
        fakeSupabase.clear();
        SupabaseReachability.invalidateCache();
        
        // Reset local database state
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM clients");
            stmt.execute("DELETE FROM sync_metadata");
        }
    }

    @Test
    public void testIncrementalPullAndWatermark() throws Exception {
        // 1. Seed 2 clients on Supabase
        JsonObject c1 = new JsonObject();
        c1.addProperty("uuid", "uuid-c1");
        c1.addProperty("client_code", "CL-01");
        c1.addProperty("client_name", "Client One");
        c1.addProperty("updated_at", "2026-06-13T10:00:00Z");
        
        JsonObject c2 = new JsonObject();
        c2.addProperty("uuid", "uuid-c2");
        c2.addProperty("client_code", "CL-02");
        c2.addProperty("client_name", "Client Two");
        c2.addProperty("updated_at", "2026-06-13T10:05:00Z"); // Highest
        
        fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS).add(c1);
        fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS).add(c2);
        
        // 2. Perform first pull (should pull all 2 records)
        RemoteToLocalSync.pullAll(fakeSupabase);
        ClientRepository repo = new ClientRepository();
        assertEquals(2, repo.findAllSortedById().size(), "First pull should insert both seeded clients");
        
        // Verify watermark is maximum remote updated_at (2026-06-13T10:05:00Z)
        String firstWatermark = getWatermark("clients");
        assertEquals("2026-06-13T10:05:00Z", firstWatermark);
        
        // 3. Perform pull again with no changes on Supabase
        int changesEmpty = RemoteToLocalSync.pullAll(fakeSupabase);
        assertEquals(0, changesEmpty, "Subsequent pull with no changes should return 0 changes");
        
        // Watermark should remain unchanged
        assertEquals("2026-06-13T10:05:00Z", getWatermark("clients"));
        
        // 4. Seed a new client with a newer timestamp
        JsonObject c3 = new JsonObject();
        c3.addProperty("uuid", "uuid-c3");
        c3.addProperty("client_code", "CL-03");
        c3.addProperty("client_name", "Client Three");
        c3.addProperty("updated_at", "2026-06-13T10:10:00Z");
        
        fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS).add(c3);
        
        // Perform incremental pull
        int changesIncremental = RemoteToLocalSync.pullAll(fakeSupabase);
        assertEquals(1, changesIncremental, "Incremental pull should only process the new record");
        
        // Verify watermark is updated to new highest
        assertEquals("2026-06-13T10:10:00Z", getWatermark("clients"));
        
        // Verify local client records count
        assertEquals(3, repo.findAllSortedById().size());
    }

    private String getWatermark(String table) throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT last_pull_at FROM sync_metadata WHERE table_name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }
}

