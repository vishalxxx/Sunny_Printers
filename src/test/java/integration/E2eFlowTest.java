package integration;
import service.sync.SyncReport;
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
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.supabase.SupabaseGate;
import api.supabase.SupabaseReachability;
import api.supabase.SupabaseEndpoints;
import model.Client;
import model.Supplier;
import model.Job;
import model.JobItem;
import repository.ClientRepository;
import service.SupplierService;
import repository.JobRepository;
import repository.JobItemRepository;
import utils.DBConnection;
import utils.AtomicDB;
import utils.ClientIdentifiers;
import utils.JobIdentifiers;
import com.google.gson.JsonObject;

@Tag("integration")
public class E2eFlowTest {

    private static String dbPath;
    private static FakeSupabaseRestClient fakeSupabase;

    @BeforeAll
    public static void setup() throws Exception {
        dbPath = TestDatabaseHelper.createIsolatedDb("E2eFlowTest");
        fakeSupabase = new FakeSupabaseRestClient();
        SupabaseGate.setOverrideClient(fakeSupabase);
        DBConnection.setTestDatabaseUrl(dbPath);
    }

    @AfterAll
    public static void tearDown() {
        TestDatabaseHelper.cleanupTestDir();
    }

    @BeforeEach
    public void resetFake() throws Exception {
        fakeSupabase.clear();
        fakeSupabase.setOnline(true);
        SupabaseReachability.invalidateCache();
        
        // Clear all transactional tables locally
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = OFF;");
            String[] tables = {
                "printing_items", "paper_items", "binding_items", "lamination_items", "ctp_items", 
                "job_items", "jobs", "payment_allocations", "payment_details", "payments", 
                "invoice_job_mapping", "invoice_master", "invoice_adjustments", 
                "invoice_additional_charges", "document_number_mappings", "billing", 
                "suppliers", "clients"
            };
            for (String table : tables) {
                stmt.execute("DELETE FROM " + table);
            }
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
    }

    @Test
    public void testOnlineE2EFlow() throws Exception {
        // --- ONLINE CASE ---
        fakeSupabase.setOnline(true);
        SupabaseReachability.invalidateCache();

        String clientUuid = ClientIdentifiers.newUuidV7String();
        String supplierUuid = ClientIdentifiers.newUuidV7String();
        String jobUuid = JobIdentifiers.newUuidString();
        String jobItemUuid = ClientIdentifiers.newUuidV7String();
        String invoiceUuid = ClientIdentifiers.newUuidV7String();
        String mappingUuid = ClientIdentifiers.newUuidV7String();
        String paymentUuid = ClientIdentifiers.newUuidV7String();
        String paymentAllocUuid = ClientIdentifiers.newUuidV7String();
        String refundUuid = ClientIdentifiers.newUuidV7String();
        String refundAllocUuid = ClientIdentifiers.newUuidV7String();
        String creditNoteUuid = ClientIdentifiers.newUuidV7String();
        String debitNoteUuid = ClientIdentifiers.newUuidV7String();

        String clientCode = "ONLINE-CL-001";
        String supplierCode = "ONLINE-SUP-001";
        String jobCode = "ONLINE/JOB/26-27/001";
        String invoiceNo = "ONLINE/INV/26-27/001";
        String cnNo = "ONLINE/CN/26-27/001";
        String dnNo = "ONLINE/DN/26-27/001";
        LocalDate today = LocalDate.now();

        // 1. Add Client
        ClientRepository clientRepo = new ClientRepository();
        Client c = new Client("Online Business", "John Online", "8888", "", "", "", "", "Delhi", "", "");
        c.setClientUuid(clientUuid);
        c.setClientCode(clientCode);
        c.setSyncStatus("PENDING");
        clientRepo.save(c);

        // 2. Add Supplier
        SupplierService supplierService = new SupplierService();
        Supplier s = new Supplier(supplierUuid, "Online Supplier", "CTP", "7777", "Supplier Address", "07GSTSUP");
        s.setSupplierCode(supplierCode);
        s.setbusinessName("Online Supplier Inc");
        supplierService.addSupplier(s);

        // 3. Add Job & Job Item
        AtomicDB.runVoid(con -> {
            Job job = new Job();
            job.setUuid(jobUuid);
            job.setClientUuid(clientUuid);
            job.setJobCode(jobCode);
            job.setJobTitle("Online print job");
            job.setJobDate(today);
            job.setStatus("Completed");
            new JobRepository().insertJob(con, job);
            exec(con, "UPDATE jobs SET sync_status='PENDING', updated_at=datetime('now') WHERE uuid=?", jobUuid);

            JobItem item = new JobItem();
            item.setUuid(jobItemUuid);
            item.setJobUuid(jobUuid);
            item.setType("PRINTING");
            item.setDescription("Online printing lines");
            item.setAmount(1000.0);
            item.setSortOrder(1);
            new JobItemRepository().save(con, item);
            JobRepository.syncAmountFromJobItems(con, jobUuid);
        });

        // 4. Add Invoice
        AtomicDB.runVoid(con -> {
            exec(con, "INSERT INTO invoice_master (uuid, invoice_no, client_uuid, client_name, invoice_date, period_from, period_to, amount, paid_amount, due_amount, payment_status, type, status, is_void, document_series, sync_status, sync_version, is_deleted, is_active, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,0,'GST_INVOICE','PENDING',1,0,1,datetime('now'),datetime('now'))",
                    invoiceUuid, invoiceNo, clientUuid, "Online Business", today.toString(),
                    today.withDayOfMonth(1).toString(), today.toString(), 1000.0, 0.0, 1000.0, "UNPAID", "DATE_RANGE", "FINAL");
            exec(con, "UPDATE jobs SET invoice_uuid=?, status='Invoiced', sync_status='PENDING', sync_version=COALESCE(sync_version,0)+1, updated_at=datetime('now') WHERE uuid=?", invoiceUuid, jobUuid);
            exec(con, "INSERT INTO invoice_job_mapping (uuid, invoice_uuid, job_uuid, sync_status, created_at, updated_at) VALUES (?,?,?,'PENDING',datetime('now'),datetime('now'))", mappingUuid, invoiceUuid, jobUuid);
        });

        // 5. Add Payment & Allocation (type = Payment)
        AtomicDB.runVoid(con -> {
            exec(con, "INSERT INTO payments (uuid, client_uuid, amount, payment_date, method, type, sync_status, sync_version, is_deleted, is_active, created_at, updated_at) VALUES (?,?,?,?,'Cash','Payment','PENDING',1,0,1,datetime('now'),datetime('now'))", paymentUuid, clientUuid, 600.0, today.toString());
            exec(con, "INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount, sync_status, created_at, updated_at) VALUES (?,?,?,?,'PENDING',datetime('now'),datetime('now'))", paymentAllocUuid, paymentUuid, invoiceUuid, 600.0);
            exec(con, "UPDATE invoice_master SET paid_amount=600.0, due_amount=400.0, payment_status='PARTIAL PAID', sync_status='PENDING', updated_at=datetime('now') WHERE uuid=?", invoiceUuid);
        });

        // 6. Add Refund & Allocation (type = Refund)
        AtomicDB.runVoid(con -> {
            exec(con, "INSERT INTO payments (uuid, client_uuid, amount, payment_date, method, type, sync_status, sync_version, is_deleted, is_active, created_at, updated_at) VALUES (?,?,?,?,'Cash','Refund','PENDING',1,0,1,datetime('now'),datetime('now'))", refundUuid, clientUuid, -100.0, today.toString());
            exec(con, "INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount, sync_status, created_at, updated_at) VALUES (?,?,?,?,'PENDING',datetime('now'),datetime('now'))", refundAllocUuid, refundUuid, invoiceUuid, -100.0);
            exec(con, "UPDATE invoice_master SET paid_amount=500.0, due_amount=500.0, payment_status='PARTIAL PAID', sync_status='PENDING', updated_at=datetime('now') WHERE uuid=?", invoiceUuid);
        });

        // 7. Add Credit Note & Debit Note (invoice_adjustments)
        AtomicDB.runVoid(con -> {
            exec(con, "INSERT INTO invoice_adjustments (uuid, invoice_uuid, type, note_no, amount, reason, date, sync_status, sync_version, is_deleted, is_active, created_at, updated_at) VALUES (?,?,?,?,?,?,?,'PENDING',1,0,1,datetime('now'),datetime('now'))",
                    creditNoteUuid, invoiceUuid, "Credit Note", cnNo, 50.0, "CN reason", today.toString());
            exec(con, "INSERT INTO invoice_adjustments (uuid, invoice_uuid, type, note_no, amount, reason, date, sync_status, sync_version, is_deleted, is_active, created_at, updated_at) VALUES (?,?,?,?,?,?,?,'PENDING',1,0,1,datetime('now'),datetime('now'))",
                    debitNoteUuid, invoiceUuid, "Debit Note", dnNo, 25.0, "DN reason", today.toString());
        });

        // Call Sync manually to ensure all remaining pending items are synchronized
        SyncReport report = UniversalSyncEngine.syncAllPending();
        assertNotNull(report);
        assertEquals(0, report.failures, "Should be no sync failures");

        // Verify local state updated to SYNCED
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT sync_status FROM clients WHERE uuid = ?")) {
            ps.setString(1, clientUuid);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("SYNCED", rs.getString(1));
            }
        }

        // Verify remote FakeSupabase contains all values
        assertTrue(hasRemoteRecord(SupabaseEndpoints.CLIENTS, clientUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.SUPPLIERS, supplierUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.JOBS, jobUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.JOB_ITEMS, jobItemUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.INVOICE_MASTER, invoiceUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.PAYMENTS, paymentUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.PAYMENT_ALLOCATIONS, paymentAllocUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.PAYMENTS, refundUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.PAYMENT_ALLOCATIONS, refundAllocUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.INVOICE_ADJUSTMENTS, creditNoteUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.INVOICE_ADJUSTMENTS, debitNoteUuid));
    }

    @Test
    public void testOfflineThenSyncE2EFlow() throws Exception {
        // --- OFFLINE THEN SYNC CASE ---
        fakeSupabase.setOnline(false);
        SupabaseReachability.invalidateCache();

        String clientUuid = ClientIdentifiers.newUuidV7String();
        String supplierUuid = ClientIdentifiers.newUuidV7String();
        String jobUuid = JobIdentifiers.newUuidString();
        String jobItemUuid = ClientIdentifiers.newUuidV7String();
        String invoiceUuid = ClientIdentifiers.newUuidV7String();
        String mappingUuid = ClientIdentifiers.newUuidV7String();
        String paymentUuid = ClientIdentifiers.newUuidV7String();
        String paymentAllocUuid = ClientIdentifiers.newUuidV7String();
        String refundUuid = ClientIdentifiers.newUuidV7String();
        String refundAllocUuid = ClientIdentifiers.newUuidV7String();
        String creditNoteUuid = ClientIdentifiers.newUuidV7String();
        String debitNoteUuid = ClientIdentifiers.newUuidV7String();

        String clientCode = "OFFLINE-CL-001";
        String supplierCode = "OFFLINE-SUP-001";
        String jobCode = "OFFLINE/JOB/26-27/001";
        String invoiceNo = "OFFLINE/INV/26-27/001";
        String cnNo = "OFFLINE/CN/26-27/001";
        String dnNo = "OFFLINE/DN/26-27/001";
        LocalDate today = LocalDate.now();

        // 1. Add Client offline
        ClientRepository clientRepo = new ClientRepository();
        Client c = new Client("Offline Business", "John Offline", "8888", "", "", "", "", "Delhi", "", "");
        c.setClientUuid(clientUuid);
        c.setClientCode(clientCode);
        c.setSyncStatus("PENDING");
        clientRepo.save(c);

        // 2. Add Supplier offline
        SupplierService supplierService = new SupplierService();
        Supplier s = new Supplier(supplierUuid, "Offline Supplier", "CTP", "7777", "Supplier Address", "07GSTSUP");
        s.setSupplierCode(supplierCode);
        s.setbusinessName("Offline Supplier Inc");
        supplierService.addSupplier(s);

        // 3. Add Job & Job Item offline
        AtomicDB.runVoid(con -> {
            Job job = new Job();
            job.setUuid(jobUuid);
            job.setClientUuid(clientUuid);
            job.setJobCode(jobCode);
            job.setJobTitle("Offline print job");
            job.setJobDate(today);
            job.setStatus("Completed");
            new JobRepository().insertJob(con, job);
            exec(con, "UPDATE jobs SET sync_status='PENDING', updated_at=datetime('now') WHERE uuid=?", jobUuid);

            JobItem item = new JobItem();
            item.setUuid(jobItemUuid);
            item.setJobUuid(jobUuid);
            item.setType("PRINTING");
            item.setDescription("Offline printing lines");
            item.setAmount(1000.0);
            item.setSortOrder(1);
            new JobItemRepository().save(con, item);
            JobRepository.syncAmountFromJobItems(con, jobUuid);
        });

        // 4. Add Invoice offline
        AtomicDB.runVoid(con -> {
            exec(con, "INSERT INTO invoice_master (uuid, invoice_no, client_uuid, client_name, invoice_date, period_from, period_to, amount, paid_amount, due_amount, payment_status, type, status, is_void, document_series, sync_status, sync_version, is_deleted, is_active, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,0,'GST_INVOICE','PENDING',1,0,1,datetime('now'),datetime('now'))",
                    invoiceUuid, invoiceNo, clientUuid, "Offline Business", today.toString(),
                    today.withDayOfMonth(1).toString(), today.toString(), 1000.0, 0.0, 1000.0, "UNPAID", "DATE_RANGE", "FINAL");
            exec(con, "UPDATE jobs SET invoice_uuid=?, status='Invoiced', sync_status='PENDING', sync_version=COALESCE(sync_version,0)+1, updated_at=datetime('now') WHERE uuid=?", invoiceUuid, jobUuid);
            exec(con, "INSERT INTO invoice_job_mapping (uuid, invoice_uuid, job_uuid, sync_status, created_at, updated_at) VALUES (?,?,?,'PENDING',datetime('now'),datetime('now'))", mappingUuid, invoiceUuid, jobUuid);
        });

        // 5. Add Payment & Allocation offline
        AtomicDB.runVoid(con -> {
            exec(con, "INSERT INTO payments (uuid, client_uuid, amount, payment_date, method, type, sync_status, sync_version, is_deleted, is_active, created_at, updated_at) VALUES (?,?,?,?,'Cash','Payment','PENDING',1,0,1,datetime('now'),datetime('now'))", paymentUuid, clientUuid, 600.0, today.toString());
            exec(con, "INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount, sync_status, created_at, updated_at) VALUES (?,?,?,?,'PENDING',datetime('now'),datetime('now'))", paymentAllocUuid, paymentUuid, invoiceUuid, 600.0);
            exec(con, "UPDATE invoice_master SET paid_amount=600.0, due_amount=400.0, payment_status='PARTIAL PAID', sync_status='PENDING', updated_at=datetime('now') WHERE uuid=?", invoiceUuid);
        });

        // 6. Add Refund & Allocation offline
        AtomicDB.runVoid(con -> {
            exec(con, "INSERT INTO payments (uuid, client_uuid, amount, payment_date, method, type, sync_status, sync_version, is_deleted, is_active, created_at, updated_at) VALUES (?,?,?,?,'Cash','Refund','PENDING',1,0,1,datetime('now'),datetime('now'))", refundUuid, clientUuid, -100.0, today.toString());
            exec(con, "INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount, sync_status, created_at, updated_at) VALUES (?,?,?,?,'PENDING',datetime('now'),datetime('now'))", refundAllocUuid, refundUuid, invoiceUuid, -100.0);
            exec(con, "UPDATE invoice_master SET paid_amount=500.0, due_amount=500.0, payment_status='PARTIAL PAID', sync_status='PENDING', updated_at=datetime('now') WHERE uuid=?", invoiceUuid);
        });

        // 7. Add Credit Note & Debit Note offline
        AtomicDB.runVoid(con -> {
            exec(con, "INSERT INTO invoice_adjustments (uuid, invoice_uuid, type, note_no, amount, reason, date, sync_status, sync_version, is_deleted, is_active, created_at, updated_at) VALUES (?,?,?,?,?,?,?,'PENDING',1,0,1,datetime('now'),datetime('now'))",
                    creditNoteUuid, invoiceUuid, "Credit Note", cnNo, 50.0, "CN reason", today.toString());
            exec(con, "INSERT INTO invoice_adjustments (uuid, invoice_uuid, type, note_no, amount, reason, date, sync_status, sync_version, is_deleted, is_active, created_at, updated_at) VALUES (?,?,?,?,?,?,?,'PENDING',1,0,1,datetime('now'),datetime('now'))",
                    debitNoteUuid, invoiceUuid, "Debit Note", dnNo, 25.0, "DN reason", today.toString());
        });

        // Verify local state is PENDING, and fakeSupabase is empty
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT sync_status FROM clients WHERE uuid = ?")) {
            ps.setString(1, clientUuid);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("PENDING", rs.getString(1));
            }
        }
        assertFalse(hasRemoteRecord(SupabaseEndpoints.CLIENTS, clientUuid));

        // Now recover: Go Online
        fakeSupabase.setOnline(true);
        SupabaseReachability.invalidateCache();

        // Call Sync
        SyncReport report = UniversalSyncEngine.syncAllPending();
        assertNotNull(report);
        assertEquals(0, report.failures, "Should be no sync failures after recovery");

        // Verify local state updated to SYNCED
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT sync_status FROM clients WHERE uuid = ?")) {
            ps.setString(1, clientUuid);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("SYNCED", rs.getString(1));
            }
        }

        // Verify remote FakeSupabase contains all values now
        assertTrue(hasRemoteRecord(SupabaseEndpoints.CLIENTS, clientUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.SUPPLIERS, supplierUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.JOBS, jobUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.JOB_ITEMS, jobItemUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.INVOICE_MASTER, invoiceUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.PAYMENTS, paymentUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.PAYMENT_ALLOCATIONS, paymentAllocUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.PAYMENTS, refundUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.PAYMENT_ALLOCATIONS, refundAllocUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.INVOICE_ADJUSTMENTS, creditNoteUuid));
        assertTrue(hasRemoteRecord(SupabaseEndpoints.INVOICE_ADJUSTMENTS, debitNoteUuid));
    }

    private static void exec(Connection con, String sql, Object... params) throws Exception {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                Object p = params[i];
                if (p instanceof String s) {
                    ps.setString(i + 1, s);
                } else if (p instanceof Double d) {
                    ps.setDouble(i + 1, d);
                } else {
                    ps.setObject(i + 1, p);
                }
            }
            ps.executeUpdate();
        }
    }

    private boolean hasRemoteRecord(SupabaseEndpoints endpoint, String uuid) {
        List<JsonObject> rows = fakeSupabase.getTableData(endpoint);
        if (rows == null) return false;
        for (JsonObject row : rows) {
            if (row.has("uuid") && uuid.equals(row.get("uuid").getAsString())) {
                return true;
            }
        }
        return false;
    }
}

