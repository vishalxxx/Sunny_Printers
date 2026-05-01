package model;

import java.time.LocalDate;

import utils.DocumentNumbering;

public class SystemSettings {

	// ================= DATABASE FIELDS (legacy + FY numbering) =================
	private String invoiceMode; // AUTO or MANUAL (legacy; increments always follow last_seq_*)
	private String invoicePrefix;
	private int invoiceStartNo;
	private int invoicePadding;
	private int lastInvoiceNo;

	private String jobPrefix;
	private int jobStartNo;
	private int jobPadding;
	private int lastJobNo;
	private int lastTempInvoiceNo;

	/** Current FY key for sequence counters (yy-yy, Indian Apr-Mar). */
	private String numberingFy = "";

	private int lastSeqInv;
	private int lastSeqPi;
	private int lastSeqCn;
	private int lastSeqDn;
	private int lastSeqQtn;
	private int lastSeqPo;
	private int lastSeqJob;
	private int lastSeqTkt;
	private int lastSeqDc;
	private int lastSeqEwb;

	// ================= BUSINESS HELPERS =================
	public boolean isAuto() {
		return "AUTO".equalsIgnoreCase(invoiceMode);
	}

	public boolean isManual() {
		return "MANUAL".equalsIgnoreCase(invoiceMode);
	}

	public int getLastSeq(MasterDocumentSeries series) {
		if (series == null) {
			return 0;
		}
		return switch (series) {
		case GST_INVOICE -> lastSeqInv;
		case PROFORMA_INVOICE -> lastSeqPi;
		case CREDIT_NOTE -> lastSeqCn;
		case DEBIT_NOTE -> lastSeqDn;
		case QUOTATION -> lastSeqQtn;
		case PURCHASE_ORDER -> lastSeqPo;
		case JOB -> lastSeqJob;
		case JOB_TICKET -> lastSeqTkt;
		case DISPATCH_CHALLAN -> lastSeqDc;
		case EWAY_BILL_REF -> lastSeqEwb;
		};
	}

	public void setLastSeq(MasterDocumentSeries series, int value) {
		if (series == null) {
			return;
		}
		int v = Math.max(0, value);
		switch (series) {
		case GST_INVOICE -> lastSeqInv = v;
		case PROFORMA_INVOICE -> lastSeqPi = v;
		case CREDIT_NOTE -> lastSeqCn = v;
		case DEBIT_NOTE -> lastSeqDn = v;
		case QUOTATION -> lastSeqQtn = v;
		case PURCHASE_ORDER -> lastSeqPo = v;
		case JOB -> lastSeqJob = v;
		case JOB_TICKET -> lastSeqTkt = v;
		case DISPATCH_CHALLAN -> lastSeqDc = v;
		case EWAY_BILL_REF -> lastSeqEwb = v;
		}
	}

	public void resetAllSeriesForNewFinancialYear() {
		lastSeqInv = 0;
		lastSeqPi = 0;
		lastSeqCn = 0;
		lastSeqDn = 0;
		lastSeqQtn = 0;
		lastSeqPo = 0;
		lastSeqJob = 0;
		lastSeqTkt = 0;
		lastSeqDc = 0;
		lastSeqEwb = 0;
	}

	/** Ensure FY row matches document date; reset series when FY rolls. */
	public void alignFinancialYearTo(LocalDate refDate) {
		if (refDate == null) {
			refDate = LocalDate.now();
		}
		String fy = DocumentNumbering.financialYearLabel(refDate);
		if (numberingFy == null || numberingFy.isBlank()) {
			numberingFy = fy;
			return;
		}
		if (!numberingFy.equals(fy)) {
			numberingFy = fy;
			resetAllSeriesForNewFinancialYear();
		}
	}

	// ================= GETTERS & SETTERS =================
	public String getInvoiceMode() {
		return invoiceMode;
	}

	public void setInvoiceMode(String invoiceMode) {
		this.invoiceMode = invoiceMode;
	}

	public String getInvoicePrefix() {
		return invoicePrefix;
	}

	public void setInvoicePrefix(String invoicePrefix) {
		this.invoicePrefix = invoicePrefix;
	}

	public int getInvoiceStartNo() {
		return invoiceStartNo;
	}

	public void setInvoiceStartNo(int invoiceStartNo) {
		this.invoiceStartNo = invoiceStartNo;
	}

	public int getInvoicePadding() {
		return invoicePadding;
	}

	public void setInvoicePadding(int invoicePadding) {
		this.invoicePadding = invoicePadding;
	}

	public int getLastInvoiceNo() {
		return lastInvoiceNo;
	}

	public void setLastInvoiceNo(int lastInvoiceNo) {
		this.lastInvoiceNo = lastInvoiceNo;
	}

	public int getLastJobNo() {
		return lastJobNo;
	}

	public void setLastJobNo(int lastJobNo) {
		this.lastJobNo = lastJobNo;
	}

	public String getJobPrefix() {
		return jobPrefix;
	}

	public void setJobPrefix(String jobPrefix) {
		this.jobPrefix = jobPrefix;
	}

	public int getJobStartNo() {
		return jobStartNo;
	}

	public void setJobStartNo(int jobStartNo) {
		this.jobStartNo = jobStartNo;
	}

	public int getJobPadding() {
		return jobPadding;
	}

	public void setJobPadding(int jobPadding) {
		this.jobPadding = jobPadding;
	}

	public int getLastTempInvoiceNo() {
		return lastTempInvoiceNo;
	}

	public void setLastTempInvoiceNo(int lastTempInvoiceNo) {
		this.lastTempInvoiceNo = lastTempInvoiceNo;
	}

	public String getNumberingFy() {
		return numberingFy;
	}

	public void setNumberingFy(String numberingFy) {
		this.numberingFy = numberingFy != null ? numberingFy : "";
	}

	public int getLastSeqInv() {
		return lastSeqInv;
	}

	public void setLastSeqInv(int lastSeqInv) {
		this.lastSeqInv = lastSeqInv;
	}

	public int getLastSeqPi() {
		return lastSeqPi;
	}

	public void setLastSeqPi(int lastSeqPi) {
		this.lastSeqPi = lastSeqPi;
	}

	public int getLastSeqCn() {
		return lastSeqCn;
	}

	public void setLastSeqCn(int lastSeqCn) {
		this.lastSeqCn = lastSeqCn;
	}

	public int getLastSeqDn() {
		return lastSeqDn;
	}

	public void setLastSeqDn(int lastSeqDn) {
		this.lastSeqDn = lastSeqDn;
	}

	public int getLastSeqQtn() {
		return lastSeqQtn;
	}

	public void setLastSeqQtn(int lastSeqQtn) {
		this.lastSeqQtn = lastSeqQtn;
	}

	public int getLastSeqPo() {
		return lastSeqPo;
	}

	public void setLastSeqPo(int lastSeqPo) {
		this.lastSeqPo = lastSeqPo;
	}

	public int getLastSeqJob() {
		return lastSeqJob;
	}

	public void setLastSeqJob(int lastSeqJob) {
		this.lastSeqJob = lastSeqJob;
	}

	public int getLastSeqTkt() {
		return lastSeqTkt;
	}

	public void setLastSeqTkt(int lastSeqTkt) {
		this.lastSeqTkt = lastSeqTkt;
	}

	public int getLastSeqDc() {
		return lastSeqDc;
	}

	public void setLastSeqDc(int lastSeqDc) {
		this.lastSeqDc = lastSeqDc;
	}

	public int getLastSeqEwb() {
		return lastSeqEwb;
	}

	public void setLastSeqEwb(int lastSeqEwb) {
		this.lastSeqEwb = lastSeqEwb;
	}
}
