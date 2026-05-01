package utils;

import java.time.LocalDate;
import java.util.Locale;

public final class DocumentNumbering {

	public static final String PAYMENT_RECEIPT_CODE = "RCPT";

	private DocumentNumbering() {
	}

	public static String financialYearLabel(LocalDate date) {
		LocalDate d = date != null ? date : LocalDate.now();
		int y = d.getYear();
		int month = d.getMonthValue();
		int startYear = month >= 4 ? y : y - 1;
		int endYear = startYear + 1;
		return String.format("%02d-%02d", startYear % 100, endYear % 100);
	}

	/** Folder segment e.g. {@code FY_2026_27} (Indian FY Apr–Mar). */
	public static String financialYearFolderName(LocalDate date) {
		LocalDate d = date != null ? date : LocalDate.now();
		int y = d.getYear();
		int month = d.getMonthValue();
		int startYear = month >= 4 ? y : y - 1;
		int endYear = startYear + 1;
		return String.format("FY_%d_%02d", startYear, endYear % 100);
	}

	public static String companyPrefixFromTradeName(String tradeName) {
		if (tradeName == null || tradeName.isBlank()) {
			return "XX";
		}
		String[] parts = tradeName.trim().split("\\s+");
		if (parts.length == 1) {
			String w = parts[0];
			if (w.length() >= 2) {
				return w.substring(0, 2).toUpperCase(Locale.ROOT);
			}
			return (w + w).substring(0, 2).toUpperCase(Locale.ROOT);
		}
		String a = parts[0];
		String b = parts[parts.length - 1];
		return ("" + a.charAt(0) + b.charAt(0)).toUpperCase(Locale.ROOT);
	}

	public static String formatMasterLine(String companyPrefix, String typeCode, LocalDate refDate, int sequence,
			int padding) {
		String fy = financialYearLabel(refDate != null ? refDate : LocalDate.now());
		return formatMasterLine(companyPrefix, typeCode, fy, sequence, padding);
	}

	public static String formatMasterLine(String companyPrefix, String typeCode, String fyLabel, int sequence,
			int padding) {
		String cp = companyPrefix != null && !companyPrefix.isBlank() ? companyPrefix : "XX";
		String fy = fyLabel != null && !fyLabel.isBlank() ? fyLabel.trim()
				: financialYearLabel(LocalDate.now());
		return cp + "/" + typeCode + "/" + fy + "/" + String.format("%0" + Math.max(1, padding) + "d", sequence);
	}

	public static String formatPaymentReceiptNo(LocalDate paymentDate, int paymentId, int padding) {
		String cp = companyPrefixFromTradeName(CompanyProfile.getName());
		return formatMasterLine(cp, PAYMENT_RECEIPT_CODE, paymentDate, paymentId, padding);
	}

	/**
	 * Removes leading {@code #} characters from a document / reference string (legacy UI or pasted input).
	 * Returns {@code null} when {@code s} is {@code null}.
	 */
	public static String stripLeadingHash(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		while (t.startsWith("#")) {
			t = t.substring(1).trim();
		}
		return t;
	}
}