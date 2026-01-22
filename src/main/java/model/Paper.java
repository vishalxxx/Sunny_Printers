package model;

import java.io.Serializable;

public class Paper implements Serializable {

    private int qty;          // optional
    private String units;     // Sheet/Rim/Bundle/Kg...
    private String size;      // 12x18 / 13x19 ...
    private String gsm;       // 80/100/170...
    private String type;      // Maplitho/Art paper...
    private String source;    // Our / Client
    private String notes;     // optional
    private double amount;    // required

    public Paper() {}

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

    @Override
    public String toString() {
        return "Paper{qty=" + qty + ", units=" + units + ", amount=" + amount + "}";
    }
}
