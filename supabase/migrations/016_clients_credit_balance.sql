-- Add credit_limit and opening_balance columns to clients table
SET search_path TO public;

ALTER TABLE public.clients ADD COLUMN IF NOT EXISTS credit_limit double precision DEFAULT 0;
ALTER TABLE public.clients ADD COLUMN IF NOT EXISTS opening_balance double precision DEFAULT 0;
