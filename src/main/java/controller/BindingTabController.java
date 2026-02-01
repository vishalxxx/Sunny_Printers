package controller;

import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import model.Binding;
import model.Job;
import model.JobItem;
import repository.BindingItemRepository;
import service.JobItemService;

public class BindingTabController {

    /* ================= TABLE ================= */

    @FXML private TableView<Binding> bindingTable;
    @FXML private TableColumn<Binding, String> processCol;
    @FXML private TableColumn<Binding, Integer> qtyCol;
    @FXML private TableColumn<Binding, Double> rateCol;
    @FXML private TableColumn<Binding, Double> amountCol;
    @FXML private TableColumn<Binding, String> notesCol;
    @FXML private TableColumn<Binding, Binding> statusCol;

    /* ================= EDITOR ================= */

    @FXML private ComboBox<String> processField;
    @FXML private TextField qtyField;
    @FXML private TextField rateField;
    @FXML private TextArea notesField;
    @FXML private TextField amountField;
    @FXML private Button addUpdateBtn;
    @FXML private Button deleteBtn;

    /* ================= STATE ================= */

    private Job currentJob;
    private Binding selectedItem;
    private Binding lastSelectedItem;
    private Binding originalSnapshot;

    private final JobItemService jobItemService = new JobItemService();
    private final BindingItemRepository repo = new BindingItemRepository();

    /* ================= INIT ================= */

    @FXML
    private void initialize() {

        processCol.setCellValueFactory(new PropertyValueFactory<>("process"));
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        rateCol.setCellValueFactory(new PropertyValueFactory<>("rate"));
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));

        bindingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        bindingTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        processField.getItems().setAll(
            "Center Pin", "Side Pin", "Perfect Binding", "Hardcase Binding",
            "Spiral", "Wire-O", "Thermal Binding", "Pad Binding",
            "Folding", "Creasing", "Punching", "Die Cutting"
        );

        /* ===== selection → editor ===== */
        bindingTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            clearEditor();
            if (n != null) {
                loadToEditor(n);
                addUpdateBtn.setText("Update Binding");
                deleteBtn.setVisible(true);
                deleteBtn.setManaged(true);
            } else {
                addUpdateBtn.setText("Add Binding");
                deleteBtn.setVisible(false);
                deleteBtn.setManaged(false);
            }
        });

        /* ===== click again → deselect ===== */
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

        bindingTable.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                bindingTable.getSelectionModel().clearSelection();
            }
        });

        /* ===== STATUS PILL ===== */
        statusCol.setCellValueFactory(p -> new javafx.beans.property.SimpleObjectProperty<>(p.getValue()));
        statusCol.setCellFactory(col -> new TableCell<>() {

            private final Label label = new Label();

            @Override
            protected void updateItem(Binding b, boolean empty) {
                super.updateItem(b, empty);
                if (empty || b == null) {
                    setGraphic(null);
                    return;
                }

                label.getStyleClass().setAll("status-pill");

                if (b.isDeleted()) {
                    label.setText("● Deleted");
                    label.getStyleClass().add("status-deleted");
                }
                else if (b.isNew()) {
                    label.setText("● New");
                    label.getStyleClass().add("status-new");
                }
                else if (b.isUpdated()) {
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

        /* ===== CHANGE LISTENER ===== */
        ChangeListener<Object> listener = (obs, o, n) -> {
            if (selectedItem != null) {
                addUpdateBtn.setDisable(!isEditorChanged());
            }
        };

        processField.valueProperty().addListener(listener);
        qtyField.textProperty().addListener(listener);
        rateField.textProperty().addListener(listener);
        notesField.textProperty().addListener(listener);
        amountField.textProperty().addListener(listener);
    }

    /* ================= LOAD JOB ================= */

    public void loadForJob(Job job) {
        currentJob = job;
        bindingTable.getItems().clear();

        for (JobItem ji : jobItemService.getJobItems(job.getId())) {
            if ("BINDING".equalsIgnoreCase(ji.getType())) {
                Binding b = repo.findByJobItemId(ji.getId());
                if (b != null) {
                    b.setJobItemId(ji.getId());
                    b.resetFlags();
                    b.captureOriginal();
                    bindingTable.getItems().add(b);
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
            Binding b = new Binding();
            b.setProcess(processField.getValue());
            b.setQty(parseInt(qtyField.getText()));
            b.setRate(parseDouble(rateField.getText()));
            b.setNotes(notesField.getText());
            b.setAmount(parseDouble(amountField.getText()));
            b.setNew(true);
            bindingTable.getItems().add(b);
        }
        else {
            selectedItem.setProcess(processField.getValue());
            selectedItem.setQty(parseInt(qtyField.getText()));
            selectedItem.setRate(parseDouble(rateField.getText()));
            selectedItem.setNotes(notesField.getText());
            selectedItem.setAmount(parseDouble(amountField.getText()));

            if (!selectedItem.isNew()) {
                selectedItem.setUpdated(!selectedItem.isSameAsOriginal());
            }
            bindingTable.refresh();
        }

        bindingTable.getSelectionModel().clearSelection();
        clearEditor();
        addUpdateBtn.setText("Add Binding");
    }

    /* ================= DELETE ================= */

    @FXML
    private void handleDelete() {
        if (selectedItem == null) return;
        selectedItem.setDeleted(true);
        bindingTable.refresh();
        bindingTable.getSelectionModel().clearSelection();
        clearEditor();
    }

    /* ================= HELPERS ================= */

    private void loadToEditor(Binding b) {
        selectedItem = b;
        originalSnapshot = b.copy();
        processField.setValue(b.getProcess());
        qtyField.setText(String.valueOf(b.getQty()));
        rateField.setText(String.valueOf(b.getRate()));
        notesField.setText(b.getNotes());
        amountField.setText(String.valueOf(b.getAmount()));
    }

    private boolean isEditorChanged() {
        if (originalSnapshot == null) return false;

        return parseInt(qtyField.getText()) != originalSnapshot.getQty()
            || parseDouble(rateField.getText()) != originalSnapshot.getRate()
            || parseDouble(amountField.getText()) != originalSnapshot.getAmount()
            || !equals(processField.getValue(), originalSnapshot.getProcess())
            || !equals(notesField.getText(), originalSnapshot.getNotes());
    }

    private void clearEditor() {
        selectedItem = null;
        originalSnapshot = null;
        processField.setValue(null);
        qtyField.clear();
        rateField.clear();
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

    public ObservableList<Binding> getItems() {
        return bindingTable.getItems();
    }
}
