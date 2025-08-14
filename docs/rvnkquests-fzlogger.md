# RVNK Ecosystem FZLogger System

## Overview

The FZLogger system is a memory-optimized, performance-focused logging framework designed for the entire RVNK plugin ecosystem. It provides two main implementations: `LogManager` for production use with minimal memory footprint, and `Debugger` for development with extended debugging capabilities.

This system is designed to be used across all RVNK plugins (RVNKQuests, RVNKCore, RVNKTools, etc.) to provide consistent logging behavior and optimal performance characteristics throughout the ecosystem.

## FZLogger API

The FZLogger interface provides a clean, consistent API across all RVNK plugins:

```java
public interface FZLogger {
    // Basic logging (all implementations)
    void debug(String message);
    void info(String message);
    void warning(String message);
    void error(String message);
    
    // Exception logging (debug and error only)
    void debug(String message, Throwable throwable);
    void error(String message, Throwable throwable);
    
    // Performance monitoring
    void performance(String section, long timeInNanos);
    
    // Configuration
    boolean isDebugEnabled();
    void setDebugEnabled(boolean enabled);
    Level getLogLevel();
    void setLogLevel(Level level);
}
```

**Design Note**: Only `debug()` and `error()` methods have throwable variants. For `info()` and `warning()` messages with exceptions, use `error()` for the exception details and `info()`/`warning()` for the descriptive message.

## Architecture

```
FZLogger (interface)
├── LogManager (production)
│   ├── Single instance per plugin
│   ├── Stack trace class detection
│   └── Memory optimized
└── Debugger (development)
    ├── Performance metrics collection
    ├── Exception analysis
    ├── Memory tracking
    └── Method call tracing
```

## Key Features

### Memory Optimization
- **Single Instance**: LogManager uses one instance per plugin, not per class
- **Dynamic Class Detection**: Class names determined at runtime via stack trace analysis
- **Minimal Overhead**: No per-class logger storage or management

### Flexible Implementation
- **Swappable Loggers**: Easy to switch between LogManager and Debugger
- **Interface-Based**: Consistent API across implementations
- **Legacy Compatible**: Can coexist with existing logging systems

### Extended Debugging

- **Performance Metrics**: Automatic timing and aggregation
- **Exception Analysis**: Detailed exception tracking and analysis
- **Memory Monitoring**: Runtime memory usage tracking
- **Method Tracing**: Optional method call counting
- **Advanced Timing**: Enhanced timing utilities with automatic cleanup

## Usage

### Basic Production Logging

```java
import org.fourz.RVNKQuests.util.log.FZLogger;
import org.fourz.RVNKQuests.util.log.LogManager;

public class MyPluginClass {
    private final FZLogger logger = LogManager.getInstance(plugin, getClass());
    
    public void doSomething() {
        logger.info("Starting operation");
        
        try {
            processOperation();
            logger.debug("Operation completed successfully");
        } catch (Exception e) {
            // Use error() for exceptions (has throwable variant)
            logger.error("Operation failed", e);
            
            // Or log exception details separately
            logger.warning("Operation encountered issues");
            logger.debug("Exception details: " + e.getMessage());
        }
    }
}
```

### Development/Debugging

```java
import org.fourz.RVNKQuests.util.log.FZLogger;
import org.fourz.RVNKQuests.util.log.Debugger;

public class MyPluginClass {
    private final FZLogger logger = Debugger.getInstance(plugin, getClass());
    
    public void doSomething() {
        logger.info("Starting operation");
        
        // Automatic performance monitoring (Debugger only)
        if (logger instanceof Debugger) {
            Debugger debugger = (Debugger) logger;
            try (var timer = debugger.timeSection("operation-processing")) {
                processOperation();
            }
            
            // Enhanced debugging features
            debugger.logMemoryUsage();
            
            // Get performance metrics
            Map<String, Long> metrics = debugger.getPerformanceMetrics();
            logger.debug("Performance metrics: " + metrics);
            
            // Handle exceptions with detailed analysis
            try {
                riskyOperation();
            } catch (Exception e) {
                logger.error("Operation failed with detailed analysis", e);
                // Debugger automatically provides exception analysis
            }
        } else {
            // Fallback for LogManager (no timing available)
            processOperation();
        }
    }
}
```

### Switching Between Implementations

```java
public class QuestConfiguration {
    private static final boolean DEBUG_MODE = true; // Set via config
    
    public static FZLogger createLogger(JavaPlugin plugin, Class<?> clazz) {
        if (DEBUG_MODE) {
            Debugger debugger = Debugger.getInstance(plugin, clazz);
            debugger.setPerformanceTrackingEnabled(true);
            debugger.setMethodTracingEnabled(false); // Enable as needed
            debugger.setMemoryTrackingEnabled(true);
            return debugger;
        } else {
            return LogManager.getInstance(plugin, clazz);
        }
    }
}
```

## Implementation Details

### LogManager - Production Implementation

**Memory Optimization Strategy:**
- Single `LogManager` instance per plugin stored in static `ConcurrentHashMap`
- Class name determined at runtime using `Thread.currentThread().getStackTrace()`
- No per-class storage or management overhead

**Performance Characteristics:**
- Stack trace inspection overhead: ~5-10μs per log call
- Memory usage: ~200 bytes per plugin (vs ~100-200 bytes per class with traditional loggers)
- Thread-safe with minimal contention

**Example Memory Savings:**
```
Traditional per-class loggers: 50 classes × 200 bytes = 10KB
FZLogger LogManager: 1 plugin × 200 bytes = 200 bytes
Memory reduction: ~98% for typical plugin
```

### Debugger - Development Implementation

**Extended Features:**
- **Performance Metrics**: Automatic collection and aggregation of timing data
- **Exception Analysis**: Detailed exception tracking with stack trace analysis
- **Memory Monitoring**: Runtime memory usage reporting
- **Method Tracing**: Optional method call counting per class

**Usage Scenarios:**
- Development and testing phases
- Performance profiling and optimization
- Debugging complex quest interactions
- Analyzing memory usage patterns

## Configuration

### Basic Configuration

```java
// Enable different debugging features
Debugger debugger = Debugger.getInstance(plugin, getClass());

// Performance tracking (default: enabled)
debugger.setPerformanceTrackingEnabled(true);

// Method call tracing (default: disabled)
debugger.setMethodTracingEnabled(true);

// Memory usage tracking (default: disabled)
debugger.setMemoryTrackingEnabled(true);

// Set log level
debugger.setLogLevel(Level.FINE); // DEBUG level
```

### Plugin Configuration Integration

```yaml
# config.yml
logging:
  mode: "production"  # or "debug"
  level: "INFO"       # INFO, DEBUG, WARNING, ERROR
  features:
    performance-tracking: true
    method-tracing: false
    memory-tracking: false
```

```java
public class LoggingConfig {
    public static FZLogger createLogger(RVNKQuests plugin, Class<?> clazz) {
        String mode = plugin.getConfig().getString("logging.mode", "production");
        
        if ("debug".equalsIgnoreCase(mode)) {
            Debugger debugger = Debugger.getInstance(plugin, clazz);
            
            boolean perfTracking = plugin.getConfig().getBoolean("logging.features.performance-tracking", true);
            boolean methodTracing = plugin.getConfig().getBoolean("logging.features.method-tracing", false);
            boolean memoryTracking = plugin.getConfig().getBoolean("logging.features.memory-tracking", false);
            
            debugger.setPerformanceTrackingEnabled(perfTracking);
            debugger.setMethodTracingEnabled(methodTracing);
            debugger.setMemoryTrackingEnabled(memoryTracking);
            
            return debugger;
        } else {
            return LogManager.getInstance(plugin, clazz);
        }
    }
}
```

## Advanced Features

### Performance Monitoring

```java
// Automatic timing with try-with-resources (Debugger only)
if (logger instanceof Debugger) {
    Debugger debugger = (Debugger) logger;
    try (var timer = debugger.timeSection("database-operation")) {
        performDatabaseOperation();
    } // Automatically logs timing

    // Manual timing (Debugger only)
    long start = debugger.startTiming();
    performOperation();
    long duration = debugger.endTiming("manual-operation", start);

    // Performance metrics
    Map<String, Long> metrics = debugger.getPerformanceMetrics();
    
    // Reset metrics
    debugger.resetMetrics();
} else {
    // LogManager doesn't have timing - just perform the operation
    performDatabaseOperation();
    performOperation();
}
```

### Exception Analysis

```java
try {
    riskyOperation();
} catch (Exception e) {
    logger.error("Operation failed", e);
    
    // Debugger automatically provides:
    // - Exception type counting
    // - Stack trace analysis
    // - Exception origin tracking
    // - Cause chain analysis
}
```

### Memory Monitoring

```java
// Manual memory logging
if (logger instanceof Debugger) {
    Debugger debugger = (Debugger) logger;
    debugger.logMemoryUsage(); // Logs current memory state
}

// Automatic memory logging in timeSection
try (var timer = logger.timeSection("memory-intensive-operation")) {
    performMemoryIntensiveOperation();
} // Logs performance AND memory if enabled
```

## Migration Guide

### From Debug Class

```java
// Old pattern
private final Debug debug = Debug.createDebugger(plugin, "ClassName", Level.INFO);
debug.info("Message");
debug.error("Error", exception);

// New pattern
private final FZLogger logger = LogManager.getInstance(plugin, getClass());
logger.info("Message");
logger.error("Error", exception);
```

### From Legacy LogManager

```java
// Old pattern
private final RVNKLogger logger = LogManager.getInstance(plugin, getClass());

// New pattern
private final FZLogger logger = org.fourz.RVNKQuests.util.log.LogManager.getInstance(plugin, getClass());
```

### Coexistence Strategy

During migration, both systems can coexist:

```java
public class TransitionHelper {
    // Legacy logger for existing code
    private final RVNKLogger legacyLogger = 
        org.fourz.RVNKQuests.util.LogManager.getInstance(plugin, getClass());
    
    // New FZLogger for new code
    private final FZLogger fzLogger = 
        org.fourz.RVNKQuests.util.log.LogManager.getInstance(plugin, getClass());
}
```

## Best Practices

### 1. **Choose the Right Implementation**

- **Production**: Use `LogManager` for minimal memory overhead
- **Development**: Use `Debugger` for extended capabilities
- **Testing**: Use `Debugger` with specific features enabled

### 2. **Performance Monitoring**

```java
// Good: Use try-with-resources for automatic cleanup (Debugger only)
if (logger instanceof Debugger) {
    Debugger debugger = (Debugger) logger;
    try (var timer = debugger.timeSection("operation")) {
        performOperation();
    }
} else {
    // LogManager: No timing available, just perform operation
    performOperation();
}

// Avoid: Manual timing without proper cleanup
if (logger instanceof Debugger) {
    Debugger debugger = (Debugger) logger;
    long start = System.nanoTime();
    performOperation();
    logger.performance("operation", System.nanoTime() - start); // Manual effort
}
```

### 3. **Feature Configuration**

```java
// Configure features based on needs
Debugger debugger = Debugger.getInstance(plugin, getClass());

// Only enable what you need
debugger.setPerformanceTrackingEnabled(true);  // Usually useful
debugger.setMethodTracingEnabled(false);       // High overhead
debugger.setMemoryTrackingEnabled(false);      // Enable for memory issues
```

### 4. **Log Level Management**

```java
// Check log level for expensive operations
if (logger.isDebugEnabled()) {
    String expensiveDebugInfo = generateComplexDebugInfo();
    logger.debug(expensiveDebugInfo);
}
```

### 5. **Cleanup and Management**

```java
// Plugin shutdown
@Override
public void onDisable() {
    LogManager.clearLoggers(this);
    Debugger.clearDebuggers(this);
}
```

## Performance Considerations

### Memory Usage Comparison

| Implementation | Memory per Class | Memory per Plugin | Relative |
|---------------|------------------|-------------------|----------|
| Traditional Logger | ~200 bytes | ~10KB (50 classes) | 100% |
| FZLogger LogManager | ~4 bytes* | ~200 bytes | ~2% |
| FZLogger Debugger | ~500 bytes | ~500 bytes + metrics | ~5% |

*Stack trace inspection overhead

### Runtime Performance

| Operation | LogManager | Debugger | Traditional |
|-----------|------------|----------|-------------|
| Simple Log | ~10μs | ~15μs | ~5μs |
| Debug Log (disabled) | ~1μs | ~1μs | ~1μs |
| Performance Timing | ~10μs | ~20μs | N/A |
| Memory Logging | N/A | ~50μs | N/A |

## Troubleshooting

### Common Issues

1. **Class Name Shows as "Unknown"**
   - Stack trace inspection failed
   - Usually indicates calling from unexpected context
   - Solution: Check calling patterns or use explicit class name logging

2. **High Memory Usage with Debugger**
   - Performance metrics accumulating without reset
   - Solution: Periodically call `resetMetrics()` or disable tracking

3. **Performance Degradation**
   - Method tracing enabled in production
   - Solution: Disable method tracing or switch to LogManager

### Debugging the Logger

```java
// Check logger type and features
if (logger instanceof Debugger) {
    Debugger debugger = (Debugger) logger;
    System.out.println("Active debugger instances: " + Debugger.getActiveDebuggerCount());
    System.out.println("Performance metrics: " + debugger.getPerformanceMetrics().size());
    System.out.println("Method calls: " + debugger.getMethodCallCounts().size());
    System.out.println("Exceptions: " + debugger.getExceptionCounts().size());
} else {
    System.out.println("Active logger instances: " + LogManager.getActiveLoggerCount());
}
```

## Future Enhancements

### Planned Features
- **Remote Logging**: Send metrics to external monitoring systems
- **Log Aggregation**: Collect and analyze logs across multiple servers
- **Configuration Hot-Reload**: Change logging settings without restart
- **Custom Metrics**: User-defined performance counters
- **Log Filtering**: Advanced filtering and categorization

### Integration Opportunities
- **RVNKCore**: Shared logging service across RVNK ecosystem
- **Database Logging**: Persist metrics and logs to database
- **Web Dashboard**: Real-time monitoring interface
- **Alerting System**: Automatic notifications for critical events
