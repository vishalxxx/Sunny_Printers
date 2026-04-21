package controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.binding.Bindings;
import javafx.scene.layout.VBox;
import java.time.LocalDate;
import model.Client;
import service.ClientService;
import javafx.util.StringConverter;
import javafx.collections.transformation.FilteredList;

public class GenerateInvoiceController {

    @FXML private Button breadcrumbBackBtn;
    @FXML private TableView<JobItem> jobsTable;
    @FXML private TableColumn<JobItem, Boolean> selectCol;
    @FXML private TableColumn<JobItem, String> jobNameCol;
    @FXML private TableColumn<JobItem, String> dateCol;
    @FXML private TableColumn<JobItem, String> qtyCol;
    @FXML private TableColumn<JobItem, String> rateCol;
    @FXML private TableColumn<JobItem, String> totalCol;
    @FXML private CheckBox selectAllCheckbox;

    @FXML private TextField jobSearchField;
    @FXML private ComboBox<Client> clientComboBox;
    @FXML private TextField invoiceNoField;
    @FXML private ComboBox<String> termsCombo;
    @FXML private DatePicker invoiceDatePicker;
    @FXML private DatePicker dueDatePicker;
    @FXML private TextArea notesArea;

    @FXML private Label itemCountLabel;
    @FXML private Label totalAmountLabel;

    @FXML
    public void initialize() {
        setupTable();
        setupFields();
        setupClientCombo();
        setupJobSearch();
    }

    private final ClientService clientService = new ClientService();
    private final service.JobService jobService = new service.JobService();
    private final ObservableList<Client> masterClients = FXCollections.observableArrayList();
    private final ObservableList<JobItem> masterJobs = FXCollections.observableArrayList();
    private FilteredList<Client> filteredClients;
    private FilteredList<JobItem> filteredJobs;

    private void setupClientCombo() {
        if (clientComboBox == null) return;

        filteredClients = new FilteredList<>(masterClients, c -> true);
        clientComboBox.setItems(filteredClients);
        clientComboBox.setEditable(false);

        // Selection listener to load jobs
        clientComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                loadJobsForClient(newV);
            } else {
                masterJobs.clear();
                updateSummary();
            }
        });

        // Premium Cell Factory
        clientComboBox.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Client c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) setText(null);
                else setText(c.getBusinessName() + " (" + c.getClientName() + ")");
            }
        });

        // Button Cell (Visible when closed)
        clientComboBox.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Client c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) setText(null);
                else setText(c.getBusinessName());
            }
        });

        // Load data
        try { masterClients.setAll(clientService.getAllClients()); } catch (Exception e) {}
    }

    private void setupTable() {
        // Select column with checkboxes
        selectCol.setCellValueFactory(data -> data.getValue().selectedProperty());
        selectCol.setCellFactory(column -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();
            {
                checkBox.setOnAction(e -> {
                    JobItem item = getTableView().getItems().get(getIndex());
                    item.setSelected(checkBox.isSelected());
                    updateSummary();
                });
            }
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    checkBox.setSelected(item);
                    setGraphic(checkBox);
                }
            }
        });

        jobNameCol.setCellValueFactory(data -> data.getValue().nameProperty());
        
        filteredJobs = new FilteredList<>(masterJobs, j -> true);
        jobsTable.setItems(filteredJobs);
        jobNameCol.setCellFactory(column -> new TableCell<>() {
            private final Label title = new Label();
            private final Label subtitle = new Label();
            private final VBox container = new VBox(title, subtitle);
            {
                title.getStyleClass().add("job-name-main");
                subtitle.getStyleClass().add("job-name-sub");
                container.setSpacing(4);
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    JobItem job = getTableView().getItems().get(getIndex());
                    title.setText(item);
                    subtitle.setText(job.getSubtitle());
                    setGraphic(container);
                }
            }
        });

        dateCol.setCellValueFactory(data -> data.getValue().dateProperty());
        qtyCol.setCellValueFactory(data -> data.getValue().qtyProperty());
        rateCol.setCellValueFactory(data -> data.getValue().rateProperty());
        
        totalCol.setCellValueFactory(data -> data.getValue().totalProperty());
        totalCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setStyle("-fx-font-weight: 800; -fx-alignment: CENTER_RIGHT;");
                }
            }
        });

        selectAllCheckbox.setOnAction(e -> {
            boolean selected = selectAllCheckbox.isSelected();
            jobsTable.getItems().forEach(item -> item.setSelected(selected));
            jobsTable.refresh();
            updateSummary();
        });

        // ✅ Prevent empty filler rows: size table to content
        jobsTable.setFixedCellSize(62); // fits title + subtitle comfortably
        final double headerHeight = 34;
        var tableHeight = Bindings.size(jobsTable.getItems())
                .multiply(jobsTable.getFixedCellSize())
                .add(headerHeight + 1);
        jobsTable.prefHeightProperty().bind(tableHeight);
        jobsTable.minHeightProperty().bind(tableHeight);
        jobsTable.maxHeightProperty().bind(tableHeight);
    }

    private void setupFields() {
        termsCombo.setItems(FXCollections.observableArrayList("Net 30", "Net 15", "Due on Receipt", "Custom"));
        termsCombo.setValue("Net 30");
        invoiceDatePicker.setValue(LocalDate.now());
        dueDatePicker.setValue(LocalDate.now().plusDays(30));
    }

    private void setupJobSearch() {
        jobSearchField.textProperty().addListener((obs, oldV, newV) -> {
            String q = newV == null ? "" : newV.toLowerCase().trim();
            filteredJobs.setPredicate(j -> {
                if (q.isEmpty()) return true;
                return j.nameProperty().get().toLowerCase().contains(q)
                    || j.getSubtitle().toLowerCase().contains(q);
            });
        });
    }

    private void loadJobsForClient(Client client) {
        if (client == null) return;
        
        masterJobs.clear();
        try {
            System.out.println("DEBUG: Loading jobs for client ID: " + client.getId());
            List<model.Job> jobs = jobService.getCompletedJobsByClient(client.getId());
            System.out.println("DEBUG: Found " + jobs.size() + " jobs.");
            
            utils.NavigationManager.getInstance().showToast("Found " + jobs.size() + " jobs for this client.");
            
            if (jobs.isEmpty()) {
                // Diagnostic: check if ANY jobs exist for this client
                List<model.Job> allJobs = jobService.getFullJobsByClientId(client.getId());
                System.out.println("DEBUG: Diagnostic - Total jobs (any status) for this client: " + allJobs.size());
                for (model.Job aj : allJobs) {
                    System.out.println("DEBUG: Job " + aj.getJobNo() + " has status: " + aj.getStatus());
                }
            }
            
            for (model.Job j : jobs) {
                String dateStr = j.getJobDate() != null ? j.getJobDate().toString() : "-";
                String totalStr = j.getJobTotal() != null ? String.format("₹ %,.2f", j.getJobTotal()) : "₹ 0.00";
                
                masterJobs.add(new JobItem(
                    j.getJobTitle(),
                    j.getJobNo(), 
                    dateStr,
                    "1", // Default Qty to 1 as placeholder
                    totalStr, // Use total as rate for now
                    totalStr
                ));
            }
        } catch (Exception e) {
            System.err.println("DEBUG: Failed to load jobs: " + e.getMessage());
            e.printStackTrace();
        }
        updateSummary();
    }

    private void updateSummary() {
        long count = jobsTable.getItems().stream().filter(JobItem::isSelected).count();
        double total = jobsTable.getItems().stream()
                .filter(JobItem::isSelected)
                .mapToDouble(item -> {
                    String cleanTotal = item.getTotal().replace("₹", "").replace(",", "").trim();
                    try { return Double.parseDouble(cleanTotal); } catch (Exception e) { return 0.0; }
                })
                .sum();

        itemCountLabel.setText(count + (count == 1 ? " job" : " jobs"));
        totalAmountLabel.setText(String.format("₹ %,.2f", total));
    }

    @FXML
    private void handleBack() {
        MainController.getInstance().handleBack(null);
    }

    @FXML
    private void handlePreview() {
        System.out.println("Previewing Invoice...");
    }

    @FXML
    private void handleGenerate() {
        System.out.println("Generating Invoice...");
    }

    // Helper class for TableView
    public static class JobItem {
        private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
        private final SimpleStringProperty name;
        private final SimpleStringProperty subtitle;
        private final SimpleStringProperty date;
        private final SimpleStringProperty qty;
        private final SimpleStringProperty rate;
        private final SimpleStringProperty total;
 
        public JobItem(String name, String subtitle, String date, String qty, String rate, String total) {
            this.name = new SimpleStringProperty(name);
            this.subtitle = new SimpleStringProperty(subtitle);
            this.date = new SimpleStringProperty(date);
            this.qty = new SimpleStringProperty(qty);
            this.rate = new SimpleStringProperty(rate);
            this.total = new SimpleStringProperty(total);
        }
 
        public SimpleBooleanProperty selectedProperty() { return selected; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean val) { selected.set(val); }
 
        public SimpleStringProperty nameProperty() { return name; }
        public String getSubtitle() { return subtitle.get(); }
        public SimpleStringProperty dateProperty() { return date; }
        public SimpleStringProperty qtyProperty() { return qty; }
        public SimpleStringProperty rateProperty() { return rate; }
        public SimpleStringProperty totalProperty() { return total; }
        public String getTotal() { return total.get(); }
    }
}
