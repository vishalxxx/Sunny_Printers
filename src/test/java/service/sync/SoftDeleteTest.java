package service.sync;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

public class SoftDeleteTest {

    private static String dbA;
    private static String dbB;
    private static FakeSupabaseRestClient fakeSupabase;

    @BeforeAll
    public static void setup() throws Exception {
        dbA = TestDatabaseHelper.createIsolatedDb("SoftDelete_MachineA");
        dbB = TestDatabaseHelper.createIsolatedDb("SoftDelete_MachineB");
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
    public void testSoftDeletePropagation() throws Exception {
        String clientUuid = "00000000-0000-0000-0000-000000008001";

        // 1. Seed on Machine A and push
        DBConnection.setUrl(dbA);
        ClientRepository repoA = new ClientRepository();
        Client c = new Client("Sunny Corp", "Doe", "123", "", "", "", "", "Delhi", "", "");
        c.setClientUuid(clientUuid);
        c.setClientCode("CL-001");
        c.setSyncStatus("PENDING");
        repoA.save(c);

        UniversalSyncEngine.syncAllPending();

        // Verify remote exists and is not deleted
        JsonObject remoteRow = fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS).get(0);
        assertFalse(remoteRow.get("is_deleted").getAsBoolean());

        // 2. Pull on Machine B
        DBConnection.setUrl(dbB);
        RemoteToLocalSync.pullAll(fakeSupabase);
        ClientRepository repoB = new ClientRepository();
        assertNotNull(repoB.findByUuid(clientUuid));

        // 3. Soft-delete on Machine A (simulate a non-admin soft delete)
        DBConnection.setUrl(dbA);
        // We write directly to SQLite to simulate setting PENDING and is_deleted
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE clients SET is_deleted = 1, deleted_at = '2028-06-13 12:00:00', sync_status = 'PENDING', updated_at = '2028-06-13 12:00:00' WHERE uuid = ?")) {
            ps.setString(1, clientUuid);
            ps.executeUpdate();
        }

        // Push from Machine A
        UniversalSyncEngine.syncAllPending();

        // Verify remote is updated to is_deleted = true
        remoteRow = fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS).get(0);
        assertTrue(remoteRow.get("is_deleted").getAsBoolean());
        assertEquals("2028-06-13 12:00:00", remoteRow.get("deleted_at").getAsString());

        // 4. Pull on Machine B
        DBConnection.setUrl(dbB);
        RemoteToLocalSync.pullAll(fakeSupabase);

        // Verify Machine B now has the row marked soft-deleted
        Client localB = repoB.findByUuid(clientUuid);
        assertNotNull(localB);
        assertTrue(localB.isDeleted());
        assertEquals("2028-06-13 12:00:00", localB.getDeletedAt());

        // 5. Subsequent pulls must not resurrect the record or clear the deleted status
        RemoteToLocalSync.pullAll(fakeSupabase);
        localB = repoB.findByUuid(clientUuid);
        assertTrue(localB.isDeleted());
    }
}
