package integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import api.supabase.SupabaseGate;
import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseRestClient;
import model.SupabaseSettings;
import repository.SupabaseSettingsRepository;

import java.util.List;
import java.util.ArrayList;

@Tag("integration")
public class SchemaCacheAuditTest {

    @Test
    public void runSchemaCacheAudit() throws Exception {
        System.out.println("Starting Zero-Impact Schema Cache Validation...");
        
        var optClient = SupabaseGate.restClientIfConfigured();
        if (optClient.isEmpty()) {
            System.out.println("Supabase not configured. Skipping remote audit.");
            return;
        }
        
        // Use the authenticated anon key
        SupabaseSettings settings = new SupabaseSettingsRepository().load();
        String anonKey = settings.getAnonKey();
        if (anonKey == null || anonKey.isBlank()) {
            anonKey = "fake-anon-key";
        }
        SupabaseRestClient http = new SupabaseRestClient(optClient.get().restV1Base().replace("/rest/v1/", ""), anonKey);

        List<SupabaseEndpoints> targetEndpoints = List.of(
            SupabaseEndpoints.CLIENTS,
            SupabaseEndpoints.JOBS,
            SupabaseEndpoints.INVOICE_MASTER,
            SupabaseEndpoints.INVOICE_JOB_MAPPING,
            SupabaseEndpoints.PAYMENTS,
            SupabaseEndpoints.PAYMENT_ALLOCATIONS,
            SupabaseEndpoints.BANK_DETAILS,
            SupabaseEndpoints.COMPANY_DETAILS,
            SupabaseEndpoints.HSN_SAC_MASTER
        );
        
        List<String> failedTables = new ArrayList<>();
        int cacheErrors = 0;
        
        for (SupabaseEndpoints endpoint : targetEndpoints) {
            System.out.print("Verifying schema cache for: " + endpoint.pathSegment() + " ... ");
            
            try {
                // Sending an empty array [] forces PostgREST to validate the target schema and constraints 
                // against its cache without actually inserting any rows.
                var res = http.postJsonWithQuery(endpoint, "on_conflict=uuid", "[]", "resolution=merge-duplicates,return=minimal");
                
                if (res.statusCode() >= 200 && res.statusCode() < 300) {
                    System.out.println("OK");
                } else if (res.statusCode() == 400 && res.body().contains("PGRST204")) {
                    System.out.println("FAILED (PGRST204 Schema Cache Stale)");
                    cacheErrors++;
                    failedTables.add(endpoint.pathSegment());
                } else {
                    System.out.println("FAILED (HTTP " + res.statusCode() + " - " + res.body() + ")");
                    failedTables.add(endpoint.pathSegment() + " [" + res.statusCode() + "]");
                }
            } catch (Exception e) {
                System.out.println("FAILED (" + e.getMessage() + ")");
                failedTables.add(endpoint.pathSegment());
            }
        }
        
        System.out.println("\n--- PostgREST Schema Cache Audit Summary ---");
        System.out.println("Tables Audited: " + targetEndpoints.size());
        System.out.println("Schema Mismatches Found: " + failedTables.size());
        System.out.println("Cache-Related Errors Found: " + cacheErrors);
        
        if (failedTables.isEmpty()) {
            System.out.println("Recovery Validation: SUCCESS. All tables successfully matched 'uuid' conflict constraints.");
            System.out.println("Final Verification Result: PASS");
        } else {
            System.out.println("Recovery Validation: FAILED.");
            System.out.println("Failed Tables: " + String.join(", ", failedTables));
            System.out.println("Final Verification Result: FAIL (Run 'NOTIFY pgrst, ''reload schema'';' on Supabase)");
            fail("Schema Cache blockages detected on " + failedTables.size() + " tables.");
        }
    }
}

