package controller; // adjust if needed

import java.io.File;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
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
import utils.Toast;

public class AddJobController {

	private Runnable onJobItemAdded;

	public void setOnJobItemAdded(Runnable r) {
	    this.onJobItemAdded = r;
	}



	
	/* ========================= STATE ========================= */

	private boolean editMode = false;
	private Job currentJob;

	public void setCurrentJob(Job job) {
		this.currentJob = job;
	}

	/* ========================= TOP FIELDS ========================= */

	@FXML
	private Button addJobBtn;

	@FXML
	private TextField jobName;

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
	private Label filePlaceholder;

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
	private ComboBox<String> lamSideCombo;
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
	private Label jobNoLabel;


	/* ========================= CLIENT COMBO STATE ========================= */

	private final ObservableList<Client> masterClients = javafx.collections.FXCollections.observableArrayList();

	private final javafx.collections.transformation.FilteredList<Client> filteredClients =
			new javafx.collections.transformation.FilteredList<>(masterClients);

	private boolean clientLocked = false;
	private Client selectedClient = null;

	/* =============================================================
	   ✅ MAIN FEATURE:
	   Cards + buttons disabled until:
	   - client selected
	   - jobName entered
	   ============================================================= */
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
	}

	/* ========================= JOB FLOW ========================= */

	public void startNewJob() {

	    JobService jobService = new JobService();

	    this.currentJob = jobService.createDraftJob();
	    this.editMode = false;

	    jobNoLabel.setText("Job No: " + currentJob.getJobNo());
	    addJobBtn.setText("Add Job ✅");

	    // reset UI state
	    clientCombo.setDisable(false);
	    clientLocked = false;
	    selectedClient = null;

	    jobName.clear();
	    clientCombo.getSelectionModel().clearSelection();

	    updateFormState();

	    System.out.println("✅ New draft created: " + currentJob.getJobNo());
	}


	public void openForEdit(int jobId) {

	    JobService jobService = new JobService();
	    Job job = jobService.getJobById(jobId);

	    if (job == null) {
	        toast("❌ Job not found");
	        return;
	    }

	    this.currentJob = job;
	    this.editMode = true;

	    jobNoLabel.setText("Job No: " + currentJob.getJobNo());
	    addJobBtn.setText("Update Job ✅");

	    preloadClientIntoCombo();

	    jobName.setText(currentJob.getJobTitle());

	    updateFormState();

	    System.out.println("✏ Resumed draft: " + currentJob.getJobNo());
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

	@FXML
	private void handleUploadFile(ActionEvent event) {
		try {
			FileChooser chooser = new FileChooser();
			chooser.setTitle("Select Image or PDF");

			chooser.getExtensionFilters().addAll(
					new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"),
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

	/* ========================= FINAL SAVE / UPDATE ========================= */

	@FXML
	private void handleAddJobButton() {

	    if (currentJob == null || currentJob.getId() == 0) {
	        toast("❌ Job not created properly");
	        return;
	    }

	    if (selectedClient == null) {
	        toast("Please Select Client..");
	        return;
	    }

	    if (jobName.getText() == null || jobName.getText().isBlank()) {
	        toast("Please Enter Job Name..");
	        return;
	    }

	    String title = jobName.getText().trim();

	    // ✅ 1) set in currentJob object
	    currentJob.setJobTitle(title);

	    // ✅ 2) save into database (important)
	    new JobService().updateJobName(currentJob.getId(), title);

	    if (!editMode) {
	        System.out.println("✅ Finalizing NEW job: " + currentJob.getJobNo());
	        toast("✅ Job Created Successfully!");
	    } else {
	        System.out.println("✅ Updating existing job: " + currentJob.getJobNo());
	        toast("✅ Job Updated Successfully!");
	    }
	}


	/* ========================= ADD ITEMS ========================= */

	@FXML
	private void handleAddPrinting() {

		if (currentJob == null || currentJob.getId() <= 0) {
			toast("Job not created ❌");
			return;
		}

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
		p.setSide(printSideCombo.getValue());
		p.setWithCtp("Yes".equalsIgnoreCase(printCtpCombo.getValue()));
		p.setNotes(printNotesArea.getText());

		try {
			if (printAmountField.getText() != null && !printAmountField.getText().isBlank())
				p.setAmount(Double.parseDouble(printAmountField.getText()));
		} catch (Exception e) {
			toast("Printing Amount must be number ❌");
			return;
		}

		JobItemService js = new JobItemService();
		js.addJobItem(currentJob.getId(), p);

		toast("Printing Added ✅");
		clearPrintingFields();
		updateFormState();
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

		if (currentJob == null || currentJob.getId() <= 0) {
			toast("Job not created ❌");
			return;
		}

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
			if (ctpAmountField.getText() != null && !ctpAmountField.getText().isBlank())
				c.setAmount(Double.parseDouble(ctpAmountField.getText()));
		} catch (Exception e) {
			toast("CTP Amount must be number ❌");
			return;
		}

		JobItemService js = new JobItemService();
		js.addJobItem(currentJob.getId(), c);

		toast("CTP Plate Added ✅");
		clearCtpFields();
		updateFormState();
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

		if (currentJob == null || currentJob.getId() <= 0) {
			toast("Job not created ❌");
			return;
		}

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
			if (paperAmountField.getText() != null && !paperAmountField.getText().isBlank())
				p.setAmount(Double.parseDouble(paperAmountField.getText()));
		} catch (Exception e) {
			toast("Paper Amount must be number ❌");
			return;
		}

		JobItemService js = new JobItemService();
		js.addJobItem(currentJob.getId(), p);

		toast("Paper Added ✅");
		clearPaperFields();
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

		if (currentJob == null || currentJob.getId() <= 0) {
			toast("Job not created ❌");
			return;
		}

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
			if (bindingRateField.getText() != null && !bindingRateField.getText().isBlank())
				b.setRate(Double.parseDouble(bindingRateField.getText()));
		} catch (Exception e) {
			toast("Binding Rate must be number ❌");
			return;
		}

		b.setNotes(bindingNotesArea.getText());

		try {
			if (bindingAmountField.getText() != null && !bindingAmountField.getText().isBlank())
				b.setAmount(Double.parseDouble(bindingAmountField.getText()));
		} catch (Exception e) {
			toast("Binding Amount must be number ❌");
			return;
		}

		JobItemService js = new JobItemService();
		js.addJobItem(currentJob.getId(), b);

		toast("Binding Added ✅");
		clearBindingFields();
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

		if (currentJob == null || currentJob.getId() <= 0) {
			toast("Job not created ❌");
			return;
		}

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
		l.setSide(lamSideCombo.getValue());
		l.setSize(lamSizeCombo.getValue());
		l.setNotes(lamNotesArea.getText());

		try {
			if (lamAmountField.getText() != null && !lamAmountField.getText().isBlank())
				l.setAmount(Double.parseDouble(lamAmountField.getText()));
		} catch (Exception e) {
			toast("Lamination Amount must be number ❌");
			return;
		}

		JobItemService js = new JobItemService();
		js.addJobItem(currentJob.getId(), l);

		toast("Lamination Added ✅");
		clearLaminationFields();
		updateFormState();
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

	/* ========================= VALIDATION HELPERS ========================= */

	private void makeNumeric(TextField tf) {
		tf.setTextFormatter(new TextFormatter<>(change -> {
			return change.getControlNewText().matches("\\d*") ? change : null;
		}));
	}

	private boolean notEmpty(String s) {
		return s != null && !s.trim().isEmpty();
	}

	/* ========================= PRINTING VALIDATION ========================= */

	private boolean isPrintingValid() {
		boolean fullEntry = notEmpty(printQtyField.getText()) && printUnitsCombo.getValue() != null
				&& notEmpty(printSetField.getText()) && notEmpty(printAmountField.getText());

		boolean notesEntry = notEmpty(printNotesArea.getText()) && notEmpty(printAmountField.getText());

		return fullEntry || notesEntry;
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
		boolean fullEntry = notEmpty(ctpQtyField.getText()) && ctpSizeCombo.getValue() != null
				&& notEmpty(ctpAmountField.getText()) && ctpSupplierCombo.getValue() != null;

		boolean notesEntry = notEmpty(ctpNotesArea.getText()) && notEmpty(ctpAmountField.getText());

		return fullEntry || notesEntry;
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
		boolean fullEntry = notEmpty(paperQtyField.getText()) && paperUnitsCombo.getValue() != null
				&& paperSizeCombo.getValue() != null && notEmpty(paperAmountField.getText());

		boolean notesEntry = notEmpty(paperNotesArea.getText()) && notEmpty(paperAmountField.getText());

		return fullEntry || notesEntry;
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
		boolean fullEntry = bindingProcessCombo.getValue() != null && notEmpty(bindingQtyField.getText())
				&& notEmpty(bindingRateField.getText()) && notEmpty(bindingAmountField.getText());

		boolean notesEntry = notEmpty(bindingNotesArea.getText()) && notEmpty(bindingAmountField.getText());

		return fullEntry || notesEntry;
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
		boolean fullEntry = notEmpty(lamQtyField.getText()) && lamUnitCombo.getValue() != null
				&& lamTypeCombo.getValue() != null && notEmpty(lamAmountField.getText());

		boolean notesEntry = notEmpty(lamNotesArea.getText()) && notEmpty(lamAmountField.getText());

		return fullEntry || notesEntry;
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

		filteredClients.setPredicate(c -> true);

		// ✅ Load clients
		masterClients.setAll(clientService.getAllClients());
		clientCombo.setItems(filteredClients);
		clientCombo.setEditable(true);

		// ✅ UI setup
		clientCombo.setCellFactory(lv -> new ListCell<>() {
			@Override
			protected void updateItem(Client c, boolean empty) {
				super.updateItem(c, empty);
				setText(empty || c == null ? null : c.getBusinessName() + " | " + c.getClientName() + " | " + c.getGst()+ " | " +c.getPhone()) ;
			}
		});

		clientCombo.setButtonCell(new ListCell<>() {
			@Override
			protected void updateItem(Client c, boolean empty) {
				super.updateItem(c, empty);
				setText(empty || c == null ? null :c.getBusinessName() + " | " + c.getClientName() + " | " + c.getGst()+ " | " +c.getPhone()) ;
			}
		});

		clientCombo.setConverter(new StringConverter<>() {
			@Override
			public String toString(Client c) {
				return c == null ? "" : c.getBusinessName() + " | " + c.getClientName() + " | " + c.getGst()+ " | " +c.getPhone();
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
		makeNumeric(printAmountField);
		makeNumeric(ctpQtyField);
		makeNumeric(ctpAmountField);
		makeNumeric(paperQtyField);
		makeNumeric(paperAmountField);
		makeNumeric(bindingQtyField);
		makeNumeric(bindingRateField);
		makeNumeric(bindingAmountField);
		makeNumeric(lamQtyField);
		makeNumeric(lamAmountField);

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

		// ✅ initial lock state
		updateFormState();
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
		Toast.show(stage, message);
	}
}
