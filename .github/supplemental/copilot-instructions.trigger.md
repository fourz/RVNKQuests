# RVNKQuests Event Trigger System Guidelines

## Event-Driven Quest Architecture

### Quest-Specific Event Filtering

All quest event listeners must implement early filtering to avoid unnecessary processing:

```java
@EventHandler
public void onPlayerInteract(PlayerInteractEvent event) {
    // Early filtering for performance
    if (!isRelevantForQuest(event)) {
        return;
    }
    
    Player player = event.getPlayer();
    
    // Check quest state before processing
    if (getCurrentState() != QuestState.QUEST_ACTIVE) {
        return;
    }
    
    // Process quest-specific logic
    handlePlayerInteraction(player, event);
}

private boolean isRelevantForQuest(PlayerInteractEvent event) {
    // Quest-specific relevance checks
    return event.getAction() == Action.RIGHT_CLICK_BLOCK &&
           event.getClickedBlock() != null &&
           isQuestWorld(event.getClickedBlock().getWorld());
}
```

### Dynamic Listener Registration

Listeners should be registered and unregistered based on quest state:

```java
public class QuestManager {
    
    public void updateQuestListeners(Quest quest) {
        // Unregister old listeners
        unregisterListenersForQuest(quest);
        
        // Register new listeners for current state
        QuestState currentState = quest.getCurrentState();
        List<Listener> newListeners = quest.createListenersForState(currentState);
        
        for (Listener listener : newListeners) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            registeredListeners.put(quest.getId(), listener);
        }
        
        logger.debug("Updated listeners for quest {} in state {}", quest.getId(), currentState);
    }
    
    private void unregisterListenersForQuest(Quest quest) {
        List<Listener> oldListeners = registeredListeners.get(quest.getId());
        if (oldListeners != null) {
            for (Listener listener : oldListeners) {
                HandlerList.unregisterAll(listener);
            }
            registeredListeners.remove(quest.getId());
        }
    }
}
```

## Event Handler Patterns

### Trigger Event Handlers

Quest triggers should be lightweight and focused on detection:

```java
public class QuestTriggerListener implements Listener {
    private final Quest quest;
    private final FZLogger logger;
    
    public QuestTriggerListener(Quest quest) {
        this.quest = quest;
        this.logger = LogManager.getInstance(quest.getPlugin(), getClass());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        // Specific trigger conditions
        if (event.getEntityType() != EntityType.PIGLIN_BRUTE) {
            return;
        }
        
        if (!isQuestWorld(event.getLocation().getWorld())) {
            return;
        }
        
        // Check for specific spawn conditions
        if (isLonePiglin(event.getEntity())) {
            logger.debug("Quest trigger detected: lone piglin spawned");
            quest.advanceState(QuestState.TRIGGER_FOUND);
            
            // Tag the entity for quest tracking
            tagQuestEntity(event.getEntity());
        }
    }
    
    private boolean isLonePiglin(Entity piglin) {
        // Check if piglin is alone (quest-specific logic)
        return piglin.getNearbyEntities(10, 10, 10).stream()
            .noneMatch(entity -> entity.getType() == EntityType.PIGLIN_BRUTE);
    }
}
```

### Reusable Trigger Patterns

#### Proximity Triggers

```java
public class ProximityTrigger implements Listener {
    private final Quest quest;
    private final Location triggerLocation;
    private final double triggerRadius;
    private final FZLogger logger;
    
    public ProximityTrigger(Quest quest, Location location, double radius) {
        this.quest = quest;
        this.triggerLocation = location;
        this.triggerRadius = radius;
        this.logger = LogManager.getInstance(quest.getPlugin(), getClass());
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null || !event.getTo().getWorld().equals(triggerLocation.getWorld())) {
            return;
        }
        
        double distance = event.getTo().distance(triggerLocation);
        if (distance <= triggerRadius) {
            handleTriggerActivation(event.getPlayer());
        }
    }
    
    private void handleTriggerActivation(Player player) {
        logger.debug("Proximity trigger activated for quest {} by player {}", 
                    quest.getId(), player.getName());
        
        quest.advanceState(QuestState.TRIGGER_FOUND);
        
        // Optional: Send notification to player
        player.sendMessage("You have discovered something interesting...");
    }
}
```

#### Item-Based Triggers

```java
public class ItemTrigger implements Listener {
    private final Quest quest;
    private final Material triggerItem;
    private final String requiredLore;
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() != triggerItem) {
            return;
        }
        
        if (!hasRequiredLore(item)) {
            return;
        }
        
        // Trigger quest
        quest.advanceState(QuestState.TRIGGER_FOUND);
        logger.debug("Item trigger activated for quest {}", quest.getId());
    }
    
    private boolean hasRequiredLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return false;
        }
        
        return meta.getLore().stream()
                .anyMatch(line -> ChatColor.stripColor(line).contains(requiredLore));
    }
}
```

#### Time-Based Triggers

```java
public class TimeTrigger {
    private final Quest quest;
    private final long triggerTime; // Server time or real time
    private final FZLogger logger;
    private BukkitTask scheduledTask;
    
    public TimeTrigger(Quest quest, long delayTicks) {
        this.quest = quest;
        this.triggerTime = System.currentTimeMillis() + (delayTicks * 50);
        this.logger = LogManager.getInstance(quest.getPlugin(), getClass());
    }
    
    public void startTimer() {
        long delayTicks = Math.max(1, (triggerTime - System.currentTimeMillis()) / 50);
        
        scheduledTask = Bukkit.getScheduler().runTaskLater(
            quest.getPlugin(), 
            this::activateTrigger, 
            delayTicks
        );
        
        logger.debug("Scheduled time trigger for quest {} in {} ticks", 
                    quest.getId(), delayTicks);
    }
    
    private void activateTrigger() {
        logger.debug("Time trigger activated for quest {}", quest.getId());
        quest.advanceState(QuestState.TRIGGER_FOUND);
    }
    
    public void cancelTimer() {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel();
        }
    }
}
```

## Event Priority and Timing

### Event Priority Guidelines

```java
public class QuestEventPriorities {
    
    // Use MONITOR for quest tracking (don't modify events)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void trackQuestProgress(PlayerEvent event) {
        // Track player actions for quest progress
    }
    
    // Use NORMAL for quest logic that may modify events
    @EventHandler(priority = EventPriority.NORMAL)
    public void handleQuestInteraction(PlayerInteractEvent event) {
        if (shouldCancelForQuest(event)) {
            event.setCancelled(true);
        }
    }
    
    // Use LOW for quest setup that should happen early
    @EventHandler(priority = EventPriority.LOW)
    public void prepareQuestArea(PlayerJoinEvent event) {
        // Set up quest area for new players
    }
}
```

### Async Event Processing

For expensive operations, use async processing:

```java
public class AsyncQuestProcessor {
    
    @EventHandler
    public void onQuestTrigger(PlayerEvent event) {
        Player player = event.getPlayer();
        
        // Quick validation on main thread
        if (!isEligibleForQuest(player)) {
            return;
        }
        
        // Async processing for expensive operations
        CompletableFuture.runAsync(() -> {
            processQuestTrigger(player);
        }).exceptionally(ex -> {
            logger.error("Failed to process quest trigger for {}", player.getName(), ex);
            return null;
        });
    }
    
    private void processQuestTrigger(Player player) {
        // Database operations, complex calculations, etc.
        QuestProgress progress = loadPlayerProgress(player.getUniqueId());
        
        // Return to main thread for Bukkit API calls
        Bukkit.getScheduler().runTask(plugin, () -> {
            applyQuestEffects(player, progress);
        });
    }
}
```

## Multi-Quest Event Triggers

### Shared Event System

```java
public class SharedEventManager {
    private final Map<String, List<Quest>> eventSubscriptions = new HashMap<>();
    
    /**
     * Register a quest to listen for specific event types
     */
    public void subscribeToEvent(Quest quest, String eventType) {
        eventSubscriptions.computeIfAbsent(eventType, k -> new ArrayList<>()).add(quest);
        logger.debug("Quest {} subscribed to event type: {}", quest.getId(), eventType);
    }
    
    /**
     * Broadcast an event to all subscribed quests
     */
    public void broadcastEvent(String eventType, Object eventData) {
        List<Quest> subscribers = eventSubscriptions.get(eventType);
        if (subscribers != null) {
            for (Quest quest : subscribers) {
                // Only notify quests that are in appropriate states
                if (quest.getCurrentState().canReceiveEvents()) {
                    quest.handleExternalEvent(eventType, eventData);
                }
            }
        }
    }
}
```

### Cross-Quest Trigger Dependencies

```java
public class CrossQuestTriggerManager {
    
    /**
     * Set up triggers that depend on multiple quests
     */
    public void setupCrossQuestTrigger(String triggerName, List<String> questIds, 
                                       List<QuestState> requiredStates) {
        
        CrossQuestTrigger trigger = new CrossQuestTrigger(triggerName, questIds, requiredStates);
        crossQuestTriggers.put(triggerName, trigger);
        
        // Listen for quest state changes
        for (String questId : questIds) {
            Quest quest = questManager.getQuest(questId);
            if (quest != null) {
                quest.addStateChangeListener(trigger);
            }
        }
    }
    
    public class CrossQuestTrigger implements QuestStateChangeListener {
        @Override
        public void onQuestStateChange(String questId, QuestState oldState, QuestState newState) {
            // Check if all required conditions are met
            if (areAllConditionsMet()) {
                activateLinkedQuests();
            }
        }
    }
}
```

## Performance Optimization

### Event Caching

```java
public class OptimizedEventHandler {
    private final Cache<UUID, QuestProgress> progressCache = 
        Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();
    
    @EventHandler
    public void onPlayerAction(PlayerEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        
        // Use cached progress when available
        QuestProgress progress = progressCache.getIfPresent(playerId);
        if (progress == null) {
            // Load from database async, cache result
            loadProgressAsync(playerId);
            return;
        }
        
        processWithCachedProgress(event.getPlayer(), progress);
    }
}
```

### Batch Event Processing

```java
public class BatchEventProcessor {
    private final Map<String, List<PlayerEvent>> eventBatches = new ConcurrentHashMap<>();
    
    @EventHandler
    public void onPlayerEvent(PlayerEvent event) {
        String questId = determineRelevantQuest(event);
        if (questId != null) {
            eventBatches.computeIfAbsent(questId, k -> new ArrayList<>()).add(event);
        }
    }
    
    // Process batches periodically
    @Scheduled(fixedRate = 1000) // Every second
    public void processBatches() {
        for (Map.Entry<String, List<PlayerEvent>> entry : eventBatches.entrySet()) {
            String questId = entry.getKey();
            List<PlayerEvent> events = entry.getValue();
            
            if (!events.isEmpty()) {
                processBatchForQuest(questId, new ArrayList<>(events));
                events.clear();
            }
        }
    }
}
```