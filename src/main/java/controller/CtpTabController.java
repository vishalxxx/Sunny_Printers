package controller;

import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import model.CtpPlate;
import model.Job;
import model.JobItem;
import repository.CtpItemRepository;
import service.JobItemService;

public class CtpTabController {

    /* =====================
       UI
       ===================== */

    @FXML private TableView<CtpPlate> ctpTable;

    @FXML private TableColumn<CtpPlate, String> supplierCol;
    @FXML private TableColumn<CtpPlate, Integer> qtyCol;
    @FXML private TableColumn<CtpPlate, String> plateSizeCol;
    @FXML private TableColumn<CtpPlate, String> gaugeCol;
    @FXML private TableColumn<CtpPlate, String> backingCol;
    @FXML private TableColumn<CtpPlate, String> colorCol;
    @FXML private TableColumn<CtpPlate, Double> amountCol;

    /* =====================
       DATA
       ===================== */

    private final JobItemService jobItemService = new JobItemService();
    private final CtpItemRepository ctpRepo = new CtpItemRepository();

    /* =====================
       INIT
       ===================== */

    @FXML
    private void initialize() {

        supplierCol.setCellValueFactory(new PropertyValueFactory<>("supplierName"));
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        plateSizeCol.setCellValueFactory(new PropertyValueFactory<>("plateSize"));
        gaugeCol.setCellValueFactory(new PropertyValueFactory<>("gauge"));
        backingCol.setCellValueFactory(new PropertyValueFactory<>("backing"));
        colorCol.setCellValueFactory(new PropertyValueFactory<>("color"));
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));

        ctpTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    /* =====================
       LOAD DATA
       ===================== */

    public void loadForJob(Job job) {

        ctpTable.getItems().clear();

        List<JobItem> items = jobItemService.getJobItems(job.getId());

        for (JobItem ji : items) {
            if ("CTP".equalsIgnoreCase(ji.getType())) {

                CtpPlate plate = ctpRepo.findByJobItemId(ji.getId());
                if (plate != null) {
                    ctpTable.getItems().add(plate);
                }
            }
        }
    }
}
