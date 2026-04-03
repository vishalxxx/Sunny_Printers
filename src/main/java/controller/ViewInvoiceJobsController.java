package controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.application.Platform;

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

    @FXML private TableColumn<Job, Integer> idCol;
    @FXML private TableColumn<Job, String> jobNoCol;
    @FXML private TableColumn<Job, String> titleCol;
    @FXML private TableColumn<Job, LocalDate> dateCol;
    @FXML private TableColumn<Job, String> statusCol;
    @FXML private TableColumn<Job, String> remarksCol;
    @FXML private TableColumn<Job, String> createdAtCol;
    @FXML private TableColumn<Job, String> updatedAtCol;

    private final ObservableList<Job> tableData = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        setupTable();
        setupClientComboBox();
        setupInvoiceComboBox();

        loadClients();

        clientComboBox.valueProperty().addListener((obs, oldV, newV) -> onClientSelected(newV));
        invoiceComboBox.valueProperty().addListener((obs, oldV, newV) -> onInvoiceSelected(newV));
    }

    private void setupTable() {
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        jobNoCol.setCellValueFactory(new PropertyValueFactory<>("jobNo"));
        titleCol.setCellValueFactory(new PropertyValueFactory<>("jobTitle"));
        dateCol.setCellValueFactory(new PropertyValueFactory<>("jobDate"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setCellFactory(col -> new TableCell<>() {
            private final ComboBox<String> statusCombo = new ComboBox<>(
                FXCollections.observableArrayList(
                    "Created", "In Progress", "On Hold", "Completed", "Cancelled"
                )
            );

            {
                statusCombo.setMaxWidth(Double.MAX_VALUE);
                statusCombo.setVisibleRowCount(5);
                statusCombo.getStyleClass().add("combo-box-base");
                this.setStyle("-fx-padding: 2 5; -fx-alignment: CENTER;");
                statusCombo.setOnAction(e -> {
                    Job job = getTableRow().getItem();
                    if (job != null) {
                        String newStatus = statusCombo.getValue();
                        if (newStatus != null && !newStatus.equals(job.getStatus())) {
                            job.setStatus(newStatus);
                            jobService.updateJobStatus(job.getId(), newStatus);
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
                    statusCombo.setOnAction(null);
                    statusCombo.setValue(item);
                    statusCombo.setOnAction(e -> {
                        Job job = getTableRow().getItem();
                        if (job != null) {
                            String newStatus = statusCombo.getValue();
                            if (newStatus != null && !newStatus.equals(job.getStatus())) {
                                job.setStatus(newStatus);
                                jobService.updateJobStatus(job.getId(), newStatus);
                            }
                        }
                    });
                    setGraphic(statusCombo);
                }
            }
        });
        remarksCol.setCellValueFactory(new PropertyValueFactory<>("remarks"));
        createdAtCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        updatedAtCol.setCellValueFactory(new PropertyValueFactory<>("updatedAt"));

        jobsTable.setItems(tableData);
        jobsTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        jobsTable.setFixedCellSize(42);
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
        tableData.clear();
        if (invoice == null) {
            resultLabel.setText("Showing 0 jobs");
            return;
        }


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

    private void toast(String message) {
        Stage stage = (Stage) ((Node) clientComboBox).getScene().getWindow();
        Platform.runLater(() -> Toast.show(stage, message));
    }
}
