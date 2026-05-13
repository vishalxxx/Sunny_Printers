package service;

import model.Invoice;

import org.apache.poi.ss.usermodel.Workbook;

import java.io.File;
import java.io.FileOutputStream;
import java.time.YearMonth;

import utils.CompanyDataLayout;
import utils.DownloadTracker;
import utils.UniversalDownloadPath;

import java.util.prefs.Preferences;

public class InvoiceStorageService {

	private static final Preferences PREFS = Preferences.userRoot().node("invoice_settings");
	private static final String KEY_SAVE_PATH = "save_path";
	private static final String KEY_NEVER_ASK = "never_ask";

	public static File saveInvoice(Workbook workbook, Invoice invoice) {
		File output = CompanyDataLayout.invoiceExcelPath(invoice);
		output.getParentFile().mkdirs();
		try (FileOutputStream fos = new FileOutputStream(output)) {
			workbook.write(fos);
		} catch (Exception e) {
			throw new RuntimeException("Failed to save invoice", e);
		}
		DownloadTracker.registerExportedFile(output, "EXCEL");
		return output;
	}

	/**
	 * Preferred root configured in General Settings (default download folder); if blank, uses
	 * {@code user.home/<CompanyName>Data}. Legacy {@code invoice_settings/save_path} is still read
	 * inside {@link CompanyDataLayout#getDataStoreRoot()}.
	 */
	public static void setSavePath(String path, boolean neverAsk) {
		if (path != null && !path.isBlank()) {
			UniversalDownloadPath.set(path.trim());
			PREFS.put(KEY_SAVE_PATH, path.trim());
		}
		PREFS.putBoolean(KEY_NEVER_ASK, neverAsk);
	}

	public static String getSavePath() {
		return CompanyDataLayout.getDataStoreRoot().getAbsolutePath();
	}

	public static boolean isNeverAsk() {
		return PREFS.getBoolean(KEY_NEVER_ASK, false);
	}

	public static File createPdfFile(Invoice invoice) {
		File out = CompanyDataLayout.invoicePdfPath(invoice);
		out.getParentFile().mkdirs();
		return out;
	}

	public static File createMonthlyPdfFile(YearMonth ym) {
		File out = CompanyDataLayout.monthlyBulkPdfPath(ym);
		out.getParentFile().mkdirs();
		return out;
	}
}
