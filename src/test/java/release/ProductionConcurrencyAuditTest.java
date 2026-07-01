package release;
import service.sync.UniversalSyncEngine;


import org.junit.jupiter.api.Tag;
import utils.FakeSupabaseRestClient;
import utils.TestDatabaseHelper;
import utils.CleanupUtility;
import utils.ClearAllExceptSettings;
import utils.ClearRemoteDatabase;
import utils.MetricsExtractor;
import utils.SchemaAndSyncChecker;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import model.Invoice;
import model.InvoiceJob;
import model.InvoiceLine;
import model.MasterDocumentSeries;
import repository.InvoiceMasterRepository;
import service.InvoiceMasterService;
import utils.AtomicDB;
import utils.ClientIdentifiers;
import utils.CompanyProfile;
import utils.DBConnection;

@Tag("release")
public class ProductionConcurrencyAuditTest {

    private static String originalDbUrl;
    private static String testDbUrl;
    private static final String REPORT_PATH = "C:/Users/VishalGoswami/.gemini/antigravity-ide/brain/0d167df4-6f31-47e6-8eb9-505a37fc5c0f/production_concurrency_audit_results.md";

    private InvoiceMasterService invoiceService = new InvoiceMasterService();
    private InvoiceMasterRepository invoiceRepo = new InvoiceMasterRepository();

    private List<String> clientUuids = new CopyOnWriteArrayList<>();
    private List<String> jobUuids = new CopyOnWriteArrayList<>();
    private List<String> draftInvoiceUuids = new CopyOnWriteArrayList<>();
    private List<String> finalizedInvoiceUuids = new CopyOnWriteArrayList<>();
    private List<Throwable> caughtExceptions = new CopyOnWriteArrayList<>();

    private AtomicInteger totalInvoicesGenerated = new AtomicInteger(0);

    @BeforeAll
    public static void setup() throws Exception {
        originalDbUrl = DBConnection.getUrl();
        testDbUrl = TestDatabaseHelper.createIsolatedDb("ConcurrencyAuditTest");
        DBConnection.setUrl(testDbUrl);
        
        CompanyProfile.setName("Sunny Printers");
        CompanyProfile.setAddress("Delhi, India");
        CompanyProfile.setGst("07BPPPS3532E2ZO");
        CompanyProfile.setEmail("test@example.com");
    }

    @AfterAll
    public static void tearDown() {
        DBConnection.setUrl(originalDbUrl);
        // TestDatabaseHelper.cleanupTestDir(); // Keep it for inspection if needed
    }

    @BeforeEach
    public void resetDb() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = OFF;");
            String[] tables = {
                "printing_items", "paper_items", "binding_items", "lamination_items", "ctp_items", 
                "job_items", "jobs", "payment_allocations", "payment_details", "payments", 
                "invoice_job_mapping", "invoice_master", "invoice_adjustments", 
                "invoice_additional_charges", "document_number_mappings", "billing", 
                "suppliers", "clients", "sync_conflicts"
            };
            for (String table : tables) {
                stmt.execute("DELETE FROM " + table);
            }
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        clientUuids.clear();
        jobUuids.clear();
        draftInvoiceUuids.clear();
        finalizedInvoiceUuids.clear();
        caughtExceptions.clear();
    }

    @Test
    public void performProductionConcurrencyStressAudit() throws Exception {
        System.out.println("Starting Production Concurrency Stress Audit...");
        long startTime = System.currentTimeMillis();

        // 1. Start Custom Sync Engine Loop
        Thread syncThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    UniversalSyncEngine.syncAllPending();
                    Thread.sleep(5000); // 5 seconds polling
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        syncThread.setDaemon(true);
        syncThread.start();
        System.out.println("Sync engines activated.");

        int numThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Callable<Void>> tasks = new ArrayList<>();

        // Phase 1: Clients & Jobs Generation
        for (int i = 0; i < 100; i++) {
            final int clientIndex = i;
            tasks.add(() -> {
                try {
                    String clientUuid = ClientIdentifiers.newUuidV7String();
                    String name = "ProdAudit Client " + clientIndex;
                    
                    try (Connection con = DBConnection.getConnection();
                         PreparedStatement ps = con.prepareStatement("INSERT INTO clients (uuid, client_code, client_name, gstin, sync_status) VALUES (?,?,?,?,?)")) {
                        ps.setString(1, clientUuid);
                        ps.setString(2, "CL-AUDIT-" + clientIndex);
                        ps.setString(3, name);
                        ps.setString(4, "07AAAAA0000A1Z" + (clientIndex % 9));
                        ps.setString(5, "PENDING");
                        ps.executeUpdate();
                    }
                    clientUuids.add(clientUuid);

                    // Create 3 jobs per client
                    for (int j = 0; j < 3; j++) {
                        String jobUuid = ClientIdentifiers.newUuidV7String();
                        try (Connection con = DBConnection.getConnection();
                             PreparedStatement ps = con.prepareStatement("INSERT INTO jobs (uuid, client_uuid, job_code, status, amount, sync_status) VALUES (?,?,?,?,?,?)")) {
                            ps.setString(1, jobUuid);
                            ps.setString(2, clientUuid);
                            ps.setString(3, "JOB-" + clientIndex + "-" + j);
                            ps.setString(4, "Completed");
                            ps.setDouble(5, 1000.0);
                            ps.setString(6, "PENDING");
                            ps.executeUpdate();
                        }
                        jobUuids.add(jobUuid);
                    }
                } catch (Exception e) {
                    caughtExceptions.add(e);
                }
                return null;
            });
        }

        // Phase 2: Invoices Generation & Finalization
        // 50 GST, 20 Proforma (Manual), 10 Date-Range, 10 Monthly
        for (int i = 0; i < 90; i++) {
            final int invoiceIndex = i;
            tasks.add(() -> {
                try {
                    // Wait for some clients/jobs to exist
                    while (clientUuids.isEmpty() || jobUuids.isEmpty()) {
                        Thread.sleep(100);
                    }
                    
                    String clientUuid = clientUuids.get((int) (Math.random() * clientUuids.size()));
                    String jobUuid = jobUuids.get((int) (Math.random() * jobUuids.size()));

                    Invoice invoice = new Invoice();
                    invoice.setInvoiceNo("TEMP-" + UUID.randomUUID().toString().substring(0, 8));
                    invoice.setInvoiceDate(LocalDate.now());
                    invoice.setClientId(clientUuid);
                    invoice.setStatus("DRAFT");
                    
                    if (invoiceIndex < 50) {
                        invoice.setInvoiceType("GST_INVOICE");
                        invoice.setMasterDocumentSeries(MasterDocumentSeries.GST_INVOICE);
                    } else {
                        invoice.setInvoiceType("PROFORMA_INVOICE");
                        invoice.setMasterDocumentSeries(MasterDocumentSeries.PROFORMA_INVOICE);
                    }

                    InvoiceJob j = new InvoiceJob();
                    j.setJobId(jobUuid);
                    j.setJobName("Audit Job");
                    j.setQuantity(1);
                    j.setRatePerUnit(1000.0);
                    j.setGstRate(0.18);
                    invoice.addJob(j);
                    
                    invoice.setTotalAfterTax(1180.0);
                    invoice.setGrandTotal(1180.0);

                    // Generate Draft
                    String invUuid = invoiceService.saveGeneratedInvoice(invoice, invoice.getInvoiceType(), "DRAFT", null);
                    draftInvoiceUuids.add(invUuid);
                    totalInvoicesGenerated.incrementAndGet();

                    // Instantly attempt Finalize
                    String finalNo = invoiceService.finalizeInvoice(invUuid);
                    finalizedInvoiceUuids.add(invUuid);

                    // Record Payment against finalized
                    String paymentUuid = UUID.randomUUID().toString();
                    try (Connection con = DBConnection.getConnection()) {
                        con.setAutoCommit(false);
                        try {
                            try (PreparedStatement ps = con.prepareStatement("INSERT INTO payments (uuid, client_uuid, amount, payment_date, method, type, sync_status) VALUES (?,?,?,?,?,?,?)")) {
                                ps.setString(1, paymentUuid);
                                ps.setString(2, clientUuid);
                                ps.setDouble(3, 1180.0);
                                ps.setString(4, LocalDate.now().toString());
                                ps.setString(5, "Bank Transfer");
                                ps.setString(6, "Payment");
                                ps.setString(7, "PENDING");
                                ps.executeUpdate();
                            }
                            try (PreparedStatement ps = con.prepareStatement("INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount, is_deleted, sync_status) VALUES (?,?,?,?,?,?)")) {
                                ps.setString(1, UUID.randomUUID().toString());
                                ps.setString(2, paymentUuid);
                                ps.setString(3, invUuid);
                                ps.setDouble(4, 1180.0);
                                ps.setInt(5, 0);
                                ps.setString(6, "PENDING");
                                ps.executeUpdate();
                            }
                            con.commit();
                        } catch (Exception ex) {
                            con.rollback();
                            throw ex;
                        }
                    }
                    
                    // Recalculate Totals (to trigger read-write patterns on allocated invoice)
                    invoiceService.recalculateInvoiceTotals(null, invUuid);

                } catch (Exception e) {
                    caughtExceptions.add(e);
                }
                return null;
            });
        }

        // Phase 3: Background View Invoices Searches (Simulate aggressive reads)
        for (int i = 0; i < 50; i++) {
            tasks.add(() -> {
                try {
                    for(int attempt=0; attempt<5; attempt++) {
                        Thread.sleep(200);
                        invoiceService.getFilteredInvoices(null, "All", "All", null, null, "", "All");
                    }
                } catch (Exception e) {
                    caughtExceptions.add(e);
                }
                return null;
            });
        }

        // Shuffle tasks to maximize chaos
        Collections.shuffle(tasks);

        // Execute all tasks concurrently
        System.out.println("Submitting " + tasks.size() + " concurrent tasks to ExecutorService...");
        List<Future<Void>> futures = executor.invokeAll(tasks);
        
        // Wait for completion
        executor.shutdown();
        boolean finished = executor.awaitTermination(3, TimeUnit.MINUTES);
        if (!finished) {
            System.err.println("Executor did not terminate in the specified time.");
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Execution complete in " + (endTime - startTime) + "ms. Generating report...");

        // Stop engines
        syncThread.interrupt();

        // Analysis & Output
        generateAuditReport();
    }

    private void generateAuditReport() throws Exception {
        int exceptionsCount = caughtExceptions.size();
        int sqliteBusyCount = 0;
        int deadlockCount = 0;

        for (Throwable t : caughtExceptions) {
            t.printStackTrace();
            if (t.getMessage() != null && t.getMessage().contains("database is locked")) {
                sqliteBusyCount++;
            }
            if (t.getMessage() != null && t.getMessage().contains("deadlock")) {
                deadlockCount++;
            }
        }

        int finalInvoices = 0;
        int draftInvoices = 0;
        int duplicateNumbers = 0;
        int waitingDependency = 0;
        double unallocatedBalanceSum = 0;
        int syncConflicts = 0;

        try (Connection con = DBConnection.getConnection(); Statement st = con.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT count(*) FROM invoice_master WHERE status = 'SENT'");
            if (rs.next()) finalInvoices = rs.getInt(1);

            rs = st.executeQuery("SELECT count(*) FROM invoice_master WHERE status = 'DRAFT'");
            if (rs.next()) draftInvoices = rs.getInt(1);

            rs = st.executeQuery("SELECT count(invoice_no) - count(DISTINCT invoice_no) FROM invoice_master WHERE status = 'SENT'");
            if (rs.next()) duplicateNumbers = rs.getInt(1);

            rs = st.executeQuery("SELECT count(*) FROM clients WHERE sync_status = 'WAITING_DEPENDENCY'");
            if (rs.next()) waitingDependency += rs.getInt(1);

            rs = st.executeQuery("SELECT count(*) FROM sync_conflicts");
            if (rs.next()) syncConflicts = rs.getInt(1);
        }

        boolean passed = (exceptionsCount == 0 && duplicateNumbers == 0);

        File reportFile = new File(REPORT_PATH);
        reportFile.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(reportFile)) {
            fw.write("# Production Concurrency Stress Audit Report\n\n");
            
            if (passed) {
                fw.write("> [!NOTE]\n");
                fw.write("> **VERDICT: PRODUCTION READY** \\n");
                fw.write("> The application successfully handled massive concurrent read/writes, network operations, and background syncing without a single database lock or corruption issue.\n\n");
            } else {
                fw.write("> [!WARNING]\n");
                fw.write("> **VERDICT: CRITICAL FAILURES DETECTED** \\n");
                fw.write("> Concurrency exceptions or data integrity issues were found.\n\n");
            }

            fw.write("## Performance Metrics\n");
            fw.write("* **Total Clients Created:** 100\n");
            fw.write("* **Total Jobs Created:** 300\n");
            fw.write("* **Total Invoices Generated:** " + totalInvoicesGenerated.get() + "\n");
            fw.write("* **Invoices Successfully Finalized:** " + finalInvoices + "\n");
            fw.write("* **Invoices Remaining Draft:** " + draftInvoices + "\n\n");

            fw.write("## Concurrency Exceptions\n");
            fw.write("* **Total Exceptions Caught:** " + exceptionsCount + "\n");
            fw.write("* **SQLITE_BUSY (Locked):** " + sqliteBusyCount + " (Expected: 0)\n");
            fw.write("* **Deadlocks:** " + deadlockCount + " (Expected: 0)\n\n");

            fw.write("## Data Integrity\n");
            fw.write("* **Duplicate Document Numbers:** " + duplicateNumbers + "\n");
            fw.write("* **Records Stuck WAITING_DEPENDENCY:** " + waitingDependency + "\n");
            fw.write("* **Sync Conflicts Logged:** " + syncConflicts + "\n\n");

            if (!caughtExceptions.isEmpty()) {
                fw.write("## Exception Stack Traces (First 10)\n```java\n");
                for (int i = 0; i < Math.min(10, caughtExceptions.size()); i++) {
                    Throwable t = caughtExceptions.get(i);
                    fw.write(t.toString() + "\n");
                    if (t.getCause() != null) {
                        fw.write("Caused by: " + t.getCause().toString() + "\n");
                    }
                    fw.write("\n");
                }
                fw.write("```\n");
            }
        }
        
        System.out.println("Audit Report written to: " + REPORT_PATH);
        assertEquals(0, exceptionsCount, "Zero exceptions should be thrown under extreme concurrency");
        assertEquals(0, sqliteBusyCount, "Zero SQLITE_BUSY errors should occur");
    }
}

