package model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class InvoiceJob {

	private String jobUuid;
	private String jobNo;
	private String jobName;
	private LocalDate jobDate;

	// GST/PDF helpers (optional)
	private long quantity;
	private String unit;
	private double ratePerUnit;
	private String hsnSac;

	private List<InvoiceLine> lines = new ArrayList<>();

	// -------- helpers --------

	public void addLine(InvoiceLine line) {
		lines.add(line);
	}

	public double getJobTotal() {
		return lines.stream().mapToDouble(InvoiceLine::getAmount).sum();
	}

	// -------- getters / setters --------

	public String getJobUuid() {
		return jobUuid;
	}

	public void setJobUuid(String jobUuid) {
		this.jobUuid = jobUuid;
	}

	/** Alias for {@link #getJobUuid()}. */
	public String getJobId() {
		return jobUuid;
	}

	public void setJobId(String jobUuid) {
		this.jobUuid = jobUuid;
	}

	public String getJobNo() {
		return jobNo;
	}

	public void setJobNo(String jobNo) {
		this.jobNo = jobNo;
	}

	public String getJobName() {
		return jobName;
	}

	public void setJobName(String jobName) {
		this.jobName = jobName;
	}

	public LocalDate getJobDate() {
		return jobDate;
	}

	public void setJobDate(LocalDate jobDate) {
		this.jobDate = jobDate;
	}

	public long getQuantity() {
		return quantity;
	}

	public void setQuantity(long quantity) {
		this.quantity = quantity;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public double getRatePerUnit() {
		return ratePerUnit;
	}

	public void setRatePerUnit(double ratePerUnit) {
		this.ratePerUnit = ratePerUnit;
	}

	public String getHsnSac() {
		return hsnSac;
	}

	public void setHsnSac(String hsnSac) {
		this.hsnSac = hsnSac;
	}

	public List<InvoiceLine> getLines() {
		return lines;
	}
}
