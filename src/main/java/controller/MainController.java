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
import javafx.scene.layout.Region;
import javafx.scene.control.Button;
import javafx.event.Event;
import javafx.stage.Stage;
import javafx.scene.Scene;
import java.io.IOException;
import model.Job;
import service.JobService;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.util.Duration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import utils.DBConnection;
import model.DashboardJobDTO;

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
    
    @FXML private Label lblDashboardGreeting;

	// ✅ DASHBOARD FIELDS
	@FXML private ScrollPane dashboardView;
	@FXML private Label lblTotalRevenue;
	@FXML private Label lblOutstanding;
	@FXML private Label lblCollected;
	@FXML private Label lblOverdue;
	
    // Analysis Bars
    @FXML private javafx.scene.layout.Region cashFlow_M1_total; @FXML private javafx.scene.layout.Region cashFlow_M1_actual;
    @FXML private javafx.scene.layout.Region cashFlow_M2_total; @FXML private javafx.scene.layout.Region cashFlow_M2_actual;
    @FXML private javafx.scene.layout.Region cashFlow_M3_total; @FXML private javafx.scene.layout.Region cashFlow_M3_actual;
    @FXML private javafx.scene.layout.Region cashFlow_M4_total; @FXML private javafx.scene.layout.Region cashFlow_M4_actual;
    @FXML private javafx.scene.layout.Region cashFlow_M5_total; @FXML private javafx.scene.layout.Region cashFlow_M5_actual;
    @FXML private javafx.scene.layout.Region cashFlow_M6_total; @FXML private javafx.scene.layout.Region cashFlow_M6_actual;
    
    @FXML private javafx.scene.shape.Arc donut_ring_green;
    @FXML private javafx.scene.shape.Arc donut_ring_orange;
    @FXML private Label lblHealthyPercent;

	@FXML private AreaChart<String, Number> revenueChart;
	@FXML private PieChart jobDistributionChart;
	@FXML private Label lblActiveJobsCount;
	@FXML private TableView<DashboardJobDTO> recentJobsTable;
	@FXML private TableColumn<DashboardJobDTO, String> colOrderClient;
	@FXML private TableColumn<DashboardJobDTO, String> colProjectDetails;
	@FXML private TableColumn<DashboardJobDTO, String> colReceived;
	@FXML private TableColumn<DashboardJobDTO, String> colDueDate;
	@FXML private TableColumn<DashboardJobDTO, String> colValuation;
	@FXML private TableColumn<DashboardJobDTO, String> colWorkflow;

	Job currentJob = new Job();
	private int currentLoadTaskId = 0;

	private Integer pendingInvoicingClientId;
	private Integer pendingInvoicingJobId;

	private Object currentController; // Tracks the controller of the view currently in center

	public void loadInvoiceWithJob(int clientId, int jobId) {
		this.pendingInvoicingClientId = clientId;
		this.pendingInvoicingJobId = jobId;
		loadInvoiceGenration();
	}
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
	@FXML private VBox jobsSubmenu;
	@FXML private VBox clientsSubmenu;
	@FXML private VBox billingSubmenu;
	@FXML private VBox paymentSubmenu;
	@FXML private VBox ledgerSubmenu;
	@FXML private VBox settingsSubmenu;

    // Accordion Headers (Buttons)
    @FXML private Button jobsBtn;
    @FXML private Button clientsBtn;
    @FXML private Button billingBtn;
    @FXML private Button paymentBtn;
    @FXML private Button ledgerBtn;
    @FXML private Button settingsBtn;

    // Chevrons
    @FXML private Region jobsChevron;
    @FXML private Region clientsChevron;
    @FXML private Region billingChevron;
    @FXML private Region paymentChevron;
    @FXML private Region ledgerChevron;
    @FXML private Region settingsChevron;

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
		loadDashboardData();
		
		if (sidebarStack != null) {
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
		}

		// Show only the main sidebar initially
		if (mainSidebar != null) {
			showOnly(mainSidebar);
		}
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
			// fallback for centerScroll if only dashboardView is provided
            ScrollPane scroll = (centerScroll != null) ? centerScroll : dashboardView;

			if (scroll != null && centerRoot != null) {

				scroll.viewportBoundsProperty().addListener((obs, oldB, newB) -> {
					if (newB != null && newB.getHeight() > 0) {
						centerRoot.setMinHeight(newB.getHeight());
					}
				});

				// Initial set (in case viewport is already available)
				if (scroll.getViewportBounds() != null) {
					centerRoot.setMinHeight(scroll.getViewportBounds().getHeight());
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
					innerStack.prefWidthProperty().bind(sidebarStack.widthProperty());
					innerStack.minWidthProperty().bind(sidebarStack.widthProperty());
					innerStack.maxWidthProperty().bind(sidebarStack.widthProperty());
				}
				
				// Forcefully eliminate all phantom focus-scroll shifting in the sidebar
				sidebarScroll.hvalueProperty().addListener((obs, oldV, newV) -> {
				    if (newV != null && newV.doubleValue() != 0) {
				        sidebarScroll.setHvalue(0);
				    }
				});
				sidebarScroll.setHvalue(0);
			}

			if (userNameLabel != null && utils.SessionManager.getInstance().isLoggedIn()) {
				String user = utils.SessionManager.getInstance().getCurrentUser().getUsername();
                userNameLabel.setText(user);
                if (lblDashboardGreeting != null) lblDashboardGreeting.setText("Welcome, " + user);
			} else if (userNameLabel != null) {
				userNameLabel.setText("Guest");
                if (lblDashboardGreeting != null) lblDashboardGreeting.setText("Welcome");
			}
		});
	}

	private void applyCollapsedStyleToAll(boolean collapsed) {

		VBox[] sidebars = {
				mainSidebar,
				jobsSubmenu,
				clientsSubmenu,
				billingSubmenu,
				paymentSubmenu,
				ledgerSubmenu,
				settingsSubmenu
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
		if (dashboardView != null) {
			dashboardView.setVisible(true);
			dashboardView.setManaged(true);
			// Only update if we are not already showing the dashboard or to ensure it's in the hierarchy
			if (centerContentHost != null && !centerContentHost.getChildren().contains(dashboardView)) {
                // If dashboardView is already in a VBox (like centerRoot), we don't want to move it 
                // but for now, we follow the existing pattern if it's direct children.
                // However, in our new FXML, dashboardView is ALREADY inside centerRoot.
			}
			loadDashboardData();
			setPageTitle("Dashboard");
		}
	}

    private int activeChartRange = 6; // months

    @FXML
    private void handleChartRange1M() { activeChartRange = 1; loadChartData(); }
    @FXML
    private void handleChartRange3M() { activeChartRange = 3; loadChartData(); }
    @FXML
    private void handleChartRange6M() { activeChartRange = 6; loadChartData(); }

	private void loadDashboardData() {
		loadSummaryData();
		loadChartData();
		loadPieChartData();
		loadTableData();
	}

	private void loadSummaryData() {
		try (Connection con = DBConnection.getConnection()) {
			String sql = "SELECT SUM(amount), SUM(paid_amount), SUM(due_amount) FROM invoice_master WHERE is_void = 0";
			try (java.sql.Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
				if (rs.next()) {
					double total = rs.getDouble(1);
					double paid = rs.getDouble(2);
					double due = rs.getDouble(3);

					if (lblTotalRevenue != null) lblTotalRevenue.setText(String.format("₹%,.0f", total));
					if (lblCollected != null) lblCollected.setText(String.format("₹%,.0f", paid));
					if (lblOutstanding != null) lblOutstanding.setText(String.format("₹%,.0f", due));
					
                    // Update Donut
                    if (donut_ring_green != null && total > 0) {
                        double healthPercent = (paid / total) * 100;
                        if (lblHealthyPercent != null) lblHealthyPercent.setText(String.format("%.0f%%", healthPercent));
                        
                        double greenLength = -(healthPercent / 100) * 360;
                        donut_ring_green.setLength(greenLength);
                        
                        double orangePercent = (due / total) * 100;
                        donut_ring_orange.setLength((orangePercent / 100) * 360);
                    }

					String sqlOverdue = "SELECT SUM(due_amount) FROM invoice_master WHERE is_void = 0 AND due_amount > 0 AND date(invoice_date) < date('now', '-30 days')";
					try (java.sql.Statement st2 = con.createStatement(); ResultSet rs2 = st2.executeQuery(sqlOverdue)) {
						if (rs2.next()) {
							double overdue = rs2.getDouble(1);
							if (lblOverdue != null) lblOverdue.setText(String.format("₹%,.0f", overdue));
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void loadChartData() {
        javafx.scene.layout.Region[] totalBars = {cashFlow_M1_total, cashFlow_M2_total, cashFlow_M3_total, cashFlow_M4_total, cashFlow_M5_total, cashFlow_M6_total};
        javafx.scene.layout.Region[] actualBars = {cashFlow_M1_actual, cashFlow_M2_actual, cashFlow_M3_actual, cashFlow_M4_actual, cashFlow_M5_actual, cashFlow_M6_actual};

        // Reset
        for (javafx.scene.layout.Region r : totalBars) if(r!=null) r.setPrefHeight(0);
        for (javafx.scene.layout.Region r : actualBars) if(r!=null) r.setPrefHeight(0);

		try (Connection con = DBConnection.getConnection()) {
			String sql = "SELECT strftime('%m', invoice_date) as month, SUM(amount), SUM(paid_amount) " +
                         "FROM invoice_master WHERE is_void = 0 AND date(invoice_date) > date('now', '-" + activeChartRange + " months') " +
                         "GROUP BY month ORDER BY month DESC LIMIT 6";
			try (java.sql.Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                int i = 0;
                double maxVal = 1000; // Baseline
				while (rs.next() && i < 6) {
                    double total = rs.getDouble(2);
                    double actual = rs.getDouble(3);
                    if (total > maxVal) maxVal = total;
                    
                    double finalTotal = total;
                    double finalActual = actual;
                    int index = i;
                    double finalMax = maxVal;
                    
                    Platform.runLater(() -> {
                        double tH = (finalTotal / finalMax) * 160 + 20;
                        double aH = (finalActual / finalMax) * 160 + 10;
                        if (totalBars[index] != null) totalBars[index].setPrefHeight(tH);
                        if (actualBars[index] != null) actualBars[index].setPrefHeight(aH);
                    });
                    i++;
				}
                
                // If no data, show some mock for "Taste Design" visuals
                if (i == 0) {
                    double[] mockT = {140, 110, 155, 125, 180, 145};
                    double[] mockA = {105, 85, 120, 90, 145, 110};
                    for(int j=0; j<6; j++) {
                        int index = j;
                        Platform.runLater(() -> {
                            if (totalBars[index] != null) totalBars[index].setPrefHeight(mockT[index]);
                            if (actualBars[index] != null) actualBars[index].setPrefHeight(mockA[index]);
                        });
                    }
                }
			}
		} catch (Exception e) { e.printStackTrace(); }
	}

	private void loadPieChartData() {
		if (jobDistributionChart == null) return;
		try (Connection con = DBConnection.getConnection()) {
			String sql = "SELECT status, COUNT(*) FROM job WHERE status IS NOT NULL GROUP BY status";
			ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
            int total = 0;
			try (java.sql.Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
				while (rs.next()) {
                    int count = rs.getInt(2);
					pieData.add(new PieChart.Data(rs.getString(1), count));
                    total += count;
				}
			}
			jobDistributionChart.setData(pieData);
            if(lblActiveJobsCount != null) lblActiveJobsCount.setText(String.valueOf(total));
		} catch (Exception e) { e.printStackTrace(); }
	}

	private void loadTableData() {
		if (recentJobsTable == null) return;
		if (colOrderClient != null) colOrderClient.setCellValueFactory(new PropertyValueFactory<>("orderClient"));
		if (colProjectDetails != null) colProjectDetails.setCellValueFactory(new PropertyValueFactory<>("projectDetails"));
		if (colReceived != null) colReceived.setCellValueFactory(new PropertyValueFactory<>("receivedDate"));
		if (colDueDate != null) colDueDate.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
		if (colValuation != null) colValuation.setCellValueFactory(new PropertyValueFactory<>("valuation"));
		if (colWorkflow != null) colWorkflow.setCellValueFactory(new PropertyValueFactory<>("workflow"));

		ObservableList<DashboardJobDTO> jobs = FXCollections.observableArrayList();
		try (Connection con = DBConnection.getConnection()) {
			String sql = "SELECT j.job_name, c.client_name, j.order_date, j.delivery_date, j.total_amount, j.status " +
                         "FROM job j JOIN client_master c ON j.client_id = c.id ORDER BY j.id DESC LIMIT 5";
			try (java.sql.Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
				while (rs.next()) {
					jobs.add(new DashboardJobDTO(
						"#" + rs.getString(1) + " / " + rs.getString(2),
						rs.getString(1),
						rs.getString(3),
						rs.getString(4),
						String.format("₹%,.0f", rs.getDouble(5)),
						rs.getString(6)
					));
				}
			}
		} catch (Exception e) { e.printStackTrace(); }
		recentJobsTable.setItems(jobs);
	}

	private VBox currentSidebar = null;

	private void loadCenterScreen(String fxmlPath, String title, String subtitle) {
		loadCenterScreen(fxmlPath, title, subtitle, true);
	}

	private String lastValidTitle = "Dashboard";

	public boolean canDiscardChanges() {
		if (currentController instanceof utils.DirtySupport ds && ds.hasUnsavedChanges()) {
			javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
			alert.setTitle("Unsaved Changes");
			alert.setHeaderText("You have unsaved changes.");
			alert.setContentText("Do you want to leave this page and discard your changes?");
			alert.getButtonTypes().setAll(javafx.scene.control.ButtonType.YES, javafx.scene.control.ButtonType.NO);

			java.util.Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
			return result.isPresent() && result.get() == javafx.scene.control.ButtonType.YES;
		}
		return true;
	}

	private void loadCenterScreen(String fxmlPath, String loaderTitle, String loaderSubtitle, boolean pushToHistory) {
		final int taskId = ++currentLoadTaskId;
		System.out.println(
				"DEBUG: loadCenterScreen called with path: " + fxmlPath + " | taskId: " + taskId + " | pushToHistory: "
						+ pushToHistory);

		if (!canDiscardChanges()) {
			return; // Stop navigation
		}

		if (pushToHistory) {
			String sidebarId = (currentSidebar != null) ? currentSidebar.getId() : null;
			System.out.println(
					"DEBUG: Preparing to push to NavigationManager... Using lastValidTitle: " + lastValidTitle);
			utils.NavigationManager.getInstance().push(fxmlPath, lastValidTitle, loaderSubtitle, sidebarId);
		}

		if (dashboardView != null) {
			dashboardView.setVisible(false);
			dashboardView.setManaged(false);
		}

		if (pageTitle != null) {
            lastValidTitle = pageTitle.getText();
        }
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
				// 🔑 Controller will be stored once loaded
				this.currentController = controller;

				// 🔹 Inject root pane into Invoice screen
				if (controller instanceof InvoiceGenerationController c) {
					c.setRootPane(appRoot); // 👈 global overlay access
				}

				Platform.runLater(() -> {
					if (taskId != currentLoadTaskId) {
						System.out.println("DEBUG: Ignoring superseded load for taskId: " + taskId);
						return;
					}

					// 🔒 Store the fully compiled Parent view and its controller into the NavigationManager history
					// map!
					utils.NavigationManager.getInstance().updateCurrentState(view, controller);

					// 2️⃣ Replace center UI
					centerContentHost.getChildren().setAll(view);

					if (controller instanceof AddJobController addJobController) {
						addJobController.startNewJob();
					}

					if (controller instanceof InvoiceGenerationController invController) {
						if (pendingInvoicingClientId != null && pendingInvoicingJobId != null) {
							invController.preSelectJob(pendingInvoicingClientId, pendingInvoicingJobId);
							pendingInvoicingClientId = null;
							pendingInvoicingJobId = null;
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
		collapseAllSubmenus();
		applyCollapsedStyleToAll(true);
        sidebarStack.getStyleClass().add("sidebar-collapsed-bg");
		sidebarStack.getStyleClass().remove("sidebar-expanded-bg");

		animateSidebarWidth(COLLAPSED_WIDTH);
	}

	private void animateSidebarWidth(double width) {
		if (anim != null)
			anim.stop();

		anim = new Timeline(
				new KeyFrame(Duration.millis(150),
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

    private void collapseAllSubmenus() {
        VBox[] submenus = {jobsSubmenu, clientsSubmenu, billingSubmenu, paymentSubmenu, ledgerSubmenu, settingsSubmenu};
        Region[] chevrons = {jobsChevron, clientsChevron, billingChevron, paymentChevron, ledgerChevron, settingsChevron};
        Button[] buttons = {jobsBtn, clientsBtn, billingBtn, paymentBtn, ledgerBtn, settingsBtn};
        
        for(int i=0; i<submenus.length; i++) {
            if(submenus[i] != null) {
                submenus[i].setVisible(false);
                submenus[i].setManaged(false);
                if(chevrons[i] != null) chevrons[i].setRotate(0);
                if(buttons[i] != null) buttons[i].getStyleClass().remove("sidebar-btn-expanded");
            }
        }
    }

	private void showOnly(VBox target) {
		if (target == null) return;
        // mainSidebar is always visible in the accordion model
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
	private void openDashboard(javafx.event.ActionEvent event) {
		utils.NavigationManager.getInstance().clear();
		System.out.println("DEBUG: Navigation History cleared at Dashboard root.");
		collapseAllSubmenus();
		openCenterDashboard();
		setPageTitle("Dashboard");
	}

    private void toggleSubmenu(VBox submenu, Region chevron, Button parentBtn) {
        boolean wasVisible = submenu.isVisible();
        collapseAllSubmenus();

        // Toggle target
        if (!wasVisible) {
            submenu.setVisible(true);
            submenu.setManaged(true);
            if(chevron != null) chevron.setRotate(180);
            if(parentBtn != null) parentBtn.getStyleClass().add("sidebar-btn-expanded");
        }
    }

	@FXML public void showJobsSubmenu() { toggleSubmenu(jobsSubmenu, jobsChevron, jobsBtn); }
	@FXML public void showClientsSubmenu() { toggleSubmenu(clientsSubmenu, clientsChevron, clientsBtn); }
	@FXML public void showBillingSubmenu() { toggleSubmenu(billingSubmenu, billingChevron, billingBtn); }
	@FXML public void showPaymentSubmenu() { toggleSubmenu(paymentSubmenu, paymentChevron, paymentBtn); }
	@FXML public void showLedgerSubmenu() { toggleSubmenu(ledgerSubmenu, ledgerChevron, ledgerBtn); }
	@FXML public void showSettingSubmenu() { toggleSubmenu(settingsSubmenu, settingsChevron, settingsBtn); }

	private VBox findSidebarById(String id) {
		return mainSidebar; // Dashboard is now a single-sidebar accordion
	}

	@FXML
	private void showMainSidebar(MouseEvent event) {
		openDashboard(null);
	}

	@FXML
	public void handleBack(MouseEvent event) {
		if (utils.NavigationManager.getInstance().hasHistory()) {

			if (!canDiscardChanges()) {
				return;
			}

			currentLoadTaskId++; // Cancel any pending background loads
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
					this.currentController = prevState.getController(); // ⚡ RESTORE CONTROLLER
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
			openDashboard(null);
		}
	}

	@FXML
	public void loadClientLedger() {
		loadCenterScreen("/fxml/client_ledger.fxml",
				"Loading Client Ledger...",
				"Fetching financial records...");
	}

	@FXML
	private void loadAddClient() {
		loadCenterScreen("/fxml/add_client.fxml",
				"Loading Dashboard...",
				"Please wait");
	}

	@FXML
	private void loadAddJob() {
		loadCenterScreen("/fxml/ht.fxml",
				"Loading Dashboard...",
				"Please wait");
	}

	@FXML
	public void loadViewJob() {
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
	public void loadViewInvoiceJobs() {
		loadCenterScreen("/fxml/view_invoice_jobs.fxml",
				"Loading Invoice Jobs...",
				"Please wait");
	}

	@FXML
	public void loadViewBills() {
		switchToInvoices();
	}

	public void switchToInvoices() {
		loadCenterScreen("/fxml/view_invoices.fxml",
				"Loading Invoices...",
				"Fetching billing records...");
	}

	@FXML
	public void loadCreditDebitNote() {
		loadCenterScreen("/fxml/credit_debit_note.fxml",
				"Loading Note...",
				"Please wait");
	}

	@FXML
	public void loadRecordPayment() {
		loadCenterScreen("/fxml/record_payment.fxml",
				"Loading Payment Screen...",
				"Loading outstanding invoices and client details...");
	}

	@FXML
	public void loadPaymentHistory() {
		loadCenterScreen("/fxml/payment_history.fxml",
				"Loading Payment History...",
				"Fetching payment records...");
	}

	@FXML
	public void loadGeneralSettings() {
		loadCenterScreen("/fxml/general_settings.fxml",
				"Loading General Settings...",
				"Fetching global configurations...");
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
	public void loadUserSettings() {
		loadCenterScreen("/fxml/user_settings.fxml",
				"Loading User Settings...",
				"Please wait");
	}

	@FXML
	private void loadInvoiceSettings() {
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
