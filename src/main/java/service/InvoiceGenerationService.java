package service;

import java.io.File;
import java.io.FileOutputStream;
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
    private static final int MAX_ROWS_PER_PAGE = 25;
    private static final int FOOTER_ROWS = 3;

    /* =========================================================
       PUBLIC API
       ========================================================= */

    // Existing single-invoice generator (UNCHANGED usage)
    public void generateSingleInvoice(Invoice invoice) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Invoice");
            generateInvoiceSheet(wb, sheet, invoice);
            saveWorkbook(wb, "output/invoice.xlsx");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ✅ NEW: Monthly client-wise workbook
    public void generateMonthlyClientWorkbook(
            YearMonth month,
            Map<String, List<InvoiceJob>> clientJobsMap
    ) {

        try (Workbook wb = new XSSFWorkbook()) {

            for (Map.Entry<String, List<InvoiceJob>> entry : clientJobsMap.entrySet()) {

                String clientName = entry.getKey();
                List<InvoiceJob> jobs = entry.getValue();

                Invoice invoice = new Invoice();
                invoice.setClientName(clientName);
                invoice.setInvoiceDate(month.atEndOfMonth());

                // ✅ add jobs correctly (NO setJobs)
                for (InvoiceJob job : jobs) {
                    if (YearMonth.from(job.getJobDate()).equals(month)) {
                        invoice.addJob(job);
                    }
                }

                if (invoice.getJobs().isEmpty()) continue;

                Sheet sheet = wb.createSheet(clientName);
                generateInvoiceSheet(wb, sheet, invoice);
            }

            saveWorkbook(
                    wb,
                    "output/invoices-" + month + ".xlsx"
            );

        } catch (Exception e) {
            throw new RuntimeException("Monthly invoice generation failed", e);
        }
    }

    /* =========================================================
       CORE SHEET GENERATOR (REUSED)
       ========================================================= */

    private void generateInvoiceSheet(
            Workbook wb,
            Sheet sheet,
            Invoice invoice
    ) {

        setupColumns(sheet);

        int rowIndex = drawHeader(wb, sheet, invoice);
        int rowsInPage = 0;

        InvoiceExcelStyles is = new InvoiceExcelStyles(wb);

        // Column header
        Row header = sheet.createRow(rowIndex++);
        rowsInPage++;

        mergeAndStyle(sheet, header.getRowNum(), header.getRowNum(), 0, 0, "#", is.descText);
        mergeAndStyle(sheet, header.getRowNum(), header.getRowNum(), 1, 1, "DATE", is.descText);
        mergeAndStyle(sheet, header.getRowNum(), header.getRowNum(), 2, 2, "Description", is.descText);
        mergeAndStyle(sheet, header.getRowNum(), header.getRowNum(), 3, 3, "Amount", is.descText);

        int serial = 0;

        for (InvoiceJob job : invoice.getJobs()) {

            int jobRowsNeeded =
                    1 + job.getLines().size() + 1;

            if (rowsInPage + jobRowsNeeded + FOOTER_ROWS > MAX_ROWS_PER_PAGE) {
                rowIndex++;
                rowsInPage = 0;
            }

            Row jobRow = sheet.createRow(rowIndex++);
            rowsInPage++;

            jobRow.createCell(0).setCellValue(++serial);
            jobRow.getCell(0).setCellStyle(is.serial);

            jobRow.createCell(1).setCellValue(java.sql.Date.valueOf(job.getJobDate()));
            jobRow.getCell(1).setCellStyle(is.Date);

            jobRow.createCell(2).setCellValue("(" + job.getJobNo() + ") - " + job.getJobName());
            jobRow.getCell(2).setCellStyle(is.jobDesc);

            jobRow.createCell(3).setCellStyle(is.serial);

            for (InvoiceLine line : job.getLines()) {

                if (rowsInPage + FOOTER_ROWS >= MAX_ROWS_PER_PAGE) {
                    rowIndex++;
                    rowsInPage = 0;
                }

                Row r = sheet.createRow(rowIndex++);
                rowsInPage++;

                r.createCell(2).setCellValue(line.getDescription());
                r.getCell(2).setCellStyle(is.jobDesc);

                r.createCell(3).setCellValue(line.getAmount());
                r.getCell(3).setCellStyle(is.amount);
            }

            // spacer (only if safe)
            if (rowsInPage < MAX_ROWS_PER_PAGE - 1) {
                rowIndex++;
                rowsInPage++;
            }
        }

        if (rowsInPage + FOOTER_ROWS > MAX_ROWS_PER_PAGE) {
            rowIndex++;
        }

        Row totalRow = sheet.createRow(rowIndex++);
        totalRow.createCell(3).setCellValue(invoice.getGrandTotal());
        totalRow.getCell(3).setCellStyle(is.totalAmount);

        mergeAndStyle(sheet, totalRow.getRowNum(), totalRow.getRowNum(),
                0, 2, "GRAND TOTAL", is.totalLabel);

        outlineInvoice(sheet, 0, totalRow.getRowNum());
        setupPrint(sheet, wb);
    }

    /* =========================================================
       HELPERS
       ========================================================= */

    private void setupColumns(Sheet sheet) {
        sheet.setColumnWidth(0, 1500);
        sheet.setColumnWidth(1, 3000);
        sheet.setColumnWidth(2, 11000);
        sheet.setColumnWidth(3, 4000);
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

    private void saveWorkbook(Workbook wb, String path) throws Exception {
        File file = new File(path);
        file.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            wb.write(fos);
        }
    }

    private int drawHeader(Workbook wb, Sheet sheet, Invoice invoice) {
        InvoiceExcelStyles is = new InvoiceExcelStyles(wb);

        mergeAndStyle(sheet, 1, 1, 0, 3, invoice.getCompanyName(), is.headerTitle);
        mergeAndStyle(sheet, 2, 2, 0, 3, invoice.getCompanyAddress(), is.headerText);
        mergeAndStyle(sheet, 3, 3, 0, 3,
                "Email: " + invoice.getEmail() + " | Ph: " + invoice.getCompanyContact(),
                is.headerText);

        mergeAndStyle(sheet, 5, 5, 0, 1,
                "Date: " + invoice.getInvoiceDate(), is.Date);

        mergeAndStyle(sheet, 5, 5, 2, 3,
                "Client: " + invoice.getClientName(), is.clientText);

        mergeAndStyle(sheet, 6, 6, 0, 3,
                "Performa No: " + invoice.getInvoiceNo(), is.headerDate);

        return BODY_START_ROW;
    }

    public static CellRangeAddress mergeAndStyle(
            Sheet sheet, int startRow, int endRow,
            int startCol, int endCol,
            Object value, CellStyle style) {

        CellRangeAddress region = null;

        if (startRow != endRow || startCol != endCol) {
            region = new CellRangeAddress(startRow, endRow, startCol, endCol);
            sheet.addMergedRegion(region);
        }

        for (int r = startRow; r <= endRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) row = sheet.createRow(r);

            for (int c = startCol; c <= endCol; c++) {
                Cell cell = row.getCell(c);
                if (cell == null) cell = row.createCell(c);
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
        ExcelRegionUtil.applyBorder(
                sheet,
                new CellRangeAddress(startRow, endRow, 0, 3),
                true, true, true, true
        );
    }
}
