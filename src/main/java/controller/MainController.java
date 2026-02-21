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
import javafx.scene.Scene;
import java.io.IOException;
import model.Job;
import service.JobService;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.util.Duration;

public class MainController implements Initializable {

	private final double COLLAPSED_WIDTH = 74;
	private final double EXPANDED_WIDTH = 240;
	@FXML
	private StackPane appRoot;
	@FXML
	private StackPane centerWrapper;

	@FXML
	private StackPane sidebarStack;
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

	@FXML
	private VBox globalLoader;
	@FXML
	private Label loaderTitle;
	@FXML
	private Label loaderSubtitle;
	@FXML
	private ProgressBar loaderBar;

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

	@FXML
	private VBox settingsSidebar;

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
		clip.widthProperty().bind(sidebarStack.widthProperty());
		clip.heightProperty().bind(sidebarStack.heightProperty());

		sidebarStack.setClip(clip);

		// Collapsable Sidebar Code
		// ✅ Start in collapsed state
		sidebarStack.setPrefWidth(COLLAPSED_WIDTH);
		applyCollapsedStyleToAll(true);
		sidebarStack.getStyleClass().add("sidebar-collapsed-bg");
		sidebarStack.getStyleClass().remove("sidebar-expanded-bg");
		// ✅ Hover expand
		sidebarStack.setOnMouseEntered(e -> expandSidebar());

		// ✅ Mouse exit collapse
		sidebarStack.setOnMouseExited(e -> collapseSidebar());

		// Collapsable sidebar Code END

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

			// Initialize User Profile

			// ---------------------------------------------------
			// 3. Absolute Layout Width Lock for Sidebars
			// ---------------------------------------------------
			if (sidebarScroll != null && sidebarStack != null) {
				if (sidebarScroll.getContent() instanceof StackPane innerStack) {
					// sidebarStack has 8px left/right padding = 16px total.
					// Locking the innerStack prefWidth to exact layout dimensions,
					// stripping JavaFX's scrollbar viewport subtraction logic entirely.
					innerStack.prefWidthProperty().bind(sidebarStack.widthProperty().subtract(16));
				}
			}

			if (userNameLabel != null && utils.SessionManager.getInstance().isLoggedIn()) {
				userNameLabel.setText(utils.SessionManager.getInstance().getCurrentUser().getUsername());
			} else if (userNameLabel != null) {
				userNameLabel.setText("Guest");
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
				ledgerSidebar,
				settingsSidebar
		};

		for (VBox s : sidebars) {
			if (s == null)
				continue;

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

	private VBox currentSidebar = null;

	private void loadCenterScreen(String fxmlPath, String title, String subtitle) {
		loadCenterScreen(fxmlPath, title, subtitle, true);
	}

	private String lastValidTitle = "Dashboard";

	private void loadCenterScreen(String fxmlPath, String loaderTitle, String loaderSubtitle, boolean pushToHistory) {
		System.out.println(
				"DEBUG: loadCenterScreen called with path: " + fxmlPath + " | pushToHistory: " + pushToHistory);
		if (pushToHistory) {
			String sidebarId = (currentSidebar != null) ? currentSidebar.getId() : null;
			System.out.println(
					"DEBUG: Preparing to push to NavigationManager... Using lastValidTitle: " + lastValidTitle);
			utils.NavigationManager.getInstance().push(fxmlPath, lastValidTitle, loaderSubtitle, sidebarId);
		}

		lastValidTitle = pageTitle.getText();
		System.out
				.println("DEBUG: loadCenterScreen finished pushing/setting. lastValidTitle is now: " + lastValidTitle);

		// 1️⃣ Show loader
		if (centerLoaderIncludeController != null) {
			centerLoaderIncludeController.show(loaderTitle, loaderSubtitle);
		}

		new Thread(() -> {
			try {
				FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
				Parent view = loader.load();
				Object controller = loader.getController();

				Job draftJob = null;

				// 🔑 Only Add Job screen needs draft logic
				if (controller instanceof AddJobController) {
					JobService jobService = new JobService();
					draftJob = jobService.getLatestDraftJob();
				}
				// 🔹 Inject root pane into Invoice screen
				if (controller instanceof InvoiceGenerationController c) {
					c.setRootPane(appRoot); // 👈 global overlay access
				}

				Job finalDraftJob = draftJob;

				Platform.runLater(() -> {

					// 🔒 Store the fully compiled Parent view into the NavigationManager history
					// map!
					utils.NavigationManager.getInstance().updateCurrentView(view);

					// 2️⃣ Replace center UI
					centerContentHost.getChildren().setAll(view);

					// 3️⃣ Resume or create job
					if (controller instanceof AddJobController addJobController) {

						if (finalDraftJob != null) {
							// 🔁 Resume unfinished draft
							addJobController.openForEdit(finalDraftJob.getId());
						} else {
							// 🆕 Create new draft
							addJobController.startNewJob();
						}
					}

					// 4️⃣ Hide loader
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

		if (anim != null)
			anim.setOnFinished(e -> {

				applyCollapsedStyleToAll(true);

				sidebarStack.getStyleClass().add("sidebar-collapsed-bg");
				sidebarStack.getStyleClass().remove("sidebar-expanded-bg");
			});
	}

	private void animateSidebarWidth(double width) {
		if (anim != null)
			anim.stop();

		anim = new Timeline(
				new KeyFrame(Duration.millis(180),
						new KeyValue(sidebarStack.prefWidthProperty(), width),
						new KeyValue(sidebarStack.minWidthProperty(), width),
						new KeyValue(sidebarStack.maxWidthProperty(), width)));
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
		hideSidebar(settingsSidebar);
	}

	private void showOnly(VBox target) {
		hideAllSidebars();
		currentSidebar = target;
		if (target != null) {
			target.setVisible(true);
			target.setManaged(true);
		}

		else
			System.out.println("Null SideBAR");
	}

	private void setPageTitle(String title) {
		System.out.println("DEBUG: setPageTitle called with: " + title);
		if (pageTitle != null) {
			pageTitle.setText(title);
			lastValidTitle = title;
			System.out.println("DEBUG: pageTitle set to: " + title + " | lastValidTitle is now: " + lastValidTitle);
		}
	}

	// Event handlers for sidebar menu items

	@FXML
	private void openDashboard(MouseEvent event) {
		utils.NavigationManager.getInstance().clear();
		System.out.println("DEBUG: Navigation History cleared at Dashboard root.");
		showOnly(mainSidebar);
		openCenterDashboard();
		setPageTitle("Dashboard");
	}

	@FXML
	public void showJobsSubmenu(MouseEvent event) {
		showOnly(jobsSidebar);
		setPageTitle("Job Details");
	}

	@FXML
	public void showClientsSubmenu(MouseEvent event) {
		showOnly(clientsSidebar);
		setPageTitle("Client Details");
	}

	@FXML
	public void showBillingSubmenu(MouseEvent event) {
		showOnly(billingSidebar);
		setPageTitle("Billing");
	}

	@FXML
	public void showPaymentSubmenu(MouseEvent event) {
		showOnly(paymentSidebar);
		setPageTitle("Payments");
	}

	@FXML
	public void showLedgerSubmenu(MouseEvent event) {
		showOnly(ledgerSidebar);
		setPageTitle("Ledger");
	}

	private VBox findSidebarById(String id) {
		if (id == null)
			return mainSidebar;
		switch (id) {
			case "jobsSidebar":
				return jobsSidebar;
			case "clientsSidebar":
				return clientsSidebar;
			case "billingSidebar":
				return billingSidebar;
			case "paymentSidebar":
				return paymentSidebar;
			case "ledgerSidebar":
				return ledgerSidebar;
			case "settingsSidebar":
				return settingsSidebar;
			default:
				return mainSidebar;
		}
	}

	@FXML
	private void showMainSidebar(MouseEvent event) {
		openDashboard(event);
	}

	@FXML
	public void handleBack(MouseEvent event) {
		if (utils.NavigationManager.getInstance().hasHistory()) {
			utils.NavigationManager.NavState prevState = utils.NavigationManager.getInstance().pop();
			if (prevState != null) {
				VBox restoredSidebar = findSidebarById(prevState.getActiveSidebarId());
				showOnly(restoredSidebar);
				lastValidTitle = prevState.getTitle();
				setPageTitle(prevState.getTitle());

				// ⚡ If the history stack preserved the actual screen memory, restore it
				// instantly:
				if (prevState.getView() != null) {
					System.out.println("DEBUG: Restoring CACHED view from history: " + prevState.getFxmlPath());
					centerContentHost.getChildren().setAll(prevState.getView());
				} else {
					// 🐢 Fallback: Only reload FXML if the memory reference was somehow lost
					System.out.println("DEBUG: FALLBACK - Re-parsing FXML from disk: " + prevState.getFxmlPath());
					loadCenterScreen(prevState.getFxmlPath(), "Loading...", "Please wait", false);
				}
			}
		} else {
			// If no history exists, back button does nothing
			// (or falls back to dashboard)
			openDashboard(event);
		}
	}

	@FXML
	public void loadClientLedger(MouseEvent event) {
		loadCenterScreen("/fxml/client_ledger.fxml",
				"Loading Client Ledger...",
				"Fetching financial records...");
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
	public void loadViewJob(MouseEvent event) {
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

	@FXML
	public void loadRecordPayment(MouseEvent event) {
		loadCenterScreen("/fxml/record_payment.fxml",
				"Loading Payment Screen...",
				"Loading outstanding invoices and client details...");
	}

	// try {
	// Parent view =
	// FXMLLoader.load(getClass().getResource("/fxml/invoice_genration.fxml"));
	// centerRoot.getChildren().setAll(view);
	// } catch (Exception e) {
	// e.printStackTrace();
	// }
	// }

	@FXML
	private StackPane centerLoaderInclude;

	public void showCenterLoader() {
		centerLoaderInclude.setVisible(true);
		centerLoaderInclude.setManaged(true);
	}

	public void hideCenterLoader() {
		centerLoaderInclude.setVisible(false);
		centerLoaderInclude.setManaged(false);
	}

	@FXML
	public void loadUserSettings(MouseEvent event) {
		loadCenterScreen("/fxml/user_settings.fxml",
				"Loading User Settings...",
				"Please wait");
	}

	@FXML
	private void loadInvoiceSettings(MouseEvent event) {
		loadCenterScreen("/fxml/invoice_settings.fxml",
				"Loading Invoice Settings...",
				"Please wait");
	}

	@FXML
	private StackPane centerContentHost;
	@FXML
	private ScreenLoaderController centerLoaderIncludeController;

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

	@FXML
	public void showSettingSubmenu(MouseEvent event) {

		showOnly(settingsSidebar);
		setPageTitle("Genral Settings");
	}

	// User Profile Logic
	@FXML
	private javafx.scene.control.Label userNameLabel;

	@FXML
	private void handleSignOut(javafx.event.ActionEvent event) {
		utils.SessionManager.getInstance().logout();
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
			Parent loginRoot = loader.load();
			Stage stage = (Stage) root.getScene().getWindow();
			stage.setScene(new Scene(loginRoot));
			stage.centerOnScreen();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@FXML
	private void handleProfileSettings(javafx.event.ActionEvent event) {
		loadCenterScreen("/fxml/profile_settings.fxml",
				"Loading Profile...",
				"Please wait");
	}
}
