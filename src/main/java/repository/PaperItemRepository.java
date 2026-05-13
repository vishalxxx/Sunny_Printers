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
    public void save(Connection con, int jobItemId, Paper p) {

        String sql = """
            INSERT INTO paper_items
            (job_item_id, qty, units, size, gsm, type, source, notes, amount)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, jobItemId);
            ps.setInt(2, p.getQty());
            ps.setString(3, p.getUnits());
            ps.setString(4, p.getSize());
            ps.setString(5, p.getGsm());
            ps.setString(6, p.getType());
            ps.setString(7, p.getSource());
            ps.setString(8, p.getNotes());
            ps.setDouble(9, p.getAmount());

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Failed to save paper item", e);
        }
    }

    /* =====================================================
       READ (safe, standalone)
       ===================================================== */
    public Paper findByJobItemId(int jobItemId) {

        String sql = """
            SELECT job_item_id, qty, units, size, gsm, type, source, notes, amount
            FROM paper_items
            WHERE job_item_id = ?
        """;

        try (
            Connection con = DBConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql)
        ) {

            ps.setInt(1, jobItemId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                Paper p = new Paper();
                p.setJobItemId(rs.getInt("job_item_id"));
                p.setQty(rs.getInt("qty"));
                p.setUnits(rs.getString("units"));
                p.setSize(rs.getString("size"));
                p.setGsm(rs.getString("gsm"));
                p.setType(rs.getString("type"));
                p.setSource(rs.getString("source"));
                p.setNotes(rs.getString("notes"));
                p.setAmount(rs.getDouble("amount"));
                return p;
            }

        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to load paper item for jobItemId=" + jobItemId, e
            );
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
                source = ?, notes = ?, amount = ?
            WHERE job_item_id = ?
        """;

        try (
        		PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, p.getQty());
            ps.setString(2, p.getUnits());
            ps.setString(3, p.getSize());
            ps.setString(4, p.getGsm());
            ps.setString(5, p.getType());
            ps.setString(6, p.getSource());
            ps.setString(7, p.getNotes());
            ps.setDouble(8, p.getAmount());
            ps.setInt(9, p.getJobItemId());

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Failed to update paper item", e);
        }
    }

    /* =====================================================
       DELETE (standalone)
       Uses CASCADE to delete paper_items automatically
       ===================================================== */
    public void deleteByJobItemId(Connection con, int jobItemId) {

        String sql = "DELETE FROM job_items WHERE id = ?";

        try (
          
            PreparedStatement ps = con.prepareStatement(sql)
        ) {

            ps.setInt(1, jobItemId);
            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Failed to delete paper item", e);
        }
    }
}
