-- Usage (from the repo root), on a database Flyway has migrated:
--   docker exec -i hrm-database-1 psql -U hrm-user -d <db> -At -F '|' < api/docs/prompt/scripts/schema-export.sql > /tmp/hrm_schema.txt
-- One line per catalogue object, read by entity-vs-schema.mjs and doc-vs-schema.mjs:
--   C|table|column|data_type|char_length|numeric_precision|numeric_scale|is_nullable|has_default
--   K|table|constraint|type (c check, f foreign key, p primary, u unique, x exclusion)|definition
--   I|table|index|definition
--   G|table|trigger
-- Only the public schema's own tables; flyway_schema_history is left out.

SELECT 'C', c.table_name, c.column_name, c.data_type, coalesce(c.character_maximum_length::text, ''),
       coalesce(c.numeric_precision::text, ''), coalesce(c.numeric_scale::text, ''), c.is_nullable,
       (c.column_default IS NOT NULL)::text
FROM information_schema.columns c
JOIN information_schema.tables t ON t.table_schema = c.table_schema AND t.table_name = c.table_name
WHERE c.table_schema = 'public' AND t.table_type = 'BASE TABLE' AND c.table_name <> 'flyway_schema_history'
ORDER BY c.table_name, c.ordinal_position;

SELECT 'K', conrelid::regclass::text, conname, contype, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE connamespace = 'public'::regnamespace AND conrelid <> 0
  AND conrelid::regclass::text <> 'flyway_schema_history'
ORDER BY 2, 3;

SELECT 'I', tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
ORDER BY 2, 3;

SELECT DISTINCT 'G', event_object_table, trigger_name
FROM information_schema.triggers
WHERE trigger_schema = 'public'
ORDER BY 2, 3;
