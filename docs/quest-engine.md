---
title: Data-Driven Quest Engine
tags: [rvnkquests, engine, reference, quests]
board: rvnkquests
status: active
updated: 2026-05-15
---

# Data-Driven Quest Engine

Complete reference for creating and managing quests via the database without writing Java code.

---

## 1. Overview

RVNKQuests uses a data-driven quest engine built on three cooperating classes:

- `DataDrivenQuest` — extends `AbstractQuest`; reads `state_mapping` from `QuestDTO` metadata to determine which components are active in each state
- `QuestComponentFactory` — creates trigger and objective listener instances from `ObjectiveType` / `TriggerType` + metadata JSON
- `QuestDefinitionSeeder` — seeds the initial built-in quest definitions on first startup; also exposes `reseed()` for forced reload

At startup, `QuestDefinitionSeeder.seedIfNeeded()` inserts the four built-in quests only if the `quest_definitions` table is empty. All subsequent quests are inserted directly into the database. Running `/quest reload reset` re-initializes quest listeners from the current DB state without restarting the server.

**Key guarantee**: No Java code changes are required to create a new quest. Insert rows into `quest_definitions` + `quest_definition_objectives` + `quest_definition_rewards`, run `/quest reload reset`, and the engine picks up the new quest automatically.

---

## 2. State Machine

Every quest goes through a six-state lifecycle defined in `QuestState.java`.

```
NOT_STARTED
    │  trigger fires (player enters proximity, picks up item, etc.)
    ▼
TRIGGER_FOUND
    │  player accepts / quest auto-advances
    ▼
QUEST_ACTIVE
    │  player completes objectives
    ▼
OBJECTIVE_FOUND
    │  final conditions met
    ▼
COMPLETED
    │
    └── (at any active state) player /quest abandon → ABANDONED
```

| State | Meaning |
|---|---|
| `NOT_STARTED` | Quest exists; player has not discovered it |
| `TRIGGER_FOUND` | Player discovered the trigger (mob, item, location) |
| `QUEST_ACTIVE` | Player has accepted the quest and is pursuing objectives |
| `OBJECTIVE_FOUND` | Player located the primary objective |
| `COMPLETED` | All requirements fulfilled; rewards delivered |
| `ABANDONED` | Player abandoned the quest; can restart |

### startQuest() behavior

`QuestManager.startQuest(playerUuid, questId)` sets player state to `QUEST_ACTIVE` directly, bypassing `TRIGGER_FOUND`. This is the code path used by `/quest journal assign` for staff assignment.

### Per-player state cache

`AbstractQuest.stateCache` (`ConcurrentHashMap`) holds one state per player UUID. The cache is populated on player join (`preloadStateForPlayer()`) with an async DB read, defaulting to `NOT_STARTED` if no record exists. It is evicted on quit (`evictStateForPlayer()`). Components read from this cache to determine whether to activate.

---

## 3. Quest Definition Schema

### quest_definitions table

```sql
CREATE TABLE quest_definitions (
    quest_id        VARCHAR(100) PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    category        VARCHAR(100),
    repeatable      BOOLEAN NOT NULL DEFAULT FALSE,
    cooldown_minutes INT NOT NULL DEFAULT 0,
    prerequisites   JSON,   -- list of quest_id strings that must be COMPLETED first
    metadata        JSON,   -- contains state_mapping, components, and other engine config
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### metadata JSON structure

The `metadata` column drives everything. The two required keys are `state_mapping` and `components`.

```json
{
  "state_mapping": {
    "NOT_STARTED":    ["component_id_1"],
    "TRIGGER_FOUND":  ["component_id_2"],
    "QUEST_ACTIVE":   ["component_id_3"],
    "OBJECTIVE_FOUND": ["component_id_4"]
  },
  "components": {
    "component_id_1": { "type": "ENTITY_PROXIMITY", ... },
    "component_id_2": { "objective_type": "KILL", ... },
    "component_id_3": { "objective_type": "COLLECT", ... },
    "component_id_4": { "objective_type": "REACH", ... }
  },
  "start_trigger": "Display name shown in UI"
}
```

`state_mapping` maps each `QuestState` name to a list of component IDs. When the engine activates a state for a player, it instantiates the listed components as Bukkit `Listener` instances.

Components with `"type"` are triggers. Components with `"objective_type"` are objectives.

### quest_definition_objectives table

Holds the human-readable objective list shown in the UI and leaderboard. The engine uses the `metadata.components` entries in `quest_definitions` for actual logic — this table is for display only.

```sql
CREATE TABLE quest_definition_objectives (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    quest_id     VARCHAR(100) NOT NULL,
    objective_id VARCHAR(100) NOT NULL,
    type         VARCHAR(50) NOT NULL,  -- ObjectiveType name
    target       VARCHAR(200),          -- Entity type, item name, etc.
    required_amount INT NOT NULL DEFAULT 1,
    description  TEXT,
    sort_order   INT NOT NULL DEFAULT 0,
    metadata     JSON,
    FOREIGN KEY (quest_id) REFERENCES quest_definitions(quest_id) ON DELETE CASCADE
);
```

### quest_definition_rewards table

```sql
CREATE TABLE quest_definition_rewards (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    quest_id   VARCHAR(100) NOT NULL,
    reward_id  VARCHAR(100) NOT NULL,
    type       VARCHAR(50) NOT NULL,   -- RewardType name
    value      VARCHAR(500),           -- Material name, command string, permission node
    amount     INT NOT NULL DEFAULT 1,
    description TEXT,
    metadata   JSON,
    FOREIGN KEY (quest_id) REFERENCES quest_definitions(quest_id) ON DELETE CASCADE
);
```

---

## 4. Trigger Types

Triggers are components listed under `NOT_STARTED` in `state_mapping`. They listen for world events and advance the player to `TRIGGER_FOUND` (or directly to `QUEST_ACTIVE` for some patterns) when the trigger condition fires.

### LOCATION_PROXIMITY

Player walks within a fixed radius of specified coordinates.

```json
{
  "type": "LOCATION_PROXIMITY",
  "world": "event",
  "x": 150,
  "y": 64,
  "z": -300,
  "radius": 25
}
```

| Field | Type | Description |
|---|---|---|
| `world` | String | World name |
| `x`, `y`, `z` | Number | Centre coordinates |
| `radius` | Number | Trigger radius in blocks |

### PROXIMITY_MOB_SPAWN

A named custom mob spawns near the player. The engine spawns the mob at the nearest safe surface location. Supports detecting pre-existing mobs by name+type on startup (useful after server restarts).

```json
{
  "type": "PROXIMITY_MOB_SPAWN",
  "entity_type": "WANDERING_TRADER",
  "custom_name": "Ashlan the Wanderer",
  "world": "event",
  "radius": 40,
  "context_key": "ashlan_entity",
  "detect_existing": true,
  "beg_on_attack": true,
  "beg_message": "Please, I need your help!",
  "beg_count": 1
}
```

| Field | Type | Description |
|---|---|---|
| `entity_type` | String | Bukkit EntityType name |
| `custom_name` | String | Display name for the spawned mob |
| `world` | String | World to spawn in |
| `radius` | Number | Spawn radius around player |
| `context_key` | String | Optional — stores the spawned entity reference for later use by other components |
| `context_location_key` | String | Optional — stores the spawn location in runtime context |
| `detect_existing` | Boolean | If `true`, scans loaded entities on activation to adopt pre-existing matching mobs |
| `beg_on_attack` | Boolean | Mob sends `beg_message` and is knocked back on hit instead of dying |
| `beg_message` | String | Message sent when mob is attacked |
| `beg_count` | Integer | Number of attacks before beg behaviour stops |
| `interact_book` | String | Lore book ID given when player right-clicks the mob |

### ENTITY_PROXIMITY

Player approaches any entity of a given type within a radius. No custom mob spawn — the entity must already exist in the world.

```json
{
  "type": "ENTITY_PROXIMITY",
  "entity_type": "ELDER_GUARDIAN",
  "world": "event",
  "radius": 50
}
```

| Field | Type | Description |
|---|---|---|
| `entity_type` | String | Bukkit EntityType name |
| `world` | String | World to scan |
| `radius` | Number | Detection radius in blocks |

### ITEM_DISCOVERY

Player picks up or holds a named item.

```json
{
  "type": "ITEM_DISCOVERY",
  "item_type": "WRITTEN_BOOK",
  "item_name": "The First City Prophecy",
  "world": "event"
}
```

| Field | Type | Description |
|---|---|---|
| `item_type` | String | Bukkit Material name |
| `item_name` | String | Display name of the item (exact match) |
| `world` | String | Restricts to this world if set |

### STRUCTURE_INTERACT

Player right-clicks a specific block type to trigger the quest.

```json
{
  "type": "STRUCTURE_INTERACT",
  "block_type": "LECTERN",
  "world": "event",
  "x": 0,
  "y": 65,
  "z": 0,
  "radius": 2
}
```

| Field | Type | Description |
|---|---|---|
| `block_type` | String | Bukkit Material name |
| `world` | String | World to restrict to |
| `x`, `y`, `z` | Number | Optional — restrict to specific coordinates |
| `radius` | Number | Optional — tolerance radius around coordinates |

---

## 5. Objective Types

Objectives are components listed under `TRIGGER_FOUND`, `QUEST_ACTIVE`, or `OBJECTIVE_FOUND` in `state_mapping`. All objectives share the fields `required_state` (the state in which the objective is active) and `advance_state` (the state to transition to on completion).

### KILL

Kill entities by type. Supports filtering by custom name to restrict tracking to quest mobs.

```json
{
  "objective_type": "KILL",
  "entity_type": "ZOMBIE",
  "custom_name": "Undead Bounty",
  "required_kills": 3,
  "quest_mob_only": true,
  "required_state": "QUEST_ACTIVE",
  "advance_state": "COMPLETED"
}
```

| Field | Type | Description |
|---|---|---|
| `entity_type` | String | Bukkit EntityType name |
| `custom_name` | String | Optional — only count kills of this named mob |
| `required_kills` | Integer | Number of kills required |
| `quest_mob_only` | Boolean | If `true`, only count mobs spawned by the quest engine |
| `sets_path` | String | Optional — records this path label in `quest_progress.path_choice` (XOR branching) |

### COLLECT

Gather items. Items can be consumed on collection. Optionally restricts the collection zone to coordinates.

```json
{
  "objective_type": "COLLECT",
  "items": {
    "SOUL_TORCH": 4,
    "GHAST_TEAR": 1
  },
  "consume": true,
  "world": "event",
  "x": 250,
  "y": 65,
  "z": -350,
  "radius": 10,
  "required_state": "QUEST_ACTIVE",
  "advance_state": "OBJECTIVE_FOUND"
}
```

| Field | Type | Description |
|---|---|---|
| `items` | Object | Map of `Material: count` pairs |
| `consume` | Boolean | If `true`, items are removed from inventory on collection |
| `world` | String | Optional — world restriction |
| `x`, `y`, `z`, `radius` | Number | Optional — restrict collection to this zone |

Single-item shorthand (for simple quests):

```json
{
  "objective_type": "COLLECT",
  "item_type": "IRON_INGOT",
  "required_amount": 5,
  "consume": true,
  "required_state": "QUEST_ACTIVE",
  "advance_state": "COMPLETED"
}
```

### REACH

Player walks to a location. The target can be fixed coordinates or a runtime context location set by a trigger.

Fixed coordinates:

```json
{
  "objective_type": "REACH",
  "world": "event",
  "x": 0,
  "y": 64,
  "z": 0,
  "radius": 10.0,
  "required_state": "OBJECTIVE_FOUND",
  "advance_state": "COMPLETED"
}
```

Context location (coordinates stored by trigger at runtime):

```json
{
  "objective_type": "REACH",
  "context_location_key": "portal_location",
  "radius": 5.0,
  "required_state": "OBJECTIVE_FOUND",
  "advance_state": "COMPLETED"
}
```

| Field | Type | Description |
|---|---|---|
| `context_location_key` | String | Runtime context key set by a trigger component |
| `world`, `x`, `y`, `z` | Number | Fixed target coordinates (used when no context key) |
| `radius` | Number | Arrival tolerance in blocks |

### INTERACT

Player right-clicks a block type. Optionally restricts to specific coordinates.

```json
{
  "objective_type": "INTERACT",
  "block_type": "SOUL_CAMPFIRE",
  "world": "event",
  "x": 400,
  "y": 63,
  "z": -500,
  "radius": 3,
  "required_count": 1,
  "required_state": "OBJECTIVE_FOUND",
  "advance_state": "COMPLETED"
}
```

| Field | Type | Description |
|---|---|---|
| `block_type` | String | Bukkit Material name |
| `world` | String | Optional world restriction |
| `x`, `y`, `z`, `radius` | Number | Optional coordinate restriction |
| `required_count` | Integer | Number of interactions required (default 1) |

### ENCOUNTER

Spawns a group of named mobs at coordinates when the player enters a trigger radius. The player must kill all spawned mobs to complete the objective.

```json
{
  "objective_type": "ENCOUNTER",
  "entity_type": "SKELETON",
  "spawn_count": 3,
  "required_kills": 3,
  "custom_name": "Test Revenant",
  "world": "event",
  "x": 0,
  "y": 64,
  "z": 0,
  "trigger_radius": 15,
  "spawn_radius": 5,
  "prevent_infighting": true,
  "block_portals": false,
  "required_state": "QUEST_ACTIVE",
  "advance_state": "OBJECTIVE_FOUND"
}
```

| Field | Type | Description |
|---|---|---|
| `entity_type` | String | Bukkit EntityType name |
| `spawn_count` | Integer | Number of mobs to spawn |
| `required_kills` | Integer | Kills needed (usually equals `spawn_count`) |
| `custom_name` | String | Display name for spawned mobs |
| `x`, `y`, `z` | Number | Encounter centre coordinates |
| `trigger_radius` | Number | Player must enter this radius to activate the spawn |
| `spawn_radius` | Number | Mobs spawn within this radius of the centre |
| `prevent_infighting` | Boolean | Mobs will not attack each other |
| `block_portals` | Boolean | Prevent portal use in the encounter zone |
| `context_location_key` | String | Alternative to fixed coords — read location from runtime context |

### DISCOVER

Player finds a structure identified by block material composition within a detection radius.

```json
{
  "objective_type": "DISCOVER",
  "world": "event",
  "detection_radius": 30,
  "detection_materials": "PRISMARINE,PRISMARINE_BRICKS,DARK_PRISMARINE,SEA_LANTERN",
  "min_blocks": 5,
  "required_state": "QUEST_ACTIVE",
  "advance_state": "OBJECTIVE_FOUND"
}
```

| Field | Type | Description |
|---|---|---|
| `world` | String | World to scan |
| `detection_radius` | Number | Block scan radius around player |
| `detection_materials` | String | Comma-separated list of Bukkit Material names |
| `min_blocks` | Integer | Minimum matching blocks required to count as discovered |

---

## 6. Reward Types

Rewards are defined in `quest_definition_rewards`. The engine delivers all rewards when the quest reaches `COMPLETED`.

| `type` value | `value` field | `amount` field |
|---|---|---|
| `EXPERIENCE` | Ignored | XP points to award |
| `ITEM` | Bukkit Material name | Stack size |
| `COMMAND` | Console command string. `%player%` is substituted with the player's name | Ignored |
| `PERMISSION` | Permission node string | Ignored |

Double reward delivery is prevented by `quest_rewards_claimed` — a unique constraint on `(player_uuid, quest_id, reward_id)`.

SQL examples:

```sql
-- 500 XP
INSERT INTO quest_definition_rewards (quest_id, reward_id, type, value, amount)
VALUES ('dev_undead_bounty', 'bounty_xp', 'EXPERIENCE', NULL, 500);

-- 3 rotten flesh
INSERT INTO quest_definition_rewards (quest_id, reward_id, type, value, amount)
VALUES ('dev_undead_bounty', 'bounty_loot', 'ITEM', 'ROTTEN_FLESH', 3);

-- Title command
INSERT INTO quest_definition_rewards (quest_id, reward_id, type, value, amount)
VALUES ('first_city_prophecy', 'prophecy_title', 'COMMAND',
        'title %player% subtitle {"text":"City Founder","color":"gold"}', 1);

-- Temporary permission
INSERT INTO quest_definition_rewards (quest_id, reward_id, type, value, amount)
VALUES ('some_quest', 'perm_reward', 'PERMISSION', 'rvnkquests.special.access', 1);
```

---

## 7. Quest Chains

Chain quests are linked by `prerequisites` and decorated with `chain` / `chapter` metadata.

### prerequisites column

The `quest_definitions.prerequisites` column holds a JSON array of quest IDs that must all be in `COMPLETED` state before this quest is available.

```sql
UPDATE quest_definitions
SET prerequisites = '["ashen_pilgrim"]'
WHERE quest_id = 'ashen_pilgrim_epilogue';
```

### chain and chapter metadata

Optional narrative metadata stored in the `metadata` column. The UI uses these to display chain progress.

```json
{
  "chain": "clavenshaft_chronicles",
  "chapter": 1,
  "state_mapping": { ... },
  "components": { ... }
}
```

| Key | Type | Description |
|---|---|---|
| `chain` | String | Shared chain identifier for all quests in the series |
| `chapter` | Integer | Chapter number within the chain (for ordering) |

Querying all quests in a chain:

```sql
SELECT quest_id, name,
       JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.chapter')) AS chapter
FROM quest_definitions
WHERE JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.chain')) = 'clavenshaft_chronicles'
ORDER BY chapter;
```

---

## 8. Repeatable Quests

Set `repeatable = TRUE` and `cooldown_minutes > 0` in `quest_definitions`.

```sql
INSERT INTO quest_definitions (quest_id, name, description, category, repeatable, cooldown_minutes, metadata)
VALUES (
  'dev_undead_bounty',
  'Undead Bounty',
  'Kill 3 zombies. Repeatable every hour.',
  'combat',
  TRUE,
  60,
  '{ "state_mapping": { "NOT_STARTED": ["trigger_proximity"], "QUEST_ACTIVE": ["obj_kill_zombies"] }, "components": { ... } }'
);
```

When a repeatable quest reaches `COMPLETED`, `RepeatableQuestServiceImpl` records the completion timestamp in `PlayerQuestRepeatDTO`. On the next attempt, the engine checks whether `cooldown_minutes` has elapsed before allowing restart. Players who attempt to start during cooldown receive a "Quest on cooldown" message showing the remaining time.

---

## 9. XOR Branching

XOR branching presents two or more competing objectives in the same state. Completing any one of them records the chosen path and advances the state, suppressing the others.

Use `sets_path` on each competing component to tag which branch was taken. The chosen path label is written to `quest_progress.path_choice` and available to later components via context.

```json
"state_mapping": {
  "TRIGGER_FOUND": ["obj_kill_piglin", "obj_escort_piglin"]
},
"components": {
  "obj_kill_piglin": {
    "objective_type": "KILL",
    "entity_type": "PIGLIN",
    "custom_name": "GrotSnout",
    "required_kills": 1,
    "quest_mob_only": true,
    "required_state": "TRIGGER_FOUND",
    "advance_state": "QUEST_ACTIVE",
    "sets_path": "COMBAT_PATH"
  },
  "obj_escort_piglin": {
    "objective_type": "REACH",
    "context_location_key": "portal_location",
    "radius": 10.0,
    "required_state": "TRIGGER_FOUND",
    "advance_state": "QUEST_ACTIVE",
    "sets_path": "ESCORT_PATH"
  }
}
```

When the player kills GrotSnout, `COMBAT_PATH` is stored and the escort listener is deregistered. When the player escorts GrotSnout to the portal, `ESCORT_PATH` is stored and the kill listener is deregistered. Downstream components can branch on `path_choice` to deliver different encounters.

---

## 10. Creating a Quest (Step-by-Step)

### Step 1 — Design the state flow

Map each game event to a state transition:

```
NOT_STARTED   → zombie enters spawn area      → QUEST_ACTIVE
QUEST_ACTIVE  → player kills 3 zombies        → COMPLETED
```

### Step 2 — Write the metadata JSON

Assign a component ID to each trigger and objective, then build `state_mapping` and `components`:

```json
{
  "state_mapping": {
    "NOT_STARTED": ["trigger_proximity"],
    "QUEST_ACTIVE": ["obj_kill_zombies"]
  },
  "components": {
    "trigger_proximity": {
      "type": "LOCATION_PROXIMITY",
      "world": "event",
      "x": 0,
      "y": 64,
      "z": 0,
      "radius": 30
    },
    "obj_kill_zombies": {
      "objective_type": "KILL",
      "entity_type": "ZOMBIE",
      "required_kills": 3,
      "required_state": "QUEST_ACTIVE",
      "advance_state": "COMPLETED"
    }
  },
  "start_trigger": "Zombie Threat Near Spawn"
}
```

### Step 3 — Insert into quest_definitions

```sql
INSERT INTO quest_definitions (quest_id, name, description, category, repeatable, cooldown_minutes, metadata)
VALUES (
  'dev_undead_bounty',
  'Undead Bounty',
  'A horde of zombies has been spotted near spawn. Eliminate 3 of them.',
  'combat',
  TRUE,
  60,
  '{
    "state_mapping": {
      "NOT_STARTED": ["trigger_proximity"],
      "QUEST_ACTIVE": ["obj_kill_zombies"]
    },
    "components": {
      "trigger_proximity": {
        "type": "LOCATION_PROXIMITY",
        "world": "event",
        "x": 0, "y": 64, "z": 0,
        "radius": 30
      },
      "obj_kill_zombies": {
        "objective_type": "KILL",
        "entity_type": "ZOMBIE",
        "required_kills": 3,
        "required_state": "QUEST_ACTIVE",
        "advance_state": "COMPLETED"
      }
    },
    "start_trigger": "Zombie Threat Near Spawn"
  }'
);
```

### Step 4 — Insert objectives and rewards

```sql
INSERT INTO quest_definition_objectives (quest_id, objective_id, type, target, required_amount, description, sort_order)
VALUES ('dev_undead_bounty', 'kill_zombies', 'KILL', 'ZOMBIE', 3, 'Kill 3 zombies near spawn', 1);

INSERT INTO quest_definition_rewards (quest_id, reward_id, type, value, amount)
VALUES
  ('dev_undead_bounty', 'bounty_xp',   'EXPERIENCE', NULL,           500),
  ('dev_undead_bounty', 'bounty_loot', 'ITEM',       'ROTTEN_FLESH', 3);
```

### Step 5 — Load without restart

```
/quest reload reset
```

This calls `QuestManager.cleanupQuests()` then `QuestManager.initializeQuests()`, which reads the current DB state and creates `DataDrivenQuest` instances for all definitions.

### Step 6 — Verify and assign

```
/quest list                                    -- confirm quest appears
/quest journal assign dev_undead_bounty Steve  -- place player into quest
/quest progress dev_undead_bounty              -- check Steve's state
```

---

## 11. Console Admin Commands

| Command | Permission | Description |
|---|---|---|
| `/quest list` | `rvnkquests.command.list` | List all registered quests |
| `/quest reload reset` | `rvnkquests.admin.reload` | Reload config + reinitialize all quest listeners from DB |
| `/quest reload reseed` | `rvnkquests.admin.reload` | Re-seed built-in definitions then reinitialize |
| `/quest journal assign <id> <player>` | `rvnkquests.journal.staff` | Set player state to QUEST_ACTIVE; works offline |
| `/quest journal unassign <id> <player>` | `rvnkquests.journal.staff` | Clear journal entries; preserves quest progress |
| `/quest journal view <id> <player>` | `rvnkquests.journal.other` | View journal entries for a quest |
| `/quest journal remove <id> <player>` | `rvnkquests.journal.remove` | Delete all journal entries for a quest |
| `/quest progress <id> [player]` | `rvnkquests.command.progress` | View current quest state and objective counts |
| `/quest state <id> <state>` | `rvnkquests.command.state` | Manually set a quest state |
| `/quest reset <id> [player]` | `rvnkquests.command.reset` | Reset quest progress to NOT_STARTED |
| `/quest mobs list` | `rvnkquests.command.mobs` | List all active quest mobs in loaded chunks |
| `/quest mobs kill` | `rvnkquests.command.mobs` | Despawn all active quest mobs |
| `/quest validate` | `rvnkquests.command.validate` | Check quest definitions for configuration errors |
| `/quest seed` | `rvnkquests.command.seed` | Manually trigger the definition seeder |

All commands support console execution — no player required.

---

## 12. Full Quest Examples

### Example 1 — dev_undead_bounty (Repeatable Kill Quest)

A repeatable bounty quest. Players kill 3 zombies near spawn. Resets every 60 minutes.

```sql
-- Quest definition
INSERT INTO quest_definitions (quest_id, name, description, category, repeatable, cooldown_minutes, metadata)
VALUES (
  'dev_undead_bounty',
  'Undead Bounty',
  'A horde of zombies has been spotted near spawn. Kill 3 of them to earn a bounty reward.',
  'combat',
  TRUE,
  60,
  '{
    "state_mapping": {
      "NOT_STARTED": ["trigger_spawn_proximity"],
      "QUEST_ACTIVE": ["obj_kill_zombies"]
    },
    "components": {
      "trigger_spawn_proximity": {
        "type": "LOCATION_PROXIMITY",
        "world": "event",
        "x": 0, "y": 64, "z": 0,
        "radius": 30
      },
      "obj_kill_zombies": {
        "objective_type": "KILL",
        "entity_type": "ZOMBIE",
        "required_kills": 3,
        "required_state": "QUEST_ACTIVE",
        "advance_state": "COMPLETED"
      }
    },
    "start_trigger": "Undead near spawn"
  }'
);

-- Display objectives
INSERT INTO quest_definition_objectives (quest_id, objective_id, type, target, required_amount, description, sort_order)
VALUES ('dev_undead_bounty', 'kill_3_zombies', 'KILL', 'ZOMBIE', 3, 'Kill 3 zombies near spawn', 1);

-- Rewards
INSERT INTO quest_definition_rewards (quest_id, reward_id, type, value, amount)
VALUES
  ('dev_undead_bounty', 'bounty_xp',   'EXPERIENCE', NULL,           500),
  ('dev_undead_bounty', 'bounty_loot', 'ITEM',       'ROTTEN_FLESH', 3);
```

### Example 2 — dev_shrine_assault (Encounter Quest)

A one-shot encounter quest. Staff assign players to it; skeletons named "Test Revenant" spawn at spawn coordinates when the player approaches.

```sql
-- Quest definition
INSERT INTO quest_definitions (quest_id, name, description, category, repeatable, cooldown_minutes, metadata)
VALUES (
  'dev_shrine_assault',
  'Shrine Assault',
  'A dark shrine has appeared near spawn. Three revenants guard it. Destroy them.',
  'combat',
  FALSE,
  0,
  '{
    "state_mapping": {
      "QUEST_ACTIVE": ["obj_revenant_encounter"]
    },
    "components": {
      "obj_revenant_encounter": {
        "objective_type": "ENCOUNTER",
        "entity_type": "SKELETON",
        "spawn_count": 3,
        "required_kills": 3,
        "custom_name": "Test Revenant",
        "world": "event",
        "x": 0, "y": 64, "z": 0,
        "trigger_radius": 20,
        "spawn_radius": 5,
        "prevent_infighting": true,
        "required_state": "QUEST_ACTIVE",
        "advance_state": "COMPLETED"
      }
    },
    "start_trigger": "Shrine Revenant"
  }'
);

-- Display objectives
INSERT INTO quest_definition_objectives (quest_id, objective_id, type, target, required_amount, description, sort_order)
VALUES ('dev_shrine_assault', 'kill_revenants', 'KILL', 'SKELETON', 3, 'Destroy the 3 Test Revenants', 1);

-- Rewards
INSERT INTO quest_definition_rewards (quest_id, reward_id, type, value, amount)
VALUES
  ('dev_shrine_assault', 'shrine_xp',   'EXPERIENCE', NULL,         750),
  ('dev_shrine_assault', 'shrine_bone', 'ITEM',       'BONE_BLOCK', 1);

-- Staff assign to a player
-- /quest journal assign dev_shrine_assault Steve
```

### Example 3 — dev_iron_salvage (Collect Quest with Consume)

Staff assign players to collect 5 iron ingots; ingots are consumed on completion.

```sql
-- Quest definition
INSERT INTO quest_definitions (quest_id, name, description, category, repeatable, cooldown_minutes, metadata)
VALUES (
  'dev_iron_salvage',
  'Iron Salvage',
  'The forge needs iron. Gather 5 iron ingots from any source and bring them to the collection point.',
  'gathering',
  FALSE,
  0,
  '{
    "state_mapping": {
      "QUEST_ACTIVE": ["obj_collect_iron"]
    },
    "components": {
      "obj_collect_iron": {
        "objective_type": "COLLECT",
        "item_type": "IRON_INGOT",
        "required_amount": 5,
        "consume": true,
        "required_state": "QUEST_ACTIVE",
        "advance_state": "COMPLETED"
      }
    },
    "start_trigger": "Iron Salvage"
  }'
);

-- Display objectives
INSERT INTO quest_definition_objectives (quest_id, objective_id, type, target, required_amount, description, sort_order)
VALUES ('dev_iron_salvage', 'collect_iron', 'COLLECT', 'IRON_INGOT', 5, 'Collect 5 iron ingots', 1);

-- Rewards
INSERT INTO quest_definition_rewards (quest_id, reward_id, type, value, amount)
VALUES
  ('dev_iron_salvage', 'salvage_xp',   'EXPERIENCE', NULL,         300),
  ('dev_iron_salvage', 'salvage_steel', 'ITEM',       'IRON_BLOCK', 1);
```

---

## Reference

- Source: `src/main/java/org/fourz/RVNKQuests/quest/DataDrivenQuest.java`
- Factory: `src/main/java/org/fourz/RVNKQuests/factory/QuestComponentFactory.java`
- Seeder: `src/main/java/org/fourz/RVNKQuests/data/QuestDefinitionSeeder.java`
- Schema: `src/main/resources/schema/mysql.sql`
- Staff commands: `src/main/java/org/fourz/RVNKQuests/command/QuestJournalSubCommand.java`
