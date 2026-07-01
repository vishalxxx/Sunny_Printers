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

import java.io.File;
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
import model.Supplier;
import repository.BankDetailsRepository;
import repository.ClientRepository;
import repository.CompanyDetailsRepository;
import service.SupplierService;
import utils.DBConnection;

@Tag("release")
public class DisasterRecoveryValidationSuite {

    private static String primaryDbUrl;
    private static String recoveryDbUrl;
    private static FakeSupabaseRestClient fakeSupabase;

    @BeforeAll
    public static void setup() throws Exception {
        primaryDbUrl = TestDatabaseHelper.createIsolatedDb("DR_PrimaryMachine");
        recoveryDbUrl = TestDatabaseHelper.createIsolatedDb("DR_RecoveryMachine");
        fakeSupabase = new FakeSupabaseRestClient();
        SupabaseGate.setOverrideClient(fakeSupabase);
    }

    @AfterAll
    public static void tearDown() {
        TestDatabaseHelper.cleanupTestDir();
    }

    @Test
    public void testFullDisasterRecoveryCycle() throws Exception {
        // --- PHASE 1 & 3: Local Creation & Sync to Supabase ---
        DBConnection.setUrl(primaryDbUrl);
        fakeSupabase.clear();
        SupabaseReachability.invalidateCache();

        String clientUuid = UUID.randomUUID().toString();
        String supplierUuid = UUID.randomUUID().toString();

        ClientRepository clientRepo = new ClientRepository();
        Client client = new Client("DR Global Corp", "Jane Doe", "9876543210", "", "jane@dr.com", "07AAAAA0000A1Z5", "", "Delhi", "", "");
        client.setClientUuid(clientUuid);
        client.setClientCode("CL-DR01");
        client.setSyncStatus("PENDING");
        clientRepo.save(client);

        SupplierService supplierService = new SupplierService();
        Supplier supplier = new Supplier();
        supplier.setUuid(supplierUuid);
        supplier.setSupplierCode("SUP-DR01");
        supplier.setName("DR Paper Works");
        supplier.setbusinessName("DR Paper Works Ltd");
        supplier.setType("PAPER");
        supplier.setMobile("9123456789");
        supplierService.addSupplier(supplier);

        // Synchronize primary DB to Supabase
        UniversalSyncEngine.syncAllPending();

        // Verify Supabase has active records
        assertEquals(1, fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS).size());
        assertEquals(1, fakeSupabase.getTableData(SupabaseEndpoints.SUPPLIERS).size());

        // --- PHASE 1 & 2: Soft Delete Execution & Soft Delete Sync ---
        clientRepo.deleteByUuid(clientUuid);
        supplierService.deleteSupplier(supplierUuid);

        // Verify primary SQLite retains soft deleted records
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT is_deleted FROM clients WHERE uuid = ?")) {
            ps.setString(1, clientUuid);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("is_deleted"), "Client must be soft-deleted in SQLite");
            }
        }

        // Push soft deletes to Supabase
        UniversalSyncEngine.syncAllPending();

        // Verify Supabase retains rows with is_deleted = true
        var remoteClients = fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS);
        assertEquals(1, remoteClients.size());
        assertTrue(remoteClients.get(0).get("is_deleted").getAsBoolean(), "Client must be soft-deleted on Supabase");

        // --- PHASE 4 & 5: Total SQLite Disaster Recovery Rebuild ---
        // Switch connection to completely fresh/clean SQLite recovery DB
        DBConnection.setUrl(recoveryDbUrl);
        
        // Execute full pull recovery from Supabase
        RemoteToLocalSync.pullAll(fakeSupabase);

        // Verify full recovery into new SQLite DB
        try (Connection con = DBConnection.getConnection()) {
            // Verify Client restored with identical UUID and soft-delete flag intact
            try (PreparedStatement ps = con.prepareStatement("SELECT client_name, is_deleted FROM clients WHERE uuid = ?")) {
                ps.setString(1, clientUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Client record must be completely restored from Supabase");
                    assertEquals("Jane Doe", rs.getString("client_name"));
                    assertEquals(1, rs.getInt("is_deleted"), "Restored client must retain soft-delete flag");
                }
            }

            // Verify Supplier restored with identical UUID and soft-delete flag intact
            try (PreparedStatement ps = con.prepareStatement("SELECT business_name, is_deleted FROM suppliers WHERE uuid = ?")) {
                ps.setString(1, supplierUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Supplier record must be completely restored from Supabase");
                    assertEquals("DR Paper Works Ltd", rs.getString("business_name"));
                    assertEquals(1, rs.getInt("is_deleted"), "Restored supplier must retain soft-delete flag");
                }
            }
        }
    }
}

