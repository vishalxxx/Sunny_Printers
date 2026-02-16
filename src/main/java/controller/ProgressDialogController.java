package controller;

	import javafx.animation.KeyFrame;
	import javafx.animation.Timeline;
	import javafx.application.Platform;
	import javafx.fxml.FXML;
	import javafx.scene.control.*;
	import javafx.scene.layout.StackPane;
	import javafx.util.Duration;
	
	
public class ProgressDialogController {
	


	    @FXML private StackPane root;
	    @FXML private Label titleLabel;
	    @FXML private Label statusLabel;
	    @FXML private Label percentLabel;
	    @FXML private ProgressBar progressBar;
	    @FXML private Button cancelButton;

	    private Runnable cancelHandler;

	    // =====================================================
	    // SHOW
	    // =====================================================
	    public void show(String title) {
	        titleLabel.setText(title);
	        statusLabel.setText("Preparing...");
	        percentLabel.setText("0 %");
	        progressBar.setProgress(0);

	        root.setVisible(true);
	        root.setManaged(true);
	    }

	    // =====================================================
	    // HIDE
	    // =====================================================
	    public void hide() {
	        root.setVisible(false);
	        root.setManaged(false);
	    }

	    // =====================================================
	    // UPDATE PROGRESS (THREAD SAFE)
	    // =====================================================
	    public void updateProgress(double progress, String message) {
	        Platform.runLater(() -> {
	            progressBar.setProgress(progress);
	            percentLabel.setText((int) (progress * 100) + " %");
	            if (message != null && !message.isEmpty()) {
	                statusLabel.setText(message);
	            }
	        });
	    }

	    // =====================================================
	    // SHOW ROLLBACK - animate progress bar backwards, keep window open
	    // =====================================================
	    public void showRollback(String message, Runnable onComplete) {
	        Platform.runLater(() -> {
	            statusLabel.setText(message != null ? message : "Reverting changes...");
	            cancelButton.setDisable(true);
	            double from = Math.max(0.01, progressBar.getProgress());
	            final int steps = 15;
	            final double durationMs = 800;
	            Timeline timeline = new Timeline();
	            for (int i = 0; i <= steps; i++) {
	                final double p = from * (steps - i) / steps;
	                final boolean isLast = (i == steps);
	                timeline.getKeyFrames().add(new KeyFrame(Duration.millis(durationMs * i / steps), e -> {
	                    progressBar.setProgress(p);
	                    percentLabel.setText((int) (p * 100) + " %");
	                    if (isLast && onComplete != null) onComplete.run();
	                }));
	            }
	            timeline.play();
	        });
	    }


	    // =====================================================
	    // CANCEL SUPPORT
	    // =====================================================
	    public void setOnCancel(Runnable handler) {
	        this.cancelHandler = handler;
	    }

	    @FXML
	    private void onCancelClicked() {

	        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
	        confirm.setTitle("Cancel Process");
	        confirm.setHeaderText("Cancel invoice generation?");
	        confirm.setContentText("All progress will be rolled back.");

	        confirm.showAndWait().ifPresent(btn -> {
	            if (btn == ButtonType.OK && cancelHandler != null) {
	                cancelHandler.run();
	            }
	        });
	    }
	

}
