package org.fourz.RVNKQuests.util.log;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Development logger with extended debugging features for RVNK plugins.
 * 
 * Usage:
 * private final FZLogger logger = Debugger.getInstance(plugin, getClass());
 * 
 * Features:
 * - Performance tracking: logger.performance("operation", timeInNanos)
 * - Method tracing: setMethodTracingEnabled(true)
 * - Memory tracking: setMemoryTrackingEnabled(true)
 * - Timing utilities: try (var timer = timeSection("operation")) { ... }
 * 
 * Switch to LogManager for production use.
 * 
 * @see FZLogger for basic logging methods
 */
public class Debugger implements FZLogger {
    private static final Map<String, Debugger> instances = new ConcurrentHashMap<>();
    
    private final JavaPlugin plugin;
    private final Map<String, AtomicLong> performanceMetrics = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> methodCallCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> exceptionCounts = new ConcurrentHashMap<>();
    
    private volatile Level logLevel;
    private volatile boolean debugEnabled;
    private volatile boolean performanceTrackingEnabled = true;
    private volatile boolean methodTracingEnabled = false;
    private volatile boolean memoryTrackingEnabled = false;
    
    /**
     * Private constructor - use getInstance() methods.
     */
    private Debugger(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logLevel = Level.FINE; // Default to debug level for Debugger
        this.debugEnabled = true;
    }
    
    /**
     * Gets or creates a Debugger instance for the specified plugin.
     * 
     * @param plugin The plugin requesting the debugger
     * @param clazz The class that will use this debugger (used for compatibility only)
     * @return A configured Debugger instance
     */
    public static Debugger getInstance(JavaPlugin plugin, Class<?> clazz) {
        return getInstance(plugin);
    }
    
    /**
     * Gets or creates a Debugger instance for the specified plugin.
     * 
     * @param plugin The plugin requesting the debugger
     * @param name Custom debugger name (ignored for compatibility)
     * @return A configured Debugger instance
     */
    public static Debugger getInstance(JavaPlugin plugin, String name) {
        return getInstance(plugin);
    }
    
    /**
     * Gets or creates a Debugger instance for the specified plugin.
     * 
     * @param plugin The plugin requesting the debugger
     * @return A configured Debugger instance
     */
    public static Debugger getInstance(JavaPlugin plugin) {
        return instances.computeIfAbsent(plugin.getName(), k -> new Debugger(plugin));
    }
    
    /**
     * Clears all cached debuggers and resets metrics. Used during plugin shutdown.
     * 
     * @param plugin The plugin to clear debuggers for
     */
    public static void clearDebuggers(JavaPlugin plugin) {
        Debugger debugger = instances.remove(plugin.getName());
        if (debugger != null) {
            debugger.resetMetrics();
        }
    }
    
    /**
     * Gets the total number of active debugger instances.
     */
    public static int getActiveDebuggerCount() {
        return instances.size();
    }
    
    // FZLogger interface implementation
    
    @Override
    public void debug(String message) {
        if (shouldLog(Level.FINE)) {
            String className = getCallingClassName();
            
            if (methodTracingEnabled) {
                incrementMethodCallCount(className);
            }
            
            String formatted = formatMessage(className, Level.FINE, message);
            logToPlugin(Level.FINE, formatted);
        }
    }
    
    @Override
    public void debug(String message, Throwable throwable) {
        if (shouldLog(Level.FINE)) {
            String className = getCallingClassName();
            
            if (throwable != null) {
                incrementExceptionCount(throwable.getClass().getSimpleName());
                analyzeException(throwable);
            }
            
            String formatted = formatMessage(className, Level.FINE, message);
            logToPlugin(Level.FINE, formatted);
            
            if (throwable != null) {
                logDetailedException(throwable);
            }
        }
    }
    
    @Override
    public void info(String message) {
        if (shouldLog(Level.INFO)) {
            String className = getCallingClassName();
            String formatted = formatMessage(className, Level.INFO, message);
            logToPlugin(Level.INFO, formatted);
        }
    }
    
    @Override
    public void warning(String message) {
        if (shouldLog(Level.WARNING)) {
            String className = getCallingClassName();
            String formatted = formatMessage(className, Level.WARNING, message);
            logToPlugin(Level.WARNING, formatted);
        }
    }
    
    @Override
    public void error(String message) {
        if (shouldLog(Level.SEVERE)) {
            String className = getCallingClassName();
            String formatted = formatMessage(className, Level.SEVERE, message);
            logToPlugin(Level.SEVERE, formatted);
        }
    }
    
    @Override
    public void error(String message, Throwable throwable) {
        if (shouldLog(Level.SEVERE)) {
            String className = getCallingClassName();
            String formatted = formatMessage(className, Level.SEVERE, message);
            logToPlugin(Level.SEVERE, formatted);
            
            if (throwable != null) {
                incrementExceptionCount(throwable.getClass().getSimpleName());
                logDetailedException(throwable);
            }
        }
    }
    
    @Override
    public void performance(String section, long timeInNanos) {
        if (performanceTrackingEnabled) {
            performanceMetrics.computeIfAbsent(section, k -> new AtomicLong())
                             .addAndGet(timeInNanos);
            
            if (shouldLog(Level.FINE)) {
                double timeInMs = timeInNanos / 1_000_000.0;
                String message = String.format("Performance [%s]: %.2fms (Total: %.2fms, Count: %d)",
                    section, timeInMs, 
                    performanceMetrics.get(section).get() / 1_000_000.0,
                    getPerformanceCallCount(section));
                debug(message);
            }
        }
    }
    
    @Override
    public boolean isDebugEnabled() {
        return debugEnabled;
    }
    
    @Override
    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
    }
    
    @Override
    public Level getLogLevel() {
        return logLevel;
    }
    
    @Override
    public void setLogLevel(Level level) {
        this.logLevel = level;
        this.debugEnabled = (level == Level.FINE);
    }
    
    // Extended debugging features
    
    /**
     * Enables or disables performance metric collection.
     * 
     * @param enabled true to enable performance tracking
     */
    public void setPerformanceTrackingEnabled(boolean enabled) {
        this.performanceTrackingEnabled = enabled;
    }
    
    /**
     * Enables or disables method call tracing.
     * 
     * @param enabled true to enable method tracing
     */
    public void setMethodTracingEnabled(boolean enabled) {
        this.methodTracingEnabled = enabled;
    }
    
    /**
     * Enables or disables memory usage tracking.
     * 
     * @param enabled true to enable memory tracking
     */
    public void setMemoryTrackingEnabled(boolean enabled) {
        this.memoryTrackingEnabled = enabled;
    }
    
    /**
     * Gets accumulated performance metrics for all monitored sections.
     * 
     * @return Map of section names to their total execution time in nanoseconds
     */
    public Map<String, Long> getPerformanceMetrics() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        performanceMetrics.forEach((key, value) -> result.put(key, value.get()));
        return result;
    }
    
    /**
     * Gets method call counts for all tracked classes.
     * 
     * @return Map of class names to their method call counts
     */
    public Map<String, Long> getMethodCallCounts() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        methodCallCounts.forEach((key, value) -> result.put(key, value.get()));
        return result;
    }
    
    /**
     * Gets exception counts by exception type.
     * 
     * @return Map of exception types to their occurrence counts
     */
    public Map<String, Long> getExceptionCounts() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        exceptionCounts.forEach((key, value) -> result.put(key, value.get()));
        return result;
    }
    
    /**
     * Resets all collected metrics.
     */
    public void resetMetrics() {
        performanceMetrics.clear();
        methodCallCounts.clear();
        exceptionCounts.clear();
    }
    
    /**
     * Logs current memory usage if memory tracking is enabled.
     */
    public void logMemoryUsage() {
        if (memoryTrackingEnabled && shouldLog(Level.FINE)) {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();
            
            String message = String.format(
                "Memory: Used=%.2fMB, Free=%.2fMB, Total=%.2fMB, Max=%.2fMB",
                usedMemory / 1024.0 / 1024.0,
                freeMemory / 1024.0 / 1024.0,
                totalMemory / 1024.0 / 1024.0,
                maxMemory / 1024.0 / 1024.0
            );
            debug(message);
        }
    }
    
    /**
     * Enhanced timing method that provides automatic cleanup and statistics.
     * 
     * @param section The name of the section being timed
     * @return AutoCloseable timer with enhanced features
     */
    public AutoCloseable timeSection(String section) {
        if (!debugEnabled && !performanceTrackingEnabled) {
            return () -> {}; // No-op if both debug and performance tracking are disabled
        }
        
        long startTime = System.nanoTime();
        return () -> {
            long duration = System.nanoTime() - startTime;
            performance(section, duration);
            
            if (memoryTrackingEnabled) {
                logMemoryUsage();
            }
        };
    }
    
    /**
     * Starts a timing operation and returns the start time.
     * Use with endTiming() for manual timing control.
     * 
     * @return The current time in nanoseconds
     */
    public long startTiming() {
        return System.nanoTime();
    }
    
    /**
     * Ends a timing operation and logs the duration.
     * 
     * @param operation The name of the operation being timed
     * @param startTime The start time from startTiming()
     * @return The duration in nanoseconds
     */
    public long endTiming(String operation, long startTime) {
        long duration = System.nanoTime() - startTime;
        performance(operation, duration);
        return duration;
    }
    
    /**
     * Convenience method for ending timing with a default start time.
     * Note: This is less accurate than using startTiming()/endTiming() pair.
     * 
     * @param operation The name of the operation being timed
     * @return The duration in nanoseconds (approximate)
     */
    public long endTiming(String operation) {
        // This is a convenience method, but less accurate since we don't have the actual start time
        // We'll just return 0 and log a warning
        if (shouldLog(Level.WARNING)) {
            warning("endTiming() called without startTime - use startTiming()/endTiming(startTime) pair for accurate timing");
        }
        return 0;
    }
    
    // Helper methods
    
    private boolean shouldLog(Level messageLevel) {
        if (logLevel == Level.OFF) {
            return false;
        }
        if (messageLevel == Level.FINE) {
            return logLevel == Level.FINE;
        }
        return messageLevel.intValue() >= logLevel.intValue();
    }
    
    private String formatMessage(String className, Level level, String message) {
        return String.format("[%s] %s%s", 
            className,
            (level == Level.FINE) ? "[DEBUG] " : "",
            message);
    }
    
    private void logToPlugin(Level level, String formattedMessage) {
        Level actualLevel = (level == Level.FINE) ? Level.INFO : level;
        plugin.getLogger().log(actualLevel, formattedMessage);
    }
    
    private String getCallingClassName() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        
        for (int i = 3; i < stack.length; i++) {
            String className = stack[i].getClassName();
            
            if (!className.contains("Debugger")) {
                String[] parts = className.split("\\.");
                return parts[parts.length - 1];
            }
        }
        
        return "Unknown";
    }
    
    private void incrementMethodCallCount(String className) {
        methodCallCounts.computeIfAbsent(className, k -> new AtomicLong()).incrementAndGet();
    }
    
    private void incrementExceptionCount(String exceptionType) {
        exceptionCounts.computeIfAbsent(exceptionType, k -> new AtomicLong()).incrementAndGet();
    }
    
    private int getPerformanceCallCount(String section) {
        return (int) (performanceMetrics.get(section).get() / 1000000); // Rough approximation
    }
    
    private void analyzeException(Throwable throwable) {
        if (shouldLog(Level.FINE)) {
            debug(String.format("Exception Analysis: %s - %s", 
                throwable.getClass().getSimpleName(), 
                throwable.getMessage()));
            
            // Log stack trace depth
            StackTraceElement[] stack = throwable.getStackTrace();
            debug(String.format("Stack depth: %d frames", stack.length));
            
            // Log if this is a common exception location
            if (stack.length > 0) {
                String location = stack[0].getClassName() + "." + stack[0].getMethodName() + ":" + stack[0].getLineNumber();
                debug(String.format("Exception origin: %s", location));
            }
        }
    }
    
    private void logDetailedException(Throwable throwable) {
        if (throwable != null && shouldLog(Level.SEVERE)) {
            debug("=== Exception Details ===");
            debug(String.format("Type: %s", throwable.getClass().getName()));
            debug(String.format("Message: %s", throwable.getMessage()));
            
            if (throwable.getCause() != null) {
                debug(String.format("Caused by: %s - %s", 
                    throwable.getCause().getClass().getSimpleName(),
                    throwable.getCause().getMessage()));
            }
            
            throwable.printStackTrace();
            debug("=== End Exception Details ===");
        }
    }
}
