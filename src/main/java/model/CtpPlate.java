package model;

import java.io.Serializable;

public class CtpPlate implements Serializable {
	private String qty;
	private String size;
	private String gauge;
	private String backing; // Yes/No
	private String notes;
	private String amount;

	public CtpPlate() {
	}

	// getters/setters...
	public String getQty() {
		return qty;
	}

	public void setQty(String qty) {
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
		if (size != null)
			s.append(size).append(" - ");
		if (gauge != null)
			s.append(gauge).append(" - ");
		if (backing != null)
			s.append(backing).append(" - ");
		if (amount != null)
			s.append("₹").append(amount);
		return s.toString().trim();
	}

	@Override
	public String toString() {
		return toShortText();
	}
}
