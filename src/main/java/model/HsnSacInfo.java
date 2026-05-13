package model;

public class HsnSacInfo {
    private String hsnSac;
    private double gstRate; // 0.18 for 18%
    private String unitDefault;

    public HsnSacInfo() {
    }

    public HsnSacInfo(String hsnSac, double gstRate, String unitDefault) {
        this.hsnSac = hsnSac;
        this.gstRate = gstRate;
        this.unitDefault = unitDefault;
    }

    public String getHsnSac() {
        return hsnSac;
    }

    public void setHsnSac(String hsnSac) {
        this.hsnSac = hsnSac;
    }

    public double getGstRate() {
        return gstRate;
    }

    public void setGstRate(double gstRate) {
        this.gstRate = gstRate;
    }

    public String getUnitDefault() {
        return unitDefault;
    }

    public void setUnitDefault(String unitDefault) {
        this.unitDefault = unitDefault;
    }
}
