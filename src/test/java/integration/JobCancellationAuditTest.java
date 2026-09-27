package integration;

import org.junit.jupiter.api.Tag;
import utils.FakeSupabaseRestClient;
import utils.TestDatabaseHelper;
import utils.CleanupUtility;
import utils.ClearAllExceptSettings;
import utils.ClearRemoteDatabase;
import utils.MetricsExtractor;
import utils.SchemaAndSyncChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import service.InvoiceMasterService;
import utils.DBConnection;

@Tag("integration")
public class JobCancellationAuditTest {

    private String testDbUrl;

    @BeforeEach
    public void setUp() throws Exception {
        testDbUrl = TestDatabaseHelper.createIsolatedDb("audit_test");
        DBConnection.setTestDatabaseUrl(testDbUrl);
    }

    @AfterEach
    public void tearDown() {
        DBConnection.clearTestDatabaseUrl();
        TestDatabaseHelper.cleanupTestDir();
    }

    @Test
    public void testJobCancellationAuditingAndReallocation() throws Exception {
        try (Connection con = DriverManager.getConnection(testDbUrl)) {
            // 1. Create a client
            String clientUuid = UUID.randomUUID().toString();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO clients (uuid, client_code, client_name, business_name) VALUES (?, 'C001', 'Test Client', 'Test Business')")) {
                ps.setString(1, clientUuid);
                ps.executeUpdate();
            }

            // 2. Create 2 jobs
            String job1Uuid = UUID.randomUUID().toString();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO jobs (uuid, client_uuid, job_code, job_title, status, amount) VALUES (?, ?, 'J001', 'Job One', 'Completed', 1000.0)")) {
                ps.setString(1, job1Uuid);
                ps.setString(2, clientUuid);
                ps.executeUpdate();
            }

            String job2Uuid = UUID.randomUUID().toString();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO jobs (uuid, client_uuid, job_code, job_title, status, amount) VALUES (?, ?, 'J002', 'Job Two', 'Completed', 1000.0)")) {
                ps.setString(1, job2Uuid);
                ps.setString(2, clientUuid);
                ps.executeUpdate();
            }

            // 3. Create job items for calculations
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO job_items (uuid, job_uuid, type, description, amount) VALUES (?, ?, 'PRINTING', 'Printing item', 1000.0)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, job1Uuid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO job_items (uuid, job_uuid, type, description, amount) VALUES (?, ?, 'PRINTING', 'Printing item', 1000.0)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, job2Uuid);
                ps.executeUpdate();
            }

            // 4. Create an invoice
            String invoiceUuid = UUID.randomUUID().toString();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO invoice_master (uuid, invoice_no, client_uuid, client_name, invoice_date, amount, paid_amount, due_amount, payment_status, status, document_series, type) "
                            +
                            "VALUES (?, 'INV-001', ?, 'Test Business', '2026-06-16', 2360.0, 1180.0, 1180.0, 'PARTIAL PAID', 'DRAFT', 'GST_INVOICE', 'GST')")) {
                ps.setString(1, invoiceUuid);
                ps.setString(2, clientUuid);
                ps.executeUpdate();
            }

            // Map jobs to the invoice
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO invoice_job_mapping (uuid, invoice_uuid, job_uuid) VALUES (?, ?, ?)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, invoiceUuid);
                ps.setString(3, job1Uuid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO invoice_job_mapping (uuid, invoice_uuid, job_uuid) VALUES (?, ?, ?)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, invoiceUuid);
                ps.setString(3, job2Uuid);
                ps.executeUpdate();
            }

            // Link invoice_uuid on jobs table
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE jobs SET invoice_uuid = ? WHERE uuid IN (?, ?)")) {
                ps.setString(1, invoiceUuid);
                ps.setString(2, job1Uuid);
                ps.setString(3, job2Uuid);
                ps.executeUpdate();
            }

            // Record a payment allocation of 1180.0 (paid half the invoice)
            String paymentUuid = UUID.randomUUID().toString();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payments (uuid, client_uuid, amount, payment_date, method, type) VALUES (?, ?, 1180.0, '2026-06-16', 'Cash', 'Payment')")) {
                ps.setString(1, paymentUuid);
                ps.setString(2, clientUuid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount) VALUES (?, ?, ?, 1180.0)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, paymentUuid);
                ps.setString(3, invoiceUuid);
                ps.executeUpdate();
            }

            // 5. Invoke service method to cancel job 1
            InvoiceMasterService service = new InvoiceMasterService();
            service.reallocatePaymentsOnJobCancellation(con, invoiceUuid, List.of(job1Uuid), "Customer request",
                    "AdminUser");

            // 6. Verify audit table entry exists and is correct
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT * FROM job_cancellation_audit WHERE job_uuid = ?")) {
                ps.setString(1, job1Uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Audit record should exist for cancelled job");
                    assertEquals("Customer request", rs.getString("cancellation_reason"));
                    assertEquals("AdminUser", rs.getString("cancelled_by"));
                    assertEquals(invoiceUuid, rs.getString("original_invoice_uuid"));
                    assertEquals(1000.0, rs.getDouble("original_job_amount"), 0.001);
                    assertEquals(180.0, rs.getDouble("original_gst_amount"), 0.001);
                    // job1 was allocated 590 (half of 1180 paid). This is reallocated to job2
                    // (which was unpaid).
                    assertEquals(590.0, rs.getDouble("reallocated_amount"), 0.001);
                    assertEquals(0.0, rs.getDouble("refund_pending_amount"), 0.001);
                }
            }
        }
    }
}
