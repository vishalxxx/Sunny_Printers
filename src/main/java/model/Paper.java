package model;

import java.io.Serializable;
import java.util.Objects;

public class Paper implements Serializable {

    private String uuid;
    private String jobItemUuid;
    private int qty;          // optional
    private String units;     // Sheet/Rim/Bundle/Kg...
    private String size;      // 12x18 / 13x19 ...
    private String gsm;       // 80/100/170...
    private String type;      // Maplitho/Art paper...
    private String source;    // Our / Client
    private String notes;     // optional
    private double amount;    // required
    
    private String syncStatus = "PENDING";
    private int syncVersion = 1;
    private int isDeletedSync = 0;
    private int isActive = 1;
    private String createdAt;
    private String updatedAt;
    private String syncedAt;
    private String deletedAt;

    public Paper() {}

    public Paper copy() {
        Paper p = new Paper();
        p.setUuid(this.uuid);
        p.setJobItemUuid(this.jobItemUuid);
        p.setQty(this.qty);
        p.setUnits(this.units);
        p.setSize(this.size);
        p.setGsm(this.gsm);
        p.setType(this.type);
        p.setSource(this.source);
        p.setNotes(this.notes);
        p.setAmount(this.amount);
        return p;
    }

 // snapshot of original DB state
    private Paper originalSnapshot;

    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }

    public String getUnits() { return units; }
    public void setUnits(String units) { this.units = units; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public String getGsm() { return gsm; }
    public void setGsm(String gsm) { this.gsm = gsm; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getJobItemUuid() { return jobItemUuid; }
    public void setJobItemUuid(String jobItemUuid) { this.jobItemUuid = jobItemUuid; }


	private transient boolean isNew;
    private transient boolean isUpdated;

    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }

    public boolean isUpdated() {
        return isUpdated;
    }

    public void setUpdated(boolean isUpdated) {
        this.isUpdated = isUpdated;
    }
    
    
    private boolean deleted;

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    
    
    
    @Override
    public String toString() {
        return "Paper{qty=" + qty + ", units=" + units + ", amount=" + amount + "}";
    }
    
    public void captureOriginal() {
        Paper o = new Paper();

        o.setQty(this.getQty());
        o.setUnits(this.getUnits());
        o.setSize(this.getSize());
        o.setGsm(this.getGsm());
        o.setType(this.getType());
        o.setAmount(this.getAmount());
        o.setSource(this.getSource());
        o.setNotes(this.getNotes());

        this.originalSnapshot = o;
    }
    
    public boolean isDifferentFromOriginal() {

        if (originalSnapshot == null) return true; // new item

        return getQty() != originalSnapshot.getQty()
            || !java.util.Objects.equals(getUnits(), originalSnapshot.getUnits())
            || !java.util.Objects.equals(getSize(), originalSnapshot.getSize())
            || !java.util.Objects.equals(getGsm(), originalSnapshot.getGsm())
            || !java.util.Objects.equals(getType(), originalSnapshot.getType())
            || getAmount() != originalSnapshot.getAmount()
            || !java.util.Objects.equals(getSource(), originalSnapshot.getSource())
            || !java.util.Objects.equals(getNotes(), originalSnapshot.getNotes());
    }

    public boolean isSameAsOriginal() {
        if (originalSnapshot == null) return false;

        return qty == originalSnapshot.qty
            && Objects.equals(units, originalSnapshot.units)
            && Objects.equals(size, originalSnapshot.size)
            && Objects.equals(gsm, originalSnapshot.gsm)
            && Objects.equals(type, originalSnapshot.type)
            && Objects.equals(source, originalSnapshot.source)
            && Objects.equals(notes, originalSnapshot.notes)
            && Double.compare(amount, originalSnapshot.amount) == 0;
    }

    public void resetFlags() {
        this.setNew(false);
        this.setUpdated(false);
        this.setDeleted(false);
    }

    
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
}
