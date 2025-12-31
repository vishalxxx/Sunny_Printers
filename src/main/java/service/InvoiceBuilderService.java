package service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

import model.Invoice;
import model.InvoiceJob;
import model.InvoiceLine;
import utils.DBConnection;

public class InvoiceBuilderService {

	public Invoice buildInvoiceForJob(int jobId) {

		Invoice invoice = new Invoice();
		invoice.setInvoiceNo(generateInvoiceNo());
		invoice.setInvoiceDate(LocalDate.now());
		invoice.setCompanyName("SUNNY PRINTERS");
		invoice.setCompanyAddress(
				"B- 234, Naraina Industrail Area, 		\r\n" + "Phase- 1, New Delhi- 110028		");
		invoice.setCompanyContact("9811269375" + " " + "9999662547");
		invoice.setEmail("sunny.printers@gmail.com");

		String sql = """
				    SELECT
				        j.id            AS job_id,
				        j.job_no        AS job_no,
				        j.created_at  AS job_date,
				        j.job_title      AS job_name,
				        ji.description,
				        ji.amount,
				        ji.type,
				        ji.sort_order
				    FROM jobs j
				    JOIN job_items ji ON ji.job_id = j.id
				    WHERE j.id = ?
				    ORDER BY ji.sort_order, ji.id
				""";

		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setInt(1, jobId);
			ResultSet rs = ps.executeQuery();

			InvoiceJob job = null;
			double grandTotal = 0;

			while (rs.next()) {

				if (job == null) {
					job = new InvoiceJob();
					job.setJobId(rs.getInt("job_id"));
					job.setJobNo(rs.getString("job_no"));
					job.setJobName(rs.getString("job_name"));
					job.setJobDate(rs.getDate("job_date").toLocalDate());
					invoice.getJobs().add(job);
				}

				InvoiceLine line = new InvoiceLine();
				line.setDescription(rs.getString("description"));
				line.setAmount(rs.getDouble("amount"));
				line.setType(rs.getString("type"));
				line.setSortOrder(rs.getInt("sort_order"));

				job.addLine(line);
				grandTotal += line.getAmount();
			}

			invoice.setGrandTotal(grandTotal);

		} catch (Exception e) {
			throw new RuntimeException("Failed to build invoice", e);
		}

		return invoice;
	}

	private String generateInvoiceNo() {
		return "INV-" + System.currentTimeMillis();
	}
}
