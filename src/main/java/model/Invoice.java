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
