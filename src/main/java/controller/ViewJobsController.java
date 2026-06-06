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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.application.Platform;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Properties;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import model.EmailSettings;
import repository.EmailSettingsRepository;
import model.Supplier;
import utils.DBConnection;
import repository.ClientRepository;
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
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import model.Client;
import model.Job;
import service.ClientService;
import service.JobService;
import utils.JobWorkflow;
import utils.Toast;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.TextAlignment;

public class ViewJobsController {

    /** Minimal vertical insets for checkbox / date / amount / actions. */
    private static final Insets TABLE_LEAD_COL_PADDING = new Insets(0, 0, 0, 0);

    /** Job Details column: fixed icon column + gap so text maxWidth matches layout (must match CSS .icon-box width + root HBox spacing). */
    private static final double JOB_DETAILS_ICON_COL = 32;
    private static final double JOB_DETAILS_ICON_GAP = 8;

    /** When set before opening View Jobs, client filter selects this client id once clients load. */
    public static volatile String pendingFilterClientUuid;

    private final ClientService clientService = new ClientService();
    private final JobService jobService = new JobService();
    private final InvoiceMasterService invoiceService = new InvoiceMasterService();

    private final ObservableList<Job> masterJobs = FXCollections.observableArrayList();

    // ✅ clientId -> clientName map (for fast lookup)
    private final Map<String, String> clientNameMap = new HashMap<>();

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
    @FXML private HBox breadcrumbContainer;

    @FXML private Label paginationInfoLabel;
    /** Optional in FXML; created in code when missing (pagination row is rebuilt). */
    @FXML private TextField goToPageField;
    @FXML private HBox paginationButtonContainer;

    private int currentPage = 1;
    private final int pageSize = 20;
    private final ObservableList<Job> pagedItems = FXCollections.observableArrayList();
    private boolean wasInvalidSelection = false;


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
            syncSelectedPropertyFromModel();
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

        ensureGoToPageField();

        // ✅ Row factory — same pattern as ViewInvoicesController:
        //   MOUSE_PRESSED event filter handles single-click toggle.
        //   Shift / Ctrl / Shortcut modifiers are forwarded to JavaFX's built-in
        //   multi-selection (range select and individual toggle work natively).
        //   Double-click opens job details.
        jobsTable.setRowFactory(tv -> {
            TableRow<Job> row = new TableRow<>();
            row.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                if (e.getButton() != MouseButton.PRIMARY || row.isEmpty() || jobsTable == null) {
                    return;
                }
                // Let JavaFX handle shift / ctrl range and toggle natively
                if (e.isShiftDown() || e.isControlDown() || e.isShortcutDown()) {
                    return;
                }
                Job item = row.getItem();
                if (item != null) {
                    int index = row.getIndex();
                    if (jobsTable.getSelectionModel().isSelected(index)) {
                        jobsTable.getSelectionModel().clearSelection(index);
                    } else {
                        jobsTable.getSelectionModel().clearAndSelect(index);
                    }
                    e.consume();
                }
            });
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showJobDetails(row.getItem());
                }
            });
            return row;
        });

        utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null, () -> handleBack(null));

    }


    private void ensureGoToPageField() {
        if (goToPageField != null) {
            return;
        }
        goToPageField = new TextField();
        goToPageField.setPromptText("Page");
        goToPageField.setPrefColumnCount(4);
        goToPageField.setMaxWidth(72);
        goToPageField.getStyleClass().add("goto-field");
        goToPageField.setOnAction(e -> handleGoToPage());
    }
    
    // =========================================================
    // ✅ ACTION BAR LOGIC
    // =========================================================

    /**
     * Syncs job.selectedProperty() with what the TableView selection model actually has selected.
     * Uses object identity (not index) so it is correct even when the table has a sort applied.
     */
    private void syncSelectedPropertyFromModel() {
        java.util.Set<Job> selectedSet = new java.util.HashSet<>(jobsTable.getSelectionModel().getSelectedItems());
        for (Job j : jobsTable.getItems()) {
            if (j != null) j.selectedProperty().set(selectedSet.contains(j));
        }
    }


    private void updateBulkActionBar() {
        List<Job> selected = jobsTable.getItems().stream()
                .filter(Job::isSelected)
                .collect(Collectors.toList());

        if (selected.isEmpty()) {
            wasInvalidSelection = false;
            selectionCountLabel.setText("0 jobs selected");
            // Keep bar visible but disable all action buttons
            bulkStartBtn.setVisible(true);   bulkStartBtn.setManaged(true);
            bulkCompleteBtn.setVisible(true); bulkCompleteBtn.setManaged(true);
            bulkInvoiceBtn.setVisible(true);  bulkInvoiceBtn.setManaged(true);
            bulkCancelBtn.setVisible(true);   bulkCancelBtn.setManaged(true);
            bulkStartBtn.setDisable(true);
            bulkCompleteBtn.setDisable(true);
            bulkInvoiceBtn.setDisable(true);
            bulkCancelBtn.setDisable(true);
            bulkStartBtn.setText("Start Processing");
            bulkCompleteBtn.setText("Mark Completed");
            bulkInvoiceBtn.setText("Generate Invoice");
            bulkCancelBtn.setText("Cancel Job");
        } else {
            selectionCountLabel.setText(selected.size() + " job" + (selected.size() == 1 ? "" : "s") + " selected");

            java.util.Set<String> uniqueStatuses = selected.stream()
                    .map(j -> j.getStatus() == null ? "" : j.getStatus().trim().toLowerCase())
                    .collect(Collectors.toSet());

            if (uniqueStatuses.size() > 1) {
                // Mixed statuses — hide action buttons
                bulkStartBtn.setVisible(false);   bulkStartBtn.setManaged(false);
                bulkCompleteBtn.setVisible(false); bulkCompleteBtn.setManaged(false);
                bulkInvoiceBtn.setVisible(false);  bulkInvoiceBtn.setManaged(false);
                bulkCancelBtn.setVisible(false);   bulkCancelBtn.setManaged(false);

                if (!wasInvalidSelection) {
                    toast("Different status jobs can't be moved ❌");
                    wasInvalidSelection = true;
                }
            } else {
                wasInvalidSelection = false;

                bulkStartBtn.setVisible(true);   bulkStartBtn.setManaged(true);
                bulkCompleteBtn.setVisible(true); bulkCompleteBtn.setManaged(true);
                bulkInvoiceBtn.setVisible(true);  bulkInvoiceBtn.setManaged(true);
                bulkCancelBtn.setVisible(true);   bulkCancelBtn.setManaged(true);

                long draftCount      = selected.stream().filter(j -> "Draft".equalsIgnoreCase(j.getStatus())).count();
                long processingCount = selected.stream().filter(j -> "In Progress".equalsIgnoreCase(j.getStatus())).count();
                long completedCount  = selected.stream().filter(j -> "Completed".equalsIgnoreCase(j.getStatus())).count();
                long anyButCancelled = selected.stream().filter(j -> !"Cancelled".equalsIgnoreCase(j.getStatus())).count();

                bulkStartBtn.setText("Start Processing (" + draftCount + ")");
                bulkStartBtn.setDisable(draftCount == 0);

                bulkCompleteBtn.setText("Mark Completed (" + processingCount + ")");
                bulkCompleteBtn.setDisable(processingCount == 0);

                bulkInvoiceBtn.setText("Generate Invoice (" + completedCount + ")");
                bulkInvoiceBtn.setDisable(completedCount == 0);

                bulkCancelBtn.setText("Cancel Job (" + anyButCancelled + ")");
                bulkCancelBtn.setDisable(anyButCancelled == 0);
            }
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
        for (Job job : jobsTable.getItems()) {
            if (job.isSelected()) {
                job.selectedProperty().set(false);
            }
        }
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
        List<Job> selected = jobsTable.getItems().stream()
                .filter(Job::isSelected)
                .collect(Collectors.toList());
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
        
        first.selectedProperty().set(false);
        updateBulkActionBar();
        
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
        List<Job> selected = jobsTable.getItems().stream()
                .filter(Job::isSelected)
                .collect(Collectors.toList());
        if (selected.isEmpty()) return;

        for (Job job : selected) {
            jobService.updateJobStatus(job.getUuid(), status);
            job.setStatus(status);
            applyDefaultChildForNewMajor(job, status);
            job.selectedProperty().set(false);
        }
        jobsTable.refresh();
        toast(successMsg);
        jobsTable.getSelectionModel().clearSelection();
        updateBulkActionBar();
    }
    
    private void handleInvoicedRequest(Job job) {
        if (job.getClientId() == null) {
            toast("❌ Job has no client associated.");
            return;
        }
        MainController.getInstance().loadInvoiceWithJob(job.getClientId(), job.getUuid());
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
            if (c != null) clientNameMap.put(c.getClientUuid(), c.getBusinessName());
        }
        // Refresh visible rows that display client names.
        if (jobsTable != null) jobsTable.refresh();
        applyPendingClientFilter();
    }

    private void applyPendingClientFilter() {
        String uuid = pendingFilterClientUuid;
        if (uuid == null || uuid.isBlank() || clientComboBox == null || clientComboBox.getItems().isEmpty()) {
            return;
        }
        pendingFilterClientUuid = null;
        autoSelectClient(uuid);
    }

    private static void useGraphicOnlyCell(TableCell<?, ?> cell) {
        cell.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        cell.setGraphicTextGap(0);
        cell.setText(null);
    }

    // =========================================================
    // ✅ TABLE SETUP
    // =========================================================
    private void setupTableColumns() {
        // 1. SELECT COLUMN — same pattern as ViewInvoicesController but matching Boolean type
        selectCol.setCellValueFactory(c -> new javafx.beans.property.SimpleBooleanProperty(false));
        selectCol.setCellFactory(col -> new TableCell<Job, Boolean>() {
            private final CheckBox checkBox = new CheckBox();
            private final HBox box = new HBox(checkBox);
            private final javafx.beans.value.ChangeListener<Boolean> rowSelectionListener =
                    (obs, oldVal, newVal) -> checkBox.setSelected(newVal != null && newVal);

            {
                getStyleClass().add("table-cell-job-select");
                box.setAlignment(Pos.CENTER);
                box.setPadding(TABLE_LEAD_COL_PADDING);
                setAlignment(Pos.CENTER);
                checkBox.setFocusTraversable(false);
                checkBox.setMnemonicParsing(false);

                // Checkbox click: toggle selection; consume all mouse events so they
                // do NOT bubble up to the row's MOUSE_PRESSED event filter.
                checkBox.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                    if (e.getButton() == MouseButton.PRIMARY) {
                        e.consume();
                        TableRow<?> tableRow = getTableRow();
                        if (tableRow != null && !tableRow.isEmpty()) {
                            int index = tableRow.getIndex();
                            jobsTable.requestFocus();
                            if (jobsTable.getSelectionModel().isSelected(index)) {
                                jobsTable.getSelectionModel().clearSelection(index);
                            } else {
                                jobsTable.getSelectionModel().select(index);
                            }
                        }
                    }
                });
                checkBox.addEventFilter(MouseEvent.MOUSE_RELEASED, MouseEvent::consume);
                checkBox.addEventFilter(MouseEvent.MOUSE_CLICKED,  MouseEvent::consume);

                // Keep checkbox in sync with the row's built-in selection state
                tableRowProperty().addListener((obs, oldRow, newRow) -> {
                    if (oldRow != null) oldRow.selectedProperty().removeListener(rowSelectionListener);
                    if (newRow != null) {
                        newRow.selectedProperty().addListener(rowSelectionListener);
                        checkBox.setSelected(newRow.isSelected());
                    } else {
                        checkBox.setSelected(false);
                    }
                });
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    TableRow<?> row = getTableRow();
                    checkBox.setSelected(row != null && row.isSelected());
                    useGraphicOnlyCell(this);
                    setGraphic(box);
                }
            }
        });
        // Table does not need to be editable for checkbox-driven selection

        // 2. JOB DETAILS COLUMN (Target Vision Alignment)
        jobDetailsCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<Job>(cellData.getValue()));
        jobDetailsCol.setCellFactory(col -> new TableCell<Job, Job>() {
            private final VBox box = new VBox(0);
            private final Label title = new Label();
            private final Label clientLabel = new Label();
            private final Label idLabel = new Label();
            private final HBox root = new HBox(JOB_DETAILS_ICON_GAP);
            private final StackPane detailsShell = new StackPane();
            private final StackPane iconBox = new StackPane();
            private final Region icon = new Region();
            private final Runnable syncTextColumnWidth = () -> {
                double cw = getWidth();
                if (cw <= 0) {
                    return;
                }
                double pad = getPadding().getLeft() + getPadding().getRight();
                double textW = cw - pad - JOB_DETAILS_ICON_COL - JOB_DETAILS_ICON_GAP;
                if (textW < 48) {
                    textW = 48;
                }
                title.setMaxWidth(textW);
                clientLabel.setMaxWidth(textW);
                idLabel.setMaxWidth(textW);
            };

            {
                getStyleClass().add("table-cell-job-details");
                setAlignment(Pos.CENTER_LEFT);
                title.getStyleClass().add("job-title-row");
                clientLabel.getStyleClass().add("job-client-row");
                idLabel.getStyleClass().add("job-id-row");
                title.setWrapText(true);
                clientLabel.setWrapText(true);
                title.setAlignment(Pos.TOP_LEFT);
                title.setTextAlignment(TextAlignment.LEFT);
                clientLabel.setAlignment(Pos.TOP_LEFT);
                clientLabel.setTextAlignment(TextAlignment.LEFT);
                idLabel.setAlignment(Pos.TOP_LEFT);
                idLabel.setTextAlignment(TextAlignment.LEFT);
                box.setAlignment(Pos.TOP_LEFT);
                box.setFillWidth(true);
                box.setMaxWidth(Double.MAX_VALUE);
                box.getChildren().addAll(title, clientLabel, idLabel);
                widthProperty().addListener((obs, ov, nv) -> syncTextColumnWidth.run());
                paddingProperty().addListener((obs, ov, nv) -> syncTextColumnWidth.run());
                root.setAlignment(Pos.CENTER_LEFT);
                root.setFillHeight(false);
                root.setPadding(Insets.EMPTY);
                root.setMaxWidth(Double.MAX_VALUE);
                root.setMinWidth(0);
                iconBox.setMinWidth(JOB_DETAILS_ICON_COL);
                iconBox.setPrefWidth(JOB_DETAILS_ICON_COL);
                iconBox.setMaxWidth(JOB_DETAILS_ICON_COL);
                iconBox.setMinHeight(JOB_DETAILS_ICON_COL);
                iconBox.setPrefHeight(JOB_DETAILS_ICON_COL);
                iconBox.setMaxHeight(JOB_DETAILS_ICON_COL);
                iconBox.setAlignment(Pos.CENTER);
                iconBox.getStyleClass().add("icon-box");
                icon.getStyleClass().add("inner-icon");
                iconBox.getChildren().add(icon);
                root.getChildren().addAll(iconBox, box);
                HBox.setHgrow(box, Priority.ALWAYS);
                detailsShell.getChildren().add(root);
                StackPane.setAlignment(root, Pos.CENTER_LEFT);
                detailsShell.setMaxWidth(Double.MAX_VALUE);
                detailsShell.setMaxHeight(Double.MAX_VALUE);
            }

            @Override
            protected void updateItem(Job job, boolean empty) {
                super.updateItem(job, empty);
                if (empty || job == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
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
                icon.setStyle("-fx-shape: '" + shape + "'; -fx-background-color: " + colorHex + "; -fx-min-width: 15; -fx-min-height: 15; -fx-max-width: 15; -fx-max-height: 15;");

                useGraphicOnlyCell(this);
                setGraphic(detailsShell);
                Platform.runLater(syncTextColumnWidth);
            }
        });

        // 3. DATE COLUMN (Target Icons)
        dateCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<LocalDate>(cellData.getValue().getJobDate()));
        dateCol.setCellFactory(col -> new TableCell<Job, LocalDate>() {
            private final HBox root = new HBox(3);
            private final Region icon = new Region();
            private final Label label = new Label();
            {
                getStyleClass().add("table-cell-job-date");
                setAlignment(Pos.CENTER);
                root.setAlignment(Pos.CENTER);
                root.setFillHeight(false);
                root.setPadding(TABLE_LEAD_COL_PADDING);
                icon.setStyle("-fx-shape: 'M19 4h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V10h14v10zm0-12H5V6h14v2z'; -fx-background-color: #A0836D; -fx-min-width: 11; -fx-min-height: 11; -fx-max-width: 11; -fx-max-height: 11;");
                label.getStyleClass().add("job-date-text");
                root.getChildren().addAll(icon, label);
            }
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                label.setText(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy").format(date));
                useGraphicOnlyCell(this);
                setGraphic(root);
            }
        });

        // 4. AMOUNT COLUMN (Metadata Synthesis with Clickable Links)
        amountCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<Job>(cellData.getValue()));
        amountCol.setCellFactory(col -> new TableCell<Job, Job>() {
            private final VBox box = new VBox(0);
            private final StackPane amountShell = new StackPane();
            private final Label amount = new Label();
            private final javafx.scene.layout.FlowPane metaBox = new javafx.scene.layout.FlowPane();
            private final Label invPrefix = new Label("Invoice:");
            private final Hyperlink invLink = new Hyperlink();
            private final Label separator = new Label("|");
            private final Label imgPrefix = new Label("Images:");
            private final Hyperlink imgLink = new Hyperlink();
            
            {
                getStyleClass().add("table-cell-job-amount");
                setAlignment(Pos.CENTER_LEFT);
                amount.getStyleClass().add("amount-row");
                amount.setAlignment(Pos.TOP_LEFT);
                amount.setTextAlignment(TextAlignment.LEFT);
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
                
                metaBox.setHgap(4);
                metaBox.setVgap(2);
                metaBox.setPrefWrapLength(140);
                metaBox.getChildren().addAll(invPrefix, invLink, separator, imgPrefix, imgLink);
                metaBox.setAlignment(Pos.CENTER_LEFT);
                metaBox.setMaxWidth(Double.MAX_VALUE);
                box.getChildren().addAll(amount, metaBox);
                /* CENTER_LEFT: left-align ₹ + invoice row; vertically center that stack in the cell when row is tall */
                box.setAlignment(Pos.CENTER_LEFT);
                box.setFillWidth(false);
                box.setMaxWidth(Region.USE_PREF_SIZE);
                box.setMaxHeight(Double.MAX_VALUE);
                box.setPadding(TABLE_LEAD_COL_PADDING);
                amountShell.getChildren().add(box);
                StackPane.setAlignment(box, Pos.CENTER_LEFT);
                amountShell.setMaxWidth(Double.MAX_VALUE);
                amountShell.setMaxHeight(Double.MAX_VALUE);
            }
            
            @Override
            protected void updateItem(Job job, boolean empty) {
                super.updateItem(job, empty);
                if (empty || job == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                
                amount.setText("₹ " + String.format("%.2f", job.getJobTotal() != null ? job.getJobTotal() : 0.0));
                
                String invNo = job.getInvoiceNo();
                if (invNo != null && !invNo.isEmpty() && !invNo.equals("-")) {
                    invLink.setText(invNo);
                    invLink.setDisable(false);
                    invLink.setOnAction(e -> openInvoiceDetailsByUuid(job.getInvoiceUuid()));
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
                
                useGraphicOnlyCell(this);
                setGraphic(amountShell);
            }
        });

        // 5. STATUS & PROGRESS COLUMN (major stepper + child workflow pills)
        statusProgressCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<Job>(cellData.getValue()));
        statusProgressCol.setCellFactory(col -> new TableCell<Job, Job>() {
            private final StackPane statusShell = new StackPane();

            {
                getStyleClass().add("table-cell-status-progress");
                statusShell.setMaxWidth(Double.MAX_VALUE);
                statusShell.setMaxHeight(Double.MAX_VALUE);
            }
            @Override
            protected void updateItem(Job job, boolean empty) {
                super.updateItem(job, empty);
                if (empty || job == null) {
                    statusShell.getChildren().clear();
                    setGraphic(null);
                    setText(null);
                    setAlignment(Pos.CENTER);
                    return;
                }
                setAlignment(Pos.CENTER);
                VBox block = new VBox(0);
                block.setAlignment(Pos.TOP_LEFT);
                block.getStyleClass().add("status-progress-cell");
                block.setPadding(new Insets(0, 2, 0, 2));
                block.setSpacing(0);
                block.setMaxWidth(Region.USE_PREF_SIZE);
                block.setMinHeight(Region.USE_PREF_SIZE);
                block.setMaxHeight(Region.USE_PREF_SIZE);

                final double statusContentWidth = 328;
                VBox parentTop = buildMajorStepperSection(job, statusContentWidth);
                parentTop.setMinWidth(statusContentWidth);
                parentTop.setPrefWidth(statusContentWidth);
                parentTop.setMaxWidth(statusContentWidth);

                VBox childPanel = buildChildWorkflowPanel(job);
                VBox.setMargin(childPanel, new Insets(0, 0, 0, 0));

                block.getChildren().addAll(parentTop, childPanel);
                statusShell.getChildren().setAll(block);
                StackPane.setAlignment(block, Pos.CENTER);
                useGraphicOnlyCell(this);
                setGraphic(statusShell);
            }
        });

        // 6. ACTIONS COLUMN (Triad Suite)
        actionsCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<Job>(cellData.getValue()));
        actionsCol.setCellFactory(col -> new TableCell<Job, Job>() {
            private final HBox root = new HBox(4);
            { 
                getStyleClass().add("table-cell-job-actions");
                root.setAlignment(Pos.CENTER);
                root.setFillHeight(false);
                root.setMaxWidth(Region.USE_PREF_SIZE);
                root.setPadding(TABLE_LEAD_COL_PADDING);
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Job job, boolean empty) {
                super.updateItem(job, empty);
                if (empty || job == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                root.getChildren().clear();
                
                String statusMsg = job.getStatus() != null ? job.getStatus().toLowerCase() : "";
                boolean isCancelled = statusMsg.contains("cancel");
                boolean isInvoiced = statusMsg.contains("invoice") || statusMsg.contains("invoic");
                
                Button primaryBtn = new Button();
                primaryBtn.setMinHeight(24);
                primaryBtn.setMaxHeight(24);
                primaryBtn.setPrefHeight(24);
                primaryBtn.setPadding(new Insets(2, 8, 2, 8));
                
                if (!isInvoiced) {
                    if (statusMsg.contains("draft") || statusMsg.contains("created")) {
                        primaryBtn.setText("Start Processing");
                        primaryBtn.getStyleClass().setAll("row-action-btn-primary");
                        primaryBtn.setGraphic(createIcon("M8 5v14l11-7z", "white"));
                        primaryBtn.setOnAction(e -> handleStartActionForJob(job));
                    } else if (statusMsg.contains("progress")) {
                        primaryBtn.setText("Mark Completed");
                        primaryBtn.getStyleClass().setAll("row-action-btn-primary", "row-action-green");
                        primaryBtn.setGraphic(createIcon("M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z", "white"));
                        primaryBtn.setOnAction(e -> handleCompleteActionForJob(job));
                    } else if (statusMsg.contains("completed")) {
                        primaryBtn.setText("Generate Invoice");
                        primaryBtn.getStyleClass().setAll("row-action-btn-primary", "row-action-purple");
                        primaryBtn.setGraphic(createIcon("M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z", "white"));
                        primaryBtn.setOnAction(e -> handleInvoicedRequest(job));
                    } else {
                        primaryBtn.setText("No Actions");
                        primaryBtn.setDisable(true);
                        primaryBtn.getStyleClass().setAll("row-action-btn-primary", "row-action-muted");
                    }
                }
                
                Button eBtn = new Button(); eBtn.getStyleClass().addAll("row-action-btn", "row-btn-orange");
                eBtn.setGraphic(createIcon("M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z", "#FA8C16"));
                eBtn.setOnAction(e -> openEditJobScreen(job));
                eBtn.setTooltip(new Tooltip("Edit Job"));

                Button mailBtn = new Button(); mailBtn.getStyleClass().addAll("row-action-btn", "row-btn-purple");
                mailBtn.setGraphic(createIcon("M2 21l21-9L2 3v7l15 2-15 2z", "#722ED1"));
                mailBtn.setOnAction(e -> handleSendMailPopup(job));
                mailBtn.setTooltip(new Tooltip("Send Email"));

                Button cancelBtn = new Button(); cancelBtn.getStyleClass().addAll("row-action-btn", "row-btn-red");
                cancelBtn.setGraphic(createIcon("M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z", "#F5222D"));
                cancelBtn.setOnAction(e -> handleCancelActionForJob(job));
                cancelBtn.setTooltip(new Tooltip("Cancel Job"));
                
                if (!isInvoiced) {
                    root.getChildren().add(primaryBtn);
                }
                if (!isCancelled) {
                    root.getChildren().add(eBtn);
                }
                root.getChildren().add(mailBtn);
                if (!isCancelled && !isInvoiced) {
                    root.getChildren().add(cancelBtn);
                }
                useGraphicOnlyCell(this);
                setGraphic(root);
            }
        });
        jobsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    public static class MailRecipient {
        private final String type;
        private final String businessName;
        private final String name;
        private final String email;
        private final String phone;

        public MailRecipient(String type, String businessName, String name, String email, String phone) {
            this.type = type;
            this.businessName = businessName;
            this.name = name;
            this.email = email;
            this.phone = phone;
        }

        public String getType() { return type; }
        public String getBusinessName() { return businessName; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
    }

    private void handleSendMailPopup(Job job) {
        if (job == null) return;
        
        Client client = null;
        try {
            ClientRepository clientRepo = new ClientRepository();
            client = clientRepo.findByUuid(job.getClientUuid());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        List<model.Supplier> suppliers = new ArrayList<>();
        String sql = "SELECT * FROM suppliers WHERE IFNULL(is_deleted,0) = 0 ORDER BY business_name ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                model.Supplier s = new model.Supplier();
                s.setUuid(rs.getString("uuid"));
                s.setName(rs.getString("name"));
                s.setbusinessName(rs.getString("business_name"));
                s.setType(rs.getString("type"));
                s.setPhone(rs.getString("phone"));
                s.setAddress(rs.getString("address"));
                s.setGstNumber(rs.getString("gst_number"));
                suppliers.add(s);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        ObservableList<MailRecipient> recipients = FXCollections.observableArrayList();
        if (client != null) {
            recipients.add(new MailRecipient("Client (Owner)", 
                client.getBusinessName() != null ? client.getBusinessName() : "", 
                client.getClientName() != null ? client.getClientName() : "", 
                client.getEmail() != null ? client.getEmail() : "", 
                client.getPhone() != null ? client.getPhone() : ""));
        }
        for (model.Supplier s : suppliers) {
            recipients.add(new MailRecipient("Supplier (" + (s.getType() != null ? s.getType() : "") + ")", 
                s.getbusinessName() != null ? s.getbusinessName() : "", 
                s.getName() != null ? s.getName() : "", 
                "", 
                s.getPhone() != null ? s.getPhone() : ""));
        }
        
        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);
        
        VBox root = new VBox(15);
        root.getStyleClass().add("mail-popup-root");
        root.setMinWidth(600);
        root.setMaxWidth(700);
        
        // Header
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        VBox titleBox = new VBox(2);
        Label idLbl = new Label("EMAIL NOTIFICATION");
        idLbl.setStyle("-fx-text-fill: #CD7B4E; -fx-font-weight: 800; -fx-font-size: 11px; -fx-letter-spacing: 0.1em;");
        Label titleLbl = new Label("Send Email for " + job.getJobTitle());
        titleLbl.setStyle("-fx-text-fill: #3E312D; -fx-font-weight: 800; -fx-font-size: 18px; -fx-font-family: 'Inter';");
        titleBox.getChildren().addAll(idLbl, titleLbl);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("view-clear-btn");
        closeBtn.setStyle("-fx-min-width: 28; -fx-min-height: 28; -fx-font-size: 12px; -fx-padding: 0;");
        closeBtn.setOnAction(e -> stage.close());
        header.getChildren().addAll(titleBox, spacer, closeBtn);
        
        // Table label
        Label tableLabel = new Label("Select Recipient (Client or Supplier):");
        tableLabel.setStyle("-fx-text-fill: #3E312D; -fx-font-weight: 700; -fx-font-size: 12px;");
        
        // TableView for recipients
        TableView<MailRecipient> table = new TableView<>();
        table.getStyleClass().add("mail-popup-table");
        table.setPrefHeight(150);
        
        TableColumn<MailRecipient, String> typeCol = new TableColumn<>("Role / Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setPrefWidth(120);
        
        TableColumn<MailRecipient, String> bNameCol = new TableColumn<>("Business Name");
        bNameCol.setCellValueFactory(new PropertyValueFactory<>("businessName"));
        bNameCol.setPrefWidth(150);
        
        TableColumn<MailRecipient, String> nameCol = new TableColumn<>("Contact Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(120);
        
        TableColumn<MailRecipient, String> emailCol = new TableColumn<>("Email Address");
        emailCol.setCellValueFactory(new PropertyValueFactory<>("email"));
        emailCol.setPrefWidth(130);
        
        table.getColumns().addAll(typeCol, bNameCol, nameCol, emailCol);
        table.setItems(recipients);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        // Form Fields
        Label toLabel = new Label("To Email:");
        toLabel.setStyle("-fx-text-fill: #3E312D; -fx-font-weight: 700;");
        TextField toEmailField = new TextField();
        toEmailField.setPromptText("Enter recipient email address...");
        toEmailField.getStyleClass().add("mail-field");
        
        Label subjectLabel = new Label("Subject:");
        subjectLabel.setStyle("-fx-text-fill: #3E312D; -fx-font-weight: 700;");
        TextField subjectField = new TextField("Sunny Printers: Update for Job - " + job.getJobTitle());
        subjectField.getStyleClass().add("mail-field");
        
        Label messageLabel = new Label("Message:");
        messageLabel.setStyle("-fx-text-fill: #3E312D; -fx-font-weight: 700;");
        TextArea messageArea = new TextArea();
        messageArea.setPrefRowCount(6);
        messageArea.setWrapText(true);
        messageArea.getStyleClass().add("mail-area");
        
        // Add listener to table
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                toEmailField.setText(newSel.getEmail() != null ? newSel.getEmail() : "");
                
                String salutation = newSel.getBusinessName() != null && !newSel.getBusinessName().isEmpty() 
                    ? newSel.getBusinessName() 
                    : (newSel.getName() != null ? newSel.getName() : "Team");
                    
                String customBody = "Dear " + salutation + ",\n\n"
                                  + "Here is an update regarding the job \"" + job.getJobTitle() + "\" (" + job.getJobNo() + "):\n\n"
                                  + "• Status: " + job.getStatus() + "\n"
                                  + "• Description: " + (job.getDescription() != null ? job.getDescription() : "N/A") + "\n"
                                  + "• Total Cost: Rs. " + String.format("%.2f", job.getJobTotal() != null ? job.getJobTotal() : 0.0) + "\n\n"
                                  + "Please feel free to contact us if you have any questions or require any adjustments.\n\n"
                                  + "Best Regards,\n"
                                  + "Sunny Printers Team";
                messageArea.setText(customBody);
            }
        });
        
        // Default select first item
        if (!recipients.isEmpty()) {
            table.getSelectionModel().selectFirst();
        }
        
        // Check email configuration
        boolean configOk = false;
        String warningMsg = "";
        try {
            EmailSettingsRepository emailRepo = new EmailSettingsRepository();
            EmailSettings settings = emailRepo.load();
            if (settings.getSenderEmail() != null && !settings.getSenderEmail().isBlank()) {
                configOk = true;
            } else {
                warningMsg = "⚠ SMTP Sender settings not configured in Settings screen!";
            }
        } catch (Exception ex) {
            warningMsg = "⚠ Error loading SMTP settings: " + ex.getMessage();
        }
        
        Label statusInfoLabel = new Label(configOk ? "SMTP Configured" : warningMsg);
        statusInfoLabel.setStyle(configOk 
            ? "-fx-text-fill: #228B22; -fx-font-weight: 600; -fx-font-size: 11px;" 
            : "-fx-text-fill: #F5222D; -fx-font-weight: 700; -fx-font-size: 11px;");
        
        Button sendBtn = new Button("Send Email");
        sendBtn.getStyleClass().add("finish-btn-premium");
        sendBtn.setGraphic(createIcon("M2 21l21-9L2 3v7l15 2-15 2z", "white"));
        sendBtn.setDisable(!configOk);
        
        sendBtn.setOnAction(e -> {
            String toEmail = toEmailField.getText().trim();
            String subject = subjectField.getText().trim();
            String messageBody = messageArea.getText();
            
            if (toEmail.isEmpty()) {
                toast("❌ Please enter a recipient email address!");
                return;
            }
            if (!toEmail.contains("@")) {
                toast("❌ Please enter a valid email address!");
                return;
            }
            
            sendBtn.setDisable(true);
            sendBtn.setText("Sending...");
            
            new Thread(() -> {
                try {
                    EmailSettingsRepository repo = new EmailSettingsRepository();
                    EmailSettings settings = repo.load();
                    
                    String smtpHost = settings.getSmtpHost();
                    String smtpPort = settings.getSmtpPort();
                    String senderEmail = settings.getSenderEmail();
                    String senderPassword = settings.getSenderPassword();
                    
                    Properties props = new Properties();
                    props.put("mail.smtp.auth", "true");
                    props.put("mail.smtp.starttls.enable", "true");
                    props.put("mail.smtp.host", smtpHost);
                    props.put("mail.smtp.port", smtpPort);
                    
                    Session session = Session.getInstance(props, new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(senderEmail, senderPassword);
                        }
                    });
                    
                    Message message = new MimeMessage(session);
                    message.setFrom(new InternetAddress(senderEmail, "Sunny Printers"));
                    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
                    message.setSubject(subject);
                    message.setContent(messageBody.replace("\n", "<br>"), "text/html; charset=utf-8");
                    
                    Transport.send(message);
                    
                    Platform.runLater(() -> {
                        toast("✅ Email sent successfully!");
                        stage.close();
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> {
                        toast("❌ Failed to send email: " + ex.getMessage());
                        sendBtn.setDisable(false);
                        sendBtn.setText("Send Email");
                    });
                }
            }).start();
        });
        
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("cancel-btn");
        cancelBtn.setOnAction(e -> stage.close());
        
        HBox actionRow = new HBox(10);
        actionRow.setAlignment(Pos.CENTER_RIGHT);
        actionRow.getChildren().addAll(statusInfoLabel, cancelBtn, sendBtn);
        
        VBox formBox = new VBox(8);
        formBox.getChildren().addAll(
            toLabel, toEmailField,
            subjectLabel, subjectField,
            messageLabel, messageArea
        );
        
        root.getChildren().addAll(header, tableLabel, table, formBox, actionRow);
        
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        
        // Apply styling stylesheets
        String themeUrl = getClass().getResource("/css/theme.css").toExternalForm();
        scene.getStylesheets().add(themeUrl);
        String viewJobCss = getClass().getResource("/css/view_job.css").toExternalForm();
        scene.getStylesheets().add(viewJobCss);
        
        stage.setScene(scene);
        stage.showAndWait();
    }

    private void handleStartActionForJob(Job job) {
        jobService.updateJobStatus(job.getUuid(), "In Progress");
        job.setStatus("In Progress");
        applyDefaultChildForNewMajor(job, "In Progress");
        jobsTable.refresh();
        toast("Job started!");
    }

    private void handleCompleteActionForJob(Job job) {
        jobService.updateJobStatus(job.getUuid(), "Completed");
        job.setStatus("Completed");
        applyDefaultChildForNewMajor(job, "Completed");
        jobsTable.refresh();
        toast("Job completed!");
    }

    private void handleCancelActionForJob(Job job) {
        jobService.updateJobStatus(job.getUuid(), "Cancelled");
        job.setStatus("Cancelled");
        applyDefaultChildForNewMajor(job, "Cancelled");
        jobsTable.refresh();
        toast("Job cancelled.");
    }

    /** Theme primary, tinted child panel background, and 24dp-style icon path (stroke-drawn in UI). */
    private record MajorStatusPaint(String primary, String panelBg, String iconSvg) {}

    private static MajorStatusPaint majorStatusPaint(JobWorkflow.Major major) {
        return switch (major) {
            case PROCESSING -> new MajorStatusPaint(
                    "#0044CC",
                    "#F0F5FF",
                    "M19 8H5c-1.66 0-3 1.34-3 3v6h4v4h12v-4h4v-6c0-1.66-1.34-3-3-3zm-3 11H8v-5h8v5zm3-7c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1zm-1-9H8v4h10V3z");
            case COMPLETED -> new MajorStatusPaint(
                    "#228B22",
                    "#F0F9F0",
                    "M21 16.5c0 .38-.21.71-.53.88l-7.9 4.44c-.16.12-.36.18-.57.18-.21 0-.41-.06-.57-.18l-7.9-4.44A.991.991 0 0 1 3 16.5v-9c0-.38.21-.71.53-.88l7.9-4.44c.16-.12.36-.18.57-.18.21 0 .41.06.57.18l7.9 4.44c.32.17.53.5.53.88v9zM12 4.15L6.04 7.5 12 10.85l5.96-3.35L12 4.15zM5 15.91l6 3.38v-6.71L5 9.19v6.72zm14 0v-6.72l-6 3.39v6.71l6-3.38z");
            case INVOICE -> new MajorStatusPaint(
                    "#6A0DAD",
                    "#F6F0FA",
                    "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z");
            case CANCELLED -> new MajorStatusPaint("#F5222D", "#FFF1F0", "");
            case DRAFT -> new MajorStatusPaint(
                    "#5D4037",
                    "#F7F2F0",
                    "M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z");
        };
    }

    /** Line-art style icon: transparent fill, theme stroke (reference: ~24–28px stroke icons). */
    private static Node createStrokeWorkflowIcon(String svgContent, String colorWeb, double targetPx) {
        SVGPath svg = new SVGPath();
        svg.setContent(svgContent);
        Paint p = Color.web(colorWeb);
        svg.setFill(Color.TRANSPARENT);
        svg.setStroke(p);
        svg.setStrokeWidth(1.2);
        svg.setStrokeLineCap(StrokeLineCap.ROUND);
        svg.setStrokeLineJoin(StrokeLineJoin.ROUND);
        Group g = new Group(svg);
        double s = targetPx / 24.0;
        g.setScaleX(s);
        g.setScaleY(s);
        StackPane pane = new StackPane(g);
        pane.setAlignment(Pos.CENTER);
        pane.setMinSize(targetPx, targetPx);
        pane.setPrefSize(targetPx, targetPx);
        pane.setMaxSize(targetPx, targetPx);
        return pane;
    }

    private VBox buildChildWorkflowPanel(Job job) {
        JobWorkflow.Major major = JobWorkflow.majorFromJobStatus(job.getStatus());
        VBox wrap = new VBox(0);
        wrap.setAlignment(Pos.TOP_LEFT);
        wrap.setMaxWidth(Region.USE_PREF_SIZE);
        if (major == JobWorkflow.Major.CANCELLED) {
            Label l = new Label("Workflow paused (cancelled)");
            l.getStyleClass().add("workflow-cancelled-note");
            wrap.getChildren().add(l);
            return wrap;
        }
        String effective = JobWorkflow.resolveEffectiveChild(job.getStatus(), job.getChildStatus(), major);
        java.util.List<String> steps = JobWorkflow.childSteps(major);
        if (steps.isEmpty()) {
            return wrap;
        }

        HBox panel = new HBox(6);
        panel.setAlignment(Pos.CENTER_LEFT);
        panel.setFillHeight(false);
        MajorStatusPaint paint = majorStatusPaint(major);
        String colorHex = paint.primary();
        panel.setPadding(new Insets(4, 10, 4, 10));
        panel.setMinHeight(34);
        panel.setMaxWidth(Double.MAX_VALUE);

        panel.setStyle("-fx-background-color: " + paint.panelBg() + "; -fx-background-radius: 8;");

        Node iconNode = createStrokeWorkflowIcon(paint.iconSvg(), colorHex, 20);

        VBox rightBox = new VBox(0);
        rightBox.setAlignment(Pos.TOP_LEFT);
        rightBox.setFillWidth(true);
        rightBox.setMaxWidth(Double.MAX_VALUE);
        rightBox.setMaxHeight(Region.USE_PREF_SIZE);

        HBox headRow = new HBox(4);
        headRow.setAlignment(Pos.TOP_LEFT);
        Label headPrefix = new Label("Current Child Status:");
        headPrefix.setStyle("-fx-font-weight: 700; -fx-text-fill: " + colorHex + "; -fx-font-size: 12px;");
        Label headVal = new Label(effective);
        headVal.setStyle("-fx-font-weight: 700; -fx-text-fill: " + colorHex + "; -fx-font-size: 12px;");
        headRow.getChildren().addAll(headPrefix, headVal);

        /* FlowPane wraps long child trails gracefully so they are fully visible */
        javafx.scene.layout.FlowPane breadcrumbPane = new javafx.scene.layout.FlowPane();
        breadcrumbPane.setHgap(4);
        breadcrumbPane.setVgap(4);
        breadcrumbPane.setPrefWrapLength(320);
        breadcrumbPane.setAlignment(Pos.CENTER_LEFT);
        breadcrumbPane.setMaxWidth(Double.MAX_VALUE);
        int curIdx = JobWorkflow.indexOfChild(major, effective);
        if (curIdx < 0) curIdx = 0;

        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) {
                Label chev = new Label(">");
                chev.setStyle("-fx-text-fill: #AAAAAA; -fx-font-size: 10px; -fx-font-weight: 400; -fx-padding: 0 3 0 3;");
                breadcrumbPane.getChildren().add(chev);
            }
            String step = steps.get(i);
            
            Hyperlink stepLink = new Hyperlink(step);
            stepLink.getStyleClass().add("workflow-step-link");
            stepLink.setPadding(new Insets(0, 2, 0, 2));
            stepLink.setUnderline(false);
            
            if (i < curIdx) {
                stepLink.setStyle("-fx-text-fill: #888888; -fx-font-size: 10px; -fx-font-weight: 600;");
            } else if (i == curIdx) {
                stepLink.setStyle("-fx-text-fill: " + colorHex + "; -fx-font-size: 10px; -fx-font-weight: 700;");
            } else {
                stepLink.setStyle("-fx-text-fill: #888888; -fx-font-size: 10px; -fx-font-weight: 600;");
            }
            final String stepFin = step;
            stepLink.setOnAction(e -> onChildWorkflowPillClicked(job, stepFin));
            breadcrumbPane.getChildren().add(stepLink);
        }

        rightBox.getChildren().addAll(headRow, breadcrumbPane);
        VBox.setVgrow(breadcrumbPane, Priority.NEVER);
        panel.setMaxHeight(Region.USE_PREF_SIZE);
        wrap.setMaxHeight(Region.USE_PREF_SIZE);
        panel.getChildren().addAll(iconNode, rightBox);
        HBox.setHgrow(rightBox, Priority.ALWAYS);
        wrap.getChildren().add(panel);
        return wrap;
    }

    private void onChildWorkflowPillClicked(Job job, String childLabel) {
        if (job == null || childLabel == null) {
            return;
        }
        JobWorkflow.Major major = JobWorkflow.majorFromJobStatus(job.getStatus());
        if (major == JobWorkflow.Major.CANCELLED) {
            return;
        }
        if (!JobWorkflow.isValidChildForMajor(major, childLabel)) {
            return;
        }
        String canon = JobWorkflow.canonicalChildLabel(major, childLabel);
        jobService.updateJobChildStatus(job.getUuid(), canon);
        job.setChildStatus(canon);
        jobsTable.refresh();
    }

    private void applyDefaultChildForNewMajor(Job job, String newStatus) {
        if (job == null) {
            return;
        }
        JobWorkflow.Major m = JobWorkflow.majorFromJobStatus(newStatus);
        if (m == JobWorkflow.Major.CANCELLED) {
            jobService.updateJobChildStatus(job.getUuid(), null);
            job.setChildStatus(null);
            return;
        }
        String def = JobWorkflow.defaultChildForMajor(m);
        if (def != null && !def.isEmpty()) {
            jobService.updateJobChildStatus(job.getUuid(), def);
            job.setChildStatus(def);
        }
    }

    /**
     * Major stepper: dot/rail row and a label row aligned so label X matches dot centers.
     */
    private VBox buildMajorStepperSection(Job job, double contentWidth) {
        String currentStatus = job != null && job.getStatus() != null ? job.getStatus() : "";
        JobWorkflow.Major major = JobWorkflow.majorFromJobStatus(currentStatus);

        final double trackRowH = 8;

        final int dotRadius = 3;
        final int dotDiameter = 6;
        final int offsetX = 2;
        String[] stages = { "Draft", "Processing", "Completed", "Invoice" };
        final int n = stages.length;

        double trackWidth = Math.max(220, contentWidth - 8);
        Pane trackPane = new Pane();
        trackPane.getStyleClass().add("job-major-stepper");
        trackPane.setMinHeight(trackRowH);
        trackPane.setPrefHeight(trackRowH);
        trackPane.setMaxHeight(trackRowH);
        trackPane.setMinWidth(trackWidth);
        trackPane.setPrefWidth(trackWidth);
        trackPane.setMaxWidth(trackWidth);

        double step = (trackWidth - 2.0 * offsetX - 2.0 * dotRadius) / (n - 1);

        int activeIdx;
        String colorHex = majorStatusPaint(major).primary();
        switch (major) {
            case PROCESSING: activeIdx = 1; break;
            case COMPLETED: activeIdx = 2; break;
            case INVOICE: activeIdx = 3; break;
            case CANCELLED: activeIdx = 0; break;
            case DRAFT:
            default: activeIdx = 0; break;
        }

        for (int i = 0; i < n - 1; i++) {
            Region rail = new Region();
            rail.getStyleClass().add("stepper-line");
            rail.setPrefHeight(2);
            rail.setMinHeight(2);
            rail.setPrefWidth(step);
            rail.setLayoutX(offsetX + dotRadius + i * step);
            rail.setLayoutY(dotRadius - 1);

            if (major == JobWorkflow.Major.CANCELLED) {
                rail.setStyle("-fx-background-color: #EEECE8;");
            } else if (i < activeIdx) {
                rail.setStyle("-fx-background-color: " + colorHex + ";");
            } else {
                rail.setStyle("-fx-background-color: #EEECE8;");
            }
            trackPane.getChildren().add(rail);
        }

        /* Same width as trackPane; coords match dots (Pane padding does not shift children). */
        Pane labelTrackPane = new Pane();
        labelTrackPane.getStyleClass().add("job-major-stepper-label-track");
        labelTrackPane.setMinWidth(trackWidth);
        labelTrackPane.setPrefWidth(trackWidth);
        labelTrackPane.setMaxWidth(trackWidth);
        labelTrackPane.setMinHeight(15);
        labelTrackPane.setPrefHeight(15);

        final double labelSlotW = Math.min(56, Math.max(38, step + 3));

        for (int i = 0; i < n; i++) {
            double centerX = offsetX + dotRadius + i * step;

            StackPane node = new StackPane();
            node.setPrefSize(dotDiameter, dotDiameter);
            node.setLayoutX(centerX - dotRadius);
            node.setLayoutY(1);

            boolean past = major != JobWorkflow.Major.CANCELLED && i < activeIdx;
            boolean active = i == activeIdx || (major == JobWorkflow.Major.CANCELLED && i == 0);

            if (major == JobWorkflow.Major.CANCELLED) {
                node.setStyle("-fx-background-color: white; -fx-border-color: #F5222D; -fx-border-width: 2; -fx-background-radius: 50; -fx-border-radius: 50;");
            } else if (past) {
                node.setStyle("-fx-background-color: white; -fx-border-color: " + colorHex + "; -fx-border-width: 2; -fx-background-radius: 50; -fx-border-radius: 50;");
            } else if (active) {
                node.setStyle("-fx-background-color: " + colorHex + "; -fx-border-color: " + colorHex + "; -fx-border-width: 2; -fx-background-radius: 50; -fx-border-radius: 50;");
            } else {
                node.setStyle("-fx-background-color: white; -fx-border-color: #CCCCCC; -fx-border-width: 2; -fx-background-radius: 50; -fx-border-radius: 50;");
            }
            trackPane.getChildren().add(node);

            Label lbl = new Label(stages[i]);
            lbl.getStyleClass().add("stepper-label");
            if (active) {
                lbl.setStyle("-fx-text-fill: " + colorHex + "; -fx-font-weight: 700; -fx-font-size: 11px;");
            } else if (past) {
                lbl.setStyle("-fx-text-fill: " + colorHex + "; -fx-font-weight: 600; -fx-font-size: 11px;");
            } else {
                lbl.setStyle("-fx-text-fill: #666666; -fx-font-weight: 400; -fx-font-size: 11px;");
            }

            HBox lblWrapper = new HBox(lbl);
            lblWrapper.setAlignment(Pos.CENTER);
            lblWrapper.setPrefWidth(labelSlotW);
            lblWrapper.setLayoutX(centerX - labelSlotW / 2.0);
            lblWrapper.setLayoutY(0);
            labelTrackPane.getChildren().add(lblWrapper);
        }

        HBox trackRow = new HBox(0);
        trackRow.setAlignment(Pos.CENTER_LEFT);
        trackRow.setMinWidth(contentWidth);
        trackRow.setPrefWidth(contentWidth);
        trackRow.setMaxWidth(contentWidth);
        trackRow.getChildren().add(trackPane);

        HBox labelRow = new HBox(0);
        labelRow.getStyleClass().add("job-major-stepper-label-row");
        labelRow.setAlignment(Pos.TOP_LEFT);
        labelRow.setMinWidth(contentWidth);
        labelRow.setPrefWidth(contentWidth);
        labelRow.setMaxWidth(contentWidth);
        labelRow.getChildren().add(labelTrackPane);

        VBox wrap = new VBox(0);
        wrap.getChildren().addAll(trackRow, labelRow);
        return wrap;
    }

    private javafx.scene.Node createIcon(String pathStr, String color) {
        javafx.scene.shape.SVGPath svg = new javafx.scene.shape.SVGPath();
        svg.setContent(pathStr);
        svg.setFill(javafx.scene.paint.Color.web(color));
        javafx.scene.layout.StackPane pane = new javafx.scene.layout.StackPane(svg);
        pane.setAlignment(Pos.CENTER);
        pane.setMinWidth(12); pane.setMaxWidth(12);
        pane.setMinHeight(12); pane.setMaxHeight(12);
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
        if (job.hasUuid()) {
            Job fresh = jobService.getJobByUuid(job.getUuid());
            if (fresh != null) {
                job = fresh;
            }
        }

        service.JobItemService jis = new service.JobItemService();
        String jobUuid = job.getUuid();
        List<model.JobItem> items = (jobUuid != null && !jobUuid.isBlank())
                ? jis.getJobItems(jobUuid)
                : List.of();
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
        Label idLbl = new Label(job.getJobNo().toUpperCase());
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

        // Items table (do not use jobs-table-premium: its CSS hides text via graphic-only cells)
        TableView<model.JobItem> table = new TableView<>();
        table.getStyleClass().add("job-details-popup-table");
        table.setPrefHeight(250);
        table.setMinHeight(120);

        TableColumn<model.JobItem, String> typeCol = new TableColumn<>("Type");
        typeCol.setPrefWidth(100);
        typeCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue() != null && cd.getValue().getType() != null ? cd.getValue().getType() : ""));
        typeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String type, boolean empty) {
                super.updateItem(type, empty);
                setText(empty || type == null || type.isBlank() ? null : type);
                setGraphic(null);
            }
        });

        TableColumn<model.JobItem, String> descCol = new TableColumn<>("Description");
        descCol.setPrefWidth(280);
        descCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue() != null && cd.getValue().getDescription() != null ? cd.getValue().getDescription() : ""));
        descCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String desc, boolean empty) {
                super.updateItem(desc, empty);
                setText(empty || desc == null || desc.isBlank() ? null : desc);
                setGraphic(null);
            }
        });

        TableColumn<model.JobItem, String> amtCol = new TableColumn<>("Amount");
        amtCol.setPrefWidth(100);
        amtCol.getStyleClass().add("job-details-amt-cell");
        amtCol.setCellValueFactory(cd -> {
            if (cd.getValue() == null) {
                return new javafx.beans.property.SimpleStringProperty("");
            }
            return new javafx.beans.property.SimpleStringProperty(
                    String.format("%.2f", cd.getValue().getAmount()));
        });
        amtCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String amtText, boolean empty) {
                super.updateItem(amtText, empty);
                if (empty || amtText == null || amtText.isBlank()) {
                    setText(null);
                } else {
                    setText("\u20B9 " + amtText);
                }
                setGraphic(null);
                if (!getStyleClass().contains("job-details-amt-cell")) {
                    getStyleClass().add("job-details-amt-cell");
                }
            }
        });

        table.getColumns().addAll(typeCol, descCol, amtCol);
        table.setItems(FXCollections.observableArrayList(items));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        if (items.isEmpty()) {
            table.setPlaceholder(new Label("No line items for this job."));
        }

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
    public void refresh() {
        if (clientComboBox != null && clientComboBox.getValue() != null) {
            loadJobsBySelectedClient();
        } else {
            loadAllJobs();
        }
    }

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
        List<Job> jobs = jobService.getFullJobsByClientId(selectedClient.getClientUuid());
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
                if (job.getClientId() == null || !job.getClientId().equals(selectedClient.getClientUuid())) {
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
        prevBtn.getStyleClass().add("page-btn");
        prevBtn.setDisable(currentPage == 1);
        prevBtn.setOnAction(e -> { currentPage--; updatePagination(); });
        paginationButtonContainer.getChildren().add(prevBtn);

        // Logic to show a max of 3 pages (sliding window) - matching view_client.css style
        int startPage = Math.max(1, currentPage - 1);
        int endPage = Math.min(totalPages, startPage + 2);
        if (endPage - startPage < 2 && startPage > 1) {
            startPage = Math.max(1, endPage - 2);
        }

        for (int i = startPage; i <= endPage; i++) {
            final int p = i;
            Button pBtn = new Button(String.valueOf(i));
            pBtn.getStyleClass().add("page-btn");
            if (i == currentPage) pBtn.getStyleClass().add("page-btn-active");
            pBtn.setOnAction(e -> { currentPage = p; updatePagination(); });
            paginationButtonContainer.getChildren().add(pBtn);
        }

        // Next Button
        Button nextBtn = new Button(">");
        nextBtn.getStyleClass().add("page-btn");
        nextBtn.setDisable(currentPage == totalPages);
        nextBtn.setOnAction(e -> { currentPage++; updatePagination(); });
        paginationButtonContainer.getChildren().add(nextBtn);

        // Jump to page label
        Label jumpLabel = new Label("Go to:");
        jumpLabel.setStyle("-fx-text-fill: #A79F99; -fx-font-size: 11px; -fx-padding: 0 0 0 10;");
        paginationButtonContainer.getChildren().add(jumpLabel);

        if (goToPageField != null) {
            if (!paginationButtonContainer.getChildren().contains(goToPageField)) {
                paginationButtonContainer.getChildren().add(goToPageField);
            }
        }
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

    private void autoSelectClient(String clientUuid) {

        if (clientUuid == null || clientUuid.isBlank()) return;

        Optional<Client> match = clientComboBox.getItems()
                .stream()
                .filter(c -> clientUuid.equals(c.getClientUuid()))
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




    private void openInvoiceDetailsByUuid(String invoiceUuid) {
        if (invoiceUuid == null || invoiceUuid.isBlank()) {
            return;
        }
        try (java.sql.Connection con = utils.DBConnection.getConnection();
                java.sql.PreparedStatement ps = con.prepareStatement(
                        "SELECT uuid FROM invoice_master WHERE uuid = ?")) {
            ps.setString(1, invoiceUuid.trim());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    openInvoiceDetails(rs.getString("uuid"));
                }
            }
        } catch (Exception e) {
            toast("Could not open invoice: " + e.getMessage());
        }
    }

    private void openInvoiceDetails(String invoiceUuid) {
        if (invoiceUuid == null || invoiceUuid.isBlank()) return;
        
        new Thread(() -> {
            InvoiceMaster inv = invoiceService.getInvoiceById(invoiceUuid);
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
            
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(file);
            } else {
                toast("Desktop not supported. Cannot open image ❌");
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            toast("Failed to open image ❌");
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
