package controller;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
import service.ClientService;
import service.InvoiceBuilderService;
import service.InvoiceGenerationService;
import service.InvoiceHistoryRowService;
import service.InvoiceStorageService;
import service.JobService;
import service.PdfInvoiceService;
import utils.DBConnection;
import utils.Toast;
import java.io.File;
import java.sql.Connection;

import javafx.stage.DirectoryChooser;

public class InvoiceGenerationController {

	private InvoiceBuilderService invoicebuilder = new InvoiceBuilderService();
	private InvoiceGenerationService invoicegeneration = new InvoiceGenerationService();
	private JobService js = new JobService();
	private final Map<String, JobSummary> selectedJobsMap = new LinkedHashMap<>();
	private final Set<String> selectedJobs = new LinkedHashSet<>();
	private final ClientService clientService = new ClientService();
	private final InvoiceHistoryRowService historyService = new InvoiceHistoryRowService();
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

	// ---------------- CARD 3 ----------------
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

	@FXML
	private ComboBox<String> formatComboBox;
	private String format;

	@FXML
	private void initialize() {

		formatComboBox.getItems().addAll("EXCEL", "PDF");
		formatComboBox.setValue("EXCEL");
		setupClientComboBoxUI(clientComboBox);
		setupClientComboBoxUI(jobClientComboBox);
		setupJobComboBoxUI();

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
	private void onGenerateDateInvoiceClicked(MouseEvent e) {

		if (InvoiceStorageService.getSavePath() == null) {
			toast("Please choose Save Location first.");
			return;
		}

		if (clientComboBox.getValue() == null) {
			toast("Please select a client.");
			return;
		}

		if (startDatePicker.getValue() == null) {
			toast("Please select Start Date.");
			return;
		}

		if (endDatePicker.getValue() == null) {
			toast("Please select End Date.");
			return;
		}

		if (startDatePicker.getValue().isAfter(endDatePicker.getValue())) {
			toast("End Date must be greater than Start Date ❌");
			return;
		}

		Client client = clientComboBox.getValue();

		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/progress_dialog.fxml"));
			StackPane progressRoot = loader.load();
			ProgressDialogController progress = loader.getController();

			rootStackPane.getChildren().add(progressRoot);
			progress.show("Generating Invoice");

			// =====================================================
			// BACKGROUND TASK
			// =====================================================
			Task<File> task = new Task<>() {

				@Override
				protected File call() throws Exception {

					AtomicBoolean realDone = new AtomicBoolean(false);

					// 🔹 smooth fake progress
					Thread smooth = new Thread(() -> {
						double p = 0.0;

						try {
							while (p < 0.85 && !isCancelled() && !realDone.get()) {

								updateProgress(p, 1);
								updateMessage("Preparing invoice data...");

								if (p < 0.20) {
									p += 0.002;
									Thread.sleep(140);
								} else if (p < 0.50) {
									p += 0.003;
									Thread.sleep(120);
								} else if (p < 0.75) {
									p += 0.004;
									Thread.sleep(100);
								} else {
									p += 0.002;
									Thread.sleep(140);
								}
							}
						} catch (InterruptedException ignored) {
						}
					});

					smooth.setDaemon(true);
					smooth.start();

					// =================================================
					// REAL WORK
					// =================================================
					Invoice invoice = invoicebuilder.buildInvoiceForClient(client.getId(), client.getClientName(),
							client.getBusinessName(), startDatePicker.getValue(), endDatePicker.getValue());

					if (isCancelled())
						return null;

					if (invoice.getJobs().isEmpty())
						throw new RuntimeException("No jobs found for selected date range");

					File file = null;

					format = formatComboBox.getValue();

					if ("Excel".equalsIgnoreCase(format)) {
						updateMessage("Generating Excel...");
						file = invoicegeneration.generateSingleInvoice(invoice);
					}

					if ("PDF".equalsIgnoreCase(format)) {
						updateMessage("Generating PDF...");
						file = pdfService.generateSingleInvoicePDF(invoice);
					}

					realDone.set(true);

					// 🔹 smooth finish
					double p = 0.85;
					while (p < 1.0 && !isCancelled()) {
						updateProgress(p, 1);
						updateMessage("Finalizing...");
						p += 0.03;
						Thread.sleep(40);
					}

					updateProgress(1, 1);
					updateMessage("Completed");

					// save history AFTER success
					historyService.saveHistory(invoice, "DATE_RANGE", "SENT", file.getAbsolutePath());

					return file;
				}
			};

			// =====================================================
			// BIND UI
			// =====================================================
			task.messageProperty().addListener((obs, o, n) -> progress.updateProgress(task.getProgress(), n));

			task.progressProperty()
					.addListener((obs, o, n) -> progress.updateProgress(n.doubleValue(), task.getMessage()));

			// =====================================================
			// CANCEL
			// =====================================================
			progress.setOnCancel(task::cancel);

			// =====================================================
			// SUCCESS
			// =====================================================
			task.setOnSucceeded(ev -> {
				progress.hide();
				rootStackPane.getChildren().remove(progressRoot);

				File file = task.getValue();

				if (file == null || !file.exists()) {
					toast("❌ Invoice not generated.");
					return;
				}

				loadRecentInvoiceHistory();
				toast("Invoice generated successfully ✅");
			});

			// =====================================================
			// CANCELLED
			// =====================================================
			task.setOnCancelled(ev -> {
				progress.hide();
				rootStackPane.getChildren().remove(progressRoot);
				toast("⚠ Process cancelled.");
			});

			// =====================================================
			// FAILED
			// =====================================================
			task.setOnFailed(ev -> {
				progress.hide();
				rootStackPane.getChildren().remove(progressRoot);
				toast("❌ " + task.getException().getMessage());
			});

			new Thread(task).start();

		} catch (Exception ex) {
			ex.printStackTrace();
			toast("Failed to start invoice generation");
		}
	}

	@FXML
	private void onRunMonthlyBulkClicked(MouseEvent e) {

		if (InvoiceStorageService.getSavePath() == null) {
			toast("Please choose Save Location first.");
			return;
		}

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
			progress.show("Generating Monthly Invoices");

			// =====================================================
			// BACKGROUND TASK
			// =====================================================
			Task<File> task = new Task<>() {

				private Thread smoothThread;
				private final AtomicBoolean realDone = new AtomicBoolean(false);

				@Override
				protected File call() throws Exception {

					// 🔹 Smooth visual progress (stops automatically)
					smoothThread = new Thread(() -> {
						double p = 0.0;

						try {
							while (p < 0.85 && !isCancelled() && !realDone.get()) {

								updateProgress(p, 1);
								updateMessage("Preparing data...");

								if (p < 0.30) {
									p += 0.01;
									Thread.sleep(60);
								} else if (p < 0.60) {
									p += 0.008;
									Thread.sleep(70);
								} else {
									p += 0.006;
									Thread.sleep(80);
								}
							}
						} catch (InterruptedException ignored) {
						}
					});

					smoothThread.setDaemon(true);
					smoothThread.start();

					// =====================================================
					// 🔥 BUILD INVOICES
					// =====================================================
					updateMessage("Loading clients...");
					Map<String, Invoice> invoiceMap = invoicebuilder.buildMonthlyInvoicesForAllClients(year,
							monthValue);

					if (isCancelled())
						return null;

					// =====================================================
					// 🔥 FILE GENERATION
					// =====================================================
					updateProgress(0.85, 1);

					File outputFile;
					String format = formatComboBox.getValue();

					if ("Excel".equalsIgnoreCase(format)) {
						updateMessage("Generating Excel workbook...");
						outputFile = invoicegeneration.generateMonthlyClientWorkbook(ym, invoiceMap);

					} else if ("PDF".equalsIgnoreCase(format)) {
						updateMessage("Generating PDF bundle...");
						outputFile = pdfService.generateMonthlyBulkPDF(ym, invoiceMap);

					} else {
						throw new RuntimeException("Unknown format selected: " + format);
					}

					if (isCancelled())
						return null;

					// =====================================================
					// 🔥 FINISH SMOOTHLY (very fast)
					// =====================================================
					realDone.set(true);
					if (smoothThread != null)
						smoothThread.interrupt();

					for (double p = 0.85; p <= 1.0; p += 0.05) {
						updateProgress(p, 1);
						updateMessage("Finalizing...");
						Thread.sleep(40);
					}

					updateMessage("Completed");
					return outputFile;
				}
			};

			// =====================================================
			// BIND UI
			// =====================================================
			task.messageProperty().addListener((obs, o, n) -> progress.updateProgress(task.getProgress(), n));

			task.progressProperty()
					.addListener((obs, o, n) -> progress.updateProgress(n.doubleValue(), task.getMessage()));

			// =====================================================
			// CANCEL
			// =====================================================
			progress.setOnCancel(task::cancel);

			// =====================================================
			// SUCCESS
			// =====================================================
			task.setOnSucceeded(ev -> {
				progress.hide();
				rootStackPane.getChildren().remove(progressRoot);

				File bulkFile = task.getValue();

				if (bulkFile == null || !bulkFile.exists()) {
					toast("No invoice data found for selected period.");
					return;
				}

				toast("Monthly invoices generated successfully ✅");
				loadRecentInvoiceHistory();
			});

			// =====================================================
			// FAILED
			// =====================================================
			task.setOnFailed(ev -> {
				progress.hide();
				rootStackPane.getChildren().remove(progressRoot);

				Throwable ex = task.getException();
				if (ex != null)
					ex.printStackTrace();

				toast("❌ Failed to generate monthly invoices.");
			});

			new Thread(task).start();

		} catch (Exception ex) {
			ex.printStackTrace();
			toast("Failed to start progress dialog");
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

		// ✅ Ensure save location exists
		String savedPath = InvoiceStorageService.getSavePath();

		if (savedPath == null || savedPath.isBlank()) {

			DirectoryChooser chooser = new DirectoryChooser();
			chooser.setTitle("Select Folder to Save Invoices");

			Stage stage = (Stage) ((Node) createJobInvoiceBtn).getScene().getWindow();
			File selectedDir = chooser.showDialog(stage);

			if (selectedDir == null) {
				toast("Please select a folder to save invoices ❌");
				return;
			}

			InvoiceStorageService.setSavePath(selectedDir.getAbsolutePath(), false);
			savePathField.setText(selectedDir.getAbsolutePath());

			toast("✅ Save location updated!");
		}

		try {

			// ===============================
			// 🔹 Collect job IDs
			// ===============================
			List<Integer> jobIds = selectedJobsMap.values().stream().map(JobSummary::getId).distinct().toList();

			if (jobIds.isEmpty()) {
				toast("Selected job list is empty.");
				return;
			}

			// ===============================
			// 🔹 Load progress dialog
			// ===============================
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/progress_dialog.fxml"));
			StackPane progressRoot = loader.load();
			ProgressDialogController progress = loader.getController();

			rootStackPane.getChildren().add(progressRoot);
			progress.show("Generating Job Invoice");

			// ===============================
			// 🔹 Background task
			// ===============================
			Task<File> task = new Task<>() {

				@Override
				protected File call() throws Exception {

					updateProgress(0.05, 1);
					updateMessage("Building invoice...");

					// 🔥 Build invoice
					Invoice invoice = invoicebuilder.buildInvoiceForClientByJobs(client.getId(), client.getClientName(),
							client.getBusinessName(), jobIds);

					if (isCancelled())
						return null;

					if (invoice.getJobs().isEmpty())
						throw new RuntimeException("No invoice lines found.");

					updateProgress(0.40, 1);
					updateMessage("Preparing file...");

					String format = formatComboBox.getValue();

					File file = null;

					// ===============================
					// 🔹 Excel generation
					// ===============================
					if ("Excel".equalsIgnoreCase(format)) {

						updateMessage("Generating Excel...");
						file = invoicegeneration.generateSingleInvoice(invoice);
					}

					// ===============================
					// 🔹 PDF generation
					// ===============================
					if ("PDF".equalsIgnoreCase(format)) {

						updateMessage("Generating PDF...");
						file = pdfService.generateSingleInvoicePDF(invoice);
					}

					if (isCancelled())
						return null;

					if (file == null || !file.exists())
						throw new RuntimeException("Invoice file not generated.");

					updateProgress(0.85, 1);
					updateMessage("Saving history...");

					historyService.saveHistory(invoice, "JOB_SPECIFIC", "SENT", file.getAbsolutePath());

					updateProgress(1, 1);
					updateMessage("Completed");

					return file;
				}
			};

			// ===============================
			// 🔹 Bind progress to UI
			// ===============================
			task.progressProperty()
					.addListener((obs, o, n) -> progress.updateProgress(n.doubleValue(), task.getMessage()));

			task.messageProperty().addListener((obs, o, n) -> progress.updateProgress(task.getProgress(), n));

			// ===============================
			// 🔹 Cancel support
			// ===============================
			progress.setOnCancel(task::cancel);

			// ===============================
			// 🔹 Success
			// ===============================
			task.setOnSucceeded(ev -> {

				progress.hide();
				rootStackPane.getChildren().remove(progressRoot);

				File file = task.getValue();

				if (file == null || !file.exists()) {
					toast("❌ Invoice not generated.");
					return;
				}

				loadRecentInvoiceHistory();
				clearCard3();

				toast("✅ Invoice Generated Successfully!");
			});

			// ===============================
			// 🔹 Cancelled
			// ===============================
			task.setOnCancelled(ev -> {
				progress.hide();
				rootStackPane.getChildren().remove(progressRoot);
				toast("⚠ Invoice generation cancelled.");
			});

			// ===============================
			// 🔹 Failed
			// ===============================
			task.setOnFailed(ev -> {
				progress.hide();
				rootStackPane.getChildren().remove(progressRoot);

				task.getException().printStackTrace();
				toast("❌ Failed: " + task.getException().getMessage());
			});

			new Thread(task).start();

		} catch (Exception ex) {
			ex.printStackTrace();
			toast("❌ Failed to start invoice generation: " + ex.getMessage());
		}
	}

	@FXML
	private void onCancelMonthlyGeneration() {

		if (monthlyTask == null || !monthlyTask.isRunning())
			return;

		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
		confirm.setTitle("Cancel Generation");
		confirm.setHeaderText("Cancel invoice generation?");
		confirm.setContentText("All progress will be lost.");

		ButtonType yes = new ButtonType("Yes");
		ButtonType no = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);

		confirm.getButtonTypes().setAll(yes, no);

		confirm.showAndWait().ifPresent(btn -> {
			if (btn == yes) {
				monthlyTask.cancel(); // 🔥 triggers rollback inside task
			}
		});
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

		DirectoryChooser chooser = new DirectoryChooser();
		chooser.setTitle("Select Folder to Save Invoices");

		Stage stage = (Stage) ((Node) savePathField).getScene().getWindow();

		File selectedDir = chooser.showDialog(stage);

		if (selectedDir == null) {
			toast("No folder selected ❌");
			return;
		}

		InvoiceStorageService.setSavePath(selectedDir.getAbsolutePath(), false);
		savePathField.setText(selectedDir.getAbsolutePath());

		toast("Save location updated ✅");
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
		var rows = historyService.getRecentHistory(10);

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

}
