package controller;

import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import model.Job;
import model.JobItem;
import model.Lamination;
import repository.LaminationItemRepository;
import service.JobItemService;

public class LaminationTabController {

    /* =========================
       TABLE
       ========================= */

    @FXML private TableView<Lamination> laminationTable;

    @FXML private TableColumn<Lamination, Integer> qtyCol;
    @FXML private TableColumn<Lamination, String> unitCol;
    @FXML private TableColumn<Lamination, String> typeCol;
    @FXML private TableColumn<Lamination, String> sideCol;
    @FXML private TableColumn<Lamination, String> sizeCol;
    @FXML private TableColumn<Lamination, Double> amountCol;

    @FXML private Label itemCount;
    @FXML private Label totalAmount;

    private final LaminationItemRepository laminationRepo =
            new LaminationItemRepository();

    private final JobItemService jobItemService =
            new JobItemService();

    private Job currentJob;

    /* =========================
       INITIALIZE
       ========================= */

    @FXML
    private void initialize() {

        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        unitCol.setCellValueFactory(new PropertyValueFactory<>("unit"));
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        sideCol.setCellValueFactory(new PropertyValueFactory<>("side"));
        sizeCol.setCellValueFactory(new PropertyValueFactory<>("size"));
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));

        laminationTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY
        );
    }

    /* =========================
       ENTRY FROM EditJobController
       ========================= */

    public void loadForJob(Job job) {
        this.currentJob = job;
        loadLaminationItems();
    }

    /* =========================
       LOAD DATA
       ========================= */

    private void loadLaminationItems() {

        if (currentJob == null) return;

        laminationTable.getItems().clear();

        List<JobItem> jobItems =
                jobItemService.getJobItems(currentJob.getId());

        int count = 0;
        double total = 0;

        for (JobItem ji : jobItems) {

            if ("LAMINATION".equalsIgnoreCase(ji.getType())) {

                Lamination l =
                        laminationRepo.findByJobItemId(ji.getId());

                if (l != null) {
                    laminationTable.getItems().add(l);
                    count++;
                    total += l.getAmount();
                }
            }
        }

        itemCount.setText(String.valueOf(count));
        totalAmount.setText("₹" + String.format("%.2f", total));
    }
}
