-- Add place_of_supply, payment_terms, due_date, vehicle_dispatch, po_no, po_date, dispatch_through, lr_tracking_no, and remarks columns to invoice_master
ALTER TABLE public.invoice_master ADD COLUMN place_of_supply TEXT DEFAULT '';
ALTER TABLE public.invoice_master ADD COLUMN payment_terms TEXT DEFAULT '';
ALTER TABLE public.invoice_master ADD COLUMN due_date TEXT DEFAULT NULL;
ALTER TABLE public.invoice_master ADD COLUMN vehicle_dispatch TEXT DEFAULT '';
ALTER TABLE public.invoice_master ADD COLUMN po_no TEXT DEFAULT '';
ALTER TABLE public.invoice_master ADD COLUMN po_date TEXT DEFAULT NULL;
ALTER TABLE public.invoice_master ADD COLUMN dispatch_through TEXT DEFAULT '';
ALTER TABLE public.invoice_master ADD COLUMN lr_tracking_no TEXT DEFAULT '';
ALTER TABLE public.invoice_master ADD COLUMN remarks TEXT DEFAULT '';

NOTIFY pgrst, 'reload schema';
