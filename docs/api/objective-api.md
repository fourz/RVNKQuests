# Objective API — `IObjectiveService` & `IPlayerQuestService`

## `IObjectiveService`

**Package**: `org.fourz.RVNKQuests.service`
**Accessor**: `RVNKQuests#getObjectiveService()`

Manages objective progress evaluation, condition checking, and group-based objective sets.

---

### Progress Operations

#### `getProgress(UUID, String questId, String objectiveId) → CompletableFuture<Integer>`

Get the player's current progress count for an objective.

#### `incrementProgress(UUID, String questId, String objectiveId, int amount) → CompletableFuture<Integer>`

Increment progress and return the new total. Useful when implementing custom objective types.

```java
// Player broke a block — increment MINE objective
objectiveService.incrementProgress(player.getUniqueId(), "gather_wool", "obj_wool", 1)
    .thenAccept(newTotal -> {
        if (newTotal >= 32) {
            // objective complete — handled internally, but you can react here
        }
    });
```

#### `setProgress(UUID, String questId, String objectiveId, int value) → CompletableFuture<Boolean>`

Set progress to an exact value. Use for synchronization with external systems.

#### `isObjectiveComplete(UUID, String questId, String objectiveId) → CompletableFuture<Boolean>`

Check whether an objective is done.

#### `completeObjective(UUID, String questId, String objectiveId) → CompletableFuture<Boolean>`

Manually mark an objective complete regardless of current progress count.

---

### Condition Evaluation

#### `evaluateCondition(UUID, ObjectiveCondition) → CompletableFuture<Boolean>`

Evaluate a single condition for a player (inventory check, permission, time, etc.).

#### `evaluateConditions(UUID, List<ObjectiveCondition>) → CompletableFuture<Boolean>`

Evaluate multiple conditions with AND semantics — all must pass.

#### `evaluateConditionWithDetails(UUID, ObjectiveCondition) → CompletableFuture<ConditionResult>`

Evaluate with full diagnostic output:

```java
record ConditionResult(
    boolean passed,
    ObjectiveCondition condition,
    String message,           // human-readable result
    Map<String, Object> data  // contextual values (current vs. required)
)
```

---

### Group Operations

Objective groups allow related objectives to be tracked collectively (e.g., "complete any 3 of these 5 tasks").

#### `isGroupComplete(UUID, String questId, String groupId) → CompletableFuture<Boolean>`

#### `getGroupStatus(UUID, String questId, String groupId) → CompletableFuture<GroupStatus>`

```java
record GroupStatus(
    String groupId,
    int totalObjectives,
    int completedObjectives,
    int requiredObjectives,    // how many must be done
    boolean complete,
    List<String> completedIds,
    List<String> remainingIds
)
```

#### `getActiveObjectives(UUID, String questId) → CompletableFuture<List<ObjectiveDTO>>`

Objectives currently in progress (not yet complete, but unlocked).

#### `getUpcomingObjectives(UUID, String questId) → CompletableFuture<List<ObjectiveDTO>>`

Objectives that are locked pending completion of earlier steps.

#### `initializeGroupProgress(UUID, String questId, String groupId) → CompletableFuture<Boolean>`

Set up tracking data when a player enters a group. Called internally; available for custom integrations.

#### `resetGroupProgress(UUID, String questId, String groupId) → CompletableFuture<Boolean>`

Reset all objectives in a group for a player.

---

## `IPlayerQuestService`

**Accessor**: `RVNKQuests#getQuestProgressService()`

Low-level player progress persistence — session management, quest states, and objective counts.
Prefer `IQuestService` for high-level operations; use this for custom tracking or admin tools.

---

### Session Management

#### `loadPlayerProgress(UUID) → CompletableFuture<Void>`

Load a player's progress from storage into the in-memory cache. Called automatically on join.

#### `saveAndUnloadPlayerProgress(UUID) → CompletableFuture<Void>`

Flush cache to storage and release it. Called automatically on quit.

---

### Quest Progress

#### `getProgress(UUID, String questId) → CompletableFuture<Optional<QuestProgressDTO>>`

Get the full progress record for one quest.

#### `getQuestState(UUID, String questId) → CompletableFuture<QuestState>`

Get the current state enum value.

#### `updateQuestState(UUID, String questId, QuestState) → CompletableFuture<Boolean>`

Write a new state value.

#### `setPathChoice(UUID, String questId, String pathId) → CompletableFuture<Boolean>`

Record which branch a player chose in a branching quest.

#### `resetQuestProgress(UUID, String questId) → CompletableFuture<Boolean>`

Wipe all progress data for one quest.

#### `getAllProgress(UUID) → CompletableFuture<List<QuestProgressDTO>>`

All progress records for a player across every quest.

---

### Objective Progress

#### `getObjectiveProgress(UUID, String questId, String objectiveId) → CompletableFuture<Optional<QuestObjectiveProgressDTO>>`

#### `incrementObjectiveProgress(UUID, String questId, String objectiveId, int amount) → CompletableFuture<Integer>`

#### `initializeObjective(UUID, String questId, String objectiveId) → CompletableFuture<Boolean>`

Create a progress row for an objective when a player starts a quest.

#### `completeObjective(UUID, String questId, String objectiveId) → CompletableFuture<Boolean>`

#### `getAllObjectives(UUID, String questId) → CompletableFuture<List<QuestObjectiveProgressDTO>>`

---

### Reward Tracking

#### `hasClaimedReward(UUID, String questId) → CompletableFuture<Boolean>`

#### `claimReward(UUID, String questId) → CompletableFuture<Boolean>`

Mark rewards as claimed (call after `IRewardService.deliverRewards` succeeds).

---

### Service Status

#### `isInFallbackMode() → boolean`

#### `flush() → CompletableFuture<Void>`

Force-write all dirty cache entries to storage.

#### `shutdown()`

---

## `ObjectiveDTO` Reference

```
ObjectiveDTO(
  String objectiveId     — unique within the quest
  ObjectiveType type     — what action the player must perform
  String target          — entity/item/block type, or custom identifier
  int requiredAmount     — quantity needed (minimum 1)
  String description     — optional player-facing text
  int order              — display/evaluation order (0-based)
  Map<String,String> metadata
)
```

**Factory**: `ObjectiveDTO.create(id, type, target, amount)`
**Builder**: `ObjectiveDTO.builder()...build()`

---

## `ObjectiveType` Reference

| Type | Player action | `target` field |
|------|--------------|----------------|
| `KILL` | Kill entities | Entity type (`"ZOMBIE"`) |
| `COLLECT` | Pick up items | Material name (`"DIAMOND"`) |
| `REACH` | Arrive at location | Location identifier |
| `TALK_TO` | Interact with NPC | NPC ID or entity UUID |
| `INTERACT` | Right-click block/object | Block type or interaction ID |
| `CRAFT` | Craft items | Material name |
| `MINE` | Break blocks | Block type |
| `PLACE` | Place blocks | Block type |
| `USE_ITEM` | Use item | Material name |
| `DELIVER` | Deliver items | Delivery point ID |
| `DISCOVER` | Enter discovery zone | Discovery point ID |
| `CUSTOM` | Plugin-defined | Custom identifier |
