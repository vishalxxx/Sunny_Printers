package model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Invoice {

	private String invoiceNo;
	private LocalDate invoiceDate;
	private LocalDate periodFrom;
	private LocalDate periodTo;

	private String companyName;
	private String companyAddress;
	private String companyContact;
	private String email;
	private String clientName;
	private int clientId;
	private String invoiceType; // JOB_SPECIFIC / DATE_RANGE / MONTHLY_BULK
	private String status; // SENT / DRAFT / PAID

	/** When non-null, this row is a standalone payment in mini-ledgers (client profile). */
	private Integer standalonePaymentId;

	/** Which master numbering sequence to use when the invoice is finalized (GST vs Proforma). */
	private MasterDocumentSeries masterDocumentSeries = MasterDocumentSeries.GST_INVOICE;

	public LocalDate getFromDate() {
		return periodFrom;
	}

	public void setFromDate(LocalDate periodFrom) {
		this.periodFrom = periodFrom;
	}

	public LocalDate getToDate() {
		return periodTo;
	}

	public void setToDate(LocalDate periodTo) {
		this.periodTo = periodTo;
	}

	public String getInvoiceType() {
		return invoiceType;
	}

	public void setInvoiceType(String invoiceType) {
		this.invoiceType = invoiceType;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Integer getStandalonePaymentId() {
		return standalonePaymentId;
	}

	public void setStandalonePaymentId(Integer standalonePaymentId) {
		this.standalonePaymentId = standalonePaymentId;
	}

	public MasterDocumentSeries getMasterDocumentSeries() {
		return masterDocumentSeries != null ? masterDocumentSeries : MasterDocumentSeries.GST_INVOICE;
	}

	public void setMasterDocumentSeries(MasterDocumentSeries masterDocumentSeries) {
		this.masterDocumentSeries = masterDocumentSeries != null ? masterDocumentSeries
				: MasterDocumentSeries.GST_INVOICE;
	}

	public int getClientId() {
		return clientId;
	}

	public void setClientId(int clientId) {
		this.clientId = clientId;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	private List<InvoiceJob> jobs = new ArrayList<>();

	private double grandTotal;

	// GST/PDF display fields (optional; used by GstPdfInvoiceService)
	private String buyerAddress;
	private String buyerGstin;
	private String buyerStateName;
	private String consigneeName;
	private String consigneeAddress;
	private String consigneeGstin;
	private String consigneeStateName;

	public String getBuyerAddress() {
		return buyerAddress;
	}

	public void setBuyerAddress(String buyerAddress) {
		this.buyerAddress = buyerAddress;
	}

	public String getBuyerGstin() {
		return buyerGstin;
	}

	public void setBuyerGstin(String buyerGstin) {
		this.buyerGstin = buyerGstin;
	}

	public String getBuyerStateName() {
		return buyerStateName;
	}

	public void setBuyerStateName(String buyerStateName) {
		this.buyerStateName = buyerStateName;
	}

	public String getConsigneeName() {
		return consigneeName;
	}

	public void setConsigneeName(String consigneeName) {
		this.consigneeName = consigneeName;
	}

	public String getConsigneeAddress() {
		return consigneeAddress;
	}

	public void setConsigneeAddress(String consigneeAddress) {
		this.consigneeAddress = consigneeAddress;
	}

	public String getConsigneeGstin() {
		return consigneeGstin;
	}

	public void setConsigneeGstin(String consigneeGstin) {
		this.consigneeGstin = consigneeGstin;
	}

	public String getConsigneeStateName() {
		return consigneeStateName;
	}

	public void setConsigneeStateName(String consigneeStateName) {
		this.consigneeStateName = consigneeStateName;
	}

	// -------- getters / setters --------

	public String getInvoiceNo() {
		return invoiceNo;
	}

	public void setInvoiceNo(String invoiceNo) {
		this.invoiceNo = invoiceNo;
	}

	public LocalDate getInvoiceDate() {
		return invoiceDate;
	}

	public void setInvoiceDate(LocalDate invoiceDate) {
		this.invoiceDate = invoiceDate;
	}

	public String getCompanyName() {
		return companyName;
	}

	public void setCompanyName(String companyName) {
		this.companyName = companyName;
	}

	public String getCompanyAddress() {
		return companyAddress;
	}

	public void setCompanyAddress(String companyAddress) {
		this.companyAddress = companyAddress;
	}

	public String getCompanyContact() {
		return companyContact;
	}

	public void setCompanyContact(String companyContact) {
		this.companyContact = companyContact;
	}

	public String getClientName() {
		return clientName;
	}

	public void setClientName(String clientName) {
		this.clientName = clientName;
	}

	public List<InvoiceJob> getJobs() {
		return jobs;
	}

	public double getGrandTotal() {
        if (this.grandTotal > 0) return this.grandTotal;
		return jobs.stream().mapToDouble(InvoiceJob::getJobTotal).sum();
	}

	public void setGrandTotal(double grandTotal) {
		this.grandTotal = grandTotal;
	}

	public void addJob(InvoiceJob job) {
		if (job == null)
			return;
		this.jobs.add(job);
	}
}
