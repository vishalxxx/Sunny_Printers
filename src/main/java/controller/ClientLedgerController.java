package controller;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ResourceBundle;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import utils.DBConnection;

public class ClientLedgerController implements Initializable {

    @FXML
    private ComboBox<ClientComboItem> clientCombo;
    @FXML
    private DatePicker dateFrom;
    @FXML
    private DatePicker dateTo;

    @FXML
    private ToggleGroup typeGroup;
    @FXML
    private RadioButton rbAll;
    @FXML
    private RadioButton rbInvoice;
    @FXML
    private RadioButton rbPayment;

    @FXML
    private Label clientNameLabel, gstLabel, addressLabel, phoneLabel, emailLabel;
    @FXML
    private Label paymentLabel, netBalanceLabel, totalDueLabel;

    @FXML
    private javafx.scene.layout.HBox summaryCard;
    @FXML
    private javafx.scene.layout.StackPane gstBox;
    @FXML
    private javafx.scene.layout.VBox clientDetailsBox;

    @FXML
    private Label recordCountLabel;
    @FXML
    private TextField searchField;

    @FXML
    private TableView<LedgerEntry> ledgerTable;
    @FXML
    private TableColumn<LedgerEntry, String> colDate;
    @FXML
    private TableColumn<LedgerEntry, String> colRef;
    @FXML
    private TableColumn<LedgerEntry, String> colType;
    @FXML
    private TableColumn<LedgerEntry, String> colMode;
    @FXML
    private TableColumn<LedgerEntry, Double> colDebit;
    @FXML
    private TableColumn<LedgerEntry, Double> colCredit;
    @FXML
    private TableColumn<LedgerEntry, Double> colBalance;
    @FXML
    private TableColumn<LedgerEntry, String> colStatus;

    @FXML
    private Label footerTotalDebit;
    @FXML
    private Label footerTotalCredit;
    @FXML
    private Label footerClosingBalance;

    private ObservableList<LedgerEntry> ledgerData = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
        loadClients();
        clearClientDetails();

        setupAutoPopupDatePicker(dateFrom);
        setupAutoPopupDatePicker(dateTo);
        setupTableDoubleClickHandler();

        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterList(newVal));

        dateFrom.valueProperty().addListener((obs, oldVal, newVal) -> loadLedgerData());
        dateTo.valueProperty().addListener((obs, oldVal, newVal) -> loadLedgerData());

        if (typeGroup != null) {
            typeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> loadLedgerData());
        }
    }

    // ... (setupAutoPopupDatePicker omitted, keeping valid) ...

    private void setupAutoPopupDatePicker(DatePicker dp) {
        dp.setEditable(false);
        dp.setDayCellFactory(picker -> new javafx.scene.control.DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null)
                    return;
                if (date.isAfter(LocalDate.now())) {
                    setDisable(true);
                    setStyle("-fx-opacity: 0.35;");
                }
            }
        });

        javafx.event.EventHandler<javafx.scene.input.MouseEvent> clickHandler = e -> {
            if (!dp.isShowing())
                dp.show();
        };
        dp.getEditor().setOnMouseClicked(clickHandler);
        dp.setOnMouseClicked(clickHandler);
    }

    @FXML
    private void onReset() {
        dateFrom.setValue(null);
        dateTo.setValue(null);
        searchField.clear();
        clientCombo.setValue(null);
        clearClientDetails();
        ledgerData.clear();
        if (rbAll != null)
            rbAll.setSelected(true);
        updateFooterTotals(0, 0);
        updateRecordCount();
        updateTableHeight();
    }

    private void clearClientDetails() {
        if (clientNameLabel != null)
            clientNameLabel.setText("");
        if (gstLabel != null)
            gstLabel.setText("");
        if (addressLabel != null)
            addressLabel.setText("");
        if (phoneLabel != null)
            phoneLabel.setText("");
        if (emailLabel != null)
            emailLabel.setText("");

        // Hide containers
        if (gstBox != null)
            gstBox.setVisible(false);
        if (clientDetailsBox != null)
            clientDetailsBox.setVisible(false);

        // Hide summary card
        if (summaryCard != null) {
            summaryCard.setVisible(false);
            summaryCard.setManaged(false);
        }

        updateFooterTotals(0, 0);
    }

    private void setupTable() {
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colRef.setCellValueFactory(new PropertyValueFactory<>("reference"));

        // Custom cell factory to wrap text
        colRef.setCellFactory(tc -> {
            TableCell<ClientLedgerController.LedgerEntry, String> cell = new TableCell<>() {
                private final javafx.scene.text.Text textNode = new javafx.scene.text.Text();
                {
                    textNode.wrappingWidthProperty().bind(widthProperty().subtract(24));
                    textNode.setFill(javafx.scene.paint.Color.web("#e8eefb"));
                    textNode.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
                    textNode.setStyle(
                            "-fx-font-size: 12px; -fx-font-family: 'Segoe UI', System; -fx-font-weight: 600;");
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                        setText(null);
                    } else {
                        textNode.setText(item);
                        setGraphic(textNode);
                        setText(null);
                    }
                }
            };
            return cell;
        });

        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colMode.setCellValueFactory(new PropertyValueFactory<>("mode"));
        colDebit.setCellValueFactory(new PropertyValueFactory<>("debit"));
        colCredit.setCellValueFactory(new PropertyValueFactory<>("credit"));
        colBalance.setCellValueFactory(new PropertyValueFactory<>("balance"));

        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(c -> new StatusCell());
        colStatus.getStyleClass().add("center-align");

        colDebit.setCellFactory(c -> new CurrencyCell(true));
        colCredit.setCellFactory(c -> new CurrencyCell(false));
        colBalance.setCellFactory(c -> new CurrencyCell(false));

        ledgerTable.setItems(ledgerData);

        ledgerData.addListener((javafx.collections.ListChangeListener.Change<? extends LedgerEntry> c) -> {
            updateTableHeight();
        });
    }

    private void setupTableDoubleClickHandler() {
        ledgerTable.setRowFactory(tv -> {
            TableRow<LedgerEntry> row = new TableRow<>();
            
            // Visual hint: Change cursor to hand on hover
            row.setOnMouseEntered(event -> {
                if (!row.isEmpty()) {
                    row.setCursor(javafx.scene.Cursor.HAND);
                }
            });
            row.setOnMouseExited(event -> {
                row.setCursor(javafx.scene.Cursor.DEFAULT);
            });

            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    LedgerEntry entry = row.getItem();
                    // Show details only if it's a payment/refund
                    if (entry != null && (entry.getType().contains("PAYMENT") || entry.getType().contains("REFUND"))) {
                        showPaymentDetailsDialog(entry);
                    }
                }
            });
            return row;
        });
    }

    private void showPaymentDetailsDialog(LedgerEntry entry) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(ledgerTable.getScene().getWindow());
        dialog.setTitle("Payment Details - " + entry.getDate());
        
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(20);
        content.setPadding(new javafx.geometry.Insets(20));
        content.setPrefWidth(500);
        
        Label title = new Label("TRANSACTION DETAILS (" + entry.getType() + ")");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");
        
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        
        int r = 0;
        addDetailRow(grid, r++, "Date", entry.getDate());
        addDetailRow(grid, r++, "Reference", entry.getReference());
        addDetailRow(grid, r++, "Amount", "₹" + String.format("%.2f", (entry.getDebit() != null ? entry.getDebit() : entry.getCredit())));
        addDetailRow(grid, r++, "Method", entry.getMode());
        
        try (Connection con = DBConnection.getConnection()) {
            // Detailed properties
            String sql = "SELECT field_key, field_value FROM payment_details WHERE payment_id = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, entry.getTxnId());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String key = rs.getString("field_key").replace("_", " ").toUpperCase();
                    addDetailRow(grid, r++, key, rs.getString("field_value"));
                }
            }
            
            // Allocations
            String allocSql = "SELECT i.invoice_no, a.allocated_amount FROM payment_allocations a JOIN invoice_master i ON a.invoice_id = i.id WHERE a.payment_id = ?";
            try (PreparedStatement ps = con.prepareStatement(allocSql)) {
                ps.setInt(1, entry.getTxnId());
                ResultSet rs = ps.executeQuery();
                StringBuilder sb = new StringBuilder();
                while (rs.next()) {
                    sb.append(rs.getString("invoice_no")).append(" (₹").append(String.format("%.2f", rs.getDouble("allocated_amount"))).append(")\n");
                }
                if (sb.length() > 0) {
                    addDetailRow(grid, r++, "ALLOCATIONS", sb.toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        content.getChildren().addAll(title, new javafx.scene.control.Separator(), grid);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/css/record_payment.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("record-payment-root");
        dialog.showAndWait();
    }

    private void addDetailRow(javafx.scene.layout.GridPane grid, int row, String label, String value) {
        Label lbl = new Label(label + ":");
        lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #64748b;");
        Label val = new Label(value);
        val.setStyle("-fx-text-fill: #1e293b;");
        val.setWrapText(true);
        grid.add(lbl, 0, row);
        grid.add(val, 1, row);
    }

    private void updateTableHeight() {
        int rowCount = ledgerData.size();
        int maxRows = 10;

        double rowHeight = 38.0;
        double headerHeight = 40.0;

        double targetHeight;
        if (rowCount == 0) {
            targetHeight = headerHeight + rowHeight;
        } else {
            int rowsToShow = Math.min(rowCount, maxRows);
            targetHeight = headerHeight + (rowsToShow * rowHeight) + 2;
        }

        ledgerTable.setPrefHeight(targetHeight);
        ledgerTable.setMinHeight(targetHeight);
        ledgerTable.setMaxHeight(targetHeight);
    }

    private void loadClients() {
        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con
                        .prepareStatement("SELECT id, client_name, business_name FROM clients ORDER BY client_name")) {
            ResultSet rs = ps.executeQuery();
            ObservableList<ClientComboItem> items = FXCollections.observableArrayList();
            while (rs.next()) {
                items.add(new ClientComboItem(rs.getInt("id"), rs.getString("client_name"),
                        rs.getString("business_name")));
            }
            clientCombo.setItems(items);
            clientCombo.setConverter(new StringConverter<ClientComboItem>() {
                @Override
                public String toString(ClientComboItem object) {
                    return object == null ? "" : object.getDisplayName();
                }

                @Override
                public ClientComboItem fromString(String string) {
                    return null;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onClientSelected() {
        ClientComboItem selected = clientCombo.getValue();
        if (selected == null) {
            clearClientDetails();
            return;
        }

        loadClientDetails(selected.id);
        loadLedgerData();

        if (summaryCard != null) {
            summaryCard.setVisible(true);
            summaryCard.setManaged(true);
        }
    }

    @FXML
    private void onFilter() {
        loadLedgerData();
    }

    @FXML
    private void onExport() {
        System.out.println("Export button clicked");
    }

    @FXML
    private void onCreateInvoice() {
        System.out.println("Create Invoice clicked");
    }

    @FXML
    private void onRecordPayment() {
        if (MainController.getInstance() != null) {
            MainController.getInstance().loadRecordPayment();
        } else {
            System.err.println("MainController instance is null");
        }
    }

    private void loadClientDetails(int clientId) {
        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT * FROM clients WHERE id = ?")) {
            ps.setInt(1, clientId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                if (clientNameLabel != null)
                    clientNameLabel.setText(rs.getString("client_name"));
                if (gstLabel != null)
                    gstLabel.setText("GST: " + (rs.getString("gst") == null ? "-" : rs.getString("gst")));
                if (addressLabel != null)
                    addressLabel
                            .setText(rs.getString("shipping_address") == null ? "-" : rs.getString("shipping_address"));
                if (phoneLabel != null)
                    phoneLabel.setText(rs.getString("phone") == null ? "-" : rs.getString("phone"));
                if (emailLabel != null)
                    emailLabel.setText(rs.getString("email") == null ? "-" : rs.getString("email"));

                if (gstBox != null)
                    gstBox.setVisible(true);
                if (clientDetailsBox != null)
                    clientDetailsBox.setVisible(true);
                if (summaryCard != null) {
                    summaryCard.setVisible(true);
                    summaryCard.setManaged(true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadLedgerData() {
        ledgerData.clear();
        ClientComboItem selected = clientCombo.getValue();
        if (selected == null) {
            updateFooterTotals(0, 0);
            updateRecordCount();
            return;
        }
        int clientId = selected.id;

        LocalDate from = dateFrom.getValue();
        LocalDate to = dateTo.getValue();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM (");

        // Invoices
        sql.append("SELECT id as txn_id, invoice_date as txn_date, invoice_no as ref, 'INVOICE' as type, ");
        sql.append("'-' as mode, ");
        sql.append("0 as debit, amount as credit, status, payment_status ");
        sql.append("FROM invoice_master WHERE client_id = ? ");
        if (from != null)
            sql.append("AND invoice_date >= ? ");
        if (to != null)
            sql.append("AND invoice_date <= ? ");

        sql.append("UNION ALL ");

        // Payments
        sql.append("SELECT p.id as txn_id, p.payment_date as txn_date, ");
        sql.append("COALESCE(");
        sql.append("  (SELECT GROUP_CONCAT(i.invoice_no, ', ') FROM payment_allocations a JOIN invoice_master i ON a.invoice_id = i.id WHERE a.payment_id = p.id), ");
        sql.append("  CASE WHEN p.type = 'Refund' THEN 'Advance Refund' ELSE 'Advance' END");
        sql.append(") as ref, ");
        sql.append("UPPER(p.type) as type, ");
        sql.append("p.method as mode, ");
        sql.append("p.amount as debit, 0 as credit, 'SUCCESS' as status, '' as payment_status ");
        sql.append("FROM payments p WHERE p.client_id = ? ");
        if (from != null)
            sql.append("AND p.payment_date >= ? ");
        if (to != null)
            sql.append("AND p.payment_date <= ? ");

        sql.append(") AS ledger_view ");

        if (rbInvoice != null && rbInvoice.isSelected()) {
            sql.append("WHERE type = 'INVOICE' ");
        } else if (rbPayment != null && rbPayment.isSelected()) {
            sql.append("WHERE type = 'PAYMENT' ");
        }

        sql.append("ORDER BY txn_date");

        double totalDebit = 0.0;
        double totalCredit = 0.0;
        double runningBalance = 0.0;

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql.toString())) {

            int idx = 1;

            // Invoices params
            ps.setInt(idx++, clientId);
            if (from != null)
                ps.setString(idx++, from.toString());
            if (to != null)
                ps.setString(idx++, to.toString());

            // Payments params
            ps.setInt(idx++, clientId);
            if (from != null)
                ps.setString(idx++, from.toString());
            if (to != null)
                ps.setString(idx++, to.toString());

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                double debit = rs.getDouble("debit"); // Payment amount
                double credit = rs.getDouble("credit"); // Invoice amount
                
                // If it's a Refund (negative payment), treat it as a Credit (increases balance)
                if (debit < 0) {
                    credit = Math.abs(debit);
                    debit = 0;
                }
                
                runningBalance = runningBalance + credit - debit;

                totalDebit += debit;
                totalCredit += credit;

                String rawStatus = rs.getString("status");
                String payStatus = rs.getString("payment_status");
                String finalStatus = rawStatus;

                if ("INVOICE".equals(rs.getString("type"))) {
                    if ("DRAFT".equalsIgnoreCase(rawStatus)) {
                        finalStatus = "DRAFT";
                    } else {
                        finalStatus = payStatus;
                        if (finalStatus == null || finalStatus.isBlank())
                            finalStatus = "UNPAID";
                    }
                }

                String dateStr = rs.getString("txn_date");
                String formattedDate = dateStr;
                try {
                    if (dateStr != null && !dateStr.isBlank()) {
                        String d = dateStr.contains(" ") ? dateStr.split(" ")[0] : dateStr;
                        formattedDate = java.time.LocalDate.parse(d).format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                    }
                } catch (Exception e) {}

                ledgerData.add(new LedgerEntry(
                        rs.getInt("txn_id"),
                        formattedDate,
                        rs.getString("ref"),
                        rs.getString("type"),
                        rs.getString("mode"),
                        debit > 0 ? debit : null,
                        credit > 0 ? credit : null,
                        runningBalance,
                        finalStatus));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        updateFooterTotals(totalDebit, totalCredit);
        updateRecordCount();
        updateTableHeight();
    }

    private void updateFooterTotals(double debit, double credit) {
        if (footerTotalDebit != null)
            footerTotalDebit.setText("₹" + String.format("%.2f", debit));
        if (footerTotalCredit != null)
            footerTotalCredit.setText("₹" + String.format("%.2f", credit));

        double closing = credit - debit;
        if (footerClosingBalance != null)
            footerClosingBalance.setText("₹" + String.format("%.2f", closing));
            
        // ALSO UPDATE TOP SUMMARY CARD LABELS
        if (totalDueLabel != null)
            totalDueLabel.setText(String.format("%.2f", credit));
        if (paymentLabel != null)
            paymentLabel.setText(String.format("%.2f", debit));
        if (netBalanceLabel != null)
            netBalanceLabel.setText(String.format("%.2f", closing));
    }

    private void updateRecordCount() {
        if (recordCountLabel != null) {
            recordCountLabel.setText(ledgerData.size() + " Records");
        }
    }

    private void filterList(String query) {
        // Implementation for search filtering if needed
    }

    // --- Inner Classes ---

    public static class ClientComboItem {
        int id;
        String clientName;
        String businessName;

        public ClientComboItem(int id, String clientName, String businessName) {
            this.id = id;
            this.clientName = clientName;
            this.businessName = businessName;
        }

        public String getDisplayName() {
            if (businessName != null && !businessName.isBlank()) {
                return clientName + " (" + businessName + ")";
            }
            return clientName;
        }
    }

    public static class LedgerEntry {
        int txnId;
        String date;
        String reference;
        // String description; // Removed
        String type;
        String mode;
        Double debit;
        Double credit;
        Double balance;
        String status;

        public LedgerEntry(int txnId, String date, String reference, String type, String mode, Double debit, Double credit,
                Double balance, String status) {
            this.txnId = txnId;
            this.date = date;
            this.reference = reference;
            this.type = type;
            this.mode = mode;
            this.debit = debit;
            this.credit = credit;
            this.balance = balance;
            this.status = status;
        }

        public int getTxnId() { return txnId; }

        public String getDate() {
            return date;
        }

        public String getReference() {
            return reference;
        }

        public String getType() {
            return type;
        }

        public String getMode() {
            return mode;
        }

        public Double getDebit() {
            return debit;
        }

        public Double getCredit() {
            return credit;
        }

        public Double getBalance() {
            return balance;
        }

        public String getStatus() {
            return status;
        }
    }

    private class CurrencyCell extends TableCell<LedgerEntry, Double> {
        private boolean isDebit;

        public CurrencyCell(boolean isDebit) {
            this.isDebit = isDebit;
        }

        @Override
        protected void updateItem(Double item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null || item == 0) {
                setText(null);
                getStyleClass().removeAll("text-red", "text-green"); // Ensure styles are removed
            } else {
                setText(String.format("₹%.2f", item));
                getStyleClass().removeAll("text-red", "text-green"); // Clear previous styles
                if (isDebit)
                    getStyleClass().add("text-red");
                else
                    getStyleClass().add("text-green");
            }
        }
    }

    private class StatusCell extends TableCell<LedgerEntry, String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                Label label = new Label(item.toUpperCase());
                label.getStyleClass().add("status-pill");

                switch (item.toUpperCase()) {
                    case "PAID":
                    case "SUCCESS":
                    case "SETTLED":
                        label.getStyleClass().add("status-pill-success");
                        break;
                    case "UNPAID":
                    case "OVERDUE":
                        label.getStyleClass().add("status-pill-danger");
                        break;
                    case "DRAFT":
                        label.getStyleClass().add("status-pill-info");
                        break;
                    default:
                        label.getStyleClass().add("status-pill-default");
                }
                setGraphic(label);
                setText(null);
            }
        }
    }
}
