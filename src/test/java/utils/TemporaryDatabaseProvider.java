package utils;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension that provides each test method with a completely isolated
 * SQLite database by injecting a thread-local URL override into {@link DBConnection}.
 *
 * <p>Using {@link DBConnection#setTestDatabaseUrl(String)} / {@link DBConnection#clearTestDatabaseUrl()}
 * means this override is <em>per-thread only</em>: concurrent test threads, background
 * sync threads, and the JavaFX application thread are all unaffected.
 *
 * <p>Additionally injects TEST Supabase credentials from {@link TestEnvironment}
 * into the freshly created SQLite database so that no test can accidentally
 * load Production Supabase credentials.
 *
 * <p>Unit tests (package {@code unit}) are skipped — they do not touch the database.
 */
public class TemporaryDatabaseProvider implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(TemporaryDatabaseProvider.class);
    private static final String ACTIVE_TEST_URL_KEY = "ACTIVE_TEST_DB_URL";

    private boolean isUnitTestClass(Class<?> testClass) {
        if (testClass == null) return false;
        String packageName = testClass.getPackageName();
        if ("unit".equals(packageName) || packageName.startsWith("unit.")) return true;
        if (testClass.isAnnotationPresent(org.junit.jupiter.api.Tag.class)) {
            org.junit.jupiter.api.Tag tag = testClass.getAnnotation(org.junit.jupiter.api.Tag.class);
            if ("unit".equals(tag.value())) return true;
        }
        return false;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        // Load TestEnvironment once per JVM so credential resolution happens early.
        TestEnvironment.load();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        if (isUnitTestClass(context.getRequiredTestClass())) return;

        // Create a fresh isolated database for this test and inject it into the
        // calling thread only.
        String testUrl = TestDatabaseFactory.createFreshTestDatabase();
        context.getStore(NAMESPACE).put(ACTIVE_TEST_URL_KEY, testUrl);
        DBConnection.setTestDatabaseUrl(testUrl);
        DBConnection.setGlobalTestDatabaseUrl(testUrl);

        // Eagerly initialise the schema so the test doesn't hit a cold start.
        try {
            DBConnection.ensureDatabaseParentDirectory();
            utils.DatabaseInitializer.initialize();
            DBConnection.registerInitializedUrl(testUrl);
        } catch (Exception e) {
            DBConnection.clearTestDatabaseUrl();
            DBConnection.clearGlobalTestDatabaseUrl();
            TestDatabaseManager.cleanupDatabase(testUrl);
            throw new RuntimeException("Failed to initialise test database: " + e.getMessage(), e);
        }

        // Inject TEST Supabase credentials into the fresh SQLite database.
        // This prevents any code path from loading Production credentials.
        TestEnvironment.injectCredentialsIntoDatabase();

        // Log the active test context for verification.
        System.out.println("[TemporaryDatabaseProvider] Test: " + context.getDisplayName());
        TestEnvironment.logContext();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (isUnitTestClass(context.getRequiredTestClass())) return;

        // Always clear the overrides first so that no further DB access from
        // cleanup code hits the now-deleted test file.
        DBConnection.clearTestDatabaseUrl();
        DBConnection.clearGlobalTestDatabaseUrl();

        String testUrl = context.getStore(NAMESPACE).get(ACTIVE_TEST_URL_KEY, String.class);
        if (testUrl != null) {
            TestDatabaseManager.cleanupDatabase(testUrl);
        }
    }
}
