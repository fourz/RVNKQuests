# RVNKQuests

A powerful, modular, event-driven quest system for Minecraft servers that creates immersive, story-rich adventures. RVNKQuests features a state-based quest progression system, dynamic triggers, lore integration, and flexible reward structures. This project is actively developed at [GitHub: fourz/RVNKQuests](https://github.com/fourz/RVNKQuests) and is designed to integrate seamlessly with RVNKLore.

## Features

- **State-Based Quest System**: Quests progress through defined states, such as `NOT_STARTED`, `TRIGGER_FOUND`, `QUEST_ACTIVE`, `OBJECTIVE_FOUND`, and `COMPLETED`.
- **Dynamic Triggers**: Quests can activate through world exploration, NPC interactions, or admin commands.
- **Location & Lore Integration**: Seamless collaboration with RVNKLore for referencing or creating lore entries. Quests can require players to discover specific lore items or locate areas tagged with custom lore.
- **Environment Integration**: Quests react to player movement, region entry, item pickups, and other server events.
- **Flexible Rewards**: Reward players with XP, items, custom lore objects, tokens, currency, or even server-wide events.
- **Admin Control**: Comprehensive commands for quest creation, management, and debugging.
- **Extensible Architecture**: Easily add or modify quests, triggers, and objectives via code or config.

## Repository & Setup

The official repository is located at:

[GitHub: fourz/RVNKQuests](https://github.com/fourz/RVNKQuests)

### To clone and build:

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
| `/quest item <item_name>`              | Get a quest item               | `rvnkquests.command.item`   |
| `/quest trigger <quest_id> [here|around]` | Trigger a quest                | `rvnkquests.command.trigger`|
| `/quest state <quest_id> <state>`      | Change a quest's state         | `rvnkquests.command.state`  |
| `/quest reload`                        | Reload plugin configuration    | `rvnkquests.command.reload` |
| `/quest list`                          | List all available quests      | `rvnkquests.command.list`   |
| `/quest info <quest_id>`               | Show detailed quest information| `rvnkquests.command.info`   |
| `/quest reset <quest_id> [player]`     | Reset quest progress           | `rvnkquests.command.reset`  |

## Configuration

### Main Configuration

```yaml
general:
  logLevel: INFO
  defaultQuestWorld: world
  enableQuestParticles: true
  questCooldownMinutes: 30

storage:
  type: sqlite
  database: quests.db
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

1. **Create Quest Class**: Implement `Quest` or extend a base class like `AbstractQuest`.
2. **Define States & Logic**: Outline triggers, objectives, and success/failure conditions.
3. **Create Listeners**: Use custom or shared listeners to track relevant events.
4. **Register & Configure**: In `QuestManager.initializeQuests()`, add your new quest, and configure it in `quests.yml`.

### Building

```bash
mvn clean package
```

The compiled JAR appears in `target/`.

## Requirements

- Java 21+
- Spigot/Paper 1.17+

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