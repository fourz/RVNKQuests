# RVNKQuests Implementation Iteration Prompt

You are tasked with implementing the next iteration of features and improvements for RVNKQuests, a dynamic narrative quest system for Bukkit/Spigot servers.

## Current Project State

RVNKQuests is currently in transition with:

- Core quest system with state-based management
- Event-driven architecture with dynamic listener registration
- Basic lore database integration
- Legacy Debug logging system (being migrated to LogManager)
- Command framework for quest management
- Three main quests in development (Ancient Guardian, First City Prophecy, Piglin Far From Home)

## Primary Implementation Focus

### 1. LogManager Migration (Q3 2025)

Key tasks to prioritize:

```java
// Replace patterns like:
private final Debug debug = Debug.createDebugger(plugin, "ClassName", Level.INFO);
debug.info("Message");

// With NEW FZLogger system:
private final FZLogger logger = LogManager.getInstance(plugin, getClass());
logger.info("Message");

// Or during debugging phases:
private final FZLogger logger = Debugger.getInstance(plugin, getClass());
logger.info("Message");
```

Migration approaches:

1. **Memory-Optimized Production**: Use `org.fourz.RVNKQuests.util.log.LogManager` - single instance per plugin
2. **Development/Debugging**: Use `org.fourz.RVNKQuests.util.log.Debugger` - extended capabilities
3. **Legacy Compatibility**: Keep existing `org.fourz.RVNKQuests.util.LogManager` during transition

Priority order for migration:

1. Core classes (RVNKQuests.java, QuestManager.java)
2. Quest implementations
3. Listener classes
4. Utility classes

### 2. RVNKCore Integration (Q4 2025)

Pattern for service integration:

```java
public class QuestService {
    private final PlayerService playerService;
    private final DataService dataService;
    
    public QuestService(RVNKQuests plugin) {
        if (plugin.isRVNKCoreAvailable()) {
            ServiceRegistry registry = RVNKCore.getServiceRegistry();
            this.playerService = registry.getService(PlayerService.class);
            this.dataService = registry.getService(DataService.class);
        } else {
            this.playerService = new LocalPlayerService(plugin);
            this.dataService = new LocalDataService(plugin);
        }
    }
}
```

Focus areas:

1. Service detection and fallback mechanisms
2. Database layer integration with local fallback
3. Player service integration for quest progress tracking

### 3. Quest Development

Active quests requiring implementation:

1. **The Ancient Guardian**
   - Underwater combat mechanics
   - Forgotten ruins exploration
   - Custom mob AI
   - Reward distribution

2. **The First City Prophecy**
   - City validation system
   - Collaborative building mechanics
   - Prophecy fulfillment tracking
   - Server-wide announcements

3. **Quest Framework Improvements**
   - Dynamic reward system
   - Quest difficulty scaling
   - Template system for common patterns

## Code Standards

### Logging Pattern

```java
// Production logging (memory optimized)
private final FZLogger logger = LogManager.getInstance(plugin, getClass());

// Development/debugging logging (extended features)
private final FZLogger logger = Debugger.getInstance(plugin, getClass());

// Use appropriate log levels
logger.debug("Detailed state transition");
logger.info("Quest initialized");
logger.warning("Invalid configuration for quest");
logger.error("Failed to save quest state", exception);

// Performance monitoring (Debugger only)
if (logger instanceof Debugger) {
    Debugger debugger = (Debugger) logger;
    try (var timer = debugger.timeSection("expensive-operation")) {
        performExpensiveOperation();
    } // Automatically logs performance metrics

    // Enhanced debugging features
    debugger.setPerformanceTrackingEnabled(true);
    debugger.setMethodTracingEnabled(true);
    debugger.logMemoryUsage();
}
```

### State Management Pattern

```java
@Override
public void advanceState(QuestState newState) {
    logger.debug("State transition: {} -> {}", currentState, newState);
    
    if (!isValidStateTransition(currentState, newState)) {
        logger.warning("Invalid state transition attempted");
        return;
    }
    
    this.currentState = newState;
    updateQuestListeners();
}
```

### Event Listener Pattern

```java
public class QuestObjectiveListener implements Listener {
    private final Quest quest;
    private final FZLogger logger;
    
    public QuestObjectiveListener(Quest quest) {
        this.quest = quest;
        this.logger = LogManager.getInstance(quest.getPlugin(), getClass());
    }
    
    @EventHandler
    public void onObjectiveProgress(Event event) {
        if (!isRelevantForQuest(event)) {
            return;
        }
        
        logger.debug("Processing objective progress for quest: " + quest.getId());
        
        // Use performance monitoring for critical paths (if using Debugger)
        if (logger instanceof Debugger) {
            Debugger debugger = (Debugger) logger;
            try (var timer = debugger.timeSection("objective-processing")) {
                processObjective(event);
            }
        } else {
            // LogManager: No timing available
            processObjective(event);
        }
    }
}
```

## Performance Requirements

- Early event filtering to minimize processing
- Async operations for I/O and database access
- Proper cleanup of resources and listeners
- Memory-efficient state tracking
- Performance logging for monitoring

## Implementation Priorities

1. **Critical**
   - Complete LogManager migration
   - Implement RVNKCore service detection
   - Finish core quest implementations

2. **High Priority**
   - Database integration with fallback
   - Quest progress tracking
   - Dynamic reward system

3. **Medium Priority**
   - Performance optimization
   - Quest template system
   - Configuration service integration

## Testing Requirements

1. **Unit Tests**
   - Quest state transitions
   - Event handling
   - Configuration loading
   - Service integration

2. **Integration Tests**
   - RVNKCore integration
   - Database operations
   - Quest progression flows
   - Event chain validation

3. **Performance Tests**
   - Listener registration impact
   - Database operation timing
   - Memory usage tracking
   - State transition overhead

## Implementation Notes

- Follow the established quest interface contract
- Use state-based listener management
- Implement proper cleanup methods
- Maintain backward compatibility
- Document all public APIs
- Include error handling for all I/O operations
- Add appropriate logging statements
- Consider cross-server compatibility
