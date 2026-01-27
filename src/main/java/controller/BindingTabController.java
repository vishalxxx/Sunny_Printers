package controller;

import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import model.Binding;
import model.Job;
import model.JobItem;
import repository.BindingItemRepository;
import service.JobItemService;

public class BindingTabController {

    /* =========================
       UI
       ========================= */
    @FXML private TableView<Binding> bindingTable;
    @FXML private TableColumn<Binding, String> processCol;
    @FXML private TableColumn<Binding, Integer> qtyCol;
    @FXML private TableColumn<Binding, Double> rateCol;
    @FXML private TableColumn<Binding, Double> amountCol;

    @FXML private Label itemCountLabel;
    @FXML private Label totalAmountLabel;

    /* =========================
       BACKEND
       ========================= */
    private final BindingItemRepository bindingRepo = new BindingItemRepository();
    private final JobItemService jobItemService = new JobItemService();

    /* =========================
       INIT
       ========================= */
    @FXML
    private void initialize() {

        processCol.setCellValueFactory(new PropertyValueFactory<>("process"));
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        rateCol.setCellValueFactory(new PropertyValueFactory<>("rate"));
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));

        bindingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    /* =========================
       LOAD DATA
       ========================= */
    public void loadForJob(Job job) {

        bindingTable.getItems().clear();

        List<JobItem> items = jobItemService.getJobItems(job.getId());

        double total = 0;
        int count = 0;

        for (JobItem ji : items) {
            if ("BINDING".equalsIgnoreCase(ji.getType())) {

                Binding b = bindingRepo.findByJobItemId(ji.getId());
                if (b != null) {
                    bindingTable.getItems().add(b);
                    total += b.getAmount();
                    count++;
                }
            }
        }

        itemCountLabel.setText(String.valueOf(count));
        totalAmountLabel.setText("₹" + String.format("%.2f", total));
    }
}
