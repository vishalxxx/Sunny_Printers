package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;

import model.CtpPlate;

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
}
