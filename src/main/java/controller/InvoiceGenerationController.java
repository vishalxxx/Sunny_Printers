package controller;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import model.Client;
import model.Invoice;
import model.InvoiceHistoryRow;
import model.JobSummary;
import model.InvoiceMaster;
import model.MasterDocumentSeries;
import service.ClientService;
import service.InvoiceBuilderService;
import service.InvoiceGenerationService;
import service.InvoiceMasterService;
import service.InvoiceStorageService;
import service.JobService;
import service.PdfInvoiceService;
import utils.DBConnection;
import utils.Toast;
import java.io.File;
import java.sql.Connection;

import javafx.stage.DirectoryChooser;

public class InvoiceGenerationController implements utils.DirtySupport {

	@Override
	public boolean hasUnsavedChanges() {
		// Dirty if any job is selected but invoice not generated
		return !selectedJobs.isEmpty();
	}

	private InvoiceBuilderService invoicebuilder = new InvoiceBuilderService();
	private InvoiceGenerationService invoicegeneration = new InvoiceGenerationService();
	private JobService js = new JobService();
	private final Map<String, JobSummary> selectedJobsMap = new LinkedHashMap<>();
	private final Set<String> selectedJobs = new LinkedHashSet<>();
	private final ClientService clientService = new ClientService();
	// replaced InvoiceHistoryRowService with InvoiceMasterService
	private final InvoiceMasterService invoiceMasterService = new InvoiceMasterService();
	private PdfInvoiceService pdfService = new PdfInvoiceService();
	private Task<File> monthlyTask;
	@FXML
	private ProgressBar progressBar;
	@FXML
	private Label statusLabel;

	// ---------------- CARD 1 ----------------
	@FXML
	private ComboBox<Client> clientComboBox;
	@FXML
	private DatePicker startDatePicker;
	@FXML
	private DatePicker endDatePicker;
	@FXML
	private Button generateInvoiceBtn;

	// ---------------- CARD 2 ----------------
	@FXML
	private ComboBox<String> monthComboBox;
	@FXML
	private ComboBox<Integer> yearComboBox;
	@FXML
	private CheckBox autoEmailCheck;
	@FXML
	private Button runBatchBtn;

	@FXML
	private HBox monthYearRow;

	@FXML
	private ComboBox<Client> jobClientComboBox;
	@FXML
	private ComboBox<JobSummary> jobComboBox;

	@FXML
	private Label selectedLabel;
	@FXML
	private ScrollPane selectedJobsScroll;
	@FXML
	private FlowPane selectedJobsPane;
	
	@FXML
	private DatePicker dateRangeInvoiceDate;
	@FXML
	private DatePicker monthlyInvoiceDate;
	@FXML
	private DatePicker jobInvoiceDate;

	@FXML
	private Button createJobInvoiceBtn;

	// ---------------- SAVE PATH ----------------
	@FXML
	private TextField savePathField;

	// ---------------- TABLE ----------------

	@FXML
	private TableView<InvoiceHistoryRow> recentInvoicesTable;
	@FXML
	private TableColumn<InvoiceHistoryRow, String> colInvNo;
	@FXML
	private TableColumn<InvoiceHistoryRow, String> colClient;
	@FXML
	private TableColumn<InvoiceHistoryRow, String> colDate;
	@FXML
	private TableColumn<InvoiceHistoryRow, Number> colAmount;
	@FXML
	private TableColumn<InvoiceHistoryRow, String> colType;
	@FXML
	private TableColumn<InvoiceHistoryRow, String> colStatus;

	private final ObservableList<InvoiceHistoryRow> recentInvoiceRows = FXCollections.observableArrayList();

	private StackPane rootStackPane; // Root of dashboard

	public void setRootPane(StackPane rootPane) {
		this.rootStackPane = rootPane;
	}

	public void preSelectJob(int clientId, int jobId) {
		// 1. Find client
		Client match = null;
		for (Client c : jobClientComboBox.getItems()) {
			if (c.getId() == clientId) {
				match = c;
				break;
			}
		}

		if (match != null) {
			jobClientComboBox.setValue(match);
			// Force reload jobs if listener hasn't run or to ensure they are there
			loadJobsForClient(clientId);

			// 2. Find and select the job
			for (JobSummary js : jobComboBox.getItems()) {
				if (js.getId() == jobId) {
					jobComboBox.setValue(js);
					break;
				}
			}
		}
	}

	@FXML
	private ComboBox<String> formatComboBox;
	private String format;

	@FXML
	private RadioButton radioGstInvoice;
	@FXML
	private RadioButton radioProformaInvoice;

	@FXML
	private void initialize() {

		ToggleGroup invoiceDocTypeGroup = new ToggleGroup();
		if (radioGstInvoice != null) {
			radioGstInvoice.setToggleGroup(invoiceDocTypeGroup);
		}
		if (radioProformaInvoice != null) {
			radioProformaInvoice.setToggleGroup(invoiceDocTypeGroup);
		}
		if (radioGstInvoice != null) {
			radioGstInvoice.setSelected(true);
		}

		formatComboBox.getItems().addAll("EXCEL", "PDF");
		formatComboBox.setValue("EXCEL");
		setupClientComboBoxUI(clientComboBox);
		setupClientComboBoxUI(jobClientComboBox);
		setupJobComboBoxUI();
		
		dateRangeInvoiceDate.setValue(LocalDate.now());
		jobInvoiceDate.setValue(LocalDate.now());
		// Monthly date will dynamically update later or we can leave it blank (defaults to end of month)

		loadClients();

		jobComboBox.setDisable(true);
		selectedLabel.setVisible(false);

		// ✅ Client change => reset + load jobs
		jobClientComboBox.valueProperty().addListener((obs, oldV, newClient) -> {

			// ✅ safe reset
			selectedJobsMap.clear();
			selectedJobsPane.getChildren().clear();
			selectedLabel.setVisible(false);

			jobComboBox.getSelectionModel().clearSelection();
			jobComboBox.setValue(null);
			jobComboBox.getItems().clear();

			if (newClient == null) {
				jobComboBox.setDisable(true);
				return;
			}

			jobComboBox.setDisable(false);
			loadJobsForClient(newClient.getId());
		});

		// ✅ Job selection => store + add chip
		jobComboBox.setOnAction(e -> {
			JobSummary selectedJob = jobComboBox.getValue();
			if (selectedJob != null) {

				// ✅ prevent duplicate
				if (!selectedJobsMap.containsKey(selectedJob.getJobNo())) {
					selectedJobsMap.put(selectedJob.getJobNo(), selectedJob);

					selectedJobsPane.getChildren().add(createChip(selectedJob));
					selectedLabel.setVisible(true);
				}

				// ✅ clear selection safely
				Platform.runLater(() -> jobComboBox.getSelectionModel().clearSelection());
			}
		});

		setupAutoPopupDatePicker(startDatePicker);
		setupAutoPopupDatePicker(endDatePicker);

		setupAutoPopupDatePicker(dateRangeInvoiceDate);
		setupAutoPopupDatePicker(monthlyInvoiceDate);
		setupAutoPopupDatePicker(jobInvoiceDate);

		setupMonthYearPicker();

		String savedPath = InvoiceStorageService.getSavePath();
		if (savedPath != null) {
			savePathField.setText(savedPath);
		}
		setupRecentInvoiceTable();
		loadRecentInvoiceHistory();

		updateState();
	}

	// ==========================================================
	// ✅ MONTH + YEAR PICKER SETUP
	// ==========================================================
	private void setupMonthYearPicker() {

		monthComboBox.prefWidthProperty().bind(monthYearRow.widthProperty().multiply(0.55));
		yearComboBox.prefWidthProperty().bind(monthYearRow.widthProperty().multiply(0.45));

		// ✅ Month Names
		monthComboBox.getItems().setAll("January", "February", "March", "April", "May", "June", "July", "August",
				"September", "October", "November", "December");

		// ✅ Years list (current year +/- range)
		int currentYear = LocalDate.now().getYear();
		for (int y = currentYear - 5; y <= currentYear + 5; y++) {
			yearComboBox.getItems().add(y);
		}

		// ✅ Default selection (current month + current year)
		monthComboBox.getSelectionModel().select(LocalDate.now().getMonthValue() - 1);
		yearComboBox.getSelectionModel().select(Integer.valueOf(currentYear));
	}

	// ==========================================================
	// CLIENTS
	// ==========================================================
	private void loadClients() {
		List<Client> clients = clientService.getAllClients();
		clientComboBox.getItems().setAll(clients);
		jobClientComboBox.getItems().setAll(clients);

	}

	private void loadJobsForClient(int clientId) {

		List<JobSummary> jobs = js.getJobsByClientId(clientId); // pending jobs only

		jobComboBox.getItems().setAll(jobs);
		jobComboBox.setDisable(jobs.isEmpty());
	}

	private void setupJobComboBoxUI() {

		jobComboBox.setButtonCell(new ListCell<>() {
			@Override
			protected void updateItem(JobSummary item, boolean empty) {
				super.updateItem(item, empty);

				if (empty || item == null) {
					setText("Select jobs...");
				} else {
					setText(item.getJobDate() + " | " + item.getJobNo() + " | " + item.getJobTitle());
				}
			}
		});

		jobComboBox.setCellFactory(cb -> new ListCell<>() {
			@Override
			protected void updateItem(JobSummary item, boolean empty) {
				super.updateItem(item, empty);

				if (empty || item == null) {
					setText(null);
				} else {
					setText(item.getJobDate() + " | " + item.getJobNo() + " | " + item.getJobTitle());
				}
			}
		});
	}

	private void setupClientComboBoxUI(ComboBox<Client> clientCombo) {

		// ✅ Selected value display
		clientCombo.setButtonCell(new ListCell<>() {
			@Override
			protected void updateItem(Client item, boolean empty) {
				super.updateItem(item, empty);

				if (empty || item == null) {
					setText("Select a client...");
				} else {
					setText(item.getBusinessName() + " (" + item.getClientName() + ")");
				}
			}
		});

		// ✅ Dropdown format
		clientCombo.setCellFactory(cb -> new ListCell<>() {
			@Override
			protected void updateItem(Client item, boolean empty) {
				super.updateItem(item, empty);

				if (empty || item == null) {
					setText(null);
				} else {
					setText(item.getBusinessName() + " (" + item.getClientName() + ")  [ID:" + item.getId() + "]");
				}
			}
		});
	}

	// ==========================================================
	// JOB CHIPS
	// ==========================================================
//	@FXML
//	private void onJobEntered() {
//		String job = jobSearchField.getText().trim();
//
//		if (job.isEmpty() || selectedJobs.contains(job)) {
//			jobSearchField.clear();
//			return;
//		}
//
//		selectedJobs.add(job);
//		selectedJobsPane.getChildren().add(createChip(job));
//		jobSearchField.clear();
//
//		updateState();
//	}

	private HBox createChip(JobSummary job) {

		HBox chip = new HBox(6);
		chip.setAlignment(Pos.CENTER_LEFT);
		chip.getStyleClass().add("job-chip");

		chip.setMaxWidth(Region.USE_PREF_SIZE);
		chip.setPrefWidth(Region.USE_COMPUTED_SIZE);

		Label chipText = new Label(job.getJobNo() + " | " + job.getJobTitle());
		chipText.getStyleClass().add("job-chip-text");

		Button removeBtn = new Button("✖");
		removeBtn.getStyleClass().add("job-chip-remove");

		removeBtn.setOnAction(e -> {
			selectedJobsMap.remove(job.getJobNo());
			selectedJobsPane.getChildren().remove(chip);

			if (selectedJobsMap.isEmpty()) {
				selectedLabel.setVisible(false);
			}
		});

		chip.getChildren().addAll(chipText, removeBtn);
		return chip;
	}

//	private HBox createChip(String job) {
//		Label text = new Label(job);
//		text.getStyleClass().add("chip-text");
//
//		Button remove = new Button("×");
//		remove.getStyleClass().add("chip-remove");
//
//		HBox chip = new HBox(6, text, remove);
//		chip.setAlignment(Pos.CENTER_LEFT);
//		chip.getStyleClass().add("job-chip");
//
//		remove.setOnAction(e -> {
//			selectedJobs.remove(job);
//			selectedJobsPane.getChildren().remove(chip);
//			updateState();
//		});
//
//		return chip;
//	}

	private void updateState() {
		boolean hasJobs = !selectedJobsMap.isEmpty();
		selectedLabel.setVisible(hasJobs);
	}

	// ==========================================================
	// BUTTON ACTIONS
	// ==========================================================

	@FXML
	private void onGenerateDateInvoiceClicked(MouseEvent event) {
		if (clientComboBox.getValue() == null) {
			toast("Please select a client.");
			return;
		}
		if (startDatePicker.getValue() == null || endDatePicker.getValue() == null) {
			toast("Please select date range.");
			return;
		}

		Client client = clientComboBox.getValue();

		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/progress_dialog.fxml"));
			StackPane progressRoot = loader.load();
			ProgressDialogController progress = loader.getController();

			rootStackPane.getChildren().add(progressRoot);
			progress.show("Creating Draft Invoice");

			final List<Integer> newlyCreatedIds = new ArrayList<>();

			Task<Void> task = new Task<>() {
				@Override
				protected Void call() throws Exception {
					updateProgress(0.1, 1);
					updateMessage("Building invoice...");
					
					LocalDate customDate = dateRangeInvoiceDate.getValue();
					Invoice invoice = invoicebuilder.buildInvoiceForClient(client.getId(), client.getClientName(),
							client.getBusinessName(), startDatePicker.getValue(), endDatePicker.getValue(), customDate);
					invoice.setMasterDocumentSeries(selectedDocumentSeriesForGeneration());

					if (isCancelled()) throw new CancellationException();

					if (invoice == null || invoice.getJobs().isEmpty())
						throw new RuntimeException("No completed jobs found for selected date range");

					// 🔥 Reserve or reuse TEMP invoice number
					InvoiceMasterService.CreateOrGetResult reserved = invoiceMasterService.createNewDraftInvoice(invoice, "DATE_RANGE", null);
					if (reserved != null) {
						invoice.setInvoiceNo(reserved.master().getInvoiceNo());
						if (reserved.wasNewlyCreated()) {
							newlyCreatedIds.add(reserved.master().getId());
						}
					}

					if (isCancelled()) throw new CancellationException();

					// 🔥 Save to DB
					invoiceMasterService.registerDateRangeInvoice(invoice, startDatePicker.getValue(),
							endDatePicker.getValue(), "DATE_RANGE", null);

					updateProgress(1, 1);
					updateMessage("Completed");
					return null;
				}
			};

			progress.setOnCancel(task::cancel);

			task.setOnSucceeded(ev -> {
				progress.hide();
				rootStackPane.getChildren().remove(progressRoot);
				loadRecentInvoiceHistory();
				toast("✅ Draft Invoice created successfully!");
			});

			task.setOnCancelled(ev -> {
				new Thread(() -> invoiceMasterService.deleteInvoicesIfCancelled(newlyCreatedIds)).start();
				progress.hide();
				rootStackPane.getChildren().remove(progressRoot);
				toast("⚠ Process cancelled.");
			});

			task.setOnFailed(ev -> {
				progress.hide();
				rootStackPane.getChildren().remove(progressRoot);
				Throwable ex = task.getException();
				toast("❌ Error: " + (ex != null ? ex.getMessage() : "Unknown"));
			});

			new Thread(task).start();

		} catch (Exception ex) {
			ex.printStackTrace();
			toast("Failed to start invoice generation");
		}
	}

	@FXML
	private void onRunMonthlyBulkClicked(MouseEvent e) {
		String month = monthComboBox.getValue();
		Integer year = yearComboBox.getValue();

		if (month == null || year == null) {
			toast("Please select Month and Year.");
			return;
		}

		Month selectedMonth = Month.valueOf(month.toUpperCase());
		int monthValue = selectedMonth.getValue();
		YearMonth ym = YearMonth.of(year, selectedMonth);

		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/progress_dialog.fxml"));
			StackPane progressRoot = loader.load();
			ProgressDialogController progress = loader.getController();

			rootStackPane.getChildren().add(progressRoot);
			progress.show("Running batch process");

			final List<Integer> newlyCreatedIds = new ArrayList<>();

			Task<Void> task = new Task<>() {
				@Override
				protected Void call() throws Exception {
					updateMessage("Loading clients...");
					LocalDate customDate = monthlyInvoiceDate.getValue();
					Map<String, Invoice> invoiceMap = invoicebuilder.buildMonthlyInvoicesForAllClients(year,
							monthValue, customDate);

					if (invoiceMap == null || invoiceMap.isEmpty())
						throw new RuntimeException("No completed jobs found for the selected month");

					MasterDocumentSeries series = selectedDocumentSeriesForGeneration();
					for (Invoice inv : invoiceMap.values()) {
						inv.setMasterDocumentSeries(series);
					}

					if (isCancelled()) throw new CancellationException();

					LocalDate fromDate = ym.atDay(1);
					LocalDate toDate = ym.atEndOfMonth();

					updateMessage("Saving Draft Invoices...");
					for (Invoice inv : invoiceMap.values()) {
						if (isCancelled()) throw new CancellationException();
						InvoiceMasterService.CreateOrGetResult reserved = invoiceMasterService.createNewDraftInvoice(inv, "MONTHLY_BULK", null);
						if (reserved != null && reserved.wasNewlyCreated()) {
							newlyCreatedIds.add(reserved.master().getId());
						}
					}

					if (isCancelled()) throw new CancellationException();

					invoiceMasterService.registerMonthlyInvoices(invoiceMap, fromDate, toDate, "MONTHLY_BULK", null);

					updateProgress(1, 1);
					updateMessage("Completed");
					return null;
				}
			};

			progress.setOnCancel(task::cancel);

			task.setOnSucceeded(ev -> {
				progress.hide();
				rootStackPane.getChildren().remove(progressRoot);
				toast("✅ Draft Invoices created successfully!");
				loadRecentInvoiceHistory();
			});

			task.setOnCancelled(ev -> {
				new Thread(() -> invoiceMasterService.deleteInvoicesIfCancelled(newlyCreatedIds)).start();
				progress.hide();
				rootStackPane.getChildren().remove(progressRoot);
				toast("⚠ Process cancelled.");
			});

			task.setOnFailed(ev -> {
				progress.hide();
				rootStackPane.getChildren().remove(progressRoot);
				Throwable ex = task.getException();
				toast("❌ Failed: " + (ex != null ? ex.getMessage() : "Unknown"));
			});

			monthlyTask = null; // reset monthly task ref if needed
			new Thread(task).start();

		} catch (Exception ex) {
			ex.printStackTrace();
			toast("Failed to start batch process");
		}
	}

	@FXML
	private void onCreateJobInvoiceClicked(MouseEvent event) {
		Client client = jobClientComboBox.getValue();
		if (client == null) {
			toast("Please select a client first.");
			return;
		}

		if (selectedJobsMap.isEmpty()) {
			toast("Please select at least 1 job.");
			return;
		}

		try {
			List<Integer> jobIds = selectedJobsMap.values().stream().map(JobSummary::getId).distinct().toList();

			FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/progress_dialog.fxml"));
			StackPane progressRoot = loader.load();
			ProgressDialogController progress = loader.getController();

			rootStackPane.getChildren().add(progressRoot);
			progress.show("Creating Draft Invoice");

			Task<Void> task = new Task<>() {
				@Override
				protected Void call() throws Exception {
					updateProgress(0.1, 1);
					updateMessage("Building invoice...");
					
					LocalDate customDate = jobInvoiceDate.getValue();
					Invoice invoice = invoicebuilder.buildInvoiceForClientByJobs(client.getId(), client.getClientName(),
							client.getBusinessName(), jobIds, customDate);
					invoice.setMasterDocumentSeries(selectedDocumentSeriesForGeneration());

					if (isCancelled()) throw new CancellationException();

					if (invoice.getJobs().isEmpty())
						throw new RuntimeException("No invoice lines found.");

					updateMessage("Saving Draft to DB...");
					invoiceMasterService.saveGeneratedInvoice(invoice, "JOB_SPECIFIC", "DRAFT", null);

					updateProgress(1, 1);
					updateMessage("Completed");
					return null;
				}
			};

			progress.setOnCancel(task::cancel);

			task.setOnSucceeded(ev -> {
				progress.hide();
				rootStackPane.getChildren().remove(progressRoot);
				loadRecentInvoiceHistory();
				clearCard3();
				toast("✅ Draft Invoice created successfully!");
			});

			task.setOnCancelled(ev -> {
				progress.hide();
				rootStackPane.getChildren().remove(progressRoot);
				toast("⚠ Cancelled.");
			});

			task.setOnFailed(ev -> {
				progress.hide();
				rootStackPane.getChildren().remove(progressRoot);
				toast("❌ Failed: " + task.getException().getMessage());
			});

			new Thread(task).start();

		} catch (Exception ex) {
			ex.printStackTrace();
			toast("❌ Failed: " + ex.getMessage());
		}
	}

	@FXML
	private void onCancelMonthlyGeneration() {
		// This method was linked to a specific monthly task, we might need to adjust it if needed
		toast("Batch process cancellation requested.");
	}

	private void clearCard3() {
		jobComboBox.getSelectionModel().clearSelection();
		jobComboBox.setValue(null);

		selectedJobsMap.clear();
		selectedJobsPane.getChildren().clear();
		selectedLabel.setVisible(false);
	}

	@FXML
	private void chooseSaveLocation(javafx.event.ActionEvent e) {
		// Redundant method on this screen
		toast("Save location selection is now managed in Settings.");
	}

	// ==========================================================
	// DATE PICKER AUTO SHOW
	// ==========================================================
	private void setupAutoPopupDatePicker(DatePicker dp) {

		dp.setEditable(false);

		// ✅ Disable future dates from calendar
		dp.setDayCellFactory(picker -> new DateCell() {
			@Override
			public void updateItem(java.time.LocalDate date, boolean empty) {
				super.updateItem(date, empty);

				if (empty || date == null)
					return;

				// ✅ future dates disabled
				if (date.isAfter(java.time.LocalDate.now())) {
					setDisable(true);
					setStyle("-fx-opacity: 0.35;"); // faded look
				}
			}
		});

		// ✅ extra safety: if value becomes future somehow, reset and show toast
		dp.valueProperty().addListener((obs, oldVal, newVal) -> {
			if (newVal != null && newVal.isAfter(java.time.LocalDate.now())) {
				dp.setValue(oldVal); // revert
				toast("Future dates are not allowed ❌");
			}
		});

		// ✅ Auto open popup on click
		dp.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
			if (!dp.isShowing())
				dp.show();
		});

		dp.focusedProperty().addListener((obs, oldV, newV) -> {
			if (newV && !dp.isShowing())
				dp.show();
		});
	}

	private void setupRecentInvoiceTable() {

		recentInvoicesTable.setFixedCellSize(42);
		recentInvoicesTable.setPrefHeight(42 * 6);
		recentInvoicesTable.setMinHeight(42 * 6);
		recentInvoicesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		colInvNo.setCellValueFactory(data -> data.getValue().invoiceNoProperty());
		colClient.setCellValueFactory(data -> data.getValue().clientNameProperty());
		colDate.setCellValueFactory(data -> data.getValue().dateProperty());
		colAmount.setCellValueFactory(data -> data.getValue().amountProperty());
		colType.setCellValueFactory(data -> data.getValue().typeProperty());
		colStatus.setCellValueFactory(data -> data.getValue().statusProperty());

		recentInvoicesTable.setItems(recentInvoiceRows);
	}

	@FXML
	private void onViewAllHistoryClicked(ActionEvent event) {
		toast("History page coming soon ✅");
	}

	private void loadRecentInvoiceHistory() {
		// fetch recent InvoiceMaster entries and convert to InvoiceHistoryRow for the
		// UI table
		var masters = invoiceMasterService.getRecentInvoices(10);

		List<InvoiceHistoryRow> rows = masters
				.stream().map(m -> new InvoiceHistoryRow(m.getInvoiceNo(), m.getClientName(),
						m.getInvoiceDate().toString(), m.getAmount(), m.getType(), m.getStatus()))
				.collect(Collectors.toList());

		System.out.println("✅ Loaded invoice rows = " + rows.size());

		recentInvoiceRows.setAll(rows);

		System.out.println("✅ Table items = " + recentInvoicesTable.getItems().size());
	}

	// ==========================================================
	// TOAST
	// ==========================================================
	private void toast(String message) {
		Stage stage = (Stage) ((Node) clientComboBox).getScene().getWindow();
		Toast.show(stage, message);
	}

	private MasterDocumentSeries selectedDocumentSeriesForGeneration() {
		if (radioProformaInvoice != null && radioProformaInvoice.isSelected()) {
			return MasterDocumentSeries.PROFORMA_INVOICE;
		}
		return MasterDocumentSeries.GST_INVOICE;
	}

}