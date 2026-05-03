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
					        invoice_id INTEGER,
					        FOREIGN KEY(client_id) REFERENCES clients(id),
					        FOREIGN KEY(invoice_id) REFERENCES invoice_master(id)
					    );
					""");
					
            // ================== JOBS TABLE MIGRATION ==================
            try {
                stmt.execute("ALTER TABLE jobs ADD COLUMN invoice_id INTEGER REFERENCES invoice_master(id);");
                System.out.println("✔ Migration: Added invoice_id to jobs table");
            } catch (Exception e) {
                // Column probably already exists (SQLite throws if column exists)
            }

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

            // ================== MIGRATION: REMOVE RESTRICTIVE CHECK CONSTRAINT ==================
            // SQLite doesn't support DROP CONSTRAINT. We must check if the constraint exists by 
            // inspecting the schema and if so, recreate the table.
            try {
                String currentSchema = "";
                try (java.sql.ResultSet rs = stmt.executeQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='invoice_master'")) {
                    if (rs.next()) currentSchema = rs.getString(1);
                }
                
                if (currentSchema.contains("CHECK(due_amount = amount - paid_amount)") || currentSchema.contains("CHECK (due_amount = amount - paid_amount)")) {
                    System.out.println("⚠ Migration: Removing restrictive CHECK constraint from invoice_master...");
                    
                    // 1. Rename old table
                    stmt.execute("ALTER TABLE invoice_master RENAME TO invoice_master_old;");
                    
                    // 2. Create new table without the constraint
                    stmt.execute("""
                        CREATE TABLE invoice_master (
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
                            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                            parent_invoice_id INTEGER,
                            FOREIGN KEY (client_id) REFERENCES clients(id)
                        );
                    """);
                    
                    // 3. Copy data
                    stmt.execute("""
                        INSERT INTO invoice_master (
                            id, invoice_no, client_id, client_name, invoice_date, period_from, period_to,
                            amount, paid_amount, due_amount, payment_status, last_payment_date,
                            type, status, is_void, void_reason, void_date, replaced_by_invoice_id,
                            status_updated_by, file_path, created_at
                        )
                        SELECT 
                            id, invoice_no, client_id, client_name, invoice_date, period_from, period_to,
                            amount, paid_amount, due_amount, payment_status, last_payment_date,
                            type, status, is_void, void_reason, void_date, replaced_by_invoice_id,
                            status_updated_by, file_path, created_at
                        FROM invoice_master_old;
                    """);
                    
                    // 4. Drop old table
                    stmt.execute("DROP TABLE invoice_master_old;");
                    
                    // 5. Recreate indexes
                    try { stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_invoice_no ON invoice_master(invoice_no);"); } catch (Exception e) {}
                    try { stmt.execute("DROP INDEX IF EXISTS ux_invoice_active_period;"); } catch (Exception e) {}
                    
                    System.out.println("✔ Migration: Successfully removed restrictive CHECK constraint.");
                }
            } catch (Exception e) {
                System.err.println("❌ Migration failed: " + e.getMessage());
                // If it fails, we try to recover by renaming back if needed, but SQLite RENAME is usually safe.
            }

            // ================== INVOICE_MASTER TABLE MIGRATIONS ==================
            try {
                stmt.execute("ALTER TABLE invoice_master ADD COLUMN parent_invoice_id INTEGER REFERENCES invoice_master(id);");
                System.out.println("✔ Migration: Added parent_invoice_id to invoice_master");
            } catch (Exception e) {
                // Ignore if exists
            }
            
            try {
                stmt.execute("ALTER TABLE invoice_master ADD COLUMN replaced_by_invoice_id INTEGER REFERENCES invoice_master(id);");
            } catch (Exception e) { }

            try {
                stmt.execute("ALTER TABLE invoice_master ADD COLUMN document_series TEXT;");
            } catch (Exception e) { }
            
            // 🔥 Fix for Invoice Revisions: Relax uniqueness to allow "REVISED" invoices to coexist with the new DRAFT
            // We drop all possible variations of the restrictive index
            try { stmt.execute("DROP INDEX IF EXISTS ux_invoice_master_client_period;"); } catch (Exception e) { }
            try { stmt.execute("DROP INDEX IF EXISTS ux_invoice_master_client_type_period;"); } catch (Exception e) { }
            try { stmt.execute("DROP INDEX IF EXISTS ux_invoice_active_period;"); } catch (Exception e) { }
            
            try {
                // Only enforce uniqueness for active invoices (not void, not revised)
                // We include 'type' to ensure it covers both monthly and range-based rules if they were separate
                // Uniqueness on period is no longer enforced to allow multiple invoices for same period if desired.
                // We only enforce invoice number uniqueness.
            } catch (Exception e) { }

            try {
                // Ensure invoice numbers remain globally unique
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_invoice_no ON invoice_master(invoice_no);");
            } catch (Exception e) { }

            try {
                // Ensure cancelled invoices remain visible in the list (not hidden like VOID)
                stmt.execute("UPDATE invoice_master SET is_void = 0 WHERE (status = 'CANCELLED' OR status = 'REVISED') AND is_void = 1;");
            } catch (Exception e) { }

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
			
            // ================== EMAIL SETTINGS TABLE ==================
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS email_settings (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        smtp_host TEXT,
                        smtp_port TEXT,
                        sender_email TEXT,
                        sender_password TEXT,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    );
            """);
            stmt.execute("""
                    INSERT OR IGNORE INTO email_settings 
                    (id, smtp_host, smtp_port, sender_email, sender_password) 
                    VALUES (1, 'smtp.gmail.com', '587', '', '');
            """);

            // ================== MIGRATION: FIX BROKEN FOREIGN KEYS ==================
            // After renaming invoice_master to invoice_master_old, some tables might have their 
            // FKs stuck pointing to the old (now deleted) table.
            try {
                String fullSchema = "";
                try (java.sql.ResultSet rs = stmt.executeQuery("SELECT sql FROM sqlite_master WHERE type='table'")) {
                    while (rs.next()) fullSchema += rs.getString(1) + "\n";
                }
                
                if (fullSchema.contains("invoice_master_old")) {
                    System.out.println("⚠ Migration: Fixing broken foreign key references to 'invoice_master_old'...");
                    stmt.execute("PRAGMA foreign_keys = OFF;");
                    
                    // 1. Fix invoice_adjustments
                    if (fullSchema.contains("CREATE TABLE invoice_adjustments") && fullSchema.contains("REFERENCES \"invoice_master_old\"")) {
                        stmt.execute("ALTER TABLE invoice_adjustments RENAME TO invoice_adjustments_old;");
                        stmt.execute("""
                            CREATE TABLE invoice_adjustments (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                invoice_id INTEGER NOT NULL,
                                type TEXT NOT NULL CHECK (type IN ('Credit Note', 'Debit Note')),
                                note_no TEXT UNIQUE NOT NULL,
                                amount REAL NOT NULL,
                                reason TEXT,
                                date TEXT DEFAULT (date('now')),
                                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                                FOREIGN KEY (invoice_id) REFERENCES invoice_master(id)
                            );
                        """);
                        stmt.execute("INSERT INTO invoice_adjustments SELECT * FROM invoice_adjustments_old;");
                        stmt.execute("DROP TABLE invoice_adjustments_old;");
                    }
                    
                    // 2. Fix payment_allocations
                    if (fullSchema.contains("CREATE TABLE payment_allocations") && fullSchema.contains("REFERENCES \"invoice_master_old\"")) {
                        stmt.execute("ALTER TABLE payment_allocations RENAME TO payment_allocations_old;");
                        stmt.execute("""
                            CREATE TABLE payment_allocations (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                payment_id INTEGER NOT NULL,
                                invoice_id INTEGER NOT NULL,
                                allocated_amount REAL NOT NULL CHECK(allocated_amount > 0),
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                FOREIGN KEY (payment_id) REFERENCES payments(id),
                                FOREIGN KEY (invoice_id) REFERENCES invoice_master(id)
                            );
                        """);
                        stmt.execute("INSERT INTO payment_allocations SELECT * FROM payment_allocations_old;");
                        stmt.execute("DROP TABLE payment_allocations_old;");
                    }
                    
                    // 3. Fix jobs
                    if (fullSchema.contains("CREATE TABLE jobs") && fullSchema.contains("REFERENCES \"invoice_master_old\"")) {
                        stmt.execute("ALTER TABLE jobs RENAME TO jobs_old;");
                        stmt.execute("""
                            CREATE TABLE jobs (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                client_id INTEGER,
                                job_no TEXT UNIQUE NOT NULL,
                                job_title TEXT,
                                job_date TEXT NOT NULL DEFAULT (date('now')),
                                status TEXT NOT NULL DEFAULT 'DRAFT',
                                remarks TEXT,
                                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                                job_number_mode TEXT,
                                image_path TEXT,
                                invoice_id INTEGER REFERENCES invoice_master(id),
                                FOREIGN KEY (client_id) REFERENCES clients(id)
                            );
                        """);
                        // Mapping columns carefully for 'jobs'
                        stmt.execute("""
                            INSERT INTO jobs (id, client_id, job_no, job_title, job_date, status, remarks, created_at, updated_at, job_number_mode, image_path, invoice_id)
                            SELECT id, client_id, job_no, job_title, job_date, status, remarks, created_at, updated_at, job_number_mode, image_path, invoice_id 
                            FROM jobs_old;
                        """);
                        stmt.execute("DROP TABLE jobs_old;");
                    }
                    
                    stmt.execute("PRAGMA foreign_keys = ON;");
                    System.out.println("✔ Migration: All foreign keys fixed.");
                }
            } catch (Exception e) {
                System.err.println("❌ Foreign key migration failed: " + e.getMessage());
                e.printStackTrace();
            }

            // ================== MIGRATION: PAYMENTS REFUND SUPPORT ==================
            try {
                stmt.execute("ALTER TABLE payments ADD COLUMN type TEXT DEFAULT 'Payment';");
                System.out.println("✔ Migration: Added type to payments table");
            } catch (Exception e) {}

            try {
                String paSchema = "";
                try (java.sql.ResultSet rs = stmt.executeQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='payment_allocations'")) {
                    if (rs.next()) paSchema = rs.getString(1);
                }
                
                // If it contains the positive check, we need to recreate it to allow refunds
                if (paSchema.contains("CHECK(allocated_amount > 0)") || paSchema.contains("CHECK (allocated_amount > 0)")) {
                    System.out.println("⚠ Migration: Removing restrictive CHECK constraint from payment_allocations for Refund support...");
                    stmt.execute("ALTER TABLE payment_allocations RENAME TO pa_old;");
                    stmt.execute("""
                        CREATE TABLE payment_allocations (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            payment_id INTEGER NOT NULL,
                            invoice_id INTEGER NOT NULL,
                            allocated_amount REAL NOT NULL,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            FOREIGN KEY (payment_id) REFERENCES payments(id),
                            FOREIGN KEY (invoice_id) REFERENCES invoice_master(id)
                        );
                    """);
                    stmt.execute("INSERT INTO payment_allocations (id, payment_id, invoice_id, allocated_amount, created_at) SELECT id, payment_id, invoice_id, allocated_amount, created_at FROM pa_old;");
                    stmt.execute("DROP TABLE pa_old;");
                    System.out.println("✔ Migration: payment_allocations updated.");
                }
            } catch (Exception e) {
                System.err.println("❌ Migration failed for payment_allocations: " + e.getMessage());
            }

            // ================== JOB ITEMS TABLE ==================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS job_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    job_id INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    description TEXT,
                    amount REAL DEFAULT 0,
                    sort_order INTEGER DEFAULT 0,
                    FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
                );
            """);

            // ================== PRINTING ITEMS TABLE ==================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS printing_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    job_item_id INTEGER NOT NULL UNIQUE,
                    qty INTEGER DEFAULT 0,
                    units TEXT,
                    sets TEXT,
                    color TEXT,
                    side TEXT,
                    with_ctp INTEGER DEFAULT 0,
                    notes TEXT,
                    amount REAL DEFAULT 0,
                    FOREIGN KEY (job_item_id) REFERENCES job_items(id) ON DELETE CASCADE
                );
            """);

            // ================== PAPER ITEMS TABLE ==================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS paper_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    job_item_id INTEGER NOT NULL UNIQUE,
                    qty INTEGER DEFAULT 0,
                    units TEXT,
                    size TEXT,
                    gsm TEXT,
                    type TEXT,
                    source TEXT,
                    notes TEXT,
                    amount REAL DEFAULT 0,
                    FOREIGN KEY (job_item_id) REFERENCES job_items(id) ON DELETE CASCADE
                );
            """);

            // ================== BINDING ITEMS TABLE ==================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS binding_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    job_item_id INTEGER NOT NULL UNIQUE,
                    process TEXT,
                    qty INTEGER DEFAULT 0,
                    rate REAL DEFAULT 0,
                    notes TEXT,
                    amount REAL DEFAULT 0,
                    FOREIGN KEY (job_item_id) REFERENCES job_items(id) ON DELETE CASCADE
                );
            """);

            // ================== LAMINATION ITEMS TABLE ==================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS lamination_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    job_item_id INTEGER NOT NULL UNIQUE,
                    qty INTEGER DEFAULT 0,
                    unit TEXT,
                    type TEXT,
                    side TEXT,
                    size TEXT,
                    notes TEXT,
                    amount REAL DEFAULT 0,
                    FOREIGN KEY (job_item_id) REFERENCES job_items(id) ON DELETE CASCADE
                );
            """);

            // ================== CTP ITEMS TABLE ==================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ctp_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    job_item_id INTEGER NOT NULL UNIQUE,
                    qty INTEGER DEFAULT 0,
                    plate_size TEXT,
                    gauge TEXT,
                    backing TEXT,
                    color TEXT,
                    supplier_id INTEGER,
                    supplier_name TEXT,
                    notes TEXT,
                    amount REAL DEFAULT 0,
                    FOREIGN KEY (job_item_id) REFERENCES job_items(id) ON DELETE CASCADE
                );
            """);

            // ================== SYSTEM_SETTINGS JOBS COLUMNS MIGRATION ==================
            try { stmt.execute("ALTER TABLE jobs ADD COLUMN job_no TEXT;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE jobs ADD COLUMN job_title TEXT;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE jobs ADD COLUMN job_date TEXT;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE jobs ADD COLUMN status TEXT DEFAULT 'Draft';"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE jobs ADD COLUMN remarks TEXT;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE jobs ADD COLUMN job_number_mode TEXT;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE jobs ADD COLUMN image_path TEXT;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE jobs ADD COLUMN child_status TEXT;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE jobs ADD COLUMN created_at TEXT DEFAULT CURRENT_TIMESTAMP;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE jobs ADD COLUMN updated_at TEXT DEFAULT CURRENT_TIMESTAMP;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE system_settings ADD COLUMN last_temp_invoice_no INTEGER DEFAULT 0;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE system_settings ADD COLUMN numbering_fy TEXT DEFAULT '';"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE system_settings ADD COLUMN last_seq_inv INTEGER DEFAULT 0;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE system_settings ADD COLUMN last_seq_pi INTEGER DEFAULT 0;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE system_settings ADD COLUMN last_seq_cn INTEGER DEFAULT 0;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE system_settings ADD COLUMN last_seq_dn INTEGER DEFAULT 0;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE system_settings ADD COLUMN last_seq_qtn INTEGER DEFAULT 0;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE system_settings ADD COLUMN last_seq_po INTEGER DEFAULT 0;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE system_settings ADD COLUMN last_seq_job INTEGER DEFAULT 0;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE system_settings ADD COLUMN last_seq_tkt INTEGER DEFAULT 0;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE system_settings ADD COLUMN last_seq_dc INTEGER DEFAULT 0;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE system_settings ADD COLUMN last_seq_ewb INTEGER DEFAULT 0;"); } catch (Exception e) {}
            try {
                stmt.execute("""
                        UPDATE system_settings SET
                          numbering_fy = CASE WHEN numbering_fy IS NULL OR numbering_fy = '' THEN '' ELSE numbering_fy END,
                          last_seq_inv = CASE WHEN last_seq_inv IS NULL OR last_seq_inv = 0 THEN COALESCE(last_invoice_no, 0) ELSE last_seq_inv END,
                          last_seq_job = CASE WHEN last_seq_job IS NULL OR last_seq_job = 0 THEN COALESCE(last_job_no, 0) ELSE last_seq_job END
                        WHERE id = 1
                        """);
            } catch (Exception e) { }

            // ================== INVOICE JOB MAPPING TABLE (NEW) ==================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS invoice_job_mapping (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    invoice_id INTEGER NOT NULL,
                    job_id INTEGER NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (invoice_id) REFERENCES invoice_master(id) ON DELETE CASCADE,
                    FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
                    UNIQUE(invoice_id, job_id)
                );
            """);

            // Migration: Populate mapping from existing jobs.invoice_id (one-time fix)
            try {
                stmt.execute("INSERT OR IGNORE INTO invoice_job_mapping (invoice_id, job_id) SELECT invoice_id, id FROM jobs WHERE invoice_id IS NOT NULL;");
            } catch (Exception e) {}

            // Strip legacy leading '#' from stored reference numbers
            try {
                stmt.execute("""
                        UPDATE invoice_master
                        SET invoice_no = TRIM(SUBSTR(invoice_no, 2))
                        WHERE invoice_no LIKE '#%' AND LENGTH(invoice_no) > 1
                        """);
            } catch (Exception e) {}
            try {
                stmt.execute("""
                        UPDATE invoice_history
                        SET invoice_no = TRIM(SUBSTR(invoice_no, 2))
                        WHERE invoice_no LIKE '#%' AND LENGTH(invoice_no) > 1
                        """);
            } catch (Exception e) {}
            try {
                stmt.execute("""
                        UPDATE invoice_adjustments
                        SET note_no = TRIM(SUBSTR(note_no, 2))
                        WHERE note_no LIKE '#%' AND LENGTH(note_no) > 1
                        """);
            } catch (Exception e) {}
            try {
                stmt.execute("""
                        UPDATE jobs
                        SET job_no = TRIM(SUBSTR(job_no, 2))
                        WHERE job_no LIKE '#%' AND LENGTH(job_no) > 1
                        """);
            } catch (Exception e) {}
            // Legacy: some DBs still have job_name; copy into job_title when title is empty
            try {
                stmt.execute("""
                        UPDATE jobs
                        SET job_title = job_name
                        WHERE job_name IS NOT NULL AND TRIM(job_name) != ''
                          AND (job_title IS NULL OR TRIM(job_title) = '')
                        """);
            } catch (Exception e) {}
            // Canonical job display no.: JOB-<primary key id>
            try {
                stmt.execute("UPDATE jobs SET job_no = 'JOB-' || id WHERE id IS NOT NULL");
            } catch (Exception e) {}

            System.out.println("✔ All tables are created and ready!");

        }

    }
}