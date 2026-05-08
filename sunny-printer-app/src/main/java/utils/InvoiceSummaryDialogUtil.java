package utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import model.InvoiceMaster;
import service.InvoiceMasterService;

/**
 * Invoice summary popup (same visual language as payment receipt details).
 */
public final class InvoiceSummaryDialogUtil {

	private InvoiceSummaryDialogUtil() {
	}

	public static void showForInvoiceNo(Window owner, String invoiceNo) {
		if (owner == null || invoiceNo == null || invoiceNo.isBlank()) {
			return;
		}
		InvoiceMasterService svc = new InvoiceMasterService();
		InvoiceMaster inv = svc.getInvoiceByInvoiceNo(invoiceNo.trim());
		if (inv == null) {
			Alert a = new Alert(Alert.AlertType.INFORMATION, "Invoice not found.", ButtonType.OK);
			a.initOwner(owner);
			a.setHeaderText(null);
			a.showAndWait();
			return;
		}

		Dialog<Void> dialog = new Dialog<>();
		dialog.initOwner(owner);
		dialog.setTitle("Invoice Details - " + inv.getInvoiceNo());
		dialog.getDialogPane().getStyleClass().add("record-payment-root");

		VBox content = new VBox(20);
		content.setPadding(new Insets(20));
		content.setPrefWidth(520);

		Label title = new Label("INVOICE SUMMARY");
		title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");

		GridPane grid = new GridPane();
		grid.setHgap(20);
		grid.setVgap(12);
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");

		int r = 0;
		addRow(grid, r++, "Invoice no", inv.getInvoiceNo());
		addRow(grid, r++, "Date", formatDate(inv.getInvoiceDate(), fmt));
		addRow(grid, r++, "Client", inv.getClientName());
		addRow(grid, r++, "Type", nullToEmpty(inv.getType()));
		addRow(grid, r++, "Status", nullToEmpty(inv.getStatus()));
		addRow(grid, r++, "Payment status", nullToEmpty(inv.getPaymentStatus()));
		addRow(grid, r++, "Amount", String.format("Rs. %,.2f", inv.getAmount()));
		addRow(grid, r++, "Paid", String.format("Rs. %,.2f", inv.getPaidAmount()));
		addRow(grid, r++, "Due", String.format("Rs. %,.2f", inv.getDueAmount()));
		if (inv.getPeriodFrom() != null || inv.getPeriodTo() != null) {
			addRow(grid, r++, "Period",
					formatDate(inv.getPeriodFrom(), fmt) + " to " + formatDate(inv.getPeriodTo(), fmt));
		}

		content.getChildren().addAll(title, new Separator(), grid);
		dialog.getDialogPane().setContent(content);
		dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

		try {
			dialog.getDialogPane().getStylesheets()
					.add(InvoiceSummaryDialogUtil.class.getResource("/css/theme.css").toExternalForm());
			dialog.getDialogPane().getStylesheets()
					.add(InvoiceSummaryDialogUtil.class.getResource("/css/record_payment.css").toExternalForm());
		} catch (Exception ignored) {
		}

		dialog.showAndWait();
	}

	private static String nullToEmpty(String s) {
		return s != null ? s : "";
	}

	private static String formatDate(LocalDate d, DateTimeFormatter fmt) {
		return d != null ? d.format(fmt) : "-";
	}

	private static void addRow(GridPane grid, int row, String label, String value) {
		Label lbl = new Label(label + ":");
		lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #64748b;");
		Label val = new Label(value != null ? value : "");
		val.setStyle("-fx-text-fill: #1e293b;");
		val.setWrapText(true);
		grid.add(lbl, 0, row);
		grid.add(val, 1, row);
	}
}
