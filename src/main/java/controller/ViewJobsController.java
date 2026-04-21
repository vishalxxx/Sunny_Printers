package controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.Hyperlink;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.application.Platform;
import service.InvoiceMasterService;
import model.InvoiceMaster;

import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import model.Client;
import model.Job;
import service.ClientService;
import service.JobService;
import utils.Toast;
import javafx.scene.paint.Color;
import javafx.scene.control.cell.CheckBoxTableCell;

public class ViewJobsController {

    private final ClientService clientService = new ClientService();
    private final JobService jobService = new JobService();
    private final InvoiceMasterService invoiceService = new InvoiceMasterService();

    private final ObservableList<Job> masterJobs = FXCollections.observableArrayList();

    // ✅ clientId -> clientName map (for fast lookup)
    private final Map<Integer, String> clientNameMap = new HashMap<>();

    private final ExecutorService dataLoadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "view-jobs-data-loader");
        t.setDaemon(true);
        return t;
    });

    // ===================== TOP SEARCH =====================
    @FXML private HBox searchContainer;
    @FXML private TextField searchField;

    // ===================== FILTERS =====================
    @FXML private ComboBox<Client> clientComboBox;
    @FXML private ComboBox<String> statusFilterComboBox;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;


    // ===================== NEW BINDINGS =====================
    @FXML private TableView<Job> jobsTable;
    @FXML private VBox bulkActionBarContainer;
    @FXML private HBox bulkActionBar;
    @FXML private Label selectionCountLabel;
    @FXML private ComboBox<String> bulkCommandCombo;

    @FXML private TableColumn<Job, Boolean> selectCol;
    @FXML private TableColumn<Job, Job> jobDetailsCol;
    @FXML private TableColumn<Job, LocalDate> dateCol;
    @FXML private TableColumn<Job, Job> amountCol;
    @FXML private TableColumn<Job, Job> statusProgressCol;
    @FXML private TableColumn<Job, Job> actionsCol;

    @FXML private Button bulkStartBtn;
    @FXML private Button bulkCompleteBtn;
    @FXML private Button bulkInvoiceBtn;
    @FXML private Button bulkCancelBtn;

    @FXML private Label paginationInfoLabel;
    @FXML private TextField goToPageField;
    @FXML private HBox paginationButtonContainer;

    private int currentPage = 1;
    private final int pageSize = 15;
    private final ObservableList<Job> pagedItems = FXCollections.observableArrayList();


    private final ObservableList<Job> selectedJobs = FXCollections.observableArrayList();
    private FilteredList<Job> filteredData;
    private SortedList<Job> sortedData;

    @FXML private VBox mainVBox;

    @FXML
    private void initialize() {
        if (mainVBox != null) {
            mainVBox.setOnMouseClicked(event -> {
                mainVBox.requestFocus();
            });
        }
        setupClientComboBox();
        setupTableColumns();

        statusFilterComboBox.getItems().addAll("All", "Draft", "In Progress", "Completed", "Invoiced", "Cancelled");
        statusFilterComboBox.getSelectionModel().selectFirst();
        statusFilterComboBox.valueProperty().addListener((obs, oldV, newV) -> applyFilters());

        if (bulkCommandCombo != null) {
            bulkCommandCombo.getItems().addAll("Actions", "Export CSV", "Print Labels");
            bulkCommandCombo.getSelectionModel().selectFirst();
        }

        clientComboBox.valueProperty().addListener(this::onClientChanged);
        searchField.setOnAction(e -> onSearchClicked());
        searchField.textProperty().addListener((obs, oldV, newV) -> {
            applyFilters();
        });

        setupAutoPopupDatePicker(fromDatePicker);
        setupAutoPopupDatePicker(toDatePicker);

        fromDatePicker.valueProperty().addListener((obs, oldV, newV) -> applyFilters());
        toDatePicker.valueProperty().addListener((obs, oldV, newV) -> applyFilters());

        // ✅ Background Loading (DB work off FX thread; UI updates on FX thread)
        CompletableFuture
                .supplyAsync(clientService::getAllClients, dataLoadExecutor)
                .thenAccept(clients -> Platform.runLater(() -> setClients(clients)));

        CompletableFuture
                .supplyAsync(jobService::getAllJobs, dataLoadExecutor)
                .thenAccept(jobs -> Platform.runLater(() -> setJobs(jobs)));

        // ✅ Multi-select support
        jobsTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // ✅ Programmatic Focus Manager (Taste Design Fix)
        if (searchField != null && searchContainer != null) {
            searchField.focusedProperty().addListener((obs, oldVal, isFocused) -> {
                if (isFocused) {
                    searchContainer.getStyleClass().add("search-container-active");
                } else {
                    searchContainer.getStyleClass().remove("search-container-active");
                }
            });
        }

        jobsTable.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<Job>) c -> {
            updateBulkActionBar();
        });

        // ✅ Setup dynamic filtering
        filteredData = new FilteredList<>(masterJobs, p -> true);
        sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(jobsTable.comparatorProperty());

        // Bind table to paged items instead of sortedData directly
        jobsTable.setItems(pagedItems);
        
        // Listen to changes in sorted data to refresh pagination
        sortedData.addListener((javafx.collections.ListChangeListener<Job>) c -> {
            currentPage = 1;
            updatePagination();
        });

        // ✅ Double click to view
        jobsTable.setRowFactory(tv -> {
            TableRow<Job> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    showJobDetails(row.getItem());
                }
            });
            return row;
        });
    }
    
    // =========================================================
    // ✅ ACTION BAR LOGIC
    // =========================================================
    private void updateBulkActionBar() {
        List<Job> selected = jobsTable.getItems().stream()
                .filter(Job::isSelected)
                .collect(Collectors.toList());
        if (selected.isEmpty()) {
            bulkActionBarContainer.setVisible(false);
            bulkActionBarContainer.setManaged(false);
        } else {
            bulkActionBarContainer.setVisible(true);
            bulkActionBarContainer.setManaged(true);
            selectionCountLabel.setText(selected.size() + " jobs selected");
            
            // Calculate eligible counts
            long draftCount = selected.stream().filter(j -> "Draft".equalsIgnoreCase(j.getStatus())).count();
            long processingCount = selected.stream().filter(j -> "In Progress".equalsIgnoreCase(j.getStatus())).count();
            long completedCount = selected.stream().filter(j -> "Completed".equalsIgnoreCase(j.getStatus())).count();
            long anyButCancelled = selected.stream().filter(j -> !"Cancelled".equalsIgnoreCase(j.getStatus())).count();
            
            bulkStartBtn.setText("Start Processing ("+draftCount+")");
            bulkStartBtn.setDisable(draftCount == 0);
            
            bulkCompleteBtn.setText("Mark Completed ("+processingCount+")");
            bulkCompleteBtn.setDisable(processingCount == 0);
            
            bulkInvoiceBtn.setText("Generate Invoice ("+completedCount+")");
            bulkInvoiceBtn.setDisable(completedCount == 0);
            
            bulkCancelBtn.setText("Cancel Job ("+anyButCancelled+")");
            bulkCancelBtn.setDisable(anyButCancelled == 0);
        }
    }

    @FXML private void clearFilters() {
        searchField.clear();
        clientComboBox.getSelectionModel().clearSelection();
        statusFilterComboBox.getSelectionModel().selectFirst();
        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);
        applyFilters();
    }

    @FXML
    private void handleCloseBulk() {
        jobsTable.getSelectionModel().clearSelection();
        updateBulkActionBar();
    }

    @FXML
    private void handleViewAction() {
        Job selected = jobsTable.getSelectionModel().getSelectedItem();
        if (selected != null) showJobDetails(selected);
    }

    @FXML
    private void handleEditAction() {
        Job selected = jobsTable.getSelectionModel().getSelectedItem();
        if (selected != null) openEditJobScreen(selected);
    }

    @FXML
    private void handleStartAction() {
        processBulkStatusUpdate("In Progress", "Job started successfully!");
    }

    @FXML
    private void handleCompleteAction() {
        processBulkStatusUpdate("Completed", "Job marked as completed!");
    }

    @FXML
    private void handleInvoiceAction() {
        List<Job> selected = jobsTable.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) return;
        
        Job first = selected.get(0);
        if (first.getClientId() == null) {
            toast("❌ Job has no client associated.");
            return;
        }
        
        // Single invoice flow for now, but in bulk bar it could be multi-job invoice
        // For now, let's just trigger for the first one if total selection is 1, else warning
        if (selected.size() > 1) {
            toast("Bulk Invoicing coming soon! Please select one job.");
            return;
        }
        
        handleInvoicedRequest(first);
    }

    @FXML
    private void handleCancelAction() {
        processBulkStatusUpdate("Cancelled", "Job cancelled.");
    }

    @FXML
    private void clearSelection() {
        for (Job job : masterJobs) {
            job.setSelected(false);
        }
        jobsTable.getSelectionModel().clearSelection();
        toast("Selection cleared.");
    }

    private void processBulkStatusUpdate(String status, String successMsg) {
        List<Job> selected = new java.util.ArrayList<>(jobsTable.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) return;

        for (Job job : selected) {
            jobService.updateJobStatus(job.getId(), status);
            job.setStatus(status);
        }
        jobsTable.refresh();
        toast(successMsg);
        jobsTable.getSelectionModel().clearSelection();
    }
    
    private void handleInvoicedRequest(Job job) {
        if (job.getClientId() == null) {
            toast("❌ Job has no client associated.");
            return;
        }
        MainController.getInstance().loadInvoiceWithJob(job.getClientId(), job.getId());
    }

    // =========================================================
    // ✅ SEARCH
    // =========================================================

 // =========================================================
    // ✅ SEARCH
    // =========================================================
    private void clearClientAndDates() {
        clientComboBox.getSelectionModel().clearSelection();
        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);
        statusFilterComboBox.getSelectionModel().select("All");
    }

    // =========================================================
    // ✅ CLIENT COMBO
    // =========================================================
    private void setupClientComboBox() {
        clientComboBox.setEditable(false);

        clientComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Client item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("Select Client");
                } else {
                    setText(item.getBusinessName());
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

    private void loadClients() {

        List<Client> clients = clientService.getAllClients();
        Platform.runLater(() -> setClients(clients));
    }

    private void setClients(List<Client> clients) {
        if (clients == null) return;
        clientComboBox.getItems().setAll(clients);

        clientNameMap.clear();
        for (Client c : clients) {
            if (c != null) clientNameMap.put(c.getId(), c.getBusinessName());
        }
        // Refresh visible rows that display client names.
        if (jobsTable != null) jobsTable.refresh();
    }

    // =========================================================
    // ✅ TABLE SETUP
    // =========================================================
    private void setupTableColumns() {
        // 1. SELECT COLUMN
        selectCol.setCellValueFactory(cellData -> {
            javafx.beans.property.BooleanProperty prop = cellData.getValue().selectedProperty();
            // Listen for changes to the checkbox
            prop.addListener((obs, old, val) -> updateBulkActionBar());
            return prop;
        });
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        jobsTable.setEditable(true);

        // 2. JOB DETAILS COLUMN (Target Vision Alignment)
        jobDetailsCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<Job>(cellData.getValue()));
        jobDetailsCol.setCellFactory(col -> new TableCell<Job, Job>() {
            private final VBox box = new VBox(2);
            private final Label title = new Label();
            private final Label clientLabel = new Label();
            private final Label idLabel = new Label();
            private final HBox root = new HBox(10);
            private final StackPane iconBox = new StackPane();
            private final Region icon = new Region();
            
            {
                title.getStyleClass().add("job-title-row");
                clientLabel.getStyleClass().add("job-client-row");
                idLabel.getStyleClass().add("job-id-row");
                box.getChildren().addAll(title, clientLabel, idLabel);
                root.setAlignment(Pos.CENTER_LEFT);
                iconBox.setMinWidth(48); iconBox.setMaxWidth(48);
                iconBox.setMinHeight(48); iconBox.setMaxHeight(48);
                iconBox.getStyleClass().add("icon-box");
                icon.getStyleClass().add("inner-icon");
                iconBox.getChildren().add(icon);
                root.getChildren().addAll(iconBox, box);
            }

            @Override
            protected void updateItem(Job job, boolean empty) {
                super.updateItem(job, empty);
                if (empty || job == null) { setGraphic(null); return; }
                title.setText(job.getJobTitle() != null ? job.getJobTitle() : "No Title");
                clientLabel.setText(clientNameMap.getOrDefault(job.getClientId(), "Unknown Client"));
                idLabel.setText(job.getJobNo());
                
                iconBox.getStyleClass().removeAll("icon-box-orange", "icon-box-blue", "icon-box-green", "icon-box-red", "icon-box-purple");
                icon.getStyleClass().removeAll("icon-inner-orange", "icon-inner-blue", "icon-inner-green", "icon-inner-red", "icon-inner-purple");
                
                String statusLower = job.getStatus() != null ? job.getStatus().toLowerCase() : "";
                String type = "orange"; // Default
                String shape = "M20 4H4v2h16V4zm1 10v-2l-1-5H4l-1 5v2h1v6h10v-6h4v6h2v-6h1zM12 18H6v-4h6v4z"; // Store
                
                if (statusLower.contains("draft")) { type = "orange"; shape = "M20 4H4v2h16V4zm1 10v-2l-1-5H4l-1 5v2h1v6h10v-6h4v6h2v-6h1zM12 18H6v-4h6v4z"; }
                else if (statusLower.contains("process")) { type = "blue"; shape = "M21.41 11.58l-9-9C12.05 2.22 11.55 2 11 2H4c-1.1 0-2 .9-2 2v7c0 .55.22 1.05.59 1.42l9 9c.36.36.86.58 1.41.58.55 0 1.05-.22 1.41-.59l7-7c.37-.36.59-.86.59-1.41 0-.55-.23-1.06-.59-1.42zM5.5 7C4.67 7 4 6.33 4 5.5S4.67 4 5.5 4 7 4.67 7 5.5 6.33 7 5.5 7z"; }
                else if (statusLower.contains("complet")) { type = "green"; shape = "M20.5 3l-.16.03L15 5.1 9 3 3.36 4.9c-.21.07-.36.25-.36.48V20.5c0 .28.22.5.5.5l.16-.03L9 18.9l6 2.1 5.64-1.9c.21-.07.36-.25.36-.48V3.5c0-.28-.22-.5-.5-.5zM15 19l-6-2.11V5l6 2.11V19z"; }
                else if (statusLower.contains("invoice") || statusLower.contains("final")) { type = "purple"; shape = "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z"; }
                else if (statusLower.contains("cancel")) { type = "red"; shape = "M12 2C6.47 2 2 6.47 2 12s4.47 10 10 10 10-4.47 10-10S17.53 2 12 2zm5 13.59L15.59 17 12 13.41 8.41 17 7 15.59 10.59 12 7 8.41 8.41 7 12 10.59 15.59 7 17 8.41 13.41 12 17 15.59z"; }
                
                iconBox.getStyleClass().add("icon-box-" + type);
                String colorHex = type.equals("orange") ? "#FA8C16" : type.equals("blue") ? "#1890FF" : type.equals("green") ? "#52C41A" : type.equals("purple") ? "#722ED1" : "#F5222D";
                icon.setStyle("-fx-shape: '" + shape + "'; -fx-background-color: " + colorHex + "; -fx-min-width: 24; -fx-min-height: 24; -fx-max-width: 24; -fx-max-height: 24;");

                setGraphic(root);
            }
        });

        // 3. DATE COLUMN (Target Icons)
        dateCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<LocalDate>(cellData.getValue().getJobDate()));
        dateCol.setCellFactory(col -> new TableCell<Job, LocalDate>() {
            private final HBox root = new HBox(8);
            private final Region icon = new Region();
            private final Label label = new Label();
            {
                root.setAlignment(Pos.CENTER_LEFT);
                icon.setStyle("-fx-shape: 'M19 4h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V10h14v10zm0-12H5V6h14v2z'; -fx-background-color: #A0836D; -fx-min-width: 14; -fx-min-height: 14; -fx-max-width: 14; -fx-max-height: 14;");
                label.setStyle("-fx-text-fill: #1A1311; -fx-font-weight: 600; -fx-font-size: 11px;");
                root.getChildren().addAll(icon, label);
            }
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) { setGraphic(null); return; }
                label.setText(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy").format(date));
                setGraphic(root);
            }
        });

        // 4. AMOUNT COLUMN (Metadata Synthesis with Clickable Links)
        amountCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<Job>(cellData.getValue()));
        amountCol.setCellFactory(col -> new TableCell<Job, Job>() {
            private final VBox box = new VBox(2);
            private final Label amount = new Label();
            private final HBox metaBox = new HBox(4);
            private final Label invPrefix = new Label("Invoice:");
            private final Hyperlink invLink = new Hyperlink();
            private final Label separator = new Label("|");
            private final Label imgPrefix = new Label("Images:");
            private final Hyperlink imgLink = new Hyperlink();
            
            {
                amount.getStyleClass().add("amount-row");
                invPrefix.getStyleClass().add("meta-row");
                invLink.getStyleClass().add("meta-link-link");
                separator.getStyleClass().add("meta-row");
                imgPrefix.getStyleClass().add("meta-row");
                imgLink.getStyleClass().add("meta-link-link");
                
                // Remove hyperlink default padding/underline
                invLink.setPadding(Insets.EMPTY);
                imgLink.setPadding(Insets.EMPTY);
                invLink.setUnderline(false);
                imgLink.setUnderline(false);

                // Prevent truncation
                invPrefix.setMinWidth(Region.USE_PREF_SIZE);
                invLink.setMinWidth(Region.USE_PREF_SIZE);
                separator.setMinWidth(Region.USE_PREF_SIZE);
                imgPrefix.setMinWidth(Region.USE_PREF_SIZE);
                imgLink.setMinWidth(Region.USE_PREF_SIZE);
                
                metaBox.getChildren().addAll(invPrefix, invLink, separator, imgPrefix, imgLink);
                metaBox.setAlignment(Pos.CENTER_LEFT);
                box.getChildren().addAll(amount, metaBox);
                box.setAlignment(Pos.CENTER_LEFT);
            }
            
            @Override
            protected void updateItem(Job job, boolean empty) {
                super.updateItem(job, empty);
                if (empty || job == null) { setGraphic(null); return; }
                
                amount.setText("₹ " + String.format("%.2f", job.getJobTotal() != null ? job.getJobTotal() : 0.0));
                
                String invNo = job.getInvoiceNo();
                if (invNo != null && !invNo.isEmpty() && !invNo.equals("-")) {
                    invLink.setText(invNo);
                    invLink.setDisable(false);
                    invLink.setOnAction(e -> openInvoiceDetails(job.getInvoiceId()));
                    invLink.getStyleClass().add("active-link");
                } else {
                    invLink.setText("-");
                    invLink.setDisable(true);
                    invLink.setOnAction(null);
                    invLink.getStyleClass().remove("active-link");
                }

                String imgPath = job.getImagePath();
                if (imgPath != null && !imgPath.isEmpty()) {
                    imgLink.setText("View");
                    imgLink.setDisable(false);
                    imgLink.setOnAction(e -> showImagePreview(imgPath));
                    imgLink.getStyleClass().add("active-link");
                } else {
                    imgLink.setText("-");
                    imgLink.setDisable(true);
                    imgLink.setOnAction(null);
                    imgLink.getStyleClass().remove("active-link");
                }
                
                setGraphic(box);
            }
        });

        // 5. STATUS & PROGRESS COLUMN (Target Stepper)
        statusProgressCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<Job>(cellData.getValue()));
        statusProgressCol.setCellFactory(col -> new TableCell<Job, Job>() {
            @Override
            protected void updateItem(Job job, boolean empty) {
                super.updateItem(job, empty);
                if (empty || job == null) { setGraphic(null); return; }
                VBox box = new VBox(8);
                box.setAlignment(Pos.CENTER_LEFT);
                
                String status = job.getStatus() != null ? job.getStatus() : "";
                Label statusBadge = new Label(status.toUpperCase());
                statusBadge.getStyleClass().addAll("status-badge", "status-badge-" + status.toLowerCase().replace(" ", "-"));
                
                box.getChildren().addAll(statusBadge, createStepper(status));
                setGraphic(box);
            }
        });

        // 6. ACTIONS COLUMN (Triad Suite)
        actionsCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<Job>(cellData.getValue()));
        actionsCol.setCellFactory(col -> new TableCell<Job, Job>() {
            private final HBox root = new HBox(8);
            { root.setAlignment(Pos.CENTER_LEFT); }

            @Override
            protected void updateItem(Job job, boolean empty) {
                super.updateItem(job, empty);
                if (empty || job == null) { setGraphic(null); return; }
                root.getChildren().clear();
                
                String statusMsg = job.getStatus() != null ? job.getStatus().toLowerCase() : "";
                Button primaryBtn = new Button();
                primaryBtn.setPadding(new Insets(6, 16, 6, 16));
                
                if (statusMsg.contains("draft") || statusMsg.contains("created")) {
                    primaryBtn.setText("Start Processing");
                    primaryBtn.getStyleClass().add("row-action-btn-primary");
                    primaryBtn.setGraphic(createIcon("M8 5v14l11-7z", "white"));
                    primaryBtn.setOnAction(e -> handleStartActionForJob(job));
                } else if (statusMsg.contains("progress")) {
                    primaryBtn.setText("Mark Completed");
                    primaryBtn.setStyle("-fx-background-color: #52C41A; -fx-text-fill: white; -fx-background-radius: 6; -fx-font-weight: 800; -fx-font-size: 11px;");
                    primaryBtn.setGraphic(createIcon("M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z", "white"));
                    primaryBtn.setOnAction(e -> handleCompleteActionForJob(job));
                } else if (statusMsg.contains("completed")) {
                    primaryBtn.setText("Generate Invoice");
                    primaryBtn.setStyle("-fx-background-color: #722ED1; -fx-text-fill: white; -fx-background-radius: 6; -fx-font-weight: 800; -fx-font-size: 11px;");
                    primaryBtn.setGraphic(createIcon("M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z", "white"));
                    primaryBtn.setOnAction(e -> handleInvoicedRequest(job));
                } else {
                    primaryBtn.setText("No Actions");
                    primaryBtn.setDisable(true);
                    primaryBtn.setStyle("-fx-background-color: #F5F5F5; -fx-text-fill: #B5ACA3; -fx-opacity: 1.0; -fx-background-radius: 6; -fx-font-weight: 800; -fx-font-size: 11px;");
                }
                
                Button vBtn = new Button(); vBtn.getStyleClass().addAll("row-action-btn", "row-btn-blue");
                vBtn.setGraphic(createIcon("M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z", "#1890FF"));
                vBtn.setOnAction(e -> showJobDetails(job));
                
                Button eBtn = new Button(); eBtn.getStyleClass().addAll("row-action-btn", "row-btn-orange");
                eBtn.setGraphic(createIcon("M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z", "#FA8C16"));
                eBtn.setOnAction(e -> openEditJobScreen(job));

                Button mBtn = new Button(); mBtn.getStyleClass().addAll("row-action-btn", "row-btn-gray");
                mBtn.setGraphic(createIcon("M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z", "#5B4F47"));
                
                root.getChildren().add(primaryBtn);
                root.getChildren().add(vBtn);
                if (!statusMsg.contains("cancel")) {
                    root.getChildren().add(eBtn);
                }
                root.getChildren().add(mBtn);
                setGraphic(root);
            }
        });
    }

    private void handleStartActionForJob(Job job) {
        jobService.updateJobStatus(job.getId(), "In Progress");
        job.setStatus("In Progress"); jobsTable.refresh(); toast("Job started!");
    }

    private void handleCompleteActionForJob(Job job) {
        jobService.updateJobStatus(job.getId(), "Completed");
        job.setStatus("Completed"); jobsTable.refresh(); toast("Job completed!");
    }

    private void handleCancelActionForJob(Job job) {
        jobService.updateJobStatus(job.getId(), "Cancelled");
        job.setStatus("Cancelled"); jobsTable.refresh(); toast("Job cancelled.");
    }

    private javafx.scene.Node createStepper(String currentStatus) {
        String s = currentStatus != null ? currentStatus.toLowerCase() : "";
        javafx.scene.layout.Pane pane = new javafx.scene.layout.Pane();
        pane.setMinHeight(30);
        pane.setPrefHeight(30);

        String[] stages = {"Draft", "Processing", "Completed", "Invoice"};
        int activeIdx = 0;
        String color = "#E2DDD8"; 
        
        if (s.contains("draft")) { activeIdx = 0; color = "#5B4F47"; }
        else if (s.contains("progress")) { activeIdx = 1; color = "#1890FF"; }
        else if (s.contains("completed")) { activeIdx = 2; color = "#52C41A"; }
        else if (s.contains("invoiced") || s.contains("final")) { activeIdx = 3; color = "#722ED1"; }
        else if (s.contains("cancel") || s.contains("cancelled")) { activeIdx = 0; color = "#F5222D"; }

        int dotSpacing = 72; // Distance between dot centers
        int dotRadius = 6;
        int dotDiameter = 12;

        // Draw Lines first so they are behind dots
        for (int i = 0; i < stages.length - 1; i++) {
            Region rail = new Region();
            rail.getStyleClass().add("stepper-line");
            rail.setPrefHeight(2);
            rail.setMinHeight(2);
            
            // Line spans from center of current dot to center of next dot
            rail.setPrefWidth(dotSpacing);
            rail.setLayoutX(i * dotSpacing + dotRadius);
            rail.setLayoutY(dotRadius - 1); // Center vertically with dot (which is at Y=0, center is 6)
            
            if (i < activeIdx) {
                if (color.contains("1890")) rail.getStyleClass().add("stepper-line-blue");
                else if (color.contains("52C4")) rail.getStyleClass().add("stepper-line-green");
                else if (color.contains("722E")) rail.getStyleClass().add("stepper-line-purple");
                else rail.getStyleClass().add("stepper-line-active");
            }
            pane.getChildren().add(rail);
        }

        // Draw Dots and Labels
        for (int i = 0; i < stages.length; i++) {
            double centerX = i * dotSpacing + dotRadius;
            
            StackPane node = new StackPane();
            node.getStyleClass().add("stepper-dot");
            node.setPrefSize(dotDiameter, dotDiameter);
            node.setLayoutX(centerX - dotRadius);
            node.setLayoutY(0);
            
            if (i == activeIdx) {
                if (color.contains("1890")) node.getStyleClass().add("stepper-dot-blue");
                else if (color.contains("52C4")) node.getStyleClass().add("stepper-dot-green");
                else if (color.contains("F522")) node.getStyleClass().add("stepper-dot-red");
                else if (color.contains("722E")) node.getStyleClass().add("stepper-dot-purple");
                else node.getStyleClass().add("stepper-dot-active");
            } else if (i < activeIdx) {
                if (color.contains("1890")) node.getStyleClass().add("stepper-dot-past-blue");
                else if (color.contains("52C4")) node.getStyleClass().add("stepper-dot-past-green");
                else if (color.contains("722E")) node.getStyleClass().add("stepper-dot-past-purple");
                else node.getStyleClass().add("stepper-dot-active");
            }
            
            Label lbl = new Label(stages[i]);
            lbl.getStyleClass().add("stepper-label");
            if (i == activeIdx) {
                lbl.getStyleClass().add("stepper-label-active");
                if (color.contains("1890")) lbl.setStyle("-fx-text-fill: #1890FF;");
                else if (color.contains("52C4")) lbl.setStyle("-fx-text-fill: #52C41A;");
                else if (color.contains("F522")) lbl.setStyle("-fx-text-fill: #F5222D;");
                else if (color.contains("722E")) lbl.setStyle("-fx-text-fill: #722ED1;");
            }
            
            if (i == 0) {
                // Perfectly left align the first label with the left edge of the dot
                lbl.setLayoutX(centerX - dotRadius);
            } else {
                // Estimate width to center other labels under their dots
                double estWidth = stages[i].length() * 5.5;
                lbl.setLayoutX(centerX - estWidth / 2);
            }
            lbl.setLayoutY(16); // Below the dot
            
            pane.getChildren().addAll(node, lbl);
        }
        
        return pane;
    }

    private javafx.scene.Node createIcon(String pathStr, String color) {
        javafx.scene.shape.SVGPath svg = new javafx.scene.shape.SVGPath();
        svg.setContent(pathStr);
        svg.setFill(javafx.scene.paint.Color.web(color));
        javafx.scene.layout.StackPane pane = new javafx.scene.layout.StackPane(svg);
        pane.setAlignment(Pos.CENTER);
        pane.setMinWidth(16); pane.setMaxWidth(16);
        pane.setMinHeight(16); pane.setMaxHeight(16);
        return pane;
    }

    private void setupAutoPopupDatePicker(DatePicker dp) {
        dp.setEditable(false);
        
        // Restriction: Future dates not allowed
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

        // Trigger popup on focus/click
        dp.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            if (!dp.isShowing()) dp.show();
        });
        dp.focusedProperty().addListener((obs, oldV, isFocused) -> {
            if (isFocused && !dp.isShowing()) dp.show();
        });
    }

    private void showJobDetails(Job job) {
        if (job == null) return;
        
        service.JobItemService jis = new service.JobItemService();
        List<model.JobItem> items = jis.getJobItems(job.getId());
        String clientName = clientNameMap.getOrDefault(job.getClientId(), "Unknown Client");
        String formattedDate = job.getJobDate() != null ? java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy").format(job.getJobDate()) : "-";

        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);
        
        VBox root = new VBox(20);
        root.setStyle("-fx-background-color: #FAF6F0; -fx-background-radius: 16; -fx-padding: 32; " +
                      "-fx-border-width: 0; -fx-background-insets: 0; " +
                      "-fx-effect: dropshadow(three-pass-box, rgba(62, 49, 45, 0.15), 30, 0, 0, 15);");
        root.setMinWidth(550);
        root.setMaxWidth(650);

        // Header Section
        HBox header = new HBox();
        header.setAlignment(Pos.TOP_LEFT);
        
        VBox titleBox = new VBox(4);
        Label idLbl = new Label("#" + job.getJobNo().toUpperCase());
        idLbl.setStyle("-fx-text-fill: #CD7B4E; -fx-font-weight: 800; -fx-font-size: 11px; -fx-letter-spacing: 0.1em;");
        
        Label titleLbl = new Label(job.getJobTitle());
        titleLbl.setStyle("-fx-text-fill: #3E312D; -fx-font-weight: 800; -fx-font-size: 22px; -fx-font-family: 'Inter';");
        
        Label clientLbl = new Label(clientName + " • " + formattedDate);
        clientLbl.setStyle("-fx-text-fill: #8B7E74; -fx-font-weight: 600; -fx-font-size: 13px;");
        
        titleBox.getChildren().addAll(idLbl, titleLbl, clientLbl);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("view-clear-btn");
        closeBtn.setStyle("-fx-min-width: 32; -fx-min-height: 32; -fx-font-size: 14px; -fx-padding: 0;");
        closeBtn.setOnAction(e -> stage.close());
        
        header.getChildren().addAll(titleBox, spacer, closeBtn);

        // Items Table
        TableView<model.JobItem> table = new TableView<>();
        table.getStyleClass().add("jobs-table-premium");
        table.setPrefHeight(250);
        
        TableColumn<model.JobItem, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(350);
        
        TableColumn<model.JobItem, Double> amtCol = new TableColumn<>("Amount");
        amtCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amtCol.setPrefWidth(120);
        amtCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Double amt, boolean empty) {
                super.updateItem(amt, empty);
                if (empty || amt == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText("₹ " + String.format("%.2f", amt));
                    setStyle("-fx-font-weight: 700; -fx-text-fill: #3E312D;");
                }
            }
        });
        
        table.getColumns().addAll(descCol, amtCol);
        table.setItems(FXCollections.observableArrayList(items));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Grand Total Section
        HBox totalBox = new HBox(12);
        totalBox.setAlignment(Pos.CENTER_RIGHT);
        totalBox.setPadding(new Insets(10, 0, 0, 0));
        
        Label totalLbl = new Label("Grand Total:");
        totalLbl.setStyle("-fx-text-fill: #8B7E74; -fx-font-weight: 700; -fx-font-size: 14px;");
        
        Label totalVal = new Label("₹ " + String.format("%.2f", items.stream().mapToDouble(model.JobItem::getAmount).sum()));
        totalVal.setStyle("-fx-text-fill: #CD7B4E; -fx-font-weight: 800; -fx-font-size: 24px; -fx-font-family: 'Manrope';");
        
        totalBox.getChildren().addAll(totalLbl, totalVal);

        root.getChildren().addAll(header, table, totalBox);

        // Full Screen Overlay for quick dismissal
        StackPane overlay = new StackPane(root);
        overlay.setStyle("-fx-background-color: transparent;");
        overlay.setPadding(new Insets(50));
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) stage.close();
        });

        Scene scene = new Scene(overlay);
        scene.setFill(Color.TRANSPARENT);
        try {
            scene.getStylesheets().add(getClass().getResource("/css/view_job.css").toExternalForm());
        } catch(Exception e) {}
        
        stage.setScene(scene);
        stage.show();
    }


    // =========================================================
    // ✅ LOAD DATA
    // =========================================================
    public void loadAllJobs() {
        CompletableFuture
                .supplyAsync(jobService::getAllJobs, dataLoadExecutor)
                .thenAccept(jobs -> Platform.runLater(() -> setJobs(jobs)));
    }

    private void setJobs(List<Job> jobs) {
        masterJobs.setAll(jobs != null ? jobs : List.of());
        applyFilters();
    }


    private void loadJobsBySelectedClient() {

        Client selectedClient = clientComboBox.getValue();

        if (selectedClient == null) {
            loadAllJobs();
            return;
        }

        // ✅ full jobs of that client
        List<Job> jobs = jobService.getFullJobsByClientId(selectedClient.getId());
        masterJobs.setAll(jobs);

        applyFilters();
    }

    // =========================================================
    // ✅ FILTERS
    // =========================================================
    private void applyFilters() {

        String keyword = (searchField.getText() == null)
                ? ""
                : searchField.getText().trim().toLowerCase();

        LocalDate from = fromDatePicker.getValue();
        LocalDate to = toDatePicker.getValue();

        filteredData.setPredicate(job -> {

            boolean matchesKeyword = keyword.isEmpty()
                    || (job.getJobNo() != null && job.getJobNo().toLowerCase().contains(keyword))
                    || (job.getJobTitle() != null && job.getJobTitle().toLowerCase().contains(keyword))
                    || (job.getRemarks() != null && job.getRemarks().toLowerCase().contains(keyword));

            boolean matchesDate = true;

            if (job.getJobDate() != null) {
                if (from != null && job.getJobDate().isBefore(from)) matchesDate = false;
                if (to != null && job.getJobDate().isAfter(to)) matchesDate = false;
            }

            boolean matchesStatus = true;
            String statusFilter = statusFilterComboBox.getValue();
            if (statusFilter != null && !statusFilter.equals("All")) {
                if (job.getStatus() == null || !job.getStatus().equalsIgnoreCase(statusFilter)) {
                    matchesStatus = false;
                }
            }

            boolean matchesClient = true;
            Client selectedClient = clientComboBox.getValue();
            if (selectedClient != null) {
                if (job.getClientId() == null || !job.getClientId().equals(selectedClient.getId())) {
                    matchesClient = false;
                }
            }

            return matchesKeyword && matchesDate && matchesStatus && matchesClient;
        });
        
        if (paginationInfoLabel != null) {
            paginationInfoLabel.setText("Showing 1 to " + filteredData.size() + " of " + masterJobs.size() + " jobs");
        }
        
        updatePagination();
    }

    private void updatePagination() {
        int totalItems = sortedData.size();
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        
        if (currentPage > totalPages) currentPage = Math.max(1, totalPages);
        
        int fromIndex = (currentPage - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalItems);
        
        if (totalItems == 0) {
            pagedItems.clear();
            if (paginationInfoLabel != null) paginationInfoLabel.setText("Showing 0 to 0 of 0 jobs");
        } else {
            pagedItems.setAll(sortedData.subList(fromIndex, toIndex));
            if (paginationInfoLabel != null) {
                paginationInfoLabel.setText("Showing " + (fromIndex + 1) + " to " + toIndex + " of " + totalItems + " jobs");
            }
        }
        
        renderPaginationButtons(totalPages);
    }

    private void renderPaginationButtons(int totalPages) {
        if (paginationButtonContainer == null) return;
        paginationButtonContainer.getChildren().clear();
        
        if (totalPages <= 1) return;

        // Previous Button
        Button prevBtn = new Button("<");
        prevBtn.getStyleClass().add("page-nav-btn");
        prevBtn.setDisable(currentPage == 1);
        prevBtn.setOnAction(e -> { currentPage--; updatePagination(); });
        paginationButtonContainer.getChildren().add(prevBtn);

        // Dynamic Page Numbers (Simplified logic: show first, current, last and neighbors)
        int startPage = Math.max(1, currentPage - 2);
        int endPage = Math.min(totalPages, startPage + 4);
        if (endPage == totalPages) startPage = Math.max(1, endPage - 4);

        if (startPage > 1) {
            addPageButton(1);
            if (startPage > 2) paginationButtonContainer.getChildren().add(new Label("..."));
        }

        for (int i = startPage; i <= endPage; i++) {
            addPageButton(i);
        }

        if (endPage < totalPages) {
            if (endPage < totalPages - 1) paginationButtonContainer.getChildren().add(new Label("..."));
            addPageButton(totalPages);
        }

        // Next Button
        Button nextBtn = new Button(">");
        nextBtn.getStyleClass().add("page-nav-btn");
        nextBtn.setDisable(currentPage == totalPages);
        nextBtn.setOnAction(e -> { currentPage++; updatePagination(); });
        paginationButtonContainer.getChildren().add(nextBtn);
    }

    private void addPageButton(int page) {
        Button btn = new Button(String.valueOf(page));
        btn.getStyleClass().add("page-btn");
        if (page == currentPage) btn.getStyleClass().add("page-btn-active");
        btn.setOnAction(e -> { currentPage = page; updatePagination(); });
        paginationButtonContainer.getChildren().add(btn);
    }

    @FXML
    private void handleGoToPage() {
        if (goToPageField == null || goToPageField.getText().isEmpty()) return;
        try {
            int target = Integer.parseInt(goToPageField.getText().trim());
            int totalItems = sortedData.size();
            int totalPages = (int) Math.ceil((double) totalItems / pageSize);
            
            if (target >= 1 && target <= totalPages) {
                currentPage = target;
                updatePagination();
                goToPageField.clear();
            } else {
                toast("Invalid page number ❌");
            }
        } catch (NumberFormatException e) {
            toast("Please enter a valid number ❌");
        }
    }


    // =========================================================
    // ✅ EVENTS
    // =========================================================
    private void onClientChanged(ObservableValue<? extends Client> obs, Client oldV, Client newV) {
        applyFilters();
    }

    @FXML
    private void onSearchClicked() {
        applyFilters();
    }

    private void autoSelectClient(Integer clientId) {

        if (clientId == null) return;

        Optional<Client> match = clientComboBox.getItems()
                .stream()
                .filter(c -> c.getId() == clientId)
                .findFirst();

        match.ifPresent(c -> clientComboBox.getSelectionModel().select(c));
    }

    @FXML
    private void onClearFilters() {

        searchField.clear();
        clientComboBox.getSelectionModel().clearSelection();

        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);
        statusFilterComboBox.getSelectionModel().select("All");

        loadAllJobs();
    }

    // =========================================================
    // ✅ EDIT / DELETE
    // =========================================================
    private void openEditJobScreen(Job job) {
        if (job == null) {
            toast("Cannot edit: job is null ❌");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/edit_job.fxml"));
            Parent view = loader.load();

            EditJobController controller = loader.getController();
            if (controller == null) {
                toast("Failed to load Edit Controller ❌");
                return;
            }

            // ✅ PASS FULL JOB OBJECT
            controller.openForEdit(job);

            utils.NavigationManager.getInstance().push("/fxml/edit_job.fxml", "Edit Job", "Editing Job...", "jobsSidebar");
            utils.NavigationManager.getInstance().updateCurrentState(view, controller);

            MainController.getInstance().setCenterView(view);

        } catch (Exception ex) {
            ex.printStackTrace();
            toast("View load failed: " + ex.getMessage() + " ❌");
        }
    }




    private void openInvoiceDetails(Integer invoiceId) {
        if (invoiceId == null || invoiceId == 0) return;
        
        new Thread(() -> {
            InvoiceMaster inv = invoiceService.getInvoiceById(invoiceId);
            if (inv != null) {
                Platform.runLater(() -> {
                    ViewInvoiceJobsController.pendingPrefillInvoice = inv;
                    ViewInvoiceJobsController.viewOnlyMode = true;
                    MainController.getInstance().loadViewInvoiceJobs();
                });
            }
        }).start();
    }

    private void showImagePreview(String path) {
        if (path == null || path.isEmpty()) return;
        
        try {
            File file = new File(path);
            if (!file.exists()) {
                toast("Image file not found ❌");
                return;
            }
            
            Stage previewStage = new Stage(StageStyle.TRANSPARENT);
            previewStage.initModality(Modality.APPLICATION_MODAL);
            
            Image image = new Image(file.toURI().toString());
            ImageView imageView = new ImageView(image);
            imageView.setPreserveRatio(true);
            
            // Limit preview size
            imageView.setFitWidth(800);
            imageView.setFitHeight(600);
            
            StackPane root = new StackPane(imageView);
            root.setStyle("-fx-background-color: rgba(0,0,0,0.85); -fx-background-radius: 12; -fx-padding: 20;");
            
            // Close button
            Button closeBtn = new Button("✕");
            closeBtn.setStyle("-fx-background-color: #F5222D; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 50; -fx-min-width: 30; -fx-min-height: 30;");
            StackPane.setAlignment(closeBtn, Pos.TOP_RIGHT);
            closeBtn.setOnAction(e -> previewStage.close());
            
            root.getChildren().add(closeBtn);
            
            Scene scene = new Scene(root);
            scene.setFill(null);
            previewStage.setScene(scene);
            
            // Close on clicking outside
            root.setOnMouseClicked(e -> {
                if (e.getTarget() == root) previewStage.close();
            });
            
            previewStage.show();
            
        } catch (Exception e) {
            e.printStackTrace();
            toast("Failed to load image preview ❌");
        }
    }

    
    private void toast(String message) {
		Stage stage = (Stage) ((Node) clientComboBox).getScene().getWindow();
		Toast.show(stage, message);
	}
	@FXML
	private void handleBack(javafx.event.Event e) {
		MainController.getInstance().handleBack(e);
	}
}
