package service;

import model.Invoice;
import model.InvoiceMaster;
import model.MasterDocumentSeries;
import repository.InvoiceMasterRepository;
import service.NumberSequenceAllocationService.AllocatedNumber;
import service.sync.UniversalNumberAllocator;
import service.sync.UniversalSyncEngine;
import utils.AtomicDB;
import utils.ClientIdentifiers;
import utils.DocumentNumbering;

import java.time.LocalDate;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InvoiceMasterService {

    /**
     * Result of createOrGetExisting: master + whether it was newly inserted (for
     * cancel rollback).
     */
    public record CreateOrGetResult(InvoiceMaster master, boolean wasNewlyCreated) {
    }

    private final InvoiceMasterRepository repo = new InvoiceMasterRepository();
    private final SettingsService settingsService = new SettingsService();
    private final UniversalNumberAllocator numberAllocator = UniversalNumberAllocator.getInstance();

    /*
     * =========================================================
     * CREATE OR REUSE BUSINESS INVOICE
     * =========================================================
     */
    public CreateOrGetResult createNewDraftInvoice(
            Invoice invoice,
            String type,
            String filePath) {

        CreateOrGetResult result = AtomicDB.run(con -> {

            if (invoice.getJobs() == null || invoice.getJobs().isEmpty()) {
                throw new RuntimeException("Cannot create an empty invoice. No jobs found.");
            }

            MasterDocumentSeries series = invoice.getMasterDocumentSeries();
            if (series == null) {
                series = MasterDocumentSeries.GST_INVOICE;
            }
            AllocatedNumber allocated;
            if (series == MasterDocumentSeries.PROFORMA_INVOICE) {
                allocated = service.sync.UniversalTemporaryNumberEngine.getInstance().allocateTemporary(con, "proforma_invoice");
            } else {
                allocated = numberAllocator.allocateInvoiceNumber(con, series, invoice.getInvoiceDate());
            }
            String invoiceNo = allocated.value();
            invoice.setInvoiceNo(invoiceNo);

            InvoiceMaster inv = new InvoiceMaster(
                    invoiceNo,
                    invoice.getClientId(),
                    invoice.getClientName(),
                    invoice.getInvoiceDate(),
                    invoice.getGrandTotal(),
                    type,
                    "DRAFT");

            inv.setFilePath(filePath);
            inv.setPeriodFrom(invoice.getFromDate());
            inv.setPeriodTo(invoice.getToDate());
            inv.setDocumentSeries(series.name());
            inv.setSyncStatus("PENDING");

            repo.insert(con, inv);
            return new CreateOrGetResult(inv, true);
        });
        UniversalSyncEngine.scheduleSyncAsync();
        return result;
    }

    /**
     * Delete invoices created during a cancelled run (avoids duplicate
     * voided+active records).
     */
    public void deleteInvoicesIfCancelled(List<String> invoiceUuids) {
        if (invoiceUuids == null || invoiceUuids.isEmpty())
            return;
        AtomicDB.runVoid(con -> {
            for (String uuid : invoiceUuids) {
                try {
                    unlinkJobsFromInvoice(con, uuid);
                    repo.deleteInvoice(con, uuid);
                } catch (Exception e) {
                    System.err.println("Failed to delete invoice " + uuid + " on cancel: " + e.getMessage());
                }
            }
        });
    }

    public String saveGeneratedInvoice(
            Invoice invoice,
            String type,
            String status,
            String filePath) {
        if (invoice.getJobs() == null || invoice.getJobs().isEmpty()) {
            throw new RuntimeException("Cannot save an empty invoice.");
        }

        String generatedUuid = AtomicDB.run(con -> {
            MasterDocumentSeries series = invoice.getMasterDocumentSeries();
            if (series == null) {
                series = MasterDocumentSeries.GST_INVOICE;
            }
            AllocatedNumber allocated;
            if (series == MasterDocumentSeries.PROFORMA_INVOICE) {
                allocated = service.sync.UniversalTemporaryNumberEngine.getInstance().allocateTemporary(con, "proforma_invoice");
            } else {
                allocated = numberAllocator.allocateInvoiceNumber(con, series, invoice.getInvoiceDate());
            }
            String invoiceNo = allocated.value();
            invoice.setInvoiceNo(invoiceNo);

            InvoiceMaster inv = new InvoiceMaster(
                    invoiceNo,
                    invoice.getClientId(),
                    invoice.getClientName(),
                    invoice.getInvoiceDate(),
                    invoice.getGrandTotal(),
                    type,
                    status);

            inv.setFilePath(filePath);
            inv.setDocumentSeries(series.name());

            repo.insert(con, inv);
            linkJobsToInvoice(con, inv.getUuid(), invoice);

            deleteEmptyInvoices(con);
            return inv.getUuid();
        });
        UniversalSyncEngine.scheduleSyncAsync();
        return generatedUuid;
    }

    public void registerDateRangeInvoice(
            Invoice invoice,
            LocalDate from,
            LocalDate to,
            String type,
            String filePath) {

        AtomicDB.runVoid(con -> {
            // Find the master created earlier in this session (by invoice_no)
            InvoiceMaster existing = repo.findByInvoiceNo(con, invoice.getInvoiceNo());

            if (existing != null) {
                // Link jobs to the draft created earlier
                linkJobsToInvoice(con, existing.getUuid(), invoice);
                return;
            }

            if (invoice.getJobs() == null || invoice.getJobs().isEmpty()) {
                throw new RuntimeException("Cannot create an empty invoice. No jobs found.");
            }

            if (invoice.getJobs() == null || invoice.getJobs().isEmpty()) {
                throw new RuntimeException("Cannot create an empty invoice. No jobs found.");
            }

            // =====================================================
            // 🆕 CREATE NEW INVOICE MASTER ENTRY
            // =====================================================
            InvoiceMaster inv = new InvoiceMaster(
                    invoice.getInvoiceNo(),
                    invoice.getClientId(),
                    invoice.getClientName(),
                    invoice.getInvoiceDate(),
                    invoice.getGrandTotal(),
                    type,
                    "DRAFT");

            inv.setPeriodFrom(from);
            inv.setPeriodTo(to);
            inv.setFilePath(filePath);
            inv.setDocumentSeries(invoice.getMasterDocumentSeries().name());

            repo.insert(con, inv);
            linkJobsToInvoice(con, inv.getUuid(), invoice);
            deleteEmptyInvoices(con);
        });
    }

    private void linkJobsToInvoice(java.sql.Connection con, String invoiceUuid, Invoice invoice) {
        if (invoice == null || invoice.getJobs() == null || invoice.getJobs().isEmpty())
            return;

        List<String> jobUuids = new ArrayList<>();
        for (model.InvoiceJob job : invoice.getJobs()) {
            String u = job.getJobUuid();
            if (u != null && !u.isBlank()) {
                // Only include if it actually exists in the jobs table
                String checkSql = "SELECT 1 FROM jobs WHERE uuid = ?";
                try (java.sql.PreparedStatement ps = con.prepareStatement(checkSql)) {
                    ps.setString(1, u);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            jobUuids.add(u);
                        }
                    }
                } catch (SQLException e) {
                    // ignore
                }
            }
        }

        if (jobUuids.isEmpty()) {
            return;
        }

        linkJobUuidsToInvoice(con, invoiceUuid, jobUuids, "Invoice Drafted");
    }

    /**
     * Sets {@code jobs.invoice_uuid} and ensures {@code invoice_job_mapping} rows exist.
     */
    public void linkJobUuidsToInvoice(java.sql.Connection con, String invoiceUuid, List<String> jobUuids,
            String jobStatus) {
        if (invoiceUuid == null || invoiceUuid.isBlank() || jobUuids == null || jobUuids.isEmpty()) {
            return;
        }

        // Verify that none of the jobUuids are cancelled
        for (String jobUuid : jobUuids) {
            String checkSql = "SELECT status FROM jobs WHERE uuid = ?";
            try (java.sql.PreparedStatement psCheck = con.prepareStatement(checkSql)) {
                psCheck.setString(1, jobUuid);
                try (java.sql.ResultSet rs = psCheck.executeQuery()) {
                    if (rs.next()) {
                        String st = rs.getString(1);
                        if ("Cancelled".equalsIgnoreCase(st)) {
                            throw new RuntimeException("Cancelled jobs cannot be linked to invoices or invoiced again.");
                        }
                    }
                }
            } catch (java.sql.SQLException ex) {
                throw new RuntimeException("Error checking job status: " + ex.getMessage(), ex);
            }
        }

        String status = jobStatus != null && !jobStatus.isBlank() ? jobStatus.trim() : "Invoice Drafted";
        String placeholders = jobUuids.stream().map(i -> "?").collect(java.util.stream.Collectors.joining(","));
        String updateJobsSql = "UPDATE jobs SET invoice_uuid = ?, status = ?, sync_status = 'PENDING', "
                + "sync_version = COALESCE(sync_version, 0) + 1, updated_at = datetime('now') WHERE uuid IN ("
                + placeholders + ")";
        try (java.sql.PreparedStatement psUpdate = con.prepareStatement(updateJobsSql)) {
            psUpdate.setString(1, invoiceUuid);
            psUpdate.setString(2, status);
            int idx = 3;
            for (String jobUuid : jobUuids) {
                psUpdate.setString(idx++, jobUuid);
            }
            psUpdate.executeUpdate();

            for (String jobUuid : jobUuids) {
                insertInvoiceJobMapping(con, invoiceUuid, jobUuid);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to link jobs to invoice: " + e.getMessage(), e);
        }
    }

    /**
     * Junction row for invoice ↔ job (requires uuid PK for SQLite + Supabase sync).
     */
    public static void insertInvoiceJobMapping(java.sql.Connection con, String invoiceUuid, String jobUuid)
            throws SQLException {
        if (invoiceUuid == null || invoiceUuid.isBlank() || jobUuid == null || jobUuid.isBlank()) {
            return;
        }
        String sql = """
                INSERT INTO invoice_job_mapping (
                  uuid, invoice_uuid, job_uuid, sync_status, sync_version, is_deleted, is_active, created_at, updated_at
                ) VALUES (?, ?, ?, 'PENDING', 1, 0, 1, datetime('now'), datetime('now'))
                ON CONFLICT(invoice_uuid, job_uuid) DO UPDATE SET
                  sync_status = 'PENDING',
                  sync_version = COALESCE(invoice_job_mapping.sync_version, 1) + 1,
                  updated_at = datetime('now')
                """;
        try (java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, ClientIdentifiers.newUuidV7String());
            ps.setString(2, invoiceUuid.trim());
            ps.setString(3, jobUuid.trim());
            ps.executeUpdate();
        }
    }

    /** Remove one job from an invoice mapping (local + marks job for re-sync). */
    public static void deleteInvoiceJobMapping(java.sql.Connection con, String invoiceUuid, String jobUuid)
            throws SQLException {
        if (jobUuid == null || jobUuid.isBlank()) {
            return;
        }

        String sql = "UPDATE invoice_job_mapping SET is_deleted = 1, sync_status = 'PENDING', updated_at = datetime('now') WHERE job_uuid = ?"
                + (invoiceUuid != null && !invoiceUuid.isBlank() ? " AND invoice_uuid = ?" : "");

        try (java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, jobUuid.trim());
            if (invoiceUuid != null && !invoiceUuid.isBlank()) {
                ps.setString(2, invoiceUuid.trim());
            }
            ps.executeUpdate();
        }
    }

    private static String resolveInvoiceUuid(java.sql.Connection con, int invoiceId) {
        try (java.sql.PreparedStatement ps = con.prepareStatement(
                "SELECT uuid FROM invoice_master WHERE id = ?")) {
            ps.setInt(1, invoiceId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("uuid");
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to resolve invoice uuid: " + e.getMessage());
        }
        return null;
    }

    public void deleteEmptyInvoices(java.sql.Connection con) {
        // Find empty draft invoices and cancel them instead of deleting
        String selectSql = "SELECT uuid FROM invoice_master WHERE status = 'DRAFT' AND uuid NOT IN (SELECT invoice_uuid FROM invoice_job_mapping WHERE invoice_uuid IS NOT NULL AND TRIM(invoice_uuid) <> '' UNION SELECT invoice_uuid FROM jobs WHERE invoice_uuid IS NOT NULL AND TRIM(invoice_uuid) <> '')";
        try (java.sql.PreparedStatement ps = con.prepareStatement(selectSql);
             java.sql.ResultSet rs = ps.executeQuery()) {
            java.util.List<String> uuids = new java.util.ArrayList<>();
            while (rs.next()) uuids.add(rs.getString(1));
            
            String updateInvStatusSql = "UPDATE invoice_master SET status = 'CANCELLED', payment_status = 'Void', amount = 0, due_amount = 0, sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?";
            try (java.sql.PreparedStatement psUpdate = con.prepareStatement(updateInvStatusSql)) {
                for (String u : uuids) {
                    psUpdate.setString(1, u);
                    psUpdate.addBatch();
                }
                psUpdate.executeBatch();
            }
        } catch (Exception e) {
            System.err.println("Failed to cancel empty invoices: " + e.getMessage());
        }
    }

    private void unlinkJobsFromInvoice(java.sql.Connection con, String invoiceUuid) {
        if (invoiceUuid == null || invoiceUuid.isBlank()) {
            return;
        }

        String type = "";
        try (java.sql.PreparedStatement psType = con.prepareStatement("SELECT type FROM invoice_master WHERE uuid = ?")) {
            psType.setString(1, invoiceUuid);
            try (java.sql.ResultSet rs = psType.executeQuery()) {
                if (rs.next()) type = rs.getString(1);
            }
        } catch (Exception ignore) {}

        String targetStatus = "Performa Bills".equalsIgnoreCase(type) ? "Cancelled" : "Completed";
        String updateJobsSql = "UPDATE jobs SET invoice_uuid = NULL, status = ?, sync_status = 'PENDING', sync_version = COALESCE(sync_version, 0) + 1, updated_at = datetime('now') WHERE invoice_uuid = ?";
        
        String deleteMappingSql = "UPDATE invoice_job_mapping SET is_deleted = 1, sync_status = 'PENDING', updated_at = datetime('now') WHERE invoice_uuid = ?";

        try (java.sql.PreparedStatement psUpdate = con.prepareStatement(updateJobsSql);
             java.sql.PreparedStatement psDelMap = con.prepareStatement(deleteMappingSql)) {

            psUpdate.setString(1, targetStatus);
            psUpdate.setString(2, invoiceUuid);
            psUpdate.executeUpdate();

            psDelMap.setString(1, invoiceUuid);
            psDelMap.executeUpdate();
            
            deleteEmptyInvoices(con);
            
        } catch (Exception e) {
            System.err.println("Failed to unlink jobs from invoice: " + e.getMessage());
        }
    }

    private void releaseJobsKeepHistory(java.sql.Connection con, String invoiceUuid) {
        if (invoiceUuid == null || invoiceUuid.isBlank()) {
            return;
        }
        String type = "";
        try (java.sql.PreparedStatement psType = con.prepareStatement("SELECT type FROM invoice_master WHERE uuid = ?")) {
            psType.setString(1, invoiceUuid);
            try (java.sql.ResultSet rs = psType.executeQuery()) {
                if (rs.next()) type = rs.getString(1);
            }
        } catch (Exception ignore) {}

        String targetStatus = "Performa Bills".equalsIgnoreCase(type) ? "Cancelled" : "Completed";
        String updateJobsSql = "UPDATE jobs SET invoice_uuid = NULL, status = ?, sync_status = 'PENDING', sync_version = COALESCE(sync_version, 0) + 1, updated_at = datetime('now') WHERE invoice_uuid = ?";
        try (java.sql.PreparedStatement psUpdate = con.prepareStatement(updateJobsSql)) {
            psUpdate.setString(1, targetStatus);
            psUpdate.setString(2, invoiceUuid);
            psUpdate.executeUpdate();
        } catch (Exception e) {
            System.err.println("Failed to release jobs for history: " + e.getMessage());
        }
    }

    /*
     * =========================================================
     * MARK SENT
     * =========================================================
     */
    public void markSent(String invoiceUuid) {
        AtomicDB.runVoid(con -> repo.updatePayment(con, invoiceUuid, 0, 0, "SENT", LocalDate.now()));
    }

    /*
     * =========================================================
     * REGISTER PAYMENT / PARTIAL / FULL
     * =========================================================
     */
    public void registerPayment(String invoiceUuid, double amount, double totalDue) {
        AtomicDB.runVoid(con -> {
            String status = amount >= totalDue ? "PAID" : "PARTIAL_PAID";
            repo.updatePayment(
                    con,
                    invoiceUuid,
                    amount,
                    totalDue - amount,
                    status,
                    LocalDate.now());
        });
    }

    /*
     * =========================================================
     * REGISTER MONTHLY INVOICES (no duplicate insert, update file path on
     * regenerate)
     * Same behavior as date range: one invoice per client/period, regeneration
     * updates file path
     * =========================================================
     */
    public void registerMonthlyInvoices(
            Map<String, Invoice> invoiceMap,
            LocalDate from,
            LocalDate to,
            String type,
            String filePath) {

        AtomicDB.runVoid(con -> {

            for (Invoice invoice : invoiceMap.values()) {
                // Skip clients with no jobs (Enforce: No empty invoices)
                if (invoice.getJobs() == null || invoice.getJobs().isEmpty()) {
                    continue;
                }

                // Find the master created earlier in this session (by invoice_no)
                InvoiceMaster existing = repo.findByInvoiceNo(con, invoice.getInvoiceNo());

                if (existing != null) {
                    // Link jobs to the draft created earlier
                    linkJobsToInvoice(con, existing.getUuid(), invoice);
                    continue;
                }

                // 🔥 create NEW invoice master entry
                InvoiceMaster inv = new InvoiceMaster(
                        invoice.getInvoiceNo(),
                        invoice.getClientId(),
                        invoice.getClientName(),
                        invoice.getInvoiceDate(),
                        invoice.getGrandTotal(),
                        type,
                        "DRAFT");

                inv.setPeriodFrom(from);
                inv.setPeriodTo(to);
                inv.setFilePath(filePath);
                inv.setDocumentSeries(invoice.getMasterDocumentSeries().name());

                repo.insert(con, inv);
                linkJobsToInvoice(con, inv.getUuid(), invoice);
            }
            deleteEmptyInvoices(con);
        });
    }

    /*
     * =========================================================
     * VOID INVOICE
     * =========================================================
     */
    public void voidInvoice(String invoiceUuid, String reason) {
        AtomicDB.runVoid(con -> {
            repo.voidInvoice(con, invoiceUuid, reason, LocalDate.now());
            unlinkJobsFromInvoice(con, invoiceUuid);
        });
    }

    /*
     * =========================================================
     * GET RECENT
     * =========================================================
     */
    public List<InvoiceMaster> getRecentInvoices(int limit) {

        return AtomicDB.run(con -> repo.findRecent(con, limit));
    }

    public InvoiceMaster getInvoiceById(String invoiceUuid) {
        return AtomicDB.run(con -> {
            try {
                return repo.findByUuid(con, invoiceUuid);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public InvoiceMaster getInvoiceByInvoiceNo(String invoiceNo) {
        if (invoiceNo == null || invoiceNo.isBlank()) {
            return null;
        }
        return AtomicDB.run(con -> {
            try {
                return repo.findByInvoiceNo(con, invoiceNo.trim());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /*
     * =========================================================
     * UPDATE INDIVIDUAL STATUSES
     * =========================================================
     */
    public InvoiceMaster getInvoiceByUuid(String uuid) {
        return AtomicDB.run(con -> repo.findByUuid(con, uuid));
    }

    public void unlinkJobsAndRecalculateProforma(String invoiceUuid, List<String> jobUuids) {
        AtomicDB.runVoid(con -> {
            String updateJobsSql = "UPDATE jobs SET invoice_uuid = NULL, status = CASE WHEN status = 'Cancelled' THEN 'Cancelled' ELSE 'Completed' END, sync_status = 'PENDING', sync_version = COALESCE(sync_version, 0) + 1, updated_at = datetime('now') WHERE uuid IN (" + 
                jobUuids.stream().map(u -> "?").collect(java.util.stream.Collectors.joining(",")) + ")";
            try (java.sql.PreparedStatement ps = con.prepareStatement(updateJobsSql)) {
                int idx = 1;
                for (String u : jobUuids) ps.setString(idx++, u);
                ps.executeUpdate();
            }
            for (String jobUuid : jobUuids) {
                deleteInvoiceJobMapping(con, invoiceUuid, jobUuid);
            }
            recalculateInvoiceTotals(con, invoiceUuid);
        });
    }

    public void deallocatePaymentsForInvoice(String invoiceUuid) {
        AtomicDB.runVoid(con -> {
            String sql = "UPDATE payment_allocations SET is_deleted = 1, sync_status = 'PENDING', updated_at = datetime('now') WHERE invoice_uuid = ?";
            try (java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, invoiceUuid);
                ps.executeUpdate();
            }
            String updateInv = "UPDATE invoice_master SET paid_amount = 0, due_amount = amount, sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?";
            try (java.sql.PreparedStatement ps = con.prepareStatement(updateInv)) {
                ps.setString(1, invoiceUuid);
                ps.executeUpdate();
            }
        });
    }

    public void refundAdvanceForInvoice(String invoiceUuid, String clientUuid, double amount) {
        AtomicDB.runVoid(con -> {
            String paymentUuid = ClientIdentifiers.newUuidV7String();
            String allocUuid = ClientIdentifiers.newUuidV7String();
            String todayStr = LocalDate.now().toString();

            String sqlPay = "INSERT INTO payments (uuid, client_uuid, amount, payment_date, method, type, sync_status, sync_version, is_deleted, is_active, created_at, updated_at) VALUES (?,?,?,?,'Cash','Refund','PENDING',1,0,1,datetime('now'),datetime('now'))";
            try (java.sql.PreparedStatement ps = con.prepareStatement(sqlPay)) {
                ps.setString(1, paymentUuid);
                ps.setString(2, clientUuid);
                ps.setDouble(3, -amount);
                ps.setString(4, todayStr);
                ps.executeUpdate();
            }

            String sqlAlloc = "INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount, sync_status, created_at, updated_at) VALUES (?,?,?,?,'PENDING',datetime('now'),datetime('now'))";
            try (java.sql.PreparedStatement ps = con.prepareStatement(sqlAlloc)) {
                ps.setString(1, allocUuid);
                ps.setString(2, paymentUuid);
                ps.setString(3, invoiceUuid);
                ps.setDouble(4, -amount);
                ps.executeUpdate();
            }

            String updateInv = "UPDATE invoice_master SET paid_amount = paid_amount - ?, due_amount = due_amount + ?, sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?";
            try (java.sql.PreparedStatement ps = con.prepareStatement(updateInv)) {
                ps.setDouble(1, amount);
                ps.setDouble(2, amount);
                ps.setString(3, invoiceUuid);
                ps.executeUpdate();
            }
        });
    }

    private void adjustAllocationsForExcess(java.sql.Connection con, String invoiceUuid, double excess) throws SQLException {
        String sql = "SELECT uuid, allocated_amount FROM payment_allocations WHERE invoice_uuid = ? AND COALESCE(is_deleted,0) = 0 ORDER BY created_at DESC";
        double remainingExcess = excess;
        try (java.sql.PreparedStatement ps = con.prepareStatement(sql);
             java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next() && remainingExcess > 0) {
                String allocUuid = rs.getString("uuid");
                double currentAlloc = rs.getDouble("allocated_amount");
                if (currentAlloc >= remainingExcess) {
                    double newAlloc = currentAlloc - remainingExcess;
                    String updateSql = "UPDATE payment_allocations SET allocated_amount = ?, sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?";
                    try (java.sql.PreparedStatement psU = con.prepareStatement(updateSql)) {
                        psU.setDouble(1, newAlloc);
                        psU.setString(2, allocUuid);
                        psU.executeUpdate();
                    }
                    remainingExcess = 0;
                } else {
                    remainingExcess -= currentAlloc;
                    String deleteSql = "UPDATE payment_allocations SET is_deleted = 1, sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?";
                    try (java.sql.PreparedStatement psD = con.prepareStatement(deleteSql)) {
                        psD.setString(1, allocUuid);
                        psD.executeUpdate();
                    }
                }
            }
        }
        String updatePaid = "UPDATE invoice_master SET paid_amount = paid_amount - ? WHERE uuid = ?";
        try (java.sql.PreparedStatement ps = con.prepareStatement(updatePaid)) {
            ps.setDouble(1, excess);
            ps.setString(2, invoiceUuid);
            ps.executeUpdate();
        }
    }

    private static String extractStateCode(String s) {
        if (s == null)
            return "";
        String trimmed = s.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\((\\d{2})\\)").matcher(trimmed);
        if (m.find())
            return m.group(1);
        m = java.util.regex.Pattern.compile("^(\\d{2})").matcher(trimmed);
        if (m.find())
            return m.group(1);
        return "";
    }

    public void recalculateInvoiceTotals(java.sql.Connection con, String invoiceUuid) throws Exception {
        InvoiceMaster inv = repo.findByUuid(con, invoiceUuid);
        if (inv == null) {
            return;
        }

        boolean isGst = model.MasterDocumentSeries.GST_INVOICE.name().equals(inv.getDocumentSeries()) || !"Performa Bills".equalsIgnoreCase(inv.getType());

        boolean intra = true;
        repository.ClientRepository clientRepo = new repository.ClientRepository();
        model.Client client = clientRepo.findByUuid(inv.getClientId());
        if (client != null) {
            String companyGst = utils.CompanyProfile.getGst();
            String buyerGst = client.getGst();
            String companyCode = extractStateCode(companyGst);
            String buyerCode = extractStateCode(buyerGst);
            if (!companyCode.isEmpty() && !buyerCode.isEmpty()) {
                intra = companyCode.equals(buyerCode);
            }
        }

        double taxable = 0.0;
        double totalTax = 0.0;

        String jobsSql = "SELECT uuid FROM jobs WHERE status <> 'Cancelled' AND (invoice_uuid = ? OR uuid IN (SELECT job_uuid FROM invoice_job_mapping WHERE invoice_uuid = ? AND COALESCE(is_deleted, 0) = 0))";
        java.util.List<String> activeJobUuids = new java.util.ArrayList<>();
        try (java.sql.PreparedStatement ps = con.prepareStatement(jobsSql)) {
            ps.setString(1, invoiceUuid);
            ps.setString(2, invoiceUuid);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    activeJobUuids.add(rs.getString(1));
                }
            }
        }

        if (activeJobUuids.isEmpty()) {
            String updateInvStatusSql = "UPDATE invoice_master SET status = 'CANCELLED', payment_status = 'Void', amount = 0, due_amount = 0, sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?";
            try (java.sql.PreparedStatement ps = con.prepareStatement(updateInvStatusSql)) {
                ps.setString(1, invoiceUuid);
                ps.executeUpdate();
            }
            return;
        }

        repository.HsnSacRepository hsnRepo = new repository.HsnSacRepository();
        for (String jobUuid : activeJobUuids) {
            String itemsSql = "SELECT type, description, amount FROM job_items WHERE job_uuid = ? AND COALESCE(is_deleted, 0) = 0";
            try (java.sql.PreparedStatement ps = con.prepareStatement(itemsSql)) {
                ps.setString(1, jobUuid);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String type = rs.getString("type");
                        String desc = rs.getString("description");
                        double amt = rs.getDouble("amount");
                        taxable += amt;

                        double rate = 0.18;
                        model.HsnSacInfo hsnInfo = hsnRepo.findBestMatch(con, type, desc);
                        if (hsnInfo != null && hsnInfo.getGstRate() > 0) {
                            rate = hsnInfo.getGstRate();
                        }

                        if (intra) {
                            double halfRate = rate / 2.0;
                            double cgst = Math.round(amt * halfRate * 100.0) / 100.0;
                            double sgst = Math.round(amt * halfRate * 100.0) / 100.0;
                            totalTax += (cgst + sgst);
                        } else {
                            double igst = Math.round(amt * rate * 100.0) / 100.0;
                            totalTax += igst;
                        }
                    }
                }
            }
        }

        taxable = Math.round(taxable * 100.0) / 100.0;
        totalTax = Math.round(totalTax * 100.0) / 100.0;

        double newAmount;
        if (isGst) {
            newAmount = Math.round(taxable + totalTax);
        } else {
            newAmount = taxable;
        }

        if (inv.getPaidAmount() > newAmount) {
            double excess = inv.getPaidAmount() - newAmount;
            adjustAllocationsForExcess(con, invoiceUuid, excess);
        }

        double paidAmount = inv.getPaidAmount();
        if (paidAmount > newAmount) {
            paidAmount = newAmount; // Excess adjusted
        }

        // Fetch adjustments
        double adjustments = 0.0;
        String adjSql = """
            SELECT (SELECT COALESCE(SUM(amount), 0) FROM invoice_adjustments WHERE invoice_uuid = ? AND type = 'Debit Note')
                 - (SELECT COALESCE(SUM(amount), 0) FROM invoice_adjustments WHERE invoice_uuid = ? AND type = 'Credit Note')
        """;
        try (java.sql.PreparedStatement psAdj = con.prepareStatement(adjSql)) {
            psAdj.setString(1, invoiceUuid);
            psAdj.setString(2, invoiceUuid);
            try (java.sql.ResultSet rs = psAdj.executeQuery()) {
                if (rs.next()) {
                    adjustments = rs.getDouble(1);
                }
            }
        }

        double newDue = newAmount + adjustments - paidAmount;
        String newPayStatus;
        if ("REFUND_PENDING".equals(inv.getPaymentStatus())) {
            newPayStatus = "REFUND_PENDING";
        } else if (newDue <= 0.0001) {
            newPayStatus = "PAID";
            newDue = 0.0;
        } else if (paidAmount > 0.0001) {
            newPayStatus = "PARTIAL PAID";
        } else {
            newPayStatus = "UNPAID";
        }

        String updateSql = """
            UPDATE invoice_master SET
              amount = ?,
              due_amount = ?,
              payment_status = ?,
              sync_status = 'PENDING',
              updated_at = datetime('now')
            WHERE uuid = ?
        """;
        try (java.sql.PreparedStatement ps = con.prepareStatement(updateSql)) {
            ps.setDouble(1, newAmount);
            ps.setDouble(2, newDue);
            ps.setString(3, newPayStatus);
            ps.setString(4, invoiceUuid);
            ps.executeUpdate();
        }

        // Automatically regenerate PDF file if one is already associated
        if (inv.getFilePath() != null && !inv.getFilePath().isBlank()) {
            try {
                service.InvoiceBuilderService builder = new service.InvoiceBuilderService();
                model.Invoice full = builder.buildInvoiceFromMasterForPdfExport(invoiceUuid);
                if (model.MasterDocumentSeries.GST_INVOICE == full.getMasterDocumentSeries()) {
                    new service.GstPdfInvoiceService().generateGstInvoice(full);
                } else {
                    new service.PdfInvoiceService().generateSingleInvoicePDF(full);
                }
            } catch (Exception ex) {
                System.err.println("Failed to regenerate invoice PDF: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    public void reallocatePaymentsOnJobCancellation(java.sql.Connection con, String invoiceUuid, List<String> cancelledJobUuids, String reason, String cancelledBy) throws Exception {
        if (invoiceUuid == null || invoiceUuid.isBlank() || cancelledJobUuids == null || cancelledJobUuids.isEmpty()) {
            return;
        }

        InvoiceMaster inv = repo.findByUuid(con, invoiceUuid);
        if (inv == null) {
            return;
        }

        boolean isGst = model.MasterDocumentSeries.GST_INVOICE.name().equals(inv.getDocumentSeries()) || !"Performa Bills".equalsIgnoreCase(inv.getType());

        boolean intra = true;
        repository.ClientRepository clientRepo = new repository.ClientRepository();
        model.Client client = clientRepo.findByUuid(inv.getClientId());
        if (client != null) {
            String companyGst = utils.CompanyProfile.getGst();
            String buyerGst = client.getGst();
            String companyCode = extractStateCode(companyGst);
            String buyerCode = extractStateCode(buyerGst);
            if (!companyCode.isEmpty() && !buyerCode.isEmpty()) {
                intra = companyCode.equals(buyerCode);
            }
        }

        // Get all active jobs before cancellation
        String jobsSql = "SELECT uuid FROM jobs WHERE status <> 'Cancelled' AND (invoice_uuid = ? OR uuid IN (SELECT job_uuid FROM invoice_job_mapping WHERE invoice_uuid = ? AND COALESCE(is_deleted, 0) = 0))";
        java.util.List<String> allJobUuids = new java.util.ArrayList<>();
        try (java.sql.PreparedStatement ps = con.prepareStatement(jobsSql)) {
            ps.setString(1, invoiceUuid);
            ps.setString(2, invoiceUuid);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    allJobUuids.add(rs.getString(1));
                }
            }
        }

        if (allJobUuids.isEmpty()) {
            return;
        }

        // Calculate each job's total, taxable, and GST amounts
        repository.HsnSacRepository hsnRepo = new repository.HsnSacRepository();
        java.util.Map<String, Double> jobTotals = new java.util.HashMap<>();
        java.util.Map<String, JobTaxDetails> jobTaxMap = new java.util.HashMap<>();
        double originalCalculatedTotal = 0.0;
        for (String jobUuid : allJobUuids) {
            JobTaxDetails details = getJobTaxDetails(con, jobUuid, isGst, intra, hsnRepo);
            double jobTotal = isGst ? Math.round(details.taxable + details.gst) : details.taxable;
            jobTotals.put(jobUuid, jobTotal);
            jobTaxMap.put(jobUuid, details);
            originalCalculatedTotal += jobTotal;
        }

        double originalPaidAmount = inv.getPaidAmount();

        // Calculate proportional allocation for each job
        java.util.Map<String, Double> allocations = new java.util.HashMap<>();
        double totalFreedPayment = 0.0;
        for (String jobUuid : allJobUuids) {
            double jobTotal = jobTotals.getOrDefault(jobUuid, 0.0);
            double allocated = 0.0;
            if (originalCalculatedTotal > 0) {
                allocated = (jobTotal / originalCalculatedTotal) * originalPaidAmount;
            }
            allocated = Math.min(allocated, jobTotal);
            allocations.put(jobUuid, allocated);

            if (cancelledJobUuids.contains(jobUuid)) {
                totalFreedPayment += allocated;
            }
        }

        // Identify remaining jobs and categorize them
        java.util.List<String> remainingJobUuids = allJobUuids.stream()
                .filter(u -> !cancelledJobUuids.contains(u))
                .toList();

        double reallocatedSum = 0.0;
        double refundPendingSum = 0.0;
        boolean refundPending = false;

        if (!remainingJobUuids.isEmpty()) {
            java.util.List<String> unpaidJobs = new java.util.ArrayList<>();
            java.util.List<String> partiallyPaidJobs = new java.util.ArrayList<>();

            for (String jobUuid : remainingJobUuids) {
                double alloc = allocations.getOrDefault(jobUuid, 0.0);
                double total = jobTotals.getOrDefault(jobUuid, 0.0);
                if (alloc <= 0.0001) {
                    unpaidJobs.add(jobUuid);
                } else if (alloc < total - 0.0001) {
                    partiallyPaidJobs.add(jobUuid);
                }
            }

            double remainingFreed = totalFreedPayment;

            // 1. Reallocate to unpaid jobs
            for (String jobUuid : unpaidJobs) {
                if (remainingFreed <= 0) break;
                double total = jobTotals.getOrDefault(jobUuid, 0.0);
                double needed = total;
                double toAlloc = Math.min(remainingFreed, needed);
                allocations.put(jobUuid, toAlloc);
                reallocatedSum += toAlloc;
                remainingFreed -= toAlloc;
            }

            // 2. Reallocate to partially paid jobs
            for (String jobUuid : partiallyPaidJobs) {
                if (remainingFreed <= 0) break;
                double total = jobTotals.getOrDefault(jobUuid, 0.0);
                double currentAlloc = allocations.getOrDefault(jobUuid, 0.0);
                double needed = total - currentAlloc;
                double toAlloc = Math.min(remainingFreed, needed);
                allocations.put(jobUuid, currentAlloc + toAlloc);
                reallocatedSum += toAlloc;
                remainingFreed -= toAlloc;
            }

            refundPendingSum = remainingFreed;
            if (refundPendingSum > 0.0001) {
                refundPending = true;
            }
        } else {
            // No remaining jobs, all freed payments become refund pending
            refundPendingSum = totalFreedPayment;
            if (refundPendingSum > 0.0001) {
                refundPending = true;
            }
        }

        if (refundPending) {
            String paymentUuid = ClientIdentifiers.newUuidV7String();
            String todayStr = LocalDate.now().toString();
            String sqlPay = "INSERT INTO payments (uuid, client_uuid, amount, payment_date, method, type, sync_status, sync_version, is_deleted, is_active, created_at, updated_at) VALUES (?,?,?,?,'Cash','REFUND_PENDING','PENDING',1,0,1,datetime('now'),datetime('now'))";
            try (java.sql.PreparedStatement ps = con.prepareStatement(sqlPay)) {
                ps.setString(1, paymentUuid);
                ps.setString(2, inv.getClientId());
                ps.setDouble(3, -refundPendingSum);
                ps.setString(4, todayStr);
                ps.executeUpdate();
            }
        }

        // Calculate proportional audit splits for each cancelled job
        double reallocationRatio = totalFreedPayment > 0.0001 ? reallocatedSum / totalFreedPayment : 0.0;
        String insertAuditSql = """
            INSERT INTO job_cancellation_audit (
                uuid, job_uuid, cancellation_reason, cancelled_by, cancelled_at,
                original_invoice_uuid, original_job_amount, original_gst_amount,
                reallocated_amount, refund_pending_amount, sync_status, sync_version,
                is_deleted, is_active, created_at, updated_at
            ) VALUES (?, ?, ?, ?, datetime('now'), ?, ?, ?, ?, ?, 'PENDING', 1, 0, 1, datetime('now'), datetime('now'))
        """;

        try (java.sql.PreparedStatement psAudit = con.prepareStatement(insertAuditSql)) {
            for (String jobUuid : cancelledJobUuids) {
                JobTaxDetails details = jobTaxMap.getOrDefault(jobUuid, new JobTaxDetails());
                double freedAlloc = allocations.getOrDefault(jobUuid, 0.0);
                double reallocatedAmount = freedAlloc * reallocationRatio;
                double refundPendingAmount = freedAlloc - reallocatedAmount;

                psAudit.setString(1, ClientIdentifiers.newUuidV7String());
                psAudit.setString(2, jobUuid);
                psAudit.setString(3, reason);
                psAudit.setString(4, cancelledBy);
                psAudit.setString(5, invoiceUuid);
                psAudit.setDouble(6, details.taxable);
                psAudit.setDouble(7, details.gst);
                psAudit.setDouble(8, reallocatedAmount);
                psAudit.setDouble(9, refundPendingAmount);
                psAudit.addBatch();
            }
            psAudit.executeBatch();
        }

        // Calculate new paid amount based on allocations of remaining jobs
        double newPaidAmount = 0.0;
        for (String jobUuid : remainingJobUuids) {
            newPaidAmount += allocations.getOrDefault(jobUuid, 0.0);
        }

        // Capped by new invoice total
        double newInvoiceAmount = 0.0;
        for (String jobUuid : remainingJobUuids) {
            newInvoiceAmount += jobTotals.getOrDefault(jobUuid, 0.0);
        }

        if (newPaidAmount > newInvoiceAmount) {
            newPaidAmount = newInvoiceAmount;
        }

        // Update paid amount of invoice_master (the due amount and status will be updated next in recalculateInvoiceTotals)
        if (refundPending) {
            String updatePaidSql = "UPDATE invoice_master SET paid_amount = ?, payment_status = 'REFUND_PENDING', sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?";
            try (java.sql.PreparedStatement ps = con.prepareStatement(updatePaidSql)) {
                ps.setDouble(1, newPaidAmount);
                ps.setString(2, invoiceUuid);
                ps.executeUpdate();
            }
        } else {
            String updatePaidSql = "UPDATE invoice_master SET paid_amount = ?, sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?";
            try (java.sql.PreparedStatement ps = con.prepareStatement(updatePaidSql)) {
                ps.setDouble(1, newPaidAmount);
                ps.setString(2, invoiceUuid);
                ps.executeUpdate();
            }
        }
    }

    private static class JobTaxDetails {
        double taxable = 0.0;
        double gst = 0.0;
    }

    private JobTaxDetails getJobTaxDetails(java.sql.Connection con, String jobUuid, boolean isGst, boolean intra, repository.HsnSacRepository hsnRepo) throws Exception {
        JobTaxDetails details = new JobTaxDetails();
        String itemsSql = "SELECT type, description, amount FROM job_items WHERE job_uuid = ? AND COALESCE(is_deleted, 0) = 0";
        try (java.sql.PreparedStatement ps = con.prepareStatement(itemsSql)) {
            ps.setString(1, jobUuid);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("type");
                    String desc = rs.getString("description");
                    double amt = rs.getDouble("amount");
                    details.taxable += amt;

                    if (isGst) {
                        double rate = 0.18;
                        model.HsnSacInfo hsnInfo = hsnRepo.findBestMatch(con, type, desc);
                        if (hsnInfo != null && hsnInfo.getGstRate() > 0) {
                            rate = hsnInfo.getGstRate();
                        }

                        if (intra) {
                            double halfRate = rate / 2.0;
                            double cgst = Math.round(amt * halfRate * 100.0) / 100.0;
                            double sgst = Math.round(amt * halfRate * 100.0) / 100.0;
                            details.gst += (cgst + sgst);
                        } else {
                            double igst = Math.round(amt * rate * 100.0) / 100.0;
                            details.gst += igst;
                        }
                    }
                }
            }
        }
        details.taxable = Math.round(details.taxable * 100.0) / 100.0;
        details.gst = Math.round(details.gst * 100.0) / 100.0;
        return details;
    }

    private double calculateJobTotalWithTax(java.sql.Connection con, String jobUuid, boolean isGst, boolean intra, repository.HsnSacRepository hsnRepo) throws Exception {
        double taxable = 0.0;
        double totalTax = 0.0;
        String itemsSql = "SELECT type, description, amount FROM job_items WHERE job_uuid = ? AND COALESCE(is_deleted, 0) = 0";
        try (java.sql.PreparedStatement ps = con.prepareStatement(itemsSql)) {
            ps.setString(1, jobUuid);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("type");
                    String desc = rs.getString("description");
                    double amt = rs.getDouble("amount");
                    taxable += amt;

                    if (isGst) {
                        double rate = 0.18;
                        model.HsnSacInfo hsnInfo = hsnRepo.findBestMatch(con, type, desc);
                        if (hsnInfo != null && hsnInfo.getGstRate() > 0) {
                            rate = hsnInfo.getGstRate();
                        }

                        if (intra) {
                            double halfRate = rate / 2.0;
                            double cgst = Math.round(amt * halfRate * 100.0) / 100.0;
                            double sgst = Math.round(amt * halfRate * 100.0) / 100.0;
                            totalTax += (cgst + sgst);
                        } else {
                            double igst = Math.round(amt * rate * 100.0) / 100.0;
                            totalTax += igst;
                        }
                    }
                }
            }
        }
        taxable = Math.round(taxable * 100.0) / 100.0;
        totalTax = Math.round(totalTax * 100.0) / 100.0;
        return isGst ? Math.round(taxable + totalTax) : taxable;
    }


    public void updateInvoiceStatus(String invoiceUuid, String newStatus) {
        AtomicDB.runVoid(con -> {
            InvoiceMaster inv = repo.findByUuid(con, invoiceUuid);
            if (inv != null) {
                if ("CANCELLED".equalsIgnoreCase(newStatus)) {
                    String invNo = inv.getInvoiceNo();
                    if (!"Performa Bills".equalsIgnoreCase(inv.getType())) {
                        if ("DRAFT".equals(inv.getStatus()) || (invNo != null && invNo.startsWith("TEMP-"))) {
                            unlinkJobsFromInvoice(con, inv.getUuid());
                            repo.deleteInvoice(con, inv.getUuid());
                            return; // Done
                        }
                    }
                }

                inv.setStatus(newStatus);
                if ("CANCELLED".equalsIgnoreCase(newStatus)) {
                    inv.setPaymentStatus("Void");
                    inv.setVoid(false); // Stay visible in table
                    releaseJobsKeepHistory(con, inv.getUuid());
                    inv.setAmount(0);
                    inv.setDueAmount(0);
                } else if ("VOID".equalsIgnoreCase(newStatus)) {
                    inv.setPaymentStatus("VOID");
                    inv.setVoid(true);
                    inv.setVoidReason("User updated status to VOID");
                    inv.setVoidDate(LocalDate.now());
                    repo.updatePayment(con, inv.getUuid(), inv.getPaidAmount(), inv.getDueAmount(), "VOID", LocalDate.now());
                    unlinkJobsFromInvoice(con, inv.getUuid());
                }
                repo.update(con, inv);
            }
        });
    }

    public String finalizeInvoice(String invoiceUuid) {
        String finalNo = AtomicDB.run(con -> {
            InvoiceMaster inv = repo.findByUuid(con, invoiceUuid);
            if (inv == null) {
                throw new RuntimeException("Invoice not found: " + invoiceUuid);
            }
            if (!"DRAFT".equals(inv.getStatus())) {
                throw new RuntimeException("Only DRAFT invoices can be finalized");
            }

            String currentNo = inv.getInvoiceNo();
            String resolvedNo;

            if (currentNo != null && currentNo.contains("-R")) {
                resolvedNo = currentNo;
            } else {
                MasterDocumentSeries series = inv.resolveDocumentSeries();
                if (series == MasterDocumentSeries.PROFORMA_INVOICE) {
                    if (api.supabase.SupabaseReachability.isReachable()) {
                        if (!numberAllocator.isRemoteReachable("proforma_invoice")) {
                            throw new RuntimeException("Cannot finalize: Supabase number sequence endpoint for Proforma Invoice is not accessible.");
                        }
                        var permanent = numberAllocator.tryAllocatePermanentInvoice(con, series, inv.getInvoiceDate());
                        if (permanent.isPresent()) {
                            resolvedNo = permanent.get().value();
                        } else {
                            throw new RuntimeException("Cannot finalize: Failed to allocate a permanent Proforma Invoice number from Supabase.");
                        }
                    } else {
                        if (currentNo != null && DocumentNumbering.isTemporaryNumber(currentNo)) {
                            resolvedNo = currentNo;
                        } else {
                            AllocatedNumber fallback = service.sync.UniversalTemporaryNumberEngine.getInstance().allocateTemporary(con, "proforma_invoice");
                            resolvedNo = fallback.value();
                        }
                    }
                } else {
                    var permanent = numberAllocator.tryAllocatePermanentInvoice(con, series, inv.getInvoiceDate());
                    if (permanent.isPresent()) {
                        resolvedNo = permanent.get().value();
                    } else {
                        if (currentNo != null && DocumentNumbering.isTemporaryNumber(currentNo)) {
                            resolvedNo = currentNo;
                        } else {
                            AllocatedNumber fallback = numberAllocator.allocateInvoiceNumber(con, series, inv.getInvoiceDate());
                            resolvedNo = fallback.value();
                        }
                    }
                }
            }

            inv.setInvoiceNo(resolvedNo);
            inv.setStatus("FINAL");
            inv.setSyncStatus("PENDING");
            repo.update(con, inv);

            updateJobsStatusByInvoice(con, invoiceUuid, "Invoiced");

            return resolvedNo;
        });
        UniversalSyncEngine.scheduleSyncAsync();
        return finalNo;
    }

    private void updateJobsStatusByInvoice(java.sql.Connection con, String invoiceUuid, String status) {
        if (invoiceUuid == null || invoiceUuid.isBlank()) {
            return;
        }
        String sql = "UPDATE jobs SET status = ?, sync_status = 'PENDING', sync_version = COALESCE(sync_version, 0) + 1, updated_at = datetime('now') WHERE invoice_uuid = ?";
        try (java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, invoiceUuid);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("Failed to update jobs status for invoice " + invoiceUuid + ": " + e.getMessage());
        }
    }

    public void updateInvoicePaymentStatus(String invoiceUuid, String newPaymentStatus) {
        AtomicDB.runVoid(con -> {
            // Updating just payment_status without affecting other amounts
            String sql = "UPDATE invoice_master SET payment_status = ? WHERE uuid = ?";
            try (java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, newPaymentStatus);
                ps.setString(2, invoiceUuid);
                ps.executeUpdate();
            }
        });
    }

    /*
     * =========================================================
     * GET INVOICES BY CLIENT
     * =========================================================
     */
    public List<InvoiceMaster> getInvoicesByClientId(String clientId) {
        return AtomicDB.run(con -> repo.findByClientId(con, clientId));
    }

    public InvoiceMaster reviseInvoice(InvoiceMaster old) {
        return AtomicDB.run(con -> {
            // 1. Calculate new invoice number
            // If old is INV-100 and has parentInvoiceUuid=50, we should count revisions of 50.
            // If old is the original (parentInvoiceUuid=null), we count revisions of old.uuid.
            String rootUuid = (old.getParentInvoiceUuid() != null) ? old.getParentInvoiceUuid() : old.getUuid();
            int revCount = repo.countRevisions(con, rootUuid);

            String originalNo = old.getInvoiceNo();
            if (originalNo.contains("-R")) {
                originalNo = originalNo.substring(0, originalNo.lastIndexOf("-R"));
            }
            String newNo = originalNo + "-R" + (revCount + 1);

            // 2. Create NEW invoice as DRAFT copy
            InvoiceMaster newInv = new InvoiceMaster();
            newInv.setInvoiceNo(newNo);
            newInv.setClientId(old.getClientId());
            newInv.setClientName(old.getClientName());
            newInv.setAmount(old.getAmount());
            newInv.setType(old.getType());
            newInv.setPeriodFrom(old.getPeriodFrom());
            newInv.setPeriodTo(old.getPeriodTo());
            newInv.setInvoiceDate(LocalDate.now());

            newInv.setStatus("DRAFT");
            newInv.setPaymentStatus("UNPAID");
            newInv.setDueAmount(old.getAmount());
            newInv.setParentInvoiceUuid(rootUuid);
            newInv.setDocumentSeries(old.getDocumentSeries());

            // 2. Mark OLD as REVISED in DB first to clear unique constraint for the new DRAFT
            // 🔥 Requirement: Revised status turning payment status to CLOSED and Dues/Amount to 0
            String clearQuery = "UPDATE invoice_master SET status = 'REVISED', payment_status = 'CLOSED', due_amount = 0, amount = 0 WHERE uuid = ?";
            try (java.sql.PreparedStatement ps = con.prepareStatement(clearQuery)) {
                ps.setString(1, old.getUuid());
                ps.executeUpdate();
                
                // Sync Memory
                old.setStatus("REVISED"); 
                old.setPaymentStatus("CLOSED");
                old.setDueAmount(0);
                old.setAmount(0);
            }

            // 3. Save NEW (period is now free in index because old is REVISED)
            repo.insert(con, newInv);

            // 4. Update OLD with proper link
            old.setReplacedByInvoiceUuid(newInv.getUuid());
            repo.update(con, old);

            // 5. Clone Mappings from OLD to NEW in invoice_job_mapping for history
            String oldUuid = old.getUuid();
            String newUuid = newInv.getUuid();
            if (oldUuid != null && newUuid != null) {
                try (java.sql.PreparedStatement ps = con.prepareStatement(
                        "SELECT job_uuid FROM invoice_job_mapping WHERE invoice_uuid = ?")) {
                    ps.setString(1, oldUuid);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            insertInvoiceJobMapping(con, newUuid, rs.getString("job_uuid"));
                        }
                    }
                }
                String moveJobsSql = "UPDATE jobs SET invoice_uuid = ? WHERE invoice_uuid = ?";
                try (java.sql.PreparedStatement ps = con.prepareStatement(moveJobsSql)) {
                    ps.setString(1, newUuid);
                    ps.setString(2, oldUuid);
                    ps.executeUpdate();
                }
            }

            return newInv;
        });
    }

    /*
     * =========================================================
     * UPDATE FILTERED INVOICES (FOR VIEW INVOICES SCREEN)
     * =========================================================
     */
    public List<InvoiceMaster> getFilteredInvoices(String clientId, String status, LocalDate start, LocalDate end, String invoiceNo, String documentSeries) {
        return AtomicDB.run(con -> repo.findFiltered(con, clientId, status, start, end, invoiceNo, documentSeries));
    }
}