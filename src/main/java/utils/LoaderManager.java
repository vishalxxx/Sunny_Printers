package utils;

import javafx.util.Duration;

import controller.GlobalLoaderController;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

public class LoaderManager {

    private static StackPane overlay;
    private static GlobalLoaderController controller;

    public static void init(StackPane rootStackPane) {
        // rootStackPane = your main StackPane above everything
        try {
            FXMLLoader loader = new FXMLLoader(LoaderManager.class.getResource("/fxml/global_loader.fxml"));
            Parent view = loader.load();
            controller = loader.getController();

            overlay = (StackPane) view;
            overlay.setVisible(false);
            overlay.setManaged(false);

            // load css
            overlay.getStylesheets().add(LoaderManager.class.getResource("/css/global_loader.css").toExternalForm());

            rootStackPane.getChildren().add(overlay);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void show(String title, String subtitle, String status, String hint) {
        Platform.runLater(() -> {
            if (overlay == null) return;

            controller.setTitle(title);
            controller.setSubtitle(subtitle);
            controller.setStatus(status);
            controller.setHint(hint);
            controller.setProgress(0.0);

            overlay.setVisible(true);
            overlay.setManaged(true);
        });
    }

    public static void setProgress(double value) {
        Platform.runLater(() -> {
            if (controller != null) controller.setProgress(value);
        });
    }

    public static void hide() {
        Platform.runLater(() -> {
            if (overlay == null) return;
            overlay.setVisible(false);
            overlay.setManaged(false);
        });
    }
    
    public static void show(String title, String subtitle) {
        show(title, subtitle, "", "");
    }

    private static StackPane centerOverlay;
    private static Label centerTitle;
    private static Label centerSub;
    private static Pane boundParent;

    public static void showScreenLoader(Pane parentPane, String title, String subtitle) {
        Platform.runLater(() -> {
            boundParent = parentPane;

            if (centerOverlay == null) {
                centerOverlay = new StackPane();
                centerOverlay.setManaged(false);
                centerOverlay.setVisible(false);
                centerOverlay.getStyleClass().add("center-loader-overlay");

                StackPane card = new StackPane();
                card.getStyleClass().add("center-loader-card");

                ProgressIndicator spinner = new ProgressIndicator();
                spinner.setPrefSize(42, 42);

                centerTitle = new Label("Loading Screen...");
                centerTitle.getStyleClass().add("center-loader-title");

                centerSub = new Label("Please wait");
                centerSub.getStyleClass().add("center-loader-subtitle");

                javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(10, spinner, centerTitle, centerSub);
                box.setAlignment(javafx.geometry.Pos.CENTER);

                card.getChildren().add(box);
                centerOverlay.getChildren().add(card);
            }

            centerTitle.setText(title);
            centerSub.setText(subtitle);

            // ✅ bind overlay to parent size
            centerOverlay.prefWidthProperty().bind(parentPane.widthProperty());
            centerOverlay.prefHeightProperty().bind(parentPane.heightProperty());

            if (!parentPane.getChildren().contains(centerOverlay)) {
                parentPane.getChildren().add(centerOverlay);
            }

            centerOverlay.toFront();
            centerOverlay.setOpacity(0);
            centerOverlay.setVisible(true);

            FadeTransition ft = new FadeTransition(Duration.millis(180), centerOverlay);
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.play();
        });
    }

    public static void hideScreenLoader() {
        Platform.runLater(() -> {
            if (centerOverlay == null) return;

            FadeTransition ft = new FadeTransition();
            ft.setDuration(Duration.millis(180));
            ft.setNode(centerOverlay);            ft.setFromValue(centerOverlay.getOpacity());
            ft.setToValue(0);

            ft.setOnFinished(e -> centerOverlay.setVisible(false));
            ft.play();
        });
    }
    
}
