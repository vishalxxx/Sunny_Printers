package utils;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;

public final class InvoiceExcelStyles {

	public final CellStyle headerTitle;
	public final CellStyle headerText;
	public final CellStyle jobDesc;
	public final CellStyle amount;
	public final CellStyle totalAmount;
	public final CellStyle text;
	public final CellStyle clientText;
	public final CellStyle descText;

	public InvoiceExcelStyles(Workbook wb) {

		headerTitle = bold(wb, HorizontalAlignment.LEFT);
		headerText = normal(wb, HorizontalAlignment.LEFT);
		text = normal(wb, HorizontalAlignment.LEFT);
		clientText = bold(wb, HorizontalAlignment.RIGHT);
		descText = bold(wb, HorizontalAlignment.CENTER);
		setBorder(descText);
		jobDesc = bordered(wb, HorizontalAlignment.LEFT);
		amount = amount(wb, false);
		totalAmount = amount(wb, true);
	}

	private CellStyle normal(Workbook wb, HorizontalAlignment h) {
		CellStyle s = wb.createCellStyle();
		s.setAlignment(h);
		s.setVerticalAlignment(VerticalAlignment.CENTER);
		s.setWrapText(true);
		return s;
	}

	private CellStyle bordered(Workbook wb, HorizontalAlignment h) {
		CellStyle s = normal(wb, h);
		setBorder(s);
		return s;
	}

	private CellStyle bold(Workbook wb, HorizontalAlignment h) {
		CellStyle s = normal(wb, h);
		Font f = wb.createFont();
		f.setBold(true);
		s.setFont(f);
		return s;
	}

	private CellStyle amount(Workbook wb, boolean bold) {
		CellStyle s = bordered(wb, HorizontalAlignment.RIGHT);
		DataFormat df = wb.createDataFormat();
		s.setDataFormat(df.getFormat("#,##0.00"));

		if (bold) {
			Font f = wb.createFont();
			f.setBold(true);
			s.setFont(f);
		}
		return s;
	}

	private void setBorder(CellStyle s) {
		s.setBorderTop(BorderStyle.THIN);
		s.setBorderBottom(BorderStyle.THIN);
		s.setBorderLeft(BorderStyle.THIN);
		s.setBorderRight(BorderStyle.THIN);
	}
}
