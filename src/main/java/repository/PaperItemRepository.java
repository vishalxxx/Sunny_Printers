package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;

import model.Paper;

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
}
