package controller;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.MouseEvent;

import java.math.BigDecimal;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.ResourceBundle;

import model.Client;
import model.InvoiceMaster;
import repository.InvoiceMasterRepository;
import service.ClientService;
import utils.AtomicDB;
import utils.DBConnection;

/**
 * RecordPaymentController
 *
 * UI-only wiring for the Record Payment screen.
 * Database integration can be added later by calling your
 * existing DAO / service layer from the save methods.
 */
public class RecordPaymentController implements Initializable {

    // --- Top payment information ---
    @FXML
    private ComboBox<Client> clientCombo;
    @FXML
    private DatePicker paymentDatePicker;
    @FXML
    private TextField amountField;
    @FXML
    private ComboBox<String> paymentModeCombo;
    @FXML
    private TextField referenceField;
    @FXML
    private TextField notesField;
    @FXML
    private Label currentDateLabel;
    @FXML
    private Label footerClientLabel;

    // Cheque details
    @FXML
    private VBox chequeDetailsBox;
    @FXML
    private TextField chequeNumberField;
    @FXML
    private ComboBox<String> bankNameCombo;
    @FXML
    private DatePicker chequeDatePicker;
    @FXML
    private DatePicker clearanceDatePicker;

    // UPI details
    @FXML
    private VBox upiDetailsBox;
    @FXML
    private TextField upiIdField;
    @FXML
    private TextField upiUtrField;

    // Invoice table + footer
    @FXML
    private TableView<InvoiceRow> invoiceTable;
    @FXML
    private TableColumn<InvoiceRow, Boolean> selectColumn;
    @FXML
    private TableColumn<InvoiceRow, String> invoiceNoColumn;
    @FXML
    private TableColumn<InvoiceRow, String> invoiceDateColumn;
    @FXML
    private TableColumn<InvoiceRow, BigDecimal> totalAmountColumn;
    @FXML
    private TableColumn<InvoiceRow, BigDecimal> alreadyPaidColumn;
    @FXML
    private TableColumn<InvoiceRow, BigDecimal> dueAmountColumn;
    @FXML
    private TableColumn<InvoiceRow, BigDecimal> allocateAmountColumn;

    @FXML
    private Label invoiceCountLabel;
    @FXML
    private Label totalEnteredLabel;
    @FXML
    private Label totalAllocatedLabel;
    @FXML
    private Label remainingBalanceLabel;
    @FXML
    private Label excessPaymentLabel;

    private final ObservableList<InvoiceRow> invoiceItems = FXCollections.observableArrayList();
    private final ClientService clientService = new ClientService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupPaymentModeCombo();
        setupDefaults();
        loadClients();
        setupInvoiceTable();
        refreshFooterTotals();

        // Load invoices initially when client is selected
        clientCombo.setOnAction(e -> onClientSelected());
        amountField.textProperty().addListener((obs, o, n) -> refreshFooterTotals());
    }

    private void onClientSelected() {
        try {
            boolean hasClient = clientCombo.getSelectionModel().getSelectedItem() != null;
            setFormEnabled(hasClient);
            updateAmountEnabledState();

            if (hasClient) {
                loadOutstandingInvoicesForSelectedClient();
            } else {
                invoiceItems.clear();
                if (invoiceCountLabel != null)
                    invoiceCountLabel.setText("0 records found");
            }
            refreshFooterTotals();
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Error loading client details: " + e.getMessage(), ButtonType.OK)
                    .showAndWait();
        }
    }

    private void loadClients() {
        try {
            clientCombo.getItems().setAll(clientService.getAllClients());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupPaymentModeCombo() {
        paymentModeCombo.setItems(FXCollections.observableArrayList("Cash", "Cheque", "UPI"));
        paymentModeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateModeSpecificSections(newVal);
            updateAmountEnabledState();
        });
    }

    private void setupDefaults() {
        paymentDatePicker.setValue(LocalDate.now());

        if (currentDateLabel != null) {
            currentDateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        }
        if (footerClientLabel != null) {
            footerClientLabel.setText("Select Client");
        }

        // DatePickers: match invoice generation behavior (auto popup + disable future)
        setupAutoPopupDatePicker(paymentDatePicker);
        setupAutoPopupDatePicker(chequeDatePicker);
        setupAutoPopupDatePicker(clearanceDatePicker);

        // Guard against missing optional sections in FXML
        if (chequeDetailsBox != null) {
            chequeDetailsBox.setVisible(false);
            chequeDetailsBox.setManaged(false);
        }
        if (upiDetailsBox != null) {
            upiDetailsBox.setVisible(false);
            upiDetailsBox.setManaged(false);
        }

        // Hide excess pill initially (prevents visible yellow border)
        if (excessPaymentLabel != null) {
            excessPaymentLabel.setText("");
            excessPaymentLabel.setVisible(false);
            excessPaymentLabel.setManaged(false);
        }

        // Bank list
        if (bankNameCombo != null) {
            bankNameCombo.setEditable(true);
            bankNameCombo.getItems().setAll(getIndianBankNames());
        }

        // Disable all inputs until a client is selected
        setFormEnabled(false);
        updateAmountEnabledState();
    }

    private void setFormEnabled(boolean enabled) {
        // Top form
        if (paymentDatePicker != null)
            paymentDatePicker.setDisable(!enabled);
        if (referenceField != null)
            referenceField.setDisable(!enabled);
        if (paymentModeCombo != null)
            paymentModeCombo.setDisable(!enabled);
        if (notesField != null)
            notesField.setDisable(!enabled);

        // Amount depends on payment mode too
        if (amountField != null)
            amountField.setDisable(true);

        // Cheque / UPI fields (mode will further hide)
        if (chequeNumberField != null)
            chequeNumberField.setDisable(!enabled);
        if (bankNameCombo != null)
            bankNameCombo.setDisable(!enabled);
        if (chequeDatePicker != null)
            chequeDatePicker.setDisable(!enabled);
        if (clearanceDatePicker != null)
            clearanceDatePicker.setDisable(!enabled);
        if (upiIdField != null)
            upiIdField.setDisable(!enabled);
        if (upiUtrField != null)
            upiUtrField.setDisable(!enabled);

        // Allocation table
        if (invoiceTable != null)
            invoiceTable.setDisable(!enabled);
    }

    private void updateAmountEnabledState() {
        if (amountField == null)
            return;
        boolean hasClient = clientCombo != null && clientCombo.getSelectionModel().getSelectedItem() != null;
        boolean hasMode = paymentModeCombo != null && paymentModeCombo.getSelectionModel().getSelectedItem() != null;
        amountField.setDisable(!(hasClient && hasMode));
    }

    private void updateModeSpecificSections(String mode) {
        boolean isCheque = "Cheque".equalsIgnoreCase(mode);
        boolean isUpi = "UPI".equalsIgnoreCase(mode);

        if (chequeDetailsBox != null) {
            chequeDetailsBox.setVisible(isCheque);
            chequeDetailsBox.setManaged(isCheque);
            chequeDetailsBox.setDisable(!isCheque);
        }

        if (upiDetailsBox != null) {
            upiDetailsBox.setVisible(isUpi);
            upiDetailsBox.setManaged(isUpi);
            upiDetailsBox.setDisable(!isUpi);
        }
    }

    private void setupInvoiceTable() {
        if (invoiceTable == null)
            return;

        // If FXML does not define columns, create them programmatically
        if (selectColumn == null) {
            selectColumn = new TableColumn<>("Select");
            selectColumn.setPrefWidth(70);
        }
        if (invoiceNoColumn == null) {
            invoiceNoColumn = new TableColumn<>("Invoice No");
            invoiceNoColumn.setPrefWidth(140);
        }
        if (invoiceDateColumn == null) {
            invoiceDateColumn = new TableColumn<>("Date");
            invoiceDateColumn.setPrefWidth(110);
        }
        if (totalAmountColumn == null) {
            totalAmountColumn = new TableColumn<>("Total");
            totalAmountColumn.setPrefWidth(110);
        }
        if (alreadyPaidColumn == null) {
            alreadyPaidColumn = new TableColumn<>("Paid");
            alreadyPaidColumn.setPrefWidth(110);
        }
        if (dueAmountColumn == null) {
            dueAmountColumn = new TableColumn<>("Due");
            dueAmountColumn.setPrefWidth(110);
        }
        if (allocateAmountColumn == null) {
            allocateAmountColumn = new TableColumn<>("Allocate");
            allocateAmountColumn.setPrefWidth(130);
        }

        // Checkbox selection
        selectColumn.setCellValueFactory(param -> param.getValue().selectedProperty());
        selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));
        selectColumn.setEditable(true);

        invoiceNoColumn.setCellValueFactory(param -> param.getValue().invoiceNoProperty());
        invoiceDateColumn.setCellValueFactory(param -> param.getValue().invoiceDateProperty());
        totalAmountColumn.setCellValueFactory(param -> param.getValue().totalAmountProperty());
        alreadyPaidColumn.setCellValueFactory(param -> param.getValue().alreadyPaidProperty());
        dueAmountColumn.setCellValueFactory(param -> param.getValue().dueAmountProperty());
        allocateAmountColumn.setCellValueFactory(param -> param.getValue().allocateAmountProperty());

        // Make allocate column editable as a number textfield
        invoiceTable.setEditable(true);
        allocateAmountColumn.setCellFactory(column -> new EditingBigDecimalCell());

        if (invoiceTable.getColumns().isEmpty()) {
            invoiceTable.getColumns().add(selectColumn);
            invoiceTable.getColumns().add(invoiceNoColumn);
            invoiceTable.getColumns().add(invoiceDateColumn);
            invoiceTable.getColumns().add(totalAmountColumn);
            invoiceTable.getColumns().add(alreadyPaidColumn);
            invoiceTable.getColumns().add(dueAmountColumn);
            invoiceTable.getColumns().add(allocateAmountColumn);
        }

        invoiceTable.setItems(invoiceItems);
    }

    private BigDecimal parseAmountField() {
        try {
            String txt = amountField.getText();
            if (txt == null || txt.trim().isEmpty()) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(txt.trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private void refreshFooterTotals() {
        BigDecimal totalEntered = parseAmountField();
        BigDecimal totalAllocated = invoiceItems.stream()
                .filter(InvoiceRow::isSelected)
                .map(InvoiceRow::getAllocateAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Due = Total Allocated - Amount Received (requested)
        BigDecimal due = totalAllocated.subtract(totalEntered);
        if (due.compareTo(BigDecimal.ZERO) < 0)
            due = BigDecimal.ZERO;

        // Positive => excess payment
        BigDecimal remaining = totalEntered.subtract(totalAllocated);

        if (totalEnteredLabel != null) {
            totalEnteredLabel.setText(formatCurrency(totalEntered));
        }
        if (totalAllocatedLabel != null) {
            totalAllocatedLabel.setText(formatCurrency(totalAllocated));
        }
        if (remainingBalanceLabel != null) {
            remainingBalanceLabel.setText(formatCurrency(due));
        }

        if (excessPaymentLabel != null) {
            if (remaining.compareTo(BigDecimal.ZERO) < 0) {
                excessPaymentLabel.setText("EXCESS PAYMENT");
                excessPaymentLabel.setVisible(true);
                excessPaymentLabel.setManaged(true);
            } else {
                excessPaymentLabel.setText("");
                excessPaymentLabel.setVisible(false);
                excessPaymentLabel.setManaged(false);
            }
        }
    }

    private String formatCurrency(BigDecimal amount) {
        return "₹ " + amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    // --- Button handlers (skeletons) ---

    @FXML
    private void onAddPayment() {
        if (clientCombo.getSelectionModel().isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Please select a client first.", ButtonType.OK).showAndWait();
            return;
        }

        try {
            AtomicDB.runVoid(con -> {
                int clientId = getSelectedClientId(con);

                BigDecimal totalAmount = parseAmountField();
                if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Amount must be greater than zero");
                }

                String mode = paymentModeCombo.getSelectionModel().getSelectedItem();
                if (mode == null)
                    mode = "Cash";

                // 1) Insert into payments
                int paymentId;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO payments (client_id, amount, payment_date, method) VALUES (?,?,?,?)",
                        PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, clientId);
                    ps.setDouble(2, totalAmount.doubleValue());
                    ps.setString(3,
                            paymentDatePicker.getValue() == null ? null : paymentDatePicker.getValue().toString());
                    ps.setString(4, mode);
                    ps.executeUpdate();

                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Failed to generate payment id");
                        }
                        paymentId = rs.getInt(1);
                    }
                }

                // 2) Allocate amounts
                InvoiceMasterRepository repo = new InvoiceMasterRepository();

                for (InvoiceRow row : invoiceItems) {
                    if (!row.isSelected())
                        continue;
                    BigDecimal alloc = row.getAllocateAmount();
                    if (alloc == null || alloc.compareTo(BigDecimal.ZERO) <= 0)
                        continue;

                    int invoiceId = row.getInvoiceId();

                    // 2a) Insert allocation
                    try (PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO payment_allocations (payment_id, invoice_id, allocated_amount) VALUES (?,?,?)")) {
                        ps.setInt(1, paymentId);
                        ps.setInt(2, invoiceId);
                        ps.setDouble(3, alloc.doubleValue());
                        ps.executeUpdate();
                    }

                    // 2b) Update invoice
                    InvoiceMaster inv = repo.findById(con, invoiceId);
                    if (inv != null) {
                        double newPaid = inv.getPaidAmount() + alloc.doubleValue();
                        double newDue = inv.getAmount() - newPaid;
                        String status;
                        if (newDue <= 0.0001) {
                            status = "PAID";
                            newDue = 0.0;
                        } else if (newPaid > 0) {
                            status = "PARTIAL";
                        } else {
                            status = "DUE";
                        }
                        repo.updatePayment(con, invoiceId, newPaid, newDue, status, paymentDatePicker.getValue());
                    }
                }

                // 3) Payment details
                savePaymentDetails(con, paymentId, mode);
            });

            // Show success message
            new Alert(Alert.AlertType.INFORMATION, "Payment recorded successfully!", ButtonType.OK).showAndWait();

            // Reset form but KEEP client selected (better UX)
            onReset(false);

        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Failed to record payment: " + e.getMessage(), ButtonType.OK)
                    .showAndWait();
        }
    }

    @FXML
    private void onPrintReceipt() {
        // Placeholder: in future you can open a receipt window or generate PDF
        System.out.println("Print receipt clicked (not implemented yet)");
    }

    @FXML
    private void onReset() {
        // Default reset behavior (clear everything)
        onReset(true);
    }

    private void onReset(boolean clearClient) {
        if (clearClient) {
            clientCombo.getSelectionModel().clearSelection();
            if (footerClientLabel != null) {
                footerClientLabel.setText("Select Client");
            }
        }

        paymentDatePicker.setValue(LocalDate.now());
        amountField.clear();
        paymentModeCombo.getSelectionModel().clearSelection();
        referenceField.clear();
        notesField.clear();

        chequeNumberField.clear();
        if (bankNameCombo != null)
            bankNameCombo.getSelectionModel().clearSelection();
        chequeDatePicker.setValue(null);
        clearanceDatePicker.setValue(null);

        upiIdField.clear();
        upiUtrField.clear();

        // Reload invoices if client is still selected, otherwise clear table
        if (!clearClient && clientCombo.getSelectionModel().getSelectedItem() != null) {
            onClientSelected(); // Re-fetch from DB
        } else {
            invoiceItems.clear();
            if (invoiceCountLabel != null)
                invoiceCountLabel.setText("0 records found");
            setFormEnabled(false);
            updateAmountEnabledState();
            refreshFooterTotals();
        }
    }

    @FXML
    private void onCancel() {
        onReset(true);
    }

    // ------------------ DB helpers ------------------

    private void loadOutstandingInvoicesForSelectedClient() {
        try (Connection con = DBConnection.getConnection()) {
            int clientId = getSelectedClientId(con);
            if (clientId <= 0)
                return;

            InvoiceMasterRepository repo = new InvoiceMasterRepository();
            // Simple example: load recent invoices for client that still have due > 0
            String sql = """
                        SELECT * FROM invoice_master
                        WHERE client_id = ? AND is_void = 0 AND due_amount > 0
                        ORDER BY invoice_date DESC
                    """;

            invoiceItems.clear();

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, clientId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        InvoiceMaster inv = repo.mapRowPublic(rs);
                        BigDecimal due = BigDecimal.valueOf(inv.getDueAmount());
                        BigDecimal paid = BigDecimal.valueOf(inv.getPaidAmount());
                        BigDecimal total = BigDecimal.valueOf(inv.getAmount());

                        invoiceItems.add(new InvoiceRow(
                                inv.getId(),
                                inv.getInvoiceNo(),
                                inv.getInvoiceDate() == null ? "" : inv.getInvoiceDate().toString(),
                                total,
                                paid,
                                due,
                                due // default allocate full due
                        ));
                    }
                }
            }

            if (invoiceCountLabel != null) {
                invoiceCountLabel.setText(invoiceItems.size() + " records found");
            }

            invoiceItems.forEach(row -> {
                row.allocateAmountProperty().addListener((obs, o, n) -> refreshFooterTotals());
                row.selectedProperty().addListener((obs, o, n) -> refreshFooterTotals());
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Translate the selected client in ComboBox to its DB id.
     * For now this assumes clientCombo stores items as "ID - Name".
     * You can replace with a proper Client model if already available.
     */
    private int getSelectedClientId(Connection con) throws Exception {
        Client c = clientCombo.getSelectionModel().getSelectedItem();
        if (c == null)
            return -1;

        if (footerClientLabel != null) {
            // show business name in footer (as requested)
            String bn = c.getBusinessName() == null ? "" : c.getBusinessName().trim();
            footerClientLabel.setText(bn.isBlank() ? c.toString() : bn);
        }
        return c.getId();
    }

    // ==========================================================
    // DATE PICKER (same behavior as invoice generation)
    // ==========================================================
    private void setupAutoPopupDatePicker(DatePicker dp) {
        if (dp == null)
            return;

        dp.setEditable(false);

        // ✅ Disable future dates from calendar
        dp.setDayCellFactory(picker -> new DateCell() {
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

        // ✅ Auto open popup on click / focus
        dp.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (!dp.isShowing())
                dp.show();
        });

        dp.focusedProperty().addListener((obs, oldV, newV) -> {
            if (newV && !dp.isShowing())
                dp.show();
        });
    }

    private ObservableList<String> getIndianBankNames() {
        return FXCollections.observableArrayList(
                "State Bank of India (SBI)",
                "HDFC Bank",
                "ICICI Bank",
                "Axis Bank",
                "Kotak Mahindra Bank",
                "IndusInd Bank",
                "Punjab National Bank (PNB)",
                "Bank of Baroda",
                "Canara Bank",
                "Union Bank of India",
                "IDBI Bank",
                "Bank of India",
                "Central Bank of India",
                "Indian Bank",
                "Indian Overseas Bank",
                "UCO Bank",
                "Bank of Maharashtra",
                "Punjab & Sind Bank",
                "Yes Bank",
                "Federal Bank",
                "RBL Bank",
                "IDFC FIRST Bank",
                "AU Small Finance Bank",
                "Bandhan Bank",
                "South Indian Bank",
                "Karnataka Bank",
                "Karur Vysya Bank",
                "City Union Bank",
                "Tamilnad Mercantile Bank",
                "CSB Bank",
                "DCB Bank",
                "Jana Small Finance Bank",
                "Equitas Small Finance Bank",
                "Ujjivan Small Finance Bank",
                "Suryoday Small Finance Bank",
                "Utkarsh Small Finance Bank",
                "ESAF Small Finance Bank",
                "Fincare Small Finance Bank",
                "Shivalik Small Finance Bank",
                "Paytm Payments Bank",
                "Airtel Payments Bank",
                "India Post Payments Bank");
    }

    private void savePaymentDetails(Connection con, int paymentId, String mode) throws Exception {
        // Ensure supporting table exists
        try (PreparedStatement ps = con.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS payment_details (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        payment_id INTEGER NOT NULL,
                        field_key TEXT NOT NULL,
                        field_value TEXT,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(payment_id, field_key)
                    )
                """)) {
            ps.execute();
        }

        insertPaymentDetail(con, paymentId, "mode", mode);

        if ("Cheque".equalsIgnoreCase(mode)) {
            insertPaymentDetail(con, paymentId, "cheque_number", chequeNumberField.getText());
            insertPaymentDetail(con, paymentId, "bank_name", bankNameCombo == null ? null : bankNameCombo.getValue());
            insertPaymentDetail(con, paymentId, "cheque_date",
                    chequeDatePicker.getValue() == null ? null : chequeDatePicker.getValue().toString());
            insertPaymentDetail(con, paymentId, "clearance_date",
                    clearanceDatePicker.getValue() == null ? null : clearanceDatePicker.getValue().toString());
        } else if ("UPI".equalsIgnoreCase(mode)) {
            insertPaymentDetail(con, paymentId, "upi_id", upiIdField.getText());
            insertPaymentDetail(con, paymentId, "utr", upiUtrField.getText());
        }

        if (referenceField.getText() != null && !referenceField.getText().isBlank()) {
            insertPaymentDetail(con, paymentId, "reference", referenceField.getText());
        }
        if (notesField.getText() != null && !notesField.getText().isBlank()) {
            insertPaymentDetail(con, paymentId, "notes", notesField.getText());
        }
    }

    private void insertPaymentDetail(Connection con, int paymentId, String key, String value) throws Exception {
        if (key == null || value == null || value.isBlank())
            return;
        try (PreparedStatement ps = con.prepareStatement("""
                    INSERT OR REPLACE INTO payment_details (payment_id, field_key, field_value)
                    VALUES (?,?,?)
                """)) {
            ps.setInt(1, paymentId);
            ps.setString(2, key);
            ps.setString(3, value);
            ps.executeUpdate();
        }
    }

    // --- Helper classes ---

    public static class InvoiceRow {
        private final int invoiceId;
        private final StringProperty invoiceNo = new SimpleStringProperty();
        private final StringProperty invoiceDate = new SimpleStringProperty();
        private final ObjectProperty<BigDecimal> totalAmount = new SimpleObjectProperty<>();
        private final ObjectProperty<BigDecimal> alreadyPaid = new SimpleObjectProperty<>();
        private final ObjectProperty<BigDecimal> dueAmount = new SimpleObjectProperty<>();
        private final ObjectProperty<BigDecimal> allocateAmount = new SimpleObjectProperty<>();
        private final BooleanProperty selected = new SimpleBooleanProperty(true);

        public InvoiceRow(int invoiceId,
                String invoiceNo,
                String invoiceDate,
                BigDecimal totalAmount,
                BigDecimal alreadyPaid,
                BigDecimal dueAmount,
                BigDecimal allocateAmount) {
            this.invoiceId = invoiceId;
            this.invoiceNo.set(invoiceNo);
            this.invoiceDate.set(invoiceDate);
            this.totalAmount.set(totalAmount);
            this.alreadyPaid.set(alreadyPaid);
            this.dueAmount.set(dueAmount);
            this.allocateAmount.set(allocateAmount);
        }

        public StringProperty invoiceNoProperty() {
            return invoiceNo;
        }

        public StringProperty invoiceDateProperty() {
            return invoiceDate;
        }

        public ObjectProperty<BigDecimal> totalAmountProperty() {
            return totalAmount;
        }

        public ObjectProperty<BigDecimal> alreadyPaidProperty() {
            return alreadyPaid;
        }

        public ObjectProperty<BigDecimal> dueAmountProperty() {
            return dueAmount;
        }

        public ObjectProperty<BigDecimal> allocateAmountProperty() {
            return allocateAmount;
        }

        public BooleanProperty selectedProperty() {
            return selected;
        }

        public int getInvoiceId() {
            return invoiceId;
        }

        public boolean isSelected() {
            return selected.get();
        }

        public void setSelected(boolean value) {
            selected.set(value);
        }

        public BigDecimal getAllocateAmount() {
            return allocateAmount.get();
        }

        public void setAllocateAmount(BigDecimal value) {
            allocateAmount.set(value);
        }

        public BigDecimal getDueAmount() {
            return dueAmount.get() == null ? BigDecimal.ZERO : dueAmount.get();
        }
    }

    /**
     * Simple editable cell for BigDecimal values using a TextField.
     */
    private static class EditingBigDecimalCell extends TableCell<InvoiceRow, BigDecimal> {
        private final TextField textField = new TextField();

        EditingBigDecimalCell() {
            textField.setOnAction(e -> commitEdit(parse(textField.getText())));
            textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                if (!isNowFocused) {
                    commitEdit(parse(textField.getText()));
                }
            });
        }

        private BigDecimal parse(String s) {
            try {
                if (s == null || s.trim().isEmpty())
                    return BigDecimal.ZERO;
                return new BigDecimal(s.trim());
            } catch (Exception e) {
                return getItem() == null ? BigDecimal.ZERO : getItem();
            }
        }

        @Override
        public void startEdit() {
            super.startEdit();
            if (getItem() != null) {
                textField.setText(getItem().toPlainString());
            } else {
                textField.setText("");
            }
            setGraphic(textField);
            setText(null);
            textField.selectAll();
            textField.requestFocus();
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getItem() == null ? "" : getItem().toPlainString());
            setGraphic(null);
        }

        @Override
        protected void updateItem(BigDecimal item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
            } else if (isEditing()) {
                textField.setText(item == null ? "" : item.toPlainString());
                setGraphic(textField);
                setText(null);
            } else {
                setText(item == null ? "" : item.toPlainString());
                setGraphic(null);
            }
        }
    }
}
