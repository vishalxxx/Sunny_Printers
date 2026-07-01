package release;

import org.junit.jupiter.api.Tag;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseGate;
import api.supabase.SupabaseRestClient;
import utils.DBConnection;
import utils.DatabaseInitializer;

@Tag("release")
public class LegacyIdentityRepairTest {

    @Test
    public void runIdentityRepair() throws Exception {
        System.out.println("Starting Legacy Identity Repair...");
        
        try {
            DatabaseInitializer.initialize();
        } catch (Exception e) {}
        
        var optClient = SupabaseGate.restClientIfConfigured();
        if (optClient.isEmpty()) {
            System.out.println("Supabase not configured. Cannot perform remote identity repair.");
            return;
        }
        
        SupabaseRestClient http = optClient.get();
        
        int mismatchesFound = 0;
        int repaired = 0;
        int skipped = 0;
        
        try (Connection con = DBConnection.getConnection()) {
            
            // We only need to check PENDING rows since SYNCED rows already match remote by definition.
            String queryLocal = "SELECT uuid, invoice_uuid, job_uuid FROM invoice_job_mapping WHERE sync_status = 'PENDING'";
            
            List<MappingDef> pendingMappings = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(queryLocal);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pendingMappings.add(new MappingDef(
                        rs.getString("uuid"),
                        rs.getString("invoice_uuid"),
                        rs.getString("job_uuid")
                    ));
                }
            }
            
            System.out.println("Found " + pendingMappings.size() + " PENDING local mappings. Checking identities against Supabase...");
            
            for (MappingDef def : pendingMappings) {
                // Query Supabase for this exact (invoice_uuid, job_uuid)
                String filter = "invoice_uuid=eq." + def.invoiceUuid + "&job_uuid=eq." + def.jobUuid;
                var res = http.get(SupabaseEndpoints.INVOICE_JOB_MAPPING, filter);
                
                if (res.statusCode() >= 200 && res.statusCode() < 300) {
                    com.google.gson.JsonArray remoteRows = com.google.gson.JsonParser.parseString(res.body()).getAsJsonArray();
                    if (remoteRows.size() > 0) {
                        // Remote exists!
                        com.google.gson.JsonObject remoteRow = remoteRows.get(0).getAsJsonObject();
                        String remoteUuid = remoteRow.get("uuid").getAsString();
                        
                        if (!remoteUuid.equals(def.uuid)) {
                            mismatchesFound++;
                            System.out.println("Mismatch Found: Local UUID [" + def.uuid + "] vs Remote UUID [" + remoteUuid + "]");
                            System.out.println("  For keys: Invoice=" + def.invoiceUuid + " Job=" + def.jobUuid);
                            
                            // Repair! Update local UUID to match remote UUID, and mark as SYNCED
                            String updateSql = "UPDATE invoice_job_mapping SET uuid = ?, sync_status = 'SYNCED' WHERE uuid = ?";
                            try (PreparedStatement psUp = con.prepareStatement(updateSql)) {
                                psUp.setString(1, remoteUuid);
                                psUp.setString(2, def.uuid);
                                int affected = psUp.executeUpdate();
                                if (affected > 0) {
                                    System.out.println("  -> Successfully repaired identity. Local UUID adopted remote identity.");
                                    repaired++;
                                } else {
                                    System.out.println("  -> Failed to repair. Row not found or locked.");
                                }
                            }
                        } else {
                            skipped++;
                        }
                    } else {
                        // Remote does not exist. It's a genuine new pending row, keep it!
                        skipped++;
                    }
                } else {
                    System.err.println("Failed to query remote for " + def.uuid + " - HTTP " + res.statusCode());
                    skipped++;
                }
            }
            
            System.out.println("\n--- Legacy Identity Repair Summary ---");
            System.out.println("Legacy Mismatches Found: " + mismatchesFound);
            System.out.println("Mappings Repaired (Adopted Remote UUID): " + repaired);
            System.out.println("Rows Skipped (Genuine new rows or verified stable): " + skipped);
            System.out.println("Final Verification Result: " + (mismatchesFound == repaired ? "SUCCESS" : "PARTIAL/FAILURE"));
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static class MappingDef {
        String uuid;
        String invoiceUuid;
        String jobUuid;
        
        MappingDef(String uuid, String invoiceUuid, String jobUuid) {
            this.uuid = uuid;
            this.invoiceUuid = invoiceUuid;
            this.jobUuid = jobUuid;
        }
    }
}

