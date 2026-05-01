package utils;

import java.io.File;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.prefs.Preferences;

import model.Invoice;
import model.MasterDocumentSeries;

/**
 * On-disk layout under the owner's data root (General Settings: default download folder).
 * If unset, uses user.home / sanitized company name + "Data".
 * Financial_Years/FY_YYYY_YY/ holds Clients, company GST, Banking, Reports, Backups; Archive at root.
 */
public final class CompanyDataLayout {

	private static final String LEGACY_INVOICE_PREFS_NODE = "invoice_settings";
	private static final String LEGACY_SAVE_PATH = "save_path";

	private CompanyDataLayout() {
	}

	public static String companyDataFolderName() {
		String raw = CompanyProfile.getName();
		String seg = sanitizeDirSegment(raw);
		return seg.isEmpty() ? "CompanyData" : seg + "Data";
	}

	public static File getDataStoreRoot() {
		String configured = UniversalDownloadPath.get();
		if (configured != null && !configured.isBlank()) {
			File f = new File(configured.trim());
			mkdirs(f);
			return f;
		}
		try {
			String legacy = Preferences.userRoot().node(LEGACY_INVOICE_PREFS_NODE).get(LEGACY_SAVE_PATH, null);
			if (legacy != null && !legacy.isBlank()) {
				File f = new File(legacy.trim());
				mkdirs(f);
				return f;
			}
		} catch (Exception ignored) {
		}
		File home = new File(System.getProperty("user.home"));
		File root = new File(home, companyDataFolderName());
		mkdirs(root);
		return root;
	}

	public static void ensureStandardFolders(File dataRoot, LocalDate refDate) {
		if (dataRoot == null || refDate == null) {
			return;
		}
		File fy = fyFolder(dataRoot, refDate);
		mkdirs(new File(fy, "Clients"));
		mkdirs(new File(fy, "GST"));

		File banking = mkdirs(new File(fy, "Banking"));
		mkdirs(new File(banking, "Bank_Statements"));

		File reports = mkdirs(new File(fy, "Reports"));
		mkdirs(new File(reports, "Monthly"));
		mkdirs(new File(reports, "Quarterly"));
		mkdirs(new File(reports, "Annual"));
		mkdirs(new File(reports, "Audit"));

		File backups = mkdirs(new File(fy, "Backups"));
		mkdirs(new File(backups, "Database"));
		mkdirs(new File(backups, "PDFs"));
		mkdirs(new File(backups, "Full_System"));

		mkdirs(new File(dataRoot, "Archive"));
	}

	public static File fyFolder(File dataRoot, LocalDate refDate) {
		String fyName = DocumentNumbering.financialYearFolderName(refDate);
		File financialYears = mkdirs(new File(dataRoot, "Financial_Years"));
		return mkdirs(new File(financialYears, fyName));
	}

	public static File clientRoot(File dataRoot, LocalDate refDate, String clientDisplayName) {
		String seg = sanitizeDirSegment(clientDisplayName);
		if (seg.isEmpty()) {
			seg = "Unknown_Client";
		}
		File clients = mkdirs(new File(fyFolder(dataRoot, refDate), "Clients"));
		File client = mkdirs(new File(clients, seg));
		ensureClientArtifactFolders(client);
		return client;
	}

	public enum InvoiceBucket {
		GST("GST"),
		Proforma("Proforma"),
		Cancelled("Cancelled"),
		Revised("Revised");

		private final String dirName;

		InvoiceBucket(String dirName) {
			this.dirName = dirName;
		}

		public String dirName() {
			return dirName;
		}
	}

	public static InvoiceBucket bucketFor(Invoice invoice) {
		if (invoice == null) {
			return InvoiceBucket.GST;
		}
		String no = invoice.getInvoiceNo() != null ? invoice.getInvoiceNo() : "";
		String st = invoice.getStatus() != null ? invoice.getStatus().toUpperCase(Locale.ROOT) : "";
		String nu = no.toUpperCase(Locale.ROOT);
		if (st.contains("CANCEL") || nu.contains("CANCELLED")) {
			return InvoiceBucket.Cancelled;
		}
		if (nu.contains("-R") || st.contains("REVISED")) {
			return InvoiceBucket.Revised;
		}
		if (invoice.getMasterDocumentSeries() == MasterDocumentSeries.PROFORMA_INVOICE) {
			return InvoiceBucket.Proforma;
		}
		return InvoiceBucket.GST;
	}

	public static File clientInvoicesSubdir(File dataRoot, LocalDate refDate, String clientName, InvoiceBucket bucket) {
		File inv = mkdirs(new File(clientRoot(dataRoot, refDate, clientName), "Invoices"));
		return mkdirs(new File(inv, bucket.dirName()));
	}

	public static File reportsMonthlyDir(File dataRoot, LocalDate refDate) {
		File reports = mkdirs(new File(fyFolder(dataRoot, refDate), "Reports"));
		return mkdirs(new File(reports, "Monthly"));
	}

	public static File invoicePdfPath(Invoice invoice) {
		File root = getDataStoreRoot();
		LocalDate d = invoice != null && invoice.getInvoiceDate() != null ? invoice.getInvoiceDate()
				: LocalDate.now();
		ensureStandardFolders(root, d);
		if (invoice != null && "MONTHLY_BULK".equals(invoice.getClientName())) {
			String name = safeFileName("Monthly_Invoices_" + YearMonth.from(d) + ".pdf");
			return new File(reportsMonthlyDir(root, d), name);
		}
		InvoiceBucket b = bucketFor(invoice);
		File dir = clientInvoicesSubdir(root, d, invoice.getClientName(), b);
		String base = safeFileName(safeInvoiceFileBase(invoice.getInvoiceNo()) + ".pdf");
		return new File(dir, base);
	}

	public static File invoiceExcelPath(Invoice invoice) {
		File root = getDataStoreRoot();
		LocalDate d = invoice != null && invoice.getInvoiceDate() != null ? invoice.getInvoiceDate()
				: LocalDate.now();
		ensureStandardFolders(root, d);
		if (invoice != null && "MONTHLY_BULK".equals(invoice.getClientName())) {
			String name = safeFileName("Monthly_Invoices_" + YearMonth.from(d) + ".xlsx");
			return new File(reportsMonthlyDir(root, d), name);
		}
		InvoiceBucket b = bucketFor(invoice);
		File dir = clientInvoicesSubdir(root, d, invoice.getClientName(), b);
		YearMonth ym = YearMonth.from(d);
		String base = safeFileName(sanitizeFileToken(invoice.getCompanyName()) + "_"
				+ sanitizeFileToken(invoice.getClientName()) + "_" + sanitizeFileToken(invoice.getInvoiceNo()) + "_"
				+ ym + ".xlsx");
		return new File(dir, base);
	}

	public static File monthlyBulkPdfPath(YearMonth ym) {
		File root = getDataStoreRoot();
		LocalDate d = ym.atEndOfMonth();
		ensureStandardFolders(root, d);
		String name = safeFileName("Monthly_Invoices_" + ym + ".pdf");
		return new File(reportsMonthlyDir(root, d), name);
	}

	public static File jobFolder(File dataRoot, LocalDate refDate, String clientDisplayName, String jobNo) {
		String j = sanitizeDirSegment(jobNo);
		if (j.isEmpty()) {
			j = "Job";
		}
		File jobsDir = mkdirs(new File(clientRoot(dataRoot, refDate, clientDisplayName), "Jobs"));
		File jobDir = mkdirs(new File(jobsDir, j));
		for (String sub : new String[] { "Artwork", "Customer_Files", "Proofs", "Plates", "Final_Print",
				"Dispatch" }) {
			mkdirs(new File(jobDir, sub));
		}
		return jobDir;
	}

	public static File companyGstDir(File dataRoot, LocalDate refDate) {
		return mkdirs(new File(fyFolder(dataRoot, refDate), "GST"));
	}

	public static File creditNotesDir(File dataRoot, LocalDate refDate, String clientDisplayName) {
		return mkdirs(new File(clientRoot(dataRoot, refDate, clientDisplayName), "Credit_Notes"));
	}

	public static File debitNotesDir(File dataRoot, LocalDate refDate, String clientDisplayName) {
		return mkdirs(new File(clientRoot(dataRoot, refDate, clientDisplayName), "Debit_Notes"));
	}

	public static File paymentReceiptsDir(File dataRoot, LocalDate refDate, String clientDisplayName) {
		return mkdirs(new File(clientRoot(dataRoot, refDate, clientDisplayName), "Payment_Receipts"));
	}

	/**
	 * Client Payment_Receipts folder for the financial year of paymentDate, e.g. RCPT-2026-001.pdf
	 */
	public static File paymentReceiptPdfPath(String clientDisplayName, LocalDate paymentDate, String receiptNo) {
		File root = getDataStoreRoot();
		LocalDate d = paymentDate != null ? paymentDate : LocalDate.now();
		ensureStandardFolders(root, d);
		String token = receiptNo != null ? receiptNo.trim() : "receipt";
		if (token.isEmpty()) {
			token = "receipt";
		}
		String base = token.toLowerCase(Locale.ROOT).endsWith(".pdf") ? token : token + ".pdf";
		return new File(paymentReceiptsDir(root, d, clientDisplayName), safeFileName(base));
	}

	public static File statementsDir(File dataRoot, LocalDate refDate, String clientDisplayName) {
		return mkdirs(new File(clientRoot(dataRoot, refDate, clientDisplayName), "Statements"));
	}

	public static File ledgerExportsDir(File dataRoot, LocalDate refDate, String clientDisplayName) {
		return mkdirs(new File(clientRoot(dataRoot, refDate, clientDisplayName), "Ledger_Exports"));
	}

	public static File attachmentsDir(File dataRoot, LocalDate refDate, String clientDisplayName) {
		return mkdirs(new File(clientRoot(dataRoot, refDate, clientDisplayName), "Attachments"));
	}

	public static void ensureClientArtifactFolders(File clientRoot) {
		if (clientRoot == null) {
			return;
		}
		File inv = mkdirs(new File(clientRoot, "Invoices"));
		mkdirs(new File(inv, "GST"));
		mkdirs(new File(inv, "Proforma"));
		mkdirs(new File(inv, "Cancelled"));
		mkdirs(new File(inv, "Revised"));
		mkdirs(new File(clientRoot, "Credit_Notes"));
		mkdirs(new File(clientRoot, "Debit_Notes"));
		mkdirs(new File(clientRoot, "Payment_Receipts"));
		mkdirs(new File(clientRoot, "Statements"));
		mkdirs(new File(clientRoot, "Ledger_Exports"));
		mkdirs(new File(clientRoot, "Jobs"));
		mkdirs(new File(clientRoot, "Attachments"));
	}

	public static String sanitizeDirSegment(String s) {
		if (s == null || s.isBlank()) {
			return "";
		}
		return s.trim().replaceAll("[^a-zA-Z0-9-_]", "_").replaceAll("_+", "_");
	}

	static String sanitizeFileToken(String s) {
		if (s == null) {
			return "unknown";
		}
		String t = s.trim().replaceAll("[^a-zA-Z0-9-_]", "_");
		return t.isEmpty() ? "unknown" : t;
	}

	static String safeInvoiceFileBase(String invoiceNo) {
		if (invoiceNo == null || invoiceNo.isBlank()) {
			return "invoice";
		}
		return invoiceNo.trim().replace('/', '-').replace('\\', '-');
	}

	static String safeFileName(String name) {
		if (name == null) {
			return "file";
		}
		return name.replaceAll("[\\\\/:*?\"<>|]", "_");
	}

	private static File mkdirs(File f) {
		if (!f.exists()) {
			f.mkdirs();
		}
		return f;
	}
}
