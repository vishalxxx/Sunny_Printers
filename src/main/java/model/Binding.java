package model;

import java.io.Serializable;
import java.util.Objects;

public class Binding implements Serializable {

    /* ================= DATA ================= */

    private String process;   // Perfect Binding / Spiral / Wire-O...
    private int qty;
    private double rate;
    private String notes;
    private double amount;

    private int jobItemId;
    public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public Binding getOriginalSnapshot() {
		return originalSnapshot;
	}

	public void setOriginalSnapshot(Binding originalSnapshot) {
		this.originalSnapshot = originalSnapshot;
	}

	private int id;
    
    
    /* ================= UI FLAGS ================= */

    private transient boolean isNew;
    private transient boolean isUpdated;
    private boolean deleted;

    /* ================= SNAPSHOT ================= */

    private transient Binding originalSnapshot;

    public Binding() {}

    /* ================= COPY ================= */

    public Binding copy() {
        Binding b = new Binding();
        b.process = this.process;
        b.qty = this.qty;
        b.rate = this.rate;
        b.notes = this.notes;
        b.amount = this.amount;
        b.jobItemId = this.jobItemId;
        return b;
    }

    /* ================= SNAPSHOT LOGIC ================= */

    public void captureOriginal() {
        this.originalSnapshot = this.copy();
    }

    public boolean isDifferentFromOriginal() {
        if (originalSnapshot == null) return true; // new item

        return qty != originalSnapshot.qty
            || Double.compare(rate, originalSnapshot.rate) != 0
            || Double.compare(amount, originalSnapshot.amount) != 0
            || !Objects.equals(process, originalSnapshot.process)
            || !Objects.equals(notes, originalSnapshot.notes);
    }

    public boolean isSameAsOriginal() {
        if (originalSnapshot == null) return false;

        return qty == originalSnapshot.qty
            && Double.compare(rate, originalSnapshot.rate) == 0
            && Double.compare(amount, originalSnapshot.amount) == 0
            && Objects.equals(process, originalSnapshot.process)
            && Objects.equals(notes, originalSnapshot.notes);
    }

    /* ================= FLAGS ================= */

    public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }

    public boolean isUpdated() { return isUpdated; }
    public void setUpdated(boolean isUpdated) { this.isUpdated = isUpdated; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public void resetFlags() {
        this.isNew = false;
        this.isUpdated = false;
        this.deleted = false;
    }

    /* ================= GETTERS / SETTERS ================= */

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

    public int getJobItemId() { return jobItemId; }
    public void setJobItemId(int jobItemId) { this.jobItemId = jobItemId; }

    @Override
    public String toString() {
        return "Binding{process=" + process + ", qty=" + qty + ", amount=" + amount + "}";
    }
}
