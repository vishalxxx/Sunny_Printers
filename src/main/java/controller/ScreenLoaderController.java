package controller;

import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class ScreenLoaderController {

    @FXML private StackPane overlayRoot;
    @FXML private Label titleLabel;
    @FXML private Label subLabel;

    public void show(String title, String sub) {
        titleLabel.setText(title);
        subLabel.setText(sub);

        overlayRoot.setOpacity(0);
        overlayRoot.setVisible(true);
        overlayRoot.setManaged(true);

        FadeTransition ft = new FadeTransition(Duration.millis(180), overlayRoot);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    public void hide() {
        FadeTransition ft = new FadeTransition(Duration.millis(180), overlayRoot);
        ft.setFromValue(overlayRoot.getOpacity());
        ft.setToValue(0);

        ft.setOnFinished(e -> {
            overlayRoot.setVisible(false);
            overlayRoot.setManaged(false);
        });

        ft.play();
    }
}
