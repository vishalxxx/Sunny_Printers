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
            AllocatedNumber allocated = numberAllocator.allocateInvoiceNumber(con, series, invoice.getInvoiceDate());
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

    public void saveGeneratedInvoice(
            Invoice invoice,
            String type,
            String status,
            String filePath) {
        if (invoice.getJobs() == null || invoice.getJobs().isEmpty()) {
            throw new RuntimeException("Cannot save an empty invoice.");
        }

        AtomicDB.runVoid(con -> {
            MasterDocumentSeries series = invoice.getMasterDocumentSeries();
            if (series == null) {
                series = MasterDocumentSeries.GST_INVOICE;
            }
            AllocatedNumber allocated = numberAllocator.allocateInvoiceNumber(con, series, invoice.getInvoiceDate());
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
        });
        UniversalSyncEngine.scheduleSyncAsync();
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

        List<String> jobUuids = invoice.getJobs().stream()
                .map(model.InvoiceJob::getJobUuid)
                .filter(u -> u != null && !u.isBlank())
                .toList();

        if (jobUuids.isEmpty()) {
            if (invoice.getJobs() != null && !invoice.getJobs().isEmpty()) {
                throw new RuntimeException("Cannot link invoice: job lines are missing job UUIDs.");
            }
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
        try (java.sql.PreparedStatement ps = con.prepareStatement(
                "DELETE FROM invoice_job_mapping WHERE job_uuid = ?"
                        + (invoiceUuid != null && !invoiceUuid.isBlank() ? " AND invoice_uuid = ?" : ""))) {
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
        // Keep drafts that still have jobs linked even when mapping insert failed earlier.
        String sql = """
            DELETE FROM invoice_master
            WHERE status = 'DRAFT'
              AND invoice_no LIKE 'TEMP-%'
              AND uuid NOT IN (
                SELECT invoice_uuid FROM invoice_job_mapping
                WHERE invoice_uuid IS NOT NULL AND TRIM(invoice_uuid) <> ''
                UNION
                SELECT invoice_uuid FROM jobs
                WHERE invoice_uuid IS NOT NULL AND TRIM(invoice_uuid) <> ''
              )
        """;
        try (java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("Failed to cleanup empty invoices: " + e.getMessage());
        }
    }

    private void unlinkJobsFromInvoice(java.sql.Connection con, String invoiceUuid) {
        if (invoiceUuid == null || invoiceUuid.isBlank()) {
            return;
        }
        String updateJobsSql = "UPDATE jobs SET invoice_uuid = NULL, status = 'Completed' WHERE invoice_uuid = ?";
        String deleteMappingSql = "DELETE FROM invoice_job_mapping WHERE invoice_uuid = ?";
        try (java.sql.PreparedStatement psUpdate = con.prepareStatement(updateJobsSql);
             java.sql.PreparedStatement psDelMap = con.prepareStatement(deleteMappingSql)) {

            psUpdate.setString(1, invoiceUuid);
            psUpdate.executeUpdate();

            psDelMap.setString(1, invoiceUuid);
            psDelMap.executeUpdate();
            
            // 🔥 Requirement: if temporary invoice is empty, delete it automatically
            deleteEmptyInvoices(con);
            
        } catch (Exception e) {
            System.err.println("Failed to unlink jobs from invoice: " + e.getMessage());
        }
    }

    private void releaseJobsKeepHistory(java.sql.Connection con, String invoiceUuid) {
        if (invoiceUuid == null || invoiceUuid.isBlank()) {
            return;
        }
        String updateJobsSql = "UPDATE jobs SET invoice_uuid = NULL, status = 'Completed' WHERE invoice_uuid = ?";
        try (java.sql.PreparedStatement psUpdate = con.prepareStatement(updateJobsSql)) {
            psUpdate.setString(1, invoiceUuid);
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
    public void updateInvoiceStatus(String invoiceUuid, String newStatus) {
        AtomicDB.runVoid(con -> {
            InvoiceMaster inv = repo.findByUuid(con, invoiceUuid);
            if (inv != null) {
                // 🔥 Requirement: if temporary (DRAFT/TEMP) cancelled, remove it from DB
                if ("CANCELLED".equalsIgnoreCase(newStatus)) {
                    String invNo = inv.getInvoiceNo();
                    if ("DRAFT".equals(inv.getStatus()) || (invNo != null && invNo.startsWith("TEMP-"))) {
                        unlinkJobsFromInvoice(con, inv.getUuid());
                        repo.deleteInvoice(con, inv.getUuid());
                        return; // Done
                    }
                }

                inv.setStatus(newStatus);
                // If cancelled/void, also update payment status and mark as void for period reuse
                if ("CANCELLED".equalsIgnoreCase(newStatus)) {
                    inv.setPaymentStatus("Void");
                    inv.setVoid(false); // Stay visible in table
                    // 🔥 Requirement: Keep history but release jobs
                    releaseJobsKeepHistory(con, inv.getUuid());
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
        String sql = "UPDATE jobs SET status = ? WHERE invoice_uuid = ?";
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
    public List<InvoiceMaster> getFilteredInvoices(String clientId, String status, LocalDate start, LocalDate end, String invoiceNo) {
        return AtomicDB.run(con -> repo.findFiltered(con, clientId, status, start, end, invoiceNo));
    }
}