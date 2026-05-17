package service;

import java.util.List;

import model.Client;
import repository.ClientRepository;

public class ClientService {
	private final ClientRepository repo = new ClientRepository();

	/**
	 * Lists clients from local SQLite. When Supabase URL + anon key are configured,
	 * {@link ClientRepository} also pushes create/update/delete to the {@code clients} table asynchronously.
	 */
	public List<Client> getAllClients() {
		return repo.findAllSortedById();
	}

	/** Clients with at least one completed, not-yet-invoiced job. */
	public List<Client> getClientsWithUninvoicedCompletedJobs() {
		return repo.findAllWithUninvoicedCompletedJobs();
	}

	public List<Client> searchClients(String keyword) {
		if (keyword == null || keyword.trim().isEmpty()) {
			return repo.findAllSortedById();
		}
		return repo.search(keyword);
	}
}
