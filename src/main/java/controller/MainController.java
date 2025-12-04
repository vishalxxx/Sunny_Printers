package controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MainController implements Initializable {

    // Sidebar containers
    @FXML private VBox mainSidebar;
    @FXML private VBox jobsSidebar;
    @FXML private VBox clientsSidebar;
    @FXML private VBox billingSidebar;
    @FXML private VBox paymentSidebar;
    @FXML private VBox ledgerSidebar;

    // Center content container
    @FXML private VBox centerRoot;

    // ScrollPanes
    @FXML private ScrollPane sidebarScroll;
    @FXML private ScrollPane centerScroll;

    // Top title
    @FXML private Label pageTitle;

    // Root layout container
    @FXML private BorderPane root;


    @Override
    public void initialize(URL location, ResourceBundle resources) {

        // Show only the main sidebar initially
        showOnly(mainSidebar);
        setPageTitle("Dashboard");

        Platform.runLater(() -> {

            Stage stage = (Stage) root.getScene().getWindow();

            // -----------------------
            // 1. Dynamic UI Scaling
            // -----------------------
            ChangeListener<Number> resizeListener = (obs, oldVal, newVal) -> {

                double w = stage.getWidth();
                double h = stage.getHeight();

                // baseline scaling = 900px height = 1.0em
                double scale = Math.min(w, h) / 900.0;

                scale = Math.max(scale, 0.85); // minimum
                scale = Math.min(scale, 1.8);  // maximum

                root.setStyle("-fx-font-size: " + scale + "em;");
            };

            stage.widthProperty().addListener(resizeListener);
            stage.heightProperty().addListener(resizeListener);


            // ---------------------------------------------------
            // 2. FINAL FIX: Make center area fill full screen
            // ---------------------------------------------------
            // Only set MIN HEIGHT (never prefHeight!)
            // This avoids binding conflicts inside ScrollPane.
            if (centerScroll != null && centerRoot != null) {

                centerScroll.viewportBoundsProperty().addListener((obs, oldB, newB) -> {
                    if (newB != null && newB.getHeight() > 0) {
                        centerRoot.setMinHeight(newB.getHeight());
                    }
                });

                // Initial set (in case viewport is already available)
                if (centerScroll.getViewportBounds() != null) {
                    centerRoot.setMinHeight(centerScroll.getViewportBounds().getHeight());
                }
            }
        });
    }

    // ---------------------------
    // Sidebar / Page switching
    // ---------------------------

    private void hideSidebar(VBox box) {
        if (box != null) {
            box.setVisible(false);
            box.setManaged(false);
        }
    }

    private void hideAllSidebars() {
        hideSidebar(mainSidebar);
        hideSidebar(jobsSidebar);
        hideSidebar(clientsSidebar);
        hideSidebar(billingSidebar);
        hideSidebar(paymentSidebar);
        hideSidebar(ledgerSidebar);
    }

    private void showOnly(VBox target) {
        hideAllSidebars();
        if (target != null) {
            target.setVisible(true);
            target.setManaged(true);
        }
    }

    private void setPageTitle(String title) {
        if (pageTitle != null) {
            pageTitle.setText(title);
        }
    }

    // Event handlers for sidebar menu items

    @FXML private void openDashboard(MouseEvent event) {
        showOnly(mainSidebar);
        setPageTitle("Dashboard");
    }

    @FXML private void showJobsSubmenu(MouseEvent event) {
        showOnly(jobsSidebar);
        setPageTitle("Job Details");
    }

    @FXML private void showClientsSubmenu(MouseEvent event) {
        showOnly(clientsSidebar);
        setPageTitle("Client Details");
    }

    @FXML private void showBillingSubmenu(MouseEvent event) {
        showOnly(billingSidebar);
        setPageTitle("Billing");
    }

    @FXML private void showPaymentSubmenu(MouseEvent event) {
        showOnly(paymentSidebar);
        setPageTitle("Payments");
    }

    @FXML private void showLedgerSubmenu(MouseEvent event) {
        showOnly(ledgerSidebar);
        setPageTitle("Ledger");
    }

    @FXML private void showMainSidebar(MouseEvent event) {
        openDashboard(event);
    }
    
    @FXML
    private void loadAddClient(MouseEvent event) {
        try {
            Parent addClientView = FXMLLoader.load(getClass().getResource("/fxml/add_client.fxml"));
            centerRoot.getChildren().setAll(addClientView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    
}
