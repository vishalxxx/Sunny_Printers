package scratch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import model.Client;
import model.Job;
import model.JobItem;
import repository.ClientRepository;
import repository.JobItemRepository;
import repository.JobRepository;
import service.sync.SyncReport;
import service.sync.UniversalSyncEngine;
import utils.AtomicDB;
import utils.ClientIdentifiers;
import utils.DBConnection;
import utils.JobIdentifiers;

public final class E2eFullFlowSeed {

    public static final String MARKER = "E2E-FLOW";

    private E2eFullFlowSeed() {
    }

    public record FlowIds(
            String clientUuid, String jobUuid, String jobItemUuid, String invoiceUuid,
            String mappingUuid, String paymentUuid, String paymentAllocUuid,
            String refundUuid, String refundAllocUuid, String creditNoteUuid, String debitNoteUuid) {
    }

    public static void main(String[] args) throws Exception {
        DBConnection.getConnection().close();
        FlowIds ids = seedLocalChain();
        printIds(ids);
        if (api.supabase.SupabaseGate.restClientIfConfigured().isEmpty()) {
            System.out.println("\n[WARN] Supabase not configured - local seed only.");
            printVerificationQueries(ids);
            return;
        }
        if (!api.supabase.SupabaseReachability.isReachable()) {
            System.out.println("\n[WARN] Supabase unreachable - local seed done.");
            printVerificationQueries(ids);
            return;
        }
        SyncReport report = UniversalSyncEngine.syncAllPending();
        System.out.println("\n[SYNC] " + report);
        printVerificationQueries(ids);
    }

    public static FlowIds seedLocalChain() throws Exception {
        String clientUuid = ClientIdentifiers.newUuidV7String();
        String jobUuid = JobIdentifiers.newUuidString();
        String jobItemUuid = ClientIdentifiers.newUuidV7String();
        String invoiceUuid = ClientIdentifiers.newUuidV7String();
        String mappingUuid = ClientIdentifiers.newUuidV7String();
        String paymentUuid = ClientIdentifiers.newUuidV7String();
        String paymentAllocUuid = ClientIdentifiers.newUuidV7String();
        String refundUuid = ClientIdentifiers.newUuidV7String();
        String refundAllocUuid = ClientIdentifiers.newUuidV7String();
        String creditNoteUuid = ClientIdentifiers.newUuidV7String();
        String debitNoteUuid = ClientIdentifiers.newUuidV7String();
        String clientCode = MARKER + "-CL-001";
        String jobCode = MARKER + "/JOB/26-27/001";
        String invoiceNo = MARKER + "/INV/26-27/001";
        String cnNo = MARKER + "/CN/26-27/001";
        String dnNo = MARKER + "/DN/26-27/001";
        LocalDate today = LocalDate.now();
        String clientName = MARKER + " Test Client";

        AtomicDB.runVoid(con -> {
            exec(con, "INSERT INTO clients (uuid, client_code, client_name, business_name, mobile, email, client_type, is_active, sync_status, sync_version, is_deleted) VALUES (?,?,?,?,?,?,'Regular',1,'PENDING',1,0)",
                    clientUuid, clientCode, clientName, MARKER + " Printers Pvt Ltd", "9999900001", "e2e@test.local");

            Job job = new Job();
            job.setUuid(jobUuid);
            job.setClientUuid(clientUuid);
            job.setJobCode(jobCode);
            job.setJobTitle("E2E print job");
            job.setJobDate(today);
            job.setStatus("Completed");
            new JobRepository().insertJob(con, job);
            exec(con, "UPDATE jobs SET sync_status='PENDING', updated_at=datetime('now') WHERE uuid=?", jobUuid);

            JobItem item = new JobItem();
            item.setUuid(jobItemUuid);
            item.setJobUuid(jobUuid);
            item.setType("PRINTING");
            item.setDescription("E2E printing lines");
            item.setAmount(5000.0);
            item.setSortOrder(1);
            new JobItemRepository().save(con, item);
            JobRepository.syncAmountFromJobItems(con, jobUuid);

            double invAmount = 5000.0;
            exec(con, "INSERT INTO invoice_master (uuid, invoice_no, client_uuid, client_name, invoice_date, period_from, period_to, amount, paid_amount, due_amount, payment_status, type, status, is_void, document_series, sync_status, sync_version, is_deleted, is_active, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,0,'GST_INVOICE','PENDING',1,0,1,datetime('now'),datetime('now'))",
                    invoiceUuid, invoiceNo, clientUuid, clientName, today.toString(),
                    today.withDayOfMonth(1).toString(), today.toString(), invAmount, 0.0, invAmount, "UNPAID", "DATE_RANGE", "FINAL");
            exec(con, "UPDATE jobs SET invoice_uuid=?, status='Invoiced', sync_status='PENDING', sync_version=COALESCE(sync_version,0)+1, updated_at=datetime('now') WHERE uuid=?", invoiceUuid, jobUuid);
            exec(con, "INSERT INTO invoice_job_mapping (uuid, invoice_uuid, job_uuid, sync_status, created_at, updated_at) VALUES (?,?,?,'PENDING',datetime('now'),datetime('now'))", mappingUuid, invoiceUuid, jobUuid);

            double payAmount = 3000.0;
            exec(con, "INSERT INTO payments (uuid, client_uuid, amount, payment_date, method, type, sync_status, sync_version, is_deleted, is_active, created_at, updated_at) VALUES (?,?,?,?,'Cash','Payment','PENDING',1,0,1,datetime('now'),datetime('now'))", paymentUuid, clientUuid, payAmount, today.toString());
            exec(con, "INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount, sync_status, created_at, updated_at) VALUES (?,?,?,?,'PENDING',datetime('now'),datetime('now'))", paymentAllocUuid, paymentUuid, invoiceUuid, payAmount);
            exec(con, "INSERT INTO payment_details (uuid, payment_uuid, field_key, field_value, sync_status, created_at, updated_at) VALUES (?,?,?,?,'PENDING',datetime('now'),datetime('now')) ON CONFLICT(payment_uuid, field_key) DO UPDATE SET field_value=excluded.field_value",
                    ClientIdentifiers.newUuidV7String(), paymentUuid, "receipt_no", MARKER + "/RCPT/001");
            double paid = payAmount;
            double due = invAmount - paid;
            exec(con, "UPDATE invoice_master SET paid_amount=?, due_amount=?, payment_status='PARTIAL PAID', sync_status='PENDING', updated_at=datetime('now') WHERE uuid=?", paid, due, invoiceUuid);

            double refundAmount = -500.0;
            exec(con, "INSERT INTO payments (uuid, client_uuid, amount, payment_date, method, type, sync_status, sync_version, is_deleted, is_active, created_at, updated_at) VALUES (?,?,?,?,'Cash','Refund','PENDING',1,0,1,datetime('now'),datetime('now'))", refundUuid, clientUuid, refundAmount, today.toString());
            exec(con, "INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount, sync_status, created_at, updated_at) VALUES (?,?,?,?,'PENDING',datetime('now'),datetime('now'))", refundAllocUuid, refundUuid, invoiceUuid, refundAmount);
            paid += refundAmount;
            due = invAmount - paid;
            exec(con, "UPDATE invoice_master SET paid_amount=?, due_amount=?, payment_status='PARTIAL PAID', sync_status='PENDING', updated_at=datetime('now') WHERE uuid=?", paid, due, invoiceUuid);

            exec(con, "INSERT INTO invoice_adjustments (uuid, invoice_uuid, type, note_no, amount, reason, date, sync_status, sync_version, is_deleted, is_active, created_at, updated_at) VALUES (?,?,?,?,?,?,?,'PENDING',1,0,1,datetime('now'),datetime('now'))",
                    creditNoteUuid, invoiceUuid, "Credit Note", cnNo, 200.0, "E2E credit note", today.toString());
            exec(con, "INSERT INTO invoice_adjustments (uuid, invoice_uuid, type, note_no, amount, reason, date, sync_status, sync_version, is_deleted, is_active, created_at, updated_at) VALUES (?,?,?,?,?,?,?,'PENDING',1,0,1,datetime('now'),datetime('now'))",
                    debitNoteUuid, invoiceUuid, "Debit Note", dnNo, 100.0, "E2E debit note", today.toString());
        });

        return new FlowIds(clientUuid, jobUuid, jobItemUuid, invoiceUuid, mappingUuid,
                paymentUuid, paymentAllocUuid, refundUuid, refundAllocUuid, creditNoteUuid, debitNoteUuid);
    }

    private static void exec(Connection con, String sql, Object... params) throws Exception {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                Object p = params[i];
                if (p instanceof String s) {
                    ps.setString(i + 1, s);
                } else if (p instanceof Double d) {
                    ps.setDouble(i + 1, d);
                } else {
                    ps.setObject(i + 1, p);
                }
            }
            ps.executeUpdate();
        }
    }

    private static void printIds(FlowIds ids) {
        System.out.println("\n=== E2E FLOW IDs ===");
        Map<String, String> m = new LinkedHashMap<>();
        m.put("client_uuid", ids.clientUuid());
        m.put("job_uuid", ids.jobUuid());
        m.put("job_item_uuid", ids.jobItemUuid());
        m.put("invoice_uuid", ids.invoiceUuid());
        m.put("payment_uuid", ids.paymentUuid());
        m.put("refund_uuid", ids.refundUuid());
        m.put("credit_note_uuid", ids.creditNoteUuid());
        m.put("debit_note_uuid", ids.debitNoteUuid());
        m.forEach((k, v) -> System.out.println("  " + k + " = " + v));
    }

    private static void printVerificationQueries(FlowIds ids) {
        System.out.println("\nSQLite:  scripts/e2e_verify_sqlite.sql");
        System.out.println("Supabase: scripts/e2e_verify_supabase.sql");
        System.out.println("UUIDs: client=" + ids.clientUuid() + " job=" + ids.jobUuid() + " invoice=" + ids.invoiceUuid());
    }
}