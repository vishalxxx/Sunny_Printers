package model;

public class SystemSettings {

    // ================= DATABASE FIELDS =================
    private String invoiceMode;        // AUTO or MANUAL
    private String invoicePrefix;
    private int invoiceStartNo;
    private int invoicePadding;
    private int lastInvoiceNo;

    // ================= BUSINESS HELPERS =================
    public boolean isAuto() {
        return "AUTO".equalsIgnoreCase(invoiceMode);
    }

    public boolean isManual() {
        return "MANUAL".equalsIgnoreCase(invoiceMode);
    }

    // ================= GETTERS & SETTERS =================
    public String getInvoiceMode() {
        return invoiceMode;
    }

    public void setInvoiceMode(String invoiceMode) {
        this.invoiceMode = invoiceMode;
    }

    public String getInvoicePrefix() {
        return invoicePrefix;
    }

    public void setInvoicePrefix(String invoicePrefix) {
        this.invoicePrefix = invoicePrefix;
    }

    public int getInvoiceStartNo() {
        return invoiceStartNo;
    }

    public void setInvoiceStartNo(int invoiceStartNo) {
        this.invoiceStartNo = invoiceStartNo;
    }

    public int getInvoicePadding() {
        return invoicePadding;
    }

    public void setInvoicePadding(int invoicePadding) {
        this.invoicePadding = invoicePadding;
    }

    public int getLastInvoiceNo() {
        return lastInvoiceNo;
    }

    public void setLastInvoiceNo(int lastInvoiceNo) {
        this.lastInvoiceNo = lastInvoiceNo;
    }
}
