# RVNKQuests Objective System Guidelines

## Performance-Focused Objective Polling

### Efficient Objective Tracking

Quest objectives should be designed for optimal performance with minimal server impact:

```java
public class PerformantObjectiveListener implements Listener {
    private final Quest quest;
    private final FZLogger logger;
    private final ObjectiveCache cache;
    
    public PerformantObjectiveListener(Quest quest) {
        this.quest = quest;
        this.logger = LogManager.getInstance(quest.getPlugin(), getClass());
        this.cache = new ObjectiveCache();
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onObjectiveEvent(PlayerEvent event) {
        // Fast pre-filtering
        if (!isRelevantPlayer(event.getPlayer())) {
            return;
        }
        
        // Use cached data when possible
        ObjectiveProgress progress = cache.getProgress(event.getPlayer().getUniqueId());
        if (progress != null && !progress.needsUpdate()) {
            return;
        }
        
        // Process objective update
        updateObjectiveProgress(event.getPlayer(), event);
    }
}
```

### Objective State Caching

```java
public class ObjectiveCache {
    private final Map<UUID, ObjectiveProgress> progressCache = new ConcurrentHashMap<>();
    private final long cacheTimeout = 30000; // 30 seconds
    
    public ObjectiveProgress getProgress(UUID playerId) {
        ObjectiveProgress progress = progressCache.get(playerId);
        
        if (progress != null && !progress.isExpired()) {
            return progress;
        }
        
        // Cache miss or expired - remove stale entry
        progressCache.remove(playerId);
        return null;
    }
    
    public void updateProgress(UUID playerId, ObjectiveProgress progress) {
        progress.setLastUpdate(System.currentTimeMillis());
        progressCache.put(playerId, progress);
    }
    
    public void invalidatePlayer(UUID playerId) {
        progressCache.remove(playerId);
    }
}
```

### Batch Objective Processing

```java
public class BatchObjectiveProcessor {
    private final Map<String, List<ObjectiveUpdate>> pendingUpdates = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    
    public void queueObjectiveUpdate(String questId, ObjectiveUpdate update) {
        pendingUpdates.computeIfAbsent(questId, k -> new ArrayList<>()).add(update);
    }
    
    @PostConstruct
    public void startBatchProcessor() {
        executor.scheduleAtFixedRate(this::processBatches, 0, 1, TimeUnit.SECONDS);
    }
    
    private void processBatches() {
        for (Map.Entry<String, List<ObjectiveUpdate>> entry : pendingUpdates.entrySet()) {
            String questId = entry.getKey();
            List<ObjectiveUpdate> updates = entry.getValue();
            
            if (!updates.isEmpty()) {
                processBatchForQuest(questId, new ArrayList<>(updates));
                updates.clear();
            }
        }
    }
    
    private void processBatchForQuest(String questId, List<ObjectiveUpdate> updates) {
        // Group updates by player
        Map<UUID, List<ObjectiveUpdate>> playerUpdates = updates.stream()
            .collect(Collectors.groupingBy(ObjectiveUpdate::getPlayerId));
        
        // Process each player's updates in batch
        for (Map.Entry<UUID, List<ObjectiveUpdate>> playerEntry : playerUpdates.entrySet()) {
            processPlayerObjectives(questId, playerEntry.getKey(), playerEntry.getValue());
        }
    }
}
```

## Objective Types and Patterns

### Collection Objectives

```java
public class CollectionObjective extends BaseObjective {
    private final Material targetItem;
    private final int requiredAmount;
    private final Map<UUID, Integer> playerProgress = new ConcurrentHashMap<>();
    
    public CollectionObjective(String id, Material item, int amount) {
        super(id, "Collect " + amount + " " + item.name().toLowerCase());
        this.targetItem = item;
        this.requiredAmount = amount;
    }
    
    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        ItemStack item = event.getItem().getItemStack();
        
        if (item.getType() != targetItem) {
            return;
        }
        
        // Update progress efficiently
        updateCollectionProgress(player.getUniqueId(), item.getAmount());
    }
    
    private void updateCollectionProgress(UUID playerId, int amount) {
        int currentAmount = playerProgress.getOrDefault(playerId, 0);
        int newAmount = Math.min(currentAmount + amount, requiredAmount);
        
        playerProgress.put(playerId, newAmount);
        
        // Check completion
        if (newAmount >= requiredAmount) {
            completeObjective(playerId);
        } else {
            // Send progress update
            sendProgressUpdate(playerId, newAmount, requiredAmount);
        }
    }
}
```

### Location-Based Objectives

```java
public class LocationObjective extends BaseObjective {
    private final Location targetLocation;
    private final double radius;
    private final Set<UUID> completedPlayers = ConcurrentHashMap.newKeySet();
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Skip if already completed
        if (completedPlayers.contains(playerId)) {
            return;
        }
        
        Location to = event.getTo();
        if (to != null && isWithinRadius(to, targetLocation, radius)) {
            completeObjective(playerId);
            completedPlayers.add(playerId);
        }
    }
    
    private boolean isWithinRadius(Location loc1, Location loc2, double radius) {
        return loc1.getWorld().equals(loc2.getWorld()) && 
               loc1.distance(loc2) <= radius;
    }
}
```

### Combat Objectives

```java
public class CombatObjective extends BaseObjective {
    private final EntityType targetType;
    private final int requiredKills;
    private final Map<UUID, Integer> killCounts = new ConcurrentHashMap<>();
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        Player killer = entity.getKiller();
        
        if (killer == null || entity.getType() != targetType) {
            return;
        }
        
        UUID killerId = killer.getUniqueId();
        int currentKills = killCounts.getOrDefault(killerId, 0) + 1;
        killCounts.put(killerId, currentKills);
        
        if (currentKills >= requiredKills) {
            completeObjective(killerId);
        } else {
            sendProgressUpdate(killerId, currentKills, requiredKills);
        }
    }
}
```

### Interaction Objectives

```java
public class InteractionObjective extends BaseObjective {
    private final Material targetBlock;
    private final Action requiredAction;
    private final Set<UUID> completedPlayers = ConcurrentHashMap.newKeySet();
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != requiredAction) {
            return;
        }
        
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != targetBlock) {
            return;
        }
        
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        if (!completedPlayers.contains(playerId)) {
            completeObjective(playerId);
            completedPlayers.add(playerId);
        }
    }
}
```

## Advanced Objective Patterns

### Multi-Player Objectives

```java
public class MultiPlayerObjective extends BaseObjective {
    private final int requiredPlayerCount;
    private final Location targetLocation;
    private final double radius;
    private final Set<UUID> participatingPlayers = ConcurrentHashMap.newKeySet();
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        
        if (to != null && isWithinRadius(to, targetLocation, radius)) {
            participatingPlayers.add(player.getUniqueId());
            
            // Check if we have enough players
            if (participatingPlayers.size() >= requiredPlayerCount) {
                // Complete objective for all participating players
                for (UUID playerId : participatingPlayers) {
                    completeObjective(playerId);
                }
            }
        } else {
            participatingPlayers.remove(player.getUniqueId());
        }
    }
}
```

### Time-Limited Objectives

```java
public class TimedObjective extends BaseObjective {
    private final long timeLimit; // in milliseconds
    private final long startTime;
    private BukkitTask timeoutTask;
    
    public TimedObjective(String id, String description, long timeLimitSeconds) {
        super(id, description);
        this.timeLimit = timeLimitSeconds * 1000;
        this.startTime = System.currentTimeMillis();
    }
    
    @Override
    public void activate() {
        super.activate();
        
        // Schedule timeout
        long timeoutTicks = (timeLimit / 50); // Convert to ticks
        timeoutTask = Bukkit.getScheduler().runTaskLater(
            getPlugin(), 
            this::onTimeout, 
            timeoutTicks
        );
    }
    
    @Override
    public void completeObjective(UUID playerId) {
        // Cancel timeout if objective is completed
        if (timeoutTask != null && !timeoutTask.isCancelled()) {
            timeoutTask.cancel();
        }
        
        super.completeObjective(playerId);
    }
    
    private void onTimeout() {
        logger.info("Objective {} timed out", getId());
        failObjective("Time limit exceeded");
    }
    
    public long getRemainingTime() {
        long elapsed = System.currentTimeMillis() - startTime;
        return Math.max(0, timeLimit - elapsed);
    }
}
```

### Conditional Objectives

```java
public class ConditionalObjective extends BaseObjective {
    private final List<ObjectiveCondition> conditions;
    private final Map<UUID, Map<String, Boolean>> playerConditionStates = new ConcurrentHashMap<>();
    
    public void checkConditions(UUID playerId) {
        Map<String, Boolean> playerStates = playerConditionStates.get(playerId);
        if (playerStates == null) {
            return;
        }
        
        // Check if all conditions are met
        boolean allConditionsMet = conditions.stream()
            .allMatch(condition -> playerStates.getOrDefault(condition.getId(), false));
        
        if (allConditionsMet) {
            completeObjective(playerId);
        }
    }
    
    public void updateCondition(UUID playerId, String conditionId, boolean state) {
        playerConditionStates.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                             .put(conditionId, state);
        checkConditions(playerId);
    }
}
```

## Objective Progress Tracking

### Progress Serialization

```java
public class ObjectiveProgressManager {
    
    public CompletableFuture<Void> saveProgress(UUID playerId, String questId, 
                                                ObjectiveProgress progress) {
        return CompletableFuture.runAsync(() -> {
            try {
                String json = gson.toJson(progress);
                dataService.saveObjectiveProgress(playerId, questId, json);
                logger.debug("Saved objective progress for player {} in quest {}", 
                           playerId, questId);
            } catch (Exception e) {
                logger.error("Failed to save objective progress for player {} in quest {}", 
                           playerId, questId, e);
            }
        });
    }
    
    public CompletableFuture<ObjectiveProgress> loadProgress(UUID playerId, String questId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String json = dataService.loadObjectiveProgress(playerId, questId);
                if (json != null) {
                    return gson.fromJson(json, ObjectiveProgress.class);
                }
                return new ObjectiveProgress(); // Default progress
            } catch (Exception e) {
                logger.error("Failed to load objective progress for player {} in quest {}", 
                           playerId, questId, e);
                return new ObjectiveProgress(); // Return default on error
            }
        });
    }
}
```

### Progress Validation

```java
public class ObjectiveValidator {
    
    public boolean validateProgress(ObjectiveProgress progress, Objective objective) {
        // Validate progress bounds
        if (progress.getCurrent() < 0 || progress.getCurrent() > progress.getRequired()) {
            logger.warn("Invalid objective progress: {} / {}", 
                       progress.getCurrent(), progress.getRequired());
            return false;
        }
        
        // Validate completion state
        if (progress.isCompleted() && progress.getCurrent() < progress.getRequired()) {
            logger.warn("Objective marked as completed but progress insufficient: {} / {}", 
                       progress.getCurrent(), progress.getRequired());
            return false;
        }
        
        // Custom objective-specific validation
        return objective.validateProgress(progress);
    }
    
    public ObjectiveProgress sanitizeProgress(ObjectiveProgress progress, Objective objective) {
        // Clamp values to valid ranges
        int sanitizedCurrent = Math.max(0, Math.min(progress.getCurrent(), progress.getRequired()));
        
        // Update completion state based on progress
        boolean isCompleted = sanitizedCurrent >= progress.getRequired();
        
        return new ObjectiveProgress(sanitizedCurrent, progress.getRequired(), isCompleted);
    }
}
```

## Performance Monitoring

### Objective Performance Metrics

```java
public class ObjectiveMetrics {
    private final Map<String, AtomicLong> eventCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> processingTimes = new ConcurrentHashMap<>();
    
    public void recordEvent(String objectiveId) {
        eventCounts.computeIfAbsent(objectiveId, k -> new AtomicLong()).incrementAndGet();
    }
    
    public void recordProcessingTime(String objectiveId, long timeMs) {
        processingTimes.computeIfAbsent(objectiveId, k -> new AtomicLong()).addAndGet(timeMs);
    }
    
    public void logMetrics() {
        logger.info("Objective Performance Metrics:");
        for (String objectiveId : eventCounts.keySet()) {
            long events = eventCounts.get(objectiveId).get();
            long totalTime = processingTimes.getOrDefault(objectiveId, new AtomicLong()).get();
            double avgTime = events > 0 ? (double) totalTime / events : 0;
            
            logger.info("  {}: {} events, avg {}ms processing time", 
                       objectiveId, events, String.format("%.2f", avgTime));
        }
    }
}
```