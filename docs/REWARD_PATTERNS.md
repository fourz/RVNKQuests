# Reward System Patterns

**RVNKQuests Reward System** - Async reward delivery framework with pluggable processors.

## Overview

The RVNKQuests reward system provides a comprehensive, extensible framework for delivering quest rewards. Built on async patterns with `CompletableFuture`, it supports nine reward types out of the box with the ability to add custom handlers.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    RewardServiceImpl                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Processor Registry                       │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐    │   │
│  │  │Experience│ │  Item   │ │Currency │ │ Command │    │   │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘    │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐    │   │
│  │  │  Title  │ │Permission│ │QuestUnlock││  Lore  │    │   │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘    │   │
│  │  ┌─────────┐                                         │   │
│  │  │ Custom  │                                         │   │
│  │  └─────────┘                                         │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## Supported Reward Types

| Type | Processor | Dependencies | Priority | Offline Queue |
|------|-----------|--------------|----------|---------------|
| EXPERIENCE | ExperienceRewardProcessor | None | 100 | No |
| ITEM | ItemRewardProcessor | None | 90 | No |
| CURRENCY | CurrencyRewardProcessor | TokenEconomy/Vault | 80 | Yes |
| COMMAND | CommandRewardProcessor | None | 70 | No |
| PERMISSION | PermissionRewardProcessor | LuckPerms | 60 | Yes |
| TITLE | TitleRewardProcessor | None | 50 | No |
| QUEST_UNLOCK | QuestUnlockRewardProcessor | None | 40 | Yes |
| LORE | LoreRewardProcessor | RVNKLore | 30 | Yes |
| CUSTOM | CustomRewardProcessor | None | 10 | Depends |

## Usage Examples

### Creating Rewards with RewardDTO

```java
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;
import java.util.Map;

// Experience reward (100 XP points)
RewardDTO xpReward = RewardDTO.builder()
    .id("quest_completion_xp")
    .type(RewardType.EXPERIENCE)
    .name("100 Experience Points")
    .amount(100)
    .metadata(Map.of("mode", "points"))
    .build();

// Experience reward (5 levels)
RewardDTO levelReward = RewardDTO.builder()
    .id("boss_kill_levels")
    .type(RewardType.EXPERIENCE)
    .name("5 Experience Levels")
    .amount(5)
    .metadata(Map.of("mode", "levels"))
    .build();

// Item reward (Diamond Sword)
RewardDTO itemReward = RewardDTO.builder()
    .id("diamond_sword_reward")
    .type(RewardType.ITEM)
    .name("Diamond Sword")
    .amount(1)
    .metadata(Map.of(
        "material", "DIAMOND_SWORD",
        "displayName", "&6Legendary Blade",
        "lore", "&7Forged in dragon fire"
    ))
    .build();

// Currency reward (1000 tokens)
RewardDTO currencyReward = RewardDTO.builder()
    .id("token_reward")
    .type(RewardType.CURRENCY)
    .name("1000 Tokens")
    .amount(1000)
    .build();

// Command reward
RewardDTO commandReward = RewardDTO.builder()
    .id("broadcast_reward")
    .type(RewardType.COMMAND)
    .name("Server Broadcast")
    .metadata(Map.of(
        "command", "broadcast {player} completed the quest!",
        "asConsole", "true"
    ))
    .build();

// Permission reward (VIP for 7 days)
RewardDTO permissionReward = RewardDTO.builder()
    .id("vip_access")
    .type(RewardType.PERMISSION)
    .name("VIP Access (7 days)")
    .metadata(Map.of(
        "permission", "group.vip",
        "duration", "604800"  // 7 days in seconds
    ))
    .build();

// Title reward
RewardDTO titleReward = RewardDTO.builder()
    .id("quest_complete_title")
    .type(RewardType.TITLE)
    .name("Quest Complete!")
    .metadata(Map.of(
        "title", "&6Quest Complete!",
        "subtitle", "&aYou earned your reward",
        "fadeIn", "10",
        "stay", "70",
        "fadeOut", "20",
        "sound", "ENTITY_PLAYER_LEVELUP"
    ))
    .build();

// Quest unlock reward
RewardDTO questUnlockReward = RewardDTO.builder()
    .id("unlock_advanced")
    .type(RewardType.QUEST_UNLOCK)
    .name("Unlocks Advanced Quest")
    .metadata(Map.of(
        "questId", "advanced_mining_quest",
        "notify", "true"
    ))
    .build();

// Lore reward
RewardDTO loreReward = RewardDTO.builder()
    .id("lore_ancient_history")
    .type(RewardType.LORE)
    .name("Ancient History Unlocked")
    .metadata(Map.of(
        "loreId", "ancient_civilization_01",
        "category", "history"
    ))
    .build();
```

### Delivering Rewards

```java
import org.fourz.RVNKQuests.service.IRewardService;
import org.fourz.RVNKQuests.service.IRewardService.*;

// Inject or retrieve the reward service
IRewardService rewardService = serviceRegistry.get(IRewardService.class);

// Single reward delivery
UUID playerId = player.getUniqueId();
rewardService.deliverReward(playerId, xpReward)
    .thenAccept(result -> {
        if (result.success()) {
            player.sendMessage("§a" + result.message());
        } else {
            player.sendMessage("§cReward failed: " + result.message());
            logger.warning("Reward error: " + result.errorCode());
        }
    });

// Batch reward delivery
List<RewardDTO> questRewards = List.of(xpReward, itemReward, currencyReward, titleReward);

rewardService.deliverRewards(playerId, "quest_123", questRewards, true)
    .thenAccept(batchResult -> {
        player.sendMessage(String.format(
            "§aReceived %d/%d rewards!",
            batchResult.successCount(),
            batchResult.totalCount()
        ));
        
        if (batchResult.failureCount() > 0) {
            batchResult.results().stream()
                .filter(r -> !r.success())
                .forEach(r -> logger.warning(
                    "Failed reward: " + r.reward().name() + " - " + r.errorCode()
                ));
        }
    });
```

### Validating Rewards

```java
// Validate before delivery
rewardService.validateReward(playerId, itemReward)
    .thenAccept(validationResult -> {
        if (validationResult.valid()) {
            // Safe to deliver
            rewardService.deliverReward(playerId, itemReward);
        } else {
            player.sendMessage("§cInvalid reward: " + validationResult.reason());
            player.sendMessage("§7Suggestion: " + validationResult.suggestion());
        }
    });

// Validate batch
rewardService.validateRewards(playerId, questRewards)
    .thenAccept(results -> {
        List<RewardDTO> validRewards = new ArrayList<>();
        for (RewardValidationResult result : results) {
            if (result.valid()) {
                validRewards.add(result.reward());
            } else {
                logger.warning("Invalid: " + result.reward().name() + " - " + result.reason());
            }
        }
        
        if (!validRewards.isEmpty()) {
            rewardService.deliverRewards(playerId, validRewards);
        }
    });
```

## Custom Reward Handlers

### Registering Custom Handlers

```java
// Get the custom processor
CustomRewardProcessor customProcessor = 
    (CustomRewardProcessor) rewardService.getProcessor(RewardType.CUSTOM);

// Register a firework effect handler
customProcessor.registerHandler("firework", (playerId, reward) -> {
    Player player = Bukkit.getPlayer(playerId);
    if (player == null) {
        return RewardDeliveryResult.failure(reward, "Player offline", "PLAYER_OFFLINE");
    }
    
    // Get firework configuration from metadata
    String colorStr = getMetadataString(reward, "color");
    Color color = parseColor(colorStr);
    
    // Create firework
    Location loc = player.getLocation();
    Firework firework = (Firework) player.getWorld().spawnEntity(loc, EntityType.FIREWORK);
    FireworkMeta meta = firework.getFireworkMeta();
    meta.addEffect(FireworkEffect.builder()
        .withColor(color)
        .with(FireworkEffect.Type.BALL_LARGE)
        .build());
    meta.setPower(1);
    firework.setFireworkMeta(meta);
    
    return RewardDeliveryResult.success(reward, "Celebration firework launched!");
});

// Register validator for the handler
customProcessor.registerValidator("firework", (playerId, reward) -> {
    String colorStr = getMetadataString(reward, "color");
    if (colorStr == null) {
        return RewardValidationResult.invalid(reward, "Missing color", "Specify color in metadata");
    }
    return RewardValidationResult.valid(reward, "Firework reward valid");
});

// Use the custom reward
RewardDTO fireworkReward = RewardDTO.builder()
    .id("celebration")
    .type(RewardType.CUSTOM)
    .name("Celebration Effect")
    .metadata(Map.of(
        "handler", "firework",
        "color", "red"
    ))
    .build();
```

### Custom Processor Implementation

```java
public class ParticleRewardProcessor implements RewardProcessor {

    @Override
    public RewardType getType() {
        return RewardType.CUSTOM; // Or define new enum
    }

    @Override
    public CompletableFuture<RewardDeliveryResult> deliver(UUID playerId, RewardDTO reward) {
        return CompletableFuture.supplyAsync(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                return RewardDeliveryResult.failure(reward, "Player offline", "PLAYER_OFFLINE");
            }
            
            // Parse particle configuration
            String particleType = getMetadata(reward, "particle", "HEART");
            int count = getMetadataInt(reward, "count", 10);
            
            // Spawn particles
            Particle particle = Particle.valueOf(particleType);
            player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), count);
            
            return RewardDeliveryResult.success(reward, "Particle effect displayed");
        });
    }

    @Override
    public CompletableFuture<RewardValidationResult> validate(UUID playerId, RewardDTO reward) {
        String particleType = getMetadata(reward, "particle", null);
        if (particleType != null) {
            try {
                Particle.valueOf(particleType);
            } catch (IllegalArgumentException e) {
                return CompletableFuture.completedFuture(
                    RewardValidationResult.invalid(reward, "Invalid particle: " + particleType, "Use valid Particle enum")
                );
            }
        }
        return CompletableFuture.completedFuture(RewardValidationResult.valid(reward, "Valid"));
    }

    @Override
    public boolean requiresOnlinePlayer() {
        return true;
    }

    @Override
    public int getPriority() {
        return 20;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
```

## Result Types

### RewardDeliveryResult

```java
// Success result
RewardDeliveryResult.success(reward, "100 XP granted")

// Failure result
RewardDeliveryResult.failure(reward, "Player inventory full", "INVENTORY_FULL")

// Partial success (some items delivered)
RewardDeliveryResult.partialSuccess(reward, "32/64 items delivered - inventory full")

// Check result
result.success()      // true/false
result.message()      // Human-readable message
result.errorCode()    // Machine-readable error code (null if success)
result.reward()       // The original reward DTO
```

### BatchRewardResult

```java
// Batch result properties
batchResult.totalCount()        // Total rewards attempted
batchResult.successCount()      // Successful deliveries
batchResult.failureCount()      // Failed deliveries
batchResult.results()           // List<RewardDeliveryResult>
batchResult.stoppedEarly()      // Whether batch was interrupted

// Example: Check for complete success
if (batchResult.successCount() == batchResult.totalCount()) {
    player.sendMessage("§aAll rewards received!");
}
```

### RewardValidationResult

```java
// Valid result
RewardValidationResult.valid(reward, "Reward configuration is valid")

// Invalid result with suggestion
RewardValidationResult.invalid(
    reward,
    "Invalid material: DAIMOND_SWORD",
    "Did you mean DIAMOND_SWORD?"
)

// Check validation
validationResult.valid()        // true/false
validationResult.reason()       // Why invalid (null if valid)
validationResult.suggestion()   // How to fix (null if valid)
validationResult.reward()       // The original reward DTO
```

## Processor Priority

Rewards are delivered in priority order (highest first):

1. **EXPERIENCE (100)** - XP is instant and non-blocking
2. **ITEM (90)** - Physical items need inventory space
3. **CURRENCY (80)** - Economy transactions
4. **COMMAND (70)** - Server commands
5. **PERMISSION (60)** - Permission grants
6. **TITLE (50)** - Visual feedback
7. **QUEST_UNLOCK (40)** - Game progression
8. **LORE (30)** - Story/narrative rewards
9. **CUSTOM (10)** - Extension handlers

## Error Handling

### Common Error Codes

| Error Code | Description | Recovery |
|------------|-------------|----------|
| PLAYER_OFFLINE | Player not online | Queue for later |
| PLAYER_NOT_FOUND | Invalid player UUID | Check UUID |
| INVENTORY_FULL | No inventory space | Drop or queue |
| INVALID_MATERIAL | Bad material name | Fix configuration |
| ECONOMY_UNAVAILABLE | No economy plugin | Install plugin |
| PERMISSION_ERROR | LuckPerms issue | Check LuckPerms |
| NO_PROCESSOR | Unknown reward type | Register processor |
| COMMAND_FAILED | Command execution failed | Check command |

### Error Recovery Patterns

```java
rewardService.deliverReward(playerId, reward)
    .exceptionally(error -> {
        // Log the error
        logger.severe("Reward delivery exception: " + error.getMessage());
        
        // Return failure result
        return RewardDeliveryResult.failure(
            reward,
            "Internal error",
            "EXCEPTION"
        );
    })
    .thenAccept(result -> {
        if (!result.success()) {
            switch (result.errorCode()) {
                case "PLAYER_OFFLINE" -> queueForLater(playerId, reward);
                case "INVENTORY_FULL" -> scheduleRetry(playerId, reward);
                default -> logFailure(result);
            }
        }
    });
```

## Integration Patterns

### With Quest Completion

```java
public void onQuestComplete(UUID playerId, QuestDTO quest) {
    List<RewardDTO> rewards = quest.rewards();
    
    rewardService.deliverRewards(playerId, quest.id(), rewards, true)
        .thenAccept(result -> {
            // Log completion
            logQuestRewards(playerId, quest.id(), result);
            
            // Update quest progress
            questService.markRewardsClaimed(playerId, quest.id());
            
            // Notify player
            notifyRewards(playerId, result);
        });
}
```

### With ServiceRegistry

```java
@Override
public void onEnable() {
    // Create service
    IRewardService rewardService = new RewardServiceImpl(this);
    
    // Register with ServiceRegistry
    serviceRegistry.register(IRewardService.class, rewardService);
    
    // Configure integrations
    setupQuestUnlockIntegration(rewardService);
    setupLoreIntegration(rewardService);
}

private void setupQuestUnlockIntegration(IRewardService rewardService) {
    QuestUnlockRewardProcessor processor = 
        (QuestUnlockRewardProcessor) rewardService.getProcessor(RewardType.QUEST_UNLOCK);
    
    processor.setUnlockCallback((playerId, questId) -> {
        questService.unlockQuest(playerId, questId);
    });
}
```

## Configuration

### Reward Configuration in YAML

```yaml
quest:
  id: "mining_tutorial"
  name: "Mining Tutorial"
  rewards:
    - id: "xp_reward"
      type: EXPERIENCE
      name: "50 Experience Points"
      amount: 50
      metadata:
        mode: points
        
    - id: "pickaxe_reward"
      type: ITEM
      name: "Stone Pickaxe"
      amount: 1
      metadata:
        material: STONE_PICKAXE
        
    - id: "token_reward"
      type: CURRENCY
      name: "100 Tokens"
      amount: 100
      
    - id: "complete_title"
      type: TITLE
      name: "Quest Complete"
      metadata:
        title: "&6Mining Tutorial Complete!"
        subtitle: "&aYou're ready for greater challenges"
        sound: ENTITY_PLAYER_LEVELUP
```

## Best Practices

1. **Always validate before delivery** - Prevent runtime errors
2. **Use batch delivery for multiple rewards** - Better error handling
3. **Handle offline players gracefully** - Queue or skip appropriately
4. **Log failures for debugging** - Include error codes
5. **Set appropriate priorities** - Process critical rewards first
6. **Use async patterns** - Never block the main thread
7. **Provide clear reward names** - For player feedback
8. **Test edge cases** - Full inventory, offline, missing plugins

## Related Documentation

- [Objective Patterns](OBJECTIVE_PATTERNS.md) - Quest objective system
- [Quest System](README.md) - Main quest system documentation
- [Data Models](data/dto/) - DTO definitions

---

**Last Updated**: January 2026  
**Version**: 1.0  
**Author**: RVNKQuests Development Team
