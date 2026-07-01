package repository;

import model.InvoiceMaster;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import service.sync.PendingSyncFilters;
import utils.DBConnection;
import utils.DocumentNumbering;
import utils.InvoiceIdentifiers;

public class InvoiceMasterRepository {

    public InvoiceMasterRepository() {
        // Initialization if needed
    }

    public List<InvoiceMaster> findPendingForSync() {
        List<InvoiceMaster> list = new ArrayList<>();
        String sql = """
                SELECT * FROM invoice_master
                WHERE %s
                ORDER BY created_at ASC
                """.formatted(PendingSyncFilters.PENDING_STATUS);
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                InvoiceMaster inv = mapInvoiceRowBase(rs);
                list.add(inv);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /*
     * =========================================================
     * INSERT NEW INVOICE
     * =========================================================
     */
    public void insert(Connection con, InvoiceMaster inv) throws Exception {

        String sql = """
                    INSERT INTO invoice_master (
                        uuid, invoice_no, client_uuid, client_name,
                        invoice_date, period_from, period_to,
                        amount, paid_amount, due_amount, payment_status,
                        last_payment_date, type, status,
                        is_void, void_reason, void_date,
                        replaced_by_invoice_uuid, parent_invoice_uuid, status_updated_by, file_path,
                        document_series, sync_status, sync_version, total_after_tax, round_off,
                    place_of_supply, payment_terms, due_date, vehicle_dispatch,
                    po_no, po_date, dispatch_through, lr_tracking_no, remarks, eway_bill_no
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            String uuid = inv.getUuid() != null && !inv.getUuid().isBlank()
                    ? inv.getUuid()
                    : InvoiceIdentifiers.newUuidString();
            inv.setUuid(uuid);

            String invNo = DocumentNumbering.stripLeadingHash(inv.getInvoiceNo());
            inv.setInvoiceNo(invNo);
            if (inv.getSyncStatus() == null || inv.getSyncStatus().isBlank()) {
                inv.setSyncStatus("PENDING");
            }

            ps.setString(1, uuid);
            ps.setString(2, invNo);
            ps.setString(3, inv.getClientUuid());
            ps.setString(4, inv.getClientName());
            ps.setString(5, toIso(inv.getInvoiceDate()));
            ps.setString(6, toIso(inv.getPeriodFrom()));
            ps.setString(7, toIso(inv.getPeriodTo()));
            ps.setDouble(8, inv.getAmount());
            ps.setDouble(9, inv.getPaidAmount());
            ps.setDouble(10, inv.getDueAmount());
            ps.setString(11, inv.getPaymentStatus());
            ps.setString(12, toIso(inv.getLastPaymentDate()));
            ps.setString(13, inv.getType());
            ps.setString(14, inv.getStatus());
            ps.setInt(15, inv.isVoid() ? 1 : 0);
            ps.setString(16, inv.getVoidReason());
            ps.setString(17, toIso(inv.getVoidDate()));
            ps.setString(18, inv.getReplacedByInvoiceUuid());
            ps.setString(19, inv.getParentInvoiceUuid());
            ps.setString(20, inv.getStatusUpdatedBy());
            ps.setString(21, inv.getFilePath());
            ps.setString(22, inv.getDocumentSeries());
            ps.setString(23, inv.getSyncStatus());
            ps.setInt(24, inv.getSyncVersion());
            ps.setDouble(25, inv.getTotalAfterTax());
            ps.setDouble(26, inv.getRoundOff());
            
            // New metadata columns
            ps.setString(27, inv.getPlaceOfSupply());
            ps.setString(28, inv.getPaymentTerms());
            ps.setString(29, toIso(inv.getDueDate()));
            ps.setString(30, inv.getVehicleDispatch());
            ps.setString(31, inv.getPoNo());
            ps.setString(32, toIso(inv.getPoDate()));
            ps.setString(33, inv.getDispatchThrough());
            ps.setString(34, inv.getLrTrackingNo());
            ps.setString(35, inv.getRemarks());
            ps.setString(36, inv.getEwayBillNo());

            ps.executeUpdate();
        }
    }

    public void updateInvoiceNo(Connection con, String invoiceUuid, String newInvoiceNo) throws Exception {
        if (invoiceUuid == null || invoiceUuid.isBlank() || newInvoiceNo == null || newInvoiceNo.isBlank()) {
            return;
        }
        try (PreparedStatement ps = con.prepareStatement("""
                UPDATE invoice_master SET invoice_no = ?, sync_status = 'PENDING',
                sync_version = sync_version + 1, updated_at = datetime('now')
                WHERE uuid = ?
                """)) {
            ps.setString(1, DocumentNumbering.stripLeadingHash(newInvoiceNo.trim()));
            ps.setString(2, invoiceUuid.trim());
            ps.executeUpdate();
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
            String clientId,
            LocalDate from,
            LocalDate to) throws Exception {

        if (from == null || to == null)
            return Optional.empty();

        String sql = """
                    SELECT * FROM invoice_master
                    WHERE client_uuid = ?
                      AND period_from = ?
                      AND period_to   = ?
                      AND is_void = 0
                      AND status NOT IN ('REVISED', 'CANCELLED')
                    LIMIT 1
                """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, clientId);
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
    public InvoiceMaster findByUuid(Connection con, String uuid) throws Exception {
        String sql = "SELECT * FROM invoice_master WHERE uuid = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid);
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
     * DELETE INVOICE (used for cancel rollback - no duplicate records)
     * =========================================================
     */
    public void deleteInvoice(Connection con, String invoiceUuid) throws Exception {
        if (invoiceUuid == null || invoiceUuid.isBlank()) {
            return;
        }
        String sql = "UPDATE invoice_master SET is_deleted = 1, is_active = 0, deleted_at = datetime('now'), sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, invoiceUuid);
            ps.executeUpdate();
        }
        deleteInvoiceOnSupabaseAsync(invoiceUuid);
    }

    private static void deleteInvoiceOnSupabaseAsync(String invoiceUuid) {
        if (invoiceUuid == null || invoiceUuid.isBlank()) {
            return;
        }
        api.supabase.SupabaseGate.restClientIfConfigured().ifPresent(http -> java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                String v = java.net.URLEncoder.encode(invoiceUuid.trim(), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
                com.google.gson.JsonObject body = new com.google.gson.JsonObject();
                body.addProperty("uuid", invoiceUuid.trim());
                body.addProperty("is_deleted", 1);
                body.addProperty("is_active", 0);
                body.addProperty("sync_status", "SYNCED");
                body.addProperty("synced_at", java.time.Instant.now().toString());
                body.addProperty("deleted_at", java.time.Instant.now().toString());
                http.patchJson(api.supabase.SupabaseEndpoints.INVOICE_MASTER, "uuid=eq." + v, body.toString(), "return=minimal");
            } catch (Exception ex) {
                System.err.println("[Supabase invoices] remote delete/patch failed for uuid=" + invoiceUuid + ": " + ex.getMessage());
            }
        }));
    }

    /*
     * =========================================================
     * UPDATE PAYMENT (partial / full)
     * =========================================================
     */
    public void updatePayment(Connection con, String invoiceUuid,
            double paidAmount,
            double dueAmount,
            String paymentStatus,
            LocalDate lastPaymentDate) throws Exception {

        String sql = """
                    UPDATE invoice_master
                    SET paid_amount = ?, due_amount = ?,
                        payment_status = ?, last_payment_date = ?,
                        updated_at = datetime('now'), sync_status = 'PENDING'
                    WHERE uuid = ?
                """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, paidAmount);
            ps.setDouble(2, dueAmount);
            ps.setString(3, paymentStatus);
            ps.setString(4, toIso(lastPaymentDate));
            ps.setString(5, invoiceUuid);
            ps.executeUpdate();
        }
    }

    /*
     * =========================================================
     * VOID INVOICE
     * =========================================================
     */
    public void voidInvoice(Connection con, String invoiceUuid,
            String reason, LocalDate date) throws Exception {

        String sql = """
                    UPDATE invoice_master
                    SET is_void = 1,
                        void_reason = ?,
                        void_date = ?,
                        payment_status = 'VOID',
                        updated_at = datetime('now'),
                        sync_status = 'PENDING'
                    WHERE uuid = ?
                """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setString(2, toIso(date));
            ps.setString(3, invoiceUuid);
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
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapInvoiceRowBase(rs));
                }
            }
        }
        for (InvoiceMaster inv : list) {
            fetchAdjustmentSummaries(con, inv);
        }
        return list;
    }

    /*
     * =========================================================
     * FIND INVOICES BY CLIENT (ALL GENERATED)
     * =========================================================
     */
    public List<InvoiceMaster> findByClientId(Connection con, String clientId) throws Exception {

        String sql = """
                    SELECT * FROM invoice_master
                    WHERE client_uuid = ? AND is_void = 0
                    ORDER BY invoice_date DESC, uuid DESC
                """;

        List<InvoiceMaster> list = new ArrayList<>();

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapInvoiceRowBase(rs));
                }
            }
        }
        for (InvoiceMaster inv : list) {
            fetchAdjustmentSummaries(con, inv);
        }
        return list;
    }

    /*
     * =========================================================
     * FIND FILTERED INVOICES (FOR VIEW INVOICES SCREEN)
     * =========================================================
     */
    public List<InvoiceMaster> findFiltered(Connection con, String clientId, String paymentStatus, String invoiceStatus, LocalDate start, LocalDate end, String invoiceNo, String documentSeries) throws Exception {
        StringBuilder sql = new StringBuilder("SELECT * FROM invoice_master WHERE is_void = 0");
        List<Object> params = new ArrayList<>();

        if (clientId != null && !clientId.isBlank()) {
            sql.append(" AND client_uuid = ?");
            params.add(clientId.trim());
        }
        if (paymentStatus != null && !paymentStatus.equalsIgnoreCase("All") && !paymentStatus.trim().isEmpty()) {
            sql.append(" AND UPPER(payment_status) = ?");
            params.add(paymentStatus.trim().toUpperCase());
        }
        if (invoiceStatus != null && !invoiceStatus.equalsIgnoreCase("All") && !invoiceStatus.trim().isEmpty()) {
            sql.append(" AND UPPER(status) = ?");
            params.add(invoiceStatus.trim().toUpperCase());
        }
        if (invoiceNo != null && !invoiceNo.trim().isEmpty()) {
            String needle = DocumentNumbering.stripLeadingHash(invoiceNo.trim());
            if (needle != null && !needle.isEmpty()) {
                sql.append(" AND invoice_no LIKE ?");
                params.add("%" + needle + "%");
            }
        }
        if (start != null) {
            sql.append(" AND DATE(invoice_date) >= ?");
            params.add(toIso(start));
        }
        if (end != null) {
            sql.append(" AND DATE(invoice_date) <= ?");
            params.add(toIso(end));
        }
        if (documentSeries != null && !documentSeries.equalsIgnoreCase("All") && !documentSeries.trim().isEmpty()) {
            sql.append(" AND document_series = ?");
            params.add(documentSeries.trim());
        }

        sql.append(" ORDER BY invoice_date DESC, COALESCE(updated_at, created_at) DESC");

        List<InvoiceMaster> list = new ArrayList<>();

        try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapInvoiceRowBase(rs));
                }
            }
        }
        for (InvoiceMaster inv : list) {
            fetchAdjustmentSummaries(con, inv);
        }
        return list;
    }

    /*
     * =========================================================
     * MAP ROW
     * =========================================================
     */    public InvoiceMaster findByInvoiceNo(Connection con, String invoiceNo) throws Exception {
        if (invoiceNo == null) return null;
        String key = DocumentNumbering.stripLeadingHash(invoiceNo);
        if (key == null || key.isEmpty()) return null;
        String sql = "SELECT * FROM invoice_master WHERE invoice_no = ? LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowPublic(rs);
                }
            }
        }
        return null;
    }

    /**
     * Maps {@code invoice_master} columns only. When reading many rows from one {@link ResultSet},
     * use this in the loop, close the result set, then call {@link #fetchAdjustmentSummaries} per
     * invoice — nested queries on the same connection fail on some JDBC drivers while the outer
     * result set is still open.
     */
    private InvoiceMaster mapInvoiceRowBase(ResultSet rs) throws Exception {
        InvoiceMaster inv = new InvoiceMaster();
        inv.setUuid(rs.getString("uuid"));
        inv.setInvoiceNo(DocumentNumbering.stripLeadingHash(rs.getString("invoice_no")));
        inv.setClientUuid(rs.getString("client_uuid"));
        inv.setClientName(rs.getString("client_name"));
        inv.setInvoiceDate(parseDate(rs.getString("invoice_date")));
        inv.setAmount(rs.getDouble("amount"));
        inv.setPaidAmount(rs.getDouble("paid_amount"));
        inv.setDueAmount(rs.getDouble("due_amount"));
        inv.setPaymentStatus(rs.getString("payment_status"));
        inv.setType(rs.getString("type"));
        inv.setStatus(rs.getString("status"));
        inv.setDocumentSeries(readOptionalString(rs, "document_series"));
        inv.setPeriodFrom(parseDate(rs.getString("period_from")));
        inv.setPeriodTo(parseDate(rs.getString("period_to")));

        inv.setReplacedByInvoiceUuid(rs.getString("replaced_by_invoice_uuid"));
        inv.setParentInvoiceUuid(rs.getString("parent_invoice_uuid"));
        
        inv.setFilePath(rs.getString("file_path"));
        inv.setSyncStatus(rs.getString("sync_status"));
        inv.setSyncVersion(rs.getInt("sync_version"));
        inv.setCreatedAt(rs.getString("created_at"));
        inv.setUpdatedAt(rs.getString("updated_at"));

        inv.setTotalAfterTax(rs.getDouble("total_after_tax"));
        inv.setRoundOff(rs.getDouble("round_off"));

        inv.setPlaceOfSupply(readOptionalString(rs, "place_of_supply"));
        inv.setPaymentTerms(readOptionalString(rs, "payment_terms"));
        inv.setDueDate(parseDate(readOptionalString(rs, "due_date")));
        inv.setVehicleDispatch(readOptionalString(rs, "vehicle_dispatch"));
        inv.setPoNo(readOptionalString(rs, "po_no"));
        inv.setPoDate(parseDate(readOptionalString(rs, "po_date")));
        inv.setDispatchThrough(readOptionalString(rs, "dispatch_through"));
        inv.setLrTrackingNo(readOptionalString(rs, "lr_tracking_no"));
        inv.setRemarks(readOptionalString(rs, "remarks"));
        inv.setEwayBillNo(readOptionalString(rs, "eway_bill_no"));
        
        return inv;
    }

    public InvoiceMaster mapRowPublic(ResultSet rs) throws Exception {
        InvoiceMaster inv = mapInvoiceRowBase(rs);
        fetchAdjustmentSummaries(rs.getStatement().getConnection(), inv);
        return inv;
    }

    /** Row mapping only; call {@link #applyAdjustmentSummaries} after the driving {@link ResultSet} is closed. */
    public InvoiceMaster mapInvoiceRowWithoutSummaries(ResultSet rs) throws Exception {
        return mapInvoiceRowBase(rs);
    }

    public void applyAdjustmentSummaries(Connection con, InvoiceMaster inv) {
        fetchAdjustmentSummaries(con, inv);
    }

    private void fetchAdjustmentSummaries(Connection con, InvoiceMaster inv) {
        String sql = "SELECT type, SUM(amount), COUNT(*) FROM invoice_adjustments WHERE invoice_uuid = ? GROUP BY type";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, inv.getUuid());
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
        } catch (Exception e) {}

        // Add refund summary
        String sqlRef = """
            SELECT SUM(pa.allocated_amount), COUNT(pa.uuid) 
            FROM payment_allocations pa 
            JOIN payments p ON pa.payment_uuid = p.uuid 
            WHERE pa.invoice_uuid = ? AND p.type = 'Refund'
        """;
        try (PreparedStatement ps = con.prepareStatement(sqlRef)) {
            ps.setString(1, inv.getUuid());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    inv.setRefundAmount(rs.getDouble(1));
                    inv.setRefundCount(rs.getInt(2));
                }
            }
        } catch (Exception e) {}
    }

    private static String readOptionalString(ResultSet rs, String column) {
        try {
            String s = rs.getString(column);
            return rs.wasNull() ? null : s;
        } catch (SQLException e) {
            return null;
        }
    }

    private LocalDate parseDate(String value) {

        if (value == null || value.isBlank())
            return null;

        try {
            // ✅ Normal ISO date: 2026-02-12
            if (value.contains("-")) {
                String d = value.trim();
                if (d.contains(" ")) {
                    d = d.split(" ")[0];
                }
                if (d.contains("T")) {
                    d = d.split("T")[0];
                }
                return LocalDate.parse(d);
            }

            // ✅ Epoch millis: 1769797800000
            long epoch = Long.parseLong(value.trim());
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
                        client_uuid  = ?,
                        client_name  = ?,
                        invoice_date = ?,
                        amount       = ?,
                        paid_amount  = ?,
                        due_amount   = ?,
                        payment_status = ?,
                        type         = ?,
                        status       = ?,
                        period_from  = ?,
                        period_to    = ?,
                        file_path    = ?,
                        replaced_by_invoice_uuid = ?,
                        parent_invoice_uuid = ?,
                        is_void      = ?,
                        void_reason  = ?,
                        void_date    = ?,
                        document_series = ?,
                        total_after_tax = ?,
                        round_off = ?,
                        place_of_supply = ?,
                        payment_terms = ?,
                        due_date = ?,
                        vehicle_dispatch = ?,
                        po_no = ?,
                        po_date = ?,
                        dispatch_through = ?,
                        lr_tracking_no = ?,
                        remarks = ?,
                        eway_bill_no = ?,
                        updated_at   = datetime('now'),
                        sync_status  = 'PENDING'
                    WHERE uuid = ?
                """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            String invNo = DocumentNumbering.stripLeadingHash(inv.getInvoiceNo());
            inv.setInvoiceNo(invNo);
            ps.setString(1, invNo);
            ps.setString(2, inv.getClientUuid());
            ps.setString(3, inv.getClientName());
            ps.setString(4, toIso(inv.getInvoiceDate()));
            ps.setDouble(5, inv.getAmount());
            ps.setDouble(6, inv.getPaidAmount());
            ps.setDouble(7, inv.getDueAmount());
            ps.setString(8, inv.getPaymentStatus());
            ps.setString(9, inv.getType());
            ps.setString(10, inv.getStatus());

            ps.setString(11, toIso(inv.getPeriodFrom()));
            ps.setString(12, toIso(inv.getPeriodTo()));

            ps.setString(13, inv.getFilePath());

            ps.setString(14, inv.getReplacedByInvoiceUuid());
            ps.setString(15, inv.getParentInvoiceUuid());

            ps.setInt(16, inv.isVoid() ? 1 : 0);
            ps.setString(17, inv.getVoidReason());
            ps.setString(18, toIso(inv.getVoidDate()));
            ps.setString(19, inv.getDocumentSeries());
            ps.setDouble(20, inv.getTotalAfterTax());
            ps.setDouble(21, inv.getRoundOff());
            
            ps.setString(22, inv.getPlaceOfSupply());
            ps.setString(23, inv.getPaymentTerms());
            ps.setString(24, toIso(inv.getDueDate()));
            ps.setString(25, inv.getVehicleDispatch());
            ps.setString(26, inv.getPoNo());
            ps.setString(27, toIso(inv.getPoDate()));
            ps.setString(28, inv.getDispatchThrough());
            ps.setString(29, inv.getLrTrackingNo());
            ps.setString(30, inv.getRemarks());
            ps.setString(31, inv.getEwayBillNo());
            
            ps.setString(32, inv.getUuid());

            ps.executeUpdate();
        }
    }

    public int countRevisions(Connection con, String parentUuid) throws Exception {
        String sql = "SELECT COUNT(*) FROM invoice_master WHERE parent_invoice_uuid = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, parentUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    public double getClientUnallocatedBalance(Connection con, String clientId) throws Exception {
        double totalPayments = 0;
        double totalAllocated = 0;

        // 1. Sum all payments (Payments are +, Refunds are -)
        String sqlPay = "SELECT SUM(amount) FROM payments WHERE client_uuid = ?";
        try (PreparedStatement ps = con.prepareStatement(sqlPay)) {
            ps.setString(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) totalPayments = rs.getDouble(1);
            }
        }

        // 2. Sum all allocations
        String sqlAlloc = """
            SELECT SUM(pa.allocated_amount) 
            FROM payment_allocations pa
            JOIN payments p ON pa.payment_uuid = p.uuid
            WHERE p.client_uuid = ? AND COALESCE(pa.is_deleted, 0) = 0
        """;
        try (PreparedStatement ps = con.prepareStatement(sqlAlloc)) {
            ps.setString(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) totalAllocated = rs.getDouble(1);
            }
        }

        return totalPayments - totalAllocated;
    }


    /*
     * =========================================================
     * GET INVOICE MONTHLY COUNTS
     * =========================================================
     */
    public java.util.Map<String, Integer> getInvoiceMonthlyCounts(Connection con, int monthsLimit) throws Exception {
        String sql = """
                    SELECT strftime('%Y-%m', invoice_date) as month_val, COUNT(uuid) as inv_count
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