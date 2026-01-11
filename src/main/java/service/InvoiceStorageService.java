package service;

import model.Invoice;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.File;
import java.io.FileOutputStream;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.prefs.Preferences;

public class InvoiceStorageService {

    private static final Preferences PREFS =
            Preferences.userRoot().node("invoice_settings");

    private static final String KEY_SAVE_PATH = "save_path";
    private static final String KEY_NEVER_ASK = "never_ask";

    /* =======================
       PUBLIC API
       ======================= */

    public static File saveInvoice(
            Workbook workbook,
            Invoice invoice
    ) {

        File baseDir = resolveBaseDirectory();

        YearMonth ym = YearMonth.from(invoice.getInvoiceDate());
        File monthDir = createYearMonthFolder(baseDir, ym);

        String fileName = buildFileName(
                invoice.getCompanyName(),
                invoice.getClientName(),
                invoice.getInvoiceNo(),
                ym
        );

        File output = new File(monthDir, fileName);

        try (FileOutputStream fos = new FileOutputStream(output)) {
            workbook.write(fos);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save invoice", e);
        }

        return output;
    }

    /* =======================
       PATH PREFERENCES
       ======================= */

    public static void setSavePath(String path, boolean neverAsk) {
        PREFS.put(KEY_SAVE_PATH, path);
        PREFS.putBoolean(KEY_NEVER_ASK, neverAsk);
    }

    public static String getSavePath() {
        return PREFS.get(KEY_SAVE_PATH, null);
    }

    public static boolean isNeverAsk() {
        return PREFS.getBoolean(KEY_NEVER_ASK, false);
    }

    private static File resolveBaseDirectory() {

        String path = getSavePath();

        if (path == null || path.isBlank()) {
            throw new IllegalStateException(
                    "Invoice save location is not configured"
            );
        }

        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /* =======================
       FOLDER CREATION
       ======================= */

    private static File createYearMonthFolder(
            File baseDir,
            YearMonth ym
    ) {

        File yearDir =
                new File(baseDir, String.valueOf(ym.getYear()));

        File monthDir =
                new File(
                        yearDir,
                        String.format(
                                "%02d_%s",
                                ym.getMonthValue(),
                                ym.getMonth()
                                  .getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                        )
                );

        if (!monthDir.exists()) {
            monthDir.mkdirs();
        }

        return monthDir;
    }

    /* =======================
       FILE NAMING
       ======================= */

    private static String buildFileName(
            String business,
            String client,
            String invoiceNo,
            YearMonth ym
    ) {

        return sanitize(business) + "_"
             + sanitize(client) + "_"
             + sanitize(invoiceNo) + "_"
             + ym + ".xlsx";
    }

    private static String sanitize(String s) {
        return s.trim().replaceAll("[^a-zA-Z0-9-_]", "_");
    }
}
