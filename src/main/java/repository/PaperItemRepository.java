package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import model.Paper;
import utils.DBConnection;

public class PaperItemRepository {

    /* =====================================================
       INSERT (uses existing transaction)
       ===================================================== */
    public void save(Connection con, String jobItemUuid, Paper p) {
        String uuid = (p.getUuid() == null || p.getUuid().isBlank()) 
                      ? utils.ClientIdentifiers.newUuidString() 
                      : p.getUuid();
        p.setUuid(uuid);

        String sql = """
            INSERT INTO paper_items
            (uuid, job_item_uuid, qty, units, size, gsm, type, source, supplier_uuid, notes, amount, sync_status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', datetime('now'), datetime('now'))
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, jobItemUuid);
            ps.setInt(3, p.getQty());
            ps.setString(4, p.getUnits());
            ps.setString(5, p.getSize());
            ps.setString(6, p.getGsm());
            ps.setString(7, p.getType());
            ps.setString(8, p.getSource());
            ps.setString(9, p.getSupplierUuid());
            ps.setString(10, p.getNotes());
            ps.setDouble(11, p.getAmount());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save paper item", e);
        }
    }

    /* =====================================================
       READ (safe, standalone)
       ===================================================== */
    public Paper findByJobItemUuid(String jobItemUuid) {
        String sql = """
            SELECT uuid, job_item_uuid, qty, units, size, gsm, type, source, supplier_uuid, notes, amount, sync_status, sync_version, created_at, updated_at
            FROM paper_items
            WHERE job_item_uuid = ? AND COALESCE(is_deleted, 0) = 0
        """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, jobItemUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Paper p = new Paper();
                    p.setUuid(rs.getString("uuid"));
                    p.setJobItemUuid(rs.getString("job_item_uuid"));
                    p.setQty(rs.getInt("qty"));
                    p.setUnits(rs.getString("units"));
                    p.setSize(rs.getString("size"));
                    p.setGsm(rs.getString("gsm"));
                    p.setType(rs.getString("type"));
                    p.setSource(rs.getString("source"));
                    p.setSupplierUuid(rs.getString("supplier_uuid"));
                    p.setNotes(rs.getString("notes"));
                    p.setAmount(rs.getDouble("amount"));
                    p.setSyncStatus(rs.getString("sync_status"));
                    p.setSyncVersion(rs.getInt("sync_version"));
                    p.setCreatedAt(rs.getString("created_at"));
                    p.setUpdatedAt(rs.getString("updated_at"));
                    return p;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load paper item for jobItemUuid=" + jobItemUuid, e);
        }
        return null;
    }

    /* =====================================================
       UPDATE (uses existing transaction)
       ===================================================== */
    public void update(Connection con, Paper p) {
        String sql = """
            UPDATE paper_items
            SET qty = ?, units = ?, size = ?, gsm = ?, type = ?,
                source = ?, supplier_uuid = ?, notes = ?, amount = ?, updated_at = datetime('now'), sync_status = 'PENDING'
            WHERE job_item_uuid = ?
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, p.getQty());
            ps.setString(2, p.getUnits());
            ps.setString(3, p.getSize());
            ps.setString(4, p.getGsm());
            ps.setString(5, p.getType());
            ps.setString(6, p.getSource());
            ps.setString(7, p.getSupplierUuid());
            ps.setString(8, p.getNotes());
            ps.setDouble(9, p.getAmount());
            ps.setString(10, p.getJobItemUuid());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update paper item", e);
        }
    }

    /* =====================================================
       DELETE (standalone)
       ===================================================== */
    public void deleteByJobItemUuid(Connection con, String jobItemUuid) {
        model.User current = utils.SessionManager.getInstance().getCurrentUser();
        boolean isAdmin = current != null && current.getRole() != null && "ADMIN".equalsIgnoreCase(current.getRole());

        String sql;
        if (isAdmin) {
            sql = "DELETE FROM paper_items WHERE job_item_uuid = ?";
        } else {
            sql = "UPDATE paper_items SET is_deleted = 1, updated_at = datetime('now'), sync_status = 'PENDING' WHERE job_item_uuid = ?";
        }

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, jobItemUuid);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete paper item", e);
        }
    }
}
