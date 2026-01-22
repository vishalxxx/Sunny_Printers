package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;

import model.Binding;

public class BindingItemRepository {

    public void save(Connection con, int jobItemId, Binding b) {

        String sql = """
            INSERT INTO binding_items
            (job_item_id, process, qty, rate, notes, amount)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, jobItemId);
            ps.setString(2, b.getProcess());
            ps.setInt(3, b.getQty());
            ps.setDouble(4, b.getRate());
            ps.setString(5, b.getNotes());
            ps.setDouble(6, b.getAmount());

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Failed to save binding item", e);
        }
    }
}
