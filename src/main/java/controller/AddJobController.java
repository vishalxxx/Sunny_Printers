package controller; // adjust if needed

import java.io.File;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import model.Binding;
import model.Client;
import model.CtpPlate;
import model.Job;
import model.Lamination;
import model.Paper;
import model.Printing;
import model.Supplier;
import service.ClientService;
import service.JobItemService;
import service.JobService;
import service.SupplierService;
import utils.NavigationManager;

import java.util.List;

public class AddJobController implements utils.DirtySupport {

	private final javafx.collections.ObservableList<Object> pendingItems = javafx.collections.FXCollections.observableArrayList();

	@Override
	public boolean hasUnsavedChanges() {
		// Dirty if anything is entered or items are added
		boolean hasJobName = jobName.getText() != null && !jobName.getText().trim().isEmpty();
		boolean hasClient = selectedClient != null;
		boolean hasItems = !pendingItems.isEmpty();
		return hasJobName || hasClient || hasItems;
	}

	private Runnable onJobItemAdded;

	public void setOnJobItemAdded(Runnable r) {
		this.onJobItemAdded = r;
	}

	private int itemCount = 0;

	private void updateItemCount() {
		itemCount = pendingItems.size();

		long printCount = pendingItems.stream().filter(i -> i instanceof model.Printing).count();
		long ctpCount = pendingItems.stream().filter(i -> i instanceof model.CtpPlate).count();
		long paperCount = pendingItems.stream().filter(i -> i instanceof model.Paper).count();
		long bindingCount = pendingItems.stream().filter(i -> i instanceof model.Binding).count();
		long lamCount = pendingItems.stream().filter(i -> i instanceof model.Lamination).count();

		updateCounterLabel(printCountLabel, printCount);
		updateCounterLabel(ctpCountLabel, ctpCount);
		updateCounterLabel(paperCountLabel, paperCount);
		updateCounterLabel(bindingCountLabel, bindingCount);
		updateCounterLabel(laminationCountLabel, lamCount);
	}

	private void updateCounterLabel(Label lbl, long count) {
		if (lbl == null)
			return;
		if (count > 0) {
			lbl.setText(String.valueOf(count));
			lbl.setVisible(true);
		} else {
			lbl.setVisible(false);
		}
	}

	/* ========================= STATE ========================= */

	private void setupJobDatePicker(DatePicker dp) {

		dp.setEditable(false);

		// ✅ Disable future dates
		dp.setDayCellFactory(picker -> new javafx.scene.control.DateCell() {
			@Override
			public void updateItem(java.time.LocalDate date, boolean empty) {
				super.updateItem(date, empty);

				if (empty || date == null) return;

				if (date.isAfter(java.time.LocalDate.now())) {
					setDisable(true);
				}
			}
		});

		// ✅ extra safety
		dp.valueProperty().addListener((obs, oldVal, newVal) -> {
			if (newVal != null && newVal.isAfter(java.time.LocalDate.now())) {
				dp.setValue(oldVal);
				System.out.println("Future dates not allowed ❌");
			}
		});

		// ✅ Auto open on click
		dp.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
			if (!dp.isShowing()) dp.show();
		});

		dp.focusedProperty().addListener((obs, oldV, newV) -> {
			if (newV && !dp.isShowing()) dp.show();
		});
	}

	private Job currentJob;

	public void setCurrentJob(Job job) {
		this.currentJob = job;
	}

	/* ========================= TOP FIELDS ========================= */

	@FXML
	private Button addJobBtn;

	@FXML
	private Button breadcrumbBackBtn;

	@FXML
	private TextField jobName;

	@FXML
	private DatePicker jobDate;

	@FXML
	private ComboBox<Client> clientCombo;

	@FXML
	private RadioButton paperOurRadio;

	@FXML
	private RadioButton paperClientRadio;

	private ToggleGroup paperSourceGroup;

	@FXML
	private ImageView jobImagePreview;

	@FXML
	private VBox pdfPreviewBox;

	@FXML
	private VBox filePlaceholder;
	@FXML
	private VBox imagePreviewContainer;

	@FXML
	private ToggleButton sideDoubleBtn;
	@FXML
	private ToggleButton sideSingleBtn;
	private ToggleGroup sideGroup;

	@FXML
	private ToggleButton lamDoubleBtn;
	@FXML
	private ToggleButton lamSingleBtn;
	private ToggleGroup lamSideGroup;

	/* ========================= SERVICES ========================= */

	private final ClientService clientService = new ClientService();
	private final SupplierService supplierService = new SupplierService();

	/* ========================= CARDS ========================= */

	@FXML
	private VBox fileCard;
	@FXML
	private VBox printingCard;
	@FXML
	private VBox ctpCard;
	@FXML
	private VBox paperCard;
	@FXML
	private VBox bindingCard;
	@FXML
	private VBox laminationCard;

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
	@FXML
	private ComboBox<String> ctpColorCombo;

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
	private ComboBox<String> lamSizeCombo;
	@FXML
	private TextArea lamNotesArea;
	@FXML
	private TextField lamAmountField;

	/* ========================= ADD BUTTONS ========================= */

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

	@FXML
	private Label printCountLabel;
	@FXML
	private Label ctpCountLabel;
	@FXML
	private Label paperCountLabel;
	@FXML
	private Label bindingCountLabel;
	@FXML
	private Label laminationCountLabel;

	@FXML
	private Label jobNoLabel;

	/* ========================= STEPPER ========================= */
	@FXML private HBox step1Btn, step2Btn, step3Btn, step4Btn, step5Btn, step6Btn;
	@FXML private Button nextBtn;
	private int currentStep = 1;
	private final VBox[] stepCards = new VBox[7];
	private final HBox[] stepButtons = new HBox[7];

	@FXML
	private void handleNextStep() {
		if (currentStep < 6) {
			currentStep++;
			updateStepUI();
		}
	}

	@FXML
	private void handleCancel() {
		if (hasUnsavedChanges()) {
			if (!canDiscardChanges()) return;
		}
		MainController.getInstance().openCenterDashboard();
	}

	private boolean canDiscardChanges() {
		return MainController.getInstance().canDiscardChanges();
	}

	@FXML
	private void handleSaveDraft() {
		// Existing logic or simple toast for now
		toast("Draft saved locally ✅");
	}

	private void updateStepUI() {
		// Initialized if null
		if (stepCards[1] == null) {
			stepCards[1] = printingCard;
			stepCards[2] = ctpCard;
			stepCards[3] = paperCard;
			stepCards[4] = bindingCard;
			stepCards[5] = laminationCard;
			stepCards[6] = fileCard;

			stepButtons[1] = step1Btn;
			stepButtons[2] = step2Btn;
			stepButtons[3] = step3Btn;
			stepButtons[4] = step4Btn;
			stepButtons[5] = step5Btn;
			stepButtons[6] = step6Btn;
		}

		// Toggle Visibility
		for (int i = 1; i <= 6; i++) {
			if (stepCards[i] != null) {
				stepCards[i].setVisible(i == currentStep);
				stepCards[i].setManaged(i == currentStep);
			}
			if (stepButtons[i] != null) {
				stepButtons[i].getStyleClass().remove("step-active");
				if (i == currentStep) stepButtons[i].getStyleClass().add("step-active");
			}
		}

		// Toggle Next/Finish Button
		if (nextBtn != null && addJobBtn != null) {
			nextBtn.setVisible(currentStep < 6);
			nextBtn.setManaged(currentStep < 6);
			addJobBtn.setVisible(currentStep == 6);
			addJobBtn.setManaged(currentStep == 6);
		}
	}

	/* ========================= CLIENT COMBO STATE ========================= */

	private final ObservableList<Client> masterClients = javafx.collections.FXCollections.observableArrayList();

	private final javafx.collections.transformation.FilteredList<Client> filteredClients = new javafx.collections.transformation.FilteredList<>(
			masterClients);

	private boolean clientLocked = false;
	private Client selectedClient = null;

	/*
	 * =============================================================
	 * ✅ MAIN FEATURE:
	 * Cards + buttons disabled until:
	 * - client selected
	 * - jobName entered
	 * =============================================================
	 */
	private void updateFormState() {

		boolean hasClient = selectedClient != null;
		boolean hasJobName = jobName.getText() != null && !jobName.getText().trim().isEmpty();

		boolean allowCards = hasClient && hasJobName;

		disableAllCards(!allowCards);

		// ✅ disable all "Add ..." buttons until both are ready
		if (!allowCards) {
			addPrintingBtn.setDisable(true);
			addPlateBtn.setDisable(true);
			addPaperBtn.setDisable(true);
			addBindingBtn.setDisable(true);
			addLaminationBtn.setDisable(true);
		} else {
			// ✅ enable based on per-card validation
			addPrintingBtn.setDisable(!isPrintingValid());
			addPlateBtn.setDisable(!isCtpValid());
			addPaperBtn.setDisable(!isPaperValid());
			addBindingBtn.setDisable(!isBindingValid());
			addLaminationBtn.setDisable(!isLaminationValid());
		}

		// ✅ disable Add Job button until at least 1 item is added
		addJobBtn.setDisable(!allowCards || itemCount == 0);
	}

	/* ========================= JOB FLOW ========================= */

	public void startNewJob() {

		this.currentJob = new Job();
		// We don't call JobService.createDraftJob() anymore.
		// We just show a local placeholder for Job No if desired, or skip it.
		jobNoLabel.setText("Job No: NEW");
		addJobBtn.setText("Add Job ✅");

		// reset UI state
		clientCombo.setDisable(false);
		clientLocked = false;
		selectedClient = null;

		jobName.clear();
		jobDate.setValue(java.time.LocalDate.now());
		clientCombo.getSelectionModel().clearSelection();
		pendingItems.clear();

		updateItemCount();
		updateFormState();

		System.out.println("✅ New local job object initialized.");
	}

	// This method might be deprecated or used only for actual resumes of saved but incomplete jobs
	public void openForEdit(int jobId) {
		JobService jobService = new JobService();
		Job job = jobService.getJobById(jobId);
		if (job == null) {
			toast("❌ Job not found");
			return;
		}
		this.currentJob = job;
		jobNoLabel.setText("Job No: " + currentJob.getJobNo());
		addJobBtn.setText("Update Job ✅");
		preloadClientIntoCombo();
		jobName.setText(currentJob.getJobTitle());
		jobDate.setValue(currentJob.getJobDate() != null ? currentJob.getJobDate() : java.time.LocalDate.now());
		
		// Load items into pending
		pendingItems.setAll(new JobItemService().getJobItems(jobId));

		updateItemCount();
		updateFormState();
	}

	private void preloadClientIntoCombo() {

		if (currentJob == null || currentJob.getClientId() == null)
			return;

		Integer cid = currentJob.getClientId();

		Client match = masterClients.stream().filter(c -> c.getId() == cid).findFirst().orElse(null);

		if (match != null) {
			clientCombo.getSelectionModel().select(match);

			clientLocked = true;
			selectedClient = match;

			// optional (recommended)
			clientCombo.setDisable(true);
		}
	}

	/* ========================= UPLOAD FILE ========================= */
	private File selectedImageFile;

	@FXML
	private void handleUploadFile() {
		try {
			FileChooser chooser = new FileChooser();
			chooser.setTitle("Select Image or PDF");

			chooser.getExtensionFilters().addAll(
					new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"),
					new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

			File file = chooser.showOpenDialog(null);
			if (file == null)
				return;
				
			this.selectedImageFile = file;

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

		imagePreviewContainer.setVisible(true);
		imagePreviewContainer.setManaged(true);

		pdfPreviewBox.setVisible(false);
		pdfPreviewBox.setManaged(false);

		filePlaceholder.setVisible(false);
		filePlaceholder.setManaged(false);
	}

	private void showPdfPreview() {
		imagePreviewContainer.setVisible(false);
		imagePreviewContainer.setManaged(false);

		pdfPreviewBox.setVisible(true);
		pdfPreviewBox.setManaged(true);

		filePlaceholder.setVisible(false);
		filePlaceholder.setManaged(false);
	}

	@FXML
	private void handleResetUpload() {
		this.selectedImageFile = null;
		jobImagePreview.setImage(null);
		
		imagePreviewContainer.setVisible(false);
		imagePreviewContainer.setManaged(false);
		
		pdfPreviewBox.setVisible(false);
		pdfPreviewBox.setManaged(false);
		
		filePlaceholder.setVisible(true);
		filePlaceholder.setManaged(true);
	}
	
	private void resetUploadView() {
		jobImagePreview.setImage(null);
		selectedImageFile = null;

		jobImagePreview.setVisible(false);
		jobImagePreview.setManaged(false);

		pdfPreviewBox.setVisible(false);
		pdfPreviewBox.setManaged(false);

		filePlaceholder.setVisible(true);
		filePlaceholder.setManaged(true);
	}

	/* ========================= FINAL SAVE / UPDATE ========================= */

	@FXML
	private void handleAddJobButton() {
		if (selectedClient == null) {
			toast("Please Select Client..");
			return;
		}
		if (jobName.getText() == null || jobName.getText().isBlank()) {
			toast("Please Enter Job Name..");
			return;
		}
		java.time.LocalDate date = jobDate.getValue();
		if (date == null) {
			toast("Please Select Job Date..");
			return;
		}
		if (pendingItems.isEmpty()) {
			toast("Please add at least one job item (Printing, Paper, etc.)");
			return;
		}

		String title = jobName.getText().trim();
		JobService js = new JobService();

		java.sql.Connection con = null;
		try {
			con = utils.DBConnection.getConnection();
			con.setAutoCommit(false);

			// 1. Create Job or Update existing
			int jobId = currentJob.getId();
			if (jobId == 0) {
				// Create NEW job
				String jobNo = JobService.JobNumberGenerator.generate(con);
				currentJob.setJobNo(jobNo);
				currentJob.setClientId(selectedClient.getId());
				currentJob.setJobTitle(title);
				currentJob.setJobDate(date);
				currentJob.setStatus("Created");
				
				repository.JobRepository jobRepo = new repository.JobRepository();
				currentJob = jobRepo.insertJob(con, currentJob); // Insert full job
				jobId = currentJob.getId();
			} else {
				// Update existing (unlikely in this screen now, but for safety)
				js.updateJobDetails(jobId, title, date);
				js.updateJobStatus(jobId, "Created");
			}

			// 2. Save Item details
			JobItemService transJis = new JobItemService(con);
			for (Object item : pendingItems) {
				// If item is already a JobItem (from openForEdit), handle update?
				// For now, simplify: if it doesn't have an ID, add it.
				// In AddJobController, they are usually new.
				transJis.addJobItem(con, jobId, item);
			}

			// 3. Save Image
			if (selectedImageFile != null) {
				java.io.File dir = new java.io.File("Images");
				if (!dir.exists()) dir.mkdirs();
				String ext = "";
				String name = selectedImageFile.getName();
				int dotIndex = name.lastIndexOf('.');
				if (dotIndex > 0) ext = name.substring(dotIndex);
				String newFileName = "job_" + jobId + "_" + System.currentTimeMillis() + ext;
				java.io.File targetFile = new java.io.File(dir, newFileName);
				java.nio.file.Files.copy(selectedImageFile.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				String relativePath = "Images/" + newFileName;
				
				String updateImgQuery = "UPDATE jobs SET image_path = ? WHERE id = ?";
				try (java.sql.PreparedStatement ps = con.prepareStatement(updateImgQuery)) {
					ps.setString(1, relativePath);
					ps.setInt(2, jobId);
					ps.executeUpdate();
				}
				currentJob.setImagePath(relativePath);
			}

			con.commit();
			System.out.println("✅ Finalizing job: " + currentJob.getJobNo());
			toast("✅ Job Added Successfully!");
			
			// Clear dirty flags
			pendingItems.clear();
			jobName.clear();
			selectedClient = null;
			
			resetUploadView();
			MainController.getInstance().openCenterDashboard();

		} catch (Exception e) {
			if (con != null) { try { con.rollback(); } catch (Exception ex) {} }
			e.printStackTrace();
			toast("❌ Failed to save job: " + e.getMessage());
		} finally {
			if (con != null) { try { con.close(); } catch (Exception ex) {} }
		}
	}

	/* ========================= ADD ITEMS ========================= */

	@FXML
	private void handleAddPrinting() {

		Printing p = new Printing();

		try {
			if (printQtyField.getText() != null && !printQtyField.getText().isBlank())
				p.setQty(Integer.parseInt(printQtyField.getText()));
		} catch (Exception e) {
			toast("Printing Qty must be number ❌");
			return;
		}

		p.setUnits(printUnitsCombo.getValue());
		p.setSets(printSetField.getText());
		p.setColor(printColorCombo.getValue());
		p.setSide(sideDoubleBtn.isSelected() ? "Double" : "Single");
		p.setWithCtp("With CTP (Computer to Plate)".equalsIgnoreCase(printCtpCombo.getValue()));
		p.setNotes(printNotesArea.getText());

		try {
			if (printAmountField.getText() != null && !printAmountField.getText().isBlank()) {
				String amountStr = printAmountField.getText().replace(",", "");
				p.setAmount(Double.parseDouble(amountStr));
			}
		} catch (Exception e) {
			toast("Printing Amount must be number ❌");
			return;
		}

		pendingItems.add(p);
		toast("Printing Added ✅");
		clearPrintingFields();
		updateItemCount();
		updateFormState();
	}

	private void clearPrintingFields() {
		printQtyField.clear();
		printUnitsCombo.setValue(null);
		printSetField.clear();
		printColorCombo.setValue(null);
		sideDoubleBtn.setSelected(true);
		printCtpCombo.setValue(null);
		printNotesArea.clear();
		printAmountField.clear();
	}

	@FXML
	private void handleAddCtpPlate() {

		CtpPlate c = new CtpPlate();

		try {
			if (ctpQtyField.getText() != null && !ctpQtyField.getText().isBlank())
				c.setQty(Integer.parseInt(ctpQtyField.getText()));
		} catch (Exception e) {
			toast("CTP Qty must be number ❌");
			return;
		}

		c.setPlateSize(ctpSizeCombo.getValue());
		c.setGauge(ctpGaugeCombo.getValue());
		c.setBacking(ctpBackingCombo.getValue());
		c.setColor(ctpColorCombo.getValue());
		c.setNotes(ctpNotesArea.getText());

		Supplier supplier = ctpSupplierCombo.getValue();
		if (supplier != null) {
			c.setSupplierId(supplier.getId());
			c.setSupplierName(supplier.getName());
		}

		try {
			if (ctpAmountField.getText() != null && !ctpAmountField.getText().isBlank()) {
				String amountStr = ctpAmountField.getText().replace(",", "");
				c.setAmount(Double.parseDouble(amountStr));
			}
		} catch (Exception e) {
			toast("CTP Amount must be number ❌");
			return;
		}

		pendingItems.add(c);
		toast("CTP Plate Added ✅");
		clearCtpFields();
		updateItemCount();
		updateFormState();
	}

	private void clearCtpFields() {
		ctpQtyField.clear();
		ctpSizeCombo.setValue(null);
		ctpColorCombo.setValue(null);
		ctpGaugeCombo.setValue(null);
		ctpBackingCombo.setValue(null);
		ctpSupplierCombo.setValue(null);
		ctpNotesArea.clear();
		ctpAmountField.clear();
	}

	@FXML
	private void handleAddPaper() {

		Paper p = new Paper();

		try {
			if (paperQtyField.getText() != null && !paperQtyField.getText().isBlank())
				p.setQty(Integer.parseInt(paperQtyField.getText()));
		} catch (Exception e) {
			toast("Paper Qty must be number ❌");
			return;
		}

		p.setUnits(paperUnitsCombo.getValue());
		p.setSize(paperSizeCombo.getValue());
		p.setGsm(paperGsmCombo.getValue());
		p.setType(paperTypeCombo.getValue());
		p.setNotes(paperNotesArea.getText());

		String source = "Our";
		if (paperClientRadio.isSelected())
			source = "Client";
		p.setSource(source);

		try {
			if (paperAmountField.getText() != null && !paperAmountField.getText().isBlank()) {
				String amountStr = paperAmountField.getText().replace(",", "");
				p.setAmount(Double.parseDouble(amountStr));
			}
		} catch (Exception e) {
			toast("Paper Amount must be number ❌");
			return;
		}

		pendingItems.add(p);
		toast("Paper Added ✅");
		clearPaperFields();
		updateItemCount();
		updateFormState();
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

		try {
			if (bindingQtyField.getText() != null && !bindingQtyField.getText().isBlank())
				b.setQty(Integer.parseInt(bindingQtyField.getText()));
		} catch (Exception e) {
			toast("Binding Qty must be number ❌");
			return;
		}

		try {
			if (bindingRateField.getText() != null && !bindingRateField.getText().isBlank()) {
				String rateStr = bindingRateField.getText().replace(",", "");
				b.setRate(Double.parseDouble(rateStr));
			}
		} catch (Exception e) {
			toast("Binding Rate must be number ❌");
			return;
		}

		b.setNotes(bindingNotesArea.getText());

		try {
			if (bindingAmountField.getText() != null && !bindingAmountField.getText().isBlank()) {
				String amountStr = bindingAmountField.getText().replace(",", "");
				b.setAmount(Double.parseDouble(amountStr));
			}
		} catch (Exception e) {
			toast("Binding Amount must be number ❌");
			return;
		}

		pendingItems.add(b);
		toast("Binding Added ✅");
		clearBindingFields();
		updateItemCount();
		updateFormState();
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

		Lamination l = new Lamination();

		try {
			if (lamQtyField.getText() != null && !lamQtyField.getText().isBlank())
				l.setQty(Integer.parseInt(lamQtyField.getText()));
		} catch (Exception e) {
			toast("Lamination Qty must be number ❌");
			return;
		}

		l.setUnit(lamUnitCombo.getValue());
		l.setType(lamTypeCombo.getValue());
		l.setSide(lamDoubleBtn.isSelected() ? "Double Side" : "Single Side");
		l.setSize(lamSizeCombo.getValue());
		l.setNotes(lamNotesArea.getText());

		try {
			if (lamAmountField.getText() != null && !lamAmountField.getText().isBlank()) {
				String amountStr = lamAmountField.getText().replace(",", "");
				l.setAmount(Double.parseDouble(amountStr));
			}
		} catch (Exception e) {
			toast("Lamination Amount must be number ❌");
			return;
		}

		pendingItems.add(l);
		toast("Lamination Added ✅");
		clearLaminationFields();
		updateItemCount();
		updateFormState();
	}

	private void clearLaminationFields() {
		lamQtyField.clear();
		lamUnitCombo.setValue(null);
		lamTypeCombo.setValue(null);
		lamDoubleBtn.setSelected(true);
		lamSizeCombo.setValue(null);
		lamNotesArea.clear();
		lamAmountField.clear();
	}

	/* ========================= VALIDATION HELPERS ========================= */

	private void makeNumeric(TextField tf) {
		tf.setTextFormatter(new TextFormatter<>(change -> {
			return change.getControlNewText().matches("\\d*") ? change : null;
		}));
	}

	private void makeDecimal(TextField tf) {
		tf.setTextFormatter(new TextFormatter<>(change -> {
			if (change.getControlNewText().replace(",", "").matches("\\d*\\.?\\d*")) {
				return change;
			}
			return null;
		}));

		// ✅ Format with commas on focus lost
		tf.focusedProperty().addListener((obs, oldVal, newVal) -> {
			if (!newVal) { // Focus lost
				String text = tf.getText().replace(",", "");
				if (!text.isBlank()) {
					try {
						double val = Double.parseDouble(text);
						tf.setText(formatCurrency(val));
					} catch (Exception e) {}
				}
			} else { // Focus gained
				tf.setText(tf.getText().replace(",", ""));
			}
		});
	}

	private String formatCurrency(double amount) {
		java.text.NumberFormat formatter = java.text.NumberFormat.getInstance(new java.util.Locale("en", "IN"));
		formatter.setMinimumFractionDigits(2);
		formatter.setMaximumFractionDigits(2);
		return formatter.format(amount);
	}

	private boolean notEmpty(String s) {
		return s != null && !s.trim().isEmpty();
	}

	/* ========================= PRINTING VALIDATION ========================= */

	private boolean isPrintingValid() {
		return notEmpty(printAmountField.getText());
	}

	private void setupPrintingValidation() {
		Runnable validate = () -> updateFormState();

		printQtyField.textProperty().addListener((a, b, c) -> validate.run());
		printSetField.textProperty().addListener((a, b, c) -> validate.run());
		printAmountField.textProperty().addListener((a, b, c) -> validate.run());
		printNotesArea.textProperty().addListener((a, b, c) -> validate.run());
		printUnitsCombo.valueProperty().addListener((a, b, c) -> validate.run());

		validate.run();
	}

	/* ========================= CTP VALIDATION ========================= */

	private boolean isCtpValid() {
		return notEmpty(ctpAmountField.getText());
	}

	private void setupCtpValidation() {
		Runnable validate = () -> updateFormState();

		ctpQtyField.textProperty().addListener((a, b, c) -> validate.run());
		ctpSizeCombo.valueProperty().addListener((a, b, c) -> validate.run());
		ctpAmountField.textProperty().addListener((a, b, c) -> validate.run());
		ctpNotesArea.textProperty().addListener((a, b, c) -> validate.run());
		ctpSupplierCombo.valueProperty().addListener((a, b, c) -> validate.run());

		validate.run();
	}

	/* ========================= PAPER VALIDATION ========================= */

	private boolean isPaperValid() {
		return notEmpty(paperAmountField.getText());
	}

	private void setupPaperValidation() {
		Runnable validate = () -> updateFormState();

		paperQtyField.textProperty().addListener((a, b, c) -> validate.run());
		paperAmountField.textProperty().addListener((a, b, c) -> validate.run());
		paperNotesArea.textProperty().addListener((a, b, c) -> validate.run());
		paperUnitsCombo.valueProperty().addListener((a, b, c) -> validate.run());
		paperSizeCombo.valueProperty().addListener((a, b, c) -> validate.run());

		validate.run();
	}

	/* ========================= BINDING VALIDATION ========================= */

	private boolean isBindingValid() {
		return notEmpty(bindingAmountField.getText());
	}

	private void setupBindingValidation() {
		Runnable validate = () -> updateFormState();

		bindingProcessCombo.valueProperty().addListener((a, b, c) -> validate.run());
		bindingQtyField.textProperty().addListener((a, b, c) -> validate.run());
		bindingRateField.textProperty().addListener((a, b, c) -> validate.run());
		bindingAmountField.textProperty().addListener((a, b, c) -> validate.run());
		bindingNotesArea.textProperty().addListener((a, b, c) -> validate.run());

		validate.run();
	}

	/* ========================= LAMINATION VALIDATION ========================= */

	private boolean isLaminationValid() {
		return notEmpty(lamAmountField.getText());
	}

	private void setupLaminationValidation() {
		Runnable validate = () -> updateFormState();

		lamQtyField.textProperty().addListener((a, b, c) -> validate.run());
		lamUnitCombo.valueProperty().addListener((a, b, c) -> validate.run());
		lamTypeCombo.valueProperty().addListener((a, b, c) -> validate.run());
		lamAmountField.textProperty().addListener((a, b, c) -> validate.run());
		lamNotesArea.textProperty().addListener((a, b, c) -> validate.run());

		validate.run();
	}

	/* ========================= INITIALIZE ========================= */

	@FXML
	public void initialize() {
		if (breadcrumbBackBtn != null) {
			breadcrumbBackBtn.visibleProperty().bind(NavigationManager.getInstance().canGoBackProperty());
			breadcrumbBackBtn.managedProperty().bind(breadcrumbBackBtn.visibleProperty());
		}
		setupJobDatePicker(jobDate);

		filteredClients.setPredicate(c -> true);

		// ✅ Load clients (Background)
		new Thread(() -> {
			try {
				// Small delay to let UI stabilize
				Thread.sleep(50);
				List<Client> clients = clientService.getAllClients();
				javafx.application.Platform.runLater(() -> {
					masterClients.setAll(clients);
					updateFormState();
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
		}).start();
		clientCombo.setItems(filteredClients);
		clientCombo.setEditable(false);

		// ✅ UI setup
		clientCombo.setCellFactory(lv -> new ListCell<>() {
			@Override
			protected void updateItem(Client c, boolean empty) {
				super.updateItem(c, empty);
				if (empty || c == null) {
					setText(null);
				} else {
					setText(c.getBusinessName() + " (" + c.getClientName() + ")");
				}
			}
		});

		clientCombo.setButtonCell(new ListCell<>() {
			@Override
			protected void updateItem(Client c, boolean empty) {
				super.updateItem(c, empty);
				if (empty || c == null) {
					setText("Select Client");
				} else {
					setText(c.getBusinessName());
				}
			}
		});

		clientCombo.setConverter(new StringConverter<>() {
			@Override
			public String toString(Client c) {
				return c == null ? ""
						: c.getBusinessName() + " | " + c.getClientName() + " | " + c.getGst() + " | " + c.getPhone();
			}

			@Override
			public Client fromString(String s) {
				return clientCombo.getValue();
			}
		});

		// ✅ Live search
		clientCombo.getEditor().textProperty().addListener((obs, oldText, newText) -> {

			if (clientLocked)
				return;

			String search = newText == null ? "" : newText.trim().toLowerCase();

			filteredClients.setPredicate(client -> search.isEmpty()
					|| client.getBusinessName().toLowerCase().contains(search)
					|| client.getClientName().toLowerCase().contains(search)
					|| (client.getPhone() != null && client.getPhone().contains(search)));

			if (!clientCombo.isShowing()) {
				clientCombo.show();
			}
		});

		// ✅ selection listener
		clientCombo.valueProperty().addListener((obs, oldClient, newClient) -> {

			if (newClient == null) {
				clientLocked = false;
				selectedClient = null;
				updateFormState();
				return;
			}

			clientLocked = true;
			selectedClient = newClient;

			if (currentJob != null && currentJob.getId() > 0) {
				new JobService().assignClient(currentJob, newClient.getId());
			}

			updateFormState();
		});

		// ✅ re-enable search click
		clientCombo.getEditor().setOnMouseClicked(e -> {
			clientLocked = false;
			filteredClients.setPredicate(null);
		});

		// ✅ Job name listener (IMPORTANT)
		jobName.textProperty().addListener((obs, oldVal, newVal) -> updateFormState());

		// ✅ numeric restrictions
		makeNumeric(printQtyField);
		makeNumeric(printSetField);
		makeDecimal(printAmountField);
		makeNumeric(ctpQtyField);
		makeDecimal(ctpAmountField);
		makeNumeric(paperQtyField);
		makeDecimal(paperAmountField);
		makeNumeric(bindingQtyField);
		makeDecimal(bindingRateField);
		makeDecimal(bindingAmountField);
		makeNumeric(lamQtyField);
		makeDecimal(lamAmountField);

		// ✅ validations
		setupPrintingValidation();
		setupCtpValidation();
		setupPaperValidation();
		setupBindingValidation();
		setupLaminationValidation();

		// ✅ suppliers
		loadSuppliers();

		// ✅ paper source
		paperSourceGroup = new ToggleGroup();
		paperOurRadio.setToggleGroup(paperSourceGroup);
		paperClientRadio.setToggleGroup(paperSourceGroup);
		paperOurRadio.setSelected(true);

		// ✅ side toggle
		sideGroup = new ToggleGroup();
		sideDoubleBtn.setToggleGroup(sideGroup);
		sideSingleBtn.setToggleGroup(sideGroup);
		sideDoubleBtn.setSelected(true);

		lamSideGroup = new ToggleGroup();
		lamDoubleBtn.setToggleGroup(lamSideGroup);
		lamSingleBtn.setToggleGroup(lamSideGroup);
		lamDoubleBtn.setSelected(true);

		// ✅ step buttons jump
		step1Btn.setOnMouseClicked(e -> { currentStep = 1; updateStepUI(); });
		step2Btn.setOnMouseClicked(e -> { currentStep = 2; updateStepUI(); });
		step3Btn.setOnMouseClicked(e -> { currentStep = 3; updateStepUI(); });
		step4Btn.setOnMouseClicked(e -> { currentStep = 4; updateStepUI(); });
		step5Btn.setOnMouseClicked(e -> { currentStep = 5; updateStepUI(); });
		step6Btn.setOnMouseClicked(e -> { currentStep = 6; updateStepUI(); });

		// ✅ Fill dropdowns
		populateCombos();

		// ✅ initial lock state
		updateFormState();
		updateStepUI();
	}

	@FXML
	private void handleBack(javafx.event.Event e) {
		MainController.getInstance().handleBack(e);
	}

	private void populateCombos() {
		// Printing
		printUnitsCombo.getItems().setAll("Copies", "Sets", "Rim", "Pkt", "Sheet");
		printColorCombo.getItems().setAll("1", "2", "4", "4+4", "Spot", "Custom");
		printCtpCombo.getItems().setAll("With CTP (Computer to Plate)", "Without CTP");

		// CTP
		ctpSizeCombo.getItems().setAll("23x36", "25x36", "19x25", "18x23", "15x20", "20x30", "10x15");
		ctpGaugeCombo.getItems().setAll("0.15mm", "0.28mm", "0.30mm");
		ctpColorCombo.getItems().setAll("CMYK (Full)", "Cyan", "Magenta", "Yellow", "Black", "Spot Color");
		ctpBackingCombo.getItems().setAll("Paper Backing", "Plastic Backing", "None");

		// Paper
		paperUnitsCombo.getItems().setAll("Rim", "Sheet", "Pkt", "Kg");
		paperSizeCombo.getItems().setAll("23x36", "25x36", "18x23", "20x30", "Custom");
		paperGsmCombo.getItems().setAll("60", "70", "80", "90", "100", "130", "170", "210", "250", "300");
		paperTypeCombo.getItems().setAll("Art Paper", "Art Card", "Maplitho", "Sunshine", "Chromo", "Mirror Coat", "Texture", "Bond");

		// Binding
		bindingProcessCombo.getItems().setAll("Center Pin", "Perfect Binding", "Hard Bound", "Spiral", "Wire-O", "Crease & Fold", "Cutting Only");

		// Lamination
		lamUnitCombo.getItems().setAll("Sq. Inch", "Piece", "Sheet", "Meter");
		lamTypeCombo.getItems().setAll("Gloss", "Matt", "Velvet", "Thermal Gloss", "Thermal Matt", "UV Coating");
		lamSizeCombo.getItems().setAll("23x36", "25x36", "18x23", "20x30", "Custom");
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

	private void disableAllCards(boolean disable) {
		printingCard.setDisable(disable);
		ctpCard.setDisable(disable);
		paperCard.setDisable(disable);
		bindingCard.setDisable(disable);
		laminationCard.setDisable(disable);
	}

	private void toast(String message) {
		Stage stage = (Stage) ((Node) clientCombo).getScene().getWindow();
		utils.Toast.show(stage, message);
	}
}
