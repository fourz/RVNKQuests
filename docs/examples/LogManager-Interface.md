# LogManager Interface for RVNKQuests

This document defines the correct LogManager interface and usage pattern for the RVNKQuests plugin.

## Interface Definition

The LogManager for RVNKQuests should provide the following interface:

```java
package org.fourz.RVNKQuests.debug;

import org.fourz.RVNKQuests.RVNKQuests;
import java.util.logging.Level;

/**
 * LogManager interface for RVNKQuests logging operations.
 */
public class LogManager {
    
    /**
     * Get the LogManager instance for the specific plugin and class context.
     * @param plugin The plugin instance
     * @param clazz The class for logging context
     * @return The LogManager instance for the specified class
     */
    public static LogManager getInstance(RVNKQuests plugin, Class<?> clazz);
    
    /**
     * Log an informational message.
     * @param message The message to log
     */
    public void info(String message);
    
    /**
     * Log a warning message.
     * @param message The message to log
     */
    public void warning(String message);
    
    /**
     * Log an error message with an exception.
     * @param message The error message
     * @param throwable The throwable to log
     */
    public void error(String message, Throwable throwable);
    
    /**
     * Log a debug message.
     * @param message The message to log
     */
    public void debug(String message);
    
    /**
     * Start timing an operation for performance monitoring.
     * @param operationName Name of the operation to time
     */
    public void startTiming(String operationName);
    
    /**
     * End timing an operation and log the results.
     * @param operationName Name of the operation that was timed
     * @return The duration in milliseconds
     */
    public long endTiming(String operationName);
    
    /**
     * Set the log level for this LogManager instance.
     * @param level The new log level
     */
    public void setLogLevel(Level level);
    
    /**
     * Get the current log level for this LogManager instance.
     * @return The current log level
     */
    public Level getLogLevel();
}
```

## Standard Usage Pattern

### Class Implementation

```java
package org.fourz.RVNKQuests.quest;

import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.debug.LogManager;

public class ExampleQuestClass implements Quest {
    // REQUIRED: Use LogManager with getClass() parameter
    private final LogManager logger;
    private final RVNKQuests plugin;
    
    public ExampleQuestClass(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        
        // Standard initialization logging
        logger.info("Quest initialized: " + getId());
    }
    
    public void performOperation() {
        logger.debug("Starting quest operation");
        
        try {
            // Performance monitoring
            logger.startTiming("quest_operation");
            
            // ... perform operation ...
            
            logger.info("Quest operation completed successfully");
            
            long duration = logger.endTiming("quest_operation");
            
        } catch (Exception e) {
            logger.error("Quest operation failed", e);
        }
    }
}
```

### Migration from Debug Class

**OLD (Deprecated):**
```java
private final Debug debug = Debug.createDebugger(plugin, "ClassName", Level.INFO);
debug.info("Message");
debug.error("Error", exception);
```

**NEW (Required):**
```java
private final LogManager logger = LogManager.getInstance(plugin, getClass());
logger.info("Message");
logger.error("Error", exception);
```

## Key Benefits

1. **Automatic Context**: Uses `getClass()` for automatic class name detection
2. **Performance Monitoring**: Built-in timing capabilities with `startTiming()`/`endTiming()`
3. **Consistent Formatting**: Standardized log message format across all components
4. **Enhanced Error Analysis**: Better exception handling and diagnostics
5. **Centralized Management**: Global log level updates and configuration

## Configuration

The LogManager respects the plugin's configuration for log levels:

```yaml
# config.yml
general:
  logLevel: INFO  # Options: OFF, SEVERE, WARNING, INFO, DEBUG
```

## Performance Considerations

- LogManager includes efficient level checking to avoid unnecessary string operations
- Performance timing is only active when debug logging is enabled
- Thread-safe implementation for concurrent access
- Minimal memory overhead with singleton pattern per class

## Migration Priority

This LogManager pattern is **mandatory** for:
- All new code development
- Q3 2025 migration of existing Debug class usage
- RVNKCore integration compatibility

## Examples

See the following example files:
- `LogManagerRVNKQuests.java` - Complete LogManager implementation
- `DebugRVNKQuests.java` - Enhanced Debug class with performance monitoring
- `LogManagerUsageExample.java` - Comprehensive usage examples
- `LogManager-Migration-Guide.md` - Detailed migration instructions
