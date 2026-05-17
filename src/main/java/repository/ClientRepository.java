package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import api.supabase.SupabaseGate;
import api.supabase.clients.ClientsSupabaseApi;
import model.Client;
import service.NumberSequenceAllocationService.AllocatedNumber;
import service.sync.UniversalNumberAllocator;
import service.sync.UniversalSyncEngine;
import utils.ClientIdentifiers;
import utils.DBConnection;
import utils.DocumentNumbering;

public class ClientRepository {

	private static Client mapFromResultSet(ResultSet rs) throws SQLException {
		Client c = new Client(nz(rs, "business_name"), nz(rs, "client_name"), nz(rs, "mobile"),
				nz(rs, "alternate_mobile"), nz(rs, "email"), nz(rs, "gstin"), nz(rs, "pan_number"),
				nz(rs, "billing_address"), nz(rs, "shipping_address"), nz(rs, "notes"));
		c.setClientUuid(nz(rs, "uuid"));
		c.setClientCode(nz(rs, "client_code"));
		c.setClientType(nz(rs, "client_type"));
		c.setPriceCategory(nz(rs, "price_category"));
		c.setPaymentTerms(nz(rs, "payment_terms"));
		c.setBalanceType(nz(rs, "balance_type"));
		c.setSyncStatus(nz(rs, "sync_status"));
		c.setSyncVersion(rs.getInt("sync_version"));
		c.setIsDeleted(rs.getInt("is_deleted"));
		c.setIsActive(rs.getInt("is_active"));
		c.setCreatedAt(nz(rs, "created_at"));
		c.setUpdatedAt(nz(rs, "updated_at"));
		c.setSyncedAt(nz(rs, "synced_at"));
		c.setDeletedAt(nz(rs, "deleted_at"));
		c.setCreditLimit(rs.getDouble("credit_limit"));
		c.setOpeningBalance(rs.getDouble("opening_balance"));
		return c;
	}

	private static String nz(ResultSet rs, String col) throws SQLException {
		String v = rs.getString(col);
		return v != null ? v : "";
	}

	public boolean save(Client client) throws Exception {
		String sql = """
				INSERT INTO clients (
				  uuid, client_code, client_name, business_name, mobile, alternate_mobile, email,
				  gstin, pan_number, billing_address, shipping_address,
				  client_type, price_category, credit_limit, payment_terms, opening_balance, balance_type,
				  is_active, notes, sync_status, sync_version, is_deleted, deleted_at
				) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
				""";
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(sql)) {
			String uid = client.getClientUuid();
			if (uid == null || uid.isBlank()) {
				uid = ClientIdentifiers.newUuidV7String();
				client.setClientUuid(uid);
			}
			String code = client.getClientCode();
			UniversalNumberAllocator seqAlloc = UniversalNumberAllocator.getInstance();
			boolean allocatedTempCode = false;
			if (code == null || code.isBlank()) {
				AllocatedNumber allocated = seqAlloc.allocateClientCode(conn);
				code = allocated.value();
				allocatedTempCode = allocated.temporary();
				client.setClientCode(code);
			} else {
				code = code.trim();
				client.setClientCode(code);
				if (ClientIdentifiers.clientCodeInUse(conn, code, null)) {
					AllocatedNumber allocated = seqAlloc.allocateClientCode(conn);
					code = allocated.value();
					allocatedTempCode = allocated.temporary();
					client.setClientCode(code);
				}
			}
			if (allocatedTempCode || DocumentNumbering.isTemporaryNumber(code)) {
				client.setSyncStatus("PENDING");
			} else if (client.getSyncStatus() == null || client.getSyncStatus().isBlank()) {
				client.setSyncStatus("PENDING");
			}
			int i = 1;
			ps.setString(i++, uid);
			ps.setString(i++, code);
			ps.setString(i++, nz(client.getClientName()));
			ps.setString(i++, nz(client.getBusinessName()));
			ps.setString(i++, nz(client.getPhone()));
			ps.setString(i++, nz(client.getAltPhone()));
			ps.setString(i++, nz(client.getEmail()));
			ps.setString(i++, nz(client.getGst()));
			ps.setString(i++, nz(client.getPan()));
			ps.setString(i++, nz(client.getBillingAddress()));
			ps.setString(i++, nz(client.getShippingAddress()));
			ps.setString(i++, nz(client.getClientType()));
			ps.setString(i++, nz(client.getPriceCategory()));
			ps.setDouble(i++, client.getCreditLimit());
			ps.setString(i++, nz(client.getPaymentTerms()));
			ps.setDouble(i++, client.getOpeningBalance());
			ps.setString(i++, nz(client.getBalanceType()));
			ps.setInt(i++, client.getIsActive() > 0 ? client.getIsActive() : 1);
			ps.setString(i++, nz(client.getNotes()));
			ps.setString(i++, nz(client.getSyncStatus()));
			ps.setInt(i++, client.getSyncVersion());
			ps.setInt(i++, 0);
			ps.setNull(i++, Types.VARCHAR);
			int n = ps.executeUpdate();
			if (n > 0) {
				if (!DocumentNumbering.isTemporaryNumber(client.getClientCode())) {
					pushClientToSupabaseAsync(client, false);
				}
				UniversalSyncEngine.scheduleSyncAsync();
			}
			return n > 0;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	private static String nz(String s) {
		return s != null ? s : "";
	}

	public List<Client> findAllSortedById() {
		List<Client> list = new ArrayList<>();
		String sql = """
				SELECT * FROM clients
				WHERE IFNULL(is_deleted,0)=0 AND IFNULL(is_active,1)=1
				ORDER BY created_at ASC, uuid ASC
				""";
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(mapFromResultSet(rs));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return list;
	}

	public List<Client> findAllWithUninvoicedCompletedJobs() {
		List<Client> list = new ArrayList<>();
		String sql = """
				SELECT DISTINCT c.*
				FROM clients c
				INNER JOIN jobs j ON j.client_uuid = c.uuid
				WHERE IFNULL(c.is_deleted,0)=0 AND IFNULL(c.is_active,1)=1
				  AND j.invoice_uuid IS NULL
				  AND LOWER(TRIM(REPLACE(COALESCE(j.status,''), '_', ' '))) = 'completed'
				ORDER BY c.business_name ASC
				""";
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(mapFromResultSet(rs));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return list;
	}

	public List<Client> search(String keyword) {
		List<Client> list = new ArrayList<>();
		String sql = """
				SELECT * FROM clients WHERE IFNULL(is_deleted,0)=0 AND IFNULL(is_active,1)=1 AND (
				  uuid LIKE ? OR client_code LIKE ? OR business_name LIKE ? OR client_name LIKE ?
				  OR mobile LIKE ? OR alternate_mobile LIKE ? OR email LIKE ? OR gstin LIKE ?
				  OR pan_number LIKE ? OR billing_address LIKE ? OR shipping_address LIKE ?
				) ORDER BY business_name ASC
				""";
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(sql)) {
			String value = "%" + keyword + "%";
			for (int i = 1; i <= 11; i++) {
				ps.setString(i, value);
			}
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					list.add(mapFromResultSet(rs));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return list;
	}

	public boolean deleteByUuid(String clientUuid) {
		if (clientUuid == null || clientUuid.isBlank()) {
			return false;
		}
		String sql = """
				UPDATE clients SET is_deleted=1, is_active=0, deleted_at=datetime('now'),
				sync_status='PENDING', updated_at=datetime('now') WHERE uuid=? AND IFNULL(is_deleted,0)=0
				""";
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, clientUuid.trim());
			boolean ok = ps.executeUpdate() > 0;
			if (ok) {
				deleteClientOnSupabaseAsync(clientUuid.trim());
			}
			return ok;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	public List<Client> findPendingForSync() {
		List<Client> list = new ArrayList<>();
		String sql = """
				SELECT * FROM clients
				WHERE IFNULL(is_deleted, 0) = 0
				  AND UPPER(TRIM(COALESCE(sync_status, ''))) IN ('', 'PENDING')
				  AND client_code NOT LIKE 'TEMP-%'
				ORDER BY created_at ASC
				""";
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(mapFromResultSet(rs));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return list;
	}

	public List<Client> findClientsWithTemporaryCodes() {
		List<Client> list = new ArrayList<>();
		String sql = """
				SELECT * FROM clients
				WHERE IFNULL(is_deleted, 0) = 0
				  AND client_code LIKE 'TEMP-%'
				ORDER BY created_at ASC
				""";
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(mapFromResultSet(rs));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return list;
	}

	public void updateClientCode(Connection con, String clientUuid, String newCode) throws Exception {
		if (clientUuid == null || clientUuid.isBlank() || newCode == null || newCode.isBlank()) {
			return;
		}
		try (PreparedStatement ps = con.prepareStatement("""
				UPDATE clients SET client_code = ?, sync_status = 'PENDING',
				sync_version = sync_version + 1, updated_at = datetime('now')
				WHERE uuid = ?
				""")) {
			ps.setString(1, newCode.trim());
			ps.setString(2, clientUuid.trim());
			ps.executeUpdate();
		}
	}

	/** Synchronous Supabase upsert (used after promoting TEMP client codes). */
	public void pushClientToSupabase(Client client) {
		if (client == null || !client.hasClientUuid()) {
			return;
		}
		if (DocumentNumbering.isTemporaryNumber(client.getClientCode())) {
			return;
		}
		SupabaseGate.restClientIfConfigured().ifPresent(http -> {
			try {
				new ClientsSupabaseApi(http).upsert(client);
				markClientSyncedLocally(client.getClientUuid());
			} catch (Exception ex) {
				System.err.println("[Supabase clients] remote write failed for uuid=" + client.getClientUuid() + ": "
						+ ex.getMessage());
			}
		});
	}

	public Client findByUuid(String clientUuid) {
		if (clientUuid == null || clientUuid.isBlank()) {
			return null;
		}
		String sql = "SELECT * FROM clients WHERE uuid=?";
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, clientUuid.trim());
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return mapFromResultSet(rs);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public boolean update(Client client) {
		Client before = findByUuid(client.getClientUuid());
		String sql = """
				UPDATE clients SET client_name=?, business_name=?, mobile=?, alternate_mobile=?,
				email=?, gstin=?, pan_number=?, billing_address=?, shipping_address=?,
				client_type=?, price_category=?, credit_limit=?, payment_terms=?, opening_balance=?, balance_type=?,
				notes=?, sync_status='PENDING', sync_version=?, updated_at=datetime('now') WHERE uuid=?
				""";
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(sql)) {
			int i = 1;
			ps.setString(i++, nz(client.getClientName()));
			ps.setString(i++, nz(client.getBusinessName()));
			ps.setString(i++, nz(client.getPhone()));
			ps.setString(i++, nz(client.getAltPhone()));
			ps.setString(i++, nz(client.getEmail()));
			ps.setString(i++, nz(client.getGst()));
			ps.setString(i++, nz(client.getPan()));
			ps.setString(i++, nz(client.getBillingAddress()));
			ps.setString(i++, nz(client.getShippingAddress()));
			ps.setString(i++, nz(client.getClientType()));
			ps.setString(i++, nz(client.getPriceCategory()));
			ps.setDouble(i++, client.getCreditLimit());
			ps.setString(i++, nz(client.getPaymentTerms()));
			ps.setDouble(i++, client.getOpeningBalance());
			ps.setString(i++, nz(client.getBalanceType()));
			ps.setString(i++, nz(client.getNotes()));
			ps.setInt(i++, client.getSyncVersion() + 1);
			ps.setString(i++, client.getClientUuid());
			boolean ok = ps.executeUpdate() > 0;
			if (ok) {
				client.setSyncVersion(client.getSyncVersion() + 1);
				pushClientToSupabaseAsync(client, true, before);
				UniversalSyncEngine.scheduleSyncAsync();
			}
			return ok;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	private static void pushClientToSupabaseAsync(Client client, boolean preferPatch) {
		pushClientToSupabaseAsync(client, preferPatch, null);
	}

	private static void pushClientToSupabaseAsync(Client client, boolean preferPatch, Client before) {
		if (client == null || !client.hasClientUuid()) {
			return;
		}
		SupabaseGate.restClientIfConfigured().ifPresent(http -> CompletableFuture.runAsync(() -> {
			try {
				ClientsSupabaseApi api = new ClientsSupabaseApi(http);
				if (preferPatch) {
					api.patchUpdate(client, before);
				} else {
					api.upsert(client);
				}
				markClientSyncedLocally(client.getClientUuid());
			} catch (Exception ex) {
				System.err.println("[Supabase clients] remote write failed for uuid=" + client.getClientUuid() + ": "
						+ ex.getMessage());
				UniversalSyncEngine.scheduleSyncAsync();
			}
		}));
	}

	private static void deleteClientOnSupabaseAsync(String clientUuid) {
		if (clientUuid == null || clientUuid.isBlank()) {
			return;
		}
		SupabaseGate.restClientIfConfigured().ifPresent(http -> CompletableFuture.runAsync(() -> {
			try {
				new ClientsSupabaseApi(http).deleteByClientUuid(clientUuid);
				markClientSyncedLocally(clientUuid);
			} catch (Exception ex) {
				System.err.println("[Supabase clients] delete failed for uuid=" + clientUuid + ": " + ex.getMessage());
				ex.printStackTrace();
			}
		}));
	}

	private static void markClientSyncedLocally(String clientUuid) {
		if (clientUuid == null || clientUuid.isBlank()) {
			return;
		}
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(
						"UPDATE clients SET sync_status='SYNCED', synced_at=datetime('now') WHERE uuid=?")) {
			ps.setString(1, clientUuid.trim());
			ps.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
