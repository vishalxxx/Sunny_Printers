package model;

import java.io.Serializable;
import java.util.Objects;

public class Printing implements Serializable {

    /* ================= IDs ================= */

    private String uuid;
    private String jobItemUuid;

    /* ================= DATA ================= */

    private int qty;
    private String units;
    private String sets;
    private String color;
    private String side;
    private boolean withCtp;
    private String notes;
    private double amount;
    
    private String syncStatus = "PENDING";
    private int syncVersion = 1;
    private int isDeletedSync = 0; // renamed to avoid conflict with UI flag isDeleted
    private int isActive = 1;
    private String createdAt;
    private String updatedAt;
    private String syncedAt;
    private String deletedAt;

    /* ================= UI FLAGS ================= */

    private transient boolean isNew;
    private transient boolean isUpdated;
    private transient boolean isDeleted;

    /* ================= SNAPSHOT ================= */

    private transient Printing originalSnapshot;

    /* ================= CONSTRUCTORS ================= */

    public Printing() {}

    /* ================= SNAPSHOT LOGIC ================= */

    public void captureOriginal() {
        this.originalSnapshot = this.copy();
    }

    public boolean isDifferentFromOriginal() {
        if (originalSnapshot == null) return true; // new item

        return qty != originalSnapshot.qty
            || !Objects.equals(units, originalSnapshot.units)
            || !Objects.equals(color, originalSnapshot.color)
            || !Objects.equals(side, originalSnapshot.side)
            || withCtp != originalSnapshot.withCtp
            || !Objects.equals(sets, originalSnapshot.sets)
            || !Objects.equals(notes, originalSnapshot.notes)
            || Double.compare(amount, originalSnapshot.amount) != 0;
    }

    public boolean isSameAsOriginal() {
        if (originalSnapshot == null) return false;
        return !isDifferentFromOriginal();
    }

    public Printing copy() {
        Printing p = new Printing();
        p.qty = qty;
        p.units = units;
        p.color = color;
        p.side = side;
        p.withCtp = withCtp;
        p.sets = sets;
        p.notes = notes;
        p.amount = amount;
        p.jobItemUuid = jobItemUuid;
        p.uuid = uuid;
        return p;
    }

    /* ================= FLAG HELPERS ================= */

    public void resetFlags() {
        this.isNew = false;
        this.isUpdated = false;
        this.isDeleted = false;
    }

    /* ================= GETTERS / SETTERS ================= */

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getJobItemUuid() { return jobItemUuid; }
    public void setJobItemUuid(String jobItemUuid) { this.jobItemUuid = jobItemUuid; }

    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }

    public String getUnits() { return units; }
    public void setUnits(String units) { this.units = units; }

    public String getSets() { return sets; }
    public void setSets(String sets) { this.sets = sets; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public boolean isWithCtp() { return withCtp; }
    public void setWithCtp(boolean withCtp) { this.withCtp = withCtp; }

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

    public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }

    public boolean isUpdated() { return isUpdated; }
    public void setUpdated(boolean isUpdated) { this.isUpdated = isUpdated; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean isDeleted) { this.isDeleted = isDeleted; }

    @Override
    public String toString() {
        return "Printing{qty=" + qty + ", units=" + units + ", amount=" + amount + "}";
    }
}
