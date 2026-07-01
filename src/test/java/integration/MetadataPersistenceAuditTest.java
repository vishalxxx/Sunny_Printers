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

import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseGate;
import api.supabase.SupabaseReachability;
import com.google.gson.JsonObject;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import model.Invoice;
import model.InvoiceJob;
import model.InvoiceLine;
import service.GstPdfInvoiceService;
import service.InvoiceBuilderService;
import service.InvoiceMasterService;
import utils.DBConnection;
import utils.ClientIdentifiers;
import utils.CompanyProfile;

@Tag("integration")
public class MetadataPersistenceAuditTest {

    private static String dbPath;
    private static FakeSupabaseRestClient fakeSupabase;
    
    private static final List<String> passedTests = new ArrayList<>();
    private static final List<String> failedTests = new ArrayList<>();
    private static final List<String> missingFields = new ArrayList<>();
    private static final List<String> dataMismatches = new ArrayList<>();
    private static final List<String> pdfIssues = new ArrayList<>();
    private static final List<String> syncIssues = new ArrayList<>();

    @BeforeAll
    public static void setup() throws Exception {
        try {
            javafx.application.Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Already initialized
        }
        dbPath = TestDatabaseHelper.createIsolatedDb("MetadataPersistenceAuditTest");
        fakeSupabase = new FakeSupabaseRestClient();
        SupabaseGate.setOverrideClient(fakeSupabase);
        DBConnection.setUrl(dbPath);
    }

    @AfterAll
    public static void tearDown() {
        TestDatabaseHelper.cleanupTestDir();
        
        System.out.println("Passed Tests:");
        for (String t : passedTests) System.out.println(" - " + t);
        System.out.println("Failed Tests:");
        for (String t : failedTests) System.out.println(" - " + t);
        System.out.println("Missing Fields:");
        for (String t : missingFields) System.out.println(" - " + t);
        System.out.println("Data Mismatches:");
        for (String t : dataMismatches) System.out.println(" - " + t);
        System.out.println("PDF Rendering Issues:");
        for (String t : pdfIssues) System.out.println(" - " + t);
        System.out.println("Sync Issues:");
        for (String t : syncIssues) System.out.println(" - " + t);
        
        System.out.println("Final Verification Result: " + (failedTests.isEmpty() ? "SUCCESS" : "FAILURE"));
    }

    @BeforeEach
    public void resetDb() throws Exception {
        fakeSupabase.clear();
        fakeSupabase.setOnline(true);
        SupabaseReachability.invalidateCache();

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
    public void testAllFieldsPopulated() throws Exception {
        runScenario("All Fields Populated", true, true);
    }

    @Test
    public void testPartialFieldsPopulated() throws Exception {
        runScenario("Partial Fields Populated", true, false);
    }

    @Test
    public void testAllOptionalFieldsEmpty() throws Exception {
        runScenario("All Optional Fields Empty", false, false);
    }

    private void runScenario(String scenarioName, boolean populateOptionalBase, boolean populateOptionalAll) throws Exception {
        // Setup company GST and client
        CompanyProfile.setName("Sunny Printers");
        CompanyProfile.setAddress("Delhi, India");
        CompanyProfile.setEmail("info@sunnyprinters.com");
        CompanyProfile.setPhone("1234567890");
        CompanyProfile.setGst("07BPPPS3532E2ZO");

        String clientUuid = ClientIdentifiers.newUuidV7String();
        try (Connection con = DBConnection.getConnection()) {
            String clientSql = "INSERT INTO clients (uuid, client_code, client_name, business_name, gstin, billing_address, shipping_address, sync_status) VALUES (?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = con.prepareStatement(clientSql)) {
                ps.setString(1, clientUuid);
                ps.setString(2, "CL-AUDIT");
                ps.setString(3, "Audit Customer");
                ps.setString(4, "Audit Customer Ltd");
                ps.setString(5, "07AAAAA0000A1Z5");
                ps.setString(6, "Delhi Road, New Delhi");
                ps.setString(7, "Delhi Road, New Delhi");
                ps.setString(8, "SYNCED");
                ps.executeUpdate();
            }
        }

        // Generate jobs and associate
        String jobUuid = ClientIdentifiers.newUuidV7String();
        try (Connection conn = DBConnection.getConnection()) {
            String insJob = "INSERT INTO jobs (uuid, client_uuid, job_code, job_title, status, job_date) VALUES (?,?,?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(insJob)) {
                ps.setString(1, jobUuid);
                ps.setString(2, clientUuid);
                ps.setString(3, "JOB-AUDIT");
                ps.setString(4, "Audit Job");
                ps.setString(5, "Completed");
                ps.setString(6, LocalDate.now().toString());
                ps.executeUpdate();
            }
        }

        // Build Invoice Master Object
        Invoice invoice = new Invoice();
        invoice.setInvoiceNo("INV-AUDIT-" + scenarioName.replace(" ", "-").toUpperCase());
        invoice.setInvoiceDate(LocalDate.of(2026, 6, 21));
        invoice.setClientName("Audit Customer Ltd (Audit Customer)");
        invoice.setClientId(clientUuid);
        invoice.setBuyerAddress("Delhi Road, New Delhi");
        invoice.setBuyerGstin("07AAAAA0000A1Z5");
        invoice.setBuyerStateName("Delhi (07)");
        invoice.setStatus("SENT");
        invoice.setInvoiceType("GST_INVOICE");
        invoice.setMasterDocumentSeries(model.MasterDocumentSeries.GST_INVOICE);

        // Required and Optional Fields Population
        invoice.setPlaceOfSupply(populateOptionalBase ? "Delhi" : "");
        invoice.setPaymentTerms(populateOptionalBase ? "Net 30 Days" : "");
        invoice.setDueDate(populateOptionalBase ? LocalDate.of(2026, 7, 21) : null);
        invoice.setVehicleDispatch(populateOptionalAll ? "DL-3C-AB-9999" : "");
        invoice.setPoNo(populateOptionalBase ? "PO-REF-777" : "");
        invoice.setPoDate(populateOptionalAll ? LocalDate.of(2026, 6, 15) : null);
        invoice.setDispatchThrough(populateOptionalAll ? "Blue Dart Express" : "");
        invoice.setLrTrackingNo(populateOptionalAll ? "LR-TRACK-654321" : "");
        invoice.setEwayBillNo(populateOptionalAll ? "123456789012" : "");
        invoice.setRemarks(populateOptionalAll ? "Deliver to warehouse gate 3" : "");

        InvoiceJob invJob = new InvoiceJob();
        invJob.setJobId(jobUuid);
        invJob.setJobNo("JOB-AUDIT");
        invJob.setJobDate(LocalDate.now());
        invJob.setJobName("Audit Print Job");
        invJob.setHsnSac("4821");
        invJob.setQuantity(1000);
        invJob.setUnit("PCS");
        invJob.setRatePerUnit(2.50);
        invJob.setGstRate(0.18);
        invJob.addLine(new InvoiceLine("Audit Print Job", 2500.0));
        
        invoice.addJob(invJob);
        invoice.setGrandTotal(2950.0);
        invoice.setTotalAfterTax(2950.0);
        invoice.setRoundOff(0.0);

        // Step 1: Save Invoice to Local SQLite
        InvoiceMasterService invoiceMasterService = new InvoiceMasterService();
        String invoiceUuid = invoiceMasterService.saveGeneratedInvoice(invoice, "GST_INVOICE", "SENT", null);
        
        // Verify local SQLite persistence
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM invoice_master WHERE uuid = ?")) {
            ps.setString(1, invoiceUuid);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "[" + scenarioName + "] SQLite invoice_master record missing");
                
                verifyField(scenarioName, "Invoice No.", invoice.getInvoiceNo(), rs.getString("invoice_no"));
                verifyField(scenarioName, "Place of Supply", invoice.getPlaceOfSupply(), rs.getString("place_of_supply"));
                verifyField(scenarioName, "Payment Terms", invoice.getPaymentTerms(), rs.getString("payment_terms"));
                verifyField(scenarioName, "Due Date", invoice.getDueDate(), rs.getString("due_date"));
                verifyField(scenarioName, "Vehicle / Dispatch", invoice.getVehicleDispatch(), rs.getString("vehicle_dispatch"));
                verifyField(scenarioName, "Reference / PO No.", invoice.getPoNo(), rs.getString("po_no"));
                verifyField(scenarioName, "PO Date", invoice.getPoDate(), rs.getString("po_date"));
                verifyField(scenarioName, "Dispatch Through / Courier", invoice.getDispatchThrough(), rs.getString("dispatch_through"));
                verifyField(scenarioName, "LR / Tracking No.", invoice.getLrTrackingNo(), rs.getString("lr_tracking_no"));
                verifyField(scenarioName, "E-Way Bill No.", invoice.getEwayBillNo(), rs.getString("eway_bill_no"));
                verifyField(scenarioName, "Remarks / Terms of Delivery", invoice.getRemarks(), rs.getString("remarks"));
            }
        }

        // Step 2: Download / Generate PDF
        GstPdfInvoiceService pdfService = new GstPdfInvoiceService();
        File pdfFile = pdfService.generateGstInvoice(invoice);
        assertTrue(pdfFile.exists(), "[" + scenarioName + "] PDF file was not created");

        // Verify PDF content
        verifyPdfContent(scenarioName, pdfFile, invoice);

        // Step 3: Reopen / Regenerate Invoice
        InvoiceBuilderService invoiceBuilderService = new InvoiceBuilderService();
        Invoice regenerated = invoiceBuilderService.buildInvoiceFromMasterForPdfExport(invoiceUuid);
        
        // Verify no data loss or field swapping on reload
        verifyInvoiceMatch(scenarioName, "Regeneration", invoice, regenerated);

        // Step 4: Re-download Invoice
        File repdfFile = pdfService.generateGstInvoice(regenerated);
        assertTrue(repdfFile.exists(), "[" + scenarioName + "] Re-downloaded PDF file was not created");
        
        // Verify re-downloaded PDF is matching original metadata
        verifyPdfContent(scenarioName + " (Re-download)", repdfFile, regenerated);

        // Step 5: Sync Validation
        SyncReport syncReport = UniversalSyncEngine.syncAllPending();
        assertEquals(0, syncReport.failures, "[" + scenarioName + "] Sync failed");
        
        // Verify remote Supabase contains exact record
        List<JsonObject> rows = fakeSupabase.getTableData(SupabaseEndpoints.INVOICE_MASTER);
        JsonObject remoteRow = null;
        for (JsonObject row : rows) {
            if (row.has("uuid") && invoiceUuid.equals(row.get("uuid").getAsString())) {
                remoteRow = row;
                break;
            }
        }
        assertNotNull(remoteRow, "[" + scenarioName + "] Supabase invoice_master record missing after sync");
        
        verifyRemoteField(scenarioName, "Place of Supply", invoice.getPlaceOfSupply(), remoteRow.get("place_of_supply"));
        verifyRemoteField(scenarioName, "Payment Terms", invoice.getPaymentTerms(), remoteRow.get("payment_terms"));
        verifyRemoteField(scenarioName, "Due Date", invoice.getDueDate(), remoteRow.get("due_date"));
        verifyRemoteField(scenarioName, "Vehicle / Dispatch", invoice.getVehicleDispatch(), remoteRow.get("vehicle_dispatch"));
        verifyRemoteField(scenarioName, "Reference / PO No.", invoice.getPoNo(), remoteRow.get("po_no"));
        verifyRemoteField(scenarioName, "PO Date", invoice.getPoDate(), remoteRow.get("po_date"));
        verifyRemoteField(scenarioName, "Dispatch Through / Courier", invoice.getDispatchThrough(), remoteRow.get("dispatch_through"));
        verifyRemoteField(scenarioName, "LR / Tracking No.", invoice.getLrTrackingNo(), remoteRow.get("lr_tracking_no"));
        verifyRemoteField(scenarioName, "E-Way Bill No.", invoice.getEwayBillNo(), remoteRow.get("eway_bill_no"));
        verifyRemoteField(scenarioName, "Remarks / Terms of Delivery", invoice.getRemarks(), remoteRow.get("remarks"));

        passedTests.add(scenarioName);
    }

    private void verifyField(String scenario, String fieldName, Object expected, String actual) {
        String expectedStr = expected == null ? "" : expected.toString();
        String actualStr = actual == null ? "" : actual.toString();
        if (!expectedStr.equals(actualStr)) {
            dataMismatches.add("[" + scenario + "] Field '" + fieldName + "' expected '" + expectedStr + "' but was '" + actualStr + "' in SQLite");
            if (!failedTests.contains(scenario)) failedTests.add(scenario);
        }
    }

    private void verifyRemoteField(String scenario, String fieldName, Object expected, com.google.gson.JsonElement actualElement) {
        String expectedStr = expected == null ? "" : expected.toString();
        String actualStr = (actualElement == null || actualElement.isJsonNull()) ? "" : actualElement.getAsString();
        if (!expectedStr.equals(actualStr)) {
            syncIssues.add("[" + scenario + "] Field '" + fieldName + "' expected '" + expectedStr + "' but was '" + actualStr + "' in Supabase");
            if (!failedTests.contains(scenario)) failedTests.add(scenario);
        }
    }

    private void verifyInvoiceMatch(String scenario, String phase, Invoice original, Invoice loaded) {
        verifyProperty(scenario, phase, "Invoice No.", original.getInvoiceNo(), loaded.getInvoiceNo());
        verifyProperty(scenario, phase, "Place of Supply", original.getPlaceOfSupply(), loaded.getPlaceOfSupply());
        verifyProperty(scenario, phase, "Payment Terms", original.getPaymentTerms(), loaded.getPaymentTerms());
        verifyProperty(scenario, phase, "Due Date", original.getDueDate(), loaded.getDueDate());
        verifyProperty(scenario, phase, "Vehicle / Dispatch", original.getVehicleDispatch(), loaded.getVehicleDispatch());
        verifyProperty(scenario, phase, "Reference / PO No.", original.getPoNo(), loaded.getPoNo());
        verifyProperty(scenario, phase, "PO Date", original.getPoDate(), loaded.getPoDate());
        verifyProperty(scenario, phase, "Dispatch Through / Courier", original.getDispatchThrough(), loaded.getDispatchThrough());
        verifyProperty(scenario, phase, "LR / Tracking No.", original.getLrTrackingNo(), loaded.getLrTrackingNo());
        verifyProperty(scenario, phase, "E-Way Bill No.", original.getEwayBillNo(), loaded.getEwayBillNo());
        verifyProperty(scenario, phase, "Remarks / Terms of Delivery", original.getRemarks(), loaded.getRemarks());
    }

    private void verifyProperty(String scenario, String phase, String fieldName, Object expected, Object actual) {
        String expectedStr = expected == null ? "" : expected.toString().trim();
        String actualStr = actual == null ? "" : actual.toString().trim();
        if (!expectedStr.equals(actualStr)) {
            dataMismatches.add("[" + scenario + " - " + phase + "] Mismatch on '" + fieldName + "': expected '" + expectedStr + "', got '" + actualStr + "'");
            if (!failedTests.contains(scenario)) failedTests.add(scenario);
        }
    }

    private void verifyPdfContent(String scenario, File pdfFile, Invoice invoice) throws Exception {
        PdfReader reader = new PdfReader(pdfFile.getAbsolutePath());
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        String text = extractor.getTextFromPage(1);
        reader.close();

        // Check Invoice No and Invoice Date (Always populated)
        assertTrue(text.contains(invoice.getInvoiceNo()), "PDF missing Invoice No.");
        
        // Assert on Optional Fields if populated, or hidden if empty
        checkPdfField(scenario, text, "Place of Supply", invoice.getPlaceOfSupply());
        checkPdfField(scenario, text, "Mode/Terms of Payment", invoice.getPaymentTerms());
        checkPdfField(scenario, text, "Due Date", invoice.getDueDate() != null ? invoice.getDueDate().format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy")) : null);
        checkPdfField(scenario, text, "Vehicle / Dispatch", invoice.getVehicleDispatch());
        checkPdfField(scenario, text, "Buyer's Order No.", invoice.getPoNo());
        checkPdfField(scenario, text, "Order Date", invoice.getPoDate() != null ? invoice.getPoDate().format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy")) : null);
        checkPdfField(scenario, text, "Dispatched through", invoice.getDispatchThrough());
        checkPdfField(scenario, text, "Dispatch Doc No. (LR)", invoice.getLrTrackingNo());
        checkPdfField(scenario, text, "E-Way Bill No.", invoice.getEwayBillNo());
        checkPdfField(scenario, text, "Remarks / Terms of Delivery", invoice.getRemarks());
    }

    private void checkPdfField(String scenario, String pdfText, String label, Object value) {
        String valStr = value == null ? "" : value.toString().trim();
        if (!valStr.isEmpty()) {
            if (!pdfText.contains(label)) {
                missingFields.add("[" + scenario + "] Populated field label '" + label + "' is missing in PDF");
                if (!failedTests.contains(scenario)) failedTests.add(scenario);
            }
            if (!pdfText.contains(valStr)) {
                missingFields.add("[" + scenario + "] Populated field value '" + valStr + "' is missing in PDF");
                if (!failedTests.contains(scenario)) failedTests.add(scenario);
            }
        } else {
            if (pdfText.contains(label)) {
                pdfIssues.add("[" + scenario + "] Empty optional field label '" + label + "' should be hidden but was found in PDF");
                if (!failedTests.contains(scenario)) failedTests.add(scenario);
            }
        }
    }
}

