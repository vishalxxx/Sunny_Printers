-- Migration 025: Add include_in_invoice column to job_items
ALTER TABLE public.job_items ADD COLUMN IF NOT EXISTS include_in_invoice integer DEFAULT 1;
