package service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import model.Invoice;
import model.InvoiceJob;
import model.InvoiceLine;
import utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

import utils.DBConnection;


public class InvoiceBuilderService {

	public Invoice buildInvoiceForClient(String clientName, LocalDate fromDate, LocalDate toDate) {

		Invoice invoice = new Invoice();
		invoice.setInvoiceNo(generateInvoiceNo());
		invoice.setInvoiceDate(LocalDate.now());

		invoice.setCompanyName("SUNNY PRINTERS");
		invoice.setCompanyAddress("B-234, Naraina Industrial Area,\nPhase-1, New Delhi-110028");
		invoice.setCompanyContact("9811269375 9999662547");
		invoice.setEmail("sunny.printers@gmail.com");

		invoice.setClientName(clientName);
		invoice.setFromDate(fromDate);
		invoice.setToDate(toDate);

		String sql = """
					SELECT
				    j.id         AS job_id,
				    j.job_no     AS job_no,
				    j.job_date   AS job_date,
				    j.job_title  AS job_name,

				    ji.description,
				    ji.amount,
				    ji.type,
				    ji.sort_order

				FROM jobs j
				JOIN clients c    ON c.id = j.client_id
				JOIN job_items ji ON ji.job_id = j.id

				WHERE c.client_name = ?
				  AND DATE(j.job_date) BETWEEN ? AND ?

				ORDER BY j.job_date, j.id, ji.sort_order;

								""";

		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setString(1, clientName);
			ps.setString(2, fromDate.toString());
			ps.setString(3, toDate.toString());

			System.out.println(clientName + "   " + fromDate.toString() + "   " + toDate.toString());
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
					invoice.getJobs().add(job); // Jobs are being added to the JOB Map<Integer, InvoiceJob>
				}

				InvoiceLine line = new InvoiceLine();
				line.setDescription(rs.getString("description"));
				line.setAmount(rs.getDouble("amount"));
				line.setType(rs.getString("type"));
				line.setSortOrder(rs.getInt("sort_order"));

				job.addLine(line);
			}
			

		} catch (Exception e) {
			throw new RuntimeException("Failed to build invoice", e);
		}

		return invoice;
	}
	
	

	public Map<String, Invoice> buildMonthlyInvoicesForAllClients(YearMonth month) {

	    Map<String, Invoice> invoiceMap = new LinkedHashMap<>();

	    LocalDate fromDate = month.atDay(1);
	    LocalDate toDate   = month.atEndOfMonth();

	    String sql = """
	        SELECT DISTINCT c.client_name
	        FROM jobs j
	        JOIN clients c ON c.id = j.client_id
	        WHERE DATE(j.job_date) BETWEEN ? AND ?
	        ORDER BY c.client_name
	    """;

	    try (
	        Connection con = DBConnection.getConnection();
	        PreparedStatement ps = con.prepareStatement(sql)
	    ) {

	        ps.setString(1, fromDate.toString());
	        ps.setString(2, toDate.toString());

	        ResultSet rs = ps.executeQuery();

	        while (rs.next()) {

	            String clientName = rs.getString("client_name");

	            Invoice invoice = buildInvoiceForClient(
	                    clientName,
	                    fromDate,
	                    toDate
	            );

	            // Safety: skip empty invoices
	            if (!invoice.getJobs().isEmpty()) {
	                invoiceMap.put(clientName, invoice);
	            }
	        }

	    } catch (Exception e) {
	        throw new RuntimeException(
	                "Failed to build monthly invoices for " + month,
	                e
	        );
	    }

	    return invoiceMap;
	}

	

	public class InvoiceFileNamer {

	    public static String buildFileName(
	            String businessName,
	            String clientName,
	            YearMonth month
	    ) {

	        return sanitize(businessName) + "_"
	             + sanitize(clientName) + "_"
	             + month + "_Invoice.xlsx";
	    }

	    private static String sanitize(String input) {
	        return input
	                .trim()
	                .replaceAll("[^a-zA-Z0-9-_]", "_");
	    }
	}

	
	
	

	private String generateInvoiceNo() {
		return "INV-" + System.currentTimeMillis();
	}
	
	
}
