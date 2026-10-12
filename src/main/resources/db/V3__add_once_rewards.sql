-- Migration V3: Add run-once world reward records (#2268)
-- Records each `once: server` reward that has fired, so it fires on the first completion only.
-- Target: MySQL 8+
-- Date: 2026-10-10
--
-- The same CREATE TABLE IF NOT EXISTS is in schema/mysql.sql, which runs on every start, so a
-- server picks the table up on its first boot of 1.1.69. Run this file by hand only to create the
-- table before that boot. With a table prefix, prefix the table name here yourself.

CREATE TABLE IF NOT EXISTS quest_once_rewards (
    quest_id VARCHAR(100) NOT NULL,
    reward_id VARCHAR(200) NOT NULL,
    fired_by VARCHAR(36) NOT NULL,
    fired_at BIGINT NOT NULL,

    PRIMARY KEY (quest_id, reward_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Verify table creation
SELECT TABLE_NAME, ENGINE, CREATE_TIME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'quest_once_rewards';
