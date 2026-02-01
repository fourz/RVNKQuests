-- Migration V2: Add Quest Leaderboard Cache Table
-- Adds caching layer for leaderboard data to improve performance
-- Target: MySQL 8+
-- Date: 2026-02-01

-- Leaderboard cache table for performance optimization
-- Stores pre-calculated leaderboard rankings with TTL expiration
CREATE TABLE IF NOT EXISTS quest_leaderboard_cache (
    id INT AUTO_INCREMENT PRIMARY KEY,
    leaderboard_type VARCHAR(32) NOT NULL UNIQUE,
    cache_data JSON NOT NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,

    INDEX idx_cache_type (leaderboard_type),
    INDEX idx_cache_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Cached leaderboard data with 5-minute TTL';

-- Verify table creation
SELECT
    TABLE_NAME,
    ENGINE,
    TABLE_ROWS,
    CREATE_TIME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'quest_leaderboard_cache';
