package controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import model.Binding;
import model.Job;
import model.JobItem;
import repository.BindingItemRepository;
import service.JobItemService;

import java.util.List;

public class BindingTabController {

    /* ================= TABLE ================= */

    @FXML private TableView<Binding> bindingTable;
    @FXML private TableColumn<Binding, String> processCol;
    @FXML private TableColumn<Binding, Integer> qtyCol;
    @FXML private TableColumn<Binding, Double> rateCol;
    @FXML private TableColumn<Binding, String> notesCol;

    @FXML private TableColumn<Binding, Double> amountCol;

    /* ================= EDITOR ================= */

    @FXML private ComboBox<String> processField;
    @FXML private TextField qtyField;
    @FXML private TextField rateField;
    @FXML private TextArea notesField;
    @FXML private TextField amountField;
    @FXML private Button addUpdateBtn;
    @FXML private VBox rightPane;

    /* ================= FOOTER ================= */

    @FXML private Label itemCountLabel;
    @FXML private Label totalAmountLabel;

    /* ================= STATE ================= */

    private Job currentJob;
    private Binding selectedItem;
    private Binding lastSelectedItem;

    private final JobItemService jobItemService = new JobItemService();
    private final BindingItemRepository bindingRepo = new BindingItemRepository();

    /* ================= INIT ================= */

    @FXML
    private void initialize() {

        /* ===== Table setup ===== */
        processCol.setCellValueFactory(new PropertyValueFactory<>("process"));
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        rateCol.setCellValueFactory(new PropertyValueFactory<>("rate"));
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));

        bindingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        bindingTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        /* ===== ComboBox data (CONTROLLER BASED) ===== */
        processField.getItems().setAll(
                "Center Pin",
                "Side Pin",
                "Perfect Binding",
                "Hardcase Binding",
                "Spiral",
                "Wire-O",
                "Half Binding",
                "Full Binding",
                "Wiro Calendar",
                "Spiral Calendar",
                "Thermal Binding",
                "Gum / Glue Binding",
                "Pad Binding",
                "Folding",
                "Creasing",
                "Perforation",
                "Punching",
                "Die Cutting"
        );

        /* ===== Selection → Editor ===== */
        bindingTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldItem, newItem) -> {
                    if (newItem != null) {
                        loadToEditor(newItem);
                        addUpdateBtn.setText("Update Binding");
                    } else {
                        clearEditor();
                        addUpdateBtn.setText("Add Binding");
                    }
                });

        /* ===== Click same row to deselect ===== */
        bindingTable.setRowFactory(tv -> {
            TableRow<Binding> row = new TableRow<>();

            row.setOnMouseClicked(e -> {
                if (row.isEmpty()) {
                    bindingTable.getSelectionModel().clearSelection();
                    lastSelectedItem = null;
                    return;
                }

                Binding clicked = row.getItem();

                if (clicked != null && clicked.equals(lastSelectedItem)) {
                    bindingTable.getSelectionModel().clearSelection();
                    lastSelectedItem = null;
                } else {
                    lastSelectedItem = clicked;
                }
            });

            return row;
        });

        /* ===== ESC clears selection ===== */
        bindingTable.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                bindingTable.getSelectionModel().clearSelection();
            }
        });
    }

    /* ================= LOAD JOB ================= */

    public void loadForJob(Job job) {
        this.currentJob = job;
        bindingTable.getItems().clear();

        for (JobItem ji : jobItemService.getJobItems(job.getId())) {
            if ("BINDING".equalsIgnoreCase(ji.getType())) {
                Binding b = bindingRepo.findByJobItemId(ji.getId());
                if (b != null) bindingTable.getItems().add(b);
            }
        }

        updateFooter();
        clearEditor();
    }

    /* ================= EDITOR ================= */

    private void loadToEditor(Binding b) {
        selectedItem = b;
        processField.setValue(b.getProcess());
        qtyField.setText(String.valueOf(b.getQty()));
        rateField.setText(String.valueOf(b.getRate()));
        notesField.setText(b.getNotes());
        amountField.setText(String.valueOf(b.getAmount()));
    }

    @FXML
    private void handleAddOrUpdate() {

        if (currentJob == null) return;

        boolean isNew = false;

        if (selectedItem == null) {
            selectedItem = new Binding();
            isNew = true;
        }

        selectedItem.setProcess(processField.getValue());
        selectedItem.setQty(parseInt(qtyField.getText()));
        selectedItem.setRate(parseDouble(rateField.getText()));
        selectedItem.setNotes(notesField.getText());
        selectedItem.setAmount(parseDouble(amountField.getText()));

        // TODO persist (same pattern as Printing)
        // bindingRepo.saveOrUpdate(currentJob, selectedItem);

        if (isNew) {
            bindingTable.getItems().add(selectedItem);
        }

        bindingTable.getSelectionModel().clearSelection();
        bindingTable.refresh();
        updateFooter();

        clearEditor();
        addUpdateBtn.setText("Add Binding");
    }

    /* ================= HELPERS ================= */

    private void clearEditor() {
        selectedItem = null;
        processField.setValue(null);
        qtyField.clear();
        rateField.clear();
        notesField.clear();
        amountField.clear();
    }

    private void updateFooter() {
        int count = bindingTable.getItems().size();
        double total = bindingTable.getItems()
                .stream()
                .mapToDouble(Binding::getAmount)
                .sum();

        itemCountLabel.setText(String.valueOf(count));
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
