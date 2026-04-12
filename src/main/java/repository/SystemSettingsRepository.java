package repository;

import java.sql.*;

import model.SystemSettings;

public class SystemSettingsRepository {

    // ================= UPSERT =================
    private static final String UPSERT = """
        INSERT INTO system_settings
        (id, invoice_mode, invoice_prefix, invoice_start_no,
         invoice_padding, last_invoice_no, last_job_no,
         job_prefix, job_start_no, job_padding, last_temp_invoice_no)
        VALUES (1, 'MANUAL', ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
            invoice_prefix = excluded.invoice_prefix,
            invoice_start_no = excluded.invoice_start_no,
            invoice_padding = excluded.invoice_padding,
            last_invoice_no = excluded.last_invoice_no,
            last_job_no = excluded.last_job_no,
            job_prefix = excluded.job_prefix,
            job_start_no = excluded.job_start_no,
            job_padding = excluded.job_padding,
            last_temp_invoice_no = excluded.last_temp_invoice_no,
            updated_at = CURRENT_TIMESTAMP
        """;

    // ================= SELECT =================
    private static final String SELECT = """
        SELECT invoice_mode,
               invoice_prefix,
               invoice_start_no,
               invoice_padding,
               last_invoice_no,
               last_job_no,
               job_prefix,
               job_start_no,
               job_padding,
               last_temp_invoice_no
        FROM system_settings
        WHERE id = 1
        """;

    // =========================================================
    // LOAD → uses SAME connection (atomic safe)
    // =========================================================
    public SystemSettings load(Connection con) throws Exception {

        try (PreparedStatement ps = con.prepareStatement(SELECT);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                SystemSettings s = new SystemSettings();
                s.setInvoiceMode(rs.getString("invoice_mode"));
                s.setInvoicePrefix(rs.getString("invoice_prefix"));
                s.setInvoiceStartNo(rs.getInt("invoice_start_no"));
                s.setInvoicePadding(rs.getInt("invoice_padding"));
                s.setLastInvoiceNo(rs.getInt("last_invoice_no"));
                s.setLastJobNo(rs.getInt("last_job_no"));
                
                s.setJobPrefix(rs.getString("job_prefix"));
                s.setJobStartNo(rs.getInt("job_start_no"));
                s.setJobPadding(rs.getInt("job_padding"));
                s.setLastTempInvoiceNo(rs.getInt("last_temp_invoice_no"));
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
        def.setLastJobNo(0);
        def.setJobPrefix("SUN-");
        def.setJobStartNo(1);
        def.setJobPadding(4);
        def.setLastTempInvoiceNo(0);
        return def;
    }

    // =========================================================
    // SAVE → uses SAME connection (atomic safe)
    // =========================================================
    public void save(Connection con, SystemSettings s) throws Exception {

        try (PreparedStatement ps = con.prepareStatement(UPSERT)) {

            ps.setString(1, s.getInvoicePrefix());
            ps.setInt(2, s.getInvoiceStartNo());
            ps.setInt(3, s.getInvoicePadding());
            ps.setInt(4, s.getLastInvoiceNo());
            ps.setInt(5, s.getLastJobNo());
            
            ps.setString(6, s.getJobPrefix() == null ? "SUN-" : s.getJobPrefix());
            ps.setInt(7, s.getJobStartNo());
            ps.setInt(8, s.getJobPadding());
            ps.setInt(9, s.getLastTempInvoiceNo());

            ps.executeUpdate();
        }
    }
}
