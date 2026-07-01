package utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class InvoiceIdentifiers {

	private InvoiceIdentifiers() {
	}

	public static String newUuidString() {
		return ClientIdentifiers.newUuidV7String();
	}

	public static boolean invoiceNoInUse(Connection con, String invoiceNo, String excludeUuid) throws Exception {
		if (invoiceNo == null || invoiceNo.isBlank()) {
			return false;
		}
		String sql = excludeUuid == null || excludeUuid.isBlank()
				? "SELECT 1 FROM invoice_master WHERE invoice_no = ? AND IFNULL(is_deleted, 0) = 0 LIMIT 1"
				: "SELECT 1 FROM invoice_master WHERE invoice_no = ? AND uuid <> ? AND IFNULL(is_deleted, 0) = 0 LIMIT 1";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, DocumentNumbering.stripLeadingHash(invoiceNo.trim()));
			if (excludeUuid != null && !excludeUuid.isBlank()) {
				ps.setString(2, excludeUuid.trim());
			}
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}
}
