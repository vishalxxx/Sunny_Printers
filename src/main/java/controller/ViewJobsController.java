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
import service.ClientService;
import service.JobService;
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
    @FXML private TableColumn<Job, String> statusCol;
    @FXML private TableColumn<Job, String> remarksCol;
    @FXML private TableColumn<Job, String> createdAtCol;
    @FXML private TableColumn<Job, String> updatedAtCol;

    @FXML private TableColumn<Job, Job> actionCol;

    @FXML
    private void initialize() {

        setupClientComboBox();
        setupTableColumns();
        setupActionsColumn();

        loadClients();   // fills clientComboBox + map
        loadAllJobs();   // default list
        setupAutoPopupDatePicker(fromDatePicker);
        setupAutoPopupDatePicker(toDatePicker);
        
        statusFilterComboBox.getItems().addAll("All", "Draft", "Created", "In Progress", "Completed", "Cancelled");
        statusFilterComboBox.getSelectionModel().selectFirst();
        statusFilterComboBox.valueProperty().addListener((obs, oldV, newV) -> {
            searchField.clear();
            applyFilters();
        });

        // ✅ filter triggers
        clientComboBox.valueProperty().addListener(this::onClientChanged);
     
        //Search using Enter KEy
        searchField.setOnAction(e -> onSearchClicked());
        searchField.setOnMouseClicked(e -> {
        // ✅ switch to SEARCH mode
            clearClientAndDates();
        });
        searchField.focusedProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                clearClientAndDates();
            }
        });
        // Switched to Combo box mode
        fromDatePicker.valueProperty().addListener((obs, oldV, newV) -> {
            searchField.clear(); // switch to filter mode
            applyFilters();
        });

        toDatePicker.valueProperty().addListener((obs, oldV, newV) -> {
            searchField.clear(); // switch to filter mode
            applyFilters();
        });


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
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
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
        createdAtCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        updatedAtCol.setCellValueFactory(new PropertyValueFactory<>("updatedAt"));
        
        actionCol.setCellValueFactory(param -> new javafx.beans.property.ReadOnlyObjectWrapper<>(param.getValue()));

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
        
        int actionColIdx = jobsTable.getColumns().indexOf(actionCol);
        if (actionColIdx != -1) {
            jobsTable.getColumns().add(actionColIdx, imageCol);
        } else {
            jobsTable.getColumns().add(imageCol);
        }

        jobsTable.setItems(masterJobs);
        jobsTable.setEditable(true);

        // ✅ horizontal scroll support
        jobsTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        jobsTable.setFixedCellSize(42);
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

    // =========================================================
    // ✅ ACTION COLUMN
    // =========================================================
    private void setupActionsColumn() {

        actionCol.setMinWidth(330);
        
        actionCol.setCellFactory(col -> new TableCell<>() {

            private final Button editBtn = new Button("✏ Edit");
            private final Button startBtn = new Button("▶ Start");
            private final Button completedBtn = new Button("✔ Completed");
            private final Button cancelBtn = new Button("✖ Cancel");
            private final HBox box = new HBox(5, editBtn, startBtn, completedBtn, cancelBtn);

            {
                editBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 3 6; -fx-font-size: 11px;");
                startBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 3 6; -fx-font-size: 11px;");
                completedBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 3 6; -fx-font-size: 11px;");
                cancelBtn.setStyle("-fx-background-color: #F44336; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 3 6; -fx-font-size: 11px;");
                box.setAlignment(javafx.geometry.Pos.CENTER);

                editBtn.setOnAction(e -> {
                    Job job = getTableRow().getItem();
                    if (job != null) {
                    	openEditJobScreen(job);
                    }
                });

                startBtn.setOnAction(e -> {
                    Job job = getTableRow().getItem();
                    if (job != null) {
                        jobService.updateJobStatus(job.getId(), "In Progress");
                        job.setStatus("In Progress");
                        getTableView().refresh();
                    }
                });

                completedBtn.setOnAction(e -> {
                    Job job = getTableRow().getItem();
                    if (job != null) {
                        jobService.updateJobStatus(job.getId(), "Completed");
                        job.setStatus("Completed");
                        getTableView().refresh();
                    }
                });

                cancelBtn.setOnAction(e -> {
                    Job job = getTableRow().getItem();
                    if (job != null) {
                        jobService.updateJobStatus(job.getId(), "Cancelled");
                        job.setStatus("Cancelled");
                        getTableView().refresh();
                    }
                });
            }

            @Override
            protected void updateItem(Job job, boolean empty) {
                super.updateItem(job, empty);
                if (empty || job == null) {
                    setGraphic(null);
                } else {
                    String status = job.getStatus() != null ? job.getStatus().toLowerCase() : "";
                    boolean isCancelled = status.startsWith("cancel");
                    boolean isCompleted = status.equals("completed");
                    boolean isInProgress = status.equals("in progress");
                    
                    boolean disableAll = isCancelled || isCompleted;
                    
                    editBtn.setDisable(disableAll || isInProgress);
                    startBtn.setDisable(disableAll);
                    completedBtn.setDisable(disableAll);
                    cancelBtn.setDisable(disableAll);

                    setGraphic(box);
                }
            }
        });
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
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/edit_job.fxml")
            );

            Parent view = loader.load();

            EditJobController controller = loader.getController();

            // ✅ PASS FULL JOB OBJECT
            controller.openForEdit(job);

            MainController.getInstance().setCenterView(view);

        } catch (Exception ex) {
            ex.printStackTrace();
            toast("Failed to open Edit Job ❌");
        }
    }




    
    private void toast(String message) {
		Stage stage = (Stage) ((Node) clientComboBox).getScene().getWindow();
		Toast.show(stage, message);
	}
}
