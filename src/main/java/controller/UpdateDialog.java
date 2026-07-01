package controller;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import model.AppUpdate;
import service.DownloadService;
import service.UpdateService;

public class UpdateDialog extends Stage {

    private boolean isAccepted = false;

    public UpdateDialog(UpdateService.UpdateCheckResult result) {
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.UTILITY);
        setTitle("Sunny Printers Update Available");

        AppUpdate update = result.getUpdate();
        boolean mandatory = result.getStatus() == UpdateService.UpdateStatus.MANDATORY_UPDATE;

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #FAF6F0; -fx-border-color: #EADFD4; -fx-border-width: 1.5;");

        // Header Title
        Label headerLabel = new Label("Sunny Printers Update Available");
        headerLabel.getStyleClass().add("page-heading");
        headerLabel.setStyle("-fx-font-family: 'Manrope'; -fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: #3E312D;");

        Label subHeader = new Label("A new stable version of Sunny Printers is ready for use.");
        subHeader.getStyleClass().add("page-subheading");
        subHeader.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 13px; -fx-text-fill: #8E7B71;");

        // Info Grid
        GridPane grid = new GridPane();
        grid.setHgap(32);
        grid.setVgap(10);
        grid.setPadding(new Insets(4, 0, 8, 0));

        addGridRow(grid, 0, "Current Version:", result.getLocalVersion());
        addGridRow(grid, 1, "Latest Version:", update.getVersion());
        addGridRow(grid, 2, "Release Date:", update.getCreatedAt() != null ? update.getCreatedAt().split("T")[0] : "N/A");
        addGridRow(grid, 3, "Mandatory Update:", mandatory ? "Yes" : "No");

        // Release Notes Area
        Label notesHeader = new Label("Release Notes:");
        notesHeader.setStyle("-fx-font-family: 'Manrope'; -fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: #3E312D;");

        Label notesContent = new Label(update.getReleaseNotes() != null ? update.getReleaseNotes() : "No release notes provided.");
        notesContent.setWrapText(true);
        notesContent.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 12px; -fx-text-fill: #6B5E58; -fx-line-spacing: 0.3em;");
        notesContent.setMaxWidth(400);

        ScrollPane scrollPane = new ScrollPane(notesContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(100);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: #EADFD4; -fx-border-radius: 8; -fx-padding: 8;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Buttons Bar
        HBox buttonBar = new HBox(12);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(8, 0, 0, 0));

        Button btnOk = new Button("OK");
        btnOk.setDefaultButton(true);
        btnOk.setStyle("-fx-background-color: #CD7B4E; -fx-text-fill: white; -fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 10 24; -fx-cursor: hand; -fx-background-radius: 8;");
        btnOk.setOnAction(e -> {
            isAccepted = true;
            close();
            try {
                DownloadService ds = new DownloadService();
                String url = ds.getDownloadUrl(update);
                javafx.application.Platform.runLater(() -> {
                    DownloadProgressDialog dpd = new DownloadProgressDialog(
                        this, update, ds, url
                    );
                    dpd.startDownload();
                });
            } catch (Exception ex) {
                System.err.println("[UpdateDialog] Failed to launch download: " + ex.getMessage());
                javafx.application.Platform.runLater(() -> {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle("Download Error");
                    alert.setHeaderText("Failed to Resolve Update URL");
                    alert.setContentText("Could not resolve the download link for the installer: " + ex.getMessage());
                    alert.showAndWait();
                });
            }
        });

        Button btnLater = new Button("Update Later");
        btnLater.setStyle("-fx-background-color: #F5EFE7; -fx-text-fill: #8E7B71; -fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 10 20; -fx-cursor: hand; -fx-background-radius: 8;");
        btnLater.setOnAction(e -> close());

        Button btnIgnore = new Button("Ignore This Version");
        btnIgnore.setStyle("-fx-background-color: #F5EFE7; -fx-text-fill: #A67C52; -fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 10 16; -fx-cursor: hand; -fx-background-radius: 8;");
        btnIgnore.setOnAction(e -> {
            new UpdateService().setIgnoredVersion(update.getVersion());
            close();
        });

        // Focus hover styling for buttons
        btnOk.setOnMouseEntered(e -> btnOk.setStyle("-fx-background-color: #B06938; -fx-text-fill: white; -fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 10 24; -fx-cursor: hand; -fx-background-radius: 8;"));
        btnOk.setOnMouseExited(e -> btnOk.setStyle("-fx-background-color: #CD7B4E; -fx-text-fill: white; -fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 10 24; -fx-cursor: hand; -fx-background-radius: 8;"));
        btnLater.setOnMouseEntered(e -> btnLater.setStyle("-fx-background-color: #EADFD4; -fx-text-fill: #3E312D; -fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 10 20; -fx-cursor: hand; -fx-background-radius: 8;"));
        btnLater.setOnMouseExited(e -> btnLater.setStyle("-fx-background-color: #F5EFE7; -fx-text-fill: #8E7B71; -fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 10 20; -fx-cursor: hand; -fx-background-radius: 8;"));
        btnIgnore.setOnMouseEntered(e -> btnIgnore.setStyle("-fx-background-color: #EADFD4; -fx-text-fill: #A67C52; -fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 10 16; -fx-cursor: hand; -fx-background-radius: 8;"));
        btnIgnore.setOnMouseExited(e -> btnIgnore.setStyle("-fx-background-color: #F5EFE7; -fx-text-fill: #A67C52; -fx-font-family: 'Inter'; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 10 16; -fx-cursor: hand; -fx-background-radius: 8;"));

        if (mandatory) {
            // For mandatory updates, hide "Update Later" and "Ignore This Version"
            btnLater.setVisible(false);
            btnIgnore.setVisible(false);
            buttonBar.getChildren().add(btnOk);
            // Disable closing window from OS close decoration
            setOnCloseRequest(e -> e.consume());
        } else {
            buttonBar.getChildren().addAll(btnIgnore, btnLater, btnOk);
        }

        root.getChildren().addAll(headerLabel, subHeader, grid, notesHeader, scrollPane, buttonBar);

        Scene scene = new Scene(root, 460, 380);
        sunnyprinters.Main.applyAppSceneStylesheets(scene);
        setScene(scene);
    }

    private void addGridRow(GridPane grid, int row, String labelText, String valueText) {
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #8E7B71;");
        Label val = new Label(valueText);
        val.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #3E312D;");
        grid.add(lbl, 0, row);
        grid.add(val, 1, row);
    }

    public boolean isAccepted() {
        return isAccepted;
    }
}
