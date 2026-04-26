package utils;

import java.util.Stack;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Parent;
import javafx.scene.layout.VBox;

public class NavigationManager {

    private static NavigationManager instance;
    private final Stack<NavState> history = new Stack<>();
    private NavState currentState;
    private final BooleanProperty canGoBack = new SimpleBooleanProperty(false);

    private NavigationManager() {
    }

    public static synchronized NavigationManager getInstance() {
        if (instance == null) {
            instance = new NavigationManager();
        }
        return instance;
    }

    public void push(String fxmlPath, String title, String subtitle, String activeSidebarId) {
        if (currentState != null) {
            history.push(currentState);
            canGoBack.set(true);
        }
        currentState = new NavState(fxmlPath, title, subtitle, activeSidebarId, null, null);
    }

    public void updateCurrentState(Parent view, Object controller) {
        if (currentState != null) {
            currentState.setView(view);
            currentState.setController(controller);
        }
    }

    public NavState pop() {
        if (!history.isEmpty()) {
            currentState = history.pop();
            canGoBack.set(!history.isEmpty());
            return currentState;
        }
        return null;
    }

    public boolean hasHistory() {
        return !history.isEmpty();
    }

    public void clear() {
        history.clear();
        currentState = null;
        canGoBack.set(false);
    }

    public BooleanProperty canGoBackProperty() {
        return canGoBack;
    }

    public java.util.List<NavState> getHistory() {
        java.util.List<NavState> list = new java.util.ArrayList<>(history);
        if (currentState != null) {
            list.add(currentState);
        }
        return list;
    }

    public NavState getCurrentState() {
        return currentState;
    }

    public static class NavState {
        private final String fxmlPath;
        private final String title;
        private final String subtitle;
        private final String activeSidebarId;
        private Parent view;
        private Object controller;

        public NavState(String fxmlPath, String title, String subtitle, String activeSidebarId, Parent view, Object controller) {
            this.fxmlPath = fxmlPath;
            this.title = title;
            this.subtitle = subtitle;
            this.activeSidebarId = activeSidebarId;
            this.view = view;
            this.controller = controller;
        }

        public String getFxmlPath() {
            return fxmlPath;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public String getActiveSidebarId() {
            return activeSidebarId;
        }

        public Parent getView() {
            return view;
        }

        public void setView(Parent view) {
            this.view = view;
        }

        public Object getController() {
            return controller;
        }

        public void setController(Object controller) {
            this.controller = controller;
        }
    }
}
