package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import model.CtpPlate;
import utils.DBConnection;

public class CtpItemRepository {

    public void save(Connection con, CtpPlate ctp) {

        String sql = """
            INSERT INTO ctp_plate
            (job_item_id, supplier_id, supplier_name, qty, plate_size, gauge, backing, color, notes, amount)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, ctp.getJobItemId());
            ps.setInt(2, ctp.getSupplierId());
            ps.setString(3, ctp.getSupplierName());
            ps.setInt(4, ctp.getQty());
            ps.setString(5, ctp.getPlateSize());
            ps.setString(6, ctp.getGauge());
            ps.setString(7, ctp.getBacking());
            ps.setString(8, ctp.getColor());
            ps.setString(9, ctp.getNotes());
            ps.setDouble(10, ctp.getAmount());

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Failed to save CTP plate item", e);
        }
    }
    
    public CtpPlate findByJobItemId(int jobItemId) {

        String sql = """
            SELECT id, job_item_id, supplier_id, supplier_name, qty, plate_size, gauge, backing, color, notes, amount
            FROM ctp_items
            WHERE job_item_id = ?
        """;

        try (
            Connection con = DBConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql)
        ) {
            ps.setInt(1, jobItemId);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                CtpPlate c = new CtpPlate();
                c.setId(rs.getInt("id"));
                c.setJobItemId(rs.getInt("job_item_id"));
                c.setSupplierName( rs.getString("supplier_name")); 
                c.setSupplierId(rs.getInt("supplier_id"));
                
                c.setQty(rs.getInt("qty"));
                c.setPlateSize(rs.getString("plate_size"));
                c.setGauge(rs.getString("gauge"));
                c.setBacking(rs.getString("backing"));
                c.setColor(rs.getString("color"));
                c.setNotes(rs.getString("notes"));
                c.setAmount(rs.getDouble("amount"));
                return c;
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load CTP item", e);
        }
        return null;
    }
    
}
