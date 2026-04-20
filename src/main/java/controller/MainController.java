package controller;

import javafx.scene.shape.Rectangle;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
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
import javafx.scene.layout.HBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.scene.Scene;
import java.io.IOException;
import model.Job;
import javafx.util.Duration;
import java.sql.Connection;
import java.sql.ResultSet;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.PieChart;
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

	@FXML
	private Label lblDashboardGreeting;

	// ✅ DASHBOARD FIELDS
	@FXML
	private ScrollPane dashboardView;
	@FXML
	private Label lblTotalRevenue;
	@FXML
	private Label lblOutstanding;
	@FXML
	private Label lblCollected;
	@FXML
	private Label lblOverdue;

	// Analysis Bars
	@FXML
	private javafx.scene.layout.Region cashFlow_M1_total;
	@FXML
	private javafx.scene.layout.Region cashFlow_M1_actual;
	@FXML
	private javafx.scene.layout.Region cashFlow_M2_total;
	@FXML
	private javafx.scene.layout.Region cashFlow_M2_actual;
	@FXML
	private javafx.scene.layout.Region cashFlow_M3_total;
	@FXML
	private javafx.scene.layout.Region cashFlow_M3_actual;
	@FXML
	private javafx.scene.layout.Region cashFlow_M4_total;
	@FXML
	private javafx.scene.layout.Region cashFlow_M4_actual;
	@FXML
	private javafx.scene.layout.Region cashFlow_M5_total;
	@FXML
	private javafx.scene.layout.Region cashFlow_M5_actual;
	@FXML
	private javafx.scene.layout.Region cashFlow_M6_total;
	@FXML
	private javafx.scene.layout.Region cashFlow_M6_actual;

	@FXML
	private Button btnChart1M, btnChart3M, btnChart6M;
	@FXML
	private Label lblCashFlow_M1, lblCashFlow_M2, lblCashFlow_M3, lblCashFlow_M4, lblCashFlow_M5, lblCashFlow_M6;

	@FXML
	private javafx.scene.control.ComboBox<String> comboTimeRange;
	private String selectedTimeRange = "All Time";

	@FXML
	private javafx.scene.shape.Arc donut_ring_green;
	@FXML
	private javafx.scene.shape.Arc donut_ring_orange;
	@FXML
	private Label lblHealthyPercent;
	@FXML
	private Label lblDonutPaid, lblDonutDue, lblDonutEfficiency, lblDonutOverdue, lblDonutTotal, lblDonutInsight,
			lblCollectionPeriod;

	@FXML
	private AreaChart<String, Number> revenueChart;
	@FXML
	private PieChart jobDistributionChart;
	@FXML
	private Label lblActiveJobsCount;
	@FXML
	private TableView<DashboardJobDTO> recentJobsTable;
	@FXML
	private TableColumn<DashboardJobDTO, String> colOrderClient;
	@FXML
	private TableColumn<DashboardJobDTO, String> colProjectDetails;
	@FXML
	private TableColumn<DashboardJobDTO, String> colReceived;
	@FXML
	private TableColumn<DashboardJobDTO, String> colDueDate;
	@FXML
	private TableColumn<DashboardJobDTO, String> colValuation;
	@FXML
	private TableColumn<DashboardJobDTO, String> colWorkflow;

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
		centerContentHost.getChildren().setAll(view);
	}

	@FXML
	private VBox globalLoader;
	@FXML
	private Label loaderTitle;
	@FXML
	private Label loaderSubtitle;
	@FXML
	private ProgressBar loaderBar;

	private Button activeParent;

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
	private VBox jobsSubmenu;
	@FXML
	private VBox clientsSubmenu;
	@FXML
	private VBox billingSubmenu;
	@FXML
	private VBox paymentSubmenu;
	@FXML
	private VBox ledgerSubmenu;
	@FXML
	private VBox settingsSubmenu;

	// Accordion Headers (Buttons)
	@FXML
	private Button jobsBtn;
	@FXML
	private Button clientsBtn;
	@FXML
	private Button billingBtn;
	@FXML
	private Button paymentBtn;
	@FXML
	private Button ledgerBtn;
	@FXML
	private Button settingsBtn;
	@FXML
	private Button dashboardBtn;

	// Submenu Buttons
	@FXML
	private Button viewClientsSubBtn;
	@FXML
	private Button editClientSubBtn;
	@FXML
	private Button addClientSubBtn;
	@FXML
	private Button addJobSubBtn;
	@FXML
	private Button genInvoiceSubBtn;
	@FXML
	private Button recPaymentSubBtn;
	@FXML
	private Button clientLedgerSubBtn;
	@FXML
	private Button genSettingsSubBtn;

	// Chevrons
	@FXML
	private Region jobsChevron;
	@FXML
	private Region clientsChevron;
	@FXML
	private Region billingChevron;
	@FXML
	private Region paymentChevron;
	@FXML
	private Region ledgerChevron;
	@FXML
	private Region settingsChevron;

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

	// Top Search
	@FXML
	private HBox mainSearchBox;
	@FXML
	private TextField mainSearchField;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		setPageTitle("Dashboard");
		openCenterDashboard();
		// Set initial state so first push creates history
		utils.NavigationManager.getInstance().push(null, "Dashboard", "Overview", null);

		// Initialize Filter Dropdown
		if (comboTimeRange != null) {
			comboTimeRange.getItems().setAll("All Time", "Last 7 Days", "Last 30 Days", "Last 3 Months");
			comboTimeRange.setValue("All Time");
			comboTimeRange.setOnAction(e -> {
				selectedTimeRange = comboTimeRange.getValue();
				loadDashboardData();
			});
		}

		loadDashboardData();

		if (mainSearchBox != null && mainSearchField != null) {
			mainSearchField.focusedProperty().addListener((obs, oldVal, focused) -> {
				javafx.animation.Timeline timeline = new javafx.animation.Timeline();
				javafx.animation.KeyValue kv;
				if (focused) {
					kv = new javafx.animation.KeyValue(mainSearchBox.prefWidthProperty(), 460,
							javafx.animation.Interpolator.EASE_OUT);
				} else {
					kv = new javafx.animation.KeyValue(mainSearchBox.prefWidthProperty(), 380,
							javafx.animation.Interpolator.EASE_OUT);
				}
				javafx.animation.KeyFrame kf = new javafx.animation.KeyFrame(javafx.util.Duration.millis(200), kv);
				timeline.getKeyFrames().add(kf);
				timeline.play();
			});
		}

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

			// Force layout update during animation to ensure center content shifts smoothly
			sidebarStack.widthProperty().addListener((obs, oldV, newV) -> {
				if (root != null) {
					root.requestLayout();
				}
			});
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
				javafx.scene.Node content = sidebarScroll.getContent();
				if (content instanceof javafx.scene.layout.Region inner) {
					inner.prefWidthProperty().bind(sidebarStack.widthProperty());
					inner.minWidthProperty().bind(sidebarStack.widthProperty());
					inner.maxWidthProperty().bind(sidebarStack.widthProperty());
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
				if (lblDashboardGreeting != null)
					lblDashboardGreeting.setText("Welcome, " + user);
			} else if (userNameLabel != null) {
				userNameLabel.setText("Guest");
				if (lblDashboardGreeting != null)
					lblDashboardGreeting.setText("Welcome");
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

			// Show search bar only on dashboard
			if (mainSearchBox != null) {
				mainSearchBox.setVisible(true);
				mainSearchBox.setManaged(true);
			}

			// Hide breadcrumb on dashboard
			if (pageTitle != null) {
				pageTitle.setVisible(false);
				pageTitle.setManaged(false);
			}

			// Only update if we are not already showing the dashboard or to ensure it's in
			// the hierarchy
			if (centerContentHost != null && !centerContentHost.getChildren().contains(dashboardView)) {
				centerContentHost.getChildren().setAll(dashboardView);
			}

			loadDashboardData();
			setPageTitle("");
			// Strip focus from any previously focused elements in the header
			if (appRoot != null) {
				appRoot.requestFocus();
			}
		}
	}

	private int activeChartRange = 6; // months

	@FXML
	private void handleChartRange1M() {
		activeChartRange = 1;
		updateChartButtonsUI();
		loadChartData();
	}

	@FXML
	private void handleChartRange3M() {
		activeChartRange = 3;
		updateChartButtonsUI();
		loadChartData();
	}

	@FXML
	private void handleChartRange6M() {
		activeChartRange = 6;
		updateChartButtonsUI();
		loadChartData();
	}

	private void updateChartButtonsUI() {
		if (btnChart1M == null || btnChart3M == null || btnChart6M == null)
			return;

		String activeStyle = "-fx-background-color: white; -fx-background-radius: 100; -fx-font-size: 11; -fx-font-weight: 800; -fx-padding: 6 16; -fx-text-fill: #D47B5A; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.02), 5, 0, 0, 2);";
		String inactiveStyle = "-fx-background-color: transparent; -fx-font-size: 11; -fx-font-weight: 800; -fx-padding: 6 16; -fx-text-fill: #737B69; -fx-effect: none;";

		btnChart1M.setStyle(activeChartRange == 1 ? activeStyle : inactiveStyle);
		btnChart3M.setStyle(activeChartRange == 3 ? activeStyle : inactiveStyle);
		btnChart6M.setStyle(activeChartRange == 6 ? activeStyle : inactiveStyle);
	}

	private String getTimeFilterSQL() {
		switch (selectedTimeRange) {
			case "Last 7 Days":
				return " AND date(invoice_date) >= date('now', '-7 days')";
			case "Last 30 Days":
				return " AND date(invoice_date) >= date('now', '-30 days')";
			case "Last 3 Months":
				return " AND date(invoice_date) >= date('now', '-90 days')";
			default:
				return ""; // All Time
		}
	}

	private void loadDashboardData() {
		loadSummaryData();
		loadChartData();
		loadPieChartData();
		loadTableData();
	}

	private void loadSummaryData() {
		try (Connection con = DBConnection.getConnection()) {
			String filter = getTimeFilterSQL();
			double totalBilled = 0, totalPaid = 0, totalDue = 0;

			if (lblCollectionPeriod != null)
				lblCollectionPeriod.setText("COLLECTED (" + selectedTimeRange.toUpperCase() + ")");

			// 1. DYNAMIC TOTALS based on Range (Revenue, Collected, Outstanding)
			String sql = "SELECT SUM(amount), SUM(paid_amount), SUM(due_amount) FROM invoice_master WHERE is_void = 0"
					+ filter;
			try (java.sql.Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
				if (rs.next()) {
					totalBilled = rs.getDouble(1);
					totalPaid = rs.getDouble(2);
					totalDue = rs.getDouble(3);
				}
			}

			// Adjust for Credit and Debit Notes
			double totalDN = 0, totalCN = 0;
			String sqlAdj = "SELECT type, SUM(amount) FROM invoice_adjustments WHERE 1=1 " +
					filter.replace("invoice_date", "date") + " GROUP BY type";
			try (java.sql.Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sqlAdj)) {
				while (rs.next()) {
					String type = rs.getString(1);
					double amt = rs.getDouble(2);
					if ("Debit Note".equalsIgnoreCase(type))
						totalDN = amt;
					else if ("Credit Note".equalsIgnoreCase(type))
						totalCN = amt;
				}
			}
			totalBilled = totalBilled + totalDN - totalCN;
			// Note: totalDue also needs adjustment if CN/DN affect balance
			totalDue = totalDue + totalDN - totalCN;

			if (lblTotalRevenue != null) {
				lblTotalRevenue.setText(String.format("₹%,.0f", totalBilled));
			}
			if (lblCollected != null)
				lblCollected.setText(String.format("₹%,.0f", totalPaid));
			if (lblOutstanding != null)
				lblOutstanding.setText(String.format("₹%,.0f", totalDue));
			if (lblDonutTotal != null)
				lblDonutTotal.setText(String.format("₹%,.0f", totalBilled));
			if (lblOverdue != null)
				lblOverdue.setText(String.format("₹%,.0f", totalDue - totalPaid)); // Default logic for "Overdue" kpi
																					// card often means dynamic
																					// outstanding

			// 2. Collection Ratio / Health Score (Within Range)
			double collectionRatio = totalBilled > 0 ? (totalPaid / totalBilled) : 0;
			if (lblHealthyPercent != null)
				lblHealthyPercent.setText(String.format("%.0f%%", collectionRatio * 100));

			// 3. Overdue (Filtered by Range + 30 day aging)
			double overdueAmt = 0;
			String sqlOverdue = "SELECT SUM(due_amount) FROM invoice_master WHERE is_void = 0 AND due_amount > 0 AND date(invoice_date) < date('now', '-30 days')"
					+ filter;
			try (java.sql.Statement stO = con.createStatement(); ResultSet rsO = stO.executeQuery(sqlOverdue)) {
				if (rsO.next())
					overdueAmt = rsO.getDouble(1);
			}
			if (lblDonutOverdue != null) {
				lblDonutOverdue.setText(String.format("₹%,.0f", overdueAmt));
				lblDonutOverdue.setStyle("-fx-text-fill: #D27357; -fx-font-weight: 900;");
			}
			if (lblOverdue != null) {
				lblOverdue.setText(String.format("₹%,.0f", overdueAmt));
			}
			if (lblDonutPaid != null)
				lblDonutPaid.setText(String.format("₹%,.0f", totalPaid));
			if (lblDonutDue != null)
				lblDonutDue.setText(String.format("₹%,.0f", totalDue));

			// 4. Efficiency Indicator (Tied to Selection)
			if (lblDonutEfficiency != null) {
				double effPct = collectionRatio * 100;
				lblDonutEfficiency.setText(String.format("%.0f%%", effPct));
				if (effPct < 50)
					lblDonutEfficiency.setStyle("-fx-text-fill: #D27357;");
				else if (effPct < 80)
					lblDonutEfficiency.setStyle("-fx-text-fill: #D97706;");
				else
					lblDonutEfficiency.setStyle("-fx-text-fill: #68765A;");
			}

			// 5. Insight Component
			if (lblDonutInsight != null) {
				if (totalDue > 0) {
					double insightPct = (overdueAmt / totalDue) * 100;
					lblDonutInsight.setText(String.format("⚠️ %.0f%% of range AR is overdue", insightPct));
				} else if (totalBilled > 0) {
					lblDonutInsight.setText("✅ All range payments are clear");
				} else {
					lblDonutInsight.setText("No data for selected period");
				}
			}

			// 6. Donut Arc control
			if (donut_ring_green != null) {
				double greenLength = -(collectionRatio * 360);
				donut_ring_green.setLength(greenLength);
				double orangePercent = totalBilled > 0 ? (totalDue / totalBilled) : 0;
				donut_ring_orange.setLength(orangePercent * 360);
			}

			if (lblOverdue != null)
				lblOverdue.setText(String.format("₹%,.0f", overdueAmt));

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void loadChartData() {
		javafx.scene.layout.Region[] totalBars = { cashFlow_M1_total, cashFlow_M2_total, cashFlow_M3_total,
				cashFlow_M4_total, cashFlow_M5_total, cashFlow_M6_total };
		javafx.scene.layout.Region[] actualBars = { cashFlow_M1_actual, cashFlow_M2_actual, cashFlow_M3_actual,
				cashFlow_M4_actual, cashFlow_M5_actual, cashFlow_M6_actual };
		Label[] mLabels = { lblCashFlow_M1, lblCashFlow_M2, lblCashFlow_M3, lblCashFlow_M4, lblCashFlow_M5,
				lblCashFlow_M6 };
		String[] monthNames = { "", "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV",
				"DEC" };

		// Reset
		for (javafx.scene.layout.Region r : totalBars)
			if (r != null)
				r.setPrefHeight(0);
		for (javafx.scene.layout.Region r : actualBars)
			if (r != null)
				r.setPrefHeight(0);
		for (Label l : mLabels)
			if (l != null)
				l.setText("");

		try (Connection con = DBConnection.getConnection()) {
			String globalFilter = getTimeFilterSQL();
			String sql = "SELECT strftime('%m', invoice_date) as month, SUM(amount), SUM(paid_amount) " +
					"FROM invoice_master WHERE is_void = 0 " + globalFilter +
					" AND date(invoice_date) >= date('now', 'start of month', '-" + (activeChartRange - 1)
					+ " months') " +
					"GROUP BY month ORDER BY month DESC LIMIT 6";
			try (java.sql.Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
				int i = 0;
				double maxVal = 1000; // Baseline
				while (rs.next() && i < 6) {
					int dbMonth = Integer.parseInt(rs.getString(1));
					String mStr = monthNames[dbMonth];
					double total = rs.getDouble(2);
					double actual = rs.getDouble(3);
					if (total > maxVal)
						maxVal = total;

					double finalTotal = total;
					double finalActual = actual;
					int index = i;
					double finalMax = maxVal;

					Platform.runLater(() -> {
						double tH = (finalTotal / finalMax) * 160 + 20;
						double aH = (finalActual / finalMax) * 160 + 10;
						if (mLabels[index] != null)
							mLabels[index].setText(mStr);

						if (totalBars[index] != null) {
							totalBars[index].setPrefHeight(tH);
							javafx.scene.control.Tooltip ttTotal = new javafx.scene.control.Tooltip(
									String.format("TOTAL AMOUNT: ₹%,.0f", finalTotal));
							ttTotal.setShowDelay(javafx.util.Duration.millis(50));
							javafx.scene.control.Tooltip.install(totalBars[index], ttTotal);
						}
						if (actualBars[index] != null) {
							actualBars[index].setPrefHeight(aH);
							javafx.scene.control.Tooltip ttAct = new javafx.scene.control.Tooltip(
									String.format("NET COLLECTION: ₹%,.0f", finalActual));
							ttAct.setShowDelay(javafx.util.Duration.millis(50));
							javafx.scene.control.Tooltip.install(actualBars[index], ttAct);
						}
					});
					i++;
				}

				// If no data, show precise mock for "Taste Design" visuals
				if (i == 0) {
					double[] mockT = { 120, 100, 150, 115, 180, 130 };
					double[] mockA = { 85, 70, 80, 90, 135, 85 };
					int currMonth = java.time.LocalDate.now().getMonthValue();

					for (int j = 0; j < activeChartRange; j++) {
						int index = j;
						int m = currMonth - j;
						if (m <= 0)
							m += 12;
						String mStr = monthNames[m];

						Platform.runLater(() -> {
							if (mLabels[index] != null)
								mLabels[index].setText(mStr);

							if (totalBars[index] != null) {
								totalBars[index].setPrefHeight(mockT[index]);
								javafx.scene.control.Tooltip ttTotalMock = new javafx.scene.control.Tooltip(
										String.format("TOTAL AMOUNT: ₹%,.0f", mockT[index] * 1450));
								ttTotalMock.setShowDelay(javafx.util.Duration.millis(50));
								javafx.scene.control.Tooltip.install(totalBars[index], ttTotalMock);
							}
							if (actualBars[index] != null) {
								actualBars[index].setPrefHeight(mockA[index]);
								javafx.scene.control.Tooltip ttActMock = new javafx.scene.control.Tooltip(
										String.format("NET COLLECTION: ₹%,.0f", mockA[index] * 1450));
								ttActMock.setShowDelay(javafx.util.Duration.millis(50));
								javafx.scene.control.Tooltip.install(actualBars[index], ttActMock);
							}
						});
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void loadPieChartData() {
		if (jobDistributionChart == null)
			return;
		try (Connection con = DBConnection.getConnection()) {
			String filter = getTimeFilterSQL();
			String sql = "SELECT status, COUNT(*) FROM job WHERE 1=1 " + filter.replace("invoice_date", "order_date")
					+ " AND status IS NOT NULL GROUP BY status";
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
			if (lblActiveJobsCount != null)
				lblActiveJobsCount.setText(String.valueOf(total));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void loadTableData() {
		if (recentJobsTable == null)
			return;
		if (colOrderClient != null)
			colOrderClient.setCellValueFactory(new PropertyValueFactory<>("orderClient"));
		if (colProjectDetails != null)
			colProjectDetails.setCellValueFactory(new PropertyValueFactory<>("projectDetails"));
		if (colReceived != null)
			colReceived.setCellValueFactory(new PropertyValueFactory<>("receivedDate"));
		if (colDueDate != null)
			colDueDate.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
		if (colValuation != null)
			colValuation.setCellValueFactory(new PropertyValueFactory<>("valuation"));
		if (colWorkflow != null)
			colWorkflow.setCellValueFactory(new PropertyValueFactory<>("workflow"));

		ObservableList<DashboardJobDTO> jobs = FXCollections.observableArrayList();
		try (Connection con = DBConnection.getConnection()) {
			String filter = getTimeFilterSQL();
			String sql = "SELECT j.job_name, c.client_name, j.order_date, j.delivery_date, j.total_amount, j.status " +
					"FROM job j JOIN client_master c ON j.client_id = c.id " +
					"WHERE 1=1 " + filter.replace("invoice_date", "j.order_date") +
					" ORDER BY j.id DESC LIMIT 5";
			try (java.sql.Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
				while (rs.next()) {
					jobs.add(new DashboardJobDTO(
							"#" + rs.getString(1) + " / " + rs.getString(2),
							rs.getString(1),
							rs.getString(3),
							rs.getString(4),
							String.format("₹%,.0f", rs.getDouble(5)),
							rs.getString(6)));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		recentJobsTable.setItems(jobs);
	}

	private VBox currentSidebar = null;

	private void loadCenterScreen(String fxmlPath, String title, String subtitle) {
		loadCenterScreen(fxmlPath, title, subtitle, true);
	}

	private String lastValidTitle = "Dashboard";

	public boolean canDiscardChanges() {
		if (currentController instanceof utils.DirtySupport ds && ds.hasUnsavedChanges()) {
			javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
					javafx.scene.control.Alert.AlertType.CONFIRMATION);
			alert.setTitle("Unsaved Changes");
			alert.setHeaderText("Discard unsaved changes?");
			alert.setContentText("You have unsaved changes on this page. If you leave now, all progress will be lost. Do you wish to proceed?");
			alert.getButtonTypes().setAll(javafx.scene.control.ButtonType.YES, javafx.scene.control.ButtonType.NO);
			
			// Apply Premium Theme
			javafx.scene.control.DialogPane pane = alert.getDialogPane();
			pane.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
			pane.getStyleClass().add("atelier-alert");
			
			// Custom Warning Icon
			javafx.scene.layout.Region icon = new javafx.scene.layout.Region();
			icon.getStyleClass().add("alert-icon-warning");
			alert.setGraphic(icon);
			
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

		// Hide search bar on non-dashboard screens
		if (mainSearchBox != null) {
			mainSearchBox.setVisible(false);
			mainSearchBox.setManaged(false);
		}

		// Hide breadcrumb by default for all list/list-like screens
		if (pageTitle != null) {
			pageTitle.setVisible(false);
			pageTitle.setManaged(false);
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
				// ✅ Ensure global theme is always applied (before per-screen CSS)
				String themeUrl = getClass().getResource("/css/theme.css").toExternalForm();
				if (!view.getStylesheets().contains(themeUrl)) {
					view.getStylesheets().add(0, themeUrl);
				}
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

					// 🔒 Store the fully compiled Parent view and its controller into the
					// NavigationManager history
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
		collapseAllSubmenus(false); // Instant hide on sidebar collapse to avoid ghosting
		applyCollapsedStyleToAll(true);
		sidebarStack.getStyleClass().add("sidebar-collapsed-bg");
		sidebarStack.getStyleClass().remove("sidebar-expanded-bg");

		animateSidebarWidth(COLLAPSED_WIDTH);
	}

	private void animateSidebarWidth(double width) {
		if (anim != null)
			anim.stop();

		// Premium Spline Interpolator for a luxurious feel
		Interpolator premiumEasing = Interpolator.SPLINE(0.25, 0.1, 0.25, 1.0);

		anim = new Timeline(
				new KeyFrame(Duration.millis(200),
						new KeyValue(sidebarStack.prefWidthProperty(), width, premiumEasing),
						new KeyValue(sidebarStack.minWidthProperty(), width, premiumEasing),
						new KeyValue(sidebarStack.maxWidthProperty(), width, premiumEasing)));
		anim.play();
	}

	private void toggleSubmenu(VBox submenu, Region chevron, Button parentBtn, Runnable onFinished) {
		boolean wasVisible = submenu.isVisible() && submenu.getOpacity() > 0;
		collapseAllSubmenus(false);

		if (!wasVisible) {
			submenu.setVisible(true);
			submenu.setManaged(true);

			// Initial state for animation
			submenu.setMaxHeight(0);
			submenu.setOpacity(0);
			submenu.setTranslateY(-10);

			Timeline expand = new Timeline(
					new KeyFrame(Duration.millis(250),
							new KeyValue(submenu.maxHeightProperty(), 350, Interpolator.EASE_OUT),
							new KeyValue(submenu.opacityProperty(), 1, Interpolator.EASE_OUT),
							new KeyValue(submenu.translateYProperty(), 0, Interpolator.EASE_OUT)));

			if (chevron != null) {
				expand.getKeyFrames().add(new KeyFrame(Duration.millis(250),
						new KeyValue(chevron.rotateProperty(), 180, Interpolator.EASE_OUT)));
			}

			if (parentBtn != null) {
				parentBtn.getStyleClass().add("sidebar-btn-expanded");
			}

			if (onFinished != null) {
				// ✅ Only run the default child screen loader if this parent isn't already the
				// active section.
				// This prevents clicking a parent after a collapse/expand cycle from resetting
				// the child view.
				if (activeParent != parentBtn) {
					expand.setOnFinished(e -> onFinished.run());
				}
			}

			expand.play();
		}
	}

	public void collapseAllSubmenus(boolean animate) {
		VBox[] submenus = { jobsSubmenu, clientsSubmenu, billingSubmenu, paymentSubmenu, ledgerSubmenu,
				settingsSubmenu };
		Region[] chevrons = { jobsChevron, clientsChevron, billingChevron, paymentChevron, ledgerChevron,
				settingsChevron };
		Button[] btns = { jobsBtn, clientsBtn, billingBtn, paymentBtn, ledgerBtn, settingsBtn };

		for (int i = 0; i < submenus.length; i++) {
			VBox sub = submenus[i];
			Region chv = chevrons[i];
			Button b = btns[i];

			if (sub != null && sub.isVisible()) {
				if (b != null)
					b.getStyleClass().remove("sidebar-btn-expanded");

				sub.setVisible(false);
				sub.setManaged(false);
				sub.setOpacity(0);
				sub.setMaxHeight(0);
				sub.setTranslateY(0);
				if (chv != null)
					chv.setRotate(0);
			}
		}
	}

	private void resetSelectionStyles() {
		Button[] buttons = { jobsBtn, clientsBtn, billingBtn, paymentBtn, ledgerBtn, settingsBtn, dashboardBtn };
		for (Button b : buttons) {
			if (b != null)
				b.getStyleClass().remove("sidebar-btn-active");
		}
	}

	private void highlightActiveMenu(Button parent) {
		resetSelectionStyles();
		resetSubmenuStyles();
		if (parent != null) {
			parent.getStyleClass().add("sidebar-btn-active");
			activeParent = parent;
		}
	}

	private void highlightSubmenu(Button subBtn) {
		resetSubmenuStyles();
		if (subBtn != null) {
			subBtn.getStyleClass().add("submenu-btn-active");
		}
	}

	private void resetSubmenuStyles() {
		if (mainSidebar != null) {
			mainSidebar.lookupAll(".submenu-btn").forEach(node -> {
				if (node instanceof Button b) {
					b.getStyleClass().remove("submenu-btn-active");
				}
			});
		}
	}

	private void showOnly(VBox target) {
		if (target == null)
			return;
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

	@FXML
	private void openDashboard(javafx.event.ActionEvent event) {
		utils.NavigationManager.getInstance().clear();
		// Navigation History cleared at Dashboard root.
		collapseAllSubmenus(true);
		highlightActiveMenu(dashboardBtn);
		openCenterDashboard();
		setPageTitle("Dashboard");
	}

	// ---------------------------
	// Sidebar / Page switching
	// ---------------------------

	@FXML
	public void showJobsSubmenu() {
		toggleSubmenu(jobsSubmenu, jobsChevron, jobsBtn, this::loadAddJob);
	}

	@FXML
	public void showClientsSubmenu() {
		toggleSubmenu(clientsSubmenu, clientsChevron, clientsBtn, this::loadViewClients);
	}

	@FXML
	public void showBillingSubmenu() {
		toggleSubmenu(billingSubmenu, billingChevron, billingBtn, this::loadInvoiceGenration);
	}

	@FXML
	public void showPaymentSubmenu() {
		toggleSubmenu(paymentSubmenu, paymentChevron, paymentBtn, this::loadRecordPayment);
	}

	@FXML
	public void showLedgerSubmenu() {
		toggleSubmenu(ledgerSubmenu, ledgerChevron, ledgerBtn, this::loadClientLedger);
	}

	@FXML
	public void showSettingSubmenu() {
		toggleSubmenu(settingsSubmenu, settingsChevron, settingsBtn, this::loadGeneralSettings);
	}

	private VBox findSidebarById(String id) {
		return mainSidebar; // Dashboard is now a single-sidebar accordion
	}

	@FXML
	private void showMainSidebar(MouseEvent event) {
		openDashboard(null);
	}

	@FXML
	public void handleBack(javafx.event.Event event) {
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
				event.consume();

				// RESTORE VISIBILITY ON BACK
				boolean isDetailView = prevState.getFxmlPath() != null &&
						(prevState.getFxmlPath().contains("client_profile") ||
								prevState.getFxmlPath().contains("client_form"));
				if (pageTitle != null) {
					pageTitle.setVisible(isDetailView);
					pageTitle.setManaged(isDetailView);
					setPageTitle(prevState.getTitle());
				}

				// ⚡ If the history stack preserved the actual screen memory, restore it
				// instantly:
				if (prevState.getView() != null) {
					System.out.println("DEBUG: Restoring CACHED view from history: " + prevState.getFxmlPath());
					this.currentController = prevState.getController(); // ⚡ RESTORE CONTROLLER
					centerContentHost.getChildren().setAll(prevState.getView());
				} else if (prevState.getFxmlPath() == null) {
					// 🏠 Back to Dashboard
					collapseAllSubmenus(true);
					highlightActiveMenu(dashboardBtn);
					openCenterDashboard();
					setPageTitle("Dashboard");
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
	private void loadClientLedger() {
		highlightActiveMenu(ledgerBtn);
		highlightSubmenu(clientLedgerSubBtn);
		loadCenterScreen("/fxml/client_ledger.fxml",
				"Loading Ledger...",
				"Fetching financial records...");
	}

	@FXML
	private void loadAddClient() {
		highlightActiveMenu(clientsBtn);
		highlightSubmenu(addClientSubBtn);
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/client_form.fxml"));
			Parent view = loader.load();
			ClientFormController controller = loader.getController();
			controller.setClientData(null); // Mode: ADD

			// ✅ Ensure global theme is always applied (before per-screen CSS)
			String themeUrl = getClass().getResource("/css/theme.css").toExternalForm();
			if (!view.getStylesheets().contains(themeUrl)) {
				view.getStylesheets().add(0, themeUrl);
			}

			centerContentHost.getChildren().setAll(view);
			if (pageTitle != null) {
				pageTitle.setVisible(true);
				pageTitle.setManaged(true);
				setPageTitle("Register New Client");
			}

			utils.NavigationManager.getInstance().push("/fxml/client_form.fxml", "Add Client", "New Registration",
					clientsSubmenu.getId());
			utils.NavigationManager.getInstance().updateCurrentState(view, controller);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@FXML
	public void loadAddJob() {
		highlightActiveMenu(jobsBtn);
		highlightSubmenu(addJobSubBtn);
		loadCenterScreen("/fxml/add_job.fxml", "Creating New Job...", "Setting up workspace");
	}

	@FXML
	public void loadViewJob() {
		highlightActiveMenu(jobsBtn);
		loadCenterScreen("/fxml/view_job.fxml",
				"Loading Dashboard...",
				"Please wait");
	}

	@FXML
	public void loadViewClients() {
		highlightActiveMenu(clientsBtn);
		highlightSubmenu(viewClientsSubBtn);
		loadCenterScreen("/fxml/view_client.fxml",
				"Loading Client Directory...",
				"Please wait");
	}

	@FXML
	public void loadEditClientSidebar() {
		highlightActiveMenu(clientsBtn);
		highlightSubmenu(editClientSubBtn);
		loadCenterScreen("/fxml/client_edit_selection.fxml",
				"Loading Client Selection...",
				"Please wait");
	}

	public void loadClientProfile(model.Client client) {
		if (client == null)
			return;
		if (pageTitle != null) {
			pageTitle.setVisible(true);
			pageTitle.setManaged(true);
			setPageTitle("Client Profile / " + client.getBusinessName());
		}

		if (centerLoaderIncludeController != null) {
			centerLoaderIncludeController.show("Loading Profile...",
					"Fetching detailed insights for " + client.getBusinessName());
		}

		new Thread(() -> {
			try {
				FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/client_profile.fxml"));
				Parent view = loader.load();
				ClientProfileController controller = loader.getController();
				controller.setClient(client);

				// ✅ Ensure global theme is always applied (before per-screen CSS)
				String themeUrl = getClass().getResource("/css/theme.css").toExternalForm();
				if (!view.getStylesheets().contains(themeUrl)) {
					view.getStylesheets().add(0, themeUrl);
				}

				Platform.runLater(() -> {
					centerContentHost.getChildren().setAll(view);
					if (centerLoaderIncludeController != null) {
						centerLoaderIncludeController.hide();
					}
					// Push to history manually for deep navigation
					utils.NavigationManager.getInstance().push("/fxml/client_profile.fxml", "Client Profile",
							client.getBusinessName(), clientsSubmenu.getId());
					utils.NavigationManager.getInstance().updateCurrentState(view, controller);
				});
			} catch (Exception e) {
				e.printStackTrace();
				Platform.runLater(() -> {
					if (centerLoaderIncludeController != null)
						centerLoaderIncludeController.hide();
				});
			}
		}).start();
	}

	public void loadEditClient(model.Client client) {
		if (client == null)
			return;
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/client_form.fxml"));
			Parent view = loader.load();
			ClientFormController controller = loader.getController();
			controller.setClientData(client);

			// ✅ Ensure global theme is always applied (before per-screen CSS)
			String themeUrl = getClass().getResource("/css/theme.css").toExternalForm();
			if (!view.getStylesheets().contains(themeUrl)) {
				view.getStylesheets().add(0, themeUrl);
			}

			centerContentHost.getChildren().setAll(view);
			if (pageTitle != null) {
				pageTitle.setVisible(true);
				pageTitle.setManaged(true);
				setPageTitle("Edit Client / " + client.getBusinessName());
			}

			utils.NavigationManager.getInstance().push("/fxml/client_form.fxml", "Edit Client",
					client.getBusinessName(), clientsSubmenu.getId());
			utils.NavigationManager.getInstance().updateCurrentState(view, controller);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setCenterView(Parent view) {
		centerContentHost.getChildren().setAll(view);
	}

	@FXML
	private void loadInvoiceGenration() {
		highlightActiveMenu(billingBtn);
		highlightSubmenu(genInvoiceSubBtn);
		loadCenterScreen("/fxml/invoice.fxml",
				"Loading Dashboard...",
				"Please wait");
	}

	@FXML
	public void loadViewInvoiceJobs() {
		highlightActiveMenu(billingBtn);
		loadCenterScreen("/fxml/view_invoice_jobs.fxml",
				"Loading Invoice Jobs...",
				"Please wait");
	}

	@FXML
	public void loadViewBills() {
		switchToInvoices();
	}

	public void switchToInvoices() {
		highlightActiveMenu(billingBtn);
		loadCenterScreen("/fxml/view_invoices.fxml",
				"Loading Invoices...",
				"Fetching billing records...");
	}

	@FXML
	public void loadCreditDebitNote() {
		highlightActiveMenu(billingBtn);
		loadCenterScreen("/fxml/credit_debit_note.fxml",
				"Loading Note...",
				"Please wait");
	}

	@FXML
	public void loadRecordPayment() {
		highlightActiveMenu(paymentBtn);
		highlightSubmenu(recPaymentSubBtn);
		loadCenterScreen("/fxml/record_payment.fxml",
				"Loading Payments...",
				"Please wait");
	}

	@FXML
	public void loadPaymentHistory() {
		highlightActiveMenu(paymentBtn);
		loadCenterScreen("/fxml/payment_history.fxml",
				"Loading Payment History...",
				"Fetching payment records...");
	}

	@FXML
	public void loadGeneralSettings() {
		highlightActiveMenu(settingsBtn);
		highlightSubmenu(genSettingsSubBtn);
		loadCenterScreen("/fxml/general_settings.fxml",
				"Loading General Settings...",
				"Please wait");
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
		highlightActiveMenu(settingsBtn);
		loadCenterScreen("/fxml/user_settings.fxml",
				"Loading User Settings...",
				"Please wait");
	}

	@FXML
	private void loadInvoiceSettings() {
		highlightActiveMenu(settingsBtn);
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
			Scene scene = new Scene(loginRoot);
			scene.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
			stage.setScene(scene);
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
