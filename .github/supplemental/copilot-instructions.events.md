# RVNKQuests Event-Driven Quest Design Guidelines

## Event Listener Architecture

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
    private final RVNKLogger logger;
    
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

### Objective Event Handlers

Objective handlers track progress toward quest completion:

```java
public class QuestObjectiveListener implements Listener {
    private final Quest quest;
    private final Map<UUID, Integer> playerProgress = new ConcurrentHashMap<>();
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is escorting the quest entity
        if (!isPlayerEscorting(player)) {
            return;
        }
        
        Location to = event.getTo();
        if (to != null && isNearNetherPortal(to)) {
            completeEscortObjective(player);
        }
    }
    
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!isQuestEntity(event.getEntity())) {
            return;
        }
        
        // Protect quest entity during escort
        if (quest.getCurrentState() == QuestState.QUEST_ACTIVE) {
            event.setCancelled(true);
            logger.debug("Protected quest entity from damage");
            
            // Notify nearby players
            notifyPlayersNearEntity(event.getEntity(), "The piglin is under your protection!");
        }
    }
    
    private void completeEscortObjective(Player player) {
        logger.info("Player {} completed escort objective", player.getName());
        quest.advanceState(QuestState.OBJECTIVE_FOUND);
        
        // Record player participation
        recordPlayerParticipation(player.getUniqueId());
    }
}
```

### Reward Event Handlers

Reward handlers manage quest completion and reward distribution:

```java
public class QuestRewardListener implements Listener {
    private final Quest quest;
    private final RewardManager rewardManager;
    
    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        if (!isQuestEntity(event.getEntity())) {
            return;
        }
        
        if (quest.getCurrentState() == QuestState.OBJECTIVE_FOUND) {
            // Quest entity successfully reached portal
            completeQuest(event.getEntity());
        }
    }
    
    private void completeQuest(Entity questEntity) {
        logger.info("Quest {} completed successfully", quest.getId());
        
        // Distribute rewards to participating players
        List<UUID> participants = getParticipatingPlayers();
        CompletableFuture.allOf(
            participants.stream()
                .map(this::distributeRewards)
                .toArray(CompletableFuture[]::new)
        ).thenRun(() -> {
            // Clean up quest
            questEntity.remove();
            quest.advanceState(QuestState.COMPLETED);
        });
    }
    
    private CompletableFuture<Void> distributeRewards(UUID playerId) {
        return rewardManager.giveQuestRewards(playerId, quest.getId());
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

## Custom Quest Events

### Creating Quest-Specific Events

```java
public class QuestStateChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final String questId;
    private final QuestState oldState;
    private final QuestState newState;
    private final List<UUID> participants;
    
    public QuestStateChangeEvent(String questId, QuestState oldState, QuestState newState, List<UUID> participants) {
        this.questId = questId;
        this.oldState = oldState;
        this.newState = newState;
        this.participants = new ArrayList<>(participants);
    }
    
    // Getters and Bukkit event methods
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
    
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
```

### Firing Custom Events

```java
public class QuestEventManager {
    
    public void fireQuestStateChange(Quest quest, QuestState oldState, QuestState newState) {
        List<UUID> participants = getQuestParticipants(quest);
        
        QuestStateChangeEvent event = new QuestStateChangeEvent(
            quest.getId(), oldState, newState, participants
        );
        
        Bukkit.getPluginManager().callEvent(event);
        
        logger.debug("Fired quest state change event: {} {} -> {}", 
            quest.getId(), oldState, newState);
    }
}
```

### Listening for Quest Events

```java
public class CrossQuestEventListener implements Listener {
    
    @EventHandler
    public void onQuestStateChange(QuestStateChangeEvent event) {
        if (event.getNewState() == QuestState.COMPLETED) {
            handleQuestCompletion(event);
        }
    }
    
    private void handleQuestCompletion(QuestStateChangeEvent event) {
        String questId = event.getQuestId();
        
        // Check for quest chain dependencies
        List<String> dependentQuests = questChainManager.getDependentQuests(questId);
        
        for (String dependentQuestId : dependentQuests) {
            Quest dependentQuest = questManager.getQuest(dependentQuestId);
            if (dependentQuest != null && dependentQuest.getCurrentState() == QuestState.NOT_STARTED) {
                // Unlock dependent quest
                dependentQuest.advanceState(QuestState.AVAILABLE);
                logger.info("Unlocked dependent quest: {}", dependentQuestId);
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

