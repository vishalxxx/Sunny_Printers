package controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.Client;
import model.InvoiceMaster;
import repository.ClientRepository;
import repository.InvoiceMasterRepository;
import utils.DBConnection;
import utils.Toast;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

public class CreditDebitNoteController {
    
    public static InvoiceMaster pendingPrefillInvoice;

    @FXML
    private void handleBack(ActionEvent event) {
        // Logic to go back, usually by asking MainController to switch view
        // For now, if it's a standalone stage, close it. If it's a view, we'd use MainController.
        // Assuming it's part of the MainView navigation:
        if (MainController.getInstance() != null) {
            MainController.getInstance().loadView("/fxml/view_invoices.fxml");
        }
    }

    @FXML
    private ComboBox<String> noteTypeComboBox;

    @FXML
    private ComboBox<Client> clientComboBox;

    @FXML
    private ComboBox<InvoiceMaster> invoiceComboBox;

    @FXML
    private DatePicker datePicker;

    @FXML
    private TextField amountField;

    @FXML
    private TextArea reasonField;

    private ClientRepository clientRepository = new ClientRepository();
    private InvoiceMasterRepository invoiceRepo = new InvoiceMasterRepository();

    @FXML
    public void initialize() {
        // Fix for expanding width issue
        noteTypeComboBox.setPrefWidth(350);
        noteTypeComboBox.setMaxWidth(400);
        clientComboBox.setPrefWidth(350);
        clientComboBox.setMaxWidth(400);
        invoiceComboBox.setPrefWidth(350);
        invoiceComboBox.setMaxWidth(400);
        datePicker.setPrefWidth(350);
        datePicker.setMaxWidth(400);

        noteTypeComboBox.setItems(FXCollections.observableArrayList("Credit Note", "Debit Note"));
        noteTypeComboBox.getSelectionModel().selectFirst();
        
        setupAutoPopupDatePicker(datePicker);
        datePicker.setValue(LocalDate.now());
        
        loadClients();

        clientComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadInvoicesForClient(newVal.getId());
            } else {
                invoiceComboBox.getItems().clear();
            }
        });

        // 📅 Update DatePicker constraints when invoice changes
        invoiceComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateDatePickerConstraints(newVal.getInvoiceDate());
            }
        });

        // 📝 Handle pre-filling if coming from View Invoices screen
        if (pendingPrefillInvoice != null) {
            prefillForm(pendingPrefillInvoice);
            pendingPrefillInvoice = null; // Clear after use
        }
    }

    private void prefillForm(InvoiceMaster inv) {
        // 1. Select Client
        for (Client c : clientComboBox.getItems()) {
            if (c.getId() == inv.getClientId()) {
                clientComboBox.getSelectionModel().select(c);
                break;
            }
        }

        // 2. Select Invoice (must wait for async load or trigger manually)
        javafx.application.Platform.runLater(() -> {
            for (InvoiceMaster existingInv : invoiceComboBox.getItems()) {
                if (existingInv.getId() == inv.getId()) {
                    invoiceComboBox.getSelectionModel().select(existingInv);
                    break;
                }
            }
        });
    }

    private void setupAutoPopupDatePicker(DatePicker dp) {

        dp.setEditable(false);

        // Disable future dates
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

        // Revert value and warn if somehow selected
        dp.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // Check Future Date
                if (newVal.isAfter(LocalDate.now())) {
                    dp.setValue(oldVal);
                    if (dp.getScene() != null && dp.getScene().getWindow() != null) {
                        Toast.show((Stage) dp.getScene().getWindow(), "Future dates are not allowed ❌");
                    }
                }
                // Check Invoice Date
                InvoiceMaster selInv = invoiceComboBox.getValue();
                if (selInv != null && selInv.getInvoiceDate() != null && newVal.isBefore(selInv.getInvoiceDate())) {
                    dp.setValue(selInv.getInvoiceDate());
                    if (dp.getScene() != null && dp.getScene().getWindow() != null) {
                        Toast.show((Stage) dp.getScene().getWindow(), "Note date cannot be before Invoice date (#" + selInv.getInvoiceNo() + ") ❌");
                    }
                }
            }
        });

        // Auto open popup on click
        dp.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            if (!dp.isShowing())
                dp.show();
        });

        // Right-align the popup below the icon
        dp.showingProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                javafx.application.Platform.runLater(() -> {
                    try {
                        // Find the popup window for this date picker
                        for (javafx.stage.Window w : javafx.stage.Window.getWindows()) {
                            if (w instanceof javafx.stage.PopupWindow) {
                                javafx.stage.PopupWindow popup = (javafx.stage.PopupWindow) w;
                                // Only adjust if it's the right kind of popup and we haven't adjusted it yet
                                // We check if it's roughly at the DP's X coordinate
                                double dpX = dp.localToScreen(0, 0).getX();
                                if (Math.abs(popup.getX() - dpX) < 5) {
                                    double dpWidth = dp.getWidth();
                                    double popupWidth = popup.getWidth();
                                    if (popupWidth > 0 && dpWidth > popupWidth) {
                                        popup.setX(dpX + dpWidth - popupWidth);
                                    }
                                }
                            }
                        }
                    } catch (Exception ex) {
                        // Ignore layout edge cases
                    }
                });
            }
        });

        dp.focusedProperty().addListener((obs, oldV, newV) -> {
            if (newV && !dp.isShowing())
                dp.show();
        });
    }

    private void updateDatePickerConstraints(LocalDate minDate) {
        if (minDate == null) return;

        // Force a refresh of the day cells
        datePicker.setDayCellFactory(picker -> new javafx.scene.control.DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) return;

                // Disable if after today OR before invoice date
                boolean isFuture = date.isAfter(LocalDate.now());
                boolean isBeforeInvoice = date.isBefore(minDate);

                if (isFuture || isBeforeInvoice) {
                    setDisable(true);
                    setStyle("-fx-opacity: 0.35;");
                }
            }
        });

        // If current date is invalid, reset to minDate
        if (datePicker.getValue() != null && datePicker.getValue().isBefore(minDate)) {
            datePicker.setValue(minDate);
        }
    }

    private void loadClients() {
        List<Client> clients = clientRepository.findAllSortedById();
        ObservableList<Client> clientList = FXCollections.observableArrayList(clients);
        clientComboBox.setItems(clientList);

        clientComboBox.setConverter(new javafx.util.StringConverter<Client>() {
            @Override
            public String toString(Client client) {
                return client != null ? client.getBusinessName() + " (" + client.getClientName() + ")" : "Select a client...";
            }

            @Override
            public Client fromString(String string) {
                return null;
            }
        });

        clientComboBox.setButtonCell(new javafx.scene.control.ListCell<Client>() {
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
    }

    private void loadInvoicesForClient(int clientId) {
        try (Connection con = DBConnection.getConnection()) {
            List<InvoiceMaster> invoices = invoiceRepo.findByClientId(con, clientId);
            ObservableList<InvoiceMaster> invList = FXCollections.observableArrayList(invoices);
            invoiceComboBox.setItems(invList);

            javafx.util.StringConverter<InvoiceMaster> converter = new javafx.util.StringConverter<InvoiceMaster>() {
                @Override
                public String toString(InvoiceMaster inv) {
                    return inv != null ? "#" + inv.getInvoiceNo() + " - ₹" + inv.getAmount() : "Select an invoice...";
                }

                @Override
                public InvoiceMaster fromString(String string) {
                    return null;
                }
            };
            
            invoiceComboBox.setConverter(converter);
            
            // Set cell factory to prevent pushing layout bounds and add nice formatting
            invoiceComboBox.setCellFactory(cb -> new javafx.scene.control.ListCell<InvoiceMaster>() {
                @Override
                protected void updateItem(InvoiceMaster item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText("#" + item.getInvoiceNo() + " - ₹" + item.getAmount());
                    }
                }
            });

            // Button cell ensures selection text is properly constrained and truncated if too long
            invoiceComboBox.setButtonCell(new javafx.scene.control.ListCell<InvoiceMaster>() {
                @Override
                protected void updateItem(InvoiceMaster item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText("Select an invoice...");
                    } else {
                        setText("#" + item.getInvoiceNo() + " - ₹" + item.getAmount());
                    }
                }
            });

            if (!invList.isEmpty()) {
                invoiceComboBox.getSelectionModel().selectFirst();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleSaveNote(ActionEvent event) {
        String noteType = noteTypeComboBox.getValue();
        Client selectedClient = clientComboBox.getValue();
        InvoiceMaster selectedInvoice = invoiceComboBox.getValue();
        String amountStr = amountField.getText().trim();
        String reasonStr = reasonField.getText() != null ? reasonField.getText().trim() : "";
        
        if (selectedClient == null || amountStr.isEmpty() || noteType == null || selectedInvoice == null) {
            Toast.show((Stage) amountField.getScene().getWindow(), "⚠ Required fields missing!");
            return;
        }

        if (reasonStr.isEmpty()) {
            Toast.show((Stage) amountField.getScene().getWindow(), "⚠ Reason is mandatory!");
            return;
        }

        // Final Date Validation
        LocalDate noteDate = datePicker.getValue();
        if (noteDate != null && selectedInvoice.getInvoiceDate() != null && noteDate.isBefore(selectedInvoice.getInvoiceDate())) {
            Toast.show((Stage) amountField.getScene().getWindow(), "❌ Note date cannot be before Invoice date!");
            return;
        }

        double rawAmount;
        try {
            rawAmount = Double.parseDouble(amountStr);
            if (rawAmount < 0) {
                // If user enters negative, we treat it as absolute. 
                // Adjustments are already signed based on CN/DN type.
                rawAmount = Math.abs(rawAmount);
            }
            if (rawAmount == 0) {
                Toast.show((Stage) amountField.getScene().getWindow(), "⚠ Amount cannot be zero!");
                return;
            }
        } catch (NumberFormatException e) {
            Toast.show((Stage) amountField.getScene().getWindow(), "Invalid amount format");
            return;
        }

        final double amount = rawAmount;

        try {
            utils.AtomicDB.runVoid(con -> {
                // 1. Generate Note Number (CN-xxx or DN-xxx)
                String prefix = noteType.startsWith("Credit") ? "CN-" : "DN-";
                String noteNo = generateNoteNumber(con, prefix);

                // 2. Save to invoice_adjustments
                String sqlInsert = "INSERT INTO invoice_adjustments (invoice_id, type, note_no, amount, reason, date) VALUES (?, ?, ?, ?, ?, ?)";
                try (java.sql.PreparedStatement ps = con.prepareStatement(sqlInsert)) {
                    ps.setInt(1, selectedInvoice.getId());
                    ps.setString(2, noteType);
                    ps.setString(3, noteNo);
                    ps.setDouble(4, amount);
                    ps.setString(5, reasonField.getText());
                    ps.setString(6, datePicker.getValue() == null ? LocalDate.now().toString() : datePicker.getValue().toString());
                    ps.executeUpdate();
                }

                // 3. Update invoice_master (Update due_amount AND payment_status)
                double adjustment = noteType.startsWith("Credit") ? -amount : amount;
                
                InvoiceMaster inv = invoiceRepo.findById(con, selectedInvoice.getId());
                if (inv != null) {
                    double newPaid = inv.getPaidAmount();
                    double newDue = inv.getDueAmount() + adjustment;
                    
                    String newStatus;
                    if (newDue <= 0.0001) {
                        newStatus = "PAID";
                        newDue = 0;
                    } else if (newPaid > 0) {
                        newStatus = "PARTIAL PAID";
                    } else {
                        newStatus = "UNPAID";
                    }
                    
                    invoiceRepo.updatePayment(con, selectedInvoice.getId(), newPaid, newDue, newStatus, LocalDate.now());
                }
                
                javafx.application.Platform.runLater(() -> {
                    Toast.show((Stage) amountField.getScene().getWindow(), noteType + " (" + noteNo + ") saved successfully! ✅");
                    
                    // Trigger refresh in View Invoices table
                    if (ViewInvoicesController.getInstance() != null) {
                        ViewInvoicesController.getInstance().refresh();
                    }

                    // Clear fields after saving
                    amountField.clear();
                    reasonField.clear();
                });
            });
        } catch (Exception e) {
            e.printStackTrace();
            // Extract the most descriptive error message
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            String errorMsg = root.getMessage();
            
            javafx.application.Platform.runLater(() -> {
                Toast.show((Stage) amountField.getScene().getWindow(), "❌ Failed to save: " + (errorMsg != null ? errorMsg : "Unknown error"));
            });
        }
    }

    /**
     * Next CN-#### / DN-#### using the highest numeric suffix for this prefix.
     * The old logic used {@code ORDER BY id DESC}, which could pick a row whose suffix was
     * not the max (e.g. id=100 → CN-0003 while CN-0004 exists) and violate UNIQUE on {@code note_no}.
     */
    private String generateNoteNumber(Connection con, String prefix) throws java.sql.SQLException {
        int start = prefix.length() + 1; // 1-based substr: digits start after "CN-" / "DN-"
        String sql = """
                SELECT COALESCE(MAX(CAST(substr(note_no, ?) AS INTEGER)), 0)
                FROM invoice_adjustments
                WHERE note_no LIKE ?
                AND length(note_no) > ?
                """;
        try (java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, start);
            ps.setString(2, prefix + "%");
            ps.setInt(3, prefix.length());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                int max = 0;
                if (rs.next()) {
                    max = rs.getInt(1);
                }
                return prefix + String.format("%04d", max + 1);
            }
        }
    }
}
