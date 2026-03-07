-- RVNKQuests Database Validation Script
-- Validates schema integrity and migration status
-- Target: MySQL 8+ and SQLite 3+

-- ============================================================================
-- SECTION 1: Table Existence Check
-- ============================================================================

-- Verify all V1 base tables exist
SELECT 'V1 Base Tables Check' AS validation_category;

SELECT
    'quest_progress' AS table_name,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'quest_progress'
        ) THEN '✅ EXISTS'
        ELSE '❌ MISSING'
    END AS status;

SELECT
    'quest_objective_progress' AS table_name,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'quest_objective_progress'
        ) THEN '✅ EXISTS'
        ELSE '❌ MISSING'
    END AS status;

SELECT
    'quest_rewards_claimed' AS table_name,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'quest_rewards_claimed'
        ) THEN '✅ EXISTS'
        ELSE '❌ MISSING'
    END AS status;

SELECT
    'quest_journal_entries' AS table_name,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'quest_journal_entries'
        ) THEN '✅ EXISTS'
        ELSE '❌ MISSING'
    END AS status;

SELECT
    'quest_categories' AS table_name,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'quest_categories'
        ) THEN '✅ EXISTS'
        ELSE '❌ MISSING'
    END AS status;

SELECT
    'quest_tags' AS table_name,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'quest_tags'
        ) THEN '✅ EXISTS'
        ELSE '❌ MISSING'
    END AS status;

SELECT
    'quest_tag_assignments' AS table_name,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'quest_tag_assignments'
        ) THEN '✅ EXISTS'
        ELSE '❌ MISSING'
    END AS status;

SELECT
    'quest_leaderboard_entries' AS table_name,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'quest_leaderboard_entries'
        ) THEN '✅ EXISTS'
        ELSE '❌ MISSING'
    END AS status;

-- ============================================================================
-- SECTION 2: V2 Migration Check
-- ============================================================================

SELECT 'V2 Migration Check' AS validation_category;

SELECT
    'quest_leaderboard_cache' AS table_name,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'quest_leaderboard_cache'
        ) THEN '✅ V2 APPLIED'
        ELSE '❌ V2 PENDING'
    END AS status;

-- ============================================================================
-- SECTION 3: Schema Structure Validation
-- ============================================================================

SELECT 'Schema Structure Validation' AS validation_category;

-- Verify quest_leaderboard_cache structure (if V2 applied)
SELECT
    COLUMN_NAME,
    COLUMN_TYPE,
    IS_NULLABLE,
    COLUMN_KEY,
    COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'quest_leaderboard_cache'
ORDER BY ORDINAL_POSITION;

-- ============================================================================
-- SECTION 4: Index Validation
-- ============================================================================

SELECT 'Index Validation' AS validation_category;

-- List all indexes on quest_leaderboard_cache
SELECT
    INDEX_NAME,
    COLUMN_NAME,
    SEQ_IN_INDEX,
    NON_UNIQUE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'quest_leaderboard_cache'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- ============================================================================
-- SECTION 5: Foreign Key Validation
-- ============================================================================

SELECT 'Foreign Key Validation' AS validation_category;

-- Verify quest_tag_assignments foreign key
SELECT
    CONSTRAINT_NAME,
    TABLE_NAME,
    REFERENCED_TABLE_NAME,
    UPDATE_RULE,
    DELETE_RULE
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('quest_tag_assignments')
ORDER BY TABLE_NAME;

-- ============================================================================
-- SECTION 6: Data Type Validation
-- ============================================================================

SELECT 'Data Type Validation' AS validation_category;

-- Verify JSON columns exist (MySQL only)
SELECT
    TABLE_NAME,
    COLUMN_NAME,
    DATA_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND DATA_TYPE = 'json'
ORDER BY TABLE_NAME, COLUMN_NAME;

-- ============================================================================
-- SECTION 7: Storage Engine Validation (MySQL only)
-- ============================================================================

SELECT 'Storage Engine Check' AS validation_category;

SELECT
    TABLE_NAME,
    ENGINE,
    ROW_FORMAT,
    TABLE_ROWS
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME LIKE 'quest_%'
ORDER BY TABLE_NAME;

-- ============================================================================
-- SECTION 8: Performance Test Queries
-- ============================================================================

SELECT 'Performance Test Queries' AS validation_category;

-- Test cache insert performance
EXPLAIN
INSERT INTO quest_leaderboard_cache (leaderboard_type, cache_data, expires_at)
VALUES ('test_type', '{"entries": []}', NOW() + INTERVAL 5 MINUTE);

-- Test cache retrieval performance
EXPLAIN
SELECT cache_data
FROM quest_leaderboard_cache
WHERE leaderboard_type = 'most_completed'
  AND expires_at > NOW();

-- Test index usage on leaderboard_type
EXPLAIN
SELECT *
FROM quest_leaderboard_cache
WHERE leaderboard_type = 'fastest_average';

-- Test index usage on expires_at
EXPLAIN
SELECT *
FROM quest_leaderboard_cache
WHERE expires_at < NOW();

-- ============================================================================
-- SECTION 9: Data Integrity Validation
-- ============================================================================

SELECT 'Data Integrity Check' AS validation_category;

-- Check for orphaned tag assignments (tags with no corresponding quest_tags entry)
SELECT
    COUNT(*) AS orphaned_tag_assignments
FROM quest_tag_assignments qta
WHERE NOT EXISTS (
    SELECT 1 FROM quest_tags qt WHERE qt.id = qta.tag_id
);

-- Verify unique constraints
SELECT
    TABLE_NAME,
    CONSTRAINT_NAME,
    CONSTRAINT_TYPE
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME LIKE 'quest_%'
  AND CONSTRAINT_TYPE = 'UNIQUE'
ORDER BY TABLE_NAME, CONSTRAINT_NAME;

-- ============================================================================
-- SECTION 10: Migration Rollback Validation
-- ============================================================================

SELECT 'Rollback Validation' AS validation_category;

-- Test rollback script (DO NOT EXECUTE IN PRODUCTION)
-- This is a DRY RUN to verify rollback syntax

SELECT
    CONCAT('DROP TABLE IF EXISTS ', TABLE_NAME, ';') AS rollback_sql
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'quest_leaderboard_cache';

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
FROM quest_leaderboard_cache;

SELECT
    'quest_categories' AS table_name,
    COUNT(*) AS row_count,
    'Should be <= 10 (predefined categories)' AS expected
FROM quest_categories;

-- ============================================================================
-- SECTION 12: Summary Report
-- ============================================================================

SELECT 'Validation Summary' AS validation_category;

SELECT
    COUNT(*) AS total_tables,
    SUM(CASE WHEN TABLE_NAME LIKE 'quest_%' THEN 1 ELSE 0 END) AS quest_tables,
    SUM(CASE WHEN ENGINE = 'InnoDB' THEN 1 ELSE 0 END) AS innodb_tables
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME LIKE 'quest_%';

SELECT
    'Migration Status' AS metric,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'quest_leaderboard_cache'
        ) THEN '✅ V2 COMPLETE'
        ELSE '⚠️ V2 PENDING'
    END AS status;

-- ============================================================================
-- END OF VALIDATION SCRIPT
-- ============================================================================

-- Usage Instructions:
--
-- 1. Connect to your RVNKQuests database
-- 2. Execute this script in sections or all at once
-- 3. Review output for any ❌ MISSING or ⚠️ WARNING indicators
-- 4. Fix any issues before running the plugin
--
-- Expected Results:
-- - All V1 base tables should show ✅ EXISTS
-- - V2 migration should show ✅ V2 APPLIED (if migration run)
-- - All indexes should be present
-- - Foreign keys should be properly defined
-- - No orphaned records in tag assignments
--
-- Common Issues:
-- - If V2 shows ❌ V2 PENDING, run db/V2__add_leaderboard_cache.sql
-- - If orphaned_tag_assignments > 0, clean up tag_assignments table
-- - If ENGINE is not InnoDB, check MySQL configuration
