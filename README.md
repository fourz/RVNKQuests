# RVNKQuests

A dynamic narrative quest system for Bukkit/Spigot servers that creates immersive, event-driven adventures for players. RVNKQuests is p## Building from Source

```bash
# Clone the repository
git clone https://github.com/fourz/RVNKQuests.git
cd RVNKQuests

# Build with Maven
mvn clean package

# The compiled JAR will be in target/RVNKQuests-[version].jar
```

### Development Environment

RVNKQuests uses RVNKDev MCP server for streamlined development workflow:

- **Build Plugin**: `Ctrl+Shift+P` → "Tasks: Run Task" → "Build Plugin"
- **Deploy to RVNK Test**: Use RVNKDev MCP batch file operations
- **Server Management**: Use RVNKDev MCP tools for restart/reload operations
- **Console Access**: Direct server console monitoring and command execution

For detailed workflow documentation, see [Development Workflow - MCP Integration](docs/development-workflow-mcp.md).

## Commands

### Player Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/quest list` | List all available quests | `rvnkquests.command.list` |
| `/quest start <quest_id>` | Start a quest | `rvnkquests.command.start` |
| `/quest abandon <quest_id>` | Abandon an active quest | `rvnkquests.command.abandon` |
| `/quest progress <quest_id>` | View quest progress | `rvnkquests.command.progress` |
| `/quest journal list` | List journal entries | `rvnkquests.command.journal` |
| `/quest journal view <id>` | View journal entry detail | `rvnkquests.command.journal` |
| `/quest menu` | Open quest browser GUI | `rvnkquests.command.menu` |
| `/quest leaderboard` | View quest leaderboards | `rvnkquests.command.leaderboard` |

### Staff Journal Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/quest journal assign <quest_id> <player>` | Assign a quest to a player (online or offline) | `rvnkquests.journal.staff` |
| `/quest journal unassign <quest_id> <player>` | Remove journal entries for a quest (progress preserved) | `rvnkquests.journal.staff` |
| `/quest journal view <quest_id> [player]` | View journal entries for a specific quest | `rvnkquests.journal.other` |
| `/quest journal remove <quest_id> [player]` | Delete all journal entries for a quest | `rvnkquests.journal.remove` |

`assign` sets the quest state to `QUEST_ACTIVE` via `QuestManager.startQuest()` and records a `STARTED` journal entry with "Assigned by `<staff>`" context. Guards against double-assign — fails if the quest is already in any non-`NOT_STARTED` state. Supports offline players via `Bukkit.getOfflinePlayer()`.

`unassign` clears journal entries via `journalService.clearQuestJournal()` but **does not** touch quest progress records. The quest state and objective counts are preserved in the database.

### Administrative Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/quest state <quest_id> <state>` | Change quest state manually | `rvnkquests.command.state` |
| `/quest complete <quest_id>` | Force-complete a quest | `rvnkquests.command.complete` |
| `/quest trigger <quest_id> [here\|around]` | Trigger quest at location | `rvnkquests.command.trigger` |
| `/quest mobs list` | List active quest mobs | `rvnkquests.command.mobs` |
| `/quest mobs kill` | Kill all active quest mobs | `rvnkquests.command.mobs` |
| `/quest validate` | Validate quest configurations | `rvnkquests.command.validate` |
| `/quest config` | View/edit quest config | `rvnkquests.command.config` |
| `/quest reload` | Reload plugin configuration | `rvnkquests.command.reload` |
| `/quest reload reset` | Reload config and reinitialize all quest listeners | `rvnkquests.admin.reload` |
| `/quest reload reseed` | Re-seed quest definitions from config then reinitialize | `rvnkquests.admin.reload` |
| `/quest reset <quest_id> [player]` | Reset quest progress | `rvnkquests.command.reset` |
| `/quest seed` | Seed quest definitions from DB | `rvnkquests.command.seed` |

### Command Examples

```bash
# List available quests
/quest list

# Start a quest and check progress
/quest start ashen_pilgrim
/quest progress ashen_pilgrim

# View your quest journal
/quest journal list

# Trigger the piglin quest around current location
/quest trigger piglin_far_from_home around

# List and manage quest mobs
/quest mobs list
/quest mobs kill

# Manually advance a quest state (debugging)
/quest state piglin_far_from_home QUEST_ACTIVE
```

## API Usage

### Creating Custom Quests

```java
public class MyCustomQuest implements Quest {
    private final RVNKQuests plugin;
    private final RVNKLogger logger;
    private QuestState currentState = QuestState.NOT_STARTED;
    
    public MyCustomQuest(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    @Override
    public List<Listener> createListenersForState(QuestState state) {
        List<Listener> listeners = new ArrayList<>();
        switch (state) {
            case NOT_STARTED:
                listeners.add(new MyTriggerListener(this));
                break;
            case QUEST_ACTIVE:
                listeners.add(new MyObjectiveListener(this));
                break;
        }
        return listeners;
    }
    
    // Implement other Quest interface methods...
}
```

### Registering Custom Quests

```java
public class MyQuestPlugin extends JavaPlugin {
    
    @Override
    public void onEnable() {
        // Get RVNKQuests instance
        RVNKQuests rvnkQuests = (RVNKQuests) getServer().getPluginManager().getPlugin("RVNKQuests");
        
        // Register custom quest
        rvnkQuests.getQuestManager().registerQuest(new MyCustomQuest(rvnkQuests));
    }
}
```

## Permissions

### Default Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `rvnkquests.command.list` | List available quests | `true` |
| `rvnkquests.command.start` | Start quests | `true` |
| `rvnkquests.command.abandon` | Abandon quests | `true` |
| `rvnkquests.command.progress` | View quest progress | `true` |
| `rvnkquests.command.journal` | Quest journal access | `true` |
| `rvnkquests.command.menu` | Quest browser GUI | `true` |
| `rvnkquests.command.leaderboard` | View leaderboards | `true` |
| `rvnkquests.command.state` | Modify quest states | `op` |
| `rvnkquests.command.complete` | Force-complete quests | `op` |
| `rvnkquests.command.trigger` | Trigger quests manually | `op` |
| `rvnkquests.command.mobs` | Manage quest mobs | `op` |
| `rvnkquests.command.validate` | Validate configurations | `op` |
| `rvnkquests.command.config` | View/edit config | `op` |
| `rvnkquests.command.reload` | Reload configuration | `op` |
| `rvnkquests.admin.reload` | Reload with reset/reseed options | `op` |
| `rvnkquests.command.reset` | Reset quest progress | `op` |
| `rvnkquests.command.seed` | Seed quest definitions | `op` |
| `rvnkquests.journal.staff` | Assign/unassign quests for players | `op` |
| `rvnkquests.journal.other` | View other players' journals | `op` |
| `rvnkquests.journal.remove` | Delete journal entries | `op` |

## Integration

### Supported Plugins

- **RVNKCore**: Enhanced data management and cross-plugin services
- **PlaceholderAPI**: Quest progress placeholders (planned)
- **Vault**: Economy integration for quest rewards (planned)
- **WorldGuard**: Region-based quest triggers (planned)

### Plugin Compatibility

RVNKQuests is designed to be compatible with most Bukkit/Spigot plugins. It uses standard Bukkit APIs and follows best practices for event handling and resource management.

## Support

### Documentation

- **[Command Reference](docs/commands.md)**: Complete command documentation
- **[Quest Development Guide](docs/quest-development.md)**: Creating custom quests
- **[API Documentation](docs/api/)**: Developer API reference

### Community

- **Issues**: [GitHub Issues](../../issues) for bug reports and feature requests
- **Discussions**: [GitHub Discussions](../../discussions) for community support
- **Discord**: Join the RVNK Discord server for real-time support

### Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details on:

- Code style and standards
- Development workflow
- Testing requirements
- Documentation guidelines

## Roadmap

See [ROADMAP.md](ROADMAP.md) for detailed development plans, including:

- LogManager migration completion
- RVNKCore integration enhancements
- New quest implementations
- Performance optimizations
- API expansion

## License

RVNKQuests is licensed under the [MIT License](LICENSE). See the LICENSE file for full details.

## Acknowledgments

- **Bukkit/Spigot Community**: For the excellent server platform and community support
- **RVNKCore Team**: For the shared service architecture and integration framework
- **Beta Testers**: Community members who help test and refine quest experiences
- **Contributors**: Everyone who contributes code, documentation, and feedback

---

*RVNKQuests: Where server events become epic adventures.*VNK plugin ecosystem and integrates seamlessly with RVNKCore for shared services and data management.

## Overview

RVNKQuests transforms traditional quest systems by creating dynamic, narrative-driven adventures that respond to natural server events and player actions. Instead of static quest NPCs, quests emerge organically from gameplay, creating unique storytelling experiences that adapt to your server's community and events.

### Core Features

- **Dynamic Quest System**: Event-triggered quests that adapt to player actions and server events
- **Generic Quest Engine**: Data-driven quest definitions via DataDrivenQuest + QuestComponentFactory -- no new Java classes needed for new quests
- **State-Based Management**: Sophisticated quest state tracking with automatic listener management and per-player state cache
- **Journal System**: Full quest history tracking with 7 action types (STARTED, COMPLETED, ABANDONED, FAILED, OBJECTIVE_COMPLETE, PATH_CHOSEN, REWARD_CLAIMED)
- **Smart Mob Detection**: Name+type+world matching detects pre-existing quest mobs on restart or admin placement
- **Narrative Integration**: Optional lore database for rich storytelling and world-building
- **Server Event Integration**: Quests that respond to natural server events and player interactions
- **Admin Command Framework**: Comprehensive tools for quest management, debugging, and configuration
- **RVNKCore Integration**: Optional integration with RVNKCore for shared data and services

## Quest Philosophy

RVNKQuests follows a unique approach to quest design:

- **Emergent Storytelling**: Quests emerge from natural server events rather than artificial quest givers
- **Community-Driven**: Adventures that involve multiple players and encourage cooperation
- **Narrative Depth**: Rich lore integration that builds your server's unique world and history
- **Administrative Flexibility**: Powerful tools for server administrators to manage and customize quest experiences

## Current Quest Catalog

### The Piglin Far From Home
*A heartwarming tale of a lost piglin seeking to return to the Nether*

- **Trigger**: Lone piglin discovered in the Overworld
- **Objective**: Community cooperation to escort the piglin safely to a Nether portal
- **Narrative**: Rich backstory about interdimensional displacement and community kindness
- **Rewards**: Nether-themed items and server-wide recognition

### The Ancient Guardian (In Development)
*An epic underwater adventure involving Elder Guardians and forgotten ruins*

- **Trigger**: Multiple players gather near ocean monuments
- **Objective**: Defeat the awakened Elder Guardian and explore forgotten sites
- **Narrative**: Ancient mysteries and underwater archaeology
- **Rewards**: Heart of the Sea and exclusive underwater exploration gear

### The First City Prophecy (Framework Complete)
*A city-building quest that helps establish player settlements*

- **Trigger**: Server world population events
- **Objective**: Establish and develop player cities according to ancient prophecies
- **Narrative**: Prophectic visions and settlement guidance
- **Rewards**: City-building resources and administrative recognition

## Technical Architecture

### Quest System Design

```
Quest Lifecycle:
NOT_STARTED → TRIGGER_FOUND → QUEST_ACTIVE → OBJECTIVE_FOUND → COMPLETED
     ↓              ↓              ↓               ↓            ↓
Trigger       Quest Begins    Objectives     Rewards      Cleanup
Listeners     Active          Processing     Distribution  & Archive
```

### Key Components

- **QuestManager**: Central coordination of all quest activities
- **AbstractQuest**: Base class with per-player stateCache (ConcurrentHashMap), preload on join, evict on quit
- **DataDrivenQuest**: Extends AbstractQuest, reads state_mapping from QuestDTO metadata for fully data-driven quests
- **QuestComponentFactory**: Creates trigger and objective listeners from ObjectiveType + metadata -- 10 generic components
- **QuestDefinitionSeeder**: Seeds data-driven quest definitions from database on startup
- **JournalService**: Quest history with 7 action types, backed by quest_journal_entries table (MySQL BIGINT epoch millis)
- **LoreDatabase**: Optional narrative content storage and retrieval
- **CommandManager**: Administrative interface for quest management

### Generic Components

**Triggers** (activate quest states):
- GenericMobSpawnTrigger -- spawns/detects mobs by name+type+world, safe spawn, beg mechanic
- GenericStructureInteractTrigger
- GenericEntityProximityTrigger
- GenericItemDiscoveryTrigger

**Objectives** (track progress):
- GenericKillObjective, GenericReachObjective, GenericEscortObjective
- GenericEncounterObjective, GenericInteractObjective, GenericDiscoverObjective
- GenericCollectObjective

### Integration Architecture

RVNKQuests supports multiple integration patterns:

1. **Standalone Mode**: Complete local functionality with YAML fallback storage
2. **RVNKCore Integration**: Shared services and cross-plugin data access
3. **Ecosystem Integration**: Full RVNK plugin ecosystem coordination

## Installation

### Requirements

- **Minecraft Server**: Bukkit/Spigot/Paper 1.20+
- **Java**: Java 21 or higher
- **Optional**: RVNKCore 1.3.0+ for enhanced features and cross-plugin integration

### Basic Installation

1. Download the latest RVNKQuests JAR from [Releases](../../releases)
2. Place the JAR file in your server's `plugins/` directory
3. Restart your server or use a plugin manager to load RVNKQuests
4. Configure the plugin using `/quest config` commands or edit `config.yml`

### RVNKCore Integration

For enhanced features and cross-plugin integration:

1. Install RVNKCore first (see [RVNKCore Documentation](../RVNKCore/README.md))
2. Install RVNKQuests as described above
3. RVNKQuests will automatically detect and integrate with RVNKCore
4. Configure shared database settings through RVNKCore if desired

## Configuration

### Basic Configuration (`config.yml`)

```yaml
general:
  logLevel: WARNING           # OFF, SEVERE, WARNING, INFO, DEBUG, FINE

quests:
  announce_completion: true
  default_world: event        # Used by seeded quest definitions
  piglin_far_from_home:
    enable: true
  ancient_guardian:
    enable: true
  ashen_pilgrim:
    enable: true

database:
  type: mysql                 # yaml | sqlite | mysql
  autosave_interval: 300
  mysql:
    host: localhost
    port: 3306
    username: questuser
    password: ''
    database: rvnkquests
    useSSL: true
    tablePrefix: ""
  fallback:
    enabled: true
    consecutive_failures: 3
    recovery_minutes: 5

mob_detection:
  name_type_matching: true    # Detect pre-existing mobs by name+type+world
  scan_interval_ms: 1000      # Scan throttle for entity scanning on move events
```

### RVNKCore Integration Configuration

When RVNKCore is available, additional configuration options become available:

```yaml
integration:
  rvnkcore:
    enabled: true            # Use RVNKCore services
    shared-database: true    # Use RVNKCore shared database
    services:
      player-service: true   # Use shared player tracking
      announcement-service: true  # Integrate with announcements
```

## Building from Source

```bash
# Clone the repository
git clone https://github.com/fourz/RVNKQuests.git
cd RVNKQuests

# Build using Maven
mvn clean package
```

The compiled JAR appears in the `target/` folder. Then place the JAR file into your server’s `plugins/` directory.

## Example Quests

### 1. Piglin Far From Home

A storyline quest where players find a lost Piglin near spawn, defeat it, and discover a journal revealing a broken Nether portal.

**Quest Flow**:
1. **Proximity Trigger**: Player approaches the lost Piglin.
2. **Combat Objective**: Defeat the Piglin and loot its dropped journal.
3. **Portal Guardians**: Discover the broken portal location, trigger guardian spawns.
4. **Completion**: Defeat the guardians, fix or activate the portal, and earn rewards.

**Implementation Details**:
- Proximity detection with a custom listener (e.g., `ListenerLonePiglin`).
- Custom quest item (`grotsnouts_journal`) describing the Piglin’s plight.
- Guardian spawn upon entering the portal area.
- Optional lore integration: Store or update a lore entry about the Piglin’s journal.

### 2. Ancient Guardian

An underwater quest requiring players to defeat an Elder Guardian and uncover hidden ruins.

**Quest Flow**:
1. **Boss Trigger**: Slay the Elder Guardian near an ocean monument.
2. **Exploration Objective**: Follow custom item clues to locate an underwater ruin.
3. **Combat Objective**: Defeat custom Drowned mobs.
4. **Completion**: Earn a powerful Trident or Heart of the Sea.

**Implementation Details**:
- Boss defeat triggers quest progression.
- `ITEM_LORE` clues referencing the hidden ruins.
- Additional Drowned defenders spawn once players enter the ruin coordinates.
- Optional lore integration: Automatically record the discovered ruin in the lore database.

### 3. The First City Prophecy

A server-wide event quest focusing on collaborative building and settlement.

**Quest Flow**:
1. **Lectern Trigger**: Players read an ancient prophecy.
2. **Vision Event**: A system message reveals coordinates for a future city.
3. **Construction Objective**: Players must build the city in the designated area.
4. **Completion**: The prophecy is fulfilled, awarding special items/server-wide buffs.

**Implementation Details**:
- Triggered when a player interacts with the prophecy lectern.
- The quest can require multiple players to be present (multiplayer activation condition).
- On completion, the new settlement is registered in RVNKLore as a recognized `CITY` lore entry.

### 4. Lost Miner’s Treasure

A classic dungeon crawl scenario featuring a ghostly NPC and hidden treasure.

**Quest Flow**:
1. **Trigger**: Player enters an abandoned mineshaft region.
2. **Lore Interaction**: The ghostly miner shares backstory (via in-game text or lore item).
3. **Key Item Objective**: Acquire the special diamond pickaxe hidden in a ravine.
4. **Completion**: Use the pickaxe to break an obsidian wall and loot the treasure room.

**Implementation Details**:
- Uses region-based triggers for quest start.
- Ghostly NPC uses particle or text events for ambiance.
- Custom pickaxe with unique NBT/lore indicating it can break quest-specific blocks.

## Commands

| Command                                | Description                     | Permission                  |
|----------------------------------------|---------------------------------|-----------------------------|
| `/quest list`                          | List all available quests      | `rvnkquests.command.list`   |
| `/quest start <quest_id>`              | Start a quest                  | `rvnkquests.command.start`  |
| `/quest abandon <quest_id>`            | Abandon an active quest        | `rvnkquests.command.abandon`|
| `/quest complete <quest_id>`           | Force-complete a quest (admin) | `rvnkquests.command.complete`|
| `/quest state <quest_id> <state>`      | Change a quest's state         | `rvnkquests.command.state`  |
| `/quest progress <quest_id> [player]`  | View quest progress            | `rvnkquests.command.progress`|
| `/quest journal [list\|view\|remove]`  | Quest journal operations       | `rvnkquests.command.journal`|
| `/quest journal assign <quest_id> <player>` | Assign quest to player (staff) | `rvnkquests.journal.staff` |
| `/quest journal unassign <quest_id> <player>` | Remove journal entries for quest (staff) | `rvnkquests.journal.staff` |
| `/quest mobs [list\|kill]`             | List/kill active quest mobs    | `rvnkquests.command.mobs`   |
| `/quest trigger <quest_id> [here\|around]` | Trigger a quest            | `rvnkquests.command.trigger`|
| `/quest validate`                      | Validate quest configurations  | `rvnkquests.command.validate`|
| `/quest menu`                          | Open quest browser GUI         | `rvnkquests.command.menu`   |
| `/quest leaderboard`                   | View quest leaderboards        | `rvnkquests.command.leaderboard`|
| `/quest config`                        | View/edit quest config         | `rvnkquests.command.config` |
| `/quest journal assign <quest_id> <player>` | Assign quest to player (staff, offline-capable) | `rvnkquests.journal.staff` |
| `/quest journal unassign <quest_id> <player>` | Clear journal entries, preserve progress (staff) | `rvnkquests.journal.staff` |
| `/quest reload`                        | Reload plugin configuration    | `rvnkquests.admin.reload`   |
| `/quest reload reset`                  | Reload + reinitialize quest listeners | `rvnkquests.admin.reload` |
| `/quest reload reseed`                 | Re-seed DB definitions + reinitialize | `rvnkquests.admin.reload` |
| `/quest reset <quest_id> [player]`     | Reset quest progress           | `rvnkquests.command.reset`  |
| `/quest seed`                          | Seed quest definitions from DB | `rvnkquests.command.seed`   |

## Configuration

### Main Configuration

```yaml
general:
  logLevel: WARNING   # OFF, SEVERE, WARNING, INFO, DEBUG, FINE

quests:
  announce_completion: true
  default_world: event

database:
  type: mysql         # yaml | sqlite | mysql
  autosave_interval: 300
  fallback:
    enabled: true
    consecutive_failures: 3
    recovery_minutes: 5

mob_detection:
  # Detect pre-existing mobs by name+type+world for quest triggers
  name_type_matching: true
  # Scan throttle in ms (entity scanning on move events)
  scan_interval_ms: 1000
```

### Quest-Specific Configuration

```yaml
quests:
  piglin_far_from_home:
    enabled: true
    world: event
    spawn_radius: 30.0
    rewards:
      exp: 500
      items:
        - material: NETHERITE_SCRAP
          amount: 2
        - material: GOLDEN_APPLE
          amount: 3
```

## Development

### Quest Lifecycle

1. **Quest Implementation**: Each quest extends or implements the `Quest` interface, handling states.
2. **Manager Registration**: The `QuestManager` registers your quest and event listeners.
3. **State Changes**: The quest transitions states based on triggers (player actions, item pickups, region entry, etc.).
4. **Rewards & Cleanup**: Once completed, the quest distributes rewards, logs data, or updates lore.

### Adding a New Quest

**Data-Driven (preferred)**: Define quest in the database with QuestDefinitionSeeder. DataDrivenQuest reads `state_mapping` from QuestDTO metadata, and QuestComponentFactory wires generic trigger/objective components automatically. No new Java classes required. See [Data-Driven Quest Engine](docs/quest-engine.md) for the full reference.

**Custom (for unique mechanics)**: Implement `Quest` or extend `AbstractQuest`, create custom listeners, and register in `QuestManager.initializeQuests()`.

## Data-Driven Quest Engine

Quests are created entirely from JSON metadata stored in the `quest_definitions` table. No Java code changes are needed for new quests.

### Trigger Types

| Trigger Type | Description |
|---|---|
| `LOCATION_PROXIMITY` | Player walks within a radius of fixed coordinates |
| `PROXIMITY_MOB_SPAWN` | A named custom mob spawns near a player |
| `ENTITY_PROXIMITY` | Player approaches any entity of a given type |
| `ITEM_DISCOVERY` | Player picks up or holds a named item |
| `STRUCTURE_INTERACT` | Player right-clicks a specific block type |

### Objective Types

| Objective Type | Description |
|---|---|
| `KILL` | Kill entities by type, with optional `custom_name` filter |
| `COLLECT` | Gather items, with optional `consume` and location constraints |
| `REACH` | Walk to fixed coordinates or a context-stored location |
| `INTERACT` | Right-click a block type, with optional coordinate constraints |
| `ENCOUNTER` | Spawns named mobs at coordinates; player must kill them all |
| `DISCOVER` | Find a structure identified by block material composition |

### Reward Types

| Reward Type | Description |
|---|---|
| `EXPERIENCE` | Awards XP points |
| `ITEM` | Gives an item by material name |
| `COMMAND` | Runs a console command (`%player%` substituted) |
| `PERMISSION` | Grants a permission node via the server's permissions system |

### Admin Flow (Console)

```
# Insert quest definition directly into the DB
INSERT INTO quest_definitions ...

# Load new definitions without restart
/quest reload reset

# Place a player into the quest (online or offline)
/quest journal assign <quest_id> <player>
```

For the complete schema, JSON examples, and step-by-step INSERT instructions, see [docs/quest-engine.md](docs/quest-engine.md).

### Building

```bash
mvn clean package
```

The compiled JAR appears in `target/`.

## Requirements

- Java 21+
- Spigot/Paper 1.20+

## License

This project is available under the MIT License. See the repository for full details.

## Credits

Created by Fourz with expansions by the Ravenkraft Dev Team.

---

# RVNKLore

A separate plugin that manages in-game lore and is closely integrated with RVNKQuests. For more details, visit the [RVNKLore repository](https://github.com/fourz/RVNKLore).

**Key Features**:
- Store and retrieve server history, stories, item lore, NPC backstories, city data, and more.
- Staff approval system for player-submitted lore.
- Automatic lore generation for significant events (e.g., first joins, boss kills).
- Powerful API for referencing lore in other plugins (like RVNKQuests).

### Setup & Usage:

```bash
git clone https://github.com/fourz/RVNKLore.git
cd RVNKLore
mvn clean package
```

Then drop the JAR into `plugins/`. Full usage instructions and examples are in the repository README.

With RVNKQuests and RVNKLore together, server owners can create a rich, story-driven Minecraft environment where every location, item, and event can be woven into a dynamic narrative.