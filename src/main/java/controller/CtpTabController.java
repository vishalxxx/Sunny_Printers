package controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import model.CtpPlate;
import model.Job;
import model.JobItem;
import repository.CtpItemRepository;
import service.JobItemService;

import java.util.List;

public class CtpTabController {

    /* =====================
       TABLE (LEFT)
       ===================== */

    @FXML private TableView<CtpPlate> ctpTable;

    @FXML private TableColumn<CtpPlate, String> supplierCol;
    @FXML private TableColumn<CtpPlate, Integer> qtyCol;
    @FXML private TableColumn<CtpPlate, String> plateSizeCol;
    @FXML private TableColumn<CtpPlate, String> gaugeCol;
    @FXML private TableColumn<CtpPlate, String> backingCol;
    @FXML private TableColumn<CtpPlate, String> colorCol;
    @FXML private TableColumn<CtpPlate, Double> amountCol;
    @FXML private TableColumn<CtpPlate, String> notesCol;


    /* =====================
       EDITOR (RIGHT)
       ===================== */

    @FXML private ComboBox<String> supplierField;
    @FXML private TextField qtyField;
    @FXML private ComboBox<String> plateSizeField;
    @FXML private ComboBox<String> gaugeField;
    @FXML private ComboBox<String> backingField;
    @FXML private ComboBox<String> colorField;
    @FXML private TextArea notesField;
    @FXML private TextField amountField;
    @FXML private Button addUpdateBtn;
    @FXML private VBox rightPane;

    /* =====================
       STATE
       ===================== */

    private Job currentJob;
    private CtpPlate selectedItem;
    private CtpPlate lastSelectedItem;

    private final JobItemService jobItemService = new JobItemService();
    private final CtpItemRepository ctpRepo = new CtpItemRepository();

    /* =====================
       INITIALIZE
       ===================== */

    @FXML
    private void initialize() {

        /* ===== TABLE SETUP ===== */
        supplierCol.setCellValueFactory(new PropertyValueFactory<>("supplierName"));
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        plateSizeCol.setCellValueFactory(new PropertyValueFactory<>("plateSize"));
        gaugeCol.setCellValueFactory(new PropertyValueFactory<>("gauge"));
        backingCol.setCellValueFactory(new PropertyValueFactory<>("backing"));
        colorCol.setCellValueFactory(new PropertyValueFactory<>("color"));
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));

        ctpTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        ctpTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        /* ===== DROPDOWNS ===== */
        plateSizeField.getItems().addAll(
                "19x25", "20x30", "23x36", "25x36", "28x40", "30x40"
        );

        gaugeField.getItems().addAll(
                "0.15 mm", "0.20 mm", "0.25 mm", "0.30 mm"
        );

        backingField.getItems().addAll(
                "Positive", "Negative", "Thermal"
        );

        colorField.getItems().addAll(
                "1 Color", "2 Color", "4 Color",
                "4 + 1", "4 + 4", "Multicolor", "Spot Color"
        );

        /* ===== SELECTION → EDITOR ===== */
        ctpTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldItem, newItem) -> {
                    if (newItem != null) {
                        loadToEditor(newItem);
                        addUpdateBtn.setText("Update CTP Plate");
                    } else {
                        clearEditor();
                        addUpdateBtn.setText("Add CTP Plate");
                    }
                });

        /* ===== CLICK SAME ROW → DESELECT ===== */
        ctpTable.setRowFactory(tv -> {
            TableRow<CtpPlate> row = new TableRow<>();

            row.setOnMouseClicked(e -> {

                if (row.isEmpty()) {
                    ctpTable.getSelectionModel().clearSelection();
                    lastSelectedItem = null;
                    return;
                }

                CtpPlate clicked = row.getItem();

                if (clicked != null && clicked.equals(lastSelectedItem)) {
                    ctpTable.getSelectionModel().clearSelection();
                    lastSelectedItem = null;
                } else {
                    lastSelectedItem = clicked;
                }
            });

            return row;
        });

        /* ===== ESC KEY → CLEAR ===== */
        ctpTable.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                ctpTable.getSelectionModel().clearSelection();
            }
        });
    }

    /* =====================
       LOAD JOB DATA
       ===================== */

    public void loadForJob(Job job) {
        this.currentJob = job;

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

        clearEditor();
        addUpdateBtn.setText("Add CTP Plate");

        System.out.println("🧩 CTP loaded: " + ctpTable.getItems().size());
    }

    /* =====================
       LOAD SELECTED ROW
       ===================== */

    private void loadToEditor(CtpPlate p) {
        selectedItem = p;

        supplierField.setValue(p.getSupplierName());
        qtyField.setText(String.valueOf(p.getQty()));
        plateSizeField.setValue(p.getPlateSize());
        gaugeField.setValue(p.getGauge());
        backingField.setValue(p.getBacking());
        colorField.setValue(p.getColor());
        notesField.setText(p.getNotes());
        amountField.setText(String.valueOf(p.getAmount()));
    }

    /* =====================
       CLEAR EDITOR
       ===================== */

    private void clearEditor() {
        selectedItem = null;

        supplierField.setValue(null);
        qtyField.clear();
        plateSizeField.setValue(null);
        gaugeField.setValue(null);
        backingField.setValue(null);
        colorField.setValue(null);
        notesField.clear();
        amountField.clear();
    }
}
