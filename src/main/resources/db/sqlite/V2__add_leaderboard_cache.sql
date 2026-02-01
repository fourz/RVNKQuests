-- Migration V2: Add Quest Leaderboard Cache Table (SQLite)
-- Adds caching layer for leaderboard data to improve performance
-- Target: SQLite 3+
-- Date: 2026-02-01

-- Leaderboard cache table for performance optimization
-- Stores pre-calculated leaderboard rankings with TTL expiration
CREATE TABLE IF NOT EXISTS quest_leaderboard_cache (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    leaderboard_type TEXT NOT NULL UNIQUE,
    cache_data TEXT NOT NULL,
    generated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TEXT NOT NULL
);

-- Create indexes separately in SQLite
CREATE INDEX IF NOT EXISTS idx_cache_type ON quest_leaderboard_cache(leaderboard_type);
CREATE INDEX IF NOT EXISTS idx_cache_expires ON quest_leaderboard_cache(expires_at);

-- Verify table creation
SELECT
    name,
    type,
    sql
FROM sqlite_master
WHERE type = 'table'
  AND name = 'quest_leaderboard_cache';
