package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import model.Lamination;
import utils.DBConnection;

public class LaminationItemRepository {

    /* ================= INSERT ================= */

    public void save(Connection con, int jobItemId, Lamination l) {

        String sql = """
            INSERT INTO lamination_items
            (job_item_id, qty, unit, type, side, size, notes, amount)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, jobItemId);
            ps.setInt(2, l.getQty());
            ps.setString(3, l.getUnit());
            ps.setString(4, l.getType());
            ps.setString(5, l.getSide());
            ps.setString(6, l.getSize());
            ps.setString(7, l.getNotes());
            ps.setDouble(8, l.getAmount());

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Failed to save Lamination item", e);
        }
    }

    /* ================= FETCH ================= */

    public Lamination findByJobItemId(int jobItemId) {

        String sql = """
            SELECT qty, unit, type, side, size, notes, amount
            FROM lamination_items
            WHERE job_item_id = ?
        """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, jobItemId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Lamination l = new Lamination();
                    l.setJobItemId(jobItemId);
                    l.setQty(rs.getInt("qty"));
                    l.setUnit(rs.getString("unit"));
                    l.setType(rs.getString("type"));
                    l.setSide(rs.getString("side"));
                    l.setSize(rs.getString("size"));
                    l.setNotes(rs.getString("notes"));
                    l.setAmount(rs.getDouble("amount"));
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
                notes = ?, amount = ?
            WHERE job_item_id = ?
        """;

        try (
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, l.getQty());
            ps.setString(2, l.getUnit());
            ps.setString(3, l.getType());
            ps.setString(4, l.getSide());
            ps.setString(5, l.getSize());
            ps.setString(6, l.getNotes());
            ps.setDouble(7, l.getAmount());
            ps.setInt(8, l.getJobItemId());

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Failed to update Lamination item", e);
        }
    }

    /* ================= DELETE ================= */

    public void deleteByJobItemId(Connection con, int jobItemId) {

        String sql = "DELETE FROM job_items WHERE id = ?";

        try (
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, jobItemId);
            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Failed to delete Lamination item", e);
        }
    }
}
