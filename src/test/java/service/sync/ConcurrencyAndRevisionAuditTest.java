package service.sync;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import service.InvoiceMasterService;
import repository.InvoiceMasterRepository;
import utils.DBConnection;

public class ConcurrencyAndRevisionAuditTest {

    private String originalDbUrl;
    private String testDbUrl;

    @BeforeEach
    public void setUp() throws Exception {
        originalDbUrl = DBConnection.getUrl();
        testDbUrl = TestDatabaseHelper.createIsolatedDb("concurrency_revision_test");
        DBConnection.setUrl(testDbUrl);
    }

    @AfterEach
    public void tearDown() {
        DBConnection.setUrl(originalDbUrl);
        TestDatabaseHelper.cleanupTestDir();
    }

    @Test
    public void testScenario1SimultaneousEdits() throws Exception {
        // Scenario 1: Two desktop systems edit the same Proforma invoice simultaneously.
        // We simulate this by checking how the SyncConflictResolver handles different updated_at timestamps.
        try (Connection con = DriverManager.getConnection(testDbUrl)) {
            String invUuid = UUID.randomUUID().toString();
            
            // Insert initial invoice
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO invoice_master (uuid, invoice_no, client_uuid, client_name, invoice_date, amount, paid_amount, due_amount, payment_status, status, updated_at) " +
                    "VALUES (?, 'PI-100', 'client-1', 'Client One', '2026-06-20', 1000.0, 0.0, 1000.0, 'UNPAID', 'DRAFT', '2026-06-20 12:00:00')")) {
                ps.setString(1, invUuid);
                ps.executeUpdate();
            }

            // System A edits locally (older timestamp)
            String localUpdatedAtOlder = "2026-06-20T12:05:00Z";
            
            // System B has edited remote with a newer timestamp
            String remoteUpdatedAtNewer = "2026-06-20T12:10:00Z";
            
            // Under LWW, if remote is newer, the resolver should overwrite the local database with remote changes.
            // Let's verify parseTimestamp behaves correctly.
            Instant localInst = SyncConflictResolver.parseTimestamp(localUpdatedAtOlder);
            Instant remoteInst = SyncConflictResolver.parseTimestamp(remoteUpdatedAtNewer);
            
            assertTrue(remoteInst.isAfter(localInst), "Remote newer timestamp should be recognized as after local older timestamp");
        }
    }

    @Test
    public void testScenario2DoubleSpendAdvance() throws Exception {
        // Scenario 2: Customer Advance is applied from two systems at nearly the same time.
        // This is a classic distributed systems race/offline conflict.
        InvoiceMasterRepository repo = new InvoiceMasterRepository();
        
        try (Connection con = DriverManager.getConnection(testDbUrl)) {
            String clientUuid = "client-1";
            
            // Insert Client
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO clients (uuid, client_code, client_name) VALUES (?, 'C001', 'Test')")) {
                ps.setString(1, clientUuid);
                ps.executeUpdate();
            }

            // 1. Client has an advance payment of 1000.0
            String paymentUuid = UUID.randomUUID().toString();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payments (uuid, client_uuid, amount, payment_date, method, type) VALUES (?, ?, 1000.0, '2026-06-20', 'Cash', 'Payment')")) {
                ps.setString(1, paymentUuid);
                ps.setString(2, clientUuid);
                ps.executeUpdate();
            }

            // Verify initial unallocated balance is 1000.0
            assertEquals(1000.0, repo.getClientUnallocatedBalance(con, clientUuid), 0.001);

            // 2. System A allocates 1000.0 to Invoice X
            String invoiceXUuid = UUID.randomUUID().toString();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO invoice_master (uuid, invoice_no, client_uuid, amount, paid_amount, due_amount, payment_status, status) VALUES (?, 'INV-X', ?, 1000.0, 0.0, 1000.0, 'UNPAID', 'SENT')")) {
                ps.setString(1, invoiceXUuid);
                ps.setString(2, clientUuid);
                ps.executeUpdate();
            }

            // System A allocation
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount, is_deleted) VALUES (?, ?, ?, 1000.0, 0)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, paymentUuid);
                ps.setString(3, invoiceXUuid);
                ps.executeUpdate();
            }

            // 3. System B allocates 1000.0 to Invoice Y concurrently
            String invoiceYUuid = UUID.randomUUID().toString();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO invoice_master (uuid, invoice_no, client_uuid, amount, paid_amount, due_amount, payment_status, status) VALUES (?, 'INV-Y', ?, 1000.0, 0.0, 1000.0, 'UNPAID', 'SENT')")) {
                ps.setString(1, invoiceYUuid);
                ps.setString(2, clientUuid);
                ps.executeUpdate();
            }

            // System B allocation (simulated incoming sync or push)
            String allocB = UUID.randomUUID().toString();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount, is_deleted) VALUES (?, ?, ?, 1000.0, 0)")) {
                ps.setString(1, allocB);
                ps.setString(2, paymentUuid);
                ps.setString(3, invoiceYUuid);
                ps.executeUpdate();
            }

            // Run validateAndResolveDoubleSpend on the concurrent allocation (as if it was processed during sync)
            boolean rejected = SyncConflictResolver.validateAndResolveDoubleSpend(con, allocB, paymentUuid, 1000.0, true, "2026-06-20 13:00:00", "{}");
            assertTrue(rejected, "The concurrent double-spend allocation should be rejected");

            // Verify client unallocated balance is not negative (remains 0.0, since the second allocation was rejected and deleted)
            double unallocatedBalance = repo.getClientUnallocatedBalance(con, clientUuid);
            System.out.println("[Scenario 2 Result] Client unallocated balance after double-spend prevention: " + unallocatedBalance);
            assertEquals(0.0, unallocatedBalance, 0.001);

            // Verify a conflict is logged in sync_conflicts
            try (PreparedStatement ps = con.prepareStatement("SELECT count(*) FROM sync_conflicts WHERE record_uuid = ?")) {
                ps.setString(1, allocB);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "A sync conflict should be logged for the rejected double-spend allocation");
                }
            }

            // Verify the concurrent allocation is marked as deleted locally
            try (PreparedStatement ps = con.prepareStatement("SELECT is_deleted FROM payment_allocations WHERE uuid = ?")) {
                ps.setString(1, allocB);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt("is_deleted"), "Rejected allocation should be marked as deleted");
                }
            }
        }
    }

    @Test
    public void testScenario3CancelWhilePaying() throws Exception {
        // Scenario 3: Invoice is cancelled while a payment is being recorded.
        InvoiceMasterRepository repo = new InvoiceMasterRepository();
        InvoiceMasterService service = new InvoiceMasterService();

        try (Connection con = DriverManager.getConnection(testDbUrl)) {
            String clientUuid = "client-1";
            String invoiceUuid = UUID.randomUUID().toString();
            String jobUuid = UUID.randomUUID().toString();

            // Insert initial client, job, and invoice
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO clients (uuid, client_code, client_name) VALUES (?, 'C001', 'Test')")) {
                ps.setString(1, clientUuid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO jobs (uuid, client_uuid, job_code, status, amount) VALUES (?, ?, 'J001', 'Completed', 1000.0)")) {
                ps.setString(1, jobUuid);
                ps.setString(2, clientUuid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO invoice_master (uuid, invoice_no, client_uuid, amount, paid_amount, due_amount, payment_status, status) VALUES (?, 'INV-101', ?, 1000.0, 0.0, 1000.0, 'UNPAID', 'DRAFT')")) {
                ps.setString(1, invoiceUuid);
                ps.setString(2, clientUuid);
                ps.executeUpdate();
            }

            // System A cancels the invoice (unlinks job)
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE jobs SET invoice_uuid = NULL WHERE uuid = ?")) {
                ps.setString(1, jobUuid);
                ps.executeUpdate();
            }
            service.recalculateInvoiceTotals(con, invoiceUuid);

            // Concurrently (or just after), System B inserts a payment allocation to the same invoice
            String paymentUuid = UUID.randomUUID().toString();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payments (uuid, client_uuid, amount, payment_date, method, type) VALUES (?, ?, 1000.0, '2026-06-20', 'Cash', 'Payment')")) {
                ps.setString(1, paymentUuid);
                ps.setString(2, clientUuid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount, is_deleted) VALUES (?, ?, ?, 1000.0, 0)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, paymentUuid);
                ps.setString(3, invoiceUuid);
                ps.executeUpdate();
            }

            // Recalculating totals again
            service.recalculateInvoiceTotals(con, invoiceUuid);

            // Fetch invoice
            var inv = repo.findByUuid(con, invoiceUuid);
            
            // Check if the invoice is CANCELLED but now has paid_amount > 0 or due_amount != 0
            System.out.println("[Scenario 3 Result] Cancelled invoice: status=" + inv.getStatus() + ", paid_amount=" + inv.getPaidAmount() + ", due_amount=" + inv.getDueAmount());
            
            // The recalculateInvoiceTotals check activeJobUuids.isEmpty() block correctly resets paid_amount = 0 and due_amount = 0 and deallocates allocations.
            // Let's verify that the paid amount was reset back to 0.0 despite the concurrent allocation.
            assertEquals(0.0, inv.getPaidAmount(), 0.001);
            assertEquals("CANCELLED", inv.getStatus());
            assertEquals("Void", inv.getPaymentStatus());
        }
    }

    @Test
    public void testScenario4RevisionWithAllocations() throws Exception {
        // Scenario 4: Invoice is revised while payment allocations exist.
        // Let's check if the system preserves existing payment allocations during recalculation/revision.
        InvoiceMasterRepository repo = new InvoiceMasterRepository();
        InvoiceMasterService service = new InvoiceMasterService();

        try (Connection con = DriverManager.getConnection(testDbUrl)) {
            String clientUuid = "client-1";
            String invoiceUuid = UUID.randomUUID().toString();
            String jobUuid = UUID.randomUUID().toString();

            // Insert initial client, job, and invoice
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO clients (uuid, client_code, client_name) VALUES (?, 'C001', 'Test')")) {
                ps.setString(1, clientUuid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO jobs (uuid, client_uuid, job_code, status, amount, invoice_uuid) VALUES (?, ?, 'J001', 'Completed', 1000.0, ?)")) {
                ps.setString(1, jobUuid);
                ps.setString(2, clientUuid);
                ps.setString(3, invoiceUuid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO job_items (uuid, job_uuid, type, description, amount) VALUES (?, ?, 'PRINTING', 'Item', 1000.0)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, jobUuid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO invoice_master (uuid, invoice_no, client_uuid, amount, paid_amount, due_amount, payment_status, status, document_series, type) " +
                    "VALUES (?, 'PI-102', ?, 1000.0, 1000.0, 0.0, 'PAID', 'SENT', 'PROFORMA_INVOICE', 'Performa Bills')")) {
                ps.setString(1, invoiceUuid);
                ps.setString(2, clientUuid);
                ps.executeUpdate();
            }

            // Allocations exist
            String paymentUuid = UUID.randomUUID().toString();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payments (uuid, client_uuid, amount, payment_date, method, type) VALUES (?, ?, 1000.0, '2026-06-20', 'Cash', 'Payment')")) {
                ps.setString(1, paymentUuid);
                ps.setString(2, clientUuid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount, is_deleted) VALUES (?, ?, ?, 1000.0, 0)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, paymentUuid);
                ps.setString(3, invoiceUuid);
                ps.executeUpdate();
            }

            // Revise/Recalculate. Under recalculateInvoiceTotals:
            service.recalculateInvoiceTotals(con, invoiceUuid);

            // Verify that allocations were NOT deleted/voided and invoice remains PAID.
            var inv = repo.findByUuid(con, invoiceUuid);
            assertEquals("PAID", inv.getPaymentStatus());
            assertEquals(1000.0, inv.getPaidAmount(), 0.001);
            assertEquals(0.0, inv.getDueAmount(), 0.001);
        }
    }

    @Test
    public void testInvoiceHistoryLoggedOnDoubleSpendRejection() throws Exception {
        try (Connection con = DriverManager.getConnection(testDbUrl)) {
            String clientUuid = "client-history-test";
            String invoiceUuid = UUID.randomUUID().toString();
            String paymentUuid = UUID.randomUUID().toString();
            String allocUuid = UUID.randomUUID().toString();

            // Setup client, invoice, payment
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO clients (uuid, client_code, client_name) VALUES (?, 'C-HIST', 'History Client')")) {
                ps.setString(1, clientUuid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO invoice_master (uuid, invoice_no, client_uuid, client_name, invoice_date, amount, paid_amount, due_amount, payment_status, status) " +
                    "VALUES (?, 'PI-999', ?, 'History Client', '2026-06-20', 1000.0, 0.0, 1000.0, 'UNPAID', 'DRAFT')")) {
                ps.setString(1, invoiceUuid);
                ps.setString(2, clientUuid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payments (uuid, client_uuid, amount, payment_date, method, type) VALUES (?, ?, 1000.0, '2026-06-20', 'Cash', 'Payment')")) {
                ps.setString(1, paymentUuid);
                ps.setString(2, clientUuid);
                ps.executeUpdate();
            }

            // Record one valid allocation of 1000.0
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount, is_deleted) VALUES (?, ?, ?, 1000.0, 0)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, paymentUuid);
                ps.setString(3, invoiceUuid);
                ps.executeUpdate();
            }

            // Attempt to record a concurrent allocation that double-spends the 1000.0 advance
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount, is_deleted) VALUES (?, ?, ?, 1000.0, 0)")) {
                ps.setString(1, allocUuid);
                ps.setString(2, paymentUuid);
                ps.setString(3, invoiceUuid);
                ps.executeUpdate();
            }

            boolean rejected = SyncConflictResolver.validateAndResolveDoubleSpend(con, allocUuid, paymentUuid, 1000.0, true, "2026-06-20 14:00:00", "{}");
            assertTrue(rejected);

            // Verify entry is written to invoice_history
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM invoice_history WHERE type = 'DOUBLE_SPEND_REJECTION'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Invoice history should record DOUBLE_SPEND_REJECTION");
                    assertEquals("PI-999", rs.getString("invoice_no"));
                    assertEquals(clientUuid, rs.getString("client_id"));
                    assertTrue(rs.getString("status").contains("REJECTED"));
                    assertTrue(rs.getString("status").contains(allocUuid));
                }
            }
        }
    }
}
