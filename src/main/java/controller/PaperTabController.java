package controller;

import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import model.Job;
import model.JobItem;
import model.Paper;
import repository.PaperItemRepository;
import service.JobItemService;

public class PaperTabController {

    @FXML private TableView<Paper> paperTable;
    @FXML private TableColumn<Paper, Integer> qtyCol;
    @FXML private TableColumn<Paper, String> sizeCol;
    @FXML private TableColumn<Paper, String> gsmCol;
    @FXML private TableColumn<Paper, Double> amountCol;

    private final JobItemService jobItemService = new JobItemService();
    private final PaperItemRepository paperRepo = new PaperItemRepository();

    @FXML
    private void initialize() {
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        sizeCol.setCellValueFactory(new PropertyValueFactory<>("size"));
        gsmCol.setCellValueFactory(new PropertyValueFactory<>("gsm"));
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
    }

    public void loadForJob(Job job) {
        paperTable.getItems().clear();

        for (JobItem ji : jobItemService.getJobItems(job.getId())) {
            if ("PAPER".equalsIgnoreCase(ji.getType())) {
                Paper p = paperRepo.findByJobItemId(ji.getId());
                if (p != null) {
                    paperTable.getItems().add(p);
                }
            }
        }
    }
}
