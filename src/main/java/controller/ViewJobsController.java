package controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import javafx.stage.Stage;
import model.Client;
import model.Job;
import model.InvoiceMaster;
import service.ClientService;
import service.JobService;
import service.InvoiceMasterService;
import utils.Toast;

public class ViewJobsController {

    private final ClientService clientService = new ClientService();
    private final JobService jobService = new JobService();

    private final ObservableList<Job> masterJobs = FXCollections.observableArrayList();

    // ✅ clientId -> clientName map (for fast lookup)
    private final Map<Integer, String> clientNameMap = new HashMap<>();

    // ===================== TOP SEARCH =====================
    @FXML private TextField searchField;

    // ===================== FILTERS =====================
    @FXML private ComboBox<Client> clientComboBox;
    @FXML private ComboBox<String> statusFilterComboBox;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;

    // ===================== RESULT LABEL =====================
    @FXML private Label resultLabel;

    // ===================== TABLE =====================
    @FXML private TableView<Job> jobsTable;

    @FXML private TableColumn<Job, Integer> idCol;
    @FXML private TableColumn<Job, String> jobNoCol;
    @FXML private TableColumn<Job, Integer> clientIdCol;
    @FXML private TableColumn<Job, String> clientNameCol;
    @FXML private TableColumn<Job, String> titleCol;
    @FXML private TableColumn<Job, LocalDate> dateCol;
    @FXML private TableColumn<Job, Double> jobTotalCol;
    @FXML private TableColumn<Job, String> statusCol;
    @FXML private TableColumn<Job, String> invoiceNoCol;
    @FXML private TableColumn<Job, String> remarksCol;
    @FXML private TableColumn<Job, String> createdAtCol;
    @FXML private TableColumn<Job, String> updatedAtCol;

    @FXML private Button btnView, btnEdit, btnStart, btnComplete, btnInvoice, btnCancel;

    @FXML
    private void initialize() {

        setupClientComboBox();
        setupTableColumns();

        loadClients();   // fills clientComboBox + map
        loadAllJobs();   // default list
        setupAutoPopupDatePicker(fromDatePicker);
        setupAutoPopupDatePicker(toDatePicker);
        
        statusFilterComboBox.getItems().addAll("All", "Draft", "Created", "In Progress", "Completed", "Invoice Drafted", "Invoiced", "Cancelled");
        statusFilterComboBox.getSelectionModel().selectFirst();
        statusFilterComboBox.valueProperty().addListener((obs, oldV, newV) -> {
            applyFilters();
        });

        // ✅ filter triggers
        clientComboBox.valueProperty().addListener(this::onClientChanged);
     
        //Search using Enter KEy
        searchField.setOnAction(e -> onSearchClicked());
        
        fromDatePicker.valueProperty().addListener((obs, oldV, newV) -> {
            applyFilters();
        });

        toDatePicker.valueProperty().addListener((obs, oldV, newV) -> {
            applyFilters();
        });

        setupActionBarStyles();

        // ✅ Button selection logic
        jobsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            updateActionBar(newSel);
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
    private void updateActionBar(Job job) {
        if (job == null) {
            btnView.setDisable(true);
            btnEdit.setDisable(true);
            btnStart.setDisable(true);
            btnComplete.setDisable(true);
            btnInvoice.setDisable(true);
            btnCancel.setDisable(true);
            return;
        }

        String status = job.getStatus() != null ? job.getStatus().toLowerCase() : "";
        boolean isCancelled = status.startsWith("cancel");
        boolean isCompleted = status.equals("completed");
        boolean isInProgress = status.equals("in progress");
        boolean isInvoiced = job.getInvoiceId() != null && job.getInvoiceId() > 0;
        boolean isDrafted = status.equals("invoice drafted");
        
        btnView.setDisable(false);

        if (isInvoiced || status.equals("invoiced") || isDrafted) {
            btnStart.setDisable(true);
            btnComplete.setDisable(true);
            btnInvoice.setDisable(true);
            btnCancel.setDisable(isDrafted ? false : true); // Allow cancel if only drafted? User didn't specify, but safer to block mostly
            String invStatus = (job.getInvoiceStatus() != null) ? job.getInvoiceStatus().toLowerCase() : "";
            boolean canEditInvoiced = invStatus.equals("draft") || invStatus.equals("final");
            btnEdit.setDisable(!canEditInvoiced);
        } else {
            btnStart.setDisable(isCancelled || isCompleted || isInProgress);
            
            btnComplete.setDisable(!isInProgress);
            btnInvoice.setDisable(!isCompleted);
            
            if (isCompleted) {
                btnEdit.setDisable(false);
                btnCancel.setDisable(false);
            } else {
                btnEdit.setDisable(isCancelled);
                btnCancel.setDisable(isCancelled);
            }
        }
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
        Job selected = jobsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            jobService.updateJobStatus(selected.getId(), "In Progress");
            selected.setStatus("In Progress");
            jobsTable.refresh();
            updateActionBar(selected);
        }
    }

    @FXML
    private void handleCompleteAction() {
        Job selected = jobsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            jobService.updateJobStatus(selected.getId(), "Completed");
            selected.setStatus("Completed");
            jobsTable.refresh();
            updateActionBar(selected);
        }
    }

    @FXML
    private void handleInvoiceAction() {
        Job selected = jobsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            handleInvoicedRequest(selected);
        }
    }

    @FXML
    private void handleCancelAction() {
        Job selected = jobsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            jobService.updateJobStatus(selected.getId(), "Cancelled");
            selected.setStatus("Cancelled");
            jobsTable.refresh();
            updateActionBar(selected);
        }
    }
    
    private void handleInvoicedRequest(Job job) {
        if (job.getClientId() == null) {
            utils.Toast.show((Stage) btnInvoice.getScene().getWindow(), "❌ Job has no client associated.");
            return;
        }
        MainController.getInstance().loadInvoiceWithJob(job.getClientId(), job.getId());
    }

    private void setupActionBarStyles() {
        String baseStyle = "-fx-font-size: 13px; -fx-padding: 8 16; -fx-cursor: hand; -fx-font-weight: bold; -fx-background-radius: 5px;";
        
        btnView.setStyle(baseStyle + " -fx-background-color: #00BCD4; -fx-text-fill: white;");
        btnEdit.setStyle(baseStyle + " -fx-background-color: #FF9800; -fx-text-fill: white;");
        btnStart.setStyle(baseStyle + " -fx-background-color: #2196F3; -fx-text-fill: white;");
        btnComplete.setStyle(baseStyle + " -fx-background-color: #4CAF50; -fx-text-fill: white;");
        btnInvoice.setStyle(baseStyle + " -fx-background-color: #673AB7; -fx-text-fill: white;");
        btnCancel.setStyle(baseStyle + " -fx-background-color: #F44336; -fx-text-fill: white;");

        btnView.setGraphic(createIcon("M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z"));
        btnEdit.setGraphic(createIcon("M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"));
        btnStart.setGraphic(createIcon("M8 5v14l11-7z"));
        btnComplete.setGraphic(createIcon("M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"));
        btnInvoice.setGraphic(createIcon("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"));
        btnCancel.setGraphic(createIcon("M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"));
    }

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

        clientComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Client item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("Select Client...");
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

    private void loadClients() {

        List<Client> clients = clientService.getAllClients();
        clientComboBox.getItems().setAll(clients);

        // ✅ Fill map: clientId -> name
        clientNameMap.clear();
        for (Client c : clients) {
            clientNameMap.put(c.getId(), c.getBusinessName());
        }
    }

    // =========================================================
    // ✅ TABLE SETUP
    // =========================================================
    private void setupTableColumns() {

        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        jobNoCol.setCellValueFactory(new PropertyValueFactory<>("jobNo"));
        clientIdCol.setCellValueFactory(new PropertyValueFactory<>("clientId"));

        // ✅ Client name column comes from map
        clientNameCol.setCellValueFactory(cellData -> {
            Job job = cellData.getValue();

            String cname = "";
            if (job.getClientId() != null) {
                cname = clientNameMap.getOrDefault(job.getClientId(), "Unknown");
            }

            return new javafx.beans.property.SimpleStringProperty(cname);
        });

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
        invoiceNoCol.setCellValueFactory(new PropertyValueFactory<>("invoiceNo"));
        
        statusCol.setCellFactory(col -> new TableCell<>() {
            private final javafx.scene.shape.Circle circle = new javafx.scene.shape.Circle(5);
            private final javafx.scene.control.Label textLabel = new javafx.scene.control.Label();
            private final HBox box = new HBox(6, circle, textLabel);
            private final javafx.scene.effect.DropShadow glow = new javafx.scene.effect.DropShadow();

            {
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                glow.setRadius(5);
                glow.setSpread(0.4);
                circle.setEffect(glow);
                
                // Ensure text changes to white when the row is selected
                textLabel.textFillProperty().bind(textFillProperty());
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    textLabel.setText(item);
                    javafx.scene.paint.Color color;
                    switch (item.toLowerCase()) {
                        case "completed": color = javafx.scene.paint.Color.web("#4CAF50"); break; // Green
                        case "in progress": color = javafx.scene.paint.Color.web("#2196F3"); break; // Blue
                        case "created": color = javafx.scene.paint.Color.web("#FF9800"); break; // Orange
                        case "cancelled":
                        case "cancel": color = javafx.scene.paint.Color.web("#F44336"); break; // Red
                        case "invoice drafted": color = javafx.scene.paint.Color.web("#9C27B0"); break; // Purple
                        case "invoiced": color = javafx.scene.paint.Color.web("#673AB7"); break; // Deep Purple
                        case "draft": color = javafx.scene.paint.Color.GRAY; break;
                        default: color = javafx.scene.paint.Color.LIGHTGRAY; break;
                    }
                    circle.setFill(color);
                    glow.setColor(color);
                    setGraphic(box);
                }
            }
        });
        
        remarksCol.setCellValueFactory(new PropertyValueFactory<>("remarks"));
        
        // Handle invoice_no '-' check and link
        invoiceNoCol.setCellFactory(col -> new TableCell<>() {
            private final Hyperlink link = new Hyperlink();

            {
                link.setStyle("-fx-text-fill: #1E88E5; -fx-underline: true; -fx-border-color: transparent;");
                link.setOnAction(e -> {
                    Job job = getTableRow().getItem();
                    if (job != null && job.getInvoiceId() != null) {
                        try {
                            InvoiceMasterService ims = new InvoiceMasterService();
                            InvoiceMaster inv = ims.getInvoiceById(job.getInvoiceId());
                            if (inv != null) {
                                  ViewInvoicesController.pendingSearchInvoiceNo = inv.getInvoiceNo();
                                  
                                  // Use MainController to switch tabs properly
                                  MainController.getInstance().switchToInvoices();
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            toast("Failed to open invoice");
                        }
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.trim().isEmpty()) {
                    setGraphic(null);
                    setText("-");
                } else {
                    link.setText(item);
                    setGraphic(link);
                    setText(null);
                }
            }
        });

        createdAtCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        updatedAtCol.setCellValueFactory(new PropertyValueFactory<>("updatedAt"));
        
        TableColumn<Job, String> imageCol = new TableColumn<>("Job Image");
        imageCol.setCellValueFactory(new PropertyValueFactory<>("imagePath"));
        imageCol.setCellFactory(col -> new TableCell<>() {
            private final javafx.scene.control.Hyperlink link = new javafx.scene.control.Hyperlink("View Image");
            {
                link.setStyle("-fx-text-fill: #1E88E5; -fx-underline: true; -fx-border-color: transparent;");
                link.setOnAction(e -> {
                    link.setVisited(false);
                    String path = getItem();
                    if (path != null && !path.trim().isEmpty()) {
                        try {
                            java.awt.Desktop.getDesktop().open(new java.io.File(path).getAbsoluteFile());
                        } catch (Exception ex) {
                            System.err.println("Failed to open image: " + ex.getMessage());
                        }
                    }
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.trim().isEmpty()) {
                    setGraphic(null);
                } else {
                    setGraphic(link);
                }
            }
        });
        
        int totalIdx = jobsTable.getColumns().indexOf(jobTotalCol);
        if (totalIdx != -1) {
            jobsTable.getColumns().add(totalIdx + 1, imageCol);
        } else {
            jobsTable.getColumns().add(imageCol);
        }

        jobsTable.setItems(masterJobs);
        jobsTable.setEditable(true);

        jobsTable.setRowFactory(tv -> {
            TableRow<Job> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    Job job = row.getItem();
                    showJobDetails(job);
                }
            });
            return row;
        });

        // ✅ horizontal scroll support
        jobsTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        jobsTable.setFixedCellSize(42);
    }

    private void showJobDetails(Job job) {
        service.JobItemService jis = new service.JobItemService();
        java.util.List<model.JobItem> items = jis.getJobItems(job.getId());

        String clientName = "Unknown";
        if (job.getClientId() != null) {
            clientName = clientNameMap.getOrDefault(job.getClientId(), "Unknown Client");
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

        table.getColumns().addAll(descCol, amtCol);
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
                // optional toast or alert
                System.out.println("Future dates not allowed ❌");
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

    // =========================================================
    // ✅ LOAD DATA
    // =========================================================
    public void loadAllJobs() {
        List<Job> jobs = jobService.getAllJobs();
        masterJobs.setAll(jobs);
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

        ObservableList<Job> filtered = masterJobs.filtered(job -> {

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

            return matchesKeyword && matchesDate && matchesStatus;
        });
        
        if (from != null && to != null && from.isAfter(to)) {
            toast("End Date must be greater than Start Date ❌");
            return;
        }

        jobsTable.setItems(filtered);
        resultLabel.setText("Showing " + filtered.size() + " jobs");
    }

    // =========================================================
    // ✅ EVENTS
    // =========================================================
    private void onClientChanged(ObservableValue<? extends Client> obs, Client oldV, Client newV) {
        loadJobsBySelectedClient();
    }

    @FXML
    private void onSearchClicked() {

        String keyword = (searchField.getText() == null) ? "" : searchField.getText().trim();

        if (keyword.isEmpty()) {
            loadJobsBySelectedClient();
            return;
        }

        // ✅ global search
        List<Job> jobs = jobService.searchJobs(keyword);
        masterJobs.setAll(jobs);

        // ✅ auto select client of first result
        if (!jobs.isEmpty() && jobs.get(0).getClientId() != null) {
            autoSelectClient(jobs.get(0).getClientId());
        }

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




    
    private void toast(String message) {
		Stage stage = (Stage) ((Node) clientComboBox).getScene().getWindow();
		Toast.show(stage, message);
	}
}
