package utils;

import java.sql.Connection;
import java.sql.DriverManager;

public class DBConnection {

	private static final String URL = "jdbc:sqlite:database/sunnyprinters.db";

	static {
		// initialize database tables
		try {
			DatabaseInitializer.initialize();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
