package live;
import utils.DBConnection;

import org.junit.jupiter.api.Tag;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import api.supabase.SupabaseGate;
import api.supabase.SupabaseRestClient;
import model.SupabaseSettings;
import repository.SupabaseSettingsRepository;
import service.sync.UniversalSyncEngine;

@Tag("live")
public class DatabaseResetUtility {

	private static final List<String> TABLES_TO_WIPE = Arrays.asList(
			"payment_allocations",
			"payment_details",
			"payments",
			"invoice_additional_charges",
			"invoice_adjustments",
			"invoice_history",
			"invoice_job_mapping",
			"invoice_master",
			"printing_items",
			"paper_items",
			"binding_items",
			"ctp_items",
			"lamination_items",
			"job_items",
			"job_cancellation_audit",
			"jobs",
			"clients",
			"suppliers",
			"sync_conflicts",
			"sync_metadata",
			"document_number_mappings"
	);

	@Test
	public void runReset() throws Exception {
		System.out.println("Starting Database Reset Utility...");
		
		// 1. Stop synchronization
		System.out.println("Ensuring UniversalSyncEngine is not active...");

		// Setup Supabase Client
		SupabaseSettings s = new SupabaseSettingsRepository().load();
		SupabaseRestClient http = new SupabaseRestClient(s.getSupabaseUrl(), s.getAnonKey());

		try (Connection conn = DBConnection.getExclusiveConnection()) {
			try (Statement stmt = conn.createStatement()) {
				// 2. Delete Data in Correct Dependency Order
				for (String table : TABLES_TO_WIPE) {
					System.out.println("Wiping table: " + table);
					
					// Local Wipe
					try {
						int deleted = stmt.executeUpdate("DELETE FROM " + table);
						System.out.println("  Local " + table + " deleted: " + deleted + " rows.");
					} catch (Exception e) {
						System.err.println("  Local wipe failed for " + table + ": " + e.getMessage());
					}

					// Remote Wipe
					try {
						String filter = "uuid=not.is.null";
						if (table.equals("invoice_job_mapping")) {
							filter = "invoice_uuid=not.is.null";
						} else if (table.equals("sync_conflicts") || table.equals("sync_metadata") || table.equals("job_cancellation_audit")) {
							continue; // Local only or no uuid easily identifiable
						}
						
						HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(s.getSupabaseUrl() + "/rest/v1/" + table + "?" + filter))
								.DELETE()
								.header("apikey", s.getAnonKey())
								.header("Authorization", "Bearer " + s.getAnonKey());
						
						HttpResponse<String> res = java.net.http.HttpClient.newHttpClient().send(req.build(), HttpResponse.BodyHandlers.ofString());
						if (res.statusCode() >= 200 && res.statusCode() < 300) {
							System.out.println("  Remote " + table + " deleted successfully.");
						} else {
							System.err.println("  Remote wipe returned HTTP " + res.statusCode() + " for " + table + ": " + res.body());
						}
					} catch (Exception e) {
						System.err.println("  Remote wipe failed for " + table + ": " + e.getMessage());
					}
				}

				// 4. Reset Number Sequences
				System.out.println("Resetting number sequences...");
				try {
					stmt.executeUpdate("UPDATE number_sequences SET current_number=0, offline_current_number=0");
					System.out.println("  Local number_sequences reset successfully.");
				} catch (Exception e) {
					System.err.println("  Local sequence reset failed: " + e.getMessage());
				}
				
				try {
					String payload = "{\"current_number\":0}";
					HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(s.getSupabaseUrl() + "/rest/v1/number_sequences?sequence_key=not.is.null"))
							.method("PATCH", HttpRequest.BodyPublishers.ofString(payload))
							.header("apikey", s.getAnonKey())
							.header("Authorization", "Bearer " + s.getAnonKey())
							.header("Content-Type", "application/json")
							.header("Prefer", "return=minimal");
					
					HttpResponse<String> res = java.net.http.HttpClient.newHttpClient().send(req.build(), HttpResponse.BodyHandlers.ofString());
					if (res.statusCode() >= 200 && res.statusCode() < 300) {
						System.out.println("  Remote number_sequences reset successfully.");
					} else {
						System.err.println("  Remote sequence reset returned HTTP " + res.statusCode() + ": " + res.body());
					}
				} catch (Exception e) {
					System.err.println("  Remote sequence reset failed: " + e.getMessage());
				}

				// 5. Verification
				System.out.println("\n--- FINAL VERIFICATION ---");
				for (String table : TABLES_TO_WIPE) {
					try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
						if (rs.next()) {
							System.out.println("Local " + table + ": " + rs.getInt(1) + " rows");
						}
					} catch (Exception ignored) {}
					
					// Verify remote
					try {
						HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(s.getSupabaseUrl() + "/rest/v1/" + table + "?select=*"))
								.GET()
								.header("apikey", s.getAnonKey())
								.header("Authorization", "Bearer " + s.getAnonKey())
								.header("Prefer", "count=exact,head=true");
						HttpResponse<Void> res = java.net.http.HttpClient.newHttpClient().send(req.build(), HttpResponse.BodyHandlers.discarding());
						String count = res.headers().firstValue("content-range").orElse("0-0/0").split("/")[1];
						System.out.println("Remote " + table + ": " + count + " rows");
					} catch (Exception ignored) {}
				}
			}
		}
		System.out.println("Database Reset Utility completed successfully.");
	}
}

