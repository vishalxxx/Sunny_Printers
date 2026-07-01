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
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import utils.DBConnection;

public class ClientLedgerController implements Initializable {

    /**
     * TableView height math must match on-screen rows (padding/fonts in client_ledger.css, status pills).
     * Underestimating clips the last row when pref height drives layout inside ScrollPane.
     */
    private static final double LEDGER_TABLE_HEADER_PX = 42.0;
    private static final double LEDGER_TABLE_ROW_PX = 46.0;
    private static final double LEDGER_TABLE_HEIGHT_FUDGE = 16.0;
    private static final int LEDGER_TABLE_MAX_VISIBLE_ROWS = 18;
    private static final int LEDGER_TABLE_EMPTY_VISIBLE_ROWS = 10;

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
    private javafx.scene.layout.HBox breadcrumbContainer;

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
    private TableColumn<LedgerEntry, String> colReceiptNo;
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
    private javafx.collections.transformation.FilteredList<LedgerEntry> filteredData;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null,
                () -> MainController.getInstance().handleBack(null));
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

        setupClientComboPopupWidthMatch();
    }

    /** Keeps the dropdown list the same width as the combo field (default popup sizes to longest item). */
    private void setupClientComboPopupWidthMatch() {
        if (clientCombo == null) {
            return;
        }
        Runnable bindPopup = () -> {
            if (clientCombo.getSkin() instanceof ComboBoxListViewSkin<?> skinNode) {
                javafx.scene.Node popup = skinNode.getPopupContent();
                if (popup instanceof javafx.scene.layout.Region region) {
                    region.minWidthProperty().bind(clientCombo.widthProperty());
                    region.prefWidthProperty().bind(clientCombo.widthProperty());
                    region.maxWidthProperty().bind(clientCombo.widthProperty());
                }
            }
        };
        clientCombo.skinProperty().addListener((obs, o, n) -> bindPopup.run());
        if (clientCombo.getSkin() != null) {
            bindPopup.run();
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
        colReceiptNo.setCellValueFactory(new PropertyValueFactory<>("receiptNo"));

        // Custom cell factory to wrap text
        colRef.setCellFactory(tc -> {
            TableCell<ClientLedgerController.LedgerEntry, String> cell = new TableCell<>() {
                private final javafx.scene.text.Text textNode = new javafx.scene.text.Text();
                {
                    textNode.wrappingWidthProperty().bind(widthProperty().subtract(24));
                    textNode.setFill(javafx.scene.paint.Color.web("#3E312D"));
                    textNode.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
                    textNode.setStyle(
                            "-fx-font-size: 12px; -fx-font-family: 'Inter', 'Segoe UI', System; -fx-font-weight: 600;");
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

        filteredData = new javafx.collections.transformation.FilteredList<>(ledgerData, p -> true);
        ledgerTable.setItems(filteredData);

        ledgerData.addListener((javafx.collections.ListChangeListener.Change<? extends LedgerEntry> c) -> {
            updateTableHeight();
            updateRecordCount();
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
                        utils.PaymentDetailsDialogUtil.showByUuid(ledgerTable.getScene().getWindow(), entry.getTxnUuid());
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
            String sql = "SELECT field_key, field_value FROM payment_details WHERE payment_uuid = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, entry.getTxnUuid());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String key = rs.getString("field_key").replace("_", " ").toUpperCase();
                    addDetailRow(grid, r++, key, rs.getString("field_value"));
                }
            }
            
            // Allocations
            String allocSql = "SELECT i.invoice_no, a.allocated_amount FROM payment_allocations a JOIN invoice_master i ON a.invoice_uuid = i.uuid WHERE a.payment_uuid = ?";
            try (PreparedStatement ps = con.prepareStatement(allocSql)) {
                ps.setString(1, entry.getTxnUuid());
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
        int rowCount = filteredData != null ? filteredData.size() : ledgerData.size();

        double targetHeight;
        if (rowCount == 0) {
            targetHeight = LEDGER_TABLE_HEADER_PX + (LEDGER_TABLE_EMPTY_VISIBLE_ROWS * LEDGER_TABLE_ROW_PX)
                    + LEDGER_TABLE_HEIGHT_FUDGE;
        } else {
            // Expand naturally so wrapped text and all rows display fully without bottom clipping
            targetHeight = LEDGER_TABLE_HEADER_PX + (rowCount * 50.0) + 28.0;
        }

        ledgerTable.setMinHeight(targetHeight);
        ledgerTable.setPrefHeight(targetHeight);
        ledgerTable.setMaxHeight(Double.MAX_VALUE);
    }

    private void loadClients() {
        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con
                        .prepareStatement("SELECT uuid, client_name, business_name FROM clients WHERE IFNULL(is_deleted,0)=0 ORDER BY client_name")) {
            ResultSet rs = ps.executeQuery();
            ObservableList<ClientComboItem> items = FXCollections.observableArrayList();
            while (rs.next()) {
                items.add(new ClientComboItem(rs.getString("uuid"), rs.getString("client_name"),
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

        loadClientDetails(selected.uuid);
        loadLedgerData();

        if (summaryCard != null) {
            summaryCard.setVisible(true);
            summaryCard.setManaged(true);
        }
    }

    public void refresh() {
        loadLedgerData();
    }

    @FXML
    private void onFilter() {
        loadLedgerData();
    }

    @FXML
    private void onExport(javafx.event.ActionEvent event) {
        ClientComboItem selected = clientCombo.getValue();
        if (selected == null) {
            utils.Toast.show((Stage) ledgerTable.getScene().getWindow(), "⚠ Select a client first!");
            return;
        }

        javafx.scene.control.Button btn = (javafx.scene.control.Button) event.getSource();
        javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();
        
        javafx.scene.control.MenuItem pdfItem = new javafx.scene.control.MenuItem("Export as PDF");
        pdfItem.setOnAction(e -> exportLedgerAsPDF());
        
        javafx.scene.control.MenuItem excelItem = new javafx.scene.control.MenuItem("Export as Excel");
        excelItem.setOnAction(e -> exportLedgerAsExcel());
        
        contextMenu.getItems().addAll(pdfItem, excelItem);
        contextMenu.show(btn, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    @FXML
    private void onPrint() {
        ClientComboItem selected = clientCombo.getValue();
        if (selected == null) {
            utils.Toast.show((Stage) ledgerTable.getScene().getWindow(), "⚠ Select a client first!");
            return;
        }
        try {
            String clientName = selected.clientName;
            String clientGst = gstLabel != null ? gstLabel.getText().replace("GST: ", "") : "";
            String clientAddress = addressLabel != null ? addressLabel.getText() : "";
            String clientPhone = phoneLabel != null ? phoneLabel.getText() : "";
            String clientEmail = emailLabel != null ? emailLabel.getText() : "";
            
            LocalDate from = dateFrom.getValue();
            LocalDate to = dateTo.getValue();
            
            double totalDebit = 0.0;
            double totalCredit = 0.0;
            for (LedgerEntry entry : ledgerData) {
                if (entry.getDebit() != null) totalDebit += entry.getDebit();
                if (entry.getCredit() != null) totalCredit += entry.getCredit();
            }
            double closingBalance = totalCredit - totalDebit;

            java.io.File file = service.LedgerExportService.generatePdfLedger(
                clientName, clientGst, clientAddress, clientPhone, clientEmail,
                from, to, ledgerData, totalDebit, totalCredit, closingBalance,
                true // isTemporary
            );
            
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.PRINT)) {
                java.awt.Desktop.getDesktop().print(file);
            } else {
                utils.Toast.show((Stage) ledgerTable.getScene().getWindow(), "Print not supported. Opening PDF.");
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                    java.awt.Desktop.getDesktop().open(file);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            utils.Toast.show((Stage) ledgerTable.getScene().getWindow(), "❌ Failed to print: " + e.getMessage());
        }
    }

    private void exportLedgerAsPDF() {
        ClientComboItem selected = clientCombo.getValue();
        if (selected == null) {
            utils.Toast.show((Stage) ledgerTable.getScene().getWindow(), "⚠ Select a client first!");
            return;
        }
        try {
            String clientName = selected.clientName;
            String clientGst = gstLabel != null ? gstLabel.getText().replace("GST: ", "") : "";
            String clientAddress = addressLabel != null ? addressLabel.getText() : "";
            String clientPhone = phoneLabel != null ? phoneLabel.getText() : "";
            String clientEmail = emailLabel != null ? emailLabel.getText() : "";
            
            LocalDate from = dateFrom.getValue();
            LocalDate to = dateTo.getValue();
            
            double totalDebit = 0.0;
            double totalCredit = 0.0;
            for (LedgerEntry entry : ledgerData) {
                if (entry.getDebit() != null) totalDebit += entry.getDebit();
                if (entry.getCredit() != null) totalCredit += entry.getCredit();
            }
            double closingBalance = totalCredit - totalDebit;

            java.io.File file = service.LedgerExportService.generatePdfLedger(
                clientName, clientGst, clientAddress, clientPhone, clientEmail,
                from, to, ledgerData, totalDebit, totalCredit, closingBalance
            );
            
            utils.Toast.show((Stage) ledgerTable.getScene().getWindow(), "Exported to PDF successfully! ✅");
            
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                java.awt.Desktop.getDesktop().open(file);
            }
        } catch (Exception e) {
            e.printStackTrace();
            utils.Toast.show((Stage) ledgerTable.getScene().getWindow(), "❌ Failed to export PDF: " + e.getMessage());
        }
    }

    private void exportLedgerAsExcel() {
        ClientComboItem selected = clientCombo.getValue();
        if (selected == null) {
            utils.Toast.show((Stage) ledgerTable.getScene().getWindow(), "⚠ Select a client first!");
            return;
        }
        try {
            String clientName = selected.clientName;
            String clientGst = gstLabel != null ? gstLabel.getText().replace("GST: ", "") : "";
            String clientAddress = addressLabel != null ? addressLabel.getText() : "";
            String clientPhone = phoneLabel != null ? phoneLabel.getText() : "";
            String clientEmail = emailLabel != null ? emailLabel.getText() : "";
            
            LocalDate from = dateFrom.getValue();
            LocalDate to = dateTo.getValue();
            
            double totalDebit = 0.0;
            double totalCredit = 0.0;
            for (LedgerEntry entry : ledgerData) {
                if (entry.getDebit() != null) totalDebit += entry.getDebit();
                if (entry.getCredit() != null) totalCredit += entry.getCredit();
            }
            double closingBalance = totalCredit - totalDebit;

            java.io.File file = service.LedgerExportService.generateExcelLedger(
                clientName, clientGst, clientAddress, clientPhone, clientEmail,
                from, to, ledgerData, totalDebit, totalCredit, closingBalance
            );
            
            utils.Toast.show((Stage) ledgerTable.getScene().getWindow(), "Exported to Excel successfully! ✅");
            
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                java.awt.Desktop.getDesktop().open(file);
            }
        } catch (Exception e) {
            e.printStackTrace();
            utils.Toast.show((Stage) ledgerTable.getScene().getWindow(), "❌ Failed to export Excel: " + e.getMessage());
        }
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

    private void loadClientDetails(String clientUuid) {
        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT * FROM clients WHERE uuid = ?")) {
            ps.setString(1, clientUuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                if (clientNameLabel != null)
                    clientNameLabel.setText(rs.getString("client_name"));
                if (gstLabel != null)
                    gstLabel.setText("GST: " + (rs.getString("gstin") == null ? "-" : rs.getString("gstin")));
                if (addressLabel != null)
                    addressLabel.setText(
                            rs.getString("shipping_address") == null || rs.getString("shipping_address").isBlank()
                                    ? (rs.getString("billing_address") == null ? "-" : rs.getString("billing_address"))
                                    : rs.getString("shipping_address"));
                if (phoneLabel != null)
                    phoneLabel.setText(rs.getString("mobile") == null ? "-" : rs.getString("mobile"));
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
        String clientUuid = selected.uuid;

        LocalDate from = dateFrom.getValue();
        LocalDate to = dateTo.getValue();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM (");

        // Invoices — no receipt_no, use NULL placeholder
        sql.append("SELECT uuid as txn_id, invoice_date as txn_date, created_at as created_ts, invoice_no as ref, 'INVOICE' as type, ");
        sql.append("'-' as mode, ");
        sql.append("0 as debit, amount as credit, status, payment_status, NULL as receipt_no ");
        sql.append("FROM invoice_master WHERE client_uuid = ? AND IFNULL(is_deleted, 0) = 0 AND IFNULL(is_void, 0) = 0 ");
        if (from != null)
            sql.append("AND invoice_date >= ? ");
        if (to != null)
            sql.append("AND invoice_date <= ? ");

        sql.append("UNION ALL ");

        // Payments
        sql.append("SELECT p.uuid as txn_id, p.payment_date as txn_date, p.created_at as created_ts, ");
        sql.append("COALESCE(");
        sql.append("  (SELECT GROUP_CONCAT(i.invoice_no, ', ') FROM payment_allocations a JOIN invoice_master i ON a.invoice_uuid = i.uuid WHERE a.payment_uuid = p.uuid AND COALESCE(a.is_deleted, 0) = 0), ");
        sql.append("  CASE WHEN p.type = 'Refund' THEN 'Advance Refund' ELSE 'Advance' END");
        sql.append(") as ref, ");
        sql.append("UPPER(p.type) as type, ");
        sql.append("p.method as mode, ");
        sql.append("p.amount as debit, 0 as credit, 'SUCCESS' as status, '' as payment_status, ");
        sql.append("(SELECT field_value FROM payment_details WHERE payment_uuid = p.uuid AND field_key = 'receipt_no') as receipt_no ");
        sql.append("FROM payments p WHERE p.client_uuid = ? AND IFNULL(p.is_deleted, 0) = 0 ");
        if (from != null)
            sql.append("AND p.payment_date >= ? ");
        if (to != null)
            sql.append("AND p.payment_date <= ? ");

        sql.append("UNION ALL ");

        // Credit / Debit Notes (invoice_adjustments) — note_no in receipt_no col, invoice_no in ref col
        sql.append("SELECT adj.uuid as txn_id, adj.date as txn_date, adj.created_at as created_ts, ");
        sql.append("inv.invoice_no as ref, ");
        sql.append("UPPER(adj.type) as type, ");
        sql.append("'-' as mode, ");
        sql.append("CASE WHEN adj.type = 'Credit Note' THEN adj.amount ELSE 0 END as debit, ");
        sql.append("CASE WHEN adj.type = 'Debit Note' THEN adj.amount ELSE 0 END as credit, ");
        sql.append("'SUCCESS' as status, '' as payment_status, adj.note_no as receipt_no ");
        sql.append("FROM invoice_adjustments adj ");
        sql.append("JOIN invoice_master inv ON adj.invoice_uuid = inv.uuid ");
        sql.append("WHERE inv.client_uuid = ? AND IFNULL(adj.is_deleted, 0) = 0 AND IFNULL(inv.is_deleted, 0) = 0 AND IFNULL(inv.is_void, 0) = 0 ");
        if (from != null)
            sql.append("AND adj.date >= ? ");
        if (to != null)
            sql.append("AND adj.date <= ? ");

        sql.append(") AS ledger_view ");

        if (rbInvoice != null && rbInvoice.isSelected()) {
            sql.append("WHERE type IN ('INVOICE', 'DEBIT NOTE') ");
        } else if (rbPayment != null && rbPayment.isSelected()) {
            sql.append("WHERE type IN ('PAYMENT', 'REFUND', 'CREDIT NOTE') ");
        }

        sql.append("ORDER BY substr(txn_date, 1, 10) ASC, CASE WHEN type = 'INVOICE' THEN 1 WHEN type = 'DEBIT NOTE' THEN 2 WHEN type = 'PAYMENT' THEN 3 ELSE 4 END ASC, created_ts ASC, txn_id ASC");

        double totalDebit = 0.0;
        double totalCredit = 0.0;
        double runningBalance = 0.0;

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql.toString())) {

            int idx = 1;

            // Invoices params
            ps.setString(idx++, clientUuid);
            if (from != null)
                ps.setString(idx++, from.toString());
            if (to != null)
                ps.setString(idx++, to.toString());

            // Payments params
            ps.setString(idx++, clientUuid);
            if (from != null)
                ps.setString(idx++, from.toString());
            if (to != null)
                ps.setString(idx++, to.toString());

            // Credit/Debit Notes params
            ps.setString(idx++, clientUuid);
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
                        String d = dateStr.length() >= 10 ? dateStr.substring(0, 10) : dateStr;
                        formattedDate = java.time.LocalDate.parse(d).format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                    }
                } catch (Exception e) {}

                String receiptNo = rs.getString("receipt_no");

                ledgerData.add(new LedgerEntry(
                        rs.getString("txn_id"),
                        formattedDate,
                        rs.getString("ref"),
                        rs.getString("type"),
                        rs.getString("mode"),
                        debit > 0 ? debit : null,
                        credit > 0 ? credit : null,
                        runningBalance,
                        finalStatus,
                        receiptNo));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        updateFooterTotals(totalDebit, totalCredit);
        updateRecordCount();
        updateTableHeight();
        if (ledgerTable != null) {
            ledgerTable.refresh();
        }
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
            int count = filteredData != null ? filteredData.size() : ledgerData.size();
            recordCountLabel.setText(count + " Records");
        }
    }

    private void filterList(String query) {
        if (filteredData == null) return;
        if (query == null || query.isBlank()) {
            filteredData.setPredicate(entry -> true);
        } else {
            String lowerCaseFilter = query.toLowerCase().trim();
            filteredData.setPredicate(entry -> {
                if (entry.getReference() != null && entry.getReference().toLowerCase().contains(lowerCaseFilter)) return true;
                if (entry.getReceiptNo() != null && entry.getReceiptNo().toLowerCase().contains(lowerCaseFilter)) return true;
                if (entry.getType() != null && entry.getType().toLowerCase().contains(lowerCaseFilter)) return true;
                if (entry.getMode() != null && entry.getMode().toLowerCase().contains(lowerCaseFilter)) return true;
                if (entry.getStatus() != null && entry.getStatus().toLowerCase().contains(lowerCaseFilter)) return true;
                if (entry.getDate() != null && entry.getDate().contains(lowerCaseFilter)) return true;
                return false;
            });
        }
        updateTableHeight();
        updateRecordCount();
    }

    // --- Inner Classes ---

    public static class ClientComboItem {
        String uuid;
        String clientName;
        String businessName;

        public ClientComboItem(String uuid, String clientName, String businessName) {
            this.uuid = uuid;
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
        String txnUuid;
        String date;
        String reference;
        // String description; // Removed
        String type;
        String mode;
        Double debit;
        Double credit;
        Double balance;
        String status;
        String receiptNo;

        public LedgerEntry(String txnUuid, String date, String reference, String type, String mode, Double debit, Double credit,
                Double balance, String status, String receiptNo) {
            this.txnUuid = txnUuid;
            this.date = date;
            this.reference = reference;
            this.type = type;
            this.mode = mode;
            this.debit = debit;
            this.credit = credit;
            this.balance = balance;
            this.status = status;
            this.receiptNo = receiptNo;
        }

        public String getTxnUuid() { return txnUuid; }

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

        public String getReceiptNo() {
            return receiptNo != null ? receiptNo : "";
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
