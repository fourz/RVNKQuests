# Debugging and Logging in RVNKQuests

This document explains how the debug and logging system works in the RVNKQuests plugin. Understanding this system will help you add effective logging to new components and troubleshoot existing code.

## The Debug Class

The `Debug` class (`org.fourz.RVNKQuests.util.Debug`) provides a structured way to log messages at different severity levels. It ensures consistent log formatting and centralized log level control across the entire plugin.

### Key Features

- **Consistent formatting**: All log messages follow the format `[ClassName] [DEBUG] Message` for debug messages or `[ClassName] Message` for other levels
- **Centralized log level control**: Log level is configured in one place and applied everywhere
- **Context-aware logging**: Each Debug instance is associated with a specific class or component
- **Performance optimized**: Debug message creation can be skipped entirely when debug mode is off

## Log Levels

Log messages are categorized by severity levels:

| Level    | Method       | Usage                                              |
|----------|-------------|---------------------------------------------------|
| SEVERE   | `severe()`   | Critical errors that prevent core functionality    |
| WARNING  | `warning()`  | Important issues that need attention               |
| INFO     | `info()`     | General operational information                    |
| FINE     | `debug()`    | Detailed information for debugging                 |
| OFF      | -           | Disables all logging                               |

The log level is set in the plugin config file (`config.yml`) under `general.logLevel`.

## Creating a Debug Instance

There are two ways to create a Debug instance:

### Method 1: Using Debug Factory Method (Recommended)

```java
// With plugin's default log level
Debug debug = Debug.createDebugger(plugin, "ComponentName", null);

// With specific log level
Debug debug = Debug.createDebugger(plugin, "ComponentName", Level.FINE);
```

### Method 2: Direct Instantiation

```java
Debug debug = new Debug(plugin, "ComponentName", plugin.getLogLevel()) {};
```

## Using the Debug Instance

```java
// General information
debug.info("Plugin starting up");

// Warning for recoverable issues
debug.warning("Could not find player data, creating new profile");

// Critical errors
debug.error("Failed to connect to database", exception);

// Detailed debug info (only shown when debug mode enabled)
debug.debug("Processing player move event at " + location);
```

## Best Practices

1. **Create one debug instance per class**:
   ```java
   private final Debug debug;
   
   public MyClass(RVNKQuests plugin) {
       this.debug = Debug.createDebugger(plugin, "MyClass", null);
   }
   ```

2. **Use the appropriate log level**:
   - Use `debug()` for information only valuable during development/troubleshooting
   - Use `info()` for normal operational events
   - Use `warning()` for concerning but non-fatal issues
   - Use `error()` for critical problems

3. **Include context in messages**:
   ```java
   // Good
   debug.debug("Player " + player.getName() + " entered region: " + regionName);
   
   // Not as useful
   debug.debug("Entered region");
   ```

4. **Log at entry/exit points of significant operations**:
   ```java
   debug.debug("Beginning quest initialization");
   // ... initialization code ...
   debug.debug("Quest initialization complete. Total quests: " + quests.size());
   ```

5. **Catch and log exceptions properly**:
   ```java
   try {
       // risky operation
   } catch (Exception e) {
       debug.error("Failed to perform operation", e);
       // recovery logic
   }
   ```

## Log Level Configuration

In your `config.yml`:

```yaml
general:
  logLevel: INFO  # Options: OFF, SEVERE, WARNING, INFO, DEBUG
```

The `DEBUG` level (mapped to Java's `FINE` level) provides the most detailed logging. 
`INFO` is recommended for normal operation.

## Examples from the Code

### Initialization
```java
// In RVNKQuests.java
configManager = new ConfigManager(this);
Level logLevel = configManager.getLogLevel();
debugger = new Debug(this, "RVNKQuests", logLevel) {};
debugger.info("Initializing RVNKQuests... (Log level: " + logLevel + ")");
```

### Listener Classes
```java
// In ListenerForgottenSite.java
this.debug = Debug.createDebugger(quest.getPlugin(), "ForgottenSite", null);
// ...
debug.debug("Player found underwater ruin at: " + playerLoc);
```

### Feature Managers
```java
// In QuestManager.java
debugger.debug("Updating listeners for quest: " + quest.getId() + " (State: " + quest.getCurrentState() + ")");
```

## Changing Log Level at Runtime

Server operators can change the log level at runtime using the debug command:

## Troubleshooting

If you need more detailed logs:

1. Change the log level to `DEBUG` in the config.yml
2. Restart the server or reload the plugin
3. Reproduce the issue
4. Check the server log for detailed debug messages

Remember to change the log level back to `INFO` for production servers to avoid filling logs with excessive details.
