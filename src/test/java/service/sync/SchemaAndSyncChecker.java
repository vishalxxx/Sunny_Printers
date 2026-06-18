package service.sync;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import api.supabase.SupabaseRestClient;
import api.supabase.SupabaseClients;
import utils.DBConnection;

public class SchemaAndSyncChecker {

    public static void main(String[] args) {
        System.out.println("=== Schema & Sync Checker ===");

        // 1. Initialize Supabase client using local database settings
        SupabaseRestClient client = null;
        try {
            client = SupabaseClients.fromLocalDatabase();
        } catch (Exception e) {
            System.err.println("Could not load Supabase credentials from local DB: " + e.getMessage());
            return;
        }

        if (client == null) {
            System.err.println("Supabase client is null. Check settings.");
            return;
        }

        String projectUrl = client.restV1Base();
        System.out.println("Local database SQLite connection url: " + DBConnection.getUrl());
        System.out.println("Remote Supabase project endpoint: " + projectUrl);

        try {
            // 2. Fetch remote OpenAPI schema from PostgREST root
            System.out.println("\nFetching remote OpenAPI schema...");
            HttpResponse<String> res = client.getRawPath("", null);
            if (res.statusCode() != 200) {
                System.err.println("Failed to fetch OpenAPI schema. HTTP status: " + res.statusCode());
                System.err.println("Response: " + res.body());
                return;
            }

            JsonObject openApi = JsonParser.parseString(res.body()).getAsJsonObject();
            JsonObject definitions = openApi.getAsJsonObject("definitions");
            if (definitions == null) {
                System.err.println("Invalid schema: 'definitions' block is missing.");
                return;
            }

            System.out.println("✔ Successfully fetched remote schema with " + definitions.size() + " tables.");

            // 3. Connect to local SQLite database and analyze schema
            try (Connection conn = DBConnection.getConnection()) {
                DatabaseMetaData dbMeta = conn.getMetaData();
                
                String[] tablesToCheck = {
                    "users", "clients", "suppliers", "jobs", "job_items", 
                    "printing_items", "paper_items", "binding_items", "lamination_items", "ctp_items", 
                    "invoice_master", "invoice_job_mapping", "invoice_additional_charges", 
                    "invoice_adjustments", "payments", "payment_allocations", 
                    "company_details", "bank_details", "hsn_sac_master", "document_number_mappings"
                };

                int totalMismatches = 0;

                for (String tableName : tablesToCheck) {
                    System.out.println("\n-------------------------------------------");
                    System.out.println("Checking table: " + tableName.toUpperCase());
                    
                    // Check if table exists locally
                    boolean localExists = false;
                    try (ResultSet tables = dbMeta.getTables(null, null, tableName, null)) {
                        if (tables.next()) {
                            localExists = true;
                        }
                    }

                    if (!localExists) {
                        System.out.println("❌ Local: Table DOES NOT exist in SQLite!");
                        totalMismatches++;
                        continue;
                    }
                    System.out.println("✔ Local: Table exists.");

                    // Check if table exists on remote
                    JsonObject remoteTable = definitions.getAsJsonObject(tableName);
                    if (remoteTable == null) {
                        System.out.println("❌ Remote: Table DOES NOT exist in Supabase!");
                        totalMismatches++;
                        continue;
                    }
                    System.out.println("✔ Remote: Table exists.");

                    // Read local columns
                    Map<String, String> localCols = new LinkedHashMap<>();
                    try (ResultSet cols = dbMeta.getColumns(null, null, tableName, null)) {
                        while (cols.next()) {
                            String colName = cols.getString("COLUMN_NAME");
                            String colType = cols.getString("TYPE_NAME");
                            localCols.put(colName.toLowerCase(), colType);
                        }
                    }

                    // Read remote columns
                    JsonObject properties = remoteTable.getAsJsonObject("properties");
                    Map<String, String> remoteCols = new LinkedHashMap<>();
                    if (properties != null) {
                        for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
                            String colName = entry.getKey().toLowerCase();
                            JsonObject colDef = entry.getValue().getAsJsonObject();
                            String type = colDef.has("type") ? colDef.get("type").getAsString() : "unknown";
                            if (colDef.has("format")) {
                                type += " (" + colDef.get("format").getAsString() + ")";
                            }
                            remoteCols.put(colName, type);
                        }
                    }

                    // Compare columns
                    List<String> missingOnRemote = new ArrayList<>();
                    List<String> typeMismatches = new ArrayList<>();

                    for (Map.Entry<String, String> entry : localCols.entrySet()) {
                        String colName = entry.getKey();
                        String localType = entry.getValue();

                        if (!remoteCols.containsKey(colName)) {
                            // Skip local-only columns like SQLite rowid or temporary columns if any
                            missingOnRemote.add(colName + " (" + localType + ")");
                        } else {
                            String remoteType = remoteCols.get(colName);
                            // Simple type check comparison
                            boolean typeMatch = checkTypeCompatibility(localType, remoteType, tableName, colName);
                            if (!typeMatch) {
                                typeMismatches.add(colName + " [Local: " + localType + " | Remote: " + remoteType + "]");
                            }
                        }
                    }

                    if (missingOnRemote.isEmpty() && typeMismatches.isEmpty()) {
                        System.out.println("✔ Schema Match: All local columns exist on remote with compatible types.");
                    } else {
                        if (!missingOnRemote.isEmpty()) {
                            System.out.println("⚠️ Columns missing on remote: " + missingOnRemote);
                            totalMismatches += missingOnRemote.size();
                        }
                        if (!typeMismatches.isEmpty()) {
                            System.out.println("⚠️ Column type mismatches: ");
                            for (String mismatch : typeMismatches) {
                                System.out.println("   - " + mismatch);
                            }
                            totalMismatches += typeMismatches.size();
                        }
                    }
                }

                System.out.println("\n===========================================");
                if (totalMismatches == 0) {
                    System.out.println("🎉 SCHEMA VERIFICATION SUCCESSFUL! No mismatch found.");
                } else {
                    System.out.println("❌ SCHEMA VERIFICATION COMPLETED with " + totalMismatches + " issues/mismatches.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean checkTypeCompatibility(String localType, String remoteType, String tableName, String colName) {
        String lt = localType.toUpperCase();
        String rt = remoteType.toLowerCase();

        // Standard mapping compatibility rules
        if (lt.contains("INT") || lt.contains("NUM")) {
            // Integer fields can map to integer, numeric, smallint, bigint, or boolean
            return rt.contains("integer") || rt.contains("numeric") || rt.contains("boolean") || rt.contains("number") || rt.contains("smallint");
        }
        if (lt.contains("BOOLEAN") || lt.contains("BOOL")) {
            return rt.contains("boolean") || rt.contains("integer") || rt.contains("numeric");
        }
        if (lt.contains("REAL") || lt.contains("DOUBLE") || lt.contains("FLOAT")) {
            return rt.contains("number") || rt.contains("numeric") || rt.contains("precision");
        }
        if (lt.contains("TEXT") || lt.contains("CHAR") || lt.contains("VARCHAR")) {
            return rt.contains("string") || rt.contains("text") || rt.contains("character");
        }
        return true;
    }
}
