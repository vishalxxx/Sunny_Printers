package integration;
import service.sync.UniversalSyncEngine;


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
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseGate;
import api.supabase.SupabaseReachability;
import model.Client;
import repository.ClientRepository;
import utils.DBConnection;

@Tag("integration")
public class PushConflictResolutionTest {

    private static String dbPath;
    private static FakeSupabaseRestClient fakeSupabase;

    @BeforeAll
    public static void setup() throws Exception {
        dbPath = TestDatabaseHelper.createIsolatedDb("PushConflictResolutionTest");
        fakeSupabase = new FakeSupabaseRestClient();
        SupabaseGate.setOverrideClient(fakeSupabase);
        DBConnection.setUrl(dbPath);
    }

    @AfterAll
    public static void tearDown() {
        TestDatabaseHelper.cleanupTestDir();
    }

    @BeforeEach
    public void resetDbAndFake() throws Exception {
        fakeSupabase.clear();
        SupabaseReachability.invalidateCache();

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM clients");
            stmt.execute("DELETE FROM sync_conflicts");
        }
    }

    @Test
    public void testOlderLocalRecordDoesNotOverwriteNewerRemote() throws Exception {
        String clientUuid = "00000000-0000-0000-0000-000000001001";

        // 1. Remote is newer (12:00:00)
        JsonObject remoteObj = new JsonObject();
        remoteObj.addProperty("uuid", clientUuid);
        remoteObj.addProperty("client_code", "CL-01");
        remoteObj.addProperty("client_name", "Remote Version (Newer)");
        remoteObj.addProperty("updated_at", "2026-06-13 12:00:00");
        fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS).add(remoteObj);

        // 2. Local is older (11:00:00)
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO clients (uuid, client_code, client_name, updated_at, sync_status) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, clientUuid);
            ps.setString(2, "CL-01");
            ps.setString(3, "Local Version (Older)");
            ps.setString(4, "2026-06-13 11:00:00");
            ps.setString(5, "PENDING");
            ps.executeUpdate();
        }

        // 3. Trigger Sync Push
        UniversalSyncEngine.syncAllPending();

        // 4. Verify Remote remains unchanged (Older local did not overwrite it)
        JsonObject finalRemote = fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS).get(0);
        assertEquals("Remote Version (Newer)", finalRemote.get("client_name").getAsString());

        // 5. Verify local pulled the newer remote version (LWW resolution)
        ClientRepository repo = new ClientRepository();
        Client localClient = repo.findByUuid(clientUuid);
        assertEquals("Remote Version (Newer)", localClient.getClientName(), "Local should be updated to newer remote");

        // 6. Verify conflict logged in SQLite
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*), resolution_strategy FROM sync_conflicts");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
            assertEquals("LAST_WRITE_WINS_REMOTE_WINS", rs.getString(2));
        }
    }

    @Test
    public void testNewerLocalRecordOverwritesOlderRemote() throws Exception {
        String clientUuid = "00000000-0000-0000-0000-000000001002";

        // 1. Remote is older (10:00:00)
        JsonObject remoteObj = new JsonObject();
        remoteObj.addProperty("uuid", clientUuid);
        remoteObj.addProperty("client_code", "CL-02");
        remoteObj.addProperty("client_name", "Remote Version (Older)");
        remoteObj.addProperty("updated_at", "2026-06-13 10:00:00");
        fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS).add(remoteObj);

        // 2. Local is newer (11:00:00)
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO clients (uuid, client_code, client_name, updated_at, sync_status) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, clientUuid);
            ps.setString(2, "CL-02");
            ps.setString(3, "Local Version (Newer)");
            ps.setString(4, "2026-06-13 11:00:00");
            ps.setString(5, "PENDING");
            ps.executeUpdate();
        }

        // 3. Trigger Sync Push
        UniversalSyncEngine.syncAllPending();

        // 4. Verify Remote updated successfully to local version
        JsonObject finalRemote = fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS).get(0);
        assertEquals("Local Version (Newer)", finalRemote.get("client_name").getAsString());

        // 5. Verify no conflict logged (since it is standard LWW local wins overwrite)
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM sync_conflicts");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    public void testEqualTimestampsNoUpdates() throws Exception {
        String clientUuid = "00000000-0000-0000-0000-000000001003";

        // 1. Remote (11:00:00)
        JsonObject remoteObj = new JsonObject();
        remoteObj.addProperty("uuid", clientUuid);
        remoteObj.addProperty("client_code", "CL-03");
        remoteObj.addProperty("client_name", "Same Version");
        remoteObj.addProperty("updated_at", "2026-06-13 11:00:00");
        fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS).add(remoteObj);

        // 2. Local (11:00:00)
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO clients (uuid, client_code, client_name, updated_at, sync_status) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, clientUuid);
            ps.setString(2, "CL-03");
            ps.setString(3, "Same Version");
            ps.setString(4, "2026-06-13 11:00:00");
            ps.setString(5, "PENDING");
            ps.executeUpdate();
        }

        // 3. Trigger Sync Push
        UniversalSyncEngine.syncAllPending();

        // 4. Verify no conflict logged
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM sync_conflicts");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    public void testAsyncPatchRequestSkippedWhenRemoteIsNewer() throws Exception {
        String clientUuid = "00000000-0000-0000-0000-000000001004";

        // 1. Remote is newer (12:00:00)
        JsonObject remoteObj = new JsonObject();
        remoteObj.addProperty("uuid", clientUuid);
        remoteObj.addProperty("client_code", "CL-04");
        remoteObj.addProperty("client_name", "Remote Version (Newer)");
        remoteObj.addProperty("updated_at", "2028-06-13 12:00:00");
        fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS).add(remoteObj);

        // 2. Save a local baseline
        ClientRepository repo = new ClientRepository();
        Client c = new Client("Remote Version (Newer)", "Doe", "123", "", "", "", "", "Delhi", "", "");
        c.setClientUuid(clientUuid);
        c.setClientCode("CL-04");
        c.setSyncStatus("SYNCED");
        c.setUpdatedAt("2026-06-13 10:00:00");
        repo.save(c);

        // 3. Modify client locally but with an older local timestamp simulation (e.g. 11:00:00)
        // We set the updated_at manually to simulate clock skew or older offline edits
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE clients SET client_name = 'Local Edit (Older)', updated_at = '2026-06-13 11:00:00' WHERE uuid = ?")) {
            ps.setString(1, clientUuid);
            ps.executeUpdate();
        }

        Client updatedClient = repo.findByUuid(clientUuid);
        
        // 4. Direct update triggers repo.update() -> pushClientToSupabaseAsync
        repo.update(updatedClient);

        // Wait brief moment for async CompletableFuture task
        Thread.sleep(800);

        // 5. Verify Remote remains "Remote Version (Newer)"
        JsonObject finalRemote = fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS).get(0);
        assertEquals("Remote Version (Newer)", finalRemote.get("client_name").getAsString());

        // 6. Verify conflict logged
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM sync_conflicts");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) > 0, "Conflict should be logged for async patch");
        }
    }
}

