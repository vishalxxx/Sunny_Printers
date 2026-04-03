package controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import model.Client;
import model.InvoiceMaster;
import service.ClientService;
import service.InvoiceMasterService;
import utils.Toast;

import java.time.LocalDate;
import java.util.List;

public class ViewInvoicesController {

    @FXML
    private ComboBox<Client> clientComboBox;
    @FXML
    private ComboBox<String> statusComboBox;
    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private Button searchBtn;
    @FXML
    private Button clearBtn;

    @FXML
    private TableView<InvoiceMaster> invoiceTable;
    @FXML
    private TableColumn<InvoiceMaster, String> colInvoiceNo;
    @FXML
    private TableColumn<InvoiceMaster, String> colClientName;
    @FXML
    private TableColumn<InvoiceMaster, String> colInvoiceDate;
    @FXML
    private TableColumn<InvoiceMaster, Double> colAmount;
    @FXML
    private TableColumn<InvoiceMaster, Double> colPaid;
    @FXML
    private TableColumn<InvoiceMaster, Double> colDue;
    @FXML
    private TableColumn<InvoiceMaster, String> colStatus;
    @FXML
    private TableColumn<InvoiceMaster, String> colPaymentStatus;

    @FXML
    private TableColumn<InvoiceMaster, String> colType;
    @FXML
    private TableColumn<InvoiceMaster, String> colStatusIndicator;

    private final ClientService clientService = new ClientService();
    private final InvoiceMasterService invoiceMasterService = new InvoiceMasterService();
    private final ObservableList<InvoiceMaster> invoiceList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        loadClients();
        loadStatuses();
        
        setupAutoPopupDatePicker(startDatePicker);
        setupAutoPopupDatePicker(endDatePicker);
        
        // Initial load of recent invoices
        handleSearch(null);
    }

    private void setupAutoPopupDatePicker(DatePicker dp) {

        dp.setEditable(false);

        // ✅ Disable future dates
        dp.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);

                if (empty || date == null) return;

                if (date.isAfter(LocalDate.now())) {
                    setDisable(true);
                    setStyle("-fx-opacity: 0.35;");
                }
            }
        });

        // ✅ extra safety
        dp.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.isAfter(LocalDate.now())) {
                dp.setValue(oldVal);
            }
        });

        // ✅ Auto open on click
        dp.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            if (!dp.isShowing()) dp.show();
        });

        dp.focusedProperty().addListener((obs, oldV, newV) -> {
            if (newV && !dp.isShowing()) dp.show();
        });
    }

    // ==========================================
    // MAP FOR DIRECT TRACKER UPDATES
    // ==========================================
    private final java.util.Map<InvoiceMaster, javafx.scene.shape.Circle> trackerMap = new java.util.HashMap<>();

    private void updateCircleColor(javafx.scene.shape.Circle circle, String status) {
        if (circle == null || status == null) return;
        switch(status.toUpperCase()) {
            case "PAID":
                circle.setFill(javafx.scene.paint.Color.LIMEGREEN);
                circle.setStyle("-fx-effect: dropshadow(gaussian, limegreen, 8, 0.4, 0, 0);");
                break;
            case "PARTIAL PAID":
            case "PARTIAL_PAID":
                circle.setFill(javafx.scene.paint.Color.ORANGE);
                circle.setStyle("-fx-effect: dropshadow(gaussian, orange, 8, 0.4, 0, 0);");
                break;
            case "SENT":
                circle.setFill(javafx.scene.paint.Color.DODGERBLUE);
                circle.setStyle("-fx-effect: dropshadow(gaussian, dodgerblue, 8, 0.4, 0, 0);");
                break;
            case "OVERDUE":
            case "VOID":
                circle.setFill(javafx.scene.paint.Color.RED);
                circle.setStyle("-fx-effect: dropshadow(gaussian, red, 8, 0.4, 0, 0);");
                break;
            case "UNPAID":
            default:
                circle.setFill(javafx.scene.paint.Color.GRAY);
                circle.setStyle("-fx-effect: dropshadow(gaussian, gray, 8, 0.4, 0, 0);");
                break;
        }
        Tooltip t = new Tooltip(status);
        Tooltip.install(circle, t);
    }

    private void setupTableColumns() {
        invoiceTable.setEditable(true); // make table editable to allow combo box interactions
        colInvoiceNo.setCellValueFactory(new PropertyValueFactory<>("invoiceNo"));
        colClientName.setCellValueFactory(new PropertyValueFactory<>("clientName"));
        colInvoiceDate.setCellValueFactory(cellData -> {
            LocalDate date = cellData.getValue().getInvoiceDate();
            return new SimpleStringProperty(date != null ? date.toString() : "");
        });
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colPaid.setCellValueFactory(new PropertyValueFactory<>("paidAmount"));
        colDue.setCellValueFactory(new PropertyValueFactory<>("dueAmount"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));

        // ==========================================
        // Invoice Status ComboBox Column
        // ==========================================
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            private final ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(
                "DRAFT", "SENT", "CANCELLED"
            ));
            {
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.getStyleClass().add("combo-box-base");
                this.setStyle("-fx-padding: 2 5; -fx-alignment: CENTER;");
                
                combo.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == javafx.scene.input.KeyCode.UP || event.getCode() == javafx.scene.input.KeyCode.DOWN) {
                        if (!combo.isShowing()) {
                            combo.show();
                            event.consume();
                        }
                    }
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    combo.setOnAction(null);
                    combo.setValue(item == null ? "DRAFT" : item.toUpperCase());
                    
                    combo.setOnAction(e -> {
                        if (combo.isShowing()) return; // Ignore mid-navigation changes
                        
                        InvoiceMaster inv = getTableRow().getItem();
                        if (inv != null) {
                            String newlySelected = combo.getValue();
                            if (newlySelected != null && !newlySelected.equalsIgnoreCase(inv.getStatus())) {
                                String upperStatus = newlySelected.toUpperCase();
                                inv.setStatus(upperStatus);
                                invoiceMasterService.updateInvoiceStatus(inv.getId(), upperStatus);
                                
                                // Process payment status rules based on new invoice status
                                if (upperStatus.equals("DRAFT") || upperStatus.equals("CANCELLED")) {
                                    inv.setPaymentStatus(null);
                                    invoiceMasterService.updateInvoicePaymentStatus(inv.getId(), null);
                                    
                                    javafx.scene.shape.Circle c = trackerMap.get(inv);
                                    if (c != null) updateCircleColor(c, null);
                                } else if (upperStatus.equals("SENT")) {
                                    if (inv.getPaymentStatus() == null || inv.getPaymentStatus().isEmpty()) {
                                        inv.setPaymentStatus("UNPAID");
                                        invoiceMasterService.updateInvoicePaymentStatus(inv.getId(), "UNPAID");
                                        
                                        javafx.scene.shape.Circle c = trackerMap.get(inv);
                                        if (c != null) updateCircleColor(c, "UNPAID");
                                    }
                                }
                                
                                // Force table to refresh the row to show the updated Payment Status text
                                javafx.application.Platform.runLater(() -> {
                                    getTableView().refresh();
                                });
                            }
                        }
                    });
                    
                    // Trigger action on close to catch final selection
                    combo.showingProperty().addListener((obs, wasShowing, isShowing) -> {
                        if (!isShowing) combo.fireEvent(new ActionEvent());
                    });
                    
                    setGraphic(combo);
                }
            }
        });

        // ==========================================
        // Payment Status Column (Read-Only)
        // ==========================================
        colPaymentStatus.setCellValueFactory(new PropertyValueFactory<>("paymentStatus"));


        // ==========================================
        // Status Indicator Column
        // ==========================================
        colStatusIndicator.setCellValueFactory(new PropertyValueFactory<>("paymentStatus"));
        colStatusIndicator.setCellFactory(col -> new TableCell<>() {
            private final javafx.scene.shape.Circle circle = new javafx.scene.shape.Circle(6);
            
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    InvoiceMaster inv = getTableRow().getItem();
                    trackerMap.put(inv, circle);
                    
                    updateCircleColor(circle, status);
                    
                    setGraphic(circle);
                    setAlignment(javafx.geometry.Pos.CENTER);
                }
            }
        });

        invoiceTable.setItems(invoiceList);
    }

    private void loadClients() {
        List<Client> clients = clientService.getAllClients();
        clientComboBox.getItems().setAll(clients);

        clientComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Client item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("Select a client...");
                } else {
                    setText(item.getBusinessName() + " (" + item.getClientName() + ")");
                }
            }
        });

        clientComboBox.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(Client item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getBusinessName() + " (" + item.getClientName() + ")");
                }
            }
        });
    }

    private void loadStatuses() {
        statusComboBox.getItems().setAll("All", "UNPAID", "PARTIAL PAID", "PAID", "OVERDUE");
        statusComboBox.getSelectionModel().selectFirst();
    }

    @FXML
    private void handleSearch(ActionEvent event) {
        Integer clientId = null;
        if (clientComboBox.getValue() != null) {
            clientId = clientComboBox.getValue().getId();
        }

        String status = statusComboBox.getValue();
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();

        try {
            List<InvoiceMaster> results = invoiceMasterService.getFilteredInvoices(clientId, status, start, end);
            invoiceList.setAll(results);
            
            if (results.isEmpty() && event != null) {
                Toast.show((javafx.stage.Stage) searchBtn.getScene().getWindow(), "No invoices found for the selected criteria.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (event != null) {
                Toast.show((javafx.stage.Stage) searchBtn.getScene().getWindow(), "Error fetching invoices.");
            }
        }
    }

    @FXML
    private void handleClear(ActionEvent event) {
        clientComboBox.setValue(null);
        statusComboBox.getSelectionModel().selectFirst();
        startDatePicker.setValue(null);
        endDatePicker.setValue(null);
        handleSearch(null); // Reload all active invoices without filters
    }
}
