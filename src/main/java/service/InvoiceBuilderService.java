package service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import model.Invoice;
import model.InvoiceJob;
import model.InvoiceLine;
import utils.DBConnection;
import service.SettingsService;

public class InvoiceBuilderService {

	private final SettingsService settingsService = new SettingsService();

    // =========================================================
    // ✅ BUILD SINGLE INVOICE BY CLIENT ID (BEST METHOD)
    // =========================================================
    public Invoice buildInvoiceForClient(
            int clientId,
            String clientName,
            String businessName,
            LocalDate fromDate,
            LocalDate toDate
    ) {

        Invoice invoice = new Invoice();
        try {
            invoice.setInvoiceNo(settingsService.generateNextInvoiceNumber());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate invoice number", e);
        }
        invoice.setInvoiceDate(LocalDate.now());

        // ✅ Your company details
        invoice.setCompanyName("SUNNY PRINTERS");
        invoice.setCompanyAddress("B-234, Naraina Industrial Area,\nPhase-1, New Delhi-110028");
        invoice.setCompanyContact("9811269375 9999662547");
        invoice.setEmail("sunny.printers@gmail.com");

        // ✅ Client details
        invoice.setClientName(businessName + " (" + clientName + ")");
        invoice.setFromDate(fromDate);
        invoice.setToDate(toDate);
        invoice.setClientId(clientId);
        invoice.setInvoiceType("DATE_RANGE");
        invoice.setStatus("SENT");


        String sql = """
                SELECT
                    j.id        AS job_id,
                    j.job_no    AS job_no,
                    j.job_date  AS job_date,
                    j.job_title AS job_name,

                    ji.description,
                    ji.amount,
                    ji.type,
                    ji.sort_order

                FROM jobs j
                JOIN job_items ji ON ji.job_id = j.id

                WHERE j.client_id = ?
                  AND DATE(j.job_date) BETWEEN ? AND ?

                ORDER BY j.job_date, j.id, ji.sort_order;
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, clientId);
            ps.setString(2, fromDate.toString());
            ps.setString(3, toDate.toString());

            ResultSet rs = ps.executeQuery();

            Map<Integer, InvoiceJob> jobMap = new LinkedHashMap<>();

            while (rs.next()) {

                int jobId = rs.getInt("job_id");

                InvoiceJob job = jobMap.get(jobId);

                if (job == null) {
                    job = new InvoiceJob();
                    job.setJobId(jobId);
                    job.setJobNo(rs.getString("job_no"));
                    job.setJobName(rs.getString("job_name"));
                    job.setJobDate(LocalDate.parse(rs.getString("job_date")));

                    jobMap.put(jobId, job);
                    invoice.getJobs().add(job);
                }

                InvoiceLine line = new InvoiceLine();
                line.setDescription(rs.getString("description"));
                line.setAmount(rs.getDouble("amount"));
                line.setType(rs.getString("type"));
                line.setSortOrder(rs.getInt("sort_order"));

                job.addLine(line);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to build invoice for clientId=" + clientId, e);
        }

        return invoice;
    }
    
    public Invoice buildInvoiceForClientByJobs(
            int clientId,
            String clientName,
            String businessName,
            List<Integer> jobIds
    ) {

        Invoice invoice = new Invoice();
        try {
            invoice.setInvoiceNo(settingsService.generateNextInvoiceNumber());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate invoice number", e);
        }        invoice.setInvoiceDate(LocalDate.now());

        // ✅ Your company details
        invoice.setCompanyName("SUNNY PRINTERS");
        invoice.setCompanyAddress("B-234, Naraina Industrial Area,\nPhase-1, New Delhi-110028");
        invoice.setCompanyContact("9811269375 9999662547");
        invoice.setEmail("sunny.printers@gmail.com");
        // ✅ Client details
        invoice.setClientName(businessName + " (" + clientName + ")");
        invoice.setClientId(clientId);
        invoice.setClientId(clientId);
        invoice.setInvoiceType("JOB_SPECIFIC");
        invoice.setStatus("SENT");


        if (jobIds == null || jobIds.isEmpty()) {
            return invoice;
        }

        // ✅ Dynamic placeholders: (?, ?, ?)
        String placeholders = String.join(",", jobIds.stream().map(x -> "?").toList());

        String sql =
                "SELECT " +
                "    j.id        AS job_id, " +
                "    j.job_no    AS job_no, " +
                "    j.job_date  AS job_date, " +
                "    j.job_title AS job_name, " +
                "    ji.description, " +
                "    ji.amount, " +
                "    ji.type, " +
                "    ji.sort_order " +
                "FROM jobs j " +
                "JOIN job_items ji ON ji.job_id = j.id " +
                "WHERE j.client_id = ? " +
                "  AND j.id IN (" + placeholders + ") " +
                "ORDER BY DATE(j.job_date), j.id, ji.sort_order;";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            int index = 1;
            ps.setInt(index++, clientId);

            for (Integer id : jobIds) {
                ps.setInt(index++, id);
            }

            ResultSet rs = ps.executeQuery();

            Map<Integer, InvoiceJob> jobMap = new LinkedHashMap<>();

            while (rs.next()) {

                int jobId = rs.getInt("job_id");

                InvoiceJob job = jobMap.get(jobId);

                if (job == null) {
                    job = new InvoiceJob();
                    job.setJobId(jobId);
                    job.setJobNo(rs.getString("job_no"));
                    job.setJobName(rs.getString("job_name"));
                    job.setJobDate(LocalDate.parse(rs.getString("job_date")));

                    jobMap.put(jobId, job);
                    invoice.getJobs().add(job);
                }

                InvoiceLine line = new InvoiceLine();
                line.setDescription(rs.getString("description"));
                line.setAmount(rs.getDouble("amount"));
                line.setType(rs.getString("type"));
                line.setSortOrder(rs.getInt("sort_order"));

                job.addLine(line);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to build invoice by selected jobs for clientId=" + clientId, e);
        }

        return invoice;
    }


    // =========================================================
    // ✅ BUILD MONTHLY INVOICES FOR ALL CLIENTS (BY ID)
    // =========================================================
    public Map<String, Invoice> buildMonthlyInvoicesForAllClients(int year, int month) {

        Map<String, Invoice> invoiceMap = new LinkedHashMap<>();

        YearMonth ym = YearMonth.of(year, month);

        LocalDate fromDate = ym.atDay(1);
        LocalDate toDate   = ym.atEndOfMonth();

        String sql = """
            SELECT DISTINCT
                c.id AS client_id,
                c.client_name,
                c.business_name
            FROM jobs j
            JOIN clients c ON c.id = j.client_id
            WHERE DATE(j.job_date) BETWEEN ? AND ?
            ORDER BY c.business_name, c.client_name, c.id
        """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, fromDate.toString());
            ps.setString(2, toDate.toString());

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                int clientId = rs.getInt("client_id");
                String clientName = rs.getString("client_name");
                String businessName = rs.getString("business_name");

                Invoice invoice = buildInvoiceForClient(
                        clientId,
                        clientName,
                        businessName,
                        fromDate,
                        toDate
                );

                if (invoice.getJobs().isEmpty()) continue;

                invoice.setClientId(clientId);
                invoice.setInvoiceType("MONTHLY_BULK");
                invoice.setStatus("SENT");
                
                // ✅ Sheet name should be business name
                String sheetKey = businessName;

                // ✅ prevent overwrite
                if (invoiceMap.containsKey(sheetKey)) {
                    sheetKey = businessName + " [ID:" + clientId + "]";
                }

                invoiceMap.put(sheetKey, invoice);
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to build monthly invoices for " + ym,
                    e
            );
        }

        return invoiceMap;
    }



    
}
