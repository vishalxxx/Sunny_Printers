package model;

import java.io.Serializable;

public class Paper implements Serializable {
	private String qty;
	private String units;
	private String size;
	private String gsm;
	private String type; // e.g., Art Paper
	private String notes;
	private String amount;
	private String source; // eg our paper or client

	public void setSource(String source) {
		this.source = source;
	}

	public String getSource() {
		return source;
	}

	public Paper() {
	}

	// getters/setters...
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

	public String getSize() {
		return size;
	}

	public void setSize(String size) {
		this.size = size;
	}

	public String getGsm() {
		return gsm;
	}

	public void setGsm(String gsm) {
		this.gsm = gsm;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
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
		if (units != null)
			s.append(units).append(" - ");
		if (size != null)
			s.append(size).append(" - ");
		if (gsm != null)
			s.append(gsm).append("gsm - ");
		if (type != null)
			s.append(type).append(" - ");
		if (amount != null)
			s.append("₹").append(amount);
		return s.toString().trim();
	}

	@Override
	public String toString() {
		return toShortText();
	}
}
