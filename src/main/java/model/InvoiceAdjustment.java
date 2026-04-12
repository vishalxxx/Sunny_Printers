package model;

import java.time.LocalDate;

public class InvoiceAdjustment {
    private int id;
    private int invoiceId;
    private String type;
    private String noteNo;
    private double amount;
    private String reason;
    private LocalDate date;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getInvoiceId() { return invoiceId; }
    public void setInvoiceId(int invoiceId) { this.invoiceId = invoiceId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getNoteNo() { return noteNo; }
    public void setNoteNo(String noteNo) { this.noteNo = noteNo; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
}
