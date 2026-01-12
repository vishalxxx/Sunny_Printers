package utils;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Centralized style factory for Invoice Excel generation. Immutable, reusable,
 * and safe.
 */
public final class InvoiceExcelStyles {

	public final CellStyle headerTitle;
	public final CellStyle headerText;
	public final CellStyle clientText;
	public final CellStyle descText;
	public final CellStyle jobDesc;
	public final CellStyle amount;
	public final CellStyle totalAmount;
	public final CellStyle totalLabel;
	public final CellStyle Date;
	public final CellStyle serial;
	public final CellStyle headerDate;

	public InvoiceExcelStyles(Workbook wb) {

		headerTitle = buildStyle(wb, HorizontalAlignment.LEFT, VerticalAlignment.CENTER, true, false, true, null,16 );
		headerText = buildStyle(wb, HorizontalAlignment.LEFT, VerticalAlignment.CENTER, false, false, true, null,12);
		clientText = buildStyle(wb, HorizontalAlignment.RIGHT, VerticalAlignment.CENTER, true, true, true, null,12);
		headerDate = buildStyle(wb, HorizontalAlignment.LEFT, VerticalAlignment.CENTER, false, true, true, null,12);
		serial = buildStyle(wb, HorizontalAlignment.CENTER, VerticalAlignment.CENTER, true, true, false, null,12);
		Date = buildStyle(wb, HorizontalAlignment.LEFT, VerticalAlignment.CENTER, false, true, true, "dd-MM-yyyy",12);
		descText = buildStyle(wb, HorizontalAlignment.CENTER, VerticalAlignment.CENTER, true, true, true, null,12);

		jobDesc = buildStyle(wb, HorizontalAlignment.LEFT, VerticalAlignment.CENTER, false, true, true, null,12.5);

		amount = buildStyle(wb, HorizontalAlignment.RIGHT, VerticalAlignment.CENTER, false, true, true, "#,##0.00",12);

		totalAmount = buildStyle(wb, HorizontalAlignment.CENTER, VerticalAlignment.CENTER, true, true, true, "#,##0.00",12.5);

		totalLabel = buildStyle(wb, HorizontalAlignment.RIGHT, VerticalAlignment.CENTER, true, true, true, null,12.5);
	}

	/*
	 * ========================================================= SINGLE STYLE
	 * BUILDER (THE HEART) =========================================================
	 */

	private CellStyle buildStyle(Workbook wb, HorizontalAlignment hAlign, VerticalAlignment vAlign, boolean bold,
			boolean border, boolean wrap, String dataFormat, double fontsize) {
		CellStyle style = wb.createCellStyle();
		Font font = wb.createFont();
		font.setFontHeightInPoints((short) fontsize);
		style.setFont(font);


		if (border) {
			style.setBorderTop(BorderStyle.THIN);
			style.setBorderBottom(BorderStyle.THIN);
			style.setBorderLeft(BorderStyle.THIN);
			style.setBorderRight(BorderStyle.THIN);
		}

		if (hAlign != null) {
			style.setAlignment(hAlign);
		}
		if (vAlign != null) {
			style.setVerticalAlignment(vAlign);
		}

		style.setWrapText(wrap);

		if (bold) {
			//Font font = wb.createFont();
			font.setBold(true);
			style.setFont(font);
		}

	
		if (dataFormat != null) {
			DataFormat df = wb.createDataFormat();
			style.setDataFormat(df.getFormat(dataFormat));
		}

		return style;
	}
}
