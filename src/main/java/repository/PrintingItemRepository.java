package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import model.Printing;
import utils.DBConnection;

public class PrintingItemRepository {

    public void save(Connection con, int jobItemId, Printing p) {

        String sql = """
            INSERT INTO printing_items
            (job_item_id, qty, units, sets, color, side, with_ctp, notes, amount)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, jobItemId);
            ps.setInt(2, p.getQty());
            ps.setString(3, p.getUnits());
            ps.setString(4, p.getSets());
            ps.setString(5, p.getColor());
            ps.setString(6, p.getSide());
            ps.setInt(7, p.isWithCtp() ? 1 : 0);
            ps.setString(8, p.getNotes());
            ps.setDouble(9, p.getAmount());

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Failed to save printing item", e);
        }
    }
    
    public Printing findByJobItemId(int jobItemId) {

        String sql = """
            SELECT *
            FROM printing_items
            WHERE job_item_id = ?
        """;

        try (
            Connection con = DBConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql)
        ) {

            ps.setInt(1, jobItemId);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {

                    Printing p = new Printing();

                    p.setId(rs.getInt("id"));
                    p.setQty(rs.getInt("qty"));
                    p.setUnits(rs.getString("units"));
                    p.setSets(rs.getString("sets"));
                    p.setColor(rs.getString("color"));
                    p.setSide(rs.getString("side"));
                    p.setWithCtp(rs.getInt("with_ctp") == 1);
                    p.setNotes(rs.getString("notes"));
                    p.setAmount(rs.getDouble("amount"));

                    return p;
                }
            }

        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to fetch Printing item for jobItemId=" + jobItemId,
                e
            );
        }

        return null;
    }
    
}
