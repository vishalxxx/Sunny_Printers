package model;

public class JobItem {

	/* ================= BASIC FIELDS ================= */

	private int id;
	private int jobId;

	private String type;
	// PRINTING | CTP | PAPER | BINDING | LAMINATION | OTHER

	private String description;
	// Human readable summary (used for bill & UI)

	private double amount;

	private int sortOrder;

	/* ================= CONSTRUCTORS ================= */

	public JobItem() {
	}

	public JobItem(int jobId, String type, String description, double amount, int sortOrder) {
		this.jobId = jobId;
		this.type = type;
		this.description = description;
		this.amount = amount;
		this.sortOrder = sortOrder;
	}

	/* ================= GETTERS / SETTERS ================= */

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getJobId() {
		return jobId;
	}

	public void setJobId(int jobId) {
		this.jobId = jobId;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public double getAmount() {
		return amount;
	}

	public void setAmount(double amount) {
		this.amount = amount;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}

	/* ================= UTIL ================= */

	@Override
	public String toString() {
		return type + " | " + description + " | ₹" + amount;
	}
}
