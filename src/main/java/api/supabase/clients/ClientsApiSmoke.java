package api.supabase.clients;

import api.supabase.SupabaseClients;
import api.supabase.SupabaseRestClient;
import model.Client;

/**
 * Smoke test for the clients PostgREST API.
 * Run from project root (SQLite path for settings):
 *   mvn -q exec:java -Dexec.mainClass=api.supabase.clients.ClientsApiSmoke
 * Or with URL and anon key (no local DB):
 *   mvn -q exec:java -Dexec.mainClass=api.supabase.clients.ClientsApiSmoke -Dexec.args="https://xxx.supabase.co YOUR_ANON_JWT"
 */
public final class ClientsApiSmoke {

	public static void main(String[] args) throws Exception {
		SupabaseRestClient http;
		if (args.length >= 2) {
			http = new SupabaseRestClient(args[0], args[1]);
		} else {
			http = SupabaseClients.fromLocalDatabase();
		}
		ClientsSupabaseApi api = new ClientsSupabaseApi(http);
		System.out.println("GET public.clients (order=uuid.asc, limit=10) ...");
		java.util.List<Client> list = api.listOrderById(10);
		System.out.println("Rows: " + list.size());
		for (Client c : list) {
			System.out.println("  uuid=" + c.getClientUuid() + "  client_code=" + c.getClientCode()
					+ "  business_name=" + c.getBusinessName() + "  client_name=" + c.getClientName());
		}
		System.out.println("Clients API read OK.");
	}
}