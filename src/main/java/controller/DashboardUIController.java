package controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import model.DashboardJobDTO;
import repository.InvoiceMasterRepository;
import repository.JobRepository;
import utils.DBConnection;

import java.net.URL;
import java.sql.Connection;
import java.util.Map;
import java.util.ResourceBundle;

public class DashboardUIController implements Initializable {

    @FXML
    private AreaChart<String, Number> revenueChart;
    @FXML
    private PieChart jobDistributionChart;
    @FXML
    private Label lblActiveJobsCount;

    @FXML
    private TableView<DashboardJobDTO> recentJobsTable;
    @FXML
    private TableColumn<DashboardJobDTO, String> colOrderClient;
    @FXML
    private TableColumn<DashboardJobDTO, String> colProjectDetails;
    @FXML
    private TableColumn<DashboardJobDTO, String> colReceived;
    @FXML
    private TableColumn<DashboardJobDTO, String> colDueDate;
    @FXML
    private TableColumn<DashboardJobDTO, String> colValuation;
    @FXML
    private TableColumn<DashboardJobDTO, String> colWorkflow;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadChartData();
        loadPieChartData();
        loadTableData();
    }

    private void loadTableData() {
        if (recentJobsTable == null)
            return;

        colOrderClient.setCellValueFactory(new PropertyValueFactory<>("orderClient"));
        colProjectDetails.setCellValueFactory(new PropertyValueFactory<>("projectDetails"));
        colReceived.setCellValueFactory(new PropertyValueFactory<>("received"));
        colDueDate.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        colValuation.setCellValueFactory(new PropertyValueFactory<>("valuation"));
        colWorkflow.setCellValueFactory(new PropertyValueFactory<>("workflow"));

        JobRepository repo = new JobRepository();
        ObservableList<DashboardJobDTO> data = FXCollections.observableArrayList(repo.getRecentDashboardJobs(10));
        recentJobsTable.setItems(data);
    }

    private void loadPieChartData() {
        if (jobDistributionChart == null)
            return;

        JobRepository repo = new JobRepository();
        Map<String, Integer> counts = repo.getJobDistributionCounts();

        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
        int totalActive = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            pieChartData.add(new PieChart.Data(entry.getKey(), entry.getValue()));
            totalActive += entry.getValue();
        }

        jobDistributionChart.setData(pieChartData);

        if (lblActiveJobsCount != null) {
            lblActiveJobsCount.setText(String.valueOf(totalActive));
        }
    }

    private void loadChartData() {
        if (revenueChart == null)
            return;

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Invoices Generated");

        try (Connection con = DBConnection.getConnection()) {
            InvoiceMasterRepository repo = new InvoiceMasterRepository();
            // Fetch last 6 months of invoice counts
            Map<String, Integer> counts = repo.getInvoiceMonthlyCounts(con, 6);

            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                // E.g. "2026-02" -> count
                series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
            }

            revenueChart.getData().clear();
            revenueChart.getData().add(series);

        } catch (Exception e) {
            System.err.println("Failed to load invoice monthly counts for chart: " + e.getMessage());
        }
    }

    @FXML
    private void loadClientLedger(MouseEvent event) {
        if (MainController.getInstance() != null) {
            MainController.getInstance().showLedgerSubmenu(event);
            MainController.getInstance().loadClientLedger(event);
        }
    }

    @FXML
    private void loadViewClients(MouseEvent event) {
        if (MainController.getInstance() != null) {
            MainController.getInstance().showClientsSubmenu(event);
            MainController.getInstance().loadViewClients();
        }
    }

    @FXML
    private void loadViewJob(MouseEvent event) {
        if (MainController.getInstance() != null) {
            MainController.getInstance().showJobsSubmenu(event);
            MainController.getInstance().loadViewJob(event);
        }
    }

    @FXML
    private void loadInvoiceGenration(MouseEvent event) {
        if (MainController.getInstance() != null) {
            MainController.getInstance().showBillingSubmenu(event);
            MainController.getInstance().loadInvoiceGenration();
        }
    }

    @FXML
    private void loadRecordPayment(MouseEvent event) {
        if (MainController.getInstance() != null) {
            MainController.getInstance().showPaymentSubmenu(event);
            MainController.getInstance().loadRecordPayment(event);
        }
    }

    @FXML
    private void loadOutstanding(MouseEvent event) {
        // Will implement later, placeholder for now
        System.out.println("Outstanding tile clicked");
    }

    @FXML
    private void loadExpenses(MouseEvent event) {
        // Will implement later, placeholder for now
        System.out.println("Expenses tile clicked");
    }

    @FXML
    private void loadReports(MouseEvent event) {
        // Will implement later, placeholder for now
        System.out.println("Reports tile clicked");
    }

    @FXML
    private void loadUserSettings(MouseEvent event) {
        if (MainController.getInstance() != null) {
            MainController.getInstance().showSettingSubmenu(event);
            MainController.getInstance().loadUserSettings(event);
        }
    }
}
