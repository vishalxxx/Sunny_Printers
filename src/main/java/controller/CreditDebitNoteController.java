package controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
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
    private HBox breadcrumbContainer;

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
        utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null,
                () -> MainController.getInstance().handleBack(null));
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
                loadInvoicesForClient(newVal.getClientUuid());
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
            if (inv.getClientId() != null && inv.getClientId().equals(c.getClientUuid())) {
                clientComboBox.getSelectionModel().select(c);
                break;
            }
        }

        // 2. Select Invoice (must wait for async load or trigger manually)
        javafx.application.Platform.runLater(() -> {
            for (InvoiceMaster existingInv : invoiceComboBox.getItems()) {
                if (existingInv.getUuid().equals(inv.getUuid())) {
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
                        Toast.show((Stage) dp.getScene().getWindow(), "Note date cannot be before Invoice date (" + selInv.getInvoiceNo() + ") ❌");
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
        utils.ComboBoxSorter.sortClients(clients);
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

    private void loadInvoicesForClient(String clientUuid) {
        try (Connection con = DBConnection.getConnection()) {
            List<InvoiceMaster> invoices = invoiceRepo.findByClientId(con, clientUuid);
            utils.ComboBoxSorter.sortInvoices(invoices);
            ObservableList<InvoiceMaster> invList = FXCollections.observableArrayList(invoices);
            invoiceComboBox.setItems(invList);

            javafx.util.StringConverter<InvoiceMaster> converter = new javafx.util.StringConverter<InvoiceMaster>() {
                @Override
                public String toString(InvoiceMaster inv) {
                    return inv != null ? inv.getInvoiceNo() + " - ₹" + inv.getAmount() : "Select an invoice...";
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
                        setText(item.getInvoiceNo() + " - ₹" + item.getAmount());
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
                        setText(item.getInvoiceNo() + " - ₹" + item.getAmount());
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
            LocalDate noteDateEffective = noteDate != null ? noteDate : LocalDate.now();
            utils.AtomicDB.runVoid(con -> {
                service.SettingsService settingsService = new service.SettingsService();
                model.MasterDocumentSeries series = noteType.startsWith("Credit")
                        ? model.MasterDocumentSeries.CREDIT_NOTE
                        : model.MasterDocumentSeries.DEBIT_NOTE;
                String noteNo = settingsService.allocateNextMasterNumber(con, series, noteDateEffective);

                // 2. Save to invoice_adjustments
                String noteUuid = utils.ClientIdentifiers.newUuidString();
                String sqlInsert = "INSERT INTO invoice_adjustments (uuid, invoice_uuid, type, note_no, amount, reason, date) VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (java.sql.PreparedStatement ps = con.prepareStatement(sqlInsert)) {
                    ps.setString(1, noteUuid);
                    ps.setString(2, selectedInvoice.getUuid());
                    ps.setString(3, noteType);
                    ps.setString(4, noteNo);
                    ps.setDouble(5, amount);
                    ps.setString(6, reasonField.getText());
                    ps.setString(7, datePicker.getValue() == null ? LocalDate.now().toString() : datePicker.getValue().toString());
                    ps.executeUpdate();
                }

                // 3. Update invoice_master (Update due_amount AND payment_status)
                double adjustment = noteType.startsWith("Credit") ? -amount : amount;
                
                InvoiceMaster inv = invoiceRepo.findByUuid(con, selectedInvoice.getUuid());
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
                    
                    invoiceRepo.updatePayment(con, selectedInvoice.getUuid(), newPaid, newDue, newStatus, LocalDate.now());
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

}
