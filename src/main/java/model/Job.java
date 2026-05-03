package model;

import java.time.LocalDate;

public class Job {

    private int id;
    private String jobNo;
    private Integer clientId; // nullable for draft

    private String jobTitle;
    private LocalDate jobDate;

    private String status;
    private String childStatus;
    private String remarks;

    private Integer invoiceId;
    private Double jobTotal;

    private String createdAt;
    private String updatedAt;
    private String imagePath;

    /** Filled when listing jobs joined with clients (e.g. monthly all-clients view). */
    private String clientBusinessName;

    public Job() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getJobNo() { return jobNo; }
    public void setJobNo(String jobNo) { this.jobNo = jobNo; }

    public Integer getClientId() { return clientId; }
    public void setClientId(Integer clientId) { this.clientId = clientId; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public LocalDate getJobDate() { return jobDate; }
    public void setJobDate(LocalDate jobDate) { this.jobDate = jobDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getChildStatus() { return childStatus; }
    public void setChildStatus(String childStatus) { this.childStatus = childStatus; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public Integer getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Integer invoiceId) { this.invoiceId = invoiceId; }

    public Double getJobTotal() { return jobTotal; }
    public void setJobTotal(Double jobTotal) { this.jobTotal = jobTotal; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getClientBusinessName() { return clientBusinessName; }
    public void setClientBusinessName(String clientBusinessName) { this.clientBusinessName = clientBusinessName; }

    private String invoiceNo;
    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }

    private String invoiceStatus;
    public String getInvoiceStatus() { return invoiceStatus; }
    public void setInvoiceStatus(String invoiceStatus) { this.invoiceStatus = invoiceStatus; }

    // --- REactive Selection Layer (Taste Design Support) ---
    private final javafx.beans.property.BooleanProperty selected = new javafx.beans.property.SimpleBooleanProperty(false);
    public javafx.beans.property.BooleanProperty selectedProperty() { return selected; }
    public boolean isSelected() { return selected.get(); }
    public void setSelected(boolean val) { this.selected.set(val); }
}
