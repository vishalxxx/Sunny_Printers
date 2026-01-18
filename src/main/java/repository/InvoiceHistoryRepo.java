package repository;

import model.InvoiceHistoryRow;
import utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class InvoiceHistoryRepo {

    // ✅ INSERT
    public void insertHistory(
            String invoiceNo,
            int clientId,
            String clientName,
            String invoiceDate,
            double amount,
            String type,
            String status,
            String filePath
    ) {

        String sql = """
            INSERT INTO invoice_history
            (invoice_no, client_id, client_name, invoice_date, amount, type, status, file_path)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, invoiceNo);
            ps.setInt(2, clientId);
            ps.setString(3, clientName);
            ps.setString(4, invoiceDate); // yyyy-mm-dd
            ps.setDouble(5, amount);
            ps.setString(6, type);
            ps.setString(7, status);
            ps.setString(8, filePath);

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Failed to insert invoice history", e);
        }
    }

    // ✅ GET RECENT
    public List<InvoiceHistoryRow> getRecentHistory(int limit) {

        List<InvoiceHistoryRow> list = new ArrayList<>();

        String sql = """
            SELECT invoice_no, client_name, invoice_date, amount, type, status
            FROM invoice_history
            ORDER BY created_at DESC
            LIMIT ?
        """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, limit);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(new InvoiceHistoryRow(
                        rs.getString("invoice_no"),
                        rs.getString("client_name"),
                        rs.getString("invoice_date"),
                        rs.getDouble("amount"),
                        rs.getString("type"),
                        rs.getString("status")
                ));
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load invoice history", e);
        }

        return list;
    }
}
