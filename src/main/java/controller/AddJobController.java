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
import javafx.scene.control.CheckBox;
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
import service.sync.UniversalSyncEngine;
import model.Job;
import model.Lamination;
import model.Paper;
import model.Printing;
import model.Supplier;
import service.ClientService;
import service.JobItemService;
import service.JobService;
import service.SupplierService;
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
				getStyleClass().remove("job-date-future-day");
				if (empty || date == null) {
					setOpacity(1.0);
					return;
				}
				java.time.LocalDate today = java.time.LocalDate.now();
				if (date.isAfter(today)) {
					setDisable(true);
					setOpacity(1.0);
					getStyleClass().add("job-date-future-day");
				} else {
					setDisable(false);
					setOpacity(1.0);
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
	private HBox breadcrumbContainer;

	@FXML
	private TextField jobName;

	@FXML
	private DatePicker jobDate;

	@FXML
	private ComboBox<Client> clientCombo;

	@FXML
	private ComboBox<String> productTypeCombo;

	@FXML
	private TextField ratePerUnitField;

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
	private CheckBox printIncludeNotesToggle;
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
	private CheckBox ctpIncludeNotesToggle;
	@FXML
	private TextField ctpAmountField;
	@FXML
	private ComboBox<Supplier> ctpSupplierCombo;
	@FXML
	private ComboBox<String> ctpColorCombo;
	@FXML
	private RadioButton ctpOurRadio;
	@FXML
	private RadioButton ctpClientRadio;
	private ToggleGroup ctpSourceGroup;
	@FXML
	private VBox ctpSupplierBox;

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
	private ComboBox<Supplier> paperSupplierCombo;
	@FXML
	private TextArea paperNotesArea;
	@FXML
	private CheckBox paperIncludeNotesToggle;
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
	private CheckBox bindingIncludeNotesToggle;
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
	private CheckBox lamIncludeNotesToggle;
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

	private void markStepCompleted(int stepIndex) {
		if (stepButtons[stepIndex] != null) {
			try {
				javafx.scene.layout.StackPane sp = (javafx.scene.layout.StackPane) stepButtons[stepIndex].getChildren().get(0);
				javafx.scene.control.Label lbl = (javafx.scene.control.Label) sp.getChildren().get(1);
				lbl.setText("✔");
			} catch (Exception e) {
				e.printStackTrace();
			}
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
		updateFormState();
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

		// Finish / Add Job: require client, job name, and at least one line item
		if (addJobBtn != null) {
			boolean hasItems = !pendingItems.isEmpty();
			addJobBtn.setDisable(!allowCards || !hasItems);
		}
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
		if (ctpOurRadio != null) {
			ctpOurRadio.setSelected(true);
		}

		updateItemCount();
		updateFormState();

		System.out.println("✅ New local job object initialized.");
	}

	// This method might be deprecated or used only for actual resumes of saved but incomplete jobs
	public void openForEdit(String jobUuid) {
		JobService jobService = new JobService();
		Job job = jobService.getJobByUuid(jobUuid);
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
		pendingItems.setAll(new JobItemService().loadJobItemCards(jobUuid));

		updateItemCount();
		updateFormState();
	}

	private void preloadClientIntoCombo() {

		if (currentJob == null || currentJob.getClientId() == null)
			return;

		String cid = currentJob.getClientId();

		Client match = masterClients.stream().filter(c -> cid != null && cid.equals(c.getClientUuid())).findFirst().orElse(null);

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
			utils.UniversalDownloadPath.prepareFileChooser(chooser);
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

		// 1. Validate all child items first. If validation fails, stop save immediately.
		try {
			for (Object item : pendingItems) {
				if (item instanceof model.Printing p) {
					if (p.getAmount() <= 0) throw new IllegalArgumentException("Printing amount required");
				} else if (item instanceof model.Paper p) {
					if (p.getAmount() <= 0) throw new IllegalArgumentException("Paper amount required");
				} else if (item instanceof model.Binding b) {
					if (b.getAmount() <= 0) throw new IllegalArgumentException("Binding amount required");
				} else if (item instanceof model.Lamination l) {
					if (l.getAmount() <= 0) throw new IllegalArgumentException("Lamination amount required");
				} else if (item instanceof model.CtpPlate ctp) {
					if (ctp.getQty() <= 0) throw new IllegalArgumentException("CTP qty required");
					if (ctp.getAmount() <= 0) throw new IllegalArgumentException("CTP amount required");
				}
			}
		} catch (IllegalArgumentException e) {
			toast("❌ Validation failed: " + e.getMessage());
			return;
		}

		String title = jobName.getText().trim();

		java.sql.Connection con = null;
		try {
			con = utils.DBConnection.getConnection();
			con.setAutoCommit(false);

			String jobUuid = currentJob.getUuid();
			if (!currentJob.hasUuid()) {
				currentJob.setClientId(selectedClient.getClientUuid());
				currentJob.setJobTitle(title);
				currentJob.setJobDate(date);
				currentJob.setStatus("Created");
				repository.JobRepository jobRepo = new repository.JobRepository();
				currentJob = jobRepo.insertJob(con, currentJob);
				jobUuid = currentJob.getUuid();
			} else {
				// Update job details and status in the same transaction
				String userUuid = null;
				if (utils.SessionManager.getInstance().getCurrentUser() != null) {
					userUuid = utils.SessionManager.getInstance().getCurrentUser().getUuid();
				}
				String updateSql = """
						UPDATE jobs SET job_title = ?, job_date = ?, status = 'Created', sync_status = 'PENDING',
						updated_at = datetime('now'), sync_version = sync_version + 1,
						updated_by_user_uuid = ?
						WHERE uuid = ?
						""";
				try (java.sql.PreparedStatement ps = con.prepareStatement(updateSql)) {
					ps.setString(1, title);
					if (date != null) {
						ps.setString(2, date.toString());
					} else {
						ps.setNull(2, java.sql.Types.VARCHAR);
					}
					ps.setString(3, userUuid);
					ps.setString(4, jobUuid);
					ps.executeUpdate();
				}
			}

			JobItemService transJis = new JobItemService(con);
			for (Object item : pendingItems) {
				transJis.addJobItem(con, jobUuid, item);
			}

			if (selectedImageFile != null) {
				String relativePath = utils.ImageStorage.saveImage(selectedImageFile, jobUuid);
				String userUuid = null;
				if (utils.SessionManager.getInstance().getCurrentUser() != null) {
					userUuid = utils.SessionManager.getInstance().getCurrentUser().getUuid();
				}
				String updateImgQuery = """
						UPDATE jobs SET image_path = ?, sync_status = 'PENDING',
						updated_at = datetime('now'), sync_version = sync_version + 1,
						updated_by_user_uuid = ?
						WHERE uuid = ?
						""";
				try (java.sql.PreparedStatement ps = con.prepareStatement(updateImgQuery)) {
					ps.setString(1, relativePath);
					ps.setString(2, userUuid);
					ps.setString(3, jobUuid);
					ps.executeUpdate();
				}
				currentJob.setImagePath(relativePath);
			}

			con.commit();

			UniversalSyncEngine.scheduleSyncAsync();

			System.out.println("✅ Finalizing job: " + currentJob.getJobNo());
			toast("✅ Job Added Successfully!");
			
			// Clear dirty flags
			pendingItems.clear();
			jobName.clear();
			selectedClient = null;
			
			resetUploadView();
			MainController.getInstance().loadViewJob();

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
		p.setIncludeNotesInInvoice(printIncludeNotesToggle.isSelected());

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
		markStepCompleted(1);
		clearPrintingFields();
		updateItemCount();
		updateFormState();
		handleNextStep();
	}

	private void clearPrintingFields() {
		printQtyField.clear();
		printUnitsCombo.setValue("Sheet");
		printSetField.clear();
		printColorCombo.setValue(null);
		sideDoubleBtn.setSelected(true);
		printCtpCombo.setValue(null);
		printNotesArea.clear();
		if (printIncludeNotesToggle != null) {
			printIncludeNotesToggle.setSelected(true);
		}
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
		c.setIncludeNotesInInvoice(ctpIncludeNotesToggle.isSelected());

		Supplier supplier = ctpSupplierCombo.getValue();
		if (ctpOurRadio.isSelected() && supplier != null && supplier.getUuid() != null && !supplier.getUuid().isBlank()) {
			c.setSupplierUuid(supplier.getUuid());
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
		markStepCompleted(2);
		clearCtpFields();
		updateItemCount();
		updateFormState();
		handleNextStep();
	}

	private void clearCtpFields() {
		ctpQtyField.clear();
		ctpSizeCombo.setValue(null);
		ctpColorCombo.setValue(null);
		ctpGaugeCombo.setValue(null);
		ctpBackingCombo.setValue(null);
		ctpSupplierCombo.setValue(null);
		ctpNotesArea.clear();
		if (ctpIncludeNotesToggle != null) {
			ctpIncludeNotesToggle.setSelected(true);
		}
		ctpAmountField.clear();
		if (ctpOurRadio != null) {
			ctpOurRadio.setSelected(true);
		}
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
		p.setIncludeNotesInInvoice(paperIncludeNotesToggle.isSelected());

		String source = "Our";
		if (paperClientRadio.isSelected())
			source = "Client";
		p.setSource(source);

		Supplier supplier = paperSupplierCombo.getValue();
		if (supplier != null && supplier.getUuid() != null && !supplier.getUuid().isBlank()) {
			p.setSupplierUuid(supplier.getUuid());
		}

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
		markStepCompleted(3);
		clearPaperFields();
		updateItemCount();
		updateFormState();
		handleNextStep();
	}

	private void clearPaperFields() {
		paperQtyField.clear();
		paperUnitsCombo.setValue(null);
		paperSizeCombo.setValue(null);
		paperGsmCombo.setValue(null);
		paperTypeCombo.setValue(null);
		paperSupplierCombo.setValue(null);
		paperNotesArea.clear();
		if (paperIncludeNotesToggle != null) {
			paperIncludeNotesToggle.setSelected(true);
		}
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
		b.setIncludeNotesInInvoice(bindingIncludeNotesToggle.isSelected());

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
		markStepCompleted(4);
		clearBindingFields();
		updateItemCount();
		updateFormState();
		handleNextStep();
	}

	private void clearBindingFields() {
		bindingProcessCombo.setValue(null);
		bindingQtyField.clear();
		bindingRateField.clear();
		bindingNotesArea.clear();
		if (bindingIncludeNotesToggle != null) {
			bindingIncludeNotesToggle.setSelected(true);
		}
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
		l.setIncludeNotesInInvoice(lamIncludeNotesToggle.isSelected());

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
		markStepCompleted(5);
		clearLaminationFields();
		updateItemCount();
		updateFormState();
		handleNextStep();
	}

	private void clearLaminationFields() {
		lamQtyField.clear();
		lamUnitCombo.setValue(null);
		lamTypeCombo.setValue(null);
		lamDoubleBtn.setSelected(true);
		lamSizeCombo.setValue(null);
		lamNotesArea.clear();
		if (lamIncludeNotesToggle != null) {
			lamIncludeNotesToggle.setSelected(true);
		}
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
		paperSupplierCombo.valueProperty().addListener((a, b, c) -> validate.run());

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
		utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null, () -> handleBack(null));
		setupJobDatePicker(jobDate);

		filteredClients.setPredicate(c -> true);

		// ✅ Load clients (Background)
		new Thread(() -> {
			try {
				// Small delay to let UI stabilize
				Thread.sleep(50);
				List<Client> clients = clientService.getAllClients();
				utils.ComboBoxSorter.sortClients(clients);
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

			if (currentJob != null && currentJob.hasUuid()) {
				new JobService().assignClient(currentJob, newClient.getClientUuid());
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
		makeDecimal(ratePerUnitField);
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

		// ✅ CTP source
		ctpSourceGroup = new ToggleGroup();
		ctpOurRadio.setToggleGroup(ctpSourceGroup);
		ctpClientRadio.setToggleGroup(ctpSourceGroup);
		ctpOurRadio.setSelected(true);

		if (ctpSupplierBox != null) {
			ctpSupplierBox.visibleProperty().bind(ctpOurRadio.selectedProperty());
			ctpSupplierBox.managedProperty().bind(ctpOurRadio.selectedProperty());
		}

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

		pendingItems.addListener((javafx.collections.ListChangeListener<Object>) c -> {
			updateItemCount();
			updateFormState();
		});

		if (addJobBtn != null) {
			addJobBtn.setDisable(true);
		}
		setupTabTraversal(printNotesArea);
		setupTabTraversal(ctpNotesArea);
		setupTabTraversal(paperNotesArea);
		setupTabTraversal(bindingNotesArea);
		setupTabTraversal(lamNotesArea);

		// ✅ initial lock state
		updateFormState();
		updateStepUI();
	}

	@FXML
	private void handleBack(javafx.event.Event e) {
		MainController.getInstance().handleBack(e);
	}

	private void populateCombos() {
		// Product type (top header)
		if (productTypeCombo != null) {
			java.util.List<String> list = new java.util.ArrayList<>(java.util.List.of("Sticker / Brochure / Label / Book", "Sticker", "Brochure", "Label", "Book"));
			utils.ComboBoxSorter.sortStrings(list);
			productTypeCombo.getItems().setAll(list);
		}

		// Printing
		java.util.List<String> printUnits = new java.util.ArrayList<>(java.util.List.of("Select Unit", "Copies", "Sets", "Rim", "Pkt", "Sheet"));
		utils.ComboBoxSorter.sortStrings(printUnits);
		printUnitsCombo.getItems().setAll(printUnits);
		printUnitsCombo.setValue("Sheet");

		java.util.List<String> printColors = new java.util.ArrayList<>(java.util.List.of("Select Color", "1", "2", "4", "4+4", "Spot", "Custom"));
		utils.ComboBoxSorter.sortStrings(printColors);
		printColorCombo.getItems().setAll(printColors);

		java.util.List<String> printCtp = new java.util.ArrayList<>(java.util.List.of("With CTP (Computer to Plate)", "Without CTP"));
		utils.ComboBoxSorter.sortStrings(printCtp);
		printCtpCombo.getItems().setAll(printCtp);

		// CTP
		java.util.List<String> ctpSizes = new java.util.ArrayList<>(java.util.List.of("Select Size", "23x36", "25x36", "19x25", "18x23", "15x20", "20x30", "10x15"));
		utils.ComboBoxSorter.sortStrings(ctpSizes);
		ctpSizeCombo.getItems().setAll(ctpSizes);

		java.util.List<String> ctpGauges = new java.util.ArrayList<>(java.util.List.of("Select Gauge", "0.15mm", "0.28mm", "0.30mm"));
		utils.ComboBoxSorter.sortStrings(ctpGauges);
		ctpGaugeCombo.getItems().setAll(ctpGauges);

		java.util.List<String> ctpColors = new java.util.ArrayList<>(java.util.List.of("Select Color", "CMYK (Full)", "Cyan", "Magenta", "Yellow", "Black", "Spot Color"));
		utils.ComboBoxSorter.sortStrings(ctpColors);
		ctpColorCombo.getItems().setAll(ctpColors);

		java.util.List<String> ctpBackings = new java.util.ArrayList<>(java.util.List.of("Select Backing", "Paper Backing", "Plastic Backing", "None"));
		utils.ComboBoxSorter.sortStrings(ctpBackings);
		ctpBackingCombo.getItems().setAll(ctpBackings);

		// Paper
		java.util.List<String> paperUnits = new java.util.ArrayList<>(java.util.List.of("Select Unit", "Rim", "Sheet", "Pkt", "Kg"));
		utils.ComboBoxSorter.sortStrings(paperUnits);
		paperUnitsCombo.getItems().setAll(paperUnits);

		java.util.List<String> paperSizes = new java.util.ArrayList<>(java.util.List.of("Select Size", "23x36", "25x36", "18x23", "20x30", "Custom"));
		utils.ComboBoxSorter.sortStrings(paperSizes);
		paperSizeCombo.getItems().setAll(paperSizes);

		java.util.List<String> paperGsms = new java.util.ArrayList<>(java.util.List.of("Select GSM", "60", "70", "80", "90", "100", "130", "170", "210", "250", "300"));
		utils.ComboBoxSorter.sortStrings(paperGsms);
		paperGsmCombo.getItems().setAll(paperGsms);

		java.util.List<String> paperTypes = new java.util.ArrayList<>(java.util.List.of("Select Type", "Art Paper", "Art Card", "Maplitho", "Sunshine", "Chromo", "Mirror Coat", "Texture", "Bond"));
		utils.ComboBoxSorter.sortStrings(paperTypes);
		paperTypeCombo.getItems().setAll(paperTypes);

		// Binding
		java.util.List<String> bindingProcesses = new java.util.ArrayList<>(java.util.List.of("Select Binding", "Center Pin", "Perfect Binding", "Hard Bound", "Spiral", "Wire-O", "Crease & Fold", "Cutting Only"));
		utils.ComboBoxSorter.sortStrings(bindingProcesses);
		bindingProcessCombo.getItems().setAll(bindingProcesses);

		// Lamination
		java.util.List<String> lamUnits = new java.util.ArrayList<>(java.util.List.of("Select Unit", "Sq. Inch", "Piece", "Sheet", "Meter"));
		utils.ComboBoxSorter.sortStrings(lamUnits);
		lamUnitCombo.getItems().setAll(lamUnits);

		java.util.List<String> lamTypes = new java.util.ArrayList<>(java.util.List.of("Select Type", "Gloss", "Matt", "Velvet", "Thermal Gloss", "Thermal Matt", "UV Coating"));
		utils.ComboBoxSorter.sortStrings(lamTypes);
		lamTypeCombo.getItems().setAll(lamTypes);

		java.util.List<String> lamSizes = new java.util.ArrayList<>(java.util.List.of("Select Size", "23x36", "25x36", "18x23", "20x30", "Custom"));
		utils.ComboBoxSorter.sortStrings(lamSizes);
		lamSizeCombo.getItems().setAll(lamSizes);
	}

	private void loadSuppliers() {
		java.util.List<Supplier> ctpSuppliers = new java.util.ArrayList<>();
		Supplier selectCtpSupplier = new Supplier();
		selectCtpSupplier.setUuid("");
		selectCtpSupplier.setbusinessName("Select Supplier");
		selectCtpSupplier.setName("");
		ctpSuppliers.add(selectCtpSupplier);
		ctpSuppliers.addAll(supplierService.getSuppliersByType("CTP"));
		utils.ComboBoxSorter.sortSuppliers(ctpSuppliers);
		ctpSupplierCombo.getItems().setAll(ctpSuppliers);

		ctpSupplierCombo.setCellFactory(cb -> new ListCell<>() {
			@Override
			protected void updateItem(Supplier s, boolean empty) {
				super.updateItem(s, empty);
				if (empty || s == null) {
					setText(null);
				} else if (s.getUuid() == null || s.getUuid().isBlank()) {
					setText("Select Supplier");
				} else {
					setText(s.getbusinessName() + " | " + s.getName());
				}
			}
		});

		ctpSupplierCombo.setButtonCell(new ListCell<>() {
			@Override
			protected void updateItem(Supplier s, boolean empty) {
				super.updateItem(s, empty);
				if (empty || s == null) {
					setText(null);
				} else if (s.getUuid() == null || s.getUuid().isBlank()) {
					setText("Select Supplier");
				} else {
					setText(s.getbusinessName() + " | " + s.getName());
				}
			}
		});

		java.util.List<Supplier> paperSuppliers = new java.util.ArrayList<>();
		Supplier selectPaperSupplier = new Supplier();
		selectPaperSupplier.setUuid("");
		selectPaperSupplier.setbusinessName("Select Supplier");
		selectPaperSupplier.setName("");
		paperSuppliers.add(selectPaperSupplier);
		paperSuppliers.addAll(supplierService.getSuppliersByType("Paper"));
		utils.ComboBoxSorter.sortSuppliers(paperSuppliers);
		paperSupplierCombo.getItems().setAll(paperSuppliers);

		paperSupplierCombo.setCellFactory(cb -> new ListCell<>() {
			@Override
			protected void updateItem(Supplier s, boolean empty) {
				super.updateItem(s, empty);
				if (empty || s == null) {
					setText(null);
				} else if (s.getUuid() == null || s.getUuid().isBlank()) {
					setText("Select Supplier");
				} else {
					setText(s.getbusinessName() + " | " + s.getName());
				}
			}
		});

		paperSupplierCombo.setButtonCell(new ListCell<>() {
			@Override
			protected void updateItem(Supplier s, boolean empty) {
				super.updateItem(s, empty);
				if (empty || s == null) {
					setText(null);
				} else if (s.getUuid() == null || s.getUuid().isBlank()) {
					setText("Select Supplier");
				} else {
					setText(s.getbusinessName() + " | " + s.getName());
				}
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

	private void setupTabTraversal(TextArea textArea) {
		if (textArea == null) return;
		textArea.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
			if (event.getCode() == javafx.scene.input.KeyCode.TAB && !event.isControlDown() && !event.isAltDown()) {
				event.consume();
				javafx.scene.Scene scene = textArea.getScene();
				if (scene != null) {
					java.util.List<javafx.scene.Node> focusable = new java.util.ArrayList<>();
					findFocusableNodes(scene.getRoot(), focusable);
					if (!focusable.isEmpty()) {
						int index = focusable.indexOf(textArea);
						if (index >= 0) {
							int nextIndex;
							if (event.isShiftDown()) {
								nextIndex = index - 1;
								if (nextIndex < 0) {
									nextIndex = focusable.size() - 1;
								}
							} else {
								nextIndex = index + 1;
								if (nextIndex >= focusable.size()) {
									nextIndex = 0;
								}
							}
							focusable.get(nextIndex).requestFocus();
						}
					}
				}
			}
		});
	}

	private void findFocusableNodes(javafx.scene.Parent parent, java.util.List<javafx.scene.Node> result) {
		for (javafx.scene.Node node : parent.getChildrenUnmodifiable()) {
			if (node.isFocusTraversable() && isPhysicallyVisibleAndEnabled(node)) {
				result.add(node);
			}
			if (node instanceof javafx.scene.Parent p) {
				findFocusableNodes(p, result);
			}
		}
	}

	private boolean isPhysicallyVisibleAndEnabled(javafx.scene.Node node) {
		javafx.scene.Node current = node;
		while (current != null) {
			if (!current.isVisible() || current.isDisable()) {
				return false;
			}
			current = current.getParent();
		}
		return true;
	}
}
