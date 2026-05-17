package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import model.Binding;
import utils.DBConnection;

public class BindingItemRepository {

    /* =====================================================
       INSERT (uses existing transaction)
       ===================================================== */
    public void save(Connection con, String jobItemUuid, Binding b) {
        String uuid = (b.getUuid() == null || b.getUuid().isBlank()) 
                      ? utils.ClientIdentifiers.newUuidString() 
                      : b.getUuid();
        b.setUuid(uuid);

        String sql = """
            INSERT INTO binding_items
            (uuid, job_item_uuid, process, qty, rate, notes, amount, sync_status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', datetime('now'), datetime('now'))
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, jobItemUuid);
            ps.setString(3, b.getProcess());
            ps.setInt(4, b.getQty());
            ps.setDouble(5, b.getRate());
            ps.setString(6, b.getNotes());
            ps.setDouble(7, b.getAmount());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save binding item", e);
        }
    }

    /* =====================================================
       READ (standalone)
       ===================================================== */
    public Binding findByJobItemUuid(String jobItemUuid) {
        String sql = """
            SELECT uuid, job_item_uuid, process, qty, rate, notes, amount, sync_status, sync_version, created_at, updated_at
            FROM binding_items
            WHERE job_item_uuid = ? AND COALESCE(is_deleted, 0) = 0
        """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, jobItemUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Binding b = new Binding();
                    b.setUuid(rs.getString("uuid"));
                    b.setJobItemUuid(rs.getString("job_item_uuid"));
                    b.setProcess(rs.getString("process"));
                    b.setQty(rs.getInt("qty"));
                    b.setRate(rs.getDouble("rate"));
                    b.setNotes(rs.getString("notes"));
                    b.setAmount(rs.getDouble("amount"));
                    b.setSyncStatus(rs.getString("sync_status"));
                    b.setSyncVersion(rs.getInt("sync_version"));
                    b.setCreatedAt(rs.getString("created_at"));
                    b.setUpdatedAt(rs.getString("updated_at"));
                    return b;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load binding item for jobItemUuid=" + jobItemUuid, e);
        }
        return null;
    }

    /* =====================================================
       UPDATE (standalone)
       ===================================================== */
    public void update(Connection con, Binding b) {
        String sql = """
            UPDATE binding_items
            SET process = ?, qty = ?, rate = ?, notes = ?, amount = ?, updated_at = datetime('now'), sync_status = 'PENDING'
            WHERE job_item_uuid = ?
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, b.getProcess());
            ps.setInt(2, b.getQty());
            ps.setDouble(3, b.getRate());
            ps.setString(4, b.getNotes());
            ps.setDouble(5, b.getAmount());
            ps.setString(6, b.getJobItemUuid());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update binding item", e);
        }
    }

    /* =====================================================
       DELETE (standalone)
       ===================================================== */
    public void deleteByJobItemUuid(Connection con, String jobItemUuid) {
        String sql = "UPDATE binding_items SET is_deleted = 1, updated_at = datetime('now'), sync_status = 'PENDING' WHERE job_item_uuid = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, jobItemUuid);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete binding item", e);
        }
    }
}
