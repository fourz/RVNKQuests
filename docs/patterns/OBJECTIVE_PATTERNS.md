# Quest Objective Patterns

**RVNKQuests** - Standard patterns for implementing quest objectives with conditions, parallel execution, and nested hierarchies.

## Overview

The enhanced objective system provides:

- **Conditional Objectives** - Activate/count only when conditions are met
- **Parallel Objectives** - Multiple objectives active simultaneously
- **Nested Hierarchies** - Groups of groups for complex quest structures
- **Flexible Completion** - ALL, ANY, COUNT, or ORDERED completion types

---

## Core Components

### ObjectiveDTO (Basic)

Simple objective definition for straightforward requirements:

```java
// Kill 10 zombies
ObjectiveDTO killZombies = ObjectiveDTO.builder()
    .objectiveId("kill_zombies")
    .type(ObjectiveType.KILL)
    .target("ZOMBIE")
    .requiredAmount(10)
    .description("Slay 10 zombies")
    .build();

// Collect 5 diamonds
ObjectiveDTO collectDiamonds = ObjectiveDTO.create(
    "collect_diamonds", ObjectiveType.COLLECT, "DIAMOND", 5);
```

### EnhancedObjectiveDTO (With Conditions)

Objectives with activation and visibility conditions:

```java
// Night-only zombie hunting
EnhancedObjectiveDTO nightHunt = EnhancedObjectiveDTO.builder()
    .objectiveId("night_hunt")
    .type(ObjectiveType.KILL)
    .target("ZOMBIE")
    .requiredAmount(20)
    .description("Hunt zombies under the moonlight")
    .activationCondition(ObjectiveCondition.timeRange("night", 13000, 23000))
    .build();

// Location-based delivery
EnhancedObjectiveDTO delivery = EnhancedObjectiveDTO.builder()
    .objectiveId("deliver_gems")
    .type(ObjectiveType.DELIVER)
    .target("EMERALD")
    .requiredAmount(10)
    .activationCondition(ObjectiveCondition.location("merchant", "world", 100, 64, 200, 5))
    .build();
```

### ObjectiveCondition (Conditions)

Reusable conditions for complex quest logic:

```java
// Time-based
ObjectiveCondition nightTime = ObjectiveCondition.timeRange("night", 13000, 23000);

// Location-based
ObjectiveCondition atMerchant = ObjectiveCondition.location("merchant", "world", 100, 64, 200, 5);

// Quest state dependency
ObjectiveCondition introComplete = ObjectiveCondition.questState("intro_done", "intro_quest", "COMPLETED");

// Objective dependency
ObjectiveCondition gatherFirst = ObjectiveCondition.objectiveComplete("gather_done", "gather_materials");

// Item requirement
ObjectiveCondition hasPickaxe = ObjectiveCondition.itemRequired("has_pickaxe", "IRON_PICKAXE", 1);

// Inverted condition (NOT)
ObjectiveCondition notRaining = ObjectiveCondition.builder()
    .conditionId("not_raining")
    .type(ConditionType.WEATHER)
    .parameter("weather", "CLEAR")
    .build()
    .negate(); // Passes when NOT clear (i.e., raining)
```

### ObjectiveGroup (Grouping)

Organize objectives with different completion requirements:

```java
// ALL - Complete all objectives
ObjectiveGroup mainLine = ObjectiveGroup.all("main_line",
    killBoss,
    talkToNpc,
    returnHome
);

// ANY - Complete any one objective (branching paths)
ObjectiveGroup chooseAllegiance = ObjectiveGroup.any("choose_side",
    helpGuards,
    helpRebels
);

// COUNT - Complete at least N objectives
ObjectiveGroup gatherResources = ObjectiveGroup.count("gather", 2,
    collectWood,
    collectStone,
    collectIron // Any 2 of 3
);

// ORDERED - Complete in sequence
ObjectiveGroup tutorial = ObjectiveGroup.ordered("tutorial",
    openInventory,
    craftSword,
    killDummy
);
```

---

## Common Patterns

### Pattern 1: Sequential Quest (Story Line)

```java
// Main story quest with sequential objectives
ObjectiveGroup mainStory = ObjectiveGroup.builder()
    .groupId("lost_piglin_story")
    .name("The Lost Piglin")
    .completionType(CompletionType.ORDERED)
    .objective(ObjectiveDTO.create("find_piglin", ObjectiveType.DISCOVER, "PIGLIN_NPC", 1))
    .objective(ObjectiveDTO.create("talk_to_piglin", ObjectiveType.TALK_TO, "PIGLIN_NPC", 1))
    .objective(ObjectiveDTO.create("escort_piglin", ObjectiveType.REACH, "NETHER_PORTAL", 1))
    .objective(ObjectiveDTO.create("defeat_guardian", ObjectiveType.KILL, "PIGLIN_BRUTE", 1))
    .objective(ObjectiveDTO.create("reunion", ObjectiveType.REACH, "PIGLIN_HOME", 1))
    .build();
```

### Pattern 2: Parallel Gathering

```java
// Multiple items collected simultaneously
ObjectiveGroup gatherExpedition = ObjectiveGroup.builder()
    .groupId("gather_expedition")
    .name("Expedition Supplies")
    .completionType(CompletionType.ALL)
    .objective(ObjectiveDTO.create("get_food", ObjectiveType.COLLECT, "COOKED_BEEF", 32))
    .objective(ObjectiveDTO.create("get_torches", ObjectiveType.COLLECT, "TORCH", 64))
    .objective(ObjectiveDTO.create("get_pickaxes", ObjectiveType.COLLECT, "IRON_PICKAXE", 3))
    .objective(ObjectiveDTO.create("get_beds", ObjectiveType.COLLECT, "RED_BED", 2))
    .build();
```

### Pattern 3: Branching Paths (Choice)

```java
// Player chooses one path
ObjectiveGroup allegianceChoice = ObjectiveGroup.builder()
    .groupId("choose_faction")
    .name("Choose Your Allegiance")
    .completionType(CompletionType.ANY)
    .objective(ObjectiveDTO.create("join_guards", ObjectiveType.TALK_TO, "GUARD_CAPTAIN", 1)
        .withDescription("Speak with the Guard Captain to join the City Watch"))
    .objective(ObjectiveDTO.create("join_rebels", ObjectiveType.TALK_TO, "REBEL_LEADER", 1)
        .withDescription("Speak with the Rebel Leader to join the Resistance"))
    .objective(ObjectiveDTO.create("stay_neutral", ObjectiveType.TALK_TO, "MERCHANT", 1)
        .withDescription("Speak with the Merchant to remain neutral"))
    .build();
```

### Pattern 4: Conditional Time-Based

```java
// Night hunting with visibility unlocked by prior objective
EnhancedObjectiveDTO nightHunt = EnhancedObjectiveDTO.builder()
    .objectiveId("night_hunt")
    .type(ObjectiveType.KILL)
    .target("ZOMBIE")
    .requiredAmount(25)
    .description("Hunt undead during the night")
    .activationCondition(ObjectiveCondition.timeRange("night", 13000, 23000))
    .visibilityCondition(ObjectiveCondition.objectiveComplete("prereq", "learn_hunting"))
    .build();
```

### Pattern 5: Nested Hierarchies (Epic Quest)

```java
// Epic quest with phases
ObjectiveGroup phase1 = ObjectiveGroup.ordered("phase1",
    findClue1, talkToWitness, decipherMap);

ObjectiveGroup phase2 = ObjectiveGroup.all("phase2",
    gatherSupplies, recruitAlly, craftWeapon);

ObjectiveGroup phase3 = ObjectiveGroup.ordered("phase3",
    infiltrateCastle, defeatMinions, confrontBoss);

// Epic combines all phases
ObjectiveGroup epicQuest = ObjectiveGroup.builder()
    .groupId("epic_adventure")
    .name("The Forgotten Kingdom")
    .completionType(CompletionType.ORDERED)
    .subGroup(phase1)
    .subGroup(phase2)
    .subGroup(phase3)
    .build();
```

### Pattern 6: Optional Bonus Objectives

```java
// Main quest with optional bonus
ObjectiveGroup dungeonRun = ObjectiveGroup.builder()
    .groupId("dungeon_run")
    .name("Clear the Dungeon")
    .completionType(CompletionType.ALL)
    // Required objectives
    .objective(ObjectiveDTO.create("clear_rooms", ObjectiveType.KILL, "ANY_HOSTILE", 50))
    .objective(ObjectiveDTO.create("defeat_boss", ObjectiveType.KILL, "DUNGEON_BOSS", 1))
    .objective(ObjectiveDTO.create("find_treasure", ObjectiveType.INTERACT, "TREASURE_CHEST", 1))
    .build();

// Separate optional group
ObjectiveGroup bonusObjectives = ObjectiveGroup.builder()
    .groupId("dungeon_bonus")
    .name("Bonus Challenges")
    .completionType(CompletionType.COUNT)
    .requiredCount(0) // None required
    .objective(EnhancedObjectiveDTO.builder()
        .objectiveId("speed_run")
        .type(ObjectiveType.CUSTOM)
        .description("Complete dungeon in under 10 minutes")
        .optional(true)
        .build())
    .objective(EnhancedObjectiveDTO.builder()
        .objectiveId("no_damage")
        .type(ObjectiveType.CUSTOM)
        .description("Complete without taking damage")
        .optional(true)
        .build())
    .build();
```

---

## Condition Types Reference

| Type | Parameters | Description |
|------|------------|-------------|
| `TIME_RANGE` | startTime, endTime, isRealTime | In-game or real-world time window |
| `LOCATION` | world, x, y, z, radius | Within radius of coordinates |
| `QUEST_STATE` | questId, requiredState | Another quest in specific state |
| `OBJECTIVE_COMPLETE` | questId, objectiveId | Another objective completed |
| `PERMISSION` | permission | Player has permission node |
| `ITEM_IN_INVENTORY` | itemType, minAmount | Player possesses items |
| `PLAYER_LEVEL` | minLevel, maxLevel | XP level range |
| `WEATHER` | weather (CLEAR/RAIN/THUNDER) | Current weather condition |
| `EQUIPMENT` | equipmentSlot, itemType | Specific item equipped |
| `CUSTOM` | evaluatorClass, ... | Plugin-defined logic |

---

## Group Completion Types

| Type | Behavior |
|------|----------|
| `ALL` | Every objective/subgroup must complete |
| `ANY` | Any single objective/subgroup completes the group |
| `COUNT` | At least `requiredCount` objectives must complete |
| `ORDERED` | All must complete in sequence; next activates after previous |

---

## Service Usage

### IObjectiveService Interface

```java
// Get progress
objectiveService.getObjectiveProgress(playerUuid, questId, objectiveId)
    .thenAccept(progressOpt -> {
        if (progressOpt.isPresent()) {
            QuestObjectiveProgressDTO progress = progressOpt.get();
            // Handle progress
        }
    });

// Increment progress
objectiveService.incrementProgress(playerUuid, questId, "kill_zombies", 1)
    .thenAccept(updated -> {
        if (updated.completed()) {
            player.sendMessage("Objective complete!");
        }
    });

// Check group completion
objectiveService.getGroupStatus(playerUuid, questId, objectiveGroup)
    .thenAccept(status -> {
        if (status.completed()) {
            // Group finished
        } else {
            player.sendMessage("Progress: " + status.completedCount() + "/" + status.requiredCount());
        }
    });

// Get active objectives (respects order and conditions)
objectiveService.getActiveObjectives(playerUuid, questId, group)
    .thenAccept(activeList -> {
        activeList.forEach(obj -> showInUI(obj));
    });
```

### ConditionEvaluator

```java
ConditionEvaluator evaluator = new ConditionEvaluator(plugin);

// Simple evaluation
evaluator.evaluate(playerUuid, condition)
    .thenAccept(passed -> {
        if (passed) {
            // Condition met
        }
    });

// Detailed evaluation
evaluator.evaluateWithDetails(playerUuid, condition)
    .thenAccept(result -> {
        if (!result.passed()) {
            player.sendMessage("Requirement not met: " + result.message());
            player.sendMessage("You have: " + result.actualValue());
            player.sendMessage("You need: " + result.expectedValue());
        }
    });
```

---

## Best Practices

1. **Use Descriptive IDs** - `kill_zombies_night` not `obj1`
2. **Keep Groups Focused** - One logical unit per group
3. **Limit Nesting Depth** - 3 levels maximum for readability
4. **Test Conditions** - Verify edge cases (midnight rollover, offline players)
5. **Document Dependencies** - Clear prerequisite chains
6. **Handle Fallback** - Check `isInFallbackMode()` for degraded operation

---

## Migration from Legacy

```java
// Legacy objective (hardcoded listener)
// Now becomes:
ObjectiveDTO modern = ObjectiveDTO.builder()
    .objectiveId("legacy_converted")
    .type(ObjectiveType.KILL)
    .target("ZOMBIE")
    .requiredAmount(10)
    .metadata(Map.of("legacyId", "old_zombie_quest"))
    .build();
```

---

**Last Updated**: January 31, 2026  
**Component**: RVNKQuests  
**Author**: java-architect
