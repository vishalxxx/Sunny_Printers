-- Add state column to clients table
ALTER TABLE public.clients ADD COLUMN state TEXT DEFAULT '';
