package utils;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class TemporaryDatabaseProvider implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(TemporaryDatabaseProvider.class);
    private static final String ORIGINAL_URL_KEY = "ORIGINAL_DB_URL";
    private static final String ACTIVE_TEST_URL_KEY = "ACTIVE_TEST_DB_URL";

    private static String safetyFallbackUrl = null;

    private boolean isUnitTestClass(Class<?> testClass) {
        if (testClass == null) return false;
        String packageName = testClass.getPackageName();
        if ("unit".equals(packageName) || packageName.startsWith("unit.")) {
            return true;
        }
        
        // Also check tag annotation
        if (testClass.isAnnotationPresent(org.junit.jupiter.api.Tag.class)) {
            org.junit.jupiter.api.Tag tag = testClass.getAnnotation(org.junit.jupiter.api.Tag.class);
            if ("unit".equals(tag.value())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (isUnitTestClass(context.getRequiredTestClass())) {
            return;
        }

        // Guarantee that if any database-dependent test class runs, the default DB Connection url is set to a safe location,
        // preventing any writes to the developer's default development/production database.
        synchronized (TemporaryDatabaseProvider.class) {
            if (safetyFallbackUrl == null) {
                safetyFallbackUrl = TestDatabaseFactory.createFreshTestDatabase();
                DBConnection.setUrl(safetyFallbackUrl);
                // Hook to clean it up at JVM shutdown
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    TestDatabaseManager.cleanupDatabase(safetyFallbackUrl);
                }));
            }
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        if (isUnitTestClass(context.getRequiredTestClass())) {
            return;
        }

        // Save original URL
        String originalUrl = DBConnection.getUrl();
        context.getStore(NAMESPACE).put(ORIGINAL_URL_KEY, originalUrl);

        // Generate and set isolated test database URL
        String testUrl = TestDatabaseFactory.createFreshTestDatabase();
        context.getStore(NAMESPACE).put(ACTIVE_TEST_URL_KEY, testUrl);
        DBConnection.setUrl(testUrl);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (isUnitTestClass(context.getRequiredTestClass())) {
            return;
        }

        // Retrieve active test database URL and cleanup
        String testUrl = context.getStore(NAMESPACE).get(ACTIVE_TEST_URL_KEY, String.class);
        String originalUrl = context.getStore(NAMESPACE).get(ORIGINAL_URL_KEY, String.class);

        if (originalUrl != null) {
            DBConnection.setUrl(originalUrl);
        } else if (safetyFallbackUrl != null) {
            DBConnection.setUrl(safetyFallbackUrl);
        }

        if (testUrl != null) {
            TestDatabaseManager.cleanupDatabase(testUrl);
        }
    }
}
