# RVNKQuests: AI Assistant Instructions

@import ../../.claude/rules/archon-workflow.md
@import ../../.claude/rules/java-plugin-build.md

---

## Project Overview

**RVNKQuests** is a dynamic narrative quest system for Bukkit/Spigot/Paper servers. It provides event-driven quest progression, location and trigger-based quest activation, multi-objective quests with AND/OR/XOR logic, quest chains, a repeatable quest system with cooldowns, a player journal, leaderboards, and a category/tag system for organization. RVNKLore is a soft dependency for quest item lore book generation.

## Build Commands

```bash
# Build plugin JAR
mvn clean package

# Build without tests (faster)
mvn clean package -DskipTests

# Validate POM and dependencies
mvn validate

# Check dependency tree
mvn dependency:tree
```

**Output**: `target/RVNKQuests-1.1.0.jar`

**Current Status**: Active development — For plugin status and history, search Graph Memory: `search_nodes("RVNKQuests")`

## Task Management

**GitHub Issues (primary)**: `gh issue list --repo fourz/Ravenkraft-Dev --label "board:rvnkquests" --json number,title,labels`

**Status flow**: `open` → in progress (comment) → `closed`

## Remote Testing Workflow

Use `/rvnkdev-deploy` and `/rvnkdev-query` skills for remote server testing:

```bash
# Full deployment cycle (build locally first)
mvn clean package
/rvnkdev-deploy <server_id> full

# Query console for errors
/rvnkdev-query <server_id> errors

# Check plugin startup logs
/rvnkdev-query <server_id> plugin RVNKQuests

# Quick config iteration (no restart)
/rvnkdev-deploy <server_id> reload-only
```

Use `mcp__ravencast-mcp__find_servers` to look up current server IDs.

## Architecture

### Core Class Structure

```
org.fourz.RVNKQuests
├── RVNKQuests.java              # Main plugin class, lifecycle management, RVNKCore registration
├── command/
│   ├── CommandManager.java      # Command registration (singleton)
│   ├── RVNKCommand.java         # Main /rvnkquests command dispatcher
│   ├── BaseCommand.java         # Base command abstraction
│   ├── SubCommand.java          # Subcommand interface
│   ├── QuestStartSubCommand.java
│   ├── QuestAbandonSubCommand.java
│   ├── QuestCompleteSubCommand.java
│   ├── QuestListSubCommand.java
│   ├── QuestStateSubCommand.java
│   ├── QuestSetStateSubCommand.java
│   ├── QuestJournalSubCommand.java  # list/view/remove + console support
│   ├── QuestLeaderboardSubCommand.java
│   ├── QuestMenuSubCommand.java
│   ├── QuestTriggerSubCommand.java
│   ├── QuestMobsSubCommand.java     # list/kill — includes GenericMobSpawnTrigger mobs
│   ├── QuestValidateSubCommand.java
│   ├── QuestConfigSubCommand.java
│   ├── QuestReloadSubCommand.java
│   ├── QuestResetSubCommand.java
│   ├── ProgressSubCommand.java
│   └── SeedSubCommand.java
├── config/
│   └── ConfigManager.java       # YAML config management
├── data/
│   ├── DatabaseManager.java     # Database lifecycle (MySQL primary, YAML fallback)
│   ├── IQuestRepository.java    # Quest definition repository interface
│   ├── IQuestProgressRepository.java  # Player progress repository interface
│   ├── QuestProgressYamlRepository.java  # YAML fallback implementation
│   ├── QuestDefinitionSeeder.java  # Seeds data-driven quest definitions from DB
│   ├── dto/
│   │   ├── QuestDTO.java            # Includes metadata map for state_mapping
│   │   ├── QuestProgressDTO.java
│   │   ├── ObjectiveDTO.java
│   │   ├── EnhancedObjectiveDTO.java    # AND/OR/XOR objective logic
│   │   ├── ObjectiveGroup.java
│   │   ├── ObjectiveCondition.java
│   │   ├── ObjectiveType.java
│   │   ├── TriggerType.java            # Enum: MOB_SPAWN, STRUCTURE_INTERACT, ENTITY_PROXIMITY, ITEM_DISCOVERY
│   │   ├── RewardDTO.java
│   │   ├── RewardType.java
│   │   ├── QuestChainDTO.java
│   │   ├── QuestPrerequisite.java
│   │   ├── JournalEntryDTO.java         # JournalAction enum (7 types), BIGINT epoch millis
│   │   ├── QuestCategoryDTO.java
│   │   ├── QuestTagDTO.java
│   │   ├── LeaderboardEntryDTO.java
│   │   ├── QuestObjectiveProgressDTO.java
│   │   ├── QuestRewardClaimedDTO.java
│   │   ├── QuestRepeatConfigDTO.java
│   │   └── PlayerQuestRepeatDTO.java
│   └── repository/
│       ├── IPreferenceRepository.java
│       ├── PreferenceRepositoryImpl.java
│       ├── IJournalRepository.java
│       ├── JournalRepositoryImpl.java   # quest_journal_entries table (MySQL BIGINT epoch millis)
│       ├── ICategoryRepository.java
│       ├── ITagRepository.java
│       └── ILeaderboardRepository.java
├── factory/
│   └── QuestComponentFactory.java   # Creates listeners from ObjectiveType + metadata
├── service/
│   ├── IQuestService.java            # Quest definitions and lifecycle
│   ├── IQuestProgressService.java    # Player quest state and progress
│   ├── IQuestDatabaseService.java    # Database access service
│   ├── IRewardService.java           # Reward delivery
│   ├── IQuestChainService.java       # Quest chain management
│   ├── IObjectiveService.java        # Objective management
│   ├── IJournalService.java          # Quest history and statistics (7 JournalAction types)
│   ├── IRepeatableQuestService.java  # Repeatability and cooldowns
│   ├── INotificationService.java     # Quest notifications
│   ├── IPlayerQuestService.java      # Player-facing quest operations
│   ├── QuestProgressServiceImpl.java
│   ├── RewardServiceImpl.java
│   ├── QuestChainServiceImpl.java
│   ├── ObjectiveServiceImpl.java
│   ├── JournalServiceImpl.java
│   ├── RepeatableQuestServiceImpl.java
│   ├── NotificationServiceImpl.java
│   ├── ConditionEvaluator.java       # AND/OR/XOR objective evaluation
│   ├── RewardProcessor.java          # Reward dispatch
│   └── reward/
│       ├── ItemRewardProcessor.java
│       ├── ExperienceRewardProcessor.java
│       ├── CurrencyRewardProcessor.java
│       ├── CommandRewardProcessor.java
│       ├── LoreRewardProcessor.java
│       ├── PermissionRewardProcessor.java
│       ├── QuestUnlockRewardProcessor.java
│       └── CustomRewardProcessor.java
├── quest/
│   ├── Quest.java               # Quest interface
│   ├── AbstractQuest.java       # Base class with per-player stateCache (ConcurrentHashMap)
│   ├── DataDrivenQuest.java     # Extends AbstractQuest, reads state_mapping from QuestDTO metadata
│   ├── QuestManager.java        # Quest registration, state tracking, event handling
│   ├── QuestState.java          # State enum (6 states)
│   └── QuestPiglinFarFromHome.java  # Legacy hardcoded quest implementation
├── objective/
│   ├── ListenerLonePiglin.java
│   ├── ListenerPreventMobInfighting.java
│   ├── ListenerPreventPortalUse.java
│   ├── ListenerQuestBookPlacer.java
│   └── generic/                     # Generic objective components (data-driven)
│       ├── GenericKillObjective.java
│       ├── GenericReachObjective.java
│       ├── GenericEscortObjective.java
│       ├── GenericEncounterObjective.java
│       ├── GenericInteractObjective.java
│       ├── GenericDiscoverObjective.java
│       └── GenericCollectObjective.java
├── trigger/
│   ├── ListenerQuestPillarStart.java
│   └── generic/                     # Generic trigger components (data-driven)
│       ├── GenericMobSpawnTrigger.java       # Name+type+world mob detection, safe spawn, beg mechanic
│       ├── GenericStructureInteractTrigger.java
│       ├── GenericEntityProximityTrigger.java
│       └── GenericItemDiscoveryTrigger.java
├── event/
│   └── PlayerJoinQuitListener.java  # Progress load/save, stateCache preload/evict on join/quit
├── ui/
│   ├── QuestMenuManager.java        # Inventory GUI for quest browsing
│   ├── QuestDetailMenu.java         # Quest detail view
│   └── QuestMenuListener.java       # GUI interaction handling
├── category/
│   ├── QuestCategory.java
│   ├── QuestTag.java
│   ├── ICategoryService.java
│   └── CategoryServiceImpl.java
├── leaderboard/
│   ├── LeaderboardType.java
│   └── LeaderboardEntry.java
├── journal/
│   └── QuestStatistics.java
├── notification/
│   ├── NotificationType.java
│   └── NotificationChannel.java
├── integration/
│   ├── ILoreIntegration.java
│   └── LoreIntegrationImpl.java     # Soft integration with RVNKLore
├── lore/
│   ├── LoreDatabase.java            # Optional narrative content store
│   └── LoreDiscovery.java
├── reward/
│   ├── QuestLoot.java
│   └── QuestItem.java               # Quest item generation (uses LoreIntegration)
└── util/
    ├── ConfigKeys.java
    ├── NameGenerator.java
    ├── FollowingMob.java
    ├── EntityFollow.java
    ├── EnvironmentEffects.java
    └── NMSUtil.java
```

### Key Patterns

**Manager Lifecycle**: All managers implement `initialize()` and `shutdown()`/`cleanup()` pattern
**Service Pattern**: Core services implement service interfaces registered with RVNKCore ServiceRegistry
**Repository Pattern**: Data access through repository interfaces with DTO layer
**Subcommand Pattern**: RVNKCommand dispatches to Quest*SubCommand implementations
**Quest State Machine**: 6-state lifecycle (see below)
**Fallback Pattern**: MySQL primary with YAML fallback via QuestProgressYamlRepository (not SQLite)
**Generic Quest Engine**: DataDrivenQuest extends AbstractQuest, reads `state_mapping` from QuestDTO metadata; QuestComponentFactory creates trigger/objective listeners from ObjectiveType + metadata; QuestDefinitionSeeder seeds quest definitions from database
**Per-Player State Cache**: AbstractQuest.stateCache (ConcurrentHashMap) with `preloadStateForPlayer()` on join, `evictStateForPlayer()` on quit, async DB load with NOT_STARTED default. `quest reset` evicts cache immediately — DB and memory are always consistent after reset.
**COMPLETED state side-effects**: All completion logic (rewards via `onComplete()`, notifications, broadcast, `QuestCompleteEvent`) fires inside `AbstractQuest.advanceStateForPlayer()` when `newState == COMPLETED`. This ensures rewards and events trigger regardless of whether completion came from a trigger component or from `complete(Player)`. Do NOT put reward delivery inside `complete(Player)` — it will never fire for data-driven trigger completions.
**Trigger/Objective event hooks**: All location-based components (GenericLocationProximityTrigger, GenericReachObjective, GenericCollectObjective) hook both `PlayerMoveEvent` AND `PlayerTeleportEvent`. Adding a new location component must hook both. AFK+ may freeze PlayerMoveEvent for static players — see #1138.
**Mob Detection**: GenericMobSpawnTrigger scans for existing mobs by entity_type + custom_name + world; supports restart recovery and admin-placed mob adoption; config: `mob_detection.name_type_matching`, `mob_detection.scan_interval_ms`
**Safe Spawn**: `world.getHighestBlockYAt(spawnLoc) + 1` prevents mob suffocation in GenericMobSpawnTrigger
**Beg Mechanic**: `beg_on_attack` config with hit counting, knockback, and `beg_message` for quest mobs

### Quest State Machine

```
NOT_STARTED → TRIGGER_FOUND → QUEST_ACTIVE → OBJECTIVE_FOUND → COMPLETED
                                                               ↘
                                                           ABANDONED
```

States defined in `QuestState.java`:
- `NOT_STARTED` — Quest exists, player has not discovered it
- `TRIGGER_FOUND` — Player discovered the quest trigger (book, pillar, NPC, etc.)
- `QUEST_ACTIVE` — Player has accepted and is pursuing objectives
- `OBJECTIVE_FOUND` — Player has located the primary objective
- `COMPLETED` — All quest requirements fulfilled
- `ABANDONED` — Player abandoned the quest (can restart)

### Service Registration (RVNKCore Integration)

RVNKQuests uses reflection-based service registration to avoid hard dependencies on RVNKCore. The main plugin class registers eight services with the RVNKCore ServiceRegistry:

```java
// Services registered (if RVNKCore available):
- IQuestService          → QuestManager
- IQuestProgressService  → QuestProgressServiceImpl
- IQuestDatabaseService  → DatabaseManager
- IRewardService         → RewardServiceImpl
- IQuestChainService     → QuestChainServiceImpl
- IObjectiveService      → ObjectiveServiceImpl
- IJournalService        → JournalServiceImpl
- IRepeatableQuestService → RepeatableQuestServiceImpl
```

Registration occurs in `onEnable()` via `registerWithRVNKCore()` and cleanup in `onDisable()` via `unregisterFromRVNKCore()`.

Additionally, 8 notification types are registered with `PlayerPreferencesService` under the `rvnkquests` namespace: `quest_start`, `quest_complete`, `quest_failed`, `objective_progress`, `objective_complete`, `quest_available`, `milestone`, `chain_progress`.

### Database System

```
Primary:   MySQL (configurable)
Fallback:  YAML via QuestProgressYamlRepository (automatic on consecutive DB failures)
Tracker:   FallbackTracker (from RVNKCore) monitors failure count and recovery
```

Note: RVNKQuests uses YAML as its fallback, not SQLite like some sibling plugins.

### Journal System

```
IJournalService → JournalRepositoryImpl → quest_journal_entries (MySQL, BIGINT epoch millis)
```

**JournalAction types** (JournalEntryDTO enum): `STARTED`, `COMPLETED`, `ABANDONED`, `OBJECTIVE_COMPLETE`, `FAILED`, `PATH_CHOSEN`, `REWARD_CLAIMED`

All 7 action types are wired into quest lifecycle events. `QuestJournalSubCommand` supports `list`, `view`, `remove` subcommands with console support.

### Performance

- 82 unit tests passing (QuestPerformanceTest suite)
- Quest lookup avg 86ns (target: <1ms)
- Concurrent lookup avg 1293ns

### RVNKLore Soft Integration

`LoreIntegrationImpl` provides optional integration with RVNKLore for quest item lore book generation. If RVNKLore is not present, quest items fall back to hardcoded descriptions. `QuestItem.populateFromLoreAsync()` seeds quest books asynchronously at startup.

## Command Formatting Standards

Use consistent message prefixes in command handlers:
- `&c▶` - Usage instructions
- `&6⚙` - Operations in progress
- `&a✓` - Success messages
- `&c✖` - Error messages
- `&e⚠` - Warnings
- `&7   ` - Additional tips

**Console/Debug**: No emojis, no color codes. Use `LogManager` from RVNKCore for all logging.

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| spigot-api | 1.21.4-R0.1-SNAPSHOT | Bukkit API |
| rvnkcore | 1.3.0-alpha | Shared services, ServiceRegistry, LogManager (provided) |
| snakeyaml | 2.0 | YAML configuration |
| gson | 2.8.9 | JSON serialization |

**Java Version**: 21 (compile target)

**Note**: RVNKCore uses `provided` scope — JAR must be in server plugins folder at runtime. The `lib/rvnkcore-*.jar` file is for IDE reference only.

## Documentation References

### Local Documentation
- [README.md](README.md) - Features, commands, configuration, API usage
- [ROADMAP.md](ROADMAP.md) - Development roadmap and milestone tracking
- **Graph Memory** — For plugin status and history: `search_nodes("RVNKQuests")`

### Parent Board Standards (Cross-cutting)
Documents on Ravenkraft Dev board (`4787f505-e92e-474d-ba54-f5ac7993ccfe`):
- [Coding Standards](../../docs/standard/coding-standards.md) - Java 17+ conventions
- [RVNKCore Integration](../../docs/standard/rvnkcore-integration.md) - ServiceRegistry usage patterns
- [Database Patterns](../../docs/standard/database-patterns.md) - Repository pattern, HikariCP

## Development Checklist

Before committing changes:
1. `mvn clean package` - Build succeeds
2. Test on local MCSS server or deploy to test server
3. Verify console output for errors: `/rvnkdev-query <id> errors`
4. Check plugin loads correctly: `/rvnkdev-query <id> plugin RVNKQuests`
5. Validate RVNKCore service registration in logs (8 services)
6. Test key commands: `/rvnkquests list`, `/rvnkquests start`, `/rvnkquests journal`
7. Verify database connectivity and fallback behavior if applicable
