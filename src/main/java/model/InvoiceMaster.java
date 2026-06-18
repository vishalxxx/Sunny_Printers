package model;

import java.time.LocalDate;

public class InvoiceMaster {

    private String uuid;
    private String invoiceNo;

    private String clientUuid;
    private String clientName;

    private LocalDate invoiceDate;
    private LocalDate periodFrom;
    private LocalDate periodTo;

    private double amount;
    private double paidAmount;
    private double dueAmount;
    private String paymentStatus;

    private LocalDate lastPaymentDate;

    private String type;
    private String status;

    private String documentSeries;

    private Double cnAmount;
    private Double dnAmount;
    private int cnCount;
    private int dnCount;

    private boolean isVoid;
    private String voidReason;
    private LocalDate voidDate;

    private String replacedByInvoiceUuid;
    private String parentInvoiceUuid;

    private String statusUpdatedBy;

    private String filePath;
    
    private String syncStatus = "PENDING";
    private int syncVersion = 1;
    private int isDeleted = 0;
    private int isActive = 1;
    private String createdAt;
    private String updatedAt;
    private String syncedAt;
    private String deletedAt;

    /* ===== CONSTRUCTORS ===== */

    public InvoiceMaster() {}

    public InvoiceMaster(String invoiceNo, String clientId, String clientName,
                         LocalDate invoiceDate, double amount,
                         String type, String status) {

        this.invoiceNo = invoiceNo;
        this.clientUuid = clientId;
        this.clientName = clientName;
        this.invoiceDate = invoiceDate;
        this.amount = amount;
        this.type = type;
        this.status = status;

        this.paidAmount = 0;
        this.dueAmount = amount;
        
        if ("CANCELLED".equalsIgnoreCase(status)) {
            this.paymentStatus = "Void";
        } else {
            this.paymentStatus = "UNPAID";
        }
    }

    /* ===== GETTERS & SETTERS ===== */

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }

    public String getClientUuid() { return clientUuid; }
    public void setClientUuid(String clientUuid) { this.clientUuid = clientUuid; }

    /** Legacy alias. */
    public String getClientId() { return clientUuid; }
    public void setClientId(String clientId) { this.clientUuid = clientId; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }

    public LocalDate getPeriodFrom() { return periodFrom; }
    public void setPeriodFrom(LocalDate periodFrom) { this.periodFrom = periodFrom; }

    public LocalDate getPeriodTo() { return periodTo; }
    public void setPeriodTo(LocalDate periodTo) { this.periodTo = periodTo; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public double getPaidAmount() { return paidAmount; }
    public void setPaidAmount(double paidAmount) { this.paidAmount = paidAmount; }

    public double getNetAmount() {
        double cn = cnAmount != null ? cnAmount : 0;
        double dn = dnAmount != null ? dnAmount : 0;
        return amount + (dn - cn);
    }

    /**
     * "Paid" in the formula (Base + DN - CN) - (Paid - Refund) 
     * represents the total gross payments received.
     */
    public double getActualPaidAmount() {
        double netPaid = paidAmount; // This is (P - R) stored in DB
        double r = refundAmount != null ? Math.abs(refundAmount) : 0;
        return netPaid + r; // Returns P (Gross Payments)
    }

    /**
     * "Refund" in the formula (Base + DN - CN) - (Paid - Refund)
     * represents the absolute total of refunds given back.
     */
    public double getActualRefundAmount() {
        return refundAmount != null ? Math.abs(refundAmount) : 0; // Returns R (Absolute Refunds)
    }

    public double getDueAmount() {
        return dueAmount;
    }
    public void setDueAmount(double dueAmount) { this.dueAmount = dueAmount; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public LocalDate getLastPaymentDate() { return lastPaymentDate; }
    public void setLastPaymentDate(LocalDate lastPaymentDate) { this.lastPaymentDate = lastPaymentDate; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDocumentSeries() {
        return documentSeries;
    }

    public void setDocumentSeries(String documentSeries) {
        this.documentSeries = documentSeries;
    }

    /** Series used when allocating the final invoice number. */
    public MasterDocumentSeries resolveDocumentSeries() {
        if (documentSeries == null || documentSeries.isBlank()) {
            return MasterDocumentSeries.GST_INVOICE;
        }
        try {
            return MasterDocumentSeries.valueOf(documentSeries);
        } catch (IllegalArgumentException e) {
            return MasterDocumentSeries.GST_INVOICE;
        }
    }

    public Double getCnAmount() { return cnAmount; }
    public void setCnAmount(Double cnAmount) { this.cnAmount = cnAmount; }

    public Double getDnAmount() { return dnAmount; }
    public void setDnAmount(Double dnAmount) { this.dnAmount = dnAmount; }

    public int getCnCount() { return cnCount; }
    public void setCnCount(int cnCount) { this.cnCount = cnCount; }

    public int getDnCount() { return dnCount; }
    public void setDnCount(int dnCount) { this.dnCount = dnCount; }

    public Double getRefundAmount() { return refundAmount; }
    public void setRefundAmount(Double refundAmount) { this.refundAmount = refundAmount; }

    public int getRefundCount() { return refundCount; }
    public void setRefundCount(int refundCount) { this.refundCount = refundCount; }

    private Double refundAmount;
    private int refundCount;

    public String getAdjustment() {
        double cn = cnAmount != null ? cnAmount : 0;
        double dn = dnAmount != null ? dnAmount : 0;
        double net = dn - cn;
        
        if (net == 0) return "-";
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s%.1f", net > 0 ? "+" : "", net));
        
        if (cnCount > 0 || dnCount > 0) {
            sb.append(" (");
            java.util.List<String> labels = new java.util.ArrayList<>();
            if (cnCount > 0) labels.add("C" + cnCount);
            if (dnCount > 0) labels.add("D" + dnCount);
            sb.append(String.join(" | ", labels));
            sb.append(")");
        }
        return sb.toString();
    }

    public boolean isVoid() { return isVoid; }
    public void setVoid(boolean isVoid) { this.isVoid = isVoid; }

    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }

    public LocalDate getVoidDate() { return voidDate; }
    public void setVoidDate(LocalDate voidDate) { this.voidDate = voidDate; }

    public String getReplacedByInvoiceUuid() { return replacedByInvoiceUuid; }
    public void setReplacedByInvoiceUuid(String replacedByInvoiceUuid) { this.replacedByInvoiceUuid = replacedByInvoiceUuid; }

    public String getParentInvoiceUuid() { return parentInvoiceUuid; }
    public void setParentInvoiceUuid(String parentInvoiceUuid) { this.parentInvoiceUuid = parentInvoiceUuid; }

    /** Legacy int getters returning -1 or parsing if possible. */
    public Integer getReplacedByInvoiceId() { return null; }
    public Integer getParentInvoiceId() { return null; }
    public int getId() { return -1; }
    public void setId(int id) {}

    public String getStatusUpdatedBy() { return statusUpdatedBy; }
    public void setStatusUpdatedBy(String statusUpdatedBy) { this.statusUpdatedBy = statusUpdatedBy; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public LocalDate getCreatedAtDate() { return createdAt != null ? LocalDate.parse(createdAt.substring(0,10)) : null; }
    public void setCreatedAtDate(LocalDate createdAt) { this.createdAt = createdAt != null ? createdAt.toString() : null; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    public int getSyncVersion() { return syncVersion; }
    public void setSyncVersion(int syncVersion) { this.syncVersion = syncVersion; }

    public int getIsDeleted() { return isDeleted; }
    public void setIsDeleted(int isDeleted) { this.isDeleted = isDeleted; }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InvoiceMaster that = (InvoiceMaster) o;
        return uuid != null ? uuid.equals(that.uuid) : that.uuid == null;
    }

    @Override
    public int hashCode() {
        return uuid != null ? uuid.hashCode() : 0;
    }
}
