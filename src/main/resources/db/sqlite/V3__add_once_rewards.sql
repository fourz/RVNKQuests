-- Migration V3: Add run-once world reward records (#2268) (SQLite)
-- Records each `once: server` reward that has fired, so it fires on the first completion only.
-- Target: SQLite 3+
-- Date: 2026-10-10
--
-- The same CREATE TABLE IF NOT EXISTS is in schema/sqlite.sql, which runs on every start.

CREATE TABLE IF NOT EXISTS quest_once_rewards (
    quest_id TEXT NOT NULL,
    reward_id TEXT NOT NULL,
    fired_by TEXT NOT NULL,
    fired_at INTEGER NOT NULL,

    PRIMARY KEY (quest_id, reward_id)
);

-- Verify table creation
SELECT name, type, sql
FROM sqlite_master
WHERE type = 'table'
  AND name = 'quest_once_rewards';
