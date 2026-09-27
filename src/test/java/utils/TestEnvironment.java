package utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized test environment configuration.
 *
 * <h2>Credential Resolution Order</h2>
 * <ol>
 *   <li>Environment variables ({@code TEST_SUPABASE_URL}, {@code TEST_SUPABASE_KEY})
 *       — used in GitHub Actions CI where secrets are injected as env vars.</li>
 *   <li>{@code .env.test} file in the project root — used for local development.</li>
 * </ol>
 *
 * <p>If neither source provides valid credentials, the test suite will
 * <strong>fail immediately</strong> rather than falling back to Production.
 *
 * <h2>Thread Safety</h2>
 * <p>All fields are initialised once via {@link #load()} and are thereafter immutable.
 */
public final class TestEnvironment {

    // ── Resolved credentials ─────────────────────────────────────────────────

    private static volatile String testSupabaseUrl;
    private static volatile String testSupabaseKey;
    private static volatile String credentialSource;
    private static volatile boolean loaded = false;

    private TestEnvironment() {}

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Loads test credentials from environment variables or {@code .env.test}.
     * Call this once from {@code @BeforeAll} or the test extension.
     *
     * @throws IllegalStateException if TEST credentials cannot be resolved
     */
    public static synchronized void load() {
        if (loaded) return;

        // 1. Try environment variables first (CI / release.ps1 injects these)
        String envUrl = System.getenv("TEST_SUPABASE_URL");
        String envKey = System.getenv("TEST_SUPABASE_KEY");

        if (isBlank(envUrl)) envUrl = System.getenv("SUPABASE_URL");
        if (isBlank(envKey)) envKey = System.getenv("SUPABASE_KEY");

        if (!isBlank(envUrl) && !isBlank(envKey)) {
            testSupabaseUrl = envUrl.trim();
            testSupabaseKey = envKey.trim();
            credentialSource = detectCiEnvironment() ? "GitHub Secrets (CI)" : "Environment Variables";
            loaded = true;
            logContext();
            return;
        }

        // 2. Try .env.test file in the project root
        Map<String, String> envFile = loadEnvFile(".env.test");
        if (envFile == null) {
            // Also try from working directory parent (Maven runs from project root)
            envFile = loadEnvFile(resolveProjectRoot() + "/.env.test");
        }

        if (envFile != null) {
            String fileUrl = envFile.get("TEST_SUPABASE_URL");
            String fileKey = envFile.get("TEST_SUPABASE_KEY");
            if (!isBlank(fileUrl) && !isBlank(fileKey)) {
                testSupabaseUrl = fileUrl.trim();
                testSupabaseKey = fileKey.trim();
                credentialSource = ".env.test (local file)";
                loaded = true;
                logContext();
                return;
            }
        }

        // 3. NO credentials found — fail-fast. Never fall back to Production.
        System.err.println("╔══════════════════════════════════════════════════════════════╗");
        System.err.println("║  FATAL: TEST Supabase credentials not found.                ║");
        System.err.println("║                                                              ║");
        System.err.println("║  Provide TEST_SUPABASE_URL and TEST_SUPABASE_KEY via:        ║");
        System.err.println("║    • Environment variables (GitHub CI)                       ║");
        System.err.println("║    • .env.test file in project root (local dev)              ║");
        System.err.println("║                                                              ║");
        System.err.println("║  REFUSING to fall back to Production credentials.            ║");
        System.err.println("╚══════════════════════════════════════════════════════════════╝");
        // Mark as loaded but with null credentials — callers that need live Supabase
        // should check isSupabaseConfigured() and skip or fail.
        credentialSource = "NONE (test credentials unavailable)";
        loaded = true;
        logContext();
    }

    /** Returns the TEST Supabase REST API URL, or {@code null} if unavailable. */
    public static String getTestSupabaseUrl() {
        ensureLoaded();
        return testSupabaseUrl;
    }

    /** Returns the TEST Supabase anon key, or {@code null} if unavailable. */
    public static String getTestSupabaseKey() {
        ensureLoaded();
        return testSupabaseKey;
    }

    /** Returns a human-readable description of where credentials were loaded from. */
    public static String getCredentialSource() {
        ensureLoaded();
        return credentialSource;
    }

    /** Returns {@code true} if valid TEST Supabase credentials are available. */
    public static boolean isSupabaseConfigured() {
        ensureLoaded();
        return !isBlank(testSupabaseUrl) && !isBlank(testSupabaseKey);
    }

    /**
     * Injects TEST Supabase credentials into the {@code supabase_settings} table
     * of the currently-active SQLite database. This ensures that any code path
     * reading credentials from SQLite will get TEST credentials, not Production.
     */
    public static void injectCredentialsIntoDatabase() {
        if (!isSupabaseConfigured()) return;
        try (Connection con = DBConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE supabase_settings SET supabase_url = ?, anon_key = ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = '00000000-0000-0000-0000-000000000003'")) {
                ps.setString(1, testSupabaseUrl);
                ps.setString(2, testSupabaseKey);
                ps.executeUpdate();
            }
            System.out.println("[TestEnvironment] Injected TEST Supabase credentials into SQLite supabase_settings.");
        } catch (Exception e) {
            System.err.println("[TestEnvironment] Failed to inject credentials: " + e.getMessage());
        }
    }

    /**
     * Logs the active test environment context to stdout.
     * Called automatically on first load and can be called manually.
     */
    public static void logContext() {
        String env = detectCiEnvironment() ? "CI TEST" : "LOCAL TEST";
        String dbPath = DBConnection.getUrl();
        String maskedUrl = maskUrl(testSupabaseUrl);

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    TEST ENVIRONMENT CONTEXT                  ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("║  Environment:       " + padRight(env, 39) + "║");
        System.out.println("║  SQLite DB:         " + padRight(truncate(dbPath, 39), 39) + "║");
        System.out.println("║  Supabase URL:      " + padRight(maskedUrl, 39) + "║");
        System.out.println("║  Credential Source: " + padRight(credentialSource, 39) + "║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    // ── Utility methods ──────────────────────────────────────────────────────

    private static void ensureLoaded() {
        if (!loaded) load();
    }

    private static boolean detectCiEnvironment() {
        return System.getenv("CI") != null
                || System.getenv("GITHUB_ACTIONS") != null
                || System.getenv("GITHUB_RUN_ID") != null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String maskUrl(String url) {
        if (url == null) return "(not configured)";
        // Show scheme + first 20 chars of host
        if (url.length() > 30) {
            return url.substring(0, 30) + "...";
        }
        return url;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "(null)";
        if (s.length() <= max) return s;
        return "..." + s.substring(s.length() - max + 3);
    }

    private static String padRight(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }

    private static String resolveProjectRoot() {
        // Maven sets user.dir to the project root
        return System.getProperty("user.dir", ".");
    }

    private static Map<String, String> loadEnvFile(String path) {
        Path p = Paths.get(path);
        if (!Files.exists(p)) return null;
        Map<String, String> map = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(p)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                // Strip surrounding quotes
                if (val.length() >= 2) {
                    if ((val.startsWith("\"") && val.endsWith("\""))
                            || (val.startsWith("'") && val.endsWith("'"))) {
                        val = val.substring(1, val.length() - 1);
                    }
                }
                map.put(key, val);
            }
        } catch (IOException e) {
            System.err.println("[TestEnvironment] Failed to read " + path + ": " + e.getMessage());
            return null;
        }
        return map;
    }
}
