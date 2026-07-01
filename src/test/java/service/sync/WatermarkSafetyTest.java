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

import com.google.gson.JsonObject;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseGate;
import api.supabase.SupabaseReachability;
import repository.ClientRepository;
import utils.DBConnection;

public class WatermarkSafetyTest {

    private static String dbPath;
    private static FakeSupabaseRestClient fakeSupabase;

    @BeforeAll
    public static void setup() throws Exception {
        dbPath = TestDatabaseHelper.createIsolatedDb("WatermarkSafetyTest");
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
            stmt.execute("DELETE FROM sync_metadata");
        }
    }

    @Test
    public void testSafetyOverlapWindowAndEmptyPulls() throws Exception {
        // 1. Initial Pull to establish a watermark (10:05:00Z)
        JsonObject c1 = new JsonObject();
        c1.addProperty("uuid", "uuid-c1");
        c1.addProperty("client_code", "CL-01");
        c1.addProperty("client_name", "Client One");
        c1.addProperty("updated_at", "2026-06-13T10:05:00Z");
        fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS).add(c1);
        
        RemoteToLocalSync.pullAll(fakeSupabase);
        String watermarkBefore = getWatermark("clients");
        assertEquals("2026-06-13T10:05:00Z", watermarkBefore);

        // 2. Simulate record written at 10:04:30Z (slightly in the past due to clock skew, but within 60s safety overlap window)
        JsonObject c2 = new JsonObject();
        c2.addProperty("uuid", "uuid-c2");
        c2.addProperty("client_code", "CL-02");
        c2.addProperty("client_name", "Client Two");
        c2.addProperty("updated_at", "2026-06-13T10:04:30Z"); // > 10:04:00Z (overlap watermark start)
        fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS).add(c2);

        // Pull again (overlap window calculates gt. 10:04:00Z)
        int changes = RemoteToLocalSync.pullAll(fakeSupabase);
        assertEquals(1, changes, "Should apply only 1 change (c2) since c1 is already up-to-date and skipped");
        
        // Verify both clients exist in the local database
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM clients");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1), "SQLite should contain 2 clients in total");
        }

        // 3. Verify watermark remains at 10:05:00Z (since it's the highest)
        assertEquals("2026-06-13T10:05:00Z", getWatermark("clients"));

        // 4. Run an empty pull (no new records on remote)
        fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS).clear();
        int emptyChanges = RemoteToLocalSync.pullAll(fakeSupabase);
        assertEquals(0, emptyChanges);

        // Watermark MUST not advance (remain 10:05:00Z)
        assertEquals("2026-06-13T10:05:00Z", getWatermark("clients"), "Watermark should not advance on empty pulls");
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
