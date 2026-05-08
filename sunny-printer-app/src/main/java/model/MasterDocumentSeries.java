package model;

public enum MasterDocumentSeries {
	GST_INVOICE("GST Invoice", "INV"),
	PROFORMA_INVOICE("Proforma Invoice", "PI"),
	CREDIT_NOTE("Credit Note", "CN"),
	DEBIT_NOTE("Debit Note", "DN"),
	QUOTATION("Quotation", "QT"),
	PURCHASE_ORDER("Purchase Order", "PO"),
	JOB("Job", "JOB"),
	JOB_TICKET("Job Ticket", "TK"),
	DISPATCH_CHALLAN("Dispatch Challan", "DC"),
	EWAY_BILL_REF("E-way bill ref", "WB");

	private final String label;
	private final String typeCode;

	MasterDocumentSeries(String label, String typeCode) {
		this.label = label;
		this.typeCode = typeCode;
	}

	public String getLabel() {
		return label;
	}

	public String getTypeCode() {
		return typeCode;
	}
}