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

-- ==================== Quest Journal System Tables ====================

-- Quest journal entries - tracks all quest actions with timestamps
CREATE TABLE IF NOT EXISTS quest_journal_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    quest_id TEXT NOT NULL,
    action TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    details TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_journal_player_quest ON quest_journal_entries(player_uuid, quest_id);
CREATE INDEX IF NOT EXISTS idx_journal_timestamp ON quest_journal_entries(timestamp);
CREATE INDEX IF NOT EXISTS idx_journal_action ON quest_journal_entries(action);

-- Quest categories for organization
CREATE TABLE IF NOT EXISTS quest_categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    display_name TEXT,
    color_code TEXT,
    icon TEXT,
    description TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_categories_name ON quest_categories(name);
CREATE INDEX IF NOT EXISTS idx_categories_sort_order ON quest_categories(sort_order);

-- Quest tags for flexible filtering
CREATE TABLE IF NOT EXISTS quest_tags (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    display_name TEXT,
    color_code TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tags_name ON quest_tags(name);

-- Many-to-many relationship between quests and tags
CREATE TABLE IF NOT EXISTS quest_tag_assignments (
    quest_id TEXT NOT NULL,
    tag_id INTEGER NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (quest_id, tag_id),
    FOREIGN KEY (tag_id) REFERENCES quest_tags(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tag_assignments_quest ON quest_tag_assignments(quest_id);
CREATE INDEX IF NOT EXISTS idx_tag_assignments_tag ON quest_tag_assignments(tag_id);

-- Leaderboard entries for competitive tracking
CREATE TABLE IF NOT EXISTS quest_leaderboard_entries (
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL,
    leaderboard_type TEXT NOT NULL,
    value INTEGER NOT NULL DEFAULT 0,
    rank INTEGER NOT NULL DEFAULT 0,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (player_uuid, leaderboard_type)
);

CREATE INDEX IF NOT EXISTS idx_leaderboard_type_value ON quest_leaderboard_entries(leaderboard_type, value DESC);
CREATE INDEX IF NOT EXISTS idx_leaderboard_rank ON quest_leaderboard_entries(leaderboard_type, rank ASC);

-- ==================== Repeatable Quest System Tables (feat-31) ====================

-- Quest repeat configuration - defines which quests are repeatable
CREATE TABLE IF NOT EXISTS quest_repeat_config (
    quest_id TEXT PRIMARY KEY,
    repeat_type TEXT NOT NULL DEFAULT 'ONE_TIME',
    cooldown_seconds INTEGER DEFAULT 0,
    max_completions INTEGER DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_repeat_config_type ON quest_repeat_config(repeat_type);

-- Player quest repeat tracking - tracks per-player completion count and cooldowns
CREATE TABLE IF NOT EXISTS player_quest_repeats (
    player_uuid TEXT NOT NULL,
    quest_id TEXT NOT NULL,
    completion_count INTEGER DEFAULT 0,
    last_completion TEXT NULL,
    next_available TEXT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (player_uuid, quest_id)
);

CREATE INDEX IF NOT EXISTS idx_player_repeats_player ON player_quest_repeats(player_uuid);
CREATE INDEX IF NOT EXISTS idx_player_repeats_quest ON player_quest_repeats(quest_id);
CREATE INDEX IF NOT EXISTS idx_player_repeats_next_available ON player_quest_repeats(next_available);
