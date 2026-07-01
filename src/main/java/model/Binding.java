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

    private String syncStatus = "PENDING";
    private int syncVersion = 1;
    private int isDeletedSync = 0;
    private int isActive = 1;
    private String createdAt;
    private String updatedAt;
    private String syncedAt;
    private String deletedAt;

	private String uuid;
	private String jobItemUuid;

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getJobItemUuid() {
		return jobItemUuid;
	}

	public void setJobItemUuid(String jobItemUuid) {
		this.jobItemUuid = jobItemUuid;
	}

    /* ================= UI FLAGS ================= */

    private transient boolean isNew;
    private transient boolean isUpdated;
    private boolean deleted;
    private boolean includeNotesInInvoice = true;

    /* ================= SNAPSHOT ================= */

    private transient Binding originalSnapshot;

    public Binding() {}

    /* ================= COPY ================= */

    public Binding copy() {
        Binding b = new Binding();
        b.uuid = this.uuid;
        b.jobItemUuid = this.jobItemUuid;
        b.process = this.process;
        b.qty = this.qty;
        b.rate = this.rate;
        b.notes = this.notes;
        b.amount = this.amount;
        b.includeNotesInInvoice = this.includeNotesInInvoice;
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

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    public int getSyncVersion() { return syncVersion; }
    public void setSyncVersion(int syncVersion) { this.syncVersion = syncVersion; }

    public int getIsDeletedSync() { return isDeletedSync; }
    public void setIsDeletedSync(int isDeletedSync) { this.isDeletedSync = isDeletedSync; }

    public int getIsActive() { return isActive; }
    public void setIsActive(int isActive) { this.isActive = isActive; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getSyncedAt() { return syncedAt; }
    public void setSyncedAt(String syncedAt) { this.syncedAt = syncedAt; }

    public String getDeletedAt() { return deletedAt; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }

    public boolean isIncludeNotesInInvoice() { return includeNotesInInvoice; }
    public void setIncludeNotesInInvoice(boolean includeNotesInInvoice) { this.includeNotesInInvoice = includeNotesInInvoice; }

    @Override
    public String toString() {
        return "Binding{process=" + process + ", qty=" + qty + ", amount=" + amount + "}";
    }
}
