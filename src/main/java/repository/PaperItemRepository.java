package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import model.Paper;
import utils.DBConnection;

public class PaperItemRepository {

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
    
    public Paper findByJobItemId(int jobItemId) {

        String sql = """
            SELECT qty, units, size, gsm, type, source, notes, amount
            FROM paper_items
            WHERE job_item_id = ?
        """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, jobItemId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                Paper p = new Paper();
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
                "Failed to fetch paper item for jobItemId=" + jobItemId, e
            );
        }

        return null;
    }

}
