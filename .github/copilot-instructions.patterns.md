# RVNKQuests Common Patterns Library

## Project Navigation Patterns

### Path Validation Pattern

```powershell
# Always validate before operations
Get-Location
Test-Path "target_file.jar"
Get-ChildItem
```

### Safe Navigation Pattern

```powershell
# Pattern: Absolute path → validate → proceed
Set-Location "c:\tools\RVNKQuests\src\main\java\rvnk\rvnkquests"
Get-Location  # Should show expected path
Get-ChildItem   # Confirm expected files present
```

## Standard Imports and Setup

```java
// Standard imports pattern for RVNKQuests
package rvnk.rvnkquests;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.World;

import rvnk.rvnkquests.quest.Quest;
import rvnk.rvnkquests.quest.QuestState;
import rvnk.rvnkquests.util.LogManager;
import rvnk.rvnkquests.util.RVNKLogger;

// Standard class initialization
public class QuestClass implements Quest {
    private final RVNKQuests plugin;
    private final RVNKLogger logger;
    
    public QuestClass(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
}
```

## Quest Implementation Patterns

### Basic Quest Structure

```java
public class ExampleQuest implements Quest {
    private static final String QUEST_ID = "example_quest";
    
    private final RVNKQuests plugin;
    private final RVNKLogger logger;
    private QuestState currentState = QuestState.NOT_STARTED;
    
    public ExampleQuest(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    @Override
    public String getId() {
        return QUEST_ID;
    }
    
    @Override
    public QuestState getCurrentState() {
        return currentState;
    }
    
    @Override
    public List<Listener> createListenersForState(QuestState state) {
        List<Listener> listeners = new ArrayList<>();
        
        switch (state) {
            case NOT_STARTED:
                listeners.add(new ExampleTriggerListener(this));
                break;
            case QUEST_ACTIVE:
                listeners.add(new ExampleObjectiveListener(this));
                break;
        }
        
        return listeners;
    }
}
```

## Event Handler Patterns

### Quest Event Listener Pattern

```java
public class QuestEventListener implements Listener {
    private final Quest quest;
    private final RVNKLogger logger;
    
    public QuestEventListener(Quest quest) {
        this.quest = quest;
        this.logger = LogManager.getInstance(quest.getPlugin(), getClass());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEvent(PlayerEvent event) {
        // Early filtering for performance
        if (!isRelevantForQuest(event)) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Process quest-specific logic
        handleQuestEvent(player, event);
    }
    
    private boolean isRelevantForQuest(PlayerEvent event) {
        // Quest-specific relevance checks
        return event.getPlayer() != null && 
               isQuestWorld(event.getPlayer().getWorld());
    }
}
```

## Error Handling Patterns

### Standard Error Response

```java
public class ErrorHandlingPattern {
    
    public CompletableFuture<QuestResult> processQuestAction(Player player, String action) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return performQuestAction(player, action);
            } catch (Exception e) {
                logger.error("Failed to process quest action {} for player {}", action, player.getName(), e);
                return QuestResult.failure("Quest action failed: " + e.getMessage());
            }
        });
    }
    
    private void handleQuestError(Exception e, String context, Player player) {
        logger.error("Quest error in {}: {}", context, e.getMessage(), e);
        
        if (player != null && player.isOnline()) {
            player.sendMessage("§cQuest Error: " + e.getMessage());
        }
    }
}
```

## Configuration Patterns

### Configuration Management

```java
public class ConfigurationPattern {
    private final FileConfiguration config;
    private final RVNKLogger logger;
    
    public ConfigurationPattern(RVNKQuests plugin) {
        this.config = plugin.getConfig();
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    public boolean getQuestEnabled(String questId) {
        String path = "quests." + questId + ".enabled";
        return config.getBoolean(path, true);
    }
    
    public String getQuestWorld(String questId) {
        String path = "quests." + questId + ".world";
        return config.getString(path, "world");
    }
    
    public void validateConfiguration() {
        ConfigurationSection questsSection = config.getConfigurationSection("quests");
        if (questsSection == null) {
            logger.warn("No quests configured in config.yml");
            return;
        }
        
        for (String questId : questsSection.getKeys(false)) {
            validateQuestConfiguration(questId);
        }
    }
}
```

## Async Patterns

### Async Quest Operations

```java
public class AsyncQuestPattern {
    
    public CompletableFuture<QuestProgress> loadQuestProgress(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return dataService.getQuestProgress(playerId, getId());
            } catch (Exception e) {
                logger.error("Failed to load quest progress for player: {}", playerId, e);
                return QuestProgress.defaultProgress();
            }
        });
    }
    
    public CompletableFuture<Void> saveQuestState(UUID playerId, QuestState state) {
        return CompletableFuture.runAsync(() -> {
            try {
                dataService.saveQuestState(playerId, getId(), state);
                logger.debug("Saved quest state {} for player {}", state, playerId);
            } catch (Exception e) {
                logger.error("Failed to save quest state for player: {}", playerId, e);
            }
        });
    }
    
    // Return to main thread for Bukkit API calls
    private void runOnMainThread(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }
}
```

## Security Patterns

### Permission and Validation

```java
public class SecurityPattern {
    
    public boolean validateQuestAccess(Player player, String questId) {
        // Check basic requirements
        if (player == null || !player.isOnline()) {
            return false;
        }
        
        // Check world restrictions
        String requiredWorld = getQuestWorld(questId);
        if (!player.getWorld().getName().equals(requiredWorld)) {
            logger.debug("Player {} not in required world {} for quest {}", 
                player.getName(), requiredWorld, questId);
            return false;
        }
        
        // Check permissions
        if (!player.hasPermission("rvnkquests.quest." + questId)) {
            logger.debug("Player {} lacks permission for quest {}", 
                player.getName(), questId);
            return false;
        }
        
        return true;
    }
}
```

## Testing Patterns

### Quest Testing Framework

```java
public class QuestTestPattern {
    
    @Test
    public void testQuestStateTransition() {
        // Setup
        Quest quest = new TestQuest(plugin);
        
        // Execute
        quest.advanceState(QuestState.TRIGGER_FOUND);
        
        // Verify
        assertEquals(QuestState.TRIGGER_FOUND, quest.getCurrentState());
    }
    
    @Test
    public void testQuestListenerRegistration() {
        // Setup
        Quest quest = new TestQuest(plugin);
        
        // Execute
        List<Listener> listeners = quest.createListenersForState(QuestState.QUEST_ACTIVE);
        
        // Verify
        assertFalse(listeners.isEmpty());
        assertTrue(listeners.stream().anyMatch(l -> l instanceof ObjectiveListener));
    }
}
```

## Logging Patterns

### LogManager Usage Pattern

```java
public class LoggingPattern {
    private final RVNKLogger logger = LogManager.getInstance(plugin, getClass());
    
    public void exampleLogging() {
        // Basic logging
        logger.info("Quest {} started for player {}", questId, playerName);
        logger.warn("Quest configuration issue detected");
        logger.error("Failed to process quest", exception);
        logger.debug("Quest state transition details");
        
        // Performance timing
        logger.startTiming("quest_processing");
        // ... processing work ...
        long duration = logger.endTiming("quest_processing");
        logger.info("Quest processing completed in {}ms", duration);
    }
}
```

## Reference Usage

Instead of duplicating these patterns in each instruction file, reference this common library:

```markdown
# In other instruction files:
See [Common Patterns](copilot-instructions.patterns.md#quest-implementation-patterns) for standard implementation.
```

## Key Pattern Principles

1. **Consistency**: Use established patterns across all quest implementations
2. **Error Handling**: Always include proper exception handling and logging
3. **Performance**: Use async operations for I/O, cache frequently accessed data
4. **Security**: Validate inputs, check permissions, sanitize data
5. **Testability**: Write code that can be easily unit tested
6. **Maintainability**: Use clear naming, proper logging, comprehensive documentation