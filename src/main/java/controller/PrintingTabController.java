package controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import model.Job;
import model.JobItem;
import model.Printing;
import repository.PrintingItemRepository;
import service.JobItemService;

import java.util.List;

public class PrintingTabController {

    /* =====================================================
       TABLE (LEFT)
       ===================================================== */

    @FXML private TableView<Printing> printingTable;

    @FXML private TableColumn<Printing, Integer> qtyCol;
    @FXML private TableColumn<Printing, String> unitsCol;
    @FXML private TableColumn<Printing, String> setsCol;
    @FXML private TableColumn<Printing, String> colorCol;
    @FXML private TableColumn<Printing, String> sideCol;
    @FXML private TableColumn<Printing, Boolean> ctpCol;
    @FXML private TableColumn<Printing, Double> amountCol;
    @FXML private TableColumn<Printing, String> notesCol;



    /* =====================================================
       EDITOR (RIGHT)
       ===================================================== */

    @FXML private TextField qtyField;
    @FXML private ComboBox<String> unitsField;
    @FXML private TextField setsField;
    @FXML private ComboBox<String> colorField;
    @FXML private ComboBox<String> sideField;
    @FXML private CheckBox ctpCheckBox;
    @FXML private TextArea notesField;
    @FXML private TextField amountField;
    @FXML private Button addUpdateBtn;
    @FXML private VBox rightPane;

    private Printing lastSelectedItem = null;


    /* =====================================================
       FOOTER (OPTIONAL)
       ===================================================== */

    @FXML private Label itemCountLabel;
    @FXML private Label totalAmountLabel;

    /* =====================================================
       STATE
       ===================================================== */

    private Job currentJob;
    private Printing selectedItem;

    private final PrintingItemRepository printingRepo = new PrintingItemRepository();
    private final JobItemService jobItemService = new JobItemService();

    /* =====================================================
       INITIALIZE
       ===================================================== */

    @FXML
    private void initialize() {

        /* ===== TABLE SETUP ===== */
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        unitsCol.setCellValueFactory(new PropertyValueFactory<>("units"));
        setsCol.setCellValueFactory(new PropertyValueFactory<>("sets"));
        colorCol.setCellValueFactory(new PropertyValueFactory<>("color"));
        sideCol.setCellValueFactory(new PropertyValueFactory<>("side"));
        ctpCol.setCellValueFactory(new PropertyValueFactory<>("withCtp"));
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));

        printingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        /* ===== EDITOR DROPDOWNS ===== */
        unitsField.getItems().addAll("Sheets", "Rim", "Bundle", "Thousand", "Lakh", "Roll", "Pckt");
        colorField.getItems().addAll("1 Color", "2 Color", "4 Color", "4 + 1 Color", "4 + 4 Color", "Multicolor", "Spot Color"  );
        sideField.getItems().addAll("F/B", "W/T", "S/S");

        /* ===== TABLE SELECTION → EDITOR ===== */
        printingTable.getSelectionModel()
        .selectedItemProperty()
        .addListener((obs, oldItem, newItem) -> {

            if (newItem != null) {
                loadToEditor(newItem);
                addUpdateBtn.setText("Update Printing");
            } else {
                clearEditor();
                addUpdateBtn.setText("Add Printing");
            }
        });

       


        /* ===== Listen to click on same Row and empty the right pane ===== */
        
        printingTable.setRowFactory(tv -> {
            TableRow<Printing> row = new TableRow<>();

            row.setOnMouseClicked(event -> {

                if (row.isEmpty()) {
                    printingTable.getSelectionModel().clearSelection();
                    lastSelectedItem = null;
                    return;
                }

                Printing clickedItem = row.getItem();

                if (clickedItem != null && clickedItem.equals(lastSelectedItem)) {
                    // 🔁 second click on same row → deselect
                    printingTable.getSelectionModel().clearSelection();
                    lastSelectedItem = null;
                } else {
                    // first click → select normally
                    lastSelectedItem = clickedItem;
                }
            });

            return row;
        });


        printingTable.getSelectionModel()
        .setSelectionMode(SelectionMode.SINGLE);

        printingTable.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                printingTable.getSelectionModel().clearSelection();
            }
        });

   /* =====END OF Listen to click on same Row and empty the right pane ===== */


    }

    /* =====================================================
       LOAD DATA FOR JOB
       ===================================================== */

    @FXML
    private void handleAddOrUpdate() {
        saveOrUpdate();
    }

    
    public void loadForJob(Job job) {
        this.currentJob = job;

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

        updateFooter();
        clearEditor();

        System.out.println("🖨 Printing loaded: " + printingTable.getItems().size());
    }

    /* =====================================================
       LOAD SELECTED ITEM INTO EDITOR
       ===================================================== */

    private void loadToEditor(Printing p) {
        selectedItem = p;

        qtyField.setText(String.valueOf(p.getQty()));
        unitsField.setValue(p.getUnits());
        setsField.setText(p.getSets());
        colorField.setValue(p.getColor());
        sideField.setValue(p.getSide());
        ctpCheckBox.setSelected(p.isWithCtp());
        notesField.setText(p.getNotes());
        amountField.setText(String.valueOf(p.getAmount()));
    }

    /* =====================================================
       SAVE / UPDATE
       ===================================================== */

    private void saveOrUpdate() {

        if (currentJob == null) return;

        boolean isNew = false;

        if (selectedItem == null) {
            selectedItem = new Printing();
            isNew = true;
        }

        selectedItem.setQty(parseInt(qtyField.getText()));
        selectedItem.setUnits(unitsField.getValue());
        selectedItem.setSets(setsField.getText());
        selectedItem.setColor(colorField.getValue());
        selectedItem.setSide(sideField.getValue());
        selectedItem.setWithCtp(ctpCheckBox.isSelected());
        selectedItem.setNotes(notesField.getText());
        selectedItem.setAmount(parseDouble(amountField.getText()));

        // TODO: persist
        // printingRepo.saveOrUpdate(currentJob, selectedItem);

        if (isNew) {
            printingTable.getItems().add(selectedItem);
        }

        printingTable.getSelectionModel().clearSelection();
        printingTable.refresh();
        updateFooter();

        clearEditor();
        addUpdateBtn.setText("Add Printing");
    }


    /* =====================================================
       HELPERS
       ===================================================== */

    private void clearEditor() {
        selectedItem = null;
        qtyField.clear();
        unitsField.setValue(null);
        setsField.clear();
        colorField.setValue(null);
        sideField.setValue(null);
        ctpCheckBox.setSelected(false);
        notesField.clear();
        amountField.clear();
    }

    private void updateFooter() {
        int count = printingTable.getItems().size();
        double total = printingTable.getItems()
                .stream()
                .mapToDouble(Printing::getAmount)
                .sum();

        if (itemCountLabel != null)
            itemCountLabel.setText(String.valueOf(count));

        if (totalAmountLabel != null)
            totalAmountLabel.setText("₹ " + String.format("%.2f", total));
    }

    private int parseInt(String v) {
        try { return Integer.parseInt(v); }
        catch (Exception e) { return 0; }
    }

    private double parseDouble(String v) {
        try { return Double.parseDouble(v); }
        catch (Exception e) { return 0.0; }
    }
}
