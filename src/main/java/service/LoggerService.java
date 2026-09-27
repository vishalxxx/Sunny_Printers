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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise-grade centralized logger for Sunny Printers ERP.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Multi-channel log files: application, database, sync, ui, performance, error</li>
 *   <li>Daily file rotation with 30-day retention (configurable)</li>
 *   <li>Per-thread Correlation ID for tracing complete workflows</li>
 *   <li>Operation start/end markers with success/failure summaries</li>
 *   <li>Performance timing helpers</li>
 *   <li>Global uncaught exception handler registration</li>
 * </ul>
 *
 * <h2>Security</h2>
 * Never logs passwords, API keys, JWT tokens, Bearer tokens, or Supabase secrets.
 * All messages are run through {@link #sanitize(String)} before writing.
 */
public final class LoggerService {

    // ── Log directory & formatters ────────────────────────────────────────────

    private static final String LOG_DIR_PATH =
            Paths.get(System.getProperty("user.home"), ".sunnyprinters", "logs").toString();

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static int retentionDays = 30;

    // ── Log channels ─────────────────────────────────────────────────────────

    public enum LogChannel {
        /** General application events */       APP("application"),
        /** Database connections, SQL, locks */  DB("database"),
        /** Supabase sync operations */         SYNC("sync"),
        /** JavaFX controller / UI events */    UI("ui"),
        /** Timing and throughput */            PERF("performance"),
        /** All ERROR-level events */           ERROR("error");

        final String fileName;
        LogChannel(String fileName) { this.fileName = fileName; }
    }

    // ── Per-thread correlation ID ─────────────────────────────────────────────

    private static final ThreadLocal<String> correlationId = new ThreadLocal<>();

    /**
     * Starts a new correlation scope for the calling thread.
     * All log lines until {@link #clearCorrelationId()} will carry this ID.
     *
     * @return the generated correlation ID
     */
    public static String newCorrelationId() {
        String id = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        correlationId.set(id);
        return id;
    }

    public static void setCorrelationId(String id) { correlationId.set(id); }

    public static String getCorrelationId() {
        String id = correlationId.get();
        return id != null ? id : "--------";
    }

    public static void clearCorrelationId() { correlationId.remove(); }

    // ── Per-thread operation timers ───────────────────────────────────────────

    private static final ThreadLocal<ConcurrentHashMap<String, Long>> timers =
            ThreadLocal.withInitial(ConcurrentHashMap::new);

    /**
     * Starts a named performance timer on the calling thread.
     * Call {@link #endTimer(String)} to emit a PERF log line.
     */
    public static void startTimer(String name) {
        timers.get().put(name, System.currentTimeMillis());
    }

    /**
     * Ends a named timer and logs the elapsed time to the PERF channel.
     *
     * @return elapsed milliseconds, or -1 if timer was never started
     */
    public static long endTimer(String name) {
        Long start = timers.get().remove(name);
        if (start == null) return -1L;
        long elapsed = System.currentTimeMillis() - start;
        perf("[TIMER] " + name + " completed in " + elapsed + " ms");
        return elapsed;
    }

    // ── Operation begin/end markers ───────────────────────────────────────────

    /**
     * Logs a structured operation start marker and starts a performance timer.
     * Use {@link #endOperation(String, boolean, String)} to close it.
     *
     * @param operationName e.g. "LOGIN", "CLIENT-CREATE", "SYNC"
     * @return correlation ID for this operation
     */
    public static String beginOperation(String operationName) {
        String cid = newCorrelationId();
        String msg = "▶ BEGIN [" + operationName + "] cid=" + cid;
        log("INFO", msg, LogChannel.APP, null, null);
        startTimer(operationName);
        return cid;
    }

    /**
     * Logs a structured operation end marker and emits timing.
     *
     * @param operationName must match the name used in {@link #beginOperation}
     * @param success       true = SUCCESS, false = FAILURE
     * @param detail        optional detail message (null = none)
     */
    public static void endOperation(String operationName, boolean success, String detail) {
        long elapsed = endTimer(operationName);
        String status = success ? "✅ SUCCESS" : "❌ FAILURE";
        String msg = "■ END   [" + operationName + "] " + status
                + (elapsed >= 0 ? " (" + elapsed + " ms)" : "")
                + (detail != null ? " — " + detail : "");
        if (success) {
            log("INFO", msg, LogChannel.APP, null, null);
        } else {
            log("WARN", msg, LogChannel.APP, null, null);
        }
        clearCorrelationId();
    }

    // ── Static initializer ────────────────────────────────────────────────────

    static {
        try {
            Files.createDirectories(Paths.get(LOG_DIR_PATH));
            rotateLogs();
        } catch (Exception e) {
            System.err.println("[LoggerService] Failed to initialize logs directory: " + e.getMessage());
        }
    }

    private LoggerService() {}

    public static void setRetentionDays(int days) { retentionDays = days; }

    // ── Public logging API ────────────────────────────────────────────────────

    // — APP channel convenience methods —

    public static void info(String message) {
        log("INFO", message, LogChannel.APP, null, null);
    }

    public static void info(String message, Class<?> clazz) {
        log("INFO", message, LogChannel.APP, clazz, null);
    }

    public static void warn(String message) {
        log("WARN", message, LogChannel.APP, null, null);
    }

    public static void warn(String message, Class<?> clazz) {
        log("WARN", message, LogChannel.APP, clazz, null);
    }

    public static void error(String message) {
        log("ERROR", message, LogChannel.APP, null, null);
    }

    public static void error(String message, Class<?> clazz) {
        log("ERROR", message, LogChannel.APP, clazz, null);
    }

    public static void error(String message, Throwable t) {
        log("ERROR", message, LogChannel.APP, null, t);
    }

    public static void error(String message, Class<?> clazz, Throwable t) {
        log("ERROR", message, LogChannel.APP, clazz, t);
    }

    public static void debug(String message) {
        log("DEBUG", message, LogChannel.APP, null, null);
    }

    public static void debug(String message, Class<?> clazz) {
        log("DEBUG", message, LogChannel.APP, clazz, null);
    }

    // — Channel-specific convenience methods —

    public static void db(String message) {
        log("INFO", message, LogChannel.DB, null, null);
    }

    public static void dbWarn(String message) {
        log("WARN", message, LogChannel.DB, null, null);
    }

    public static void dbError(String message, Throwable t) {
        log("ERROR", message, LogChannel.DB, null, t);
    }

    public static void sync(String message) {
        log("INFO", message, LogChannel.SYNC, null, null);
    }

    public static void syncWarn(String message) {
        log("WARN", message, LogChannel.SYNC, null, null);
    }

    public static void syncError(String message, Throwable t) {
        log("ERROR", message, LogChannel.SYNC, null, t);
    }

    public static void ui(String message) {
        log("INFO", message, LogChannel.UI, null, null);
    }

    public static void uiWarn(String message) {
        log("WARN", message, LogChannel.UI, null, null);
    }

    public static void perf(String message) {
        log("INFO", message, LogChannel.PERF, null, null);
    }

    // ── Internal core log writer ──────────────────────────────────────────────

    private static synchronized void log(String level, String message,
                                          LogChannel channel, Class<?> clazz, Throwable t) {
        try {
            String sanitizedMessage = sanitize(message);
            String threadName      = Thread.currentThread().getName();
            String className       = clazz != null ? clazz.getSimpleName() : getCallerClassName();
            String timestamp       = LocalDateTime.now().format(TIME_FMT);
            String cid             = getCorrelationId();
            String dateStr         = LocalDate.now().format(DATE_FMT);

            String formattedLog = String.format(
                    "[%s] [%-5s] [%s] [%s] [%s] %s",
                    timestamp, level, cid, className, sanitizedMessage, "");
            // Trim trailing space
            formattedLog = formattedLog.stripTrailing();

            // Console output
            if ("ERROR".equals(level) || "WARN".equals(level)) {
                System.err.println(formattedLog);
            } else {
                System.out.println(formattedLog);
            }

            // Stack trace to console
            if (t != null) t.printStackTrace(System.err);

            // ── Write to main application log ──────────────────────────────
            writeLine(dateStr, "sunnyprinters", formattedLog, t);

            // ── Write to channel-specific log ──────────────────────────────
            writeLine(dateStr, channel.fileName, formattedLog, t);

            // ── ERROR channel: always mirror errors ──────────────────────
            if ("ERROR".equals(level) && channel != LogChannel.ERROR) {
                writeLine(dateStr, LogChannel.ERROR.fileName, formattedLog, t);
            }

        } catch (Exception e) {
            System.err.println("[LoggerService] Logging failed: " + e.getMessage());
        }
    }

    private static void writeLine(String dateStr, String filePrefix, String line, Throwable t) {
        try {
            Path logFilePath = Paths.get(LOG_DIR_PATH, filePrefix + "-" + dateStr + ".log");
            try (FileWriter fw = new FileWriter(logFilePath.toFile(), true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(line);
                if (t != null) t.printStackTrace(pw);
            }
        } catch (Exception e) {
            System.err.println("[LoggerService] Could not write to " + filePrefix + ": " + e.getMessage());
        }
    }

    // ── Caller class detection ────────────────────────────────────────────────

    private static String getCallerClassName() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 1; i < stack.length; i++) {
            String cls = stack[i].getClassName();
            if (!cls.equals(LoggerService.class.getName())
                    && !cls.startsWith("java.lang.Thread")) {
                return cls.substring(cls.lastIndexOf('.') + 1);
            }
        }
        return "Unknown";
    }

    // ── Secret sanitization ───────────────────────────────────────────────────

    /**
     * Strips sensitive values from log messages before writing.
     * Covers: password, anonKey, apikey, token, secret, jwt, bearer, authorization.
     * Safe to call with null (returns null).
     */
    public static String sanitize(String input) {
        if (input == null) return null;
        String out = input;
        // password=xxx or password: xxx
        out = out.replaceAll("(?i)(password\\s*[=:]\\s*['\"]?)[^'\"\\s&,;]+(['\"]?)", "$1********$2");
        // anonKey, anon_key
        out = out.replaceAll("(?i)(anon[_]?[Kk]ey\\s*[=:]\\s*['\"]?)[^'\"\\s&,;]+(['\"]?)", "$1********$2");
        // apikey, api_key
        out = out.replaceAll("(?i)(api[_]?[Kk]ey\\s*[=:]\\s*['\"]?)[^'\"\\s&,;]+(['\"]?)", "$1********$2");
        // token=xxx
        out = out.replaceAll("(?i)(\\btoken\\s*[=:]\\s*['\"]?)[^'\"\\s&,;]+(['\"]?)", "$1********$2");
        // secret=xxx
        out = out.replaceAll("(?i)(\\bsecret\\s*[=:]\\s*['\"]?)[^'\"\\s&,;]+(['\"]?)", "$1********$2");
        // jwt=xxx
        out = out.replaceAll("(?i)(\\bjwt\\s*[=:]\\s*['\"]?)[^'\"\\s&,;]+(['\"]?)", "$1********$2");
        // Bearer xxx
        out = out.replaceAll("(?i)(Bearer\\s+)[^'\"\\s&,;]+", "$1********");
        // Authorization: xxx
        out = out.replaceAll("(?i)(Authorization\\s*[=:]\\s*['\"]?)[^'\"\\s&,;]+(['\"]?)", "$1********$2");
        // Supabase anon key pattern: eyJ...
        out = out.replaceAll("(eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{5,})", "eyJ***.***");
        // sb_publishable_ pattern
        out = out.replaceAll("(?i)(sb_publishable_)[^'\"\\s&,;]+", "$1********");
        return out;
    }

    // ── Log rotation ──────────────────────────────────────────────────────────

    /**
     * Deletes log files older than {@link #retentionDays} days across all channels.
     */
    public static void rotateLogs() {
        try {
            File dir = new File(LOG_DIR_PATH);
            if (!dir.exists() || !dir.isDirectory()) return;

            File[] logFiles = dir.listFiles((d, name) -> name.endsWith(".log"));
            if (logFiles == null) return;

            LocalDate threshold = LocalDate.now().minusDays(retentionDays);
            for (File file : logFiles) {
                try {
                    String name = file.getName();
                    // Extract date from names like "application-2025-01-01.log" or "sunnyprinters-2025-01-01.log"
                    int dashIdx = name.lastIndexOf('-', name.length() - 15);
                    if (dashIdx < 0) continue;
                    String dateStr = name.substring(dashIdx + 1, name.length() - 4);
                    LocalDate fileDate = LocalDate.parse(dateStr, DATE_FMT);
                    if (fileDate.isBefore(threshold)) {
                        if (file.delete()) {
                            System.out.println("[LoggerService] Rotated old log: " + name);
                        }
                    }
                } catch (Exception ignored) {
                    // Skip files with unexpected names
                }
            }
        } catch (Exception e) {
            System.err.println("[LoggerService] Log rotation failed: " + e.getMessage());
        }
    }

    // ── Global uncaught exception handler ────────────────────────────────────

    /**
     * Registers a global handler that writes every unhandled JVM exception
     * to both application.log and error.log with a full stack trace.
     */
    public static void registerUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                log("ERROR",
                    "[UNCAUGHT] Exception in thread '" + thread.getName()
                        + "': " + throwable.getMessage(),
                    LogChannel.ERROR, LoggerService.class, throwable);
            } catch (Exception e) {
                System.err.println("[LoggerService] Failed to log uncaught exception: " + e.getMessage());
                throwable.printStackTrace();
            }
        });
        info("Global uncaught exception handler registered.", LoggerService.class);
    }
}
