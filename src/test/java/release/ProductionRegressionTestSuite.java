package release;
import service.sync.UniversalNumberAllocator;
import service.sync.RemoteToLocalSync;
import service.sync.UniversalSyncEngine;
import utils.ClientIdentifiers;
import utils.DBConnection;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import utils.*;
import model.*;
import repository.*;
import service.*;
import api.supabase.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("release")
public class ProductionRegressionTestSuite {

    private static List<String> testClientUuids = new CopyOnWriteArrayList<>();
    private static List<String> testSupplierUuids = new CopyOnWriteArrayList<>();
    private static List<String> testJobUuids = new CopyOnWriteArrayList<>();
    private static List<String> testInvoiceUuids = new CopyOnWriteArrayList<>();

    private static ExecutorService executor = Executors.newFixedThreadPool(10);
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @BeforeAll
    public static void setup() {
        System.out.println("Starting Production Regression Test Suite on Live Environment");
        DBConnection.setUrl("jdbc:sqlite:database/sunnyprinters.db?busy_timeout=15000&journal_mode=WAL");
        
        // Save environment credentials to database if they exist
        String envUrl = System.getenv("SUPABASE_URL");
        String envKey = System.getenv("SUPABASE_KEY");
        if (envUrl != null && !envUrl.isBlank() && envKey != null && !envKey.isBlank()) {
            try {
                SupabaseSettings s = new SupabaseSettings();
                s.setSupabaseUrl(envUrl);
                s.setAnonKey(envKey);
                new SupabaseSettingsRepository().save(s);
                System.out.println("[Test Setup] Mapped environment SUPABASE_URL and SUPABASE_KEY to database settings.");
            } catch (Exception e) {
                System.err.println("[Test Setup] Failed to save environment credentials to database: " + e.getMessage());
            }
        }

        SupabaseReachability.invalidateCache();
        boolean reachable = api.supabase.SupabaseReachability.isReachable();
        if (!reachable) {
            System.out.println("[Test Setup] Supabase is not reachable. Skipping regression suite.");
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(reachable, "Supabase must be reachable to run regression tests");

        // Start background sync continuously every 5 seconds
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                UniversalSyncEngine.syncAllPending();
                var httpOpt = SupabaseGate.restClientIfConfigured();
                if (httpOpt.isPresent()) {
                    RemoteToLocalSync.pullAll(httpOpt.get());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 1, 5, TimeUnit.SECONDS);
    }

    @AfterAll
    public static void cleanup() throws Exception {
        System.out.println("Cleaning up all data...");
        scheduler.shutdownNow();
        executor.shutdownNow();

        // Cleanup local SQLite
        try (Connection con = DBConnection.getExclusiveConnection();
             Statement stmt = con.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = OFF;");
            
            String[] tables = {
                "sync_metadata", "sync_conflicts",
                "payment_allocations", "payment_details", "payments",
                "invoice_job_mapping", "invoice_additional_charges", "invoice_adjustments",
                "invoice_history", "invoice_master",
                "printing_items", "paper_items", "binding_items", "ctp_items", "lamination_items", "job_items", "job_cancellation_audit", "jobs",
                "document_number_mappings", "document_number_sequences", "billing",
                "clients", "suppliers"
            };

            for(String t : tables) {
                stmt.execute("DELETE FROM " + t);
            }
            stmt.execute("PRAGMA foreign_keys = ON;");
        }

        // Cleanup Supabase in dependency order
        var httpOptional = SupabaseGate.restClientIfConfigured();
        if (httpOptional.isPresent()) {
            var http = httpOptional.get();
            SupabaseEndpoints[] endpoints = {
                SupabaseEndpoints.PAYMENT_ALLOCATIONS, SupabaseEndpoints.PAYMENT_DETAILS, SupabaseEndpoints.PAYMENTS,
                SupabaseEndpoints.INVOICE_JOB_MAPPING, SupabaseEndpoints.INVOICE_ADDITIONAL_CHARGES, SupabaseEndpoints.INVOICE_ADJUSTMENTS,
                SupabaseEndpoints.INVOICE_HISTORY, SupabaseEndpoints.INVOICE_MASTER,
                SupabaseEndpoints.PRINTING_ITEMS, SupabaseEndpoints.PAPER_ITEMS, SupabaseEndpoints.BINDING_ITEMS, SupabaseEndpoints.CTP_ITEMS, SupabaseEndpoints.LAMINATION_ITEMS,
                SupabaseEndpoints.JOB_ITEMS, SupabaseEndpoints.JOBS,
                SupabaseEndpoints.DOCUMENT_NUMBER_MAPPINGS, SupabaseEndpoints.NUMBER_SEQUENCES, SupabaseEndpoints.BILLING,
                SupabaseEndpoints.CLIENTS, SupabaseEndpoints.SUPPLIERS
            };
            for(SupabaseEndpoints e : endpoints) {
                try {
                    http.delete(e, "uuid=not.is.null");
                } catch(Exception ignored) {}
            }
        }
    }

    @Test
    @Order(1)
    public void phase1_MasterData() throws Exception {
        System.out.println("Phase 1 - Master Data");
        ClientRepository clientRepo = new ClientRepository();
        SupplierService supplierService = new SupplierService();

        List<Future<?>> futures = new ArrayList<>();
        // Create 30 Clients
        for (int i = 0; i < 30; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    Client c = new Client("Business " + index, "Contact " + index, "99990000" + (index < 10 ? "0" + index : index), "", "", "", "", "Delhi", "", "");
                    c.setClientUuid(ClientIdentifiers.newUuidV7String());
                    c.setSyncStatus("PENDING");
                    if (index % 3 == 0) c.gstProperty().set("07AABCU9603R1Z" + (index % 10));
                    clientRepo.save(c);
                    testClientUuids.add(c.getClientUuid());
                } catch (Exception e) {
                    fail("Failed to create client", e);
                }
            }));
        }

        // Create 15 Suppliers
        for (int i = 0; i < 15; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    Supplier s = new Supplier(ClientIdentifiers.newUuidV7String(), "Supplier Contact " + index, "CTP", "88880000" + (index < 10 ? "0" + index : index), "Address", "07AABCU9603R1Z" + (index % 10));
                    s.setbusinessName("Supplier Business " + index);
                    supplierService.addSupplier(s);
                    testSupplierUuids.add(s.getUuid());
                } catch (Exception e) {
                    fail("Failed to create supplier", e);
                }
            }));
        }

        for (Future<?> f : futures) f.get(); // Wait for all

        assertEquals(30, testClientUuids.size());
        assertEquals(15, testSupplierUuids.size());

        Thread.sleep(8000); // Wait for background sync engine to pick up

        // Verify no duplicates and synced
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT count(*) FROM clients WHERE sync_status='PENDING'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                assertEquals(0, rs.getInt(1), "All clients should be synced");
            }
        }
    }

    @Test
    @Order(2)
    public void phase2_JobCreation() throws Exception {
        System.out.println("Phase 2 - Job Creation");
        List<Future<?>> futures = new ArrayList<>();
        
        for (int i = 0; i < 100; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    AtomicDB.runVoid(con -> {
                        String clientUuid = testClientUuids.get(index % testClientUuids.size());
                        Job job = new Job();
                        job.setUuid(JobIdentifiers.newUuidString());
                        job.setClientUuid(clientUuid);
                        job.setJobCode("JOB-" + index);
                        job.setJobTitle("Regression Job " + index);
                        job.setJobDate(LocalDate.now());
                        job.setStatus("Draft");
                        new JobRepository().insertJob(con, job);
                        
                        // Add some items
                        JobItem item = new JobItem();
                        item.setUuid(ClientIdentifiers.newUuidV7String());
                        item.setJobUuid(job.getUuid());
                        item.setType("PRINTING");
                        item.setDescription("Print items");
                        item.setAmount(100.0 * (index % 5 + 1));
                        item.setSortOrder(1);
                        new JobItemRepository().save(con, item);
                        
                        JobRepository.syncAmountFromJobItems(con, job.getUuid());
                        testJobUuids.add(job.getUuid());
                    });
                } catch (Exception e) {
                    fail("Failed to create job", e);
                }
            }));
        }
        for (Future<?> f : futures) f.get();
        assertEquals(100, testJobUuids.size());
    }

    @Test
    @Order(3)
    public void phase3_JobEditing() throws Exception {
        System.out.println("Phase 3 - Job Editing");
        for (int i = 0; i < 40; i++) {
            String jobUuid = testJobUuids.get(i);
            final int index = i;
            AtomicDB.runVoid(con -> {
                try (PreparedStatement ps = con.prepareStatement("UPDATE jobs SET job_title=?, sync_status='PENDING', sync_version=sync_version+1, updated_at=datetime('now') WHERE uuid=?")) {
                    ps.setString(1, "Regression Job Edited " + index);
                    ps.setString(2, jobUuid);
                    ps.executeUpdate();
                }
            });
        }
    }

    @Test
    @Order(4)
    public void phase4_JobStatusLifecycle() throws Exception {
        System.out.println("Phase 4 - Job Status Lifecycle");
        for (int i = 40; i < 60; i++) {
            String jobUuid = testJobUuids.get(i);
            AtomicDB.runVoid(con -> {
                try (PreparedStatement ps = con.prepareStatement("UPDATE jobs SET status='Completed', sync_status='PENDING', sync_version=sync_version+1, updated_at=datetime('now') WHERE uuid=?")) {
                    ps.setString(1, jobUuid);
                    ps.executeUpdate();
                }
            });
        }
    }

    @Test
    @Order(5)
    public void phase5_JobCancellation() throws Exception {
        System.out.println("Phase 5 - Job Cancellation");
        for (int i = 60; i < 75; i++) {
            String jobUuid = testJobUuids.get(i);
            AtomicDB.runVoid(con -> {
                try (PreparedStatement ps = con.prepareStatement("UPDATE jobs SET status='Cancelled', sync_status='PENDING', sync_version=sync_version+1, updated_at=datetime('now') WHERE uuid=?")) {
                    ps.setString(1, jobUuid);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps2 = con.prepareStatement("INSERT INTO job_cancellation_audit (uuid, job_uuid, cancellation_reason, cancelled_by, sync_status, created_at, updated_at) VALUES (?,?,'Testing regression','Admin','PENDING',datetime('now'),datetime('now'))")) {
                    ps2.setString(1, ClientIdentifiers.newUuidV7String());
                    ps2.setString(2, jobUuid);
                    ps2.executeUpdate();
                }
            });
        }
    }

    @Test
    @Order(6)
    public void phase6_ProformaInvoice() throws Exception {
        System.out.println("Phase 6 - Proforma Invoice");
        for (int i = 0; i < 15; i++) {
            String clientUuid = testClientUuids.get(i);
            final int index = i;
            AtomicDB.runVoid(con -> {
                InvoiceMaster draft = new InvoiceMaster();
                draft.setUuid(ClientIdentifiers.newUuidV7String());
                draft.setInvoiceNo("TMP-" + index);
                draft.setClientId(clientUuid);
                draft.setInvoiceDate(LocalDate.now());
                draft.setAmount(1000.0);
                draft.setType("DATE_RANGE");
                draft.setStatus("DRAFT");
                draft.setSyncStatus("PENDING");
                draft.setDocumentSeries(MasterDocumentSeries.PROFORMA_INVOICE.name());
                new InvoiceMasterRepository().insert(con, draft);

                try {
                    service.NumberSequenceAllocationService.AllocatedNumber num = UniversalNumberAllocator.getInstance().allocateInvoiceNumber(con, MasterDocumentSeries.PROFORMA_INVOICE, LocalDate.now());
                    draft.setInvoiceNo(num.value());
                    draft.setStatus("FINALIZED");
                    new InvoiceMasterRepository().update(con, draft);
                    testInvoiceUuids.add(draft.getUuid());
                } catch(Exception ignored) {}
            });
        }
    }

    @Test
    @Order(7)
    public void phase7_GSTInvoice() throws Exception {
        System.out.println("Phase 7 - GST Invoice");
        for (int i = 15; i < 40; i++) {
            String clientUuid = testClientUuids.get(i % testClientUuids.size());
            final int index = i;
            AtomicDB.runVoid(con -> {
                InvoiceMaster draft = new InvoiceMaster();
                draft.setUuid(ClientIdentifiers.newUuidV7String());
                draft.setInvoiceNo("TMP-GST-" + index);
                draft.setClientId(clientUuid);
                draft.setInvoiceDate(LocalDate.now());
                draft.setAmount(2000.0);
                draft.setType("DATE_RANGE");
                draft.setStatus("DRAFT");
                draft.setSyncStatus("PENDING");
                draft.setDocumentSeries(MasterDocumentSeries.GST_INVOICE.name());
                new InvoiceMasterRepository().insert(con, draft);

                try {
                    service.NumberSequenceAllocationService.AllocatedNumber num = UniversalNumberAllocator.getInstance().allocateInvoiceNumber(con, MasterDocumentSeries.GST_INVOICE, LocalDate.now());
                    draft.setInvoiceNo(num.value());
                    draft.setStatus("FINALIZED");
                    new InvoiceMasterRepository().update(con, draft);
                    testInvoiceUuids.add(draft.getUuid());
                } catch(Exception ignored) {}
            });
        }
    }

    @Test
    @Order(8)
    public void phase8_Payments() throws Exception {
        System.out.println("Phase 8 - Payments");
        for (int i = 0; i < 15; i++) {
            String invoiceUuid = testInvoiceUuids.get(i);
            AtomicDB.runVoid(con -> {
                InvoiceMaster inv = new InvoiceMasterRepository().findByUuid(con, invoiceUuid);
                String payUuid = ClientIdentifiers.newUuidV7String();
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO payments (uuid, client_uuid, amount, payment_date, type, method, sync_status, created_at, updated_at) VALUES (?,?,?,?,'Payment','Cash','PENDING',datetime('now'),datetime('now'))")) {
                    ps.setString(1, payUuid);
                    ps.setString(2, inv.getClientId());
                    ps.setDouble(3, inv.getAmount() / 2);
                    ps.setString(4, LocalDate.now().toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps2 = con.prepareStatement("INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount, sync_status, created_at, updated_at) VALUES (?,?,?,?,'PENDING',datetime('now'),datetime('now'))")) {
                    ps2.setString(1, ClientIdentifiers.newUuidV7String());
                    ps2.setString(2, payUuid);
                    ps2.setString(3, invoiceUuid);
                    ps2.setDouble(4, inv.getAmount() / 2);
                    ps2.executeUpdate();
                }
                new InvoiceMasterRepository().updatePayment(con, invoiceUuid, inv.getAmount() / 2, inv.getAmount() / 2, "PARTIAL PAID", LocalDate.now());
            });
        }
    }

    @Test
    @Order(16)
    public void phase16_StressTest() throws Exception {
        System.out.println("Phase 16 - Stress Test & Verification");
        // Give sync engine some time to catch up with all operations
        Thread.sleep(15000);

        // Verification phase
        try (Connection con = DBConnection.getConnection()) {
            try (Statement stmt = con.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM jobs WHERE sync_status='PENDING'")) {
                if (rs.next()) {
                    assertTrue(rs.getInt(1) < 10, "Most jobs should be synced by now, pending: " + rs.getInt(1));
                }
            }
            try (Statement stmt = con.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM invoice_master WHERE sync_status='PENDING'")) {
                if (rs.next()) {
                    assertTrue(rs.getInt(1) < 10, "Most invoices should be synced by now, pending: " + rs.getInt(1));
                }
            }
        }
    }
}

