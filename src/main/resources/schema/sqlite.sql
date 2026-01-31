-- RVNKQuests SQLite Schema
-- Per-player quest progress persistence

-- Main quest progress table
CREATE TABLE IF NOT EXISTS quest_progress (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    quest_id TEXT NOT NULL,
    state TEXT NOT NULL DEFAULT 'NOT_STARTED',
    path_choice TEXT DEFAULT NULL,
    started_at TEXT NULL,
    completed_at TEXT NULL,
    metadata TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (player_uuid, quest_id)
);

CREATE INDEX IF NOT EXISTS idx_quest_progress_player ON quest_progress(player_uuid);
CREATE INDEX IF NOT EXISTS idx_quest_progress_quest ON quest_progress(quest_id);
CREATE INDEX IF NOT EXISTS idx_quest_progress_state ON quest_progress(state);

-- Per-objective completion tracking
CREATE TABLE IF NOT EXISTS quest_objective_progress (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    quest_id TEXT NOT NULL,
    objective_id TEXT NOT NULL,
    progress_count INTEGER NOT NULL DEFAULT 0,
    target_count INTEGER NOT NULL DEFAULT 1,
    is_completed INTEGER NOT NULL DEFAULT 0,
    completed_at TEXT NULL,
    metadata TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (player_uuid, quest_id, objective_id)
);

CREATE INDEX IF NOT EXISTS idx_objective_progress_player_quest ON quest_objective_progress(player_uuid, quest_id);
CREATE INDEX IF NOT EXISTS idx_objective_progress_completed ON quest_objective_progress(is_completed);

-- Prevent double reward claiming
CREATE TABLE IF NOT EXISTS quest_rewards_claimed (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    quest_id TEXT NOT NULL,
    reward_id TEXT NOT NULL,
    claimed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (player_uuid, quest_id, reward_id)
);

CREATE INDEX IF NOT EXISTS idx_rewards_player_quest ON quest_rewards_claimed(player_uuid, quest_id);
