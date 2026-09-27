package integration;
import service.sync.RemoteToLocalSync;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.supabase.SupabaseGate;
import api.supabase.SupabaseReachability;
import model.Client;
import repository.ClientRepository;
import utils.DBConnection;

@Tag("integration")
public class ConflictResolutionTest {

    private static String dbA;
    private static String dbB;
    private static FakeSupabaseRestClient fakeSupabase;

    @BeforeAll
    public static void setup() throws Exception {
        dbA = TestDatabaseHelper.createIsolatedDb("Conflict_MachineA");
        dbB = TestDatabaseHelper.createIsolatedDb("Conflict_MachineB");
        fakeSupabase = new FakeSupabaseRestClient();
        SupabaseGate.setOverrideClient(fakeSupabase);
    }

    @AfterAll
    public static void tearDown() {
        TestDatabaseHelper.cleanupTestDir();
    }

    @BeforeEach
    public void resetFake() {
        fakeSupabase.clear();
        SupabaseReachability.invalidateCache();
    }

    @Test
    public void testLastWriteWinsConflict() throws Exception {
        String clientUuid = "00000000-0000-0000-0000-000000001234";
        
        // 1. Setup client on Machine A and sync to Supabase
        DBConnection.setTestDatabaseUrl(dbA);
        ClientRepository repoA = new ClientRepository();
        Client cA = new Client("Sunny Corp", "John Doe", "123456", "", "", "", "", "Delhi", "", "");
        cA.setClientUuid(clientUuid);
        cA.setClientCode("CL-001");
        cA.setSyncStatus("PENDING");
        cA.setSyncVersion(1);
        repoA.save(cA);
        
        // Push Machine A to Supabase
        UniversalSyncEngine.syncAllPending();
        
        // 2. Pull on Machine B to replicate the client
        DBConnection.setTestDatabaseUrl(dbB);
        RemoteToLocalSync.pullAll(fakeSupabase);
        
        ClientRepository repoB = new ClientRepository();
        Client cB = repoB.findByUuid(clientUuid);
        assertNotNull(cB, "Client should be replicated to Machine B");
        
        // 3. Simulate conflicts with distinct timestamps
        // Machine A updates to "Delhi Branch" (relative future)
        DBConnection.setTestDatabaseUrl(dbA);
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE clients SET client_name = 'Delhi Branch', updated_at = datetime('now', '+1 hour'), sync_status = 'PENDING' WHERE uuid = ?")) {
            ps.setString(1, clientUuid);
            ps.executeUpdate();
        }
        
        // Machine B updates to "Noida Branch" (relative further future)
        DBConnection.setTestDatabaseUrl(dbB);
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE clients SET client_name = 'Noida Branch', updated_at = datetime('now', '+2 hours'), sync_status = 'PENDING' WHERE uuid = ?")) {
            ps.setString(1, clientUuid);
            ps.executeUpdate();
        }
        
        // 4. Sync Machine B (pushes 11:00:00 change)
        DBConnection.setTestDatabaseUrl(dbB);
        UniversalSyncEngine.syncAllPending();
        
        // 5. Sync Machine A (attempts to push 10:00:00 change, but B is newer on remote, so A conflicts, logs conflict, and pulls Noida Branch)
        DBConnection.setTestDatabaseUrl(dbA);
        UniversalSyncEngine.syncAllPending();
        
        // Verify both machines ended up with Machine B's newer version
        DBConnection.setTestDatabaseUrl(dbA);
        Client finalA = repoA.findByUuid(clientUuid);
        assertEquals("Noida Branch", finalA.getClientName(), "Delhi should have been overwritten by Noida Branch");
        
        DBConnection.setTestDatabaseUrl(dbB);
        Client finalB = repoB.findByUuid(clientUuid);
        assertEquals("Noida Branch", finalB.getClientName());
        
        // Check that conflict was logged on Machine A (since Noida Branch overwrote Delhi Branch)
        DBConnection.setTestDatabaseUrl(dbA);
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM sync_conflicts");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) > 0, "Conflict should have been logged in SQLite sync_conflicts");
        }
    }
}

