package utils;

import java.sql.Connection;
import java.sql.Statement;
import java.net.http.HttpResponse;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseRestClient;
import api.supabase.SupabaseClients;
import utils.DBConnection;

public class ClearAllExceptSettings {
    public static void main(String[] args) {
        System.out.println("=== Starting Database Clearing Script ===");

        // 1. Clear Local SQLite Tables
        System.out.println("\n--- Clearing Local SQLite Database ---");
        String[] sqliteTables = {
            "printing_items", "paper_items", "binding_items", "lamination_items", "ctp_items", 
            "job_items", "jobs", "payment_allocations", "payment_details", "payments", 
            "invoice_job_mapping", "invoice_master", "invoice_adjustments", 
            "invoice_additional_charges", "document_number_mappings", 
            "suppliers", "clients"
        };

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("PRAGMA foreign_keys = OFF;");
            
            for (String table : sqliteTables) {
                try {
                    System.out.println("Deleting local table: " + table + "...");
                    stmt.executeUpdate("DELETE FROM " + table + ";");
                } catch (Exception e) {
                    System.err.println("Failed to clear local table " + table + ": " + e.getMessage());
                }
            }
            
            stmt.execute("PRAGMA foreign_keys = ON;");
            System.out.println("✔ Local database tables cleared successfully!");
            
        } catch (Exception e) {
            System.err.println("Failed to connect to local database: " + e.getMessage());
            e.printStackTrace();
        }

        // 2. Clear Remote Supabase Tables
        System.out.println("\n--- Clearing Remote Supabase Database ---");
        SupabaseRestClient client = null;
        try {
            client = SupabaseClients.fromLocalDatabase();
        } catch (Exception e) {
            System.err.println("Could not load Supabase credentials: " + e.getMessage());
            return;
        }

        if (client == null) {
            System.err.println("Supabase client is null. Skipping remote database clearing.");
            return;
        }

        SupabaseEndpoints[] remoteTables = {
            SupabaseEndpoints.PRINTING_ITEMS,
            SupabaseEndpoints.PAPER_ITEMS,
            SupabaseEndpoints.BINDING_ITEMS,
            SupabaseEndpoints.LAMINATION_ITEMS,
            SupabaseEndpoints.CTP_ITEMS,
            SupabaseEndpoints.JOB_ITEMS,
            SupabaseEndpoints.JOBS,
            SupabaseEndpoints.PAYMENT_ALLOCATIONS,
            SupabaseEndpoints.PAYMENT_DETAILS,
            SupabaseEndpoints.PAYMENTS,
            SupabaseEndpoints.INVOICE_JOB_MAPPING,
            SupabaseEndpoints.INVOICE_MASTER,
            SupabaseEndpoints.INVOICE_ADJUSTMENTS,
            SupabaseEndpoints.INVOICE_ADDITIONAL_CHARGES,
            SupabaseEndpoints.DOCUMENT_NUMBER_MAPPINGS,
            SupabaseEndpoints.SUPPLIERS,
            SupabaseEndpoints.CLIENTS
        };

        String filter = "uuid=not.is.null";

        for (SupabaseEndpoints table : remoteTables) {
            try {
                System.out.println("Deleting remote table: " + table.name() + "...");
                HttpResponse<String> res = client.delete(table, filter);
                System.out.println("Result: " + res.statusCode() + " " + res.body());
            } catch (Exception e) {
                System.err.println("Failed to clear remote table " + table.name() + ": " + e.getMessage());
            }
        }
        
        System.out.println("\n=== Clearing Process Finished ===");
    }
}

