package service.sync;

import java.sql.Connection;
import java.sql.Statement;

import utils.DBConnection;
import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseGate;

public class CleanupUtility {
    public static void main(String[] args) throws Exception {
        System.out.println("Cleaning up all data...");
        DBConnection.setUrl("jdbc:sqlite:database/sunnyprinters.db?busy_timeout=15000&journal_mode=WAL");

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
                "document_number_mappings", "billing",
                "clients", "suppliers"
            };

            for(String t : tables) {
                try {
                    stmt.execute("DELETE FROM " + t);
                    System.out.println("Cleaned local table: " + t);
                } catch(Exception e) {
                    System.err.println("Error cleaning local " + t + ": " + e.getMessage());
                }
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
                SupabaseEndpoints.DOCUMENT_NUMBER_MAPPINGS, SupabaseEndpoints.BILLING,
                SupabaseEndpoints.CLIENTS, SupabaseEndpoints.SUPPLIERS
            };
            for(SupabaseEndpoints e : endpoints) {
                try {
                    http.delete(e, "uuid=not.is.null");
                    System.out.println("Cleaned remote endpoint: " + e.name());
                } catch(Exception ex) {
                    System.err.println("Error cleaning remote " + e.name() + ": " + ex.getMessage());
                }
            }
        }
        System.out.println("Cleanup Complete!");
    }
}
