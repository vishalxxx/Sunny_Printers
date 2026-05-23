package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import model.Lamination;
import utils.DBConnection;

public class LaminationItemRepository {

    /* ================= INSERT ================= */

    public void save(Connection con, String jobItemUuid, Lamination l) {
        String uuid = (l.getUuid() == null || l.getUuid().isBlank()) 
                      ? utils.ClientIdentifiers.newUuidString() 
                      : l.getUuid();
        l.setUuid(uuid);

        String sql = """
            INSERT INTO lamination_items
            (uuid, job_item_uuid, qty, unit, type, side, size, notes, amount, sync_status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', datetime('now'), datetime('now'))
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, jobItemUuid);
            ps.setInt(3, l.getQty());
            ps.setString(4, l.getUnit());
            ps.setString(5, l.getType());
            ps.setString(6, l.getSide());
            ps.setString(7, l.getSize());
            ps.setString(8, l.getNotes());
            ps.setDouble(9, l.getAmount());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save Lamination item", e);
        }
    }

    /* ================= FETCH ================= */

    public Lamination findByJobItemUuid(String jobItemUuid) {
        String sql = """
            SELECT uuid, job_item_uuid, qty, unit, type, side, size, notes, amount, sync_status, sync_version, created_at, updated_at
            FROM lamination_items
            WHERE job_item_uuid = ? AND COALESCE(is_deleted, 0) = 0
        """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, jobItemUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Lamination l = new Lamination();
                    l.setUuid(rs.getString("uuid"));
                    l.setJobItemUuid(rs.getString("job_item_uuid"));
                    l.setQty(rs.getInt("qty"));
                    l.setUnit(rs.getString("unit"));
                    l.setType(rs.getString("type"));
                    l.setSide(rs.getString("side"));
                    l.setSize(rs.getString("size"));
                    l.setNotes(rs.getString("notes"));
                    l.setAmount(rs.getDouble("amount"));
                    l.setSyncStatus(rs.getString("sync_status"));
                    l.setSyncVersion(rs.getInt("sync_version"));
                    l.setCreatedAt(rs.getString("created_at"));
                    l.setUpdatedAt(rs.getString("updated_at"));
                    return l;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch Lamination item", e);
        }
        return null;
    }

    /* ================= UPDATE ================= */

    public void update(Connection con, Lamination l) {
        String sql = """
            UPDATE lamination_items
            SET qty = ?, unit = ?, type = ?, side = ?, size = ?,
                notes = ?, amount = ?, updated_at = datetime('now'), sync_status = 'PENDING'
            WHERE job_item_uuid = ?
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, l.getQty());
            ps.setString(2, l.getUnit());
            ps.setString(3, l.getType());
            ps.setString(4, l.getSide());
            ps.setString(5, l.getSize());
            ps.setString(6, l.getNotes());
            ps.setDouble(7, l.getAmount());
            ps.setString(8, l.getJobItemUuid());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update Lamination item", e);
        }
    }

    /* ================= DELETE ================= */

    public void deleteByJobItemUuid(Connection con, String jobItemUuid) {
        model.User current = utils.SessionManager.getInstance().getCurrentUser();
        boolean isAdmin = current != null && current.getRole() != null && "ADMIN".equalsIgnoreCase(current.getRole());

        String sql;
        if (isAdmin) {
            sql = "DELETE FROM lamination_items WHERE job_item_uuid = ?";
        } else {
            sql = "UPDATE lamination_items SET is_deleted = 1, updated_at = datetime('now'), sync_status = 'PENDING' WHERE job_item_uuid = ?";
        }

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, jobItemUuid);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete Lamination item", e);
        }
    }
}
