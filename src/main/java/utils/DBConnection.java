package utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DBConnection {

    private static final String URL = "jdbc:sqlite:database/sunnyprinters.db";

    static {
        try {
			ensureDatabaseParentDirectory();
			DatabaseInitializer.initialize();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

	/**
	 * SQLite needs the parent directory to exist; {@code database/} is not in Git.
	 */
	public static void ensureDatabaseParentDirectory() throws Exception {
		Files.createDirectories(Path.of("database"));
	}

    public static Connection getConnection() throws Exception {
		ensureDatabaseParentDirectory();
        Connection connection = DriverManager.getConnection(URL);

        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA synchronous=NORMAL;");
            st.execute("PRAGMA busy_timeout=5000;");
            st.execute("PRAGMA foreign_keys=ON;");
        }

        return connection;
    }
}

