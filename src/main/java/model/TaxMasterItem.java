package model;

import java.util.Locale;

/**
 * Row from {@code hsn_sac_master} for the Tax Master (HSN/SAC) settings screen.
 */
public class TaxMasterItem {

	private int id;
	private String itemType = "";
	private String itemName = "";
	private String keyword = "";
	private String codeType = "HSN";
	private String hsnSac = "";
	private double gstRate = 0.18;
	private String unitDefault = "";
	private String description = "";
	private boolean favorite;
	private boolean active = true;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getItemType() {
		return itemType;
	}

	public void setItemType(String itemType) {
		this.itemType = itemType != null ? itemType : "";
	}

	public String getItemName() {
		return itemName;
	}

	public void setItemName(String itemName) {
		this.itemName = itemName != null ? itemName : "";
	}

	public String getKeyword() {
		return keyword;
	}

	public void setKeyword(String keyword) {
		this.keyword = keyword != null ? keyword : "";
	}

	public String getCodeType() {
		return codeType;
	}

	public void setCodeType(String codeType) {
		this.codeType = codeType != null && !codeType.isBlank() ? codeType.trim().toUpperCase(Locale.ROOT) : "HSN";
	}

	public String getHsnSac() {
		return hsnSac;
	}

	public void setHsnSac(String hsnSac) {
		this.hsnSac = hsnSac != null ? hsnSac : "";
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
		this.unitDefault = unitDefault != null ? unitDefault : "";
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description != null ? description : "";
	}

	public boolean isFavorite() {
		return favorite;
	}

	public void setFavorite(boolean favorite) {
		this.favorite = favorite;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	/** Display label for category column (from {@code item_type}). */
	public String categoryLabel() {
		String t = itemType != null ? itemType.trim() : "";
		if (t.isEmpty()) {
			return "—";
		}
		String lower = t.toLowerCase(Locale.ROOT);
		return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
	}
}
