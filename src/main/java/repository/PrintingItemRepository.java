package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import model.Printing;
import utils.DBConnection;

public class PrintingItemRepository {

    /* ================= INSERT ================= */

    public void save(Connection con, int jobItemId, Printing p) {

        String sql = """
            INSERT INTO printing_items
            (job_item_id, qty, units, color, side, with_ctp, notes, amount)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, jobItemId);
            ps.setInt(2, p.getQty());
            ps.setString(3, p.getUnits());
            ps.setString(4, p.getColor());
            ps.setString(5, p.getSide());
            ps.setInt(6, p.isWithCtp() ? 1 : 0);
            ps.setString(7, p.getNotes());
            ps.setDouble(8, p.getAmount());

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Failed to save Printing item", e);
        }
    }

    /* ================= FETCH ================= */

    public Printing findByJobItemId(int jobItemId) {

        String sql = """
            SELECT qty, units, color, side, with_ctp, notes, amount
            FROM printing_items
            WHERE job_item_id = ?
        """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, jobItemId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Printing p = new Printing();
                    p.setJobItemId(jobItemId);
                    p.setQty(rs.getInt("qty"));
                    p.setUnits(rs.getString("units"));
                    p.setColor(rs.getString("color"));
                    p.setSide(rs.getString("side"));
                    p.setWithCtp(rs.getInt("with_ctp") == 1);
                    p.setNotes(rs.getString("notes"));
                    p.setAmount(rs.getDouble("amount"));
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
                notes = ?, amount = ?
            WHERE job_item_id = ?
        """;

        try (
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, p.getQty());
            ps.setString(2, p.getUnits());
            ps.setString(3, p.getColor());
            ps.setString(4, p.getSide());
            ps.setInt(5, p.isWithCtp() ? 1 : 0);
            ps.setString(6, p.getNotes());
            ps.setDouble(7, p.getAmount());
            ps.setInt(8, p.getJobItemId());

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Failed to update Printing item", e);
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
            throw new RuntimeException("Failed to delete Printing item", e);
        }
    }
}
