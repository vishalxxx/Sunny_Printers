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

    /** Result of createOrGetExisting: master + whether it was newly inserted (for cancel rollback). */
    public record CreateOrGetResult(InvoiceMaster master, boolean wasNewlyCreated) {}

    private final InvoiceMasterRepository repo = new InvoiceMasterRepository();

    /* =========================================================
       CREATE OR REUSE BUSINESS INVOICE
       ========================================================= */
    public CreateOrGetResult createOrGetExisting(
            Invoice invoice,
            String type,
            String filePath
    ) {

        return AtomicDB.run(con -> {

            // Lookup by client+period only (ignores type) - prevents duplicates across monthly vs date range
            Optional<InvoiceMaster> existing =
                    repo.findActiveByClientPeriod(
                            con,
                            invoice.getClientId(),
                            invoice.getFromDate(),
                            invoice.getToDate()
                    );

            if (existing.isPresent()) {
                return new CreateOrGetResult(existing.get(), false); // 🔥 reuse same invoice
            }

            InvoiceMaster inv = new InvoiceMaster(
                    invoice.getInvoiceNo(),
                    invoice.getClientId(),
                    invoice.getClientName(),
                    invoice.getInvoiceDate(),
                    invoice.getGrandTotal(),
                    type,
                    "GENERATED"
            );

            inv.setFilePath(filePath);
            inv.setPeriodFrom(invoice.getFromDate());
            inv.setPeriodTo(invoice.getToDate());

            try {
                repo.insert(con, inv);
                return new CreateOrGetResult(inv, true); // newly created
            } catch (Exception e) {
                // Likely a concurrent insert / unique constraint violation.
                Optional<InvoiceMaster> retry = repo.findActiveByClientPeriod(
                        con,
                        invoice.getClientId(),
                        invoice.getFromDate(),
                        invoice.getToDate()
                );

                if (retry.isPresent()) {
                    return new CreateOrGetResult(retry.get(), false);
                }

                throw e;
            }
        });
    }

    /** Delete invoices created during a cancelled run (avoids duplicate voided+active records). */
    public void deleteInvoicesIfCancelled(List<Integer> invoiceIds) {
        if (invoiceIds == null || invoiceIds.isEmpty()) return;
        AtomicDB.runVoid(con -> {
            for (int id : invoiceIds) {
                try {
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
            String filePath
    ) {
        InvoiceMaster inv = new InvoiceMaster(
                invoice.getInvoiceNo(),
                invoice.getClientId(),
                invoice.getClientName(),
                invoice.getInvoiceDate(),
                invoice.getGrandTotal(),
                type,
                status
        );

        inv.setFilePath(filePath);

        AtomicDB.runVoid(con -> repo.insert(con, inv));
    }
    
    public void registerDateRangeInvoice(
            Invoice invoice,
            LocalDate from,
            LocalDate to,
            String type,
            String filePath
    ) {

        AtomicDB.runVoid(con -> {

            // Lookup by client+period only (reuse invoice from monthly or date range)
            Optional<InvoiceMaster> existing =
                    repo.findActiveByClientPeriod(con, invoice.getClientId(), from, to);

            // =====================================================
            // ♻️ EXISTING INVOICE FOUND
            // =====================================================
            if (existing.isPresent()) {

                InvoiceMaster old = existing.get();

                invoice.setInvoiceNo(old.getInvoiceNo());

                if (filePath != null && !filePath.isBlank()) {
                    old.setFilePath(filePath);
                    old.setStatus("SENT");
                    repo.update(con, old);
                }

                return;
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
                    "SENT"
            );

            inv.setPeriodFrom(from);
            inv.setPeriodTo(to);
            inv.setFilePath(filePath);

            repo.insert(con, inv);
        });
    }



    
    

    
    /* =========================================================
       MARK SENT
       ========================================================= */
    public void markSent(int invoiceId) {
        AtomicDB.runVoid(con ->
                repo.updatePayment(con, invoiceId, 0, 0, "SENT", LocalDate.now())
        );
    }

    /* =========================================================
       REGISTER PAYMENT / PARTIAL / FULL
       ========================================================= */
    public void registerPayment(int invoiceId, double amount, double totalDue) {

        AtomicDB.runVoid(con -> {

            String status = amount >= totalDue ? "PAID" : "PARTIAL_PAID";

            repo.updatePayment(
                    con,
                    invoiceId,
                    amount,
                    totalDue - amount,
                    status,
                    LocalDate.now()
            );
        });
    }

    /* =========================================================
    REGISTER MONTHLY INVOICES (no duplicate insert, update file path on regenerate)
    Same behavior as date range: one invoice per client/period, regeneration updates file path
    ========================================================= */
    public void registerMonthlyInvoices(
            Map<String, Invoice> invoiceMap,
            LocalDate from,
            LocalDate to,
            String type,
            String filePath
    ) {

        AtomicDB.runVoid(con -> {

            for (Invoice invoice : invoiceMap.values()) {

                // Lookup by client+period only (reuse invoice from date range or monthly)
                Optional<InvoiceMaster> existing =
                        repo.findActiveByClientPeriod(con, invoice.getClientId(), from, to);

                if (existing.isPresent()) {

                    InvoiceMaster old = existing.get();

                    invoice.setInvoiceNo(old.getInvoiceNo());

                    // If file generated → update path/status (regeneration overwrites)
                    if (filePath != null && !filePath.isBlank()) {
                        old.setFilePath(filePath);
                        old.setStatus("SENT");
                        repo.update(con, old);
                    }

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
                        "SENT"
                );

                inv.setPeriodFrom(from);
                inv.setPeriodTo(to);
                inv.setFilePath(filePath);

                repo.insert(con, inv);
            }
        });
    }

 
 
    /* =========================================================
       VOID INVOICE
       ========================================================= */
    public void voidInvoice(int invoiceId, String reason) {

        AtomicDB.runVoid(con ->
                repo.voidInvoice(con, invoiceId, reason, LocalDate.now())
        );
    }

    /* =========================================================
       GET RECENT
       ========================================================= */
    public List<InvoiceMaster> getRecentInvoices(int limit) {

        return AtomicDB.run(con -> repo.findRecent(con, limit));
    }

    /* =========================================================
       UPDATE INDIVIDUAL STATUSES
       ========================================================= */
    public void updateInvoiceStatus(int invoiceId, String newStatus) {
        AtomicDB.runVoid(con -> {
            InvoiceMaster inv = repo.findById(con, invoiceId);
            if (inv != null) {
                inv.setStatus(newStatus);
                repo.update(con, inv);
            }
        });
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

    /* =========================================================
       GET INVOICES BY CLIENT
       ========================================================= */
    public List<InvoiceMaster> getInvoicesByClientId(int clientId) {
        return AtomicDB.run(con -> repo.findByClientId(con, clientId));
    }

    /* =========================================================
       GET FILTERED INVOICES (FOR VIEW INVOICES SCREEN)
       ========================================================= */
    public List<InvoiceMaster> getFilteredInvoices(Integer clientId, String status, LocalDate start, LocalDate end) {
        return AtomicDB.run(con -> repo.findFiltered(con, clientId, status, start, end));
    }
}