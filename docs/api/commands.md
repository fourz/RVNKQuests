# RVNKQuests Command Reference

**Version**: 1.1.1
**Last Updated**: 2026-04-11

---

## Overview

All RVNKQuests commands use the `/quest` base command. Console execution is supported on all commands except where noted. Commands that target a specific player accept an optional `[player]` argument when run from console.

**Base Permission**: `rvnkquests.command.quest` (default: true)

---

## Quick Reference

### Player Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/quest list` | List available quests | `rvnkquests.command.list` |
| `/quest start <id> [player]` | Start a quest | `rvnkquests.command.start` |
| `/quest progress <id> [player]` | Check quest progress | `rvnkquests.command.progress` |
| `/quest abandon <id> [player]` | Abandon active quest | `rvnkquests.command.abandon` |
| `/quest journal [view\|remove] [id] [player]` | Quest journal | `rvnkquests.command.journal` |
| `/quest menu [filter] [player]` | Open quest GUI | `rvnkquests.command.menu` |
| `/quest leaderboard [type]` | View leaderboards | `rvnkquests.command.leaderboard` |
| `/quest prefs` | Notification preferences | `rvnkquests.prefs` |

### Staff Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/quest item <name> [player]` | Give quest item | `rvnkquests.command.item` |
| `/quest state <id> <state> [player]` | Set quest state | `rvnkquests.command.state` |
| `/quest trigger <id> [location]` | Trigger quest at location | `rvnkquests.command.trigger` |
| `/quest objective <id> <obj_id> <op> [value] [player]` | Edit objective progress | `rvnkquests.command.objective` |
| `/quest chain <op> [chain_id] [player]` | Quest chain management | `rvnkquests.command.chain` |
| `/quest reset <id> <player>` | Reset player progress | `rvnkquests.admin.reset` |
| `/quest complete <id> <player>` | Force-complete a quest | `rvnkquests.admin.complete` |

### Admin Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/quest reload [reset\|reseed]` | Reload configuration | `rvnkquests.admin` |
| `/quest validate` | Validate quest configs | `rvnkquests.admin` |
| `/quest config <op> [id\|all]` | Enable/disable quests | `rvnkquests.admin` |
| `/quest mobs <op>` | List/kill quest mobs | `rvnkquests.admin` |
| `/quest create <id> <name>` | Create quest definition | `rvnkquests.admin.create` |
| `/quest edit <id> <field> <value>` | Edit quest property | `rvnkquests.admin.edit` |
| `/quest reward <add\|remove> <id> ...` | Manage quest rewards | `rvnkquests.admin.edit` |
| `/quest export <id\|all>` | Export quest definitions | `rvnkquests.admin.export` |
| `/quest import <file\|all>` | Import quest definitions | `rvnkquests.admin.import` |
| `/quest debug <subcommand>` | Diagnostics | `rvnkquests.admin` |

---

## Player Commands

### /quest list

Lists quests available to the player.

**Usage**: `/quest list`

**Permission**: `rvnkquests.command.list`

**Console**: Yes

---

### /quest start

Starts a quest for the player. If a quest has prerequisites, they are validated first.

**Usage**: `/quest start <quest_id> [player]`

**Parameters**:
- `quest_id` — Quest identifier (required)
- `player` — Target player (optional; required from console)

**Permission**: `rvnkquests.command.start`

**Console**: Yes (requires `[player]`)

---

### /quest progress

Shows current objective progress for an active quest.

**Usage**: `/quest progress <quest_id> [player]`

**Parameters**:
- `quest_id` — Quest identifier (required)
- `player` — Target player (optional; required from console)

**Permission**: `rvnkquests.command.progress`

**Console**: Yes

---

### /quest abandon

Abandons an active quest. Progress is lost; quest may be restartable depending on its repeat configuration.

**Usage**: `/quest abandon <quest_id> [player]`

**Parameters**:
- `quest_id` — Quest identifier (required)
- `player` — Target player (optional; required from console)

**Permission**: `rvnkquests.command.abandon`

**Console**: Yes

---

### /quest journal

Views or manages a player's quest journal. The journal records all quest events (start, complete, abandon, objective updates, rewards claimed).

**Usage**: `/quest journal [view|remove] [quest_id] [player]`

**Parameters**:
- No args — Show journal summary
- `view [quest_id]` — View full journal, optionally filtered to one quest
- `remove <quest_id>` — Remove journal entries for a quest
- `player` — Target player (optional; required from console)

**Permission**: `rvnkquests.command.journal`

**Console**: Yes

**Journal events tracked**: STARTED, COMPLETED, ABANDONED, OBJECTIVE_COMPLETE, FAILED, PATH_CHOSEN, REWARD_CLAIMED

---

### /quest menu

Opens the quest GUI. Supports optional filtering to a quest category or state.

**Usage**: `/quest menu [filter] [player]`

**Parameters**:
- `filter` — Category name or state filter (optional)
- `player` — Target player (optional; required from console)

**Permission**: `rvnkquests.command.menu`

---

### /quest leaderboard

Shows the quest leaderboard.

**Usage**: `/quest leaderboard [type]`

**Parameters**:
- `type` — Leaderboard type (optional; defaults to global completion count)

**Permission**: `rvnkquests.command.leaderboard`

**Console**: Yes

---

### /quest prefs

Opens the notification preference manager for RVNKQuests events.

**Usage**: `/quest prefs`

**Permission**: `rvnkquests.prefs`

**Player-only**: Yes

---

## Staff Commands

### /quest item

Gives the player a named quest item from the item registry.

**Usage**: `/quest item <item_name> [player]`

**Parameters**:
- `item_name` — Quest item registry key (required)
- `player` — Target player (optional; required from console)

**Permission**: `rvnkquests.command.item`

**Console**: Yes

---

### /quest state

Sets a player's quest to a specific state.

**Usage**: `/quest state <quest_id> <state> [player]`

**Parameters**:
- `quest_id` — Quest identifier (required)
- `state` — Target state (required); valid values: `NOT_STARTED`, `TRIGGER_FOUND`, `QUEST_ACTIVE`, `OBJECTIVE_FOUND`, `COMPLETED`, `ABANDONED`
- `player` — Target player (optional; required from console)

**Permission**: `rvnkquests.command.state`

**Console**: Yes

**State machine**:
```
NOT_STARTED → TRIGGER_FOUND → QUEST_ACTIVE → OBJECTIVE_FOUND → COMPLETED
                                                              → ABANDONED
```

---

### /quest trigger

Fires a quest trigger at the sender's current location. Used to manually activate location-based or proximity triggers during testing and setup.

**Usage**: `/quest trigger <quest_id> [location]`

**Parameters**:
- `quest_id` — Quest identifier (required)
- `location` — Explicit location override (optional; defaults to sender position)

**Permission**: `rvnkquests.command.trigger`

**Player-only**: Yes

---

### /quest objective

Directly edits a player's objective progress for a specific quest.

**Usage**: `/quest objective <quest_id> <objective_id> <set|complete|reset> [value] [player]`

**Parameters**:
- `quest_id` — Quest identifier (required)
- `objective_id` — Objective identifier within the quest (required)
- `set <value>` — Set progress to a specific value
- `complete` — Mark objective as complete
- `reset` — Reset objective to zero
- `player` — Target player (optional; required from console)

**Permission**: `rvnkquests.command.objective`

**Console**: Yes

---

### /quest chain

Manages quest chains — ordered sequences of quests that unlock progressively.

**Usage**: `/quest chain <subcommand> [chain_id] [player]`

**Permission**: `rvnkquests.command.chain`

**Console**: Yes

**Subcommands**:

| Subcommand | Usage | Description |
|------------|-------|-------------|
| `list` | `/quest chain list` | List all quest chains |
| `start` | `/quest chain start <chain_id> [player]` | Start the first quest in a chain |
| `status` | `/quest chain status <chain_id> [player]` | Show chain progress |
| `reset` | `/quest chain reset <chain_id> [player]` | Reset all chain progress |

---

### /quest reset

Resets all progress for a specific quest on a specific player. Unlike abandon, this clears journal history and returns the quest to NOT_STARTED.

**Usage**: `/quest reset <quest_id> <player>`

**Parameters**:
- `quest_id` — Quest identifier (required)
- `player` — Target player (required)

**Permission**: `rvnkquests.admin.reset`

**Console**: Yes

---

### /quest complete

Force-completes a quest for a player, granting all rewards immediately.

**Usage**: `/quest complete <quest_id> <player>`

**Parameters**:
- `quest_id` — Quest identifier (required)
- `player` — Target player (required)

**Permission**: `rvnkquests.admin.complete`

**Console**: Yes

---

## Admin Commands

### /quest reload

Reloads quest configuration from disk. Optional flags allow resetting or reseeding data.

**Usage**: `/quest reload [reset|reseed]`

**Parameters**:
- No args — Reload config files only (player progress preserved)
- `reset` — Reload and reset all player progress data
- `reseed` — Reload and reseed quest definitions from templates

**Permission**: `rvnkquests.admin`

**Console**: Yes

---

### /quest validate

Validates all quest definition files for structural errors, missing objectives, invalid reward types, and broken references.

**Usage**: `/quest validate`

**Permission**: `rvnkquests.admin`

**Console**: Yes

---

### /quest config

Enables or disables quest definitions at runtime without a full reload.

**Usage**: `/quest config <operation> [quest_id|all]`

**Parameters**:
- `enable <quest_id|all>` — Enable a quest or all quests
- `disable <quest_id|all>` — Disable a quest or all quests

**Permission**: `rvnkquests.admin`

**Console**: Yes

---

### /quest mobs

Lists or removes active quest-related mob entities.

**Usage**: `/quest mobs <operation>`

**Parameters**:
- `list` — List all currently spawned quest mobs
- `kill` — Remove all quest mob entities (used for cleanup)

**Permission**: `rvnkquests.admin`

**Console**: Yes

---

### /quest create

Creates a new quest definition with a given ID and display name. Persists to YAML and database.

**Usage**: `/quest create <id> <name...>`

**Parameters**:
- `id` — Unique quest identifier (required; alphanumeric, underscores)
- `name` — Display name (required; multi-word supported)

**Permission**: `rvnkquests.admin.create`

**Console**: Yes

---

### /quest edit

Edits a property of an existing quest definition.

**Usage**: `/quest edit <id> <field> <value>`

**Parameters**:
- `id` — Quest identifier (required)
- `field` — Property to edit (required); valid fields: `description`, `category`, `repeatable`, `cooldown`
- `value` — New value (required)

**Permission**: `rvnkquests.admin.edit`

**Console**: Yes

---

### /quest reward

Adds or removes rewards from a quest definition.

**Usage**: `/quest reward <add|remove> <quest_id> [reward args...]`

**Parameters**:
- `add <quest_id> <type> <value>` — Add a reward of the given type
- `remove <quest_id> <reward_id>` — Remove a reward by ID

**Permission**: `rvnkquests.admin.edit`

**Console**: Yes

**Reward types**: `ITEM`, `EXPERIENCE`, `CURRENCY`, `PERMISSION`, `COMMAND`, `TITLE`, `QUEST_UNLOCK`, `LORE`, `CUSTOM`

---

### /quest export

Exports one or all quest definitions to YAML files in the plugin's export directory.

**Usage**: `/quest export <quest_id|all>`

**Parameters**:
- `quest_id` — Export a single quest
- `all` — Export all quest definitions

**Permission**: `rvnkquests.admin.export`

**Console**: Yes

---

### /quest import

Imports quest definitions from YAML files in the plugin's import directory.

**Usage**: `/quest import <filename|all>`

**Parameters**:
- `filename` — Import a specific YAML file
- `all` — Import all YAML files in the import directory

**Permission**: `rvnkquests.admin.import`

**Console**: Yes

---

## Debug Commands

### /quest debug

Admin diagnostics and development utilities. All subcommands require `rvnkquests.admin` unless noted.

**Usage**: `/quest debug <subcommand> [args]`

**Permission**: `rvnkquests.admin`

**Console**: Yes

---

#### /quest debug diagnostics

Prints a full system health report: registered services, database connectivity, loaded quest count, active player sessions, and configuration status.

**Usage**: `/quest debug diagnostics`

---

#### /quest debug list

Lists all quests registered in the quest engine with their IDs, names, and enabled state.

**Usage**: `/quest debug list`

**Aliases**: `/quest debug ls`

---

#### /quest debug player

Shows detailed quest progress for a player — active quests, objective states, journal entry count, and cached state.

**Usage**: `/quest debug player [name]`

**Aliases**: `/quest debug p`

**Parameters**:
- `name` — Player name (optional; defaults to sender)

---

#### /quest debug loglevel

Views or changes the runtime log verbosity for RVNKQuests.

**Usage**: `/quest debug loglevel [level]`

**Aliases**: `/quest debug level`

**Parameters**:
- `level` — Log level (optional; if omitted, shows current level): `DEBUG`, `INFO`, `WARNING`, `SEVERE`

---

#### /quest debug seed

Seeds the server with test quest data for development and QA.

**Usage**: `/quest debug seed <mode>`

**Parameters**:
- `minimal` — Seed one minimal quest for smoke testing
- `standard` — Seed a representative set of quest types
- `stress` — Seed a large dataset for performance testing
- `cleanup` — Remove all seeded test data
- `status` — Show current seed state

**Permission**: `rvnkquests.admin.seed`

---

#### /quest debug setstate

Directly sets a player's quest to any state, bypassing normal state transition validation. Use for testing and recovery.

**Usage**: `/quest debug setstate <quest_id> <state> <player>`

**Parameters**:
- `quest_id` — Quest identifier (required)
- `state` — Target state (required): `NOT_STARTED`, `TRIGGER_FOUND`, `QUEST_ACTIVE`, `OBJECTIVE_FOUND`, `COMPLETED`, `ABANDONED`
- `player` — Target player (required)

**Permission**: `rvnkquests.admin.debug`

---

## Reference

### Quest States

| State | Description |
|-------|-------------|
| `NOT_STARTED` | Quest not yet encountered by the player |
| `TRIGGER_FOUND` | Player has found the trigger; quest intro shown |
| `QUEST_ACTIVE` | Quest accepted and objectives are tracking |
| `OBJECTIVE_FOUND` | At least one objective has been discovered |
| `COMPLETED` | All objectives complete; rewards granted |
| `ABANDONED` | Player abandoned the quest |

### Objective Types

| Type | Description |
|------|-------------|
| `KILL` | Kill a specified entity type or named mob |
| `COLLECT` | Collect an item into inventory |
| `REACH` | Reach a specific location or region |
| `TALK_TO` | Interact with a named NPC or entity |
| `INTERACT` | Interact with a block or object |
| `CRAFT` | Craft a specific item |
| `MINE` | Break a specific block type |
| `PLACE` | Place a specific block type |
| `USE_ITEM` | Use a specific item |
| `DELIVER` | Deliver an item to a location or entity |
| `DISCOVER` | Discover a biome, structure, or region |
| `ESCORT` | Escort an entity to a destination |
| `ENCOUNTER` | Encounter/spawn a specific entity |
| `CUSTOM` | Plugin-registered custom objective handler |

### Reward Types

| Type | Description |
|------|-------------|
| `ITEM` | Give item(s) to player inventory |
| `EXPERIENCE` | Grant experience points or levels |
| `CURRENCY` | Grant currency (requires Vault + economy plugin) |
| `PERMISSION` | Grant a LuckPerms permission node or group |
| `COMMAND` | Execute a console command with player context |
| `TITLE` | Display a title/subtitle message |
| `QUEST_UNLOCK` | Unlock another quest for the player |
| `LORE` | Unlock a lore entry (requires RVNKLore) |
| `CUSTOM` | Plugin-registered custom reward handler |

### Trigger Types

| Type | Description |
|------|-------------|
| `PROXIMITY_MOB_SPAWN` | Trigger when a mob spawns near the player |
| `STRUCTURE_INTERACT` | Trigger on interaction with a structure |
| `ITEM_DISCOVERY` | Trigger when player discovers a specific item |
| `ENTITY_PROXIMITY` | Trigger when player enters proximity of an entity |
| `LOCATION_PROXIMITY` | Trigger when player enters a location radius |
| `WORLD_EVENT` | Trigger on a Bukkit world event |
| `COMMAND` | Trigger via `/quest trigger` command |
| `CUSTOM` | Plugin-registered custom trigger handler |

---

## Permission Nodes

### Player Permissions

| Permission | Default | Purpose |
|-----------|---------|---------|
| `rvnkquests.command.quest` | true | Base `/quest` access |
| `rvnkquests.command.list` | true | `/quest list` |
| `rvnkquests.command.start` | true | `/quest start` |
| `rvnkquests.command.progress` | true | `/quest progress` |
| `rvnkquests.command.abandon` | true | `/quest abandon` |
| `rvnkquests.command.journal` | true | `/quest journal` |
| `rvnkquests.command.menu` | true | `/quest menu` |
| `rvnkquests.command.leaderboard` | true | `/quest leaderboard` |
| `rvnkquests.prefs` | true | `/quest prefs` |

### Staff Permissions

| Permission | Default | Purpose |
|-----------|---------|---------|
| `rvnkquests.command.item` | op | `/quest item` |
| `rvnkquests.command.state` | op | `/quest state` |
| `rvnkquests.command.trigger` | op | `/quest trigger` |
| `rvnkquests.command.objective` | op | `/quest objective` |
| `rvnkquests.command.chain` | op | `/quest chain` |
| `rvnkquests.admin.reset` | op | `/quest reset` |
| `rvnkquests.admin.complete` | op | `/quest complete` |

### Admin Permissions

| Permission | Default | Purpose |
|-----------|---------|---------|
| `rvnkquests.admin` | op | All admin commands |
| `rvnkquests.admin.create` | op | Create quest definitions |
| `rvnkquests.admin.edit` | op | Edit definitions and rewards |
| `rvnkquests.admin.export` | op | Export definitions to YAML |
| `rvnkquests.admin.import` | op | Import definitions from YAML |
| `rvnkquests.admin.reset` | op | Reset player progress |
| `rvnkquests.admin.complete` | op | Force-complete quests |
| `rvnkquests.admin.seed` | op | Seed test data |
| `rvnkquests.admin.debug` | op | `debug setstate` |

---

## Console Support

All commands support console execution. Commands targeting a player require an explicit `<player>` or `[player]` argument from console:

```
quest list
quest start daily_quest Steve
quest complete boss_quest Steve
quest debug diagnostics
quest debug player Steve
quest reload
quest export all
```

**Player-only** (no console support): `/quest trigger`, `/quest menu`, `/quest prefs`
