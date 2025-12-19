package controller; // adjust if needed

import java.io.File;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import model.Binding;
import model.Client;
import model.CtpPlate;
import model.Job;
import model.Lamination;
import model.Paper;
import model.Printing;
import model.Supplier;
import service.ClientService;
import service.SupplierService;

public class AddJobController {

	private Job currentJob = new Job();
	@FXML
	private RadioButton paperOurRadio;
	@FXML
	private RadioButton paperClientRadio;
	@FXML
	private ComboBox<Client> clientCombo;
	private final ClientService clientService = new ClientService();

	private ToggleGroup paperSourceGroup;

	@FXML
	private ImageView jobImagePreview;
	@FXML
	private VBox pdfPreviewBox;
	@FXML
	private Label filePlaceholder;

	private final SupplierService supplierService = new SupplierService();

	/* ========================= PRINTING ========================= */
	@FXML
	private TextField printQtyField;
	@FXML
	private ComboBox<String> printUnitsCombo;
	@FXML
	private TextField printSetField;
	@FXML
	private ComboBox<String> printColorCombo;
	@FXML
	private ComboBox<String> printSideCombo;
	@FXML
	private ComboBox<String> printCtpCombo;
	@FXML
	private TextArea printNotesArea;
	@FXML
	private TextField printAmountField;

	/* ========================= CTP PLATE ========================= */
	@FXML
	private TextField ctpQtyField;
	@FXML
	private ComboBox<String> ctpSizeCombo;
	@FXML
	private ComboBox<String> ctpGaugeCombo;
	@FXML
	private ComboBox<String> ctpBackingCombo;
	@FXML
	private TextArea ctpNotesArea;
	@FXML
	private TextField ctpAmountField;
	@FXML
	private ComboBox<Supplier> ctpSupplierCombo;

	/* ========================= PAPER ========================= */
	@FXML
	private TextField paperQtyField;
	@FXML
	private ComboBox<String> paperUnitsCombo;
	@FXML
	private ComboBox<String> paperSizeCombo;
	@FXML
	private ComboBox<String> paperGsmCombo;
	@FXML
	private ComboBox<String> paperTypeCombo;
	@FXML
	private TextArea paperNotesArea;
	@FXML
	private TextField paperAmountField;

	/* ========================= BINDING ========================= */
	@FXML
	private ComboBox<String> bindingProcessCombo;
	@FXML
	private TextField bindingQtyField;
	@FXML
	private TextField bindingRateField;
	@FXML
	private TextArea bindingNotesArea;
	@FXML
	private TextField bindingAmountField;

	/* ========================= LAMINATION ========================= */
	@FXML
	private TextField lamQtyField;
	@FXML
	private ComboBox<String> lamUnitCombo;
	@FXML
	private ComboBox<String> lamTypeCombo;
	@FXML
	private ComboBox<String> lamSideCombo;
	@FXML
	private ComboBox<String> lamSizeCombo;
	@FXML
	private TextArea lamNotesArea;
	@FXML
	private TextField lamAmountField;

	@FXML
	private void handleUploadFile(ActionEvent event) {
		try {
			FileChooser chooser = new FileChooser();
			chooser.setTitle("Select Image or PDF");

			chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"),
					new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

			File file = chooser.showOpenDialog(null);
			if (file == null)
				return;

			String name = file.getName().toLowerCase();

			if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
				showImagePreview(file);
			} else if (name.endsWith(".pdf")) {
				showPdfPreview();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void showImagePreview(File file) {
		Image img = new Image(file.toURI().toString());
		jobImagePreview.setImage(img);

		jobImagePreview.setVisible(true);
		jobImagePreview.setManaged(true);

		pdfPreviewBox.setVisible(false);
		pdfPreviewBox.setManaged(false);

		filePlaceholder.setVisible(false);
		filePlaceholder.setManaged(false);
	}

	private void showPdfPreview() {
		jobImagePreview.setVisible(false);
		jobImagePreview.setManaged(false);

		pdfPreviewBox.setVisible(true);
		pdfPreviewBox.setManaged(true);

		filePlaceholder.setVisible(false);
		filePlaceholder.setManaged(false);
	}

	@FXML
	private void handleAddJobButton() {
//
//		String summaryText = JobSummaryFormatter.generateSummary(currentJob);
//
//		boolean confirmed = PopupUtil.showJobSummary(summaryText);
//
//		if (confirmed) {
//			// JobService.saveJob(currentJob);
//			showSuccessMessage();
//			resetForm();
//		}
	}

	@FXML
	private void handleAddPrinting() {

		Printing p = new Printing();
		p.setQty(printQtyField.getText());
		p.setUnits(printUnitsCombo.getValue());
		p.setSet(printSetField.getText());
		p.setColor(printColorCombo.getValue());
		p.setSide(printSideCombo.getValue());
		p.setWithCtp(printCtpCombo.getValue());
		p.setNotes(printNotesArea.getText());
		p.setAmount(printAmountField.getText());

		// currentJob.addPrinting(p);

		clearPrintingFields();
	}

	private void clearPrintingFields() {
		printQtyField.clear();
		printUnitsCombo.setValue(null);
		printSetField.setText(null);
		printColorCombo.setValue(null);
		printSideCombo.setValue(null);
		printCtpCombo.setValue(null);
		printNotesArea.clear();
		printAmountField.clear();
	}

	@FXML
	private void handleAddCtpPlate() {

		CtpPlate plate = new CtpPlate();
		plate.setQty(ctpQtyField.getText());
		plate.setSize(ctpSizeCombo.getValue());
		plate.setGauge(ctpGaugeCombo.getValue());
		plate.setBacking(ctpBackingCombo.getValue());
		plate.setNotes(ctpNotesArea.getText());
		plate.setAmount(ctpAmountField.getText());
		plate.setSupplier(ctpSupplierCombo.getValue());

		// currentJob.addCtpPlate(plate);
		clearCtpFields();
	}

	private void clearCtpFields() {
		ctpQtyField.setText(null);
		ctpSizeCombo.setValue(null);
		ctpGaugeCombo.setValue(null);
		ctpBackingCombo.setValue(null);
		ctpSupplierCombo.setValue(null);
		ctpNotesArea.clear();
		ctpAmountField.clear();

	}

	@FXML
	private void handleAddPaper() {

		Paper paper = new Paper();
		paper.setQty(paperQtyField.getText());
		paper.setUnits(paperUnitsCombo.getValue());
		paper.setSize(paperSizeCombo.getValue());
		paper.setGsm(paperGsmCombo.getValue());
		paper.setType(paperTypeCombo.getValue());
		paper.setNotes(paperNotesArea.getText());
		paper.setAmount(paperAmountField.getText());

		// NEW: Set source (Our or Client)
		String source = ((RadioButton) paperSourceGroup.getSelectedToggle()).getText();
		paper.setSource(source);

		// currentJob.addPaper(paper);
		clearPaperFields();
	}

	private void clearPaperFields() {
		paperQtyField.clear();
		paperUnitsCombo.setValue(null);
		paperSizeCombo.setValue(null);
		paperGsmCombo.setValue(null);
		paperTypeCombo.setValue(null);
		paperNotesArea.clear();
		paperAmountField.clear();
		paperOurRadio.setSelected(true);

	}

	@FXML
	private void handleAddBinding() {

		Binding b = new Binding();
		b.setProcess(bindingProcessCombo.getValue());
		b.setQty(bindingQtyField.getText());
		b.setRate(bindingRateField.getText());
		b.setNotes(bindingNotesArea.getText());
		b.setAmount(bindingAmountField.getText());

		// currentJob.addBinding(b);
		clearBindingFields();
	}

	private void clearBindingFields() {
		bindingProcessCombo.setValue(null);
		bindingQtyField.clear();
		bindingRateField.clear();
		bindingNotesArea.clear();
		bindingAmountField.clear();
	}

	@FXML
	private void handleAddLamination() {

		Lamination lam = new Lamination();
		lam.setQty(lamQtyField.getText());
		lam.setUnit(lamUnitCombo.getValue());
		lam.setType(lamTypeCombo.getValue());
		lam.setSide(lamSideCombo.getValue());
		lam.setSize(lamSizeCombo.getValue());
		lam.setNotes(lamNotesArea.getText());
		lam.setAmount(lamAmountField.getText());

		// currentJob.addLamination(lam);
		clearLaminationFields();
	}

	private void clearLaminationFields() {
		lamQtyField.clear();
		lamUnitCombo.setValue(null);
		lamTypeCombo.setValue(null);
		lamSideCombo.setValue(null);
		lamSizeCombo.setValue(null);
		lamNotesArea.clear();
		lamAmountField.clear();
	}

	private void resetForm() {
		currentJob = new Job(); // fresh job

		// Clear Printing fields
		printQtyField.clear();
		printUnitsCombo.setValue(null);
		printSetField.setText(null);
		printColorCombo.setValue(null);
		printSideCombo.setValue(null);
		printCtpCombo.setValue(null);
		printNotesArea.clear();
		printAmountField.clear();

		// Clear CTP fields
		ctpQtyField.setText(null);
		ctpSizeCombo.setValue(null);
		ctpGaugeCombo.setValue(null);
		ctpBackingCombo.setValue(null);
		ctpNotesArea.clear();
		ctpAmountField.clear();

		// Clear Paper fields
		paperQtyField.clear();
		paperUnitsCombo.setValue(null);
		paperSizeCombo.setValue(null);
		paperGsmCombo.setValue(null);
		paperTypeCombo.setValue(null);
		paperNotesArea.clear();
		paperAmountField.clear();

		// Clear Binding fields
		bindingProcessCombo.setValue(null);
		bindingQtyField.clear();
		bindingRateField.clear();
		bindingNotesArea.clear();
		bindingAmountField.clear();

		// Clear Lamination fields
		lamQtyField.clear();
		lamUnitCombo.setValue(null);
		lamTypeCombo.setValue(null);
		lamSideCombo.setValue(null);
		lamSizeCombo.setValue(null);
		lamNotesArea.clear();
		lamAmountField.clear();

		// Clear image/pdf preview
		jobImagePreview.setImage(null);
		jobImagePreview.setVisible(false);
		jobImagePreview.setManaged(false);

		pdfPreviewBox.setVisible(false);
		pdfPreviewBox.setManaged(false);

		filePlaceholder.setVisible(true);
		filePlaceholder.setManaged(true);
	}

	private void showSuccessMessage() {
		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle("Success");
		alert.setHeaderText(null);
		alert.setContentText("Job added successfully!");
		alert.showAndWait();
	}

	// <=====================Validation Logics =======================>

	// ---------- Add these FXML fields (if not already present) ----------
	@FXML
	private Button addPrintingBtn;
	@FXML
	private Button addPlateBtn;
	@FXML
	private Button addPaperBtn;
	@FXML
	private Button addBindingBtn;
	@FXML
	private Button addLaminationBtn;

	// ---------- Helper: allow only digits in TextField ----------
	private void makeNumeric(TextField tf) {
		tf.setTextFormatter(new TextFormatter<>(change -> {
			// allow empty or digits only
			return change.getControlNewText().matches("\\d*") ? change : null;
		}));
	}

	// ---------- Small helper ----------
	private boolean notEmpty(String s) {
		return s != null && !s.trim().isEmpty();
	}

	// ---------- PRINTING validation ----------
	private boolean isPrintingValid() {
		boolean fullEntry = notEmpty(printQtyField.getText()) && printUnitsCombo.getValue() != null
				&& notEmpty(printSetField.getText()) && notEmpty(printAmountField.getText());

		boolean notesEntry = notEmpty(printNotesArea.getText()) && notEmpty(printAmountField.getText());

		return fullEntry || notesEntry;
	}

	private void setupPrintingValidation() {
		Runnable validate = () -> addPrintingBtn.setDisable(!isPrintingValid());

		// watch relevant properties
		printQtyField.textProperty().addListener((a, b, c) -> validate.run());
		printSetField.textProperty().addListener((a, b, c) -> validate.run());
		printAmountField.textProperty().addListener((a, b, c) -> validate.run());
		printNotesArea.textProperty().addListener((a, b, c) -> validate.run());
		printUnitsCombo.valueProperty().addListener((a, b, c) -> validate.run());

		// initial
		validate.run();
	}

	// ---------- CTP validation ----------
	private boolean isCtpValid() {
		boolean fullEntry = notEmpty(ctpQtyField.getText()) && ctpSizeCombo.getValue() != null
				&& notEmpty(ctpAmountField.getText()) && ctpSupplierCombo.getValue() != null;

		boolean notesEntry = notEmpty(ctpNotesArea.getText()) && notEmpty(ctpAmountField.getText());

		return fullEntry || notesEntry;
	}

	private void setupCtpValidation() {
		Runnable validate = () -> addPlateBtn.setDisable(!isCtpValid());

		ctpQtyField.textProperty().addListener((a, b, c) -> validate.run());
		ctpSizeCombo.valueProperty().addListener((a, b, c) -> validate.run());
		ctpAmountField.textProperty().addListener((a, b, c) -> validate.run());
		ctpNotesArea.textProperty().addListener((a, b, c) -> validate.run());

		validate.run();
	}

	// ---------- PAPER validation ----------
	private boolean isPaperValid() {
		boolean fullEntry = notEmpty(paperQtyField.getText()) && paperUnitsCombo.getValue() != null
				&& paperSizeCombo.getValue() != null && notEmpty(paperAmountField.getText());

		boolean notesEntry = notEmpty(paperNotesArea.getText()) && notEmpty(paperAmountField.getText());

		return fullEntry || notesEntry;
	}

	private void setupPaperValidation() {
		Runnable validate = () -> addPaperBtn.setDisable(!isPaperValid());

		paperQtyField.textProperty().addListener((a, b, c) -> validate.run());
		paperAmountField.textProperty().addListener((a, b, c) -> validate.run());
		paperNotesArea.textProperty().addListener((a, b, c) -> validate.run());
		paperUnitsCombo.valueProperty().addListener((a, b, c) -> validate.run());
		paperSizeCombo.valueProperty().addListener((a, b, c) -> validate.run());

		validate.run();
	}

	// ---------- BINDING validation ----------
	private boolean isBindingValid() {
		boolean fullEntry = bindingProcessCombo.getValue() != null && notEmpty(bindingQtyField.getText())
				&& notEmpty(bindingRateField.getText()) && notEmpty(bindingAmountField.getText());

		boolean notesEntry = notEmpty(bindingNotesArea.getText()) && notEmpty(bindingAmountField.getText());

		return fullEntry || notesEntry;
	}

	private void setupBindingValidation() {
		Runnable validate = () -> addBindingBtn.setDisable(!isBindingValid());

		bindingProcessCombo.valueProperty().addListener((a, b, c) -> validate.run());
		bindingQtyField.textProperty().addListener((a, b, c) -> validate.run());
		bindingRateField.textProperty().addListener((a, b, c) -> validate.run());
		bindingAmountField.textProperty().addListener((a, b, c) -> validate.run());
		bindingNotesArea.textProperty().addListener((a, b, c) -> validate.run());

		validate.run();
	}

	// ---------- LAMINATION validation ----------
	private boolean isLaminationValid() {
		boolean fullEntry = notEmpty(lamQtyField.getText()) && lamUnitCombo.getValue() != null
				&& lamTypeCombo.getValue() != null && notEmpty(lamAmountField.getText());

		boolean notesEntry = notEmpty(lamNotesArea.getText()) && notEmpty(lamAmountField.getText());

		return fullEntry || notesEntry;
	}

	private void setupLaminationValidation() {
		Runnable validate = () -> addLaminationBtn.setDisable(!isLaminationValid());

		lamQtyField.textProperty().addListener((a, b, c) -> validate.run());
		lamUnitCombo.valueProperty().addListener((a, b, c) -> validate.run());
		lamTypeCombo.valueProperty().addListener((a, b, c) -> validate.run());
		lamAmountField.textProperty().addListener((a, b, c) -> validate.run());
		lamNotesArea.textProperty().addListener((a, b, c) -> validate.run());

		validate.run();
	}

	// ---------- initialize() wiring ----------
	@FXML
	public void initialize() {

		clientCombo.getItems().setAll(clientService.getAllClients());
		clientCombo.setCellFactory(lv -> new ListCell<>() {
			@Override
			protected void updateItem(Client c, boolean empty) {
				super.updateItem(c, empty);
				if (empty || c == null) {
					setText(null);
				} else {
					setText(c.getBusinessName() + " | " + c.getClientName() + " | " + c.getPhone() + " | "
							+ c.getGst());
				}
			}
		});
		clientCombo.setEditable(true);

		clientCombo.getEditor().textProperty().addListener((obs, oldText, newText) -> {
			if (newText == null)
				return;

			// fetch filtered list
			clientCombo.getItems().setAll(clientService.searchClients(newText));

			// keep dropdown open while typing
			clientCombo.show();
		});

		// this controls what is shown AFTER selection
		clientCombo.setButtonCell(clientCombo.getCellFactory().call(null));

		// numeric-only
		makeNumeric(printQtyField);
		makeNumeric(printSetField); // set is numeric per your requirement
		makeNumeric(printAmountField);
		makeNumeric(ctpAmountField);
		makeNumeric(ctpQtyField);
		makeNumeric(paperQtyField);
		makeNumeric(paperAmountField);
		makeNumeric(bindingQtyField);
		makeNumeric(bindingRateField);
		makeNumeric(bindingAmountField);
		makeNumeric(lamQtyField);
		makeNumeric(lamAmountField);

		// setup validators
		setupPrintingValidation();
		setupCtpValidation();
		setupPaperValidation();
		setupBindingValidation();
		setupLaminationValidation();
		loadSuppliers();
		// ensure initial state (disable all add buttons)
		addPrintingBtn.setDisable(true);
		addPlateBtn.setDisable(true);
		addPaperBtn.setDisable(true);
		addBindingBtn.setDisable(true);
		addLaminationBtn.setDisable(true);

		// STEP: Create toggle group for paper selection
		paperSourceGroup = new ToggleGroup();
		paperOurRadio.setToggleGroup(paperSourceGroup);
		paperClientRadio.setToggleGroup(paperSourceGroup);

		paperOurRadio.setSelected(true);

	}

	private void loadSuppliers() {
		ctpSupplierCombo.getItems().setAll(supplierService.getSuppliersByType("CTP"));
		ctpSupplierCombo.setCellFactory(cb -> new ListCell<>() {
			@Override
			protected void updateItem(Supplier s, boolean empty) {
				super.updateItem(s, empty);
				setText(empty || s == null ? null : s.getbusinessName() + " | " + s.getName());
			}
		});

		ctpSupplierCombo.setButtonCell(new ListCell<>() {
			@Override
			protected void updateItem(Supplier s, boolean empty) {
				super.updateItem(s, empty);
				setText(empty || s == null ? null : s.getbusinessName() + " | " + s.getName());
			}
		});

	}

	private void lockInvalidClientEntry() {
		clientCombo.focusedProperty().addListener((obs, old, focused) -> {
			if (!focused && !clientCombo.getItems().contains(clientCombo.getValue())) {
				clientCombo.setValue(null);
			}
		});
	}

}