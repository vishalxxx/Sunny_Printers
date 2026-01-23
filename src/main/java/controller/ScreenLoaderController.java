package controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public class ScreenLoaderController {

    @FXML private StackPane overlayRoot;
    @FXML private Label titleLabel;
    @FXML private Label subLabel;

    public void setText(String title, String subtitle) {
        if (titleLabel != null) titleLabel.setText(title);
        if (subLabel != null) subLabel.setText(subtitle);
    }

    public StackPane getOverlayRoot() {
        return overlayRoot;
    }
}
