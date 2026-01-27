package model;

import java.io.Serializable;

public class Printing implements Serializable {

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	private int id;
    private int qty;              // optional (0 allowed)
    private String units;         // Sheet/Rim/Bundle...
    private String sets;          // ex: "2"
    private String color;         // 4 Color / 2 Color
    private String side;          // F/B / S/S
    private boolean withCtp;      // yes/no
    private String notes;         // optional
    private double amount;        // required (>=0)

    public Printing() {}

    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }

    public String getUnits() { return units; }
    public void setUnits(String units) { this.units = units; }

    public String getSets() { return sets; }
    public void setSets(String sets) { this.sets = sets; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public boolean isWithCtp() { return withCtp; }
    public void setWithCtp(boolean withCtp) { this.withCtp = withCtp; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    @Override
    public String toString() {
        return "Printing{qty=" + qty + ", units=" + units + ", amount=" + amount + "}";
    }
}
