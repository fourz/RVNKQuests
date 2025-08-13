# LogManager Migration Guide for RVNKQuests

**Priority**: High - Q3 2025 Critical Task  
**Status**: Migration Required  
**Target Completion**: End of Q3 2025

## Overview

RVNKQuests is transitioning from the legacy `Debug` class to the modern `LogManager` pattern for consistency with the RVNK ecosystem. This migration is **mandatory** for all new code and should be applied to existing code as part of the Q3 2025 roadmap.

## Migration Priority

### Critical Migration Tasks (Q3 2025)

1. **Core Classes Migration** *(Critical)*
   - [ ] `RVNKQuests.java` main class
   - [ ] `QuestManager.java`
   - [ ] `CommandManager.java` and all subcommands
   - [ ] `ConfigManager.java`

2. **Quest Implementation Updates** *(High Priority)*
   - [ ] `QuestPiglinFarFromHome.java`
   - [ ] `QuestFirstCityProphecy.java`
   - [ ] `QuestAncientGuardian.java`

3. **Listener Classes Migration** *(Medium Priority)*
   - [ ] All trigger listeners
   - [ ] All objective listeners
   - [ ] Utility classes using Debug

## New LogManager Pattern

### Correct Implementation

```java
package org.fourz.RVNKQuests.quest;

import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.debug.LogManager;

public class MyQuest implements Quest {
    // REQUIRED: Use LogManager with getClass() for proper context
    private final LogManager logger;
    private final RVNKQuests plugin;
    
    public MyQuest(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        
        // Standard logging
        logger.info("Quest initialized: " + getId());
    }
    
    public void someMethod() {
        logger.debug("Processing quest operation");
        
        try {
            // Performance monitoring
            logger.startTiming("quest_operation");
            
            // ... perform operation ...
            
            long duration = logger.endTiming("quest_operation");
            
            if (duration > 100) {
                logger.warning("Operation took " + duration + "ms - consider optimization");
            }
            
        } catch (Exception e) {
            logger.error("Operation failed", e);
        }
    }
}
```

### Legacy Pattern (DEPRECATED)

```java
// OLD PATTERN - DO NOT USE FOR NEW CODE
private final Debug debug = Debug.createDebugger(plugin, "ClassName", Level.INFO);

public void someMethod() {
    debug.info("Message");
    debug.warning("Warning");
    debug.error("Error", exception);
}
```

## Migration Steps

### Step 1: Update Import Statements

**Before:**
```java
import org.fourz.RVNKQuests.debug.Debug;
import java.util.logging.Level;
```

**After:**
```java
import org.fourz.RVNKQuests.debug.LogManager;
// Remove Debug import
```

### Step 2: Replace Field Declaration

**Before:**
```java
private final Debug debug = Debug.createDebugger(plugin, "ClassName", Level.INFO);
```

**After:**
```java
private final LogManager logger = LogManager.getInstance(plugin, getClass());
```

### Step 3: Update Method Calls

**Before:**
```java
debug.info("Quest started");
debug.warning("Configuration issue");
debug.error("Failed to process", exception);
```

**After:**
```java
logger.info("Quest started");
logger.warning("Configuration issue");
logger.error("Failed to process", exception);
```

### Step 4: Add Performance Monitoring (Optional)

```java
// NEW: Performance monitoring capabilities
logger.startTiming("operation_name");
// ... perform operation ...
long duration = logger.endTiming("operation_name");
```

## Key Differences

| Feature | Debug Class | LogManager |
|---------|-------------|------------|
| **Initialization** | `Debug.createDebugger(plugin, "ClassName", Level.INFO)` | `LogManager.getInstance(plugin, getClass())` |
| **Context** | Manual string class name | Automatic class context via `getClass()` |
| **Performance** | No built-in timing | Built-in `startTiming()`/`endTiming()` |
| **Error Analysis** | Basic exception logging | Enhanced exception analysis with diagnostics |
| **Thread Safety** | Basic | Enhanced with blocking detection |
| **Configuration** | Manual level management | Centralized level management |

## Benefits of Migration

### 1. Improved Context
- Automatic class name detection via `getClass()`
- Consistent logging format across all components
- Better debugging and troubleshooting

### 2. Performance Monitoring
```java
logger.startTiming("quest_state_transition");
advanceQuestState(newState);
long duration = logger.endTiming("quest_state_transition");
// Automatic performance warnings for slow operations
```

### 3. Enhanced Error Analysis
```java
try {
    processQuestObjective();
} catch (Exception e) {
    // LogManager provides detailed exception analysis
    logger.error("Failed to process quest objective", e);
    // Automatic root cause analysis and solution suggestions
}
```

### 4. Centralized Configuration
```java
// Update all LogManager instances at once
LogManager.updateAllLogLevels(Level.FINE);
```

## Migration Checklist

### For Each Class:

- [ ] Replace `Debug` import with `LogManager`
- [ ] Update field declaration to use `LogManager.getInstance(plugin, getClass())`
- [ ] Replace all `debug.method()` calls with `logger.method()`
- [ ] Add performance monitoring where appropriate
- [ ] Test logging output
- [ ] Remove unused `Debug` imports

### Class-Specific Notes:

#### Quest Implementations
- Add performance monitoring for state transitions
- Log quest trigger conditions
- Monitor listener registration/unregistration

#### Command Classes
- Log command execution and validation
- Monitor permission checks
- Track command performance

#### Manager Classes
- Log service initialization and shutdown
- Monitor resource allocation and cleanup
- Track cross-component communication

## Testing Migration

### 1. Compile Check
```bash
mvn clean compile
# Ensure no compilation errors after migration
```

### 2. Runtime Verification
- Start plugin with DEBUG level logging
- Verify log messages appear with correct class context
- Check performance timing messages
- Test error logging with exceptions

### 3. Configuration Test
```yaml
# config.yml
general:
  logLevel: DEBUG  # Should enable debug messages
```

### 4. Performance Test
```java
// Add this to verify timing works
logger.startTiming("test_operation");
Thread.sleep(100);  // Simulate work
long duration = logger.endTiming("test_operation");
// Should log: "Operation 'test_operation' completed in ~100ms"
```

## Common Migration Issues

### Issue 1: Missing Class Context
**Problem**: Using string class names instead of `getClass()`
**Solution**: Always use `LogManager.getInstance(plugin, getClass())`

### Issue 2: Circular Dependencies
**Problem**: LogManager initialization during plugin startup
**Solution**: LogManager handles lazy initialization automatically

### Issue 3: Performance Impact
**Problem**: Concern about logging performance
**Solution**: LogManager includes efficient level checking and conditional execution

### Issue 4: Debug Level Configuration
**Problem**: Debug messages not appearing
**Solution**: Set `logLevel: DEBUG` in config.yml to enable fine-grained logging

## Timeline and Dependencies

### Q3 2025 Milestones:

1. **Week 1-2**: Core classes migration (RVNKQuests, QuestManager, CommandManager)
2. **Week 3-4**: Quest implementation migration
3. **Week 5-6**: Listener and utility class migration
4. **Week 7-8**: Testing, validation, and documentation updates

### Dependencies:
- No external dependencies required
- LogManager and Debug classes already exist
- Configuration system supports new pattern

## Validation Criteria

### Migration Complete When:
- [ ] All `Debug` class usage removed from production code
- [ ] All classes use `LogManager.getInstance(plugin, getClass())`
- [ ] Performance monitoring added to critical operations
- [ ] All tests pass with new logging system
- [ ] Documentation updated to reflect new pattern
- [ ] No compilation warnings or errors

## Future Considerations

### Q4 2025: Debug Class Removal
After successful migration:
- Remove `Debug.java` class entirely
- Update build dependencies
- Final cleanup of any remaining references

### RVNKCore Integration
The LogManager pattern aligns with RVNKCore integration plans:
- Compatible with RVNKCore logging standards
- Supports shared logging configuration
- Enables cross-plugin log aggregation

---

**Next Steps**: Begin migration with critical core classes, starting with `RVNKQuests.java` main class and `QuestManager.java`.
