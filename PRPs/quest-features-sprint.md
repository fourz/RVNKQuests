name: "RVNKQuests Feature Sprint - Quest Journal, Categories, Leaderboards, and Lore Integration"
description: |
  Comprehensive feature sprint implementing Quest Journal System, Quest Categories/Tags,
  Leaderboards, Lore Integration, and essential player-facing commands for RVNKQuests plugin.

---

## Goal

**Feature Goal**: Deliver a complete suite of quest management features including player journal tracking, quest categorization with tagging system, competitive leaderboards, deep lore integration, and essential player/admin commands with full console compatibility.

**Deliverable**: Production-ready implementations of:
1. Quest Journal System with history tracking and statistics
2. Quest Categories and Tags with filtering capabilities
3. Quest Leaderboards with multiple ranking types
4. RVNKLore cross-plugin integration
5. Console-compatible commands for progress, abandonment, and debugging

**Success Definition**:
- All features accessible via commands from both players and console
- Database schema validated against existing data
- No regressions in existing quest functionality
- Performance targets maintained (TPS ≥ 19.5 on production server)
- 70%+ test coverage for new components

## User Persona

**Target Users**:
- **Primary**: Minecraft server players using quest system for gameplay progression
- **Secondary**: Server administrators managing quest content and debugging
- **Tertiary**: Other plugin developers integrating with RVNKQuests API

**Use Cases**:
1. **Player Journal**: Player wants to review quest history, track statistics, and see completion records
2. **Quest Discovery**: Player searches for quests by category (Main Story, Daily) or tags (PvE, Exploration)
3. **Competition**: Player checks leaderboards to compare progress with server community
4. **Narrative Integration**: Player completes quest and unlocks related lore entries automatically
5. **Admin Management**: Console operator needs to debug quest states or modify player progress remotely

**User Journey**:
1. Player joins server and uses `/quest journal` to view available and completed quests
2. Player filters quests using `/quest list category:Daily tag:PvE` to find relevant content
3. Player completes quest, automatically unlocking lore entry in RVNKLore plugin
4. Player checks `/quest leaderboard fastest` to see ranking
5. Admin uses `/quest progress <quest_id> <player>` from console to debug issues

**Pain Points Addressed**:
- **No quest history**: Players can't track what they've completed or review past achievements
- **Quest overload**: No way to filter or categorize large numbers of quests
- **No competition**: Missing leaderboards for player engagement and competition
- **Disconnected narrative**: Lore and quests exist in silos without cross-plugin integration
- **Console limitations**: Many commands require player execution, blocking automation

## Why

**Business Value**:
- **Player Retention**: Journal and leaderboards increase engagement through tracking and competition
- **Content Discovery**: Categories and tags improve quest visibility and reduce player confusion
- **Narrative Cohesion**: Lore integration creates immersive storytelling experience
- **Admin Efficiency**: Console commands enable automation and remote debugging

**Integration with Existing Features**:
- Builds on existing quest state machine (NOT_STARTED → QUEST_ACTIVE → QUEST_COMPLETE → QUEST_FINISHED)
- Extends current database schema (quest_progress, quest_objective_progress, quest_rewards_claimed)
- Integrates with CommandManager framework for consistent command handling
- Uses existing async patterns (CompletableFuture) for database operations

**Problems Solved**:
- **For Players**: Lack of quest tracking, difficulty finding relevant quests, no competitive element
- **For Admins**: Cannot debug player issues from console, no quest analytics available
- **For Developers**: No public API for quest data access, difficult to integrate with other plugins

## What

### User-Visible Behavior

**Quest Journal System**:
- `/quest journal [player]` - Display quest history with completion dates, statistics
- Journal UI shows: active quests, completed quests, failed quests, total statistics
- Per-player metrics: total completed, fastest completion time, current streak

**Quest Categories and Tags**:
- Predefined categories: Main Story, Side Quest, Daily, Weekly, Event, Challenge
- Custom tags: PvE, PvP, Exploration, Crafting, Social, Combat
- `/quest list category:<name>` - Filter by category
- `/quest list tag:<name>` - Filter by tag
- Quest definitions in YAML support multiple categories and tags

**Quest Leaderboards**:
- `/quest leaderboard [type]` - Display rankings
- Types: most_completed, fastest_average, current_streak, category_leader
- Cached leaderboard data refreshes every 5 minutes
- Top 10 players displayed with pagination support

**Lore Integration**:
- Quest completion triggers lore unlock in RVNKLore
- Quest objectives can reference lore entries as prerequisites
- Lore rewards delivered via RVNKLore API

**Commands**:
- `/quest progress <quest_id> [player]` - Show detailed progress for quest
- `/quest abandon <quest_id> [player]` - Abandon active quest (requires confirmation)
- `/quest state <quest_id> <state> [player]` - Debug command to manually set quest state

### Technical Requirements

**Database Schema Extensions**:
- New tables: quest_journal_entries, quest_categories, quest_tags, quest_tag_assignments, quest_leaderboard_entries, quest_leaderboard_cache
- Schema must be backward compatible with existing quest_progress, quest_objective_progress, quest_rewards_claimed
- Support both MySQL and SQLite backends

**Performance Requirements**:
- Leaderboard queries ≤ 100ms (cached)
- Journal page load ≤ 50ms
- Tag filtering ≤ 200ms for 1000+ quests
- No TPS impact during leaderboard refresh

**Integration Requirements**:
- RVNKLore soft dependency (plugin works without it)
- API methods for external plugin access
- Event firing for quest journal updates, category changes, leaderboard updates

### Success Criteria

- [ ] All commands work from console with `[player]` argument
- [ ] Database schema validates with existing quest data (no data loss)
- [ ] Journal displays accurate quest history for all players
- [ ] Categories and tags filter quests correctly
- [ ] Leaderboards update within 5 minutes of quest completion
- [ ] RVNKLore integration unlocks lore entries on quest completion
- [ ] Performance targets met: TPS ≥ 19.5, query times within limits
- [ ] Test coverage ≥ 70% for new components
- [ ] Zero security vulnerabilities in command handling

## All Needed Context

### Context Completeness Check

This PRP includes:
- Complete database schema design with MySQL and SQLite variants
- Detailed command specifications with permission nodes
- Integration patterns with existing RVNKQuests architecture
- RVNKLore cross-plugin communication patterns
- Performance requirements and caching strategies
- Testing requirements and validation criteria

### Documentation & References

```yaml
# MUST READ - Include these in your context window
- file: repos/RVNKQuests/src/main/java/org/fourz/RVNKQuests/data/DatabaseManager.java
  why: HikariCP connection pooling pattern, schema initialization logic
  pattern: Async database operations with CompletableFuture
  gotcha: SQLite limitation (single writer), MySQL prepared statement caching

- file: repos/RVNKQuests/src/main/resources/schema/mysql.sql
  why: Existing schema structure for quest_progress, quest_objective_progress, quest_rewards_claimed
  pattern: JSON metadata columns, composite unique keys, timestamp tracking
  critical: Must maintain backward compatibility with existing indexes and constraints

- file: repos/RVNKQuests/src/main/resources/schema/sqlite.sql
  why: SQLite-specific schema patterns (TEXT vs VARCHAR, INTEGER vs BOOLEAN)
  pattern: Separate index creation statements, AUTOINCREMENT vs AUTO_INCREMENT
  gotcha: SQLite stores booleans as INTEGER (0/1), timestamps as TEXT

- file: repos/RVNKQuests/src/main/java/org/fourz/RVNKQuests/command/CommandManager.java
  why: Centralized command routing and permission checking
  pattern: BaseCommand and BaseSubCommand abstractions
  critical: Console compatibility requires CommandSender instead of Player type

- url: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/command/CommandSender.html
  why: Console-compatible command execution patterns
  critical: Use CommandSender interface, check instanceof Player only when player-specific

- url: https://github.com/brettwooldridge/HikariCP
  why: Connection pool configuration and performance tuning
  critical: MaximumPoolSize=1 for SQLite, 10 for MySQL; enable prepared statement caching

- docfile: .claude/rules/rvnk-coding-standards.md
  why: Java 17+ conventions, async patterns, console support requirements
  section: Development Standards and Best Practices

- docfile: .claude/rules/java-plugin-build.md
  why: Maven build commands, MCSS deployment patterns
  section: Maven Build Commands and MCSS Deployment
```

### Current Codebase Tree

```bash
repos/RVNKQuests/
├── src/main/java/org/fourz/RVNKQuests/
│   ├── command/
│   │   ├── BaseCommand.java
│   │   ├── BaseSubCommand.java
│   │   ├── CommandManager.java
│   │   ├── QuestCommand.java
│   │   ├── QuestDebugSubCommand.java
│   │   ├── QuestReloadSubCommand.java
│   │   ├── QuestStartSubCommand.java
│   │   ├── QuestStateSubCommand.java
│   │   └── [other subcommands]
│   ├── config/
│   │   └── ConfigManager.java
│   ├── data/
│   │   ├── DatabaseManager.java
│   │   ├── FallbackTracker.java
│   │   └── dto/
│   │       ├── EnhancedObjectiveDTO.java
│   │       ├── ObjectiveCondition.java
│   │       └── ObjectiveDTO.java
│   ├── lore/
│   │   └── [lore integration classes]
│   ├── objective/
│   │   └── [objective implementations]
│   ├── quest/
│   │   ├── Quest.java (interface)
│   │   ├── QuestManager.java
│   │   └── [quest implementations]
│   ├── reward/
│   │   └── [reward implementations]
│   ├── service/
│   │   └── IQuestDatabaseService.java
│   └── trigger/
│       └── [trigger implementations]
├── src/main/resources/
│   ├── schema/
│   │   ├── mysql.sql (existing tables)
│   │   └── sqlite.sql (existing tables)
│   ├── config.yml
│   └── plugin.yml
├── pom.xml
└── ROADMAP.md
```

### Desired Codebase Tree with New Files

```bash
repos/RVNKQuests/
├── src/main/java/org/fourz/RVNKQuests/
│   ├── command/
│   │   ├── [existing commands]
│   │   ├── QuestJournalSubCommand.java      # NEW: /quest journal [player]
│   │   ├── QuestProgressSubCommand.java     # NEW: /quest progress <quest_id> [player]
│   │   ├── QuestAbandonSubCommand.java      # NEW: /quest abandon <quest_id> [player]
│   │   └── QuestLeaderboardSubCommand.java  # NEW: /quest leaderboard [type]
│   ├── data/
│   │   ├── [existing classes]
│   │   ├── repository/
│   │   │   ├── JournalRepository.java       # NEW: Journal data access
│   │   │   ├── CategoryRepository.java      # NEW: Category/tag data access
│   │   │   └── LeaderboardRepository.java   # NEW: Leaderboard data access
│   ├── journal/
│   │   ├── JournalEntry.java                # NEW: Journal entry model
│   │   ├── JournalService.java              # NEW: Journal business logic
│   │   └── QuestStatistics.java             # NEW: Player statistics DTO
│   ├── category/
│   │   ├── QuestCategory.java               # NEW: Category enum
│   │   ├── QuestTag.java                    # NEW: Tag model
│   │   └── CategoryService.java             # NEW: Category filtering logic
│   ├── leaderboard/
│   │   ├── LeaderboardType.java             # NEW: Leaderboard type enum
│   │   ├── LeaderboardEntry.java            # NEW: Leaderboard entry model
│   │   ├── LeaderboardService.java          # NEW: Leaderboard business logic
│   │   └── LeaderboardCache.java            # NEW: Caching layer
│   ├── integration/
│   │   └── RVNKLoreIntegration.java         # NEW: Cross-plugin communication
├── src/main/resources/
│   ├── schema/
│   │   ├── mysql.sql                        # MODIFIED: Add new tables
│   │   └── sqlite.sql                       # MODIFIED: Add new tables
│   └── migrations/
│       ├── V2_add_journal_tables.sql        # NEW: Migration script
│       ├── V3_add_category_tables.sql       # NEW: Migration script
│       └── V4_add_leaderboard_tables.sql    # NEW: Migration script
└── src/test/java/org/fourz/RVNKQuests/
    ├── journal/
    │   └── JournalServiceTest.java          # NEW: Journal tests
    ├── category/
    │   └── CategoryServiceTest.java         # NEW: Category tests
    ├── leaderboard/
    │   └── LeaderboardServiceTest.java      # NEW: Leaderboard tests
    └── integration/
        └── RVNKLoreIntegrationTest.java     # NEW: Integration tests
```

### Known Gotchas & Library Quirks

```java
// CRITICAL: HikariCP requires different pool sizes for MySQL vs SQLite
// Example: DatabaseManager.java lines 139-163
// MySQL: setMaximumPoolSize(10) for concurrent access
// SQLite: setMaximumPoolSize(1) due to single-writer limitation

// CRITICAL: Bukkit API requires CommandSender interface for console compatibility
// Example pattern for console-compatible commands:
public boolean execute(CommandSender sender, String[] args) {
    Player target;
    if (args.length == 0) {
        if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage("Console must specify player: /quest journal <player>");
            return false;
        }
    } else {
        target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("Player not found: " + args[0]);
            return false;
        }
    }
}

// CRITICAL: SQLite stores BOOLEAN as INTEGER (0/1)
// MySQL uses BOOLEAN/TINYINT(1), SQLite uses INTEGER with 0=false, 1=true
// Schema must reflect this difference

// CRITICAL: Async database operations must use plugin's executor service
// Never block main thread - use CompletableFuture.supplyAsync(...)
// Example: DatabaseManager.getExecutor() for async operations

// CRITICAL: JSON metadata column in MySQL, TEXT in SQLite
// MySQL: metadata JSON (native type)
// SQLite: metadata TEXT (parse as JSON string)

// GOTCHA: Quest state machine transitions are strict
// Valid states: NOT_STARTED, QUEST_ACTIVE, QUEST_COMPLETE, QUEST_REWARD_PENDING, QUEST_FINISHED
// Validate transitions before database updates

// GOTCHA: RVNKLore is soft dependency (may not be present)
// Always check plugin existence before cross-plugin calls
// Pattern: Bukkit.getPluginManager().getPlugin("RVNKLore") != null
```

## Implementation Blueprint

### Data Models and Structure

Create core data models for journal, categories, leaderboards, and lore integration.

```java
// Journal Entry Model (Java Record for immutability)
package org.fourz.RVNKQuests.journal;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable quest journal entry representing player's quest history.
 */
public record JournalEntry(
    UUID playerUuid,
    String questId,
    String questState,
    Instant startedAt,
    Instant completedAt,
    long durationSeconds,
    String pathChoice,
    int objectivesCompleted,
    int totalObjectives
) {
    /**
     * Check if quest is completed.
     */
    public boolean isCompleted() {
        return "QUEST_FINISHED".equals(questState);
    }

    /**
     * Check if quest is active.
     */
    public boolean isActive() {
        return "QUEST_ACTIVE".equals(questState);
    }
}

// Quest Statistics DTO
package org.fourz.RVNKQuests.journal;

/**
 * Player quest statistics aggregated from journal.
 */
public record QuestStatistics(
    UUID playerUuid,
    int totalCompleted,
    int totalActive,
    int totalFailed,
    long averageCompletionSeconds,
    long fastestCompletionSeconds,
    int currentStreak,
    int longestStreak
) {}

// Quest Category Enum
package org.fourz.RVNKQuests.category;

/**
 * Predefined quest categories for organization.
 */
public enum QuestCategory {
    MAIN_STORY("Main Story"),
    SIDE_QUEST("Side Quest"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    EVENT("Event"),
    CHALLENGE("Challenge");

    private final String displayName;

    QuestCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

// Quest Tag Model
package org.fourz.RVNKQuests.category;

/**
 * Custom quest tags for flexible filtering.
 */
public record QuestTag(
    String name,
    String description,
    String color
) {
    // Predefined common tags
    public static final QuestTag PVE = new QuestTag("PvE", "Player vs Environment", "GREEN");
    public static final QuestTag PVP = new QuestTag("PvP", "Player vs Player", "RED");
    public static final QuestTag EXPLORATION = new QuestTag("Exploration", "World exploration", "BLUE");
    public static final QuestTag CRAFTING = new QuestTag("Crafting", "Crafting and building", "YELLOW");
    public static final QuestTag SOCIAL = new QuestTag("Social", "Social interaction", "LIGHT_PURPLE");
    public static final QuestTag COMBAT = new QuestTag("Combat", "Combat focused", "DARK_RED");
}

// Leaderboard Entry Model
package org.fourz.RVNKQuests.leaderboard;

import java.util.UUID;

/**
 * Single leaderboard entry for a player.
 */
public record LeaderboardEntry(
    int rank,
    UUID playerUuid,
    String playerName,
    int value,
    String displayValue
) implements Comparable<LeaderboardEntry> {
    @Override
    public int compareTo(LeaderboardEntry other) {
        return Integer.compare(other.value, this.value); // Descending order
    }
}

// Leaderboard Type Enum
package org.fourz.RVNKQuests.leaderboard;

/**
 * Types of leaderboards available.
 */
public enum LeaderboardType {
    MOST_COMPLETED("Most Quests Completed"),
    FASTEST_AVERAGE("Fastest Average Completion"),
    CURRENT_STREAK("Current Completion Streak"),
    CATEGORY_LEADER("Category Leader");

    private final String displayName;

    LeaderboardType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
```

### Database Schema Design

Extend existing schema with new tables while maintaining backward compatibility.

**MySQL Schema Extension** (`src/main/resources/schema/mysql.sql`):

```sql
-- Existing tables remain unchanged:
-- quest_progress, quest_objective_progress, quest_rewards_claimed

-- Quest journal entries (historical tracking)
CREATE TABLE IF NOT EXISTS quest_journal_entries (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    quest_id VARCHAR(100) NOT NULL,
    quest_state VARCHAR(50) NOT NULL,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    duration_seconds INT DEFAULT 0,
    path_choice VARCHAR(50) DEFAULT NULL,
    objectives_completed INT DEFAULT 0,
    total_objectives INT DEFAULT 0,
    metadata JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_journal_player (player_uuid),
    INDEX idx_journal_quest (quest_id),
    INDEX idx_journal_state (quest_state),
    INDEX idx_journal_completed (completed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Quest categories (predefined types)
CREATE TABLE IF NOT EXISTS quest_categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    quest_id VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uq_quest_category (quest_id, category),
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Quest tags (custom labels)
CREATE TABLE IF NOT EXISTS quest_tags (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    color VARCHAR(20) DEFAULT 'WHITE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Quest tag assignments (many-to-many)
CREATE TABLE IF NOT EXISTS quest_tag_assignments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    quest_id VARCHAR(100) NOT NULL,
    tag_name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uq_quest_tag (quest_id, tag_name),
    INDEX idx_tag_assignments_quest (quest_id),
    INDEX idx_tag_assignments_tag (tag_name),

    FOREIGN KEY (tag_name) REFERENCES quest_tags(name) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Leaderboard entries (player rankings)
CREATE TABLE IF NOT EXISTS quest_leaderboard_entries (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    leaderboard_type VARCHAR(50) NOT NULL,
    value INT NOT NULL DEFAULT 0,
    rank INT DEFAULT NULL,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uq_player_leaderboard (player_uuid, leaderboard_type),
    INDEX idx_leaderboard_type_rank (leaderboard_type, rank),
    INDEX idx_leaderboard_value (leaderboard_type, value DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Leaderboard cache (performance optimization)
CREATE TABLE IF NOT EXISTS quest_leaderboard_cache (
    id INT AUTO_INCREMENT PRIMARY KEY,
    leaderboard_type VARCHAR(50) NOT NULL UNIQUE,
    cache_data JSON NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,

    INDEX idx_cache_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**SQLite Schema Extension** (`src/main/resources/schema/sqlite.sql`):

```sql
-- Existing tables remain unchanged:
-- quest_progress, quest_objective_progress, quest_rewards_claimed

-- Quest journal entries (historical tracking)
CREATE TABLE IF NOT EXISTS quest_journal_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    quest_id TEXT NOT NULL,
    quest_state TEXT NOT NULL,
    started_at TEXT NULL,
    completed_at TEXT NULL,
    duration_seconds INTEGER DEFAULT 0,
    path_choice TEXT DEFAULT NULL,
    objectives_completed INTEGER DEFAULT 0,
    total_objectives INTEGER DEFAULT 0,
    metadata TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_journal_player ON quest_journal_entries(player_uuid);
CREATE INDEX IF NOT EXISTS idx_journal_quest ON quest_journal_entries(quest_id);
CREATE INDEX IF NOT EXISTS idx_journal_state ON quest_journal_entries(quest_state);
CREATE INDEX IF NOT EXISTS idx_journal_completed ON quest_journal_entries(completed_at);

-- Quest categories (predefined types)
CREATE TABLE IF NOT EXISTS quest_categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    quest_id TEXT NOT NULL,
    category TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (quest_id, category)
);

CREATE INDEX IF NOT EXISTS idx_category ON quest_categories(category);

-- Quest tags (custom labels)
CREATE TABLE IF NOT EXISTS quest_tags (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    color TEXT DEFAULT 'WHITE',
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Quest tag assignments (many-to-many)
CREATE TABLE IF NOT EXISTS quest_tag_assignments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    quest_id TEXT NOT NULL,
    tag_name TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (quest_id, tag_name),
    FOREIGN KEY (tag_name) REFERENCES quest_tags(name) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tag_assignments_quest ON quest_tag_assignments(quest_id);
CREATE INDEX IF NOT EXISTS idx_tag_assignments_tag ON quest_tag_assignments(tag_name);

-- Leaderboard entries (player rankings)
CREATE TABLE IF NOT EXISTS quest_leaderboard_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    leaderboard_type TEXT NOT NULL,
    value INTEGER NOT NULL DEFAULT 0,
    rank INTEGER DEFAULT NULL,
    calculated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (player_uuid, leaderboard_type)
);

CREATE INDEX IF NOT EXISTS idx_leaderboard_type_rank ON quest_leaderboard_entries(leaderboard_type, rank);
CREATE INDEX IF NOT EXISTS idx_leaderboard_value ON quest_leaderboard_entries(leaderboard_type, value DESC);

-- Leaderboard cache (performance optimization)
CREATE TABLE IF NOT EXISTS quest_leaderboard_cache (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    leaderboard_type TEXT NOT NULL UNIQUE,
    cache_data TEXT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cache_expires ON quest_leaderboard_cache(expires_at);
```

### Implementation Tasks (Ordered by Dependencies)

```yaml
Task 1: CREATE src/main/resources/migrations/V2_add_journal_tables.sql
  - IMPLEMENT: Journal table migration script
  - FOLLOW pattern: schema/mysql.sql and schema/sqlite.sql structure
  - NAMING: V{version}_descriptive_name.sql (Flyway-compatible)
  - PLACEMENT: src/main/resources/migrations/
  - VALIDATION: Test migration on both MySQL and SQLite databases

Task 2: CREATE src/main/java/org/fourz/RVNKQuests/journal/JournalEntry.java
  - IMPLEMENT: Immutable journal entry model (Java Record)
  - FOLLOW pattern: data/dto/ObjectiveDTO.java (DTO pattern)
  - NAMING: JournalEntry record with UUID, timestamps, quest data
  - DEPENDENCIES: None (pure data model)
  - PLACEMENT: src/main/java/org/fourz/RVNKQuests/journal/

Task 3: CREATE src/main/java/org/fourz/RVNKQuests/journal/QuestStatistics.java
  - IMPLEMENT: Statistics aggregation DTO (Java Record)
  - FOLLOW pattern: data/dto/ObjectiveDTO.java
  - NAMING: QuestStatistics record with aggregated player data
  - DEPENDENCIES: None (pure data model)
  - PLACEMENT: src/main/java/org/fourz/RVNKQuests/journal/

Task 4: CREATE src/main/java/org/fourz/RVNKQuests/data/repository/JournalRepository.java
  - IMPLEMENT: Async journal data access with CompletableFuture
  - FOLLOW pattern: data/DatabaseManager.java (async operations, connection pooling)
  - NAMING: JournalRepository class with getJournalEntries(), saveJournalEntry(), getStatistics()
  - DEPENDENCIES: Import JournalEntry from Task 2, QuestStatistics from Task 3
  - PLACEMENT: src/main/java/org/fourz/RVNKQuests/data/repository/

Task 5: CREATE src/main/java/org/fourz/RVNKQuests/journal/JournalService.java
  - IMPLEMENT: Business logic for journal operations
  - FOLLOW pattern: quest/QuestManager.java (service layer pattern)
  - NAMING: JournalService class with getPlayerJournal(), recordQuestCompletion(), calculateStatistics()
  - DEPENDENCIES: JournalRepository from Task 4
  - PLACEMENT: src/main/java/org/fourz/RVNKQuests/journal/

Task 6: CREATE src/main/java/org/fourz/RVNKQuests/category/QuestCategory.java
  - IMPLEMENT: Category enumeration
  - FOLLOW pattern: Standard Java enum pattern
  - NAMING: QuestCategory enum (MAIN_STORY, SIDE_QUEST, DAILY, WEEKLY, EVENT, CHALLENGE)
  - DEPENDENCIES: None
  - PLACEMENT: src/main/java/org/fourz/RVNKQuests/category/

Task 7: CREATE src/main/java/org/fourz/RVNKQuests/category/QuestTag.java
  - IMPLEMENT: Tag model (Java Record)
  - FOLLOW pattern: data/dto/ObjectiveDTO.java
  - NAMING: QuestTag record with name, description, color
  - DEPENDENCIES: None
  - PLACEMENT: src/main/java/org/fourz/RVNKQuests/category/

Task 8: CREATE src/main/java/org/fourz/RVNKQuests/data/repository/CategoryRepository.java
  - IMPLEMENT: Async category and tag data access
  - FOLLOW pattern: data/DatabaseManager.java
  - NAMING: CategoryRepository with getCategoriesForQuest(), getTagsForQuest(), assignCategory(), assignTag()
  - DEPENDENCIES: QuestCategory from Task 6, QuestTag from Task 7
  - PLACEMENT: src/main/java/org/fourz/RVNKQuests/data/repository/

Task 9: CREATE src/main/java/org/fourz/RVNKQuests/category/CategoryService.java
  - IMPLEMENT: Category filtering and search logic
  - FOLLOW pattern: quest/QuestManager.java
  - NAMING: CategoryService with filterByCategory(), filterByTag(), searchQuests()
  - DEPENDENCIES: CategoryRepository from Task 8
  - PLACEMENT: src/main/java/org/fourz/RVNKQuests/category/

Task 10: CREATE src/main/java/org/fourz/RVNKQuests/leaderboard/LeaderboardType.java
  - IMPLEMENT: Leaderboard type enumeration
  - FOLLOW pattern: Standard Java enum
  - NAMING: LeaderboardType enum (MOST_COMPLETED, FASTEST_AVERAGE, CURRENT_STREAK)
  - DEPENDENCIES: None
  - PLACEMENT: src/main/java/org/fourz/RVNKQuests/leaderboard/

Task 11: CREATE src/main/java/org/fourz/RVNKQuests/leaderboard/LeaderboardEntry.java
  - IMPLEMENT: Leaderboard entry model (Java Record implementing Comparable)
  - FOLLOW pattern: data/dto/ObjectiveDTO.java
  - NAMING: LeaderboardEntry record with rank, playerUuid, value
  - DEPENDENCIES: None
  - PLACEMENT: src/main/java/org/fourz/RVNKQuests/leaderboard/

Task 12: CREATE src/main/java/org/fourz/RVNKQuests/data/repository/LeaderboardRepository.java
  - IMPLEMENT: Async leaderboard data access with caching
  - FOLLOW pattern: data/DatabaseManager.java
  - NAMING: LeaderboardRepository with getLeaderboard(), updateLeaderboard(), getCachedLeaderboard()
  - DEPENDENCIES: LeaderboardEntry from Task 11, LeaderboardType from Task 10
  - PLACEMENT: src/main/java/org/fourz/RVNKQuests/data/repository/

Task 13: CREATE src/main/java/org/fourz/RVNKQuests/leaderboard/LeaderboardCache.java
  - IMPLEMENT: In-memory cache with 5-minute TTL
  - FOLLOW pattern: Use ConcurrentHashMap for thread-safety
  - NAMING: LeaderboardCache with get(), put(), invalidate(), isExpired()
  - DEPENDENCIES: LeaderboardEntry from Task 11
  - PLACEMENT: src/main/java/org/fourz/RVNKQuests/leaderboard/

Task 14: CREATE src/main/java/org/fourz/RVNKQuests/leaderboard/LeaderboardService.java
  - IMPLEMENT: Leaderboard calculation and ranking logic
  - FOLLOW pattern: quest/QuestManager.java
  - NAMING: LeaderboardService with getLeaderboard(), refreshLeaderboard(), calculateRankings()
  - DEPENDENCIES: LeaderboardRepository from Task 12, LeaderboardCache from Task 13
  - PLACEMENT: src/main/java/org/fourz/RVNKQuests/leaderboard/

Task 15: CREATE src/main/java/org/fourz/RVNKQuests/integration/RVNKLoreIntegration.java
  - IMPLEMENT: Soft dependency integration with RVNKLore plugin
  - FOLLOW pattern: Check plugin existence with Bukkit.getPluginManager().getPlugin()
  - NAMING: RVNKLoreIntegration with isAvailable(), unlockLore(), getLoreForQuest()
  - DEPENDENCIES: RVNKLore plugin API (soft dependency)
  - PLACEMENT: src/main/java/org/fourz/RVNKQuests/integration/

Task 16: CREATE src/main/java/org/fourz/RVNKQuests/command/QuestJournalSubCommand.java
  - IMPLEMENT: /quest journal [player] command with console compatibility
  - FOLLOW pattern: command/QuestStateSubCommand.java (BaseSubCommand, CommandSender)
  - NAMING: QuestJournalSubCommand extends BaseSubCommand
  - DEPENDENCIES: JournalService from Task 5
  - PLACEMENT: src/main/java/org/fourz/RVNKQuests/command/
  - PERMISSIONS: rvnkquests.journal.self (view own), rvnkquests.journal.other (view others)

Task 17: CREATE src/main/java/org/fourz/RVNKQuests/command/QuestProgressSubCommand.java
  - IMPLEMENT: /quest progress <quest_id> [player] command
  - FOLLOW pattern: command/QuestStateSubCommand.java
  - NAMING: QuestProgressSubCommand extends BaseSubCommand
  - DEPENDENCIES: JournalService from Task 5
  - PLACEMENT: src/main/java/org/fourz/RVNKQuests/command/
  - PERMISSIONS: rvnkquests.progress.self, rvnkquests.progress.other

Task 18: CREATE src/main/java/org/fourz/RVNKQuests/command/QuestAbandonSubCommand.java
  - IMPLEMENT: /quest abandon <quest_id> [player] command with confirmation
  - FOLLOW pattern: command/QuestStateSubCommand.java
  - NAMING: QuestAbandonSubCommand extends BaseSubCommand
  - DEPENDENCIES: quest/QuestManager.java (state transitions)
  - PLACEMENT: src/main/java/org/fourz/RVNKQuests/command/
  - PERMISSIONS: rvnkquests.abandon.self, rvnkquests.abandon.other

Task 19: CREATE src/main/java/org/fourz/RVNKQuests/command/QuestLeaderboardSubCommand.java
  - IMPLEMENT: /quest leaderboard [type] command
  - FOLLOW pattern: command/QuestStateSubCommand.java
  - NAMING: QuestLeaderboardSubCommand extends BaseSubCommand
  - DEPENDENCIES: LeaderboardService from Task 14
  - PLACEMENT: src/main/java/org/fourz/RVNKQuests/command/
  - PERMISSIONS: rvnkquests.leaderboard.view

Task 20: MODIFY src/main/java/org/fourz/RVNKQuests/command/CommandManager.java
  - INTEGRATE: Register new subcommands (journal, progress, abandon, leaderboard)
  - FIND pattern: Existing subcommand registrations
  - ADD: Import new subcommands and register in command map
  - PRESERVE: Existing command registrations and routing logic

Task 21: MODIFY src/main/resources/plugin.yml
  - INTEGRATE: Add permissions for new commands
  - FIND pattern: Existing permission nodes
  - ADD: Permission definitions with default values and descriptions
  - PRESERVE: Existing permissions and command definitions

Task 22: CREATE src/test/java/org/fourz/RVNKQuests/journal/JournalServiceTest.java
  - IMPLEMENT: Unit tests for JournalService (happy path, edge cases, error handling)
  - FOLLOW pattern: Use JUnit 5, Mockito for mocking repository
  - NAMING: test_{method}_{scenario} function naming
  - COVERAGE: All public methods with positive and negative test cases
  - PLACEMENT: src/test/java/org/fourz/RVNKQuests/journal/

Task 23: CREATE src/test/java/org/fourz/RVNKQuests/category/CategoryServiceTest.java
  - IMPLEMENT: Unit tests for CategoryService
  - FOLLOW pattern: JUnit 5, Mockito
  - NAMING: test_{method}_{scenario}
  - COVERAGE: Category filtering, tag assignments, search functionality
  - PLACEMENT: src/test/java/org/fourz/RVNKQuests/category/

Task 24: CREATE src/test/java/org/fourz/RVNKQuests/leaderboard/LeaderboardServiceTest.java
  - IMPLEMENT: Unit tests for LeaderboardService
  - FOLLOW pattern: JUnit 5, Mockito
  - NAMING: test_{method}_{scenario}
  - COVERAGE: Ranking calculations, cache behavior, concurrent access
  - PLACEMENT: src/test/java/org/fourz/RVNKQuests/leaderboard/

Task 25: CREATE src/test/java/org/fourz/RVNKQuests/integration/RVNKLoreIntegrationTest.java
  - IMPLEMENT: Integration tests for RVNKLore cross-plugin communication
  - FOLLOW pattern: JUnit 5, mock Bukkit plugin manager
  - NAMING: test_{method}_{scenario}
  - COVERAGE: Plugin detection, lore unlocking, fallback behavior
  - PLACEMENT: src/test/java/org/fourz/RVNKQuests/integration/
```

### Implementation Patterns & Key Details

```java
// Console-Compatible Command Pattern
public class QuestJournalSubCommand extends BaseSubCommand {
    private final JournalService journalService;

    public QuestJournalSubCommand(RVNKQuests plugin) {
        super(plugin, "journal", "View quest journal");
        this.journalService = plugin.getJournalService();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // PATTERN: Console compatibility - use CommandSender, not Player
        Player target;
        if (args.length == 0) {
            // No player specified - sender must be player
            if (sender instanceof Player) {
                target = (Player) sender;
            } else {
                sender.sendMessage("§cConsole must specify player: /quest journal <player>");
                return false;
            }
        } else {
            // Player specified - can be executed from console
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found: " + args[0]);
                return false;
            }
            // GOTCHA: Check permission for viewing other player's journal
            if (!sender.equals(target) && !sender.hasPermission("rvnkquests.journal.other")) {
                sender.sendMessage("§cYou don't have permission to view other players' journals");
                return false;
            }
        }

        // PATTERN: Async database operation to avoid blocking main thread
        journalService.getPlayerJournal(target.getUniqueId())
            .thenAccept(journal -> {
                // CRITICAL: Schedule back to main thread for player interaction
                Bukkit.getScheduler().runTask(plugin, () -> {
                    displayJournal(sender, target, journal);
                });
            })
            .exceptionally(ex -> {
                sender.sendMessage("§cError loading journal: " + ex.getMessage());
                return null;
            });

        return true;
    }
}

// Async Repository Pattern with HikariCP
public class JournalRepository {
    private final DatabaseManager dbManager;
    private final ExecutorService executor;

    public CompletableFuture<List<JournalEntry>> getJournalEntries(UUID playerUuid) {
        // PATTERN: Use DatabaseManager's executor for async operations
        return CompletableFuture.supplyAsync(() -> {
            String sql = dbManager.isMySQL()
                ? "SELECT * FROM quest_journal_entries WHERE player_uuid = ? ORDER BY completed_at DESC"
                : "SELECT * FROM quest_journal_entries WHERE player_uuid = ? ORDER BY completed_at DESC";

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    List<JournalEntry> entries = new ArrayList<>();
                    while (rs.next()) {
                        // GOTCHA: MySQL uses TIMESTAMP, SQLite uses TEXT for timestamps
                        Instant startedAt = dbManager.isMySQL()
                            ? rs.getTimestamp("started_at").toInstant()
                            : Instant.parse(rs.getString("started_at"));

                        entries.add(new JournalEntry(
                            playerUuid,
                            rs.getString("quest_id"),
                            rs.getString("quest_state"),
                            startedAt,
                            // ... other fields
                        ));
                    }
                    return entries;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to fetch journal entries", e);
            }
        }, dbManager.getExecutor());
    }
}

// RVNKLore Soft Dependency Pattern
public class RVNKLoreIntegration {
    private final RVNKQuests plugin;
    private Plugin lorePlugin;

    public RVNKLoreIntegration(RVNKQuests plugin) {
        this.plugin = plugin;
        // PATTERN: Soft dependency check - don't fail if plugin not present
        this.lorePlugin = Bukkit.getPluginManager().getPlugin("RVNKLore");
    }

    public boolean isAvailable() {
        return lorePlugin != null && lorePlugin.isEnabled();
    }

    public CompletableFuture<Boolean> unlockLore(UUID playerUuid, String loreId) {
        if (!isAvailable()) {
            // GOTCHA: Gracefully handle missing dependency
            return CompletableFuture.completedFuture(false);
        }

        // CRITICAL: Use RVNKLore API (when available)
        // Example: return ((RVNKLore) lorePlugin).unlockLore(playerUuid, loreId);
        return CompletableFuture.completedFuture(true);
    }
}

// Leaderboard Cache with TTL
public class LeaderboardCache {
    private final ConcurrentHashMap<LeaderboardType, CachedLeaderboard> cache;
    private static final long CACHE_TTL_SECONDS = 300; // 5 minutes

    record CachedLeaderboard(List<LeaderboardEntry> entries, Instant cachedAt) {
        boolean isExpired() {
            return Instant.now().isAfter(cachedAt.plusSeconds(CACHE_TTL_SECONDS));
        }
    }

    public Optional<List<LeaderboardEntry>> get(LeaderboardType type) {
        CachedLeaderboard cached = cache.get(type);
        if (cached != null && !cached.isExpired()) {
            return Optional.of(cached.entries());
        }
        return Optional.empty();
    }

    // PATTERN: Thread-safe cache updates
    public void put(LeaderboardType type, List<LeaderboardEntry> entries) {
        cache.put(type, new CachedLeaderboard(entries, Instant.now()));
    }
}
```

### Integration Points

```yaml
DATABASE:
  - migration: "Add quest_journal_entries, quest_categories, quest_tags tables"
  - migration: "Add quest_tag_assignments, quest_leaderboard_entries tables"
  - migration: "Add quest_leaderboard_cache table"
  - index: "CREATE INDEX idx_journal_player ON quest_journal_entries(player_uuid)"
  - index: "CREATE INDEX idx_leaderboard_type_rank ON quest_leaderboard_entries(leaderboard_type, rank)"

PLUGIN_YML:
  - add permissions:
      rvnkquests.journal.self:
        description: "View own quest journal"
        default: true
      rvnkquests.journal.other:
        description: "View other players' journals"
        default: op
      rvnkquests.progress.self:
        description: "View own quest progress"
        default: true
      rvnkquests.progress.other:
        description: "View other players' progress"
        default: op
      rvnkquests.abandon.self:
        description: "Abandon own quests"
        default: true
      rvnkquests.abandon.other:
        description: "Abandon other players' quests"
        default: op
      rvnkquests.leaderboard.view:
        description: "View quest leaderboards"
        default: true

COMMAND_MANAGER:
  - add to: src/main/java/org/fourz/RVNKQuests/command/CommandManager.java
  - pattern: "registerSubCommand(new QuestJournalSubCommand(plugin));"
  - pattern: "registerSubCommand(new QuestProgressSubCommand(plugin));"
  - pattern: "registerSubCommand(new QuestAbandonSubCommand(plugin));"
  - pattern: "registerSubCommand(new QuestLeaderboardSubCommand(plugin));"

RVNKLORE_INTEGRATION:
  - add to: pom.xml (soft dependency)
  - pattern: |
      <dependency>
          <groupId>org.fourz</groupId>
          <artifactId>rvnklore</artifactId>
          <version>1.0-SNAPSHOT</version>
          <scope>provided</scope>
          <optional>true</optional>
      </dependency>
  - add to: plugin.yml
  - pattern: |
      softdepend:
        - RVNKLore
```

## Validation Loop

### Level 1: Syntax & Style (Immediate Feedback)

```bash
# Run after each Java file creation
mvn validate                          # Validate POM and dependencies
mvn compile -DskipTests               # Compile Java sources only

# Full build validation
mvn clean package -DskipTests         # Build without running tests

# Expected: Zero compilation errors. If errors exist, fix before proceeding.
```

### Level 2: Unit Tests (Component Validation)

```bash
# Test each service as created
mvn test -Dtest=JournalServiceTest
mvn test -Dtest=CategoryServiceTest
mvn test -Dtest=LeaderboardServiceTest
mvn test -Dtest=RVNKLoreIntegrationTest

# Full test suite
mvn test

# Coverage validation (if coverage plugin configured)
mvn verify

# Expected: All tests pass, coverage ≥ 70% for new components
```

### Level 3: Integration Testing (System Validation)

```bash
# Build plugin JAR
mvn clean package

# Deploy to local test server (MCSS)
# Using rvnkdev-mcp-server tools
/rvnkdev-deploy b2bc4d7e full

# Check console for startup errors
/rvnkdev-query b2bc4d7e errors

# Verify plugin loads
/rvnkdev-query b2bc4d7e plugin RVNKQuests

# Manual command testing (in-game or console)
# Test journal command
/quest journal
/quest journal PlayerName

# Test progress command
/quest progress ancient_guardian
/quest progress ancient_guardian PlayerName

# Test leaderboard
/quest leaderboard most_completed
/quest leaderboard fastest_average

# Test abandon command
/quest abandon ancient_guardian
/quest abandon ancient_guardian PlayerName

# Database validation
# Connect to MySQL/SQLite and verify tables exist
mysql -u root -p minecraft -e "SHOW TABLES LIKE 'quest_%';"
sqlite3 data/quests.db ".tables"

# Verify schema structure
mysql -u root -p minecraft -e "DESCRIBE quest_journal_entries;"

# Expected: All commands work from console, database schema correct, no TPS drops
```

### Level 4: Performance & Production Validation

```bash
# Performance testing with TPS monitoring
# 1. Start test server with TPS monitor plugin
# 2. Execute leaderboard refresh under load
/quest leaderboard most_completed  # Should complete in ≤ 100ms

# 3. Monitor TPS during leaderboard cache refresh
# Expected TPS ≥ 19.5

# Database query performance testing
# Run EXPLAIN on slow queries
mysql -u root -p minecraft -e "
  EXPLAIN SELECT * FROM quest_leaderboard_entries
  WHERE leaderboard_type = 'MOST_COMPLETED'
  ORDER BY rank ASC LIMIT 10;
"

# Verify indexes are used
# Expected: Using index on leaderboard_type, rank

# Concurrent access testing
# 1. Have multiple players execute journal commands simultaneously
# 2. Monitor for deadlocks or connection pool exhaustion
/rvnkdev-query b2bc4d7e errors

# RVNKLore integration testing
# 1. Install RVNKLore plugin on test server
# 2. Complete quest that should unlock lore
# 3. Verify lore entry unlocked in RVNKLore
/lore list PlayerName

# 4. Remove RVNKLore plugin
# 5. Verify RVNKQuests still functions (soft dependency)
/quest journal

# Security validation
# 1. Test permission nodes work correctly
# 2. Verify console cannot execute player-only operations without target
# 3. Test SQL injection protection in commands
/quest journal '; DROP TABLE quest_progress; --

# Expected: All security checks pass, no SQL injection possible
```

## Final Validation Checklist

### Technical Validation

- [ ] All 4 validation levels completed successfully
- [ ] All tests pass: `mvn test`
- [ ] No compilation errors: `mvn clean package`
- [ ] Database schema validated on MySQL and SQLite
- [ ] Plugin loads without errors on test server
- [ ] Console commands work with `[player]` argument

### Feature Validation

- [ ] Quest journal displays accurate history
- [ ] Categories filter quests correctly (Main Story, Daily, etc.)
- [ ] Tags filter quests correctly (PvE, Exploration, etc.)
- [ ] Leaderboards show top 10 players with correct rankings
- [ ] Leaderboard cache refreshes every 5 minutes
- [ ] RVNKLore integration unlocks lore on quest completion
- [ ] RVNKQuests works without RVNKLore (soft dependency)
- [ ] All commands work from console with player argument

### Performance Validation

- [ ] Leaderboard queries ≤ 100ms (cached)
- [ ] Journal page load ≤ 50ms
- [ ] Tag filtering ≤ 200ms for 1000+ quests
- [ ] No TPS drops during leaderboard refresh (TPS ≥ 19.5)
- [ ] Connection pool does not exhaust under load

### Code Quality Validation

- [ ] Follows RVNKQuests coding standards
- [ ] Uses async patterns (CompletableFuture) for database
- [ ] Console compatibility implemented correctly (CommandSender)
- [ ] Test coverage ≥ 70% for new components
- [ ] No hardcoded credentials or sensitive data
- [ ] LogManager used for all logging (not Debug class)

### Security Validation

- [ ] Permission nodes work correctly
- [ ] SQL injection protection verified
- [ ] Command argument validation prevents exploits
- [ ] Console cannot bypass permission checks
- [ ] Player data access properly restricted

### Documentation & Deployment

- [ ] JavaDoc complete for all public methods
- [ ] ROADMAP.md updated with completion status
- [ ] Migration scripts documented
- [ ] Deployment tested on development server
- [ ] No regressions in existing quest functionality

---

## Anti-Patterns to Avoid

- ❌ Don't block main thread with database operations - always use CompletableFuture
- ❌ Don't use Player type for console commands - use CommandSender interface
- ❌ Don't assume RVNKLore exists - always check soft dependency availability
- ❌ Don't hardcode leaderboard refresh interval - make it configurable
- ❌ Don't ignore SQLite vs MySQL differences in schema and queries
- ❌ Don't skip migration scripts - database changes must be versioned
- ❌ Don't cache leaderboards indefinitely - implement TTL expiration
- ❌ Don't expose internal quest state in error messages - sanitize output
- ❌ Don't allow quest abandonment without confirmation - prevent accidental data loss
- ❌ Don't forget to index frequently queried columns (player_uuid, quest_id)

---

## Success Metrics

**Feature Completeness**:
- 5 major features implemented (Journal, Categories, Leaderboards, Lore, Commands)
- 4 new commands with console compatibility
- 6 new database tables with migrations

**Quality Metrics**:
- Test coverage ≥ 70% for new code
- Zero security vulnerabilities
- Zero data loss during migrations
- Performance targets met

**Integration Metrics**:
- RVNKLore integration functional
- Existing quests continue working
- No breaking changes to public API

**Documentation**:
- Complete JavaDoc for all public APIs
- Migration guide for server admins
- Command usage examples in ROADMAP.md

---

**Estimated Effort**: 80-120 hours (2-3 weeks for experienced developer)
**Priority**: High - Foundational features for quest engagement
**Risk Level**: Medium - Database migrations and cross-plugin integration require careful testing
