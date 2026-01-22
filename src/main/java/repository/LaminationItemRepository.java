package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;

import model.Lamination;

public class LaminationItemRepository {

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
            throw new RuntimeException("Failed to save lamination item", e);
        }
    }
}
