package utils;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;

public class BreadcrumbUtil {

    public static void populateBreadcrumbs(HBox container, String currentScreenTitle, Runnable backAction) {
        if (container == null) return;
        
        container.getChildren().clear();
        
        NavigationManager nav = NavigationManager.getInstance();
        List<NavigationManager.NavState> history = nav.getHistory();
        
        // Add Back Button if we have history
        if (!history.isEmpty() && history.size() > 1) {
            Button backBtn = new Button();
            backBtn.getStyleClass().add("breadcrumb-back-btn");
            Region icon = new Region();
            icon.getStyleClass().add("back-icon");
            backBtn.setGraphic(icon);
            if (backAction != null) {
                backBtn.setOnAction(e -> backAction.run());
            }
            container.getChildren().add(backBtn);
        }

        for (int i = 0; i < history.size(); i++) {
            NavigationManager.NavState state = history.get(i);
            String title = state.getTitle();
            
            if (title == null || title.isEmpty()) continue;
            
            Label label = new Label(title.toUpperCase());
            label.getStyleClass().add("breadcrumb-label");
            
            if (i == history.size() - 1) {
                label.getStyleClass().add("breadcrumb-current");
            }
            
            container.getChildren().add(label);
            
            if (i < history.size() - 1) {
                Label separator = new Label("›");
                separator.getStyleClass().add("breadcrumb-separator");
                container.getChildren().add(separator);
            }
        }
    }

    public static void populateBreadcrumbs(HBox container, String currentScreenTitle) {
        populateBreadcrumbs(container, currentScreenTitle, null);
    }
}
