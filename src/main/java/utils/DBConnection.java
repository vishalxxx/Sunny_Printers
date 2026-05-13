package utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DBConnection {

    private static final String URL = "jdbc:sqlite:database/sunnyprinters.db";

    static {
        try {
            DatabaseInitializer.initialize();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws Exception {

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

