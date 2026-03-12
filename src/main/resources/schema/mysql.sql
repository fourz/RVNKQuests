-- RVNKQuests MySQL Schema
-- Per-player quest progress persistence

-- Main quest progress table
CREATE TABLE IF NOT EXISTS quest_progress (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    quest_id VARCHAR(100) NOT NULL,
    state VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    path_choice VARCHAR(50) DEFAULT NULL,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    metadata JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uq_player_quest (player_uuid, quest_id),
    INDEX idx_player (player_uuid),
    INDEX idx_quest (quest_id),
    INDEX idx_state (state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Per-objective completion tracking
CREATE TABLE IF NOT EXISTS quest_objective_progress (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    quest_id VARCHAR(100) NOT NULL,
    objective_id VARCHAR(100) NOT NULL,
    progress_count INT NOT NULL DEFAULT 0,
    target_count INT NOT NULL DEFAULT 1,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP NULL,
    metadata JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uq_player_quest_objective (player_uuid, quest_id, objective_id),
    INDEX idx_player_quest (player_uuid, quest_id),
    INDEX idx_completed (is_completed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Prevent double reward claiming
CREATE TABLE IF NOT EXISTS quest_rewards_claimed (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    quest_id VARCHAR(100) NOT NULL,
    reward_id VARCHAR(100) NOT NULL,
    claimed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uq_player_quest_reward (player_uuid, quest_id, reward_id),
    INDEX idx_player_quest (player_uuid, quest_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== Quest Definition Tables ====================

-- Quest definitions - the template/blueprint for each quest
CREATE TABLE IF NOT EXISTS quest_definitions (
    quest_id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    repeatable BOOLEAN NOT NULL DEFAULT FALSE,
    cooldown_minutes INT NOT NULL DEFAULT 0,
    prerequisites JSON,
    metadata JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_quest_def_category (category),
    INDEX idx_quest_def_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Quest definition objectives - objectives belonging to a quest definition
CREATE TABLE IF NOT EXISTS quest_definition_objectives (
    id INT AUTO_INCREMENT PRIMARY KEY,
    quest_id VARCHAR(100) NOT NULL,
    objective_id VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    target VARCHAR(200),
    required_amount INT NOT NULL DEFAULT 1,
    description TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    metadata JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uq_quest_objective (quest_id, objective_id),
    INDEX idx_quest_def_obj_quest (quest_id),
    FOREIGN KEY (quest_id) REFERENCES quest_definitions(quest_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Quest definition rewards - rewards belonging to a quest definition
CREATE TABLE IF NOT EXISTS quest_definition_rewards (
    id INT AUTO_INCREMENT PRIMARY KEY,
    quest_id VARCHAR(100) NOT NULL,
    reward_id VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    value VARCHAR(500),
    amount INT NOT NULL DEFAULT 1,
    description TEXT,
    metadata JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uq_quest_reward (quest_id, reward_id),
    INDEX idx_quest_def_rwd_quest (quest_id),
    FOREIGN KEY (quest_id) REFERENCES quest_definitions(quest_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== Quest Journal System Tables ====================

-- Quest journal entries - tracks all quest actions with timestamps
CREATE TABLE IF NOT EXISTS quest_journal_entries (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    quest_id VARCHAR(100) NOT NULL,
    action VARCHAR(32) NOT NULL,
    timestamp BIGINT NOT NULL,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_player_quest (player_uuid, quest_id),
    INDEX idx_timestamp (timestamp),
    INDEX idx_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Quest categories for organization
CREATE TABLE IF NOT EXISTS quest_categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(128),
    color_code VARCHAR(16),
    icon VARCHAR(64),
    description TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_name (name),
    INDEX idx_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Quest tags for flexible filtering
CREATE TABLE IF NOT EXISTS quest_tags (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(128),
    color_code VARCHAR(16),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Many-to-many relationship between quests and tags
CREATE TABLE IF NOT EXISTS quest_tag_assignments (
    quest_id VARCHAR(100) NOT NULL,
    tag_id INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (quest_id, tag_id),
    FOREIGN KEY (tag_id) REFERENCES quest_tags(id) ON DELETE CASCADE,
    INDEX idx_quest (quest_id),
    INDEX idx_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Leaderboard entries for competitive tracking
CREATE TABLE IF NOT EXISTS quest_leaderboard_entries (
    player_uuid VARCHAR(36) NOT NULL,
    player_name VARCHAR(16) NOT NULL,
    leaderboard_type VARCHAR(32) NOT NULL,
    value INT NOT NULL DEFAULT 0,
    rank INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    metadata JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (player_uuid, leaderboard_type),
    INDEX idx_type_value (leaderboard_type, value DESC),
    INDEX idx_type_rank (leaderboard_type, rank ASC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== Player Preferences Table ====================

-- Player notification preferences - local plugin storage
CREATE TABLE IF NOT EXISTS quest_player_preferences (
    player_id VARCHAR(36) NOT NULL,
    pref_key VARCHAR(64) NOT NULL,
    pref_value TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (player_id, pref_key),
    INDEX idx_player (player_id),
    INDEX idx_key (pref_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
