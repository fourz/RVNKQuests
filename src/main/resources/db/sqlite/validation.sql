-- RVNKQuests Database Validation Script (SQLite)
-- Validates schema integrity and migration status
-- Target: SQLite 3+

-- ============================================================================
-- SECTION 1: Table Existence Check
-- ============================================================================

SELECT 'V1 Base Tables Check' AS validation_category;

SELECT
    name AS table_name,
    CASE WHEN type = 'table' THEN '✅ EXISTS' ELSE '❌ MISSING' END AS status
FROM sqlite_master
WHERE type = 'table' AND name = 'quest_progress';

SELECT
    name AS table_name,
    CASE WHEN type = 'table' THEN '✅ EXISTS' ELSE '❌ MISSING' END AS status
FROM sqlite_master
WHERE type = 'table' AND name = 'quest_objective_progress';

SELECT
    name AS table_name,
    CASE WHEN type = 'table' THEN '✅ EXISTS' ELSE '❌ MISSING' END AS status
FROM sqlite_master
WHERE type = 'table' AND name = 'quest_rewards_claimed';

SELECT
    name AS table_name,
    CASE WHEN type = 'table' THEN '✅ EXISTS' ELSE '❌ MISSING' END AS status
FROM sqlite_master
WHERE type = 'table' AND name = 'quest_journal_entries';

SELECT
    name AS table_name,
    CASE WHEN type = 'table' THEN '✅ EXISTS' ELSE '❌ MISSING' END AS status
FROM sqlite_master
WHERE type = 'table' AND name = 'quest_categories';

SELECT
    name AS table_name,
    CASE WHEN type = 'table' THEN '✅ EXISTS' ELSE '❌ MISSING' END AS status
FROM sqlite_master
WHERE type = 'table' AND name = 'quest_tags';

SELECT
    name AS table_name,
    CASE WHEN type = 'table' THEN '✅ EXISTS' ELSE '❌ MISSING' END AS status
FROM sqlite_master
WHERE type = 'table' AND name = 'quest_tag_assignments';

SELECT
    name AS table_name,
    CASE WHEN type = 'table' THEN '✅ EXISTS' ELSE '❌ MISSING' END AS status
FROM sqlite_master
WHERE type = 'table' AND name = 'quest_leaderboard_entries';

-- ============================================================================
-- SECTION 2: V2 Migration Check
-- ============================================================================

SELECT 'V2 Migration Check' AS validation_category;

SELECT
    'quest_leaderboard_cache' AS table_name,
    CASE
        WHEN EXISTS (SELECT 1 FROM sqlite_master WHERE type='table' AND name='quest_leaderboard_cache')
        THEN '✅ V2 APPLIED'
        ELSE '❌ V2 PENDING'
    END AS status;

-- ============================================================================
-- SECTION 3: Schema Structure Validation
-- ============================================================================

SELECT 'Schema Structure Validation' AS validation_category;

-- Verify quest_leaderboard_cache structure (if V2 applied)
PRAGMA table_info(quest_leaderboard_cache);

-- ============================================================================
-- SECTION 4: Index Validation
-- ============================================================================

SELECT 'Index Validation' AS validation_category;

-- List all indexes on quest tables
SELECT
    name AS index_name,
    tbl_name AS table_name,
    sql AS index_definition
FROM sqlite_master
WHERE type = 'index'
  AND tbl_name = 'quest_leaderboard_cache'
ORDER BY name;

-- ============================================================================
-- SECTION 5: Foreign Key Validation
-- ============================================================================

SELECT 'Foreign Key Validation' AS validation_category;

-- Enable foreign keys (required for SQLite)
PRAGMA foreign_keys;

-- Verify quest_tag_assignments foreign key
PRAGMA foreign_key_list(quest_tag_assignments);

-- ============================================================================
-- SECTION 6: Data Type Validation
-- ============================================================================

SELECT 'Data Type Validation' AS validation_category;

-- List all tables with their column types
SELECT
    m.name AS table_name,
    p.name AS column_name,
    p.type AS data_type,
    p.pk AS is_primary_key
FROM sqlite_master m
JOIN pragma_table_info(m.name) p
WHERE m.type = 'table'
  AND m.name LIKE 'quest_%'
ORDER BY m.name, p.cid;

-- ============================================================================
-- SECTION 7: All Quest Tables Summary
-- ============================================================================

SELECT 'All Quest Tables' AS validation_category;

SELECT
    name AS table_name,
    type AS object_type,
    CASE WHEN sql IS NOT NULL THEN '✅ DEFINED' ELSE '❌ NO DEFINITION' END AS status
FROM sqlite_master
WHERE type = 'table'
  AND name LIKE 'quest_%'
ORDER BY name;

-- ============================================================================
-- SECTION 8: Performance Test Queries
-- ============================================================================

SELECT 'Performance Test Queries' AS validation_category;

-- Test cache insert (explain query plan)
EXPLAIN QUERY PLAN
INSERT INTO quest_leaderboard_cache (leaderboard_type, cache_data, expires_at)
VALUES ('test_type', '{"entries": []}', datetime('now', '+5 minutes'));

-- Test cache retrieval
EXPLAIN QUERY PLAN
SELECT cache_data
FROM quest_leaderboard_cache
WHERE leaderboard_type = 'most_completed'
  AND expires_at > datetime('now');

-- Test index usage on leaderboard_type
EXPLAIN QUERY PLAN
SELECT *
FROM quest_leaderboard_cache
WHERE leaderboard_type = 'fastest_average';

-- Test index usage on expires_at
EXPLAIN QUERY PLAN
SELECT *
FROM quest_leaderboard_cache
WHERE expires_at < datetime('now');

-- ============================================================================
-- SECTION 9: Data Integrity Validation
-- ============================================================================

SELECT 'Data Integrity Check' AS validation_category;

-- Check for orphaned tag assignments
SELECT
    COUNT(*) AS orphaned_tag_assignments
FROM quest_tag_assignments qta
WHERE NOT EXISTS (
    SELECT 1 FROM quest_tags qt WHERE qt.id = qta.tag_id
);

-- Verify unique constraints (SQLite shows unique indexes)
SELECT
    name AS constraint_name,
    tbl_name AS table_name,
    sql AS definition
FROM sqlite_master
WHERE type = 'index'
  AND tbl_name LIKE 'quest_%'
  AND sql LIKE '%UNIQUE%'
ORDER BY tbl_name, name;

-- ============================================================================
-- SECTION 10: Migration Rollback Validation
-- ============================================================================

SELECT 'Rollback Validation' AS validation_category;

-- Test rollback script (DO NOT EXECUTE IN PRODUCTION)
-- This is a DRY RUN to verify rollback syntax
SELECT
    'DROP TABLE IF EXISTS ' || name || ';' AS rollback_sql
FROM sqlite_master
WHERE type = 'table'
  AND name = 'quest_leaderboard_cache';

-- ============================================================================
-- SECTION 11: Expected Row Counts
-- ============================================================================

SELECT 'Expected Row Counts' AS validation_category;

-- Verify table row counts are reasonable
SELECT
    'quest_progress' AS table_name,
    COUNT(*) AS row_count,
    'Should be >= 0' AS expected
FROM quest_progress;

SELECT
    'quest_leaderboard_cache' AS table_name,
    COUNT(*) AS row_count,
    'Should be <= 10 (max leaderboard types)' AS expected
FROM quest_leaderboard_cache
WHERE EXISTS (SELECT 1 FROM sqlite_master WHERE type='table' AND name='quest_leaderboard_cache');

SELECT
    'quest_categories' AS table_name,
    COUNT(*) AS row_count,
    'Should be <= 10 (predefined categories)' AS expected
FROM quest_categories;

-- ============================================================================
-- SECTION 12: Database Integrity Check
-- ============================================================================

SELECT 'Database Integrity Check' AS validation_category;

-- Run SQLite's built-in integrity check
PRAGMA integrity_check;

-- Check for database corruption
PRAGMA quick_check;

-- ============================================================================
-- SECTION 13: Summary Report
-- ============================================================================

SELECT 'Validation Summary' AS validation_category;

SELECT
    COUNT(*) AS total_tables
FROM sqlite_master
WHERE type = 'table'
  AND name LIKE 'quest_%';

SELECT
    'Migration Status' AS metric,
    CASE
        WHEN EXISTS (SELECT 1 FROM sqlite_master WHERE type='table' AND name='quest_leaderboard_cache')
        THEN '✅ V2 COMPLETE'
        ELSE '⚠️ V2 PENDING'
    END AS status;

-- List all quest tables
SELECT
    name AS table_name,
    'Table' AS object_type
FROM sqlite_master
WHERE type = 'table'
  AND name LIKE 'quest_%'
UNION ALL
SELECT
    name AS object_name,
    'Index (' || tbl_name || ')' AS object_type
FROM sqlite_master
WHERE type = 'index'
  AND tbl_name LIKE 'quest_%'
  AND name NOT LIKE 'sqlite_%'
ORDER BY object_type, table_name;

-- ============================================================================
-- SECTION 14: SQLite-Specific Checks
-- ============================================================================

SELECT 'SQLite-Specific Checks' AS validation_category;

-- Check database page size
PRAGMA page_size;

-- Check database encoding
PRAGMA encoding;

-- Check foreign key enforcement
PRAGMA foreign_keys;

-- Check auto_vacuum setting
PRAGMA auto_vacuum;

-- ============================================================================
-- END OF VALIDATION SCRIPT
-- ============================================================================

-- Usage Instructions:
--
-- 1. Connect to your RVNKQuests SQLite database
--    sqlite3 /path/to/database.db
-- 2. Execute this script:
--    .read validation.sql
-- 3. Review output for any ❌ MISSING or ⚠️ WARNING indicators
-- 4. Fix any issues before running the plugin
--
-- Expected Results:
-- - All V1 base tables should show ✅ EXISTS
-- - V2 migration should show ✅ V2 APPLIED (if migration run)
-- - All indexes should be present
-- - Foreign keys should be properly defined
-- - integrity_check should return 'ok'
-- - No orphaned records in tag assignments
--
-- Common Issues:
-- - If V2 shows ❌ V2 PENDING, run sqlite/V2__add_leaderboard_cache.sql
-- - If orphaned_tag_assignments > 0, clean up tag_assignments table
-- - If foreign_keys is 0, enable with: PRAGMA foreign_keys = ON;
-- - If integrity_check fails, database may be corrupted - restore from backup
--
-- SQLite-Specific Notes:
-- - SQLite stores JSON as TEXT (manual parsing required)
-- - SQLite timestamps are TEXT in ISO-8601 format
-- - SQLite booleans are INTEGER (0 = false, 1 = true)
-- - Foreign keys must be explicitly enabled
