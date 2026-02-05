package repository;

import java.sql.*;

import model.SystemSettings;
import utils.DBConnection;

public class SystemSettingsRepository {

    // ================= UPSERT =================
    private static final String UPSERT = """
        INSERT INTO system_settings
        (id, invoice_mode, invoice_prefix, invoice_start_no,
         invoice_padding, last_invoice_no)
        VALUES (1, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
            invoice_mode = excluded.invoice_mode,
            invoice_prefix = excluded.invoice_prefix,
            invoice_start_no = excluded.invoice_start_no,
            invoice_padding = excluded.invoice_padding,
            last_invoice_no = excluded.last_invoice_no,
            updated_at = CURRENT_TIMESTAMP
        """;

    // ================= SELECT =================
    private static final String SELECT = """
        SELECT invoice_mode,
               invoice_prefix,
               invoice_start_no,
               invoice_padding,
               last_invoice_no
        FROM system_settings
        WHERE id = 1
        """;

    // ================= LOAD =================
    public SystemSettings load() throws Exception {

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                SystemSettings s = new SystemSettings();
                s.setInvoiceMode(rs.getString("invoice_mode"));
                s.setInvoicePrefix(rs.getString("invoice_prefix"));
                s.setInvoiceStartNo(rs.getInt("invoice_start_no"));
                s.setInvoicePadding(rs.getInt("invoice_padding"));
                s.setLastInvoiceNo(rs.getInt("last_invoice_no"));
                return s;
            }
        }

        // ===== fallback default =====
        SystemSettings def = new SystemSettings();
        def.setInvoiceMode("AUTO");
        def.setInvoicePrefix("INV-");
        def.setInvoiceStartNo(1);
        def.setInvoicePadding(3);
        def.setLastInvoiceNo(0);
        return def;
    }

    // ================= SAVE =================
    public void save(SystemSettings s) throws Exception {

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(UPSERT)) {

            ps.setString(1, s.getInvoiceMode());
            ps.setString(2, s.getInvoicePrefix());
            ps.setInt(3, s.getInvoiceStartNo());
            ps.setInt(4, s.getInvoicePadding());
            ps.setInt(5, s.getLastInvoiceNo());

            ps.executeUpdate();
        }
    }
}
