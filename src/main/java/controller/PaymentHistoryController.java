package controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import model.Client;
import model.InvoiceMaster;
import repository.ClientRepository;
import service.InvoiceMasterService;
import utils.DBConnection;
import javafx.geometry.Insets;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;
import java.util.ResourceBundle;

public class PaymentHistoryController implements Initializable {

    @FXML private ComboBox<Client> clientCombo;
    @FXML private ComboBox<InvoiceMaster> invoiceCombo;
    @FXML private DatePicker fromDate;
    @FXML private DatePicker toDate;
    @FXML private TextField searchField;
    @FXML private Label totalAmountLabel;

    @FXML private TableView<PaymentRow> paymentsTable;
    @FXML private TableColumn<PaymentRow, String> colDate;
    @FXML private TableColumn<PaymentRow, String> colClient;
    @FXML private TableColumn<PaymentRow, String> colType;
    @FXML private TableColumn<PaymentRow, String> colInvoiceRef;
    @FXML private TableColumn<PaymentRow, String> colMethod;
    @FXML private TableColumn<PaymentRow, String> colAmount;
    @FXML private TableColumn<PaymentRow, String> colReference;

    private ObservableList<PaymentRow> masterPaymentList = FXCollections.observableArrayList();
    private FilteredList<PaymentRow> filteredList;

    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();
        loadClients();
        loadInvoices(null);
        setupSearchFilter();
        setupTableDoubleClickHandler();

        // Fix date picker popup
        setupAutoPopupDatePicker(fromDate);
        setupAutoPopupDatePicker(toDate);

        // Listeners for triggers
        clientCombo.valueProperty().addListener((obs, oldV, newV) -> {
            loadInvoices(newV);
            loadPaymentData();
        });
        invoiceCombo.valueProperty().addListener((obs, oldV, newV) -> loadPaymentData());
        fromDate.valueProperty().addListener((obs, oldV, newV) -> loadPaymentData());
        toDate.valueProperty().addListener((obs, oldV, newV) -> loadPaymentData());

        // Initial load
        loadPaymentData();
    }

    private void setupTableColumns() {
        colDate.setCellValueFactory(cell -> cell.getValue().dateProperty());
        colClient.setCellValueFactory(cell -> cell.getValue().clientProperty());
        colType.setCellValueFactory(cell -> cell.getValue().typeProperty());
        colInvoiceRef.setCellValueFactory(cell -> cell.getValue().invoiceRefProperty());
        colMethod.setCellValueFactory(cell -> cell.getValue().methodProperty());
        colAmount.setCellValueFactory(cell -> cell.getValue().amountProperty());
        colReference.setCellValueFactory(cell -> cell.getValue().referenceProperty());
    }

    private void setupAutoPopupDatePicker(DatePicker dp) {
        if (dp == null)
            return;

        dp.setEditable(false);
        dp.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (date != null && date.isAfter(LocalDate.now())) {
                    setDisable(true);
                    setStyle("-fx-opacity: 0.35;");
                }
            }
        });

        // Auto open popup on click / focus
        dp.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (!dp.isShowing())
                dp.show();
        });

        dp.focusedProperty().addListener((obs, oldV, newV) -> {
            if (newV && !dp.isShowing())
                dp.show();
        });
    }

    private void loadClients() {
        try {
            ClientRepository clientRepo = new ClientRepository();
            ObservableList<Client> clients = FXCollections.observableArrayList(clientRepo.findAllSortedById());
            clientCombo.setItems(clients);
            
            // Set how the client object is displayed in the combobox
            clientCombo.setConverter(new javafx.util.StringConverter<Client>() {
                @Override
                public String toString(Client client) {
                    if (client == null) return null;
                    String bn = client.getBusinessName();
                    return (bn != null && !bn.trim().isEmpty()) ? bn : client.getClientName();
                }

                @Override
                public Client fromString(String string) {
                    return null; // not needed
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to load clients: " + e.getMessage());
        }
    }

    private void loadInvoices(Client selectedClient) {
        try {
            InvoiceMasterService invService = new InvoiceMasterService();
            java.util.List<InvoiceMaster> list;
            if (selectedClient != null) {
                list = invService.getInvoicesByClientId(selectedClient.getId());
            } else {
                list = invService.getRecentInvoices(200);
            }
            invoiceCombo.setItems(FXCollections.observableArrayList(list));
            
            invoiceCombo.setConverter(new javafx.util.StringConverter<InvoiceMaster>() {
                @Override
                public String toString(InvoiceMaster inv) {
                    if (inv == null) return null;
                    return inv.getInvoiceNo();
                }
                @Override
                public InvoiceMaster fromString(String string) { return null; }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupTableDoubleClickHandler() {
        paymentsTable.setRowFactory(tv -> {
            TableRow<PaymentRow> row = new TableRow<>();
            
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
                    PaymentRow rowData = row.getItem();
                    if (rowData != null) {
                        showPaymentDetailsDialog(rowData);
                    }
                }
            });
            return row;
        });
    }

    private void showPaymentDetailsDialog(PaymentRow payment) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(paymentsTable.getScene().getWindow());
        dialog.setTitle("Payment Details - " + payment.getDate());
        dialog.getDialogPane().getStyleClass().add("record-payment-root"); // Reuse styles
        
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setPrefWidth(500);
        
        Label title = new Label("PAYMENT RECEIPT DETAILS");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");
        
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        
        int r = 0;
        addDetailRow(grid, r++, "Date", payment.getDate());
        addDetailRow(grid, r++, "Client", payment.getClient());
        addDetailRow(grid, r++, "Type", payment.getType());
        addDetailRow(grid, r++, "Method", payment.getMethod());
        addDetailRow(grid, r++, "Amount", payment.getAmount());
        
        // Fetch details from DB
        try (Connection con = DBConnection.getConnection()) {
            String sql = "SELECT field_key, field_value FROM payment_details WHERE payment_id = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, payment.getId());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String key = rs.getString("field_key").replace("_", " ").toUpperCase();
                    addDetailRow(grid, r++, key, rs.getString("field_value"));
                }
            }
            
            // Also show allocated invoices
            String allocSql = "SELECT i.invoice_no, a.allocated_amount FROM payment_allocations a JOIN invoice_master i ON a.invoice_id = i.id WHERE a.payment_id = ?";
            try (PreparedStatement ps = con.prepareStatement(allocSql)) {
                ps.setInt(1, payment.getId());
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
        
        content.getChildren().addAll(title, new Separator(), grid);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        
        // Add stylesheet explicitly to dialog
        try {
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("/css/record_payment.css").toExternalForm());
        } catch (Exception e) {}
        
        dialog.showAndWait();
    }

    private void addDetailRow(GridPane grid, int row, String label, String value) {
        Label lbl = new Label(label + ":");
        lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #64748b;");
        Label val = new Label(value);
        val.setStyle("-fx-text-fill: #1e293b;");
        val.setWrapText(true);
        grid.add(lbl, 0, row);
        grid.add(val, 1, row);
    }

    private void loadPaymentData() {
        masterPaymentList.clear();

        StringBuilder sql = new StringBuilder("""
            SELECT 
                p.id,
                p.payment_date, 
                c.business_name, 
                c.client_name, 
                p.type,
                COALESCE(
                    (SELECT GROUP_CONCAT(i.invoice_no, ', ') FROM payment_allocations a JOIN invoice_master i ON a.invoice_id = i.id WHERE a.payment_id = p.id),
                    CASE WHEN p.type = 'Refund' THEN 'Advance Refund' ELSE 'Advance' END
                ) as invoice_ref,
                p.method, 
                p.amount,
                CASE p.method
                    WHEN 'Cheque' THEN 
                        'Cheque No: ' || COALESCE((SELECT field_value FROM payment_details WHERE payment_id = p.id AND field_key = 'cheque_number'), 'N/A') || 
                        ' | Bank: ' || COALESCE((SELECT field_value FROM payment_details WHERE payment_id = p.id AND field_key = 'bank_name'), 'N/A') ||
                        ' | Date: ' || COALESCE((SELECT field_value FROM payment_details WHERE payment_id = p.id AND field_key = 'cheque_date'), 'N/A') ||
                        COALESCE(' | Ref: ' || (SELECT field_value FROM payment_details WHERE payment_id = p.id AND field_key = 'reference'), '') ||
                        COALESCE(' | Notes: ' || (SELECT field_value FROM payment_details WHERE payment_id = p.id AND field_key = 'notes'), '')
                    WHEN 'UPI' THEN 
                        'UPI ID: ' || COALESCE((SELECT field_value FROM payment_details WHERE payment_id = p.id AND field_key = 'upi_id'), 'N/A') || 
                        ' | UTR: ' || COALESCE((SELECT field_value FROM payment_details WHERE payment_id = p.id AND field_key = 'utr'), 'N/A') ||
                        COALESCE(' | Ref: ' || (SELECT field_value FROM payment_details WHERE payment_id = p.id AND field_key = 'reference'), '') ||
                        COALESCE(' | Notes: ' || (SELECT field_value FROM payment_details WHERE payment_id = p.id AND field_key = 'notes'), '')
                    ELSE 
                        COALESCE('Ref: ' || (SELECT field_value FROM payment_details WHERE payment_id = p.id AND field_key = 'reference'), '') ||
                        CASE WHEN (SELECT field_value FROM payment_details WHERE payment_id = p.id AND field_key = 'reference') IS NOT NULL AND (SELECT field_value FROM payment_details WHERE payment_id = p.id AND field_key = 'notes') IS NOT NULL THEN ' | ' ELSE '' END ||
                        COALESCE('Notes: ' || (SELECT field_value FROM payment_details WHERE payment_id = p.id AND field_key = 'notes'), '')
                END as reference
            FROM payments p
            LEFT JOIN clients c ON p.client_id = c.id
            WHERE 1=1
        """);

        Client selectedClient = clientCombo.getValue();
        InvoiceMaster selectedInvoice = invoiceCombo.getValue();
        LocalDate from = fromDate.getValue();
        LocalDate to = toDate.getValue();

        if (selectedClient != null) {
            sql.append(" AND p.client_id = ").append(selectedClient.getId());
        }
        if (selectedInvoice != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM payment_allocations tx WHERE tx.payment_id = p.id AND tx.invoice_id = ").append(selectedInvoice.getId()).append(")");
        }
        if (from != null) {
            sql.append(" AND p.payment_date >= '").append(from.toString()).append("'");
        }
        if (to != null) {
            sql.append(" AND p.payment_date <= '").append(to.toString()).append("'");
        }

        sql.append(" ORDER BY p.payment_date DESC");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString());
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int pid = rs.getInt("id");
                String date = rs.getString("payment_date");
                String bName = rs.getString("business_name");
                String cName = rs.getString("client_name");
                String clientDisplay = (bName != null && !bName.isBlank()) ? bName : cName;
                
                String typeStr = rs.getString("type");
                String invRef = rs.getString("invoice_ref");
                String method = rs.getString("method");
                double amount = rs.getDouble("amount");
                String ref = rs.getString("reference");
                
                masterPaymentList.add(new PaymentRow(
                    pid,
                    date,
                    clientDisplay,
                    typeStr,
                    invRef,
                    method,
                    amount,
                    ref
                ));
            }

            // Setup filtering on the master list and bind to table
            filteredList = new FilteredList<>(masterPaymentList, b -> true);
            SortedList<PaymentRow> sortedList = new SortedList<>(filteredList);
            sortedList.comparatorProperty().bind(paymentsTable.comparatorProperty());
            paymentsTable.setItems(sortedList);

            refreshTotal();

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to load payment history: " + e.getMessage());
        }
    }

    private void setupSearchFilter() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (filteredList != null) {
                filteredList.setPredicate(payment -> {
                    if (newValue == null || newValue.isEmpty()) {
                        return true;
                    }

                    String lowerCaseFilter = newValue.toLowerCase();

                    // Search by Amount, Method, or Reference (as requested)
                    if (payment.getAmountRaw().toString().contains(lowerCaseFilter)) {
                        return true;
                    } else if (payment.getMethod().toLowerCase().contains(lowerCaseFilter)) {
                        return true;
                    } else if (payment.getReference().toLowerCase().contains(lowerCaseFilter)) {
                        return true;
                    }
                    return false; // Does not match.
                });
                refreshTotal();
            }
        });
    }

    private void refreshTotal() {
        if (filteredList == null) return;
        
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentRow row : filteredList) {
            total = total.add(row.getAmountRaw());
        }
        totalAmountLabel.setText(currencyFormat.format(total));
    }

    @FXML
    private void onFilter() {
        loadPaymentData();
        // searchField listener will automatically re-apply global search after DB load
        String currentSearch = searchField.getText();
        if (currentSearch != null && !currentSearch.isEmpty()) {
            searchField.setText(currentSearch + " "); // Hack to trigger listener
            searchField.setText(currentSearch);
        }
    }

    @FXML
    private void onReset() {
        clientCombo.getSelectionModel().clearSelection();
        invoiceCombo.getSelectionModel().clearSelection();
        fromDate.setValue(null);
        toDate.setValue(null);
        searchField.clear();
        loadPaymentData();
    }


    /**
     * Inner class representing a row in the table.
     */
    public class PaymentRow {
        private final int id;
        private final SimpleStringProperty date;
        private final SimpleStringProperty client;
        private final SimpleStringProperty type;
        private final SimpleStringProperty invoiceRef;
        private final SimpleStringProperty method;
        private final SimpleStringProperty amount;
        private final SimpleStringProperty reference;
        private final BigDecimal amountRaw;
 
        public PaymentRow(int id, String date, String client, String typeStr, String invRef, String method, double amount, String reference) {
            this.id = id;
            String formattedDate = "";
            if (date != null && !date.isBlank()) {
                try {
                    String d = date.contains(" ") ? date.split(" ")[0] : date;
                    formattedDate = java.time.LocalDate.parse(d).format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                } catch(Exception e) {
                    formattedDate = date;
                }
            }
            this.date = new SimpleStringProperty(formattedDate);
            this.client = new SimpleStringProperty(client != null ? client : "");
            this.type = new SimpleStringProperty(typeStr != null ? typeStr.toUpperCase() : "PAYMENT");
            this.invoiceRef = new SimpleStringProperty(invRef != null ? invRef : "");
            this.method = new SimpleStringProperty(method != null ? method : "");
            this.amountRaw = BigDecimal.valueOf(amount);
            this.amount = new SimpleStringProperty(currencyFormat.format(amount));
            this.reference = new SimpleStringProperty(reference != null ? reference : "");
        }
 
        public int getId() { return id; }
        public String getDate() { return date.get(); }
        public String getClient() { return client.get(); }
        public String getType() { return type.get(); }
        public String getInvoiceRef() { return invoiceRef.get(); }
        public String getMethod() { return method.get(); }
        public String getAmount() { return amount.get(); }
        public String getReference() { return reference.get(); }
        public BigDecimal getAmountRaw() { return amountRaw; }
 
        public SimpleStringProperty dateProperty() { return date; }
        public SimpleStringProperty clientProperty() { return client; }
        public SimpleStringProperty typeProperty() { return type; }
        public SimpleStringProperty invoiceRefProperty() { return invoiceRef; }
        public SimpleStringProperty methodProperty() { return method; }
        public SimpleStringProperty amountProperty() { return amount; }
        public SimpleStringProperty referenceProperty() { return reference; }
    }
}
