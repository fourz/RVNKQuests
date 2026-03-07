# Bukkit/Spigot Framework Usage Guidelines

## ✅ **WORKING IMPLEMENTATION STATUS** (Updated: October 11, 2025)

**Current Status**: RVNKQuests has established core plugin functionality with quest management system

**Production-Ready Plugin**: `src/main/java/rvnk/rvnkquests/RVNKQuests.java` contains:
- ✅ **Quest System**: Dynamic quest management with state tracking
- ✅ **Spigot 1.21.4**: Latest Spigot version tested
- ✅ **Command Framework**: Successfully configured quest admin tools
- ✅ **Event System**: Complete listener management based on quest states
- ✅ **Configuration**: YAML-based configuration system

**Verified Working Patterns**:
- Plugin lifecycle (onEnable/onDisable)
- Event listener registration/unregistration
- Command registration and handling
- Configuration loading and validation

**Ready for**: Bukkit/Spigot 1.21.4 servers with quest-driven gameplay

## Bukkit/Spigot Framework Context

**Spigot** is the enhanced server implementation of Bukkit API for Minecraft servers. It provides:

- **Event System**: Comprehensive event handling for server and player actions
- **Command Framework**: Built-in command registration and processing
- **Configuration API**: YAML-based configuration with validation
- **Scheduler**: Asynchronous and synchronous task execution
- **Plugin Management**: Lifecycle management and dependency resolution

## Bukkit Plugin Pattern

```java
// RVNKQuests main plugin class pattern
public class RVNKQuests extends JavaPlugin {
    private final RVNKLogger logger = LogManager.getInstance(this, getClass());
    private QuestManager questManager;
    private CommandManager commandManager;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        logger.info("Initializing RVNKQuests plugin");
        
        // Initialize configuration
        configManager = new ConfigManager(this);
        
        // Initialize core managers
        questManager = new QuestManager(this);
        commandManager = new CommandManager(this);
        
        // Register commands and events
        registerCommands();
        registerEvents();
        
        logger.info("RVNKQuests enabled successfully");
    }

    @Override
    public void onDisable() {
        logger.info("Shutting down RVNKQuests");
        
        // Clean up resources
        if (questManager != null) {
            questManager.shutdown();
        }
        
        logger.info("RVNKQuests disabled");
    }
}
```

## 🗂️ Working Directory Context & Navigation

**CRITICAL**: Always validate your working directory before executing commands.

### Bukkit Plugin Project Navigation

```powershell
# Navigate to project root
Set-Location "c:\tools\RVNKQuests"

# Navigate to main development directory
Set-Location "c:\tools\RVNKQuests\src\main\java\rvnk\rvnkquests"

# Navigate to resources directory
Set-Location "c:\tools\RVNKQuests\src\main\resources"
```

### Path Validation Commands

```powershell
# Verify current location
Get-Location
Get-ChildItem  # Confirm expected files exist

# Test for specific files
Test-Path "pom.xml"
Test-Path "src\main\java\rvnk\rvnkquests\RVNKQuests.java"
```

## Bukkit Implementation Best Practices

### 1. Event Handling
- Use @EventHandler annotation with proper priority
- Unregister listeners when no longer needed
- Check event cancellation status appropriately
- Use specific event types for better performance

### 2. Command Framework
- Extend appropriate command base classes
- Implement proper permission checking
- Provide meaningful error messages
- Support tab completion where applicable

### 3. Configuration Management
- Use FileConfiguration for YAML handling
- Validate configuration values on load
- Provide sensible defaults
- Save default configuration on first run

### 4. Resource Management
- Cancel scheduled tasks in onDisable()
- Unregister all event listeners
- Close database connections properly
- Clean up temporary files and data

## Event System Integration

### Dynamic Listener Registration

```java
// Quest-specific listener management
public class QuestManager {
    private final Map<String, List<Listener>> registeredListeners = new HashMap<>();
    
    public void updateQuestListeners(Quest quest) {
        // Unregister old listeners
        unregisterListenersForQuest(quest.getId());
        
        // Register new listeners for current state
        List<Listener> listeners = quest.createListenersForState(quest.getCurrentState());
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, plugin);
        }
        
        registeredListeners.put(quest.getId(), listeners);
    }
}
```

### Event Priority Guidelines

```java
public class QuestEventHandler {
    // Use MONITOR for tracking (don't modify events)
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
}
```

## Configuration Integration

### YAML Configuration Pattern

```java
public class ConfigManager {
    private final RVNKQuests plugin;
    private FileConfiguration config;
    
    public ConfigManager(RVNKQuests plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }
    
    private void loadConfiguration() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        
        // Validate configuration
        validateConfiguration();
    }
    
    public boolean getQuestEnabled(String questId) {
        return config.getBoolean("quests." + questId + ".enabled", true);
    }
}
```

## Command Framework Integration

### Command Registration

```java
public class CommandManager {
    public void registerCommands() {
        // Register main quest command
        QuestCommand questCommand = new QuestCommand(plugin);
        plugin.getCommand("quest").setExecutor(questCommand);
        plugin.getCommand("quest").setTabCompleter(questCommand);
    }
}
```

## Best Practices for Code Reviews

### Security
- Validate all player input in commands
- Check permissions before executing operations
- Sanitize file paths and database queries
- Use secure configuration loading

### Performance
- Minimize event handler processing time
- Use asynchronous operations for I/O
- Cache frequently accessed data
- Unregister unused listeners promptly

### Code Quality
- Follow Bukkit naming conventions
- Use appropriate Bukkit data types
- Handle edge cases (offline players, etc.)
- Document event handler behavior

## Key Reminders

- **Plugin Lifecycle**: Always clean up in onDisable()
- **Event Handling**: Use appropriate event priorities and check cancellation
- **Async Operations**: Use Bukkit scheduler for thread management
- **Configuration**: Validate all configuration values on load
- **Commit frequently** with descriptive messages following conventional commit format