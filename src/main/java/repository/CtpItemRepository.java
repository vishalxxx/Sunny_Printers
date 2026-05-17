package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import model.CtpPlate;
import utils.DBConnection;

public class CtpItemRepository {

    /* ================= INSERT ================= */

    public void save(Connection con, String jobItemUuid, CtpPlate c) {
        String uuid = (c.getUuid() == null || c.getUuid().isBlank()) 
                      ? utils.ClientIdentifiers.newUuidString() 
                      : c.getUuid();
        c.setUuid(uuid);

        String sql = """
            INSERT INTO ctp_items
            (uuid, job_item_uuid, qty, plate_size, gauge, backing,
             color, supplier_uuid, supplier_name, notes, amount, sync_status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', datetime('now'), datetime('now'))
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, jobItemUuid);
            ps.setInt(3, c.getQty());
            ps.setString(4, c.getPlateSize());
            ps.setString(5, c.getGauge());
            ps.setString(6, c.getBacking());
            ps.setString(7, c.getColor());
            ps.setString(8, c.getSupplierUuid());
            ps.setString(9, c.getSupplierName());
            ps.setString(10, c.getNotes());
            ps.setDouble(11, c.getAmount());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save CTP item", e);
        }
    }

    /* ================= FETCH ================= */

    public CtpPlate findByJobItemUuid(String jobItemUuid) {
        String sql = """
            SELECT uuid, job_item_uuid, qty, plate_size, gauge, backing, color, supplier_uuid, supplier_name, notes, amount, sync_status, sync_version, created_at, updated_at
            FROM ctp_items
            WHERE job_item_uuid = ? AND COALESCE(is_deleted, 0) = 0
        """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, jobItemUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    CtpPlate c = new CtpPlate();
                    c.setUuid(rs.getString("uuid"));
                    c.setJobItemUuid(rs.getString("job_item_uuid"));
                    c.setQty(rs.getInt("qty"));
                    c.setPlateSize(rs.getString("plate_size"));
                    c.setGauge(rs.getString("gauge"));
                    c.setBacking(rs.getString("backing"));
                    c.setColor(rs.getString("color"));
                    c.setSupplierUuid(rs.getString("supplier_uuid"));
                    c.setSupplierName(rs.getString("supplier_name"));
                    c.setNotes(rs.getString("notes"));
                    c.setAmount(rs.getDouble("amount"));
                    c.setSyncStatus(rs.getString("sync_status"));
                    c.setSyncVersion(rs.getInt("sync_version"));
                    c.setCreatedAt(rs.getString("created_at"));
                    c.setUpdatedAt(rs.getString("updated_at"));
                    return c;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load CTP item", e);
        }
        return null;
    }

    /* ================= UPDATE ================= */

    public void update(Connection con, CtpPlate c) {
        String sql = """
            UPDATE ctp_items
            SET qty=?, plate_size=?, gauge=?, backing=?,
                color=?, supplier_uuid=?, supplier_name=?,
                notes=?, amount=?, updated_at=datetime('now'), sync_status='PENDING'
            WHERE job_item_uuid=?
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, c.getQty());
            ps.setString(2, c.getPlateSize());
            ps.setString(3, c.getGauge());
            ps.setString(4, c.getBacking());
            ps.setString(5, c.getColor());
            ps.setString(6, c.getSupplierUuid());
            ps.setString(7, c.getSupplierName());
            ps.setString(8, c.getNotes());
            ps.setDouble(9, c.getAmount());
            ps.setString(10, c.getJobItemUuid());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update CTP item", e);
        }
    }

    /* ================= DELETE ================= */

    public void deleteByJobItemUuid(Connection con, String jobItemUuid) {
        String sql = "UPDATE ctp_items SET is_deleted = 1, updated_at = datetime('now'), sync_status = 'PENDING' WHERE job_item_uuid = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, jobItemUuid);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete CTP item", e);
        }
    }
}
