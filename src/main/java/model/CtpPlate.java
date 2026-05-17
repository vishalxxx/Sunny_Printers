package model;

import java.io.Serializable;
import java.util.Objects;

public class CtpPlate implements Serializable {

    /* ================= IDENTIFIERS ================= */

    private String uuid;
    private String jobItemUuid;

    private String supplierUuid;
    private String supplierName;

    /* ================= DATA ================= */

    private int qty;
    private String plateSize;
    private String gauge;
    private String backing;
    private String color;
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

    /* ================= UI FLAGS ================= */

    private transient boolean isNew;
    private transient boolean isUpdated;
    private transient boolean isDeleted;

    /* ================= SNAPSHOT ================= */

    private transient CtpPlate originalSnapshot;

    public CtpPlate() {}

    /* ================= COPY ================= */

    public CtpPlate copy() {
        CtpPlate c = new CtpPlate();
        c.uuid = this.uuid;
        c.jobItemUuid = this.jobItemUuid;
        c.supplierUuid = this.supplierUuid;
        c.supplierName = this.supplierName;
        c.qty = this.qty;
        c.plateSize = this.plateSize;
        c.gauge = this.gauge;
        c.backing = this.backing;
        c.color = this.color;
        c.notes = this.notes;
        c.amount = this.amount;
        return c;
    }

    /* ================= SNAPSHOT LOGIC ================= */

    public void captureOriginal() {
        this.originalSnapshot = this.copy();
    }

    /** true → values changed compared to DB snapshot */
    public boolean isDifferentFromOriginal() {
        if (originalSnapshot == null) return true; // new item

        return qty != originalSnapshot.qty
            || !Objects.equals(supplierName, originalSnapshot.supplierName)
            || !Objects.equals(plateSize, originalSnapshot.plateSize)
            || !Objects.equals(gauge, originalSnapshot.gauge)
            || !Objects.equals(backing, originalSnapshot.backing)
            || !Objects.equals(color, originalSnapshot.color)
            || !Objects.equals(notes, originalSnapshot.notes)
            || Double.compare(amount, originalSnapshot.amount) != 0;
    }

    /** true → identical to DB snapshot */
    public boolean isSameAsOriginal() {
        if (originalSnapshot == null) return false;

        return qty == originalSnapshot.qty
            && Objects.equals(supplierName, originalSnapshot.supplierName)
            && Objects.equals(plateSize, originalSnapshot.plateSize)
            && Objects.equals(gauge, originalSnapshot.gauge)
            && Objects.equals(backing, originalSnapshot.backing)
            && Objects.equals(color, originalSnapshot.color)
            && Objects.equals(notes, originalSnapshot.notes)
            && Double.compare(amount, originalSnapshot.amount) == 0;
    }

    /* ================= FLAGS ================= */

    public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }

    public boolean isUpdated() { return isUpdated; }
    public void setUpdated(boolean isUpdated) { this.isUpdated = isUpdated; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean isDeleted) { this.isDeleted = isDeleted; }

    public void resetFlags() {
        isNew = false;
        isUpdated = false;
        isDeleted = false;
    }

    /* ================= GETTERS / SETTERS ================= */

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getJobItemUuid() { return jobItemUuid; }
    public void setJobItemUuid(String jobItemUuid) { this.jobItemUuid = jobItemUuid; }

    public String getSupplierUuid() { return supplierUuid; }
    public void setSupplierUuid(String supplierUuid) { this.supplierUuid = supplierUuid; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }

    public String getPlateSize() { return plateSize; }
    public void setPlateSize(String plateSize) { this.plateSize = plateSize; }

    public String getGauge() { return gauge; }
    public void setGauge(String gauge) { this.gauge = gauge; }

    public String getBacking() { return backing; }
    public void setBacking(String backing) { this.backing = backing; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

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

    /* ================= DEBUG ================= */

    @Override
    public String toString() {
        return "CTP{qty=" + qty + ", size=" + plateSize + ", amount=" + amount + "}";
    }
}
