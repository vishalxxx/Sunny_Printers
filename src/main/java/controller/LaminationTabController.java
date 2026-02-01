package controller;

import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import model.Job;
import model.JobItem;
import model.Lamination;
import repository.LaminationItemRepository;
import service.JobItemService;

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

    @FXML private TableColumn<Lamination, Lamination> statusCol;

    /* ================= EDITOR ================= */

    @FXML private TextField qtyField;
    @FXML private ComboBox<String> unitField;
    @FXML private ComboBox<String> typeField;
    @FXML private ComboBox<String> sideField;
    @FXML private ComboBox<String> sizeField;
    @FXML private TextArea notesField;
    @FXML private TextField amountField;
    @FXML private Button addUpdateBtn;
    @FXML private Button deleteBtn;

    /* ================= FOOTER ================= */

    @FXML private Label itemCount;
    @FXML private Label totalAmount;

    /* ================= STATE ================= */

    private Job currentJob;
    private Lamination selectedItem;
    private Lamination lastSelectedItem;
    private Lamination originalSnapshot;

    private final JobItemService jobItemService = new JobItemService();
    private final LaminationItemRepository repo = new LaminationItemRepository();

    /* ================= INIT ================= */

    @FXML
    private void initialize() {

        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        unitCol.setCellValueFactory(new PropertyValueFactory<>("unit"));
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        sideCol.setCellValueFactory(new PropertyValueFactory<>("side"));
        sizeCol.setCellValueFactory(new PropertyValueFactory<>("size"));
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));

        laminationTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        laminationTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        unitField.getItems().addAll("Sheet", "Thousand", "Sq. Ft", "Sq. Meter", "Roll", "Packet");
        typeField.getItems().addAll("Gloss", "Matte", "Velvet", "Thermal Gloss", "Thermal Matte", "UV Gloss", "UV Matte");
        sideField.getItems().addAll("Single Side", "Both Side");
        sizeField.getItems().addAll("12x18", "13x19", "17x22", "19x25", "20x30", "23x36", "25x36", "28x40");

        /* ===== Selection ===== */
        laminationTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            clearEditor();
            if (n != null) {
                loadToEditor(n);
                addUpdateBtn.setText("Update Lamination");
                deleteBtn.setVisible(true);
                deleteBtn.setManaged(true);
            } else {
                addUpdateBtn.setText("Add Lamination");
                deleteBtn.setVisible(false);
                deleteBtn.setManaged(false);
            }
        });

        /* ===== Row click toggle ===== */
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
            if (e.getCode() == KeyCode.ESCAPE)
                laminationTable.getSelectionModel().clearSelection();
        });

        /* ===== Status Pill ===== */
        statusCol.setCellValueFactory(p -> new javafx.beans.property.SimpleObjectProperty<>(p.getValue()));
        statusCol.setCellFactory(col -> new TableCell<>() {
            private final Label label = new Label();
            @Override protected void updateItem(Lamination l, boolean empty) {
                super.updateItem(l, empty);
                if (empty || l == null) { setGraphic(null); return; }

                label.getStyleClass().setAll("status-pill");

                if (l.isDeleted()) { label.setText("● Deleted"); label.getStyleClass().add("status-deleted"); }
                else if (l.isNew()) { label.setText("● New"); label.getStyleClass().add("status-new"); }
                else if (l.isUpdated()) { label.setText("● Updated"); label.getStyleClass().add("status-updated"); }
                else { setGraphic(null); return; }

                setGraphic(label);
            }
        });

        /* ===== Change detection ===== */
        ChangeListener<Object> listener = (obs, o, n) -> {
            if (selectedItem != null)
                addUpdateBtn.setDisable(!isEditorChanged());
        };

        qtyField.textProperty().addListener(listener);
        unitField.valueProperty().addListener(listener);
        typeField.valueProperty().addListener(listener);
        sideField.valueProperty().addListener(listener);
        sizeField.valueProperty().addListener(listener);
        notesField.textProperty().addListener(listener);
        amountField.textProperty().addListener(listener);
    }

    /* ================= LOAD ================= */

    public void loadForJob(Job job) {
        currentJob = job;
        laminationTable.getItems().clear();

        int count = 0; double total = 0;

        for (JobItem ji : jobItemService.getJobItems(job.getId())) {
            if ("LAMINATION".equalsIgnoreCase(ji.getType())) {
                Lamination l = repo.findByJobItemId(ji.getId());
                if (l != null) {
                    l.captureOriginal();
                    laminationTable.getItems().add(l);
                    count++; total += l.getAmount();
                }
            }
        }

        itemCount.setText(String.valueOf(count));
        totalAmount.setText("₹ " + String.format("%.2f", total));
        clearEditor();
    }

    /* ================= ADD / UPDATE ================= */

    @FXML
    private void handleAddOrUpdate() {

        if (currentJob == null) return;

        if (selectedItem == null) {
            Lamination l = new Lamination();
            fillFromEditor(l);
            l.setNew(true);
            laminationTable.getItems().add(l);
        } else {
            fillFromEditor(selectedItem);
            if (!selectedItem.isNew()) {
                selectedItem.setUpdated(selectedItem.isDifferentFromOriginal());
            }
            laminationTable.refresh();
        }

        laminationTable.getSelectionModel().clearSelection();
        clearEditor();
        addUpdateBtn.setText("Add Lamination");
        addUpdateBtn.setDisable(true);
    }

    /* ================= DELETE ================= */

    @FXML
    private void handleDelete() {
        if (selectedItem == null) return;
        selectedItem.setDeleted(true);
        laminationTable.refresh();
        laminationTable.getSelectionModel().clearSelection();
        clearEditor();
    }

    /* ================= HELPERS ================= */

    private void loadToEditor(Lamination l) {
        selectedItem = l;
        originalSnapshot = l.copy();
        qtyField.setText(String.valueOf(l.getQty()));
        unitField.setValue(l.getUnit());
        typeField.setValue(l.getType());
        sideField.setValue(l.getSide());
        sizeField.setValue(l.getSize());
        notesField.setText(l.getNotes());
        amountField.setText(String.valueOf(l.getAmount()));
    }

    private boolean isEditorChanged() {
        if (selectedItem == null || originalSnapshot == null) return false;
        return parseInt(qtyField.getText()) != originalSnapshot.getQty()
            || !eq(unitField.getValue(), originalSnapshot.getUnit())
            || !eq(typeField.getValue(), originalSnapshot.getType())
            || !eq(sideField.getValue(), originalSnapshot.getSide())
            || !eq(sizeField.getValue(), originalSnapshot.getSize())
            || !eq(notesField.getText(), originalSnapshot.getNotes())
            || parseDouble(amountField.getText()) != originalSnapshot.getAmount();
    }

    private void fillFromEditor(Lamination l) {
        l.setQty(parseInt(qtyField.getText()));
        l.setUnit(unitField.getValue());
        l.setType(typeField.getValue());
        l.setSide(sideField.getValue());
        l.setSize(sizeField.getValue());
        l.setNotes(notesField.getText());
        l.setAmount(parseDouble(amountField.getText()));
    }

    private boolean eq(Object a, Object b) { return a == null ? b == null : a.equals(b); }

    private void clearEditor() {
        selectedItem = null;
        originalSnapshot = null;
        qtyField.clear();
        unitField.setValue(null);
        typeField.setValue(null);
        sideField.setValue(null);
        sizeField.setValue(null);
        notesField.clear();
        amountField.clear();
        addUpdateBtn.setDisable(false);
    }

    private int parseInt(String v) { try { return Integer.parseInt(v); } catch (Exception e) { return 0; } }
    private double parseDouble(String v) { try { return Double.parseDouble(v); } catch (Exception e) { return 0; } }

    public ObservableList<Lamination> getItems() {
        return laminationTable.getItems();
    }
}
