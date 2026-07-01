package service.sync;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import service.InvoiceMasterService;
import repository.InvoiceMasterRepository;
import utils.DBConnection;

public class CustomerAdvanceLifecycleTest {

    private String originalDbUrl;
    private String testDbUrl;

    @BeforeEach
    public void setUp() throws Exception {
        originalDbUrl = DBConnection.getUrl();
        testDbUrl = TestDatabaseHelper.createIsolatedDb("advance_lifecycle_test");
        DBConnection.setUrl(testDbUrl);
    }

    @AfterEach
    public void tearDown() {
        DBConnection.setUrl(originalDbUrl);
        TestDatabaseHelper.cleanupTestDir();
    }

    @Test
    public void testCustomerAdvanceLifecycle() throws Exception {
        InvoiceMasterRepository repo = new InvoiceMasterRepository();
        InvoiceMasterService service = new InvoiceMasterService();

        try (Connection con = DriverManager.getConnection(testDbUrl)) {
            // 1. Create a client
            String clientUuid = UUID.randomUUID().toString();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO clients (uuid, client_code, client_name, business_name) VALUES (?, 'C001', 'Advance Client', 'Advance Business')")) {
                ps.setString(1, clientUuid);
                ps.executeUpdate();
            }

            // 2. Create job
            String jobUuid = UUID.randomUUID().toString();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO jobs (uuid, client_uuid, job_code, job_title, status, amount) VALUES (?, ?, 'J001', 'Job One', 'Completed', 1000.0)")) {
                ps.setString(1, jobUuid);
                ps.setString(2, clientUuid);
                ps.executeUpdate();
            }

            // Create job item (printing item)
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO job_items (uuid, job_uuid, type, description, amount) VALUES (?, ?, 'PRINTING', 'Printing item', 1000.0)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, jobUuid);
                ps.executeUpdate();
            }

            // 3. Create a Proforma Invoice (type = "Performa Bills", document_series = "PROFORMA_INVOICE")
            // Proforma totals are computed without GST
            String invoiceUuid = UUID.randomUUID().toString();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO invoice_master (uuid, invoice_no, client_uuid, client_name, invoice_date, amount, paid_amount, due_amount, payment_status, status, document_series, type) " +
                    "VALUES (?, 'PI-001', ?, 'Advance Business', '2026-06-20', 1000.0, 0.0, 1000.0, 'UNPAID', 'DRAFT', 'PROFORMA_INVOICE', 'Performa Bills')")) {
                ps.setString(1, invoiceUuid);
                ps.setString(2, clientUuid);
                ps.executeUpdate();
            }

            // Map job to invoice
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO invoice_job_mapping (uuid, invoice_uuid, job_uuid) VALUES (?, ?, ?)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, invoiceUuid);
                ps.setString(3, jobUuid);
                ps.executeUpdate();
            }

            // Link invoice_uuid on jobs table
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE jobs SET invoice_uuid = ? WHERE uuid = ?")) {
                ps.setString(1, invoiceUuid);
                ps.setString(2, jobUuid);
                ps.executeUpdate();
            }

            // Recalculate totals to make sure draft totals are synced
            service.recalculateInvoiceTotals(con, invoiceUuid);

            // Fetch invoice to verify initial amounts
            var inv = repo.findByUuid(con, invoiceUuid);
            assertEquals(1000.0, inv.getAmount(), 0.001);
            assertEquals(0.0, inv.getPaidAmount(), 0.001);
            assertEquals(1000.0, inv.getDueAmount(), 0.001);
            assertEquals("UNPAID", inv.getPaymentStatus());

            // 4. Record partial payment (400.0) and full payment (600.0)
            String pay1Uuid = UUID.randomUUID().toString();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payments (uuid, client_uuid, amount, payment_date, method, type) VALUES (?, ?, 400.0, '2026-06-20', 'UPI', 'Payment')")) {
                ps.setString(1, pay1Uuid);
                ps.setString(2, clientUuid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount) VALUES (?, ?, ?, 400.0)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, pay1Uuid);
                ps.setString(3, invoiceUuid);
                ps.executeUpdate();
            }

            // Apply partial payment to invoice
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE invoice_master SET paid_amount = 400.0, due_amount = 600.0, payment_status = 'PARTIAL PAID' WHERE uuid = ?")) {
                ps.setString(1, invoiceUuid);
                ps.executeUpdate();
            }

            String pay2Uuid = UUID.randomUUID().toString();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payments (uuid, client_uuid, amount, payment_date, method, type) VALUES (?, ?, 600.0, '2026-06-20', 'Cash', 'Payment')")) {
                ps.setString(1, pay2Uuid);
                ps.setString(2, clientUuid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount) VALUES (?, ?, ?, 600.0)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, pay2Uuid);
                ps.setString(3, invoiceUuid);
                ps.executeUpdate();
            }

            // Apply full payment to invoice
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE invoice_master SET paid_amount = 1000.0, due_amount = 0.0, payment_status = 'PAID' WHERE uuid = ?")) {
                ps.setString(1, invoiceUuid);
                ps.executeUpdate();
            }

            // Verify invoice is paid
            inv = repo.findByUuid(con, invoiceUuid);
            assertEquals(1000.0, inv.getPaidAmount(), 0.001);
            assertEquals(0.0, inv.getDueAmount(), 0.001);
            assertEquals("PAID", inv.getPaymentStatus());

            // Ledger balance: should be 0.0 (Credit = 1000.0, Debit = 1000.0)
            double initialLedgerBalance = getLedgerBalance(con, clientUuid);
            assertEquals(0.0, initialLedgerBalance, 0.001);

            // No customer advance before canceling
            double initialAdvance = repo.getClientUnallocatedBalance(con, clientUuid);
            assertEquals(0.0, initialAdvance, 0.001);

            // 5. Remove all jobs so the invoice auto-cancels.
            // Under business rules, removing all jobs from a proforma marks jobs as unlinked, and recalculating auto-cancels.
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE jobs SET invoice_uuid = NULL WHERE invoice_uuid = ?")) {
                ps.setString(1, invoiceUuid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE invoice_job_mapping SET is_deleted = 1 WHERE invoice_uuid = ?")) {
                ps.setString(1, invoiceUuid);
                ps.executeUpdate();
            }

            // Trigger recalculation to auto-cancel and deallocate payments
            service.recalculateInvoiceTotals(con, invoiceUuid);

            // 6. Verify invoice status and payment status are correct
            inv = repo.findByUuid(con, invoiceUuid);
            assertEquals("CANCELLED", inv.getStatus());
            assertEquals("Void", inv.getPaymentStatus());
            assertEquals(0.0, inv.getAmount(), 0.001);
            assertEquals(0.0, inv.getPaidAmount(), 0.001);
            assertEquals(0.0, inv.getDueAmount(), 0.001);

            // 7. Verify all payment_allocations are deleted/deallocated (is_deleted = 1)
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COUNT(*) FROM payment_allocations WHERE invoice_uuid = ? AND COALESCE(is_deleted, 0) = 0")) {
                ps.setString(1, invoiceUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1));
                }
            }

            // 8. Verify Customer Advance equals the exact released amount (1000.0)
            double currentAdvance = repo.getClientUnallocatedBalance(con, clientUuid);
            assertEquals(1000.0, currentAdvance, 0.001);

            // 9. Create a new Proforma/GST invoice for the same client (1000.0)
            String newInvoiceUuid = UUID.randomUUID().toString();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO invoice_master (uuid, invoice_no, client_uuid, client_name, invoice_date, amount, paid_amount, due_amount, payment_status, status, document_series, type) " +
                    "VALUES (?, 'PI-002', ?, 'Advance Business', '2026-06-20', 1000.0, 0.0, 1000.0, 'UNPAID', 'DRAFT', 'PROFORMA_INVOICE', 'Performa Bills')")) {
                ps.setString(1, newInvoiceUuid);
                ps.setString(2, clientUuid);
                ps.executeUpdate();
            }

            // Apply the Customer Advance (from pay1 and pay2) to the new invoice.
            // Allocate pay1 (400.0) to new invoice
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount, sync_status, created_at, updated_at) VALUES (?, ?, ?, 400.0, 'PENDING', datetime('now'), datetime('now'))")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, pay1Uuid);
                ps.setString(3, newInvoiceUuid);
                ps.executeUpdate();
            }
            // Allocate pay2 (600.0) to new invoice
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount, sync_status, created_at, updated_at) VALUES (?, ?, ?, 600.0, 'PENDING', datetime('now'), datetime('now'))")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, pay2Uuid);
                ps.setString(3, newInvoiceUuid);
                ps.executeUpdate();
            }

            // Update new invoice paid/due amount
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE invoice_master SET paid_amount = 1000.0, due_amount = 0.0, payment_status = 'PAID', status = 'SENT TO CLIENT' WHERE uuid = ?")) {
                ps.setString(1, newInvoiceUuid);
                ps.executeUpdate();
            }

            // Verify advance is fully applied and unallocated balance is 0
            double remainingAdvance = repo.getClientUnallocatedBalance(con, clientUuid);
            assertEquals(0.0, remainingAdvance, 0.001);

            // Verify new invoice is fully paid
            var newInv = repo.findByUuid(con, newInvoiceUuid);
            assertEquals(1000.0, newInv.getPaidAmount(), 0.001);
            assertEquals(0.0, newInv.getDueAmount(), 0.001);
            assertEquals("PAID", newInv.getPaymentStatus());

            // 10. Ledger balance after workflow remains identical to before the workflow (0.0)
            double finalLedgerBalance = getLedgerBalance(con, clientUuid);
            assertEquals(initialLedgerBalance, finalLedgerBalance, 0.001);
        }
    }

    private double getLedgerBalance(Connection con, String clientUuid) throws Exception {
        // Compute ledger balance exactly as ClientLedgerController does: Credit - Debit (with Refund adjustment)
        String sql = """
            SELECT debit, credit, type FROM (
                SELECT 0 as debit, amount as credit, 'INVOICE' as type FROM invoice_master WHERE client_uuid = ?
                UNION ALL
                SELECT amount as debit, 0 as credit, type FROM payments WHERE client_uuid = ?
            )
        """;
        double totalDebit = 0.0;
        double totalCredit = 0.0;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, clientUuid);
            ps.setString(2, clientUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double debit = rs.getDouble("debit");
                    double credit = rs.getDouble("credit");
                    String type = rs.getString("type");
                    if (debit < 0) {
                        credit = Math.abs(debit);
                        debit = 0;
                    }
                    totalDebit += debit;
                    totalCredit += credit;
                }
            }
        }
        return totalCredit - totalDebit;
    }
}
