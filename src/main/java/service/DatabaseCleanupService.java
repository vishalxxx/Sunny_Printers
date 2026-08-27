package service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.*;
import model.SupabaseSettings;
import repository.SupabaseSettingsRepository;
import utils.DBConnection;

public final class DatabaseCleanupService {

    private static final List<String> DEPENDENCY_ORDER = List.of(
        "payment_allocations",
        "payment_details",
        "invoice_job_mapping",
        "invoice_additional_charges",
        "invoice_adjustments",
        "job_cancellation_audit",
        "job_items",
        "payments",
        "invoice_master",
        "jobs",
        "clients",
        "suppliers",
        "printing_items",
        "paper_items",
        "binding_items",
        "lamination_items",
        "ctp_items",
        "document_number_mappings",
        "sync_conflicts",
        "sync_metadata"
    );

    private static final Map<String, List<String>> TABLE_TO_SEQUENCE_MAP = Map.of(
        "jobs", List.of("job", "job_ticket"),
        "invoice_master", List.of("proforma_invoice", "gst_invoice", "temp_invoice", "credit_note", "debit_note", "dispatch_challan", "eway_bill_ref", "purchase_order", "quotation"),
        "payments", List.of("payment_receipt"),
        "clients", List.of("client"),
        "suppliers", List.of("supplier")
    );

    private static final Map<String, String> TABLE_DISPLAY_NAMES = Map.ofEntries(
        Map.entry("payment_allocations", "Payment Allocations"),
        Map.entry("payment_details", "Payment Details"),
        Map.entry("invoice_job_mapping", "Invoice Job Mapping"),
        Map.entry("invoice_additional_charges", "Invoice Additional Charges"),
        Map.entry("invoice_adjustments", "Invoice Adjustments"),
        Map.entry("job_cancellation_audit", "Job Cancellation Audit"),
        Map.entry("job_items", "Job Items"),
        Map.entry("payments", "Payments"),
        Map.entry("invoice_master", "Invoices"),
        Map.entry("jobs", "Jobs"),
        Map.entry("clients", "Clients"),
        Map.entry("suppliers", "Suppliers"),
        Map.entry("printing_items", "Printing Master Items"),
        Map.entry("paper_items", "Paper Master Items"),
        Map.entry("binding_items", "Binding Master Items"),
        Map.entry("lamination_items", "Lamination Master Items"),
        Map.entry("ctp_items", "CTP Master Items"),
        Map.entry("document_number_mappings", "Document Number Mappings"),
        Map.entry("sync_conflicts", "Sync Conflicts"),
        Map.entry("sync_metadata", "Sync Metadata")
    );

    public static List<String> getClearableTables() {
        return DEPENDENCY_ORDER;
    }

    public static String getDisplayName(String table) {
        return TABLE_DISPLAY_NAMES.getOrDefault(table, table);
    }

    public static void clearTables(List<String> selectedTables) throws Exception {
        if (selectedTables == null || selectedTables.isEmpty()) {
            return;
        }

        // Sort based on dependency order (child tables first)
        List<String> sortedTables = new ArrayList<>(selectedTables);
        sortedTables.sort(Comparator.comparingInt(DEPENDENCY_ORDER::indexOf));

        // Get Supabase settings if remote is active
        String supabaseUrl = null;
        String supabaseKey = null;
        try {
            SupabaseSettings s = new SupabaseSettingsRepository().load();
            if (s != null && s.getSupabaseUrl() != null && !s.getSupabaseUrl().isBlank() 
                    && s.getAnonKey() != null && !s.getAnonKey().isBlank()) {
                supabaseUrl = s.getSupabaseUrl().trim();
                if (supabaseUrl.endsWith("/")) {
                    supabaseUrl = supabaseUrl.substring(0, supabaseUrl.length() - 1);
                }
                supabaseKey = s.getAnonKey().trim();
            }
        } catch (Exception ignored) {
        }

        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        // Perform local and remote deletes
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try (Statement stmt = con.createStatement()) {
                // Disable foreign keys temporarily locally to allow raw deletes
                stmt.execute("PRAGMA foreign_keys = OFF;");

                for (String table : sortedTables) {
                    // Local Delete
                    stmt.executeUpdate("DELETE FROM " + table + ";");

                    // Remote Delete (if configured and table is not local-only)
                    boolean isLocalOnly = "sync_conflicts".equals(table) || "sync_metadata".equals(table) || "job_cancellation_audit".equals(table);
                    if (supabaseUrl != null && !isLocalOnly) {
                        try {
                            HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(supabaseUrl + "/rest/v1/" + table + "?uuid=not.is.null"))
                                .header("apikey", supabaseKey)
                                .header("Authorization", "Bearer " + supabaseKey)
                                .DELETE()
                                .timeout(Duration.ofSeconds(15))
                                .build();
                            
                            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                            if (response.statusCode() >= 300) {
                                System.err.println("Supabase delete failed for table " + table + ", status: " + response.statusCode());
                            }
                        } catch (Exception ex) {
                            System.err.println("Supabase delete failed for table " + table + ": " + ex.getMessage());
                        }
                    }

                    // Reset Local and Remote Sequences
                    List<String> sequencesToReset = TABLE_TO_SEQUENCE_MAP.get(table);
                    if (sequencesToReset != null) {
                        for (String seqKey : sequencesToReset) {
                            // Local reset
                            stmt.executeUpdate("UPDATE number_sequences SET current_number = 0, offline_current_number = 0 WHERE sequence_key = '" + seqKey + "';");

                            // Remote reset (if configured)
                            if (supabaseUrl != null) {
                                try {
                                    HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create(supabaseUrl + "/rest/v1/number_sequences?sequence_key=eq." + seqKey))
                                        .header("apikey", supabaseKey)
                                        .header("Authorization", "Bearer " + supabaseKey)
                                        .header("Content-Type", "application/json")
                                        .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"current_number\":0}"))
                                        .timeout(Duration.ofSeconds(10))
                                        .build();

                                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                                    if (response.statusCode() >= 300) {
                                        System.err.println("Supabase sequence reset failed for " + seqKey + ", status: " + response.statusCode());
                                    }
                                } catch (Exception ex) {
                                    System.err.println("Supabase sequence reset failed for " + seqKey + ": " + ex.getMessage());
                                }
                            }
                        }
                    }
                }

                stmt.execute("PRAGMA foreign_keys = ON;");
                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }
}
