package utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Shared payment detail popup (same content as Payment History row double-click).
 */
public final class PaymentDetailsDialogUtil {

	private PaymentDetailsDialogUtil() {
	}

	public static void show(Window owner, int paymentId) {
		if (owner == null || paymentId <= 0) {
			return;
		}
		PaymentHeader h = loadHeader(paymentId);
		if (h == null) {
			return;
		}

		Dialog<Void> dialog = new Dialog<>();
		dialog.initOwner(owner);
		dialog.setTitle("Payment Details - " + h.dateDisplay);
		dialog.getDialogPane().getStyleClass().add("record-payment-root");

		VBox content = new VBox(20);
		content.setPadding(new Insets(20));
		content.setPrefWidth(500);

		Label title = new Label("PAYMENT RECEIPT DETAILS");
		title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");

		GridPane grid = new GridPane();
		grid.setHgap(20);
		grid.setVgap(12);

		int r = 0;
		addDetailRow(grid, r++, "Date", h.dateDisplay);
		addDetailRow(grid, r++, "Client", h.client);
		addDetailRow(grid, r++, "Type", h.type);
		addDetailRow(grid, r++, "Method", h.method);
		addDetailRow(grid, r++, "Amount", h.amountDisplay);

		try (Connection con = DBConnection.getConnection()) {
			String sql = "SELECT field_key, field_value FROM payment_details WHERE payment_id = ?";
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, paymentId);
				ResultSet rs = ps.executeQuery();
				while (rs.next()) {
					String key = rs.getString("field_key").replace("_", " ").toUpperCase();
					addDetailRow(grid, r++, key, rs.getString("field_value"));
				}
			}

			String allocSql = """
					SELECT i.invoice_no, a.allocated_amount
					FROM payment_allocations a
					JOIN invoice_master i ON a.invoice_id = i.id
					WHERE a.payment_id = ?""";
			try (PreparedStatement ps = con.prepareStatement(allocSql)) {
				ps.setInt(1, paymentId);
				ResultSet rs = ps.executeQuery();
				StringBuilder sb = new StringBuilder();
				while (rs.next()) {
					sb.append(rs.getString("invoice_no")).append(" (Rs. ")
							.append(String.format("%.2f", rs.getDouble("allocated_amount"))).append(")\n");
				}
				if (sb.length() > 0) {
					addDetailRow(grid, r++, "ALLOCATIONS", sb.toString());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		content.getChildren().addAll(title, new Separator(), grid);
		dialog.getDialogPane().setContent(content);
		dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

		try {
			dialog.getDialogPane().getStylesheets()
					.add(PaymentDetailsDialogUtil.class.getResource("/css/theme.css").toExternalForm());
			dialog.getDialogPane().getStylesheets()
					.add(PaymentDetailsDialogUtil.class.getResource("/css/record_payment.css").toExternalForm());
		} catch (Exception ignored) {
		}

		dialog.showAndWait();
	}

	private record PaymentHeader(String dateDisplay, String client, String type, String method, String amountDisplay) {
	}

	private static PaymentHeader loadHeader(int paymentId) {
		NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
		try (Connection con = DBConnection.getConnection()) {
			String sql = """
					SELECT p.payment_date, p.type, p.method, p.amount,
					       c.business_name, c.client_name
					FROM payments p
					LEFT JOIN clients c ON c.id = p.client_id
					WHERE p.id = ?""";
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, paymentId);
				ResultSet rs = ps.executeQuery();
				if (!rs.next()) {
					return null;
				}
				String dateRaw = rs.getString("payment_date");
				String dateDisplay = formatPaymentDate(dateRaw);
				String bName = rs.getString("business_name");
				String cName = rs.getString("client_name");
				String client = (bName != null && !bName.isBlank()) ? bName : (cName != null ? cName : "");
				String typeStr = rs.getString("type");
				if (typeStr != null) {
					typeStr = typeStr.toUpperCase(Locale.ROOT);
				}
				String method = rs.getString("method") != null ? rs.getString("method") : "";
				double amount = rs.getDouble("amount");
				String amountDisplay = currencyFormat.format(amount);
				return new PaymentHeader(dateDisplay, client, typeStr, method, amountDisplay);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private static String formatPaymentDate(String dateRaw) {
		if (dateRaw == null || dateRaw.isBlank()) {
			return "";
		}
		try {
			String d = dateRaw.contains(" ") ? dateRaw.split(" ")[0] : dateRaw;
			LocalDate ld = LocalDate.parse(d);
			return ld.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
		} catch (Exception e) {
			return dateRaw;
		}
	}

	private static void addDetailRow(GridPane grid, int row, String label, String value) {
		Label lbl = new Label(label + ":");
		lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #64748b;");
		Label val = new Label(value != null ? value : "");
		val.setStyle("-fx-text-fill: #1e293b;");
		val.setWrapText(true);
		grid.add(lbl, 0, row);
		grid.add(val, 1, row);
	}
}
