package repository;

import model.InvoiceMaster;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

public class InvoiceMasterRepository {

    public InvoiceMasterRepository() {
        // Initialization if needed
    }

    /*
     * =========================================================
     * INSERT NEW INVOICE
     * =========================================================
     */
    public void insert(Connection con, InvoiceMaster inv) throws Exception {

        String sql = """
                    INSERT INTO invoice_master (
                        invoice_no, client_id, client_name,
                        invoice_date, period_from, period_to,
                        amount, paid_amount, due_amount, payment_status,
                        last_payment_date, type, status,
                        is_void, void_reason, void_date,
                        replaced_by_invoice_id, parent_invoice_id, status_updated_by, file_path
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;

        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, inv.getInvoiceNo());
            ps.setInt(2, inv.getClientId());
            ps.setString(3, inv.getClientName());
            ps.setString(4, toIso(inv.getInvoiceDate())); // CHECK requires YYYY-MM-DD

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

            if (inv.getParentInvoiceId() != null)
                ps.setInt(18, inv.getParentInvoiceId());
            else
                ps.setNull(18, Types.INTEGER);

            ps.setString(19, inv.getStatusUpdatedBy());
            ps.setString(20, inv.getFilePath());

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    inv.setId(rs.getInt(1));
                }
            }
        }
    }

    /*
     * =========================================================
     * FIND ACTIVE INVOICE BY CLIENT+PERIOD (ignores type - prevents cross-type
     * duplicates)
     * Used when monthly task and date range task share same period (e.g. Jan 1–31)
     * =========================================================
     */
    public Optional<InvoiceMaster> findActiveByClientPeriod(
            Connection con,
            int clientId,
            LocalDate from,
            LocalDate to) throws Exception {

        if (from == null || to == null)
            return Optional.empty();

        String sql = """
                    SELECT * FROM invoice_master
                    WHERE client_id = ?
                      AND period_from = ?
                      AND period_to   = ?
                      AND is_void = 0
                      AND status IN ('DRAFT', 'FINAL')
                    LIMIT 1
                """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, clientId);
            ps.setString(2, toIso(from));
            ps.setString(3, toIso(to));

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(mapRowPublic(rs));
            }
            return Optional.empty();
        }
    }

    /*
     * =========================================================
     * SIMPLE FIND BY ID (used by payments)
     * =========================================================
     */
    public InvoiceMaster findById(Connection con, int id) throws Exception {
        String sql = "SELECT * FROM invoice_master WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowPublic(rs);
                }
            }
        }
        return null;
    }

    /*
     * =========================================================
     * FIND EXISTING BUSINESS INVOICE (by type - legacy)
     * =========================================================
     */
    public Optional<InvoiceMaster> findActiveByClientPeriodType(
            Connection con,
            int clientId,
            LocalDate from,
            LocalDate to,
            String type) throws Exception {

        String sql = """
                    SELECT * FROM invoice_master
                    WHERE client_id = ?
                      AND type = ?
                      AND is_void = 0
                      AND status IN ('DRAFT', 'FINAL')
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
                return Optional.of(mapRowPublic(rs));
            }

            return Optional.empty();
        }
    }

    /*
     * =========================================================
     * UPDATE PAYMENT (partial / full)
     * =========================================================
     */
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

    /*
     * =========================================================
     * DELETE INVOICE (used for cancel rollback - no duplicate records)
     * =========================================================
     */
    public void deleteInvoice(Connection con, int invoiceId) throws Exception {
        String sql = "DELETE FROM invoice_master WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, invoiceId);
            ps.executeUpdate();
        }
    }

    /*
     * =========================================================
     * VOID INVOICE
     * =========================================================
     */
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

    /*
     * =========================================================
     * GET RECENT
     * =========================================================
     */
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

            while (rs.next())
                list.add(mapRowPublic(rs));
        }

        return list;
    }

    /*
     * =========================================================
     * FIND INVOICES BY CLIENT (ALL GENERATED)
     * =========================================================
     */
    public List<InvoiceMaster> findByClientId(Connection con, int clientId) throws Exception {

        String sql = """
                    SELECT * FROM invoice_master
                    WHERE client_id = ? AND is_void = 0
                    ORDER BY invoice_date DESC, id DESC
                """;

        List<InvoiceMaster> list = new ArrayList<>();

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowPublic(rs));
                }
            }
        }
        return list;
    }

    /*
     * =========================================================
     * FIND FILTERED INVOICES (FOR VIEW INVOICES SCREEN)
     * =========================================================
     */
    public List<InvoiceMaster> findFiltered(Connection con, Integer clientId, String status, LocalDate start, LocalDate end, String invoiceNo) throws Exception {
        StringBuilder sql = new StringBuilder("SELECT * FROM invoice_master WHERE is_void = 0");
        List<Object> params = new ArrayList<>();

        if (clientId != null && clientId > 0) {
            sql.append(" AND client_id = ?");
            params.add(clientId);
        }
        if (status != null && !status.equalsIgnoreCase("All") && !status.trim().isEmpty()) {
            sql.append(" AND UPPER(payment_status) = ?");
            params.add(status.trim().toUpperCase());
        }
        if (invoiceNo != null && !invoiceNo.trim().isEmpty()) {
            sql.append(" AND invoice_no LIKE ?");
            params.add("%" + invoiceNo.trim() + "%");
        }
        if (start != null) {
            sql.append(" AND DATE(invoice_date) >= ?");
            params.add(toIso(start));
        }
        if (end != null) {
            sql.append(" AND DATE(invoice_date) <= ?");
            params.add(toIso(end));
        }

        sql.append(" ORDER BY invoice_date DESC, id DESC");

        List<InvoiceMaster> list = new ArrayList<>();

        try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowPublic(rs));
                }
            }
        }
        return list;
    }

    /*
     * =========================================================
     * MAP ROW
     * =========================================================
     */    public InvoiceMaster mapRowPublic(ResultSet rs) throws Exception {
        InvoiceMaster inv = new InvoiceMaster();
        inv.setId(rs.getInt("id"));
        inv.setInvoiceNo(rs.getString("invoice_no"));
        inv.setClientId(rs.getInt("client_id"));
        inv.setClientName(rs.getString("client_name"));
        inv.setInvoiceDate(parseDate(rs.getString("invoice_date")));
        inv.setAmount(rs.getDouble("amount"));
        inv.setPaidAmount(rs.getDouble("paid_amount"));
        inv.setDueAmount(rs.getDouble("due_amount"));
        inv.setPaymentStatus(rs.getString("payment_status"));
        inv.setType(rs.getString("type"));
        inv.setStatus(rs.getString("status"));
        inv.setPeriodFrom(parseDate(rs.getString("period_from")));
        inv.setPeriodTo(parseDate(rs.getString("period_to")));
        
        int replacedId = rs.getInt("replaced_by_invoice_id");
        if (!rs.wasNull()) inv.setReplacedByInvoiceId(replacedId);
        int parentId = rs.getInt("parent_invoice_id");
        if (!rs.wasNull()) inv.setParentInvoiceId(parentId);
        inv.setFilePath(rs.getString("file_path"));

        // Efficiently fetch adjustment summaries only if needed or keep it robust
        fetchAdjustmentSummaries(rs.getStatement().getConnection(), inv);

        return inv;
    }

    private void fetchAdjustmentSummaries(Connection con, InvoiceMaster inv) {
        String sql = "SELECT type, SUM(amount), COUNT(*) FROM invoice_adjustments WHERE invoice_id = ? GROUP BY type";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, inv.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString(1);
                    double sum = rs.getDouble(2);
                    int count = rs.getInt(3);
                    if ("Credit Note".equalsIgnoreCase(type)) {
                        inv.setCnAmount(sum);
                        inv.setCnCount(count);
                    } else if ("Debit Note".equalsIgnoreCase(type)) {
                        inv.setDnAmount(sum);
                        inv.setDnCount(count);
                    }
                }
            }
        } catch (Exception e) {
            // Silently ignore if table doesn't exist or other issues; default values 0 and null are fine
        }
    }

    private LocalDate parseDate(String value) {

        if (value == null || value.isBlank())
            return null;

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
                        file_path    = ?,
                        replaced_by_invoice_id = ?,
                        parent_invoice_id = ?,
                        is_void      = ?,
                        void_reason  = ?,
                        void_date    = ?
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

            if (inv.getReplacedByInvoiceId() != null)
                ps.setInt(11, inv.getReplacedByInvoiceId());
            else
                ps.setNull(11, Types.INTEGER);

            if (inv.getParentInvoiceId() != null)
                ps.setInt(12, inv.getParentInvoiceId());
            else
                ps.setNull(12, Types.INTEGER);

            ps.setInt(13, inv.isVoid() ? 1 : 0);
            ps.setString(14, inv.getVoidReason());
            ps.setString(15, toIso(inv.getVoidDate()));
            
            ps.setInt(16, inv.getId());

            ps.executeUpdate();
        }
    }

    public int countRevisions(Connection con, int parentId) throws Exception {
        String sql = "SELECT COUNT(*) FROM invoice_master WHERE parent_invoice_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, parentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }


    /*
     * =========================================================
     * GET INVOICE MONTHLY COUNTS
     * =========================================================
     */
    public java.util.Map<String, Integer> getInvoiceMonthlyCounts(Connection con, int monthsLimit) throws Exception {
        String sql = """
                    SELECT strftime('%Y-%m', invoice_date) as month_val, COUNT(id) as inv_count
                    FROM invoice_master
                    WHERE is_void = 0 AND invoice_date IS NOT NULL
                    GROUP BY month_val
                    ORDER BY month_val DESC
                    LIMIT ?
                """;

        java.util.LinkedHashMap<String, Integer> map = new java.util.LinkedHashMap<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, monthsLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String m = rs.getString("month_val");
                    int count = rs.getInt("inv_count");
                    if (m != null) {
                        map.put(m, count);
                    }
                }
            }
        }

        // Reverse to chronological order
        java.util.List<String> keys = new ArrayList<>(map.keySet());
        java.util.Collections.reverse(keys);

        java.util.Map<String, Integer> chronologicalMap = new java.util.LinkedHashMap<>();
        for (String k : keys) {
            chronologicalMap.put(k, map.get(k));
        }

        return chronologicalMap;
    }

    /**
     * Returns ISO YYYY-MM-DD string for CHECK constraint compatibility; null for
     * null input.
     */
    private String toIso(LocalDate d) {
        return d == null ? null : d.toString();
    }
}