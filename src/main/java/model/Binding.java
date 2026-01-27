package model;

import java.io.Serializable;

public class Binding implements Serializable {

	
	private int id;
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getJobitemid() {
		return jobitemid;
	}

	public void setJobItemId(int job_item_id) {
		this.jobitemid = jobitemid;
	}

	private int jobitemid;
    private String process;   // Perfect Binding / Spiral...
    private int qty;          // optional
    private double rate;      // optional
    private String notes;     // optional
    private double amount;    // required

    public Binding() {}

    public String getProcess() { return process; }
    public void setProcess(String process) { this.process = process; }

    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }

    public double getRate() { return rate; }
    public void setRate(double rate) { this.rate = rate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    @Override
    public String toString() {
        return "Binding{process=" + process + ", qty=" + qty + ", amount=" + amount + "}";
    }

}
