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
	public Invoice buildInvoiceForClient(String clientId, String clientName, String businessName, LocalDate fromDate,
			LocalDate toDate, LocalDate invoiceDate) {
		return buildInvoiceForClient(clientId, clientName, businessName, fromDate, toDate, invoiceDate, true);
	}

	/**
	 * @param reserveTempInvoiceNumber when false (PDF preview), uses "PREVIEW" and does
	 *                                 not advance the TEMP-* sequence.
	 */
	public Invoice buildInvoiceForClient(String clientId, String clientName, String businessName, LocalDate fromDate,
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

	public Invoice buildInvoiceForClientByJobs(String clientId, String clientName, String businessName,
			List<String> jobUuids, LocalDate invoiceDate) {
		return buildInvoiceForClientByJobs(clientId, clientName, businessName, jobUuids, invoiceDate, true);
	}

	/**
	 * @param reserveTempInvoiceNumber when false (e.g. PDF preview), uses a fixed
	 *                                   invoice no and does not advance TEMP-* sequence.
	 */
	public Invoice buildInvoiceForClientByJobs(String clientId, String clientName, String businessName,
			List<String> jobUuids, LocalDate invoiceDate, boolean reserveTempInvoiceNumber) {

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

			loadSelectedJobs(con, invoice, clientId, jobUuids);

			return invoice;
		});
	}

	private void loadSelectedJobs(Connection con, Invoice invoice, String clientId, List<String> jobUuids) {

		if (jobUuids == null || jobUuids.isEmpty())
			return;

		String placeholders = String.join(",", jobUuids.stream().map(x -> "?").toList());

		String sql = """
				SELECT j.uuid AS job_uuid,
				       j.job_code,
				       j.job_date,
				       j.job_title,
				       j.description AS job_desc,
				       ji.description,
				       ji.amount,
				       ji.type,
				       ji.sort_order
				FROM jobs j
				JOIN job_items ji ON ji.job_uuid = j.uuid
				WHERE j.client_uuid = ?
				  AND LOWER(TRIM(REPLACE(COALESCE(j.status,''), '_', ' '))) = 'completed'
				  AND j.invoice_uuid IS NULL
				  AND j.uuid IN (""" + placeholders + ") " + "ORDER BY j.job_date, j.uuid, ji.sort_order";

		try (PreparedStatement ps = con.prepareStatement(sql)) {

			int index = 1;
			ps.setString(index++, clientId);

			for (String uuid : jobUuids)
				ps.setString(index++, uuid);

			ResultSet rs = ps.executeQuery();

			Map<String, InvoiceJob> jobMap = new LinkedHashMap<>();

			while (rs.next()) {

				String jobUuid = rs.getString("job_uuid");

				InvoiceJob job = jobMap.get(jobUuid);

				if (job == null) {
					job = new InvoiceJob();
					job.setJobUuid(jobUuid);
					job.setJobNo(rs.getString("job_code"));
					String jobDesc = rs.getString("job_desc");
					if (jobDesc != null && !jobDesc.isBlank()) {
						job.setJobName(jobDesc.trim());
					} else {
						job.setJobName(rs.getString("job_title"));
					}
					LocalDate jd = parseJobDateFlexible(rs.getString("job_date"));
					job.setJobDate(jd != null ? jd : invoice.getInvoiceDate());

					jobMap.put(jobUuid, job);
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
	public Invoice buildInvoiceFromMasterForPdfExport(String invoiceUuid) {
		InvoiceMasterService invoiceMasterService = new InvoiceMasterService();
		InvoiceMaster master = invoiceMasterService.getInvoiceById(invoiceUuid);
		if (master == null) {
			throw new IllegalArgumentException("Invoice not found: uuid=" + invoiceUuid);
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

			invoice.setPlaceOfSupply(master.getPlaceOfSupply());
			invoice.setPaymentTerms(master.getPaymentTerms());
			invoice.setDueDate(master.getDueDate());
			invoice.setVehicleDispatch(master.getVehicleDispatch());
			invoice.setPoNo(master.getPoNo());
			invoice.setPoDate(master.getPoDate());
			invoice.setDispatchThrough(master.getDispatchThrough());
			invoice.setLrTrackingNo(master.getLrTrackingNo());
			invoice.setRemarks(master.getRemarks());
			invoice.setEwayBillNo(master.getEwayBillNo());

			repository.ClientRepository clientRepo = new repository.ClientRepository();
			model.Client client = clientRepo.findByUuid(master.getClientId());
			if (client != null) {
				invoice.setBuyerAddress(client.getBillingAddress());
				invoice.setBuyerGstin(client.getGst());
				invoice.setConsigneeName(client.getBusinessName());
				invoice.setConsigneeAddress(client.getShippingAddress());
				invoice.setConsigneeGstin(client.getGst());

				String gst = client.getGst();
				String stateCode = "";
				if (gst != null) {
					String trimmed = gst.trim();
					java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\((\\d{2})\\)").matcher(trimmed);
					if (m.find()) {
						stateCode = m.group(1);
					} else {
						m = java.util.regex.Pattern.compile("^(\\d{2})").matcher(trimmed);
						if (m.find()) {
							stateCode = m.group(1);
						}
					}
				}

				String stateName = "Delhi";
				if (!stateCode.isEmpty()) {
					stateName = switch (stateCode) {
						case "01" -> "Jammu & Kashmir";
						case "02" -> "Himachal Pradesh";
						case "03" -> "Punjab";
						case "04" -> "Chandigarh";
						case "05" -> "Uttarakhand";
						case "06" -> "Haryana";
						case "07" -> "Delhi";
						case "08" -> "Rajasthan";
						case "09" -> "Uttar Pradesh";
						case "10" -> "Bihar";
						case "11" -> "Sikkim";
						case "12" -> "Arunachal Pradesh";
						case "13" -> "Nagaland";
						case "14" -> "Manipur";
						case "15" -> "Mizoram";
						case "16" -> "Tripura";
						case "17" -> "Meghalaya";
						case "18" -> "Assam";
						case "19" -> "West Bengal";
						case "20" -> "Jharkhand";
						case "21" -> "Odisha";
						case "22" -> "Chhattisgarh";
						case "23" -> "Madhya Pradesh";
						case "24" -> "Gujarat";
						case "25" -> "Daman & Diu";
						case "26" -> "Dadra & Nagar Haveli";
						case "27" -> "Maharashtra";
						case "29" -> "Karnataka";
						case "30" -> "Goa";
						case "31" -> "Lakshadweep";
						case "32" -> "Kerala";
						case "33" -> "Tamil Nadu";
						case "34" -> "Puducherry";
						case "35" -> "Andaman & Nicobar Islands";
						case "36" -> "Telangana";
						case "37" -> "Andhra Pradesh";
						case "38" -> "Ladakh";
						default -> "Other State";
					};
					invoice.setBuyerStateName(stateName + " (" + stateCode + ")");
					invoice.setConsigneeStateName(stateName + " (" + stateCode + ")");
				} else {
					invoice.setBuyerStateName("Delhi (07)");
					invoice.setConsigneeStateName("Delhi (07)");
				}
			}

			if (master.getPlaceOfSupply() != null && !master.getPlaceOfSupply().isBlank()) {
				invoice.setBuyerStateName(master.getPlaceOfSupply());
				invoice.setConsigneeStateName(master.getPlaceOfSupply());
			}

			List<String> jobUuids = loadJobUuidsForSavedInvoice(con, master.getUuid());
			loadJobLinesForSavedInvoice(con, invoice, jobUuids, master.getUuid());
			loadAdditionalChargesForSavedInvoice(con, invoice, master.getUuid());
			invoice.setGrandTotal(master.getAmount());
			invoice.setTotalAfterTax(master.getTotalAfterTax());
			invoice.setRoundOff(master.getRoundOff());
			return invoice;
		});
	}

	private List<String> loadJobUuidsForSavedInvoice(Connection con, String invoiceUuid) {
		List<String> uuids = new ArrayList<>();
		try {
			String sql = """
					SELECT m.job_uuid FROM invoice_job_mapping m
					JOIN jobs j ON m.job_uuid = j.uuid
					WHERE m.invoice_uuid = ?
					  AND COALESCE(m.is_deleted, 0) = 0
					  AND j.status <> 'Cancelled'
					ORDER BY m.job_uuid
					""";
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setString(1, invoiceUuid);
				ResultSet rs = ps.executeQuery();
				while (rs.next()) {
					uuids.add(rs.getString(1));
				}
			}
			if (!uuids.isEmpty()) {
				return uuids;
			}
			String sql2 = """
					SELECT uuid FROM jobs
					WHERE invoice_uuid = ?
					  AND status <> 'Cancelled'
					  AND COALESCE(is_deleted, 0) = 0
					  ORDER BY created_at
					""";
			try (PreparedStatement ps = con.prepareStatement(sql2)) {
				ps.setString(1, invoiceUuid);
				ResultSet rs = ps.executeQuery();
				while (rs.next()) {
					uuids.add(rs.getString(1));
				}
			}
			return uuids;
		} catch (Exception e) {
			throw new RuntimeException("Failed loading job uuids for invoice " + invoiceUuid, e);
		}
	}

	/**
	 * Loads job lines for a saved invoice.
	 * Priority: stored snapshot in invoice_additional_charges (charge_type='JOB') > live job_items.
	 * Snapshot rows are written at invoice generation time, so they are immutable.
	 * Legacy invoices (no snapshot) fall back to live job_items data.
	 */
	private void loadJobLinesForSavedInvoice(Connection con, Invoice invoice, List<String> jobUuids, String invoiceUuid) {
		if (jobUuids == null || jobUuids.isEmpty()) {
			return;
		}

		// ── Step 1: pre-fetch snapshots from invoice_additional_charges (charge_type='JOB') ──
		// keyed by job_uuid (which is stored as the row uuid for JOB rows)
		Map<String, double[]> snapAmountByJob = new LinkedHashMap<>();   // jobUuid -> [qty, rate, gstRate, amount]
		Map<String, String[]> snapStrByJob   = new LinkedHashMap<>();    // jobUuid -> [unit, hsn, desc]
		if (invoiceUuid != null) {
			String snapSql = """
					SELECT uuid, description, amount, hsn_sac, gst_rate
					FROM invoice_additional_charges
					WHERE invoice_uuid = ? AND charge_type = 'JOB' AND COALESCE(is_deleted,0) = 0
					""";
			try (PreparedStatement pSnap = con.prepareStatement(snapSql)) {
				pSnap.setString(1, invoiceUuid);
				try (ResultSet rsSnap = pSnap.executeQuery()) {
					while (rsSnap.next()) {
						String jobUuid    = rsSnap.getString("uuid");
						String serialized = rsSnap.getString("description");
						double snapAmount = rsSnap.getDouble("amount");
						String snapHsn    = rsSnap.getString("hsn_sac");
						double snapGst    = rsSnap.getDouble("gst_rate");

						// Parse serialized: QTY:{q}|UNIT:{u}|RATE:{r}|HSN:{h}|GST:{g}|DESC:{d}
						long   qty  = 0;
						String unit = "PCS";
						double rate = 0;
						String desc = null;
						if (serialized != null && serialized.startsWith("QTY:")) {
							for (String part : serialized.split("\\|")) {
								try {
									if (part.startsWith("QTY:"))  qty  = (long) Double.parseDouble(part.substring(4));
									else if (part.startsWith("UNIT:")) unit = part.substring(5);
									else if (part.startsWith("RATE:")) rate = Double.parseDouble(part.substring(5));
									else if (part.startsWith("HSN:"))  { String h = part.substring(4); if (!h.isBlank()) snapHsn = h; }
									else if (part.startsWith("GST:"))  { double g = Double.parseDouble(part.substring(4)); if (g > 0) snapGst = g; }
									else if (part.startsWith("DESC:")) desc = part.substring(5);
								} catch (Exception ignored) {}
							}
						}
						snapAmountByJob.put(jobUuid, new double[]{qty, rate, snapGst, snapAmount});
						snapStrByJob.put(jobUuid,   new String[]{unit, snapHsn, desc});
					}
				}
			} catch (Exception e) {
				// Non-fatal: snapshot lookup failed, will fall back to live data
				snapAmountByJob.clear();
				snapStrByJob.clear();
			}
		}

		// ── Step 2: load job headers from jobs table ──
		String placeholders = String.join(",", jobUuids.stream().map(x -> "?").toList());
		String sql = """
				SELECT j.uuid AS job_uuid,
				       j.job_code,
				       j.job_date,
				       j.job_title,
				       j.job_type,
				       j.remarks,
				       j.description AS job_desc,
				       ji.description,
				       ji.amount,
				       ji.type,
				       ji.sort_order
				FROM jobs j
				JOIN job_items ji ON ji.job_uuid = j.uuid
				WHERE j.uuid IN (""" + placeholders + ") ORDER BY j.job_date, j.uuid, ji.sort_order";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			int index = 1;
			for (String uuid : jobUuids) {
				ps.setString(index++, uuid);
			}
			ResultSet rs = ps.executeQuery();
			Map<String, InvoiceJob> jobMap = new LinkedHashMap<>();
			while (rs.next()) {
				String jobUuid = rs.getString("job_uuid");
				InvoiceJob job = jobMap.get(jobUuid);
				if (job == null) {
					job = new InvoiceJob();
					job.setJobUuid(jobUuid);
					String jobNo = rs.getString("job_code");
					job.setJobNo(jobNo);
					LocalDate jd = parseJobDateFlexible(rs.getString("job_date"));
					job.setJobDate(jd != null ? jd : invoice.getInvoiceDate());

					double[] nums = snapAmountByJob.get(jobUuid);
					if (nums != null) {
						// ✅ Snapshot found – use values stored at invoice generation time
						String[] strs = snapStrByJob.get(jobUuid);
						long   snapQty  = (long) nums[0];
						double snapRate = nums[1];
						double snapGst  = nums[2];
						double snapAmt  = nums[3];
						String snapUnit = strs[0];
						String snapHsn  = strs[1];
						String snapDesc = strs[2];

						job.setJobName(snapDesc != null && !snapDesc.isBlank() ? snapDesc : jobDisplayTitle(rs, jobUuid, jobNo));
						job.setQuantity(snapQty);
						job.setUnit(snapUnit != null ? snapUnit : "PCS");
						job.setRatePerUnit(snapRate);
						job.setHsnSac(snapHsn != null && !snapHsn.isBlank() ? snapHsn : "—");
						job.setGstRate(snapGst > 0 ? snapGst : 0.18);

						// Use the stored amount as the single authoritative line
						InvoiceLine snapLine = new InvoiceLine();
						snapLine.setDescription(job.getJobName());
						snapLine.setAmount(snapAmt);
						snapLine.setType("PRINTING");
						snapLine.setSortOrder(1);
						job.addLine(snapLine);

						jobMap.put(jobUuid, job);
						invoice.getJobs().add(job);
						continue; // skip live job_items lines for this job
					}

					// ⚠️ No snapshot – legacy invoice: fall back to live job_items data
					job.setJobName(jobDisplayTitle(rs, jobUuid, jobNo));
					String jobType = rs.getString("job_type");
					String remarks = rs.getString("remarks");

					long qty = 0; String unit = ""; double ratePerUnit = 0.0; String hsnSac = "—"; double gstRate = 0.18;
					if ("CHARGE".equalsIgnoreCase(jobType)) {
						if (remarks != null && remarks.startsWith("QTY:")) {
							try {
								for (String part : remarks.split("\\|")) {
									if (part.startsWith("QTY:"))       qty          = (long) Double.parseDouble(part.substring(4));
									else if (part.startsWith("UNIT:"))  unit         = part.substring(5);
									else if (part.startsWith("RATE:"))  ratePerUnit  = Double.parseDouble(part.substring(5));
									else if (part.startsWith("GST:"))   gstRate      = Double.parseDouble(part.substring(4));
									else if (part.startsWith("HSN:"))   hsnSac       = part.substring(4);
								}
							} catch (Exception ignored) {}
						} else {
							boolean isCustomItem = jobNo == null || jobNo.isBlank();
							qty = isCustomItem ? 0 : 1; unit = isCustomItem ? "" : "PCS";
						}
					} else {
						qty = new service.JobService().getTotalPrintingQtyForJobUuids(List.of(jobUuid));
						unit = "PCS";
						try {
							List<model.JobItem> items = new service.JobItemService().getJobItems(jobUuid);
							double jobTaxable = new service.JobService().getSumJobItemsAmountForJobUuids(List.of(jobUuid));
							ratePerUnit = qty > 0 ? (jobTaxable / qty) : jobTaxable;
							service.HsnSacService hsnSacService = new service.HsnSacService();
							for (model.JobItem ji : items) {
								model.HsnSacInfo info = hsnSacService.lookup(ji);
								if (info != null && info.getHsnSac() != null && !info.getHsnSac().isBlank()) {
									hsnSac = info.getHsnSac();
									if (info.getGstRate() > 0) gstRate = info.getGstRate();
									break;
								}
							}
						} catch (Exception ignored) {}
					}
					job.setQuantity(qty); job.setUnit(unit); job.setRatePerUnit(ratePerUnit);
					job.setHsnSac(hsnSac); job.setGstRate(gstRate);

					jobMap.put(jobUuid, job);
					invoice.getJobs().add(job);
				}
				// For snapshot jobs we already added the line above and continued,
				// so this block only runs for legacy (no-snapshot) jobs.
				if (!snapAmountByJob.containsKey(jobUuid)) {
					InvoiceLine line = new InvoiceLine();
					line.setDescription(rs.getString("description"));
					line.setAmount(rs.getDouble("amount"));
					line.setType(rs.getString("type"));
					line.setSortOrder(rs.getInt("sort_order"));
					job.addLine(line);
				}
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
                c.uuid AS client_id,
                c.client_name,
                c.business_name
            FROM jobs j
            JOIN clients c ON c.uuid = j.client_uuid
            WHERE DATE(j.job_date) BETWEEN ? AND ?
            AND LOWER(TRIM(REPLACE(COALESCE(j.status,''), '_', ' '))) = 'completed'
            AND j.invoice_uuid IS NULL
            ORDER BY c.business_name, c.client_name, c.uuid
        """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, fromDate.toString());
            ps.setString(2, toDate.toString());

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                String clientId = rs.getString("client_id");
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

                invoice.setInvoiceType("MONTHLY_PROFORMA");
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


	private void loadJobsIntoInvoice(Connection con, Invoice invoice, String clientId, LocalDate fromDate,
			LocalDate toDate) {

		String sql = """
				SELECT j.uuid AS job_uuid,
				j.job_code,
				j.job_date,
				j.job_title,
				j.description AS job_desc,
				ji.description,
				ji.amount,
				ji.type,
				ji.sort_order
				FROM jobs j
				JOIN job_items ji ON ji.job_uuid = j.uuid
				WHERE j.client_uuid = ?
				AND DATE(j.job_date) BETWEEN ? AND ?
				AND LOWER(TRIM(REPLACE(COALESCE(j.status,''), '_', ' '))) = 'completed'
				AND j.invoice_uuid IS NULL
				ORDER BY j.job_date, j.uuid, ji.sort_order
				""";

		try (PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setString(1, clientId);
			ps.setString(2, fromDate.toString());
			ps.setString(3, toDate.toString());

			ResultSet rs = ps.executeQuery();

			Map<String, InvoiceJob> jobMap = new LinkedHashMap<>();

			while (rs.next()) {

				String jobUuid = rs.getString("job_uuid");

				InvoiceJob job = jobMap.get(jobUuid);

				if (job == null) {
					job = new InvoiceJob();
					job.setJobUuid(jobUuid);
					job.setJobNo(rs.getString("job_code"));
					String jobDesc = rs.getString("job_desc");
					if (jobDesc != null && !jobDesc.isBlank()) {
						job.setJobName(jobDesc.trim());
					} else {
						job.setJobName(rs.getString("job_title"));
					}
					LocalDate jd = parseJobDateFlexible(rs.getString("job_date"));
					job.setJobDate(jd != null ? jd : invoice.getInvoiceDate());

					jobMap.put(jobUuid, job);
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

	private static String jobDisplayTitle(ResultSet rs, String jobUuid, String jobNo) throws SQLException {
		try {
			String jobDesc = rs.getString("job_desc");
			if (jobDesc != null && !jobDesc.isBlank()) {
				return jobDesc.trim();
			}
		} catch (SQLException ignored) {}
		String t = rs.getString("job_title");
		if (t != null && !t.isBlank()) {
			return t.trim();
		}
		if (jobNo != null && !jobNo.isBlank()) {
			return jobNo.trim();
		}
		return jobUuid != null && jobUuid.length() > 8 ? "Job " + jobUuid.substring(0, 8) : "Job";
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

	private void loadAdditionalChargesForSavedInvoice(Connection con, Invoice invoice, String invoiceUuid) {
		// Exclude 'JOB' rows — those are already handled by loadJobLinesForSavedInvoice via snapshot.
		String sql = """
				SELECT uuid, charge_type, description, amount, hsn_sac, gst_rate, taxable_flag
				FROM invoice_additional_charges
				WHERE invoice_uuid = ? AND charge_type != 'JOB' AND COALESCE(is_deleted, 0) = 0
				ORDER BY created_at
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, invoiceUuid);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					InvoiceJob job = new InvoiceJob();
					String uuid = rs.getString("uuid");
					job.setJobUuid(uuid);
					job.setJobNo(""); // No job number for custom items

					String chargeType = rs.getString("charge_type");
					String rawDesc = rs.getString("description");
					double amount = rs.getDouble("amount");
					String hsnSac = rs.getString("hsn_sac");
					double gstRate = rs.getDouble("gst_rate");

					long qty = 0;
					String unit = "";
					double ratePerUnit = 0.0;
					String printedDesc = rawDesc;

					if (rawDesc != null && rawDesc.startsWith("QTY:")) {
						try {
							String[] parts = rawDesc.split("\\|");
							for (String part : parts) {
								if (part.startsWith("QTY:")) {
									qty = (long) Double.parseDouble(part.substring(4));
								} else if (part.startsWith("UNIT:")) {
									unit = part.substring(5);
								} else if (part.startsWith("RATE:")) {
									ratePerUnit = Double.parseDouble(part.substring(5));
								} else if (part.startsWith("DESC:")) {
									printedDesc = part.substring(5);
								}
							}
						} catch (Exception e) {
							// fallback
						}
					}

					// Format description to match how it is printed
					String formattedDesc = printedDesc;
					if ("CHARGE".equalsIgnoreCase(chargeType)) {
						String pctStr = String.format("%.0f%%", gstRate * 100.0);
						if (!formattedDesc.toUpperCase().contains(pctStr) && !formattedDesc.toUpperCase().contains("-")) {
							formattedDesc = formattedDesc + " - " + pctStr;
						}
					}

					job.setJobName(formattedDesc);
					job.setQuantity(qty);
					job.setUnit(unit);
					job.setRatePerUnit(ratePerUnit);
					job.setHsnSac(hsnSac != null ? hsnSac : "—");
					job.setGstRate(gstRate);

					// Add a dummy invoice line so that getJobTotal() works correctly (sum of lines amount)
					InvoiceLine line = new InvoiceLine();
					line.setDescription(formattedDesc);
					line.setAmount(amount);
					line.setType("OTHER");
					line.setSortOrder(1);
					job.addLine(line);

					invoice.getJobs().add(job);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed loading additional charges for invoice " + invoiceUuid, e);
		}
	}

}
