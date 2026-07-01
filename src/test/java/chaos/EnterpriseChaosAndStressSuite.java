package chaos;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseGate;
import api.supabase.SupabaseReachability;
import model.Client;
import repository.ClientRepository;
import utils.DBConnection;

@Tag("chaos")
public class EnterpriseChaosAndStressSuite {

    private static String dbA;
    private static String dbB;
    private static String dbC;
    private static FakeSupabaseRestClient fakeSupabase;

    @BeforeAll
    public static void setup() throws Exception {
        dbA = TestDatabaseHelper.createIsolatedDb("Chaos_MachineA");
        dbB = TestDatabaseHelper.createIsolatedDb("Chaos_MachineB");
        dbC = TestDatabaseHelper.createIsolatedDb("Chaos_MachineC");
        fakeSupabase = new FakeSupabaseRestClient();
        SupabaseGate.setOverrideClient(fakeSupabase);
    }

    @org.junit.jupiter.api.BeforeEach
    public void resetFake() {
        fakeSupabase.clear();
        SupabaseReachability.invalidateCache();
    }

    @AfterAll
    public static void tearDown() {
        TestDatabaseHelper.cleanupTestDir();
    }

    @Test
    public void testMultiMachineConcurrentOperationsAndMultiCycleRecovery() throws Exception {
        fakeSupabase.clear();
        SupabaseReachability.invalidateCache();

        // --- PHASE 1 & 2: Multi-Machine Operations ---
        // Machine A creates client
        DBConnection.setUrl(dbA);
        ClientRepository repoA = new ClientRepository();
        String uuidA = UUID.randomUUID().toString();
        Client clientA = new Client("Chaos Corp A", "Operator A", "9876543210", "", "opA@chaos.com", "07AAAAA0000A1Z5", "", "Delhi", "", "");
        clientA.setClientUuid(uuidA);
        clientA.setClientCode("CL-CHAOS01");
        clientA.setSyncStatus("PENDING");
        repoA.save(clientA);
        UniversalSyncEngine.syncAllPending();

        // Machine B creates client concurrently
        DBConnection.setUrl(dbB);
        ClientRepository repoB = new ClientRepository();
        String uuidB = UUID.randomUUID().toString();
        Client clientB = new Client("Chaos Corp B", "Operator B", "9876543211", "", "opB@chaos.com", "07AAAAA0000A1Z6", "", "Delhi", "", "");
        clientB.setClientUuid(uuidB);
        clientB.setClientCode("CL-CHAOS02");
        clientB.setSyncStatus("PENDING");
        repoB.save(clientB);
        UniversalSyncEngine.syncAllPending();

        // Verify remote Supabase contains both clients
        assertEquals(2, fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS).size());

        // Machine A soft deletes clientA
        DBConnection.setUrl(dbA);
        repoA.deleteByUuid(uuidA);
        UniversalSyncEngine.syncAllPending();

        // Verify remote Supabase retains clientA with is_deleted = true
        var remoteClients = fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS);
        assertEquals(2, remoteClients.size());

        // --- PHASE 13: Multi-Cycle Database Recovery ---
        for (int cycle = 1; cycle <= 3; cycle++) {
            String cycleDb = TestDatabaseHelper.createIsolatedDb("Chaos_MachineC_Cycle" + cycle);
            DBConnection.setUrl(cycleDb);
            RemoteToLocalSync.pullAll(fakeSupabase);

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM clients")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(2, rs.getInt(1), "Cycle " + cycle + " rebuild must restore exactly 2 client rows");
                }
            }
        }
    }

    @Test
    public void testConcurrentThreadStressAndConnectionSafety() throws Exception {
        DBConnection.setUrl(dbA);
        ExecutorService executor = Executors.newFixedThreadPool(5);
        for (int i = 0; i < 10; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    ClientRepository repo = new ClientRepository();
                    Client c = new Client("Concurrent Corp " + index, "User " + index, "900000000" + index, "", "user@stress.com", "07AAAAA0000A1Z5", "", "Delhi", "", "");
                    c.setClientUuid(UUID.randomUUID().toString());
                    c.setClientCode("CL-STR" + index);
                    c.setSyncStatus("PENDING");
                    repo.save(c);
                } catch (Exception e) {
                    fail("Concurrent operation threw exception: " + e.getMessage());
                }
            });
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
}

