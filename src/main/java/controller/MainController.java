package controller;
import javafx.scene.shape.Rectangle;

import java.net.URL;
import java.util.ResourceBundle;


import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.Job;
import service.JobService;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class MainController implements Initializable {

	private final double COLLAPSED_WIDTH = 82;
	private final double EXPANDED_WIDTH = 260;
	@FXML private StackPane appRoot;
	@FXML private StackPane centerWrapper;

	
	@FXML private StackPane sidebarStack;
	private Timeline anim;

	Job currentJob = new Job();
	// Center content container

	public void goDashboard() {
	    setPageTitle("Dashboard");
	    // if you also want to show main sidebar here then do it
	}
	
	@FXML
	private VBox centerRoot;
	// singleton-like reference so other controllers can reach MainController
	private static MainController instance;

	// no-arg constructor — FXMLLoader will call this when creating the controller
	public MainController() {
		instance = this;
	}

	/** Return the MainController instance created by the FXMLLoader */
	public static MainController getInstance() {
		return instance;
	}

	public void setCenterContent(Parent view) {
		centerRoot.getChildren().setAll(view);
	}
	
	@FXML private VBox globalLoader;
	@FXML private Label loaderTitle;
	@FXML private Label loaderSubtitle;
	@FXML private ProgressBar loaderBar;

	public void showGlobalLoader(String title, String subtitle) {
	    loaderTitle.setText(title);
	    loaderSubtitle.setText(subtitle);

	    globalLoader.setVisible(true);
	    globalLoader.setManaged(true);
	}

	public void hideGlobalLoader() {
	    globalLoader.setVisible(false);
	    globalLoader.setManaged(false);
	}

	

	// Sidebar containers
	@FXML
	private VBox mainSidebar;
	@FXML
	private VBox jobsSidebar;
	@FXML
	private VBox clientsSidebar;
	@FXML
	private VBox billingSidebar;
	@FXML
	private VBox paymentSidebar;
	@FXML
	private VBox ledgerSidebar;

	// ScrollPanes
	@FXML
	private ScrollPane sidebarScroll;
	@FXML
	private ScrollPane centerScroll;

	// Top title
	@FXML
	private Label pageTitle;

	// Root layout container
	@FXML
	private BorderPane root;

	public BorderPane getRoot() {
		return root;
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		
		setPageTitle("Dashboard");
		openCenterDashboard();
		Rectangle clip = new Rectangle();
	    clip.widthProperty().bind(sidebarScroll.widthProperty());
	    clip.heightProperty().bind(sidebarScroll.heightProperty());

	    sidebarScroll.setClip(clip);
		sidebarStack.prefWidthProperty().bind(sidebarScroll.widthProperty());
		sidebarStack.minWidthProperty().bind(sidebarScroll.widthProperty());
		sidebarStack.maxWidthProperty().bind(sidebarScroll.widthProperty());

		mainSidebar.prefWidthProperty().bind(sidebarScroll.widthProperty());
		mainSidebar.minWidthProperty().bind(sidebarScroll.widthProperty());
		mainSidebar.maxWidthProperty().bind(sidebarScroll.widthProperty());


			//Collapsable Sidebar Code
		  // ✅ Start in collapsed state
        sidebarScroll.setPrefWidth(COLLAPSED_WIDTH);
        applyCollapsedStyleToAll(true);
        sidebarStack.getStyleClass().add("sidebar-collapsed-bg");
        sidebarStack.getStyleClass().remove("sidebar-expanded-bg");
        // ✅ Hover expand
        sidebarScroll.setOnMouseEntered(e -> expandSidebar());

        // ✅ Mouse exit collapse
        sidebarScroll.setOnMouseExited(e -> collapseSidebar());
		
		
		//Collapsable sidebar Code END
		
		
		
		
		
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
				// baseline = 900px = 1.0
				double scale = Math.min(w, h) / 900.0;

				// Never go below 1.0 (never shrink)
				if (scale < 1.0) {
					scale = 1.0;
				}

				// Optional: maximum limit
				if (scale > 1.6) {
					scale = 1.6;
				}

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
	private void applyCollapsedStyleToAll(boolean collapsed) {

	    VBox[] sidebars = {
	            mainSidebar,
	            jobsSidebar,
	            clientsSidebar,
	            billingSidebar,
	            paymentSidebar,
	            ledgerSidebar
	    };

	    for (VBox s : sidebars) {
	        if (s == null) continue;

	        if (collapsed) {
	            if (!s.getStyleClass().contains("sidebar-collapsed")) {
	                s.getStyleClass().add("sidebar-collapsed");
	            }
	        } else {
	            s.getStyleClass().remove("sidebar-collapsed");
	        }
	    }
	}

	@FXML
	public void openCenterDashboard() {
	    loadCenterScreen("/fxml/dashboard_ui.fxml",
	            "Loading Dashboard...",
	            "Please wait");
	}
	
	private void loadCenterScreen(String fxmlPath, String title, String subtitle) {

	    if (centerLoaderIncludeController != null) {
	        centerLoaderIncludeController.show(title, subtitle);
	    }

	    new Thread(() -> {
	        try {
	            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
	            Parent view = loader.load();

	            Platform.runLater(() -> {
	                centerContentHost.getChildren().setAll(view);

	                if (centerLoaderIncludeController != null) {
	                    centerLoaderIncludeController.hide();
	                }
	            });

	        } catch (Exception ex) {
	            ex.printStackTrace();

	            Platform.runLater(() -> {
	                if (centerLoaderIncludeController != null) {
	                    centerLoaderIncludeController.hide();
	                }
	            });
	        }
	    }).start();
	}

	private void expandSidebar() {

	    applyCollapsedStyleToAll(false);

	    sidebarStack.getStyleClass().remove("sidebar-collapsed-bg");
	    sidebarStack.getStyleClass().add("sidebar-expanded-bg");

	    animateSidebarWidth(EXPANDED_WIDTH);
	}
	private void collapseSidebar() {

	    animateSidebarWidth(COLLAPSED_WIDTH);

	    if (anim != null) anim.setOnFinished(e -> {

	        applyCollapsedStyleToAll(true);

	        sidebarStack.getStyleClass().add("sidebar-collapsed-bg");
	        sidebarStack.getStyleClass().remove("sidebar-expanded-bg");
	    });
	}


	    private void animateSidebarWidth(double width) {
	        if (anim != null) anim.stop();

	        anim = new Timeline(
	                new KeyFrame(Duration.millis(180),
	                        new KeyValue(sidebarScroll.prefWidthProperty(), width)
	                )
	        );
	        anim.play();
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

	@FXML
	private void openDashboard(MouseEvent event) {
		showOnly(mainSidebar);
		openCenterDashboard();
		setPageTitle("Dashboard");
	}

	@FXML
	private void showJobsSubmenu(MouseEvent event) {
		showOnly(jobsSidebar);
		setPageTitle("Job Details");
	}

	@FXML
	private void showClientsSubmenu(MouseEvent event) {
		showOnly(clientsSidebar);
		setPageTitle("Client Details");
	}

	@FXML
	private void showBillingSubmenu(MouseEvent event) {
		showOnly(billingSidebar);
		setPageTitle("Billing");
	}

	@FXML
	private void showPaymentSubmenu(MouseEvent event) {
		showOnly(paymentSidebar);
		setPageTitle("Payments");
	}

	@FXML
	private void showLedgerSubmenu(MouseEvent event) {
		showOnly(ledgerSidebar);
		setPageTitle("Ledger");
	}

	@FXML
	private void showMainSidebar(MouseEvent event) {
		openDashboard(event);
	}
	
	
	@FXML
	private void loadAddClient(MouseEvent event) {
		loadCenterScreen("/fxml/add_client.fxml",
	            "Loading Dashboard...",
	            "Please wait");
	}

	@FXML
	private void loadAddJob(MouseEvent event) {
		loadCenterScreen("/fxml/ht.fxml",
	            "Loading Dashboard...",
	            "Please wait");
	}

	@FXML
	private void loadViewJob(MouseEvent event) {
		loadCenterScreen("/fxml/view_job.fxml",
	            "Loading Dashboard...",
	            "Please wait");
	}

	@FXML
	public void loadViewClients() {
		loadCenterScreen("/fxml/view_client.fxml",
	            "Loading Dashboard...",
	            "Please wait");
	}

	

	public void setCenterView(Parent view) {
		centerContentHost.getChildren().setAll(view);
	}

	@FXML
	public void loadInvoiceGenration() {

		loadCenterScreen("/fxml/invoice_genration.fxml",
	            "Loading Dashboard...",
	            "Please wait");
	}


//		try {
//			Parent view = FXMLLoader.load(getClass().getResource("/fxml/invoice_genration.fxml"));
//			centerRoot.getChildren().setAll(view);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//	}
	
	@FXML private StackPane centerLoaderInclude;
	public void showCenterLoader() {
	    centerLoaderInclude.setVisible(true);
	    centerLoaderInclude.setManaged(true);
	}

	public void hideCenterLoader() {
	    centerLoaderInclude.setVisible(false);
	    centerLoaderInclude.setManaged(false);
	}

	@FXML private StackPane centerContentHost;
	@FXML private ScreenLoaderController centerLoaderIncludeController;

	public void loadCenterPage(String fxmlPath, String title, String sub) {

	    centerLoaderIncludeController.show(title, sub);

	    new Thread(() -> {
	        try {
	            Parent view = FXMLLoader.load(getClass().getResource(fxmlPath));

	            Platform.runLater(() -> {
	                centerContentHost.getChildren().setAll(view);
	                centerLoaderIncludeController.hide();
	            });

	        } catch (Exception e) {
	            e.printStackTrace();
	            Platform.runLater(() -> centerLoaderIncludeController.hide());
	        }
	    }).start();
	}

	
	

}
