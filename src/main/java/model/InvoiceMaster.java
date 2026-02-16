package model;

import java.time.LocalDate;

public class InvoiceMaster {

    private int id;

    private String invoiceNo;

    private int clientId;
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

    private boolean isVoid;
    private String voidReason;
    private LocalDate voidDate;

    private Integer replacedByInvoiceId;

    private String statusUpdatedBy;

    private String filePath;
    private LocalDate createdAt;

    /* ===== CONSTRUCTORS ===== */

    public InvoiceMaster() {}

    public InvoiceMaster(String invoiceNo, int clientId, String clientName,
                         LocalDate invoiceDate, double amount,
                         String type, String status) {

        this.invoiceNo = invoiceNo;
        this.clientId = clientId;
        this.clientName = clientName;
        this.invoiceDate = invoiceDate;
        this.amount = amount;
        this.type = type;
        this.status = status;

        this.paidAmount = 0;
        this.dueAmount = amount;
        this.paymentStatus = "UNPAID";
    }

    /* ===== GETTERS & SETTERS ===== */

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }

    public int getClientId() { return clientId; }
    public void setClientId(int clientId) { this.clientId = clientId; }

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

    public double getDueAmount() { return dueAmount; }
    public void setDueAmount(double dueAmount) { this.dueAmount = dueAmount; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public LocalDate getLastPaymentDate() { return lastPaymentDate; }
    public void setLastPaymentDate(LocalDate lastPaymentDate) { this.lastPaymentDate = lastPaymentDate; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isVoid() { return isVoid; }
    public void setVoid(boolean isVoid) { this.isVoid = isVoid; }

    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }

    public LocalDate getVoidDate() { return voidDate; }
    public void setVoidDate(LocalDate voidDate) { this.voidDate = voidDate; }

    public Integer getReplacedByInvoiceId() { return replacedByInvoiceId; }
    public void setReplacedByInvoiceId(Integer replacedByInvoiceId) { this.replacedByInvoiceId = replacedByInvoiceId; }

    public String getStatusUpdatedBy() { return statusUpdatedBy; }
    public void setStatusUpdatedBy(String statusUpdatedBy) { this.statusUpdatedBy = statusUpdatedBy; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public LocalDate getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDate createdAt) { this.createdAt = createdAt; }
}
