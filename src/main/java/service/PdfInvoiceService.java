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

            writeInvoiceContent(doc, invoice);

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
        writeInvoiceContent(doc, invoice);
    }

    private void writeInvoiceContent(Document doc, Invoice invoice) throws Exception {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        // Clean black-and-white/grayscale palette for print-friendly design
        java.awt.Color primaryColor = java.awt.Color.BLACK;
        java.awt.Color darkColor    = java.awt.Color.BLACK;
        java.awt.Color lightColor   = new java.awt.Color(60, 60, 60);     // dark gray body text
        java.awt.Color bgCream      = new java.awt.Color(242, 242, 242);  // light gray for header/totals
        java.awt.Color borderCream  = new java.awt.Color(160, 160, 160);  // clean grey divider/borders

        // Fonts
        Font companyNameFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, darkColor);
        Font titleFont         = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, darkColor);
        Font boldFont          = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, darkColor);
        Font normalFont        = FontFactory.getFont(FontFactory.HELVETICA, 10, lightColor);
        Font tableHeaderFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, darkColor);
        Font tableBodyFont     = FontFactory.getFont(FontFactory.HELVETICA, 9, lightColor);
        Font tableBodyBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, darkColor);
        Font totalFont         = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, darkColor);

        // ================= HEADER SECTION (Aligned Two-Column Table) =================
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{60, 40});

        // Company Details (Left)
        PdfPCell companyCell = new PdfPCell();
        companyCell.setBorder(Rectangle.NO_BORDER);
        companyCell.setPadding(0);
        
        companyCell.addElement(new Paragraph(invoice.getCompanyName().toUpperCase(), companyNameFont));
        
        Paragraph addrPara = new Paragraph(invoice.getCompanyAddress(), normalFont);
        addrPara.setSpacingBefore(3f);
        companyCell.addElement(addrPara);
        
        Paragraph contactPara = new Paragraph("Email: " + invoice.getEmail() + " | Ph: " + invoice.getCompanyContact(), normalFont);
        contactPara.setSpacingBefore(2f);
        companyCell.addElement(contactPara);
        
        String gst = CompanyProfile.getGst();
        if (gst != null && !gst.isBlank()) {
            Paragraph gstPara = new Paragraph("GSTIN: " + gst.trim(), normalFont);
            gstPara.setSpacingBefore(2f);
            companyCell.addElement(gstPara);
        }
        headerTable.addCell(companyCell);

        // Invoice Metadata (Right)
        PdfPCell metaCell = new PdfPCell();
        metaCell.setBorder(Rectangle.NO_BORDER);
        metaCell.setPadding(0);
        metaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        String docTitle = CompanyDataLayout.isProformaDocument(invoice) ? "PROFORMA INVOICE" : "TAX INVOICE";
        Paragraph titlePara = new Paragraph(docTitle, titleFont);
        titlePara.setAlignment(Element.ALIGN_RIGHT);
        metaCell.addElement(titlePara);
        
        String noLabel = CompanyDataLayout.isProformaDocument(invoice) ? "Proforma No: " : "Invoice No: ";
        Paragraph noPara = new Paragraph(noLabel + invoice.getInvoiceNo(), boldFont);
        noPara.setSpacingBefore(6f);
        noPara.setAlignment(Element.ALIGN_RIGHT);
        metaCell.addElement(noPara);
        
        Paragraph datePara = new Paragraph("Date: " + invoice.getInvoiceDate().format(fmt), normalFont);
        datePara.setSpacingBefore(2f);
        datePara.setAlignment(Element.ALIGN_RIGHT);
        metaCell.addElement(datePara);
        
        headerTable.addCell(metaCell);
        
        doc.add(headerTable);

        // ================= BILL TO SECTION (Dedicated Container Block) =================
        doc.add(new Paragraph(" ")); // spacer
        
        PdfPTable billToTable = new PdfPTable(1);
        billToTable.setWidthPercentage(100);
        
        PdfPCell billToCell = new PdfPCell();
        billToCell.setBorder(Rectangle.BOX);
        billToCell.setBorderColor(borderCream);
        billToCell.setBorderWidth(0.8f);
        billToCell.setPadding(10f);
        billToCell.setBackgroundColor(bgCream);
        
        Paragraph billToHeader = new Paragraph("BILL TO", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, darkColor));
        billToHeader.setSpacingAfter(4f);
        billToCell.addElement(billToHeader);
        
        Paragraph clientNamePara = new Paragraph(invoice.getClientName(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10.5f, darkColor));
        billToCell.addElement(clientNamePara);

        if (invoice.getBuyerAddress() != null && !invoice.getBuyerAddress().isBlank()) {
            Paragraph buyerAddr = new Paragraph(invoice.getBuyerAddress().trim(), normalFont);
            buyerAddr.setSpacingBefore(2f);
            billToCell.addElement(buyerAddr);
        }
        if (invoice.getBuyerGstin() != null && !invoice.getBuyerGstin().isBlank()) {
            Paragraph buyerGst = new Paragraph("GSTIN: " + invoice.getBuyerGstin().trim(), normalFont);
            buyerGst.setSpacingBefore(2f);
            billToCell.addElement(buyerGst);
        }
        billToTable.addCell(billToCell);
        doc.add(billToTable);

        doc.add(new Paragraph(" ")); // spacer

        // ================= SPECIFICATION TABLE =================
        // Column widths: Sr. (6%), Date (14%), Description (62%), Amount (18%)
        PdfPTable table = new PdfPTable(new float[]{30f, 70f, 310f, 90f});
        table.setWidthPercentage(100);
        table.setSplitRows(true);
        table.setSplitLate(false);

        // Table Header
        table.addCell(customHeaderCell("Sr.", tableHeaderFont, bgCream, primaryColor));
        table.addCell(customHeaderCell("Date", tableHeaderFont, bgCream, primaryColor));
        table.addCell(customHeaderCell("Description of Services / Goods", tableHeaderFont, bgCream, primaryColor));
        table.addCell(customHeaderCell("Amount", tableHeaderFont, bgCream, primaryColor));

        int serial = 0;

        for (InvoiceJob job : invoice.getJobs()) {
            // Main Job Row (Bold header text)
            table.addCell(centerCell(String.valueOf(++serial), tableBodyBoldFont));
            table.addCell(centerCell(job.getJobDate().format(fmt), tableBodyBoldFont));
            table.addCell(leftDescCell("(" + job.getJobNo() + ") - " + job.getJobName(), tableBodyBoldFont));
            table.addCell(rightAmountCell("", tableBodyFont)); // empty amount for header row

            // Job Lines (Indented description + aligned amount values)
            for (InvoiceLine line : job.getLines()) {
                table.addCell(centerCell("", tableBodyFont));
                table.addCell(centerCell("", tableBodyFont));
                table.addCell(subLineDescCell(line.getDescription(), tableBodyFont));
                table.addCell(rightAmountCell(formatAmount(line.getAmount()), tableBodyFont));
            }

            addSubtleSpacerRow(table);
        }

        // Subtly separate list from totals
        addSubtleSpacerRow(table);

        // Grand Total row (Single line, spanned columns with bold grayscale borders)
        PdfPCell totalLabelCell = new PdfPCell(new Paragraph("GRAND TOTAL", totalFont));
        totalLabelCell.setColspan(3);
        totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalLabelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        totalLabelCell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        totalLabelCell.setBorderColor(primaryColor);
        totalLabelCell.setBorderWidthTop(1.8f);
        totalLabelCell.setBorderWidthBottom(1.8f);
        totalLabelCell.setPadding(8f);
        totalLabelCell.setBackgroundColor(bgCream);
        
        PdfPCell totalValCell = new PdfPCell(new Paragraph("Rs. " + formatAmount(invoice.getGrandTotal()), totalFont));
        totalValCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalValCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        totalValCell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        totalValCell.setBorderColor(primaryColor);
        totalValCell.setBorderWidthTop(1.8f);
        totalValCell.setBorderWidthBottom(1.8f);
        totalValCell.setPadding(8f);
        totalValCell.setBackgroundColor(bgCream);
        
        table.addCell(totalLabelCell);
        table.addCell(totalValCell);

        // Outer outline grid border
        table.setTableEvent((tbl, widths, heights, headerRows, rowStart, canvases) -> {
            float left   = widths[0][0];
            float right  = widths[0][widths[0].length - 1];
            float top    = heights[0];
            float bottom = heights[heights.length - 1];

            Rectangle rect = new Rectangle(left, bottom, right, top);
            rect.setBorder(Rectangle.BOX);
            rect.setBorderColor(primaryColor);
            rect.setBorderWidth(1.2f);

            canvases[PdfPTable.LINECANVAS].rectangle(rect);
        });

        doc.add(table);

        // ================= COMPACT FOOTER (Terms & Authorized Signatory) =================
        doc.add(new Paragraph(" ")); // spacer
        
        PdfPTable footerTable = new PdfPTable(2);
        footerTable.setWidthPercentage(100);
        footerTable.setWidths(new float[]{65, 35});
        
        // Left: Terms & Conditions
        PdfPCell termsCell = new PdfPCell();
        termsCell.setBorder(Rectangle.NO_BORDER);
        termsCell.setPadding(0);
        
        Font footerTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, darkColor);
        Font footerTextFont  = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, lightColor);
        
        termsCell.addElement(new Paragraph("Terms & Conditions:", footerTitleFont));
        
        Paragraph t1 = new Paragraph("1. Goods once sold will not be taken back.", footerTextFont);
        t1.setLeading(9f);
        Paragraph t2 = new Paragraph("2. Payment shall be made immediately upon receipt of this document.", footerTextFont);
        t2.setLeading(9f);
        Paragraph t3 = new Paragraph("3. All disputes are subject to local jurisdiction.", footerTextFont);
        t3.setLeading(9f);
        
        termsCell.addElement(t1);
        termsCell.addElement(t2);
        termsCell.addElement(t3);
        footerTable.addCell(termsCell);
        
        // Right: Authorized Signatory
        PdfPCell sigCell = new PdfPCell();
        sigCell.setBorder(Rectangle.NO_BORDER);
        sigCell.setPadding(0);
        sigCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        Paragraph forCompany = new Paragraph("For " + invoice.getCompanyName().toUpperCase(), footerTitleFont);
        forCompany.setAlignment(Element.ALIGN_RIGHT);
        sigCell.addElement(forCompany);
        
        // Signatory line spacer
        Paragraph spacerPara = new Paragraph("\n\n");
        spacerPara.setLeading(8f);
        sigCell.addElement(spacerPara);
        
        Paragraph authorizedSig = new Paragraph("Authorized Signatory", footerTitleFont);
        authorizedSig.setAlignment(Element.ALIGN_RIGHT);
        sigCell.addElement(authorizedSig);
        
        footerTable.addCell(sigCell);
        
        doc.add(footerTable);
    }

    private PdfPCell customHeaderCell(String text, Font font, java.awt.Color bg, java.awt.Color border) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_CENTER);

        PdfPCell cell = new PdfPCell(p);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(border);
        cell.setBorderWidth(1.2f);
        cell.setPadding(8f);
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        return cell;
    }

    private PdfPCell subLineDescCell(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_LEFT);

        PdfPCell cell = new PdfPCell(p);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingLeft(15f); // Indentation for sublines
        cell.setPaddingRight(6f);
        cell.setPaddingTop(4f);
        cell.setPaddingBottom(4f);

        return cell;
    }

    private PdfPCell rightAmountCell(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_RIGHT);

        PdfPCell cell = new PdfPCell(p);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPaddingRight(10f);
        cell.setPaddingTop(4f);
        cell.setPaddingBottom(4f);

        return cell;
    }

    private void addSubtleSpacerRow(PdfPTable table) {
        PdfPCell spacer = new PdfPCell(new Paragraph(" "));
        spacer.setBorder(Rectangle.NO_BORDER);
        spacer.setFixedHeight(4f);

        table.addCell(spacer);
        table.addCell(spacer);
        table.addCell(spacer);
        table.addCell(spacer);
    }

    private String formatAmount(double amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(amount);
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
