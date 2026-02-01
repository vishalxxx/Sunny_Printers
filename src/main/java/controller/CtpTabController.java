package controller;

import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
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

public class CtpTabController {

    /* ================= TABLE ================= */

    @FXML private TableView<CtpPlate> ctpTable;

    @FXML private TableColumn<CtpPlate, String> supplierCol;
    @FXML private TableColumn<CtpPlate, Integer> qtyCol;
    @FXML private TableColumn<CtpPlate, String> plateSizeCol;
    @FXML private TableColumn<CtpPlate, String> gaugeCol;
    @FXML private TableColumn<CtpPlate, String> backingCol;
    @FXML private TableColumn<CtpPlate, String> colorCol;
    @FXML private TableColumn<CtpPlate, Double> amountCol;
    @FXML private TableColumn<CtpPlate, String> notesCol;

    @FXML private TableColumn<CtpPlate, CtpPlate> statusCol;

    /* ================= EDITOR ================= */

    @FXML private ComboBox<String> supplierField;
    @FXML private TextField qtyField;
    @FXML private ComboBox<String> plateSizeField;
    @FXML private ComboBox<String> gaugeField;
    @FXML private ComboBox<String> backingField;
    @FXML private ComboBox<String> colorField;
    @FXML private TextArea notesField;
    @FXML private TextField amountField;
    @FXML private Button addUpdateBtn;
    @FXML private Button deleteBtn;
    @FXML private VBox rightPane;

    /* ================= STATE ================= */

    private Job currentJob;
    private CtpPlate selectedItem;
    private CtpPlate lastSelectedItem;

    private CtpPlate originalSnapshot;

    private final JobItemService jobItemService = new JobItemService();
    private final CtpItemRepository ctpRepo = new CtpItemRepository();

    /* ================= INIT ================= */

    @FXML
    private void initialize() {

        supplierCol.setCellValueFactory(new PropertyValueFactory<>("supplierName"));
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        plateSizeCol.setCellValueFactory(new PropertyValueFactory<>("plateSize"));
        gaugeCol.setCellValueFactory(new PropertyValueFactory<>("gauge"));
        backingCol.setCellValueFactory(new PropertyValueFactory<>("backing"));
        colorCol.setCellValueFactory(new PropertyValueFactory<>("color"));
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));

        ctpTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        ctpTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        plateSizeField.getItems().addAll("19x25", "20x30", "23x36", "25x36", "28x40", "30x40");
        gaugeField.getItems().addAll("0.15 mm", "0.20 mm", "0.25 mm", "0.30 mm");
        backingField.getItems().addAll("Positive", "Negative", "Thermal");
        colorField.getItems().addAll(
                "1 Color", "2 Color", "4 Color",
                "4 + 1", "4 + 4", "Multicolor", "Spot Color"
        );

        /* ================= STATUS COLUMN ================= */

        statusCol.setCellValueFactory(param -> new javafx.beans.property.SimpleObjectProperty<>(param.getValue()));

        statusCol.setCellFactory(col -> new TableCell<>() {
            private final Label label = new Label();

            @Override
            protected void updateItem(CtpPlate item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                label.getStyleClass().setAll("status-pill");

                if (item.isDeleted()) {
                    label.setText("● Deleted");
                    label.getStyleClass().add("status-deleted");
                }
                else if (item.isNew()) {
                    label.setText("● New");
                    label.getStyleClass().add("status-new");
                }
                else if (item.isUpdated()) {
                    label.setText("● Updated");
                    label.getStyleClass().add("status-updated");
                }
                else {
                    setGraphic(null);
                    return;
                }

                setGraphic(label);
            }
        });

        /* ================= SELECTION ================= */

        ctpTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            clearEditor();
            if (n != null) {
                loadToEditor(n);
                addUpdateBtn.setText("Update CTP Plate");
                deleteBtn.setVisible(true);
                deleteBtn.setManaged(true);
            } else {
                addUpdateBtn.setText("Add CTP Plate");
                deleteBtn.setVisible(false);
                deleteBtn.setManaged(false);
            }
        });

        /* ================= CLICK AGAIN = DESELECT ================= */

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

        ctpTable.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                ctpTable.getSelectionModel().clearSelection();
            }
        });

        /* ================= CHANGE LISTENER ================= */

        ChangeListener<Object> changeListener = (obs, o, n) -> {
            if (selectedItem != null) {
                addUpdateBtn.setDisable(!isEditorChanged());
            }
        };

        supplierField.valueProperty().addListener(changeListener);
        qtyField.textProperty().addListener(changeListener);
        plateSizeField.valueProperty().addListener(changeListener);
        gaugeField.valueProperty().addListener(changeListener);
        backingField.valueProperty().addListener(changeListener);
        colorField.valueProperty().addListener(changeListener);
        notesField.textProperty().addListener(changeListener);
        amountField.textProperty().addListener(changeListener);
    }

    /* ================= LOAD ================= */

    public void loadForJob(Job job) {
        currentJob = job;
        ctpTable.getItems().clear();

        for (JobItem ji : jobItemService.getJobItems(job.getId())) {
            if ("CTP".equalsIgnoreCase(ji.getType())) {
                CtpPlate p = ctpRepo.findByJobItemId(ji.getId());
                if (p != null) {
                    p.setJobItemId(ji.getId());
                    p.resetFlags();
                    p.captureOriginal();
                    ctpTable.getItems().add(p);
                }
            }
        }

        clearEditor();
    }

    /* ================= ADD / UPDATE ================= */

    @FXML
    private void handleAddOrUpdate() {

        if (currentJob == null) return;

        if (selectedItem == null) {
            CtpPlate p = new CtpPlate();
            fillFromEditor(p);
            p.setNew(true);
            ctpTable.getItems().add(p);
        }
        else {
            fillFromEditor(selectedItem);

            if (!selectedItem.isNew()) {
                if (selectedItem.isDifferentFromOriginal()) {
                    selectedItem.setUpdated(true);
                } else {
                    selectedItem.setUpdated(false);
                }
            }
            ctpTable.refresh();
        }

        ctpTable.getSelectionModel().clearSelection();
        clearEditor();
        addUpdateBtn.setText("Add CTP Plate");
    }

    /* ================= DELETE ================= */

    @FXML
    private void handleDelete() {
        if (selectedItem == null) return;

        selectedItem.setDeleted(true);
        ctpTable.refresh();

        ctpTable.getSelectionModel().clearSelection();
        clearEditor();
    }

    /* ================= HELPERS ================= */

    private void fillFromEditor(CtpPlate p) {
        p.setSupplierName(supplierField.getValue());
        p.setQty(parseInt(qtyField.getText()));
        p.setPlateSize(plateSizeField.getValue());
        p.setGauge(gaugeField.getValue());
        p.setBacking(backingField.getValue());
        p.setColor(colorField.getValue());
        p.setNotes(notesField.getText());
        p.setAmount(parseDouble(amountField.getText()));
    }

    private void loadToEditor(CtpPlate p) {
        selectedItem = p;
        originalSnapshot = p.copy();

        supplierField.setValue(p.getSupplierName());
        qtyField.setText(String.valueOf(p.getQty()));
        plateSizeField.setValue(p.getPlateSize());
        gaugeField.setValue(p.getGauge());
        backingField.setValue(p.getBacking());
        colorField.setValue(p.getColor());
        notesField.setText(p.getNotes());
        amountField.setText(String.valueOf(p.getAmount()));

        addUpdateBtn.setDisable(true);
    }

    private boolean isEditorChanged() {
        if (selectedItem == null || originalSnapshot == null) return false;

        return parseInt(qtyField.getText()) != originalSnapshot.getQty()
                || !equals(supplierField.getValue(), originalSnapshot.getSupplierName())
                || !equals(plateSizeField.getValue(), originalSnapshot.getPlateSize())
                || !equals(gaugeField.getValue(), originalSnapshot.getGauge())
                || !equals(backingField.getValue(), originalSnapshot.getBacking())
                || !equals(colorField.getValue(), originalSnapshot.getColor())
                || !equals(notesField.getText(), originalSnapshot.getNotes())
                || parseDouble(amountField.getText()) != originalSnapshot.getAmount();
    }

    private void clearEditor() {
        selectedItem = null;
        originalSnapshot = null;

        supplierField.setValue(null);
        qtyField.clear();
        plateSizeField.setValue(null);
        gaugeField.setValue(null);
        backingField.setValue(null);
        colorField.setValue(null);
        notesField.clear();
        amountField.clear();

        addUpdateBtn.setDisable(false);
    }

    private boolean equals(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private int parseInt(String v) {
        try { return Integer.parseInt(v); }
        catch (Exception e) { return 0; }
    }

    private double parseDouble(String v) {
        try { return Double.parseDouble(v); }
        catch (Exception e) { return 0.0; }
    }

    public ObservableList<CtpPlate> getItems() {
        return ctpTable.getItems();
    }
}
