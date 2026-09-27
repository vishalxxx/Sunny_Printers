-- Add extended information fields to public.suppliers
-- Run in Supabase SQL Editor

SET search_path TO public;

ALTER TABLE public.suppliers 
ADD COLUMN IF NOT EXISTS created_by_user_uuid TEXT DEFAULT NULL,
ADD COLUMN IF NOT EXISTS updated_by_user_uuid TEXT DEFAULT NULL,
ADD COLUMN IF NOT EXISTS mobile TEXT DEFAULT '',
ADD COLUMN IF NOT EXISTS email TEXT DEFAULT '',
ADD COLUMN IF NOT EXISTS website TEXT DEFAULT '',
ADD COLUMN IF NOT EXISTS state TEXT DEFAULT '',
ADD COLUMN IF NOT EXISTS city TEXT DEFAULT '',
ADD COLUMN IF NOT EXISTS pincode TEXT DEFAULT '',
ADD COLUMN IF NOT EXISTS payment_terms TEXT DEFAULT '',
ADD COLUMN IF NOT EXISTS credit_limit REAL DEFAULT 0,
ADD COLUMN IF NOT EXISTS notes TEXT DEFAULT '';

NOTIFY pgrst, 'reload schema';
