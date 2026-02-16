package service;

import java.io.File;
import java.io.FileOutputStream;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Element;
import com.lowagie.text.Rectangle;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPCell;

import model.Invoice;
import model.InvoiceJob;
import model.InvoiceLine;

public class PdfInvoiceService {

    public File generateSingleInvoicePDF(Invoice invoice) {

        try {
            File file = InvoiceStorageService.createPdfFile(invoice);

            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, new FileOutputStream(file));
            doc.open();

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");

            Font titleFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font boldFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12);

            // ================= HEADER =================
            doc.add(new Paragraph(invoice.getCompanyName(), titleFont));
            doc.add(new Paragraph(invoice.getCompanyAddress(), normalFont));
            doc.add(new Paragraph(
                    "Email: " + invoice.getEmail() +
                    " | Ph: " + invoice.getCompanyContact(),
                    normalFont
            ));

            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Invoice No: " + invoice.getInvoiceNo(), boldFont));
            doc.add(new Paragraph("Date: " + invoice.getInvoiceDate().format(fmt), normalFont));
            doc.add(new Paragraph("Client: " + invoice.getClientName(), normalFont));

            doc.add(new Paragraph(" "));

            // ================= TABLE =================
            PdfPTable table = new PdfPTable(new float[]{40, 80, 300, 80});
            table.setSplitRows(true);
           

            table.setWidthPercentage(100);
            table.setSplitLate(false);
         //   table.setSpacingBefore(10f);

            // ---- HEADER ROW ----
            table.addCell(headerCell("#", boldFont));
            table.addCell(headerCell("Date", boldFont));
            table.addCell(headerCell("Description", boldFont));
            table.addCell(headerCell("Amount", boldFont));

            int serial = 0;

            // ================= JOB LOOP =================
            for (InvoiceJob job : invoice.getJobs()) {

                // Job header
                table.addCell(centerCell(String.valueOf(++serial), normalFont));
                table.addCell(centerCell(job.getJobDate().format(fmt), normalFont));
                table.addCell(leftDescCell("(" + job.getJobNo() + ") - " + job.getJobName(), normalFont));
                table.addCell(centerCell("", normalFont));

                // Job lines
                for (InvoiceLine line : job.getLines()) {
                    table.addCell(centerCell("", normalFont));
                    table.addCell(centerCell("", normalFont));
                    table.addCell(leftDescCell(line.getDescription(), normalFont));
                    table.addCell(centerCell(String.format("%.2f", line.getAmount()), normalFont));
                }

                // Spacer between jobs
                addSpacerRow(table);
            }

            // ================= GRAND TOTAL =================
            addSpacerRow(table);

            table.addCell(centerCell("", boldFont));
            table.addCell(centerCell("", boldFont));
            table.addCell(rightBoldCell("GRAND TOTAL", boldFont));
            table.addCell(rightBoldCell(String.format("%.2f", invoice.getGrandTotal()), boldFont));

            // ---- OUTER BORDER ----
//            PdfPCell wrapper = new PdfPCell(table);
//            wrapper.setBorder(Rectangle.BOX);
//            wrapper.setPadding(6f);
//
//            PdfPTable outer = new PdfPTable(1);
//            outer.setWidthPercentage(100);
//            outer.addCell(wrapper);
//
//            doc.add(outer);
            
            table.setTableEvent((tbl, widths, heights, headerRows, rowStart, canvases) -> {

                float left   = widths[0][0];
                float right  = widths[0][widths[0].length - 1];
                float top    = heights[0];
                float bottom = heights[heights.length - 1];

                Rectangle rect = new Rectangle(left, bottom, right, top);
                rect.setBorder(Rectangle.BOX);
                rect.setBorderWidth(1.2f);

                canvases[PdfPTable.LINECANVAS].rectangle(rect);
            });
            
            doc.add(table);
            doc.close();
            return file;

        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed", e);
        }
    }
    
    public Map<String, File> generateMonthlyClientPDF(Map<String, Invoice> invoiceMap) {

        Map<String, File> createdFiles = new LinkedHashMap<>();

        for (Map.Entry<String, Invoice> entry : invoiceMap.entrySet()) {

            Invoice invoice = entry.getValue();

            if (invoice.getJobs() == null || invoice.getJobs().isEmpty())
                continue;

            File pdf = generateSingleInvoicePDF(invoice);

            if (pdf != null && pdf.exists()) {
                createdFiles.put(entry.getKey(), pdf);
            }
        }

        return createdFiles;
    }

    public File generateMonthlyBulkPDF(YearMonth ym, Map<String, Invoice> invoiceMap) {
        return generateMonthlyBulkPDF(ym, invoiceMap, null, null);
    }

    public File generateMonthlyBulkPDF(YearMonth ym, Map<String, Invoice> invoiceMap, BooleanSupplier isCancelled) {
        return generateMonthlyBulkPDF(ym, invoiceMap, isCancelled, null);
    }

    public File generateMonthlyBulkPDF(YearMonth ym, Map<String, Invoice> invoiceMap, BooleanSupplier isCancelled, File[] outFileRef) {

        try {
            if (invoiceMap == null || invoiceMap.isEmpty()) {
                return null;
            }

            boolean hasData = false;
            for (Invoice inv : invoiceMap.values()) {
                if (inv.getJobs() != null && !inv.getJobs().isEmpty()) {
                    hasData = true;
                    break;
                }
            }

            if (!hasData) {
                return null;
            }

            File file = InvoiceStorageService.createMonthlyPdfFile(ym);
            if (outFileRef != null && outFileRef.length > 0) outFileRef[0] = file;

            Document doc = new Document(PageSize.A4, 36, 36, 30, 36);
            PdfWriter.getInstance(doc, new FileOutputStream(file));
            doc.open();

            try {
                boolean firstPageWritten = false;

                for (Invoice invoice : invoiceMap.values()) {

                    if (isCancelled != null && isCancelled.getAsBoolean())
                        throw new CancellationException();

                    if (invoice.getJobs() == null || invoice.getJobs().isEmpty())
                        continue;

                    if (firstPageWritten) {
                        doc.newPage();
                    }

                    addInvoiceToDocument(doc, invoice);
                    firstPageWritten = true;
                }

                if (!firstPageWritten) {
                    doc.add(new Paragraph("No invoice data available for this period."));
                }

                doc.close();
                return file;

            } catch (CancellationException e) {
                try { doc.close(); } catch (Exception ignored) {}
                throw e;
            }

        } catch (CancellationException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Monthly bulk PDF generation failed", e);
        }
    }


    
    private void addInvoiceToDocument(Document doc, Invoice invoice) throws Exception {

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        Font titleFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font boldFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12);

        // ================= HEADER =================
        doc.add(new Paragraph(invoice.getCompanyName(), titleFont));
        doc.add(new Paragraph(invoice.getCompanyAddress(), normalFont));
        doc.add(new Paragraph(
                "Email: " + invoice.getEmail() +
                " | Ph: " + invoice.getCompanyContact(),
                normalFont
        ));

        doc.add(new Paragraph(" "));

        doc.add(new Paragraph("Invoice No: " + invoice.getInvoiceNo(), boldFont));
        doc.add(new Paragraph("Date: " + invoice.getInvoiceDate().format(fmt), normalFont));
        doc.add(new Paragraph("Client: " + invoice.getClientName(), normalFont));

        doc.add(new Paragraph(" "));

        // ================= TABLE =================
        PdfPTable table = new PdfPTable(new float[]{40, 80, 300, 80});
        table.setWidthPercentage(100);
        table.setSplitRows(true);
        table.setSplitLate(false);

        table.addCell(headerCell("#", boldFont));
        table.addCell(headerCell("Date", boldFont));
        table.addCell(headerCell("Description", boldFont));
        table.addCell(headerCell("Amount", boldFont));

        int serial = 0;

        for (InvoiceJob job : invoice.getJobs()) {

            table.addCell(centerCell(String.valueOf(++serial), normalFont));
            table.addCell(centerCell(job.getJobDate().format(fmt), normalFont));
            table.addCell(leftDescCell("(" + job.getJobNo() + ") - " + job.getJobName(), normalFont));
            table.addCell(centerCell("", normalFont));

            for (InvoiceLine line : job.getLines()) {
                table.addCell(centerCell("", normalFont));
                table.addCell(centerCell("", normalFont));
                table.addCell(leftDescCell(line.getDescription(), normalFont));
                table.addCell(centerCell(String.format("%.2f", line.getAmount()), normalFont));
            }

            addSpacerRow(table);
        }

        addSpacerRow(table);

        table.addCell(centerCell("", boldFont));
        table.addCell(centerCell("", boldFont));
        table.addCell(rightBoldCell("GRAND TOTAL", boldFont));
        table.addCell(rightBoldCell(String.format("%.2f", invoice.getGrandTotal()), boldFont));

        // 🔹 Outer border
        table.setTableEvent((tbl, widths, heights, headerRows, rowStart, canvases) -> {

            float left   = widths[0][0];
            float right  = widths[0][widths[0].length - 1];
            float top    = heights[0];
            float bottom = heights[heights.length - 1];

            Rectangle rect = new Rectangle(left, bottom, right, top);
            rect.setBorder(Rectangle.BOX);
            rect.setBorderWidth(1.2f);

            canvases[PdfPTable.LINECANVAS].rectangle(rect);
        });

        doc.add(table);
    }


    // =========================================================
    // CELL BUILDERS
    // =========================================================

    private PdfPCell headerCell(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_CENTER);

        PdfPCell cell = new PdfPCell(p);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderWidth(1f);
        cell.setPadding(6f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        return cell;
    }

    private PdfPCell centerCell(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_CENTER);

        PdfPCell cell = new PdfPCell(p);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(4f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        return cell;
    }

    private PdfPCell leftDescCell(String text, Font font) {
        Paragraph p = new Paragraph("   " + text, font); // small left padding
        p.setAlignment(Element.ALIGN_LEFT);

        PdfPCell cell = new PdfPCell(p);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(4f);

        return cell;
    }

    private PdfPCell rightBoldCell(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_RIGHT);

        PdfPCell cell = new PdfPCell(p);
        cell.setBorder(Rectangle.TOP);
        cell.setBorderWidthTop(1.5f);
        cell.setPadding(6f);

        return cell;
    }

    private void addSpacerRow(PdfPTable table) {
        PdfPCell spacer = new PdfPCell(new Paragraph(" "));
        spacer.setBorder(Rectangle.NO_BORDER);
        spacer.setFixedHeight(6f);

        table.addCell(spacer);
        table.addCell(spacer);
        table.addCell(spacer);
        table.addCell(spacer);
    }
}
