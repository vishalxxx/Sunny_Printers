package utils;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.DriverManager;

public class DatabaseInitializer {

    public static void initialize() throws Exception
 {

        // Open a direct JDBC connection here to avoid calling DBConnection.getConnection()
        // which itself calls DatabaseInitializer.initialize() and causes recursion.
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:database/sunnyprinters.db");
             Statement stmt = conn.createStatement()) {

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

            // ================== PAYMENT ALLOCATIONS TABLE ==================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS payment_allocations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    payment_id INTEGER NOT NULL,
                    invoice_id INTEGER NOT NULL,
                    allocated_amount REAL NOT NULL CHECK(allocated_amount > 0),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (payment_id) REFERENCES payments(id),
                    FOREIGN KEY (invoice_id) REFERENCES invoice_master(id)
                );
            """);

            // ================== PAYMENT DETAILS TABLE ==================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS payment_details (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    payment_id INTEGER NOT NULL,
                    field_key TEXT NOT NULL,
                    field_value TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(payment_id, field_key)
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

            // ================== INVOICE_MASTER TABLE (NEW) ==================
            // Columns must match repository expectations. created_at defaults to now.
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS invoice_master (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    invoice_no TEXT,
                    client_id INTEGER,
                    client_name TEXT,
                    invoice_date TEXT,
                    period_from TEXT,
                    period_to TEXT,
                    amount REAL DEFAULT 0,
                    paid_amount REAL DEFAULT 0,
                    due_amount REAL DEFAULT 0,
                    payment_status TEXT,
                    last_payment_date TEXT,
                    type TEXT,
                    status TEXT,
                    is_void INTEGER DEFAULT 0,
                    void_reason TEXT,
                    void_date TEXT,
                    replaced_by_invoice_id INTEGER,
                    status_updated_by TEXT,
                    file_path TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // ================== SYSTEM SETTINGS TABLE ==================
            stmt.execute("""
			        CREATE TABLE IF NOT EXISTS system_settings (
			            id INTEGER PRIMARY KEY CHECK (id = 1),

			            invoice_mode TEXT NOT NULL CHECK (invoice_mode IN ('AUTO','MANUAL')),
			            invoice_prefix TEXT,
			            invoice_start_no INTEGER,
			            invoice_padding INTEGER,

			            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
			        );
			""");

            // Ensure default configuration row exists
            stmt.execute("""
			        INSERT OR IGNORE INTO system_settings
			        (id, invoice_mode, invoice_prefix, invoice_start_no, invoice_padding)
			        VALUES (1, 'AUTO', 'INV-', 1, 3);
			""");
			

            System.out.println("✔ All tables are created and ready!");
        }

    }
}