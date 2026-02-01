-- RVNKQuests V2 Migration Test Script (MySQL)
-- Tests leaderboard cache functionality after migration
-- Execute AFTER running V2__add_leaderboard_cache.sql

-- ============================================================================
-- TEST 1: Verify Table Structure
-- ============================================================================

SELECT 'TEST 1: Verify Table Structure' AS test_name;

-- Check table exists
SELECT
    TABLE_NAME,
    ENGINE,
    TABLE_COLLATION,
    CREATE_TIME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'quest_leaderboard_cache';

-- Expected: 1 row with ENGINE=InnoDB

-- Check column structure
SELECT
    COLUMN_NAME,
    COLUMN_TYPE,
    IS_NULLABLE,
    COLUMN_KEY,
    COLUMN_DEFAULT,
    EXTRA
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'quest_leaderboard_cache'
ORDER BY ORDINAL_POSITION;

-- Expected: 5 columns (id, leaderboard_type, cache_data, generated_at, expires_at)

-- ============================================================================
-- TEST 2: Verify Indexes
-- ============================================================================

SELECT 'TEST 2: Verify Indexes' AS test_name;

SELECT
    INDEX_NAME,
    COLUMN_NAME,
    NON_UNIQUE,
    SEQ_IN_INDEX
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'quest_leaderboard_cache'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- Expected: PRIMARY (id), idx_cache_type (leaderboard_type), idx_cache_expires (expires_at)

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
     NOW() + INTERVAL 5 MINUTE),
    ('test_fastest_average',
     '{"entries": [{"rank": 1, "uuid": "550e8400-e29b-41d4-a716-446655440001", "value": 30}]}',
     NOW() + INTERVAL 5 MINUTE),
    ('test_expired',
     '{"entries": []}',
     NOW() - INTERVAL 1 MINUTE);  -- Already expired

-- Verify insertions
SELECT
    id,
    leaderboard_type,
    JSON_VALID(cache_data) AS is_valid_json,
    generated_at,
    expires_at,
    CASE
        WHEN expires_at > NOW() THEN 'VALID'
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
EXPLAIN
SELECT cache_data
FROM quest_leaderboard_cache
WHERE leaderboard_type = 'test_most_completed';

-- Expected: Using index idx_cache_type (key_len=130)

-- Test index usage for expiration filtering
EXPLAIN
SELECT *
FROM quest_leaderboard_cache
WHERE expires_at > NOW();

-- Expected: Using index idx_cache_expires

-- ============================================================================
-- TEST 5: JSON Data Access
-- ============================================================================

SELECT 'TEST 5: JSON Data Access' AS test_name;

-- Extract JSON data from cache
SELECT
    leaderboard_type,
    JSON_EXTRACT(cache_data, '$.entries[0].rank') AS top_rank,
    JSON_EXTRACT(cache_data, '$.entries[0].uuid') AS top_player,
    JSON_EXTRACT(cache_data, '$.entries[0].value') AS top_value
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
-- VALUES ('test_most_completed', '{"entries": []}', NOW() + INTERVAL 5 MINUTE);

-- Expected: ERROR 1062 (23000): Duplicate entry 'test_most_completed'

-- Test update instead
UPDATE quest_leaderboard_cache
SET cache_data = '{"entries": [{"rank": 1, "uuid": "550e8400-e29b-41d4-a716-446655440000", "value": 150}]}',
    expires_at = NOW() + INTERVAL 5 MINUTE
WHERE leaderboard_type = 'test_most_completed';

-- Verify update
SELECT
    leaderboard_type,
    JSON_EXTRACT(cache_data, '$.entries[0].value') AS updated_value
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
WHERE expires_at < NOW();

-- Expected: 1 (test_expired)

-- Clean up expired entries
DELETE FROM quest_leaderboard_cache
WHERE expires_at < NOW();

-- Count expired entries after cleanup
SELECT COUNT(*) AS expired_after
FROM quest_leaderboard_cache
WHERE expires_at < NOW();

-- Expected: 0

-- ============================================================================
-- TEST 8: Cache Replacement Pattern
-- ============================================================================

SELECT 'TEST 8: Cache Replacement Pattern' AS test_name;

-- Use REPLACE INTO for upsert pattern
REPLACE INTO quest_leaderboard_cache (leaderboard_type, cache_data, expires_at)
VALUES
    ('test_most_completed',
     '{"entries": [{"rank": 1, "uuid": "550e8400-e29b-41d4-a716-446655440002", "value": 200}]}',
     NOW() + INTERVAL 5 MINUTE);

-- Verify replacement
SELECT
    leaderboard_type,
    JSON_EXTRACT(cache_data, '$.entries[0].value') AS value_after_replace,
    JSON_EXTRACT(cache_data, '$.entries[0].uuid') AS uuid_after_replace
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

-- Check for lock contentions (should be minimal with InnoDB)
SHOW ENGINE INNODB STATUS\G

-- Look for "TRANSACTIONS" section - should show no deadlocks

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
     NOW() + INTERVAL 5 MINUTE);

-- Verify JSON structure and size
SELECT
    leaderboard_type,
    LENGTH(cache_data) AS json_size_bytes,
    JSON_LENGTH(cache_data, '$.entries') AS entry_count,
    JSON_EXTRACT(cache_data, '$.total_players') AS total_players,
    expires_at
FROM quest_leaderboard_cache
WHERE leaderboard_type = 'most_completed';

-- Expected: json_size ~800-1000 bytes, entry_count=10, total_players=147

-- ============================================================================
-- TEST 11: Cleanup Test Data
-- ============================================================================

SELECT 'TEST 11: Cleanup Test Data' AS test_name;

-- Remove all test entries
DELETE FROM quest_leaderboard_cache WHERE leaderboard_type LIKE 'test_%';

-- Verify cleanup
SELECT COUNT(*) AS test_entries_remaining
FROM quest_leaderboard_cache
WHERE leaderboard_type LIKE 'test_%';

-- Expected: 0

-- ============================================================================
-- TEST SUMMARY
-- ============================================================================

SELECT 'TEST SUMMARY' AS test_name;

SELECT
    'quest_leaderboard_cache' AS table_name,
    COUNT(*) AS total_entries,
    SUM(CASE WHEN expires_at > NOW() THEN 1 ELSE 0 END) AS valid_entries,
    SUM(CASE WHEN expires_at <= NOW() THEN 1 ELSE 0 END) AS expired_entries,
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
    TIMESTAMPDIFF(SECOND, NOW(), expires_at) AS seconds_until_expiry,
    CASE
        WHEN expires_at > NOW() THEN '✅ VALID'
        ELSE '❌ EXPIRED'
    END AS status
FROM quest_leaderboard_cache
ORDER BY leaderboard_type;

-- ============================================================================
-- RECOMMENDATIONS
-- ============================================================================

SELECT 'RECOMMENDATIONS' AS section;

SELECT '1. Enable query caching for leaderboard lookups' AS recommendation
UNION ALL
SELECT '2. Schedule periodic cleanup of expired cache entries (cron job)'
UNION ALL
SELECT '3. Monitor cache hit/miss ratio in application logs'
UNION ALL
SELECT '4. Consider adding cache_hits column for analytics'
UNION ALL
SELECT '5. Set up monitoring alerts for cache size growth';

-- ============================================================================
-- END OF TEST SCRIPT
-- ============================================================================

-- Expected Final State:
-- ✅ quest_leaderboard_cache table exists with correct structure
-- ✅ All indexes present and being used
-- ✅ JSON data stored and retrieved correctly
-- ✅ Unique constraint enforced on leaderboard_type
-- ✅ Expiration logic works correctly
-- ✅ REPLACE INTO pattern works for cache updates
-- ✅ No lock contention or deadlocks
-- ✅ Production-like data stored successfully
--
-- If all tests pass, V2 migration is PRODUCTION READY
