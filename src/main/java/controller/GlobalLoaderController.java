package controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;

public class GlobalLoaderController {

    @FXML private StackPane overlayRoot;

    @FXML private ProgressIndicator spinner;
    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;

    @FXML private Label statusLabel;
    @FXML private Label percentLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Label hintLabel;

    public void setTitle(String text) {
        titleLabel.setText(text);
    }

    public void setSubtitle(String text) {
        subtitleLabel.setText(text);
    }

    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    public void setHint(String text) {
        hintLabel.setText(text);
    }

    public void setProgress(double value) {
        if (value < 0) value = 0;
        if (value > 1) value = 1;

        progressBar.setProgress(value);

        int percent = (int) Math.round(value * 100);
        percentLabel.setText(percent + "%");
    }

    public StackPane getRoot() {
        return overlayRoot;
    }
}
