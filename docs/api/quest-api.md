# Quest API — `IQuestService`

**Package**: `org.fourz.RVNKQuests.service`
**Implementation**: `org.fourz.RVNKQuests.quest.QuestManager`
**Accessor**: `RVNKQuests#getQuestManager()`

Manages quest definitions and player engagement with the quest system.

---

## Quest Definition Methods

### `registerQuest(QuestDTO) → CompletableFuture<Boolean>`

Register a new quest definition. Returns `true` if registration succeeded.

```java
QuestDTO quest = QuestDTO.builder()
    .questId("gather_wool")
    .name("The Shepherd's Request")
    .description("Collect 32 wool for the village shepherd.")
    .category("gathering")
    .objectives(List.of(
        ObjectiveDTO.create("obj_wool", ObjectiveType.COLLECT, "WHITE_WOOL", 32)
    ))
    .rewards(List.of(
        new RewardDTO(RewardType.EXPERIENCE, "500", "500 XP", null)
    ))
    .build();

questService.registerQuest(quest).thenAccept(ok -> {
    if (ok) getLogger().info("Quest registered");
});
```

### `unregisterQuest(String questId) → CompletableFuture<Boolean>`

Remove a quest definition. Players currently on this quest will have their state preserved but cannot advance.

### `getQuest(String questId) → CompletableFuture<Optional<QuestDTO>>`

Look up a quest by ID.

```java
questService.getQuest("gather_wool").thenAccept(opt ->
    opt.ifPresent(q -> getLogger().info("Found: " + q.name()))
);
```

### `getAllQuests() → CompletableFuture<List<QuestDTO>>`

Returns all registered quest definitions.

### `getActiveQuests() → CompletableFuture<List<QuestDTO>>`

Returns only quests currently available for players to start (prerequisites met globally).

### `getQuestCount() → CompletableFuture<Integer>`

Returns the total number of registered quests.

---

## Player Quest State Methods

### `getPlayerQuestState(UUID, String questId) → CompletableFuture<QuestState>`

Get a player's current state for a quest.

**Quest State Machine:**
```
NOT_STARTED → QUEST_ACTIVE → QUEST_COMPLETE → QUEST_REWARD_PENDING → QUEST_FINISHED
```

### `updatePlayerQuestState(UUID, String questId, QuestState) → CompletableFuture<Boolean>`

Directly update a player's quest state. Prefer `startQuest`, `completeQuest`, etc. for lifecycle transitions.

### `startQuest(UUID, String questId) → CompletableFuture<Boolean>`

Start a quest for a player. Returns `false` if the player cannot start it (see `canStartQuest`).

```java
questService.canStartQuest(player.getUniqueId(), "gather_wool")
    .thenCompose(canStart -> {
        if (canStart) return questService.startQuest(player.getUniqueId(), "gather_wool");
        return CompletableFuture.completedFuture(false);
    })
    .thenAccept(started -> {
        if (started) player.sendMessage("Quest started!");
    });
```

### `completeQuest(UUID, String questId) → CompletableFuture<Boolean>`

Mark a quest as complete for a player. Triggers reward delivery and journal recording.

### `abandonQuest(UUID, String questId) → CompletableFuture<Boolean>`

Allow a player to abandon an active quest. Returns `false` if the quest is not abandonable.

### `resetQuest(UUID, String questId) → CompletableFuture<Boolean>`

Reset all player progress on a quest back to `NOT_STARTED`. Use for admin corrections.

### `canStartQuest(UUID, String questId) → CompletableFuture<Boolean>`

Check whether a player meets all prerequisites and conditions to start a quest without actually starting it.

---

## Player Quest Retrieval

### `getPlayerActiveQuests(UUID) → CompletableFuture<List<QuestDTO>>`

All quests the player currently has in `QUEST_ACTIVE` state.

### `getPlayerCompletedQuests(UUID) → CompletableFuture<List<String>>`

IDs of all quests the player has completed at least once.

---

## Service Status

### `isInFallbackMode() → boolean`

Returns `true` if the service is using in-memory/YAML storage due to database unavailability.

### `reloadQuests() → CompletableFuture<Integer>`

Reload quest definitions from config/database. Returns the count of quests loaded.

### `shutdown()`

Called by the plugin on disable. Do not call from external plugins.

---

## `QuestDTO` Reference

```
QuestDTO(
  String questId          — unique identifier (e.g. "gather_wool")
  String name             — display name
  String description      — player-facing description
  String category         — optional category label
  boolean repeatable      — whether the quest can be done multiple times
  int cooldownMinutes     — cooldown between repeats (0 = no cooldown)
  List<ObjectiveDTO> objectives
  List<RewardDTO> rewards
  List<String> prerequisites — questIds that must be completed first
  Instant createdAt
  Map<String,Object> metadata — plugin-defined extra data
)
```

**Builder** (preferred):
```java
QuestDTO quest = QuestDTO.builder()
    .questId("daily_hunt")
    .name("Daily Hunt")
    .description("Kill 10 zombies.")
    .repeatable(true)
    .cooldownMinutes(1440)  // 24 hours
    .objectives(...)
    .rewards(...)
    .build();
```

**Fluent withers** for incremental construction:
- `withObjective(ObjectiveDTO)` / `withObjectives(List<ObjectiveDTO>)`
- `withReward(RewardDTO)` / `withRewards(List<RewardDTO>)`
- `withPrerequisite(String questId)`
- `withCategory(String)`
- `asRepeatable(int cooldownMinutes)`
