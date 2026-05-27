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
			con.setAutoCommit(true);
			total += reconcileClients(con);
			total += reconcileSuppliers(con);
			total += reconcileJobs(con);
			total += reconcileInvoices(con);
			total += reconcilePayments(con);
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
		Optional<DocumentNumberMapping> existing = mappingRepo.findByEntity(DocumentNumberEntityType.CLIENT, row.uuid);
		String newCode;
		if (existing.isPresent()) {
			newCode = existing.get().getPermanentNumber();
			System.out.println("[TemporaryDocumentReconciliation] Client mapping reused: " + row.uuid + " (" + row.code + " -> " + newCode + ")");
		} else {
			Optional<AllocatedNumber> permanent = allocator.tryAllocatePermanentClientCode(con);
			if (permanent.isEmpty()) {
				return false;
			}
			newCode = permanent.get().value();
			mappingRepo.recordPromotion(con, DocumentNumberEntityType.CLIENT, row.uuid, "client", row.code, newCode,
					DocumentNumberMapping.SOURCE_REMOTE);
			System.out.println("[TemporaryDocumentReconciliation] Client reconciliation success: allocated " + newCode + " for " + row.uuid);
		}

		if (ClientIdentifiers.clientCodeInUse(con, newCode, row.uuid)) {
			System.out.println("[TemporaryDocumentReconciliation] Client code " + newCode + " is already in use, skipping update...");
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
		return true;
	}

	private boolean promoteJob(Connection con, JobRow row) throws Exception {
		Optional<DocumentNumberMapping> existing = mappingRepo.findByEntity(DocumentNumberEntityType.JOB, row.uuid);
		String newCode;
		if (existing.isPresent()) {
			newCode = existing.get().getPermanentNumber();
			System.out.println("[TemporaryDocumentReconciliation] Job mapping reused: " + row.uuid + " (" + row.code + " -> " + newCode + ")");
		} else {
			Optional<AllocatedNumber> permanent = allocator.tryAllocatePermanentJobCode(con);
			if (permanent.isEmpty()) {
				return false;
			}
			newCode = permanent.get().value();
			mappingRepo.recordPromotion(con, DocumentNumberEntityType.JOB, row.uuid, "job", row.code, newCode,
					DocumentNumberMapping.SOURCE_REMOTE);
			System.out.println("[TemporaryDocumentReconciliation] Job reconciliation success: allocated " + newCode + " for " + row.uuid);
		}

		if (JobIdentifiers.jobCodeExists(con, newCode)) {
			System.out.println("[TemporaryDocumentReconciliation] Job code " + newCode + " is already in use, skipping update...");
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
		return true;
	}

	private boolean promoteInvoice(Connection con, InvoiceRow row) throws Exception {
		MasterDocumentSeries series = resolveInvoiceSeries(row.documentSeries());
		String sequenceKey = utils.NumberSequenceCatalog.moduleNameFor(series);
		if (sequenceKey == null) {
			sequenceKey = "gst_invoice";
		}

		Optional<DocumentNumberMapping> existing = mappingRepo.findByEntity(DocumentNumberEntityType.INVOICE, row.uuid());
		String newNo;
		if (existing.isPresent()) {
			newNo = existing.get().getPermanentNumber();
			System.out.println("[TemporaryDocumentReconciliation] Invoice mapping reused: " + row.uuid() + " (" + row.code() + " -> " + newNo + ")");
		} else {
			Optional<AllocatedNumber> permanent = allocator.tryAllocatePermanentInvoice(con, series, row.invoiceDate());
			if (permanent.isEmpty()) {
				return false;
			}
			newNo = permanent.get().value();
			mappingRepo.recordPromotion(con, DocumentNumberEntityType.INVOICE, row.uuid(), sequenceKey, row.code(), newNo,
					DocumentNumberMapping.SOURCE_REMOTE);
			System.out.println("[TemporaryDocumentReconciliation] Invoice reconciliation success: allocated " + newNo + " for " + row.uuid());
		}

		if (InvoiceIdentifiers.invoiceNoInUse(con, newNo, row.uuid())) {
			System.out.println("[TemporaryDocumentReconciliation] Invoice number " + newNo + " is already in use, skipping update...");
			return false;
		}
		invoiceRepo.updateInvoiceNo(con, row.uuid(), newNo);
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
				  AND (document_series <> 'PROFORMA_INVOICE' OR status = 'FINAL')
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

	private int reconcileSuppliers(Connection con) throws Exception {
		List<SupplierRow> rows = loadTempSuppliers(con);
		int n = 0;
		for (SupplierRow row : rows) {
			if (promoteSupplier(con, row)) {
				n++;
			}
		}
		return n;
	}

	private boolean promoteSupplier(Connection con, SupplierRow row) throws Exception {
		Optional<DocumentNumberMapping> existing = mappingRepo.findByEntity(DocumentNumberEntityType.SUPPLIER, row.uuid);
		String newCode;
		if (existing.isPresent()) {
			newCode = existing.get().getPermanentNumber();
			System.out.println("[TemporaryDocumentReconciliation] Supplier mapping reused: " + row.uuid + " (" + row.code + " -> " + newCode + ")");
		} else {
			Optional<AllocatedNumber> permanent = allocator.tryAllocatePermanentSupplierCode(con);
			if (permanent.isEmpty()) {
				return false;
			}
			newCode = permanent.get().value();
			mappingRepo.recordPromotion(con, DocumentNumberEntityType.SUPPLIER, row.uuid, "supplier", row.code, newCode,
					DocumentNumberMapping.SOURCE_REMOTE);
			System.out.println("[TemporaryDocumentReconciliation] Supplier reconciliation success: allocated " + newCode + " for " + row.uuid);
		}

		if (supplierCodeInUse(con, newCode, row.uuid)) {
			System.out.println("[TemporaryDocumentReconciliation] Supplier code " + newCode + " is already in use, skipping update...");
			return false;
		}
		try (var ps = con.prepareStatement("""
				UPDATE suppliers SET supplier_code = ?, sync_status = 'PENDING',
				sync_version = sync_version + 1, updated_at = datetime('now')
				WHERE uuid = ?
				""")) {
			ps.setString(1, newCode);
			ps.setString(2, row.uuid);
			ps.executeUpdate();
		}
		return true;
	}

	private static boolean supplierCodeInUse(Connection con, String code, String excludeUuid) throws Exception {
		if (code == null || code.isBlank()) {
			return false;
		}
		String sql = excludeUuid != null && !excludeUuid.isBlank()
				? "SELECT 1 FROM suppliers WHERE supplier_code = ? AND uuid <> ? LIMIT 1"
				: "SELECT 1 FROM suppliers WHERE supplier_code = ? LIMIT 1";
		try (var ps = con.prepareStatement(sql)) {
			ps.setString(1, code.trim());
			if (excludeUuid != null && !excludeUuid.isBlank()) {
				ps.setString(2, excludeUuid.trim());
			}
			try (var rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private static List<SupplierRow> loadTempSuppliers(Connection con) throws Exception {
		List<SupplierRow> list = new ArrayList<>();
		try (var ps = con.prepareStatement("""
				SELECT uuid, supplier_code FROM suppliers
				WHERE IFNULL(is_deleted, 0) = 0 AND supplier_code LIKE 'TEMP-%'
				""");
				var rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(new SupplierRow(rs.getString(1), rs.getString(2)));
			}
		}
		return list;
	}

	private int reconcilePayments(Connection con) throws Exception {
		List<PaymentRow> rows = loadTempPayments(con);
		int n = 0;
		for (PaymentRow row : rows) {
			if (promotePayment(con, row)) {
				n++;
			}
		}
		return n;
	}

	private boolean promotePayment(Connection con, PaymentRow row) throws Exception {
		Optional<DocumentNumberMapping> existing = mappingRepo.findByEntity(DocumentNumberEntityType.PAYMENT, row.uuid);
		String newNo;
		if (existing.isPresent()) {
			newNo = existing.get().getPermanentNumber();
			System.out.println("[TemporaryDocumentReconciliation] Payment mapping reused: " + row.uuid + " (" + row.code + " -> " + newNo + ")");
		} else {
			Optional<AllocatedNumber> permanent = allocator.tryAllocatePermanentPaymentReceiptNo(con, row.paymentDate);
			if (permanent.isEmpty()) {
				return false;
			}
			newNo = permanent.get().value();
			mappingRepo.recordPromotion(con, DocumentNumberEntityType.PAYMENT, row.uuid, "payment_receipt", row.code, newNo,
					DocumentNumberMapping.SOURCE_REMOTE);
			System.out.println("[TemporaryDocumentReconciliation] Payment reconciliation success: allocated " + newNo + " for " + row.uuid);
		}

		if (paymentReceiptNoInUse(con, newNo, row.uuid)) {
			System.out.println("[TemporaryDocumentReconciliation] Payment receipt number " + newNo + " is already in use, skipping update...");
			return false;
		}
		
		// Update payment_details
		try (var ps = con.prepareStatement("""
				UPDATE payment_details SET field_value = ?, sync_status = 'PENDING',
				updated_at = datetime('now')
				WHERE payment_uuid = ? AND field_key = 'receipt_no'
				""")) {
			ps.setString(1, newNo);
			ps.setString(2, row.uuid);
			ps.executeUpdate();
		}
		
		// Update payments
		try (var ps = con.prepareStatement("""
				UPDATE payments SET sync_status = 'PENDING',
				sync_version = sync_version + 1, updated_at = datetime('now')
				WHERE uuid = ?
				""")) {
			ps.setString(1, row.uuid);
			ps.executeUpdate();
		}
		return true;
	}

	private static boolean paymentReceiptNoInUse(Connection con, String receiptNo, String excludePaymentUuid) throws Exception {
		if (receiptNo == null || receiptNo.isBlank()) {
			return false;
		}
		String sql = excludePaymentUuid != null && !excludePaymentUuid.isBlank()
				? "SELECT 1 FROM payment_details WHERE field_key = 'receipt_no' AND field_value = ? AND payment_uuid <> ? LIMIT 1"
				: "SELECT 1 FROM payment_details WHERE field_key = 'receipt_no' AND field_value = ? LIMIT 1";
		try (var ps = con.prepareStatement(sql)) {
			ps.setString(1, receiptNo.trim());
			if (excludePaymentUuid != null && !excludePaymentUuid.isBlank()) {
				ps.setString(2, excludePaymentUuid.trim());
			}
			try (var rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private static List<PaymentRow> loadTempPayments(Connection con) throws Exception {
		List<PaymentRow> list = new ArrayList<>();
		try (var ps = con.prepareStatement("""
				SELECT p.uuid, p.payment_date, pd.field_value FROM payments p
				JOIN payment_details pd ON p.uuid = pd.payment_uuid
				WHERE IFNULL(p.is_deleted, 0) = 0
				  AND pd.field_key = 'receipt_no'
				  AND pd.field_value LIKE 'TEMP-%'
				""");
				var rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(new PaymentRow(
						rs.getString(1),
						parsePaymentDate(rs.getString(2)),
						rs.getString(3)));
			}
		}
		return list;
	}

	private static java.time.LocalDate parsePaymentDate(String raw) {
		return parseInvoiceDate(raw);
	}

	private record InvoiceRow(String uuid, String code, String documentSeries, java.time.LocalDate invoiceDate) {
	}

	private record SupplierRow(String uuid, String code) {
	}

	private record PaymentRow(String uuid, java.time.LocalDate paymentDate, String code) {
	}
}