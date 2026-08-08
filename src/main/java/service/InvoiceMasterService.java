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
        service.LoggerService.beginOperation("INVOICE-DRAFT");
        try {
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
                    allocated = numberAllocator.allocateTempInvoiceNumber(con);
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
                if (invoice.getTotalAfterTax() != null) {
                    inv.setTotalAfterTax(invoice.getTotalAfterTax());
                } else {
                    inv.setTotalAfterTax(invoice.getGrandTotal());
                }
                if (invoice.getRoundOff() != null) {
                    inv.setRoundOff(invoice.getRoundOff());
                } else {
                    inv.setRoundOff(0.0);
                }

                repo.insert(con, inv);
                return new CreateOrGetResult(inv, true);
            });
            UniversalSyncEngine.scheduleSyncAsync();
            service.LoggerService.endOperation("INVOICE-DRAFT", true, "Invoice No: " + result.master().getInvoiceNo());
            return result;
        } catch (Exception e) {
            service.LoggerService.endOperation("INVOICE-DRAFT", false, "Exception: " + e.getMessage());
            throw e;
        }
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

        String generatedUuid = AtomicDB.runExclusive(con -> {
            MasterDocumentSeries series = invoice.getMasterDocumentSeries();
            if (series == null) {
                series = MasterDocumentSeries.GST_INVOICE;
            }
            AllocatedNumber allocated;
            if ("DRAFT".equalsIgnoreCase(status)) {
                allocated = numberAllocator.allocateTempInvoiceNumber(con);
            } else if (series == MasterDocumentSeries.PROFORMA_INVOICE) {
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
            if (invoice.getTotalAfterTax() != null) {
                inv.setTotalAfterTax(invoice.getTotalAfterTax());
            } else {
                inv.setTotalAfterTax(invoice.getGrandTotal());
            }
            if (invoice.getRoundOff() != null) {
                inv.setRoundOff(invoice.getRoundOff());
            } else {
                inv.setRoundOff(0.0);
            }

            inv.setPlaceOfSupply(invoice.getPlaceOfSupply());
            inv.setPaymentTerms(invoice.getPaymentTerms());
            inv.setDueDate(invoice.getDueDate());
            inv.setVehicleDispatch(invoice.getVehicleDispatch());
            inv.setPoNo(invoice.getPoNo());
            inv.setPoDate(invoice.getPoDate());
            inv.setDispatchThrough(invoice.getDispatchThrough());
            inv.setLrTrackingNo(invoice.getLrTrackingNo());
            inv.setRemarks(invoice.getRemarks());
            inv.setEwayBillNo(invoice.getEwayBillNo());
            inv.setSyncStatus("PENDING");

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
                existing.setPlaceOfSupply(invoice.getPlaceOfSupply());
                existing.setPaymentTerms(invoice.getPaymentTerms());
                existing.setDueDate(invoice.getDueDate());
                existing.setVehicleDispatch(invoice.getVehicleDispatch());
                existing.setPoNo(invoice.getPoNo());
                existing.setPoDate(invoice.getPoDate());
                existing.setDispatchThrough(invoice.getDispatchThrough());
                existing.setLrTrackingNo(invoice.getLrTrackingNo());
                existing.setRemarks(invoice.getRemarks());
                existing.setEwayBillNo(invoice.getEwayBillNo());
                existing.setTotalAfterTax(invoice.getTotalAfterTax() != null ? invoice.getTotalAfterTax() : invoice.getGrandTotal());
                existing.setRoundOff(invoice.getRoundOff() != null ? invoice.getRoundOff() : 0.0);
                
                repo.update(con, existing);

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
            if (invoice.getTotalAfterTax() != null) {
                inv.setTotalAfterTax(invoice.getTotalAfterTax());
            } else {
                inv.setTotalAfterTax(invoice.getGrandTotal());
            }
            if (invoice.getRoundOff() != null) {
                inv.setRoundOff(invoice.getRoundOff());
            } else {
                inv.setRoundOff(0.0);
            }

            inv.setPlaceOfSupply(invoice.getPlaceOfSupply());
            inv.setPaymentTerms(invoice.getPaymentTerms());
            inv.setDueDate(invoice.getDueDate());
            inv.setVehicleDispatch(invoice.getVehicleDispatch());
            inv.setPoNo(invoice.getPoNo());
            inv.setPoDate(invoice.getPoDate());
            inv.setDispatchThrough(invoice.getDispatchThrough());
            inv.setLrTrackingNo(invoice.getLrTrackingNo());
            inv.setRemarks(invoice.getRemarks());
            inv.setEwayBillNo(invoice.getEwayBillNo());

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
        
        // 1. Existence check to prevent generating a new UUID for an existing mapping.
        // If we push a new UUID for an existing (invoice, job) pair, Supabase will throw a unique constraint violation.
        String checkSql = "SELECT uuid FROM invoice_job_mapping WHERE invoice_uuid = ? AND job_uuid = ?";
        try (java.sql.PreparedStatement psCheck = con.prepareStatement(checkSql)) {
            psCheck.setString(1, invoiceUuid.trim());
            psCheck.setString(2, jobUuid.trim());
            try (java.sql.ResultSet rs = psCheck.executeQuery()) {
                if (rs.next()) {
                    String existingUuid = rs.getString("uuid");
                    String updateSql = "UPDATE invoice_job_mapping SET sync_status = 'PENDING', is_deleted = 0, is_active = 1, updated_at = datetime('now'), sync_version = COALESCE(sync_version, 1) + 1 WHERE uuid = ?";
                    try (java.sql.PreparedStatement psUpdate = con.prepareStatement(updateSql)) {
                        psUpdate.setString(1, existingUuid);
                        psUpdate.executeUpdate();
                    }
                    return; // Successfully updated existing, skip insert
                }
            }
        }

        // 2. Insert new mapping
        String sql = """
                INSERT INTO invoice_job_mapping (
                  uuid, invoice_uuid, job_uuid, sync_status, sync_version, is_deleted, is_active, created_at, updated_at
                ) VALUES (?, ?, ?, 'PENDING', 1, 0, 1, datetime('now'), datetime('now'))
                ON CONFLICT(invoice_uuid, job_uuid) DO UPDATE SET
                  sync_status = 'PENDING',
                  sync_version = COALESCE(invoice_job_mapping.sync_version, 1) + 1,
                  updated_at = datetime('now'),
                  is_deleted = 0,
                  is_active = 1
                """;
        try (java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
            String deterministicUuid = java.util.UUID.nameUUIDFromBytes((invoiceUuid.trim() + "_" + jobUuid.trim()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            ps.setString(1, deterministicUuid);
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
        String documentSeries = "";
        String invoiceStatus = "";
        try (java.sql.PreparedStatement psType = con.prepareStatement("SELECT type, document_series, status FROM invoice_master WHERE uuid = ?")) {
            psType.setString(1, invoiceUuid);
            try (java.sql.ResultSet rs = psType.executeQuery()) {
                if (rs.next()) {
                    type = rs.getString(1);
                    documentSeries = rs.getString(2);
                    invoiceStatus = rs.getString(3);
                }
            }
        } catch (Exception ignore) {}

        boolean isGst = "GST_INVOICE".equalsIgnoreCase(documentSeries);
        boolean isDraft = "DRAFT".equalsIgnoreCase(invoiceStatus);
        if (isGst && !isDraft) {
            String updateJobsSql = """
                UPDATE jobs SET status = 'Cancelled', sync_status = 'PENDING', sync_version = COALESCE(sync_version, 0) + 1, updated_at = datetime('now')
                WHERE invoice_uuid = ?
                   OR uuid IN (
                     SELECT job_uuid FROM invoice_job_mapping
                     WHERE invoice_uuid = ? AND COALESCE(is_deleted, 0) = 0
                   )
                """;
            try (java.sql.PreparedStatement psUpdate = con.prepareStatement(updateJobsSql)) {
                psUpdate.setString(1, invoiceUuid);
                psUpdate.setString(2, invoiceUuid);
                psUpdate.executeUpdate();
            } catch (Exception e) {
                System.err.println("Failed to cancel jobs for GST invoice: " + e.getMessage());
            }
            return;
        }

        String targetStatus = "Performa Bills".equalsIgnoreCase(type) ? "Cancelled" : "Completed";
        String updateJobsSql = """
            UPDATE jobs SET invoice_uuid = NULL, status = ?, sync_status = 'PENDING', sync_version = COALESCE(sync_version, 0) + 1, updated_at = datetime('now')
            WHERE invoice_uuid = ?
               OR uuid IN (
                 SELECT job_uuid FROM invoice_job_mapping
                 WHERE invoice_uuid = ? AND COALESCE(is_deleted, 0) = 0
               )
            """;
        
        String deleteMappingSql = "UPDATE invoice_job_mapping SET is_deleted = 1, sync_status = 'PENDING', updated_at = datetime('now') WHERE invoice_uuid = ?";

        try (java.sql.PreparedStatement psUpdate = con.prepareStatement(updateJobsSql);
             java.sql.PreparedStatement psDelMap = con.prepareStatement(deleteMappingSql)) {

            psUpdate.setString(1, targetStatus);
            psUpdate.setString(2, invoiceUuid);
            psUpdate.setString(3, invoiceUuid);
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
        String updateJobsSql = """
            UPDATE jobs SET invoice_uuid = NULL, status = ?, sync_status = 'PENDING', sync_version = COALESCE(sync_version, 0) + 1, updated_at = datetime('now')
            WHERE invoice_uuid = ?
               OR uuid IN (
                 SELECT job_uuid FROM invoice_job_mapping
                 WHERE invoice_uuid = ? AND COALESCE(is_deleted, 0) = 0
               )
            """;
        try (java.sql.PreparedStatement psUpdate = con.prepareStatement(updateJobsSql)) {
            psUpdate.setString(1, targetStatus);
            psUpdate.setString(2, invoiceUuid);
            psUpdate.setString(3, invoiceUuid);
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
        AtomicDB.runExclusiveVoid(con -> {
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

        AtomicDB.runExclusiveVoid(con -> {

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
                if (invoice.getTotalAfterTax() != null) {
                    inv.setTotalAfterTax(invoice.getTotalAfterTax());
                } else {
                    inv.setTotalAfterTax(invoice.getGrandTotal());
                }
                if (invoice.getRoundOff() != null) {
                    inv.setRoundOff(invoice.getRoundOff());
                } else {
                    inv.setRoundOff(0.0);
                }

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
        AtomicDB.runExclusiveVoid(con -> {
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
            String updateJobsSql = "UPDATE jobs SET invoice_uuid = NULL, status = CASE WHEN UPPER(status) = 'CANCELLED' THEN 'Cancelled' ELSE 'Completed' END, sync_status = 'PENDING', sync_version = COALESCE(sync_version, 0) + 1, updated_at = datetime('now') WHERE uuid IN (" + 
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
            String updateInv = "UPDATE invoice_master SET paid_amount = 0, due_amount = amount, payment_status = 'KEPT_AS_ADVANCE', sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?";
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

            String updateInv = "UPDATE invoice_master SET paid_amount = paid_amount - ?, due_amount = due_amount + ?, payment_status = 'REFUNDED', sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?";
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
        boolean localCon = false;
        if (con == null) {
            con = utils.DBConnection.getConnection();
            localCon = true;
        }
        
        try {
            InvoiceMaster inv = repo.findByUuid(con, invoiceUuid);
            if (inv == null) {
                return;
            }
            if ("CANCELLED".equalsIgnoreCase(inv.getStatus())) {
                return;
            }

        boolean isProforma = (inv.getDocumentSeries() != null && ("PROFORMA_INVOICE".equalsIgnoreCase(inv.getDocumentSeries()) || "PROFORMA".equalsIgnoreCase(inv.getDocumentSeries())))
                || (inv.getType() != null && (inv.getType().toUpperCase().contains("PROFORMA") || inv.getType().toUpperCase().contains("PERFORMA") || "JOB_SPECIFIC".equalsIgnoreCase(inv.getType()) || "DATE_RANGE".equalsIgnoreCase(inv.getType()) || inv.getType().toUpperCase().contains("MONTHLY")))
                || (inv.getInvoiceNo() != null && inv.getInvoiceNo().toUpperCase().contains("/PI/"));
        boolean isGst = !isProforma;

        boolean intra = true;
        repository.ClientRepository clientRepo = new repository.ClientRepository();
        model.Client client = clientRepo.findByUuid(inv.getClientId());
        if (client != null) {
            String companyGst = utils.CompanyProfile.getGst();
            String buyerGst = client.getGst();
            String companyCode = extractStateCode(companyGst);
            if (companyCode.isEmpty()) {
                companyCode = "07";
            }
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
            String deallocSql = "UPDATE payment_allocations SET is_deleted = 1, sync_status = 'PENDING', updated_at = datetime('now') WHERE invoice_uuid = ?";
            try (java.sql.PreparedStatement ps = con.prepareStatement(deallocSql)) {
                ps.setString(1, invoiceUuid);
                ps.executeUpdate();
            }
            String updateInvStatusSql = "UPDATE invoice_master SET status = 'CANCELLED', payment_status = 'Void', amount = 0, paid_amount = 0, due_amount = 0, sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?";
            try (java.sql.PreparedStatement ps = con.prepareStatement(updateInvStatusSql)) {
                ps.setString(1, invoiceUuid);
                ps.executeUpdate();
            }
            return;
        }

        repository.HsnSacRepository hsnRepo = new repository.HsnSacRepository();
        for (String jobUuid : activeJobUuids) {
            String itemsSql = "SELECT type, description, amount FROM job_items WHERE job_uuid = ? AND COALESCE(is_deleted, 0) = 0 AND COALESCE(include_in_invoice, 1) = 1";
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
        double newTotalAfterTax = isGst ? (taxable + totalTax) : taxable;
        double newRoundOff = newAmount - newTotalAfterTax;

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
              total_after_tax = ?,
              round_off = ?,
              sync_status = 'PENDING',
              updated_at = datetime('now')
            WHERE uuid = ?
        """;
        try (java.sql.PreparedStatement ps = con.prepareStatement(updateSql)) {
            ps.setDouble(1, newAmount);
            ps.setDouble(2, newDue);
            ps.setString(3, newPayStatus);
            ps.setDouble(4, newTotalAfterTax);
            ps.setDouble(5, newRoundOff);
            ps.setString(6, invoiceUuid);
            ps.executeUpdate();
        }

        // Automatically regenerate PDF file if one is already associated
        if (inv.getFilePath() != null && !inv.getFilePath().isBlank()) {
            try {
                service.InvoiceBuilderService builder = new service.InvoiceBuilderService();
                model.Invoice full = builder.buildInvoiceFromMasterForPdfExport(invoiceUuid);
                boolean isProformaPdf = (full.getMasterDocumentSeries() == model.MasterDocumentSeries.PROFORMA_INVOICE)
                                       || (full.getInvoiceType() != null && (full.getInvoiceType().toUpperCase().contains("PROFORMA") || full.getInvoiceType().toUpperCase().contains("PERFORMA") || "JOB_SPECIFIC".equalsIgnoreCase(full.getInvoiceType()) || "DATE_RANGE".equalsIgnoreCase(full.getInvoiceType()) || full.getInvoiceType().toUpperCase().contains("MONTHLY")))
                                       || (full.getInvoiceNo() != null && full.getInvoiceNo().toUpperCase().contains("/PI/"));
                if (isProformaPdf) {
                    new service.PdfInvoiceService().generateSingleInvoicePDF(full);
                } else {
                    new service.GstPdfInvoiceService().generateGstInvoice(full);
                }
            } catch (Exception ex) {
                System.err.println("Failed to regenerate invoice PDF: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
        } finally {
            if (localCon && con != null) {
                con.close();
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

        boolean isProforma = (inv.getDocumentSeries() != null && ("PROFORMA_INVOICE".equalsIgnoreCase(inv.getDocumentSeries()) || "PROFORMA".equalsIgnoreCase(inv.getDocumentSeries())))
                || (inv.getType() != null && (inv.getType().toUpperCase().contains("PROFORMA") || inv.getType().toUpperCase().contains("PERFORMA") || "JOB_SPECIFIC".equalsIgnoreCase(inv.getType()) || "DATE_RANGE".equalsIgnoreCase(inv.getType()) || inv.getType().toUpperCase().contains("MONTHLY")))
                || (inv.getInvoiceNo() != null && inv.getInvoiceNo().toUpperCase().contains("/PI/"));
        boolean isGst = !isProforma;

        boolean intra = true;
        repository.ClientRepository clientRepo = new repository.ClientRepository();
        model.Client client = clientRepo.findByUuid(inv.getClientId());
        if (client != null) {
            String companyGst = utils.CompanyProfile.getGst();
            String buyerGst = client.getGst();
            String companyCode = extractStateCode(companyGst);
            if (companyCode.isEmpty()) {
                companyCode = "07";
            }
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
        String itemsSql = "SELECT type, description, amount FROM job_items WHERE job_uuid = ? AND COALESCE(is_deleted, 0) = 0 AND COALESCE(include_in_invoice, 1) = 1";
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
        String itemsSql = "SELECT type, description, amount FROM job_items WHERE job_uuid = ? AND COALESCE(is_deleted, 0) = 0 AND COALESCE(include_in_invoice, 1) = 1";
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
        service.LoggerService.beginOperation("INVOICE-UPDATE-STATUS");
        try {
            AtomicDB.runVoid(con -> {
                InvoiceMaster inv = repo.findByUuid(con, invoiceUuid);
                if (inv != null) {
                    if ("CANCELLED".equalsIgnoreCase(newStatus)) {
                        String invNo = inv.getInvoiceNo();
                        if ("DRAFT".equalsIgnoreCase(inv.getStatus()) || (invNo != null && invNo.startsWith("TEMP-"))) {
                            unlinkJobsFromInvoice(con, inv.getUuid());
                            repo.deleteInvoice(con, inv.getUuid());
                            return; // Done
                        }
                    }

                    inv.setStatus(newStatus);
                    if ("CANCELLED".equalsIgnoreCase(newStatus)) {
                        if (inv.getPaymentStatus() == null || (!inv.getPaymentStatus().equalsIgnoreCase("REFUNDED") && !inv.getPaymentStatus().equalsIgnoreCase("KEPT_AS_ADVANCE"))) {
                            inv.setPaymentStatus("Void");
                        }
                        inv.setVoid(false); // Stay visible in table
                        updateJobsStatusByInvoice(con, inv.getUuid(), "Cancelled");
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
            service.LoggerService.endOperation("INVOICE-UPDATE-STATUS", true, "UUID: " + invoiceUuid + " -> " + newStatus);
        } catch (Exception e) {
            service.LoggerService.endOperation("INVOICE-UPDATE-STATUS", false, "Exception: " + e.getMessage());
            throw e;
        }
    }

    public void cancelProformaInvoice(String invoiceUuid, boolean cancelJobs) {
        service.LoggerService.beginOperation("PROFORMA-CANCEL");
        try {
            AtomicDB.runVoid(con -> {
                InvoiceMaster inv = repo.findByUuid(con, invoiceUuid);
                if (inv != null) {
                    boolean isDraft = "DRAFT".equalsIgnoreCase(inv.getStatus()) || (inv.getInvoiceNo() != null && inv.getInvoiceNo().startsWith("TEMP-"));
                    if (isDraft) {
                        // Draft is cancelled directly without finalizing - delete it entirely
                        String targetStatus = cancelJobs ? "Cancelled" : "Completed";
                        String updateJobsSql = """
                            UPDATE jobs SET invoice_uuid = NULL, status = ?, sync_status = 'PENDING', sync_version = COALESCE(sync_version, 0) + 1, updated_at = datetime('now')
                            WHERE invoice_uuid = ?
                               OR uuid IN (
                                 SELECT job_uuid FROM invoice_job_mapping
                                 WHERE invoice_uuid = ? AND COALESCE(is_deleted, 0) = 0
                               )
                            """;
                        try (java.sql.PreparedStatement psUpdate = con.prepareStatement(updateJobsSql)) {
                            psUpdate.setString(1, targetStatus);
                            psUpdate.setString(2, invoiceUuid);
                            psUpdate.setString(3, invoiceUuid);
                            psUpdate.executeUpdate();
                        }

                        // Delete mappings in invoice_job_mapping
                        String deleteMappingSql = "UPDATE invoice_job_mapping SET is_deleted = 1, sync_status = 'PENDING', updated_at = datetime('now') WHERE invoice_uuid = ?";
                        try (java.sql.PreparedStatement psDelMap = con.prepareStatement(deleteMappingSql)) {
                            psDelMap.setString(1, invoiceUuid);
                            psDelMap.executeUpdate();
                        }

                        repo.deleteInvoice(con, invoiceUuid);
                    } else {
                        // It was finalized, so keep it in DB as CANCELLED
                        inv.setStatus("CANCELLED");
                        if (inv.getPaymentStatus() == null || (!inv.getPaymentStatus().equalsIgnoreCase("REFUNDED") && !inv.getPaymentStatus().equalsIgnoreCase("KEPT_AS_ADVANCE"))) {
                            inv.setPaymentStatus("Void");
                        }
                        inv.setVoid(false); // Stay visible in table
                        inv.setSyncStatus("PENDING");
                        repo.update(con, inv);

                        // Now handle jobs status based on the cancelJobs flag
                        if (cancelJobs) {
                            String updateJobsSql = """
                                UPDATE jobs SET status = 'Cancelled', sync_status = 'PENDING', sync_version = COALESCE(sync_version, 0) + 1, updated_at = datetime('now')
                                WHERE invoice_uuid = ?
                                   OR uuid IN (
                                     SELECT job_uuid FROM invoice_job_mapping
                                     WHERE invoice_uuid = ? AND COALESCE(is_deleted, 0) = 0
                                   )
                                """;
                            try (java.sql.PreparedStatement psUpdate = con.prepareStatement(updateJobsSql)) {
                                psUpdate.setString(1, invoiceUuid);
                                psUpdate.setString(2, invoiceUuid);
                                psUpdate.executeUpdate();
                            }
                        } else {
                            String updateJobsSql = """
                                UPDATE jobs SET invoice_uuid = NULL, status = 'Completed', sync_status = 'PENDING', sync_version = COALESCE(sync_version, 0) + 1, updated_at = datetime('now')
                                WHERE invoice_uuid = ?
                                   OR uuid IN (
                                     SELECT job_uuid FROM invoice_job_mapping
                                     WHERE invoice_uuid = ? AND COALESCE(is_deleted, 0) = 0
                                   )
                                """;
                            try (java.sql.PreparedStatement psUpdate = con.prepareStatement(updateJobsSql)) {
                                psUpdate.setString(1, invoiceUuid);
                                psUpdate.setString(2, invoiceUuid);
                                psUpdate.executeUpdate();
                            }

                            // Delete mappings in invoice_job_mapping
                            String deleteMappingSql = "UPDATE invoice_job_mapping SET is_deleted = 1, sync_status = 'PENDING', updated_at = datetime('now') WHERE invoice_uuid = ?";
                            try (java.sql.PreparedStatement psDelMap = con.prepareStatement(deleteMappingSql)) {
                                psDelMap.setString(1, invoiceUuid);
                                psDelMap.executeUpdate();
                            }

                            deleteEmptyInvoices(con);
                        }
                    }
                }
            });
            UniversalSyncEngine.scheduleSyncAsync();
            service.LoggerService.endOperation("PROFORMA-CANCEL", true, "UUID: " + invoiceUuid + ", cancelJobs: " + cancelJobs);
        } catch (Exception e) {
            service.LoggerService.endOperation("PROFORMA-CANCEL", false, "Exception: " + e.getMessage());
            throw e;
        }
    }

    public void deleteDraftGstInvoice(String invoiceUuid, boolean cancelJobs) {
        service.LoggerService.beginOperation("GST-DRAFT-DELETE");
        try {
            AtomicDB.runVoid(con -> {
                InvoiceMaster inv = repo.findByUuid(con, invoiceUuid);
                if (inv != null && "DRAFT".equalsIgnoreCase(inv.getStatus())) {
                    if (cancelJobs) {
                        inv.setStatus("CANCELLED");
                        inv.setPaymentStatus("Void");
                        inv.setSyncStatus("PENDING");
                        repo.update(con, inv);

                        // Set jobs to Cancelled but keep linked
                        String updateJobsSql = """
                            UPDATE jobs SET status = 'Cancelled', sync_status = 'PENDING', sync_version = COALESCE(sync_version, 0) + 1, updated_at = datetime('now')
                            WHERE invoice_uuid = ?
                               OR uuid IN (
                                 SELECT job_uuid FROM invoice_job_mapping
                                 WHERE invoice_uuid = ? AND COALESCE(is_deleted, 0) = 0
                               )
                            """;
                        try (java.sql.PreparedStatement psUpdate = con.prepareStatement(updateJobsSql)) {
                            psUpdate.setString(1, invoiceUuid);
                            psUpdate.setString(2, invoiceUuid);
                            psUpdate.executeUpdate();
                        }
                    } else {
                        // Keep jobs (unlink them) and delete the invoice
                        String updateJobsSql = """
                            UPDATE jobs SET invoice_uuid = NULL, status = 'Completed', sync_status = 'PENDING', sync_version = COALESCE(sync_version, 0) + 1, updated_at = datetime('now')
                            WHERE invoice_uuid = ?
                               OR uuid IN (
                                 SELECT job_uuid FROM invoice_job_mapping
                                 WHERE invoice_uuid = ? AND COALESCE(is_deleted, 0) = 0
                               )
                            """;
                        try (java.sql.PreparedStatement psUpdate = con.prepareStatement(updateJobsSql)) {
                            psUpdate.setString(1, invoiceUuid);
                            psUpdate.setString(2, invoiceUuid);
                            psUpdate.executeUpdate();
                        }

                        // Delete mappings in invoice_job_mapping
                        String deleteMappingSql = "UPDATE invoice_job_mapping SET is_deleted = 1, sync_status = 'PENDING', updated_at = datetime('now') WHERE invoice_uuid = ?";
                        try (java.sql.PreparedStatement psDelMap = con.prepareStatement(deleteMappingSql)) {
                            psDelMap.setString(1, invoiceUuid);
                            psDelMap.executeUpdate();
                        }

                        repo.deleteInvoice(con, invoiceUuid);
                    }
                }
            });
            UniversalSyncEngine.scheduleSyncAsync();
            service.LoggerService.endOperation("GST-DRAFT-DELETE", true, "UUID: " + invoiceUuid + ", cancelJobs: " + cancelJobs);
        } catch (Exception e) {
            service.LoggerService.endOperation("GST-DRAFT-DELETE", false, "Exception: " + e.getMessage());
            throw e;
        }
    }

    public String finalizeInvoice(String invoiceUuid) {
        service.LoggerService.beginOperation("INVOICE-FINALIZE");
        try {
            String finalNo = AtomicDB.runExclusive(con -> {
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
                        if (api.supabase.SupabaseReachability.isReachable() && numberAllocator.isRemoteReachable("proforma_invoice")) {
                            var permanent = numberAllocator.tryAllocatePermanentInvoice(con, series, inv.getInvoiceDate());
                            if (permanent.isPresent()) {
                                resolvedNo = permanent.get().value();
                            } else {
                                if (currentNo != null && DocumentNumbering.isTemporaryNumber(currentNo)) {
                                    resolvedNo = currentNo;
                                } else {
                                    AllocatedNumber fallback = service.sync.UniversalTemporaryNumberEngine.getInstance().allocateTemporary(con, "proforma_invoice");
                                    resolvedNo = fallback.value();
                                }
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

                try {
                    recalculateInvoiceTotals(con, invoiceUuid);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to recalculate totals during finalization: " + e.getMessage(), e);
                }

                return resolvedNo;
            });
            UniversalSyncEngine.scheduleSyncAsync();
            service.LoggerService.endOperation("INVOICE-FINALIZE", true, "Invoice UUID: " + invoiceUuid + " -> No: " + finalNo);
            return finalNo;
        } catch (Exception e) {
            service.LoggerService.endOperation("INVOICE-FINALIZE", false, "Exception: " + e.getMessage());
            throw e;
        }
    }

    private void updateJobsStatusByInvoice(java.sql.Connection con, String invoiceUuid, String status) {
        if (invoiceUuid == null || invoiceUuid.isBlank()) {
            return;
        }
        String sql = """
            UPDATE jobs SET status = ?, sync_status = 'PENDING', sync_version = COALESCE(sync_version, 0) + 1, updated_at = datetime('now')
            WHERE invoice_uuid = ?
               OR uuid IN (
                 SELECT job_uuid FROM invoice_job_mapping
                 WHERE invoice_uuid = ? AND COALESCE(is_deleted, 0) = 0
               )
            """;
        try (java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, invoiceUuid);
            ps.setString(3, invoiceUuid);
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
            newInv.setTotalAfterTax(old.getTotalAfterTax());
            newInv.setRoundOff(old.getRoundOff());

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

            // 5. Clone/Copy jobs and job items from OLD to NEW revised invoice
            String oldUuid = old.getUuid();
            String newUuid = newInv.getUuid();
            if (oldUuid != null && newUuid != null) {
                // Find all active jobs in the old invoice
                String selectJobsSql = """
                    SELECT uuid, client_uuid, job_title, job_date, job_type, description, remarks, image_path, amount, job_number_mode, delivery_date, created_by_user_uuid 
                    FROM jobs 
                    WHERE (invoice_uuid = ? OR uuid IN (SELECT job_uuid FROM invoice_job_mapping WHERE invoice_uuid = ? AND COALESCE(is_deleted, 0) = 0))
                      AND COALESCE(is_deleted, 0) = 0
                """;
                java.util.List<java.util.Map<String, Object>> oldJobs = new java.util.ArrayList<>();
                try (java.sql.PreparedStatement ps = con.prepareStatement(selectJobsSql)) {
                    ps.setString(1, oldUuid);
                    ps.setString(2, oldUuid);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            java.util.Map<String, Object> jobData = new java.util.HashMap<>();
                            jobData.put("uuid", rs.getString("uuid"));
                            jobData.put("client_uuid", rs.getString("client_uuid"));
                            jobData.put("job_title", rs.getString("job_title"));
                            jobData.put("job_date", rs.getString("job_date"));
                            jobData.put("job_type", rs.getString("job_type"));
                            jobData.put("description", rs.getString("description"));
                            jobData.put("remarks", rs.getString("remarks"));
                            jobData.put("image_path", rs.getString("image_path"));
                            jobData.put("amount", rs.getDouble("amount"));
                            jobData.put("job_number_mode", rs.getString("job_number_mode"));
                            jobData.put("delivery_date", rs.getString("delivery_date"));
                            jobData.put("created_by_user_uuid", rs.getString("created_by_user_uuid"));
                            oldJobs.add(jobData);
                        }
                    }
                }

                String insertJobSql = """
                    INSERT INTO jobs (
                        uuid, job_code, client_uuid, job_title, job_date, job_type, description,
                        status, child_status, remarks, image_path, invoice_uuid, amount, job_number_mode,
                        delivery_date, is_deleted, is_active, sync_status, sync_version, created_at, updated_at,
                        created_by_user_uuid, updated_by_user_uuid
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, 'Invoice Drafted', 'Invoice Drafted', ?, ?, ?, ?, ?, ?, 0, 1, 'PENDING', 1, datetime('now'), datetime('now'), ?, ?)
                """;

                String insertJobItemSql = """
                    INSERT INTO job_items (uuid, job_uuid, type, description, amount, sort_order, include_in_invoice, sync_status, is_deleted, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, datetime('now'), datetime('now'))
                """;

                for (java.util.Map<String, Object> oldJob : oldJobs) {
                    String oldJobUuid = (String) oldJob.get("uuid");
                    String newJobUuid = utils.JobIdentifiers.newUuidString();
                    String newJobCode = "";
                    try {
                        newJobCode = service.sync.UniversalNumberAllocator.getInstance().allocateJobCode(con).value();
                    } catch (Exception ex) {
                        newJobCode = "J-" + System.currentTimeMillis();
                    }

                    // Insert cloned job
                    try (java.sql.PreparedStatement ps = con.prepareStatement(insertJobSql)) {
                        ps.setString(1, newJobUuid);
                        ps.setString(2, newJobCode);
                        ps.setString(3, (String) oldJob.get("client_uuid"));
                        ps.setString(4, (String) oldJob.get("job_title"));
                        ps.setString(5, (String) oldJob.get("job_date"));
                        ps.setString(6, (String) oldJob.get("job_type"));
                        ps.setString(7, (String) oldJob.get("description"));
                        ps.setString(8, (String) oldJob.get("remarks"));
                        ps.setString(9, (String) oldJob.get("image_path"));
                        ps.setString(10, newUuid);
                        ps.setDouble(11, (Double) oldJob.get("amount"));
                        ps.setString(12, (String) oldJob.get("job_number_mode"));
                        ps.setString(13, (String) oldJob.get("delivery_date"));
                        ps.setString(14, (String) oldJob.get("created_by_user_uuid"));
                        ps.setString(15, (String) oldJob.get("created_by_user_uuid"));
                        ps.executeUpdate();
                    }

                    // Insert mapping in invoice_job_mapping
                    insertInvoiceJobMapping(con, newUuid, newJobUuid);

                    // Fetch and clone job items
                    String selectJobItemsSql = "SELECT uuid, type, description, amount, sort_order, include_in_invoice FROM job_items WHERE job_uuid = ? AND COALESCE(is_deleted, 0) = 0";
                    try (java.sql.PreparedStatement ps = con.prepareStatement(selectJobItemsSql)) {
                        ps.setString(1, oldJobUuid);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String oldJobItemUuid = rs.getString("uuid");
                                String newJobItemUuid = utils.ClientIdentifiers.newUuidString();
                                String type = rs.getString("type");

                                try (java.sql.PreparedStatement psItem = con.prepareStatement(insertJobItemSql)) {
                                    psItem.setString(1, newJobItemUuid);
                                    psItem.setString(2, newJobUuid);
                                    psItem.setString(3, type);
                                    psItem.setString(4, rs.getString("description"));
                                    psItem.setDouble(5, rs.getDouble("amount"));
                                    psItem.setInt(6, rs.getInt("sort_order"));
                                    psItem.setInt(7, rs.getInt("include_in_invoice"));
                                    psItem.executeUpdate();
                                }

                                // Copy the type-specific detail record
                                if ("Paper".equalsIgnoreCase(type)) {
                                    String copyPaperSql = """
                                        INSERT INTO paper_items (uuid, job_item_uuid, qty, units, size, gsm, type, source, supplier_uuid, supplier_name, notes, amount, sync_status, is_deleted, created_at, updated_at)
                                        SELECT ?, ?, qty, units, size, gsm, type, source, supplier_uuid, supplier_name, notes, amount, 'PENDING', 0, datetime('now'), datetime('now')
                                        FROM paper_items WHERE job_item_uuid = ? AND COALESCE(is_deleted, 0) = 0
                                    """;
                                    try (java.sql.PreparedStatement psDetail = con.prepareStatement(copyPaperSql)) {
                                        psDetail.setString(1, utils.ClientIdentifiers.newUuidString());
                                        psDetail.setString(2, newJobItemUuid);
                                        psDetail.setString(3, oldJobItemUuid);
                                        psDetail.executeUpdate();
                                    }
                                } else if ("Printing".equalsIgnoreCase(type)) {
                                    String copyPrintingSql = """
                                        INSERT INTO printing_items (uuid, job_item_uuid, qty, units, sets, color, side, with_ctp, notes, amount, sync_status, is_deleted, created_at, updated_at)
                                        SELECT ?, ?, qty, units, sets, color, side, with_ctp, notes, amount, 'PENDING', 0, datetime('now'), datetime('now')
                                        FROM printing_items WHERE job_item_uuid = ? AND COALESCE(is_deleted, 0) = 0
                                    """;
                                    try (java.sql.PreparedStatement psDetail = con.prepareStatement(copyPrintingSql)) {
                                        psDetail.setString(1, utils.ClientIdentifiers.newUuidString());
                                        psDetail.setString(2, newJobItemUuid);
                                        psDetail.setString(3, oldJobItemUuid);
                                        psDetail.executeUpdate();
                                    }
                                } else if ("Binding".equalsIgnoreCase(type)) {
                                    String copyBindingSql = """
                                        INSERT INTO binding_items (uuid, job_item_uuid, process, qty, rate, notes, amount, sync_status, is_deleted, created_at, updated_at)
                                        SELECT ?, ?, process, qty, rate, notes, amount, 'PENDING', 0, datetime('now'), datetime('now')
                                        FROM binding_items WHERE job_item_uuid = ? AND COALESCE(is_deleted, 0) = 0
                                    """;
                                    try (java.sql.PreparedStatement psDetail = con.prepareStatement(copyBindingSql)) {
                                        psDetail.setString(1, utils.ClientIdentifiers.newUuidString());
                                        psDetail.setString(2, newJobItemUuid);
                                        psDetail.setString(3, oldJobItemUuid);
                                        psDetail.executeUpdate();
                                    }
                                } else if ("Lamination".equalsIgnoreCase(type)) {
                                    String copyLaminationSql = """
                                        INSERT INTO lamination_items (uuid, job_item_uuid, qty, unit, type, side, size, notes, amount, sync_status, is_deleted, created_at, updated_at)
                                        SELECT ?, ?, qty, unit, type, side, size, notes, amount, 'PENDING', 0, datetime('now'), datetime('now')
                                        FROM lamination_items WHERE job_item_uuid = ? AND COALESCE(is_deleted, 0) = 0
                                    """;
                                    try (java.sql.PreparedStatement psDetail = con.prepareStatement(copyLaminationSql)) {
                                        psDetail.setString(1, utils.ClientIdentifiers.newUuidString());
                                        psDetail.setString(2, newJobItemUuid);
                                        psDetail.setString(3, oldJobItemUuid);
                                        psDetail.executeUpdate();
                                    }
                                } else if ("CTP".equalsIgnoreCase(type)) {
                                    String copyCtpSql = """
                                        INSERT INTO ctp_items (uuid, job_item_uuid, qty, plate_size, gauge, backing, color, supplier_uuid, supplier_name, notes, amount, sync_status, is_deleted, created_at, updated_at)
                                        SELECT ?, ?, qty, plate_size, gauge, backing, color, supplier_uuid, supplier_name, notes, amount, 'PENDING', 0, datetime('now'), datetime('now')
                                        FROM ctp_items WHERE job_item_uuid = ? AND COALESCE(is_deleted, 0) = 0
                                    """;
                                    try (java.sql.PreparedStatement psDetail = con.prepareStatement(copyCtpSql)) {
                                        psDetail.setString(1, utils.ClientIdentifiers.newUuidString());
                                        psDetail.setString(2, newJobItemUuid);
                                        psDetail.setString(3, oldJobItemUuid);
                                        psDetail.executeUpdate();
                                    }
                                }
                            }
                        }
                    }
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
    public List<InvoiceMaster> getFilteredInvoices(String clientId, String paymentStatus, String invoiceStatus, LocalDate start, LocalDate end, String invoiceNo, String documentSeries) {
        return AtomicDB.run(con -> repo.findFiltered(con, clientId, paymentStatus, invoiceStatus, start, end, invoiceNo, documentSeries));
    }
}