package controller;

import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import model.DownloadItem;
import javafx.collections.ObservableList;
import javafx.collections.FXCollections;
import java.io.File;
import java.awt.Desktop;
import java.io.IOException;
import javafx.scene.layout.StackPane;
import java.time.format.DateTimeFormatter;

public class DownloadsPopupController {

    @FXML private VBox downloadsRoot;
    @FXML private ScrollPane itemsScroll;
    @FXML private Label lblTitle;
    @FXML private VBox itemsContainer;

    private ObservableList<DownloadItem> downloads = FXCollections.observableArrayList();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    @FXML
    public void initialize() {
        if (downloadsRoot != null) {
            downloadsRoot.setSnapToPixel(true);
        }
        if (itemsScroll != null) {
            itemsScroll.setSnapToPixel(true);
        }
        if (itemsContainer != null) {
            itemsContainer.setSnapToPixel(true);
        }
    }

    public void setDownloads(ObservableList<DownloadItem> downloads) {
        this.downloads = downloads;
        renderItems();
    }

    private void renderItems() {
        itemsContainer.getChildren().clear();
        lblTitle.setText("Downloads (" + downloads.size() + ")");

        for (DownloadItem item : downloads) {
            itemsContainer.getChildren().add(createItemRow(item));
        }
    }

    private VBox createItemRow(DownloadItem item) {
        VBox row = new VBox();
        row.setSnapToPixel(true);
        row.getStyleClass().add("download-item-row");
        row.setOnMouseClicked(e -> handleOpenFile(item.getFilePath()));

        HBox topPart = new HBox(8);
        topPart.setSnapToPixel(true);
        topPart.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Icon
        StackPane iconContainer = new StackPane();
        iconContainer.setSnapToPixel(true);
        iconContainer.getStyleClass().addAll("file-icon-container", 
            item.getFileType().equalsIgnoreCase("PDF") ? "file-icon-pdf" : "file-icon-excel");
        iconContainer.getChildren().add(new Region());
        
        // Text Info
        VBox textInfo = new VBox(2);
        textInfo.setSnapToPixel(true);
        Label nameLbl = new Label(item.getFileName());
        nameLbl.getStyleClass().add("file-name");
        nameLbl.setSnapToPixel(true);

        Label metaLbl = new Label(item.getDownloadDate().format(formatter) + " • " + item.getFileSize());
        metaLbl.getStyleClass().add("file-meta");
        metaLbl.setSnapToPixel(true);
        
        textInfo.getChildren().addAll(nameLbl, metaLbl);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        
        // Row Action (Open Folder)
        Button actionBtn = new Button();
        actionBtn.getStyleClass().add("row-action-btn");
        Region actionIcon = new Region();
        actionIcon.getStyleClass().add("row-action-icon");
        actionBtn.setGraphic(actionIcon);
        actionBtn.setOnAction(e -> {
            e.consume(); // Prevent row click
            handleOpenFolderLocation(item.getFilePath());
        });

        topPart.getChildren().addAll(iconContainer, textInfo, spacer, actionBtn);
        row.getChildren().add(topPart);

        return row;
    }

    @FXML
    private void handleViewAll() {
        // Implement View All logic if needed
        System.out.println("View All clicked");
    }

    @FXML
    private void handleOpenFolder() {
        if (!downloads.isEmpty()) {
            handleOpenFolderLocation(downloads.get(0).getFilePath());
        }
    }

    private void handleOpenFile(String path) {
        try {
            File file = new File(path);
            if (file.exists()) {
                Desktop.getDesktop().open(file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleOpenFolderLocation(String path) {
        try {
            File file = new File(path);
            if (file.exists()) {
                Desktop.getDesktop().open(file.getParentFile());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
