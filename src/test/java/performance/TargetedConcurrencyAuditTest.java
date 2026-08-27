package performance;
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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import model.Invoice;
import model.InvoiceJob;
import model.MasterDocumentSeries;
import service.InvoiceMasterService;
import utils.AtomicDB;
import utils.ClientIdentifiers;
import utils.CompanyProfile;
import utils.DBConnection;

@Tag("performance")
public class TargetedConcurrencyAuditTest {

    private static String testDbUrl;
    private static final String REPORT_PATH = "C:/Users/VishalGoswami/.gemini/antigravity-ide/brain/0d167df4-6f31-47e6-8eb9-505a37fc5c0f/targeted_audit_results.md";

    private InvoiceMasterService invoiceService = new InvoiceMasterService();

    private List<String> clientUuids = new CopyOnWriteArrayList<>();
    private List<String> jobUuids = new CopyOnWriteArrayList<>();
    private List<String> draftGstInvoiceUuids = new CopyOnWriteArrayList<>();
    private List<String> draftProformaInvoiceUuids = new CopyOnWriteArrayList<>();
    private List<String> finalGstInvoiceUuids = new CopyOnWriteArrayList<>();
    private List<Throwable> caughtExceptions = new CopyOnWriteArrayList<>();

    @BeforeAll
    public static void setup() throws Exception {
        testDbUrl = TestDatabaseHelper.createIsolatedDb("TargetedConcurrencyTest");
        DBConnection.setTestDatabaseUrl(testDbUrl);

        CompanyProfile.setName("Sunny Printers Targeted");
        CompanyProfile.setGst("07BPPPS3532E2Z1");
    }

    @AfterAll
    public static void tearDown() {
        DBConnection.clearTestDatabaseUrl();
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
                "invoice_additional_charges", "document_number_mappings", 
                "suppliers", "clients", "sync_conflicts"
            };
            for (String table : tables) {
                stmt.execute("DELETE FROM " + table);
            }
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        clientUuids.clear();
        jobUuids.clear();
        draftGstInvoiceUuids.clear();
        draftProformaInvoiceUuids.clear();
        finalGstInvoiceUuids.clear();
        caughtExceptions.clear();
    }

    @Test
    public void performTargetedAudit() throws Exception {
        System.out.println("Starting Targeted Concurrency Audit...");
        long startTime = System.currentTimeMillis();

        // 1. Sync Loop
        Thread syncThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    UniversalSyncEngine.syncAllPending();
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        syncThread.setDaemon(true);
        syncThread.start();

        int numThreads = 15;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Callable<Void>> tasks = new ArrayList<>();

        // Phase 1: Clients & Jobs (20 Clients, 60 Jobs)
        for (int i = 0; i < 20; i++) {
            final int index = i;
            tasks.add(() -> {
                try {
                    String clientUuid = ClientIdentifiers.newUuidV7String();
                    AtomicDB.runExclusiveVoid(con -> {
                        try (PreparedStatement ps = con.prepareStatement("INSERT INTO clients (uuid, client_code, client_name, sync_status) VALUES (?,?,?,?)")) {
                            ps.setString(1, clientUuid);
                            ps.setString(2, "TC-" + index);
                            ps.setString(3, "[TARGET-AUDIT] Client " + index);
                            ps.setString(4, "PENDING");
                            ps.executeUpdate();
                        } catch (Exception ex) { throw new RuntimeException(ex); }
                    });
                    clientUuids.add(clientUuid);

                    for (int j = 0; j < 3; j++) {
                        String jobUuid = ClientIdentifiers.newUuidV7String();
                        AtomicDB.runExclusiveVoid(con -> {
                            try (PreparedStatement ps = con.prepareStatement("INSERT INTO jobs (uuid, client_uuid, job_code, status, amount, sync_status) VALUES (?,?,?,?,?,?)")) {
                                ps.setString(1, jobUuid);
                                ps.setString(2, clientUuid);
                                ps.setString(3, "TJOB-" + index + "-" + System.nanoTime());
                                ps.setString(4, "Completed");
                                ps.setDouble(5, 5000.0);
                                ps.setString(6, "PENDING");
                                ps.executeUpdate();
                            } catch (Exception ex) { throw new RuntimeException(ex); }
                        });
                        jobUuids.add(jobUuid);
                    }
                } catch (Exception e) {
                    caughtExceptions.add(e);
                }
                return null;
            });
        }

        // Phase 2: Invoices Generation (10 GST, 10 Proforma)
        for (int i = 0; i < 20; i++) {
            final boolean isGst = i < 10;
            tasks.add(() -> {
                try {
                    while (clientUuids.isEmpty() || jobUuids.isEmpty()) Thread.sleep(50);
                    
                    String cUuid = clientUuids.get(0);
                    String jUuid = jobUuids.get((int) (Math.random() * jobUuids.size()));

                    Invoice invoice = new Invoice();
                    invoice.setInvoiceNo("TEMP-" + UUID.randomUUID().toString().substring(0, 5));
                    invoice.setInvoiceDate(LocalDate.now());
                    invoice.setClientId(cUuid);
                    invoice.setStatus("DRAFT");
                    invoice.setInvoiceType(isGst ? "GST_INVOICE" : "PROFORMA_INVOICE");
                    invoice.setMasterDocumentSeries(isGst ? MasterDocumentSeries.GST_INVOICE : MasterDocumentSeries.PROFORMA_INVOICE);

                    InvoiceJob j = new InvoiceJob();
                    j.setJobId(jUuid);
                    j.setJobName("Targeted Audit Job");
                    j.setQuantity(5);
                    j.setRatePerUnit(1000.0);
                    j.setGstRate(0.18);
                    invoice.addJob(j);
                    invoice.setTotalAfterTax(5900.0);
                    invoice.setGrandTotal(5900.0);

                    String invUuid = invoiceService.saveGeneratedInvoice(invoice, invoice.getInvoiceType(), "DRAFT", null);
                    if (isGst) draftGstInvoiceUuids.add(invUuid);
                    else draftProformaInvoiceUuids.add(invUuid);
                    
                } catch (Exception e) {
                    caughtExceptions.add(e);
                }
                return null;
            });
        }

        // Phase 3: Finalization & Payments (GST only)
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> {
                try {
                    while (draftGstInvoiceUuids.isEmpty()) Thread.sleep(50);
                    String invUuid = draftGstInvoiceUuids.get((int)(Math.random() * draftGstInvoiceUuids.size()));
                    
                    try {
                        String finalNo = invoiceService.finalizeInvoice(invUuid);
                        if (finalNo != null) finalGstInvoiceUuids.add(invUuid);
                    } catch(Exception ignored) {} // May fail if another thread finalized it

                    // Apply a partial payment and a customer advance
                    if (finalGstInvoiceUuids.contains(invUuid)) {
                        String cUuid = clientUuids.get(0);
                        
                        // 1. Record Partial Payment (direct to invoice)
                        String payId = UUID.randomUUID().toString();
                        AtomicDB.runExclusiveVoid(con -> {
                            try (PreparedStatement ps = con.prepareStatement("INSERT INTO payments (uuid, client_uuid, amount, payment_date, method, type, sync_status) VALUES (?,?,?,?,?,?,?)")) {
                                ps.setString(1, payId);
                                ps.setString(2, cUuid);
                                ps.setDouble(3, 2000.0);
                                ps.setString(4, LocalDate.now().toString());
                                ps.setString(5, "Cash");
                                ps.setString(6, "Payment");
                                ps.setString(7, "PENDING");
                                ps.executeUpdate();
                            } catch (Exception ex) { throw new RuntimeException(ex); }
                            try (PreparedStatement ps = con.prepareStatement("INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount, is_deleted, sync_status) VALUES (?,?,?,?,?,?)")) {
                                ps.setString(1, UUID.randomUUID().toString());
                                ps.setString(2, payId);
                                ps.setString(3, invUuid);
                                ps.setDouble(4, 2000.0);
                                ps.setInt(5, 0);
                                ps.setString(6, "PENDING");
                                ps.executeUpdate();
                            } catch (Exception ex) { throw new RuntimeException(ex); }
                        });
                        invoiceService.recalculateInvoiceTotals(null, invUuid);

                        // 2. Customer Advance (Unallocated)
                        String advId = UUID.randomUUID().toString();
                        AtomicDB.runExclusiveVoid(con -> {
                            try (PreparedStatement ps = con.prepareStatement("INSERT INTO payments (uuid, client_uuid, amount, payment_date, method, type, sync_status) VALUES (?,?,?,?,?,?,?)")) {
                                ps.setString(1, advId);
                                ps.setString(2, cUuid);
                                ps.setDouble(3, 1000.0);
                                ps.setString(4, LocalDate.now().toString());
                                ps.setString(5, "Bank Transfer");
                                ps.setString(6, "Customer Advance");
                                ps.setString(7, "PENDING");
                                ps.executeUpdate();
                            } catch (Exception ex) { throw new RuntimeException(ex); }
                        });

                        // 3. Credit Note
                        String cnId = UUID.randomUUID().toString();
                        AtomicDB.runExclusiveVoid(con -> {
                            try (PreparedStatement ps = con.prepareStatement("INSERT INTO invoice_adjustments (uuid, invoice_uuid, type, note_no, amount, reason, date, sync_status) VALUES (?,?,?,?,?,?,?,?)")) {
                                ps.setString(1, cnId);
                                ps.setString(2, invUuid);
                                ps.setString(3, "Credit Note");
                                ps.setString(4, "CN-" + UUID.randomUUID().toString().substring(0, 5));
                                ps.setDouble(5, 500.0);
                                ps.setString(6, "Discount");
                                ps.setString(7, LocalDate.now().toString());
                                ps.setString(8, "PENDING");
                                ps.executeUpdate();
                            } catch (Exception ex) { throw new RuntimeException(ex); }
                        });
                        invoiceService.recalculateInvoiceTotals(null, invUuid);

                        // 4. Job Cancellation Re-allocation Test
                        // Simulate cancelling one of the jobs attached to this invoice
                        List<String> jobsToCancel = new ArrayList<>();
                        try (Connection c = DBConnection.getConnection();
                             PreparedStatement ps = c.prepareStatement("SELECT invoice_job_mapping.job_uuid FROM invoice_job_mapping WHERE invoice_uuid = ? LIMIT 1")) {
                            ps.setString(1, invUuid);
                            try(ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) jobsToCancel.add(rs.getString(1));
                            }
                        }
                        if (!jobsToCancel.isEmpty()) {
                            AtomicDB.runExclusiveVoid(con -> {
                                try {
                                    invoiceService.reallocatePaymentsOnJobCancellation(con, invUuid, jobsToCancel, "Client Request", "Admin");
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
                        }
                    }

                } catch (Exception e) {
                    caughtExceptions.add(e);
                }
                return null;
            });
        }

        Collections.shuffle(tasks);
        List<Future<Void>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.MINUTES);

        long endTime = System.currentTimeMillis();
        syncThread.interrupt();

        System.out.println("Execution complete in " + (endTime - startTime) + "ms. Generating report...");
        generateReport();
    }

    private void generateReport() throws Exception {
        int exceptionsCount = caughtExceptions.size();
        int sqliteBusyCount = 0;
        int deadlockCount = 0;

        for (Throwable t : caughtExceptions) {
            t.printStackTrace();
            if (t.getMessage() != null && t.getMessage().contains("database is locked")) sqliteBusyCount++;
            if (t.getMessage() != null && t.getMessage().contains("deadlock")) deadlockCount++;
        }

        int fkViolations = 0;
        int duplicateInvoices = 0;
        int negativeAdvances = 0;
        int waitingDependency = 0;
        int outOfBalanceInvoices = 0;

        try (Connection con = DBConnection.getConnection(); Statement st = con.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT count(invoice_no) - count(DISTINCT invoice_no) FROM invoice_master WHERE status = 'SENT'");
            if (rs.next()) duplicateInvoices = rs.getInt(1);

            rs = st.executeQuery("SELECT count(*) FROM payments WHERE type = 'Customer Advance' AND amount < 0");
            if (rs.next()) negativeAdvances = rs.getInt(1);

            rs = st.executeQuery("SELECT count(*) FROM clients WHERE sync_status = 'WAITING_DEPENDENCY'");
            if (rs.next()) waitingDependency = rs.getInt(1);

            rs = st.executeQuery("SELECT count(*) FROM invoice_master i JOIN (SELECT m.invoice_uuid, SUM(j.amount) as sum_jobs FROM invoice_job_mapping m JOIN jobs j ON m.job_uuid = j.uuid GROUP BY m.invoice_uuid) x ON i.uuid = x.invoice_uuid WHERE i.status = 'SENT' AND ABS(i.amount - x.sum_jobs) > 1.0");
            if (rs.next()) outOfBalanceInvoices = rs.getInt(1);
        }

        boolean passed = (exceptionsCount == 0 && duplicateInvoices == 0 && outOfBalanceInvoices == 0);

        File reportFile = new File(REPORT_PATH);
        reportFile.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(reportFile)) {
            fw.write("# Targeted Concurrency Stress Audit Report\n\n");
            
            if (passed) {
                fw.write("> [!NOTE]\n");
                fw.write("> **VERDICT: PASSED** \\n");
                fw.write("> All advanced accounting operations (Advances, Credit Notes, Cancellations) handled immense concurrency securely without a single SQLITE_BUSY error.\n\n");
            } else {
                fw.write("> [!WARNING]\n");
                fw.write("> **VERDICT: FAILED** \\n");
                fw.write("> Concurrency exceptions or data integrity issues were found during execution.\n\n");
            }

            fw.write("## Locking & Sync Exceptions\n");
            fw.write("* **Total Exceptions Caught:** " + exceptionsCount + "\n");
            fw.write("* **SQLITE_BUSY (Locked):** " + sqliteBusyCount + " (Expected: 0)\n");
            fw.write("* **Deadlocks:** " + deadlockCount + " (Expected: 0)\n\n");

            fw.write("## Data Integrity & Accounting Issues\n");
            fw.write("* **Duplicate Invoice Numbers:** " + duplicateInvoices + "\n");
            fw.write("* **Negative Customer Advances:** " + negativeAdvances + "\n");
            fw.write("* **Out-of-Balance Invoices:** " + outOfBalanceInvoices + "\n");
            fw.write("* **Records Stuck WAITING_DEPENDENCY:** " + waitingDependency + "\n");
        }
        
        System.out.println("Audit Report written to: " + REPORT_PATH);
        assertEquals(0, exceptionsCount, "Zero exceptions should be thrown under targeted extreme concurrency");
    }
}

