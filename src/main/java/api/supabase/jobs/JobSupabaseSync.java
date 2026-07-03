package api.supabase.jobs;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.CompletableFuture;

import api.supabase.SupabaseGate;
import api.supabase.SupabaseRestClient;
import api.supabase.clients.ClientsSupabaseApi;
import api.supabase.invoices.InvoicesSupabaseApi;
import model.Client;
import model.InvoiceMaster;
import model.Job;
import repository.ClientRepository;
import repository.InvoiceMasterRepository;
import repository.JobRepository;
import utils.DBConnection;
import utils.DocumentNumbering;

/** Fire-and-forget dual-write of jobs to Supabase when configured. */
public final class JobSupabaseSync {

	private JobSupabaseSync() {
	}

	public static void pushAsync(Job job) {
		if (job == null || !job.hasUuid()) {
			return;
		}
		SupabaseGate.restClientIfConfigured().ifPresent(http -> {
			Runnable task = () -> {
				String uuid = job.getUuid().trim();
				try {
					upsertToRemote(http, job);
					markSyncedLocally(uuid);
				} catch (Exception ex) {
					System.err.println("[Supabase jobs] remote write failed for uuid=" + uuid + ": " + ex.getMessage());
				}
			};
			if (SupabaseGate.isOverrideActive()) {
				task.run();
			} else {
				CompletableFuture.runAsync(task);
			}
		});
	}

	/**
	 * FK-safe push: client, linked invoice (including TEMP drafts), then job with {@code invoice_uuid}.
	 */
	public static void upsertToRemote(SupabaseRestClient http, Job job) throws Exception {
		if (job == null || !job.hasUuid()) {
			return;
		}
		Job loaded = new JobRepository().findJobByUuid(job.getUuid());
		if (loaded != null) {
			job = loaded;
		}
		ensureClientOnRemote(http, job.getClientUuid());

		String savedInvoiceUuid = job.getInvoiceUuid();
		try {
			if (savedInvoiceUuid != null && !savedInvoiceUuid.isBlank()) {
				try (Connection con = DBConnection.getConnection()) {
					InvoiceMaster inv = new InvoiceMasterRepository().findByUuid(con, savedInvoiceUuid.trim());
					if (inv != null) {
						if (DocumentNumbering.isTemporaryNumber(inv.getInvoiceNo())) {
							job.setInvoiceUuid(null);
						} else {
							new InvoicesSupabaseApi(http).upsert(inv);
							markInvoiceSyncedLocally(savedInvoiceUuid.trim());
						}
					} else {
						System.err.println("[JobSupabaseSync] job " + job.getUuid()
								+ ": invoice " + savedInvoiceUuid + " missing locally");
						job.setInvoiceUuid(null);
					}
				}
			}
			new JobsSupabaseApi(http).upsert(job);
		} finally {
			job.setInvoiceUuid(savedInvoiceUuid);
		}
	}

	private static void ensureClientOnRemote(SupabaseRestClient http, String clientUuid) throws Exception {
		if (clientUuid == null || clientUuid.isBlank()) {
			return;
		}
		Client client = new ClientRepository().findByUuid(clientUuid.trim());
		if (client == null) {
			return;
		}
		new ClientsSupabaseApi(http).upsert(client);
		markClientSyncedLocally(clientUuid.trim());
	}

	private static void markInvoiceSyncedLocally(String invoiceUuid) {
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(
						"UPDATE invoice_master SET sync_status='SYNCED', synced_at=datetime('now') WHERE uuid=?")) {
			ps.setString(1, invoiceUuid);
			ps.executeUpdate();
		} catch (Exception e) {
			System.err.println("[JobSupabaseSync] mark invoice synced: " + e.getMessage());
		}
	}

	private static void markClientSyncedLocally(String clientUuid) {
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(
						"UPDATE clients SET sync_status='SYNCED', synced_at=datetime('now') WHERE uuid=?")) {
			ps.setString(1, clientUuid);
			ps.executeUpdate();
		} catch (Exception ignored) {
		}
	}

	private static void markSyncedLocally(String jobUuid) {
		if (jobUuid == null || jobUuid.isBlank()) {
			return;
		}
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(
						"UPDATE jobs SET sync_status='SYNCED', synced_at=datetime('now') WHERE uuid=?")) {
			ps.setString(1, jobUuid.trim());
			ps.executeUpdate();
		} catch (Exception e) {
			System.err.println("[Supabase jobs] failed to mark job synced locally: " + e.getMessage());
		}
	}

}
