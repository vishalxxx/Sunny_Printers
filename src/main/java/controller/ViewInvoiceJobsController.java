package controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;

import model.Client;
import model.InvoiceMaster;
import model.Job;

import service.ClientService;
import service.InvoiceMasterService;
import service.JobService;

import utils.Toast;

import java.time.LocalDate;
import java.util.List;

public class ViewInvoiceJobsController {

    private final ClientService clientService = new ClientService();
    private final InvoiceMasterService invoiceMasterService = new InvoiceMasterService();
    private final JobService jobService = new JobService();

    @FXML private ComboBox<Client> clientComboBox;
    @FXML private ComboBox<InvoiceMaster> invoiceComboBox;
    @FXML private Label resultLabel;
    @FXML private TableView<Job> jobsTable;
    
    @FXML private javafx.scene.layout.HBox searchModeBox;
    @FXML private javafx.scene.layout.VBox editModeBox;
    @FXML private Label lblPageHeading;
    @FXML private TextField txtClientName;
    @FXML private TextField txtInvoiceNo;
    @FXML private DatePicker dpInvoiceDate;
    @FXML private TextField txtProcessStatus;

    @FXML private TableColumn<Job, Integer> idCol;
    @FXML private TableColumn<Job, String> jobNoCol;
    @FXML private TableColumn<Job, String> titleCol;
    @FXML private TableColumn<Job, LocalDate> dateCol;
    @FXML private TableColumn<Job, Double> jobTotalCol;
    @FXML private TableColumn<Job, String> statusCol;
    @FXML private TableColumn<Job, String> remarksCol;

    @FXML private javafx.scene.layout.HBox jobActionsBox;
    @FXML private Button btnJobView, btnJobEdit, btnJobCancel, btnJobUnlink;
    @FXML private Button btnSave, btnDiscard;

    private final ObservableList<Job> tableData = FXCollections.observableArrayList();

    public static InvoiceMaster pendingPrefillInvoice;
    public static boolean viewOnlyMode = false;
    private boolean isViewOnly = false;
    private InvoiceMaster currentEditInvoice;
    private final java.util.Set<Integer> jobsToCancel = new java.util.HashSet<>();
    private final java.util.Map<Integer, String> jobsToUpdateStatus = new java.util.HashMap<>();
    private final java.util.Set<Integer> jobsToUnlink = new java.util.HashSet<>();
    private final java.util.Set<Integer> jobsToAdd = new java.util.HashSet<>();
    @FXML private Button btnAddJob;

    @FXML
    private void initialize() {
        setupTable();
        setupClientComboBox();
        setupInvoiceComboBox();
        setupAutoPopupDatePicker(dpInvoiceDate);

        loadClients();

        clientComboBox.valueProperty().addListener((obs, oldV, newV) -> onClientSelected(newV));
        invoiceComboBox.valueProperty().addListener((obs, oldV, newV) -> onInvoiceSelected(newV));

        try {
            var cssUrl = getClass().getResource("/css/invoice_genration.css");
            if (cssUrl != null) {
                editModeBox.getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception e) {}

        setupJobActionBarStyles();

        // ✅ Job selection logic
        jobsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            updateJobActionBar(newSel);
        });

        // ✅ Double click to edit job
        jobsTable.setRowFactory(tv -> {
            TableRow<Job> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    Job item = row.getItem();
                    String invoiceStatus = currentEditInvoice != null && currentEditInvoice.getStatus() != null ? currentEditInvoice.getStatus().toUpperCase() : "";
                    
                    if (isViewOnly || "FINAL".equals(invoiceStatus) || "SENT".equals(invoiceStatus) || "PAID".equals(invoiceStatus)) {
                        showJobDetails(item);
                    } else {
                        openEditJobScreen(item);
                    }
                }
            });
            return row;
        });

        if (pendingPrefillInvoice != null) {
            final InvoiceMaster prefill = pendingPrefillInvoice;
            pendingPrefillInvoice = null;
            currentEditInvoice = prefill;

            searchModeBox.setVisible(false);
            searchModeBox.setManaged(false);
            editModeBox.setVisible(true);
            editModeBox.setManaged(true);
            jobActionsBox.setVisible(true);
            jobActionsBox.setManaged(true);
            
            this.isViewOnly = viewOnlyMode; 
            viewOnlyMode = false; // Reset for next use
            
            lblPageHeading.setText(this.isViewOnly ? "Invoice Details & Jobs (View Mode)" : "Invoice Details & Jobs");

            onInvoiceSelected(prefill);
        } else {
            isViewOnly = false;
            lblPageHeading.setText("Invoice Search");
            searchModeBox.setVisible(true);
            searchModeBox.setManaged(true);
            editModeBox.setVisible(false);
            editModeBox.setManaged(false);
            jobActionsBox.setVisible(false);
            jobActionsBox.setManaged(false);
        }
    }

    private void setupTable() {
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        jobNoCol.setCellValueFactory(new PropertyValueFactory<>("jobNo"));
        titleCol.setCellValueFactory(new PropertyValueFactory<>("jobTitle"));
        dateCol.setCellValueFactory(new PropertyValueFactory<>("jobDate"));
        dateCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")));
            }
        });
        
        jobTotalCol.setCellValueFactory(new PropertyValueFactory<>("jobTotal"));
        jobTotalCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("₹%.2f", item));
                }
            }
        });

        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        remarksCol.setCellValueFactory(new PropertyValueFactory<>("remarks"));

        jobsTable.setItems(tableData);
        jobsTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        jobsTable.setFixedCellSize(42);

        // ✅ Prevent "empty rows": size table to content
        // TableView renders filler rows when it has extra vertical space.
        // Binding pref/min/max height to item count removes those blanks.
        final double headerHeight = 32; // approx. header height
        var tableHeight = Bindings.size(jobsTable.getItems())
                .multiply(jobsTable.getFixedCellSize())
                .add(headerHeight + 1);
        jobsTable.prefHeightProperty().bind(tableHeight);
        jobsTable.minHeightProperty().bind(tableHeight);
        jobsTable.maxHeightProperty().bind(tableHeight);
    }

    private void setupJobActionBarStyles() {
        String baseStyle = "-fx-font-size: 11.5px; -fx-padding: 5 10; -fx-cursor: hand; -fx-font-weight: bold; -fx-background-radius: 4px;";
        
        btnJobView.setStyle(baseStyle + " -fx-background-color: #00BCD4; -fx-text-fill: white;");
        btnJobEdit.setStyle(baseStyle + " -fx-background-color: #FF9800; -fx-text-fill: white;");
        btnJobCancel.setStyle(baseStyle + " -fx-background-color: #F44336; -fx-text-fill: white;");

        btnJobView.setGraphic(createIcon("M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z"));
        btnJobEdit.setGraphic(createIcon("M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"));
        btnJobCancel.setGraphic(createIcon("M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"));
        btnJobUnlink.setStyle(baseStyle + " -fx-background-color: #9C27B0; -fx-text-fill: white;");
        btnJobUnlink.setGraphic(createIcon("M19 13H5v-2h14v2z"));
    }

    private void updateJobActionBar(Job job) {
        if (job == null) {
            btnJobView.setDisable(true);
            btnJobEdit.setDisable(true);
            btnJobCancel.setDisable(true);
            btnJobUnlink.setDisable(true);
            return;
        }

        btnJobView.setDisable(false);
        btnJobUnlink.setDisable(false);

        String status = job.getStatus() != null ? job.getStatus().toLowerCase() : "";
        boolean isCancelled = status.startsWith("cancel");
        // boolean isCompleted = status.equals("completed");
        // boolean isInProgress = status.equals("in progress");
        boolean isInvoiced = job.getInvoiceId() != null && job.getInvoiceId() > 0;

        boolean isInvoiceDrafted = status.equals("invoice drafted") || status.equals("invoice_drafted");

        if (isInvoiced || status.equals("invoiced")) {
            // btnJobStart removed
            
            // Allow cancelling if invoice is Draft
            if (currentEditInvoice != null) {
                String invStatus = currentEditInvoice.getStatus() != null ? currentEditInvoice.getStatus().toUpperCase() : "";
                btnJobCancel.setDisable(!"DRAFT".equals(invStatus));
            } else {
                btnJobCancel.setDisable(true);
            }
            
            // Allow editing only if invoice is Draft/Final
            if (currentEditInvoice != null) {
                String invStatus = currentEditInvoice.getStatus() != null ? currentEditInvoice.getStatus().toLowerCase() : "";
                boolean canEditInvoiced = invStatus.equals("draft") || invStatus.equals("final");
                btnJobEdit.setDisable(!canEditInvoiced);
            } else {
                btnJobEdit.setDisable(true);
            }
        } else if (isInvoiceDrafted) {
            // btnJobStart removed
            
            // Allow cancelling if invoice is Draft
            if (currentEditInvoice != null) {
                String invS = currentEditInvoice.getStatus() != null ? currentEditInvoice.getStatus().toUpperCase() : "";
                btnJobCancel.setDisable(!"DRAFT".equals(invS));
            } else {
                btnJobCancel.setDisable(true);
            }
            
            // Allow editing if the parent invoice is Draft/Final
            if (currentEditInvoice != null) {
                String invS = currentEditInvoice.getStatus() != null ? currentEditInvoice.getStatus().toUpperCase() : "";
                boolean canEdit = "DRAFT".equals(invS) || "FINAL".equals(invS);
                btnJobEdit.setDisable(!canEdit);
            } else {
                btnJobEdit.setDisable(true);
            }
        } else {
            // boolean disableProgressBtns = isCancelled || isCompleted;
            // btnJobStart removed
            btnJobEdit.setDisable(isCancelled);
            btnJobCancel.setDisable(isCancelled);
        }

        // Final check on action bar based on Invoice Status and View Mode
        if (currentEditInvoice != null) {
            String invStatus = currentEditInvoice.getStatus() != null ? currentEditInvoice.getStatus().toUpperCase() : "";
            boolean isDraft = "DRAFT".equals(invStatus);
            boolean isLocked = "FINAL".equals(invStatus) || "SENT".equals(invStatus) || "PAID".equals(invStatus) || isViewOnly;

            if (isDraft) {
                // For draft invoices, we allow Edit and Unlink
                btnJobEdit.setDisable(false);
                btnJobUnlink.setDisable(false);
                btnJobCancel.setDisable(false);
                btnAddJob.setDisable(false);
            } else if (isLocked) {
                btnJobEdit.setDisable(true);
                btnJobCancel.setDisable(true);
                btnJobUnlink.setDisable(true);
                btnAddJob.setDisable(true);
            } else {
                btnJobEdit.setDisable(false);
                btnJobUnlink.setDisable(false);
                btnAddJob.setDisable(false);
            }
        } else if (isViewOnly) {
            btnJobEdit.setDisable(true);
            btnJobCancel.setDisable(true);
            btnJobUnlink.setDisable(true);
            btnAddJob.setDisable(true);
        }
    }

    @FXML
    private void handleJobViewAction() {
        Job selected = jobsTable.getSelectionModel().getSelectedItem();
        if (selected != null) showJobDetails(selected);
    }

    @FXML
    private void handleJobEditAction() {
        Job selected = jobsTable.getSelectionModel().getSelectedItem();
        if (selected != null) openEditJobScreen(selected);
    }

    @FXML
    private void handleJobStartAction() {
        // Method kept for FXML compatibility if needed, but logic removed
    }

    @FXML
    private void handleJobCancelAction() {
        Job selected = jobsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            jobsToCancel.add(selected.getId());
            selected.setStatus("Cancelled");
            tableData.remove(selected);
            resultLabel.setText("Showing " + tableData.size() + " jobs");
            toast("Job marked for cancellation and removal. Click Save to persist.");
        }
    }

    @FXML
    private void handleJobUnlinkAction() {
        Job selected = jobsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            String msg = "Unlink job " + selected.getJobNo() + " from this invoice?";
			if (tableData.size() <= 1) {
				String invStatus = currentEditInvoice.getStatus() != null ? currentEditInvoice.getStatus().toUpperCase() : "";
				String invNo = currentEditInvoice.getInvoiceNo() != null ? currentEditInvoice.getInvoiceNo().toUpperCase() : "";
				boolean isTemp = "DRAFT".equals(invStatus) && invNo.startsWith("TEMP-");

				if (isTemp) {
					msg = "Unlinking the last job will result in the temporary invoice being deleted and jobs moved back to Completed status. Proceed?";
				} else if (!"DRAFT".equals(invStatus) && !"FINAL".equals(invStatus)) {
					msg = "Unlinking the last job will result in the Invoice being CANCELLED. Proceed?";
				} else {
					msg = "Unlink the last job? The invoice will remain as an empty " + invStatus + " invoice. Proceed?";
				}
			}
            
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.NO);
            alert.setHeaderText("Caution: Unlink Job");
            alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
            alert.getDialogPane().setStyle("-fx-font-family: 'Segoe UI';");
            alert.showAndWait().ifPresent(type -> {
                if (type == ButtonType.YES) {
                    jobsToUnlink.add(selected.getId());
                    tableData.remove(selected);
                    resultLabel.setText("Showing " + tableData.size() + " jobs");
                    toast("Job unlinked locally. Click Save to persist.");
                }
            });
        }
    }

    @FXML
    private void handleAddJobsToInvoice() {
        if (currentEditInvoice == null) return;
        
        String invStatus = currentEditInvoice.getStatus() != null ? currentEditInvoice.getStatus().toLowerCase() : "";
        if (!invStatus.equals("draft") && !invStatus.equals("final")) {
            toast("Adding jobs is only allowed for Draft or Final invoices.");
            return;
        }
        
        List<Job> availableJobs = jobService.getCompletedJobsByClient(currentEditInvoice.getClientId());
        if (availableJobs.isEmpty()) {
            toast("No completed jobs found for this client.");
            return;
        }

        // Show choice dialog
        Dialog<List<Job>> dialog = new Dialog<>();
        dialog.setTitle("Add Jobs to Invoice");
        dialog.setHeaderText("Select completed jobs to add to invoice " + currentEditInvoice.getInvoiceNo());
        
        ListView<Job> listView = new ListView<>(FXCollections.observableArrayList(availableJobs));
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Job item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(item.getJobNo() + " - " + item.getJobTitle() + " (₹" + item.getJobTotal() + ")");
            }
        });
        listView.setPrefHeight(250);

        dialog.getDialogPane().setContent(listView);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) return listView.getSelectionModel().getSelectedItems();
            return null;
        });

        dialog.showAndWait().ifPresent(selected -> {
            for (Job j : selected) {
                if (!tableData.contains(j)) {
                    jobsToAdd.add(j.getId());
                    j.setStatus("Invoiced"); // Local update for UI
                    tableData.add(j);
                }
            }
            resultLabel.setText("Showing " + tableData.size() + " jobs");
            toast("Jobs added locally. Click Save to persist.");
        });
    }

    private javafx.scene.Node createIcon(String pathStr) {
        javafx.scene.shape.SVGPath svg = new javafx.scene.shape.SVGPath();
        svg.setContent(pathStr);
        svg.setFill(javafx.scene.paint.Color.WHITE);
        svg.setScaleX(0.7);
        svg.setScaleY(0.7);
        javafx.scene.layout.StackPane pane = new javafx.scene.layout.StackPane(svg);
        pane.setPrefSize(16, 16);
        pane.setMinSize(16, 16);
        pane.setMaxSize(16, 16);
        return pane;
    }

    private void showJobDetails(Job job) {
        service.JobItemService jis = new service.JobItemService();
        java.util.List<model.JobItem> items = jis.getJobItems(job.getId());

        String clientName = "Unknown Client";
        if (job.getClientId() != null) {
            for (Client c : clientComboBox.getItems()) {
                if (c.getId() == job.getClientId()) {
                    clientName = c.getBusinessName();
                    break;
                }
            }
        }

        String formattedDate = "-";
        if (job.getJobDate() != null) {
            formattedDate = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy").format(job.getJobDate());
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Job View - " + job.getJobNo());
        dialog.setHeaderText("Client: " + clientName + "\nJob Title: " + job.getJobTitle() + "\nDate: " + formattedDate);
        
        DialogPane dialogPane = dialog.getDialogPane();
        try {
            dialogPane.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
            dialogPane.getStylesheets().add(getClass().getResource("/css/invoice_genration.css").toExternalForm());
        } catch(Exception e) {}
        dialogPane.setStyle("-fx-background-color: #1e1e1e; -fx-border-color: #444444; -fx-border-width: 1px;");

        TableView<model.JobItem> table = new TableView<>();

        TableColumn<model.JobItem, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(260);

        TableColumn<model.JobItem, Double> amtCol = new TableColumn<>("Amount");
        amtCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amtCol.setPrefWidth(100);

        table.getColumns().add(descCol);
        table.getColumns().add(amtCol);
        table.setItems(FXCollections.observableArrayList(items));
        table.setPrefHeight(200);

        double total = items.stream().mapToDouble(model.JobItem::getAmount).sum();
        Label lblTotal = new Label("Grand Total: ₹" + String.format("%.2f", total));
        lblTotal.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-alignment: center-right; -fx-pref-width: 360px; -fx-text-fill: white;");

        javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(10, table, lblTotal);
        dialogPane.setContent(vbox);
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);

        javafx.scene.Node header = dialogPane.lookup(".header-panel");
        if (header != null) header.setStyle("-fx-background-color: #2b2b2b;");
        javafx.scene.Node headerText = dialogPane.lookup(".header-panel .label");
        if (headerText != null) headerText.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");
        
        javafx.scene.Node graphic = dialogPane.lookup(".header-panel .graphic-container");
        if (graphic != null) {
            graphic.setManaged(false);
            graphic.setVisible(false);
        }

        Button closeBtn = (Button) dialogPane.lookupButton(ButtonType.CLOSE);
        if (closeBtn != null) {
            closeBtn.setStyle("-fx-background-color: #333333; -fx-text-fill: white; -fx-cursor: hand;");
        }

        dialog.showAndWait();
    }

    private void openEditJobScreen(Job job) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/edit_job.fxml"));
            javafx.scene.Parent view = loader.load();
            EditJobController controller = loader.getController();
            controller.openForEdit(job);
            
            utils.NavigationManager.getInstance().push("/fxml/edit_job.fxml", "Edit Job", "Editing Job...", "billingSidebar");
            utils.NavigationManager.getInstance().updateCurrentState(view, controller);
            
            MainController.getInstance().setCenterView(view);
        } catch (Exception ex) {
            ex.printStackTrace();
            toast("Failed to open Edit Job");
        }
    }

    private void setupClientComboBox() {
        clientComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Client item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select Client..." : item.getBusinessName() + " (" + item.getClientName() + ")");
            }
        });
        clientComboBox.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(Client item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getBusinessName() + " (" + item.getClientName() + ")");
            }
        });
    }

    private void setupInvoiceComboBox() {
        invoiceComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(InvoiceMaster item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select Invoice..." : item.getInvoiceNo() + " - ₹" + item.getAmount());
            }
        });
        invoiceComboBox.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(InvoiceMaster item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getInvoiceNo() + " - ₹" + item.getAmount() + " [" + item.getInvoiceDate() + "]");
            }
        });
    }

    private void loadClients() {
        clientComboBox.getItems().setAll(clientService.getAllClients());
    }

    private void onClientSelected(Client client) {
        invoiceComboBox.getSelectionModel().clearSelection();
        tableData.clear();
        resultLabel.setText("Showing 0 jobs");

        if (client == null) {
            invoiceComboBox.setDisable(true);
            return;
        }

        invoiceComboBox.setDisable(false);
        List<InvoiceMaster> invoices = invoiceMasterService.getInvoicesByClientId(client.getId());
        invoiceComboBox.getItems().setAll(invoices);
    }

    private void onInvoiceSelected(InvoiceMaster invoice) {
        jobsToCancel.clear();
        jobsToUpdateStatus.clear();
        jobsToUnlink.clear();
        jobsToAdd.clear();
        tableData.clear();
        if (invoice == null) {
            editModeBox.setVisible(false);
            editModeBox.setManaged(false);
            jobActionsBox.setVisible(false);
            jobActionsBox.setManaged(false);
            resultLabel.setText("Showing 0 jobs");
            return;
        }

        // Show the info card and actions
        editModeBox.setVisible(true);
        editModeBox.setManaged(true);
        jobActionsBox.setVisible(true);
        jobActionsBox.setManaged(true);
        
        // Populate client name for search mode too
        if (clientComboBox.getValue() != null) {
            Client c = clientComboBox.getValue();
            txtClientName.setText(c.getBusinessName() + " (" + c.getClientName() + ")");
        }

        populateInvoiceDetails(invoice);


        Thread thread = new Thread(() -> {
            try {
                List<Job> jobs = jobService.getJobsByInvoice(invoice);
                Platform.runLater(() -> {
                    tableData.setAll(jobs);
                    resultLabel.setText("Showing " + jobs.size() + " jobs");
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();
    }

    private void populateInvoiceDetails(InvoiceMaster inv) {
        if (inv == null) return;
        
        // Populate Client Name automatically
        Client matchingClient = clientComboBox.getItems().stream()
                .filter(c -> c.getId() == inv.getClientId())
                .findFirst().orElse(null);
        
        if (matchingClient != null) {
            txtClientName.setText(matchingClient.getBusinessName() + " (" + matchingClient.getClientName() + ")");
        } else if (clientComboBox.getValue() != null) {
             Client c = clientComboBox.getValue();
             txtClientName.setText(c.getBusinessName() + " (" + c.getClientName() + ")");
        }

        txtInvoiceNo.setText(inv.getInvoiceNo());
        dpInvoiceDate.setValue(inv.getInvoiceDate());
        txtProcessStatus.setText(inv.getStatus());

        String stat = inv.getStatus() != null ? inv.getStatus().toUpperCase() : "";
        boolean isDraft = "DRAFT".equals(stat);
        
        // Locking logic
        boolean isLocked = !isDraft; // Consistent with user request to lock anything not Draft
        dpInvoiceDate.setDisable(isLocked);
        
        // Button Visibility
        boolean canSave = isDraft && !isViewOnly; // 🔥 Respect both status and view mode
        btnSave.setVisible(canSave);
        btnSave.setManaged(canSave);
        
        // For Discard/Close button
        btnDiscard.setVisible(true);
        btnDiscard.setManaged(true);
        if (isDraft) {
            btnDiscard.setText(pendingPrefillInvoice == null ? "Discard" : "Discard Changes");
        } else {
            btnDiscard.setText("Close");
        }
        
    }

    private void toast(String message) {
        Stage stage = (Stage) ((Node) clientComboBox).getScene().getWindow();
        Platform.runLater(() -> Toast.show(stage, message));
    }

    private void setupAutoPopupDatePicker(DatePicker dp) {
        dp.setEditable(false);

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

        dp.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.isAfter(LocalDate.now())) {
                dp.setValue(oldVal);
            }
        });

        dp.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            if (!dp.isShowing()) dp.show();
        });

        dp.focusedProperty().addListener((obs, oldV, newV) -> {
            if (newV && !dp.isShowing()) dp.show();
        });
    }

    @FXML
    private void handleSaveChanges() {
        if (currentEditInvoice == null) {
            MainController.getInstance().handleBack(null);
            return;
        }

        // 🛑 Requirement: Revised invoices cannot be empty
        String invNo = currentEditInvoice.getInvoiceNo();
        if (invNo != null && invNo.contains("-R") && tableData.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Empty revised invoice not possible please cancel the invoice..");
            alert.showAndWait();
            return;
        }

        LocalDate newDate = dpInvoiceDate.getValue();
        boolean dateChanged = newDate != null && !newDate.equals(currentEditInvoice.getInvoiceDate());
        boolean jobsChanged = !jobsToCancel.isEmpty() || !jobsToUnlink.isEmpty();
        boolean hasAddedJobs = !jobsToAdd.isEmpty();
        boolean hasStatusChanges = !jobsToUpdateStatus.isEmpty();

        if (dateChanged || jobsChanged || hasAddedJobs || hasStatusChanges) {
            try {
                utils.AtomicDB.runVoid(con -> {
                    // 1. Update Date
                    if (dateChanged) {
                        try (java.sql.PreparedStatement ps = con.prepareStatement("UPDATE invoice_master SET invoice_date = ? WHERE id = ?")) {
                            ps.setString(1, newDate.toString());
                            ps.setInt(2, currentEditInvoice.getId());
                            ps.executeUpdate();
                        }
                    }

                    // 2. Status Updates
                    if (hasStatusChanges) {
                        try (java.sql.PreparedStatement psS = con.prepareStatement("UPDATE jobs SET status = ? WHERE id = ?")) {
                            for (java.util.Map.Entry<Integer, String> entry : jobsToUpdateStatus.entrySet()) {
                                psS.setString(1, entry.getValue());
                                psS.setInt(2, entry.getKey());
                                psS.addBatch();
                            }
                            psS.executeBatch();
                        }
                    }

                    // 3. Cancel Jobs
                    if (!jobsToCancel.isEmpty()) {
                        try (java.sql.PreparedStatement psC = con.prepareStatement("UPDATE jobs SET status = 'Cancelled', invoice_id = NULL WHERE id = ?")) {
                            for (int jobId : jobsToCancel) {
                                psC.setInt(1, jobId);
                                psC.addBatch();
                            }
                            psC.executeBatch();
                        }
                    }

                    // 4. Unlink Jobs
                    if (!jobsToUnlink.isEmpty()) {
                        try (java.sql.PreparedStatement psU = con.prepareStatement("UPDATE jobs SET invoice_id = NULL, status = 'Completed' WHERE id = ?")) {
                            for (int jobId : jobsToUnlink) {
                                psU.setInt(1, jobId);
                                psU.addBatch();
                            }
                            psU.executeBatch();
                        }
                    }

                    // 5. Add Jobs
                    if (hasAddedJobs) {
                        boolean isRevision = invNo != null && invNo.contains("-R");
                        String statusToSet = isRevision ? "Invoiced" : "Invoice Drafted";
                        try (java.sql.PreparedStatement psA = con.prepareStatement("UPDATE jobs SET invoice_id = ?, status = ? WHERE id = ?")) {
                            for (int jobId : jobsToAdd) {
                                psA.setInt(1, currentEditInvoice.getId());
                                psA.setString(2, statusToSet);
                                psA.setInt(3, jobId);
                                psA.addBatch();
                            }
                            psA.executeBatch();
                        }
                    }

                    // 6. Recalculate Totals
                    try (java.sql.PreparedStatement psT = con.prepareStatement(
                        "UPDATE invoice_master SET " +
                        "amount = (SELECT COALESCE(SUM(ji.amount), 0) FROM job_items ji JOIN jobs j ON ji.job_id = j.id WHERE j.invoice_id = ?), " +
                        "due_amount = (SELECT COALESCE(SUM(ji.amount), 0) FROM job_items ji JOIN jobs j ON ji.job_id = j.id WHERE j.invoice_id = ?) " +
                        " + (SELECT COALESCE(SUM(amount), 0) FROM invoice_adjustments WHERE invoice_id = ? AND type = 'Debit Note') " +
                        " - (SELECT COALESCE(SUM(amount), 0) FROM invoice_adjustments WHERE invoice_id = ? AND type = 'Credit Note') " +
                        " - paid_amount " +
                        "WHERE id = ?")) {
                        psT.setInt(1, currentEditInvoice.getId());
                        psT.setInt(2, currentEditInvoice.getId());
                        psT.setInt(3, currentEditInvoice.getId());
                        psT.setInt(4, currentEditInvoice.getId());
                        psT.setInt(5, currentEditInvoice.getId());
                        psT.executeUpdate();
                    }

                    // 7. Cleanup
                    invoiceMasterService.deleteEmptyInvoices(con);
                });

                if (ViewInvoicesController.getInstance() != null) {
                    ViewInvoicesController.getInstance().refresh();
                }
                toast("Changes saved successfully!");
                MainController.getInstance().handleBack(null);
            } catch (Exception e) {
                e.printStackTrace();
                toast("Error saving changes: " + e.getMessage());
            }
        } else {
            MainController.getInstance().handleBack(null);
        }
    }

    @FXML
    private void handleDiscardChanges() {
        MainController.getInstance().handleBack(null);
    }
}
