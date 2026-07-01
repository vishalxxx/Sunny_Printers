package service.sync;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseRestClient;
import repository.BankDetailsRepository;
import repository.ClientRepository;
import repository.CompanyDetailsRepository;
import repository.JobItemRepository;
import service.SupplierService;
import utils.DBConnection;

public class PermanentRecoverySyncTest {

    private static String dbUrl;

    @BeforeAll
    public static void setup() throws Exception {
        dbUrl = TestDatabaseHelper.createIsolatedDb("PermanentRecoveryTest");
        DBConnection.setUrl(dbUrl);
    }

    @AfterAll
    public static void tearDown() {
        TestDatabaseHelper.cleanupTestDir();
    }

    @Test
    public void testSupabaseRestClientDeleteIsBlocked() {
        SupabaseRestClient client = new SupabaseRestClient("https://fake-project.supabase.co", "fake-anon-key");
        assertThrows(UnsupportedOperationException.class, () -> {
            client.delete(SupabaseEndpoints.CLIENTS, "uuid=eq.test-123");
        }, "SupabaseRestClient.delete must throw UnsupportedOperationException for business tables");
    }

    @Test
    public void testSupplierSoftDeleteEnforced() throws Exception {
        String uuid = "00000000-0000-0000-0000-000000000999";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO suppliers (uuid, supplier_code, name, business_name, is_deleted, sync_status, created_at, updated_at) VALUES (?, 'SUP-TEST', 'Test Supplier', 'Test Biz', 0, 'SYNCED', datetime('now'), datetime('now'))")) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        }

        SupplierService service = new SupplierService();
        service.deleteSupplier(uuid);

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT is_deleted, sync_status FROM suppliers WHERE uuid = ?")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Supplier record must remain in database");
                assertEquals(1, rs.getInt("is_deleted"), "Supplier must be soft deleted (is_deleted = 1)");
                assertEquals("PENDING", rs.getString("sync_status"), "Sync status must be PENDING");
            }
        }
    }

    @Test
    public void testClientSoftDeleteEnforced() throws Exception {
        String uuid = "00000000-0000-0000-0000-000000000888";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO clients (uuid, client_code, client_name, business_name, is_deleted, sync_status, created_at, updated_at) VALUES (?, 'CL-TEST', 'Test Client', 'Test Biz', 0, 'SYNCED', datetime('now'), datetime('now'))")) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        }

        ClientRepository repo = new ClientRepository();
        repo.deleteByUuid(uuid);

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT is_deleted, sync_status FROM clients WHERE uuid = ?")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Client record must remain in database");
                assertEquals(1, rs.getInt("is_deleted"), "Client must be soft deleted (is_deleted = 1)");
                assertEquals("PENDING", rs.getString("sync_status"), "Sync status must be PENDING");
            }
        }
    }

    @Test
    public void testBankDetailsSoftDeleteEnforced() throws Exception {
        String uuid = "00000000-0000-0000-0000-000000000777";
        try (Connection con = DBConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO bank_details (uuid, bank_name, account_holder_name, account_no, is_deleted, sync_status, created_at, updated_at) VALUES (?, 'Test Bank', 'Holder', '12345', 0, 'SYNCED', datetime('now'), datetime('now'))")) {
                ps.setString(1, uuid);
                ps.executeUpdate();
            }

            BankDetailsRepository repo = new BankDetailsRepository();
            repo.delete(con, uuid);

            try (PreparedStatement ps = con.prepareStatement("SELECT is_deleted, sync_status FROM bank_details WHERE uuid = ?")) {
                ps.setString(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "BankDetails record must remain in database");
                    assertEquals(1, rs.getInt("is_deleted"), "BankDetails must be soft deleted (is_deleted = 1)");
                    assertEquals("PENDING", rs.getString("sync_status"), "Sync status must be PENDING");
                }
            }
        }
    }
}
