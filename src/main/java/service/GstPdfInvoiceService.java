package service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import model.Invoice;
import model.InvoiceJob;
import model.InvoiceLine;
import utils.CompanyProfile;
import utils.NumberToWords;

import java.io.File;
import java.io.FileOutputStream;
import java.time.format.DateTimeFormatter;

public class GstPdfInvoiceService {

    private final Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private final Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
    private final Font fontNormal = FontFactory.getFont(FontFactory.HELVETICA, 8);
    private final Font fontSmall = FontFactory.getFont(FontFactory.HELVETICA, 7);
    private final Font fontBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);

    public File generateGstInvoice(Invoice invoice) throws Exception {
        File file = InvoiceStorageService.createPdfFile(invoice);
        Document document = new Document(PageSize.A4, 15, 15, 15, 15);
        PdfWriter.getInstance(document, new FileOutputStream(file));
        document.open();

        // Main Wrapper Table (1 column)
        PdfPTable mainTable = new PdfPTable(1);
        mainTable.setWidthPercentage(100);

        // 1. Tax Invoice Title
        mainTable.addCell(createTitleCell());

        // 2. Seller and Invoice Info Section
        mainTable.addCell(createSellerAndInvoiceInfoCell(invoice));

        // 3. Ship To and Bill To Section
        mainTable.addCell(createAddressSectionCell(invoice));

        // 4. Items Table Section
        mainTable.addCell(createItemsTableCell(invoice));

        // 5. Amount in Words Section
        mainTable.addCell(createAmountInWordsCell(invoice));

        // 6. HSN Summary Section
        mainTable.addCell(createHsnSummaryCell(invoice));

        // 7. Footer (Bank Details & Signatory) Section
        mainTable.addCell(createFooterSectionCell(invoice));

        document.add(mainTable);
        Paragraph computerGen = new Paragraph("This is a Computer Generated Invoice", fontSmall);
        computerGen.setAlignment(Element.ALIGN_CENTER);
        document.add(computerGen);
        document.close();
        return file;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String safeUpper(String s) {
        return safe(s).toUpperCase();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String fmtMoney(double v) {
        return String.format("%.2f", v);
    }

    private static String fmtQty3(long v) {
        return String.format("%.3f", (double) v);
    }

    private static final DateTimeFormatter INVOICE_DATE_FMT = DateTimeFormatter.ofPattern("d-MMM-yy");

    private PdfPCell createTitleCell() {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        
        PdfPCell c1 = new PdfPCell(new Paragraph("Tax Invoice", fontTitle));
        c1.setBorder(Rectangle.NO_BORDER);
        c1.setHorizontalAlignment(Element.ALIGN_CENTER);
        
        PdfPCell c2 = new PdfPCell(new Paragraph("(ORIGINAL FOR RECIPIENT)", fontHeader));
        c2.setBorder(Rectangle.NO_BORDER);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        table.addCell(c1);
        table.addCell(c2);
        
        PdfPCell cell = new PdfPCell(table);
        cell.setPadding(2);
        cell.setBorder(Rectangle.TOP | Rectangle.LEFT | Rectangle.RIGHT);
        return cell;
    }

    private PdfPCell createSellerAndInvoiceInfoCell(Invoice invoice) {
        PdfPTable table = new PdfPTable(new float[]{1, 1});
        table.setWidthPercentage(100);

        // Seller Info
        PdfPCell sellerCell = new PdfPCell();
        sellerCell.addElement(new Paragraph(safeUpper(CompanyProfile.getName()), fontBold));
        sellerCell.addElement(new Paragraph(safe(CompanyProfile.getAddress()), fontNormal));
        if (!safe(CompanyProfile.getGst()).isBlank()) {
            sellerCell.addElement(new Paragraph("GSTIN/UIN: " + safe(CompanyProfile.getGst()), fontNormal));
        }
        if (!safe(CompanyProfile.getEmail()).isBlank()) {
            sellerCell.addElement(new Paragraph("E-Mail: " + safe(CompanyProfile.getEmail()), fontNormal));
        }
        sellerCell.setBorder(Rectangle.BOX);
        table.addCell(sellerCell);

        // Invoice Info
        PdfPTable infoGrid = new PdfPTable(2);
        infoGrid.setWidthPercentage(100);

        addKvCell(infoGrid, "Invoice No.", safe(invoice.getInvoiceNo()));
        addKvCell(infoGrid, "Dated", invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().format(INVOICE_DATE_FMT) : "");
        addKvCell(infoGrid, "Delivery Note", "");
        addKvCell(infoGrid, "Mode/Terms of Payment", "");
        addKvCell(infoGrid, "Reference No. & Date", "");
        addKvCell(infoGrid, "Other References", "");
        addKvCell(infoGrid, "Buyer's Order No.", "");
        addKvCell(infoGrid, "Dated", "");
        addKvCell(infoGrid, "Dispatch Doc No.", "");
        addKvCell(infoGrid, "Delivery Note Date", "");
        addKvCell(infoGrid, "Dispatched through", "");
        addKvCell(infoGrid, "Destination", "");
        addKvCell(infoGrid, "Terms of Delivery", "");
        addKvCell(infoGrid, "", "");
        
        PdfPCell infoCell = new PdfPCell(infoGrid);
        infoCell.setBorder(Rectangle.BOX);
        table.addCell(infoCell);

        PdfPCell container = new PdfPCell(table);
        container.setBorder(Rectangle.LEFT | Rectangle.RIGHT);
        container.setPadding(0);
        return container;
    }

    private PdfPCell createAddressSectionCell(Invoice invoice) {
        PdfPTable table = new PdfPTable(new float[]{1, 1});
        table.setWidthPercentage(100);

        // Consignee (Ship To)
        PdfPCell shipTo = new PdfPCell();
        shipTo.addElement(new Paragraph("Consignee (Ship to)", fontSmall));
        shipTo.addElement(new Paragraph(safeUpper(invoice.getConsigneeName()), fontBold));
        shipTo.addElement(new Paragraph(safe(invoice.getConsigneeAddress()), fontNormal));
        shipTo.addElement(new Paragraph("GSTIN/UIN: " + safe(invoice.getConsigneeGstin()), fontNormal));
        shipTo.addElement(new Paragraph("State Name: " + safe(invoice.getConsigneeStateName()), fontNormal));
        shipTo.setBorder(Rectangle.BOX);
        table.addCell(shipTo);

        // Buyer (Bill To)
        PdfPCell billTo = new PdfPCell();
        billTo.addElement(new Paragraph("Buyer (Bill to)", fontSmall));
        billTo.addElement(new Paragraph(safeUpper(invoice.getClientName()), fontBold));
        billTo.addElement(new Paragraph(safe(invoice.getBuyerAddress()), fontNormal));
        billTo.addElement(new Paragraph("GSTIN/UIN: " + safe(invoice.getBuyerGstin()), fontNormal));
        billTo.addElement(new Paragraph("State Name: " + safe(invoice.getBuyerStateName()), fontNormal));
        billTo.setBorder(Rectangle.BOX);
        table.addCell(billTo);

        PdfPCell container = new PdfPCell(table);
        container.setBorder(Rectangle.LEFT | Rectangle.RIGHT);
        container.setPadding(0);
        return container;
    }

    private PdfPCell createItemsTableCell(Invoice invoice) {
        PdfPTable table = new PdfPTable(new float[]{3, 30, 8, 8, 8, 5, 12});
        table.setWidthPercentage(100);

        // Headers
        table.addCell(headerCell("sl No."));
        table.addCell(headerCell("Description of Goods"));
        table.addCell(headerCell("HSN/SAC"));
        table.addCell(headerCell("Quantity"));
        table.addCell(headerCell("Rate"));
        table.addCell(headerCell("per"));
        table.addCell(headerCell("Amount"));

        double taxable = 0.0;
        int sl = 1;
        if (invoice.getJobs() != null) {
            for (InvoiceJob job : invoice.getJobs()) {
                // If a job has multiple lines, print each line as a row. Otherwise print job as row.
                if (job.getLines() != null && !job.getLines().isEmpty()) {
                    for (InvoiceLine line : job.getLines()) {
                        taxable += line.getAmount();
                        table.addCell(bodyCell(String.valueOf(sl++), Element.ALIGN_CENTER));
                        table.addCell(bodyCell(safe(line.getDescription()), Element.ALIGN_LEFT));
                        table.addCell(bodyCell(safe(job.getHsnSac()), Element.ALIGN_CENTER));
                        String unit = safe(job.getUnit());
                        long qty = job.getQuantity();
                        table.addCell(bodyCell(qty > 0 ? (fmtQty3(qty) + " " + unit) : "", Element.ALIGN_RIGHT));
                        table.addCell(bodyCell(job.getRatePerUnit() > 0 ? fmtMoney(job.getRatePerUnit()) : "", Element.ALIGN_RIGHT));
                        table.addCell(bodyCell(unit, Element.ALIGN_CENTER));
                        table.addCell(bodyCell(fmtMoney(line.getAmount()), Element.ALIGN_RIGHT));
                    }
                } else {
                    double amt = job.getJobTotal();
                    taxable += amt;
                    table.addCell(bodyCell(String.valueOf(sl++), Element.ALIGN_CENTER));
                    table.addCell(bodyCell("(" + safe(job.getJobNo()) + ") " + safe(job.getJobName()), Element.ALIGN_LEFT));
                    table.addCell(bodyCell(safe(job.getHsnSac()), Element.ALIGN_CENTER));
                    String unit = safe(job.getUnit());
                    long qty = job.getQuantity();
                    table.addCell(bodyCell(qty > 0 ? (fmtQty3(qty) + " " + unit) : "", Element.ALIGN_RIGHT));
                    table.addCell(bodyCell(job.getRatePerUnit() > 0 ? fmtMoney(job.getRatePerUnit()) : "", Element.ALIGN_RIGHT));
                    table.addCell(bodyCell(unit, Element.ALIGN_CENTER));
                    table.addCell(bodyCell(fmtMoney(amt), Element.ALIGN_RIGHT));
                }
            }
        }

        taxable = round2(taxable);

        // Screenshot parity: show IGST output row with rate in Rate column.
        double gstRate = 0.18;
        double igst = round2(taxable * gstRate);

        addTaxRow(table, "IGST OUTPUT", "18 %", igst);

        double grandTotal = invoice.getGrandTotal();
        if (grandTotal <= 0) {
            grandTotal = round2(taxable + igst);
        }

        // Total Row
        PdfPCell totalLabel = new PdfPCell(new Paragraph("Total", fontBold));
        totalLabel.setColspan(6);
        totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalLabel.setPadding(3);
        table.addCell(totalLabel);
        table.addCell(new PdfPCell(new Paragraph("₹ " + fmtMoney(grandTotal), fontBold)) {{
            setHorizontalAlignment(Element.ALIGN_RIGHT);
            setPadding(3);
        }});

        PdfPCell container = new PdfPCell(table);
        container.setBorder(Rectangle.LEFT | Rectangle.RIGHT | Rectangle.BOTTOM);
        container.setPadding(0);
        return container;
    }

    private PdfPCell createAmountInWordsCell(Invoice invoice) {
        PdfPCell cell = new PdfPCell();
        cell.addElement(new Paragraph("Amount Chargeable (in words)", fontSmall));
        cell.addElement(new Paragraph(NumberToWords.convertToIndianCurrency(invoice.getGrandTotal()), fontBold));
        Paragraph eoe = new Paragraph("E. & O. E", fontSmall);
        eoe.setAlignment(Element.ALIGN_RIGHT);
        cell.addElement(eoe);
        cell.setBorder(Rectangle.LEFT | Rectangle.RIGHT | Rectangle.BOTTOM);
        cell.setPadding(4);
        return cell;
    }

    private PdfPCell createHsnSummaryCell(Invoice invoice) {
        // Match screenshot: IGST-only summary layout
        PdfPTable table = new PdfPTable(new float[]{15, 15, 10, 10, 15});
        table.setWidthPercentage(100);
        
        table.addCell(headerCell("HSN/SAC"));
        table.addCell(headerCell("Taxable Value"));
        table.addCell(headerCell("IGST Rate"));
        table.addCell(headerCell("Amount"));
        table.addCell(headerCell("Total Tax Amount"));

        // Compute from invoice totals if available
        double taxable = 0.0;
        if (invoice.getJobs() != null) {
            taxable = invoice.getJobs().stream().mapToDouble(InvoiceJob::getJobTotal).sum();
        }
        taxable = round2(taxable);
        double igst = round2(taxable * 0.18);

        table.addCell(bodyCell("", Element.ALIGN_CENTER));
        table.addCell(bodyCell(fmtMoney(taxable), Element.ALIGN_RIGHT));
        table.addCell(bodyCell("18%", Element.ALIGN_CENTER));
        table.addCell(bodyCell(fmtMoney(igst), Element.ALIGN_RIGHT));
        table.addCell(bodyCell(fmtMoney(igst), Element.ALIGN_RIGHT));

        PdfPCell total = new PdfPCell(new Paragraph("Total", fontBold));
        total.setHorizontalAlignment(Element.ALIGN_RIGHT);
        total.setColspan(4);
        total.setPadding(3);
        table.addCell(total);
        table.addCell(bodyCell(fmtMoney(igst), Element.ALIGN_RIGHT));

        PdfPCell container = new PdfPCell(table);
        container.setBorder(Rectangle.LEFT | Rectangle.RIGHT | Rectangle.BOTTOM);
        container.setPadding(0);
        return container;
    }

    private PdfPCell createFooterSectionCell(Invoice invoice) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.addElement(new Paragraph("Declaration", fontSmall));
        left.addElement(new Paragraph("Terms & Conditions", fontSmall));
        left.addElement(new Paragraph("E. & O. E", fontSmall));
        left.addElement(new Paragraph("GOODS ONCE SOLD WILL NOT BE TAKEN BACK", fontSmall));
        left.addElement(new Paragraph("INTEREST @24% P.A WILL BE CHARGED IF THE", fontSmall));
        left.addElement(new Paragraph("AMOUNT IS NOT PAID ON DEMAND", fontSmall));
        left.addElement(new Paragraph("ALL DISPUTES SUBJECT TO DELHI JURISDICTION", fontSmall));
        left.addElement(new Paragraph("ONLY AFTER PRINTING THE PLATES WILL NOT BE", fontSmall));
        left.addElement(new Paragraph("RETURNED", fontSmall));
        table.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.addElement(new Paragraph("Company's Bank Details", fontSmall));
        right.addElement(new Paragraph("A/c Holder's Name : " + safeUpper(CompanyProfile.getName()), fontSmall));
        right.addElement(new Paragraph("Bank Name : INDIAN OVERSEAS BANK", fontSmall));
        right.addElement(new Paragraph("A/c No. : 15980200000000780", fontSmall));
        right.addElement(new Paragraph("Branch & IFS Code : PITAMPURA & IOBA0001598", fontSmall));
        right.addElement(new Paragraph("\nfor " + safeUpper(CompanyProfile.getName()), fontSmall));
        Paragraph auth = new Paragraph("\n\nAuthorised Signatory", fontSmall);
        auth.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(auth);
        table.addCell(right);

        PdfPCell container = new PdfPCell(table);
        container.setBorder(Rectangle.LEFT | Rectangle.RIGHT | Rectangle.BOTTOM);
        container.setPadding(4);
        return container;
    }

    private void addKvCell(PdfPTable table, String key, String value) {
        PdfPCell k = new PdfPCell(new Paragraph(key, fontSmall));
        k.setBorder(Rectangle.BOX);
        PdfPCell v = new PdfPCell(new Paragraph(value, fontBold));
        v.setBorder(Rectangle.BOX);
        table.addCell(k);
        table.addCell(v);
    }

    private PdfPCell headerCell(String text) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, fontHeader));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBackgroundColor(java.awt.Color.WHITE);
        cell.setPadding(4);
        return cell;
    }

    private PdfPCell bodyCell(String text, int align) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, fontNormal));
        cell.setHorizontalAlignment(align);
        cell.setBorder(Rectangle.LEFT | Rectangle.RIGHT);
        cell.setPadding(3);
        return cell;
    }

    private void addTaxRow(PdfPTable table, String label, String rateText, double amount) {
        table.addCell(bodyCell("", Element.ALIGN_CENTER));                 // sl
        table.addCell(bodyCell(label, Element.ALIGN_RIGHT));              // description
        table.addCell(bodyCell("", Element.ALIGN_CENTER));                // hsn
        table.addCell(bodyCell("", Element.ALIGN_RIGHT));                 // qty
        table.addCell(bodyCell(rateText, Element.ALIGN_CENTER));          // rate column used for %
        table.addCell(bodyCell("", Element.ALIGN_CENTER));                // per
        table.addCell(bodyCell(fmtMoney(amount), Element.ALIGN_RIGHT));   // amount
    }
}
