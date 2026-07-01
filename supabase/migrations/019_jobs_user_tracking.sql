-- Add user tracking fields to public.jobs
-- Run in Supabase SQL Editor

SET search_path TO public;

ALTER TABLE public.jobs 
ADD COLUMN IF NOT EXISTS created_by_user_uuid TEXT DEFAULT NULL,
ADD COLUMN IF NOT EXISTS updated_by_user_uuid TEXT DEFAULT NULL;
