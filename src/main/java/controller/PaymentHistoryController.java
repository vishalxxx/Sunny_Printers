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
import model.Client;
import repository.ClientRepository;
import utils.DBConnection;

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
    @FXML private DatePicker fromDate;
    @FXML private DatePicker toDate;
    @FXML private TextField searchField;
    @FXML private Label totalAmountLabel;

    @FXML private TableView<PaymentRow> paymentsTable;
    @FXML private TableColumn<PaymentRow, String> colDate;
    @FXML private TableColumn<PaymentRow, String> colClient;
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
        setupSearchFilter();

        // Fix date picker popup
        setupAutoPopupDatePicker(fromDate);
        setupAutoPopupDatePicker(toDate);

        // Listeners for triggers
        clientCombo.valueProperty().addListener((obs, oldV, newV) -> loadPaymentData());
        fromDate.valueProperty().addListener((obs, oldV, newV) -> loadPaymentData());
        toDate.valueProperty().addListener((obs, oldV, newV) -> loadPaymentData());
    }

    private void setupTableColumns() {
        colDate.setCellValueFactory(cell -> cell.getValue().dateProperty());
        colClient.setCellValueFactory(cell -> cell.getValue().clientProperty());
        colMethod.setCellValueFactory(cell -> cell.getValue().methodProperty());
        colAmount.setCellValueFactory(cell -> cell.getValue().amountProperty());
        colReference.setCellValueFactory(cell -> cell.getValue().referenceProperty());
    }

    private void setupAutoPopupDatePicker(DatePicker dp) {
        if (dp == null)
            return;

        dp.setEditable(false);

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

    private void loadPaymentData() {
        masterPaymentList.clear();

        StringBuilder sql = new StringBuilder("""
            SELECT 
                p.payment_date, 
                c.business_name, 
                c.client_name, 
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
        LocalDate from = fromDate.getValue();
        LocalDate to = toDate.getValue();

        if (selectedClient != null) {
            sql.append(" AND p.client_id = ").append(selectedClient.getId());
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
                String date = rs.getString("payment_date");
                String bName = rs.getString("business_name");
                String cName = rs.getString("client_name");
                String clientDisplay = (bName != null && !bName.isBlank()) ? bName : cName;

                String method = rs.getString("method");
                double amount = rs.getDouble("amount");
                String ref = rs.getString("reference");

                masterPaymentList.add(new PaymentRow(
                    date,
                    clientDisplay,
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
        fromDate.setValue(null);
        toDate.setValue(null);
        searchField.clear();
        loadPaymentData();
    }


    /**
     * Inner class representing a row in the table.
     */
    public class PaymentRow {
        private final SimpleStringProperty date;
        private final SimpleStringProperty client;
        private final SimpleStringProperty method;
        private final SimpleStringProperty amount;
        private final SimpleStringProperty reference;
        private final BigDecimal amountRaw;

        public PaymentRow(String date, String client, String method, double amount, String reference) {
            this.date = new SimpleStringProperty(date != null ? date : "");
            this.client = new SimpleStringProperty(client != null ? client : "");
            this.method = new SimpleStringProperty(method != null ? method : "");
            this.amountRaw = BigDecimal.valueOf(amount);
            this.amount = new SimpleStringProperty(currencyFormat.format(amount));
            this.reference = new SimpleStringProperty(reference != null ? reference : "");
        }

        public SimpleStringProperty dateProperty() { return date; }
        public SimpleStringProperty clientProperty() { return client; }
        public SimpleStringProperty methodProperty() { return method; }
        public SimpleStringProperty amountProperty() { return amount; }
        public SimpleStringProperty referenceProperty() { return reference; }

        public String getMethod() { return method.get(); }
        public String getReference() { return reference.get(); }
        public BigDecimal getAmountRaw() { return amountRaw; }
    }
}
