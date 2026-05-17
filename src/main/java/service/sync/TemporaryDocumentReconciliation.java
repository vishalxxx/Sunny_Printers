package service.sync;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import api.supabase.SupabaseReachability;
import model.DocumentNumberMapping;
import repository.DocumentNumberMappingRepository;
import service.NumberSequenceAllocationService.AllocatedNumber;
import model.MasterDocumentSeries;
import repository.InvoiceMasterRepository;
import utils.ClientIdentifiers;
import utils.DBConnection;
import utils.DocumentNumberEntityType;
import utils.InvoiceIdentifiers;
import utils.JobIdentifiers;

/**
 * Replaces TEMP-* document numbers with permanent sequence values when Supabase is online.
 */
public final class TemporaryDocumentReconciliation {

	private final UniversalNumberAllocator allocator = UniversalNumberAllocator.getInstance();
	private final DocumentNumberMappingRepository mappingRepo = new DocumentNumberMappingRepository();
	private final InvoiceMasterRepository invoiceRepo = new InvoiceMasterRepository();

	private TemporaryDocumentReconciliation() {
	}

	public static int reconcileAll() {
		if (!UniversalTemporaryNumberEngine.getInstance().isSupabaseConfigured()
				|| !SupabaseReachability.isReachable()) {
			return 0;
		}
		try {
			return new TemporaryDocumentReconciliation().reconcile();
		} catch (Exception e) {
			System.err.println("[TemporaryDocumentReconciliation] failed: " + e.getMessage());
			return 0;
		}
	}

	private int reconcile() throws Exception {
		int total = 0;
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			try {
				total += reconcileClients(con);
				total += reconcileJobs(con);
				total += reconcileInvoices(con);
				con.commit();
			} catch (Exception e) {
				con.rollback();
				throw e;
			}
		}
		return total;
	}

	private int reconcileClients(Connection con) throws Exception {
		List<ClientRow> rows = loadTempClients(con);
		int n = 0;
		for (ClientRow row : rows) {
			if (promoteClient(con, row)) {
				n++;
			}
		}
		return n;
	}

	private int reconcileJobs(Connection con) throws Exception {
		List<JobRow> rows = loadTempJobs(con);
		int n = 0;
		for (JobRow row : rows) {
			if (promoteJob(con, row)) {
				n++;
			}
		}
		return n;
	}

	private int reconcileInvoices(Connection con) throws Exception {
		List<InvoiceRow> rows = loadTempInvoices(con);
		int n = 0;
		for (InvoiceRow row : rows) {
			if (promoteInvoice(con, row)) {
				n++;
			}
		}
		return n;
	}

	private boolean promoteClient(Connection con, ClientRow row) throws Exception {
		Optional<AllocatedNumber> permanent = allocator.tryAllocatePermanentClientCode(con);
		if (permanent.isEmpty()) {
			return false;
		}
		String newCode = permanent.get().value();
		if (ClientIdentifiers.clientCodeInUse(con, newCode, row.uuid)) {
			return false;
		}
		try (var ps = con.prepareStatement("""
				UPDATE clients SET client_code = ?, sync_status = 'PENDING',
				sync_version = sync_version + 1, updated_at = datetime('now')
				WHERE uuid = ?
				""")) {
			ps.setString(1, newCode);
			ps.setString(2, row.uuid);
			ps.executeUpdate();
		}
		mappingRepo.recordPromotion(con, DocumentNumberEntityType.CLIENT, row.uuid, "client", row.code, newCode,
				DocumentNumberMapping.SOURCE_REMOTE);
		System.out.println("[TemporaryDocumentReconciliation] client " + row.uuid + ": " + row.code + " -> " + newCode);
		return true;
	}

	private boolean promoteJob(Connection con, JobRow row) throws Exception {
		Optional<AllocatedNumber> permanent = allocator.tryAllocatePermanentJobCode(con);
		if (permanent.isEmpty()) {
			return false;
		}
		String newCode = permanent.get().value();
		if (JobIdentifiers.jobCodeExists(con, newCode)) {
			return false;
		}
		try (var ps = con.prepareStatement("""
				UPDATE jobs SET job_code = ?, sync_status = 'PENDING',
				sync_version = sync_version + 1, updated_at = datetime('now')
				WHERE uuid = ?
				""")) {
			ps.setString(1, newCode);
			ps.setString(2, row.uuid);
			ps.executeUpdate();
		}
		mappingRepo.recordPromotion(con, DocumentNumberEntityType.JOB, row.uuid, "job", row.code, newCode,
				DocumentNumberMapping.SOURCE_REMOTE);
		System.out.println("[TemporaryDocumentReconciliation] job " + row.uuid + ": " + row.code + " -> " + newCode);
		return true;
	}

	private boolean promoteInvoice(Connection con, InvoiceRow row) throws Exception {
		MasterDocumentSeries series = resolveInvoiceSeries(row.documentSeries());
		Optional<AllocatedNumber> permanent = allocator.tryAllocatePermanentInvoice(con, series, row.invoiceDate());
		if (permanent.isEmpty()) {
			return false;
		}
		String newNo = permanent.get().value();
		if (InvoiceIdentifiers.invoiceNoInUse(con, newNo, row.uuid())) {
			return false;
		}
		invoiceRepo.updateInvoiceNo(con, row.uuid(), newNo);
		String sequenceKey = utils.NumberSequenceCatalog.moduleNameFor(series);
		if (sequenceKey == null) {
			sequenceKey = "gst_invoice";
		}
		mappingRepo.recordPromotion(con, DocumentNumberEntityType.INVOICE, row.uuid(), sequenceKey, row.code(), newNo,
				DocumentNumberMapping.SOURCE_REMOTE);
		System.out.println("[TemporaryDocumentReconciliation] invoice " + row.uuid() + ": " + row.code() + " -> "
				+ newNo);
		return true;
	}

	private static MasterDocumentSeries resolveInvoiceSeries(String documentSeries) {
		if (documentSeries == null || documentSeries.isBlank()) {
			return MasterDocumentSeries.GST_INVOICE;
		}
		try {
			return MasterDocumentSeries.valueOf(documentSeries.trim());
		} catch (IllegalArgumentException e) {
			return MasterDocumentSeries.GST_INVOICE;
		}
	}

	private static List<ClientRow> loadTempClients(Connection con) throws Exception {
		List<ClientRow> list = new ArrayList<>();
		try (var ps = con.prepareStatement("""
				SELECT uuid, client_code FROM clients
				WHERE IFNULL(is_deleted, 0) = 0 AND client_code LIKE 'TEMP-%'
				""");
				var rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(new ClientRow(rs.getString(1), rs.getString(2)));
			}
		}
		return list;
	}

	private static List<JobRow> loadTempJobs(Connection con) throws Exception {
		List<JobRow> list = new ArrayList<>();
		try (var ps = con.prepareStatement("""
				SELECT uuid, job_code FROM jobs
				WHERE IFNULL(is_deleted, 0) = 0 AND job_code LIKE 'TEMP-%'
				""");
				var rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(new JobRow(rs.getString(1), rs.getString(2)));
			}
		}
		return list;
	}

	private record ClientRow(String uuid, String code) {
	}

	private record JobRow(String uuid, String code) {
	}

	private static List<InvoiceRow> loadTempInvoices(Connection con) throws Exception {
		List<InvoiceRow> list = new ArrayList<>();
		try (var ps = con.prepareStatement("""
				SELECT uuid, invoice_no, document_series, invoice_date FROM invoice_master
				WHERE IFNULL(is_deleted, 0) = 0
				  AND invoice_no LIKE 'TEMP-%'
				""");
				var rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(new InvoiceRow(
						rs.getString(1),
						rs.getString(2),
						rs.getString(3),
						parseInvoiceDate(rs.getString(4))));
			}
		}
		return list;
	}

	private static java.time.LocalDate parseInvoiceDate(String raw) {
		if (raw == null || raw.isBlank()) {
			return java.time.LocalDate.now();
		}
		try {
			if (raw.contains("-")) {
				return java.time.LocalDate.parse(raw.substring(0, Math.min(10, raw.length())));
			}
		} catch (Exception ignored) {
		}
		return java.time.LocalDate.now();
	}

	private record InvoiceRow(String uuid, String code, String documentSeries, java.time.LocalDate invoiceDate) {
	}
}