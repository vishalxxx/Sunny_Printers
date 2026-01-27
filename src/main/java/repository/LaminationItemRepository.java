package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;


import model.Lamination;
import utils.DBConnection;

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
    
    public Lamination findByJobItemId(int jobItemId) {

        String sql = """
            SELECT *
            FROM lamination_items
            WHERE job_item_id = ?
        """;

        try (
            Connection con = DBConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql)
        ) {

            ps.setInt(1, jobItemId);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                Lamination l = new Lamination();

                l.setId(rs.getInt("id"));
                l.setJobItemId(rs.getInt("job_item_id"));

                l.setQty(rs.getInt("qty"));
                l.setUnit(rs.getString("unit"));
                l.setType(rs.getString("type"));
                l.setSide(rs.getString("side"));
                l.setSize(rs.getString("size"));

                l.setNotes(rs.getString("notes"));
                l.setAmount(rs.getDouble("amount"));

                return l;
            }

            return null;

        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to fetch lamination item for job_item_id=" + jobItemId,
                e
            );
        }
    }
    
}
