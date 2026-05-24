package service.sync;



import java.sql.Connection;

import java.sql.PreparedStatement;

import java.util.List;

import java.util.concurrent.CompletableFuture;



import api.supabase.SupabaseGate;

import api.supabase.SupabaseReachability;

import api.supabase.SupabaseRestClient;

import api.supabase.clients.ClientsSupabaseApi;

import api.supabase.invoices.InvoicesSupabaseApi;

import api.supabase.jobs.JobSupabaseSync;

import api.supabase.payments.PaymentsSupabaseApi;

import api.supabase.sequences.NumberSequenceSupabaseSync;
import api.supabase.SupabaseEndpoints;

import model.Client;

import model.InvoiceMaster;

import model.Job;

import model.Payment;

import repository.ClientRepository;

import repository.InvoiceMasterRepository;

import repository.JobRepository;

import repository.PaymentRepository;

import utils.DBConnection;

import utils.DocumentNumbering;



/**

 * Global sync: promote TEMP-* numbers, push sequences, then pending entities in FK order:

 * Client -> Invoice -> Job -> Payment -> other tables (jobs may reference invoice_uuid).

 */

public final class UniversalSyncEngine {



	private static final UniversalSyncEngine INSTANCE = new UniversalSyncEngine();



	private final ClientRepository clientRepo = new ClientRepository();

	private final JobRepository jobRepo = new JobRepository();

	private final InvoiceMasterRepository invoiceRepo = new InvoiceMasterRepository();

	private final PaymentRepository paymentRepo = new PaymentRepository();



	private UniversalSyncEngine() {

	}



	public static UniversalSyncEngine getInstance() {

		return INSTANCE;

	}



	public static void scheduleSyncAsync() {

		if (SupabaseGate.restClientIfConfigured().isEmpty()) {

			return;

		}

		CompletableFuture.runAsync(() -> {

			if (!SupabaseReachability.isReachable()) {

				return;

			}

			SyncReport report = syncAllPending();

			if (report.totalSynced() > 0 || report.failures > 0 || report.pendingRemaining > 0) {

				System.out.println("[UniversalSyncEngine] " + report);

			}

		});

	}



	public static boolean hasPendingWork() {

		if (SupabaseGate.restClientIfConfigured().isEmpty()) {

			return false;

		}

		return countStillPending() > 0;

	}



	public static SyncReport syncAllPending() {
		SyncReport report = new SyncReport();
		var httpOpt = SupabaseGate.restClientIfConfigured();
		if (httpOpt.isEmpty()) {
			return report;
		}
		if (!SupabaseReachability.isReachable()) {
			return report;
		}
		SupabaseRestClient http = httpOpt.get();

		try {
			report.tempCodesPromoted = TemporaryDocumentReconciliation.reconcileAll();

			// 1. users
			report.othersSynced += OtherPendingEntitiesSync.syncTable(http, new OtherPendingEntitiesSync.TableDef("users", SupabaseEndpoints.USERS, "uuid", false), report);

			// 2. clients
			report.clientsSynced = INSTANCE.syncPendingClients(http, report);

			// 3. suppliers
			report.othersSynced += OtherPendingEntitiesSync.syncTable(http, new OtherPendingEntitiesSync.TableDef("suppliers", SupabaseEndpoints.SUPPLIERS, "uuid", false), report);

			// 4. number_sequences
			report.numberSequencesSynced = NumberSequenceSupabaseSync.syncRemoteToLocal(http);

			// 5. jobs
			report.jobsSynced = INSTANCE.syncPendingJobs(http, report);

			// 6. invoice_master
			report.invoicesSynced = INSTANCE.syncPendingInvoices(http, report);

			// 7. payments
			report.paymentsSynced = INSTANCE.syncPendingPayments(http, report);

			// 8. job_items
			report.othersSynced += OtherPendingEntitiesSync.syncTable(http, new OtherPendingEntitiesSync.TableDef("job_items", SupabaseEndpoints.JOB_ITEMS, "uuid", false), report);

			// 9. invoice_job_mapping
			report.othersSynced += OtherPendingEntitiesSync.syncTable(http, new OtherPendingEntitiesSync.TableDef("invoice_job_mapping", SupabaseEndpoints.INVOICE_JOB_MAPPING, "uuid", false), report);

			// 10. printing_items
			report.othersSynced += OtherPendingEntitiesSync.syncTable(http, new OtherPendingEntitiesSync.TableDef("printing_items", SupabaseEndpoints.PRINTING_ITEMS, "uuid", false), report);

			// 11. paper_items
			report.othersSynced += OtherPendingEntitiesSync.syncTable(http, new OtherPendingEntitiesSync.TableDef("paper_items", SupabaseEndpoints.PAPER_ITEMS, "uuid", false), report);

			// 12. binding_items
			report.othersSynced += OtherPendingEntitiesSync.syncTable(http, new OtherPendingEntitiesSync.TableDef("binding_items", SupabaseEndpoints.BINDING_ITEMS, "uuid", false), report);

			// 13. lamination_items
			report.othersSynced += OtherPendingEntitiesSync.syncTable(http, new OtherPendingEntitiesSync.TableDef("lamination_items", SupabaseEndpoints.LAMINATION_ITEMS, "uuid", false), report);

			// 14. ctp_items
			report.othersSynced += OtherPendingEntitiesSync.syncTable(http, new OtherPendingEntitiesSync.TableDef("ctp_items", SupabaseEndpoints.CTP_ITEMS, "uuid", false), report);

			// 15. invoice_adjustments
			report.othersSynced += OtherPendingEntitiesSync.syncTable(http, new OtherPendingEntitiesSync.TableDef("invoice_adjustments", SupabaseEndpoints.INVOICE_ADJUSTMENTS, "uuid", true), report);

			// 16. payment_allocations
			report.othersSynced += OtherPendingEntitiesSync.syncTable(http, new OtherPendingEntitiesSync.TableDef("payment_allocations", SupabaseEndpoints.PAYMENT_ALLOCATIONS, "uuid", false), report);

			// 17. payment_details
			report.othersSynced += OtherPendingEntitiesSync.syncTable(http, new OtherPendingEntitiesSync.TableDef("payment_details", SupabaseEndpoints.PAYMENT_DETAILS, "uuid", false), report);

			report.pendingRemaining = countStillPending();
		} catch (Exception e) {
			report.failures++;
			System.err.println("[UniversalSyncEngine] sync failed: " + e.getMessage());
			SupabaseReachability.invalidateCache();
		}
		return report;
	}



	private static int countStillPending() {

		String pending = PendingSyncFilters.PENDING_STATUS;

		try (Connection conn = DBConnection.getConnection()) {

			int total = 0;

			total += countPending(conn, "clients", "client_code", pending);

			total += countPending(conn, "jobs", "job_code", pending);

			total += countPending(conn, "invoice_master", "invoice_no", pending);

			total += countPending(conn, "payments", null, pending);

			total += countPending(conn, "job_items", null, pending);

			total += countPending(conn, "payment_allocations", null, pending);

			return total;

		} catch (Exception ignored) {

			return 0;

		}

	}



	private static int countPending(Connection conn, String table, String codeColumn, String pendingClause)

			throws Exception {

		if (!sqliteTableExists(conn, table) || !sqliteColumnExists(conn, table, "sync_status")) {

			return 0;

		}

		String deleted = sqliteColumnExists(conn, table, "is_deleted") ? "IFNULL(is_deleted,0)=0 AND " : "";

		String predicate = codeColumn != null && sqliteColumnExists(conn, table, codeColumn)

				? "(" + codeColumn + " LIKE 'TEMP-%' OR " + pendingClause + ")"

				: pendingClause;

		String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + deleted + predicate;

		try (var ps = conn.prepareStatement(sql);

				var rs = ps.executeQuery()) {

			return rs.next() ? rs.getInt(1) : 0;

		}

	}



	private static boolean sqliteTableExists(Connection conn, String table) throws Exception {

		try (var ps = conn.prepareStatement(

				"SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {

			ps.setString(1, table);

			try (var rs = ps.executeQuery()) {

				return rs.next();

			}

		}

	}



	private static boolean sqliteColumnExists(Connection conn, String table, String column) throws Exception {

		try (var ps = conn.prepareStatement("PRAGMA table_info(" + table + ")")) {

			try (var rs = ps.executeQuery()) {

				while (rs.next()) {

					if (column.equalsIgnoreCase(rs.getString("name"))) {

						return true;

					}

				}

			}

		}

		return false;

	}



	private int syncPendingClients(SupabaseRestClient http, SyncReport report) {

		int synced = 0;

		ClientsSupabaseApi api = new ClientsSupabaseApi(http);

		for (Client client : clientRepo.findPendingForSync()) {

			if (client == null || !client.hasClientUuid()) {

				continue;

			}

			if (DocumentNumbering.isTemporaryNumber(client.getClientCode())) {

				continue;

			}

			try {

				api.upsert(client);

				markTableSynced("clients", client.getClientUuid());

				synced++;

			} catch (Exception e) {

				if (isForeignKeyFailure(e)) {
					markTableWaitingDependency("clients", "uuid", client.getClientUuid());
				} else {
					report.failures++;
				}

				System.err.println("[UniversalSyncEngine] client " + client.getClientUuid() + ": " + e.getMessage());

			}

		}

		return synced;

	}



	private int syncPendingJobs(SupabaseRestClient http, SyncReport report) {

		int synced = 0;

		for (Job job : jobRepo.findPendingForSync()) {

			if (job == null || !job.hasUuid()) {

				continue;

			}

			if (DocumentNumbering.isTemporaryNumber(job.getJobCode())) {

				continue;

			}

			try {

				JobSupabaseSync.upsertToRemote(http, job);

				markTableSynced("jobs", job.getUuid());

				synced++;

			} catch (Exception e) {

				if (isForeignKeyFailure(e)) {
					markTableWaitingDependency("jobs", "uuid", job.getUuid());
				} else {
					report.failures++;
				}

				System.err.println("[UniversalSyncEngine] job " + job.getUuid() + ": " + e.getMessage());

			}

		}

		return synced;

	}



	private int syncPendingInvoices(SupabaseRestClient http, SyncReport report) {

		int synced = 0;

		InvoicesSupabaseApi api = new InvoicesSupabaseApi(http);

		for (InvoiceMaster inv : invoiceRepo.findPendingForSync()) {

			if (inv == null || inv.getUuid() == null || inv.getUuid().isBlank()) {

				continue;

			}

			if (DocumentNumbering.isTemporaryNumber(inv.getInvoiceNo())) {

				continue;

			}

			try {

				api.upsert(inv);

				markTableSynced("invoice_master", inv.getUuid());

				synced++;

			} catch (Exception e) {

				if (isForeignKeyFailure(e)) {
					markTableWaitingDependency("invoice_master", "uuid", inv.getUuid());
				} else {
					report.failures++;
				}

				System.err.println("[UniversalSyncEngine] invoice " + inv.getUuid() + ": " + e.getMessage());

			}

		}

		return synced;

	}



	private int syncPendingPayments(SupabaseRestClient http, SyncReport report) {

		int synced = 0;

		PaymentsSupabaseApi api = new PaymentsSupabaseApi(http);

		for (Payment payment : paymentRepo.findPendingForSync()) {

			if (payment == null || payment.getUuid() == null || payment.getUuid().isBlank()) {

				continue;

			}

			try {

				api.upsert(payment);

				markTableSynced("payments", payment.getUuid());

				synced++;

			} catch (Exception e) {

				if (isForeignKeyFailure(e)) {
					markTableWaitingDependency("payments", "uuid", payment.getUuid());
				} else {
					report.failures++;
				}

				System.err.println("[UniversalSyncEngine] payment " + payment.getUuid() + ": " + e.getMessage());

			}

		}

		return synced;

	}



	private static void markTableSynced(String table, String uuid) {

		if (uuid == null || uuid.isBlank()) {

			return;

		}

		try (Connection conn = DBConnection.getConnection();

				PreparedStatement ps = conn.prepareStatement(

						"UPDATE " + table + " SET sync_status='SYNCED', synced_at=datetime('now') WHERE uuid=?")) {

			ps.setString(1, uuid.trim());

			ps.executeUpdate();

		} catch (Exception ignored) {

		}

	}

	public static boolean isForeignKeyFailure(Throwable t) {
		if (t == null) {
			return false;
		}
		String msg = t.getMessage();
		if (msg != null && msg.contains("23503")) {
			return true;
		}
		if (t.getCause() != null && t.getCause() != t) {
			return isForeignKeyFailure(t.getCause());
		}
		return false;
	}

	public static void markTableWaitingDependency(String table, String uuidColumn, String uuid) {
		if (uuid == null || uuid.isBlank()) {
			return;
		}
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(
						"UPDATE " + table + " SET sync_status='WAITING_DEPENDENCY' WHERE " + uuidColumn + "=?")) {
			ps.setString(1, uuid.trim());
			ps.executeUpdate();
			System.out.println("[UniversalSyncEngine] Marked " + table + " " + uuid + " as WAITING_DEPENDENCY due to FK violation (23503)");
		} catch (Exception e) {
			System.err.println("Failed to update status to WAITING_DEPENDENCY for table " + table + ": " + e.getMessage());
		}
	}

}