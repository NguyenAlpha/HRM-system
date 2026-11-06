-- Usage (from the repo root), on a database Flyway has migrated and the API has seeded:
--   docker exec -i hrm-database-1 psql -U hrm-user -d <db> -q < api/docs/prompt/scripts/schema-checks.sql
-- Sections marked "must be 0 rows" are invariants; the others list objects to judge one by one.

\echo '1. Single-column foreign keys with no index starting with that column (audit *_by_account_id columns counted below)'
SELECT c.conrelid::regclass AS "table", a.attname AS "column", c.confrelid::regclass AS "references"
FROM pg_constraint c
JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
WHERE c.contype = 'f' AND c.connamespace = 'public'::regnamespace AND array_length(c.conkey, 1) = 1
  AND NOT EXISTS (SELECT 1 FROM pg_index i WHERE i.indrelid = c.conrelid AND i.indkey[0] = c.conkey[1])
  AND a.attname NOT LIKE '%\_by\_account\_id'
ORDER BY 1, 2;
SELECT count(*) AS "unindexed audit *_by_account_id foreign keys"
FROM pg_constraint c
JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
WHERE c.contype = 'f' AND c.connamespace = 'public'::regnamespace AND array_length(c.conkey, 1) = 1
  AND NOT EXISTS (SELECT 1 FROM pg_index i WHERE i.indrelid = c.conrelid AND i.indkey[0] = c.conkey[1])
  AND a.attname LIKE '%\_by\_account\_id';

\echo '2. Unique constraints and indexes on soft-deleted tables (deleted_at) that do not leave deleted rows out'
SELECT i.indrelid::regclass AS "table", ic.relname AS "index", pg_get_indexdef(i.indexrelid) AS definition
FROM pg_index i
JOIN pg_class ic ON ic.oid = i.indexrelid
WHERE i.indisunique AND NOT i.indisprimary
  AND EXISTS (SELECT 1 FROM pg_attribute a WHERE a.attrelid = i.indrelid AND a.attname = 'deleted_at' AND NOT a.attisdropped)
  AND coalesce(pg_get_expr(i.indpred, i.indrelid), '') NOT LIKE '%deleted_at IS NULL%'
ORDER BY 1, 2;

\echo '3. Tables with effective_from: period CHECK and EXCLUDE present? (no means the rule lives in code only)'
SELECT t.relname AS "table",
       EXISTS (SELECT 1 FROM pg_constraint k WHERE k.conrelid = t.oid AND k.contype = 'c'
               AND pg_get_constraintdef(k.oid) LIKE '%effective_to >= effective_from%') AS period_check,
       EXISTS (SELECT 1 FROM pg_constraint k WHERE k.conrelid = t.oid AND k.contype = 'x') AS exclusion
FROM pg_class t
WHERE t.relnamespace = 'public'::regnamespace AND t.relkind = 'r'
  AND EXISTS (SELECT 1 FROM pg_attribute a WHERE a.attrelid = t.oid AND a.attname = 'effective_from')
ORDER BY 2, 3, 1;

\echo '4. Columns that break the type rules of DATABASE_SCHEMA.md §1 (must be 0 rows; a name-based guess, judge each)'
SELECT table_name AS "table", column_name AS "column", data_type, numeric_precision, numeric_scale
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history' AND (
       data_type IN ('timestamp without time zone', 'double precision', 'real', 'money')
    OR (data_type = 'numeric' AND numeric_precision IS NULL)
    OR (column_name LIKE '%\_at' AND data_type <> 'timestamp with time zone')
    OR (column_name LIKE '%\_date' AND data_type <> 'date')
    OR (column_name LIKE '%\_minutes' AND data_type <> 'integer'))
ORDER BY 1, 2;

\echo '5. Numeric columns other than money NUMERIC(15,2), whose count comes first (rates, multipliers, quantities)'
SELECT count(*) AS "NUMERIC(15,2) columns" FROM information_schema.columns
WHERE table_schema = 'public' AND data_type = 'numeric' AND numeric_precision = 15 AND numeric_scale = 2;
SELECT table_name AS "table", column_name AS "column", numeric_precision, numeric_scale
FROM information_schema.columns
WHERE table_schema = 'public' AND data_type = 'numeric' AND NOT (numeric_precision = 15 AND numeric_scale = 2)
ORDER BY 1, 2;

\echo '6. Tables other tables reference, with the ON DELETE action of those foreign keys (a = NO ACTION, c = CASCADE)'
SELECT c.confrelid::regclass AS "referenced table", c.confdeltype AS on_delete, count(*) AS foreign_keys,
       string_agg(DISTINCT c.conrelid::regclass::text, ', ') AS from_tables
FROM pg_constraint c
WHERE c.contype = 'f' AND c.connamespace = 'public'::regnamespace
GROUP BY 1, 2 ORDER BY 1, 2;

\echo '7. Status columns and the CHECKs tying the status to other columns (none means no state <-> column rule in the DB)'
SELECT t.relname AS "table",
       string_agg(k.conname, ', ' ORDER BY k.conname) FILTER (
         WHERE pg_get_constraintdef(k.oid) ~ '\(status\)' AND pg_get_constraintdef(k.oid) ~ '_(at|by_account_id|id)\M') AS state_checks
FROM pg_class t
JOIN pg_attribute a ON a.attrelid = t.oid AND a.attname = 'status'
LEFT JOIN pg_constraint k ON k.conrelid = t.oid AND k.contype = 'c'
WHERE t.relnamespace = 'public'::regnamespace AND t.relkind = 'r'
GROUP BY 1 ORDER BY 2 NULLS FIRST, 1;

\echo '8. Rows whose status disagrees with the columns that go with it (must be 0 rows; tells whether a CHECK is safe to add)'
SELECT 'leave_requests' AS "table", id, status FROM leave_requests
WHERE (status = 'PENDING' AND (submitted_at IS NULL OR approver_employee_id IS NULL))
   OR (status IN ('APPROVED', 'REJECTED') AND reviewed_at IS NULL)
   OR (status = 'DRAFT' AND (submitted_at IS NOT NULL OR reviewed_at IS NOT NULL))
UNION ALL
SELECT 'resignation_requests', id, status FROM resignation_requests
WHERE (status IN ('APPROVED', 'REJECTED') AND (reviewed_at IS NULL OR reviewed_by_account_id IS NULL))
   OR (status = 'PENDING' AND reviewed_at IS NOT NULL)
UNION ALL
SELECT 'payroll_periods', id, status FROM payroll_periods
WHERE (status IN ('CALCULATED', 'APPROVED', 'PAID', 'LOCKED')
       AND (calculated_at IS NULL OR calculated_by_account_id IS NULL OR tax_payment_date IS NULL))
   OR (status IN ('APPROVED', 'PAID', 'LOCKED') AND (approved_at IS NULL OR approved_by_account_id IS NULL))
   OR (status IN ('PAID', 'LOCKED') AND (paid_at IS NULL OR paid_by_account_id IS NULL))
   OR (status = 'LOCKED' AND (locked_at IS NULL OR locked_by_account_id IS NULL))
   OR (status = 'DRAFT' AND (approved_at IS NOT NULL OR paid_at IS NOT NULL OR locked_at IS NOT NULL))
UNION ALL
SELECT 'payslips', p.id, pp.status FROM payslips p JOIN payroll_periods pp ON pp.id = p.payroll_period_id
WHERE pp.status IN ('DRAFT', 'CANCELLED');

\echo '9. Payslips whose items do not add up to the totals (must be 0 rows; DATABASE_SCHEMA.md §12.2)'
SELECT p.id, p.base_salary_pay, sum(i.amount) FILTER (WHERE i.component_type = 'BASE_SALARY') AS base_items,
       p.allowance_pay, sum(i.amount) FILTER (WHERE i.component_type IN ('POSITION_ALLOWANCE', 'SENIORITY_ALLOWANCE')) AS allowance_items,
       p.overtime_pay, sum(i.amount) FILTER (WHERE i.component_type = 'OVERTIME') AS overtime_items,
       p.other_earnings_pay, sum(i.amount) FILTER (WHERE i.component_type IN ('BONUS', 'TAXABLE_ALLOWANCE', 'NON_TAXABLE_ALLOWANCE')) AS earning_items,
       p.other_deductions, sum(i.amount) FILTER (WHERE i.component_type = 'DEDUCTION') AS deduction_items
FROM payslips p LEFT JOIN payslip_items i ON i.payslip_id = p.id
GROUP BY p.id
HAVING p.base_salary_pay <> coalesce(sum(i.amount) FILTER (WHERE i.component_type = 'BASE_SALARY'), 0)
    OR p.allowance_pay <> coalesce(sum(i.amount) FILTER (WHERE i.component_type IN ('POSITION_ALLOWANCE', 'SENIORITY_ALLOWANCE')), 0)
    OR p.overtime_pay <> coalesce(sum(i.amount) FILTER (WHERE i.component_type = 'OVERTIME'), 0)
    OR p.other_earnings_pay <> coalesce(sum(i.amount) FILTER (WHERE i.component_type IN ('BONUS', 'TAXABLE_ALLOWANCE', 'NON_TAXABLE_ALLOWANCE')), 0)
    OR p.other_deductions <> coalesce(sum(i.amount) FILTER (WHERE i.component_type = 'DEDUCTION'), 0)
ORDER BY p.id LIMIT 20;

\echo '10. Days from 2020-01-01 to 2026-12-31 without a tax rule or an insurance rule (must be 0 rows; V8 promises full cover)'
SELECT d::date AS day, NOT EXISTS (SELECT 1 FROM payroll_tax_rules r WHERE d::date BETWEEN r.effective_from AND coalesce(r.effective_to, 'infinity')) AS no_tax_rule,
       NOT EXISTS (SELECT 1 FROM payroll_insurance_rules r WHERE d::date BETWEEN r.effective_from AND coalesce(r.effective_to, 'infinity')) AS no_insurance_rule
FROM generate_series('2020-01-01'::date, '2026-12-31'::date, '1 day') d
WHERE NOT EXISTS (SELECT 1 FROM payroll_tax_rules r WHERE d::date BETWEEN r.effective_from AND coalesce(r.effective_to, 'infinity'))
   OR NOT EXISTS (SELECT 1 FROM payroll_insurance_rules r WHERE d::date BETWEEN r.effective_from AND coalesce(r.effective_to, 'infinity'))
ORDER BY 1 LIMIT 10;

\echo '11. Tax brackets that leave a gap or overlap (must be 0 rows: first starts at 0, each starts where the last ended, only the last is open)'
SELECT tax_rule_id, lower_bound, upper_bound, previous_upper
FROM (SELECT b.*, lag(upper_bound) OVER w AS previous_upper, lead(lower_bound) OVER w AS next_lower,
             row_number() OVER w AS n
      FROM payroll_tax_brackets b WINDOW w AS (PARTITION BY tax_rule_id ORDER BY lower_bound)) x
WHERE (n = 1 AND lower_bound <> 0)
   OR (n > 1 AND lower_bound IS DISTINCT FROM previous_upper)
   OR (upper_bound IS NULL AND next_lower IS NOT NULL)
   OR (upper_bound IS NOT NULL AND next_lower IS NULL)
ORDER BY 1, 2;

\echo '12. Triggers (each one is behaviour the entities do not show; DATABASE_SCHEMA.md should name it)'
SELECT event_object_table AS "table", trigger_name, action_timing || ' ' || string_agg(event_manipulation, '/') AS fires
FROM information_schema.triggers WHERE trigger_schema = 'public'
GROUP BY 1, 2, action_timing ORDER BY 1, 2;
