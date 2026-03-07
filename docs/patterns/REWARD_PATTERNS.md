# Quest Reward Patterns

**RVNKQuests** - Standard patterns for implementing quest rewards with async delivery, validation, and multiple reward types.

## Overview

The reward system provides:

- **Multi-Type Rewards** - Items, XP, currency, commands, permissions, titles
- **Async Delivery** - Non-blocking reward processing via CompletableFuture
- **Pluggable Processors** - Custom reward types via RewardProcessor interface
- **Batch Operations** - Deliver multiple rewards with error handling
- **Integration Support** - TokenEconomy, LuckPerms, RVNKLore hooks

---

## Core Components

### RewardDTO (Reward Definition)

Define rewards using the immutable RewardDTO record:

```java
// Simple XP reward
RewardDTO xpReward = RewardDTO.experience("xp_bonus", 500);

// Currency/token reward
RewardDTO tokenReward = RewardDTO.currency("token_reward", 100);

// Item reward
RewardDTO itemReward = RewardDTO.item("diamond_reward", "DIAMOND", 5);

// Command reward (with placeholders)
RewardDTO cmdReward = RewardDTO.command("broadcast", "say %player% completed quest!");

// Builder for complex rewards
RewardDTO customReward = RewardDTO.builder()
    .rewardId("special_reward")
    .type(RewardType.ITEM)
    .value("NETHERITE_SWORD")
    .amount(1)
    .description("A legendary sword")
    .metadata(Map.of(
        "enchantments", "SHARPNESS:5,UNBREAKING:3",
        "displayName", "§6Legendary Blade"
    ))
    .build();
```

### RewardType (Supported Types)

| Type | Description | Value Format |
|------|-------------|--------------|
| `ITEM` | Physical item delivery | Item ID (e.g., "DIAMOND", "NETHERITE_SWORD") |
| `EXPERIENCE` | XP points | Amount as string |
| `CURRENCY` | TokenEconomy tokens | Amount as string |
| `PERMISSION` | LuckPerms permission | Permission node |
| `COMMAND` | Server command | Command with %player% placeholder |
| `TITLE` | Title display | Title configuration |
| `QUEST_UNLOCK` | Unlock another quest | Quest ID |
| `LORE` | RVNKLore entry | Lore entry ID |
| `CUSTOM` | Plugin-defined | Custom identifier |

---

## Service Usage

### IRewardService Interface

```java
// Get service from ServiceRegistry
IRewardService rewardService = serviceRegistry.get(IRewardService.class);

// Deliver single reward
rewardService.deliverReward(playerUuid, reward)
    .thenAccept(result -> {
        if (result.success()) {
            player.sendMessage("§aReward delivered: " + result.message());
        } else {
            player.sendMessage("§cFailed: " + result.message());
        }
    });

// Deliver with quest context (for tracking)
rewardService.deliverReward(playerUuid, questId, reward)
    .thenAccept(result -> handleResult(result));

// Batch delivery
rewardService.deliverRewards(playerUuid, questId, rewardList, true)
    .thenAccept(batchResult -> {
        player.sendMessage("§aDelivered " + batchResult.successCount() + 
                          "/" + batchResult.totalCount() + " rewards");
    });

// Validate before delivery
rewardService.validateReward(playerUuid, reward)
    .thenAccept(validationResult -> {
        if (validationResult.valid()) {
            // Safe to deliver
        } else {
            player.sendMessage("§cCannot claim: " + validationResult.reason());
        }
    });
```

### RewardDeliveryResult

```java
// Check delivery result
if (result.success()) {
    // Reward delivered successfully
    String message = result.message();        // "500 XP granted"
    String rewardId = result.reward().rewardId();
} else {
    // Delivery failed
    String errorCode = result.errorCode();    // "INVENTORY_FULL"
    String message = result.message();        // "Player inventory full"
    boolean canRetry = result.canRetry();     // true if temporary failure
}
```

### BatchRewardResult

```java
BatchRewardResult batch = ...;

int total = batch.totalCount();           // Total rewards attempted
int success = batch.successCount();       // Successfully delivered
int failed = batch.failedCount();         // Failed deliveries
List<RewardDeliveryResult> results = batch.results();  // Individual results
boolean allSuccess = batch.allSuccessful();  // True if no failures
```

---

## Common Patterns

### Pattern 1: Quest Completion Rewards

```java
// Define quest with rewards
QuestDTO quest = QuestDTO.builder()
    .questId("dragon_slayer")
    .name("Dragon Slayer")
    .reward(RewardDTO.experience("xp_reward", 1000))
    .reward(RewardDTO.item("dragon_egg", "DRAGON_EGG", 1))
    .reward(RewardDTO.currency("tokens", 500))
    .reward(RewardDTO.command("announce", "broadcast %player% slew the dragon!"))
    .build();

// On quest completion
void onQuestComplete(Player player, QuestDTO quest) {
    rewardService.deliverRewards(
        player.getUniqueId(),
        quest.questId(),
        quest.rewards(),
        true  // Continue on error
    ).thenAccept(result -> {
        if (result.allSuccessful()) {
            player.sendMessage("§aAll rewards claimed!");
        } else {
            player.sendMessage("§eSome rewards pending: " + result.failedCount());
        }
    });
}
```

### Pattern 2: Tiered Rewards

```java
// Bronze tier
List<RewardDTO> bronzeRewards = List.of(
    RewardDTO.experience("bronze_xp", 100),
    RewardDTO.item("bronze_medal", "GOLD_NUGGET", 1)
);

// Silver tier
List<RewardDTO> silverRewards = List.of(
    RewardDTO.experience("silver_xp", 250),
    RewardDTO.item("silver_medal", "IRON_INGOT", 1),
    RewardDTO.currency("silver_tokens", 50)
);

// Gold tier
List<RewardDTO> goldRewards = List.of(
    RewardDTO.experience("gold_xp", 500),
    RewardDTO.item("gold_medal", "GOLD_INGOT", 1),
    RewardDTO.currency("gold_tokens", 150),
    RewardDTO.permission("gold_perm", "quest.vip.gold")
);

// Determine tier and deliver
List<RewardDTO> rewards = switch (completionTime) {
    case t when t < 60 -> goldRewards;
    case t when t < 120 -> silverRewards;
    default -> bronzeRewards;
};
```

### Pattern 3: Conditional Rewards

```java
// Reward based on player state
RewardDTO bonusReward = RewardDTO.builder()
    .rewardId("bonus")
    .type(RewardType.EXPERIENCE)
    .value("1000")
    .amount(1000)
    .metadata(Map.of("condition", "no_deaths"))
    .build();

// Validate condition before delivery
if (questProgress.getDeathCount() == 0) {
    rewardService.deliverReward(playerId, bonusReward);
}
```

### Pattern 4: Delayed/Scheduled Rewards

```java
// Schedule reward for later
Bukkit.getScheduler().runTaskLater(plugin, () -> {
    rewardService.deliverReward(playerId, delayedReward)
        .thenAccept(result -> {
            if (result.success()) {
                // Notify player if online
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.sendMessage("§aDelayed reward received!");
                }
            }
        });
}, 20L * 60); // 1 minute delay
```

### Pattern 5: Fallback Rewards

```java
// Handle unavailable integrations
rewardService.deliverReward(playerId, currencyReward)
    .thenAccept(result -> {
        if (!result.success() && "PROCESSOR_UNAVAILABLE".equals(result.errorCode())) {
            // TokenEconomy not installed, give XP instead
            RewardDTO fallback = RewardDTO.experience("fallback_xp", 
                currencyReward.amount() * 10);
            rewardService.deliverReward(playerId, fallback);
        }
    });
```

---

## Custom Reward Processors

### Creating a Custom Processor

```java
public class MyCustomProcessor implements RewardProcessor {
    
    @Override
    public CompletableFuture<RewardDeliveryResult> deliver(UUID playerId, RewardDTO reward) {
        return CompletableFuture.supplyAsync(() -> {
            // Custom delivery logic
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                return RewardDeliveryResult.failure(reward, "Player offline", "PLAYER_OFFLINE");
            }
            
            // Do custom reward processing
            String customValue = reward.value();
            // ... custom logic ...
            
            return RewardDeliveryResult.success(reward, "Custom reward delivered!");
        });
    }
    
    @Override
    public CompletableFuture<ValidationResult> validate(UUID playerId, RewardDTO reward) {
        return CompletableFuture.completedFuture(
            new ValidationResult(true, "Valid", Map.of())
        );
    }
    
    @Override
    public boolean isAvailable() {
        return true; // Or check for required dependencies
    }
    
    @Override
    public boolean canRetry(RewardDeliveryResult failedResult) {
        return "PLAYER_OFFLINE".equals(failedResult.errorCode());
    }
}

// Register custom processor
rewardService.registerProcessor(RewardType.CUSTOM, new MyCustomProcessor());
```

---

## Integration Patterns

### TokenEconomy Integration

```java
// Currency rewards use TokenEconomy when available
RewardDTO tokens = RewardDTO.currency("daily_tokens", 100);

// Processor checks for TokenEconomy plugin
// Falls back gracefully if unavailable
```

### LuckPerms Integration

```java
// Permission rewards use LuckPerms
RewardDTO vipPerm = RewardDTO.builder()
    .rewardId("vip_access")
    .type(RewardType.PERMISSION)
    .value("quest.vip.access")
    .metadata(Map.of(
        "duration", "7d",  // Temporary permission
        "context", "server=survival"
    ))
    .build();
```

### RVNKLore Integration

```java
// Lore entry rewards
RewardDTO loreEntry = RewardDTO.builder()
    .rewardId("dragon_lore")
    .type(RewardType.LORE)
    .value("lore_dragon_slayer")
    .description("Unlocks 'Dragon Slayer' lore entry")
    .build();
```

---

## Error Handling

### Common Error Codes

| Code | Meaning | Retryable |
|------|---------|-----------|
| `NO_PROCESSOR` | No processor for reward type | No |
| `PROCESSOR_UNAVAILABLE` | Processor dependencies missing | No |
| `PLAYER_OFFLINE` | Player not online | Yes |
| `INVENTORY_FULL` | No inventory space for items | Yes |
| `PERMISSION_DENIED` | Missing required permission | No |
| `VALIDATION_FAILED` | Reward validation failed | Depends |
| `DELIVERY_FAILED` | Generic delivery failure | Depends |

### Retry Logic

```java
rewardService.deliverReward(playerId, reward)
    .thenAccept(result -> {
        if (!result.success() && result.canRetry()) {
            // Queue for retry
            pendingRewards.add(new PendingReward(playerId, reward, Instant.now()));
        }
    });

// Process pending rewards periodically
void processPendingRewards() {
    Iterator<PendingReward> iter = pendingRewards.iterator();
    while (iter.hasNext()) {
        PendingReward pending = iter.next();
        if (Bukkit.getPlayer(pending.playerId()) != null) {
            rewardService.deliverReward(pending.playerId(), pending.reward());
            iter.remove();
        }
    }
}
```

---

## Best Practices

1. **Always Use Async** - Never block on reward delivery
2. **Validate First** - Check `validateReward()` before important deliveries
3. **Handle Failures** - Check `canRetry()` and queue failed rewards
4. **Use Quest Context** - Pass questId for proper tracking
5. **Provide Feedback** - Notify players of delivery results
6. **Log Deliveries** - Use LogManager for audit trail
7. **Test Integrations** - Verify optional integrations are available

---

## Migration from Legacy

```java
// Legacy reward handling (manual)
player.giveExp(500);
player.getInventory().addItem(new ItemStack(Material.DIAMOND, 5));

// Modern reward handling (async, validated, tracked)
rewardService.deliverRewards(playerId, questId, List.of(
    RewardDTO.experience("xp", 500),
    RewardDTO.item("diamonds", "DIAMOND", 5)
), true).thenAccept(result -> {
    // Handle results
});
```

---

**Last Updated**: January 31, 2026  
**Component**: RVNKQuests  
**Author**: java-architect
