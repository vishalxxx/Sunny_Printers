package utils;

import java.sql.Connection;
import java.sql.DriverManager;

public class DBConnection {

	private static final String URL = "jdbc:sqlite:database/sunnyprinters.db";

	static {
		// initialize database tables
		DatabaseInitializer.initialize();
	}

	public static Connection getConnection() {
		try {
			return DriverManager.getConnection(URL);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
