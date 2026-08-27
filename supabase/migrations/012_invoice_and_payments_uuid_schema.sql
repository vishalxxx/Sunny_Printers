-- Invoice + payment tables (UUID PK, mirrors local SQLite / UniversalSyncEngine)
-- Prerequisites: public.clients(uuid), public.jobs(uuid), public.number_sequences
-- Run in Supabase SQL Editor after 008_clients_uuid_v7_primary_key.sql and 009_jobs_uuid_v7_primary_key.sql

SET search_path TO public;

-- ---------------------------------------------------------------------------
-- invoice_master
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.invoice_master (
    uuid TEXT PRIMARY KEY NOT NULL,
    invoice_no TEXT,
    client_uuid TEXT REFERENCES public.clients(uuid) ON DELETE SET NULL,
    client_name TEXT DEFAULT '',
    invoice_date TIMESTAMPTZ,
    period_from TIMESTAMPTZ,
    period_to TIMESTAMPTZ,
    amount DOUBLE PRECISION NOT NULL DEFAULT 0,
    paid_amount DOUBLE PRECISION NOT NULL DEFAULT 0,
    due_amount DOUBLE PRECISION NOT NULL DEFAULT 0,
    payment_status TEXT DEFAULT 'UNPAID',
    last_payment_date TIMESTAMPTZ,
    type TEXT DEFAULT '',
    status TEXT DEFAULT 'DRAFT',
    is_void INTEGER NOT NULL DEFAULT 0,
    void_reason TEXT DEFAULT '',
    void_date TIMESTAMPTZ,
    replaced_by_invoice_uuid TEXT REFERENCES public.invoice_master(uuid) ON DELETE SET NULL,
    parent_invoice_uuid TEXT REFERENCES public.invoice_master(uuid) ON DELETE SET NULL,
    status_updated_by TEXT DEFAULT '',
    file_path TEXT DEFAULT '',
    document_series TEXT DEFAULT 'GST_INVOICE',
    sync_status TEXT NOT NULL DEFAULT 'PENDING',
    sync_version INTEGER NOT NULL DEFAULT 1,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    synced_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_invoice_master_invoice_no
    ON public.invoice_master (invoice_no)
    WHERE invoice_no IS NOT NULL AND is_deleted = 0;

CREATE INDEX IF NOT EXISTS idx_invoice_master_client_uuid
    ON public.invoice_master (client_uuid);

CREATE INDEX IF NOT EXISTS idx_invoice_master_sync_status
    ON public.invoice_master (sync_status);

CREATE INDEX IF NOT EXISTS idx_invoice_master_status
    ON public.invoice_master (status);

-- ---------------------------------------------------------------------------
-- invoice_adjustments (credit / debit notes)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.invoice_adjustments (
    uuid TEXT PRIMARY KEY NOT NULL,
    invoice_uuid TEXT NOT NULL REFERENCES public.invoice_master(uuid) ON DELETE CASCADE,
    type TEXT NOT NULL,
    note_no TEXT NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    reason TEXT DEFAULT '',
    date DATE DEFAULT (CURRENT_DATE),
    sync_status TEXT NOT NULL DEFAULT 'PENDING',
    sync_version INTEGER NOT NULL DEFAULT 1,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    synced_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT invoice_adjustments_type_chk
        CHECK (type IN ('Credit Note', 'Debit Note')),
    CONSTRAINT invoice_adjustments_note_no_uk UNIQUE (note_no)
);

CREATE INDEX IF NOT EXISTS idx_invoice_adjustments_invoice_uuid
    ON public.invoice_adjustments (invoice_uuid);

-- ---------------------------------------------------------------------------
-- invoice_job_mapping (many jobs per invoice)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.invoice_job_mapping (
    uuid TEXT PRIMARY KEY NOT NULL,
    invoice_uuid TEXT NOT NULL REFERENCES public.invoice_master(uuid) ON DELETE CASCADE,
    job_uuid TEXT NOT NULL REFERENCES public.jobs(uuid) ON DELETE CASCADE,
    sync_status TEXT NOT NULL DEFAULT 'PENDING',
    sync_version INTEGER NOT NULL DEFAULT 1,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    synced_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT invoice_job_mapping_uk UNIQUE (invoice_uuid, job_uuid)
);

CREATE INDEX IF NOT EXISTS idx_invoice_job_mapping_job_uuid
    ON public.invoice_job_mapping (job_uuid);

-- Ensure jobs.invoice_uuid FK points at invoice_master (optional; 009 may already define column)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'jobs_invoice_uuid_fkey'
          AND table_name = 'jobs'
    ) THEN
        ALTER TABLE public.jobs
            ADD CONSTRAINT jobs_invoice_uuid_fkey
            FOREIGN KEY (invoice_uuid) REFERENCES public.invoice_master(uuid) ON DELETE SET NULL;
    END IF;
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

-- ---------------------------------------------------------------------------
-- payments
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.payments (
    uuid TEXT PRIMARY KEY NOT NULL,
    client_uuid TEXT REFERENCES public.clients(uuid) ON DELETE SET NULL,
    amount DOUBLE PRECISION NOT NULL DEFAULT 0,
    payment_date DATE,
    method TEXT DEFAULT '',
    type TEXT NOT NULL DEFAULT 'Payment',
    sync_status TEXT NOT NULL DEFAULT 'PENDING',
    sync_version INTEGER NOT NULL DEFAULT 1,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    synced_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_payments_client_uuid
    ON public.payments (client_uuid);

CREATE INDEX IF NOT EXISTS idx_payments_sync_status
    ON public.payments (sync_status);

-- ---------------------------------------------------------------------------
-- payment_allocations (payment applied to invoice)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.payment_allocations (
    uuid TEXT PRIMARY KEY NOT NULL,
    payment_uuid TEXT NOT NULL REFERENCES public.payments(uuid) ON DELETE CASCADE,
    invoice_uuid TEXT NOT NULL REFERENCES public.invoice_master(uuid) ON DELETE CASCADE,
    allocated_amount DOUBLE PRECISION NOT NULL,
    sync_status TEXT NOT NULL DEFAULT 'PENDING',
    sync_version INTEGER NOT NULL DEFAULT 1,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    synced_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_payment_allocations_payment_uuid
    ON public.payment_allocations (payment_uuid);

CREATE INDEX IF NOT EXISTS idx_payment_allocations_invoice_uuid
    ON public.payment_allocations (invoice_uuid);

-- ---------------------------------------------------------------------------
-- payment_details (extra key/value fields per payment)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.payment_details (
    uuid TEXT PRIMARY KEY NOT NULL,
    payment_uuid TEXT NOT NULL REFERENCES public.payments(uuid) ON DELETE CASCADE,
    field_key TEXT NOT NULL,
    field_value TEXT DEFAULT '',
    sync_status TEXT NOT NULL DEFAULT 'PENDING',
    sync_version INTEGER NOT NULL DEFAULT 1,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    synced_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT payment_details_payment_field_uk UNIQUE (payment_uuid, field_key)
);

-- ---------------------------------------------------------------------------
-- document_number_mappings (TEMP-* -> permanent number audit; optional on Supabase)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.document_number_mappings (
    uuid TEXT PRIMARY KEY NOT NULL,
    entity_type TEXT NOT NULL,
    entity_uuid TEXT NOT NULL,
    sequence_key TEXT NOT NULL,
    temporary_number TEXT NOT NULL,
    permanent_number TEXT NOT NULL,
    allocation_source TEXT NOT NULL DEFAULT 'remote',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT document_number_mappings_temp_uk UNIQUE (temporary_number),
    CONSTRAINT document_number_mappings_entity_uk UNIQUE (entity_type, entity_uuid)
);

CREATE INDEX IF NOT EXISTS idx_document_number_mappings_permanent
    ON public.document_number_mappings (permanent_number);

-- ---------------------------------------------------------------------------
-- PostgREST: allow anon/authenticated upsert (adjust to your RLS policy)
-- ---------------------------------------------------------------------------
-- ALTER TABLE public.invoice_master ENABLE ROW LEVEL SECURITY;
-- CREATE POLICY "invoice_master_all" ON public.invoice_master FOR ALL USING (true) WITH CHECK (true);

NOTIFY pgrst, 'reload schema';
