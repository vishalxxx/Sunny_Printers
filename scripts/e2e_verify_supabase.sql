-- =============================================================================
-- E2E verification on Supabase (PostgreSQL)
-- Replace placeholders with UUIDs printed by: mvn -q compile exec:java -Dexec.mainClass=scratch.E2eFullFlowSeed
-- Or filter by marker codes:
-- =============================================================================

-- By marker (if you used default E2eFullFlowSeed codes)
SELECT 'clients' AS entity, uuid, client_code, client_name, sync_status, synced_at
FROM clients WHERE client_code LIKE 'E2E-FLOW%';

SELECT 'jobs' AS entity, j.uuid, j.job_code, j.amount,
       (SELECT COALESCE(SUM(ji.amount), 0) FROM job_items ji WHERE ji.job_uuid = j.uuid) AS items_sum,
       j.invoice_uuid, j.status, j.sync_status, j.synced_at
FROM jobs j WHERE j.job_code LIKE 'E2E-FLOW%';

SELECT 'job_items' AS entity, ji.uuid, ji.job_uuid, ji.type, ji.amount, ji.sync_status, ji.synced_at
FROM job_items ji
JOIN jobs j ON j.uuid = ji.job_uuid
WHERE j.job_code LIKE 'E2E-FLOW%';

SELECT 'invoice_master' AS entity, uuid, invoice_no, amount, paid_amount, due_amount,
       payment_status, status, sync_status, synced_at
FROM invoice_master WHERE invoice_no LIKE 'E2E-FLOW%';

-- Junction table only stores UUIDs; use this for human-readable "details":
SELECT 'invoice_job_mapping' AS entity, m.uuid, m.invoice_uuid, im.invoice_no,
       m.job_uuid, j.job_code, j.job_title, j.amount AS job_amount, m.sync_status, m.synced_at
FROM invoice_job_mapping m
JOIN invoice_master im ON im.uuid = m.invoice_uuid
LEFT JOIN jobs j ON j.uuid = m.job_uuid
WHERE im.invoice_no LIKE 'E2E-FLOW%';

-- Payments: link via E2E invoice (client_code may be renumbered to SP/CL/... after save)
SELECT 'payments' AS entity, p.uuid, p.type, p.amount, p.method, p.sync_status, p.synced_at, c.client_code
FROM payments p
JOIN payment_allocations pa ON pa.payment_uuid = p.uuid
JOIN invoice_master im ON im.uuid = pa.invoice_uuid
LEFT JOIN clients c ON c.uuid = p.client_uuid
WHERE im.invoice_no LIKE 'E2E-FLOW%'
ORDER BY p.type, p.created_at;

SELECT 'payment_allocations' AS entity, pa.*
FROM payment_allocations pa
JOIN invoice_master im ON im.uuid = pa.invoice_uuid
WHERE im.invoice_no LIKE 'E2E-FLOW%';

SELECT 'payment_details' AS entity, pd.*, c.client_code
FROM payment_details pd
JOIN payments p ON p.uuid = pd.payment_uuid
JOIN payment_allocations pa ON pa.payment_uuid = p.uuid
JOIN invoice_master im ON im.uuid = pa.invoice_uuid
LEFT JOIN clients c ON c.uuid = p.client_uuid
WHERE im.invoice_no LIKE 'E2E-FLOW%';

SELECT 'invoice_adjustments' AS entity, ia.uuid, ia.type, ia.note_no, ia.amount, ia.sync_status, ia.synced_at
FROM invoice_adjustments ia
JOIN invoice_master im ON im.uuid = ia.invoice_uuid
WHERE im.invoice_no LIKE 'E2E-FLOW%';

-- =============================================================================
-- Single-row checks (paste UUIDs from Java output)
-- =============================================================================
-- SELECT * FROM clients WHERE uuid = '<client_uuid>';
-- SELECT * FROM jobs WHERE uuid = '<job_uuid>';
-- SELECT * FROM job_items WHERE job_uuid = '<job_uuid>';
-- SELECT * FROM invoice_master WHERE uuid = '<invoice_uuid>';
-- SELECT * FROM payments WHERE client_uuid = '<client_uuid>';
-- SELECT * FROM payment_allocations WHERE invoice_uuid = '<invoice_uuid>';
-- SELECT * FROM invoice_adjustments WHERE invoice_uuid = '<invoice_uuid>';

-- =============================================================================
-- Integrity: job amount should match sum of job_items (Supabase)
-- =============================================================================
SELECT j.job_code,
       j.amount AS job_header_amount,
       COALESCE(SUM(ji.amount), 0) AS items_total,
       CASE WHEN ABS(j.amount - COALESCE(SUM(ji.amount), 0)) < 0.01 THEN 'OK' ELSE 'MISMATCH' END AS check_amount
FROM jobs j
LEFT JOIN job_items ji ON ji.job_uuid = j.uuid
WHERE j.job_code LIKE 'E2E-FLOW%'
GROUP BY j.uuid, j.job_code, j.amount;

-- =============================================================================
-- One-shot row counts (all sync_status columns qualified)
-- =============================================================================
SELECT 'clients' AS check_name, COUNT(*)::text AS cnt, MAX(c.sync_status) AS sync_status
FROM clients c
WHERE c.client_code LIKE 'E2E-FLOW%'
   OR c.uuid IN (SELECT im.client_uuid FROM invoice_master im WHERE im.invoice_no LIKE 'E2E-FLOW%')
UNION ALL
SELECT 'jobs', COUNT(*)::text, MAX(j.sync_status) FROM jobs j WHERE j.job_code LIKE 'E2E-FLOW%'
UNION ALL
SELECT 'job_items', COUNT(*)::text, MAX(ji.sync_status)
FROM job_items ji
JOIN jobs j ON j.uuid = ji.job_uuid
WHERE j.job_code LIKE 'E2E-FLOW%'
UNION ALL
SELECT 'invoices', COUNT(*)::text, MAX(im.sync_status)
FROM invoice_master im WHERE im.invoice_no LIKE 'E2E-FLOW%'
UNION ALL
SELECT 'payments', COUNT(*)::text, MAX(p.sync_status)
FROM payments p
WHERE p.uuid IN (
  SELECT pa.payment_uuid FROM payment_allocations pa
  JOIN invoice_master im ON im.uuid = pa.invoice_uuid
  WHERE im.invoice_no LIKE 'E2E-FLOW%'
)
UNION ALL
SELECT 'allocations', COUNT(*)::text, MAX(pa.sync_status)
FROM payment_allocations pa
JOIN invoice_master im ON im.uuid = pa.invoice_uuid
WHERE im.invoice_no LIKE 'E2E-FLOW%'
UNION ALL
SELECT 'payment_details', COUNT(*)::text, MAX(pd.sync_status)
FROM payment_details pd
WHERE pd.payment_uuid IN (
  SELECT pa.payment_uuid FROM payment_allocations pa
  JOIN invoice_master im ON im.uuid = pa.invoice_uuid
  WHERE im.invoice_no LIKE 'E2E-FLOW%'
)
UNION ALL
SELECT 'CN/DN', COUNT(*)::text, MAX(ia.sync_status)
FROM invoice_adjustments ia
JOIN invoice_master im ON im.uuid = ia.invoice_uuid
WHERE im.invoice_no LIKE 'E2E-FLOW%';
