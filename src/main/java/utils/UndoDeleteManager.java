package utils;

import model.Client;
import repository.ClientRepository;

public class UndoDeleteManager {

	private static Client lastDeletedClient = null;

	public static void store(Client client) {
		lastDeletedClient = client;
	}

	public static boolean undo() {
		if (lastDeletedClient == null)
			return false;

		ClientRepository repo = new ClientRepository();
		boolean restored = repo.save(lastDeletedClient); // Reinsert into DB

		if (restored) {
			lastDeletedClient = null;
		}
		return restored;
	}

	public static void clear() {
		lastDeletedClient = null;
	}

}
