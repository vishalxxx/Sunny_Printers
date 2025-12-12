package model;

import java.io.Serializable;

public class Binding implements Serializable {
	private String process;
	private String qty;
	private String rate;
	private String notes;
	private String amount;

	public Binding() {
	}

	// getters/setters...
	public String getProcess() {
		return process;
	}

	public void setProcess(String process) {
		this.process = process;
	}

	public String getQty() {
		return qty;
	}

	public void setQty(String qty) {
		this.qty = qty;
	}

	public String getRate() {
		return rate;
	}

	public void setRate(String rate) {
		this.rate = rate;
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
		if (process != null)
			s.append(process).append(" - ");
		if (qty != null)
			s.append(qty).append(" - ");
		if (rate != null)
			s.append("₹").append(rate).append(" - ");
		if (amount != null)
			s.append("₹").append(amount);
		return s.toString().trim();
	}

	@Override
	public String toString() {
		return toShortText();
	}
}
