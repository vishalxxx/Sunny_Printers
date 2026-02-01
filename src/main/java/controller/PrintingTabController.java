package controller;

import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import model.Job;
import model.JobItem;
import model.Paper;
import model.Printing;
import repository.PrintingItemRepository;
import service.JobItemService;

public class PrintingTabController {

    /* ========================= TABLE ========================= */

    @FXML private TableView<Printing> printingTable;

    @FXML private TableColumn<Printing, Integer> qtyCol;
    @FXML private TableColumn<Printing, String> unitsCol;
    @FXML private TableColumn<Printing, String> colorCol;
    @FXML private TableColumn<Printing, String> sideCol;
    @FXML private TableColumn<Printing, Boolean> ctpCol;
    @FXML private TableColumn<Printing, String> setsCol;
    @FXML private TableColumn<Printing, Double> amountCol;
    @FXML private TableColumn<Printing, String> notesCol;

    @FXML private TableColumn<Printing, Printing> statusCol;

    /* ========================= EDITOR ========================= */

    @FXML private TextField qtyField;
    @FXML private ComboBox<String> unitsField;
    @FXML private ComboBox<String> colorField;
    @FXML private ComboBox<String> sideField;
    @FXML private CheckBox ctpCheckBox;
    @FXML private TextField setsField;
    @FXML private TextArea notesField;
    @FXML private TextField amountField;

    @FXML private Button addUpdateBtn;
    @FXML private Button deleteBtn;

    /* ========================= STATE ========================= */

    private Job currentJob;
    private Printing selectedItem;
    private Printing lastSelectedItem;
    private Printing originalSnapshot;

    private final JobItemService jobItemService = new JobItemService();
    private final PrintingItemRepository printingRepo = new PrintingItemRepository();


	
    /* ========================= INIT ========================= */

    @FXML
    private void initialize() {

        /* ===== Columns ===== */
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        unitsCol.setCellValueFactory(new PropertyValueFactory<>("units"));
        colorCol.setCellValueFactory(new PropertyValueFactory<>("color"));
        sideCol.setCellValueFactory(new PropertyValueFactory<>("side"));
        ctpCol.setCellValueFactory(new PropertyValueFactory<>("withCtp"));
        setsCol.setCellValueFactory(new PropertyValueFactory<>("sets"));
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));

        printingTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        printingTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        /* ===== Combo data ===== */
        unitsField.getItems().setAll(
                "Sheets", "Rim", "Bundle", "Thousand", "Lakh", "Roll", "Packet"
        );

        colorField.getItems().setAll(
                "1 Color", "2 Color", "4 Color",
                "4 + 1", "4 + 4", "Multicolor", "Spot Color"
        );

        sideField.getItems().setAll("F/B", "W/T", "S/S");

        /* ===== Selection ===== */
        printingTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldItem, newItem) -> {
                    clearEditor();
                    if (newItem != null) {
                        loadToEditor(newItem);
                        addUpdateBtn.setText("Update Printing");
                        deleteBtn.setVisible(true);
                        deleteBtn.setManaged(true);
                    } else {
                        addUpdateBtn.setText("Add Printing");
                        deleteBtn.setVisible(false);
                        deleteBtn.setManaged(false);
                    }
                });

        /* ===== Row click deselect ===== */
        printingTable.setRowFactory(tv -> {
            TableRow<Printing> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (row.isEmpty()) {
                    printingTable.getSelectionModel().clearSelection();
                    lastSelectedItem = null;
                    return;
                }
                Printing clicked = row.getItem();
                if (clicked != null && clicked.equals(lastSelectedItem)) {
                    printingTable.getSelectionModel().clearSelection();
                    lastSelectedItem = null;
                } else {
                    lastSelectedItem = clicked;
                }
            });
            return row;
        });

        /* ===== ESC ===== */
        printingTable.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                printingTable.getSelectionModel().clearSelection();
            }
        });

        /* ===== Status Pill ===== */
        statusCol.setCellValueFactory(param ->
                new javafx.beans.property.SimpleObjectProperty<>(param.getValue()));

        statusCol.setCellFactory(col -> new TableCell<>() {
            private final Label label = new Label();

            @Override
            protected void updateItem(Printing item, boolean empty) {
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

        /* ===== Change detection ===== */
        ChangeListener<Object> changeListener = (obs, o, n) -> {
            if (selectedItem != null) {
                addUpdateBtn.setDisable(!isEditorChanged());
            }
        };

        qtyField.textProperty().addListener(changeListener);
        unitsField.valueProperty().addListener(changeListener);
        colorField.valueProperty().addListener(changeListener);
        sideField.valueProperty().addListener(changeListener);
        ctpCheckBox.selectedProperty().addListener(changeListener);
        setsField.textProperty().addListener(changeListener);
        notesField.textProperty().addListener(changeListener);
        amountField.textProperty().addListener(changeListener);
    }

    /* ========================= LOAD JOB ========================= */

    public void loadForJob(Job job) {
        this.currentJob = job;
        printingTable.getItems().clear();

        for (JobItem ji : jobItemService.getJobItems(job.getId())) {
            if ("PRINTING".equalsIgnoreCase(ji.getType())) {
                Printing p = printingRepo.findByJobItemId(ji.getId());
                if (p != null) {
                    p.captureOriginal(); // 🔥 critical
                    printingTable.getItems().add(p);
                }
            }
        }

        clearEditor();
        addUpdateBtn.setText("Add Printing");
    }

    /* ========================= ADD / UPDATE ========================= */

    @FXML
    private void handleAddOrUpdate() {

        if (currentJob == null) return;

        boolean isNew = false;

        if (selectedItem == null) {
            selectedItem = new Printing();
            selectedItem.setNew(true);
            isNew = true;
        }

        selectedItem.setQty(parseInt(qtyField.getText()));
        selectedItem.setUnits(unitsField.getValue());
        selectedItem.setColor(colorField.getValue());
        selectedItem.setSide(sideField.getValue());
        selectedItem.setWithCtp(ctpCheckBox.isSelected());
        selectedItem.setSets(setsField.getText());
        selectedItem.setNotes(notesField.getText());
        selectedItem.setAmount(parseDouble(amountField.getText()));

        if (!isNew) {
            if (selectedItem.isDifferentFromOriginal()) {
                selectedItem.setUpdated(true);
            } else {
                selectedItem.setUpdated(false); // revert
            }
        }

        if (isNew) {
            printingTable.getItems().add(selectedItem);
        }

        printingTable.getSelectionModel().clearSelection();
        printingTable.refresh();
        clearEditor();
        addUpdateBtn.setText("Add Printing");
    }

    /* ========================= HELPERS ========================= */

    private void loadToEditor(Printing p) {
        selectedItem = p;
        originalSnapshot = p.copy();

        qtyField.setText(String.valueOf(p.getQty()));
        unitsField.setValue(p.getUnits());
        colorField.setValue(p.getColor());
        sideField.setValue(p.getSide());
        ctpCheckBox.setSelected(p.isWithCtp());
        setsField.setText(p.getSets());
        notesField.setText(p.getNotes());
        amountField.setText(String.valueOf(p.getAmount()));
    }

    private boolean isEditorChanged() {
        if (selectedItem == null || originalSnapshot == null) return false;

        return parseInt(qtyField.getText()) != originalSnapshot.getQty()
            || !equals(unitsField.getValue(), originalSnapshot.getUnits())
            || !equals(colorField.getValue(), originalSnapshot.getColor())
            || !equals(sideField.getValue(), originalSnapshot.getSide())
            || ctpCheckBox.isSelected() != originalSnapshot.isWithCtp()
            || !equals(setsField.getText(), originalSnapshot.getSets())
            || !equals(notesField.getText(), originalSnapshot.getNotes())
            || parseDouble(amountField.getText()) != originalSnapshot.getAmount();
    }

    private boolean equals(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private void clearEditor() {
        selectedItem = null;
        originalSnapshot = null;

        qtyField.clear();
        unitsField.setValue(null);
        colorField.setValue(null);
        sideField.setValue(null);
        ctpCheckBox.setSelected(false);
        setsField.clear();
        notesField.clear();
        amountField.clear();

        addUpdateBtn.setDisable(false);
    }

    private int parseInt(String v) {
        try { return Integer.parseInt(v); }
        catch (Exception e) { return 0; }
    }

    private double parseDouble(String v) {
        try { return Double.parseDouble(v); }
        catch (Exception e) { return 0.0; }
    }

    public ObservableList<Printing> getItems() {
        return printingTable.getItems();
    }

    @FXML
    private void handleDelete() {
        if (selectedItem == null) return;
        selectedItem.setDeleted(true);
        printingTable.refresh();
        printingTable.getSelectionModel().clearSelection();
        clearEditor();
    }
}
