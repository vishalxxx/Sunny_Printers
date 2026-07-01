-- =============================================================================
-- E2E verification on local SQLite: database/sunnyprinters.db
-- sqlite3 database/sunnyprinters.db < scripts/e2e_verify_sqlite.sql
-- =============================================================================

.headers on
.mode column

SELECT '--- clients (E2E-FLOW) ---' AS section;
SELECT uuid, client_code, client_name, sync_status, synced_at
FROM clients WHERE client_code LIKE 'E2E-FLOW%';

SELECT '--- jobs ---' AS section;
SELECT j.uuid, j.job_code, j.amount,
       (SELECT COALESCE(SUM(ji.amount), 0) FROM job_items ji
        WHERE ji.job_uuid = j.uuid AND COALESCE(ji.is_deleted, 0) = 0) AS items_sum,
       j.invoice_uuid, j.status, j.sync_status
FROM jobs j WHERE j.job_code LIKE 'E2E-FLOW%';

SELECT '--- job_items ---' AS section;
SELECT ji.uuid, ji.job_uuid, ji.type, ji.amount, ji.sync_status
FROM job_items ji
JOIN jobs j ON j.uuid = ji.job_uuid
WHERE j.job_code LIKE 'E2E-FLOW%';

SELECT '--- invoice_master ---' AS section;
SELECT uuid, invoice_no, amount, paid_amount, due_amount, payment_status, status, sync_status
FROM invoice_master WHERE invoice_no LIKE 'E2E-FLOW%';

SELECT '--- invoice_job_mapping ---' AS section;
SELECT m.*
FROM invoice_job_mapping m
JOIN invoice_master im ON im.uuid = m.invoice_uuid
WHERE im.invoice_no LIKE 'E2E-FLOW%';

SELECT '--- payments ---' AS section;
SELECT p.uuid, p.type, p.amount, p.method, p.sync_status
FROM payments p
JOIN clients c ON c.uuid = p.client_uuid
WHERE c.client_code LIKE 'E2E-FLOW%'
ORDER BY p.type;

SELECT '--- payment_allocations ---' AS section;
SELECT pa.*
FROM payment_allocations pa
JOIN invoice_master im ON im.uuid = pa.invoice_uuid
WHERE im.invoice_no LIKE 'E2E-FLOW%';

SELECT '--- payment_details ---' AS section;
SELECT pd.*
FROM payment_details pd
JOIN payments p ON p.uuid = pd.payment_uuid
JOIN clients c ON c.uuid = p.client_uuid
WHERE c.client_code LIKE 'E2E-FLOW%';

SELECT '--- invoice_adjustments (CN/DN) ---' AS section;
SELECT ia.uuid, ia.type, ia.note_no, ia.amount, ia.sync_status
FROM invoice_adjustments ia
JOIN invoice_master im ON im.uuid = ia.invoice_uuid
WHERE im.invoice_no LIKE 'E2E-FLOW%';

SELECT '--- amount integrity ---' AS section;
SELECT j.job_code, j.amount AS job_amount,
       (SELECT COALESCE(SUM(amount), 0) FROM job_items WHERE job_uuid = j.uuid) AS items_sum,
       CASE WHEN ABS(j.amount - (SELECT COALESCE(SUM(amount), 0) FROM job_items WHERE job_uuid = j.uuid)) < 0.01
            THEN 'OK' ELSE 'MISMATCH' END AS ok
FROM jobs j WHERE j.job_code LIKE 'E2E-FLOW%';

SELECT '--- still pending sync ---' AS section;
SELECT 'clients' AS tbl, COUNT(*) AS pending FROM clients WHERE client_code LIKE 'E2E-FLOW%' AND UPPER(TRIM(sync_status)) = 'PENDING'
UNION ALL SELECT 'jobs', COUNT(*) FROM jobs WHERE job_code LIKE 'E2E-FLOW%' AND UPPER(TRIM(sync_status)) = 'PENDING'
UNION ALL SELECT 'invoice_master', COUNT(*) FROM invoice_master WHERE invoice_no LIKE 'E2E-FLOW%' AND UPPER(TRIM(sync_status)) = 'PENDING'
UNION ALL SELECT 'payments', COUNT(*) FROM payments p JOIN clients c ON c.uuid = p.client_uuid WHERE c.client_code LIKE 'E2E-FLOW%' AND UPPER(TRIM(p.sync_status)) = 'PENDING';
