package controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import model.Job;
import model.JobItem;
import model.Paper;
import repository.PaperItemRepository;
import service.JobItemService;

import java.util.List;

public class PaperTabController {

	/*
	 * ========================= TABLE (LEFT) =========================
	 */

	@FXML
	private TableView<Paper> paperTable;

	@FXML
	private TableColumn<Paper, Integer> qtyCol;
	@FXML
	private TableColumn<Paper, String> sizeCol;
	@FXML
	private TableColumn<Paper, String> gsmCol;
	@FXML
	private TableColumn<Paper, Double> amountCol;

	// NEW columns (must exist in FXML)
	@FXML
	private TableColumn<Paper, String> unitsCol;
	@FXML
	private TableColumn<Paper, String> typeCol;
	@FXML
	private TableColumn<Paper, String> sourceCol;

	@FXML
	private TableColumn<Paper, String> notesCol;

	@FXML
	private Label itemCountLabel;
	@FXML
	private Label totalAmountLabel;

	/*
	 * ========================= EDITOR (RIGHT) =========================
	 */

	@FXML
	private VBox rightPane;

	@FXML
	private TextField qtyField;
	@FXML
	private ComboBox<String> unitsField;
	@FXML
	private ComboBox<String> sizeField;
	@FXML
	private ComboBox<String> gsmField;
	@FXML
	private ComboBox<String> typeField;

	@FXML
	private RadioButton ourRadio;
	@FXML
	private RadioButton clientRadio;

	@FXML
	private TextArea notesField;
	@FXML
	private TextField amountField;
	@FXML
	private Button addUpdateBtn;

	/*
	 * ========================= STATE =========================
	 */

	private Job currentJob;
	private Paper selectedItem;
	private Paper lastSelectedItem;

	private final JobItemService jobItemService = new JobItemService();
	private final PaperItemRepository paperRepo = new PaperItemRepository();

	/*
	 * ========================= INITIALIZE =========================
	 */

	@FXML
	private void initialize() {

		/* ===== Table columns ===== */
		qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
		unitsCol.setCellValueFactory(new PropertyValueFactory<>("units"));
		sizeCol.setCellValueFactory(new PropertyValueFactory<>("size"));
		gsmCol.setCellValueFactory(new PropertyValueFactory<>("gsm"));
		typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
		sourceCol.setCellValueFactory(new PropertyValueFactory<>("source"));
		amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
		notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));

		// Enable horizontal scroll
		paperTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

		qtyCol.setPrefWidth(80);
		unitsCol.setPrefWidth(110);
		sizeCol.setPrefWidth(110);
		gsmCol.setPrefWidth(90);
		typeCol.setPrefWidth(160);
		sourceCol.setPrefWidth(110);
		amountCol.setPrefWidth(130);
		notesCol.setPrefWidth(130);

		/* ===== Radio group ===== */
		ToggleGroup sourceGroup = new ToggleGroup();
		ourRadio.setToggleGroup(sourceGroup);
		clientRadio.setToggleGroup(sourceGroup);
		ourRadio.setSelected(true);

		/* ===== ComboBox values (controller-based) ===== */
		unitsField.getItems().addAll("Sheet", "Rim", "Bundle", "Thousand", "Kg", "Roll", "Packet");

		sizeField.getItems().addAll("12x18", "13x19", "17x22", "19x25", "20x30", "23x36", "25x36", "28x40");

		gsmField.getItems().addAll("54", "60", "70", "80", "90", "100", "120", "130", "170", "200", "250", "300", "350",
				"400");

		typeField.getItems().addAll("Maplitho", "Art Paper", "Art Card", "Duplex", "Kraft", "NCR", "Sticker", "Chromo",
				"Synthetic");

		/* ===== Selection → Editor ===== */
		paperTable.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
			if (newItem != null) {
				loadToEditor(newItem);
				addUpdateBtn.setText("Update Paper");
			} else {
				clearEditor();
				addUpdateBtn.setText("Add Paper");
			}
		});

		/* ===== Click same row again → deselect ===== */
		paperTable.setRowFactory(tv -> {
			TableRow<Paper> row = new TableRow<>();

			row.setOnMouseClicked(event -> {
				if (row.isEmpty()) {
					paperTable.getSelectionModel().clearSelection();
					lastSelectedItem = null;
					return;
				}

				Paper clicked = row.getItem();

				if (clicked != null && clicked.equals(lastSelectedItem)) {
					paperTable.getSelectionModel().clearSelection();
					lastSelectedItem = null;
				} else {
					lastSelectedItem = clicked;
				}
			});

			return row;
		});

		paperTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

		/* ===== ESC key clears selection ===== */
		paperTable.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ESCAPE) {
				paperTable.getSelectionModel().clearSelection();
			}
		});
	}

	/*
	 * ========================= LOAD FOR JOB =========================
	 */

	public void loadForJob(Job job) {
		this.currentJob = job;
		paperTable.getItems().clear();

		List<JobItem> items = jobItemService.getJobItems(job.getId());

		for (JobItem ji : items) {
			if ("PAPER".equalsIgnoreCase(ji.getType())) {
				Paper p = paperRepo.findByJobItemId(ji.getId());
				if (p != null) {
					paperTable.getItems().add(p);
				}
			}
		}

		updateFooter();
		clearEditor();
	}

	/*
	 * ========================= EDITOR LOAD =========================
	 */

	private void loadToEditor(Paper p) {
		selectedItem = p;

		qtyField.setText(String.valueOf(p.getQty()));
		unitsField.setValue(p.getUnits());
		sizeField.setValue(p.getSize());
		gsmField.setValue(p.getGsm());
		typeField.setValue(p.getType());
		notesField.setText(p.getNotes());
		amountField.setText(String.valueOf(p.getAmount()));

		if ("CLIENT".equalsIgnoreCase(p.getSource())) {
			clientRadio.setSelected(true);
		} else {
			ourRadio.setSelected(true);
		}
	}

	/*
	 * ========================= ADD / UPDATE =========================
	 */

	@FXML
	private void handleAddOrUpdate() {

		if (currentJob == null)
			return;

		boolean isNew = false;

		if (selectedItem == null) {
			selectedItem = new Paper();
			isNew = true;
		}

		selectedItem.setQty(parseInt(qtyField.getText()));
		selectedItem.setUnits(unitsField.getValue());
		selectedItem.setSize(sizeField.getValue());
		selectedItem.setGsm(gsmField.getValue());
		selectedItem.setType(typeField.getValue());
		selectedItem.setNotes(notesField.getText());
		selectedItem.setAmount(parseDouble(amountField.getText()));
		selectedItem.setSource(ourRadio.isSelected() ? "OUR" : "CLIENT");

		// TODO: persist using repository
		// paperRepo.saveOrUpdate(currentJob, selectedItem);

		if (isNew) {
			paperTable.getItems().add(selectedItem);
		}

		paperTable.getSelectionModel().clearSelection();
		paperTable.refresh();
		updateFooter();
		clearEditor();
		addUpdateBtn.setText("Add Paper");
	}

	/*
	 * ========================= HELPERS =========================
	 */

	private void clearEditor() {
		selectedItem = null;
		qtyField.clear();
		unitsField.setValue(null);
		sizeField.setValue(null);
		gsmField.setValue(null);
		typeField.setValue(null);
		notesField.clear();
		amountField.clear();
		ourRadio.setSelected(true);
	}

	private void updateFooter() {
		int count = paperTable.getItems().size();
		double total = paperTable.getItems().stream().mapToDouble(Paper::getAmount).sum();

		if (itemCountLabel != null)
			itemCountLabel.setText(String.valueOf(count));

		if (totalAmountLabel != null)
			totalAmountLabel.setText("₹ " + String.format("%.2f", total));
	}

	private int parseInt(String v) {
		try {
			return Integer.parseInt(v);
		} catch (Exception e) {
			return 0;
		}
	}

	private double parseDouble(String v) {
		try {
			return Double.parseDouble(v);
		} catch (Exception e) {
			return 0.0;
		}
	}
}
