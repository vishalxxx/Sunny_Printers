package service;

import java.io.File;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import model.Invoice;
import model.InvoiceJob;
import model.InvoiceLine;
import utils.ExcelRegionUtil;
import utils.InvoiceExcelStyles;

public class InvoiceGenerationService {

	private static final int BODY_START_ROW = 8;
//    private static final int MAX_ROWS_PER_PAGE = 22;
//    private static final int FOOTER_ROWS = 3;

	/*
	 * ========================================================= PUBLIC API
	 * =========================================================
	 */

	// Existing single-invoice generator (UNCHANGED usage)
	public File generateSingleInvoice(Invoice invoice) {
		try (Workbook wb = new XSSFWorkbook()) {

			Sheet sheet = wb.createSheet("Invoice");
			generateInvoiceSheet(wb, sheet, invoice);

			// ✅ SAVE using storage class
			return InvoiceStorageService.saveInvoice(wb, invoice);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// ✅ NEW: Monthly client-wise workbook
	public File generateMonthlyClientWorkbook(YearMonth month, Map<String, Invoice> invoiceMap) {
		try (Workbook wb = new XSSFWorkbook()) {

			for (Map.Entry<String, Invoice> entry : invoiceMap.entrySet()) {

				String sheetName = entry.getKey(); // business name
				Invoice invoice = entry.getValue();

				// Excel sheet name max length 31
				sheetName = safeSheetName(sheetName);

				Sheet sheet = wb.createSheet(sheetName);
				generateInvoiceSheet(wb, sheet, invoice);
			}

			// ✅ Save into base/year/month folder
			// Use any invoice just for date folder
			Invoice dummy = new Invoice();
			dummy.setCompanyName("SUNNY_PRINTERS");
			dummy.setClientName("MONTHLY_BULK");
			dummy.setInvoiceNo("BULK-" + month);
			dummy.setInvoiceDate(month.atEndOfMonth());

			return InvoiceStorageService.saveInvoice(wb, dummy);

		} catch (Exception e) {
			throw new RuntimeException("Monthly bulk invoice generation failed", e);
		}
	}

	private String safeSheetName(String name) {
		if (name == null || name.isBlank())
			return "UNKNOWN";
		name = name.replaceAll("[\\\\/*?:\\[\\]]", "_");
		if (name.length() > 31)
			name = name.substring(0, 31);
		return name;
	}

	/*
	 * ========================================================= CORE SHEET
	 * GENERATOR (REUSED) =========================================================
	 */

	private void generateInvoiceSheet(Workbook wb, Sheet sheet, Invoice invoice) {

		setupColumns(sheet);

		int rowIndex = drawHeader(wb, sheet, invoice);

		InvoiceExcelStyles is = new InvoiceExcelStyles(wb);

		/*
		 * ========================= COLUMN HEADER =========================
		 */
		Row header = sheet.createRow(rowIndex++);

		mergeAndStyle(sheet, header.getRowNum(), header.getRowNum(), 0, 0, "#", is.descText);
		mergeAndStyle(sheet, header.getRowNum(), header.getRowNum(), 1, 1, "DATE", is.descText);
		mergeAndStyle(sheet, header.getRowNum(), header.getRowNum(), 2, 2, "Description", is.descText);
		mergeAndStyle(sheet, header.getRowNum(), header.getRowNum(), 3, 3, "Amount", is.descText);

		int serial = 0;

		/*
		 * ========================= JOB LOOP (CONTINUOUS) =========================
		 */
		for (InvoiceJob job : invoice.getJobs()) {

			/* ---- Job Header ---- */
			Row jobRow = sheet.createRow(rowIndex++);

			jobRow.createCell(0).setCellValue(++serial);
			jobRow.getCell(0).setCellStyle(is.serial);

			jobRow.createCell(1).setCellValue(java.sql.Date.valueOf(job.getJobDate()));
			jobRow.getCell(1).setCellStyle(is.Date);

			jobRow.createCell(2).setCellValue("(" + job.getJobNo() + ") - " + job.getJobName());
			jobRow.getCell(2).setCellStyle(is.jobDesc);

			jobRow.createCell(3).setCellStyle(is.serial);

			/* ---- Job Lines ---- */
			for (InvoiceLine line : job.getLines()) {

				Row r = sheet.createRow(rowIndex++);

				r.createCell(2).setCellValue(line.getDescription());
				r.getCell(2).setCellStyle(is.jobDesc);

				r.createCell(3).setCellValue(line.getAmount());
				r.getCell(3).setCellStyle(is.amount);
			}

			/* ---- EXACTLY ONE spacer row after each job ---- */
			sheet.createRow(rowIndex++);
		}

		/*
		 * ========================= GRAND TOTAL =========================
		 */
		Row totalRow = sheet.createRow(rowIndex++);

		totalRow.createCell(3).setCellValue(invoice.getGrandTotal());
		totalRow.getCell(3).setCellStyle(is.totalAmount);

		mergeAndStyle(sheet, totalRow.getRowNum(), totalRow.getRowNum(), 0, 2, "GRAND TOTAL", is.totalLabel);

		outlineInvoice(sheet, 0, totalRow.getRowNum());
		setupPrint(sheet, wb);
	}

	/*
	 * ========================================================= HELPERS
	 * =========================================================
	 */

	private void setupColumns(Sheet sheet) {
		sheet.setColumnWidth(0, 1500);
		sheet.setColumnWidth(1, 3000);
		sheet.setColumnWidth(2, 14000);
		sheet.setColumnWidth(3, 4000);
		PrintSetup ps = sheet.getPrintSetup();

		// You can keep A4 as default (client can still print on Letter)
		ps.setPaperSize(PrintSetup.A4_PAPERSIZE);

		// ✅ MOST IMPORTANT
		sheet.setFitToPage(true);
		ps.setFitWidth((short) 1); // ✅ Always 1 page width
		ps.setFitHeight((short) 0); // unlimited pages height

		// ✅ Recommended
		ps.setLandscape(false); // portrait

		sheet.setHorizontallyCenter(true);
	}

	private void setupPrint(Sheet sheet, Workbook wb) {
		sheet.setMargin(PageMargin.LEFT, 0.3);
		sheet.setMargin(PageMargin.TOP, 0.5);
		sheet.setMargin(PageMargin.BOTTOM, 0.5);

		PrintSetup ps = sheet.getPrintSetup();
		ps.setPaperSize(PrintSetup.A4_PAPERSIZE);
		ps.setFitWidth((short) 1);

		wb.setPrintArea(wb.getSheetIndex(sheet), 0, 3, 0, sheet.getLastRowNum());
	}

	

	private int drawHeader(Workbook wb, Sheet sheet, Invoice invoice) {
		InvoiceExcelStyles is = new InvoiceExcelStyles(wb);

		mergeAndStyle(sheet, 1, 1, 0, 3, invoice.getCompanyName(), is.headerTitle);
		mergeAndStyle(sheet, 2, 2, 0, 3, invoice.getCompanyAddress(), is.headerText);
		mergeAndStyle(sheet, 3, 3, 0, 3, "Email: " + invoice.getEmail() + " | Ph: " + invoice.getCompanyContact(),
				is.headerText);

		mergeAndStyle(sheet, 5, 5, 0, 1, "Date: " + invoice.getInvoiceDate(), is.Date);

		mergeAndStyle(sheet, 5, 5, 2, 3, "Client: " + invoice.getClientName(), is.clientText);

		mergeAndStyle(sheet, 6, 6, 0, 3, "Performa No: " + invoice.getInvoiceNo(), is.headerDate);

		return BODY_START_ROW;
	}

	public static CellRangeAddress mergeAndStyle(Sheet sheet, int startRow, int endRow, int startCol, int endCol,
			Object value, CellStyle style) {

		CellRangeAddress region = null;

		if (startRow != endRow || startCol != endCol) {
			region = new CellRangeAddress(startRow, endRow, startCol, endCol);
			sheet.addMergedRegion(region);
		}

		for (int r = startRow; r <= endRow; r++) {
			Row row = sheet.getRow(r);
			if (row == null)
				row = sheet.createRow(r);

			for (int c = startCol; c <= endCol; c++) {
				Cell cell = row.getCell(c);
				if (cell == null)
					cell = row.createCell(c);
				cell.setCellStyle(style);
			}
		}

		if (value != null) {
			Cell cell = sheet.getRow(startRow).getCell(startCol);
			if (value instanceof Number)
				cell.setCellValue(((Number) value).doubleValue());
			else
				cell.setCellValue(value.toString());
		}
		return region;
	}

	private void outlineInvoice(Sheet sheet, int startRow, int endRow) {
		ExcelRegionUtil.applyBorder(sheet, new CellRangeAddress(startRow, endRow, 0, 3), true, true, true, true);
	}
}
