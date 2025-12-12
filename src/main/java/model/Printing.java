package model;

import java.io.Serializable;

public class Printing implements Serializable {
	private String qty;
	private String units; // e.g., "Sheet", "Rim", "Gross"
	private String set; // e.g., "1/Set"
	private String color; // e.g., "4+1", "1C"
	private String side; // "F" / "B" or "F/B"
	private String withCtp; // "Yes"/"No"
	private String notes;
	private String amount; // store as string to keep UI simple; parse/validate in service

	public Printing() {
	}

	// getters/setters
	public String getQty() {
		return qty;
	}

	public void setQty(String qty) {
		this.qty = qty;
	}

	public String getUnits() {
		return units;
	}

	public void setUnits(String units) {
		this.units = units;
	}

	public String getSet() {
		return set;
	}

	public void setSet(String set) {
		this.set = set;
	}

	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}

	public String getSide() {
		return side;
	}

	public void setSide(String side) {
		this.side = side;
	}

	public String getWithCtp() {
		return withCtp;
	}

	public void setWithCtp(String withCtp) {
		this.withCtp = withCtp;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public String getAmount() {
		return amount;
	}

	public void setAmount(String amount) {
		this.amount = amount;
	}

	// Returns formatted one-line description for summary display
	public String toShortText() {
		StringBuilder s = new StringBuilder();
		if (qty != null && !qty.isBlank())
			s.append(qty).append(" ");
		if (units != null && !units.isBlank())
			s.append(units).append(" - ");
		if (set != null && !set.isBlank())
			s.append(set).append(" - ");
		if (color != null && !color.isBlank())
			s.append(color).append(" - ");
		if (side != null && !side.isBlank())
			s.append(side).append(" - ");
		if (withCtp != null && withCtp.equalsIgnoreCase("Yes"))
			s.append("with CTP - ");
		if (amount != null && !amount.isBlank())
			s.append("₹").append(amount);
		return s.toString().trim();
	}

	@Override
	public String toString() {
		return toShortText();
	}
}
