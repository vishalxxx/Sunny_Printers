-- Add total_after_tax and round_off columns to invoice_master
ALTER TABLE public.invoice_master ADD COLUMN total_after_tax DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE public.invoice_master ADD COLUMN round_off DOUBLE PRECISION NOT NULL DEFAULT 0;

NOTIFY pgrst, 'reload schema';
