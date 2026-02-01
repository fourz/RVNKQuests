-- RVNKQuests V2 Migration Test Script (SQLite)
-- Tests leaderboard cache functionality after migration
-- Execute AFTER running sqlite/V2__add_leaderboard_cache.sql

-- ============================================================================
-- TEST 1: Verify Table Structure
-- ============================================================================

SELECT 'TEST 1: Verify Table Structure' AS test_name;

-- Check table exists
SELECT
    name AS table_name,
    type,
    sql
FROM sqlite_master
WHERE type = 'table'
  AND name = 'quest_leaderboard_cache';

-- Expected: 1 row with full CREATE TABLE statement

-- Check column structure
PRAGMA table_info(quest_leaderboard_cache);

-- Expected: 5 columns (id, leaderboard_type, cache_data, generated_at, expires_at)

-- ============================================================================
-- TEST 2: Verify Indexes
-- ============================================================================

SELECT 'TEST 2: Verify Indexes' AS test_name;

SELECT
    name AS index_name,
    tbl_name AS table_name,
    sql AS index_definition
FROM sqlite_master
WHERE type = 'index'
  AND tbl_name = 'quest_leaderboard_cache'
ORDER BY name;

-- Expected: idx_cache_type, idx_cache_expires

-- ============================================================================
-- TEST 3: Insert Test Data
-- ============================================================================

SELECT 'TEST 3: Insert Test Data' AS test_name;

-- Clear any existing test data
DELETE FROM quest_leaderboard_cache WHERE leaderboard_type LIKE 'test_%';

-- Insert test cache entries
INSERT INTO quest_leaderboard_cache (leaderboard_type, cache_data, expires_at)
VALUES
    ('test_most_completed',
     '{"entries": [{"rank": 1, "uuid": "550e8400-e29b-41d4-a716-446655440000", "value": 100}]}',
     datetime('now', '+5 minutes')),
    ('test_fastest_average',
     '{"entries": [{"rank": 1, "uuid": "550e8400-e29b-41d4-a716-446655440001", "value": 30}]}',
     datetime('now', '+5 minutes')),
    ('test_expired',
     '{"entries": []}',
     datetime('now', '-1 minute'));  -- Already expired

-- Verify insertions
SELECT
    id,
    leaderboard_type,
    json_valid(cache_data) AS is_valid_json,
    generated_at,
    expires_at,
    CASE
        WHEN expires_at > datetime('now') THEN 'VALID'
        ELSE 'EXPIRED'
    END AS cache_status
FROM quest_leaderboard_cache
WHERE leaderboard_type LIKE 'test_%'
ORDER BY leaderboard_type;

-- Expected: 3 rows, all with is_valid_json=1, 2 VALID, 1 EXPIRED

-- ============================================================================
-- TEST 4: Query Performance Test
-- ============================================================================

SELECT 'TEST 4: Query Performance Test' AS test_name;

-- Test index usage for leaderboard_type lookup
EXPLAIN QUERY PLAN
SELECT cache_data
FROM quest_leaderboard_cache
WHERE leaderboard_type = 'test_most_completed';

-- Expected: SEARCH using index idx_cache_type

-- Test index usage for expiration filtering
EXPLAIN QUERY PLAN
SELECT *
FROM quest_leaderboard_cache
WHERE expires_at > datetime('now');

-- Expected: SEARCH using index idx_cache_expires

-- ============================================================================
-- TEST 5: JSON Data Access
-- ============================================================================

SELECT 'TEST 5: JSON Data Access' AS test_name;

-- Extract JSON data from cache (SQLite uses json_extract function)
SELECT
    leaderboard_type,
    json_extract(cache_data, '$.entries[0].rank') AS top_rank,
    json_extract(cache_data, '$.entries[0].uuid') AS top_player,
    json_extract(cache_data, '$.entries[0].value') AS top_value
FROM quest_leaderboard_cache
WHERE leaderboard_type = 'test_most_completed';

-- Expected: rank=1, uuid=550e8400..., value=100

-- ============================================================================
-- TEST 6: Unique Constraint Test
-- ============================================================================

SELECT 'TEST 6: Unique Constraint Test' AS test_name;

-- Attempt duplicate insert (should fail)
-- UNCOMMENT to test (will produce error):
-- INSERT INTO quest_leaderboard_cache (leaderboard_type, cache_data, expires_at)
-- VALUES ('test_most_completed', '{"entries": []}', datetime('now', '+5 minutes'));

-- Expected: UNIQUE constraint failed: quest_leaderboard_cache.leaderboard_type

-- Test update instead
UPDATE quest_leaderboard_cache
SET cache_data = '{"entries": [{"rank": 1, "uuid": "550e8400-e29b-41d4-a716-446655440000", "value": 150}]}',
    expires_at = datetime('now', '+5 minutes')
WHERE leaderboard_type = 'test_most_completed';

-- Verify update
SELECT
    leaderboard_type,
    json_extract(cache_data, '$.entries[0].value') AS updated_value
FROM quest_leaderboard_cache
WHERE leaderboard_type = 'test_most_completed';

-- Expected: updated_value=150

-- ============================================================================
-- TEST 7: Expiration Cleanup Test
-- ============================================================================

SELECT 'TEST 7: Expiration Cleanup Test' AS test_name;

-- Count expired entries before cleanup
SELECT COUNT(*) AS expired_before
FROM quest_leaderboard_cache
WHERE expires_at < datetime('now');

-- Expected: 1 (test_expired)

-- Clean up expired entries
DELETE FROM quest_leaderboard_cache
WHERE expires_at < datetime('now');

-- Count expired entries after cleanup
SELECT COUNT(*) AS expired_after
FROM quest_leaderboard_cache
WHERE expires_at < datetime('now');

-- Expected: 0

-- ============================================================================
-- TEST 8: Cache Replacement Pattern
-- ============================================================================

SELECT 'TEST 8: Cache Replacement Pattern' AS test_name;

-- Use REPLACE INTO for upsert pattern (SQLite supports this)
REPLACE INTO quest_leaderboard_cache (leaderboard_type, cache_data, expires_at)
VALUES
    ('test_most_completed',
     '{"entries": [{"rank": 1, "uuid": "550e8400-e29b-41d4-a716-446655440002", "value": 200}]}',
     datetime('now', '+5 minutes'));

-- Verify replacement
SELECT
    leaderboard_type,
    json_extract(cache_data, '$.entries[0].value') AS value_after_replace,
    json_extract(cache_data, '$.entries[0].uuid') AS uuid_after_replace
FROM quest_leaderboard_cache
WHERE leaderboard_type = 'test_most_completed';

-- Expected: value=200, uuid=...440002 (replaced)

-- ============================================================================
-- TEST 9: Concurrent Access Simulation
-- ============================================================================

SELECT 'TEST 9: Concurrent Access Simulation' AS test_name;

-- Simulate multiple reads (typical leaderboard access pattern)
SELECT cache_data FROM quest_leaderboard_cache WHERE leaderboard_type = 'test_most_completed';
SELECT cache_data FROM quest_leaderboard_cache WHERE leaderboard_type = 'test_most_completed';
SELECT cache_data FROM quest_leaderboard_cache WHERE leaderboard_type = 'test_most_completed';

-- Note: SQLite has single-writer limitation, so concurrent writes will block
-- Read operations are concurrent-safe

-- ============================================================================
-- TEST 10: Production-Like Leaderboard Data
-- ============================================================================

SELECT 'TEST 10: Production-Like Leaderboard Data' AS test_name;

-- Create realistic leaderboard cache entry
REPLACE INTO quest_leaderboard_cache (leaderboard_type, cache_data, expires_at)
VALUES
    ('most_completed',
     '{
       "entries": [
         {"rank": 1, "uuid": "550e8400-e29b-41d4-a716-446655440000", "name": "Player1", "value": 150},
         {"rank": 2, "uuid": "550e8400-e29b-41d4-a716-446655440001", "name": "Player2", "value": 120},
         {"rank": 3, "uuid": "550e8400-e29b-41d4-a716-446655440002", "name": "Player3", "value": 100},
         {"rank": 4, "uuid": "550e8400-e29b-41d4-a716-446655440003", "name": "Player4", "value": 95},
         {"rank": 5, "uuid": "550e8400-e29b-41d4-a716-446655440004", "name": "Player5", "value": 80},
         {"rank": 6, "uuid": "550e8400-e29b-41d4-a716-446655440005", "name": "Player6", "value": 75},
         {"rank": 7, "uuid": "550e8400-e29b-41d4-a716-446655440006", "name": "Player7", "value": 70},
         {"rank": 8, "uuid": "550e8400-e29b-41d4-a716-446655440007", "name": "Player8", "value": 65},
         {"rank": 9, "uuid": "550e8400-e29b-41d4-a716-446655440008", "name": "Player9", "value": 60},
         {"rank": 10, "uuid": "550e8400-e29b-41d4-a716-446655440009", "name": "Player10", "value": 55}
       ],
       "generated_at": "2026-02-01T12:00:00Z",
       "total_players": 147
     }',
     datetime('now', '+5 minutes'));

-- Verify JSON structure and size
SELECT
    leaderboard_type,
    LENGTH(cache_data) AS json_size_bytes,
    json_array_length(cache_data, '$.entries') AS entry_count,
    json_extract(cache_data, '$.total_players') AS total_players,
    expires_at
FROM quest_leaderboard_cache
WHERE leaderboard_type = 'most_completed';

-- Expected: json_size ~800-1000 bytes, entry_count=10, total_players=147

-- ============================================================================
-- TEST 11: Database Integrity Check
-- ============================================================================

SELECT 'TEST 11: Database Integrity Check' AS test_name;

-- Run SQLite integrity check
PRAGMA integrity_check;

-- Expected: ok

-- Quick check for corruption
PRAGMA quick_check;

-- Expected: ok

-- ============================================================================
-- TEST 12: Cleanup Test Data
-- ============================================================================

SELECT 'TEST 12: Cleanup Test Data' AS test_name;

-- Remove all test entries
DELETE FROM quest_leaderboard_cache WHERE leaderboard_type LIKE 'test_%';

-- Verify cleanup
SELECT COUNT(*) AS test_entries_remaining
FROM quest_leaderboard_cache
WHERE leaderboard_type LIKE 'test_%';

-- Expected: 0

-- ============================================================================
-- TEST 13: Foreign Key Check
-- ============================================================================

SELECT 'TEST 13: Foreign Key Check' AS test_name;

-- Verify foreign keys are enabled
PRAGMA foreign_keys;

-- Expected: 1 (enabled)

-- Check for foreign key violations
PRAGMA foreign_key_check;

-- Expected: No rows (no violations)

-- ============================================================================
-- TEST SUMMARY
-- ============================================================================

SELECT 'TEST SUMMARY' AS test_name;

SELECT
    'quest_leaderboard_cache' AS table_name,
    COUNT(*) AS total_entries,
    SUM(CASE WHEN expires_at > datetime('now') THEN 1 ELSE 0 END) AS valid_entries,
    SUM(CASE WHEN expires_at <= datetime('now') THEN 1 ELSE 0 END) AS expired_entries,
    AVG(LENGTH(cache_data)) AS avg_cache_size_bytes,
    MIN(generated_at) AS oldest_cache,
    MAX(generated_at) AS newest_cache
FROM quest_leaderboard_cache;

-- List all current cache entries
SELECT
    id,
    leaderboard_type,
    LENGTH(cache_data) AS size_bytes,
    generated_at,
    expires_at,
    CAST((julianday(expires_at) - julianday('now')) * 86400 AS INTEGER) AS seconds_until_expiry,
    CASE
        WHEN expires_at > datetime('now') THEN '✅ VALID'
        ELSE '❌ EXPIRED'
    END AS status
FROM quest_leaderboard_cache
ORDER BY leaderboard_type;

-- ============================================================================
-- SQLITE-SPECIFIC CHECKS
-- ============================================================================

SELECT 'SQLITE-SPECIFIC CHECKS' AS test_name;

-- Check database page size (should be 4096 or higher for performance)
PRAGMA page_size;

-- Check encoding (should be UTF-8)
PRAGMA encoding;

-- Check journal mode (should be WAL for better concurrency)
PRAGMA journal_mode;

-- Check synchronous setting
PRAGMA synchronous;

-- Check auto_vacuum setting
PRAGMA auto_vacuum;

-- ============================================================================
-- RECOMMENDATIONS
-- ============================================================================

SELECT 'RECOMMENDATIONS' AS section;

SELECT '1. Enable WAL mode for better concurrent reads: PRAGMA journal_mode=WAL;' AS recommendation
UNION ALL
SELECT '2. Schedule periodic cleanup of expired cache entries (cron job)'
UNION ALL
SELECT '3. Monitor cache hit/miss ratio in application logs'
UNION ALL
SELECT '4. Set page_size=4096 for optimal performance (if not already set)'
UNION ALL
SELECT '5. Enable foreign keys: PRAGMA foreign_keys=ON;'
UNION ALL
SELECT '6. Consider VACUUM periodically to reclaim space';

-- ============================================================================
-- PERFORMANCE OPTIMIZATION SUGGESTIONS
-- ============================================================================

SELECT 'PERFORMANCE OPTIMIZATION' AS section;

-- Enable WAL mode for better concurrency
-- PRAGMA journal_mode=WAL;

-- Enable foreign keys
-- PRAGMA foreign_keys=ON;

-- Set cache size (pages)
-- PRAGMA cache_size=-2000;  -- 2MB cache

-- Set page size (must be done before any tables are created)
-- PRAGMA page_size=4096;

-- ============================================================================
-- END OF TEST SCRIPT
-- ============================================================================

-- Expected Final State:
-- ✅ quest_leaderboard_cache table exists with correct structure
-- ✅ All indexes present and being used
-- ✅ JSON data stored and retrieved correctly (as TEXT)
-- ✅ Unique constraint enforced on leaderboard_type
-- ✅ Expiration logic works correctly
-- ✅ REPLACE INTO pattern works for cache updates
-- ✅ Database integrity check passes (ok)
-- ✅ No foreign key violations
-- ✅ Production-like data stored successfully
--
-- SQLite-Specific Notes:
-- ⚠️ SQLite has single-writer limitation (concurrent writes will block)
-- ⚠️ JSON stored as TEXT (manual parsing required in application)
-- ⚠️ Timestamps stored as TEXT in ISO-8601 format
-- ⚠️ Foreign keys must be explicitly enabled
-- ⚠️ Consider WAL mode for better read concurrency
--
-- If all tests pass, V2 migration is PRODUCTION READY for SQLite
