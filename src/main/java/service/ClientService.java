package service;

import java.util.List;

import model.Client;
import repository.ClientRepository;

public class ClientService {
	private final ClientRepository repo = new ClientRepository();

	public List<Client> getAllClients() {
		return repo.findAllSortedById();
	}

	public List<Client> searchClients(String keyword) {
		if (keyword == null || keyword.trim().isEmpty()) {
			return repo.findAllSortedById();
		}
		return repo.search(keyword);
	}
}
