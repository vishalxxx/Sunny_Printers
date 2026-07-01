package service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import model.Invoice;
import model.InvoiceJob;
import model.InvoiceLine;
import model.BankDetails;
import utils.CompanyProfile;
import utils.NumberToWords;

import java.io.File;
import java.io.FileOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class GstPdfInvoiceService {

    private final Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10.8f);

    private final Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.4f);

    private final Font fontNormal = FontFactory.getFont(FontFactory.HELVETICA, 8.3f);

    private final Font fontTotal = FontFactory.getFont(
            FontFactory.HELVETICA_BOLD,
            9.2f);
    private final Font fontSmall = FontFactory.getFont(FontFactory.HELVETICA, 8f);

    private final Font fontBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.4f);

    private final Font fontCompany = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f);
    private final Font fontBold9 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f);
    private final Font fontRegular9 = FontFactory.getFont(FontFactory.HELVETICA, 9f);
    private final Font fontAddr = FontFactory.getFont(FontFactory.HELVETICA, 8f);
    private final Font fontTinyBold = FontFactory.getFont(
            FontFactory.HELVETICA_BOLD,
            6.5f);

    public File generateGstInvoice(Invoice invoice) throws Exception {

        File file = InvoiceStorageService.createPdfFile(invoice);

        // Increased margins for the page box
        Document document = new Document(PageSize.A4, 20, 20, 20, 20);

        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(file));

        document.open();

        // PDF Metadata
        document.addTitle("GST Invoice");
        document.addAuthor("Sunny Printers");
        document.addCreator("Sunny Printers ERP");

        // Main Wrapper Table
        PdfPTable mainTable = new PdfPTable(1);

        // Use maximum printable width
        mainTable.setWidthPercentage(100);

        // Slight spacing compression
        mainTable.getDefaultCell().setPadding(0);

        // ===== TITLE SECTION =====
        PdfPCell titleCell = createTitleCell();

        titleCell.setPaddingTop(1f);
        titleCell.setPaddingBottom(12f);

        mainTable.addCell(titleCell);

        // ===== HEADER SECTION =====
        PdfPCell upperHeader = createUpperHeaderSectionCell(invoice);

        upperHeader.setPadding(0);

        mainTable.addCell(upperHeader);

        // ===== ITEMS SECTION =====
        PdfPCell itemsSection = createItemsTableCell(invoice);

        itemsSection.setPadding(0);

        mainTable.addCell(itemsSection);

        // ===== AMOUNT IN WORDS =====
        PdfPCell amountWords = createAmountInWordsCell(invoice);

        amountWords.setPaddingTop(1.5f);
        amountWords.setPaddingBottom(1.5f);

        mainTable.addCell(amountWords);

        // ===== HSN SUMMARY =====
        PdfPCell hsnSection = createHsnSummaryCell(invoice);

        hsnSection.setPadding(0);

        mainTable.addCell(hsnSection);

        // ===== TAX WORDS =====
        PdfPCell taxWords = createTaxAmountInWordsCell(invoice);

        taxWords.setPaddingTop(1.5f);
        taxWords.setPaddingBottom(1.5f);

        mainTable.addCell(taxWords);

        // ===== FOOTER =====
        PdfPCell footerSection = createFooterSectionCell(invoice);

        footerSection.setPadding(0);

        mainTable.addCell(footerSection);

        // Add Main Table
        document.add(mainTable);

        // Bottom Computer Generated Note
        Paragraph computerGen = new Paragraph(
                "This is a Computer Generated Invoice",
                fontTinyBold);

        computerGen.setAlignment(Element.ALIGN_CENTER);

        // Tight spacing like industrial ERP invoices
        computerGen.setSpacingBefore(2f);
        computerGen.setLeading(6f);

        document.add(computerGen);

        document.close();

        utils.DownloadTracker.registerExportedFile(file, "PDF");

        return file;

    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String safeUpper(String s) {
        return safe(s).toUpperCase();
    }

    private Paragraph p(String text, Font font, float leading) {

        Paragraph p = new Paragraph(text, font);

        // Compact industrial invoice line spacing
        p.setLeading(leading);

        // Prevent excessive spacing
        p.setSpacingBefore(0f);
        p.setSpacingAfter(0f);

        // Better text density like Busy/Tally invoices
        p.setMultipliedLeading(0.95f);

        return p;

    }

    private Paragraph normalP(String text) {

        Paragraph p = p(text, fontNormal, 8.8f);

        p.setSpacingBefore(0f);
        p.setSpacingAfter(0f);

        return p;

    }

    private Paragraph boldP(String text) {

        Paragraph p = p(text, fontBold, 8.8f);

        p.setSpacingBefore(0f);
        p.setSpacingAfter(0f);

        return p;

    }

    private Paragraph smallP(String text) {

        Paragraph p = p(text, fontSmall, 7.6f);

        p.setSpacingBefore(0f);
        p.setSpacingAfter(0f);

        return p;

    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String fmtMoney(double v) {

        return String.format("%,.2f", v);

    }

    private static String fmtQty3(long v) {

        return String.format("%.3f", (double) v);

    }

    private static final DateTimeFormatter INVOICE_DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    private PdfPCell createTitleCell() {

        // 3 columns: 30% (empty spacer), 40% (centered title), 30% (right-aligned
        // recipient label)
        PdfPTable table = new PdfPTable(new float[] { 30f, 40f, 30f });

        table.setWidthPercentage(100);

        table.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        // ===== LEFT SPACER =====
        PdfPCell cEmpty = new PdfPCell();
        cEmpty.setBorder(Rectangle.NO_BORDER);
        table.addCell(cEmpty);

        // ===== CENTER TITLE =====
        Paragraph p1 = new Paragraph("Tax Invoice", fontTitle);
        p1.setLeading(11f);
        p1.setAlignment(Element.ALIGN_CENTER);

        PdfPCell c1 = new PdfPCell();
        c1.setPaddingTop(2f);
        c1.setPaddingBottom(2f);
        c1.setBorder(Rectangle.NO_BORDER);
        c1.setHorizontalAlignment(Element.ALIGN_CENTER);
        c1.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c1.addElement(p1);
        table.addCell(c1);

        // ===== RIGHT LABEL =====
        Paragraph p2 = new Paragraph(
                "(ORIGINAL FOR RECIPIENT)",
                fontSmall);
        p2.setLeading(8f);
        p2.setAlignment(Element.ALIGN_RIGHT);

        PdfPCell c2 = new PdfPCell();
        c2.setBorder(Rectangle.NO_BORDER);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c2.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c2.setPaddingTop(2f);
        c2.setPaddingBottom(1f);
        c2.setPaddingLeft(1f);
        c2.setPaddingRight(2f);
        c2.addElement(p2);
        table.addCell(c2);

        // ===== OUTER CONTAINER (BORDERLESS OUT OF THE BOX) =====
        PdfPCell cell = new PdfPCell(table);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingTop(1f);
        cell.setPaddingBottom(8f);

        return cell;

    }

    private PdfPCell createUpperHeaderSectionCell(Invoice invoice) {
        PdfPTable table = new PdfPTable(new float[] { 1f, 1f });
        table.setWidthPercentage(100);

        float leftCellHeight = 72f;
        float rightRowHeight = (leftCellHeight * 3f) / 7f; // 30.85f

        // --- LEFT COLUMN ---
        PdfPTable leftNested = new PdfPTable(1);
        leftNested.setWidthPercentage(100);

        // 1. Seller Info
        String companyName = CompanyProfile.getName();
        String companyAddress = CompanyProfile.getAddress();
        String companyGst = CompanyProfile.getGst().trim();
        if (companyGst.isBlank() && safeUpper(companyName).contains("SUNNY PRINTER")) {
            companyGst = "07BPPPS3532E2ZO";
        }

        PdfPCell sellerCell = new PdfPCell();
        sellerCell.setFixedHeight(leftCellHeight);
        sellerCell.setBorder(Rectangle.BOTTOM);
        sellerCell.setBorderWidth(0.6f);
        sellerCell.setPaddingTop(3f);
        sellerCell.setPaddingBottom(3f);
        sellerCell.setPaddingLeft(3f);
        sellerCell.setPaddingRight(3f);
        sellerCell.addElement(p(safeUpper(companyName), fontCompany, 10f));

        String sellerAddr = safeUpper(companyAddress);
        Paragraph sellerAddrPara = p(sellerAddr, fontAddr, 8.5f);
        sellerAddrPara.setSpacingBefore(2.5f);
        sellerCell.addElement(sellerAddrPara);
        if (safeUpper(companyName).contains("SUNNY PRINTER") && !sellerAddr.contains("MSME")) {
            sellerCell.addElement(normalP("MSME-UAM NO-DL06A0017978"));
        }
        if (!companyGst.isBlank()) {
            sellerCell.addElement(normalP("GSTIN/UIN : " + companyGst));
        }

        String companyCode = extractStateCode(companyGst);
        if (companyCode == null || companyCode.isBlank()) {
            companyCode = "07";
        }
        String companyState = "State Name : " + getStateNameFromCode(companyCode) + ", Code : " + companyCode;
        sellerCell.addElement(normalP(companyState));

        String companyEmail = CompanyProfile.getEmail();
        if (!safe(companyEmail).isBlank()) {
            sellerCell.addElement(normalP("E-Mail : " + safe(companyEmail)));
        }
        leftNested.addCell(sellerCell);

        // 2. Consignee Info (Ship To)
        PdfPCell consigneeCell = new PdfPCell();
        consigneeCell.setFixedHeight(leftCellHeight);
        consigneeCell.setBorder(Rectangle.BOTTOM);
        consigneeCell.setBorderWidth(0.6f);
        consigneeCell.setPaddingTop(3f);
        consigneeCell.setPaddingBottom(2f);
        consigneeCell.setPaddingLeft(3f);
        consigneeCell.setPaddingRight(3f);
        consigneeCell.addElement(p("Consignee (Ship to)", fontRegular9, 10f));
        Paragraph conNamePara = p(safeUpper(invoice.getConsigneeName()), fontBold9, 10f);
        conNamePara.setSpacingBefore(2.5f);
        consigneeCell.addElement(conNamePara);
        Paragraph consigneeAddrPara = p(safeUpper(invoice.getConsigneeAddress()), fontAddr, 8.5f);
        consigneeAddrPara.setSpacingBefore(2.5f);
        consigneeCell.addElement(consigneeAddrPara);

        String conGst = safe(invoice.getConsigneeGstin());
        consigneeCell.addElement(normalP("GSTIN/UIN : " + conGst));

        String conState = safe(invoice.getConsigneeStateName());
        String conStateCode = extractStateCode(conGst);
        if (conStateCode == null || conStateCode.isBlank()) {
            conStateCode = extractStateCode(conState);
        }
        if (conStateCode != null && !conStateCode.isBlank()) {
            String nameOnly = conState.replaceAll("\\s*\\(\\d{2}\\)\\s*", "").replaceAll(",\\s*Code\\s*:\\s*\\d{2}", "")
                    .trim();
            conState = "State Name : " + nameOnly + ", Code : " + conStateCode;
        } else {
            conState = "State Name : " + conState;
        }
        consigneeCell.addElement(normalP(conState));
        leftNested.addCell(consigneeCell);

        // 3. Buyer Info (Bill To)
        PdfPCell buyerCell = new PdfPCell();
        buyerCell.setFixedHeight(leftCellHeight);
        buyerCell.setBorder(Rectangle.BOTTOM);
        buyerCell.setBorderWidth(0.6f);
        buyerCell.setPaddingTop(3f);
        buyerCell.setPaddingBottom(2f);
        buyerCell.setPaddingLeft(3f);
        buyerCell.setPaddingRight(3f);
        buyerCell.addElement(p("Buyer (Bill to)", fontRegular9, 10f));
        Paragraph buyerNamePara = p(safeUpper(invoice.getClientName()), fontBold9, 10f);
        buyerNamePara.setSpacingBefore(2.5f);
        buyerCell.addElement(buyerNamePara);
        Paragraph buyerAddrPara = p(safeUpper(invoice.getBuyerAddress()), fontAddr, 8.5f);
        buyerAddrPara.setSpacingBefore(2.5f);
        buyerCell.addElement(buyerAddrPara);

        String buyerGst = safe(invoice.getBuyerGstin());
        buyerCell.addElement(normalP("GSTIN/UIN : " + buyerGst));

        String buyerState = safe(invoice.getBuyerStateName());
        String buyerStateCode = extractStateCode(buyerGst);
        if (buyerStateCode == null || buyerStateCode.isBlank()) {
            buyerStateCode = extractStateCode(buyerState);
        }
        if (buyerStateCode != null && !buyerStateCode.isBlank()) {
            String nameOnly = buyerState.replaceAll("\\s*\\(\\d{2}\\)\\s*", "")
                    .replaceAll(",\\s*Code\\s*:\\s*\\d{2}", "").trim();
            buyerState = "State Name : " + nameOnly + ", Code : " + buyerStateCode;
        } else {
            buyerState = "State Name : " + buyerState;
        }
        buyerCell.addElement(normalP(buyerState));
        leftNested.addCell(buyerCell);

        PdfPCell leftCell = new PdfPCell(leftNested);
        leftCell.setBorder(Rectangle.RIGHT);
        leftCell.setBorderWidth(0.6f);
        leftCell.setPadding(0);
        table.addCell(leftCell);

        // --- RIGHT COLUMN ---
        PdfPTable rightGrid = new PdfPTable(2);
        rightGrid.setWidthPercentage(100);

        java.util.List<String[]> metaPairs = new java.util.ArrayList<>();
        metaPairs.add(new String[]{"Invoice No.", safe(invoice.getInvoiceNo())});
        metaPairs.add(new String[]{"Dated", invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().format(INVOICE_DATE_FMT) : ""});

        if (invoice.getPlaceOfSupply() != null && !invoice.getPlaceOfSupply().isBlank()) {
            metaPairs.add(new String[]{"Place of Supply", invoice.getPlaceOfSupply()});
        }
        if (invoice.getPaymentTerms() != null && !invoice.getPaymentTerms().isBlank()) {
            metaPairs.add(new String[]{"Mode/Terms of Payment", invoice.getPaymentTerms()});
        }
        if (invoice.getDueDate() != null) {
            metaPairs.add(new String[]{"Due Date", invoice.getDueDate().format(INVOICE_DATE_FMT)});
        }
        if (invoice.getPoNo() != null && !invoice.getPoNo().isBlank()) {
            metaPairs.add(new String[]{"PO No.", invoice.getPoNo()});
            if (invoice.getPoDate() != null) {
                metaPairs.add(new String[]{"PO Date", invoice.getPoDate().format(INVOICE_DATE_FMT)});
            }
        }
        if (invoice.getVehicleDispatch() != null && !invoice.getVehicleDispatch().isBlank()) {
            metaPairs.add(new String[]{"Vehicle / Dispatch", invoice.getVehicleDispatch()});
        }
        if (invoice.getDispatchThrough() != null && !invoice.getDispatchThrough().isBlank()) {
            metaPairs.add(new String[]{"Dispatched through", invoice.getDispatchThrough()});
        }
        if (invoice.getLrTrackingNo() != null && !invoice.getLrTrackingNo().isBlank()) {
            metaPairs.add(new String[]{"Dispatch Doc No. (LR)", invoice.getLrTrackingNo()});
        }
        if (invoice.getEwayBillNo() != null && !invoice.getEwayBillNo().isBlank()) {
            metaPairs.add(new String[]{"E-Way Bill No.", invoice.getEwayBillNo()});
        }

        int count = 0;
        for (String[] pair : metaPairs) {
            int col = count % 2;
            addMetaCellWithHeight(rightGrid, pair[0], pair[1], col, 1, rightRowHeight);
            count++;
        }

        if (count % 2 != 0) {
            addMetaCellWithHeight(rightGrid, "", "", 1, 1, rightRowHeight);
        }

        if (invoice.getRemarks() != null && !invoice.getRemarks().isBlank()) {
            addMetaCellWithHeight(rightGrid, "Remarks / Terms of Delivery", invoice.getRemarks(), 0, 2, rightRowHeight * 2);
        }

        PdfPCell rightCell = new PdfPCell(rightGrid);
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setPadding(0);
        table.addCell(rightCell);

        PdfPCell container = new PdfPCell(table);
        container.setBorder(Rectangle.TOP | Rectangle.LEFT | Rectangle.RIGHT);
        container.setBorderWidth(0.6f);
        container.setPadding(0);
        return container;
    }

    private void addMetaCellWithHeight(PdfPTable table, String key, String value, int col, int colspan, float height) {
        PdfPCell cell = new PdfPCell();
        cell.setColspan(colspan);
        cell.setPaddingTop(1f);
        cell.setPaddingBottom(1f);
        cell.setPaddingLeft(2.5f);
        cell.setPaddingRight(2.5f);
        cell.setFixedHeight(height);
        cell.setBorderWidth(0.6f);

        if (colspan == 2) {
            cell.setBorder(Rectangle.BOTTOM);
        } else if (col == 0) {
            cell.setBorder(Rectangle.BOTTOM);
        } else {
            cell.setBorder(Rectangle.BOTTOM | Rectangle.LEFT);
        }

        Paragraph k = new Paragraph(key, fontSmall);
        k.setLeading(7.2f);
        k.setSpacingBefore(0f);
        k.setSpacingAfter(0f);

        Paragraph v = new Paragraph(value, fontBold);
        v.setLeading(8.2f);
        v.setSpacingBefore(0f);
        v.setSpacingAfter(0f);

        cell.addElement(k);
        cell.addElement(v);
        table.addCell(cell);
    }

    private PdfPCell createItemsTableCell(Invoice invoice) {

        // Better Tally-style proportions
        PdfPTable table = new PdfPTable(
                new float[] {
                        3f, // Sl
                        52f, // Description
                        8f, // HSN
                        10f, // Qty
                        9f, // Rate
                        5f, // Per
                        13f // Amount
                });

        table.setWidthPercentage(100);

        // =========================================================
        // HEADER ROW
        // =========================================================

        table.addCell(headerCell("Sl\nNo."));
        table.addCell(headerCell("Description of Goods"));
        table.addCell(headerCell("HSN/SAC"));
        table.addCell(headerCell("Quantity"));
        table.addCell(headerCell("Rate"));
        table.addCell(headerCell("per"));
        table.addCell(headerCell("Amount"));

        double taxable = 0.0;

        int sl = 1;

        int descLinesCount = 0;

        // =========================================================
        // JOB ROWS
        // =========================================================

        if (invoice.getJobs() != null) {

            for (InvoiceJob job : invoice.getJobs()) {

                double amt = job.getJobTotal();

                taxable += amt;

                boolean isCustomItem = job.getJobNo() == null || job.getJobNo().isBlank();

                // Sl No
                table.addCell(
                        bodyCell(
                                isCustomItem ? "" : String.valueOf(sl++),
                                Element.ALIGN_CENTER));

                // Description
                String descText = safe(job.getJobName());

                table.addCell(
                        bodyCellWithDesc(
                                descText,
                                isCustomItem ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT));

                descLinesCount += descText.split("\n").length;

                // HSN
                String hsnVal = safe(job.getHsnSac());
                if ("NA".equalsIgnoreCase(hsnVal) || "N/A".equalsIgnoreCase(hsnVal)) {
                    hsnVal = "";
                }
                table.addCell(
                        bodyCell(
                                hsnVal,
                                Element.ALIGN_CENTER));

                // Qty
                String unit = safe(job.getUnit());
                if ("NA".equalsIgnoreCase(unit) || "N/A".equalsIgnoreCase(unit)) {
                    unit = "";
                }

                long qty = job.getQuantity();

                table.addCell(
                        bodyCell(
                                qty > 0
                                        ? (unit.isEmpty() ? fmtQty3(qty) : (fmtQty3(qty) + " " + unit))
                                        : "",
                                Element.ALIGN_RIGHT));

                // Rate
                table.addCell(
                        bodyCell(
                                job.getRatePerUnit() > 0
                                        ? fmtMoney(job.getRatePerUnit())
                                        : "",
                                Element.ALIGN_RIGHT));

                // Per
                table.addCell(
                        bodyCell(
                                unit,
                                Element.ALIGN_CENTER));

                // Amount
                table.addCell(
                        bodyCell(
                                fmtMoney(amt),
                                Element.ALIGN_RIGHT));
            }
        }

        taxable = round2(taxable);

        // =========================================================
        // DYNAMIC SPACER
        // =========================================================

        // Reduced empty desert space
        float spacerHeight = 120f - (descLinesCount * 9f);

        if (spacerHeight < 40f) {

            spacerHeight = 40f;
        }

        for (int i = 0; i < 7; i++) {

            table.addCell(
                    spacerCell("", spacerHeight));
        }

        // =========================================================
        // GST TAX ROWS
        // =========================================================

        boolean intra = isIntraState(invoice);

        java.util.Map<Double, Double> taxableByRate = new java.util.LinkedHashMap<>();

        if (invoice.getJobs() != null) {

            for (InvoiceJob job : invoice.getJobs()) {

                double rate = job.getGstRate() > 0
                        ? job.getGstRate()
                        : 0.18;

                double amt = job.getJobTotal();

                taxableByRate.put(
                        rate,
                        taxableByRate.getOrDefault(rate, 0.0) + amt);
            }
        }

        double totalCgst = 0.0;
        double totalSgst = 0.0;
        double totalIgst = 0.0;

        for (java.util.Map.Entry<Double, Double> entry : taxableByRate.entrySet()) {

            double rate = entry.getKey();

            double amt = entry.getValue();

            if (intra) {

                double halfRate = rate / 2.0;

                double cgstVal = round2(amt * halfRate);

                double sgstVal = round2(amt * halfRate);

                totalCgst += cgstVal;
                totalSgst += sgstVal;

                String rateValStr = String.format("%.1f", halfRate * 100.0)
                        .replace(".0", "");

                addTaxRow(
                        table,
                        "C GST OUTPUT",
                        rateValStr,
                        "%",
                        cgstVal);

                addTaxRow(
                        table,
                        "S GST OUTPUT",
                        rateValStr,
                        "%",
                        sgstVal);

            } else {

                double igstVal = round2(amt * rate);

                totalIgst += igstVal;

                String rateValStr = String.format("%.1f", rate * 100.0)
                        .replace(".0", "");

                addTaxRow(
                        table,
                        "I GST OUTPUT",
                        rateValStr,
                        "%",
                        igstVal);
            }
        }

        // =========================================================
        // ROUND OFF
        // =========================================================

        double grandTotal = invoice.getGrandTotal();
        double roundOff = 0.0;

        if (invoice.getRoundOff() != null) {
            roundOff = invoice.getRoundOff();
            grandTotal = round2(taxable + totalCgst + totalSgst + totalIgst + roundOff);
        } else {
            if (grandTotal <= 0) {
                grandTotal = round2(
                        taxable
                                + totalCgst
                                + totalSgst
                                + totalIgst);
            }
            roundOff = grandTotal
                    - (taxable
                            + totalCgst
                            + totalSgst
                            + totalIgst);
            roundOff = round2(roundOff);
        }

        if (Math.abs(roundOff) > 0.001) {
            addRoundOffRow(table, roundOff);
        }

        // =========================================================
        // TOTAL ROW
        // =========================================================

        Paragraph totalPara = new Paragraph("Total", fontBold);

        totalPara.setLeading(8f);

        PdfPCell totalLabel = new PdfPCell(totalPara);

        totalLabel.setColspan(3);

        totalLabel.setHorizontalAlignment(
                Element.ALIGN_RIGHT);

        totalLabel.setVerticalAlignment(
                Element.ALIGN_MIDDLE);

        totalLabel.setPaddingTop(3f);
        totalLabel.setPaddingBottom(3f);
        totalLabel.setPaddingLeft(2f);
        totalLabel.setPaddingRight(2f);

        totalLabel.setBorder(
                Rectangle.TOP |
                        Rectangle.BOTTOM);

        totalLabel.setBorderWidth(0.7f);

        table.addCell(totalLabel);

        // =========================================================
        // TOTAL QTY
        // =========================================================

        long totalQty = 0;

        String commonUnit = "";

        if (invoice.getJobs() != null) {
            for (InvoiceJob job : invoice.getJobs()) {
                totalQty += job.getQuantity();
                String u = job.getUnit();
                if (u != null) {
                    if (commonUnit.isEmpty()) {
                        commonUnit = u;
                    } else if (!commonUnit.equals(u)) {
                        commonUnit = "MIXED";
                    }
                }
            }
        }

        if ("MIXED".equals(commonUnit)) {
            commonUnit = "";
        }

        String qtyText = totalQty > 0
                ? (commonUnit.isEmpty() ? fmtQty3(totalQty) : (fmtQty3(totalQty) + " " + commonUnit))
                : "";

        Paragraph qtyPara = new Paragraph(qtyText, fontBold);

        qtyPara.setLeading(8f);

        PdfPCell qtyCell = new PdfPCell(qtyPara);

        qtyCell.setHorizontalAlignment(
                Element.ALIGN_RIGHT);

        qtyCell.setVerticalAlignment(
                Element.ALIGN_MIDDLE);

        qtyCell.setPaddingTop(3f);
        qtyCell.setPaddingBottom(3f);
        qtyCell.setPaddingLeft(2f);
        qtyCell.setPaddingRight(2f);

        qtyCell.setBorder(
                Rectangle.TOP |
                        Rectangle.BOTTOM);

        qtyCell.setBorderWidth(0.7f);

        table.addCell(qtyCell);

        // =========================================================
        // EMPTY RATE CELL
        // =========================================================

        PdfPCell rateCell = new PdfPCell(
                new Paragraph("", fontBold));

        rateCell.setBorder(
                Rectangle.TOP |
                        Rectangle.BOTTOM);

        rateCell.setBorderWidth(0.7f);

        rateCell.setPaddingTop(3f);
        rateCell.setPaddingBottom(3f);

        table.addCell(rateCell);

        // =========================================================
        // EMPTY PER CELL
        // =========================================================

        PdfPCell perCell = new PdfPCell(
                new Paragraph("", fontBold));

        perCell.setBorder(
                Rectangle.TOP |
                        Rectangle.BOTTOM);

        perCell.setBorderWidth(0.7f);

        perCell.setPaddingTop(3f);
        perCell.setPaddingBottom(3f);

        table.addCell(perCell);

        // =========================================================
        // GRAND TOTAL
        // =========================================================

        Paragraph amtPara = new Paragraph(
                "Rs. " + fmtMoney(grandTotal),
                fontTotal);

        amtPara.setLeading(8f);

        PdfPCell amtCell = new PdfPCell(amtPara);

        amtCell.setHorizontalAlignment(
                Element.ALIGN_RIGHT);

        amtCell.setVerticalAlignment(
                Element.ALIGN_MIDDLE);

        amtCell.setPaddingTop(3f);
        amtCell.setPaddingBottom(3f);
        amtCell.setPaddingLeft(2f);
        amtCell.setPaddingRight(2f);

        amtCell.setBorder(
                Rectangle.TOP |
                        Rectangle.BOTTOM);

        amtCell.setBorderWidth(0.7f);

        table.addCell(amtCell);

        // =========================================================
        // OUTER CONTAINER
        // =========================================================

        PdfPCell container = new PdfPCell(table);

        container.setBorder(
                Rectangle.LEFT |
                        Rectangle.RIGHT |
                        Rectangle.BOTTOM);

        container.setBorderWidth(0.7f);

        container.setPadding(0);

        return container;
    }

    private PdfPCell createAmountInWordsCell(Invoice invoice) {

        PdfPCell cell = new PdfPCell();

        cell.setBorder(
                Rectangle.LEFT |
                        Rectangle.RIGHT |
                        Rectangle.BOTTOM);

        cell.setBorderWidth(0.6f);

        cell.setPaddingTop(2f);
        cell.setPaddingBottom(2f);
        cell.setPaddingLeft(3f);
        cell.setPaddingRight(3f);

        // =========================================================
        // TOP HEADER ROW
        // =========================================================

        PdfPTable topTable = new PdfPTable(
                new float[] { 80f, 20f });

        topTable.setWidthPercentage(100);

        Paragraph p1 = new Paragraph(
                "Amount Chargeable (in words)",
                fontSmall);

        p1.setLeading(7f);

        PdfPCell c1 = new PdfPCell(p1);

        c1.setBorder(Rectangle.NO_BORDER);

        c1.setHorizontalAlignment(
                Element.ALIGN_LEFT);

        c1.setPadding(0);

        Paragraph p2 = new Paragraph(
                "E. & O.E",
                fontSmall);

        p2.setLeading(7f);

        PdfPCell c2 = new PdfPCell(p2);

        c2.setBorder(Rectangle.NO_BORDER);

        c2.setHorizontalAlignment(
                Element.ALIGN_RIGHT);

        c2.setPadding(0);

        topTable.addCell(c1);
        topTable.addCell(c2);

        cell.addElement(topTable);

        // =========================================================
        // AMOUNT WORDS
        // =========================================================

        Paragraph amtWords = new Paragraph(
                NumberToWords.convertToIndianCurrency(
                        invoice.getGrandTotal()),
                fontBold);

        amtWords.setLeading(9f);
        amtWords.setSpacingBefore(1f);

        cell.addElement(amtWords);

        return cell;

    }

    private PdfPCell createHsnSummaryCell(Invoice invoice) {

        boolean intra = isIntraState(invoice);

        // =========================================================
        // GROUP HSN DATA
        // =========================================================

        java.util.Map<String, HsnGroup> groups = new java.util.LinkedHashMap<>();

        if (invoice.getJobs() != null) {

            for (InvoiceJob job : invoice.getJobs()) {

                String hsn = safe(job.getHsnSac());

                if ("NA".equalsIgnoreCase(hsn) || "N/A".equalsIgnoreCase(hsn)) {
                    continue;
                }

                if (hsn.isBlank() || "—".equals(hsn)) {

                    hsn = "4821";
                }

                double rate = job.getGstRate() > 0
                        ? job.getGstRate()
                        : 0.18;

                double taxableAmt = job.getJobTotal();

                HsnGroup group = groups.get(hsn);

                if (group == null) {

                    group = new HsnGroup(hsn, rate);

                    groups.put(hsn, group);
                }

                group.taxable += taxableAmt;
            }
        }

        PdfPTable table;

        // =========================================================
        // INTRA STATE
        // =========================================================

        if (intra) {

            table = new PdfPTable(
                    new float[] {
                            15f,
                            17f,
                            10f,
                            14f,
                            10f,
                            14f,
                            20f
                    });

            table.setWidthPercentage(100);

            // =====================================================
            // HEADER
            // =====================================================

            PdfPCell c1 = headerCell("HSN/SAC");
            c1.setRowspan(2);
            table.addCell(c1);

            PdfPCell c2 = headerCell("Taxable Value");
            c2.setRowspan(2);
            table.addCell(c2);

            PdfPCell cCgst = headerCell("Central Tax");

            cCgst.setColspan(2);

            table.addCell(cCgst);

            PdfPCell cSgst = headerCell("State Tax");

            cSgst.setColspan(2);

            table.addCell(cSgst);

            PdfPCell cTot = headerCell("Total Tax Amount");

            cTot.setRowspan(2);

            table.addCell(cTot);

            table.addCell(headerCell("Rate"));
            table.addCell(headerCell("Amount"));

            table.addCell(headerCell("Rate"));
            table.addCell(headerCell("Amount"));

            // =====================================================
            // BODY
            // =====================================================

            double totalTaxable = 0.0;

            double totalCgst = 0.0;

            double totalSgst = 0.0;

            for (HsnGroup g : groups.values()) {

                double taxableAmt = round2(g.taxable);

                double halfRate = g.rate / 2.0;

                double cgst = round2(
                        taxableAmt * halfRate);

                double sgst = round2(
                        taxableAmt * halfRate);

                double lineTax = round2(cgst + sgst);

                totalTaxable += taxableAmt;

                totalCgst += cgst;

                totalSgst += sgst;

                table.addCell(
                        hsnBodyCell(
                                g.hsnCode,
                                Element.ALIGN_CENTER));

                table.addCell(
                        hsnBodyCell(
                                fmtMoney(taxableAmt),
                                Element.ALIGN_RIGHT));

                table.addCell(
                        hsnBodyCell(
                                String.format(
                                        "%.1f%%",
                                        halfRate * 100.0).replace(".0%", "%"),
                                Element.ALIGN_CENTER));

                table.addCell(
                        hsnBodyCell(
                                fmtMoney(cgst),
                                Element.ALIGN_RIGHT));

                table.addCell(
                        hsnBodyCell(
                                String.format(
                                        "%.1f%%",
                                        halfRate * 100.0).replace(".0%", "%"),
                                Element.ALIGN_CENTER));

                table.addCell(
                        hsnBodyCell(
                                fmtMoney(sgst),
                                Element.ALIGN_RIGHT));

                table.addCell(
                        hsnBodyCell(
                                fmtMoney(lineTax),
                                Element.ALIGN_RIGHT));
            }

            // =====================================================
            // TOTAL ROW
            // =====================================================

            Paragraph totalPara = new Paragraph("Total", fontBold);

            totalPara.setLeading(8f);

            PdfPCell totalLbl = new PdfPCell(totalPara);

            totalLbl.setHorizontalAlignment(
                    Element.ALIGN_RIGHT);

            totalLbl.setVerticalAlignment(
                    Element.ALIGN_MIDDLE);

            totalLbl.setPaddingTop(3f);
            totalLbl.setPaddingBottom(3f);
            totalLbl.setPaddingLeft(2f);
            totalLbl.setPaddingRight(2f);

            totalLbl.setBorderWidth(0.7f);

            table.addCell(totalLbl);

            table.addCell(
                    hsnBodyCell(
                            fmtMoney(totalTaxable),
                            Element.ALIGN_RIGHT));

            table.addCell(
                    hsnBodyCell(
                            "",
                            Element.ALIGN_CENTER));

            table.addCell(
                    hsnBodyCell(
                            fmtMoney(totalCgst),
                            Element.ALIGN_RIGHT));

            table.addCell(
                    hsnBodyCell(
                            "",
                            Element.ALIGN_CENTER));

            table.addCell(
                    hsnBodyCell(
                            fmtMoney(totalSgst),
                            Element.ALIGN_RIGHT));

            table.addCell(
                    hsnBodyCell(
                            fmtMoney(totalCgst + totalSgst),
                            Element.ALIGN_RIGHT));

        } else {

            // =====================================================
            // INTER STATE
            // =====================================================

            table = new PdfPTable(
                    new float[] {
                            18f,
                            20f,
                            12f,
                            20f,
                            30f
                    });

            table.setWidthPercentage(100);

            PdfPCell c1 = headerCell("HSN/SAC");
            c1.setRowspan(2);
            table.addCell(c1);

            PdfPCell c2 = headerCell("Taxable Value");
            c2.setRowspan(2);
            table.addCell(c2);

            PdfPCell cGst = headerCell("IGST");
            cGst.setColspan(2);
            table.addCell(cGst);

            PdfPCell cTot = headerCell("Total Tax Amount");

            cTot.setRowspan(2);

            table.addCell(cTot);

            table.addCell(headerCell("Rate"));
            table.addCell(headerCell("Amount"));

            double totalTaxable = 0.0;

            double totalIgst = 0.0;

            for (HsnGroup g : groups.values()) {

                double taxableAmt = round2(g.taxable);

                double igst = round2(
                        taxableAmt * g.rate);

                totalTaxable += taxableAmt;

                totalIgst += igst;

                table.addCell(
                        hsnBodyCell(
                                g.hsnCode,
                                Element.ALIGN_CENTER));

                table.addCell(
                        hsnBodyCell(
                                fmtMoney(taxableAmt),
                                Element.ALIGN_RIGHT));

                table.addCell(
                        hsnBodyCell(
                                String.format(
                                        "%.1f%%",
                                        g.rate * 100.0).replace(".0%", "%"),
                                Element.ALIGN_CENTER));

                table.addCell(
                        hsnBodyCell(
                                fmtMoney(igst),
                                Element.ALIGN_RIGHT));

                table.addCell(
                        hsnBodyCell(
                                fmtMoney(igst),
                                Element.ALIGN_RIGHT));
            }

            Paragraph totalPara = new Paragraph("Total", fontBold);

            totalPara.setLeading(8f);

            PdfPCell totalLbl = new PdfPCell(totalPara);

            totalLbl.setHorizontalAlignment(
                    Element.ALIGN_RIGHT);

            totalLbl.setVerticalAlignment(
                    Element.ALIGN_MIDDLE);

            totalLbl.setPaddingTop(3f);
            totalLbl.setPaddingBottom(3f);
            totalLbl.setPaddingLeft(2f);
            totalLbl.setPaddingRight(2f);

            totalLbl.setBorderWidth(0.7f);

            table.addCell(totalLbl);

            table.addCell(
                    hsnBodyCell(
                            fmtMoney(totalTaxable),
                            Element.ALIGN_RIGHT));

            table.addCell(
                    hsnBodyCell(
                            "",
                            Element.ALIGN_CENTER));

            table.addCell(
                    hsnBodyCell(
                            fmtMoney(totalIgst),
                            Element.ALIGN_RIGHT));

            table.addCell(
                    hsnBodyCell(
                            fmtMoney(totalIgst),
                            Element.ALIGN_RIGHT));
        }

        // =========================================================
        // OUTER CONTAINER
        // =========================================================

        PdfPCell container = new PdfPCell(table);

        container.setBorder(
                Rectangle.LEFT |
                        Rectangle.RIGHT |
                        Rectangle.BOTTOM);

        container.setBorderWidth(0.7f);

        container.setPadding(0);

        return container;

    }

    private boolean isIntraState(Invoice invoice) {

        String companyGst = safe(CompanyProfile.getGst());

        String buyerGst = safe(invoice.getBuyerGstin());

        String companyCode = extractStateCode(companyGst);
        if (companyCode.isBlank()) {
            companyCode = "07";
        }

        String buyerCode = extractStateCode(buyerGst);
        if (buyerCode.isBlank()) {
            buyerCode = extractStateCode(invoice.getBuyerStateName());
        }

        if (companyCode.isBlank()
                || buyerCode.isBlank()) {

            return true;
        }

        return companyCode.equals(buyerCode);

    }

    private static String extractStateCode(String s) {
        if (s == null)
            return "";
        String trimmed = s.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\((\\d{2})\\)").matcher(trimmed);
        if (m.find())
            return m.group(1);
        m = java.util.regex.Pattern.compile("^(\\d{2})").matcher(trimmed);
        if (m.find())
            return m.group(1);
        return "";
    }

    private PdfPCell createTaxAmountInWordsCell(Invoice invoice) {

        double totalTax = 0.0;

        boolean intra = isIntraState(invoice);

        if (invoice.getJobs() != null) {

            for (InvoiceJob job : invoice.getJobs()) {

                double jobTaxable = job.getJobTotal();

                double rate = job.getGstRate() > 0
                        ? job.getGstRate()
                        : 0.18;

                if (intra) {

                    double halfRate = rate / 2.0;

                    double cgstVal = round2(
                            jobTaxable * halfRate);

                    double sgstVal = round2(
                            jobTaxable * halfRate);

                    totalTax += (cgstVal + sgstVal);

                } else {

                    double igstVal = round2(
                            jobTaxable * rate);

                    totalTax += igstVal;
                }
            }
        }

        totalTax = round2(totalTax);

        PdfPCell cell = new PdfPCell();

        cell.setBorder(
                Rectangle.LEFT |
                        Rectangle.RIGHT |
                        Rectangle.BOTTOM);

        cell.setBorderWidth(0.6f);

        cell.setPaddingTop(2f);
        cell.setPaddingBottom(2f);
        cell.setPaddingLeft(3f);
        cell.setPaddingRight(3f);

        Paragraph p = new Paragraph();

        p.setLeading(8f);

        p.add(
                new Chunk(
                        "Tax Amount (in words) : ",
                        fontSmall));

        String words = NumberToWords.convertToIndianCurrency(
                totalTax);

        if (words != null
                && !words.toUpperCase().startsWith("INR")) {

            words = "INR " + words;
        }

        p.add(
                new Chunk(
                        words,
                        fontBold));

        cell.addElement(p);

        return cell;

    }

    private PdfPCell createFooterSectionCell(Invoice invoice) {

        PdfPTable table = new PdfPTable(
                new float[] { 50f, 50f });

        table.setWidthPercentage(100);

        // =========================================================
        // LEFT SECTION
        // =========================================================

        PdfPCell left = new PdfPCell();

        left.setBorder(Rectangle.RIGHT);

        left.setBorderWidth(0.6f);

        left.setPaddingTop(2f);
        left.setPaddingBottom(2f);
        left.setPaddingLeft(4f);
        left.setPaddingRight(4f);

        Paragraph decHeader = p("Declaration", fontBold, 8f);

        left.addElement(decHeader);

        Paragraph tcHeader = p(
                "TERMS & CONDITIONS.",
                fontHeader,
                7f);

        tcHeader.setSpacingBefore(1f);

        left.addElement(tcHeader);

        left.addElement(
                p("E. & O.E", fontSmall, 6.8f));

        left.addElement(
                p(
                        "GOODS ONCE SOLD WILL NOT BE TAKEN BACK",
                        fontSmall,
                        6.8f));

        left.addElement(
                p(
                        "INTEREST @24% P.A WILL BE CHARGED IF THE",
                        fontSmall,
                        6.8f));

        left.addElement(
                p(
                        "AMOUNT IS NOT PAID ON DEMAND",
                        fontSmall,
                        6.8f));

        left.addElement(
                p(
                        "ALL DISPUTES SUBJECT TO DELHI JURISDICTION",
                        fontSmall,
                        6.8f));

        left.addElement(
                p(
                        "ONLY AFTER PRINTING THE PLATES WILL NOT BE",
                        fontSmall,
                        6.8f));

        left.addElement(
                p(
                        "RETURNED",
                        fontSmall,
                        6.8f));

        table.addCell(left);

        // =========================================================
        // RIGHT SECTION
        // =========================================================

        PdfPTable rightNested = new PdfPTable(1);

        rightNested.setWidthPercentage(100);

        // =====================================================
        // BANK DETAILS
        // =====================================================

        PdfPCell upperRight = new PdfPCell();

        upperRight.setBorder(Rectangle.NO_BORDER);

        upperRight.setPaddingTop(3f);
        upperRight.setPaddingBottom(3f);
        upperRight.setPaddingLeft(4f);
        upperRight.setPaddingRight(4f);

        Paragraph bankHeader = p(
                "Company's Bank Details",
                fontBold,
                8f);

        upperRight.addElement(bankHeader);

        PdfPTable bankTable = new PdfPTable(
                new float[] { 1.4f, 2.6f });

        bankTable.setWidthPercentage(100);

        bankTable.setSpacingBefore(2f);

        String bankHolder = safeUpper(
                CompanyProfile.getName());

        String bankName = "INDIAN OVERSEAS BANK";

        String bankAc = "15980200000000780";

        String bankIfsc = "PITAMPURA & IOBA0001688";

        try {

            BankDetails defaultBank = invoice.getBankDetails();
            if (defaultBank == null) {
                defaultBank = new service.BankDetailsService().getDefault();
            }

            if (defaultBank != null) {

                if (defaultBank.getAccountHolderName() != null
                        && !defaultBank.getAccountHolderName().isBlank()) {

                    bankHolder = defaultBank
                            .getAccountHolderName()
                            .toUpperCase();
                }

                if (defaultBank.getBankName() != null
                        && !defaultBank.getBankName().isBlank()) {

                    bankName = defaultBank
                            .getBankName()
                            .toUpperCase();
                }

                if (defaultBank.getAccountNo() != null
                        && !defaultBank.getAccountNo().isBlank()) {

                    bankAc = defaultBank.getAccountNo();
                }

                String branchIfsc = defaultBank.getBranchIfsc();

                if (branchIfsc != null
                        && !branchIfsc.isBlank()) {

                    bankIfsc = branchIfsc.toUpperCase();
                }
            }

        } catch (Exception e) {

            // ignore
        }

        addBankDetailRow(
                bankTable,
                "A/c Holder's Name",
                ": " + bankHolder);

        addBankDetailRow(
                bankTable,
                "Bank Name",
                ": " + bankName);

        addBankDetailRow(
                bankTable,
                "A/c No.",
                ": " + bankAc);

        addBankDetailRow(
                bankTable,
                "Branch & IFS Code",
                ": " + bankIfsc);

        upperRight.addElement(bankTable);

        rightNested.addCell(upperRight);

        // =====================================================
        // SIGNATURE AREA
        // =====================================================

        PdfPCell lowerRight = new PdfPCell();

        lowerRight.setBorder(Rectangle.TOP);

        lowerRight.setBorderWidth(0.6f);

        lowerRight.setPaddingTop(4f);
        lowerRight.setPaddingBottom(4f);
        lowerRight.setPaddingLeft(4f);
        lowerRight.setPaddingRight(4f);

        Paragraph forCompany = p(
                "for "
                        + safeUpper(
                                CompanyProfile.getName()),
                fontBold,
                8f);

        forCompany.setAlignment(
                Element.ALIGN_RIGHT);

        // Leave 30 points space for stamp / signature
        forCompany.setSpacingAfter(30f);

        lowerRight.addElement(forCompany);

        Paragraph auth = p(
                "Authorised Signatory",
                fontNormal,
                8f);

        auth.setAlignment(
                Element.ALIGN_RIGHT);

        lowerRight.addElement(auth);

        rightNested.addCell(lowerRight);

        PdfPCell right = new PdfPCell(rightNested);

        right.setBorder(Rectangle.NO_BORDER);

        right.setPadding(0);

        table.addCell(right);

        // =========================================================
        // OUTER CONTAINER
        // =========================================================

        PdfPCell container = new PdfPCell(table);

        container.setBorder(
                Rectangle.LEFT |
                        Rectangle.RIGHT |
                        Rectangle.BOTTOM);

        container.setBorderWidth(0.7f);

        container.setPadding(0);

        return container;

    }

    private void addBankDetailRow(
            PdfPTable table,
            String key,
            String value) {

        PdfPCell cellKey = new PdfPCell(
                p(key, fontSmall, 6.8f));

        cellKey.setBorder(Rectangle.NO_BORDER);

        cellKey.setPaddingTop(1f);
        cellKey.setPaddingBottom(1f);
        cellKey.setPaddingLeft(0);
        cellKey.setPaddingRight(2f);

        PdfPCell cellVal = new PdfPCell(
                p(value, fontBold, 7.2f));

        cellVal.setBorder(Rectangle.NO_BORDER);

        cellVal.setPaddingTop(1f);
        cellVal.setPaddingBottom(1f);
        cellVal.setPaddingLeft(0);
        cellVal.setPaddingRight(0);

        table.addCell(cellKey);

        table.addCell(cellVal);

    }

    private PdfPCell headerCell(String text) {

        Paragraph p = new Paragraph(text, fontHeader);

        // Compact industrial density
        p.setLeading(7.5f);

        p.setSpacingBefore(0f);
        p.setSpacingAfter(0f);

        PdfPCell cell = new PdfPCell(p);

        cell.setHorizontalAlignment(
                Element.ALIGN_CENTER);

        cell.setVerticalAlignment(
                Element.ALIGN_MIDDLE);

        cell.setPaddingTop(3.5f);
        cell.setPaddingBottom(3.5f);
        cell.setPaddingLeft(1f);
        cell.setPaddingRight(1f);
        cell.setMinimumHeight(22f);
        // Stronger invoice borders
        cell.setBorderWidth(0.6f);
        cell.setUseAscender(true);
        cell.setUseDescender(true);
        return cell;

    }

    private PdfPCell bodyCell(
            String text,
            int align) {

        Paragraph p = new Paragraph(text, fontNormal);

        p.setLeading(8.2f);

        p.setSpacingBefore(0f);
        p.setSpacingAfter(0f);

        PdfPCell cell = new PdfPCell(p);

        cell.setHorizontalAlignment(align);

        cell.setVerticalAlignment(Element.ALIGN_TOP);

        cell.setPaddingTop(3f);
        cell.setPaddingBottom(3f);
        cell.setPaddingLeft(1.5f);
        cell.setPaddingRight(1.5f);

        cell.setBorder(
                Rectangle.LEFT |
                        Rectangle.RIGHT);

        cell.setBorderWidth(0.6f);

        return cell;

    }

    private PdfPCell bodyCellWithDesc(
            String text,
            int align) {

        PdfPCell cell = new PdfPCell();

        cell.setHorizontalAlignment(align);

        cell.setVerticalAlignment(Element.ALIGN_TOP);

        cell.setBorder(
                Rectangle.LEFT |
                        Rectangle.RIGHT);

        cell.setBorderWidth(0.6f);

        // Compact description spacing
        cell.setPaddingTop(3f);
        cell.setPaddingBottom(3f);
        cell.setPaddingLeft(2f);
        cell.setPaddingRight(2f);

        String[] lines = text.split("\n");

        for (int i = 0; i < lines.length; i++) {

            String line = lines[i];

            Paragraph paragraph;

            // =====================================================
            // FIRST LINE = MAIN JOB TITLE
            // =====================================================

            if (i == 0) {

                paragraph = p(
                        line,
                        fontBold,
                        8.5f);

            }
            // =====================================================
            // COMPLETE- ROWS
            // =====================================================
            else if (line.trim().startsWith("COMPLETE-")) {

                paragraph = p(
                        line,
                        fontNormal,
                        8.3f);

            }
            // =====================================================
            // DETAIL ROWS
            // =====================================================
            else {

                paragraph = p(
                        line,
                        fontNormal,
                        8.3f);
            }

            paragraph.setSpacingBefore(0f);
            paragraph.setSpacingAfter(0f);
            paragraph.setAlignment(align);

            cell.addElement(paragraph);
        }

        return cell;

    }

    private PdfPCell spacerCell(
            String text,
            float height) {

        Paragraph p = new Paragraph(text, fontNormal);

        p.setLeading(8f);

        PdfPCell cell = new PdfPCell(p);

        cell.setFixedHeight(height);

        cell.setBorder(
                Rectangle.LEFT |
                        Rectangle.RIGHT);

        cell.setBorderWidth(0.6f);

        cell.setPadding(0);

        return cell;

    }

    private void addTaxRow(
            PdfPTable table,
            String label,
            String rateVal,
            String rateUnit,
            double amount) {

        table.addCell(
                bodyCell(
                        "",
                        Element.ALIGN_CENTER));

        // =====================================================
        // TAX LABEL
        // =====================================================

        Paragraph taxPara = new Paragraph(label, fontBold);

        taxPara.setLeading(8f);

        PdfPCell taxCell = new PdfPCell(taxPara);

        taxCell.setHorizontalAlignment(
                Element.ALIGN_RIGHT);

        taxCell.setVerticalAlignment(
                Element.ALIGN_MIDDLE);

        taxCell.setPaddingTop(3f);
        taxCell.setPaddingBottom(3f);
        taxCell.setPaddingLeft(1.5f);
        taxCell.setPaddingRight(1.5f);

        taxCell.setBorder(
                Rectangle.LEFT |
                        Rectangle.RIGHT);

        taxCell.setBorderWidth(0.6f);

        table.addCell(taxCell);

        table.addCell(
                bodyCell(
                        "",
                        Element.ALIGN_CENTER));

        table.addCell(
                bodyCell(
                        "",
                        Element.ALIGN_RIGHT));

        table.addCell(
                bodyCell(
                        rateVal,
                        Element.ALIGN_RIGHT));

        table.addCell(
                bodyCell(
                        rateUnit,
                        Element.ALIGN_CENTER));

        table.addCell(
                bodyCell(
                        fmtMoney(amount),
                        Element.ALIGN_RIGHT));

    }

    private void addRoundOffRow(
            PdfPTable table,
            double roundOff) {

        table.addCell(
                bodyCell(
                        "",
                        Element.ALIGN_CENTER));

        // =====================================================
        // ROUND OFF DESCRIPTION
        // =====================================================

        PdfPTable descTable = new PdfPTable(
                new float[] { 50f, 50f });

        descTable.setWidthPercentage(100);

        PdfPCell cLeft = new PdfPCell(
                new Paragraph(
                        "Less :",
                        fontNormal));

        cLeft.setBorder(Rectangle.NO_BORDER);

        cLeft.setHorizontalAlignment(
                Element.ALIGN_LEFT);

        cLeft.setPadding(0);

        PdfPCell cRight = new PdfPCell(
                new Paragraph(
                        "ROUND OFF",
                        fontNormal));

        cRight.setBorder(Rectangle.NO_BORDER);

        cRight.setHorizontalAlignment(
                Element.ALIGN_RIGHT);

        cRight.setPadding(0);

        descTable.addCell(cLeft);
        descTable.addCell(cRight);

        PdfPCell descCell = new PdfPCell(descTable);

        descCell.setBorder(
                Rectangle.LEFT |
                        Rectangle.RIGHT);

        descCell.setBorderWidth(0.6f);

        descCell.setPaddingTop(3f);
        descCell.setPaddingBottom(3f);
        descCell.setPaddingLeft(1.5f);
        descCell.setPaddingRight(1.5f);

        table.addCell(descCell);

        table.addCell(
                bodyCell(
                        "",
                        Element.ALIGN_CENTER));

        table.addCell(
                bodyCell(
                        "",
                        Element.ALIGN_RIGHT));

        table.addCell(
                bodyCell(
                        "",
                        Element.ALIGN_CENTER));

        table.addCell(
                bodyCell(
                        "",
                        Element.ALIGN_CENTER));

        String amountText = (roundOff < 0)
                ? "(-)" + fmtMoney(Math.abs(roundOff))
                : fmtMoney(roundOff);

        table.addCell(
                bodyCell(
                        amountText,
                        Element.ALIGN_RIGHT));

    }

    private static String getStateNameFromCode(String code) {
        if (code == null)
            return "Unknown";
        return switch (code.trim()) {
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
    }

    private static class HsnGroup {

        String hsnCode;

        double rate;

        double taxable;

        HsnGroup(String hsnCode, double rate) {

            this.hsnCode = hsnCode;

            this.rate = rate;

            this.taxable = 0.0;
        }

    }

    private PdfPCell hsnBodyCell(
            String text,
            int align) {

        Paragraph p = new Paragraph(text, fontNormal);

        // Compact industrial ERP density
        p.setLeading(8f);

        p.setSpacingBefore(0f);
        p.setSpacingAfter(0f);

        PdfPCell cell = new PdfPCell(p);

        cell.setHorizontalAlignment(align);

        cell.setVerticalAlignment(
                Element.ALIGN_MIDDLE);

        // Tally-style sharp borders
        cell.setBorder(Rectangle.BOX);

        cell.setBorderWidth(0.6f);

        // Compact padding
        cell.setPaddingTop(3f);
        cell.setPaddingBottom(3f);
        cell.setPaddingLeft(1.8f);
        cell.setPaddingRight(1.8f);

        return cell;

    }
}
