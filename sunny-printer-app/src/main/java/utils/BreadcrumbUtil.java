package utils;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Breadcrumbs mirror the left sidebar hierarchy (application structure), not click history.
 * Pattern: {@code Parent › Submenu leaf}, max {@value #MAX_CRUMBS} segments, no duplicate labels.
 */
public final class BreadcrumbUtil {

	private static final int MAX_CRUMBS = 3;

	private record SidebarEntry(String parent, String defaultLeaf) {
	}

	private static final Map<String, SidebarEntry> SIDEBAR = createSidebarMap();

	private static Map<String, SidebarEntry> createSidebarMap() {
		Map<String, SidebarEntry> m = new LinkedHashMap<>();
		// Clients (submenu labels from dashboard.fxml)
		m.put("/fxml/view_client.fxml", new SidebarEntry("Clients", "View Clients"));
		m.put("/fxml/client_edit_selection.fxml", new SidebarEntry("Clients", "Edit Client"));
		m.put("/fxml/client_form.fxml", new SidebarEntry("Clients", "Add Client"));
		m.put("/fxml/client_profile.fxml", new SidebarEntry("Clients", "Client Profile"));
		// Jobs
		m.put("/fxml/add_job.fxml", new SidebarEntry("Jobs", "New Print Job"));
		m.put("/fxml/view_job.fxml", new SidebarEntry("Jobs", "View All Jobs"));
		m.put("/fxml/edit_job.fxml", new SidebarEntry("Jobs", "Edit Job"));
		// Invoices (sidebar parent label "Invoices")
		m.put("/fxml/generate_invoice.fxml", new SidebarEntry("Invoices", "Generate Invoice"));
		m.put("/fxml/genrate_gst_invoice.fxml", new SidebarEntry("Invoices", "Generate GST Invoice"));
		m.put("/fxml/view_invoices.fxml", new SidebarEntry("Invoices", "View Invoices"));
		m.put("/fxml/view_invoice_jobs.fxml", new SidebarEntry("Invoices", "Invoice Jobs"));
		m.put("/fxml/credit_debit_note.fxml", new SidebarEntry("Invoices", "CN / DN Notes"));
		// Payments
		m.put("/fxml/record_payment.fxml", new SidebarEntry("Payments", "Record Payment"));
		m.put("/fxml/payment_history.fxml", new SidebarEntry("Payments", "Payment History"));
		// Ledger
		m.put("/fxml/client_ledger.fxml", new SidebarEntry("Ledger", "Client Ledger"));
		// Settings
		m.put("/fxml/general_settings.fxml", new SidebarEntry("Settings", "General Settings"));
		m.put("/fxml/invoice_settings.fxml", new SidebarEntry("Settings", "Invoice Templates"));
		m.put("/fxml/user_settings.fxml", new SidebarEntry("Settings", "User Permissions"));
		m.put("/fxml/profile_settings.fxml", new SidebarEntry("Settings", "Profile Settings"));
		return Collections.unmodifiableMap(m);
	}

	private BreadcrumbUtil() {
	}

	/**
	 * Builds breadcrumbs from the current {@link NavigationManager} screen and sidebar map.
	 *
	 * @param leafLabelOverride optional leaf label (e.g. {@code Add Client} vs {@code Edit Client} for
	 *                          {@code client_form.fxml}, or a client name for {@code client_profile.fxml})
	 */
	public static void populateBreadcrumbs(HBox container, String leafLabelOverride, Runnable backAction) {
		if (container == null) {
			return;
		}
		container.getChildren().clear();

		NavigationManager nav = NavigationManager.getInstance();
		NavigationManager.NavState current = nav.getCurrentState();
		if (current == null) {
			return;
		}
		String fxmlPath = current.getFxmlPath();
		if (fxmlPath == null || fxmlPath.isBlank()) {
			return;
		}

		List<NavigationManager.NavState> states = nav.getHistory();
		if (states.size() > 1 && shouldShowParentScopedBack(states)) {
			Button backBtn = new Button();
			backBtn.getStyleClass().add("breadcrumb-back-btn");
			backBtn.setFocusTraversable(false);
			Region icon = new Region();
			icon.getStyleClass().add("back-icon");
			backBtn.setGraphic(icon);
			if (backAction != null) {
				backBtn.setOnAction(e -> backAction.run());
			} else {
				backBtn.setOnAction(e -> {
					controller.MainController mc = controller.MainController.getInstance();
					if (mc != null) {
						mc.handleBack(e);
					}
				});
			}
			container.getChildren().add(backBtn);
		}

		List<String> trail = structuralTrail(fxmlPath, leafLabelOverride);
		if (trail.isEmpty()) {
			return;
		}

		if (!container.getStyleClass().contains("breadcrumb-container")) {
			container.getStyleClass().add("breadcrumb-container");
		}

		for (int i = 0; i < trail.size(); i++) {
			String text = trail.get(i);
			if (text == null || text.isBlank()) {
				continue;
			}
			Label label = new Label(text.trim());
			label.getStyleClass().add("breadcrumb-label");
			if (i == trail.size() - 1) {
				label.getStyleClass().add("breadcrumb-current");
			}
			container.getChildren().add(label);
			if (i < trail.size() - 1) {
				Label sep = new Label("›");
				sep.getStyleClass().add("breadcrumb-separator");
				sep.setMouseTransparent(true);
				container.getChildren().add(sep);
			}
		}
	}

	public static void populateBreadcrumbs(HBox container, Runnable backAction) {
		populateBreadcrumbs(container, null, backAction);
	}

	/**
	 * Drops history entries that belong to a different sidebar parent than {@code currentFxmlPath},
	 * so back does not land on a child of another parent.
	 */
	public static void discardForeignParentHistory(NavigationManager nav, String currentFxmlPath) {
		if (nav == null) {
			return;
		}
		String leavingModule = sidebarParentForPath(currentFxmlPath);
		if (leavingModule == null) {
			return;
		}
		while (nav.hasHistory()) {
			NavigationManager.NavState top = nav.peekHistory();
			if (top == null) {
				break;
			}
			if (isBreadcrumbSuppressed(top)) {
				break;
			}
			String path = top.getFxmlPath();
			if (path == null || path.isBlank()) {
				break;
			}
			String topModule = sidebarParentForPath(path);
			if (topModule != null && !leavingModule.equals(topModule)) {
				nav.discardTopHistoryEntry();
			} else {
				break;
			}
		}
	}

	static boolean shouldShowParentScopedBack(List<NavigationManager.NavState> states) {
		if (states == null || states.size() < 2) {
			return false;
		}
		NavigationManager.NavState current = states.get(states.size() - 1);
		String leavingMod = sidebarParentForPath(current != null ? current.getFxmlPath() : null);
		if (leavingMod == null) {
			return true;
		}
		for (int i = states.size() - 2; i >= 0; i--) {
			NavigationManager.NavState s = states.get(i);
			if (isBreadcrumbSuppressed(s)) {
				return true;
			}
			String path = s.getFxmlPath();
			if (path == null || path.isBlank()) {
				return true;
			}
			String sm = sidebarParentForPath(path);
			if (sm == null || sm.equals(leavingMod)) {
				return true;
			}
		}
		return true;
	}

	private static List<String> structuralTrail(String fxmlPath, String leafLabelOverride) {
		SidebarEntry entry = SIDEBAR.get(fxmlPath);
		if (entry == null) {
			return List.of();
		}
		String leaf = entry.defaultLeaf();
		if (leafLabelOverride != null && !leafLabelOverride.isBlank()) {
			leaf = leafLabelOverride.trim();
		}
		List<String> out = new ArrayList<>(2);
		String parent = entry.parent();
		out.add(parent);
		if (!parent.equalsIgnoreCase(leaf)) {
			out.add(leaf);
		}
		// Dedupe accidental consecutive equals
		List<String> compact = new ArrayList<>();
		for (String s : out) {
			if (compact.isEmpty() || !compact.get(compact.size() - 1).equalsIgnoreCase(s)) {
				compact.add(s);
			}
		}
		if (compact.size() > MAX_CRUMBS) {
			return new ArrayList<>(compact.subList(compact.size() - MAX_CRUMBS, compact.size()));
		}
		return compact;
	}

	private static String sidebarParentForPath(String fxmlPath) {
		if (fxmlPath == null || fxmlPath.isBlank()) {
			return null;
		}
		SidebarEntry e = SIDEBAR.get(fxmlPath);
		return e != null ? e.parent() : null;
	}

	private static boolean isBreadcrumbSuppressed(NavigationManager.NavState s) {
		if (s == null) {
			return true;
		}
		String path = s.getFxmlPath();
		if (path == null || path.isBlank()) {
			return true;
		}
		String title = s.getTitle();
		return title != null && "dashboard".equalsIgnoreCase(title.trim());
	}
}
