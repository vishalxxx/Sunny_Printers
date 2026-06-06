package service;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import model.SystemSettings;

import repository.SystemSettingsRepository;

import utils.CompanyProfile;
import utils.CompanyDataLayout;
import utils.DBConnection;
import utils.DocumentNumbering;
import utils.DownloadTracker;

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
            addGstLineIfPresent(doc, normalFont);

            doc.add(new Paragraph(" "));

            String noLabel = CompanyDataLayout.isProformaDocument(invoice) ? "Proforma No: " : "Invoice No: ";
            doc.add(new Paragraph(noLabel + invoice.getInvoiceNo(), boldFont));
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
            table.addCell(headerCell("Sr.", boldFont));
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
            table.addCell(rightBoldCell("Rs. " + String.format("%.2f", invoice.getGrandTotal()), boldFont));

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
            DownloadTracker.registerExportedFile(file, "PDF");
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
                DownloadTracker.registerExportedFile(file, "PDF");
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
        addGstLineIfPresent(doc, normalFont);

        doc.add(new Paragraph(" "));

        String noLabel = CompanyDataLayout.isProformaDocument(invoice) ? "Proforma No: " : "Invoice No: ";
        doc.add(new Paragraph(noLabel + invoice.getInvoiceNo(), boldFont));
        doc.add(new Paragraph("Date: " + invoice.getInvoiceDate().format(fmt), normalFont));
        doc.add(new Paragraph("Client: " + invoice.getClientName(), normalFont));

        doc.add(new Paragraph(" "));

        // ================= TABLE =================
        PdfPTable table = new PdfPTable(new float[]{40, 80, 300, 80});
        table.setWidthPercentage(100);
        table.setSplitRows(true);
        table.setSplitLate(false);

        table.addCell(headerCell("Sr.", boldFont));
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
        table.addCell(rightBoldCell("Rs. " + String.format("%.2f", invoice.getGrandTotal()), boldFont));

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

    /**
     * Payment receipt PDF: payment header, payment_details rows, invoice allocations.
     */
    public void writePaymentReceiptPdf(String paymentUuid, File destFile) throws Exception {
        if (destFile == null || paymentUuid == null || paymentUuid.isBlank()) {
            throw new IllegalArgumentException("Invalid payment or destination file.");
        }
        File parent = destFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        Map<String, String> core = new LinkedHashMap<>();
        List<String[]> detailLines = new ArrayList<>();
        List<String[]> allocLines = new ArrayList<>();

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        try (Connection con = DBConnection.getConnection()) {
            String sql = "SELECT p.payment_date, p.type, p.method, p.amount, "
                    + "c.business_name, c.client_name FROM payments p "
                    + "LEFT JOIN clients c ON c.uuid = p.client_uuid WHERE p.uuid = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, paymentUuid);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    throw new IllegalArgumentException("Payment not found: " + paymentUuid);
                }
                String dateRaw = rs.getString("payment_date");
                LocalDate payDate = LocalDate.now();
                try {
                    if (dateRaw != null && !dateRaw.isBlank()) {
                        String d = dateRaw.contains(" ") ? dateRaw.split(" ")[0] : dateRaw;
                        payDate = LocalDate.parse(d);
                    }
                } catch (Exception ignored) {
                }
                String receiptNo = new NumberSequenceAllocationService()
                        .resolvePaymentReceiptNo(con, paymentUuid, payDate, true);
                core.put("Receipt no", receiptNo);
                core.put("Date", formatPaymentDateForReceiptPdf(dateRaw));
                String bName = rs.getString("business_name");
                String cName = rs.getString("client_name");
                String client = (bName != null && !bName.isBlank()) ? bName : (cName != null ? cName : "");
                core.put("Client", client);
                String typeStr = rs.getString("type");
                core.put("Type", typeStr != null ? typeStr : "");
                core.put("Method", rs.getString("method") != null ? rs.getString("method") : "");
                core.put("Amount", currencyFormat.format(rs.getDouble("amount")));
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT field_key, field_value FROM payment_details WHERE payment_uuid = ?")) {
                ps.setString(1, paymentUuid);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String key = rs.getString("field_key").replace("_", " ").toUpperCase(Locale.ROOT);
                    detailLines.add(new String[] { key, rs.getString("field_value") });
                }
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT i.invoice_no, a.allocated_amount FROM payment_allocations a "
                            + "JOIN invoice_master i ON a.invoice_uuid = i.uuid WHERE a.payment_uuid = ?")) {
                ps.setString(1, paymentUuid);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    allocLines.add(new String[] {
                            rs.getString("invoice_no"),
                            String.format("Rs. %.2f", rs.getDouble("allocated_amount"))
                    });
                }
            }
        }

        DateTimeFormatter pdfStamp = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 11);

        Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
        try (FileOutputStream fos = new FileOutputStream(destFile)) {
            PdfWriter.getInstance(doc, fos);
            doc.open();

            addPaymentReceiptLetterhead(doc, titleFont, normal);
            doc.add(new Paragraph("Payment receipt", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13)));
            doc.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(new float[] { 32, 68 });
            table.setWidthPercentage(100);
            for (Map.Entry<String, String> e : core.entrySet()) {
                paymentReceiptKvRow(table, e.getKey(), e.getValue(), bold, normal);
            }
            doc.add(table);

            if (!detailLines.isEmpty()) {
                doc.add(new Paragraph(" "));
                doc.add(new Paragraph("Additional details", bold));
                PdfPTable t2 = new PdfPTable(new float[] { 32, 68 });
                t2.setWidthPercentage(100);
                for (String[] row : detailLines) {
                    paymentReceiptKvRow(t2, row[0], row[1] != null ? row[1] : "", bold, normal);
                }
                doc.add(t2);
            }

            if (!allocLines.isEmpty()) {
                doc.add(new Paragraph(" "));
                doc.add(new Paragraph("Invoice allocations", bold));
                PdfPTable t3 = new PdfPTable(new float[] { 50, 50 });
                t3.setWidthPercentage(100);
                t3.addCell(new PdfPCell(new Paragraph("Invoice", bold)));
                t3.addCell(new PdfPCell(new Paragraph("Allocated amount", bold)));
                for (String[] row : allocLines) {
                    t3.addCell(new PdfPCell(new Paragraph(row[0], normal)));
                    t3.addCell(new PdfPCell(new Paragraph(row[1], normal)));
                }
                doc.add(t3);
            }

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Generated: " + LocalDateTime.now().format(pdfStamp),
                    FontFactory.getFont(FontFactory.HELVETICA, 9)));
            doc.close();
        }
        DownloadTracker.registerExportedFile(destFile, "PDF");
    }

    private static void addGstLineIfPresent(Document doc, Font normalFont) throws Exception {
        String gst = CompanyProfile.getGst();
        if (gst != null && !gst.isBlank()) {
            doc.add(new Paragraph("GST: " + gst.trim(), normalFont));
        }
    }

    private static void addPaymentReceiptLetterhead(Document doc, Font titleFont, Font normalFont) throws Exception {
        doc.add(new Paragraph(CompanyProfile.getName(), titleFont));
        doc.add(new Paragraph(CompanyProfile.getAddress(), normalFont));
        doc.add(new Paragraph(
                "Email: " + CompanyProfile.getEmail() + " | Ph: " + CompanyProfile.getPhone(),
                normalFont));
        addGstLineIfPresent(doc, normalFont);
    }

    private static void paymentReceiptKvRow(PdfPTable table, String key, String val, Font keyFont, Font valFont) {
        PdfPCell k = new PdfPCell(new Paragraph(key + ":", keyFont));
        k.setBorder(Rectangle.NO_BORDER);
        k.setPaddingBottom(4f);
        PdfPCell v = new PdfPCell(new Paragraph(val != null ? val : "", valFont));
        v.setBorder(Rectangle.NO_BORDER);
        v.setPaddingBottom(4f);
        table.addCell(k);
        table.addCell(v);
    }

    private static String formatPaymentDateForReceiptPdf(String dateRaw) {
        if (dateRaw == null || dateRaw.isBlank()) {
            return "";
        }
        try {
            String d = dateRaw.contains(" ") ? dateRaw.split(" ")[0] : dateRaw;
            LocalDate ld = LocalDate.parse(d);
            return ld.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        } catch (Exception e) {
            return dateRaw;
        }
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
