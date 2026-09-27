package integration;
import service.JobService;
import service.InvoiceMasterService;
import service.InvoiceBuilderService;


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
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import model.Client;
import model.Invoice;
import model.Job;
import model.JobItem;
import repository.ClientRepository;
import utils.DBConnection;
import utils.DatabaseInitializer;
import utils.TestDatabaseHelper;

@Tag("integration")
public class InvoiceJobMappingDuplicateTest {

    private static String clientUuid;
    private static String jobUuid;
    private static String dbUrl;
    
    @BeforeEach
    public void setup() throws Exception {
        dbUrl = TestDatabaseHelper.createIsolatedDb("InvoiceJobMappingDuplicateTest");
        DBConnection.setTestDatabaseUrl(dbUrl);
        
        // Setup mock data
        clientUuid = UUID.randomUUID().toString();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO clients (uuid, client_code, client_name, business_name, mobile, email, client_type, is_active, sync_status, sync_version, is_deleted) VALUES (?,?,?,?,?,?,'Regular',1,'PENDING',1,0)")) {
            ps.setString(1, clientUuid);
            ps.setString(2, "TEST-CL-001");
            ps.setString(3, "Test Name");
            ps.setString(4, "Test Mapping Corp");
            ps.setString(5, "9999999999");
            ps.setString(6, "test@example.com");
            ps.executeUpdate();
        }
        
        JobService js = new JobService();
        Job job = js.createDraftJob();
        js.assignClient(job, clientUuid);
        js.updateJobDetails(job.getUuid(), "Test Duplicate Mapping Job", LocalDate.now());
        
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO job_items (uuid, job_uuid, description, amount, type, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, 'General', 1, datetime('now'), datetime('now'))")) {
             ps.setString(1, UUID.randomUUID().toString());
             ps.setString(2, job.getUuid());
             ps.setString(3, "Test Item");
             ps.setDouble(4, 100.0);
             ps.executeUpdate();
        }
        
        js.updateJobStatus(job.getUuid(), "Completed");
        jobUuid = job.getUuid();
    }
    
    @AfterAll
    public static void teardown() throws Exception {
        TestDatabaseHelper.cleanupTestDir();
    }

    private int countMappings(String invoiceUuid) throws Exception {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT count(*) FROM invoice_job_mapping WHERE invoice_uuid = ?")) {
            ps.setString(1, invoiceUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }
    
    private String getMappingUuid(String invoiceUuid, String jobUuid) throws Exception {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT uuid FROM invoice_job_mapping WHERE invoice_uuid = ? AND job_uuid = ?")) {
            ps.setString(1, invoiceUuid);
            ps.setString(2, jobUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("uuid");
            }
        }
        return null;
    }

    @Test
    public void testScenario1_AttachJobTwice() throws Exception {
        String invoiceUuid = UUID.randomUUID().toString();
        
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("INSERT INTO invoice_master (uuid, invoice_no, client_uuid, client_name, invoice_date, period_from, period_to, amount, type, status, is_void, document_series, sync_status, is_active, is_deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, invoiceUuid);
            ps.setString(2, "TEMP-INV-TEST");
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

        try (Connection con = DBConnection.getConnection()) {
            // Attach first time
            InvoiceMasterService.insertInvoiceJobMapping(con, invoiceUuid, jobUuid);
            int count1 = countMappings(invoiceUuid);
            String uuid1 = getMappingUuid(invoiceUuid, jobUuid);
            
            // Attach second time
            InvoiceMasterService.insertInvoiceJobMapping(con, invoiceUuid, jobUuid);
            int count2 = countMappings(invoiceUuid);
            String uuid2 = getMappingUuid(invoiceUuid, jobUuid);
            
            assertEquals(1, count1, "Should have 1 mapping after first insert");
            assertEquals(1, count2, "Should STILL have exactly 1 mapping after duplicate insert");
            assertNotNull(uuid1, "UUID1 should not be null");
            assertEquals(uuid1, uuid2, "UUID must remain completely stable across upserts to prevent Supabase push errors");
            
            System.out.println("[Scenario 1] Passed. Count: " + count2 + " | Stable UUID: " + uuid1);
        }
    }
    
    @Test
    public void testScenario2_DateRangeRegeneration() throws Exception {
        InvoiceBuilderService builder = new InvoiceBuilderService();
        InvoiceMasterService master = new InvoiceMasterService();
        
        LocalDate from = LocalDate.now().minusDays(1);
        LocalDate to = LocalDate.now().plusDays(1);
        
        // Gen 1
        Invoice inv1 = new Invoice();
        inv1.setClientId(clientUuid);
        inv1.setClientName("Test Mapping Corp");
        inv1.setInvoiceNo("TEMP-INV-DR1");
        model.InvoiceJob ij1 = new model.InvoiceJob();
        ij1.setJobUuid(jobUuid);
        ij1.setJobName("Job1");
        inv1.getJobs().add(ij1);
        inv1.setMasterDocumentSeries(model.MasterDocumentSeries.PROFORMA_INVOICE);
        String invUuid1 = null;
        try {
             service.InvoiceMasterService.CreateOrGetResult res = master.createNewDraftInvoice(inv1, "DATE_RANGE", null);
             invUuid1 = res.master().getUuid();
             master.registerDateRangeInvoice(inv1, from, to, "DATE_RANGE", null);
        } catch(Exception e) { e.printStackTrace(); }
        
        int count1 = countMappings(invUuid1);
        String mapUuid1 = getMappingUuid(invUuid1, jobUuid);
        
        // Gen 2 (Regenerate)
        Invoice inv2 = new Invoice();
        inv2.setClientId(clientUuid);
        inv2.setClientName("Test Mapping Corp");
        inv2.setInvoiceNo("TEMP-INV-DR1");
        model.InvoiceJob ij2 = new model.InvoiceJob();
        ij2.setJobUuid(jobUuid);
        ij2.setJobName("Job1");
        inv2.getJobs().add(ij2);
        inv2.setMasterDocumentSeries(model.MasterDocumentSeries.PROFORMA_INVOICE);
        master.registerDateRangeInvoice(inv2, from, to, "DATE_RANGE", null);
        
        int count2 = countMappings(invUuid1);
        String mapUuid2 = getMappingUuid(invUuid1, jobUuid);
        
        assertEquals(count1, count2, "Mapping count must remain unchanged upon regeneration");
        assertEquals(mapUuid1, mapUuid2, "UUID must remain stable upon regeneration");
        System.out.println("[Scenario 2] Passed. Count: " + count2 + " | Stable UUID: " + mapUuid1);
    }
    
    @Test
    public void testScenario3_MonthlyRegeneration() throws Exception {
        InvoiceBuilderService builder = new InvoiceBuilderService();
        InvoiceMasterService master = new InvoiceMasterService();
        
        int y = LocalDate.now().getYear();
        int m = LocalDate.now().getMonthValue();
        
        // Gen 1
        Map<String, Invoice> map1 = builder.buildMonthlyInvoicesForAllClients(y, m, LocalDate.now());
        String invUuid = null;
        for(Invoice i : map1.values()) {
            i.setMasterDocumentSeries(model.MasterDocumentSeries.PROFORMA_INVOICE);
            service.InvoiceMasterService.CreateOrGetResult res = master.createNewDraftInvoice(i, "MONTHLY_PROFORMA", null);
            if(i.getClientId().equals(clientUuid)) {
                invUuid = res.master().getUuid();
            }
        }
        master.registerMonthlyInvoices(map1, YearMonth.of(y, m).atDay(1), YearMonth.of(y, m).atEndOfMonth(), "MONTHLY_PROFORMA", null);
        
        int count1 = countMappings(invUuid);
        String mapUuid1 = getMappingUuid(invUuid, jobUuid);
        
        // Gen 2
        Map<String, Invoice> map2 = builder.buildMonthlyInvoicesForAllClients(y, m, LocalDate.now());
        for(Invoice i : map2.values()) {
            i.setMasterDocumentSeries(model.MasterDocumentSeries.PROFORMA_INVOICE);
            // We simulate the overwrite behavior 
            if(i.getClientId().equals(clientUuid)) i.setInvoiceNo(map1.get(i.getClientName()).getInvoiceNo());
        }
        master.registerMonthlyInvoices(map2, YearMonth.of(y, m).atDay(1), YearMonth.of(y, m).atEndOfMonth(), "MONTHLY_PROFORMA", null);
        
        int count2 = countMappings(invUuid);
        String mapUuid2 = getMappingUuid(invUuid, jobUuid);
        
        assertEquals(count1, count2, "Monthly mapping count must remain unchanged");
        assertEquals(mapUuid1, mapUuid2, "Monthly UUID must remain stable");
        System.out.println("[Scenario 3] Passed. Count: " + count2 + " | Stable UUID: " + mapUuid1);
    }
    
}

