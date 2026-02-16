package repository;

import model.InvoiceMaster;
import utils.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;


public class InvoiceMasterRepository {

    // Ensure unique indexes exist to prevent duplicates (exclude voided rows so regenerate after cancel works)
    public InvoiceMasterRepository() {
        try (Connection con = DBConnection.getConnection()) {
            // Drop old indexes that didn't exclude voided rows
            try (PreparedStatement ps = con.prepareStatement("DROP INDEX IF EXISTS ux_invoice_master_client_type_period")) {
                ps.execute();
            }
            try (PreparedStatement ps = con.prepareStatement("DROP INDEX IF EXISTS ux_invoice_master_client_period")) {
                ps.execute();
            }
            // One active invoice per client/type/period
            try (PreparedStatement ps = con.prepareStatement(
                    "CREATE UNIQUE INDEX ux_invoice_master_client_type_period ON invoice_master(client_id, type, period_from, period_to) WHERE is_void = 0")) {
                ps.execute();
            }
            // One active invoice per client/period (cross-type: monthly vs date range)
            try (PreparedStatement ps = con.prepareStatement(
                    "CREATE UNIQUE INDEX ux_invoice_master_client_period ON invoice_master(client_id, period_from, period_to) WHERE period_from IS NOT NULL AND period_to IS NOT NULL AND is_void = 0")) {
                ps.execute();
            }
        } catch (Exception e) {
            System.err.println("Warning: failed to ensure invoice_master unique index: " + e.getMessage());
        }
    }

    /* =========================================================
       INSERT NEW INVOICE
       ========================================================= */
    public void insert(Connection con, InvoiceMaster inv) throws Exception {

        String sql = """
            INSERT INTO invoice_master (
                invoice_no, client_id, client_name,
                invoice_date, period_from, period_to,
                amount, paid_amount, due_amount, payment_status,
                last_payment_date, type, status,
                is_void, void_reason, void_date,
                replaced_by_invoice_id, status_updated_by, file_path
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """;

        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, inv.getInvoiceNo());
            ps.setInt(2, inv.getClientId());
            ps.setString(3, inv.getClientName());
            ps.setString(4, toIso(inv.getInvoiceDate()));  // CHECK requires YYYY-MM-DD

            ps.setString(5, toIso(inv.getPeriodFrom()));
            ps.setString(6, toIso(inv.getPeriodTo()));

            ps.setDouble(7, inv.getAmount());
            ps.setDouble(8, inv.getPaidAmount());
            ps.setDouble(9, inv.getDueAmount());
            ps.setString(10, inv.getPaymentStatus());

            ps.setString(11, toIso(inv.getLastPaymentDate()));

            ps.setString(12, inv.getType());
            ps.setString(13, inv.getStatus());

            ps.setInt(14, inv.isVoid() ? 1 : 0);
            ps.setString(15, inv.getVoidReason());
            ps.setString(16, toIso(inv.getVoidDate()));

            if (inv.getReplacedByInvoiceId() != null)
                ps.setInt(17, inv.getReplacedByInvoiceId());
            else
                ps.setNull(17, Types.INTEGER);

            ps.setString(18, inv.getStatusUpdatedBy());
            ps.setString(19, inv.getFilePath());

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    inv.setId(rs.getInt(1));
                }
            }
        }
    }

    /* =========================================================
       FIND ACTIVE INVOICE BY CLIENT+PERIOD (ignores type - prevents cross-type duplicates)
       Used when monthly task and date range task share same period (e.g. Jan 1–31)
       ========================================================= */
    public Optional<InvoiceMaster> findActiveByClientPeriod(
            Connection con,
            int clientId,
            LocalDate from,
            LocalDate to
    ) throws Exception {

        if (from == null || to == null) return Optional.empty();

        String sql = """
            SELECT * FROM invoice_master
            WHERE client_id = ?
              AND period_from = ?
              AND period_to   = ?
              AND is_void = 0
            LIMIT 1
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, clientId);
            ps.setString(2, toIso(from));
            ps.setString(3, toIso(to));

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
            return Optional.empty();
        }
    }

    /* =========================================================
       FIND EXISTING BUSINESS INVOICE (by type - legacy)
       ========================================================= */
    public Optional<InvoiceMaster> findActiveByClientPeriodType(
            Connection con,
            int clientId,
            LocalDate from,
            LocalDate to,
            String type
    ) throws Exception {

        String sql = """
            SELECT * FROM invoice_master
            WHERE client_id = ?
              AND type = ?
              AND is_void = 0
              AND period_from = ?
              AND period_to   = ?
            LIMIT 1
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, clientId);
            ps.setString(2, type);

            // Use ISO strings for CHECK constraint compatibility
            ps.setString(3, toIso(from));
            ps.setString(4, toIso(to));

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }

            return Optional.empty();
        }
    }

    /* =========================================================
       UPDATE PAYMENT (partial / full)
       ========================================================= */
    public void updatePayment(Connection con, int invoiceId,
                              double paidAmount,
                              double dueAmount,
                              String paymentStatus,
                              LocalDate lastPaymentDate) throws Exception {

        String sql = """
            UPDATE invoice_master
            SET paid_amount = ?, due_amount = ?,
                payment_status = ?, last_payment_date = ?
            WHERE id = ?
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, paidAmount);
            ps.setDouble(2, dueAmount);
            ps.setString(3, paymentStatus);
            ps.setString(4, toIso(lastPaymentDate));
            ps.setInt(5, invoiceId);
            ps.executeUpdate();
        }
    }

    /* =========================================================
       DELETE INVOICE (used for cancel rollback - no duplicate records)
       ========================================================= */
    public void deleteInvoice(Connection con, int invoiceId) throws Exception {
        String sql = "DELETE FROM invoice_master WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, invoiceId);
            ps.executeUpdate();
        }
    }

    /* =========================================================
       VOID INVOICE
       ========================================================= */
    public void voidInvoice(Connection con, int invoiceId,
                            String reason, LocalDate date) throws Exception {

        String sql = """
            UPDATE invoice_master
            SET is_void = 1,
                void_reason = ?,
                void_date = ?,
                payment_status = 'VOID'
            WHERE id = ?
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setString(2, toIso(date));
            ps.setInt(3, invoiceId);
            ps.executeUpdate();
        }
    }

    /* =========================================================
       GET RECENT
       ========================================================= */
    public List<InvoiceMaster> findRecent(Connection con, int limit) throws Exception {

        String sql = """
            SELECT * FROM invoice_master
            ORDER BY created_at DESC
            LIMIT ?
        """;

        List<InvoiceMaster> list = new ArrayList<>();

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) list.add(mapRow(rs));
        }

        return list;
    }

    /* =========================================================
       MAP ROW
       ========================================================= */
    private InvoiceMaster mapRow(ResultSet rs) throws Exception {

        InvoiceMaster inv = new InvoiceMaster();

        inv.setId(rs.getInt("id"));
        inv.setInvoiceNo(rs.getString("invoice_no"));
        inv.setClientId(rs.getInt("client_id"));
        inv.setClientName(rs.getString("client_name"));

        // 🔥 SAFE DATE PARSING (handles ISO + epoch millis)
        inv.setInvoiceDate(parseDate(rs.getString("invoice_date")));

        inv.setAmount(rs.getDouble("amount"));
        inv.setPaidAmount(rs.getDouble("paid_amount"));
        inv.setDueAmount(rs.getDouble("due_amount"));
        inv.setPaymentStatus(rs.getString("payment_status"));

        inv.setType(rs.getString("type"));
        inv.setStatus(rs.getString("status"));

        // Load period dates so update() doesn't overwrite with null (causing unique constraint violation)
        inv.setPeriodFrom(parseDate(rs.getString("period_from")));
        inv.setPeriodTo(parseDate(rs.getString("period_to")));

        return inv;
    }
    
    private LocalDate parseDate(String value) {

        if (value == null || value.isBlank()) return null;

        try {
            // ✅ Normal ISO date: 2026-02-12
            if (value.contains("-")) {
                return LocalDate.parse(value);
            }

            // ✅ Epoch millis: 1769797800000
            long epoch = Long.parseLong(value);
            return java.time.Instant.ofEpochMilli(epoch)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate();

        } catch (Exception e) {
            throw new RuntimeException("Invalid date in DB: " + value, e);
        }
    }

    public void update(Connection con, InvoiceMaster inv) throws Exception {

        String sql = """
            UPDATE invoice_master
            SET
                invoice_no   = ?,
                client_id    = ?,
                client_name  = ?,
                invoice_date = ?,
                amount       = ?,
                type         = ?,
                status       = ?,
                period_from  = ?,
                period_to    = ?,
                file_path    = ?
            WHERE id = ?
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, inv.getInvoiceNo());
            ps.setInt(2, inv.getClientId());
            ps.setString(3, inv.getClientName());
            ps.setString(4, toIso(inv.getInvoiceDate()));
            ps.setDouble(5, inv.getAmount());
            ps.setString(6, inv.getType());
            ps.setString(7, inv.getStatus());

            ps.setString(8, toIso(inv.getPeriodFrom()));
            ps.setString(9, toIso(inv.getPeriodTo()));

            ps.setString(10, inv.getFilePath());

            ps.setInt(11, inv.getId());

            ps.executeUpdate();
        }
    }



    /** Returns ISO YYYY-MM-DD string for CHECK constraint compatibility; null for null input. */
    private String toIso(LocalDate d) {
        return d == null ? null : d.toString();
    }
}