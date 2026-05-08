package utils;

import java.util.prefs.Preferences;

import model.Invoice;

/**
 * Owner / company letterhead from General Settings (used on invoices, receipts, exports).
 */
public final class CompanyProfile {

	private static final Preferences PREFS = Preferences.userRoot().node("sunny_printers");

	private static final String K_NAME = "company_name";
	private static final String K_ADDRESS = "company_address";
	private static final String K_PHONE = "company_phone";
	private static final String K_EMAIL = "company_email";
	private static final String K_GST = "company_gst";

	private static final String D_NAME = "SUNNY PRINTERS";
	private static final String D_ADDRESS = "B-234, Naraina Industrial Area,\nPhase-1, New Delhi-110028";
	private static final String D_PHONE = "9811269375 9999662547";
	private static final String D_EMAIL = "sunny.printers@gmail.com";

	private CompanyProfile() {
	}

	public static String getName() {
		return nz(PREFS.get(K_NAME, ""), D_NAME);
	}

	public static String getAddress() {
		return nz(PREFS.get(K_ADDRESS, ""), D_ADDRESS);
	}

	public static String getPhone() {
		return nz(PREFS.get(K_PHONE, ""), D_PHONE);
	}

	public static String getEmail() {
		return nz(PREFS.get(K_EMAIL, ""), D_EMAIL);
	}

	public static String getGst() {
		String v = PREFS.get(K_GST, "");
		return v != null ? v.trim() : "";
	}

	private static String nz(String stored, String fallback) {
		if (stored == null || stored.isBlank()) {
			return fallback;
		}
		return stored.trim();
	}

	public static void setName(String v) {
		put(K_NAME, v);
	}

	public static void setAddress(String v) {
		put(K_ADDRESS, v);
	}

	public static void setPhone(String v) {
		put(K_PHONE, v);
	}

	public static void setEmail(String v) {
		put(K_EMAIL, v);
	}

	public static void setGst(String v) {
		put(K_GST, v);
	}

	private static void put(String key, String v) {
		if (v == null || v.isBlank()) {
			PREFS.remove(key);
		} else {
			PREFS.put(key, v.trim());
		}
	}

	/** Fills letterhead fields on an {@link Invoice}. */
	public static void applyToInvoice(Invoice invoice) {
		if (invoice == null) {
			return;
		}
		invoice.setCompanyName(getName());
		invoice.setCompanyAddress(getAddress());
		invoice.setCompanyContact(getPhone());
		invoice.setEmail(getEmail());
	}
}
