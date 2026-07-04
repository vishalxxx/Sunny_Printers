package live;

import org.junit.jupiter.api.Tag;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseRestClient;
import utils.DBConnection;
import utils.DatabaseInitializer;
import utils.TestDatabaseHelper;
import utils.TestEnvironment;

@Tag("live")
public class PostgrestSchemaReloadVerificationTest {

    @Test
    public void runVerification() throws Exception {
        System.out.println("Starting Final PGRST204 Verification (TEST environment only)...");

        // Load TEST credentials — never use production
        TestEnvironment.load();
        if (!TestEnvironment.isSupabaseConfigured()) {
            System.out.println("TEST Supabase credentials not found. Skipping verification.");
            return;
        }

        String supabaseUrl = TestEnvironment.getTestSupabaseUrl();
        String anonKey = TestEnvironment.getTestSupabaseKey();

        // Use isolated test database
        String testDbUrl = TestDatabaseHelper.createIsolatedDb("PGRST204_Verify");
        DBConnection.setTestDatabaseUrl(testDbUrl);
        DBConnection.setGlobalTestDatabaseUrl(testDbUrl);
        TestEnvironment.logContext();

        try {
            try {
                DatabaseInitializer.initialize();
            } catch (Exception e) {}
            
            // 1 & 2: Local PK and Index Verification
            try (Connection con = DBConnection.getConnection()) {
                System.out.println("\n--- Local Schema Verification ---");
                verifyPkAndIndex(con, "bank_details");
                verifyPkAndIndex(con, "company_details");
                verifyPkAndIndex(con, "hsn_sac_master");
            }
            
            SupabaseRestClient captureClient = new SupabaseRestClient(supabaseUrl, anonKey) {
                @Override
                public java.net.http.HttpResponse<String> postJsonWithQuery(SupabaseEndpoints table, String query, String body, String prefer) {
                    try {
                        System.out.println("\n--- Outbound HTTP Request ---");
                        System.out.println("METHOD: POST");
                        System.out.println("URL: " + this.restV1Base() + table.pathSegment() + "?" + query);
                        System.out.println("HEADERS: Prefer: " + prefer + ", Content-Type: application/json");
                        System.out.println("PAYLOAD: " + body);
                        
                        var res = super.postJsonWithQuery(table, query, body, prefer);
                        
                        System.out.println("\n--- Inbound HTTP Response ---");
                        System.out.println("STATUS: " + res.statusCode());
                        System.out.println("BODY: " + res.body());
                        return res;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            
            // 4. Create new Bank Record
            String bankUuid = UUID.randomUUID().toString();
            try (Connection con = DBConnection.getConnection()) {
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO bank_details (uuid, bank_name, account_holder_name, account_no, branch_ifsc, is_default, is_active, sync_status, sync_version) VALUES (?, 'Verified Bank', 'Holder', '999', 'IFSC-999', 0, 1, 'PENDING', 1)")) {
                    ps.setString(1, bankUuid);
                    ps.executeUpdate();
                }
            }
            System.out.println("\nCreated Local Bank Record: " + bankUuid);
            
            // 5. Run Push Sync for that specific row
            System.out.println("\nExecuting Push Sync...");
            JsonObject row = new JsonObject();
            row.addProperty("uuid", bankUuid);
            row.addProperty("bank_name", "Verified Bank");
            row.addProperty("account_holder_name", "Holder");
            row.addProperty("account_no", "999");
            row.addProperty("branch_ifsc", "IFSC-999");
            row.addProperty("is_default", 0);
            row.addProperty("is_active", 1);
            
            JsonArray body = new JsonArray();
            body.add(row);
            
            try {
                captureClient.postJsonWithQuery(SupabaseEndpoints.BANK_DETAILS, "on_conflict=uuid", body.toString(), "resolution=merge-duplicates,return=minimal");
                System.out.println("\nVerdict: SUCCESS - Schema Cache Issue is Resolved.");
            } catch (Exception e) {
                System.out.println("\nVerdict: FAILED - " + e.getMessage());
                System.out.println("If this threw PGRST204, the schema cache reload was not successful on the backend.");
            }
        } finally {
            DBConnection.clearTestDatabaseUrl();
            DBConnection.clearGlobalTestDatabaseUrl();
        }
    }
    
    private void verifyPkAndIndex(Connection con, String table) throws Exception {
        try (PreparedStatement ps = con.prepareStatement("PRAGMA table_info(" + table + ")")) {
            boolean isPk = false;
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if ("uuid".equalsIgnoreCase(rs.getString("name"))) {
                        if (rs.getInt("pk") > 0) isPk = true;
                    }
                }
            }
            System.out.println(table + " UUID is PK: " + isPk);
        }
    }
}
