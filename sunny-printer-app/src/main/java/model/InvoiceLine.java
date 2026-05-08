package model;

public class InvoiceLine {

	private String description; // printed exactly as stored
	private double amount; // final amount

	// future-safe (optional later)
	private String type; // PRINTING / CTP / PAPER etc
	private int sortOrder;

	// -------- constructors --------

	public InvoiceLine() {
	}

	public InvoiceLine(String description, double amount) {
		this.description = description;
		this.amount = amount;
	}

	// -------- getters / setters --------

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

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}
}
