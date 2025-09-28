# RVNKQuests RVNKCore Integration Guidelines

## Service Integration Pattern

### Optional RVNKCore Dependency

RVNKQuests is designed to work with or without RVNKCore, providing fallback implementations when core services are unavailable.

```java
public class QuestService {
    private final PlayerService playerService;
    private final DataService dataService;
    
    public QuestService(RVNKQuests plugin) {
        if (plugin.isRVNKCoreAvailable()) {
            // Use RVNKCore services
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

### Service Detection

```java
public class RVNKQuests extends JavaPlugin {
    private boolean rvnkCoreAvailable = false;
    
    @Override
    public void onEnable() {
        // Check for RVNKCore availability
        Plugin rvnkCore = getServer().getPluginManager().getPlugin("RVNKCore");
        this.rvnkCoreAvailable = (rvnkCore != null && rvnkCore.isEnabled());
        
        if (rvnkCoreAvailable) {
            logger.info("RVNKCore detected, using shared services");
            initializeWithRVNKCore();
        } else {
            logger.info("RVNKCore not available, using local implementations");
            initializeStandalone();
        }
    }
    
    public boolean isRVNKCoreAvailable() {
        return rvnkCoreAvailable;
    }
}
```

## Database Integration

### Asynchronous Quest State Persistence

```java
public class QuestDataService {
    private final DataService coreDataService;
    private final LocalDatabase localDatabase;
    private final boolean useCore;
    
    public CompletableFuture<Void> saveQuestState(UUID playerId, String questId, QuestState state) {
        if (useCore) {
            return coreDataService.saveQuestProgress(playerId, questId, state);
        } else {
            return localDatabase.saveQuestState(playerId, questId, state);
        }
    }
    
    public CompletableFuture<QuestState> loadQuestState(UUID playerId, String questId) {
        if (useCore) {
            return coreDataService.getQuestProgress(playerId, questId)
                .thenApply(progress -> progress.getState());
        } else {
            return localDatabase.loadQuestState(playerId, questId);
        }
    }
}
```

### Local Database Fallback

```java
public class LocalDataService implements DataService {
    private final SQLiteDatabase database;
    
    public LocalDataService(RVNKQuests plugin) {
        this.database = new SQLiteDatabase(plugin.getDataFolder());
        initializeTables();
    }
    
    @Override
    public CompletableFuture<Void> saveQuestProgress(UUID playerId, String questId, QuestState state) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO quest_progress (player_id, quest_id, state, updated_at) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = database.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, questId);
                stmt.setString(3, state.name());
                stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save quest progress", e);
            }
        });
    }
}
```

## Player Service Integration

### Player Data Access

```java
public class QuestPlayerManager {
    private final PlayerService playerService;
    
    public CompletableFuture<Optional<Player>> getPlayerAsync(UUID playerId) {
        if (playerService instanceof CorePlayerService) {
            return playerService.getPlayerAsync(playerId);
        } else {
            // Fallback to Bukkit synchronous API
            return CompletableFuture.supplyAsync(() -> 
                Optional.ofNullable(Bukkit.getPlayer(playerId))
            );
        }
    }
    
    public CompletableFuture<Location> getPlayerLocation(UUID playerId) {
        return getPlayerAsync(playerId)
            .thenApply(optPlayer -> 
                optPlayer.map(Player::getLocation)
                         .orElse(null)
            );
    }
}
```

### Player Quest History

```java
public class PlayerQuestHistory {
    
    public CompletableFuture<List<QuestProgress>> getCompletedQuests(UUID playerId) {
        if (useCore) {
            return coreDataService.getPlayerQuestHistory(playerId);
        } else {
            return localDatabase.getCompletedQuestsForPlayer(playerId);
        }
    }
    
    public CompletableFuture<Void> recordQuestCompletion(UUID playerId, String questId, long completionTime) {
        QuestCompletion completion = new QuestCompletion(playerId, questId, completionTime);
        
        if (useCore) {
            return coreDataService.recordQuestCompletion(completion);
        } else {
            return localDatabase.saveQuestCompletion(completion);
        }
    }
}
```

## Event System Integration

### Cross-Plugin Quest Events

```java
public class QuestEventManager {
    
    public void fireQuestStateChangeEvent(Quest quest, QuestState oldState, QuestState newState) {
        if (rvnkCoreAvailable) {
            // Use RVNKCore event system for cross-plugin communication
            QuestStateChangeEvent coreEvent = new QuestStateChangeEvent(
                quest.getId(), oldState, newState
            );
            RVNKCore.getEventManager().fireEvent(coreEvent);
        }
        
        // Always fire local Bukkit event
        QuestStateChangeBukkitEvent bukkitEvent = new QuestStateChangeBukkitEvent(
            quest, oldState, newState
        );
        Bukkit.getPluginManager().callEvent(bukkitEvent);
    }
}
```

### Listening for Core Events

```java
@EventHandler
public void onPlayerWorldChange(PlayerWorldChangeEvent event) {
    if (rvnkCoreAvailable) {
        // Enhanced world change tracking via RVNKCore
        handleCoreWorldChangeEvent(event);
    } else {
        // Basic world change handling
        handleBasicWorldChangeEvent(event);
    }
}
```

## Configuration Integration

### Shared Configuration Services

```java
public class QuestConfigManager {
    private final ConfigService configService;
    private final FileConfiguration localConfig;
    
    public QuestConfigManager(RVNKQuests plugin) {
        if (plugin.isRVNKCoreAvailable()) {
            this.configService = RVNKCore.getServiceRegistry().getService(ConfigService.class);
        } else {
            this.configService = null;
        }
        this.localConfig = plugin.getConfig();
    }
    
    public boolean getBoolean(String path, boolean defaultValue) {
        if (configService != null) {
            return configService.getBoolean("rvnkquests." + path, defaultValue);
        } else {
            return localConfig.getBoolean(path, defaultValue);
        }
    }
    
    public void reloadConfig() {
        if (configService != null) {
            configService.reloadConfig("rvnkquests");
        } else {
            plugin.reloadConfig();
        }
    }
}
```

## Migration Path Planning

### Phase 1: Optional Integration

```java
// Current state: RVNKQuests works independently
// Goal: Add optional RVNKCore integration without breaking existing functionality

public class QuestManager {
    public void initializeServices() {
        if (plugin.isRVNKCoreAvailable()) {
            logger.info("Initializing with RVNKCore integration");
            this.dataService = new CoreQuestDataService();
            this.playerService = RVNKCore.getServiceRegistry().getService(PlayerService.class);
        } else {
            logger.info("Initializing standalone mode");
            this.dataService = new LocalQuestDataService();
            this.playerService = new LocalPlayerService();
        }
    }
}
```

### Phase 2: Deep Integration

```java
// Future state: Full RVNKCore integration with advanced features
// Enhanced quest sharing, cross-plugin quest dependencies, centralized player data

public class AdvancedQuestManager {
    
    public CompletableFuture<List<Quest>> getAvailableQuests(UUID playerId) {
        // Use RVNKCore's cross-plugin quest registry
        return RVNKCore.getQuestRegistry()
            .getAvailableQuests(playerId)
            .thenApply(quests -> 
                quests.stream()
                      .filter(quest -> quest.getPlugin().equals("RVNKQuests"))
                      .collect(Collectors.toList())
            );
    }
    
    public CompletableFuture<Void> createQuestDependency(String questId, String dependentQuestId) {
        return RVNKCore.getQuestRegistry()
            .createDependency(questId, dependentQuestId);
    }
}
```

### Backward Compatibility

```java
public class CompatibilityLayer {
    
    // Maintain compatibility with existing quest data during migration
    public CompletableFuture<Void> migrateLocalDataToCore() {
        return localDatabase.getAllQuestProgress()
            .thenCompose(progressList -> {
                List<CompletableFuture<Void>> migrations = progressList.stream()
                    .map(progress -> coreDataService.saveQuestProgress(
                        progress.getPlayerId(),
                        progress.getQuestId(),
                        progress.getState()
                    ))
                    .collect(Collectors.toList());
                
                return CompletableFuture.allOf(migrations.toArray(new CompletableFuture[0]));
            });
    }
}
```

