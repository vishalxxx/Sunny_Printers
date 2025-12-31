package service;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
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

	public void generateExcel(Invoice invoice) {

		try (Workbook wb = new XSSFWorkbook()) {

			Sheet sheet = wb.createSheet("Invoice");
			setupColumns(sheet);

			CellStyle border = borderStyle(wb);
			CellStyle bold = boldStyle(wb);
			CellStyle amount = amountStyle(wb);
			CellStyle boldAmount = boldAmountStyle(wb);
			CellStyle borderUPDown = borderStyleUpDown(wb);

			int rowIndex = drawHeader(wb, sheet, invoice, bold, border, borderUPDown);
			int rowsInPage = 0;
			InvoiceExcelStyles is = new InvoiceExcelStyles(wb);

			// Columns Description ---------- #, DATE, JOB DESCRIPTION, AMOUNT
			Row descRow = sheet.createRow(rowIndex++);
			descRow.setRowStyle(border);

			mergeAndStyle(sheet, descRow.getRowNum(), descRow.getRowNum(), 0, 0, "#", is.descText);

			mergeAndStyle(sheet, descRow.getRowNum(), descRow.getRowNum(), 1, 1, "DATE", is.descText);

			mergeAndStyle(sheet, descRow.getRowNum(), descRow.getRowNum(), 2, 2, "Description", is.descText);

			mergeAndStyle(sheet, descRow.getRowNum(), descRow.getRowNum(), 3, 3, "Amount", is.descText);

			/* ================= BODY ================= */

			for (InvoiceJob job : invoice.getJobs()) {

				// Check page space for job header
				if (rowsInPage >= MAX_ROWS_PER_PAGE - 4) {
					sheet.setRowBreak(rowIndex - 1);
					rowIndex++;
					rowsInPage = 0;
				}

				// Job header
				Row jobRow = sheet.createRow(rowIndex++);
				Cell jobCell = jobRow.createCell(0);
				jobCell.setCellValue("(" + job.getJobNo() + ") - " + job.getJobName());
				jobCell.setCellStyle(bold);

				// merge description + amount columns
				sheet.addMergedRegion(
						new org.apache.poi.ss.util.CellRangeAddress(jobRow.getRowNum(), jobRow.getRowNum(), 0, 3));

				applyRowBorder(jobRow, border);
				rowsInPage++;

				for (InvoiceLine line : job.getLines()) {

					if (rowsInPage >= MAX_ROWS_PER_PAGE) {
						sheet.setRowBreak(rowIndex - 1);
						rowIndex++;
						rowsInPage = 0;
					}

					Row r = sheet.createRow(rowIndex++);

					Cell desc = r.createCell(0);
					desc.setCellValue(line.getDescription());
					desc.setCellStyle(border);

					sheet.addMergedRegion(
							new org.apache.poi.ss.util.CellRangeAddress(r.getRowNum(), r.getRowNum(), 0, 2));

					Cell amt = r.createCell(3);
					amt.setCellValue(line.getAmount());
					amt.setCellStyle(amount);

					applyRowBorder(r, border);
					rowsInPage++;
				}
			}

			/* ================= FOOTER ================= */

			if (rowsInPage > MAX_ROWS_PER_PAGE - FOOTER_ROWS) {
				sheet.setRowBreak(rowIndex - 1);
				rowIndex++;
			}

			Row totalRow = sheet.createRow(rowIndex++);
			Cell label = totalRow.createCell(2);
			label.setCellValue("GRAND TOTAL");
			label.setCellStyle(bold);

			Cell value = totalRow.createCell(3);
			value.setCellValue(invoice.getGrandTotal());
			value.setCellStyle(boldAmount);

			applyRowBorder(totalRow, border);

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

	private int drawHeader(Workbook wb, Sheet sheet, Invoice invoice, CellStyle bold, CellStyle border,
			CellStyle borderUPDown) {

		InvoiceExcelStyles is = new InvoiceExcelStyles(wb);
		// Row 1
		mergeAndStyle(sheet, 1, 1, 0, 3, invoice.getCompanyName(), is.headerTitle);

		// Row 2
		mergeAndStyle(sheet, 2, 2, 0, 3, invoice.getCompanyAddress(), is.headerText);

		// Row 3
		mergeAndStyle(sheet, 3, 3, 0, 3, "Email: " + invoice.getEmail() + "| Ph: " + invoice.getCompanyContact(),
				is.headerText);

		// Row 5
		CellRangeAddress datemerge = mergeAndStyle(sheet, 5, 5, 0, 1, "Date: " + invoice.getInvoiceDate(), is.text);
		ExcelRegionUtil.applyBorder(sheet, datemerge, true, true, true, true);
		CellRangeAddress clientmerge = mergeAndStyle(sheet, 5, 5, 2, 3, "Client: " + invoice.getClientName(),
				is.clientText);
		ExcelRegionUtil.applyBorder(sheet, clientmerge, true, true, true, true);
		// Row 6
		CellRangeAddress performa = mergeAndStyle(sheet, 6, 6, 0, 3, "Performa No: " + invoice.getInvoiceNo(), is.text);
		ExcelRegionUtil.applyBorder(sheet, performa, true, true, true, true);

		return BODY_START_ROW;
	}

	/* ================= STYLES ================= */

	private CellStyle borderStyle(Workbook wb) {
		CellStyle s = wb.createCellStyle();
		s.setBorderTop(BorderStyle.THIN);
		s.setBorderBottom(BorderStyle.THIN);
		s.setBorderLeft(BorderStyle.THIN);
		s.setBorderRight(BorderStyle.THIN);
		s.setVerticalAlignment(VerticalAlignment.TOP);
		s.setWrapText(true);
		return s;
	}

	private CellStyle borderStyleUpDown(Workbook wb) {
		CellStyle s = wb.createCellStyle();
		s.setBorderTop(BorderStyle.THIN);
		s.setBorderBottom(BorderStyle.THIN);
		s.setVerticalAlignment(VerticalAlignment.TOP);
		s.setWrapText(true);
		return s;
	}

	private CellStyle boldStyle(Workbook wb) {
		Font f = wb.createFont();
		f.setBold(true);
		CellStyle s = wb.createCellStyle();
		s.setFont(f);
		return s;
	}

	private CellStyle amountStyle(Workbook wb) {
		CellStyle s = borderStyle(wb);
		s.setAlignment(HorizontalAlignment.RIGHT);
		DataFormat df = wb.createDataFormat();

		s.setDataFormat(df.getFormat("#,##0.00"));
		return s;
	}

	private CellStyle boldAmountStyle(Workbook wb) {
		Font f = wb.createFont();
		f.setBold(true);
		CellStyle s = amountStyle(wb);
		s.setFont(f);
		return s;
	}

	private void setupColumns(Sheet sheet) {
		sheet.setColumnWidth(0, 1800); // #
		sheet.setColumnWidth(1, 3200); // Date
		sheet.setColumnWidth(2, 12000); // Description
		sheet.setColumnWidth(3, 4500); // Amount (important)
	}

	private void applyRowBorder(Row r, CellStyle style) {
		for (int i = 0; i <= 3; i++) {
			Cell c = r.getCell(i);
			if (c == null)
				c = r.createCell(i);
			c.setCellStyle(style);
		}
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

}
