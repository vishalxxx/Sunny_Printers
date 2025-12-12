package utils;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class DeleteConfirmationDialog {

	public static boolean show(String clientName) {

		final boolean[] result = { false };

		Stage dialog = new Stage();
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle("Confirm Delete");

		Label msg = new Label("Type DELETE to confirm removing client:\n" + clientName);
		msg.setStyle("-fx-text-fill: white; -fx-font-size: 1.1em;");

		TextField input = new TextField();
		input.setPromptText("Type DELETE");

		Button confirmBtn = new Button("Delete");
		confirmBtn.setDisable(true);
		confirmBtn.setStyle("-fx-background-color: #c62828; -fx-text-fill: white; -fx-font-weight: bold;");

		Button cancelBtn = new Button("Cancel");

		// Enable delete only when text matches
		input.textProperty().addListener((obs, oldVal, newVal) -> {
			confirmBtn.setDisable(!"DELETE".equalsIgnoreCase(newVal.trim()));
		});

		confirmBtn.setOnAction(e -> {
			result[0] = true;
			dialog.close();
		});

		cancelBtn.setOnAction(e -> {
			result[0] = false;
			dialog.close();
		});

		VBox box = new VBox(14, msg, input, confirmBtn, cancelBtn);
		box.setAlignment(Pos.CENTER);
		box.setPadding(new Insets(20));
		box.setStyle("-fx-background-color: #222; -fx-padding: 20;");

		dialog.setScene(new Scene(box, 400, 220));
		dialog.showAndWait();

		return result[0];
	}
}
