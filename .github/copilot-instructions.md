# RVNKQuests Copilot Instructions

These guidelines should be followed when modifying or creating code for RVNKQuests, a dynamic narrative quest system for Bukkit/Spigot servers. RVNKQuests is part of the RVNK plugin ecosystem and follows ecosystem-wide standards.

## Plugin Overview

RVNKQuests is a narrative-driven quest system that creates immersive, event-based adventures for Minecraft servers. It features:

- **Dynamic Quest System**: Event-triggered quests that adapt to player actions and server events
- **State-Based Quest Management**: Sophisticated quest state tracking with listener management
- **Narrative Integration**: Optional lore database for rich storytelling
- **Server Event Integration**: Quests that respond to natural server events and player interactions
- **Command Framework**: Comprehensive admin tools for quest management and debugging

## Architecture Overview

RVNKQuests follows a manager-based architecture with clear separation of concerns:

```
RVNKQuests/
├── quest/                 # Core quest system
│   ├── Quest.java         # Quest interface
│   ├── QuestManager.java  # Central quest coordination
│   ├── QuestState.java    # State enumeration
│   └── [Quest Implementations]
├── command/              # Command system
├── config/               # Configuration management
├── lore/                 # Optional narrative database
├── objective/            # Quest objective listeners
├── trigger/              # Quest trigger handlers
├── reward/               # Quest rewards and loot
└── util/                 # Utilities and helpers
```

## Core Development Guidelines

### Quest System Architecture

- **Follow the Quest interface contract** for all quest implementations
- **Use state-based listener management** through `createListenersForState()`
- **Implement proper cleanup** in quest lifecycle methods
- **Use the QuestManager** for all quest registration and coordination

```java
public class MyQuest implements Quest {
    private final RVNKQuests plugin;
    private final RVNKLogger logger;
    private QuestState currentState = QuestState.NOT_STARTED;
    
    public MyQuest(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    @Override
    public List<Listener> createListenersForState(QuestState state) {
        List<Listener> listeners = new ArrayList<>();
        switch (state) {
            case NOT_STARTED:
                listeners.add(new TriggerListener(this));
                break;
            case QUEST_ACTIVE:
                listeners.add(new ObjectiveListener(this));
                break;
            // Additional states...
        }
        return listeners;
    }
}
```

### Logging Standards

**MANDATORY**: Use LogManager for all logging operations. The Debug class is deprecated.

```java
// REQUIRED: Use LogManager instance with Class parameter for proper context
private final LogManager logger = LogManager.getInstance(plugin, getClass());

// Correct logging patterns
logger.info("Quest initialized: " + getId());
logger.debug("Processing quest state transition");
logger.warning("Quest configuration incomplete");
logger.error("Failed to save quest state", exception);

// Performance monitoring
logger.startTiming("operation_name");
// ... perform operation ...
long duration = logger.endTiming("operation_name");

// Parameterized logging (SLF4J style)
logger.info("Quest {} advanced to state {}", questId, newState);
logger.debug("Processing {} objectives for quest {}", objectiveCount, questId);

// AVOID: Do not use Debug class for new code
private final Debug debug = Debug.createDebugger(...); // DEPRECATED
```

### RVNKCore Integration

RVNKQuests is designed to integrate with RVNKCore for shared data and services:

#### Service Integration Pattern

```java
// When RVNKCore is available
public class QuestService {
    private final PlayerService playerService;
    private final DataService dataService;
    
    public QuestService(RVNKQuests plugin) {
        if (plugin.isRVNKCoreAvailable()) {
            ServiceRegistry registry = RVNKCore.getServiceRegistry();
            this.playerService = registry.getService(PlayerService.class);
            this.dataService = registry.getService(DataService.class);
        } else {
            // Fallback to local implementations
            this.playerService = new LocalPlayerService(plugin);
            this.dataService = new LocalDataService(plugin);
        }
    }
}
```

#### Database Integration

```java
// Asynchronous quest state persistence
public CompletableFuture<Void> saveQuestState(UUID playerId, String questId, QuestState state) {
    if (rvnkCoreAvailable) {
        return coreDataService.saveQuestProgress(playerId, questId, state);
    } else {
        return localDatabase.saveQuestState(playerId, questId, state);
    }
}
```

### Event-Driven Quest Design

- **Use specific event listeners** for quest triggers and objectives
- **Implement proper event filtering** to avoid unnecessary processing
- **Register/unregister listeners dynamically** based on quest state
- **Use quest-specific listener classes** rather than generic handlers

```java
@EventHandler
public void onPlayerAction(PlayerInteractEvent event) {
    // Filter early for performance
    if (!isRelevantForQuest(event)) {
        return;
    }
    
    Player player = event.getPlayer();
    Quest quest = getQuest();
    
    // Process quest-specific logic
    if (quest.getCurrentState() == QuestState.QUEST_ACTIVE) {
        processObjective(player, event);
    }
}
```

### Configuration Management

- **Use ConfigManager** for all configuration access
- **Support dynamic configuration reloading** where possible
- **Validate configuration values** with meaningful error messages
- **Provide sensible defaults** for optional settings

```java
public class QuestConfig {
    private final ConfigManager configManager;
    
    public boolean isQuestEnabled(String questId) {
        return configManager.getBoolean("quests." + questId + ".enable", true);
    }
    
    public String getQuestWorld(String questId) {
        String world = configManager.getString("quests." + questId + ".world");
        if (world == null) {
            logger.warning("No world configured for quest: " + questId);
            return "world"; // Default world
        }
        return world;
    }
}
```

### Command System Integration

- **Use the existing CommandManager framework** for consistency
- **Implement subcommands** for complex functionality
- **Provide proper permission checking** and error handling
- **Support tab completion** for better user experience

```java
public class QuestDebugSubCommand extends SubCommand {
    public QuestDebugSubCommand(RVNKQuests plugin) {
        super(plugin, "debug", "Debug quest information", "/quest debug <quest_id>");
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Usage: " + getUsage());
            return false;
        }
        
        Quest quest = plugin.getQuestManager().getQuest(args[0]);
        if (quest == null) {
            sender.sendMessage("Quest not found: " + args[0]);
            return false;
        }
        
        // Display debug information
        displayQuestDebugInfo(sender, quest);
        return true;
    }
}
```

## Quest Development Patterns

### State Management

- **Use QuestState enum** for all state tracking
- **Implement proper state transitions** with validation
- **Update listeners when state changes** via QuestManager
- **Log state transitions** for debugging

```java
@Override
public void advanceState(QuestState newState) {
    logger.debug("Advancing quest state from " + currentState + " to " + newState);
    
    // Validate state transition
    if (!isValidStateTransition(currentState, newState)) {
        logger.warning("Invalid state transition attempted: " + currentState + " -> " + newState);
        return;
    }
    
    this.currentState = newState;
    plugin.getQuestManager().updateQuestListeners(this);
    
    // Optional: Fire quest state change event
    fireQuestStateChangeEvent(newState);
}
```

### Listener Management

- **Create state-specific listeners** rather than monolithic handlers
- **Use descriptive listener class names** that indicate their purpose
- **Keep listeners focused** on single responsibilities
- **Clean up listeners** when quests are completed or disabled

```java
public class ListenerPiglinEscort implements Listener {
    private final Quest quest;
    private final RVNKLogger logger;
    
    public ListenerPiglinEscort(Quest quest) {
        this.quest = quest;
        this.logger = LogManager.getInstance(quest.getPlugin(), getClass());
    }
    
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof PiglinBrute)) {
            return;
        }
        
        // Quest-specific logic for piglin escort protection
        handlePiglinDamage(event);
    }
}
```

### Lore Integration

- **Use LoreDatabase when available** for narrative content
- **Provide fallback behavior** when lore database is disabled
- **Keep lore content separate** from quest logic
- **Support dynamic lore generation** based on quest context

```java
public void displayQuestLore(Player player, String loreKey) {
    if (plugin.hasLoreDatabase()) {
        LoreDatabase loreDb = plugin.getLoreDatabase();
        loreDb.getContentAsync(loreKey)
              .thenAccept(content -> player.sendMessage(content))
              .exceptionally(ex -> {
                  logger.error("Failed to retrieve lore: " + loreKey, ex);
                  player.sendMessage("A mysterious story unfolds..."); // Fallback
                  return null;
              });
    } else {
        // Provide basic narrative without database
        player.sendMessage("A mysterious story unfolds...");
    }
}
```

## Performance Guidelines

### Resource Management

- **Clean up scheduled tasks** in quest cleanup methods
- **Unregister listeners properly** when quests complete
- **Use efficient event filtering** to avoid unnecessary processing
- **Cache frequently accessed data** where appropriate

```java
@Override
public void cleanup() {
    logger.debug("Cleaning up quest: " + getId());
    
    // Cancel any scheduled tasks
    questManager.cancelTask(getId() + "_reminder_task");
    
    // Clean up any spawned entities
    cleanupQuestEntities();
    
    // Remove temporary blocks or structures
    cleanupQuestStructures();
}
```

### Asynchronous Operations

- **Use async operations** for database access when RVNKCore is available
- **Handle CompletableFuture chains** properly with error handling
- **Don't block the main thread** with I/O operations
- **Use appropriate thread pools** for long-running operations

```java
public CompletableFuture<QuestProgress> loadQuestProgress(UUID playerId) {
    return CompletableFuture.supplyAsync(() -> {
        try {
            return dataService.getQuestProgress(playerId, getId());
        } catch (Exception e) {
            logger.error("Failed to load quest progress for player: " + playerId, e);
            return QuestProgress.defaultProgress();
        }
    });
}
```

## Migration and Compatibility

### Legacy Debug to LogManager Migration

When updating existing code:

1. **Replace Debug instances** with LogManager
2. **Update logging calls** to use new methods
3. **Remove Debug imports** and dependencies
4. **Test logging output** to ensure compatibility

```java
// Old pattern (DEPRECATED)
private final Debug debug = Debug.createDebugger(plugin, "ClassName", Level.INFO);
debug.info("Message");
debug.error("Error", exception);

// New pattern (REQUIRED)
private final RVNKLogger logger = LogManager.getInstance(plugin, getClass());
logger.info("Message");
logger.error("Error", exception);
```

### RVNKCore Integration Path

- **Design for optional RVNKCore dependency** initially
- **Implement local fallbacks** for core functionality
- **Plan migration path** to full RVNKCore integration
- **Maintain backward compatibility** during transition

## Documentation Standards

- **Document quest mechanics** in quest class JavaDoc
- **Explain state transitions** and their triggers
- **Document configuration options** with examples
- **Provide troubleshooting guidance** for common issues

```java
/**
 * Implements the Piglin Far From Home quest, a narrative-driven adventure
 * involving a lost piglin seeking to return to the Nether.
 * 
 * Quest States:
 * - NOT_STARTED: Waiting for trigger conditions
 * - TRIGGER_FOUND: Lone piglin discovered, quest begins
 * - QUEST_ACTIVE: Players escort piglin to portal
 * - OBJECTIVE_FOUND: Portal reached, reward phase
 * - COMPLETED: Quest finished, cleanup initiated
 * 
 * Configuration:
 * - quests.piglin_far_from_home.world: Target world name
 * - quests.piglin_far_from_home.enable: Enable/disable quest
 * 
 * @since 1.0.0
 */
public class QuestPiglinFarFromHome implements Quest {
    // Implementation...
}
```