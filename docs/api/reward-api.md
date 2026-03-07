# Reward API — `IRewardService`

**Package**: `org.fourz.RVNKQuests.service`
**Accessor**: `RVNKQuests#getRewardService()`

Handles reward validation and delivery to players. Supports built-in and custom reward types.

---

## Reward Types

| `RewardType` | What is delivered | `value` field |
|---|---|---|
| `ITEM` | Physical items | Item serialization string or material ID |
| `EXPERIENCE` | XP points | Amount as string (e.g. `"500"`) |
| `CURRENCY` | TokenEconomy currency | Amount as string |
| `PERMISSION` | LuckPerms permission grant | Permission node |
| `COMMAND` | Server command | Command string (supports `{player}` placeholder) |
| `TITLE` | Title/achievement display | Title configuration string |
| `QUEST_UNLOCK` | Makes another quest available | Quest ID to unlock |
| `LORE` | RVNKLore entry discovery | Lore entry ID |
| `CUSTOM` | Plugin-defined handler | Custom identifier |

---

## Single Reward Delivery

### `deliverReward(UUID, RewardDTO) → CompletableFuture<RewardDeliveryResult>`

Deliver one reward without quest context.

### `deliverReward(UUID, String questId, RewardDTO) → CompletableFuture<RewardDeliveryResult>`

Deliver one reward with quest context for tracking.

```java
RewardDTO reward = new RewardDTO(RewardType.EXPERIENCE, "500", "500 XP", null);

rewardService.deliverReward(player.getUniqueId(), "gather_wool", reward)
    .thenAccept(result -> {
        if (result.success()) {
            player.sendMessage("Reward delivered: " + result.message());
        } else {
            getLogger().warning("Reward failed [" + result.errorCode() + "]: " + result.message());
        }
    });
```

---

## Batch Reward Delivery

### `deliverRewards(UUID, List<RewardDTO>) → CompletableFuture<BatchRewardResult>`

Deliver multiple rewards. Stops on the first critical failure.

### `deliverRewards(UUID, String questId, List<RewardDTO>, boolean continueOnError) → CompletableFuture<BatchRewardResult>`

Deliver multiple rewards with quest context. Set `continueOnError = true` to attempt all rewards even if some fail.

```java
rewardService.deliverRewards(player.getUniqueId(), "gather_wool", quest.rewards(), false)
    .thenAccept(batch -> {
        if (batch.allSucceeded()) {
            player.sendMessage("All rewards received!");
        } else if (batch.hasFailures()) {
            batch.getFailedResults().forEach(r ->
                getLogger().warning("Failed: " + r.errorCode()));
        }
    });
```

---

## Validation

### `validateReward(UUID, RewardDTO) → CompletableFuture<RewardValidationResult>`

Check if a reward can be delivered (inventory space, required permissions, etc.) without actually delivering it.

### `validateRewards(UUID, List<RewardDTO>) → CompletableFuture<List<RewardValidationResult>>`

Validate a batch.

```java
rewardService.validateReward(player.getUniqueId(), itemReward)
    .thenAccept(v -> {
        if (!v.valid()) {
            player.sendMessage("Cannot receive reward: " + v.message());
            v.requirements().forEach(r -> player.sendMessage("  - " + r));
        }
    });
```

---

## Custom Reward Processors

Register a handler for `RewardType.CUSTOM` or override built-in types:

```java
// In your plugin's onEnable, after RVNKQuests loads:
rewardService.registerProcessor(RewardType.CUSTOM, new MyRewardProcessor());
```

### `RewardProcessor` interface

```java
package org.fourz.RVNKQuests.service;

public interface RewardProcessor {
    /**
     * Process and deliver a reward to a player.
     *
     * @param playerId the recipient
     * @param reward   the reward to deliver
     * @return a delivery result indicating success or failure
     */
    RewardDeliveryResult process(UUID playerId, RewardDTO reward);
}
```

**Example custom processor:**
```java
public class PrestigePointProcessor implements IRewardService.RewardProcessor {
    private final PrestigeService prestige;

    public PrestigePointProcessor(PrestigeService prestige) {
        this.prestige = prestige;
    }

    @Override
    public IRewardService.RewardDeliveryResult process(UUID playerId, RewardDTO reward) {
        int points = Integer.parseInt(reward.value());
        boolean ok = prestige.addPoints(playerId, points);
        if (ok) {
            return IRewardService.RewardDeliveryResult.success(reward,
                "Granted " + points + " prestige points");
        } else {
            return IRewardService.RewardDeliveryResult.failure(reward,
                "Prestige service unavailable", "PRESTIGE_UNAVAILABLE");
        }
    }
}
```

### `hasProcessor(RewardType) → boolean`

Check if a processor is registered before overriding.

### `getProcessor(RewardType) → RewardProcessor`

Retrieve a registered processor (returns `null` if not present).

### `getSupportedTypes() → List<RewardType>`

All types with registered processors.

---

## Service Status

### `isInFallbackMode() → boolean`

Returns `true` if external integrations (TokenEconomy, LuckPerms) are unavailable. ITEM and EXPERIENCE rewards still function in fallback mode.

---

## Result Records

### `RewardDeliveryResult`

```
record RewardDeliveryResult(
  boolean success,
  RewardDTO reward,
  String message,              // human-readable status
  String errorCode,            // null on success; error key on failure
  Map<String,Object> metadata  // additional delivery context
)
```

**Factory methods**: `success(reward, message)`, `success(reward, message, metadata)`,
`failure(reward, message, errorCode)`, `failure(reward, message, errorCode, metadata)`

### `BatchRewardResult`

```
record BatchRewardResult(
  int totalRewards,
  int successCount,
  int failureCount,
  List<RewardDeliveryResult> results,
  boolean stoppedEarly
)
```

**Helper methods**: `allSucceeded()`, `hasFailures()`, `getFailedResults()`, `getSuccessfulResults()`

### `RewardValidationResult`

```
record RewardValidationResult(
  boolean valid,
  RewardDTO reward,
  String message,
  List<String> requirements   // unmet requirements if invalid
)
```

**Factory methods**: `valid(reward)`, `invalid(reward, message, requirements)`, `invalid(reward, message, requirement)`

---

## `RewardDTO` Reference

```java
// RewardDTO is a record — construct directly:
new RewardDTO(RewardType.EXPERIENCE, "500", "500 XP", null)
new RewardDTO(RewardType.COMMAND, "give {player} diamond 1", "A diamond", null)
new RewardDTO(RewardType.QUEST_UNLOCK, "chapter_2", "Unlocks Chapter 2", null)
```

Check `docs/REWARD_PATTERNS.md` for extended configuration examples.
