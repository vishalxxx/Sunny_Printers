package utils;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

public class Toast {

	public static void show(Stage stage, String message) {

		Platform.runLater(() -> {

			Label label = new Label(message);
			label.setStyle("-fx-background-color: rgba(0,0,0,0.85);" + "-fx-text-fill: white;" + "-fx-padding: 10 20;"
					+ "-fx-background-radius: 8;" + "-fx-font-size: 1.1em;");

			// The original root (BorderPane in your case)
			var originalRoot = stage.getScene().getRoot();

			// If root is already a StackPane → use it
			StackPane stackRoot;
			if (originalRoot instanceof StackPane sp) {
				stackRoot = sp;
			} else {
				// Wrap BorderPane or other layouts inside StackPane
				stackRoot = new StackPane(originalRoot);
				stage.getScene().setRoot(stackRoot);
			}

			StackPane toastPane = new StackPane(label);
			toastPane.setMouseTransparent(true);
			toastPane.setTranslateY(40);

			stackRoot.getChildren().add(toastPane);

			// Slide up animation
			TranslateTransition slide = new TranslateTransition(Duration.millis(300), toastPane);
			slide.setFromY(40);
			slide.setToY(0);
			slide.play();

			// Fade out
			FadeTransition fade = new FadeTransition(Duration.seconds(3), toastPane);
			fade.setFromValue(1.0);
			fade.setToValue(0.0);
			fade.setDelay(Duration.seconds(1.5));

			fade.setOnFinished(e -> stackRoot.getChildren().remove(toastPane));
			fade.play();
		});
	}

	public static void showUndo(Stage stage, String message, Runnable undoAction) {

		Platform.runLater(() -> {

			Label text = new Label(message);
			text.setStyle("-fx-text-fill: white; -fx-font-size: 1.1em;");

			Button undoBtn = new Button("UNDO");
			undoBtn.setStyle("-fx-background-color: #ff9800;" + "-fx-text-fill: black;" + "-fx-padding: 6 14;"
					+ "-fx-background-radius: 6;" + "-fx-font-weight: bold;");

			HBox layout = new HBox(12, text, undoBtn);
			layout.setAlignment(Pos.CENTER);
			layout.setStyle(
					"-fx-background-color: rgba(0,0,0,0.85);" + "-fx-padding: 12 20;" + "-fx-background-radius: 10;");

			// Get original root of stage
			var originalRoot = stage.getScene().getRoot();

			StackPane stackRoot;
			if (originalRoot instanceof StackPane sp) {
				stackRoot = sp;
			} else {
				stackRoot = new StackPane(originalRoot);
				stage.getScene().setRoot(stackRoot);
			}

			StackPane toastPane = new StackPane(layout);
			toastPane.setTranslateY(40);
			stackRoot.getChildren().add(toastPane);

			// When UNDO clicked
			undoBtn.setOnAction(e -> {
				undoAction.run(); // execute restore logic
				stackRoot.getChildren().remove(toastPane);
			});

			// Auto fade-out after 5 seconds
			FadeTransition fade = new FadeTransition(Duration.seconds(5), toastPane);
			fade.setFromValue(1.0);
			fade.setToValue(0.0);
			fade.setOnFinished(e -> stackRoot.getChildren().remove(toastPane));
			fade.play();
		});
	}

	public static void showUndo(Window owner, String message, Runnable undoAction, Runnable onTimeout) {

		Stage toast = new Stage();
		toast.initOwner(owner);
		toast.setAlwaysOnTop(true);
		toast.initStyle(StageStyle.TRANSPARENT);

		Label msg = new Label(message);
		msg.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");

		Button undoBtn = new Button("UNDO");
		undoBtn.setStyle("-fx-background-color: #ffca28;" + "-fx-text-fill: black;" + "-fx-font-weight: bold;"
				+ "-fx-background-radius: 6;");

		undoBtn.setOnAction(e -> {
			toast.close();
			undoAction.run();
		});

		HBox row = new HBox(12, msg, undoBtn);
		row.setAlignment(Pos.CENTER_LEFT);
		row.setPadding(new Insets(12));
		row.setStyle("-fx-background-color: rgba(0,0,0,0.85); -fx-background-radius: 8;");

		Scene scene = new Scene(row);
		scene.setFill(Color.TRANSPARENT);
		toast.setScene(scene);

		toast.show();

		// Auto hide after 4 seconds → clears undo memory
		PauseTransition delay = new PauseTransition(Duration.seconds(4));
		delay.setOnFinished(e -> {
			toast.close();
			onTimeout.run(); // clearing UndoDeleteManager
		});
		delay.play();
	}

}
