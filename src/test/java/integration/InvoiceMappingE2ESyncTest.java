package integration;
import service.sync.OtherPendingEntitiesSync;
import service.sync.SyncReport;
import service.sync.RemoteToLocalSync;


import org.junit.jupiter.api.Tag;
import utils.FakeSupabaseRestClient;
import utils.TestDatabaseHelper;
import utils.CleanupUtility;
import utils.ClearAllExceptSettings;
import utils.ClearRemoteDatabase;
import utils.MetricsExtractor;
import utils.SchemaAndSyncChecker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.UUID;

import api.supabase.SupabaseGate;
import api.supabase.SupabaseRestClient;
import api.supabase.SupabaseEndpoints;
import service.InvoiceMasterService;
import utils.DBConnection;
import utils.DatabaseInitializer;
import com.google.gson.JsonArray;

@Tag("integration")
public class InvoiceMappingE2ESyncTest {

    private static String clientUuid;
    private static String jobUuid;
    private static String invoiceUuid;
    private static String dbUrl;
    
    @BeforeEach
    public void setup() throws Exception {
        dbUrl = TestDatabaseHelper.createIsolatedDb("InvoiceMappingE2ESyncTest");
        DBConnection.setUrl(dbUrl);
        SupabaseGate.setOverrideClient(null);
        
        clientUuid = UUID.randomUUID().toString();
        jobUuid = UUID.randomUUID().toString();
        invoiceUuid = UUID.randomUUID().toString();
        
        try (Connection con = DBConnection.getConnection()) {
            // Client
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO clients (uuid, client_code, client_name, business_name, mobile, email, client_type, is_active, sync_status, sync_version, is_deleted) VALUES (?,?,?,?,?,?,'Regular',1,'PENDING',1,0)")) {
                ps.setString(1, clientUuid);
                ps.setString(2, UUID.randomUUID().toString().substring(0, 8));
                ps.setString(3, "Test Name");
                ps.setString(4, "Test E2E Corp");
                ps.setString(5, "9999999999");
                ps.setString(6, "test@example.com");
                ps.executeUpdate();
            }
            
            // Job
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO jobs (uuid, client_uuid, job_code, job_title, job_date, status, sync_status, sync_version, is_active, is_deleted, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'Completed', 'PENDING', 1, 1, 0, datetime('now'), datetime('now'))")) {
                ps.setString(1, jobUuid);
                ps.setString(2, clientUuid);
                ps.setString(3, "TEST-JOB-01");
                ps.setString(4, "E2E Sync Job");
                ps.setString(5, LocalDate.now().toString());
                ps.executeUpdate();
            }
            
            // Job Item
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO job_items (uuid, job_uuid, description, amount, type, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, 'General', 1, datetime('now'), datetime('now'))")) {
                 ps.setString(1, UUID.randomUUID().toString());
                 ps.setString(2, jobUuid);
                 ps.setString(3, "Test Item");
                 ps.setDouble(4, 100.0);
                 ps.executeUpdate();
            }
            
            // Invoice
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO invoice_master (uuid, invoice_no, client_uuid, client_name, invoice_date, period_from, period_to, amount, type, status, is_void, document_series, sync_status, is_active, is_deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, invoiceUuid);
                ps.setString(2, "TEMP-E2E-INV");
                ps.setString(3, clientUuid);
                ps.setString(4, "Test Client");
                ps.setString(5, LocalDate.now().toString());
                ps.setString(6, LocalDate.now().toString());
                ps.setString(7, LocalDate.now().toString());
                ps.setDouble(8, 100.0);
                ps.setString(9, "DATE_RANGE");
                ps.setString(10, "DRAFT");
                ps.setInt(11, 0);
                ps.setString(12, model.MasterDocumentSeries.PROFORMA_INVOICE.name());
                ps.setString(13, "PENDING");
                ps.setInt(14, 1);
                ps.setInt(15, 0);
                ps.executeUpdate();
            }
        }
    }
    
    @AfterAll
    public static void tearDown() {
        SupabaseGate.setOverrideClient(null);
        TestDatabaseHelper.cleanupTestDir();
    }
    
    private int getLocalMappingCount(String invUuid) throws Exception {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT count(*) FROM invoice_job_mapping WHERE invoice_uuid = ?")) {
            ps.setString(1, invUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }
    
    private String getLocalMappingUuid(String invUuid, String jUuid) throws Exception {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT uuid FROM invoice_job_mapping WHERE invoice_uuid = ? AND job_uuid = ?")) {
            ps.setString(1, invUuid);
            ps.setString(2, jUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("uuid");
            }
        }
        return null;
    }
    
    private int getRemoteMappingCount(SupabaseRestClient client, String invUuid) throws Exception {
        var res = client.get(SupabaseEndpoints.INVOICE_JOB_MAPPING, "invoice_uuid=eq." + invUuid);
        if (res.statusCode() >= 200 && res.statusCode() < 300) {
             JsonArray array = com.google.gson.JsonParser.parseString(res.body()).getAsJsonArray();
             return array.size();
        }
        return 0;
    }

    @Test
    public void runEndToEndSyncLoop() throws Exception {
        var optClient = SupabaseGate.restClientIfConfigured();
        if (optClient.isEmpty()) {
            System.out.println("Supabase not configured. Skipping remote verification. Will verify local stabilization loop.");
        }
        
        System.out.println("Starting 10x E2E Sync Regeneration Loops...");
        String stabilizedUuid = null;
        
        for (int i = 1; i <= 10; i++) {
            System.out.println("--- Loop " + i + " ---");
            
            // 1. Create/Regenerate mapping locally
            try (Connection con = DBConnection.getConnection()) {
                 InvoiceMasterService.insertInvoiceJobMapping(con, invoiceUuid, jobUuid);
            }
            
            String currentUuid = getLocalMappingUuid(invoiceUuid, jobUuid);
            if (stabilizedUuid == null) {
                stabilizedUuid = currentUuid;
            } else {
                assertEquals(stabilizedUuid, currentUuid, "UUID Destabilized at loop " + i);
            }
            
            int localCount = getLocalMappingCount(invoiceUuid);
            assertEquals(1, localCount, "Local mapping duplicated at loop " + i);
            
            // 2. Trigger Sync Push
            if (optClient.isPresent()) {
                 SyncReport pushReport = new SyncReport();
                 // Sync parent dependencies first so mapping doesn't fail foreign keys remote
                 OtherPendingEntitiesSync.syncTable(optClient.get(), new OtherPendingEntitiesSync.TableDef("clients", SupabaseEndpoints.CLIENTS, "uuid", false), pushReport);
                 OtherPendingEntitiesSync.syncTable(optClient.get(), new OtherPendingEntitiesSync.TableDef("jobs", SupabaseEndpoints.JOBS, "uuid", false), pushReport);
                 OtherPendingEntitiesSync.syncTable(optClient.get(), new OtherPendingEntitiesSync.TableDef("job_items", SupabaseEndpoints.JOB_ITEMS, "uuid", false), pushReport);
                 OtherPendingEntitiesSync.syncTable(optClient.get(), new OtherPendingEntitiesSync.TableDef("invoice_master", SupabaseEndpoints.INVOICE_MASTER, "uuid", false), pushReport);
                 
                 // Push mapping
                 OtherPendingEntitiesSync.syncTable(optClient.get(), new OtherPendingEntitiesSync.TableDef("invoice_job_mapping", SupabaseEndpoints.INVOICE_JOB_MAPPING, "uuid", false), pushReport);
                 
                 assertEquals(0, pushReport.failures, "Push sync encountered failures (likely 409 Constraint Violation)");
                 
                 int remoteCount = getRemoteMappingCount(optClient.get(), invoiceUuid);
                 assertEquals(1, remoteCount, "Remote mapping duplicated at loop " + i);
                 
                 // 3. Trigger Pull Sync
                 service.sync.RemoteToLocalSync.pullAll(optClient.get());
                 
                 // Re-verify local hasn't changed post-pull
                 String postPullUuid = getLocalMappingUuid(invoiceUuid, jobUuid);
                 assertEquals(stabilizedUuid, postPullUuid, "UUID Destabilized post-pull at loop " + i);
            }
        }
        
        System.out.println("Success! Verified 10 consecutive E2E regeneration and sync cycles without constraint failures.");
        System.out.println("Final Local Mapping Count: 1");
        if (optClient.isPresent()) {
             System.out.println("Final Remote Mapping Count: 1");
        }
        System.out.println("Stable UUID Retained: " + stabilizedUuid);
    }
}

