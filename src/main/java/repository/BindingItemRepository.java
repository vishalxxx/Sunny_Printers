package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import model.Binding;
import utils.DBConnection;

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
    
    public Binding findByJobItemId(int jobItemId) {

        String sql = """
            SELECT id, job_item_id, process, qty, rate, notes, amount
            FROM binding_items
            WHERE job_item_id = ?
        """;

        try (
            Connection con = DBConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql)
        ) {
            ps.setInt(1, jobItemId);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Binding b = new Binding();
                b.setId(rs.getInt("id"));
                b.setJobItemId(rs.getInt("job_item_id"));
                b.setProcess(rs.getString("process"));
                b.setQty(rs.getInt("qty"));
                b.setRate(rs.getDouble("rate"));
                b.setNotes(rs.getString("notes"));
                b.setAmount(rs.getDouble("amount"));
                return b;
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load binding item", e);
        }
        return null;
    }
}
