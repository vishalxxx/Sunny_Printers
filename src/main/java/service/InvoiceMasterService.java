package service;

import model.Invoice;
import model.InvoiceMaster;
import repository.InvoiceMasterRepository;
import utils.AtomicDB;

import java.time.LocalDate;
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

    /*
     * =========================================================
     * CREATE OR REUSE BUSINESS INVOICE
     * =========================================================
     */
    public CreateOrGetResult createNewDraftInvoice(
            Invoice invoice,
            String type,
            String filePath) {

        return AtomicDB.run(con -> {

            if (invoice.getJobs() == null || invoice.getJobs().isEmpty()) {
                throw new RuntimeException("Cannot create an empty invoice. No jobs found.");
            }

            // 🔥 Requirement: Always create a new draft, do not reuse old invoices based on period
            String tempNo = settingsService.generateNextTempInvoiceNumber(con);

            InvoiceMaster inv = new InvoiceMaster(
                    tempNo,
                    invoice.getClientId(),
                    invoice.getClientName(),
                    invoice.getInvoiceDate(),
                    invoice.getGrandTotal(),
                    type,
                    "DRAFT");

            inv.setFilePath(filePath);
            inv.setPeriodFrom(invoice.getFromDate());
            inv.setPeriodTo(invoice.getToDate());

            repo.insert(con, inv);
            return new CreateOrGetResult(inv, true);
        });
    }

    /**
     * Delete invoices created during a cancelled run (avoids duplicate
     * voided+active records).
     */
    public void deleteInvoicesIfCancelled(List<Integer> invoiceIds) {
        if (invoiceIds == null || invoiceIds.isEmpty())
            return;
        AtomicDB.runVoid(con -> {
            for (int id : invoiceIds) {
                try {
                    unlinkJobsFromInvoice(con, id);
                    repo.deleteInvoice(con, id);
                } catch (Exception e) {
                    System.err.println("Failed to delete invoice " + id + " on cancel: " + e.getMessage());
                }
            }
        });
    }

    public void saveGeneratedInvoice(
            Invoice invoice,
            String type,
            String status,
            String filePath) {
        InvoiceMaster inv = new InvoiceMaster(
                invoice.getInvoiceNo(),
                invoice.getClientId(),
                invoice.getClientName(),
                invoice.getInvoiceDate(),
                invoice.getGrandTotal(),
                type,
                status);

        inv.setFilePath(filePath);

        if (invoice.getJobs() == null || invoice.getJobs().isEmpty()) {
            throw new RuntimeException("Cannot save an empty invoice.");
        }

        AtomicDB.runVoid(con -> {
            repo.insert(con, inv);
            linkJobsToInvoice(con, inv.getId(), invoice);
            
            deleteEmptyInvoices(con);
        });
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
                linkJobsToInvoice(con, existing.getId(), invoice);
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

            repo.insert(con, inv);
            linkJobsToInvoice(con, inv.getId(), invoice);
            deleteEmptyInvoices(con);
        });
    }

    private void linkJobsToInvoice(java.sql.Connection con, int invoiceId, Invoice invoice) {
        if (invoice == null || invoice.getJobs() == null || invoice.getJobs().isEmpty())
            return;

        List<Integer> jobIds = invoice.getJobs().stream()
                .map(model.InvoiceJob::getJobId)
                .toList();

        if (jobIds.isEmpty())
            return;

        String placeholders = jobIds.stream().map(i -> "?").collect(java.util.stream.Collectors.joining(","));
        String updateJobsSql = "UPDATE jobs SET invoice_id = ?, status = 'Invoice Drafted' WHERE id IN (" + placeholders + ")";
        String insertMappingSql = "INSERT OR IGNORE INTO invoice_job_mapping (invoice_id, job_id) VALUES (?, ?)";

        try (java.sql.PreparedStatement psUpdate = con.prepareStatement(updateJobsSql);
             java.sql.PreparedStatement psMap = con.prepareStatement(insertMappingSql)) {
            
            // 1. Update Jobs
            psUpdate.setInt(1, invoiceId);
            int idx = 2;
            for (int jobId : jobIds) {
                psUpdate.setInt(idx++, jobId);
            }
            psUpdate.executeUpdate();

            // 2. Insert Mappings
            for (int jobId : jobIds) {
                psMap.setInt(1, invoiceId);
                psMap.setInt(2, jobId);
                psMap.addBatch();
            }
            psMap.executeBatch();

        } catch (Exception e) {
            System.err.println("Failed to link jobs to invoice: " + e.getMessage());
        }
    }

    public void deleteEmptyInvoices(java.sql.Connection con) {
        String sql = """
            DELETE FROM invoice_master 
            WHERE status = 'DRAFT' 
              AND invoice_no LIKE 'TEMP-%'
              AND id NOT IN (SELECT DISTINCT invoice_id FROM invoice_job_mapping)
        """;
        try (java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("Failed to cleanup empty invoices: " + e.getMessage());
        }
    }

    private void unlinkJobsFromInvoice(java.sql.Connection con, int invoiceId) {
        String updateJobsSql = "UPDATE jobs SET invoice_id = NULL, status = 'Completed' WHERE invoice_id = ?";
        String deleteMappingSql = "DELETE FROM invoice_job_mapping WHERE invoice_id = ?";
        try (java.sql.PreparedStatement psUpdate = con.prepareStatement(updateJobsSql);
             java.sql.PreparedStatement psDelMap = con.prepareStatement(deleteMappingSql)) {
            
            psUpdate.setInt(1, invoiceId);
            psUpdate.executeUpdate();
            
            psDelMap.setInt(1, invoiceId);
            psDelMap.executeUpdate();
            
            // 🔥 Requirement: if temporary invoice is empty, delete it automatically
            deleteEmptyInvoices(con);
            
        } catch (Exception e) {
            System.err.println("Failed to unlink jobs from invoice: " + e.getMessage());
        }
    }

    private void releaseJobsKeepHistory(java.sql.Connection con, int invoiceId) {
        // Releases jobs for reuse but keeps the 'invoice_job_mapping' for history
        String updateJobsSql = "UPDATE jobs SET invoice_id = NULL, status = 'Completed' WHERE invoice_id = ?";
        try (java.sql.PreparedStatement psUpdate = con.prepareStatement(updateJobsSql)) {
            psUpdate.setInt(1, invoiceId);
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
    public void markSent(int invoiceId) {
        AtomicDB.runVoid(con -> repo.updatePayment(con, invoiceId, 0, 0, "SENT", LocalDate.now()));
    }

    /*
     * =========================================================
     * REGISTER PAYMENT / PARTIAL / FULL
     * =========================================================
     */
    public void registerPayment(int invoiceId, double amount, double totalDue) {

        AtomicDB.runVoid(con -> {

            String status = amount >= totalDue ? "PAID" : "PARTIAL_PAID";

            repo.updatePayment(
                    con,
                    invoiceId,
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

                repo.insert(con, inv);
                linkJobsToInvoice(con, inv.getId(), invoice);
            }
            deleteEmptyInvoices(con);
        });
    }

    /*
     * =========================================================
     * VOID INVOICE
     * =========================================================
     */
    public void voidInvoice(int invoiceId, String reason) {

        AtomicDB.runVoid(con -> {
            repo.voidInvoice(con, invoiceId, reason, LocalDate.now());
            unlinkJobsFromInvoice(con, invoiceId);
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

    public InvoiceMaster getInvoiceById(int invoiceId) {
        return AtomicDB.run(con -> repo.findById(con, invoiceId));
    }

    /*
     * =========================================================
     * UPDATE INDIVIDUAL STATUSES
     * =========================================================
     */
    public void updateInvoiceStatus(int invoiceId, String newStatus) {
        AtomicDB.runVoid(con -> {
            InvoiceMaster inv = repo.findById(con, invoiceId);
            if (inv != null) {
                // 🔥 Requirement: if temporary (DRAFT/TEMP) cancelled, remove it from DB
                if ("CANCELLED".equalsIgnoreCase(newStatus)) {
                    String invNo = inv.getInvoiceNo();
                    if ("DRAFT".equals(inv.getStatus()) || (invNo != null && invNo.startsWith("TEMP-"))) {
                        unlinkJobsFromInvoice(con, invoiceId);
                        repo.deleteInvoice(con, invoiceId);
                        return; // Done
                    }
                }

                inv.setStatus(newStatus);
                // If cancelled/void, also update payment status and mark as void for period reuse
                if ("CANCELLED".equalsIgnoreCase(newStatus)) {
                    inv.setPaymentStatus("Void");
                    inv.setVoid(false); // Stay visible in table
                    // 🔥 Requirement: Keep history but release jobs
                    releaseJobsKeepHistory(con, invoiceId);
                } else if ("VOID".equalsIgnoreCase(newStatus)) {
                    inv.setPaymentStatus("VOID");
                    inv.setVoid(true);
                    inv.setVoidReason("User updated status to VOID");
                    inv.setVoidDate(LocalDate.now());
                    
                    repo.updatePayment(con, invoiceId, inv.getPaidAmount(), inv.getDueAmount(), "VOID", LocalDate.now());
                    unlinkJobsFromInvoice(con, invoiceId);
                }
                repo.update(con, inv);
            }
        });
    }

    public String finalizeInvoice(int invoiceId) {
        return AtomicDB.run(con -> {
            InvoiceMaster inv = repo.findById(con, invoiceId);
            if (inv == null) throw new RuntimeException("Invoice not found: " + invoiceId);
            if (!"DRAFT".equals(inv.getStatus())) throw new RuntimeException("Only DRAFT invoices can be finalized");

            String currentNo = inv.getInvoiceNo();
            String finalNo;

            // 🔥 Requirement: Revised invoices (-R1, -R2...) keep their number
            if (currentNo != null && currentNo.contains("-R")) {
                finalNo = currentNo;
            } else {
                // 1. Generate permanent number for TEMP or original DRAFTs
                finalNo = settingsService.generateNextInvoiceNumber(con);
            }
            
            // 2. Update Invoice Master
            inv.setInvoiceNo(finalNo);
            inv.setStatus("FINAL");
            repo.update(con, inv);

            // 3. Update Jobs Status
            updateJobsStatusByInvoice(con, invoiceId, "Invoiced");
            
            return finalNo;
        });
    }

    private void updateJobsStatusByInvoice(java.sql.Connection con, int invoiceId, String status) {
        String sql = "UPDATE jobs SET status = ? WHERE invoice_id = ?";
        try (java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, invoiceId);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("Failed to update jobs status for invoice " + invoiceId + ": " + e.getMessage());
        }
    }

    public void updateInvoicePaymentStatus(int invoiceId, String newPaymentStatus) {
        AtomicDB.runVoid(con -> {
            // Updating just payment_status without affecting other amounts
            String sql = "UPDATE invoice_master SET payment_status = ? WHERE id = ?";
            try (java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, newPaymentStatus);
                ps.setInt(2, invoiceId);
                ps.executeUpdate();
            }
        });
    }

    /*
     * =========================================================
     * GET INVOICES BY CLIENT
     * =========================================================
     */
    public List<InvoiceMaster> getInvoicesByClientId(int clientId) {
        return AtomicDB.run(con -> repo.findByClientId(con, clientId));
    }

    public InvoiceMaster reviseInvoice(InvoiceMaster old) {
        return AtomicDB.run(con -> {
            // 1. Calculate new invoice number
            // If old is INV-100 and has parentId=50, we should count revisions of 50.
            // If old is the original (parentId=null), we count revisions of old.id.
            int rootId = (old.getParentInvoiceId() != null) ? old.getParentInvoiceId() : old.getId();
            int revCount = repo.countRevisions(con, rootId);

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
            newInv.setPaidAmount(0);
            newInv.setDueAmount(old.getAmount());
            newInv.setParentInvoiceId(rootId);

            // 2. Mark OLD as REVISED in DB first to clear unique constraint for the new DRAFT
            // 🔥 Requirement: Revised status turning payment status to CLOSED and Dues/Amount to 0
            String clearQuery = "UPDATE invoice_master SET status = 'REVISED', payment_status = 'CLOSED', due_amount = 0, amount = 0 WHERE id = ?";
            try (java.sql.PreparedStatement ps = con.prepareStatement(clearQuery)) {
                ps.setInt(1, old.getId());
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
            old.setReplacedByInvoiceId(newInv.getId());
            repo.update(con, old);

            // 5. Move JOBS from OLD to NEW
            String moveJobsSql = "UPDATE jobs SET invoice_id = ? WHERE invoice_id = ?";
            try (java.sql.PreparedStatement ps = con.prepareStatement(moveJobsSql)) {
                ps.setInt(1, newInv.getId());
                ps.setInt(2, old.getId());
                ps.executeUpdate();
            }

            return newInv;
        });
    }

    /*
     * =========================================================
     * UPDATE FILTERED INVOICES (FOR VIEW INVOICES SCREEN)
     * =========================================================
     */
    public List<InvoiceMaster> getFilteredInvoices(Integer clientId, String status, LocalDate start, LocalDate end, String invoiceNo) {
        return AtomicDB.run(con -> repo.findFiltered(con, clientId, status, start, end, invoiceNo));
    }
}