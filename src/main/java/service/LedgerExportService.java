package service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import controller.ClientLedgerController.LedgerEntry;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.util.List;
import utils.CompanyDataLayout;
import utils.CompanyProfile;
import utils.DownloadTracker;

public class LedgerExportService {

    public static File generatePdfLedger(String clientName, String clientGst, String clientAddress, String clientPhone, String clientEmail,
                                        LocalDate fromDate, LocalDate toDate, List<LedgerEntry> entries,
                                        double totalDebit, double totalCredit, double closingBalance) throws Exception {
        return generatePdfLedger(clientName, clientGst, clientAddress, clientPhone, clientEmail,
                                 fromDate, toDate, entries, totalDebit, totalCredit, closingBalance, false);
    }

    public static File generatePdfLedger(String clientName, String clientGst, String clientAddress, String clientPhone, String clientEmail,
                                        LocalDate fromDate, LocalDate toDate, List<LedgerEntry> entries,
                                        double totalDebit, double totalCredit, double closingBalance, boolean isTemporary) throws Exception {
        
        File file;
        if (isTemporary) {
            file = File.createTempFile("ledger_print_", ".pdf");
            file.deleteOnExit();
        } else {
            File root = CompanyDataLayout.getDataStoreRoot();
            File exportDir = CompanyDataLayout.ledgerExportsDir(root, LocalDate.now(), clientName);
            String dateRange = (fromDate != null ? fromDate.toString() : "ALL") + "_to_" + (toDate != null ? toDate.toString() : "ALL");
            file = new File(exportDir, "Ledger_" + clientName.replaceAll("[^a-zA-Z0-9]", "_") + "_" + dateRange + ".pdf");
        }
        
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, new FileOutputStream(file));
        doc.open();

        Font companyTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        Font sectionTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

        // Company Details (Header)
        doc.add(new Paragraph(CompanyProfile.getName(), companyTitleFont));
        doc.add(new Paragraph(CompanyProfile.getAddress(), normalFont));
        doc.add(new Paragraph("Phone: " + CompanyProfile.getPhone() + " | Email: " + CompanyProfile.getEmail(), normalFont));
        if (!CompanyProfile.getGst().isEmpty()) {
            doc.add(new Paragraph("GSTIN: " + CompanyProfile.getGst(), normalFont));
        }
        
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("CLIENT GENERAL LEDGER STATEMENT", sectionTitleFont));
        
        // Date range
        String fromStr = fromDate != null ? fromDate.toString() : "Beginning";
        String toStr = toDate != null ? toDate.toString() : "Present";
        doc.add(new Paragraph("Period: " + fromStr + " to " + toStr, boldFont));
        
        doc.add(new Paragraph(" "));

        // Client Details
        doc.add(new Paragraph("Client: " + clientName, boldFont));
        if (clientGst != null && !clientGst.isEmpty() && !clientGst.equals("-")) {
            doc.add(new Paragraph("GSTIN: " + clientGst, normalFont));
        }
        if (clientAddress != null && !clientAddress.isEmpty() && !clientAddress.equals("-")) {
            doc.add(new Paragraph("Address: " + clientAddress, normalFont));
        }
        if (clientPhone != null && !clientPhone.isEmpty() && !clientPhone.equals("-")) {
            doc.add(new Paragraph("Phone: " + clientPhone, normalFont));
        }
        
        doc.add(new Paragraph(" "));

        // Table
        // DATE | REFERENCE | TYPE | MODE | DEBIT | CREDIT | BALANCE | STATUS
        PdfPTable table = new PdfPTable(new float[]{70, 160, 80, 60, 70, 70, 80, 65});
        table.setWidthPercentage(100);
        table.setSpacingBefore(10f);

        // Header Row
        table.addCell(headerCell("Date", boldFont));
        table.addCell(headerCell("Reference", boldFont));
        table.addCell(headerCell("Type", boldFont));
        table.addCell(headerCell("Mode", boldFont));
        table.addCell(headerCell("Debit (-)", boldFont));
        table.addCell(headerCell("Credit (+)", boldFont));
        table.addCell(headerCell("Balance", boldFont));
        table.addCell(headerCell("Status", boldFont));

        for (LedgerEntry entry : entries) {
            table.addCell(cell(entry.getDate(), normalFont, Element.ALIGN_CENTER));
            table.addCell(cell(entry.getReference(), normalFont, Element.ALIGN_LEFT));
            table.addCell(cell(entry.getType(), normalFont, Element.ALIGN_CENTER));
            table.addCell(cell(entry.getMode(), normalFont, Element.ALIGN_CENTER));
            
            String debVal = entry.getDebit() != null ? String.format("%.2f", entry.getDebit()) : "-";
            table.addCell(cell(debVal, normalFont, Element.ALIGN_RIGHT));
            
            String credVal = entry.getCredit() != null ? String.format("%.2f", entry.getCredit()) : "-";
            table.addCell(cell(credVal, normalFont, Element.ALIGN_RIGHT));
            
            table.addCell(cell(String.format("%.2f", entry.getBalance()), normalFont, Element.ALIGN_RIGHT));
            table.addCell(cell(entry.getStatus(), normalFont, Element.ALIGN_CENTER));
        }

        // Summary Row
        PdfPCell summaryLabelCell = new PdfPCell(new Phrase("TOTALS", boldFont));
        summaryLabelCell.setColspan(4);
        summaryLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        summaryLabelCell.setPadding(5);
        table.addCell(summaryLabelCell);

        table.addCell(cell(String.format("%.2f", totalDebit), boldFont, Element.ALIGN_RIGHT));
        table.addCell(cell(String.format("%.2f", totalCredit), boldFont, Element.ALIGN_RIGHT));
        table.addCell(cell(String.format("%.2f", closingBalance), boldFont, Element.ALIGN_RIGHT));
        table.addCell(cell("", boldFont, Element.ALIGN_CENTER));

        doc.add(table);
        doc.close();

        if (!isTemporary) {
            DownloadTracker.registerExportedFile(file, "PDF");
        }
        return file;
    }

    public static File generateExcelLedger(String clientName, String clientGst, String clientAddress, String clientPhone, String clientEmail,
                                          LocalDate fromDate, LocalDate toDate, List<LedgerEntry> entries,
                                          double totalDebit, double totalCredit, double closingBalance) throws Exception {
        
        File root = CompanyDataLayout.getDataStoreRoot();
        File exportDir = CompanyDataLayout.ledgerExportsDir(root, LocalDate.now(), clientName);
        String dateRange = (fromDate != null ? fromDate.toString() : "ALL") + "_to_" + (toDate != null ? toDate.toString() : "ALL");
        File file = new File(exportDir, "Ledger_" + clientName.replaceAll("[^a-zA-Z0-9]", "_") + "_" + dateRange + ".xlsx");

        org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Ledger");

        // Cell styles
        org.apache.poi.ss.usermodel.CellStyle titleStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);

        org.apache.poi.ss.usermodel.CellStyle boldStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        boldStyle.setFont(boldFont);

        org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(boldFont);
        headerStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.MEDIUM);
        headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

        org.apache.poi.ss.usermodel.CellStyle rightAlignStyle = workbook.createCellStyle();
        rightAlignStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT);

        org.apache.poi.ss.usermodel.CellStyle centerAlignStyle = workbook.createCellStyle();
        centerAlignStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);

        int rowNum = 0;

        // Title
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue(CompanyProfile.getName());
        row.getCell(0).setCellStyle(titleStyle);

        row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue(CompanyProfile.getAddress());
        
        row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue("Phone: " + CompanyProfile.getPhone() + " | Email: " + CompanyProfile.getEmail());

        rowNum++; // Spacer

        // Period
        row = sheet.createRow(rowNum++);
        String fromStr = fromDate != null ? fromDate.toString() : "Beginning";
        String toStr = toDate != null ? toDate.toString() : "Present";
        row.createCell(0).setCellValue("Statement Period: " + fromStr + " to " + toStr);
        row.getCell(0).setCellStyle(boldStyle);

        rowNum++; // Spacer

        // Client info
        row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue("Client: " + clientName);
        row.getCell(0).setCellStyle(boldStyle);

        if (clientGst != null && !clientGst.isEmpty() && !clientGst.equals("-")) {
            row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue("GSTIN: " + clientGst);
        }
        if (clientAddress != null && !clientAddress.isEmpty() && !clientAddress.equals("-")) {
            row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue("Address: " + clientAddress);
        }

        rowNum++; // Spacer

        // Header
        row = sheet.createRow(rowNum++);
        String[] headers = {"Date", "Reference", "Type", "Mode", "Debit (-)", "Credit (+)", "Balance", "Status"};
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data Rows
        for (LedgerEntry entry : entries) {
            row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(entry.getDate());
            row.getCell(0).setCellStyle(centerAlignStyle);
            
            row.createCell(1).setCellValue(entry.getReference());
            
            row.createCell(2).setCellValue(entry.getType());
            row.getCell(2).setCellStyle(centerAlignStyle);
            
            row.createCell(3).setCellValue(entry.getMode());
            row.getCell(3).setCellStyle(centerAlignStyle);

            if (entry.getDebit() != null) {
                org.apache.poi.ss.usermodel.Cell c = row.createCell(4);
                c.setCellValue(entry.getDebit());
                c.setCellStyle(rightAlignStyle);
            } else {
                row.createCell(4).setCellValue("-");
                row.getCell(4).setCellStyle(centerAlignStyle);
            }

            if (entry.getCredit() != null) {
                org.apache.poi.ss.usermodel.Cell c = row.createCell(5);
                c.setCellValue(entry.getCredit());
                c.setCellStyle(rightAlignStyle);
            } else {
                row.createCell(5).setCellValue("-");
                row.getCell(5).setCellStyle(centerAlignStyle);
            }

            org.apache.poi.ss.usermodel.Cell cBal = row.createCell(6);
            cBal.setCellValue(entry.getBalance());
            cBal.setCellStyle(rightAlignStyle);

            row.createCell(7).setCellValue(entry.getStatus());
            row.getCell(7).setCellStyle(centerAlignStyle);
        }

        org.apache.poi.ss.usermodel.CellStyle rightAlignBoldStyle = workbook.createCellStyle();
        rightAlignBoldStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT);
        rightAlignBoldStyle.setFont(boldFont);

        // Totals Row
        row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue("TOTALS");
        row.getCell(0).setCellStyle(boldStyle);
        
        org.apache.poi.ss.usermodel.Cell totDeb = row.createCell(4);
        totDeb.setCellValue(totalDebit);
        totDeb.setCellStyle(rightAlignBoldStyle);
        
        org.apache.poi.ss.usermodel.Cell totCred = row.createCell(5);
        totCred.setCellValue(totalCredit);
        totCred.setCellStyle(rightAlignBoldStyle);

        org.apache.poi.ss.usermodel.Cell totBal = row.createCell(6);
        totBal.setCellValue(closingBalance);
        totBal.setCellStyle(rightAlignBoldStyle);

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        try (FileOutputStream fos = new FileOutputStream(file)) {
            workbook.write(fos);
        }
        workbook.close();

        DownloadTracker.registerExportedFile(file, "EXCEL");
        return file;
    }

    private static PdfPCell headerCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(6);
        cell.setBackgroundColor(new java.awt.Color(220, 220, 220));
        return cell;
    }

    private static PdfPCell cell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5);
        return cell;
    }
}
