-- Rebuild public.suppliers to match canonical SQLite schema with UUID PK
-- Run in Supabase SQL Editor to support supplier synchronization.

SET search_path TO public;

DROP TABLE IF EXISTS public.suppliers CASCADE;

CREATE TABLE public.suppliers (
    uuid TEXT PRIMARY KEY NOT NULL,
    supplier_code TEXT DEFAULT '',
    name TEXT NOT NULL DEFAULT '',
    business_name TEXT DEFAULT '',
    type TEXT NOT NULL DEFAULT '',
    phone TEXT DEFAULT '',
    address TEXT DEFAULT '',
    gst_number TEXT DEFAULT '',
    sync_status TEXT NOT NULL DEFAULT 'PENDING',
    sync_version INTEGER NOT NULL DEFAULT 1,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    synced_at TIMESTAMPTZ DEFAULT NULL,
    deleted_at TIMESTAMPTZ DEFAULT NULL
);

CREATE INDEX IF NOT EXISTS idx_suppliers_sync_status ON public.suppliers(sync_status);
CREATE INDEX IF NOT EXISTS idx_suppliers_type ON public.suppliers(type);

NOTIFY pgrst, 'reload schema';
