package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import model.Printing;
import utils.DBConnection;

public class PrintingItemRepository {

    /* ================= INSERT ================= */

    public void save(Connection con, String jobItemUuid, Printing p) {
        String uuid = (p.getUuid() == null || p.getUuid().isBlank()) 
                      ? utils.ClientIdentifiers.newUuidString() 
                      : p.getUuid();
        p.setUuid(uuid);

        String sql = """
            INSERT INTO printing_items
            (uuid, job_item_uuid, qty, units, color, side, with_ctp, notes, amount, sets, sync_status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', datetime('now'), datetime('now'))
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, jobItemUuid);
            ps.setInt(3, p.getQty());
            ps.setString(4, p.getUnits());
            ps.setString(5, p.getColor());
            ps.setString(6, p.getSide());
            ps.setInt(7, p.isWithCtp() ? 1 : 0);
            ps.setString(8, p.getNotes());
            ps.setDouble(9, p.getAmount());
            ps.setString(10, p.getSets());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save Printing item", e);
        }
    }

    /* ================= FETCH ================= */

    public Printing findByJobItemUuid(String jobItemUuid) {
        String sql = """
            SELECT uuid, job_item_uuid, qty, units, color, side, with_ctp, notes, amount, sets, sync_status, sync_version, created_at, updated_at
            FROM printing_items
            WHERE job_item_uuid = ? AND COALESCE(is_deleted, 0) = 0
        """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, jobItemUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Printing p = new Printing();
                    p.setUuid(rs.getString("uuid"));
                    p.setJobItemUuid(rs.getString("job_item_uuid"));
                    p.setQty(rs.getInt("qty"));
                    p.setUnits(rs.getString("units"));
                    p.setColor(rs.getString("color"));
                    p.setSide(rs.getString("side"));
                    p.setWithCtp(rs.getInt("with_ctp") == 1);
                    p.setNotes(rs.getString("notes"));
                    p.setAmount(rs.getDouble("amount"));
                    p.setSets(rs.getString("sets"));
                    p.setSyncStatus(rs.getString("sync_status"));
                    p.setSyncVersion(rs.getInt("sync_version"));
                    p.setCreatedAt(rs.getString("created_at"));
                    p.setUpdatedAt(rs.getString("updated_at"));
                    return p;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch Printing item", e);
        }
        return null;
    }

    /* ================= UPDATE ================= */

    public void update(Connection con, Printing p) {
        String sql = """
            UPDATE printing_items
            SET qty = ?, units = ?, color = ?, side = ?, with_ctp = ?,
                notes = ?, amount = ?, sets = ?, updated_at = datetime('now'), sync_status = 'PENDING'
            WHERE uuid = ?
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, p.getQty());
            ps.setString(2, p.getUnits());
            ps.setString(3, p.getColor());
            ps.setString(4, p.getSide());
            ps.setInt(5, p.isWithCtp() ? 1 : 0);
            ps.setString(6, p.getNotes());
            ps.setDouble(7, p.getAmount());
            ps.setString(8, p.getSets());
            ps.setString(9, p.getUuid());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update Printing item", e);
        }
    }

    /* ================= DELETE ================= */

    public void deleteByJobItemUuid(Connection con, String jobItemUuid) {
        String sql = "UPDATE printing_items SET is_deleted = 1, updated_at = datetime('now'), sync_status = 'PENDING' WHERE job_item_uuid = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, jobItemUuid);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete Printing item", e);
        }
    }
}
