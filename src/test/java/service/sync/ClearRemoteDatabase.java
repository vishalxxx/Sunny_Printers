package service.sync;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseRestClient;
import java.net.http.HttpResponse;

public class ClearRemoteDatabase {
    public static void main(String[] args) {
        String projectRoot = "https://ajdekyhiqgqvufdmaugf.supabase.co";
        // Using the secret key extracted from DB
        String secretKey = "sb_secret_FpTQW36FupOx4BOemZpWBA_WnjEdhNu";

        System.out.println("Initializing Supabase Client...");
        SupabaseRestClient client = new SupabaseRestClient(projectRoot, secretKey);

        SupabaseEndpoints[] tablesToDelete = {
            SupabaseEndpoints.PRINTING_ITEMS,
            SupabaseEndpoints.PAPER_ITEMS,
            SupabaseEndpoints.BINDING_ITEMS,
            SupabaseEndpoints.LAMINATION_ITEMS,
            SupabaseEndpoints.CTP_ITEMS,
            SupabaseEndpoints.JOB_ITEMS,
            SupabaseEndpoints.JOBS,
            SupabaseEndpoints.PAYMENT_ALLOCATIONS,
            SupabaseEndpoints.PAYMENTS,
            SupabaseEndpoints.INVOICE_JOB_MAPPING,
            SupabaseEndpoints.INVOICE_MASTER
        };

        // PostgREST filter - "uuid=not.is.null" applies to all rows
        String filter = "uuid=not.is.null";

        for (SupabaseEndpoints table : tablesToDelete) {
            try {
                System.out.println("Deleting from remote table " + table.name() + "...");
                HttpResponse<String> res = client.delete(table, filter);
                System.out.println("Result: " + res.statusCode() + " " + res.body());
            } catch (Exception e) {
                System.err.println("Failed to delete from " + table.name() + ": " + e.getMessage());
            }
        }

        // Also try raw delete for job_cancellation_audit in case it exists on remote
        try {
            System.out.println("Deleting from remote table job_cancellation_audit...");
            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(client.restV1Base() + "job_cancellation_audit?" + filter))
                .DELETE()
                .header("apikey", secretKey)
                .header("Authorization", "Bearer " + secretKey)
                .build();
            var res = httpClient.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            System.out.println("Result (job_cancellation_audit): " + res.statusCode() + " " + res.body());
        } catch (Exception e) {
            System.err.println("Failed to delete from job_cancellation_audit: " + e.getMessage());
        }
        System.out.println("Remote database clearing process finished.");
    }
}
