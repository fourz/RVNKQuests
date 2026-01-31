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
