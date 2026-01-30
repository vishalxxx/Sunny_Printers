package utils;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;

public class ViewportContainer extends StackPane {

    private final double designWidth;
    private final double designHeight;

    public ViewportContainer(Node content,
                             double designWidth,
                             double designHeight) {

        this.designWidth = designWidth;
        this.designHeight = designHeight;

        // Wrapper that enforces design size
        StackPane fixedViewport = new StackPane(content);
        fixedViewport.setPrefSize(designWidth, designHeight);
        fixedViewport.setMinSize(designWidth, designHeight);
        fixedViewport.setMaxSize(designWidth, designHeight);

        // Scroll container (for smaller screens)
        ScrollPane scrollPane = new ScrollPane(fixedViewport);
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(false);
        scrollPane.setPannable(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background-color: transparent;");

        // Center everything
        getChildren().add(scrollPane);
        setStyle("-fx-background-color: #0F1115;");
    }
}
