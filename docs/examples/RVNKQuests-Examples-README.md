# RVNKQuests Examples and Documentation

This directory contains example implementations, usage patterns, and documentation for the RVNKQuests plugin.

## LogManager Examples (New Pattern)

### Core Files

- **`log/LogManagerRVNKQuests.java`** - Complete LogManager implementation for RVNKQuests
- **`log/DebugRVNKQuests.java`** - Enhanced Debug class with performance monitoring and diagnostics
- **`log/LogManagerUsageExample.java`** - Comprehensive usage examples showing proper implementation patterns

### Documentation

- **`LogManager-Interface.md`** - Interface definition and standard usage patterns
- **`LogManager-Migration-Guide.md`** - Detailed migration guide from Debug class to LogManager

### Key LogManager Features

1. **Automatic Class Context**: Uses `getClass()` for proper logging context
2. **Performance Monitoring**: Built-in timing with `startTiming()`/`endTiming()`
3. **Enhanced Error Analysis**: Detailed exception analysis with diagnostic suggestions
4. **Centralized Management**: Global log level updates and configuration
5. **Thread Safety**: Safe for concurrent access across multiple threads

### Standard Usage Pattern

```java
public class MyQuestClass {
    // REQUIRED: Use LogManager with getClass() parameter
    private final LogManager logger;
    
    public MyQuestClass(RVNKQuests plugin) {
        this.logger = LogManager.getInstance(plugin, getClass());
        logger.info("Class initialized");
    }
    
    public void performOperation() {
        logger.startTiming("operation");
        try {
            // ... operation code ...
            logger.info("Operation completed");
        } catch (Exception e) {
            logger.error("Operation failed", e);
        } finally {
            logger.endTiming("operation");
        }
    }
}
```

## Command Framework Examples

### Core Files

- **`command/manager/`** - Complete command framework implementation
  - `CommandManager.java` - Central command coordination
  - `BaseCommand.java` - Base command implementation
  - `SubCommand.java` - Subcommand framework
  - `TabCompletionUtil.java` - Tab completion utilities

### Command Examples

- **`command/manager/commands/`** - Various command implementations
  - `DebugSubCommand.java` - Debug command for quest information
  - `ReloadSubCommand.java` - Configuration reload command

### Usage Pattern

```java
public class MySubCommand extends SubCommand {
    private final LogManager logger;
    
    public MySubCommand(RVNKQuests plugin) {
        super(plugin, "mycommand", "Description", "/quest mycommand");
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        logger.debug("Executing command with " + args.length + " arguments");
        // Command logic here
        return true;
    }
}
```

## Migration Guide

### Priority: Q3 2025 Critical Task

The LogManager migration is a **critical priority** for Q3 2025. All existing code using the Debug class must be migrated to use LogManager.

### Migration Steps:

1. **Replace Debug Import**: `import org.fourz.RVNKQuests.debug.LogManager;`
2. **Update Field Declaration**: `private final LogManager logger = LogManager.getInstance(plugin, getClass());`
3. **Replace Method Calls**: `debug.info()` → `logger.info()`
4. **Add Performance Monitoring**: Use `startTiming()`/`endTiming()` for critical operations
5. **Test and Validate**: Ensure logging works correctly

### Benefits:

- **Consistency**: Aligns with RVNK ecosystem standards
- **Performance**: Built-in performance monitoring and optimization
- **Debugging**: Enhanced error analysis and diagnostic capabilities
- **Maintainability**: Centralized logging configuration and management

## Configuration Integration

### Log Level Configuration

```yaml
# config.yml
general:
  logLevel: INFO  # Options: OFF, SEVERE, WARNING, INFO, DEBUG
```

### Performance Monitoring

When debug logging is enabled, LogManager automatically monitors operation performance:

- **Warning Threshold**: 1000ms (1 second)
- **Severe Threshold**: 5000ms (5 seconds)
- **Automatic Diagnostics**: Provides solutions for common performance issues

## Best Practices

### 1. Always Use getClass()

```java
// CORRECT
private final LogManager logger = LogManager.getInstance(plugin, getClass());

// INCORRECT
private final LogManager logger = LogManager.getInstance(plugin, "MyClass");
```

### 2. Log State Transitions

```java
public void advanceQuestState(QuestState newState) {
    logger.debug("Advancing quest state from " + currentState + " to " + newState);
    // ... state change logic ...
    logger.info("Quest state advanced to " + newState);
}
```

### 3. Use Performance Monitoring

```java
public void processQuestObjective() {
    logger.startTiming("objective_processing");
    try {
        // ... processing logic ...
    } finally {
        long duration = logger.endTiming("objective_processing");
    }
}
```

### 4. Proper Error Handling

```java
try {
    processQuest();
} catch (Exception e) {
    logger.error("Failed to process quest: " + questId, e);
    // LogManager automatically provides diagnostic information
}
```

## Integration with RVNKCore

The LogManager pattern is designed for seamless integration with RVNKCore:

- Compatible with RVNKCore logging standards
- Supports shared logging configuration
- Enables cross-plugin log aggregation
- Maintains backward compatibility for standalone operation

## Future Enhancements

### Q4 2025 Plans:

- **Debug Class Removal**: Complete removal after migration
- **RVNKCore Integration**: Enhanced integration with shared logging services
- **Advanced Diagnostics**: AI-powered error analysis and suggestions
- **Performance Analytics**: Historical performance tracking and optimization recommendations

---

For detailed implementation examples, see the individual files in this directory. For migration assistance, refer to the `LogManager-Migration-Guide.md`.
