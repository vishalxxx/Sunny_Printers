package service;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Centralized application logger with daily-rotating log files.
 * Writes to ~/.sunnyprinters/logs/sunnyprinters-YYYY-MM-DD.log
 * and mirrors output to console.
 *
 * SAFETY: Never logs passwords, Supabase keys, JWT tokens, or API secrets.
 */
public final class LoggerService {

    private static final String LOG_DIR_PATH = Paths.get(System.getProperty("user.home"), ".sunnyprinters", "logs").toString();
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static int retentionDays = 30;

    static {
        try {
            Files.createDirectories(Paths.get(LOG_DIR_PATH));
            rotateLogs();
        } catch (Exception e) {
            System.err.println("[LoggerService] Failed to initialize logs directory: " + e.getMessage());
        }
    }

    private LoggerService() {}

    public static void setRetentionDays(int days) {
        retentionDays = days;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static void info(String message) { log("INFO", message, null, null); }
    public static void info(String message, Class<?> clazz) { log("INFO", message, clazz, null); }

    public static void warn(String message) { log("WARN", message, null, null); }
    public static void warn(String message, Class<?> clazz) { log("WARN", message, clazz, null); }

    public static void error(String message) { log("ERROR", message, null, null); }
    public static void error(String message, Class<?> clazz) { log("ERROR", message, clazz, null); }
    public static void error(String message, Throwable t) { log("ERROR", message, null, t); }
    public static void error(String message, Class<?> clazz, Throwable t) { log("ERROR", message, clazz, t); }

    public static void debug(String message) { log("DEBUG", message, null, null); }
    public static void debug(String message, Class<?> clazz) { log("DEBUG", message, clazz, null); }

    // ── Internal logging ──────────────────────────────────────────────────────

    private static synchronized void log(String level, String message, Class<?> clazz, Throwable t) {
        try {
            String sanitizedMessage = sanitize(message);
            String threadName = Thread.currentThread().getName();
            String className = clazz != null ? clazz.getSimpleName() : getCallerClassName();
            String timestamp = LocalDateTime.now().format(TIME_FORMATTER);

            String formattedLog = String.format("[%s] [%-5s] [%s] [%s] - %s", timestamp, level, threadName, className, sanitizedMessage);

            if ("ERROR".equals(level) || "WARN".equals(level)) {
                System.err.println(formattedLog);
            } else {
                System.out.println(formattedLog);
            }

            // Write to daily log file
            String dateStr = LocalDate.now().format(FILE_DATE_FORMATTER);
            Path logFilePath = Paths.get(LOG_DIR_PATH, "sunnyprinters-" + dateStr + ".log");

            try (FileWriter fw = new FileWriter(logFilePath.toFile(), true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(formattedLog);
                if (t != null) {
                    t.printStackTrace(pw);
                    t.printStackTrace(System.err);
                }
            }
        } catch (Exception e) {
            System.err.println("[LoggerService] Logging failed: " + e.getMessage());
        }
    }

    private static String getCallerClassName() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 1; i < stack.length; i++) {
            String cls = stack[i].getClassName();
            if (!cls.equals(LoggerService.class.getName()) && !cls.startsWith("java.lang.Thread")) {
                return cls.substring(cls.lastIndexOf('.') + 1);
            }
        }
        return "Unknown";
    }

    // ── Sanitization ──────────────────────────────────────────────────────────

    /**
     * Strips sensitive values (passwords, tokens, keys) from log messages.
     */
    public static String sanitize(String input) {
        if (input == null) return null;
        String output = input;
        output = output.replaceAll("(?i)(password\\s*=\\s*['\"]?)[^'\"\\s&,]+(['\"]?)", "$1********$2");
        output = output.replaceAll("(?i)(anonKey\\s*=\\s*['\"]?)[^'\"\\s&,]+(['\"]?)", "$1********$2");
        output = output.replaceAll("(?i)(sb_publishable_)[^'\"\\s&,]+", "$1********");
        output = output.replaceAll("(?i)(bearer\\s+)[^'\"\\s&,]+", "$1********");
        output = output.replaceAll("(?i)(token\\s*=\\s*['\"]?)[^'\"\\s&,]+(['\"]?)", "$1********$2");
        return output;
    }

    // ── Log rotation ──────────────────────────────────────────────────────────

    public static void rotateLogs() {
        try {
            File dir = new File(LOG_DIR_PATH);
            if (!dir.exists() || !dir.isDirectory()) return;

            File[] logFiles = dir.listFiles((d, name) -> name.startsWith("sunnyprinters-") && name.endsWith(".log"));
            if (logFiles == null) return;

            LocalDate thresholdDate = LocalDate.now().minusDays(retentionDays);
            for (File file : logFiles) {
                try {
                    String name = file.getName();
                    String dateStr = name.substring("sunnyprinters-".length(), name.length() - ".log".length());
                    LocalDate fileDate = LocalDate.parse(dateStr, FILE_DATE_FORMATTER);
                    if (fileDate.isBefore(thresholdDate)) {
                        if (file.delete()) {
                            System.out.println("[LoggerService] Rotated old log file: " + name);
                        }
                    }
                } catch (Exception ignored) {
                    // Skip files with non-standard names
                }
            }
        } catch (Exception e) {
            System.err.println("[LoggerService] Log rotation failed: " + e.getMessage());
        }
    }

    // ── Global exception handler ──────────────────────────────────────────────

    /**
     * Registers a global uncaught exception handler that writes every unhandled
     * exception with full stack trace to the log file.
     */
    public static void registerUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                error("[UNCAUGHT] Exception in thread '" + thread.getName() + "': " + throwable.getMessage(), LoggerService.class, throwable);
            } catch (Exception e) {
                System.err.println("[LoggerService] Failed to log uncaught exception: " + e.getMessage());
                throwable.printStackTrace();
            }
        });
        info("Global uncaught exception handler registered.", LoggerService.class);
    }
}
