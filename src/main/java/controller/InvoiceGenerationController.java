package controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import model.Client;
import service.ClientService;
import service.InvoiceBuilderService;
import service.InvoiceGenerationService;
import service.InvoiceStorageService;
import utils.Toast;
import java.io.File;
import javafx.stage.DirectoryChooser;

public class InvoiceGenerationController {

	private InvoiceBuilderService invoicebuilder = new InvoiceBuilderService();
	private InvoiceGenerationService invoicegeneration = new InvoiceGenerationService();

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
	private TextField jobSearchField;
	@FXML
	private FlowPane selectedJobsPane;
	@FXML
	private ScrollPane selectedJobsScroll;
	@FXML
	private Label selectedLabel;
	@FXML
	private Button createJobInvoiceBtn;

	// ---------------- SAVE PATH ----------------
	@FXML
	private TextField savePathField;

	// ---------------- TABLE ----------------
	@FXML
	private TableView<?> recentInvoicesTable;

	private final Set<String> selectedJobs = new LinkedHashSet<>();
	private final ClientService clientService = new ClientService();

	@FXML
	private void initialize() {
		loadClients();
		setupClientComboBoxUI();

		setupAutoPopupDatePicker(startDatePicker);
		setupAutoPopupDatePicker(endDatePicker);

		setupMonthYearPicker();
		String savedPath = InvoiceStorageService.getSavePath();
		if (savedPath != null) {
			savePathField.setText(savedPath);
		}
		
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
	}

	private void setupClientComboBoxUI() {

		// ✅ Selected value display
		clientComboBox.setButtonCell(new ListCell<>() {
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
		clientComboBox.setCellFactory(cb -> new ListCell<>() {
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
	@FXML
	private void onJobEntered() {
		String job = jobSearchField.getText().trim();

		if (job.isEmpty() || selectedJobs.contains(job)) {
			jobSearchField.clear();
			return;
		}

		selectedJobs.add(job);
		selectedJobsPane.getChildren().add(createChip(job));
		jobSearchField.clear();

		updateState();
	}

	private HBox createChip(String job) {
		Label text = new Label(job);
		text.getStyleClass().add("chip-text");

		Button remove = new Button("×");
		remove.getStyleClass().add("chip-remove");

		HBox chip = new HBox(6, text, remove);
		chip.setAlignment(Pos.CENTER_LEFT);
		chip.getStyleClass().add("job-chip");

		remove.setOnAction(e -> {
			selectedJobs.remove(job);
			selectedJobsPane.getChildren().remove(chip);
			updateState();
		});

		return chip;
	}

	private void updateState() {
		boolean hasJobs = !selectedJobs.isEmpty();
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

	    // ✅ Start must be <= End
	    if (startDatePicker.getValue().isAfter(endDatePicker.getValue())) {
	        toast("End Date must be greater than Start Date ❌");
	        return;
	    }

	    Client client = clientComboBox.getValue();

	    toast("Generating invoice for " + client.getBusinessName() + " ✅");

	    var invoice = invoicebuilder.buildInvoiceForClient(
	            client.getId(),
	            client.getClientName(),
	            client.getBusinessName(),
	            startDatePicker.getValue(),
	            endDatePicker.getValue()
	    );

	    if (invoice.getJobs().isEmpty()) {
	        toast("No jobs found for selected date range ❌");
	        return;
	    }

	    invoicegeneration.generateSingleInvoice(invoice);

	    toast("Invoice generated successfully ✅");
	}


	@FXML
	private void onRunMonthlyBulkClicked(MouseEvent e) {

		if (InvoiceStorageService.getSavePath() == null) {
			toast("Please choose Save Location first.");
			return;
		}

		String month = monthComboBox.getValue(); // "JANUARY"
		Integer year = yearComboBox.getValue(); // 2026

		if (month == null || year == null) {
			toast("Please select Month and Year.");
			return;
		}

		// ✅ Convert "JANUARY" -> Month.JANUARY -> 1
		java.time.Month selectedMonth = java.time.Month.valueOf(month.toUpperCase());
		int monthValue = selectedMonth.getValue(); // 1 to 12

		YearMonth ym = YearMonth.of(year, selectedMonth);

		toast("Running Monthly Bulk for: " + selectedMonth + " " + year + " ✅");
		System.out.println("Monthly Bulk Selected: " + selectedMonth + " " + year);

		// ✅ Build invoice map using int year + int month
		var invoiceMap = invoicebuilder.buildMonthlyInvoicesForAllClients(year, monthValue);

		if (invoiceMap.isEmpty()) {
			toast("No jobs found for selected Month and Year.");
			return;
		}

		// ✅ Generate workbook (PASS SAME SIGNATURE)
		invoicegeneration.generateMonthlyClientWorkbook(ym, invoiceMap);

		toast("Monthly invoices generated successfully ✅");
	}

	@FXML
	private void onCreateJobInvoiceClicked(MouseEvent e) {
		if (InvoiceStorageService.getSavePath() == null) {
			toast("Please choose Save Location first.");
			return;
		}

		if (selectedJobs.isEmpty()) {
			toast("Please enter at least 1 Job ID.");
			return;
		}

		toast("Creating Job Invoice for " + selectedJobs.size() + " job(s) ✅");
		System.out.println("Selected Jobs: " + selectedJobs);
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

	            if (empty || date == null) return;

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
	        if (!dp.isShowing()) dp.show();
	    });

	    dp.focusedProperty().addListener((obs, oldV, newV) -> {
	        if (newV && !dp.isShowing()) dp.show();
	    });
	}


	// ==========================================================
	// TOAST
	// ==========================================================
	private void toast(String message) {
		Stage stage = (Stage) ((Node) clientComboBox).getScene().getWindow();
		Toast.show(stage, message);
	}
}
