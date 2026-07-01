from pathlib import Path

content = """package api.supabase.jobs;

import java.sql.Connection;
import java.sql.PreparedStatement;

import api.supabase.SupabaseRestClient;
import api.supabase.clients.ClientsSupabaseApi;
import api.supabase.invoices.InvoicesSupabaseApi;
import model.Client;
import model.InvoiceMaster;
import model.Job;
import repository.ClientRepository;
import repository.InvoiceMasterRepository;
import utils.DBConnection;

/** Pushes a job to Supabase: client, linked invoice (incl. TEMP), then job. */
public final class JobRemoteUpsert {

    private JobRemoteUpsert() {
    }

    public static void upsert(SupabaseRestClient http, Job job) throws Exception {
        if (job == null || !job.hasUuid()) {
            return;
        }
        ensureClientOnRemote(http, job.getClientUuid());

        String savedInvoiceUuid = job.getInvoiceUuid();
        try {
            if (savedInvoiceUuid != null && !savedInvoiceUuid.isBlank()) {
                try (Connection con = DBConnection.getConnection()) {
                    InvoiceMaster inv = new InvoiceMasterRepository().findByUuid(con, savedInvoiceUuid.trim());
                    if (inv != null) {
                        new InvoicesSupabaseApi(http).upsert(inv);
                        markInvoiceSyncedLocally(savedInvoiceUuid.trim());
                    } else {
                        System.err.println("[JobRemoteUpsert] job " + job.getUuid()
                                + ": invoice " + savedInvoiceUuid + " missing locally");
                        job.setInvoiceUuid(null);
                    }
                }
            }
            new JobsSupabaseApi(http).upsert(job);
        } finally {
            job.setInvoiceUuid(savedInvoiceUuid);
        }
    }

    private static void ensureClientOnRemote(SupabaseRestClient http, String clientUuid) throws Exception {
        if (clientUuid == null || clientUuid.isBlank()) {
            return;
        }
        Client client = new ClientRepository().findByUuid(clientUuid.trim());
        if (client == null) {
            return;
        }
        new ClientsSupabaseApi(http).upsert(client);
        markClientSyncedLocally(clientUuid.trim());
    }

    private static void markInvoiceSyncedLocally(String invoiceUuid) {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE invoice_master SET sync_status='SYNCED', synced_at=datetime('now') WHERE uuid=?")) {
            ps.setString(1, invoiceUuid);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("[JobRemoteUpsert] mark invoice synced: " + e.getMessage());
        }
    }

    private static void markClientSyncedLocally(String clientUuid) {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE clients SET sync_status='SYNCED', synced_at=datetime('now') WHERE uuid=?")) {
            ps.setString(1, clientUuid);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }
}
"""

target = Path(__file__).resolve().parents[1] / "src/main/java/api/supabase/jobs/JobRemoteUpsert.java"
target.write_text(content, encoding="utf-8", newline="\n")
print("wrote", target)
