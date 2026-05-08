-- Add some sample data for testing analytics
-- Corrected for actual schema

-- 1. Add Invoices
INSERT INTO invoice_master (invoice_no, client_id, client_name, invoice_date, amount, paid_amount, due_amount, payment_status, type, status, is_void)
VALUES ('INV-TEST-001', 1, 'Client One', '2026-04-10', 65000.0, 0.0, 65000.0, 'UNPAID', 'Business', 'FINAL', 0);

INSERT INTO invoice_master (invoice_no, client_id, client_name, invoice_date, amount, paid_amount, due_amount, payment_status, type, status, is_void)
VALUES ('INV-TEST-002', 2, 'Client Two', '2026-04-05', 12000.0, 12000.0, 0.0, 'PAID', 'Business', 'FINAL', 0);

INSERT INTO invoice_master (invoice_no, client_id, client_name, invoice_date, amount, paid_amount, due_amount, payment_status, type, status, is_void)
VALUES ('INV-TEST-003', 3, 'Client Three', '2026-03-01', 5000.0, 0.0, 5000.0, 'UNPAID', 'Business', 'FINAL', 0);

-- 2. Add Payments
INSERT INTO payments (client_id, payment_date, amount, method, type)
VALUES (2, '2026-04-12', 12000.0, 'UPI', 'Payment');

-- 3. Add CN/DN (Adjustments)
-- We need the auto-increment ID of the invoices added above. 
-- Assuming they are the latest ones or we can use subqueries.
INSERT INTO invoice_adjustments (invoice_id, type, note_no, amount, reason)
SELECT id, 'Debit Note', 'DN-T-001', 5000.0, 'Late fee' FROM invoice_master WHERE invoice_no = 'INV-TEST-001';

INSERT INTO invoice_adjustments (invoice_id, type, note_no, amount, reason)
SELECT id, 'Credit Note', 'CN-T-001', 1000.0, 'Discount' FROM invoice_master WHERE invoice_no = 'INV-TEST-003';

-- 4. Add Refund
INSERT INTO payments (client_id, payment_date, amount, method, type)
VALUES (1, '2026-04-14', -2000.0, 'Bank Transfer', 'Refund');
