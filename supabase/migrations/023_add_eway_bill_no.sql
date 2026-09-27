-- Add eway_bill_no column to invoice_master
ALTER TABLE public.invoice_master ADD COLUMN eway_bill_no TEXT DEFAULT '';

NOTIFY pgrst, 'reload schema';
