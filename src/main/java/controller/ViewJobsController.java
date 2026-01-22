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

    @FXML private TableColumn<Job, Void> actionCol;

    @FXML
    private void initialize() {

        setupClientComboBox();
        setupTableColumns();
        setupActionsColumn();

        loadClients();   // fills clientComboBox + map
        loadAllJobs();   // default list
        setupAutoPopupDatePicker(fromDatePicker);
        setupAutoPopupDatePicker(toDatePicker);
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
        remarksCol.setCellValueFactory(new PropertyValueFactory<>("remarks"));
        createdAtCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        updatedAtCol.setCellValueFactory(new PropertyValueFactory<>("updatedAt"));

        jobsTable.setItems(masterJobs);

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

        actionCol.setCellFactory(col -> new TableCell<>() {

            private final Button editBtn = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final HBox box = new HBox(8, editBtn, deleteBtn);

            {
                editBtn.getStyleClass().add("edit-button");
                deleteBtn.getStyleClass().add("delete-button");

                box.setStyle("-fx-alignment: CENTER;");

                editBtn.setOnAction(e -> {
                    Job job = getTableView().getItems().get(getIndex());
                    onEditJob(job);
                });

                deleteBtn.setOnAction(e -> {
                    Job job = getTableView().getItems().get(getIndex());
                    onDeleteJob(job);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
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

            return matchesKeyword && matchesDate;
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

        loadAllJobs();
    }

    // =========================================================
    // ✅ EDIT / DELETE
    // =========================================================
    private void onEditJob(Job job) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ht.fxml"));
            Parent view = loader.load();

            AddJobController ctrl = loader.getController();
            ctrl.openForEdit(job.getId());

            MainController.getInstance().setCenterView(view);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void onDeleteJob(Job job) {

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Job");
        alert.setHeaderText("Are you sure you want to delete this job?");
        alert.setContentText("Job No: " + job.getJobNo());

        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            System.out.println("🗑 Delete job: " + job.getJobNo());
        }
    }
    
    private void toast(String message) {
		Stage stage = (Stage) ((Node) clientComboBox).getScene().getWindow();
		Toast.show(stage, message);
	}
}
