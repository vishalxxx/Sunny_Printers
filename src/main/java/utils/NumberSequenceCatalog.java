package utils;

import java.util.List;
import java.util.Optional;

import model.MasterDocumentSeries;

/** Canonical {@code number_sequences} rows (local SQLite + Supabase). */
public final class NumberSequenceCatalog {

	public record ModuleDef(String moduleName, String displayName, String prefix, String legacySettingsColumn) {
	}

	public static final List<ModuleDef> ALL = List.of(
			new ModuleDef("gst_invoice", "GST Invoice", "INV", "last_seq_inv"),
			new ModuleDef("proforma_invoice", "Proforma Invoice", "PI", "last_seq_pi"),
			new ModuleDef("credit_note", "Credit Note", "CN", "last_seq_cn"),
			new ModuleDef("debit_note", "Debit Note", "DN", "last_seq_dn"),
			new ModuleDef("quotation", "Quotation", "QT", "last_seq_qtn"),
			new ModuleDef("purchase_order", "Purchase Order", "PO", "last_seq_po"),
			new ModuleDef("job", "Job", "JOB", "last_seq_job"),
			new ModuleDef("job_ticket", "Job Ticket", "TK", "last_seq_tkt"),
			new ModuleDef("dispatch_challan", "Dispatch Challan", "DC", "last_seq_dc"),
			new ModuleDef("eway_bill_ref", "E-way bill ref", "WB", "last_seq_ewb"),
			new ModuleDef("payment_receipt", "Payment Receipt", "RCPT", null),
			new ModuleDef("temp_invoice", "Temp Invoice", "TEMP", "last_temp_invoice_no"),
			new ModuleDef("client", "Client", "CL", null),
			new ModuleDef("supplier", "Supplier", "SUP", null));

	private NumberSequenceCatalog() {
	}

	public static String moduleNameFor(MasterDocumentSeries series) {
		if (series == null) {
			return null;
		}
		if (series == MasterDocumentSeries.GST_INVOICE) {
			return "gst_invoice";
		}
		if (series == MasterDocumentSeries.PROFORMA_INVOICE) {
			return "proforma_invoice";
		}
		if (series == MasterDocumentSeries.CREDIT_NOTE) {
			return "credit_note";
		}
		if (series == MasterDocumentSeries.DEBIT_NOTE) {
			return "debit_note";
		}
		if (series == MasterDocumentSeries.QUOTATION) {
			return "quotation";
		}
		if (series == MasterDocumentSeries.PURCHASE_ORDER) {
			return "purchase_order";
		}
		if (series == MasterDocumentSeries.JOB) {
			return "job";
		}
		if (series == MasterDocumentSeries.JOB_TICKET) {
			return "job_ticket";
		}
		if (series == MasterDocumentSeries.DISPATCH_CHALLAN) {
			return "dispatch_challan";
		}
		if (series == MasterDocumentSeries.EWAY_BILL_REF) {
			return "eway_bill_ref";
		}
		return null;
	}

	public static Optional<MasterDocumentSeries> seriesForModule(String moduleName) {
		if (moduleName == null || moduleName.isBlank()) {
			return Optional.empty();
		}
		String m = moduleName.trim();
		for (MasterDocumentSeries s : MasterDocumentSeries.values()) {
			if (m.equals(moduleNameFor(s))) {
				return Optional.of(s);
			}
		}
		return Optional.empty();
	}

	public static Optional<ModuleDef> defForModule(String moduleName) {
		if (moduleName == null) {
			return Optional.empty();
		}
		for (ModuleDef d : ALL) {
			if (d.moduleName().equals(moduleName)) {
				return Optional.of(d);
			}
		}
		return Optional.empty();
	}
}
