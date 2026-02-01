package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import model.CtpPlate;
import utils.DBConnection;

public class CtpItemRepository {

    /* ================= INSERT ================= */

    public void save(Connection con, CtpPlate c) {

        String sql = """
            INSERT INTO ctp_items
            (job_item_id, qty, plate_size, gauge, backing,
             color, supplier_id, supplier_name, notes, amount)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, c.getJobItemId());
            ps.setInt(2, c.getQty());
            ps.setString(3, c.getPlateSize());
            ps.setString(4, c.getGauge());
            ps.setString(5, c.getBacking());
            ps.setString(6, c.getColor());
            ps.setInt(7, c.getSupplierId());
            ps.setString(8, c.getSupplierName());
            ps.setString(9, c.getNotes());
            ps.setDouble(10, c.getAmount());

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Failed to save CTP item", e);
        }
    }

    /* ================= FETCH ================= */

    public CtpPlate findByJobItemId(int jobItemId) {

        String sql = """
            SELECT *
            FROM ctp_items
            WHERE job_item_id = ?
        """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, jobItemId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                CtpPlate c = new CtpPlate();
                c.setJobItemId(jobItemId);
                c.setQty(rs.getInt("qty"));
                c.setPlateSize(rs.getString("plate_size"));
                c.setGauge(rs.getString("gauge"));
                c.setBacking(rs.getString("backing"));
                c.setColor(rs.getString("color"));
                c.setSupplierId(rs.getInt("supplier_id"));
                c.setSupplierName(rs.getString("supplier_name"));
                c.setNotes(rs.getString("notes"));
                c.setAmount(rs.getDouble("amount"));
                return c;
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
                color=?, supplier_id=?, supplier_name=?,
                notes=?, amount=?
            WHERE job_item_id=?
        """;

        try (
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, c.getQty());
            ps.setString(2, c.getPlateSize());
            ps.setString(3, c.getGauge());
            ps.setString(4, c.getBacking());
            ps.setString(5, c.getColor());
            ps.setInt(6, c.getSupplierId());
            ps.setString(7, c.getSupplierName());
            ps.setString(8, c.getNotes());
            ps.setDouble(9, c.getAmount());
            ps.setInt(10, c.getJobItemId());

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Failed to update CTP item", e);
        }
    }

    /* ================= DELETE ================= */

    public void deleteByJobItemId(Connection con, int jobItemId) {

        String sql = "DELETE FROM job_items WHERE id=?";

        try (
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, jobItemId);
            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Failed to delete CTP item", e);
        }
    }
}
