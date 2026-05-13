package model;

public class PrintableLine {

	private final String description; // multi-line text
	private final double amount;

	public PrintableLine(String description, double amount) {
		this.description = description;
		this.amount = amount;
	}

	public String getDescription() {
		return description;
	}

	public double getAmount() {
		return amount;
	}
}
