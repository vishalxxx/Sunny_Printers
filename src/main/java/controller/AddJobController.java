package controller; // adjust if needed

import java.io.File;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import model.Binding;
import model.CtpPlate;
import model.Job;
import model.Lamination;
import model.Paper;
import model.Printing;
import service.JobService;
import utils.JobSummaryFormatter;
import utils.PopupUtil;

public class AddJobController {

	private Job currentJob = new Job();

	@FXML
	private ImageView jobImagePreview;
	@FXML
	private VBox pdfPreviewBox;
	@FXML
	private Label filePlaceholder;

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
	private ComboBox<String> ctpQtyCombo;
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

		String summaryText = JobSummaryFormatter.generateSummary(currentJob);

		boolean confirmed = PopupUtil.showJobSummary(summaryText);

		if (confirmed) {
			JobService.saveJob(currentJob);
			showSuccessMessage();
			resetForm();
		}
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

		currentJob.addPrinting(p);

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
		plate.setQty(ctpQtyCombo.getValue());
		plate.setSize(ctpSizeCombo.getValue());
		plate.setGauge(ctpGaugeCombo.getValue());
		plate.setBacking(ctpBackingCombo.getValue());
		plate.setNotes(ctpNotesArea.getText());
		plate.setAmount(ctpAmountField.getText());

		currentJob.addCtpPlate(plate);
		clearCtpFields();
	}

	private void clearCtpFields() {
		ctpQtyCombo.setValue(null);
		ctpSizeCombo.setValue(null);
		ctpGaugeCombo.setValue(null);
		ctpBackingCombo.setValue(null);
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

		currentJob.addPaper(paper);
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
	}

	@FXML
	private void handleAddBinding() {

		Binding b = new Binding();
		b.setProcess(bindingProcessCombo.getValue());
		b.setQty(bindingQtyField.getText());
		b.setRate(bindingRateField.getText());
		b.setNotes(bindingNotesArea.getText());
		b.setAmount(bindingAmountField.getText());

		currentJob.addBinding(b);
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

		currentJob.addLamination(lam);
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
		ctpQtyCombo.setValue(null);
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

}