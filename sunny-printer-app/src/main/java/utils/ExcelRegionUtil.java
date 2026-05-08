package utils;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;

public final class ExcelRegionUtil {

	private ExcelRegionUtil() {
	}

	public static void applyBorder(Sheet sheet, CellRangeAddress region, boolean top, boolean bottom, boolean left,
			boolean right) {
		if (top) {
			RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
		}
		if (bottom) {
			RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
		}
		if (left) {
			RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);
		}
		if (right) {
			RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
		}
	}
}
