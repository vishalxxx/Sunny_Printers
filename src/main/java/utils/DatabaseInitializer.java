package utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;

public class DatabaseInitializer {
    
    private static final String SYNC_COLUMNS = """
            sync_status TEXT DEFAULT 'PENDING',
            sync_version INTEGER DEFAULT 1,
            is_deleted INTEGER DEFAULT 0,
            is_active INTEGER DEFAULT 1,
            created_at TEXT DEFAULT (datetime('now')),
            updated_at TEXT DEFAULT (datetime('now')),
            synced_at TEXT DEFAULT NULL,
            deleted_at TEXT DEFAULT NULL
            """;

    /** SQLite expression for a new random UUID v4-style string. Never pass through {@code String.formatted} (contains {@code %}). */
    private static final String NEW_UUID_SQL =
            "lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))";

    /**
     * Local SQLite bootstrap. Remote parity: {@code supabase/migrations/012_invoice_and_payments_uuid_schema.sql}
     * (after clients/jobs UUID migrations). Legacy integer-PK SQLite DBs are upgraded on startup.
     */
    public static void initialize() throws Exception
 {

		Files.createDirectories(Path.of("database"));

        // Open a direct JDBC connection here to avoid calling DBConnection.getConnection()
        // which itself calls DatabaseInitializer.initialize() and causes recursion.
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:database/sunnyprinters.db");
             Statement stmt = conn.createStatement()) {

            dropLeftoverMigrationBackupTables(conn, stmt);

            stmt.execute(CLIENTS_DDL);
            ensureClientsTable(conn, stmt);
            ensureJobsTable(conn, stmt);
            ensureInvoiceMasterUuid(conn, stmt);
            ensureSystemSettingsUuid(conn, stmt);
            ensureEmailSettingsUuid(conn, stmt);
            ensureSupabaseSettingsUuid(conn, stmt);
            ensureJobItemsUuid(conn, stmt);
            ensureJobItemDetailTablesUuid(conn, stmt);
            ensureSuppliersUuid(conn, stmt);
            ensurePaymentsUuid(conn, stmt);
            ensureInvoiceAdjustmentsUuid(conn, stmt);
            ensureDocumentNumberMappingsTable(conn, stmt);
            ensurePaperItemsSupplierColumns(conn, stmt);

            // ================== SUPPLIERS TABLE ==================
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS suppliers (
                        uuid TEXT PRIMARY KEY NOT NULL,
                        supplier_code TEXT DEFAULT '',
                        name TEXT NOT NULL DEFAULT '',
                        business_name TEXT DEFAULT '',
                        type TEXT NOT NULL DEFAULT '',
                        phone TEXT DEFAULT '',
                        address TEXT DEFAULT '',
                        gst_number TEXT DEFAULT '',
                        created_by_user_uuid TEXT DEFAULT NULL,
                        updated_by_user_uuid TEXT DEFAULT NULL,
                        %s
                    );
                    """.formatted(SYNC_COLUMNS));
            try {
                if (!columnExists(conn, "suppliers", "supplier_code")) {
                    stmt.execute("ALTER TABLE suppliers ADD COLUMN supplier_code TEXT DEFAULT '';");
                    System.out.println("✔ Migration: Added supplier_code column to suppliers table.");
                }
                if (!columnExists(conn, "suppliers", "created_by_user_uuid")) {
                    stmt.execute("ALTER TABLE suppliers ADD COLUMN created_by_user_uuid TEXT DEFAULT NULL;");
                }
                if (!columnExists(conn, "suppliers", "updated_by_user_uuid")) {
                    stmt.execute("ALTER TABLE suppliers ADD COLUMN updated_by_user_uuid TEXT DEFAULT NULL;");
                }
                if (!columnExists(conn, "suppliers", "mobile")) {
                    stmt.execute("ALTER TABLE suppliers ADD COLUMN mobile TEXT DEFAULT '';");
                }
                if (!columnExists(conn, "suppliers", "email")) {
                    stmt.execute("ALTER TABLE suppliers ADD COLUMN email TEXT DEFAULT '';");
                }
                if (!columnExists(conn, "suppliers", "website")) {
                    stmt.execute("ALTER TABLE suppliers ADD COLUMN website TEXT DEFAULT '';");
                }
                if (!columnExists(conn, "suppliers", "state")) {
                    stmt.execute("ALTER TABLE suppliers ADD COLUMN state TEXT DEFAULT '';");
                }
                if (!columnExists(conn, "suppliers", "city")) {
                    stmt.execute("ALTER TABLE suppliers ADD COLUMN city TEXT DEFAULT '';");
                }
                if (!columnExists(conn, "suppliers", "pincode")) {
                    stmt.execute("ALTER TABLE suppliers ADD COLUMN pincode TEXT DEFAULT '';");
                }
                if (!columnExists(conn, "suppliers", "payment_terms")) {
                    stmt.execute("ALTER TABLE suppliers ADD COLUMN payment_terms TEXT DEFAULT '';");
                }
                if (!columnExists(conn, "suppliers", "credit_limit")) {
                    stmt.execute("ALTER TABLE suppliers ADD COLUMN credit_limit REAL DEFAULT 0;");
                }
                if (!columnExists(conn, "suppliers", "notes")) {
                    stmt.execute("ALTER TABLE suppliers ADD COLUMN notes TEXT DEFAULT '';");
                }
            } catch (Exception e) {
                System.err.println("Migration failed: adding new columns to suppliers: " + e.getMessage());
            }
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_suppliers_type ON suppliers(type);");
            } catch (Exception ignored) {
            }

            stmt.execute(JOBS_DDL);

            // ================== BILLING TABLE ==================
            stmt.execute("""
					    CREATE TABLE IF NOT EXISTS billing (
					        uuid TEXT PRIMARY KEY NOT NULL,
					        job_uuid TEXT,
					        client_uuid TEXT,
					        amount REAL,
					        bill_date TEXT,
					        %s,
					        FOREIGN KEY(job_uuid) REFERENCES jobs(uuid),
					        FOREIGN KEY(client_uuid) REFERENCES clients(uuid)
					    );
					""".formatted(SYNC_COLUMNS));

            stmt.execute(PAYMENTS_DDL);
            stmt.execute(PAYMENT_ALLOCATIONS_DDL);
            stmt.execute(PAYMENT_DETAILS_DDL);

            // ================== USERS TABLE ==================
            boolean needUsersMigration = false;
            try (java.sql.ResultSet rs = stmt.executeQuery("PRAGMA table_info(users)")) {
                boolean hasUuid = false;
                boolean hasEmail = false;
                boolean hasTable = false;
                while (rs.next()) {
                    hasTable = true;
                    String colName = rs.getString("name");
                    if ("uuid".equalsIgnoreCase(colName)) {
                        hasUuid = true;
                    } else if ("email".equalsIgnoreCase(colName)) {
                        hasEmail = true;
                    }
                }
                if (hasTable && (!hasUuid || hasEmail)) {
                    needUsersMigration = true;
                }
            } catch (Exception ignored) {}

            if (needUsersMigration) {
                System.out.println("⚠ Migration: Recreating users table without email column...");
                stmt.execute("DROP TABLE IF EXISTS users;");
            }

            stmt.execute("""
					    CREATE TABLE IF NOT EXISTS users (
					        uuid TEXT PRIMARY KEY NOT NULL,
					        username TEXT UNIQUE,
					        password TEXT,
					        role TEXT,
					        %s
					    );
					""".formatted(SYNC_COLUMNS));

            stmt.execute("""
                    INSERT OR IGNORE INTO users
                    (uuid, username, password, role, sync_status)
                    VALUES ('00000000-0000-0000-0000-00000000000a', 'Admin', 'admin', 'ADMIN', 'SYNCED');
                    """);

            stmt.execute(INVOICE_MASTER_DDL);
            stmt.execute(INVOICE_ADJUSTMENTS_DDL);

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
                            uuid TEXT PRIMARY KEY NOT NULL,
                            invoice_no TEXT,
                            client_uuid TEXT,
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
                            replaced_by_invoice_uuid TEXT,
                            parent_invoice_uuid TEXT,
                            status_updated_by TEXT,
                            file_path TEXT,
                            document_series TEXT,
                            %s,
                            FOREIGN KEY (client_uuid) REFERENCES clients(uuid)
                        );
                    """.formatted(SYNC_COLUMNS));
                    
                    // 3. Copy data
                    stmt.execute("""
                        INSERT INTO invoice_master (
                            uuid, invoice_no, client_uuid, client_name, invoice_date, period_from, period_to,
                            amount, paid_amount, due_amount, payment_status, last_payment_date,
                            type, status, is_void, void_reason, void_date,
                            status_updated_by, file_path, created_at
                        )
                        SELECT 
                            COALESCE(uuid, lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))),
                            invoice_no, client_id, client_name, invoice_date, period_from, period_to,
                            amount, paid_amount, due_amount, payment_status, last_payment_date,
                            type, status, is_void, void_reason, void_date,
                            status_updated_by, file_path, created_at
                        FROM invoice_master_old;
                    """);
                    
                    // 4. Drop old table
                    stmt.execute("DROP TABLE invoice_master_old;");
                    
                    // 5. Recreate indexes
                    try { stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_invoice_no ON invoice_master(invoice_no);"); } catch (Exception e) {}
                    try { stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_invoice_master_uuid ON invoice_master(uuid);"); } catch (Exception e) {}
                    
                    System.out.println("✔ Migration: Successfully updated invoice_master to UUID PK schema.");
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
                stmt.execute("""
                        CREATE UNIQUE INDEX IF NOT EXISTS ux_invoice_master_invoice_no
                        ON invoice_master(invoice_no)
                        WHERE invoice_no IS NOT NULL AND is_deleted = 0
                        """);
            } catch (Exception e) { }

            try {
                // Ensure cancelled invoices remain visible in the list (not hidden like VOID)
                stmt.execute("UPDATE invoice_master SET is_void = 0 WHERE (status = 'CANCELLED' OR status = 'REVISED') AND is_void = 1;");
            } catch (Exception e) { }

            stmt.execute("""
			        CREATE TABLE IF NOT EXISTS system_settings (
			            uuid TEXT PRIMARY KEY NOT NULL,
			            invoice_mode TEXT NOT NULL CHECK (invoice_mode IN ('AUTO','MANUAL')),
			            invoice_prefix TEXT,
			            invoice_start_no INTEGER,
			            invoice_padding INTEGER,
			            %s
			        );
			""".formatted(SYNC_COLUMNS));

            // Ensure default configuration row exists
            stmt.execute("""
			        INSERT OR IGNORE INTO system_settings
			        (uuid, invoice_mode, invoice_prefix, invoice_start_no, invoice_padding)
			        VALUES ('00000000-0000-0000-0000-000000000001', 'AUTO', 'INV-', 1, 4);
			""");

            // Columns expected by SystemSettingsRepository / numbering (safe on existing DBs)
            try { stmt.execute("ALTER TABLE system_settings ADD COLUMN last_invoice_no INTEGER DEFAULT 0;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE system_settings ADD COLUMN last_job_no INTEGER DEFAULT 0;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE system_settings ADD COLUMN job_prefix TEXT DEFAULT 'SUN-';"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE system_settings ADD COLUMN job_start_no INTEGER DEFAULT 1;"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE system_settings ADD COLUMN job_padding INTEGER DEFAULT 4;"); } catch (Exception e) {}
			
            // ================== EMAIL SETTINGS TABLE ==================
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS email_settings (
                        uuid TEXT PRIMARY KEY NOT NULL,
                        smtp_host TEXT,
                        smtp_port TEXT,
                        sender_email TEXT,
                        sender_password TEXT,
                        %s
                    );
            """.formatted(SYNC_COLUMNS));
            stmt.execute("""
                    INSERT OR IGNORE INTO email_settings 
                    (uuid, smtp_host, smtp_port, sender_email, sender_password) 
                    VALUES ('00000000-0000-0000-0000-000000000002', 'smtp.gmail.com', '587', '', '');
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
                    if (fullSchema.contains("CREATE TABLE invoice_adjustments") && (fullSchema.contains("REFERENCES \"invoice_master_old\"") || fullSchema.contains("invoice_id INTEGER"))) {
                        stmt.execute("ALTER TABLE invoice_adjustments RENAME TO invoice_adjustments_old;");
                        stmt.execute("""
                            CREATE TABLE invoice_adjustments (
                                uuid TEXT PRIMARY KEY NOT NULL,
                                invoice_uuid TEXT NOT NULL,
                                type TEXT NOT NULL CHECK (type IN ('Credit Note', 'Debit Note')),
                                note_no TEXT UNIQUE NOT NULL,
                                amount REAL NOT NULL,
                                reason TEXT,
                                date TEXT DEFAULT (date('now')),
                                %s,
                                FOREIGN KEY (invoice_uuid) REFERENCES invoice_master(uuid)
                            );
                        """.formatted(SYNC_COLUMNS));
                        // Re-map IDs to UUIDs if needed, or just generate fresh UUIDs
                        stmt.execute("""
                            INSERT INTO invoice_adjustments (uuid, invoice_uuid, type, note_no, amount, reason, date, created_at)
                            SELECT 
                                lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))),
                                (SELECT im.uuid FROM invoice_master im WHERE im.id = old.invoice_id),
                                type, note_no, amount, reason, date, created_at
                            FROM invoice_adjustments_old old;
                        """);
                        stmt.execute("DROP TABLE invoice_adjustments_old;");
                    }
                    
                    // 2. Fix payment_allocations
                    if (fullSchema.contains("CREATE TABLE payment_allocations") && (fullSchema.contains("REFERENCES \"invoice_master_old\"") || fullSchema.contains("invoice_id INTEGER"))) {
                        stmt.execute("ALTER TABLE payment_allocations RENAME TO payment_allocations_old;");
                        stmt.execute("""
                            CREATE TABLE payment_allocations (
                                uuid TEXT PRIMARY KEY NOT NULL,
                                payment_uuid TEXT NOT NULL,
                                invoice_uuid TEXT NOT NULL,
                                allocated_amount REAL NOT NULL,
                                sync_status TEXT DEFAULT 'PENDING',
                                sync_version INTEGER DEFAULT 1,
                                is_deleted INTEGER DEFAULT 0,
                                is_active INTEGER DEFAULT 1,
                                created_at TEXT DEFAULT (datetime('now')),
                                updated_at TEXT DEFAULT (datetime('now')),
                                synced_at TEXT DEFAULT NULL,
                                deleted_at TEXT DEFAULT NULL,
                                FOREIGN KEY (payment_uuid) REFERENCES payments(uuid),
                                FOREIGN KEY (invoice_uuid) REFERENCES invoice_master(uuid)
                            );
                        """);
                        stmt.execute("""
                            INSERT INTO payment_allocations (uuid, payment_uuid, invoice_uuid, allocated_amount, created_at)
                            SELECT 
                                lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))),
                                COALESCE(payment_uuid, (SELECT p.uuid FROM payments p WHERE p.id = old.payment_id)),
                                COALESCE(invoice_uuid, (SELECT im.uuid FROM invoice_master im WHERE im.id = old.invoice_id)),
                                allocated_amount, created_at
                            FROM payment_allocations_old old;
                        """);
                        stmt.execute("DROP TABLE payment_allocations_old;");
                    }
                    
                    // 3. Fix jobs
                    if (fullSchema.contains("CREATE TABLE jobs") && fullSchema.contains("REFERENCES \"invoice_master_old\"")) {
                        stmt.execute("ALTER TABLE jobs RENAME TO jobs_old;");
                        stmt.execute(JOBS_DDL);
                        stmt.execute("""
                            INSERT INTO jobs (uuid, client_uuid, invoice_uuid, job_code, job_title, status, created_at, updated_at)
                            SELECT 
                                lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))),
                                client_id,
                                (SELECT im.uuid FROM invoice_master im WHERE im.id = old.invoice_id),
                                job_no, job_title, status, created_at, updated_at
                            FROM jobs_old old;
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
                            uuid TEXT PRIMARY KEY NOT NULL,
                            payment_uuid TEXT NOT NULL,
                            invoice_uuid TEXT NOT NULL,
                            allocated_amount REAL NOT NULL,
                            sync_status TEXT DEFAULT 'PENDING',
                            sync_version INTEGER DEFAULT 1,
                            is_deleted INTEGER DEFAULT 0,
                            is_active INTEGER DEFAULT 1,
                            created_at TEXT DEFAULT (datetime('now')),
                            updated_at TEXT DEFAULT (datetime('now')),
                            synced_at TEXT DEFAULT NULL,
                            deleted_at TEXT DEFAULT NULL,
                            FOREIGN KEY (payment_uuid) REFERENCES payments(uuid),
                            FOREIGN KEY (invoice_uuid) REFERENCES invoice_master(uuid)
                        );
                    """);
                    stmt.execute("INSERT INTO payment_allocations (id, payment_id, invoice_id, allocated_amount, created_at) SELECT id, payment_id, invoice_id, allocated_amount, created_at FROM pa_old;");
                    stmt.execute("DROP TABLE pa_old;");
                    System.out.println("✔ Migration: payment_allocations updated.");
                }
            } catch (Exception e) {
                System.err.println("❌ Migration failed for payment_allocations: " + e.getMessage());
            }

            stmt.execute(JOB_ITEMS_DDL);

            // ================== PRINTING ITEMS TABLE ==================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS printing_items (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    job_item_uuid TEXT NOT NULL UNIQUE,
                    qty INTEGER DEFAULT 0,
                    units TEXT,
                    sets TEXT,
                    color TEXT,
                    side TEXT,
                    with_ctp INTEGER DEFAULT 0,
                    notes TEXT,
                    amount REAL DEFAULT 0,
                    %s,
                    FOREIGN KEY (job_item_uuid) REFERENCES job_items(uuid) ON DELETE CASCADE
                );
            """.formatted(SYNC_COLUMNS));

            // ================== PAPER ITEMS TABLE ==================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS paper_items (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    job_item_uuid TEXT NOT NULL UNIQUE,
                    qty INTEGER DEFAULT 0,
                    units TEXT,
                    size TEXT,
                    gsm TEXT,
                    type TEXT,
                    source TEXT,
                    supplier_uuid TEXT,
                    supplier_name TEXT,
                    notes TEXT,
                    amount REAL DEFAULT 0,
                    %s,
                    FOREIGN KEY (job_item_uuid) REFERENCES job_items(uuid) ON DELETE CASCADE,
                    FOREIGN KEY (supplier_uuid) REFERENCES suppliers(uuid)
                );
            """.formatted(SYNC_COLUMNS));

            // ================== BINDING ITEMS TABLE ==================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS binding_items (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    job_item_uuid TEXT NOT NULL UNIQUE,
                    process TEXT,
                    qty INTEGER DEFAULT 0,
                    rate REAL DEFAULT 0,
                    notes TEXT,
                    amount REAL DEFAULT 0,
                    %s,
                    FOREIGN KEY (job_item_uuid) REFERENCES job_items(uuid) ON DELETE CASCADE
                );
            """.formatted(SYNC_COLUMNS));

            // ================== LAMINATION ITEMS TABLE ==================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS lamination_items (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    job_item_uuid TEXT NOT NULL UNIQUE,
                    qty INTEGER DEFAULT 0,
                    unit TEXT,
                    type TEXT,
                    side TEXT,
                    size TEXT,
                    notes TEXT,
                    amount REAL DEFAULT 0,
                    %s,
                    FOREIGN KEY (job_item_uuid) REFERENCES job_items(uuid) ON DELETE CASCADE
                );
            """.formatted(SYNC_COLUMNS));

            // ================== CTP ITEMS TABLE ==================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ctp_items (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    job_item_uuid TEXT NOT NULL UNIQUE,
                    qty INTEGER DEFAULT 0,
                    plate_size TEXT,
                    gauge TEXT,
                    backing TEXT,
                    color TEXT,
                    supplier_uuid TEXT,
                    supplier_name TEXT,
                    notes TEXT,
                    amount REAL DEFAULT 0,
                    %s,
                    FOREIGN KEY (job_item_uuid) REFERENCES job_items(uuid) ON DELETE CASCADE,
                    FOREIGN KEY (supplier_uuid) REFERENCES suppliers(uuid)
                );
            """.formatted(SYNC_COLUMNS));

            // ================== HSN / SAC MASTER TABLE (GST) ==================
            // Stores HSN/SAC + GST rate mapping per job item type (PRINTING/PAPER/BINDING/LAMINATION/CTP).
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS hsn_sac_master (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    item_type TEXT NOT NULL,                  -- PRINTING | PAPER | BINDING | LAMINATION | CTP | OTHER
                    item_name TEXT NOT NULL DEFAULT '',
                    keyword TEXT NOT NULL DEFAULT '',          -- optional: description keyword match (case-insensitive)
                    hsn_sac TEXT NOT NULL,
                    gst_rate REAL NOT NULL DEFAULT 0.18,      -- e.g. 0.18 for 18%%
                    unit_default TEXT,                        -- optional override: PCS/SHEET/SET etc.
                    %s,
                    UNIQUE(item_type, keyword)
                );
            """.formatted(SYNC_COLUMNS));

            migrateHsnSacMasterIfNeeded(conn);
            seedDefaultHsnSacRows(stmt);

            // ================== BANK DETAILS (INVOICE FOOTER / GST BANK BLOCK) ==================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS bank_details (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    bank_name TEXT NOT NULL,
                    account_holder_name TEXT NOT NULL DEFAULT '',
                    account_no TEXT NOT NULL DEFAULT '',
                    branch_ifsc TEXT NOT NULL DEFAULT '',
                    branch_name TEXT NOT NULL DEFAULT '',
                    ifsc_code TEXT NOT NULL DEFAULT '',
                    is_default INTEGER NOT NULL DEFAULT 0,
                    %s
                );
                """.formatted(SYNC_COLUMNS));
            try {
                stmt.execute("ALTER TABLE bank_details ADD COLUMN branch_name TEXT NOT NULL DEFAULT '';");
            } catch (Exception e) {
            }
            try {
                stmt.execute("ALTER TABLE bank_details ADD COLUMN ifsc_code TEXT NOT NULL DEFAULT '';");
            } catch (Exception e) {
            }
            try {
                stmt.execute("""
                    INSERT INTO bank_details (uuid, bank_name, account_holder_name, account_no, branch_ifsc, is_default, is_active)
                    SELECT lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))),
                           'INDIAN OVERSEAS BANK', 'SUNNY PRINTER', '15980200000000780', 'PITAMPURA & IOBA0001598', 1, 1
                    WHERE NOT EXISTS (SELECT 1 FROM bank_details)
                    """);
            } catch (Exception e) {
            }

            // ================== COMPANY DETAILS (MULTI-COMPANY) ==================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS company_details (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    trade_name TEXT NOT NULL,
                    address TEXT NOT NULL DEFAULT '',
                    phone TEXT NOT NULL DEFAULT '',
                    alt_phone TEXT NOT NULL DEFAULT '',
                    email TEXT NOT NULL DEFAULT '',
                    gstin TEXT NOT NULL DEFAULT '',
                    state TEXT NOT NULL DEFAULT '',
                    is_default INTEGER NOT NULL DEFAULT 0,
                    %s
                );
                """.formatted(SYNC_COLUMNS));
            try {
                stmt.execute("ALTER TABLE company_details ADD COLUMN alt_phone TEXT NOT NULL DEFAULT '';");
            } catch (Exception e) {
            }
            try {
                String trade = utils.CompanyProfile.getName().replace("'", "''");
                String addr = utils.CompanyProfile.getAddress().replace("'", "''");
                String phone = utils.CompanyProfile.getPhone().replace("'", "''");
                String email = utils.CompanyProfile.getEmail().replace("'", "''");
                String gst = utils.CompanyProfile.getGst().replace("'", "''");
                stmt.execute("INSERT INTO company_details (trade_name,address,phone,alt_phone,email,gstin,state,is_default,is_active) "
                        + "SELECT '" + trade + "','" + addr + "','" + phone + "','','" + email + "','" + gst
                        + "','',1,1 WHERE NOT EXISTS (SELECT 1 FROM company_details)");
            } catch (Exception e) {
            }

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS supabase_settings (
                        uuid TEXT PRIMARY KEY NOT NULL,
                        supabase_url TEXT NOT NULL DEFAULT '',
                        anon_key TEXT NOT NULL DEFAULT '',
                        auth_email TEXT NOT NULL DEFAULT '',
                        auth_password TEXT NOT NULL DEFAULT '',
                        %s
                    );
                    """.formatted(SYNC_COLUMNS));
            stmt.execute("""
                    INSERT OR IGNORE INTO supabase_settings
                    (uuid, supabase_url, anon_key, auth_email, auth_password)
                    VALUES ('00000000-0000-0000-0000-000000000003', '', '', '', '');
                    """);

            ensureInvoiceMasterUuid(conn, stmt);
            ensureJobsTable(conn, stmt);
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
                        WHERE uuid = '00000000-0000-0000-0000-000000000001'
                        """);
            } catch (Exception e) { }

            ensureNumberSequences(conn, stmt);

            stmt.execute(INVOICE_JOB_MAPPING_DDL);
            reconcileOrphanedInvoiceMasters(conn, stmt);
            ensureInvoiceJobMappingFromJobs(stmt);
            backfillInvoiceJobMappingUuids(stmt);
            syncJobsInvoiceUuidFromMappings(stmt);
            syncAllJobAmountsFromItems(stmt);

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

            ensureInvoicePaymentCanonicalSchema(conn, stmt);
            ensureUuidOnlySchema(conn, stmt);
            System.out.println("✔ All tables are created and ready!");

        }

    }

    /**
     * Seed baseline Tax Master rows after {@link #migrateHsnSacMasterIfNeeded(Connection)}.
     */
    public static void seedDefaultHsnSacRows(Statement stmt) {
        String[][] rows = {
                { "PRINTING", "General — Printing" },
                { "PAPER", "General — Paper" },
                { "BINDING", "General — Binding" },
                { "LAMINATION", "General — Lamination" },
                { "CTP", "General — CTP" }
        };
        for (String[] r : rows) {
            try {
                stmt.execute("""
                        INSERT INTO hsn_sac_master (uuid, item_type, item_name, keyword, code_type, hsn_sac, gst_rate, unit_default, description, is_favorite, sync_status)
                        SELECT lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))),
                               '%s', '%s', '', 'HSN', '—', 0.18, 'PCS', '', 0, 'PENDING'
                        WHERE NOT EXISTS (
                          SELECT 1 FROM hsn_sac_master WHERE upper(item_type)=upper('%s') AND trim(coalesce(keyword,''))='' AND hsn_sac='—'
                        )
                        """.formatted(r[0], r[1].replace("'", "''"), r[0]));
            } catch (Exception ignored) {
            }
        }
    }

    /** Canonical {@code clients} shape (SQLite). Primary key is UUID v7 in {@code uuid}. */
    private static final String CLIENTS_DDL = """
            CREATE TABLE IF NOT EXISTS clients (
                uuid TEXT PRIMARY KEY NOT NULL,
                client_code TEXT UNIQUE NOT NULL,
                client_name TEXT NOT NULL DEFAULT '',
                business_name TEXT DEFAULT '',
                mobile TEXT DEFAULT '',
                alternate_mobile TEXT DEFAULT '',
                email TEXT DEFAULT '',
                gstin TEXT DEFAULT '',
                pan_number TEXT DEFAULT '',
                billing_address TEXT DEFAULT '',
                shipping_address TEXT DEFAULT '',
                client_type TEXT DEFAULT 'Regular',
                price_category TEXT DEFAULT '',
                credit_limit REAL DEFAULT 0,
                payment_terms TEXT DEFAULT '',
                opening_balance REAL DEFAULT 0,
                balance_type TEXT DEFAULT 'DR',
                sync_status TEXT DEFAULT 'PENDING',
                sync_version INTEGER DEFAULT 1,
                is_deleted INTEGER DEFAULT 0,
                is_active INTEGER DEFAULT 1,
                created_at TEXT DEFAULT (datetime('now')),
                updated_at TEXT DEFAULT (datetime('now')),
                synced_at TEXT DEFAULT NULL,
                deleted_at TEXT DEFAULT NULL,
                notes TEXT DEFAULT '',
                created_by_user_uuid TEXT DEFAULT NULL,
                updated_by_user_uuid TEXT DEFAULT NULL
            )
            """;

    private static String nz(String s) {
        return s != null ? s : "";
    }

    private static boolean clientsNeedsCanonicalSchemaReset(Connection conn) throws java.sql.SQLException {
        if (!tableExists(conn, "clients")) {
            return false;
        }
        if (!columnExists(conn, "clients", "billing_address")) {
            return true;
        }
        if (!columnExists(conn, "clients", "is_active")) {
            return true;
        }
        return columnExists(conn, "clients", "address_line1")
                || columnExists(conn, "clients", "contact_person")
                || columnExists(conn, "clients", "company_name");
    }

    /**
     * Replaces {@code clients} with the canonical schema and clears all client rows. Unlinks {@code client_id} on
     * jobs, invoices, payments, and billing so FK constraints do not block the drop.
     */
    private static void migrateClientsResetToCanonicalSchema(Connection conn, Statement stmt) throws Exception {
        stmt.execute("PRAGMA foreign_keys=OFF");
        stmt.execute("BEGIN IMMEDIATE");
        try {
            clearClientIdReferences(stmt);
            stmt.execute("DROP TABLE IF EXISTS clients");
            stmt.execute(CLIENTS_DDL.replace("IF NOT EXISTS ", ""));
            stmt.execute("COMMIT");
        } catch (Exception e) {
            try {
                stmt.execute("ROLLBACK");
            } catch (Exception ignored) {
            }
            throw e;
        } finally {
            try {
                stmt.execute("PRAGMA foreign_keys=ON");
            } catch (Exception ignored) {
            }
        }
        System.out.println("Migration: clients reset to canonical schema (all client rows removed)");
    }

    private static void clearClientIdReferences(Statement stmt) {
        String[] sqls = {
                "UPDATE jobs SET client_id = NULL WHERE client_id IS NOT NULL",
                "UPDATE invoice_master SET client_id = NULL WHERE client_id IS NOT NULL",
                "UPDATE payments SET client_id = NULL WHERE client_id IS NOT NULL",
                "UPDATE billing SET client_id = NULL WHERE client_id IS NOT NULL"
        };
        for (String sql : sqls) {
            try {
                stmt.executeUpdate(sql);
            } catch (Exception ignored) {
            }
        }
    }

    private static void ensureClientsTable(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "clients")) {
            return;
        }
        if (clientsNeedsCanonicalSchemaReset(conn)) {
            migrateClientsResetToCanonicalSchema(conn, stmt);
        } else if (clientsHasIntegerPk(conn)) {
            migrateClientsToUuidPrimaryKey(conn, stmt);
        }
        ensureUniqueSqliteClientCodeConstraint(conn, stmt);
        
        if (!columnExists(conn, "clients", "created_by_user_uuid")) {
            stmt.execute("ALTER TABLE clients ADD COLUMN created_by_user_uuid TEXT DEFAULT NULL;");
            System.out.println("✔ Migration: Added created_by_user_uuid column to clients table.");
        }
        if (!columnExists(conn, "clients", "credit_limit")) {
            stmt.execute("ALTER TABLE clients ADD COLUMN credit_limit REAL DEFAULT 0;");
            System.out.println("✔ Migration: Added credit_limit column to clients table.");
        }
        if (!columnExists(conn, "clients", "opening_balance")) {
            stmt.execute("ALTER TABLE clients ADD COLUMN opening_balance REAL DEFAULT 0;");
            System.out.println("✔ Migration: Added opening_balance column to clients table.");
        }
        if (!columnExists(conn, "clients", "updated_by_user_uuid")) {
            stmt.execute("ALTER TABLE clients ADD COLUMN updated_by_user_uuid TEXT DEFAULT NULL;");
            System.out.println("✔ Migration: Added updated_by_user_uuid column to clients table.");
        }
    }

    /** Canonical {@code jobs} shape: UUID v7 primary key. */
    private static final String JOBS_DDL = """
            CREATE TABLE IF NOT EXISTS jobs (
                uuid TEXT PRIMARY KEY NOT NULL,
                client_uuid TEXT NOT NULL,
                invoice_uuid TEXT DEFAULT NULL,
                job_code TEXT UNIQUE NOT NULL,
                job_title TEXT DEFAULT '',
                job_type TEXT DEFAULT '',
                description TEXT DEFAULT '',
                amount REAL DEFAULT 0,
                status TEXT DEFAULT 'Draft',
                child_status TEXT DEFAULT '',
                job_number_mode TEXT DEFAULT 'AUTO',
                image_path TEXT DEFAULT '',
                remarks TEXT DEFAULT '',
                job_date TEXT DEFAULT (datetime('now')),
                delivery_date TEXT DEFAULT NULL,
                sync_status TEXT DEFAULT 'PENDING',
                sync_version INTEGER DEFAULT 1,
                is_deleted INTEGER DEFAULT 0,
                is_active INTEGER DEFAULT 1,
                created_at TEXT DEFAULT (datetime('now')),
                updated_at TEXT DEFAULT (datetime('now')),
                synced_at TEXT DEFAULT NULL,
                deleted_at TEXT DEFAULT NULL,
                created_by_user_uuid TEXT DEFAULT NULL,
                updated_by_user_uuid TEXT DEFAULT NULL,
                FOREIGN KEY (client_uuid) REFERENCES clients(uuid)
            )
            """;

    /** Mirrors {@code supabase/migrations/012_invoice_and_payments_uuid_schema.sql}. */
    private static final String INVOICE_MASTER_DDL = """
            CREATE TABLE IF NOT EXISTS invoice_master (
                uuid TEXT PRIMARY KEY NOT NULL,
                invoice_no TEXT,
                client_uuid TEXT,
                client_name TEXT DEFAULT '',
                invoice_date TEXT,
                period_from TEXT,
                period_to TEXT,
                amount REAL NOT NULL DEFAULT 0,
                paid_amount REAL NOT NULL DEFAULT 0,
                due_amount REAL NOT NULL DEFAULT 0,
                payment_status TEXT DEFAULT 'UNPAID',
                last_payment_date TEXT,
                type TEXT DEFAULT '',
                status TEXT DEFAULT 'DRAFT',
                is_void INTEGER NOT NULL DEFAULT 0,
                void_reason TEXT DEFAULT '',
                void_date TEXT,
                replaced_by_invoice_uuid TEXT,
                parent_invoice_uuid TEXT,
                status_updated_by TEXT DEFAULT '',
                file_path TEXT DEFAULT '',
                document_series TEXT DEFAULT 'GST_INVOICE',
                %s,
                FOREIGN KEY (client_uuid) REFERENCES clients(uuid) ON DELETE SET NULL,
                FOREIGN KEY (parent_invoice_uuid) REFERENCES invoice_master(uuid) ON DELETE SET NULL,
                FOREIGN KEY (replaced_by_invoice_uuid) REFERENCES invoice_master(uuid) ON DELETE SET NULL
            )
            """.formatted(SYNC_COLUMNS);

    private static final String INVOICE_ADJUSTMENTS_DDL = """
            CREATE TABLE IF NOT EXISTS invoice_adjustments (
                uuid TEXT PRIMARY KEY NOT NULL,
                invoice_uuid TEXT NOT NULL,
                type TEXT NOT NULL CHECK (type IN ('Credit Note', 'Debit Note')),
                note_no TEXT NOT NULL,
                amount REAL NOT NULL,
                reason TEXT DEFAULT '',
                date TEXT DEFAULT (date('now')),
                %s,
                FOREIGN KEY (invoice_uuid) REFERENCES invoice_master(uuid) ON DELETE CASCADE,
                UNIQUE (note_no)
            )
            """.formatted(SYNC_COLUMNS);

    private static final String INVOICE_JOB_MAPPING_DDL = """
            CREATE TABLE IF NOT EXISTS invoice_job_mapping (
                uuid TEXT PRIMARY KEY NOT NULL,
                invoice_uuid TEXT NOT NULL,
                job_uuid TEXT NOT NULL,
                %s,
                FOREIGN KEY (invoice_uuid) REFERENCES invoice_master(uuid) ON DELETE CASCADE,
                FOREIGN KEY (job_uuid) REFERENCES jobs(uuid) ON DELETE CASCADE,
                UNIQUE (invoice_uuid, job_uuid)
            )
            """.formatted(SYNC_COLUMNS);

    private static final String PAYMENTS_DDL = """
            CREATE TABLE IF NOT EXISTS payments (
                uuid TEXT PRIMARY KEY NOT NULL,
                client_uuid TEXT,
                amount REAL NOT NULL DEFAULT 0,
                payment_date TEXT,
                method TEXT DEFAULT '',
                type TEXT NOT NULL DEFAULT 'Payment',
                %s,
                FOREIGN KEY (client_uuid) REFERENCES clients(uuid) ON DELETE SET NULL
            )
            """.formatted(SYNC_COLUMNS);

    private static final String PAYMENT_ALLOCATIONS_DDL = """
            CREATE TABLE IF NOT EXISTS payment_allocations (
                uuid TEXT PRIMARY KEY NOT NULL,
                payment_uuid TEXT NOT NULL,
                invoice_uuid TEXT NOT NULL,
                allocated_amount REAL NOT NULL,
                %s,
                FOREIGN KEY (payment_uuid) REFERENCES payments(uuid) ON DELETE CASCADE,
                FOREIGN KEY (invoice_uuid) REFERENCES invoice_master(uuid) ON DELETE CASCADE
            )
            """.formatted(SYNC_COLUMNS);

    private static final String PAYMENT_DETAILS_DDL = """
            CREATE TABLE IF NOT EXISTS payment_details (
                uuid TEXT PRIMARY KEY NOT NULL,
                payment_uuid TEXT NOT NULL,
                field_key TEXT NOT NULL,
                field_value TEXT DEFAULT '',
                %s,
                FOREIGN KEY (payment_uuid) REFERENCES payments(uuid) ON DELETE CASCADE,
                UNIQUE (payment_uuid, field_key)
            )
            """.formatted(SYNC_COLUMNS);

    private static final String DOCUMENT_NUMBER_MAPPINGS_DDL = """
            CREATE TABLE IF NOT EXISTS document_number_mappings (
                uuid TEXT PRIMARY KEY NOT NULL,
                entity_type TEXT NOT NULL,
                entity_uuid TEXT NOT NULL,
                sequence_key TEXT NOT NULL,
                temporary_number TEXT NOT NULL,
                permanent_number TEXT NOT NULL,
                allocation_source TEXT NOT NULL DEFAULT 'remote',
                %s,
                UNIQUE (temporary_number),
                UNIQUE (entity_type, entity_uuid)
            )
            """.formatted(SYNC_COLUMNS);

    private static final String JOB_ITEMS_DDL = """
            CREATE TABLE IF NOT EXISTS job_items (
                uuid TEXT PRIMARY KEY NOT NULL,
                job_uuid TEXT NOT NULL,
                type TEXT NOT NULL,
                description TEXT,
                amount REAL DEFAULT 0,
                sort_order INTEGER DEFAULT 0,
                sync_status TEXT DEFAULT 'PENDING',
                sync_version INTEGER DEFAULT 1,
                is_deleted INTEGER DEFAULT 0,
                is_active INTEGER DEFAULT 1,
                created_at TEXT DEFAULT (datetime('now')),
                updated_at TEXT DEFAULT (datetime('now')),
                synced_at TEXT DEFAULT NULL,
                deleted_at TEXT DEFAULT NULL,
                FOREIGN KEY (job_uuid) REFERENCES jobs(uuid) ON DELETE CASCADE
            )
            """;

    private static void ensureInvoiceMasterUuid(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "invoice_master")) {
            return;
        }
        ensureInvoiceMasterClientUuid(conn, stmt);
        if (!columnExists(conn, "invoice_master", "uuid")) {
            try {
                stmt.execute("ALTER TABLE invoice_master ADD COLUMN uuid TEXT");
            } catch (Exception ignored) {
            }
        }
        boolean hasIntegerId = columnExists(conn, "invoice_master", "id");
        String missingUuidSql = hasIntegerId
                ? "SELECT id FROM invoice_master WHERE uuid IS NULL OR TRIM(uuid) = ''"
                : "SELECT rowid FROM invoice_master WHERE uuid IS NULL OR TRIM(uuid) = ''";
        String fillUuidSql = hasIntegerId
                ? "UPDATE invoice_master SET uuid = ? WHERE id = ?"
                : "UPDATE invoice_master SET uuid = ? WHERE rowid = ?";
        try (PreparedStatement ps = conn.prepareStatement(missingUuidSql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try (PreparedStatement up = conn.prepareStatement(fillUuidSql)) {
                        up.setString(1, ClientIdentifiers.newUuidV7String());
                        if (hasIntegerId) {
                            up.setInt(2, rs.getInt(1));
                        } else {
                            up.setLong(2, rs.getLong(1));
                        }
                        up.executeUpdate();
                    }
                }
            }
        }
        try {
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_invoice_master_uuid ON invoice_master(uuid)");
        } catch (Exception ignored) {
        }
    }

    /** Legacy DBs use {@code client_id}; application code expects {@code client_uuid}. */
    private static void ensureInvoiceMasterClientUuid(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "invoice_master")) {
            return;
        }
        if (columnExists(conn, "invoice_master", "client_uuid")) {
            return;
        }
        System.out.println("? Migration: Adding client_uuid to invoice_master...");
        try {
            stmt.execute("ALTER TABLE invoice_master ADD COLUMN client_uuid TEXT;");
        } catch (Exception ignored) {
        }
        if (!columnExists(conn, "invoice_master", "client_id")) {
            return;
        }
        if (columnExists(conn, "clients", "id")) {
            stmt.execute("""
                    UPDATE invoice_master
                    SET client_uuid = (
                        SELECT c.uuid FROM clients c
                        WHERE c.id = invoice_master.client_id
                    )
                    WHERE client_id IS NOT NULL;
                    """);
        } else {
            stmt.execute("""
                    UPDATE invoice_master
                    SET client_uuid = CAST(client_id AS TEXT)
                    WHERE client_id IS NOT NULL
                      AND client_uuid IS NULL
                      AND EXISTS (
                        SELECT 1 FROM clients c WHERE c.uuid = CAST(invoice_master.client_id AS TEXT)
                      );
                    """);
        }
        System.out.println("? Migration: invoice_master.client_uuid populated from client_id.");
    }

    private static void ensureJobsTable(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "jobs")) {
            return;
        }
        if (jobsHasIntegerPk(conn)) {
            migrateJobsToUuidPrimaryKey(conn, stmt);
        }
        try {
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_jobs_job_code ON jobs(job_code)");
        } catch (Exception ignored) {
        }
        if (!columnExists(conn, "jobs", "created_by_user_uuid")) {
            stmt.execute("ALTER TABLE jobs ADD COLUMN created_by_user_uuid TEXT DEFAULT NULL;");
            System.out.println("✔ Migration: Added created_by_user_uuid column to jobs table.");
        }
        if (!columnExists(conn, "jobs", "updated_by_user_uuid")) {
            stmt.execute("ALTER TABLE jobs ADD COLUMN updated_by_user_uuid TEXT DEFAULT NULL;");
            System.out.println("✔ Migration: Added updated_by_user_uuid column to jobs table.");
        }
    }

    private static boolean jobsHasIntegerPk(Connection conn) throws java.sql.SQLException {
        return columnExists(conn, "jobs", "id");
    }

    private static void migrateJobsToUuidPrimaryKey(Connection conn, Statement stmt) throws Exception {
        ensureInvoiceMasterUuid(conn, stmt);
        stmt.execute("PRAGMA foreign_keys=OFF");
        stmt.execute("BEGIN IMMEDIATE");
        try {
            stmt.execute("""
                    CREATE TABLE jobs_uuid_pk (
                        uuid TEXT PRIMARY KEY NOT NULL,
                        client_uuid TEXT NOT NULL,
                        invoice_uuid TEXT DEFAULT NULL,
                        job_code TEXT UNIQUE NOT NULL,
                        job_title TEXT DEFAULT '',
                        job_type TEXT DEFAULT '',
                        description TEXT DEFAULT '',
                        amount REAL DEFAULT 0,
                        status TEXT DEFAULT 'Draft',
                        child_status TEXT DEFAULT '',
                        job_number_mode TEXT DEFAULT 'AUTO',
                        image_path TEXT DEFAULT '',
                        remarks TEXT DEFAULT '',
                        job_date TEXT DEFAULT (datetime('now')),
                        delivery_date TEXT DEFAULT NULL,
                        sync_status TEXT DEFAULT 'PENDING',
                        sync_version INTEGER DEFAULT 1,
                        is_deleted INTEGER DEFAULT 0,
                        is_active INTEGER DEFAULT 1,
                        created_at TEXT DEFAULT (datetime('now')),
                        updated_at TEXT DEFAULT (datetime('now')),
                        synced_at TEXT DEFAULT NULL,
                        deleted_at TEXT DEFAULT NULL
                    )
                    """);
            stmt.execute("CREATE TEMP TABLE IF NOT EXISTS _job_id_map (old_id INTEGER PRIMARY KEY, new_uuid TEXT NOT NULL)");
            String insertJob = """
                    INSERT INTO jobs_uuid_pk (
                        uuid, client_uuid, invoice_uuid, job_code, job_title, job_type, description, amount,
                        status, child_status, job_number_mode, image_path, remarks, job_date,
                        is_deleted, is_active, created_at, updated_at
                    )
                    SELECT
                        ?,
                        COALESCE(NULLIF(TRIM(CAST(j.client_id AS TEXT)), ''), ''),
                        (SELECT im.uuid FROM invoice_master im WHERE im.id = j.invoice_id),
                        ?,
                        COALESCE(NULLIF(TRIM(j.job_title), ''), NULLIF(TRIM(j.job_name), ''), ''),
                        COALESCE(j.job_type, ''),
                        COALESCE(j.description, ''),
                        COALESCE(j.amount, 0),
                        COALESCE(NULLIF(TRIM(j.status), ''), 'Draft'),
                        COALESCE(j.child_status, ''),
                        COALESCE(j.job_number_mode, 'AUTO'),
                        COALESCE(j.image_path, ''),
                        COALESCE(j.remarks, ''),
                        COALESCE(NULLIF(TRIM(j.job_date), ''), NULLIF(TRIM(j.date_created), ''), datetime('now')),
                        0, 1,
                        COALESCE(j.created_at, datetime('now')),
                        COALESCE(j.updated_at, datetime('now'))
                    FROM jobs j
                    WHERE j.id = ?
                    """;
            try (PreparedStatement sel = conn.prepareStatement("SELECT id FROM jobs ORDER BY id");
                    ResultSet ids = sel.executeQuery()) {
                while (ids.next()) {
                    int oldId = ids.getInt("id");
                    String jobUuid = JobIdentifiers.newUuidString();
                    String code = JobIdentifiers.legacyJobCodeFromSqliteId(oldId);
                    if (JobIdentifiers.jobCodeExists(conn, code)) {
                        code = JobIdentifiers.allocateUniqueJobCode(conn);
                    }
                    try (PreparedStatement ins = conn.prepareStatement(insertJob)) {
                        ins.setString(1, jobUuid);
                        ins.setString(2, code);
                        ins.setInt(3, oldId);
                        ins.executeUpdate();
                    }
                    try (PreparedStatement mapIns = conn.prepareStatement(
                            "INSERT OR REPLACE INTO _job_id_map (old_id, new_uuid) VALUES (?, ?)")) {
                        mapIns.setInt(1, oldId);
                        mapIns.setString(2, jobUuid);
                        mapIns.executeUpdate();
                    }
                }
            }
            stmt.execute("DROP TABLE IF EXISTS jobs");
            stmt.execute("ALTER TABLE jobs_uuid_pk RENAME TO jobs");
            migrateJobItemsToJobUuid(stmt);
            migrateInvoiceJobMappingToUuids(stmt);
            stmt.execute("COMMIT");
            System.out.println("Migration: jobs primary key is uuid (UUID v7); integer id removed");
        } catch (Exception e) {
            try {
                stmt.execute("ROLLBACK");
            } catch (Exception ignored) {
            }
            throw e;
        } finally {
            try {
                stmt.execute("PRAGMA foreign_keys=ON");
            } catch (Exception ignored) {
            }
        }
    }

    private static void migrateJobItemsToJobUuid(Statement stmt) throws Exception {
        if (!tableExistsQuiet(stmt, "job_items")) {
            return;
        }
        if (!columnExistsQuiet(stmt, "job_items", "job_id")) {
            return;
        }
        stmt.execute("DROP TABLE IF EXISTS job_items_uuid");
        stmt.execute("""
                CREATE TABLE job_items_uuid (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    job_uuid TEXT NOT NULL,
                    type TEXT NOT NULL,
                    description TEXT,
                    amount REAL DEFAULT 0,
                    sort_order INTEGER DEFAULT 0,
                    %s,
                    FOREIGN KEY (job_uuid) REFERENCES jobs(uuid) ON DELETE CASCADE
                )
                """.formatted(SYNC_COLUMNS));

        // Note: We'll need to generate fresh UUIDs for existing items
        try (Connection con = stmt.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM job_items");
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                int oldJobId = rs.getInt("job_id");
                String newJobUuid = null;
                // Get new job uuid from the map table created in migrateJobsToUuidPrimaryKey
                try (PreparedStatement mapSt = con.prepareStatement("SELECT new_uuid FROM _job_id_map WHERE old_id = ?")) {
                    mapSt.setInt(1, oldJobId);
                    try (ResultSet mapRs = mapSt.executeQuery()) {
                        if (mapRs.next()) newJobUuid = mapRs.getString(1);
                    }
                }
                
                if (newJobUuid != null) {
                    try (PreparedStatement ins = con.prepareStatement("""
                            INSERT INTO job_items_uuid (uuid, job_uuid, type, description, amount, sort_order, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """)) {
                        ins.setString(1, utils.ClientIdentifiers.newUuidV7String());
                        ins.setString(2, newJobUuid);
                        ins.setString(3, rs.getString("type"));
                        ins.setString(4, rs.getString("description"));
                        ins.setDouble(5, rs.getDouble("amount"));
                        ins.setInt(6, rs.getInt("sort_order"));
                        ins.setString(7, rs.getString("created_at") != null ? rs.getString("created_at") : "datetime('now')");
                        ins.executeUpdate();
                    }
                }
            }
        }

        stmt.execute("DROP TABLE job_items");
        stmt.execute("ALTER TABLE job_items_uuid RENAME TO job_items");
    }

    private static void migrateInvoiceJobMappingToUuids(Statement stmt) throws Exception {
        if (!tableExistsQuiet(stmt, "invoice_job_mapping")) {
            return;
        }
        stmt.execute("DROP TABLE IF EXISTS invoice_job_mapping_uuid");
        stmt.execute("""
                CREATE TABLE invoice_job_mapping_uuid (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    invoice_uuid TEXT NOT NULL,
                    job_uuid TEXT NOT NULL,
                    %s,
                    UNIQUE(invoice_uuid, job_uuid)
                )
                """.formatted(SYNC_COLUMNS));

        try (Connection con = stmt.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM invoice_job_mapping");
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                int oldJobId = rs.getInt("job_id");
                int oldInvoiceId = rs.getInt("invoice_id");
                
                String newJobUuid = null;
                try (PreparedStatement mapSt = con.prepareStatement("SELECT new_uuid FROM _job_id_map WHERE old_id = ?")) {
                    mapSt.setInt(1, oldJobId);
                    try (ResultSet mapRs = mapSt.executeQuery()) {
                        if (mapRs.next()) newJobUuid = mapRs.getString(1);
                    }
                }
                
                String newInvoiceUuid = null;
                try (PreparedStatement invSt = con.prepareStatement("SELECT uuid FROM invoice_master WHERE id = ?")) {
                    invSt.setInt(1, oldInvoiceId);
                    try (ResultSet invRs = invSt.executeQuery()) {
                        if (invRs.next()) newInvoiceUuid = invRs.getString(1);
                    }
                }
                
                if (newJobUuid != null && newInvoiceUuid != null) {
                    try (PreparedStatement ins = con.prepareStatement("""
                            INSERT INTO invoice_job_mapping_uuid (uuid, invoice_uuid, job_uuid, created_at)
                            VALUES (?, ?, ?, ?)
                            """)) {
                        ins.setString(1, utils.ClientIdentifiers.newUuidV7String());
                        ins.setString(2, newInvoiceUuid);
                        ins.setString(3, newJobUuid);
                        ins.setString(4, rs.getString("created_at") != null ? rs.getString("created_at") : "datetime('now')");
                        ins.executeUpdate();
                    }
                }
            }
        }
        stmt.execute("DROP TABLE invoice_job_mapping");
        stmt.execute("ALTER TABLE invoice_job_mapping_uuid RENAME TO invoice_job_mapping");
        stmt.execute("DROP TABLE IF EXISTS _job_id_map");
    }

    private static boolean columnExistsQuiet(Statement stmt, String table, String column) {
        try {
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
                while (rs.next()) {
                    if (column.equalsIgnoreCase(rs.getString("name"))) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean clientsHasIntegerPk(Connection conn) throws java.sql.SQLException {
        return columnExists(conn, "clients", "id");
    }

    /**
     * Preserves client rows: fills missing {@code uuid} with v7, rewrites child {@code client_id} to uuid strings,
     * rebuilds {@code clients} without integer {@code id}.
     */
    private static void migrateClientsToUuidPrimaryKey(Connection conn, Statement stmt) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id, uuid FROM clients");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String uuid = rs.getString("uuid");
                if (uuid == null || uuid.isBlank()) {
                    try (PreparedStatement up = conn.prepareStatement("UPDATE clients SET uuid=? WHERE id=?")) {
                        up.setString(1, ClientIdentifiers.newUuidV7String());
                        up.setInt(2, rs.getInt("id"));
                        up.executeUpdate();
                    }
                }
            }
        }
        stmt.execute("PRAGMA foreign_keys=OFF");
        stmt.execute("BEGIN IMMEDIATE");
        try {
            remapChildClientIdToUuid(stmt, "jobs");
            remapChildClientIdToUuid(stmt, "invoice_master");
            remapChildClientIdToUuid(stmt, "payments");
            remapChildClientIdToUuid(stmt, "billing");
            stmt.execute("""
                    CREATE TABLE clients_uuid_pk (
                        uuid TEXT PRIMARY KEY NOT NULL,
                        client_code TEXT UNIQUE NOT NULL,
                        client_name TEXT NOT NULL DEFAULT '',
                        business_name TEXT DEFAULT '',
                        mobile TEXT DEFAULT '',
                        alternate_mobile TEXT DEFAULT '',
                        email TEXT DEFAULT '',
                        gstin TEXT DEFAULT '',
                        pan_number TEXT DEFAULT '',
                        billing_address TEXT DEFAULT '',
                        shipping_address TEXT DEFAULT '',
                        client_type TEXT DEFAULT 'Regular',
                        price_category TEXT DEFAULT '',
                        credit_limit REAL DEFAULT 0,
                        payment_terms TEXT DEFAULT '',
                        opening_balance REAL DEFAULT 0,
                        balance_type TEXT DEFAULT 'DR',
                        sync_status TEXT DEFAULT 'PENDING',
                        sync_version INTEGER DEFAULT 1,
                        is_deleted INTEGER DEFAULT 0,
                        is_active INTEGER DEFAULT 1,
                        created_at TEXT DEFAULT (datetime('now')),
                        updated_at TEXT DEFAULT (datetime('now')),
                        synced_at TEXT DEFAULT NULL,
                        deleted_at TEXT DEFAULT NULL,
                        notes TEXT DEFAULT '',
                        created_by_user_uuid TEXT DEFAULT NULL,
                        updated_by_user_uuid TEXT DEFAULT NULL
                    )
                    """);
            stmt.execute("""
                    INSERT INTO clients_uuid_pk (
                        uuid, client_code, client_name, business_name, mobile, alternate_mobile, email,
                        gstin, pan_number, billing_address, shipping_address,
                        client_type, price_category, credit_limit, payment_terms, opening_balance, balance_type,
                        is_active, notes, sync_status, sync_version, is_deleted, deleted_at,
                        created_at, updated_at, synced_at, created_by_user_uuid, updated_by_user_uuid
                    )
                    SELECT
                        uuid, client_code, client_name, business_name, mobile, alternate_mobile, email,
                        gstin, pan_number, billing_address, shipping_address,
                        client_type, price_category, credit_limit, payment_terms, opening_balance, balance_type,
                        is_active, notes, sync_status, sync_version, is_deleted, deleted_at,
                        created_at, updated_at, synced_at, NULL, NULL
                    FROM clients
                    """);
            stmt.execute("DROP TABLE clients");
            stmt.execute("ALTER TABLE clients_uuid_pk RENAME TO clients");
            stmt.execute("COMMIT");
            System.out.println("Migration: clients primary key is uuid (UUID v7); integer id removed");
        } catch (Exception e) {
            try {
                stmt.execute("ROLLBACK");
            } catch (Exception ignored) {
            }
            throw e;
        } finally {
            try {
                stmt.execute("PRAGMA foreign_keys=ON");
            } catch (Exception ignored) {
            }
        }
    }

    private static void remapChildClientIdToUuid(Statement stmt, String table) {
        if (!tableExistsQuiet(stmt, table)) {
            return;
        }
        try {
            stmt.executeUpdate("""
                    UPDATE %s
                    SET client_id = (
                        SELECT c.uuid FROM clients c
                        WHERE c.id = CAST(%s.client_id AS INTEGER)
                    )
                    WHERE client_id IS NOT NULL
                      AND EXISTS (
                        SELECT 1 FROM clients c
                        WHERE c.id = CAST(%s.client_id AS INTEGER)
                      )
                    """.formatted(table, table, table));
        } catch (Exception ignored) {
            try {
                stmt.executeUpdate("""
                        UPDATE %s
                        SET client_id = (SELECT c.uuid FROM clients c WHERE c.uuid = %s.client_id)
                        WHERE client_id IS NOT NULL
                          AND EXISTS (SELECT 1 FROM clients c WHERE c.uuid = %s.client_id)
                        """.formatted(table, table, table));
            } catch (Exception ignored2) {
            }
        }
    }

    private static boolean tableExistsQuiet(Statement stmt, String table) {
        try {
            String safe = table.replace("'", "''");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name='" + safe + "'")) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Legacy {@code ALTER TABLE ADD COLUMN client_code} cannot attach {@code UNIQUE}; dedupe then add a unique index.
     */
    private static void ensureUniqueSqliteClientCodeConstraint(Connection conn, Statement stmt) throws Exception {
        if (!columnExists(conn, "clients", "client_code")) {
            return;
        }
        dedupeSqliteClientCodes(conn);
        try {
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_clients_client_code ON clients(client_code)");
        } catch (Exception ignored) {
        }
    }

    private static void dedupeSqliteClientCodes(Connection conn) throws Exception {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT client_code FROM clients
                        WHERE TRIM(COALESCE(client_code, '')) != ''
                        GROUP BY client_code
                        HAVING COUNT(*) > 1
                        """)) {
            while (rs.next()) {
                String dup = rs.getString(1);
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT uuid FROM clients WHERE client_code = ? ORDER BY uuid ASC")) {
                    ps.setString(1, dup);
                    try (ResultSet ids = ps.executeQuery()) {
                        boolean first = true;
                        while (ids.next()) {
                            String uuid = ids.getString(1);
                            if (first) {
                                first = false;
                                continue;
                            }
                            String nu = ClientIdentifiers.allocateUniqueClientCode(conn);
                            try (PreparedStatement up = conn.prepareStatement(
                                    "UPDATE clients SET client_code = ? WHERE uuid = ?")) {
                                up.setString(1, nu);
                                up.setString(2, uuid);
                                up.executeUpdate();
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean tableExists(Connection conn, String table) throws java.sql.SQLException {
        String safe = table.replace("'", "''");
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='" + safe + "'")) {
            return rs.next();
        }
    }

    private static boolean columnExists(Connection conn, String table, String column) throws java.sql.SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** {@code im.col} when present on invoice_master, else a SQL literal/expression. */
    private static String imCol(Connection conn, String column, String ifAbsent) throws Exception {
        return columnExists(conn, "invoice_master", column) ? "im." + column : ifAbsent;
    }

    private static String imColCoalesce(Connection conn, String column, String ifAbsent) throws Exception {
        return columnExists(conn, "invoice_master", column)
                ? "COALESCE(im." + column + ", " + ifAbsent + ")"
                : ifAbsent;
    }

    private static String imColCoalesceTrim(Connection conn, String column, String ifAbsent) throws Exception {
        return columnExists(conn, "invoice_master", column)
                ? "COALESCE(NULLIF(TRIM(im." + column + "), ''), " + ifAbsent + ")"
                : ifAbsent;
    }

    private static String sqlCoalesceNewUuid(String alias, String column, boolean hasColumn) {
        if (!hasColumn) {
            return NEW_UUID_SQL;
        }
        return "COALESCE(NULLIF(TRIM(" + alias + "." + column + "), ''), " + NEW_UUID_SQL + ")";
    }

    /** {@code old.col} when present on a *_old migration table, else a SQL literal/expression. */
    private static String oldCol(Connection conn, String oldTable, String column, String ifAbsent) throws Exception {
        return columnExists(conn, oldTable, column) ? "old." + column : ifAbsent;
    }

    private static String oldColCoalesce(Connection conn, String oldTable, String column, String ifAbsent)
            throws Exception {
        return columnExists(conn, oldTable, column)
                ? "COALESCE(old." + column + ", " + ifAbsent + ")"
                : ifAbsent;
    }

    /**
     * Removes {@code *_old} / {@code *_uuid_pk} tables left by interrupted migrations. Their FKs
     * often still reference legacy {@code invoice_master(id)} and break all SQLite writes.
     */
    private static void dropLeftoverMigrationBackupTables(Connection conn, Statement stmt) throws Exception {
        stmt.execute("PRAGMA foreign_keys=OFF");
        try (ResultSet rs = stmt.executeQuery("""
                SELECT name FROM sqlite_master
                WHERE type = 'table'
                  AND (name GLOB '*_old' OR name GLOB '*_uuid_pk')
                ORDER BY name
                """)) {
            while (rs.next()) {
                String table = rs.getString(1);
                if (table == null || table.isBlank()) {
                    continue;
                }
                String quoted = "\"" + table.replace("\"", "\"\"") + "\"";
                stmt.execute("DROP TABLE IF EXISTS " + quoted);
                System.out.println("Migration cleanup: dropped leftover backup table " + table);
            }
        } finally {
            stmt.execute("PRAGMA foreign_keys=ON");
        }
    }

    /**
     * Upgrade legacy hsn_sac_master (no item_name / fixed UNIQUE) to Tax Master schema.
     */
    private static void migrateHsnSacMasterIfNeeded(Connection conn) throws java.sql.SQLException {
        if (!tableExists(conn, "hsn_sac_master")) {
            return;
        }
        if (columnExists(conn, "hsn_sac_master", "item_name")) {
            return;
        }
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("ALTER TABLE hsn_sac_master RENAME TO hsn_sac_master_legacy");
            st.executeUpdate("""
                    CREATE TABLE hsn_sac_master (
                        uuid TEXT PRIMARY KEY NOT NULL,
                        item_type TEXT NOT NULL,
                        item_name TEXT NOT NULL DEFAULT '',
                        keyword TEXT NOT NULL DEFAULT '',
                        code_type TEXT NOT NULL DEFAULT 'HSN',
                        hsn_sac TEXT NOT NULL,
                        gst_rate REAL NOT NULL DEFAULT 0.18,
                        unit_default TEXT,
                        description TEXT NOT NULL DEFAULT '',
                        is_favorite INTEGER NOT NULL DEFAULT 0,
                        %s,
                        UNIQUE(item_type, keyword)
                    )
                    """.formatted(SYNC_COLUMNS));
            st.executeUpdate("""
                    INSERT INTO hsn_sac_master (uuid, item_type, item_name, keyword, code_type, hsn_sac, gst_rate, unit_default, description, is_favorite, created_at)
                    SELECT lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))),
                           item_type,
                           CASE WHEN trim(coalesce(keyword,'')) = '' THEN item_type || ' default'
                                ELSE trim(keyword) END,
                           coalesce(keyword,''),
                           'HSN',
                           hsn_sac,
                           gst_rate,
                           coalesce(unit_default,'PCS'),
                           '',
                           0,
                           created_at
                    FROM hsn_sac_master_legacy
                    """);
            st.executeUpdate("DROP TABLE hsn_sac_master_legacy");
        }
        System.out.println("✔ Migration: hsn_sac_master upgraded for Tax Master");
    }

    /** Mirrors Supabase {@code public.number_sequences} — per-module FY counters. */
    private static final String NUMBER_SEQUENCES_DDL = """
            CREATE TABLE IF NOT EXISTS number_sequences (
                sequence_key TEXT PRIMARY KEY NOT NULL,
                display_name TEXT NOT NULL,
                prefix TEXT NOT NULL,
                current_number INTEGER NOT NULL DEFAULT 0,
                digit_width INTEGER NOT NULL DEFAULT 3,
                financial_year TEXT NOT NULL DEFAULT '',
                offline_current_number INTEGER NOT NULL DEFAULT 0,
                sync_status TEXT DEFAULT 'PENDING',
                sync_version INTEGER DEFAULT 1,
                is_deleted INTEGER DEFAULT 0,
                is_active INTEGER DEFAULT 1,
                created_at TEXT DEFAULT (datetime('now')),
                updated_at TEXT DEFAULT (datetime('now')),
                synced_at TEXT DEFAULT NULL,
                deleted_at TEXT DEFAULT NULL
            )
            """;

    private static void ensureNumberSequences(Connection conn, Statement stmt) throws Exception {
        stmt.execute(NUMBER_SEQUENCES_DDL);
        migrateNumberSequencesDropModuleName(conn, stmt);
        try {
            stmt.execute("ALTER TABLE number_sequences ADD COLUMN offline_current_number INTEGER NOT NULL DEFAULT 0;");
        } catch (Exception ignored) {
        }
        String fy = DocumentNumbering.financialYearLabel(LocalDate.now());
        int pad = 4;
        try (ResultSet rs = stmt.executeQuery(
                "SELECT invoice_padding, numbering_fy FROM system_settings WHERE uuid = '00000000-0000-0000-0000-000000000001'")) {
            if (rs.next()) {
                pad = Math.max(1, rs.getInt("invoice_padding"));
                String storedFy = rs.getString("numbering_fy");
                if (storedFy != null && !storedFy.isBlank()) {
                    fy = storedFy.trim();
                }
            }
        } catch (Exception ignored) {
        }

        try (PreparedStatement ins = conn.prepareStatement("""
                INSERT OR IGNORE INTO number_sequences
                (sequence_key, display_name, prefix, current_number, digit_width, financial_year)
                VALUES (?, ?, ?, 0, ?, ?)
                """)) {
            for (NumberSequenceCatalog.ModuleDef m : NumberSequenceCatalog.ALL) {
                ins.setString(1, m.moduleName());
                ins.setString(2, m.displayName());
                ins.setString(3, m.prefix());
                ins.setInt(4, pad);
                ins.setString(5, fy);
                ins.executeUpdate();
            }
        }

        migrateNumberSequencesFromSystemSettings(conn);
    }

    /**
     * Pull legacy counters from {@code system_settings} into {@code number_sequences} without lowering values.
     */
    private static void migrateNumberSequencesFromSystemSettings(Connection conn) throws Exception {
        if (!tableExists(conn, "system_settings")) {
            return;
        }
        String fy = DocumentNumbering.financialYearLabel(LocalDate.now());
        int pad = 4;
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT * FROM system_settings WHERE uuid = '00000000-0000-0000-0000-000000000001'")) {
            if (!rs.next()) {
                return;
            }
            pad = Math.max(1, rs.getInt("invoice_padding"));
            String storedFy = rs.getString("numbering_fy");
            if (storedFy != null && !storedFy.isBlank()) {
                fy = storedFy.trim();
            }
            try (PreparedStatement upd = conn.prepareStatement("""
                    UPDATE number_sequences
                    SET current_number = CASE WHEN ? > current_number THEN ? ELSE current_number END,
                        digit_width = ?,
                        financial_year = ?,
                        updated_at = datetime('now')
                    WHERE sequence_key = ?
                    """)) {
                for (NumberSequenceCatalog.ModuleDef m : NumberSequenceCatalog.ALL) {
                    String legacyCol = m.legacySettingsColumn();
                    if (legacyCol == null) {
                        continue;
                    }
                    int legacy = 0;
                    try {
                        legacy = rs.getInt(legacyCol);
                    } catch (Exception ignored) {
                    }
                    upd.setInt(1, legacy);
                    upd.setInt(2, legacy);
                    upd.setInt(3, pad);
                    upd.setString(4, fy);
                    upd.setString(5, m.moduleName());
                    upd.executeUpdate();
                }
            }
        }
    }

    private static void migrateNumberSequencesDropModuleName(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "number_sequences") || !columnExists(conn, "number_sequences", "module_name")) {
            return;
        }
        try {
            stmt.execute("ALTER TABLE number_sequences DROP COLUMN module_name");
            System.out.println("✔ Migration: dropped number_sequences.module_name");
        } catch (Exception e) {
            System.err.println("⚠ Migration: ALTER TABLE DROP COLUMN failed. Recreating table instead...");
            stmt.execute("ALTER TABLE number_sequences RENAME TO ns_old");
            stmt.execute(NUMBER_SEQUENCES_DDL);
            stmt.execute("""
                INSERT INTO number_sequences (
                    sequence_key, display_name, prefix, current_number,
                    digit_width, financial_year, offline_current_number, created_at, updated_at
                )
                SELECT 
                    sequence_key, display_name, prefix, current_number,
                    digit_width, financial_year, offline_current_number, created_at, updated_at
                FROM ns_old
            """);
            stmt.execute("DROP TABLE ns_old");
            System.out.println("✔ Migration: Recreated number_sequences without module_name");
        }
    }

    private static void ensureSystemSettingsUuid(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "system_settings")) return;
        if (!columnExists(conn, "system_settings", "uuid")) {
            System.out.println("? Migration: Adding uuid to system_settings...");
            stmt.execute("ALTER TABLE system_settings RENAME TO system_settings_old;");
            stmt.execute("""
                    CREATE TABLE system_settings (
                        uuid TEXT PRIMARY KEY NOT NULL,
                        invoice_mode TEXT NOT NULL CHECK (invoice_mode IN ('AUTO','MANUAL')),
                        invoice_prefix TEXT,
                        invoice_start_no INTEGER,
                        invoice_padding INTEGER,
                        last_invoice_no INTEGER DEFAULT 0,
                        last_job_no INTEGER DEFAULT 0,
                        job_prefix TEXT DEFAULT 'SUN-',
                        job_start_no INTEGER DEFAULT 1,
                        job_padding INTEGER DEFAULT 4,
                        last_temp_invoice_no INTEGER DEFAULT 0,
                        numbering_fy TEXT DEFAULT '',
                        last_seq_inv INTEGER DEFAULT 0,
                        last_seq_pi INTEGER DEFAULT 0,
                        last_seq_cn INTEGER DEFAULT 0,
                        last_seq_dn INTEGER DEFAULT 0,
                        last_seq_qtn INTEGER DEFAULT 0,
                        last_seq_po INTEGER DEFAULT 0,
                        last_seq_job INTEGER DEFAULT 0,
                        last_seq_tkt INTEGER DEFAULT 0,
                        last_seq_dc INTEGER DEFAULT 0,
                        last_seq_ewb INTEGER DEFAULT 0,
                        sync_status TEXT DEFAULT 'PENDING',
                        sync_version INTEGER DEFAULT 1,
                        is_deleted INTEGER DEFAULT 0,
                        is_active INTEGER DEFAULT 1,
                        created_at TEXT DEFAULT (datetime('now')),
                        updated_at TEXT DEFAULT (datetime('now')),
                        synced_at TEXT DEFAULT NULL,
                        deleted_at TEXT DEFAULT NULL
                    );
                    """);
            boolean hasCreatedAt = columnExists(conn, "system_settings_old", "created_at");
            String insertSql = """
                    INSERT INTO system_settings (
                        uuid, invoice_mode, invoice_prefix, invoice_start_no, invoice_padding,
                        last_invoice_no, last_job_no, job_prefix, job_start_no, job_padding,
                        last_temp_invoice_no, numbering_fy,
                        last_seq_inv, last_seq_pi, last_seq_cn, last_seq_dn,
                        last_seq_qtn, last_seq_po, last_seq_job, last_seq_tkt,
                        last_seq_dc, last_seq_ewb %s
                    )
                    SELECT 
                        '00000000-0000-0000-0000-000000000001', invoice_mode, invoice_prefix, invoice_start_no, invoice_padding,
                        COALESCE(last_invoice_no, 0), COALESCE(last_job_no, 0), COALESCE(job_prefix, 'SUN-'),
                        COALESCE(job_start_no, 1), COALESCE(job_padding, 4),
                        COALESCE(last_temp_invoice_no, 0), COALESCE(numbering_fy, ''),
                        COALESCE(last_seq_inv, 0), COALESCE(last_seq_pi, 0), COALESCE(last_seq_cn, 0), COALESCE(last_seq_dn, 0),
                        COALESCE(last_seq_qtn, 0), COALESCE(last_seq_po, 0), COALESCE(last_seq_job, 0), COALESCE(last_seq_tkt, 0),
                        COALESCE(last_seq_dc, 0), COALESCE(last_seq_ewb, 0) %s
                    FROM system_settings_old WHERE id = 1;
                    """.formatted(hasCreatedAt ? ", created_at" : "", hasCreatedAt ? ", created_at" : "");
            stmt.execute(insertSql);
            stmt.execute("DROP TABLE system_settings_old;");
            System.out.println("? Migration: system_settings updated to uuid PK.");
        }
    }

    private static void ensureEmailSettingsUuid(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "email_settings")) return;
        if (!columnExists(conn, "email_settings", "uuid")) {
            System.out.println("? Migration: Adding uuid to email_settings...");
            stmt.execute("ALTER TABLE email_settings RENAME TO email_settings_old;");
            stmt.execute("""
                    CREATE TABLE email_settings (
                        uuid TEXT PRIMARY KEY NOT NULL,
                        smtp_host TEXT,
                        smtp_port TEXT,
                        sender_email TEXT,
                        sender_password TEXT,
                        sync_status TEXT DEFAULT 'PENDING',
                        sync_version INTEGER DEFAULT 1,
                        is_deleted INTEGER DEFAULT 0,
                        is_active INTEGER DEFAULT 1,
                        created_at TEXT DEFAULT (datetime('now')),
                        updated_at TEXT DEFAULT (datetime('now')),
                        synced_at TEXT DEFAULT NULL,
                        deleted_at TEXT DEFAULT NULL
                    );
                    """);
            boolean hasCreatedAt = columnExists(conn, "email_settings_old", "created_at");
            String insertSql = """
                    INSERT INTO email_settings (uuid, smtp_host, smtp_port, sender_email, sender_password %s)
                    SELECT
                        '00000000-0000-0000-0000-000000000002',
                        smtp_host, smtp_port, sender_email, sender_password %s
                    FROM email_settings_old WHERE id = 1;
                    """.formatted(hasCreatedAt ? ", created_at" : "", hasCreatedAt ? ", created_at" : "");
            stmt.execute(insertSql);
            stmt.execute("DROP TABLE email_settings_old;");
            System.out.println("? Migration: email_settings updated to uuid PK.");
        }
    }

    private static void ensureSupabaseSettingsUuid(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "supabase_settings")) return;
        if (!columnExists(conn, "supabase_settings", "uuid")) {
            System.out.println("? Migration: Adding uuid to supabase_settings...");
            stmt.execute("ALTER TABLE supabase_settings RENAME TO supabase_settings_old;");
            stmt.execute("""
                    CREATE TABLE supabase_settings (
                        uuid TEXT PRIMARY KEY NOT NULL,
                        supabase_url TEXT NOT NULL DEFAULT '',
                        anon_key TEXT NOT NULL DEFAULT '',
                        auth_email TEXT NOT NULL DEFAULT '',
                        auth_password TEXT NOT NULL DEFAULT '',
                        sync_status TEXT DEFAULT 'PENDING',
                        sync_version INTEGER DEFAULT 1,
                        is_deleted INTEGER DEFAULT 0,
                        is_active INTEGER DEFAULT 1,
                        created_at TEXT DEFAULT (datetime('now')),
                        updated_at TEXT DEFAULT (datetime('now')),
                        synced_at TEXT DEFAULT NULL,
                        deleted_at TEXT DEFAULT NULL
                    );
                    """);
            boolean hasCreatedAt = columnExists(conn, "supabase_settings_old", "created_at");
            String insertSql = """
                    INSERT INTO supabase_settings (uuid, supabase_url, anon_key, auth_email, auth_password %s)
                    SELECT
                        '00000000-0000-0000-0000-000000000003',
                        supabase_url, anon_key, auth_email, auth_password %s
                    FROM supabase_settings_old WHERE id = 1;
                    """.formatted(hasCreatedAt ? ", created_at" : "", hasCreatedAt ? ", created_at" : "");
            stmt.execute(insertSql);
            stmt.execute("DROP TABLE supabase_settings_old;");
            System.out.println("? Migration: supabase_settings updated to uuid PK.");
        }
    }

    private static String buildJobItemDetailInsertSql(
            String table, String columns, String selectValues, String jobItemUuidExpr) {
        return "INSERT INTO " + table + " (uuid, job_item_uuid, " + columns
                + ", sync_status, created_at, updated_at)\n"
                + "SELECT " + NEW_UUID_SQL + ", " + jobItemUuidExpr + ", " + selectValues
                + ", 'PENDING', datetime('now'), datetime('now')\n"
                + "FROM " + table + "_old old\n"
                + "WHERE " + jobItemUuidExpr + " IS NOT NULL";
    }

    /**
     * Legacy detail tables used {@code job_item_id INTEGER}; code expects {@code uuid} + {@code job_item_uuid}.
     */
    private static void ensureJobItemDetailTablesUuid(Connection conn, Statement stmt) throws Exception {
        String jobItemUuidExpr = legacyJobItemUuidSelect(conn);

        migrateJobItemDetailTable(conn, stmt, "printing_items", """
                CREATE TABLE printing_items (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    job_item_uuid TEXT NOT NULL UNIQUE,
                    qty INTEGER DEFAULT 0,
                    units TEXT,
                    sets TEXT,
                    color TEXT,
                    side TEXT,
                    with_ctp INTEGER DEFAULT 0,
                    notes TEXT,
                    amount REAL DEFAULT 0,
                    sync_status TEXT DEFAULT 'PENDING',
                    sync_version INTEGER DEFAULT 1,
                    is_deleted INTEGER DEFAULT 0,
                    is_active INTEGER DEFAULT 1,
                    created_at TEXT DEFAULT (datetime('now')),
                    updated_at TEXT DEFAULT (datetime('now')),
                    synced_at TEXT DEFAULT NULL,
                    deleted_at TEXT DEFAULT NULL,
                    FOREIGN KEY (job_item_uuid) REFERENCES job_items(uuid) ON DELETE CASCADE
                );
                """,
                buildJobItemDetailInsertSql("printing_items",
                        "qty, units, sets, color, side, with_ctp, notes, amount",
                        "qty, units, sets, color, side, with_ctp, notes, amount",
                        jobItemUuidExpr));

        migrateJobItemDetailTable(conn, stmt, "paper_items", """
                CREATE TABLE paper_items (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    job_item_uuid TEXT NOT NULL UNIQUE,
                    qty INTEGER DEFAULT 0,
                    units TEXT,
                    size TEXT,
                    gsm TEXT,
                    type TEXT,
                    source TEXT,
                    notes TEXT,
                    amount REAL DEFAULT 0,
                    sync_status TEXT DEFAULT 'PENDING',
                    sync_version INTEGER DEFAULT 1,
                    is_deleted INTEGER DEFAULT 0,
                    is_active INTEGER DEFAULT 1,
                    created_at TEXT DEFAULT (datetime('now')),
                    updated_at TEXT DEFAULT (datetime('now')),
                    synced_at TEXT DEFAULT NULL,
                    deleted_at TEXT DEFAULT NULL,
                    FOREIGN KEY (job_item_uuid) REFERENCES job_items(uuid) ON DELETE CASCADE
                );
                """,
                buildJobItemDetailInsertSql("paper_items",
                        "qty, units, size, gsm, type, source, notes, amount",
                        "qty, units, size, gsm, type, source, notes, amount",
                        jobItemUuidExpr));

        migrateJobItemDetailTable(conn, stmt, "binding_items", """
                CREATE TABLE binding_items (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    job_item_uuid TEXT NOT NULL UNIQUE,
                    process TEXT,
                    qty INTEGER DEFAULT 0,
                    rate REAL DEFAULT 0,
                    notes TEXT,
                    amount REAL DEFAULT 0,
                    sync_status TEXT DEFAULT 'PENDING',
                    sync_version INTEGER DEFAULT 1,
                    is_deleted INTEGER DEFAULT 0,
                    is_active INTEGER DEFAULT 1,
                    created_at TEXT DEFAULT (datetime('now')),
                    updated_at TEXT DEFAULT (datetime('now')),
                    synced_at TEXT DEFAULT NULL,
                    deleted_at TEXT DEFAULT NULL,
                    FOREIGN KEY (job_item_uuid) REFERENCES job_items(uuid) ON DELETE CASCADE
                );
                """,
                buildJobItemDetailInsertSql("binding_items",
                        "process, qty, rate, notes, amount",
                        "process, qty, rate, notes, amount",
                        jobItemUuidExpr));

        migrateJobItemDetailTable(conn, stmt, "lamination_items", """
                CREATE TABLE lamination_items (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    job_item_uuid TEXT NOT NULL UNIQUE,
                    qty INTEGER DEFAULT 0,
                    unit TEXT,
                    type TEXT,
                    side TEXT,
                    size TEXT,
                    notes TEXT,
                    amount REAL DEFAULT 0,
                    sync_status TEXT DEFAULT 'PENDING',
                    sync_version INTEGER DEFAULT 1,
                    is_deleted INTEGER DEFAULT 0,
                    is_active INTEGER DEFAULT 1,
                    created_at TEXT DEFAULT (datetime('now')),
                    updated_at TEXT DEFAULT (datetime('now')),
                    synced_at TEXT DEFAULT NULL,
                    deleted_at TEXT DEFAULT NULL,
                    FOREIGN KEY (job_item_uuid) REFERENCES job_items(uuid) ON DELETE CASCADE
                );
                """,
                buildJobItemDetailInsertSql("lamination_items",
                        "qty, unit, type, side, size, notes, amount",
                        "qty, unit, type, side, size, notes, amount",
                        jobItemUuidExpr));

        String supplierUuidExpr = legacySupplierUuidSelect(conn);
        migrateJobItemDetailTable(conn, stmt, "ctp_items", """
                CREATE TABLE ctp_items (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    job_item_uuid TEXT NOT NULL UNIQUE,
                    qty INTEGER DEFAULT 0,
                    plate_size TEXT,
                    gauge TEXT,
                    backing TEXT,
                    color TEXT,
                    supplier_uuid TEXT,
                    supplier_name TEXT,
                    notes TEXT,
                    amount REAL DEFAULT 0,
                    sync_status TEXT DEFAULT 'PENDING',
                    sync_version INTEGER DEFAULT 1,
                    is_deleted INTEGER DEFAULT 0,
                    is_active INTEGER DEFAULT 1,
                    created_at TEXT DEFAULT (datetime('now')),
                    updated_at TEXT DEFAULT (datetime('now')),
                    synced_at TEXT DEFAULT NULL,
                    deleted_at TEXT DEFAULT NULL,
                    FOREIGN KEY (job_item_uuid) REFERENCES job_items(uuid) ON DELETE CASCADE,
                    FOREIGN KEY (supplier_uuid) REFERENCES suppliers(uuid)
                );
                """,
                buildJobItemDetailInsertSql("ctp_items",
                        "qty, plate_size, gauge, backing, color, supplier_uuid, supplier_name, notes, amount",
                        "qty, plate_size, gauge, backing, color, " + supplierUuidExpr
                                + ", supplier_name, notes, amount",
                        jobItemUuidExpr));
    }

    private static void migrateJobItemDetailTable(
            Connection conn, Statement stmt, String table, String createSql, String insertSql) throws Exception {
        String oldTable = table + "_old";
        if (tableExists(conn, oldTable) && tableExists(conn, table) && columnExists(conn, table, "uuid")) {
            System.out.println("? Migration: Finishing partial " + table + " migration...");
            stmt.execute(insertSql);
            stmt.execute("DROP TABLE " + oldTable + ";");
            System.out.println("? Migration: " + table + " data copied from " + oldTable + ".");
            return;
        }
        if (!tableExists(conn, table)) {
            return;
        }
        if (columnExists(conn, table, "uuid")
                && !columnExists(conn, table, "id")
                && !columnExists(conn, table, "job_item_id")) {
            return;
        }
        if (columnExists(conn, table, "uuid")) {
            System.out.println("Migration: " + table + " → strip integer id / job_item_id");
        } else {
            System.out.println("? Migration: Adding uuid to " + table + "...");
        }
        stmt.execute("ALTER TABLE " + table + " RENAME TO " + oldTable + ";");
        stmt.execute(createSql);
        stmt.execute(insertSql);
        stmt.execute("DROP TABLE " + oldTable + ";");
        System.out.println("? Migration: " + table + " updated to uuid PK.");
    }

    private static String legacyJobItemUuidSelect(Connection conn) throws Exception {
        if (columnExists(conn, "job_items", "id")) {
            return "(SELECT ji.uuid FROM job_items ji WHERE ji.id = old.job_item_id)";
        }
        if (tableExists(conn, "job_items_old")) {
            return """
                    (SELECT ji.uuid FROM job_items ji
                     INNER JOIN job_items_old o ON o.id = old.job_item_id
                     WHERE ji.job_uuid = o.job_uuid AND ji.type = o.type
                       AND COALESCE(ji.sort_order, 0) = COALESCE(o.sort_order, 0)
                     LIMIT 1)
                    """;
        }
        return "NULL";
    }

    private static String legacySupplierUuidSelect(Connection conn) throws Exception {
        if (columnExists(conn, "suppliers", "id")) {
            return "(SELECT s.uuid FROM suppliers s WHERE s.id = old.supplier_id)";
        }
        return "NULL";
    }

    private static void ensureJobItemsUuid(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "job_items")) return;
        if (!columnExists(conn, "job_items", "uuid")) {
            System.out.println("? Migration: Adding uuid to job_items...");
            stmt.execute("ALTER TABLE job_items RENAME TO job_items_old;");
            stmt.execute("""
            CREATE TABLE job_items (
                uuid TEXT PRIMARY KEY NOT NULL,
                job_uuid TEXT NOT NULL,
                type TEXT NOT NULL,
                description TEXT,
                amount REAL DEFAULT 0,
                sort_order INTEGER DEFAULT 0,
                sync_status TEXT DEFAULT 'PENDING',
                sync_version INTEGER DEFAULT 1,
                is_deleted INTEGER DEFAULT 0,
                is_active INTEGER DEFAULT 1,
                created_at TEXT DEFAULT (datetime('now')),
                updated_at TEXT DEFAULT (datetime('now')),
                synced_at TEXT DEFAULT NULL,
                deleted_at TEXT DEFAULT NULL,
                FOREIGN KEY (job_uuid) REFERENCES jobs(uuid) ON DELETE CASCADE
            )
            """);
            
            boolean hasJobId = columnExists(conn, "job_items_old", "job_id");
            
            boolean hasCreatedAt = columnExists(conn, "job_items_old", "created_at");
            
            if (hasJobId) {
                String insertSql = """
                        INSERT INTO job_items (uuid, job_uuid, type, description, amount, sort_order %s)
                        SELECT 
                            lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))),
                            (SELECT uuid FROM jobs WHERE id = old.job_id),
                            type, description, amount, sort_order %s
                        FROM job_items_old old;
                        """.formatted(hasCreatedAt ? ", created_at" : "", hasCreatedAt ? ", created_at" : "");
                stmt.execute(insertSql);
            } else {
                 String insertSql = """
                        INSERT INTO job_items (uuid, job_uuid, type, description, amount, sort_order %s)
                        SELECT 
                            COALESCE(uuid, lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))),
                            job_uuid, type, description, amount, sort_order %s
                        FROM job_items_old;
                        """.formatted(hasCreatedAt ? ", created_at" : "", hasCreatedAt ? ", created_at" : "");
                 stmt.execute(insertSql);
            }
            stmt.execute("DROP TABLE job_items_old;");
        }
        try {
            stmt.execute("DROP TABLE IF EXISTS job_items_old;");
        } catch (Exception ignored) {
        }
    }

    private static void ensureSuppliersUuid(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "suppliers")) return;
        if (!columnExists(conn, "suppliers", "uuid")) {
            System.out.println("✔ Migration: Adding uuid to suppliers...");
            stmt.execute("ALTER TABLE suppliers RENAME TO suppliers_old;");
            stmt.execute("""
                    CREATE TABLE suppliers (
                        uuid TEXT PRIMARY KEY NOT NULL,
                        supplier_code TEXT DEFAULT '',
                        name TEXT NOT NULL DEFAULT '',
                        business_name TEXT DEFAULT '',
                        type TEXT NOT NULL DEFAULT '',
                        phone TEXT DEFAULT '',
                        gst_number TEXT DEFAULT '',
                        created_by_user_uuid TEXT DEFAULT NULL,
                        updated_by_user_uuid TEXT DEFAULT NULL,
                        sync_status TEXT DEFAULT 'PENDING',
                        sync_version INTEGER DEFAULT 1,
                        is_deleted INTEGER DEFAULT 0,
                        is_active INTEGER DEFAULT 1,
                        created_at TEXT DEFAULT (datetime('now')),
                        updated_at TEXT DEFAULT (datetime('now')),
                        synced_at TEXT DEFAULT NULL,
                        deleted_at TEXT DEFAULT NULL
                    );
                    """);
            boolean hasCreatedAt = columnExists(conn, "suppliers_old", "created_at");
            String insertSql = """
                    INSERT INTO suppliers (uuid, supplier_code, name, business_name, type, phone, address, gst_number, created_by_user_uuid, updated_by_user_uuid %s)
                    SELECT 
                        lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))),
                        '', name, business_name, type, phone, address, gst_number, NULL, NULL %s
                    FROM suppliers_old;
                    """.formatted(hasCreatedAt ? ", created_at" : "", hasCreatedAt ? ", created_at" : "");
            stmt.execute(insertSql);
            stmt.execute("DROP TABLE suppliers_old;");
        }
    }

    private static void ensurePaymentsUuid(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "payments")) return;
        if (!columnExists(conn, "payments", "uuid")) {
            System.out.println("✔ Migration: Adding uuid to payments...");
            stmt.execute("ALTER TABLE payments RENAME TO payments_old;");
            stmt.execute(PAYMENTS_DDL.replace("IF NOT EXISTS ", ""));
            boolean hasCreatedAt = columnExists(conn, "payments_old", "created_at");
            String insertSql = """
                    INSERT INTO payments (uuid, client_uuid, amount, payment_date, method, type %s)
                    SELECT 
                        lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))),
                        (SELECT uuid FROM clients WHERE id = old.client_id),
                        amount, payment_date, method, COALESCE(type, 'Payment') %s
                    FROM payments_old old;
                    """.formatted(hasCreatedAt ? ", created_at" : "", hasCreatedAt ? ", created_at" : "");
            stmt.execute(insertSql);
            stmt.execute("DROP TABLE payments_old;");
        }
    }

    private static void ensureInvoiceAdjustmentsUuid(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "invoice_adjustments")) return;
        if (!columnExists(conn, "invoice_adjustments", "uuid")) {
            System.out.println("✔ Migration: Adding uuid to invoice_adjustments...");
            stmt.execute("ALTER TABLE invoice_adjustments RENAME TO invoice_adjustments_old;");
            stmt.execute(INVOICE_ADJUSTMENTS_DDL.replace("IF NOT EXISTS ", ""));
            boolean hasCreatedAt = columnExists(conn, "invoice_adjustments_old", "created_at");
            String insertSql = """
                    INSERT INTO invoice_adjustments (uuid, invoice_uuid, type, note_no, amount, reason, date %s)
                    SELECT 
                        lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))),
                        (SELECT uuid FROM invoice_master WHERE id = old.invoice_id),
                        type, note_no, amount, reason, date %s
                    FROM invoice_adjustments_old old;
                    """.formatted(hasCreatedAt ? ", created_at" : "", hasCreatedAt ? ", created_at" : "");
            stmt.execute(insertSql);
            stmt.execute("DROP TABLE invoice_adjustments_old;");
        }
    }

    /**
     * Maps offline {@code TEMP-*} numbers to permanent codes allocated from remote {@code number_sequences}.
     */
    private static void ensureDocumentNumberMappingsTable(Connection conn, Statement stmt) throws Exception {
        stmt.execute(DOCUMENT_NUMBER_MAPPINGS_DDL);
        ensureChildTableSyncColumns(conn, stmt, "document_number_mappings");
        try {
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_dnm_permanent ON document_number_mappings(permanent_number);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_dnm_entity ON document_number_mappings(entity_type, entity_uuid);");
        } catch (Exception ignored) {
        }
    }

    /**
     * Ensure paper_items table has supplier_uuid and supplier_name columns.
     */
    private static void ensurePaperItemsSupplierColumns(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "paper_items")) return;
        
        if (!columnExists(conn, "paper_items", "supplier_uuid")) {
            System.out.println("✔ Migration: Adding supplier_uuid to paper_items...");
            stmt.execute("ALTER TABLE paper_items ADD COLUMN supplier_uuid TEXT;");
        }
        
        if (!columnExists(conn, "paper_items", "supplier_name")) {
            System.out.println("✔ Migration: Adding supplier_name to paper_items...");
            stmt.execute("ALTER TABLE paper_items ADD COLUMN supplier_name TEXT;");
        }
        
        try {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_paper_items_supplier_uuid ON paper_items(supplier_uuid);");
        } catch (Exception ignored) {
        }
    }

    /**
     * Align invoice/payment tables with Supabase 012: UUID PKs, sync columns, indexes, defaults.
     */
    private static void ensureInvoicePaymentCanonicalSchema(Connection conn, Statement stmt) throws Exception {
        dropLeftoverMigrationBackupTables(conn, stmt);
        stmt.execute(INVOICE_MASTER_DDL);
        stmt.execute(INVOICE_ADJUSTMENTS_DDL);
        stmt.execute(INVOICE_JOB_MAPPING_DDL);
        stmt.execute(PAYMENTS_DDL);
        stmt.execute(PAYMENT_ALLOCATIONS_DDL);
        stmt.execute(PAYMENT_DETAILS_DDL);

        migrateInvoiceMasterToCanonicalIfNeeded(conn, stmt);
        migratePaymentAllocationsToCanonicalIfNeeded(conn, stmt);
        migratePaymentDetailsToCanonicalIfNeeded(conn, stmt);
        migrateInvoiceJobMappingToCanonicalIfNeeded(conn, stmt);

        ensureChildTableSyncColumns(conn, stmt, "invoice_job_mapping");
        ensureChildTableSyncColumns(conn, stmt, "payment_allocations");
        ensureChildTableSyncColumns(conn, stmt, "payment_details");
        ensurePaymentDetailsHaveUuid(conn, stmt);

        ensureInvoiceMasterColumnDefaults(conn, stmt);
        ensureInvoicePaymentIndexes(stmt);
    }

    private static void ensurePaymentDetailsHaveUuid(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "payment_details") || !columnExists(conn, "payment_details", "uuid")) {
            return;
        }
        try {
            int n = stmt.executeUpdate("""
                    UPDATE payment_details SET uuid = lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4'
                        || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1)
                        || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))
                    WHERE uuid IS NULL OR TRIM(uuid) = ''
                    """);
            if (n > 0) {
                System.out.println("Migration: assigned uuid to " + n + " payment_details row(s)");
            }
        } catch (Exception e) {
            System.err.println("Migration: payment_details uuid backfill failed: " + e.getMessage());
        }
    }

    private static void ensureChildTableSyncColumns(Connection conn, Statement stmt, String table)
            throws Exception {
        if (!tableExists(conn, table)) {
            return;
        }
        tryAddColumn(stmt, table, "sync_status", "TEXT DEFAULT 'PENDING'");
        tryAddColumn(stmt, table, "sync_version", "INTEGER DEFAULT 1");
        tryAddColumn(stmt, table, "is_deleted", "INTEGER DEFAULT 0");
        tryAddColumn(stmt, table, "is_active", "INTEGER DEFAULT 1");
        tryAddColumn(stmt, table, "created_at", "TEXT DEFAULT NULL");
        tryAddColumn(stmt, table, "updated_at", "TEXT DEFAULT NULL");
        tryAddColumn(stmt, table, "synced_at", "TEXT DEFAULT NULL");
        tryAddColumn(stmt, table, "deleted_at", "TEXT DEFAULT NULL");

        try {
            stmt.execute("UPDATE " + table + " SET created_at = datetime('now') WHERE created_at IS NULL");
            stmt.execute("UPDATE " + table + " SET updated_at = datetime('now') WHERE updated_at IS NULL");
        } catch (Exception ignored) {
        }
    }

    /**
     * Final pass: every business table uses {@code uuid} TEXT PRIMARY KEY only — no integer {@code id}
     * or legacy {@code *_id} FK columns.
     */
    private static void ensureUuidOnlySchema(Connection conn, Statement stmt) throws Exception {
        if (clientsHasIntegerPk(conn)) {
            migrateClientsToUuidPrimaryKey(conn, stmt);
        }
        migrateInvoiceMasterToCanonicalIfNeeded(conn, stmt);
        migratePaymentsToCanonicalIfNeeded(conn, stmt);
        migrateInvoiceAdjustmentsToCanonicalIfNeeded(conn, stmt);
        migratePaymentAllocationsToCanonicalIfNeeded(conn, stmt);
        migratePaymentDetailsToCanonicalIfNeeded(conn, stmt);
        migrateInvoiceJobMappingToCanonicalIfNeeded(conn, stmt);
        migrateJobsToCanonicalIfNeeded(conn, stmt);
        migrateJobItemsToCanonicalIfNeeded(conn, stmt);
        migrateBillingToCanonicalIfNeeded(conn, stmt);
        migrateSuppliersToCanonicalIfNeeded(conn, stmt);
        ensureJobItemDetailTablesUuid(conn, stmt);
        assertNoLegacyIntegerIdColumns(conn);
    }

    private static boolean hasAnyLegacyColumn(Connection conn, String table, String... columns)
            throws Exception {
        if (!tableExists(conn, table)) {
            return false;
        }
        for (String col : columns) {
            if (columnExists(conn, table, col)) {
                return true;
            }
        }
        return false;
    }

    private static void migrateInvoiceMasterToCanonicalIfNeeded(Connection conn, Statement stmt) throws Exception {
        if (!hasAnyLegacyColumn(conn, "invoice_master", "id", "client_id", "parent_invoice_id", "replaced_by_invoice_id")) {
            return;
        }
        ensureInvoiceMasterUuid(conn, stmt);
        System.out.println("Migration: invoice_master → uuid PK (no integer id / *_id columns)");
        stmt.execute("PRAGMA foreign_keys=OFF");
        stmt.execute("BEGIN IMMEDIATE");
        try {
            stmt.execute("""
                    CREATE TABLE invoice_master_uuid_pk (
                        uuid TEXT PRIMARY KEY NOT NULL,
                        invoice_no TEXT,
                        client_uuid TEXT,
                        client_name TEXT DEFAULT '',
                        invoice_date TEXT,
                        period_from TEXT,
                        period_to TEXT,
                        amount REAL NOT NULL DEFAULT 0,
                        paid_amount REAL NOT NULL DEFAULT 0,
                        due_amount REAL NOT NULL DEFAULT 0,
                        payment_status TEXT DEFAULT 'UNPAID',
                        last_payment_date TEXT,
                        type TEXT DEFAULT '',
                        status TEXT DEFAULT 'DRAFT',
                        is_void INTEGER NOT NULL DEFAULT 0,
                        void_reason TEXT DEFAULT '',
                        void_date TEXT,
                        replaced_by_invoice_uuid TEXT,
                        parent_invoice_uuid TEXT,
                        status_updated_by TEXT DEFAULT '',
                        file_path TEXT DEFAULT '',
                        document_series TEXT DEFAULT 'GST_INVOICE',
                        sync_status TEXT DEFAULT 'PENDING',
                        sync_version INTEGER DEFAULT 1,
                        is_deleted INTEGER DEFAULT 0,
                        is_active INTEGER DEFAULT 1,
                        created_at TEXT DEFAULT (datetime('now')),
                        updated_at TEXT DEFAULT (datetime('now')),
                        synced_at TEXT DEFAULT NULL,
                        deleted_at TEXT DEFAULT NULL,
                        FOREIGN KEY (client_uuid) REFERENCES clients(uuid) ON DELETE SET NULL,
                        FOREIGN KEY (parent_invoice_uuid) REFERENCES invoice_master_uuid_pk(uuid) ON DELETE SET NULL,
                        FOREIGN KEY (replaced_by_invoice_uuid) REFERENCES invoice_master_uuid_pk(uuid) ON DELETE SET NULL
                    )
                    """);
            boolean hasClientId = columnExists(conn, "invoice_master", "client_id");
            boolean hasClientUuid = columnExists(conn, "invoice_master", "client_uuid");
            boolean hasParentId = columnExists(conn, "invoice_master", "parent_invoice_id");
            boolean hasParentUuid = columnExists(conn, "invoice_master", "parent_invoice_uuid");
            boolean hasReplacedId = columnExists(conn, "invoice_master", "replaced_by_invoice_id");
            boolean hasReplacedUuid = columnExists(conn, "invoice_master", "replaced_by_invoice_uuid");
            boolean clientsHasId = columnExists(conn, "clients", "id");
            boolean invoiceMasterHasId = columnExists(conn, "invoice_master", "id");
            String clientUuidExpr;
            if (hasClientId) {
                String fromClient = clientsHasId
                        ? "(SELECT c.uuid FROM clients c WHERE c.id = im.client_id)"
                        : "(SELECT c.uuid FROM clients c WHERE c.uuid = CAST(im.client_id AS TEXT))";
                clientUuidExpr = hasClientUuid
                        ? "COALESCE(NULLIF(TRIM(im.client_uuid), ''), " + fromClient + ")"
                        : fromClient;
            } else if (hasClientUuid) {
                clientUuidExpr = "im.client_uuid";
            } else {
                clientUuidExpr = "NULL";
            }
            String parentFromId = invoiceMasterHasId
                    ? "(SELECT p.uuid FROM invoice_master p WHERE p.id = im.parent_invoice_id)"
                    : "(SELECT p.uuid FROM invoice_master p WHERE p.uuid = CAST(im.parent_invoice_id AS TEXT))";
            String parentExpr;
            if (hasParentId) {
                parentExpr = hasParentUuid
                        ? "COALESCE(NULLIF(TRIM(im.parent_invoice_uuid), ''), " + parentFromId + ")"
                        : parentFromId;
            } else if (hasParentUuid) {
                parentExpr = "im.parent_invoice_uuid";
            } else {
                parentExpr = "NULL";
            }
            String replacedFromId = invoiceMasterHasId
                    ? "(SELECT p.uuid FROM invoice_master p WHERE p.id = im.replaced_by_invoice_id)"
                    : "(SELECT p.uuid FROM invoice_master p WHERE p.uuid = CAST(im.replaced_by_invoice_id AS TEXT))";
            String replacedExpr;
            if (hasReplacedId) {
                replacedExpr = hasReplacedUuid
                        ? "COALESCE(NULLIF(TRIM(im.replaced_by_invoice_uuid), ''), " + replacedFromId + ")"
                        : replacedFromId;
            } else if (hasReplacedUuid) {
                replacedExpr = "im.replaced_by_invoice_uuid";
            } else {
                replacedExpr = "NULL";
            }
            // Do not use String.formatted here: NEW_UUID_SQL contains '%' (modulo).
            String uuidExpr = sqlCoalesceNewUuid("im", "uuid", columnExists(conn, "invoice_master", "uuid"));
            String selectCols = String.join(",\n                        ",
                    uuidExpr,
                    imCol(conn, "invoice_no", "NULL"),
                    clientUuidExpr,
                    imColCoalesce(conn, "client_name", "''"),
                    imCol(conn, "invoice_date", "NULL"),
                    imCol(conn, "period_from", "NULL"),
                    imCol(conn, "period_to", "NULL"),
                    imColCoalesce(conn, "amount", "0"),
                    imColCoalesce(conn, "paid_amount", "0"),
                    imColCoalesce(conn, "due_amount", "0"),
                    imColCoalesceTrim(conn, "payment_status", "'UNPAID'"),
                    imCol(conn, "last_payment_date", "NULL"),
                    imColCoalesce(conn, "type", "''"),
                    imColCoalesceTrim(conn, "status", "'DRAFT'"),
                    imColCoalesce(conn, "is_void", "0"),
                    imColCoalesce(conn, "void_reason", "''"),
                    imCol(conn, "void_date", "NULL"),
                    replacedExpr,
                    parentExpr,
                    imColCoalesce(conn, "status_updated_by", "''"),
                    imColCoalesce(conn, "file_path", "''"),
                    imColCoalesceTrim(conn, "document_series", "'GST_INVOICE'"),
                    imColCoalesce(conn, "sync_status", "'PENDING'"),
                    imColCoalesce(conn, "sync_version", "1"),
                    imColCoalesce(conn, "is_deleted", "0"),
                    imColCoalesce(conn, "is_active", "1"),
                    imColCoalesce(conn, "created_at", "datetime('now')"),
                    imColCoalesce(conn, "updated_at", "datetime('now')"),
                    imCol(conn, "synced_at", "NULL"),
                    imCol(conn, "deleted_at", "NULL"));
            stmt.execute("""
                    INSERT INTO invoice_master_uuid_pk (
                        uuid, invoice_no, client_uuid, client_name, invoice_date, period_from, period_to,
                        amount, paid_amount, due_amount, payment_status, last_payment_date,
                        type, status, is_void, void_reason, void_date,
                        replaced_by_invoice_uuid, parent_invoice_uuid, status_updated_by, file_path, document_series,
                        sync_status, sync_version, is_deleted, is_active, created_at, updated_at, synced_at, deleted_at
                    )
                    SELECT
                        """
                    + selectCols
                    + """
                    
                    FROM invoice_master im
                    """);
            stmt.execute("DROP TABLE invoice_master");
            stmt.execute("ALTER TABLE invoice_master_uuid_pk RENAME TO invoice_master");
            stmt.execute("COMMIT");
        } catch (Exception e) {
            stmt.execute("ROLLBACK");
            throw e;
        } finally {
            stmt.execute("PRAGMA foreign_keys=ON");
        }
    }

    private static void migratePaymentAllocationsToCanonicalIfNeeded(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "payment_allocations")) {
            return;
        }
        boolean legacy = hasAnyLegacyColumn(conn, "payment_allocations", "id", "payment_id", "invoice_id");
        boolean missingSync = tableExists(conn, "payment_allocations")
                && !columnExists(conn, "payment_allocations", "sync_status");
        if (!legacy && !missingSync) {
            return;
        }
        System.out.println("Migration: payment_allocations → canonical UUID + sync columns");
        stmt.execute("PRAGMA foreign_keys=OFF");
        stmt.execute("ALTER TABLE payment_allocations RENAME TO payment_allocations_old");
        stmt.execute(PAYMENT_ALLOCATIONS_DDL.replace("IF NOT EXISTS ", ""));
        String oldTable = "payment_allocations_old";
        boolean hasUuid = columnExists(conn, oldTable, "uuid");
        boolean hasPaymentUuid = columnExists(conn, oldTable, "payment_uuid");
        boolean hasInvoiceUuid = columnExists(conn, oldTable, "invoice_uuid");
        boolean paymentsHasId = columnExists(conn, "payments", "id");
        boolean invoiceMasterHasId = columnExists(conn, "invoice_master", "id");
        String uuidExpr = sqlCoalesceNewUuid("old", "uuid", hasUuid);
        String payFromId = paymentsHasId
                ? "(SELECT p.uuid FROM payments p WHERE p.id = old.payment_id)"
                : "(SELECT p.uuid FROM payments p WHERE p.uuid = CAST(old.payment_id AS TEXT))";
        String payExpr = hasPaymentUuid
                ? "COALESCE(NULLIF(TRIM(old.payment_uuid), ''), " + payFromId + ")"
                : payFromId;
        String invFromId = invoiceMasterHasId
                ? "(SELECT im.uuid FROM invoice_master im WHERE im.id = old.invoice_id)"
                : "(SELECT im.uuid FROM invoice_master im WHERE im.uuid = CAST(old.invoice_id AS TEXT))";
        String invExpr = hasInvoiceUuid
                ? "COALESCE(NULLIF(TRIM(old.invoice_uuid), ''), " + invFromId + ")"
                : invFromId;
        String allocCols = String.join(",\n                    ",
                uuidExpr,
                payExpr,
                invExpr,
                oldCol(conn, oldTable, "allocated_amount", "0"),
                oldColCoalesce(conn, oldTable, "sync_status", "'PENDING'"),
                oldColCoalesce(conn, oldTable, "sync_version", "1"),
                oldColCoalesce(conn, oldTable, "is_deleted", "0"),
                oldColCoalesce(conn, oldTable, "is_active", "1"),
                oldColCoalesce(conn, oldTable, "created_at", "datetime('now')"),
                oldColCoalesce(conn, oldTable, "updated_at", "datetime('now')"),
                oldCol(conn, oldTable, "synced_at", "NULL"),
                oldCol(conn, oldTable, "deleted_at", "NULL"));
        stmt.execute("""
                INSERT INTO payment_allocations (
                    uuid, payment_uuid, invoice_uuid, allocated_amount,
                    sync_status, sync_version, is_deleted, is_active, created_at, updated_at, synced_at, deleted_at
                )
                SELECT
                    """
                + allocCols
                + """
                
                FROM payment_allocations_old old
                WHERE """
                + " " + payExpr + " IS NOT NULL AND " + invExpr + " IS NOT NULL");
        stmt.execute("DROP TABLE payment_allocations_old");
        stmt.execute("PRAGMA foreign_keys=ON");
    }

    private static void migratePaymentDetailsToCanonicalIfNeeded(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "payment_details")) {
            return;
        }
        boolean legacy = hasAnyLegacyColumn(conn, "payment_details", "id", "payment_id");
        boolean missingSync = tableExists(conn, "payment_details")
                && !columnExists(conn, "payment_details", "sync_status");
        if (!legacy && !missingSync) {
            return;
        }
        System.out.println("Migration: payment_details → canonical UUID + sync columns");
        stmt.execute("PRAGMA foreign_keys=OFF");
        stmt.execute("ALTER TABLE payment_details RENAME TO payment_details_old");
        stmt.execute(PAYMENT_DETAILS_DDL.replace("IF NOT EXISTS ", ""));
        String oldTable = "payment_details_old";
        boolean hasUuid = columnExists(conn, oldTable, "uuid");
        boolean hasPaymentUuid = columnExists(conn, oldTable, "payment_uuid");
        boolean paymentsHasId = columnExists(conn, "payments", "id");
        String uuidExpr = sqlCoalesceNewUuid("old", "uuid", hasUuid);
        String payFromId = paymentsHasId
                ? "(SELECT p.uuid FROM payments p WHERE p.id = old.payment_id)"
                : "(SELECT p.uuid FROM payments p WHERE p.uuid = CAST(old.payment_id AS TEXT))";
        String payExpr = hasPaymentUuid
                ? "COALESCE(NULLIF(TRIM(old.payment_uuid), ''), " + payFromId + ")"
                : payFromId;
        String detailCols = String.join(",\n                    ",
                uuidExpr,
                payExpr,
                oldCol(conn, oldTable, "field_key", "''"),
                oldColCoalesce(conn, oldTable, "field_value", "''"),
                oldColCoalesce(conn, oldTable, "sync_status", "'PENDING'"),
                oldColCoalesce(conn, oldTable, "sync_version", "1"),
                oldColCoalesce(conn, oldTable, "is_deleted", "0"),
                oldColCoalesce(conn, oldTable, "is_active", "1"),
                oldColCoalesce(conn, oldTable, "created_at", "datetime('now')"),
                oldColCoalesce(conn, oldTable, "updated_at", "datetime('now')"),
                oldCol(conn, oldTable, "synced_at", "NULL"),
                oldCol(conn, oldTable, "deleted_at", "NULL"));
        stmt.execute("""
                INSERT INTO payment_details (
                    uuid, payment_uuid, field_key, field_value,
                    sync_status, sync_version, is_deleted, is_active, created_at, updated_at, synced_at, deleted_at
                )
                SELECT
                    """
                + detailCols
                + """
                
                FROM payment_details_old old
                WHERE """
                + " " + payExpr + " IS NOT NULL");
        stmt.execute("DROP TABLE payment_details_old");
        stmt.execute("PRAGMA foreign_keys=ON");
    }

    private static void migrateInvoiceJobMappingToCanonicalIfNeeded(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "invoice_job_mapping")) {
            return;
        }
        if (!hasAnyLegacyColumn(conn, "invoice_job_mapping", "id", "invoice_id", "job_id")) {
            return;
        }
        System.out.println("Migration: invoice_job_mapping integer ids → uuids");
        stmt.execute("PRAGMA foreign_keys=OFF");
        stmt.execute("ALTER TABLE invoice_job_mapping RENAME TO invoice_job_mapping_old");
        stmt.execute(INVOICE_JOB_MAPPING_DDL.replace("IF NOT EXISTS ", ""));
        boolean invoiceMasterHasId = columnExists(conn, "invoice_master", "id");
        boolean jobsHasId = columnExists(conn, "jobs", "id");
        String invFromId = invoiceMasterHasId
                ? "(SELECT im.uuid FROM invoice_master im WHERE im.id = old.invoice_id)"
                : "(SELECT im.uuid FROM invoice_master im WHERE im.uuid = CAST(old.invoice_id AS TEXT))";
        String jobFromId = jobsHasId
                ? "(SELECT j.uuid FROM jobs j WHERE j.id = old.job_id)"
                : "(SELECT j.uuid FROM jobs j WHERE j.uuid = CAST(old.job_id AS TEXT))";
        String invExpr = columnExists(conn, "invoice_job_mapping_old", "invoice_uuid")
                ? "COALESCE(NULLIF(TRIM(old.invoice_uuid), ''), " + invFromId + ")"
                : invFromId;
        String jobExpr = columnExists(conn, "invoice_job_mapping_old", "job_uuid")
                ? "COALESCE(NULLIF(TRIM(old.job_uuid), ''), " + jobFromId + ")"
                : jobFromId;
        String uuidExpr = sqlCoalesceNewUuid("old", "uuid", columnExists(conn, "invoice_job_mapping_old", "uuid"));
        stmt.execute("""
                INSERT INTO invoice_job_mapping (uuid, invoice_uuid, job_uuid, created_at, updated_at)
                SELECT
                    """
                + uuidExpr + ", " + invExpr + ", " + jobExpr + ", "
                + oldColCoalesce(conn, "invoice_job_mapping_old", "created_at", "datetime('now')") + ", "
                + oldColCoalesce(conn, "invoice_job_mapping_old", "updated_at", "datetime('now')")
                + """
                
                FROM invoice_job_mapping_old old
                WHERE """
                + " " + invExpr + " IS NOT NULL AND " + jobExpr + " IS NOT NULL");
        stmt.execute("DROP TABLE invoice_job_mapping_old");
        stmt.execute("PRAGMA foreign_keys=ON");
    }

    /**
     * Recreates {@code invoice_master} rows when jobs still reference an invoice UUID but the master
     * row was removed (e.g. {@code deleteEmptyInvoices} ran before {@code invoice_job_mapping} existed).
     */
    private static void reconcileOrphanedInvoiceMasters(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "invoice_master") || !tableExists(conn, "jobs")) {
            return;
        }
        try (ResultSet rs = stmt.executeQuery("""
                SELECT COUNT(DISTINCT j.invoice_uuid) AS n
                FROM jobs j
                WHERE j.invoice_uuid IS NOT NULL AND TRIM(j.invoice_uuid) <> ''
                  AND IFNULL(j.is_deleted, 0) = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM invoice_master im WHERE im.uuid = j.invoice_uuid
                  )
                """)) {
            if (!rs.next() || rs.getInt("n") <= 0) {
                return;
            }
            System.out.println("Migration: reconciling " + rs.getInt("n") + " orphaned invoice(s) from jobs");
        }
        stmt.execute("""
                INSERT INTO invoice_master (
                    uuid, invoice_no, client_uuid, client_name, invoice_date,
                    amount, paid_amount, due_amount, payment_status,
                    type, status, is_void, document_series, sync_status, sync_version
                )
                SELECT
                    o.invoice_uuid,
                    COALESCE(
                        (SELECT d.temporary_number
                         FROM document_number_mappings d
                         WHERE d.entity_uuid = o.invoice_uuid
                           AND d.temporary_number LIKE 'TEMP%'
                         LIMIT 1),
                        'TEMP-' || printf('%03d',
                            COALESCE(
                                (SELECT CAST(ns.offline_current_number AS INTEGER) + 1
                                 FROM number_sequences ns
                                 WHERE ns.sequence_key = 'temp_invoice'),
                                1
                            ) + o.ord - 1)
                    ),
                    o.client_uuid,
                    COALESCE(
                        (SELECT COALESCE(NULLIF(TRIM(c.client_name), ''), NULLIF(TRIM(c.business_name), ''), '')
                         FROM clients c WHERE c.uuid = o.client_uuid LIMIT 1),
                        ''),
                    COALESCE(o.invoice_date, date('now')),
                    o.amount,
                    0,
                    o.amount,
                    'UNPAID',
                    'DATE_RANGE',
                    'DRAFT',
                    0,
                    'GST_INVOICE',
                    'PENDING',
                    1
                FROM (
                    SELECT
                        j.invoice_uuid,
                        MIN(j.client_uuid) AS client_uuid,
                        date(COALESCE(MAX(j.job_date), datetime('now'))) AS invoice_date,
                        COALESCE((
                            SELECT SUM(ji.amount)
                            FROM job_items ji
                            INNER JOIN jobs j2 ON j2.uuid = ji.job_uuid
                            WHERE j2.invoice_uuid = j.invoice_uuid
                              AND IFNULL(ji.is_deleted, 0) = 0
                        ), COALESCE(SUM(j.amount), 0)) AS amount,
                        ROW_NUMBER() OVER (ORDER BY MIN(j.job_date), j.invoice_uuid) AS ord
                    FROM jobs j
                    WHERE j.invoice_uuid IS NOT NULL AND TRIM(j.invoice_uuid) <> ''
                      AND IFNULL(j.is_deleted, 0) = 0
                      AND NOT EXISTS (
                        SELECT 1 FROM invoice_master im WHERE im.uuid = j.invoice_uuid
                      )
                    GROUP BY j.invoice_uuid
                ) o
                """);
    }

    private static void ensureInvoiceJobMappingFromJobs(Statement stmt) {
        try {
            stmt.execute("""
                    INSERT OR IGNORE INTO invoice_job_mapping (uuid, invoice_uuid, job_uuid, sync_status, created_at, updated_at)
                    SELECT
                        lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))),
                        j.invoice_uuid, j.uuid, 'PENDING', datetime('now'), datetime('now')
                    FROM jobs j
                    WHERE j.invoice_uuid IS NOT NULL AND TRIM(j.invoice_uuid) != ''
                    """);
        } catch (Exception ignored) {
        }
    }

    /** Rows inserted without uuid (legacy linkJobUuidsToInvoice) never synced and break invoice job views. */
    private static void backfillInvoiceJobMappingUuids(Statement stmt) {
        try {
            int n = stmt.executeUpdate("""
                    UPDATE invoice_job_mapping SET
                      uuid = lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4'
                        || substr(hex(randomblob(2)), 2) || '-'
                        || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-'
                        || hex(randomblob(6))),
                      sync_status = 'PENDING',
                      updated_at = datetime('now')
                    WHERE uuid IS NULL OR TRIM(uuid) = ''
                    """);
            if (n > 0) {
                System.out.println("Migration: backfilled uuid on " + n + " invoice_job_mapping row(s)");
            }
        } catch (Exception e) {
            System.err.println("Migration: invoice_job_mapping uuid backfill failed: " + e.getMessage());
        }
    }

    /** Align {@code jobs.amount} with sum of active {@code job_items} (legacy rows). */
    private static void syncAllJobAmountsFromItems(Statement stmt) {
        try {
            int updated = stmt.executeUpdate("""
                    UPDATE jobs SET
                      amount = (
                        SELECT COALESCE(SUM(ji.amount), 0)
                        FROM job_items ji
                        WHERE ji.job_uuid = jobs.uuid AND COALESCE(ji.is_deleted, 0) = 0
                      ),
                      updated_at = datetime('now')
                    WHERE IFNULL(is_deleted, 0) = 0
                      AND amount != (
                        SELECT COALESCE(SUM(ji.amount), 0)
                        FROM job_items ji
                        WHERE ji.job_uuid = jobs.uuid AND COALESCE(ji.is_deleted, 0) = 0
                      )
                    """);
            if (updated > 0) {
                System.out.println("Migration: synced jobs.amount from job_items for " + updated + " job(s)");
            }
        } catch (Exception e) {
            System.err.println("Migration: sync jobs.amount from job_items failed: " + e.getMessage());
        }
    }

    /** Fills {@code jobs.invoice_uuid} when mapping exists but the job row was never updated. */
    private static void syncJobsInvoiceUuidFromMappings(Statement stmt) {
        try {
            int updated = stmt.executeUpdate("""
                    UPDATE jobs
                    SET invoice_uuid = (
                        SELECT m.invoice_uuid
                        FROM invoice_job_mapping m
                        WHERE m.job_uuid = jobs.uuid
                        LIMIT 1
                    )
                    WHERE (invoice_uuid IS NULL OR TRIM(invoice_uuid) = '')
                      AND EXISTS (
                        SELECT 1 FROM invoice_job_mapping m
                        WHERE m.job_uuid = jobs.uuid
                          AND m.invoice_uuid IS NOT NULL AND TRIM(m.invoice_uuid) <> ''
                      )
                    """);
            if (updated > 0) {
                System.out.println("Migration: set jobs.invoice_uuid on " + updated + " row(s) from invoice_job_mapping");
            }
        } catch (Exception e) {
            System.err.println("Migration: sync jobs.invoice_uuid from mapping failed: " + e.getMessage());
        }
    }

    private static void ensureInvoiceMasterColumnDefaults(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "invoice_master")) {
            return;
        }
        tryAddColumn(stmt, "invoice_master", "document_series", "TEXT DEFAULT 'GST_INVOICE'");
        tryAddColumn(stmt, "invoice_master", "parent_invoice_uuid", "TEXT");
        tryAddColumn(stmt, "invoice_master", "replaced_by_invoice_uuid", "TEXT");
        tryAddColumn(stmt, "invoice_master", "client_uuid", "TEXT");
        tryAddColumn(stmt, "invoice_master", "sync_status", "TEXT DEFAULT 'PENDING'");
        tryAddColumn(stmt, "invoice_master", "sync_version", "INTEGER DEFAULT 1");
        tryAddColumn(stmt, "invoice_master", "is_deleted", "INTEGER DEFAULT 0");
        tryAddColumn(stmt, "invoice_master", "is_active", "INTEGER DEFAULT 1");
        tryAddColumn(stmt, "invoice_master", "created_at", "TEXT DEFAULT (datetime('now'))");
        tryAddColumn(stmt, "invoice_master", "updated_at", "TEXT DEFAULT (datetime('now'))");
        tryAddColumn(stmt, "invoice_master", "synced_at", "TEXT");
        tryAddColumn(stmt, "invoice_master", "deleted_at", "TEXT");
        stmt.execute("""
                UPDATE invoice_master SET payment_status = 'UNPAID'
                WHERE payment_status IS NULL OR TRIM(payment_status) = ''
                """);
        stmt.execute("""
                UPDATE invoice_master SET status = 'DRAFT'
                WHERE status IS NULL OR TRIM(status) = ''
                """);
        stmt.execute("""
                UPDATE invoice_master SET document_series = 'GST_INVOICE'
                WHERE document_series IS NULL OR TRIM(document_series) = ''
                """);
        if (columnExists(conn, "invoice_master", "client_id") && columnExists(conn, "invoice_master", "client_uuid")) {
            stmt.execute("""
                    UPDATE invoice_master
                    SET client_uuid = (SELECT c.uuid FROM clients c WHERE c.id = invoice_master.client_id)
                    WHERE (client_uuid IS NULL OR TRIM(client_uuid) = '') AND client_id IS NOT NULL
                    """);
        }
    }

    private static void ensureInvoicePaymentIndexes(Statement stmt) {
        try {
            stmt.execute("DROP INDEX IF EXISTS ux_invoice_no");
        } catch (Exception ignored) {
        }
        try {
            stmt.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS ux_invoice_master_invoice_no
                    ON invoice_master(invoice_no)
                    WHERE invoice_no IS NOT NULL AND is_deleted = 0
                    """);
        } catch (Exception ignored) {
        }
        try {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_invoice_master_client_uuid ON invoice_master(client_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_invoice_master_sync_status ON invoice_master(sync_status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_invoice_master_status ON invoice_master(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_invoice_adjustments_invoice_uuid ON invoice_adjustments(invoice_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_invoice_job_mapping_job_uuid ON invoice_job_mapping(job_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_payments_client_uuid ON payments(client_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_payments_sync_status ON payments(sync_status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_payment_allocations_payment_uuid ON payment_allocations(payment_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_payment_allocations_invoice_uuid ON payment_allocations(invoice_uuid)");
        } catch (Exception ignored) {
        }
    }

    private static void tryAddColumn(Statement stmt, String table, String column, String ddl) {
        try {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddl);
        } catch (Exception ignored) {
        }
    }

    private static void migratePaymentsToCanonicalIfNeeded(Connection conn, Statement stmt) throws Exception {
        if (!hasAnyLegacyColumn(conn, "payments", "id", "client_id")) {
            return;
        }
        if (columnExists(conn, "payments", "id") && !columnExists(conn, "payments", "uuid")) {
            ensurePaymentsUuid(conn, stmt);
            return;
        }
        System.out.println("Migration: payments → uuid PK (strip id / client_id)");
        stmt.execute("PRAGMA foreign_keys=OFF");
        stmt.execute("ALTER TABLE payments RENAME TO payments_old");
        stmt.execute(PAYMENTS_DDL.replace("IF NOT EXISTS ", ""));
        boolean hasClientId = columnExists(conn, "payments_old", "client_id");
        String clientExpr = hasClientId
                ? "COALESCE(NULLIF(TRIM(old.client_uuid), ''), (SELECT c.uuid FROM clients c WHERE c.uuid = CAST(old.client_id AS TEXT) OR c.id = old.client_id LIMIT 1))"
                : "old.client_uuid";
        stmt.execute("""
                INSERT INTO payments (
                    uuid, client_uuid, amount, payment_date, method, type,
                    sync_status, sync_version, is_deleted, is_active, created_at, updated_at, synced_at, deleted_at
                )
                SELECT
                    COALESCE(NULLIF(TRIM(old.uuid), ''), lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))),
                    %s, COALESCE(old.amount, 0), old.payment_date, COALESCE(old.method, ''), COALESCE(old.type, 'Payment'),
                    COALESCE(old.sync_status, 'PENDING'), COALESCE(old.sync_version, 1),
                    COALESCE(old.is_deleted, 0), COALESCE(old.is_active, 1),
                    COALESCE(old.created_at, datetime('now')), COALESCE(old.updated_at, datetime('now')),
                    old.synced_at, old.deleted_at
                FROM payments_old old
                """.formatted(clientExpr));
        stmt.execute("DROP TABLE payments_old");
        stmt.execute("PRAGMA foreign_keys=ON");
    }

    private static void migrateInvoiceAdjustmentsToCanonicalIfNeeded(Connection conn, Statement stmt) throws Exception {
        if (!hasAnyLegacyColumn(conn, "invoice_adjustments", "id", "invoice_id")) {
            return;
        }
        if (columnExists(conn, "invoice_adjustments", "id") && !columnExists(conn, "invoice_adjustments", "uuid")) {
            ensureInvoiceAdjustmentsUuid(conn, stmt);
            return;
        }
        System.out.println("Migration: invoice_adjustments → uuid PK (strip id / invoice_id)");
        stmt.execute("PRAGMA foreign_keys=OFF");
        stmt.execute("ALTER TABLE invoice_adjustments RENAME TO invoice_adjustments_old");
        stmt.execute(INVOICE_ADJUSTMENTS_DDL.replace("IF NOT EXISTS ", ""));
        boolean hasInvoiceId = columnExists(conn, "invoice_adjustments_old", "invoice_id");
        String invExpr = hasInvoiceId
                ? "COALESCE(NULLIF(TRIM(old.invoice_uuid), ''), (SELECT im.uuid FROM invoice_master im WHERE im.uuid = CAST(old.invoice_id AS TEXT) OR im.id = old.invoice_id LIMIT 1))"
                : "old.invoice_uuid";
        stmt.execute("""
                INSERT INTO invoice_adjustments (
                    uuid, invoice_uuid, type, note_no, amount, reason, date,
                    sync_status, sync_version, is_deleted, is_active, created_at, updated_at, synced_at, deleted_at
                )
                SELECT
                    COALESCE(NULLIF(TRIM(old.uuid), ''), lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))),
                    %s, old.type, old.note_no, old.amount, COALESCE(old.reason, ''), COALESCE(old.date, date('now')),
                    COALESCE(old.sync_status, 'PENDING'), COALESCE(old.sync_version, 1),
                    COALESCE(old.is_deleted, 0), COALESCE(old.is_active, 1),
                    COALESCE(old.created_at, datetime('now')), COALESCE(old.updated_at, datetime('now')),
                    old.synced_at, old.deleted_at
                FROM invoice_adjustments_old old
                WHERE %s IS NOT NULL
                """.formatted(invExpr, invExpr));
        stmt.execute("DROP TABLE invoice_adjustments_old");
        stmt.execute("PRAGMA foreign_keys=ON");
    }

    private static void migrateJobsToCanonicalIfNeeded(Connection conn, Statement stmt) throws Exception {
        if (!tableExists(conn, "jobs")) {
            return;
        }
        if (columnExists(conn, "jobs", "id")) {
            migrateJobsToUuidPrimaryKey(conn, stmt);
            return;
        }
        if (!hasAnyLegacyColumn(conn, "jobs", "client_id", "invoice_id", "job_no")) {
            return;
        }
        System.out.println("Migration: jobs → strip client_id / invoice_id / job_no");
        stmt.execute("PRAGMA foreign_keys=OFF");
        stmt.execute("ALTER TABLE jobs RENAME TO jobs_old");
        stmt.execute(JOBS_DDL.replace("IF NOT EXISTS ", ""));
        boolean hasClientId = columnExists(conn, "jobs_old", "client_id");
        boolean hasInvoiceId = columnExists(conn, "jobs_old", "invoice_id");
        String clientExpr = hasClientId
                ? "COALESCE(NULLIF(TRIM(old.client_uuid), ''), NULLIF(TRIM(CAST(old.client_id AS TEXT)), ''), '')"
                : "COALESCE(NULLIF(TRIM(old.client_uuid), ''), '')";
        String invExpr = hasInvoiceId
                ? "COALESCE(NULLIF(TRIM(old.invoice_uuid), ''), (SELECT im.uuid FROM invoice_master im WHERE im.uuid = CAST(old.invoice_id AS TEXT) LIMIT 1))"
                : "old.invoice_uuid";
        String codeExpr = columnExists(conn, "jobs_old", "job_code")
                ? "COALESCE(NULLIF(TRIM(old.job_code), ''), NULLIF(TRIM(old.job_no), ''), '')"
                : "COALESCE(NULLIF(TRIM(old.job_no), ''), '')";
        stmt.execute("""
                INSERT INTO jobs (
                    uuid, client_uuid, invoice_uuid, job_code, job_title, job_type, description, amount,
                    status, child_status, job_number_mode, image_path, remarks, job_date, delivery_date,
                    sync_status, sync_version, is_deleted, is_active, created_at, updated_at, synced_at, deleted_at
                )
                SELECT
                    COALESCE(NULLIF(TRIM(old.uuid), ''), lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))),
                    %s, %s, %s,
                    COALESCE(old.job_title, ''), COALESCE(old.job_type, ''), COALESCE(old.description, ''),
                    COALESCE(old.amount, 0), COALESCE(old.status, 'Draft'), COALESCE(old.child_status, ''),
                    COALESCE(old.job_number_mode, 'AUTO'), COALESCE(old.image_path, ''), COALESCE(old.remarks, ''),
                    COALESCE(old.job_date, datetime('now')), old.delivery_date,
                    COALESCE(old.sync_status, 'PENDING'), COALESCE(old.sync_version, 1),
                    COALESCE(old.is_deleted, 0), COALESCE(old.is_active, 1),
                    COALESCE(old.created_at, datetime('now')), COALESCE(old.updated_at, datetime('now')),
                    old.synced_at, old.deleted_at
                FROM jobs_old old
                WHERE TRIM(%s) != ''
                """.formatted(clientExpr, invExpr, codeExpr, codeExpr));
        stmt.execute("DROP TABLE jobs_old");
        stmt.execute("PRAGMA foreign_keys=ON");
    }

    private static void migrateJobItemsToCanonicalIfNeeded(Connection conn, Statement stmt) throws Exception {
        if (!hasAnyLegacyColumn(conn, "job_items", "id", "job_id")) {
            return;
        }
        if (columnExists(conn, "job_items", "id") && !columnExists(conn, "job_items", "uuid")) {
            ensureJobItemsUuid(conn, stmt);
            return;
        }
        System.out.println("Migration: job_items → uuid PK (strip id / job_id)");
        stmt.execute("PRAGMA foreign_keys=OFF");
        stmt.execute("ALTER TABLE job_items RENAME TO job_items_old");
        stmt.execute(JOB_ITEMS_DDL.replace("IF NOT EXISTS ", ""));
        boolean hasJobId = columnExists(conn, "job_items_old", "job_id");
        String jobExpr = hasJobId
                ? "COALESCE(NULLIF(TRIM(old.job_uuid), ''), (SELECT j.uuid FROM jobs j WHERE j.uuid = CAST(old.job_id AS TEXT) OR j.id = old.job_id LIMIT 1))"
                : "old.job_uuid";
        stmt.execute("""
                INSERT INTO job_items (
                    uuid, job_uuid, type, description, amount, sort_order,
                    sync_status, sync_version, is_deleted, is_active, created_at, updated_at, synced_at, deleted_at
                )
                SELECT
                    COALESCE(NULLIF(TRIM(old.uuid), ''), lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))),
                    %s, old.type, old.description, COALESCE(old.amount, 0), COALESCE(old.sort_order, 0),
                    COALESCE(old.sync_status, 'PENDING'), COALESCE(old.sync_version, 1),
                    COALESCE(old.is_deleted, 0), COALESCE(old.is_active, 1),
                    COALESCE(old.created_at, datetime('now')), COALESCE(old.updated_at, datetime('now')),
                    old.synced_at, old.deleted_at
                FROM job_items_old old
                WHERE %s IS NOT NULL
                """.formatted(jobExpr, jobExpr));
        stmt.execute("DROP TABLE job_items_old");
        stmt.execute("PRAGMA foreign_keys=ON");
    }

    private static void migrateBillingToCanonicalIfNeeded(Connection conn, Statement stmt) throws Exception {
        if (!hasAnyLegacyColumn(conn, "billing", "id", "job_id", "client_id")) {
            return;
        }
        System.out.println("Migration: billing → uuid PK (strip id / job_id / client_id)");
        stmt.execute("PRAGMA foreign_keys=OFF");
        stmt.execute("ALTER TABLE billing RENAME TO billing_old");
        stmt.execute("""
                CREATE TABLE billing (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    job_uuid TEXT,
                    client_uuid TEXT,
                    amount REAL,
                    bill_date TEXT,
                    sync_status TEXT DEFAULT 'PENDING',
                    sync_version INTEGER DEFAULT 1,
                    is_deleted INTEGER DEFAULT 0,
                    is_active INTEGER DEFAULT 1,
                    created_at TEXT DEFAULT (datetime('now')),
                    updated_at TEXT DEFAULT (datetime('now')),
                    synced_at TEXT DEFAULT NULL,
                    deleted_at TEXT DEFAULT NULL,
                    FOREIGN KEY(job_uuid) REFERENCES jobs(uuid) ON DELETE SET NULL,
                    FOREIGN KEY(client_uuid) REFERENCES clients(uuid) ON DELETE SET NULL
                )
                """);
        String oldTable = "billing_old";
        boolean hasJobId = columnExists(conn, oldTable, "job_id");
        boolean hasJobUuid = columnExists(conn, oldTable, "job_uuid");
        boolean hasClientId = columnExists(conn, oldTable, "client_id");
        boolean hasClientUuid = columnExists(conn, oldTable, "client_uuid");
        boolean jobsHasId = columnExists(conn, "jobs", "id");
        boolean clientsHasId = columnExists(conn, "clients", "id");
        String jobFromId = jobsHasId
                ? "(SELECT j.uuid FROM jobs j WHERE j.id = old.job_id LIMIT 1)"
                : "(SELECT j.uuid FROM jobs j WHERE j.uuid = CAST(old.job_id AS TEXT) LIMIT 1)";
        String jobExpr;
        if (hasJobId) {
            jobExpr = hasJobUuid
                    ? "COALESCE(NULLIF(TRIM(old.job_uuid), ''), " + jobFromId + ")"
                    : jobFromId;
        } else if (hasJobUuid) {
            jobExpr = "old.job_uuid";
        } else {
            jobExpr = "NULL";
        }
        String clientFromId = clientsHasId
                ? "(SELECT c.uuid FROM clients c WHERE c.id = old.client_id LIMIT 1)"
                : "(SELECT c.uuid FROM clients c WHERE c.uuid = CAST(old.client_id AS TEXT) LIMIT 1)";
        String clientExpr;
        if (hasClientId) {
            clientExpr = hasClientUuid
                    ? "COALESCE(NULLIF(TRIM(old.client_uuid), ''), " + clientFromId + ")"
                    : clientFromId;
        } else if (hasClientUuid) {
            clientExpr = "old.client_uuid";
        } else {
            clientExpr = "NULL";
        }
        String billingCols = String.join(",\n                    ",
                sqlCoalesceNewUuid("old", "uuid", columnExists(conn, oldTable, "uuid")),
                jobExpr,
                clientExpr,
                oldCol(conn, oldTable, "amount", "NULL"),
                oldCol(conn, oldTable, "bill_date", "NULL"),
                oldColCoalesce(conn, oldTable, "sync_status", "'PENDING'"),
                oldColCoalesce(conn, oldTable, "sync_version", "1"),
                oldColCoalesce(conn, oldTable, "is_deleted", "0"),
                oldColCoalesce(conn, oldTable, "is_active", "1"),
                oldColCoalesce(conn, oldTable, "created_at", "datetime('now')"),
                oldColCoalesce(conn, oldTable, "updated_at", "datetime('now')"),
                oldCol(conn, oldTable, "synced_at", "NULL"),
                oldCol(conn, oldTable, "deleted_at", "NULL"));
        stmt.execute("""
                INSERT INTO billing (
                    uuid, job_uuid, client_uuid, amount, bill_date,
                    sync_status, sync_version, is_deleted, is_active, created_at, updated_at, synced_at, deleted_at
                )
                SELECT
                    """
                + billingCols
                + """
                
                FROM billing_old old
                """);
        stmt.execute("DROP TABLE billing_old");
        stmt.execute("PRAGMA foreign_keys=ON");
    }

    private static void migrateSuppliersToCanonicalIfNeeded(Connection conn, Statement stmt) throws Exception {
        if (!columnExists(conn, "suppliers", "id")) {
            return;
        }
        if (!columnExists(conn, "suppliers", "uuid")) {
            ensureSuppliersUuid(conn, stmt);
            return;
        }
        System.out.println("Migration: suppliers → uuid PK (strip integer id)");
        stmt.execute("PRAGMA foreign_keys=OFF");
        stmt.execute("ALTER TABLE suppliers RENAME TO suppliers_old");
        stmt.execute("""
                CREATE TABLE suppliers (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    supplier_code TEXT DEFAULT '',
                    name TEXT NOT NULL DEFAULT '',
                    business_name TEXT DEFAULT '',
                    type TEXT NOT NULL DEFAULT '',
                    phone TEXT DEFAULT '',
                    address TEXT DEFAULT '',
                    gst_number TEXT DEFAULT '',
                    created_by_user_uuid TEXT DEFAULT NULL,
                    updated_by_user_uuid TEXT DEFAULT NULL,
                    sync_status TEXT DEFAULT 'PENDING',
                    sync_version INTEGER DEFAULT 1,
                    is_deleted INTEGER DEFAULT 0,
                    is_active INTEGER DEFAULT 1,
                    created_at TEXT DEFAULT (datetime('now')),
                    updated_at TEXT DEFAULT (datetime('now')),
                    synced_at TEXT DEFAULT NULL,
                    deleted_at TEXT DEFAULT NULL
                )
                """);
        stmt.execute("""
                INSERT INTO suppliers (
                    uuid, supplier_code, name, business_name, type, phone, address, gst_number,
                    created_by_user_uuid, updated_by_user_uuid,
                    sync_status, sync_version, is_deleted, is_active, created_at, updated_at, synced_at, deleted_at
                )
                SELECT
                    COALESCE(NULLIF(TRIM(old.uuid), ''), lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))),
                    COALESCE(old.supplier_code, ''), name, business_name, type, phone, address, gst_number,
                    NULL, NULL,
                    COALESCE(sync_status, 'PENDING'), COALESCE(sync_version, 1),
                    COALESCE(is_deleted, 0), COALESCE(is_active, 1),
                    COALESCE(created_at, datetime('now')), COALESCE(updated_at, datetime('now')),
                    synced_at, deleted_at
                FROM suppliers_old old
                """);
        stmt.execute("DROP TABLE suppliers_old");
        stmt.execute("PRAGMA foreign_keys=ON");
    }

    private static void assertNoLegacyIntegerIdColumns(Connection conn) throws Exception {
        String[] tables = {
                "clients", "jobs", "job_items", "invoice_master", "invoice_adjustments", "invoice_job_mapping",
                "payments", "payment_allocations", "payment_details", "billing", "suppliers",
                "printing_items", "paper_items", "binding_items", "lamination_items", "ctp_items"
        };
        for (String table : tables) {
            if (tableExists(conn, table) && columnExists(conn, table, "id")) {
                System.err.println("WARNING: table " + table + " still has integer column 'id' — run DB repair or delete database/sunnyprinters.db for clean bootstrap");
            }
        }
    }
}
