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
import api.supabase.SupabaseEndpoints;
import api.supabase.clients.ClientsSupabaseApi;
import model.Client;
import model.User;
import service.NumberSequenceAllocationService.AllocatedNumber;
import service.sync.UniversalNumberAllocator;
import service.sync.UniversalSyncEngine;
import service.sync.SyncConflictResolver;
import utils.ClientIdentifiers;
import utils.DBConnection;
import utils.DocumentNumbering;
import utils.SessionManager;

public class ClientRepository {

	private static Client mapFromResultSet(ResultSet rs) throws SQLException {
		Client c = new Client(nz(rs, "business_name"), nz(rs, "client_name"), nz(rs, "mobile"),
				nz(rs, "alternate_mobile"), nz(rs, "email"), nz(rs, "gstin"), nz(rs, "pan_number"),
				nz(rs, "billing_address"), nz(rs, "shipping_address"), nz(rs, "notes"));
		c.setClientUuid(nz(rs, "uuid"));
		c.setClientCode(nz(rs, "client_code"));
		c.setState(nz(rs, "state"));
		c.setClientType(nz(rs, "client_type"));
		c.setPriceCategory(nz(rs, "price_category"));
		c.setPaymentTerms(nz(rs, "payment_terms"));
		c.setBalanceType(nz(rs, "balance_type"));
		c.setSyncStatus(nz(rs, "sync_status"));
		c.setSyncVersion(rs.getInt("sync_version"));
		c.setIsDeleted(rs.getInt("is_deleted") != 0);
		c.setIsActive(rs.getInt("is_active") != 0);
		c.setCreatedAt(nz(rs, "created_at"));
		c.setUpdatedAt(nz(rs, "updated_at"));
		c.setSyncedAt(nz(rs, "synced_at"));
		c.setDeletedAt(nz(rs, "deleted_at"));
		c.setCreditLimit(rs.getDouble("credit_limit"));
		c.setOpeningBalance(rs.getDouble("opening_balance"));
		c.setCreatedByUserUuid(nz(rs, "created_by_user_uuid"));
		c.setUpdatedByUserUuid(nz(rs, "updated_by_user_uuid"));
		return c;
	}

	private static String nz(ResultSet rs, String col) throws SQLException {
		String v = rs.getString(col);
		return v != null ? v : "";
	}

	public boolean save(Client client) throws Exception {
		String userUuid = null;
		if (utils.SessionManager.getInstance().getCurrentUser() != null) {
			userUuid = utils.SessionManager.getInstance().getCurrentUser().getUuid();
		}
		client.setCreatedByUserUuid(userUuid);
		client.setUpdatedByUserUuid(userUuid);

		String sql = """
				INSERT INTO clients (
				  uuid, client_code, client_name, business_name, mobile, alternate_mobile, email,
				  gstin, pan_number, billing_address, shipping_address,
				  client_type, price_category, credit_limit, payment_terms, opening_balance, balance_type,
				  is_active, notes, state, sync_status, sync_version, is_deleted, deleted_at,
				  created_by_user_uuid, updated_by_user_uuid
				) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
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
			ps.setInt(i++, client.isActive() ? 1 : 1);
			ps.setString(i++, nz(client.getNotes()));
			ps.setString(i++, nz(client.getState()));
			ps.setString(i++, nz(client.getSyncStatus()));
			ps.setInt(i++, client.getSyncVersion());
			ps.setInt(i++, 0);
			ps.setNull(i++, Types.VARCHAR);
			ps.setString(i++, nz(client.getCreatedByUserUuid()));
			ps.setString(i++, nz(client.getUpdatedByUserUuid()));
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
		model.User current = utils.SessionManager.getInstance().getCurrentUser();
		boolean isAdmin = current != null && current.getRole() != null && "ADMIN".equalsIgnoreCase(current.getRole());

		List<Client> list = new ArrayList<>();
		String sql = isAdmin ? """
				SELECT * FROM clients
				ORDER BY created_at ASC, uuid ASC
				""" : """
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
		model.User current = utils.SessionManager.getInstance().getCurrentUser();
		boolean isAdmin = current != null && current.getRole() != null && "ADMIN".equalsIgnoreCase(current.getRole());

		List<Client> list = new ArrayList<>();
		String sql = isAdmin ? """
				SELECT * FROM clients WHERE (
				  uuid LIKE ? OR client_code LIKE ? OR business_name LIKE ? OR client_name LIKE ?
				  OR mobile LIKE ? OR alternate_mobile LIKE ? OR email LIKE ? OR gstin LIKE ?
				  OR pan_number LIKE ? OR billing_address LIKE ? OR shipping_address LIKE ?
				) ORDER BY business_name ASC
				""" : """
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

	public boolean reviveByUuid(String clientUuid) {
		if (clientUuid == null || clientUuid.isBlank()) {
			return false;
		}

		String sql = """
				UPDATE clients SET is_deleted=0, is_active=1, deleted_at=NULL,
				sync_status='PENDING', updated_at=datetime('now') WHERE uuid=?
				""";

		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, clientUuid.trim());
			boolean ok = ps.executeUpdate() > 0;
			if (ok) {
				Client local = findByUuid(clientUuid.trim());
				if (local != null) {
					pushClientToSupabaseAsync(local, true, null);
				}
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
				WHERE UPPER(TRIM(COALESCE(sync_status, ''))) IN ('', 'PENDING', 'WAITING_DEPENDENCY')
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
		String userUuid = null;
		if (utils.SessionManager.getInstance().getCurrentUser() != null) {
			userUuid = utils.SessionManager.getInstance().getCurrentUser().getUuid();
		}
		client.setUpdatedByUserUuid(userUuid);

		String sql = """
				UPDATE clients SET client_name=?, business_name=?, mobile=?, alternate_mobile=?,
				email=?, gstin=?, pan_number=?, billing_address=?, shipping_address=?,
				client_type=?, price_category=?, credit_limit=?, payment_terms=?, opening_balance=?, balance_type=?,
				notes=?, state=?, sync_status='PENDING', sync_version=?, updated_at=datetime('now'),
				updated_by_user_uuid=? WHERE uuid=?
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
			ps.setString(i++, nz(client.getState()));
			ps.setInt(i++, client.getSyncVersion() + 1);
			ps.setString(i++, nz(client.getUpdatedByUserUuid()));
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

	public boolean duplicateGstinExists(String gstin, String excludeUuid) {
		if (gstin == null || gstin.trim().isBlank()) {
			return false;
		}
		String sql = "SELECT 1 FROM clients WHERE gstin = ? AND uuid <> ? AND IFNULL(is_deleted,0)=0 LIMIT 1";
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, gstin.trim());
			ps.setString(2, excludeUuid != null ? excludeUuid.trim() : "");
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean duplicateMobileExists(String mobile, String excludeUuid) {
		if (mobile == null || mobile.trim().isBlank()) {
			return false;
		}
		String sql = "SELECT 1 FROM clients WHERE mobile = ? AND uuid <> ? AND IFNULL(is_deleted,0)=0 LIMIT 1";
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, mobile.trim());
			ps.setString(2, excludeUuid != null ? excludeUuid.trim() : "");
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
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
		System.out.println("[PUSH] pushClientToSupabaseAsync called for client UUID " + client.getClientUuid());
		SupabaseGate.restClientIfConfigured().ifPresent(http -> CompletableFuture.runAsync(() -> {
			try {
				try (Connection conn = DBConnection.getConnection()) {
					List<String> colsList = SyncConflictResolver.getColumns(conn, "clients");
					if (SyncConflictResolver.checkPushConflictAndResolve(conn, http, "clients", SupabaseEndpoints.CLIENTS, client.getClientUuid(), client.getUpdatedAt(), colsList)) {
						System.out.println("[PUSH] Async push skipped because of conflict resolver");
						return; // Skip push: remote was newer, conflict was logged and resolved
					}
				} catch (Exception e) {
					System.err.println("[ClientRepository] Push conflict check failed: " + e.getMessage());
				}

				// RACE CONDITION GUARD: Re-read sync_status before pushing.
				// If the UniversalSyncEngine conflict resolver ran on another thread
				// and already marked this record SYNCED, abort this push.
				try (Connection checkConn = DBConnection.getConnection();
						PreparedStatement checkPs = checkConn.prepareStatement(
								"SELECT sync_status FROM clients WHERE uuid=?")) {
					checkPs.setString(1, client.getClientUuid());
					try (java.sql.ResultSet checkRs = checkPs.executeQuery()) {
						if (checkRs.next()) {
							String currentStatus = checkRs.getString(1);
							boolean stillPending = "PENDING".equalsIgnoreCase(currentStatus)
									|| "WAITING_DEPENDENCY".equalsIgnoreCase(currentStatus)
									|| (currentStatus == null || currentStatus.isBlank());
							if (!stillPending) {
								System.out.println("[PUSH] Async push aborted - record " + client.getClientUuid() + " is no longer PENDING (status=" + currentStatus + "). Conflict resolved on another thread.");
								return;
							}
						}
					}
				} catch (Exception staleCheckEx) {
					System.err.println("[ClientRepository] Pre-push stale check failed: " + staleCheckEx.getMessage());
				}

				System.out.println("[PUSH] Async dependency check PASSED. Executing POST/PATCH /clients for " + client.getClientUuid());
				ClientsSupabaseApi api = new ClientsSupabaseApi(http);
				if (preferPatch) {
					api.patchUpdate(client, before);
				} else {
					api.upsert(client);
				}
				System.out.println("[PUSH] Async push HTTP Success");
				markClientSyncedLocally(client.getClientUuid());
			} catch (Exception ex) {
				System.err.println("[Supabase clients] remote write failed for uuid=" + client.getClientUuid() + ": "
						+ ex.getMessage());
				ex.printStackTrace();
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
				Client local = new ClientRepository().findByUuid(clientUuid);
				if (local != null) {
					local.setIsDeleted(true);
					local.setIsActive(false);
					if (local.getDeletedAt() == null || local.getDeletedAt().isBlank()) {
						local.setDeletedAt(java.time.Instant.now().toString());
					}
					new ClientsSupabaseApi(http).patchUpdate(local, null);
				}
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
