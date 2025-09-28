# RVNKQuests Migration and Compatibility Guidelines

## Legacy Debug to LogManager Migration

### Migration Process Overview

When updating existing RVNKQuests code from the legacy Debug system to the new LogManager:

1. **Identify Debug Usage**: Find all instances of Debug class usage
2. **Replace with LogManager**: Convert to LogManager pattern
3. **Update Method Calls**: Use new logging API
4. **Test Output**: Verify logging functionality
5. **Remove Dependencies**: Clean up deprecated imports

### Step 1: Debug Instance Replacement

#### Old Pattern (DEPRECATED)
```java
private final Debug debug = Debug.createDebugger(plugin, "ClassName", Level.INFO);
```

#### New Pattern (REQUIRED)
```java
private final RVNKLogger logger = LogManager.getInstance(plugin, getClass());
```

### Step 2: Method Call Migration

#### Basic Logging Calls

**Old:**
```java
debug.info("Message");
debug.warning("Warning message");
debug.error("Error message", exception);
debug.debug("Debug information");
```

**New:**
```java
logger.info("Message");
logger.warn("Warning message");
logger.error("Error message", exception);
logger.debug("Debug information");
```

#### Parameterized Logging

**Old:**
```java
debug.info("Quest " + questId + " completed by " + playerName);
debug.error("Failed to load quest " + questId + " for player " + playerId);
```

**New:**
```java
logger.info("Quest {} completed by {}", questId, playerName);
logger.error("Failed to load quest {} for player {}", questId, playerId);
```

### Step 3: Performance Monitoring Migration

#### Timing Operations

**Old:**
```java
long startTime = System.currentTimeMillis();
// ... operation ...
long duration = System.currentTimeMillis() - startTime;
debug.info("Operation took " + duration + "ms");
```

**New:**
```java
logger.startTiming("operation_name");
// ... operation ...
long duration = logger.endTiming("operation_name");
```

### Complete Migration Example

#### Before Migration
```java
public class OldQuestManager {
    private final Debug debug;
    
    public OldQuestManager(RVNKQuests plugin) {
        this.debug = Debug.createDebugger(plugin, "QuestManager", Level.INFO);
    }
    
    public void loadQuest(String questId) {
        debug.info("Loading quest: " + questId);
        
        try {
            long start = System.currentTimeMillis();
            Quest quest = questLoader.load(questId);
            long duration = System.currentTimeMillis() - start;
            
            debug.info("Quest " + questId + " loaded in " + duration + "ms");
            activeQuests.put(questId, quest);
            
        } catch (Exception e) {
            debug.error("Failed to load quest: " + questId, e);
        }
    }
}
```

#### After Migration
```java
public class QuestManager {
    private final RVNKLogger logger;
    
    public QuestManager(RVNKQuests plugin) {
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    public void loadQuest(String questId) {
        logger.info("Loading quest: {}", questId);
        
        try {
            logger.startTiming("quest_load");
            Quest quest = questLoader.load(questId);
            long duration = logger.endTiming("quest_load");
            
            logger.info("Quest {} loaded in {}ms", questId, duration);
            activeQuests.put(questId, quest);
            
        } catch (Exception e) {
            logger.error("Failed to load quest: {}", questId, e);
        }
    }
}
```

## Configuration Migration

### YAML Configuration Updates

#### Legacy Configuration Structure
```yaml
# Old configuration format
debug:
  enabled: true
  level: INFO
  
quests:
  piglin_far_from_home:
    enable: true
    world: "world"
    debug_mode: true
```

#### Updated Configuration Structure
```yaml
# New configuration format
logging:
  level: INFO
  performance_monitoring: true
  
quests:
  piglin_far_from_home:
    enabled: true
    world: "world"
    config:
      timeout_minutes: 30
      max_participants: 5
```

### Configuration Migration Utility

```java
public class ConfigMigrationManager {
    private final RVNKQuests plugin;
    private final RVNKLogger logger;
    
    public void migrateConfiguration() {
        FileConfiguration config = plugin.getConfig();
        boolean needsSave = false;
        
        // Migrate debug settings to logging
        if (config.contains("debug")) {
            migrateDebugSettings(config);
            needsSave = true;
        }
        
        // Migrate quest enable/disable format
        if (hasLegacyQuestFormat(config)) {
            migrateLegacyQuestFormat(config);
            needsSave = true;
        }
        
        if (needsSave) {
            plugin.saveConfig();
            logger.info("Configuration migrated to new format");
        }
    }
    
    private void migrateDebugSettings(FileConfiguration config) {
        if (config.getBoolean("debug.enabled", false)) {
            config.set("logging.level", config.getString("debug.level", "INFO"));
            config.set("logging.performance_monitoring", true);
        }
        
        config.set("debug", null); // Remove old section
        logger.info("Migrated debug settings to logging configuration");
    }
    
    private void migrateLegacyQuestFormat(FileConfiguration config) {
        ConfigurationSection questsSection = config.getConfigurationSection("quests");
        if (questsSection == null) return;
        
        for (String questId : questsSection.getKeys(false)) {
            ConfigurationSection questConfig = questsSection.getConfigurationSection(questId);
            if (questConfig == null) continue;
            
            // Migrate 'enable' to 'enabled'
            if (questConfig.contains("enable")) {
                boolean enabled = questConfig.getBoolean("enable");
                questConfig.set("enabled", enabled);
                questConfig.set("enable", null);
            }
            
            // Migrate debug_mode to config section
            if (questConfig.contains("debug_mode")) {
                questConfig.set("config.debug_mode", questConfig.getBoolean("debug_mode"));
                questConfig.set("debug_mode", null);
            }
        }
        
        logger.info("Migrated quest configuration format");
    }
}
```

## Data Migration

### Quest Progress Data Migration

```java
public class QuestDataMigration {
    
    public CompletableFuture<Void> migrateQuestProgressData() {
        logger.info("Starting quest progress data migration");
        
        return loadLegacyQuestData()
            .thenCompose(this::convertToNewFormat)
            .thenCompose(this::saveConvertedData)
            .thenRun(() -> {
                logger.info("Quest progress data migration completed");
            })
            .exceptionally(ex -> {
                logger.error("Failed to migrate quest progress data", ex);
                return null;
            });
    }
    
    private CompletableFuture<List<LegacyQuestProgress>> loadLegacyQuestData() {
        return CompletableFuture.supplyAsync(() -> {
            // Load from old format (file-based, old database schema, etc.)
            return legacyDataLoader.loadAllQuestProgress();
        });
    }
    
    private CompletableFuture<List<QuestProgress>> convertToNewFormat(List<LegacyQuestProgress> legacyData) {
        return CompletableFuture.supplyAsync(() -> {
            return legacyData.stream()
                .map(this::convertProgress)
                .collect(Collectors.toList());
        });
    }
    
    private QuestProgress convertProgress(LegacyQuestProgress legacy) {
        return QuestProgress.builder()
            .playerId(legacy.getPlayerId())
            .questId(legacy.getQuestId())
            .state(mapLegacyState(legacy.getState()))
            .progress(legacy.getProgressData())
            .startTime(legacy.getStartTime())
            .lastUpdate(legacy.getLastUpdate())
            .build();
    }
}
```

### Database Schema Migration

```java
public class DatabaseSchemaMigration {
    
    public void migrateSchema(String fromVersion, String toVersion) {
        logger.info("Migrating database schema from {} to {}", fromVersion, toVersion);
        
        List<MigrationScript> scripts = getMigrationScripts(fromVersion, toVersion);
        
        for (MigrationScript script : scripts) {
            try {
                executeMigrationScript(script);
                logger.debug("Executed migration script: {}", script.getName());
            } catch (SQLException e) {
                logger.error("Failed to execute migration script: {}", script.getName(), e);
                throw new RuntimeException("Schema migration failed", e);
            }
        }
        
        updateSchemaVersion(toVersion);
        logger.info("Database schema migration completed");
    }
    
    private void executeMigrationScript(MigrationScript script) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            for (String sql : script.getSqlStatements()) {
                stmt.execute(sql);
            }
        }
    }
}
```

## RVNKCore Integration Path

### Phase 1: Optional Integration (Current)

The current implementation supports optional RVNKCore integration without breaking existing functionality:

```java
public class Phase1Integration {
    
    public void initializeServices() {
        if (plugin.isRVNKCoreAvailable()) {
            logger.info("RVNKCore detected - enabling enhanced features");
            initializeWithCore();
        } else {
            logger.info("Running in standalone mode");
            initializeStandalone();
        }
    }
    
    private void initializeWithCore() {
        // Optional enhanced features when RVNKCore is available
        this.dataService = new CoreIntegratedDataService();
        this.eventManager = new CrossPluginEventManager();
        this.configService = RVNKCore.getServiceRegistry().getService(ConfigService.class);
    }
    
    private void initializeStandalone() {
        // Full functionality using local implementations
        this.dataService = new LocalDataService();
        this.eventManager = new LocalEventManager();
        this.configService = new LocalConfigService();
    }
}
```

### Phase 2: Deep Integration (Future)

Future integration will provide enhanced cross-plugin features while maintaining backward compatibility:

```java
public class Phase2Integration {
    
    public void enableDeepIntegration() {
        // Enhanced quest sharing between plugins
        registerWithCrossPluginQuestRegistry();
        
        // Centralized player progression tracking
        initializeCentralizedPlayerData();
        
        // Advanced quest dependency management
        enableQuestChainManagement();
    }
    
    private void registerWithCrossPluginQuestRegistry() {
        QuestRegistry registry = RVNKCore.getServiceRegistry().getService(QuestRegistry.class);
        
        // Register all RVNKQuests with central registry
        for (Quest quest : getAllQuests()) {
            registry.registerQuest(quest.getId(), quest.getClass(), "RVNKQuests");
        }
        
        logger.info("Registered {} quests with cross-plugin registry", getAllQuests().size());
    }
}
```

### Migration Compatibility Layer

```java
public class MigrationCompatibilityLayer {
    
    /**
     * Provides backward compatibility during migration phases
     */
    public void maintainBackwardCompatibility() {
        // Support old API methods during transition
        supportLegacyAPI();
        
        // Provide data format compatibility
        maintainDataCompatibility();
        
        // Support configuration format transitions
        supportConfigurationCompatibility();
    }
    
    @Deprecated
    public void legacyMethod(String questId) {
        logger.warn("Using deprecated method - please update to new API");
        // Delegate to new implementation
        newMethod(questId);
    }
    
    private void supportLegacyAPI() {
        // Maintain old method signatures for external plugins
        // that might depend on RVNKQuests API
        ApiCompatibilityManager.registerLegacyMethods(this);
    }
}
```

## Testing Migration

### Migration Testing Framework

```java
public class MigrationTestFramework {
    
    @Test
    public void testLoggerMigration() {
        // Test that old debug calls work with new logger
        QuestManager manager = new QuestManager(plugin);
        
        // Verify logging output
        assertLoggingWorks(manager);
        assertPerformanceMonitoring(manager);
    }
    
    @Test
    public void testConfigurationMigration() {
        // Create legacy configuration
        FileConfiguration legacy = createLegacyConfig();
        
        // Run migration
        ConfigMigrationManager migration = new ConfigMigrationManager(plugin);
        migration.migrateConfiguration();
        
        // Verify new configuration structure
        FileConfiguration newConfig = plugin.getConfig();
        assertConfigurationMigrated(newConfig);
    }
    
    @Test
    public void testDataMigration() {
        // Create legacy quest progress data
        List<LegacyQuestProgress> legacyData = createLegacyData();
        
        // Run migration
        QuestDataMigration migration = new QuestDataMigration();
        migration.migrateQuestProgressData().join();
        
        // Verify data integrity
        verifyMigratedData();
    }
    
    private void assertLoggingWorks(QuestManager manager) {
        // Capture log output
        TestLogAppender appender = new TestLogAppender();
        
        manager.loadQuest("test_quest");
        
        // Verify log messages were written correctly
        assertTrue(appender.getMessages().contains("Loading quest: test_quest"));
    }
}
```

### Rollback Procedures

```java
public class MigrationRollback {
    
    public void rollbackMigration(String migrationId) {
        logger.warn("Rolling back migration: {}", migrationId);
        
        MigrationRecord record = getMigrationRecord(migrationId);
        if (record == null) {
            throw new IllegalStateException("Migration record not found: " + migrationId);
        }
        
        try {
            // Restore configuration backup
            restoreConfigurationBackup(record.getConfigBackupPath());
            
            // Restore data backup
            restoreDataBackup(record.getDataBackupPath());
            
            // Revert code changes if necessary
            revertCodeChanges(record);
            
            logger.info("Successfully rolled back migration: {}", migrationId);
            
        } catch (Exception e) {
            logger.error("Failed to rollback migration: {}", migrationId, e);
            throw new RuntimeException("Rollback failed", e);
        }
    }
    
    private void createMigrationBackup(String migrationId) {
        // Backup current configuration
        backupConfiguration(migrationId);
        
        // Backup current data
        backupData(migrationId);
        
        // Record migration details
        recordMigration(migrationId);
    }
}
```

