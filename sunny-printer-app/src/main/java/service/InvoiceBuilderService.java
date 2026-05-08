package service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.concurrent.Task;
import model.Invoice;
import model.InvoiceMaster;
import model.InvoiceJob;
import model.InvoiceLine;
import utils.AtomicDB;
import utils.CompanyProfile;
import utils.DBConnection;
import service.SettingsService;

public class InvoiceBuilderService {

	private final SettingsService settingsService = new SettingsService();

	// =========================================================
	// ✅ BUILD SINGLE INVOICE BY CLIENT ID (BEST METHOD)
	// =========================================================
	public Invoice buildInvoiceForClient(int clientId, String clientName, String businessName, LocalDate fromDate,
			LocalDate toDate, LocalDate invoiceDate) {
		return buildInvoiceForClient(clientId, clientName, businessName, fromDate, toDate, invoiceDate, true);
	}

	/**
	 * @param reserveTempInvoiceNumber when false (PDF preview), uses "PREVIEW" and does
	 *                                 not advance the TEMP-* sequence.
	 */
	public Invoice buildInvoiceForClient(int clientId, String clientName, String businessName, LocalDate fromDate,
			LocalDate toDate, LocalDate invoiceDate, boolean reserveTempInvoiceNumber) {

		return AtomicDB.run(con -> {

			Invoice invoice = new Invoice();

			if (reserveTempInvoiceNumber) {
				try {
					invoice.setInvoiceNo(settingsService.generateNextTempInvoiceNumber(con));
				} catch (Exception e) {
					throw new RuntimeException("Failed to generate temp invoice number", e);
				}
			} else {
				invoice.setInvoiceNo("PREVIEW");
			}

			invoice.setInvoiceDate(invoiceDate != null ? invoiceDate : LocalDate.now());

			CompanyProfile.applyToInvoice(invoice);

			invoice.setClientName(businessName + " (" + clientName + ")");
			invoice.setFromDate(fromDate);
			invoice.setToDate(toDate);
			invoice.setClientId(clientId);
			invoice.setInvoiceType("DATE_RANGE");
			invoice.setStatus("DRAFT");

			loadJobsIntoInvoice(con, invoice, clientId, fromDate, toDate);

			return invoice;
		});
	}

	public Invoice buildInvoiceForClientByJobs(int clientId, String clientName, String businessName,
			List<Integer> jobIds, LocalDate invoiceDate) {
		return buildInvoiceForClientByJobs(clientId, clientName, businessName, jobIds, invoiceDate, true);
	}

	/**
	 * @param reserveTempInvoiceNumber when false (e.g. PDF preview), uses a fixed
	 *                                   invoice no and does not advance TEMP-* sequence.
	 */
	public Invoice buildInvoiceForClientByJobs(int clientId, String clientName, String businessName,
			List<Integer> jobIds, LocalDate invoiceDate, boolean reserveTempInvoiceNumber) {

		return AtomicDB.run(con -> {

			Invoice invoice = new Invoice();

			if (reserveTempInvoiceNumber) {
				try {
					invoice.setInvoiceNo(settingsService.generateNextTempInvoiceNumber(con));
				} catch (Exception e) {
					throw new RuntimeException("Failed to generate temp invoice number", e);
				}
			} else {
				invoice.setInvoiceNo("PREVIEW");
			}

			invoice.setInvoiceDate(invoiceDate != null ? invoiceDate : LocalDate.now());

			CompanyProfile.applyToInvoice(invoice);

			invoice.setClientName(businessName + " (" + clientName + ")");
			invoice.setClientId(clientId);
			invoice.setInvoiceType("JOB_SPECIFIC");
			invoice.setStatus("DRAFT");

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
				  AND j.status = 'Completed'
				  AND j.invoice_id IS NULL
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

	/**
	 * Rebuild a printable {@link Invoice} from a saved {@code invoice_master} row (for PDF export).
	 */
	public Invoice buildInvoiceFromMasterForPdfExport(int invoiceMasterId) {
		InvoiceMasterService invoiceMasterService = new InvoiceMasterService();
		InvoiceMaster master = invoiceMasterService.getInvoiceById(invoiceMasterId);
		if (master == null) {
			throw new IllegalArgumentException("Invoice not found: id=" + invoiceMasterId);
		}
		return AtomicDB.run(con -> {
			Invoice invoice = new Invoice();
			CompanyProfile.applyToInvoice(invoice);
			invoice.setInvoiceNo(master.getInvoiceNo());
			invoice.setInvoiceDate(master.getInvoiceDate() != null ? master.getInvoiceDate() : LocalDate.now());
			invoice.setClientName(master.getClientName());
			invoice.setClientId(master.getClientId());
			invoice.setFromDate(master.getPeriodFrom());
			invoice.setToDate(master.getPeriodTo());
			invoice.setInvoiceType(master.getType());
			invoice.setStatus(master.getStatus());
			invoice.setMasterDocumentSeries(master.resolveDocumentSeries());
			invoice.setGrandTotal(0);

			List<Integer> jobIds = loadJobIdsForSavedInvoice(con, master.getId());
			loadJobLinesForSavedInvoice(con, invoice, jobIds);
			if (invoice.getJobs().isEmpty()) {
				invoice.setGrandTotal(master.getAmount());
			}
			return invoice;
		});
	}

	private List<Integer> loadJobIdsForSavedInvoice(Connection con, int invoiceId) {
		List<Integer> ids = new ArrayList<>();
		try {
			String sql = "SELECT job_id FROM invoice_job_mapping WHERE invoice_id = ? ORDER BY job_id";
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, invoiceId);
				ResultSet rs = ps.executeQuery();
				while (rs.next()) {
					ids.add(rs.getInt(1));
				}
			}
			if (!ids.isEmpty()) {
				return ids;
			}
			String sql2 = "SELECT id FROM jobs WHERE invoice_id = ? ORDER BY id";
			try (PreparedStatement ps = con.prepareStatement(sql2)) {
				ps.setInt(1, invoiceId);
				ResultSet rs = ps.executeQuery();
				while (rs.next()) {
					ids.add(rs.getInt(1));
				}
			}
			return ids;
		} catch (Exception e) {
			throw new RuntimeException("Failed loading job ids for invoice " + invoiceId, e);
		}
	}

	private void loadJobLinesForSavedInvoice(Connection con, Invoice invoice, List<Integer> jobIds) {
		if (jobIds == null || jobIds.isEmpty()) {
			return;
		}
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
				WHERE j.id IN (""" + placeholders + ") ORDER BY j.job_date, j.id, ji.sort_order";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			int index = 1;
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
					String jobNo = rs.getString("job_no");
					job.setJobNo(jobNo);
					job.setJobName(jobDisplayTitle(rs, jobId, jobNo));
					LocalDate jd = parseJobDateFlexible(rs.getString("job_date"));
					job.setJobDate(jd != null ? jd : invoice.getInvoiceDate());
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
			throw new RuntimeException("Failed loading saved invoice job lines", e);
		}
	}

	// =========================================================
	// ✅ BUILD MONTHLY INVOICES FOR ALL CLIENTS (BY ID)
	// =========================================================
	public Map<String, Invoice> buildMonthlyInvoicesForAllClients(int year, int month, LocalDate invoiceDate) {

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
            AND j.status = 'Completed'
            AND j.invoice_id IS NULL
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

                CompanyProfile.applyToInvoice(invoice);

                invoice.setClientId(clientId);
                invoice.setClientName(businessName + " (" + clientName + ")");

                // ✅ Monthly invoice date should be custom or default to end of month
                invoice.setInvoiceDate(invoiceDate != null ? invoiceDate : toDate);

                invoice.setFromDate(fromDate);
                invoice.setToDate(toDate);

                invoice.setInvoiceType("MONTHLY_BULK");
                invoice.setStatus("DRAFT");

                // 🔹 Load jobs
                loadJobsIntoInvoice(con, invoice, clientId, fromDate, toDate);

                if (!invoice.getJobs().isEmpty()) {
                    invoiceMap.put(invoice.getClientName(), invoice);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed building monthly invoices for " + ym, e);
        }

        // 🔥 Generate temp invoice numbers in ONE DB call
        String[] numbers = settingsService.generateNextTempInvoiceNumbers(con, invoiceMap.size());

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
				AND LOWER(TRIM(REPLACE(COALESCE(j.status,''), '_', ' '))) = 'completed'
				AND j.invoice_id IS NULL
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

	private static String jobDisplayTitle(ResultSet rs, int jobId, String jobNo) throws SQLException {
		String t = rs.getString("job_title");
		if (t != null && !t.isBlank()) {
			return t.trim();
		}
		if (jobNo != null && !jobNo.isBlank()) {
			return jobNo.trim();
		}
		return "Job " + jobId;
	}

	private static LocalDate parseJobDateFlexible(String dateStr) {
		if (dateStr == null || dateStr.isBlank()) {
			return null;
		}
		String s = dateStr.trim();
		try {
			return LocalDate.parse(s);
		} catch (DateTimeParseException e1) {
			if (s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
				try {
					return LocalDate.parse(s.substring(0, 10));
				} catch (DateTimeParseException ignored) {
				}
			}
			try {
				return LocalDate.parse(s, DateTimeFormatter.ofPattern("d/M/uuuu"));
			} catch (DateTimeParseException ignored) {
			}
			try {
				return LocalDate.parse(s, DateTimeFormatter.ofPattern("dd/MM/uuuu"));
			} catch (DateTimeParseException ignored) {
			}
		}
		return null;
	}

}
