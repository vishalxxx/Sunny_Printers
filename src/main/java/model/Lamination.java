package model;

import java.io.Serializable;
import java.util.Objects;

public class Lamination implements Serializable {

    /* ================= IDENTIFIERS ================= */

    private int id;
    private int jobItemId;

    /* ================= DATA ================= */

    private int qty;
    private String unit;
    private String type;
    private String side;
    private String size;
    private String notes;
    private double amount;

    /* ================= UI STATE FLAGS ================= */

    private transient boolean isNew;
    private transient boolean isUpdated;
    private transient boolean isDeleted;

    /* ================= ORIGINAL SNAPSHOT ================= */

    private transient Lamination originalSnapshot;

    public Lamination() {}

    /* ================= COPY ================= */

    public Lamination copy() {
        Lamination l = new Lamination();
        l.id = this.id;
        l.jobItemId = this.jobItemId;
        l.qty = this.qty;
        l.unit = this.unit;
        l.type = this.type;
        l.side = this.side;
        l.size = this.size;
        l.notes = this.notes;
        l.amount = this.amount;
        return l;
    }

    /* ================= SNAPSHOT ================= */

    public void captureOriginal() {
        this.originalSnapshot = this.copy();
    }

    /* ================= COMPARISON ================= */

    public boolean isDifferentFromOriginal() {
        if (originalSnapshot == null) return true;

        return qty != originalSnapshot.qty
            || !Objects.equals(unit, originalSnapshot.unit)
            || !Objects.equals(type, originalSnapshot.type)
            || !Objects.equals(side, originalSnapshot.side)
            || !Objects.equals(size, originalSnapshot.size)
            || !Objects.equals(notes, originalSnapshot.notes)
            || Double.compare(amount, originalSnapshot.amount) != 0;
    }

    public boolean isSameAsOriginal() {
        if (originalSnapshot == null) return false;

        return qty == originalSnapshot.qty
            && Objects.equals(unit, originalSnapshot.unit)
            && Objects.equals(type, originalSnapshot.type)
            && Objects.equals(side, originalSnapshot.side)
            && Objects.equals(size, originalSnapshot.size)
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

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getJobItemId() { return jobItemId; }
    public void setJobItemId(int jobItemId) { this.jobItemId = jobItemId; }

    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    /* ================= DISPLAY ================= */

    @Override
    public String toString() {
        return toShortText();
    }

    public String toShortText() {
        StringBuilder s = new StringBuilder();
        if (qty != 0) s.append(qty).append(" ");
        if (unit != null) s.append(unit).append(" - ");
        if (type != null) s.append(type).append(" - ");
        if (side != null) s.append(side).append(" - ");
        if (amount != 0.0) s.append("₹").append(amount);
        return s.toString().trim();
    }
}
