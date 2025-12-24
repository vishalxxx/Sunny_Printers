package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;

import model.CtpPlate;
import utils.DBConnection;

public class CtpItemRepository {

	public void save(CtpPlate item) {

		String sql = """
				    INSERT INTO ctp_plate
				    (job_id, qty, plate_size, gauge, backing,
				     supplier_id, supplier_name,
				     notes, amount, color)
				    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?,?)
				""";

		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setInt(1, item.getJobId());
			ps.setInt(2, item.getQty());
			ps.setString(3, item.getSize());
			ps.setString(4, item.getGauge());
			ps.setString(5, item.getBacking());
			ps.setObject(6, item.getSupplierId());
			ps.setString(7, item.getSupplierNameSnapshot());
			ps.setString(8, item.getNotes());
			ps.setDouble(9, item.getAmount());
			ps.setString(10, item.getColor());

			ps.executeUpdate();
		} catch (Exception e) {
			throw new RuntimeException("Failed to save CTP item", e);
		}
	}

}
