package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import model.Payment;
import service.sync.PendingSyncFilters;
import utils.DBConnection;

public class PaymentRepository {

	public List<Payment> findPendingForSync() {
		List<Payment> list = new ArrayList<>();
		String sql = """
				SELECT uuid, client_uuid, amount, payment_date, method, type,
				       sync_status, sync_version, is_deleted, is_active, created_at, updated_at
				FROM payments
				WHERE %s AND %s
				ORDER BY created_at ASC
				""".formatted(PendingSyncFilters.NOT_DELETED, PendingSyncFilters.PENDING_STATUS);
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(mapRow(rs));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return list;
	}

	private static Payment mapRow(ResultSet rs) throws Exception {
		Payment p = new Payment();
		p.setUuid(rs.getString("uuid"));
		p.setClientUuid(rs.getString("client_uuid"));
		p.setAmount(rs.getDouble("amount"));
		p.setPaymentDate(rs.getString("payment_date"));
		p.setMethod(rs.getString("method"));
		p.setType(rs.getString("type"));
		p.setSyncStatus(rs.getString("sync_status"));
		p.setSyncVersion(rs.getInt("sync_version"));
		p.setIsDeleted(rs.getInt("is_deleted"));
		p.setIsActive(rs.getInt("is_active"));
		p.setCreatedAt(rs.getString("created_at"));
		p.setUpdatedAt(rs.getString("updated_at"));
		return p;
	}
}
