package controller;

import java.util.LinkedHashSet;
import java.util.Set;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

public class InvoiceGenerationController {

	@FXML
	private TextField jobSearchField;
	@FXML
	private FlowPane selectedJobsPane;
	@FXML
	private ScrollPane selectedJobsScroll;
	@FXML
	private Button createJobInvoiceBtn;
	@FXML
	private Label selectedLabel;

	private final Set<String> selectedJobs = new LinkedHashSet<>();

	@FXML
	private void initialize() {
		updateState();
	}

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
		createJobInvoiceBtn.setDisable(!hasJobs);
		selectedLabel.setVisible(hasJobs);
	}
}
