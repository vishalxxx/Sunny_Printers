package utils;

import java.util.Optional;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.stage.Window;

import model.Client;
import repository.ClientRepository;

public final class ClientDeleteHelper {

	private ClientDeleteHelper() {
	}

	public static boolean confirmAndDelete(Window ownerWindow, Client client, ClientRepository repo) {
		if (client == null || !client.hasClientUuid() || repo == null) {
			return false;
		}
		String display = client.getBusinessName();
		if (display == null || display.isBlank()) {
			display = client.getClientName();
		}
		if (display == null || display.isBlank()) {
			display = client.getClientCode();
		}
		if (display == null || display.isBlank()) {
			display = "Client";
		}
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		if (ownerWindow != null) {
			alert.initOwner(ownerWindow);
		}
		alert.setTitle("Delete client");
		alert.setHeaderText("Remove this client?");
		alert.setContentText(
				"Archive \"" + display + "\" on this device and request removal from the cloud. Continue?");
		Optional<ButtonType> choice = alert.showAndWait();
		if (choice.isEmpty() || choice.get() != ButtonType.OK) {
			return false;
		}
		try {
			if (repo.deleteByUuid(client.getClientUuid())) {
				toast(ownerWindow, "Client removed.");
				return true;
			}
			toast(ownerWindow, "Could not delete this client.");
		} catch (Exception ex) {
			ex.printStackTrace();
			toast(ownerWindow, "Delete failed.");
		}
		return false;
	}

	private static void toast(Window w, String msg) {
		if (w instanceof Stage stage) {
			Toast.show(stage, msg);
		}
	}
}