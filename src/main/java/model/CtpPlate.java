package model;

public class CtpPlate {

	private int jobId;

	private int qty;
	private String size;
	private String gauge;
	private String backing;

	private Integer supplierId;
	private String supplierNameSnapshot;

	private String notes;
	private double amount;

	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}

	private String color;

	public int getJobId() {
		return jobId;
	}

	public void setJobId(int jobId) {
		this.jobId = jobId;
	}

	public int getQty() {
		return qty;
	}

	public void setQty(int qty) {
		this.qty = qty;
	}

	public String getSize() {
		return size;
	}

	public void setSize(String size) {
		this.size = size;
	}

	public String getGauge() {
		return gauge;
	}

	public void setGauge(String gauge) {
		this.gauge = gauge;
	}

	public String getBacking() {
		return backing;
	}

	public void setBacking(String backing) {
		this.backing = backing;
	}

	public Integer getSupplierId() {
		return supplierId;
	}

	public void setSupplierId(Integer supplierId) {
		this.supplierId = supplierId;
	}

	public String getSupplierNameSnapshot() {
		return supplierNameSnapshot;
	}

	public void setSupplierNameSnapshot(String supplierNameSnapshot) {
		this.supplierNameSnapshot = supplierNameSnapshot;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public double getAmount() {
		return amount;
	}

	public void setAmount(double amount) {
		this.amount = amount;
	}

	// getters & setters
}
