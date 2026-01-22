package model;

public class CtpPlate {

    private int id;
    private int jobItemId;          // 🔥 important: link to job_items.id

    private int supplierId;
    private String supplierName;    // snapshot

    private int qty;
    private String plateSize;
    private String gauge;
    private String backing;
    private String color;
    private String notes;

    private double amount;

    private String createdAt;
    private String updatedAt;

    public CtpPlate() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getJobItemId() { return jobItemId; }
    public void setJobItemId(int jobItemId) { this.jobItemId = jobItemId; }

    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }

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

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

	
}
