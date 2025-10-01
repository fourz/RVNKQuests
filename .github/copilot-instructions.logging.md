# RVNKQuests Logging Standards

## Logging Framework Integration

### FZLogger Usage

**MANDATORY**: Use LogManager for all logging operations following RVNK ecosystem standards.

```java
// REQUIRED: Use LogManager instance with Class parameter for proper context
private final FZLogger logger = LogManager.getInstance(plugin, getClass());

// Correct logging patterns
logger.info("Quest initialized: {}", getId());
logger.debug("Processing quest state transition");
logger.warn("Quest configuration incomplete for quest: {}", questId);
logger.error("Failed to save quest state for {}", questId, exception);

// Performance monitoring
logger.startTiming("operation_name");
// ... perform operation ...
long duration = logger.endTiming("operation_name");

// Parameterized logging (SLF4J style)
logger.info("Quest {} advanced to state {}", questId, newState);
logger.debug("Processing {} objectives for quest {}", objectiveCount, questId);
```

### Migration from Debug Class

When updating existing code from the deprecated Debug system:

```java
// Old pattern (DEPRECATED) - DO NOT USE
private final Debug debug = Debug.createDebugger(plugin, "ClassName", Level.INFO);
debug.info("Message");
debug.error("Error", exception);

// New pattern (REQUIRED)
private final FZLogger logger = LogManager.getInstance(plugin, getClass());
logger.info("Message");
logger.error("Error", exception);
```

## Quest-Specific Logging Patterns

### Quest Lifecycle Logging

```java
public class QuestLifecycleLogger {
    private final FZLogger logger = LogManager.getInstance(plugin, getClass());
    
    public void logQuestInitialization(String questId) {
        logger.info("Initializing quest: {}", questId);
    }
    
    public void logQuestStateChange(String questId, QuestState oldState, QuestState newState) {
        logger.info("Quest {} state change: {} -> {}", questId, oldState, newState);
    }
    
    public void logQuestCompletion(String questId, List<UUID> participants) {
        logger.info("Quest {} completed by {} participants", questId, participants.size());
        logger.debug("Participants: {}", participants);
    }
    
    public void logQuestCleanup(String questId) {
        logger.info("Cleaning up quest: {}", questId);
    }
}
```

### Event Listener Logging

```java
public class QuestEventLogger {
    private final FZLogger logger = LogManager.getInstance(plugin, getClass());
    
    @EventHandler
    public void onQuestEvent(PlayerInteractEvent event) {
        logger.debug("Processing interaction event for quest trigger");
        
        // Log relevant event details
        logger.debug("Player: {}, Action: {}, Block: {}", 
                    event.getPlayer().getName(),
                    event.getAction(),
                    event.getClickedBlock() != null ? event.getClickedBlock().getType() : "null");
        
        // Process event...
        
        logger.debug("Quest trigger evaluation completed");
    }
}
```

### Performance Logging

```java
public class QuestPerformanceLogger {
    private final FZLogger logger = LogManager.getInstance(plugin, getClass());
    
    public void logObjectiveProcessing(String questId, int playerCount) {
        logger.startTiming("objective_processing_" + questId);
        
        // Process objectives...
        
        long duration = logger.endTiming("objective_processing_" + questId);
        logger.info("Processed objectives for {} players in quest {} ({}ms)", 
                   playerCount, questId, duration);
    }
    
    public void logDatabaseOperation(String operation, String questId) {
        logger.startTiming("db_" + operation + "_" + questId);
        
        // Perform database operation...
        
        long duration = logger.endTiming("db_" + operation + "_" + questId);
        if (duration > 100) { // Log slow operations
            logger.warn("Slow database operation: {} for quest {} took {}ms", 
                       operation, questId, duration);
        }
    }
}
```

## Logging Levels and Usage

### Debug Level Logging

Use debug level for detailed development and troubleshooting information:

```java
logger.debug("Evaluating trigger conditions for quest {}", questId);
logger.debug("Player {} progress: {} / {}", playerId, current, required);
logger.debug("Listener registration completed for state {}", state);
logger.debug("Cache hit for player {} in quest {}", playerId, questId);
```

### Info Level Logging

Use info level for important operational events:

```java
logger.info("Quest manager initialized with {} quests", questCount);
logger.info("Quest {} triggered by player {}", questId, playerName);
logger.info("Reward distributed to {} participants", participantCount);
logger.info("Configuration reloaded successfully");
```

### Warning Level Logging

Use warning level for recoverable issues that need attention:

```java
logger.warn("Quest {} not found in configuration, using defaults", questId);
logger.warn("Player {} attempted invalid quest operation", playerId);
logger.warn("Lore database unavailable, using fallback content");
logger.warn("Slow objective processing detected: {}ms", duration);
```

### Error Level Logging

Use error level for serious issues that affect functionality:

```java
logger.error("Failed to save quest progress for player {}", playerId, exception);
logger.error("Database connection lost during quest operation", exception);
logger.error("Invalid quest configuration for {}", questId, exception);
logger.error("Critical quest state corruption detected", exception);
```

## Structured Logging for Analysis

### Quest Event Correlation

```java
public class CorrelatedQuestLogger {
    private final FZLogger logger = LogManager.getInstance(plugin, getClass());
    
    public void logCorrelatedEvent(String questId, UUID playerId, String eventType, 
                                  Map<String, Object> context) {
        // Create structured log entry
        StructuredLogEntry entry = StructuredLogEntry.builder()
            .questId(questId)
            .playerId(playerId)
            .eventType(eventType)
            .timestamp(System.currentTimeMillis())
            .context(context)
            .build();
        
        logger.info("Quest event: {}", entry.toJson());
    }
}
```

### Performance Metrics Logging

```java
public class QuestMetricsLogger {
    private final FZLogger logger = LogManager.getInstance(plugin, getClass());
    
    public void logQuestMetrics(String questId, QuestMetrics metrics) {
        logger.info("Quest metrics for {}: participants={}, duration={}ms, " +
                   "objectives_completed={}, rewards_distributed={}", 
                   questId, 
                   metrics.getParticipantCount(),
                   metrics.getDurationMs(),
                   metrics.getObjectivesCompleted(),
                   metrics.getRewardsDistributed());
    }
    
    public void logSystemMetrics() {
        SystemMetrics metrics = collectSystemMetrics();
        
        logger.info("System metrics: active_quests={}, total_listeners={}, " +
                   "memory_usage={}MB, avg_tick_time={}ms",
                   metrics.getActiveQuests(),
                   metrics.getTotalListeners(),
                   metrics.getMemoryUsageMB(),
                   metrics.getAverageTickTimeMs());
    }
}
```

## Logging Configuration

### Log Level Management

```java
public class QuestLogLevelManager {
    private final FZLogger logger = LogManager.getInstance(plugin, getClass());
    
    public void updateLogLevel(Level level) {
        logger.setLogLevel(level);
        logger.info("Quest logging level updated to: {}", level.getName());
        
        // Update all quest loggers
        updateAllQuestLoggers(level);
    }
    
    public void enablePerformanceLogging(boolean enabled) {
        if (enabled) {
            logger.info("Performance logging enabled for quests");
        } else {
            logger.info("Performance logging disabled for quests");
        }
        
        // Configure performance logging flags
        QuestConfig.setPerformanceLoggingEnabled(enabled);
    }
}
```

### Conditional Logging

```java
public class ConditionalQuestLogger {
    private final FZLogger logger = LogManager.getInstance(plugin, getClass());
    private final boolean debugEnabled;
    
    public ConditionalQuestLogger(RVNKQuests plugin) {
        this.debugEnabled = plugin.getConfigManager().isDebugEnabled();
    }
    
    public void logIfEnabled(String message, Object... args) {
        if (debugEnabled) {
            logger.debug(message, args);
        }
    }
    
    public void logPerformanceIfSlow(String operation, long durationMs, Object... args) {
        if (durationMs > 50) { // Only log if operation is slow
            logger.warn("Slow operation {}: {}ms - {}", operation, durationMs, 
                       String.format(Arrays.toString(args)));
        }
    }
}
```

## Error Handling and Logging

### Exception Logging Patterns

```java
public class QuestExceptionLogger {
    private final FZLogger logger = LogManager.getInstance(plugin, getClass());
    
    public void logAndHandle(String operation, Exception exception, Runnable fallback) {
        logger.error("Operation failed: {}", operation, exception);
        
        try {
            if (fallback != null) {
                fallback.run();
                logger.info("Fallback executed successfully for operation: {}", operation);
            }
        } catch (Exception fallbackEx) {
            logger.error("Fallback also failed for operation: {}", operation, fallbackEx);
        }
    }
    
    public <T> CompletableFuture<T> logAsyncError(CompletableFuture<T> future, 
                                                 String operation) {
        return future.exceptionally(ex -> {
            logger.error("Async operation failed: {}", operation, ex);
            return null; // or appropriate default value
        });
    }
}
```

### Validation Logging

```java
public class QuestValidationLogger {
    private final FZLogger logger = LogManager.getInstance(plugin, getClass());
    
    public boolean validateAndLog(boolean condition, String message, Object... args) {
        if (!condition) {
            logger.warn("Validation failed: " + message, args);
        }
        return condition;
    }
    
    public <T> T validateNotNullAndLog(T object, String objectName) {
        if (object == null) {
            logger.error("Null validation failed for: {}", objectName);
            throw new IllegalArgumentException("Required object is null: " + objectName);
        }
        return object;
    }
}
```

## Log Output Guidelines

### Console Output Standards

- **Do not use emojis or symbols in console messages**
- **Do not use color codes in console output**
- **Do not use ChatFormat for logger output**
- For command output to console (outside of logger), use `ChatFormat.stripColors()` to ensure clean output

```java
// CORRECT: Clean console output
logger.info("Quest completed successfully");
logger.warn("Configuration issue detected");

// INCORRECT: Avoid colors and symbols in logger output
logger.info("✓ Quest completed successfully"); // Don't use symbols
logger.info(ChatColor.GREEN + "Quest completed"); // Don't use color codes
```

### Player Message vs Logger Separation

```java
public class MessageLogger {
    private final FZLogger logger = LogManager.getInstance(plugin, getClass());
    
    public void notifyQuestComplete(Player player, String questId) {
        // Player message: Can use colors and formatting
        player.sendMessage(ChatColor.GREEN + "Quest completed: " + ChatColor.GOLD + questId);
        
        // Logger message: Clean, structured
        logger.info("Player {} completed quest {}", player.getName(), questId);
    }
    
    public void handleError(CommandSender sender, String operation, Exception ex) {
        // User message: Friendly error message
        sender.sendMessage(ChatColor.RED + "Operation failed. Please try again.");
        
        // Logger message: Technical details for debugging
        logger.error("Command operation failed for sender {}: {}", sender.getName(), operation, ex);
    }
}
```