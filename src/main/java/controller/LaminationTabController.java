package controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.control.TableRow;
import model.Job;
import model.JobItem;
import model.Lamination;
import repository.LaminationItemRepository;
import service.JobItemService;

import java.util.List;

public class LaminationTabController {

    /* ================= TABLE ================= */

    @FXML private TableView<Lamination> laminationTable;
    @FXML private TableColumn<Lamination, Integer> qtyCol;
    @FXML private TableColumn<Lamination, String> unitCol;
    @FXML private TableColumn<Lamination, String> typeCol;
    @FXML private TableColumn<Lamination, String> sideCol;
    @FXML private TableColumn<Lamination, String> sizeCol;
    @FXML private TableColumn<Lamination, Double> amountCol;
    @FXML private TableColumn<Lamination, String> notesCol;


    /* ================= EDITOR ================= */

    @FXML private TextField qtyField;
    @FXML private ComboBox<String> unitField;
    @FXML private ComboBox<String> typeField;
    @FXML private ComboBox<String> sideField;
    @FXML private ComboBox<String> sizeField;
    @FXML private TextArea notesField;
    @FXML private TextField amountField;
    @FXML private Button addUpdateBtn;

    /* ================= FOOTER ================= */

    @FXML private Label itemCount;
    @FXML private Label totalAmount;

    /* ================= STATE ================= */

    private Job currentJob;
    private Lamination selectedItem;
    private Lamination lastSelectedItem;

    private final JobItemService jobItemService = new JobItemService();
    private final LaminationItemRepository laminationRepo =
            new LaminationItemRepository();

    /* ================= INIT ================= */

    @FXML
    private void initialize() {

        /* ===== Table mapping ===== */
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        unitCol.setCellValueFactory(new PropertyValueFactory<>("unit"));
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        sideCol.setCellValueFactory(new PropertyValueFactory<>("side"));
        sizeCol.setCellValueFactory(new PropertyValueFactory<>("size"));
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));

        laminationTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        laminationTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        /* ===== ComboBox values (controller based) ===== */
        unitField.getItems().setAll(
                "Sheet", "Thousand", "Sq. Ft", "Sq. Meter", "Roll", "Packet"
        );

        typeField.getItems().setAll(
                "Gloss", "Matte", "Velvet",
                "Thermal Gloss", "Thermal Matte",
                "UV Gloss", "UV Matte",
                "Drip Off", "Scuff Free Matte"
        );

        sideField.getItems().setAll("Single Side", "Both Side");

        sizeField.getItems().setAll(
                "12x18", "13x19", "17x22",
                "19x25", "20x30", "23x36",
                "25x36", "28x40"
        );

        /* ===== Selection → Editor ===== */
        laminationTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldItem, newItem) -> {
                    if (newItem != null) {
                        loadToEditor(newItem);
                        addUpdateBtn.setText("Update Lamination");
                    } else {
                        clearEditor();
                        addUpdateBtn.setText("Add Lamination");
                    }
                });

        /* ===== Click same row again → deselect ===== */
        laminationTable.setRowFactory(tv -> {
            TableRow<Lamination> row = new TableRow<>();

            row.setOnMouseClicked(e -> {
                if (row.isEmpty()) {
                    laminationTable.getSelectionModel().clearSelection();
                    lastSelectedItem = null;
                    return;
                }

                Lamination clicked = row.getItem();

                if (clicked != null && clicked.equals(lastSelectedItem)) {
                    laminationTable.getSelectionModel().clearSelection();
                    lastSelectedItem = null;
                } else {
                    lastSelectedItem = clicked;
                }
            });

            return row;
        });

        laminationTable.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                laminationTable.getSelectionModel().clearSelection();
            }
        });
    }

    /* ================= LOAD JOB ================= */

    public void loadForJob(Job job) {
        this.currentJob = job;
        loadLaminationItems();
        clearEditor();
    }

    private void loadLaminationItems() {

        laminationTable.getItems().clear();

        int count = 0;
        double total = 0;

        for (JobItem ji : jobItemService.getJobItems(currentJob.getId())) {
            if ("LAMINATION".equalsIgnoreCase(ji.getType())) {
                Lamination l = laminationRepo.findByJobItemId(ji.getId());
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

    /* ================= ADD / UPDATE ================= */

    @FXML
    private void handleAddOrUpdate() {

        if (currentJob == null) return;

        boolean isNew = false;

        if (selectedItem == null) {
            selectedItem = new Lamination();
            isNew = true;
        }

        selectedItem.setQty(parseInt(qtyField.getText()));
        selectedItem.setUnit(unitField.getValue());
        selectedItem.setType(typeField.getValue());
        selectedItem.setSide(sideField.getValue());
        selectedItem.setSize(sizeField.getValue());
        selectedItem.setNotes(notesField.getText());
        selectedItem.setAmount(parseDouble(amountField.getText()));

        // TODO: persist
        // laminationRepo.saveOrUpdate(currentJob, selectedItem);

        if (isNew) {
            laminationTable.getItems().add(selectedItem);
        }

        laminationTable.getSelectionModel().clearSelection();
        laminationTable.refresh();
        loadLaminationItems();
        clearEditor();
        addUpdateBtn.setText("Add Lamination");
    }

    /* ================= HELPERS ================= */

    private void loadToEditor(Lamination l) {
        selectedItem = l;
        qtyField.setText(String.valueOf(l.getQty()));
        unitField.setValue(l.getUnit());
        typeField.setValue(l.getType());
        sideField.setValue(l.getSide());
        sizeField.setValue(l.getSize());
        notesField.setText(l.getNotes());
        amountField.setText(String.valueOf(l.getAmount()));
    }

    private void clearEditor() {
        selectedItem = null;
        qtyField.clear();
        unitField.setValue(null);
        typeField.setValue(null);
        sideField.setValue(null);
        sizeField.setValue(null);
        notesField.clear();
        amountField.clear();
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
