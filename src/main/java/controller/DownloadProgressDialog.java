package controller;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import model.AppUpdate;
import service.DownloadService;
import service.DownloadTask;

public class DownloadProgressDialog extends Stage {

    private static final Logger LOGGER = Logger.getLogger(DownloadProgressDialog.class.getName());

    private final AppUpdate update;
    private final DownloadService downloadService;
    private final DownloadTask downloadTask;
    private final Path targetFile;

    private boolean isFinishedSuccessfully = false;

    public DownloadProgressDialog(Stage owner, AppUpdate update, DownloadService downloadService, String downloadUrl) {
        this.update = update;
        this.downloadService = downloadService;
        this.targetFile = downloadService.getTargetFilePath(update);

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.UTILITY);
        setTitle("Downloading Update");

        // Create the task
        this.downloadTask = new DownloadTask(update, targetFile, downloadUrl);

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #FAF6F0; -fx-border-color: #EADFD4; -fx-border-width: 1.5;");

        // Header Title
        Label headerLabel = new Label("Downloading Sunny Printers Update");
        headerLabel.setStyle("-fx-font-family: 'Manrope'; -fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #3E312D;");

        // Grid info
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(8);
        grid.setPadding(new Insets(4, 0, 8, 0));

        addGridRow(grid, 0, "File Name:", update.getFileName());
        addGridRow(grid, 1, "Version:", update.getVersion());
        addGridRow(grid, 2, "File Size:", formatSize(update.getFileSize()));

        // Progress bar and percentage
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(20);
        progressBar.setStyle("-fx-accent: #CD7B4E; -fx-control-inner-background: #FAF6F0; -fx-text-box-border: #EADFD4;");
        VBox.setVgrow(progressBar, Priority.ALWAYS);

        Label lblPercentage = new Label("0%");
        lblPercentage.setStyle("-fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-text-fill: #3E312D;");

        HBox progressBox = new HBox(12, progressBar, lblPercentage);
        progressBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        // Status Message (Speed, time remaining)
        Label lblStatus = new Label("Connecting...");
        lblStatus.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 12px; -fx-text-fill: #8E7B71;");

        // Bindings
        progressBar.progressProperty().bind(downloadTask.progressProperty());
        downloadTask.progressProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.doubleValue() >= 0) {
                lblPercentage.setText(String.format("%.0f%%", newVal.doubleValue() * 100));
            }
        });
        lblStatus.textProperty().bind(downloadTask.messageProperty());

        // Buttons Bar
        HBox buttonBar = new HBox(12);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(8, 0, 0, 0));

        Button btnHide = new Button("Hide");
        btnHide.setStyle("-fx-background-color: #F5EFE7; -fx-text-fill: #8E7B71; -fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 8 20; -fx-cursor: hand; -fx-background-radius: 8;");
        btnHide.setOnAction(e -> {
            // Just hide the window, keep downloading in background
            hide();
        });

        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-color: #F5EFE7; -fx-text-fill: #CD7B4E; -fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 8 20; -fx-cursor: hand; -fx-background-radius: 8;");
        btnCancel.setOnAction(e -> {
            downloadTask.cancel();
            close();
        });

        // Hover stylings
        btnHide.setOnMouseEntered(e -> btnHide.setStyle("-fx-background-color: #EADFD4; -fx-text-fill: #3E312D; -fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 8 20; -fx-cursor: hand; -fx-background-radius: 8;"));
        btnHide.setOnMouseExited(e -> btnHide.setStyle("-fx-background-color: #F5EFE7; -fx-text-fill: #8E7B71; -fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 8 20; -fx-cursor: hand; -fx-background-radius: 8;"));
        btnCancel.setOnMouseEntered(e -> btnCancel.setStyle("-fx-background-color: #EADFD4; -fx-text-fill: #B06938; -fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 8 20; -fx-cursor: hand; -fx-background-radius: 8;"));
        btnCancel.setOnMouseExited(e -> btnCancel.setStyle("-fx-background-color: #F5EFE7; -fx-text-fill: #CD7B4E; -fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 8 20; -fx-cursor: hand; -fx-background-radius: 8;"));

        buttonBar.getChildren().addAll(btnHide, btnCancel);
        root.getChildren().addAll(headerLabel, grid, progressBox, lblStatus, buttonBar);

        Scene scene = new Scene(root, 440, 260);
        sunnyprinters.Main.applyAppSceneStylesheets(scene);
        setScene(scene);

        // Setup Task Handlers
        downloadTask.setOnSucceeded(e -> {
            LOGGER.info("[DownloadProgressDialog] Download task succeeded. Verifying integrity...");
            boolean verified = downloadService.verifyFile(targetFile, update);
            if (verified) {
                isFinishedSuccessfully = true;
                showSuccessAlert(targetFile.toAbsolutePath().toString(), update.getVersion(), update.getSha256());
            } else {
                showErrorAlert("File verification failed. The downloaded installer's size or SHA-256 hash does not match the database.");
            }
            close();
        });

        downloadTask.setOnFailed(e -> {
            Throwable ex = downloadTask.getException();
            String errorMsg = ex != null ? ex.getMessage() : "Unknown error occurred during download.";
            LOGGER.log(Level.SEVERE, "[DownloadProgressDialog] Download task failed: " + errorMsg, ex);
            showErrorAlert("Download failed: " + errorMsg);
            downloadService.cleanupCorruptFile(targetFile);
            close();
        });

        downloadTask.setOnCancelled(e -> {
            LOGGER.info("[DownloadProgressDialog] Download cancelled.");
            // Do NOT clean up the file here to allow Resume on future checks if needed.
        });

        // Block OS window close request - force user to use Cancel or Hide
        setOnCloseRequest(e -> e.consume());
    }

    public void startDownload() {
        Thread thread = new Thread(downloadTask);
        thread.setDaemon(true);
        thread.start();
        show();
    }

    private void addGridRow(GridPane grid, int row, String labelText, String valueText) {
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #8E7B71;");
        Label val = new Label(valueText != null ? valueText : "N/A");
        val.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #3E312D;");
        grid.add(lbl, 0, row);
        grid.add(val, 1, row);
    }

    private String formatSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
        return String.format("%.2f %s", size/Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private void showSuccessAlert(String path, String version, String sha) {
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Update Ready");
            alert.setHeaderText("Installer Downloaded & Verified");
            alert.setContentText("The update installer has been successfully downloaded and verified.\n\n"
                    + "Version: " + version + "\n\n"
                    + "The application must close to apply the update. Would you like to launch the installer and exit now?");
            
            javafx.scene.control.ButtonType buttonTypeYes = new javafx.scene.control.ButtonType("Install & Exit", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
            javafx.scene.control.ButtonType buttonTypeNo = new javafx.scene.control.ButtonType("Later", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(buttonTypeYes, buttonTypeNo);

            java.util.Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == buttonTypeYes) {
                try {
                    LOGGER.info("[DownloadProgressDialog] Launching installer: " + path);
                    ProcessBuilder pb = new ProcessBuilder("msiexec.exe", "/i", path);
                    pb.start();
                    System.exit(0);
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "[DownloadProgressDialog] Failed to launch installer", ex);
                    showErrorAlert("Failed to launch installer: " + ex.getMessage());
                }
            }
        });
    }

    private void showErrorAlert(String msg) {
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Download Error");
            alert.setHeaderText("Update Download Failed");
            alert.setContentText(msg);
            alert.showAndWait();
        });
    }

    public boolean isFinishedSuccessfully() {
        return isFinishedSuccessfully;
    }
}
