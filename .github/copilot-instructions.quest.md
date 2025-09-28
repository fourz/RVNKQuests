# RVNKQuests Quest Development Guidelines

## Quest System Architecture

### Quest Interface Contract

All quests must implement the Quest interface and follow these patterns:

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

### State Management Patterns

#### State Transitions

```java
@Override
public void advanceState(QuestState newState) {
    logger.debug("Advancing quest state from {} to {}", currentState, newState);
    
    // Validate state transition
    if (!isValidStateTransition(currentState, newState)) {
        logger.warn("Invalid state transition attempted: {} -> {}", currentState, newState);
        return;
    }
    
    this.currentState = newState;
    plugin.getQuestManager().updateQuestListeners(this);
    
    // Optional: Fire quest state change event
    fireQuestStateChangeEvent(newState);
}
```

#### State Validation

```java
private boolean isValidStateTransition(QuestState from, QuestState to) {
    // Implement quest-specific transition rules
    switch (from) {
        case NOT_STARTED:
            return to == QuestState.TRIGGER_FOUND;
        case TRIGGER_FOUND:
            return to == QuestState.QUEST_ACTIVE || to == QuestState.COMPLETED;
        case QUEST_ACTIVE:
            return to == QuestState.OBJECTIVE_FOUND || to == QuestState.COMPLETED;
        case OBJECTIVE_FOUND:
            return to == QuestState.COMPLETED;
        default:
            return false;
    }
}
```

### Listener Management Patterns

#### State-Specific Listeners

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

#### Efficient Event Filtering

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

### Resource Management

#### Quest Cleanup

```java
@Override
public void cleanup() {
    logger.debug("Cleaning up quest: {}", getId());
    
    // Cancel any scheduled tasks
    questManager.cancelTask(getId() + "_reminder_task");
    
    // Clean up any spawned entities
    cleanupQuestEntities();
    
    // Remove temporary blocks or structures
    cleanupQuestStructures();
}
```

#### Entity Management

```java
private void cleanupQuestEntities() {
    List<Entity> questEntities = getQuestEntities();
    for (Entity entity : questEntities) {
        if (entity != null && entity.isValid()) {
            entity.remove();
            logger.debug("Removed quest entity: {}", entity.getType());
        }
    }
}
```

### Asynchronous Quest Operations

#### Quest Progress Loading

```java
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
```

#### Quest State Persistence

```java
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
```

## Quest Implementation Examples

### Basic Quest Structure

```java
/**
 * Example quest demonstrating basic quest patterns and lifecycle management.
 */
public class ExampleQuest implements Quest {
    private static final String QUEST_ID = "example_quest";
    
    private final RVNKQuests plugin;
    private final RVNKLogger logger;
    private final ConfigManager config;
    
    private QuestState currentState = QuestState.NOT_STARTED;
    private final Map<UUID, QuestProgress> playerProgress = new ConcurrentHashMap<>();
    
    public ExampleQuest(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.config = plugin.getConfigManager();
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
    public boolean isEnabled() {
        return config.getBoolean("quests." + QUEST_ID + ".enabled", true);
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
            case OBJECTIVE_FOUND:
                listeners.add(new ExampleRewardListener(this));
                break;
        }
        
        return listeners;
    }
    
    @Override
    public void cleanup() {
        logger.info("Cleaning up quest: {}", getId());
        
        // Cancel scheduled tasks
        plugin.getServer().getScheduler().cancelTasks(plugin);
        
        // Clean up player progress
        playerProgress.clear();
        
        // Additional cleanup...
    }
}
```

### Advanced Quest Features

#### Multi-Stage Quest with Checkpoints

```java
public class MultiStageQuest implements Quest {
    private final List<QuestStage> stages;
    private int currentStageIndex = 0;
    
    @Override
    public void advanceStage() {
        if (currentStageIndex < stages.size() - 1) {
            currentStageIndex++;
            QuestStage newStage = stages.get(currentStageIndex);
            
            logger.info("Quest {} advanced to stage: {}", getId(), newStage.getName());
            
            // Update listeners for new stage
            plugin.getQuestManager().updateQuestListeners(this);
            
            // Save checkpoint
            saveCheckpoint(currentStageIndex);
        } else {
            advanceState(QuestState.COMPLETED);
        }
    }
}
```

#### Dynamic Quest Configuration

```java
public class ConfigurableQuest implements Quest {
    
    private void loadQuestConfiguration() {
        String configPath = "quests." + getId();
        
        // Load configurable parameters
        String targetWorld = config.getString(configPath + ".world", "world");
        int requiredItems = config.getInt(configPath + ".required_items", 5);
        List<String> rewards = config.getStringList(configPath + ".rewards");
        
        // Validate configuration
        if (plugin.getServer().getWorld(targetWorld) == null) {
            logger.warn("Invalid world configured for quest {}: {}", getId(), targetWorld);
        }
    }
}
```

