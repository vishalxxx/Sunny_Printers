package service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.concurrent.Task;
import model.Invoice;
import model.InvoiceJob;
import model.InvoiceLine;
import utils.AtomicDB;
import utils.DBConnection;
import service.SettingsService;

public class InvoiceBuilderService {

	private final SettingsService settingsService = new SettingsService();

	// =========================================================
	// ✅ BUILD SINGLE INVOICE BY CLIENT ID (BEST METHOD)
	// =========================================================
	public Invoice buildInvoiceForClient(int clientId, String clientName, String businessName, LocalDate fromDate,
			LocalDate toDate) {

		return AtomicDB.run(con -> {

			Invoice invoice = new Invoice();

			try {
				invoice.setInvoiceNo(settingsService.generateNextInvoiceNumber(con));
			} catch (Exception e) {
				throw new RuntimeException("Failed to generate invoice number", e);
			}

			invoice.setInvoiceDate(LocalDate.now());

			invoice.setCompanyName("SUNNY PRINTERS");
			invoice.setCompanyAddress("B-234, Naraina Industrial Area,\nPhase-1, New Delhi-110028");
			invoice.setCompanyContact("9811269375 9999662547");
			invoice.setEmail("sunny.printers@gmail.com");

			invoice.setClientName(businessName + " (" + clientName + ")");
			invoice.setFromDate(fromDate);
			invoice.setToDate(toDate);
			invoice.setClientId(clientId);
			invoice.setInvoiceType("DATE_RANGE");
			invoice.setStatus("SENT");

			loadJobsIntoInvoice(con, invoice, clientId, fromDate, toDate);

			return invoice;
		});
	}

	public Invoice buildInvoiceForClientByJobs(int clientId, String clientName, String businessName,
			List<Integer> jobIds) {

		return AtomicDB.run(con -> {

			Invoice invoice = new Invoice();

			try {
				invoice.setInvoiceNo(settingsService.generateNextInvoiceNumber(con));
			} catch (Exception e) {
				throw new RuntimeException("Failed to generate invoice number", e);
			}

			invoice.setInvoiceDate(LocalDate.now());

			invoice.setCompanyName("SUNNY PRINTERS");
			invoice.setCompanyAddress("B-234, Naraina Industrial Area,\nPhase-1, New Delhi-110028");
			invoice.setCompanyContact("9811269375 9999662547");
			invoice.setEmail("sunny.printers@gmail.com");

			invoice.setClientName(businessName + " (" + clientName + ")");
			invoice.setClientId(clientId);
			invoice.setInvoiceType("JOB_SPECIFIC");
			invoice.setStatus("SENT");

			loadSelectedJobs(con, invoice, clientId, jobIds);

			return invoice;
		});
	}

	private void loadSelectedJobs(Connection con, Invoice invoice, int clientId, List<Integer> jobIds) {

		if (jobIds == null || jobIds.isEmpty())
			return;

		String placeholders = String.join(",", jobIds.stream().map(x -> "?").toList());

		String sql = """
				SELECT j.id AS job_id,
				       j.job_no,
				       j.job_date,
				       j.job_title,
				       ji.description,
				       ji.amount,
				       ji.type,
				       ji.sort_order
				FROM jobs j
				JOIN job_items ji ON ji.job_id = j.id
				WHERE j.client_id = ?
				  AND j.status IN ('Created', 'In Progress', 'Completed')
				  AND j.id IN (""" + placeholders + ") " + "ORDER BY j.job_date, j.id, ji.sort_order";

		try (PreparedStatement ps = con.prepareStatement(sql)) {

			int index = 1;
			ps.setInt(index++, clientId);

			for (Integer id : jobIds)
				ps.setInt(index++, id);

			ResultSet rs = ps.executeQuery();

			Map<Integer, InvoiceJob> jobMap = new LinkedHashMap<>();

			while (rs.next()) {

				int jobId = rs.getInt("job_id");

				InvoiceJob job = jobMap.get(jobId);

				if (job == null) {
					job = new InvoiceJob();
					job.setJobId(jobId);
					job.setJobNo(rs.getString("job_no"));
					job.setJobName(rs.getString("job_title"));
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
			throw new RuntimeException("Failed loading selected jobs", e);
		}
	}

	// =========================================================
	// ✅ BUILD MONTHLY INVOICES FOR ALL CLIENTS (BY ID)
	// =========================================================
	public Map<String, Invoice> buildMonthlyInvoicesForAllClients(int year, int month) {

    return AtomicDB.run(con -> {

        Map<String, Invoice> invoiceMap = new LinkedHashMap<>();

        YearMonth ym = YearMonth.of(year, month);
        LocalDate fromDate = ym.atDay(1);
        LocalDate toDate = ym.atEndOfMonth();

        // ✅ ONLY clients who actually have jobs in this month
        String sql = """
            SELECT DISTINCT
                c.id AS client_id,
                c.client_name,
                c.business_name
            FROM jobs j
            JOIN clients c ON c.id = j.client_id
            WHERE DATE(j.job_date) BETWEEN ? AND ?
            AND j.status IN ('Created', 'In Progress', 'Completed')
            ORDER BY c.business_name, c.client_name, c.id
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, fromDate.toString());
            ps.setString(2, toDate.toString());

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                int clientId = rs.getInt("client_id");
                String clientName = rs.getString("client_name");
                String businessName = rs.getString("business_name");

                // 🔹 Build invoice using SAME field structure as single invoice
                Invoice invoice = new Invoice();

                invoice.setCompanyName("SUNNY PRINTERS");
                invoice.setCompanyAddress("B-234, Naraina Industrial Area,\nPhase-1, New Delhi-110028");
                invoice.setCompanyContact("9811269375 9999662547");
                invoice.setEmail("sunny.printers@gmail.com");

                invoice.setClientId(clientId);
                invoice.setClientName(businessName + " (" + clientName + ")");

                // ✅ Monthly invoice date should be end of month
                invoice.setInvoiceDate(toDate);

                invoice.setFromDate(fromDate);
                invoice.setToDate(toDate);

                invoice.setInvoiceType("MONTHLY_BULK");
                invoice.setStatus("SENT");

                // 🔹 Load jobs
                loadJobsIntoInvoice(con, invoice, clientId, fromDate, toDate);

                if (!invoice.getJobs().isEmpty()) {
                    invoiceMap.put(invoice.getClientName(), invoice);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed building monthly invoices for " + ym, e);
        }

        // 🔥 Generate invoice numbers in ONE DB call
        String[] numbers = settingsService.generateNextInvoiceNumbers(con, invoiceMap.size());

        int i = 0;
        for (Invoice inv : invoiceMap.values()) {
            inv.setInvoiceNo(numbers[i++]);
        }

        return invoiceMap;
    });
}


	private void loadJobsIntoInvoice(Connection con, Invoice invoice, int clientId, LocalDate fromDate,
			LocalDate toDate) {

		String sql = """
				SELECT j.id AS job_id,
				j.job_no,
				j.job_date,
				j.job_title,
				ji.description,
				ji.amount,
				ji.type,
				ji.sort_order
				FROM jobs j
				JOIN job_items ji ON ji.job_id = j.id
				WHERE j.client_id = ?
				AND DATE(j.job_date) BETWEEN ? AND ?
				AND j.status IN ('Created', 'In Progress', 'Completed')
				ORDER BY j.job_date, j.id, ji.sort_order
				""";

		try (PreparedStatement ps = con.prepareStatement(sql)) {

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
					job.setJobName(rs.getString("job_title"));
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
			throw new RuntimeException("Failed loading jobs", e);
		}
	}

	private void log(String msg) {
		System.out.println("[InvoiceBuilder] " + msg);
	}

}
