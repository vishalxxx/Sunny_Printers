package service;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import model.Invoice;
import model.InvoiceJob;
import model.InvoiceLine;
import utils.ExcelRegionUtil;
import utils.InvoiceExcelStyles;

public class InvoiceGenerationService {

	private static final int BODY_START_ROW = 8;
	private static final int MAX_ROWS_PER_PAGE = 25;
	private static final int FOOTER_ROWS = 3;

	@SuppressWarnings("deprecation")
	public void generateExcel(Invoice invoice) {

		try (Workbook wb = new XSSFWorkbook()) {

			Sheet sheet = wb.createSheet("Invoice");
			setupColumns(sheet);

			int rowIndex = drawHeader(wb, sheet, invoice);
			int rowsInPage = 0;
			InvoiceExcelStyles is = new InvoiceExcelStyles(wb);

			// Columns Description ---------- #, DATE, JOB DESCRIPTION, AMOUNT
			Row descRow = sheet.createRow(rowIndex++);

			mergeAndStyle(sheet, descRow.getRowNum(), descRow.getRowNum(), 0, 0, "#", is.descText);

			mergeAndStyle(sheet, descRow.getRowNum(), descRow.getRowNum(), 1, 1, "DATE", is.descText);

			mergeAndStyle(sheet, descRow.getRowNum(), descRow.getRowNum(), 2, 2, "Description", is.descText);

			mergeAndStyle(sheet, descRow.getRowNum(), descRow.getRowNum(), 3, 3, "Amount", is.descText);

			/* ================= BODY ================= */
			int serial = 0;
			for (InvoiceJob job : invoice.getJobs()) {

				// Check page space for job header
				if (rowsInPage >= MAX_ROWS_PER_PAGE - 4) {
					rowIndex++;
					rowsInPage = 0;
				}

				// Job header
				Row jobRow = sheet.createRow(rowIndex++);
				Cell serialCell = jobRow.createCell(0);
				serialCell.setCellValue(serial += 1);
				serialCell.setCellStyle(is.serial);

				Cell dateCell = jobRow.createCell(1);
				dateCell.setCellValue(java.sql.Date.valueOf(job.getJobDate()));
				dateCell.setCellStyle(is.Date);

				Cell jobCell = jobRow.createCell(2);
				jobCell.setCellValue("(" + job.getJobNo() + ") - " + job.getJobName());
				jobCell.setCellStyle(is.jobDesc);

				Cell extraCell = jobRow.createCell(3);
				extraCell.setCellStyle(is.serial);

				rowsInPage++;

				for (InvoiceLine line : job.getLines()) {
					if (rowsInPage >= MAX_ROWS_PER_PAGE) {
						rowIndex++;
						rowsInPage = 0;
					}

					Row r = sheet.createRow(rowIndex++);

					Cell desc = r.createCell(2);
					desc.setCellValue(line.getDescription());
					desc.setCellStyle(is.jobDesc);

					Cell amt = r.createCell(3);
					amt.setCellValue(line.getAmount());
					amt.setCellStyle(is.amount);
					rowsInPage++;

				}
				rowIndex++;

			}

			/* ================= FOOTER ================= */

			if (rowsInPage > MAX_ROWS_PER_PAGE - FOOTER_ROWS) {
				rowIndex++;
			}

			Row totalRow = sheet.createRow(rowIndex++);

			Cell value = totalRow.createCell(3);
			value.setCellValue(invoice.getGrandTotal());
			value.setCellStyle(is.totalAmount);
			mergeAndStyle(sheet, totalRow.getRowNum(), totalRow.getRowNum(), 0, 2, "GRAND TOTAL", is.totalLabel);
			
			outlineInvoice(sheet, 0, totalRow.getRowNum());

			// ================= PRINT FIX (FINAL & CORRECT) =================

			// Margins (inches)
			sheet.setMargin(PageMargin.LEFT, 0.3);
			sheet.setMargin(PageMargin.RIGHT, 0.3);
			sheet.setMargin(PageMargin.TOP, 0.5);
			sheet.setMargin(PageMargin.BOTTOM, 0.5);
			sheet.setMargin(PageMargin.HEADER, 0.3);
			sheet.setMargin(PageMargin.FOOTER, 0.3);

			// Print setup
			PrintSetup ps = sheet.getPrintSetup();
			ps.setPaperSize(PrintSetup.A4_PAPERSIZE);
			ps.setLandscape(false);

			// LOCK WIDTH ONLY
			ps.setFitWidth((short) 1);

			// 🚫 DO NOT SET FIT HEIGHT AT ALL
			// ps.setFitHeight(...); <-- NEVER

			// Let rows flow naturally
			sheet.setAutobreaks(true);

			// Disable centering artifacts
			sheet.setHorizontallyCenter(false);
			sheet.setVerticallyCenter(false);

			// Gridlines OFF
			sheet.setDisplayGridlines(false);
			sheet.setPrintGridlines(false);

			// Print ONLY A–D
			int lastRow = sheet.getLastRowNum();
			wb.setPrintArea(wb.getSheetIndex(sheet), 0, 3, // A–D only
					0, lastRow);

			/* ================= SAVE ================= */

			File file = new File("output/invoice.xlsx");
			file.getParentFile().mkdirs();

			try (FileOutputStream fos = new FileOutputStream(file)) {
				wb.write(fos);
			}

		} catch (Exception e) {
			throw new RuntimeException("Invoice generation failed", e);
		}

	}

	/* ================= HEADER ================= */

	private int drawHeader(Workbook wb, Sheet sheet, Invoice invoice) {

		InvoiceExcelStyles is = new InvoiceExcelStyles(wb);
		// Row 1
		mergeAndStyle(sheet, 1, 1, 0, 3, invoice.getCompanyName(), is.headerTitle);

		// Row 2
		mergeAndStyle(sheet, 2, 2, 0, 3, invoice.getCompanyAddress(), is.headerText);

		// Row 3
		mergeAndStyle(sheet, 3, 3, 0, 3, "Email: " + invoice.getEmail() + "| Ph: " + invoice.getCompanyContact(),
				is.headerText);

		// Row 5
		mergeAndStyle(sheet, 5, 5, 0, 1, "Date: " + invoice.getInvoiceDate(), is.Date);
		mergeAndStyle(sheet, 5, 5, 2, 3, "Client: " + invoice.getClientName(), is.clientText);
		// Row 6
		mergeAndStyle(sheet, 6, 6, 0, 3, "Performa No: " + invoice.getInvoiceNo(), is.headerDate);

		return BODY_START_ROW;
	}

	private void setupColumns(Sheet sheet) {
		sheet.setColumnWidth(0, 1500); // #
		sheet.setColumnWidth(1, 3000); // Date
		sheet.setColumnWidth(2, 11000); // Description
		sheet.setColumnWidth(3, 4000); // Amount
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
			if (value instanceof Number) {
				cell.setCellValue(((Number) value).doubleValue());
			} else {
				cell.setCellValue(value.toString());
			}
		}
		return region;
	}
	
	private void outlineInvoice(Sheet sheet, int startRow, int endRow) {
	    CellRangeAddress invoiceBox =
	        new CellRangeAddress(startRow, endRow, 0, 3); // A–D only

	    ExcelRegionUtil.applyBorder(
	        sheet,
	        invoiceBox,
	        true,  // top
	        true,  // bottom
	        true,  // left
	        true   // right
	    );
	}

	
	

}
