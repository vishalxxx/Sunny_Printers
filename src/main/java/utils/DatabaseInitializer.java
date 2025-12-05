package utils;

import java.sql.Connection;
import java.sql.Statement;

public class DatabaseInitializer {

	public static void initialize() {

		try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) {

			// ================== CLIENTS TABLE ==================
			stmt.execute("""
					    CREATE TABLE IF NOT EXISTS clients (
					        id INTEGER PRIMARY KEY AUTOINCREMENT,
					        business_name TEXT,
					        client_name TEXT,
					        nick_name TEXT,
					        phone TEXT,
					        alt_phone TEXT,
					        email TEXT,
					        gst TEXT,
					        pan TEXT,
					        billing_address TEXT,
					        shipping_address TEXT,
					        notes TEXT
					    );
					""");

			// ================== JOBS TABLE ==================
			stmt.execute("""
					    CREATE TABLE IF NOT EXISTS jobs (
					        id INTEGER PRIMARY KEY AUTOINCREMENT,
					        client_id INTEGER,
					        job_name TEXT,
					        job_type TEXT,
					        description TEXT,
					        amount REAL,
					        date_created TEXT,
					        FOREIGN KEY(client_id) REFERENCES clients(id)
					    );
					""");

			// ================== BILLING TABLE ==================
			stmt.execute("""
					    CREATE TABLE IF NOT EXISTS billing (
					        id INTEGER PRIMARY KEY AUTOINCREMENT,
					        job_id INTEGER,
					        client_id INTEGER,
					        amount REAL,
					        bill_date TEXT,
					        FOREIGN KEY(job_id) REFERENCES jobs(id),
					        FOREIGN KEY(client_id) REFERENCES clients(id)
					    );
					""");

			// ================== PAYMENTS TABLE ==================
			stmt.execute("""
					    CREATE TABLE IF NOT EXISTS payments (
					        id INTEGER PRIMARY KEY AUTOINCREMENT,
					        client_id INTEGER,
					        amount REAL,
					        payment_date TEXT,
					        method TEXT,
					        FOREIGN KEY(client_id) REFERENCES clients(id)
					    );
					""");

			// ================== USERS TABLE ==================
			stmt.execute("""
					    CREATE TABLE IF NOT EXISTS users (
					        id INTEGER PRIMARY KEY AUTOINCREMENT,
					        username TEXT UNIQUE,
					        password TEXT,
					        role TEXT
					    );
					""");

			System.out.println("✔ All tables are created and ready!");

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
