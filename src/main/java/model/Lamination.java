package model;

import java.io.Serializable;

public class Lamination implements Serializable {
	private String qty;
	private String unit;
	private String type;
	private String side;
	private String size;
	private String notes;
	private String amount;

	public Lamination() {
	}

	// getters/setters...
	public String getQty() {
		return qty;
	}

	public void setQty(String qty) {
		this.qty = qty;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getSide() {
		return side;
	}

	public void setSide(String side) {
		this.side = side;
	}

	public String getSize() {
		return size;
	}

	public void setSize(String size) {
		this.size = size;
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

	public String toShortText() {
		StringBuilder s = new StringBuilder();
		if (qty != null)
			s.append(qty).append(" ");
		if (unit != null)
			s.append(unit).append(" - ");
		if (type != null)
			s.append(type).append(" - ");
		if (side != null)
			s.append(side).append(" - ");
		if (amount != null)
			s.append("₹").append(amount);
		return s.toString().trim();
	}

	@Override
	public String toString() {
		return toShortText();
	}
}
