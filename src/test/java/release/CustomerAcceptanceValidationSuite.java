package release;
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
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseGate;
import api.supabase.SupabaseReachability;
import model.Client;
import repository.ClientRepository;
import utils.DBConnection;

@Tag("release")
public class CustomerAcceptanceValidationSuite {

    private static String dbPrimary;
    private static String dbBackup;
    private static FakeSupabaseRestClient fakeSupabase;

    @BeforeAll
    public static void setup() throws Exception {
        dbPrimary = TestDatabaseHelper.createIsolatedDb("CustAccept_Primary");
        dbBackup = TestDatabaseHelper.createIsolatedDb("CustAccept_Backup");
        fakeSupabase = new FakeSupabaseRestClient();
        SupabaseGate.setOverrideClient(fakeSupabase);
    }

    @AfterAll
    public static void tearDown() {
        TestDatabaseHelper.cleanupTestDir();
    }

    @org.junit.jupiter.api.BeforeEach
    public void resetFake() {
        fakeSupabase.clear();
        SupabaseReachability.invalidateCache();
    }

    @Test
    public void testBackupRestoreAndSupabaseRecoveryParity() throws Exception {
        DBConnection.setUrl(dbPrimary);
        ClientRepository repo = new ClientRepository();
        String uuid = UUID.randomUUID().toString();
        Client c = new Client("Acceptance Corp", "John Smith", "9999988888", "", "john@accept.com", "07AAAAA0000A1Z5", "", "Delhi", "", "");
        c.setClientUuid(uuid);
        c.setClientCode("CL-ACC01");
        c.setSyncStatus("PENDING");
        repo.save(c);

        UniversalSyncEngine.syncAllPending();

        // Simulate Supabase Disaster Recovery Pull onto new DB
        DBConnection.setUrl(dbBackup);
        RemoteToLocalSync.pullAll(fakeSupabase);

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT client_name FROM clients WHERE uuid = ?")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Restored DB must contain client record");
                assertEquals("John Smith", rs.getString("client_name"));
            }
        }
    }

    @Test
    public void testUserMistakeIdempotencyDoubleSave() throws Exception {
        DBConnection.setUrl(dbPrimary);
        ClientRepository repo = new ClientRepository();
        String uuid = UUID.randomUUID().toString();
        Client c = new Client("Idempotent Corp", "Alice", "9999977777", "", "alice@idemp.com", "07AAAAA0000A1Z5", "", "Delhi", "", "");
        c.setClientUuid(uuid);
        c.setClientCode("CL-IDEMP01");
        c.setSyncStatus("PENDING");
        
        // Rapid double save simulation
        repo.save(c);
        repo.save(c);

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM clients WHERE uuid = ?")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Double save must not create duplicate records");
            }
        }
    }
}

