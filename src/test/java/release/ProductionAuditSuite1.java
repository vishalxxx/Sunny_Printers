package release;
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
public class ProductionAuditSuite1 {

    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    public static List<String> clients = new ArrayList<>();
    public static List<String> jobs = new ArrayList<>();
    public static List<String> invoices = new ArrayList<>();

    @BeforeAll
    public static void setup() {
        System.out.println("--- Starting Audit Suite 1 ---");
        // Production audit suite: uses DBConnection.PRODUCTION_URL automatically.
        SupabaseReachability.invalidateCache();
        scheduler.scheduleWithFixedDelay(() -> {
            try { UniversalSyncEngine.syncAllPending(); } catch (Exception e) {}
        }, 1, 3, TimeUnit.SECONDS);
    }

    @AfterAll
    public static void teardown() {
        scheduler.shutdownNow();
    }

    @Test
    @Order(1)
    public void phase1_DatabaseCleanState() throws Exception {
        System.out.println("Phase 1: DB Clean State");
        try (Connection con = DBConnection.getExclusiveConnection(); Statement stmt = con.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = OFF;");
            String[] tables = { "payment_allocations", "payment_details", "payments", "invoice_job_mapping", "invoice_additional_charges", "invoice_adjustments", "invoice_master", "job_items", "jobs", "clients", "suppliers" };
            for(String t : tables) stmt.execute("DELETE FROM " + t);
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        var httpOpt = SupabaseGate.restClientIfConfigured();
        assertTrue(httpOpt.isPresent(), "Supabase must be configured");
        var http = httpOpt.get();
        SupabaseEndpoints[] endpoints = { SupabaseEndpoints.PAYMENT_ALLOCATIONS, SupabaseEndpoints.PAYMENTS, SupabaseEndpoints.INVOICE_JOB_MAPPING, SupabaseEndpoints.INVOICE_MASTER, SupabaseEndpoints.JOB_ITEMS, SupabaseEndpoints.JOBS, SupabaseEndpoints.CLIENTS, SupabaseEndpoints.SUPPLIERS };
        for(SupabaseEndpoints e : endpoints) {
            try { http.delete(e, "uuid=not.is.null"); } catch(Exception ignored) {}
        }
    }

    @Test
    @Order(2)
    public void phase2_MasterDataCreation() throws Exception {
        System.out.println("Phase 2: Master Data Creation");
        ClientRepository repo = new ClientRepository();
        for (int i = 0; i < 50; i++) {
            Client c = new Client("Audit Client " + i, "Contact " + i, "9999000" + i, "", "", "", "", "Address", "", "");
            c.setClientUuid(ClientIdentifiers.newUuidV7String());
            c.setSyncStatus("PENDING");
            repo.save(c);
            clients.add(c.getClientUuid());
        }
        Thread.sleep(5000);
        try (Connection con = DBConnection.getConnection(); Statement stmt = con.createStatement(); ResultSet rs = stmt.executeQuery("SELECT count(*) FROM clients WHERE sync_status='PENDING'")) {
            if (rs.next()) assertEquals(0, rs.getInt(1), "Pending clients should be 0");
        }
    }

    @Test
    @Order(3)
    public void phase3_JobCreationWithEdgeCases() throws Exception {
        System.out.println("Phase 3: Job Creation");
        for (int i = 0; i < 50; i++) {
            final int index = i;
            AtomicDB.runVoid(con -> {
                Job job = new Job();
                job.setUuid(JobIdentifiers.newUuidString());
                job.setClientUuid(clients.get(index));
                job.setJobCode("AUDIT-" + index);
                job.setJobTitle("Audit Job " + index);
                job.setStatus("Completed");
                new JobRepository().insertJob(con, job);
                
                // Edge Case: Zero Quantity, Negative Amount, Massive Amount
                JobItem item = new JobItem();
                item.setUuid(ClientIdentifiers.newUuidV7String());
                item.setJobUuid(job.getUuid());
                item.setType("PRINTING");
                if (index % 3 == 0) item.setAmount(0.0);
                else if (index % 3 == 1) item.setAmount(-150.0);
                else item.setAmount(999999.99);
                item.setSortOrder(1);
                new JobItemRepository().save(con, item);
                JobRepository.syncAmountFromJobItems(con, job.getUuid());
                jobs.add(job.getUuid());
            });
        }
    }

    @Test
    @Order(4)
    public void phase4_InvoiceTesting() throws Exception {
        System.out.println("Phase 4: Invoice Testing");
        for (int i = 0; i < 25; i++) {
            final int index = i;
            AtomicDB.runVoid(con -> {
                InvoiceMaster draft = new InvoiceMaster();
                draft.setUuid(ClientIdentifiers.newUuidV7String());
                draft.setInvoiceNo("INV-AUDIT-" + index);
                draft.setClientId(clients.get(index));
                draft.setInvoiceDate(LocalDate.now());
                draft.setAmount(5000.0);
                draft.setType("DATE_RANGE");
                draft.setStatus("FINALIZED");
                draft.setSyncStatus("PENDING");
                draft.setDocumentSeries("GST_INVOICE");
                new InvoiceMasterRepository().insert(con, draft);
                invoices.add(draft.getUuid());

                // Invoice Mapping
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO invoice_job_mapping (uuid, invoice_uuid, job_uuid, sync_status, created_at, updated_at) VALUES (?,?,?, 'PENDING', datetime('now'), datetime('now'))")) {
                    ps.setString(1, ClientIdentifiers.newUuidV7String());
                    ps.setString(2, draft.getUuid());
                    ps.setString(3, jobs.get(index));
                    ps.executeUpdate();
                }
            });
        }
    }

    @Test
    @Order(10)
    public void phase10_SoftDelete() throws Exception {
        System.out.println("Phase 10: Soft Delete");
        Thread.sleep(5000); // Allow all prior phases to sync

        String uuidToDel = clients.get(0);
        ClientRepository repo = new ClientRepository();
        repo.deleteByUuid(uuidToDel);
        
        Thread.sleep(8000); // Wait for sync push to complete

        var http = SupabaseGate.restClientIfConfigured().get();
        var res = http.get(SupabaseEndpoints.CLIENTS, "uuid=eq." + uuidToDel);
        System.out.println("[Phase10 assertion] Remote response: " + res.body());
        assertTrue(res.body().contains("\"is_deleted\":true"), "Soft delete flag not pushed to Supabase. Response: " + res.body());
    }

    @Test
    @Order(11)
    public void phase11_ConflictResolution() throws Exception {
        System.out.println("Phase 11: Conflict Resolution");
        String uuidToConflict = clients.get(1);
        var http = SupabaseGate.restClientIfConfigured().get();

        // Step 1: Push a future-dated change directly to Supabase simulating Instance B (another machine) using PATCH
        String futureTimestamp = java.time.Instant.now().plusSeconds(60).toString();
        String remoteJson = "{\"business_name\":\"Instance B Win\", \"updated_at\":\"" + futureTimestamp + "\"}";
        http.patchJson(SupabaseEndpoints.CLIENTS, "uuid=eq." + uuidToConflict, remoteJson, "return=minimal");

        // Step 2: Make a local change simulating Instance A (5 minutes OLDER than Instance B)
        // Since this is older, it MUST NOT win.
        String pastTimestamp = java.time.Instant.now().minusSeconds(300).toString();
        AtomicDB.runVoid(con -> {
            try (PreparedStatement ps = con.prepareStatement("UPDATE clients SET business_name='Instance A Lose', sync_status='PENDING', sync_version=sync_version+1, updated_at=? WHERE uuid=?")) {
                ps.setString(1, pastTimestamp);
                ps.setString(2, uuidToConflict);
                ps.executeUpdate();
            }
        });

        // Step 3: Run a sync cycle synchronously - conflict resolver should detect and apply remote-wins
        UniversalSyncEngine.syncAllPending();

        // Step 4: Wait for any async threads to settle
        Thread.sleep(3000);

        // Step 5: Run a pull to get the latest state from Supabase
        RemoteToLocalSync.pullAll(http);
        Thread.sleep(2000);

        // Step 6: Assert the local DB now has the remote-wins value
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT business_name, sync_status FROM clients WHERE uuid=?")) {
            ps.setString(1, uuidToConflict);
            ResultSet rs = ps.executeQuery();
            rs.next();
            String localName = rs.getString(1);
            String localStatus = rs.getString(2);
            System.out.println("[Phase11 assertion] local business_name=" + localName + ", sync_status=" + localStatus);
            assertEquals("Instance B Win", localName, "Conflict resolution failed. Last write did not win. Got: " + localName);
        }
    }
}

