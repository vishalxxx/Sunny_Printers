package controller;

import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import model.Job;
import model.JobItem;
import model.Printing;
import repository.PrintingItemRepository;
import service.JobItemService;

import java.util.List;

public class PrintingTabController {

    @FXML private TableView<Printing> printingTable;

    @FXML private TableColumn<Printing, Integer> qtyCol;
    @FXML private TableColumn<Printing, String> unitsCol;
    @FXML private TableColumn<Printing, String> setsCol;
    @FXML private TableColumn<Printing, String> colorCol;
    @FXML private TableColumn<Printing, String> sideCol;
    @FXML private TableColumn<Printing, Boolean> ctpCol;
    @FXML private TableColumn<Printing, Double> amountCol;

    private final PrintingItemRepository printingRepo = new PrintingItemRepository();
    private final JobItemService jobItemService = new JobItemService();

    @FXML
    private void initialize() {

        // Hard guard: fail fast if wiring is broken
        if (qtyCol == null) {
            throw new IllegalStateException("Printing FXML not wired correctly");
        }

        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        unitsCol.setCellValueFactory(new PropertyValueFactory<>("units"));
        setsCol.setCellValueFactory(new PropertyValueFactory<>("sets"));
        colorCol.setCellValueFactory(new PropertyValueFactory<>("color"));
        sideCol.setCellValueFactory(new PropertyValueFactory<>("side"));
        ctpCol.setCellValueFactory(new PropertyValueFactory<>("withCtp"));
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));

        printingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    public void loadForJob(Job job) {

        printingTable.getItems().clear();

        List<JobItem> items = jobItemService.getJobItems(job.getId());

        for (JobItem ji : items) {
            if ("PRINTING".equalsIgnoreCase(ji.getType())) {
                Printing p = printingRepo.findByJobItemId(ji.getId());
                if (p != null) {
                    printingTable.getItems().add(p);
                }
            }
        }

        System.out.println("🖨 Printing loaded: " + printingTable.getItems().size());
    }
}
