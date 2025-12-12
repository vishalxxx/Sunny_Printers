package utils;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class PopupUtil {

	public static boolean showJobSummary(String summaryText) {

		final boolean[] result = { false };

		Stage dialog = new Stage();
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle("Job Summary Preview");

		// TITLE
		Label title = new Label("Job Summary");
		title.getStyleClass().add("popup-title");

		// SUMMARY TEXT AREA (read-only)
		TextArea summary = new TextArea(summaryText);
		summary.setEditable(false);
		summary.setWrapText(true);
		summary.getStyleClass().add("popup-summary");

		// ADD JOB BUTTON
		Button addBtn = new Button("Add Job");
		addBtn.getStyleClass().add("popup-add-btn");

		// CANCEL BUTTON
		Button cancelBtn = new Button("Cancel");
		cancelBtn.getStyleClass().add("popup-cancel-btn");

		addBtn.setOnAction(e -> {
			result[0] = true;
			dialog.close();
		});

		cancelBtn.setOnAction(e -> {
			result[0] = false;
			dialog.close();
		});

		// BUTTON ROW
		HBox buttonRow = new HBox(cancelBtn, addBtn);
		buttonRow.getStyleClass().add("popup-buttons");

		// ROOT
		VBox root = new VBox(title, summary, buttonRow);
		root.getStyleClass().add("popup-root");

		Scene scene = new Scene(root, 450, 350);

		// APPLY CSS
		scene.getStylesheets().add(PopupUtil.class.getResource("/css/popup.css").toExternalForm());

		dialog.setScene(scene);
		dialog.showAndWait();

		return result[0];
	}
}
