package org.fourz.RVNKQuests.debug;

import org.fourz.RVNKQuests.RVNKQuests;
import java.util.logging.Level;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized logging manager for the RVNKQuests plugin.
 * <p>
 * This class provides unified logging methods for all components with class context,
 * performance monitoring, and configurable log levels. It replaces the legacy Debug
 * class with improved performance and ecosystem-wide consistency.
 * <p>
 * Usage pattern (standardized):
 * <pre>
 * private final LogManager logger;
 *
 * public MyClass(RVNKQuests plugin) {
 *     this.logger = LogManager.getInstance(plugin, getClass());
 * }
 *
 * public void doSomething() {
 *     logger.info("Something happened");
 *     logger.warning("A warning");
 *     logger.error("An error occurred", exception);
 *     
 *     // Performance monitoring
 *     logger.startTiming("operation");
 *     // ... perform operation ...
 *     logger.endTiming("operation");
 * }
 * </pre>
 * <p>
 * All info, warning, and error messages should use this manager.
 *
 * @author Fourz
 */
public class LogManager {
    private static final Map<String, LogManager> instances = new ConcurrentHashMap<>();
    
    private final RVNKQuests plugin;
    private final String className;
    private volatile Level logLevel;
    private volatile boolean debugEnabled;
    
    // Performance monitoring
    private static final Map<String, Long> operationStartTimes = new ConcurrentHashMap<>();
    private static final long WARNING_THRESHOLD = 1000; // 1 second
    private static final long SEVERE_THRESHOLD = 5000;  // 5 seconds

    private LogManager(RVNKQuests plugin, String className) {
        this.plugin = plugin;
        this.className = className;
        this.logLevel = Level.INFO;
        this.debugEnabled = false;
    }

    /**
     * Get the LogManager instance for the specific plugin and class context.
     *
     * @param plugin The plugin instance
     * @param clazz The class for logging context
     * @return The LogManager instance for the specified class
     */
    public static synchronized LogManager getInstance(RVNKQuests plugin, Class<?> clazz) {
        String className = clazz.getSimpleName();
        LogManager instance = instances.get(className);
        if (instance == null) {
            instance = new LogManager(plugin, className);
            instances.put(className, instance);
        }
        return instance;
    }

    /**
     * Get the LogManager instance for the specific plugin and class context.
     *
     * @param plugin The plugin instance
     * @param className The class name for logging context
     * @return The LogManager instance for the specified class
     */
    public static synchronized LogManager getInstance(RVNKQuests plugin, String className) {
        LogManager instance = instances.get(className);
        if (instance == null) {
            instance = new LogManager(plugin, className);
            instances.put(className, instance);
        }
        return instance;
    }

    /**
     * Log an informational message.
     * @param message The message to log
     */
    public void info(String message) {
        log(Level.INFO, message);
    }

    /**
     * Log an informational message with parameters.
     * @param message Message template with {} placeholders
     * @param params Parameters to substitute
     */
    public void info(String message, Object... params) {
        if (shouldLog(Level.INFO)) {
            log(Level.INFO, formatMessage(message, params));
        }
    }

    /**
     * Log a warning message.
     * @param message The message to log
     */
    public void warning(String message) {
        log(Level.WARNING, message);
    }

    /**
     * Log a warning message with parameters.
     * @param message Message template with {} placeholders
     * @param params Parameters to substitute
     */
    public void warning(String message, Object... params) {
        if (shouldLog(Level.WARNING)) {
            log(Level.WARNING, formatMessage(message, params));
        }
    }

    /**
     * Log an error message with an exception.
     * @param message The error message
     * @param t The throwable to log
     */
    public void error(String message, Throwable t) {
        log(Level.SEVERE, message);
        if (t != null && shouldLog(Level.SEVERE)) {
            t.printStackTrace();
        }
    }

    /**
     * Log an error message.
     * @param message The error message
     */
    public void error(String message) {
        log(Level.SEVERE, message);
    }

    /**
     * Log an error message with parameters.
     * @param message Message template with {} placeholders
     * @param params Parameters to substitute
     */
    public void error(String message, Object... params) {
        if (shouldLog(Level.SEVERE)) {
            log(Level.SEVERE, formatMessage(message, params));
        }
    }

    /**
     * Log a debug message.
     * @param message The message to log
     */
    public void debug(String message) {
        log(Level.FINE, message);
    }

    /**
     * Log a debug message with parameters.
     * @param message Message template with {} placeholders
     * @param params Parameters to substitute
     */
    public void debug(String message, Object... params) {
        if (shouldLog(Level.FINE)) {
            log(Level.FINE, formatMessage(message, params));
        }
    }

    /**
     * Start timing an operation for performance monitoring.
     * @param operationName Name of the operation to time
     */
    public void startTiming(String operationName) {
        if (debugEnabled) {
            String key = className + "." + operationName;
            operationStartTimes.put(key, System.currentTimeMillis());
            debug("Starting operation: " + operationName);
        }
    }

    /**
     * End timing an operation and log the results.
     * @param operationName Name of the operation that was timed
     * @return The duration in milliseconds
     */
    public long endTiming(String operationName) {
        if (!debugEnabled) return 0;
        
        String key = className + "." + operationName;
        Long startTime = operationStartTimes.remove(key);
        
        if (startTime == null) {
            warning("Tried to end timing for operation that wasn't started: " + operationName);
            return 0;
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Log based on duration
        if (duration > SEVERE_THRESHOLD) {
            error("Operation '" + operationName + "' took " + duration + "ms - THIS IS CAUSING SERVER LAG");
        } else if (duration > WARNING_THRESHOLD) {
            warning("Operation '" + operationName + "' took " + duration + "ms - this may impact performance");
        } else {
            debug("Operation '" + operationName + "' completed in " + duration + "ms");
        }
        
        return duration;
    }

    /**
     * Set the log level for this LogManager instance.
     * @param level The new log level
     */
    public void setLogLevel(Level level) {
        this.logLevel = level;
        this.debugEnabled = (level == Level.FINE);
    }

    /**
     * Get the current log level for this LogManager instance.
     * @return The current log level
     */
    public Level getLogLevel() {
        return logLevel;
    }

    /**
     * Update log level for all LogManager instances.
     * This is called when the global configuration changes.
     * @param level The new log level to apply to all instances
     */
    public static synchronized void updateAllLogLevels(Level level) {
        for (LogManager instance : instances.values()) {
            instance.setLogLevel(level);
        }
    }

    /**
     * Clear all cached loggers. Used during plugin shutdown.
     * @param plugin The plugin to clear loggers for
     */
    public static void clearLoggers(RVNKQuests plugin) {
        String prefix = plugin.getName() + ":";
        instances.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
    }

    /**
     * Get the class name this LogManager instance is associated with.
     * @return The class name
     */
    public String getClassName() {
        return className;
    }

    /**
     * Check if a message at the specified level should be logged.
     */
    private boolean shouldLog(Level messageLevel) {
        if (logLevel == Level.OFF) {
            return false;
        }
        if (messageLevel == Level.FINE) {
            return logLevel == Level.FINE;
        }
        return messageLevel.intValue() >= logLevel.intValue();
    }

    /**
     * Perform the actual logging operation.
     */
    private void log(Level level, String message) {
        if (shouldLog(level)) {
            Level actualLevel = (level == Level.FINE) ? Level.INFO : level;
            String formattedMessage = String.format("[%s] %s%s", 
                className,
                (level == Level.FINE) ? "[DEBUG] " : "",
                message);
            plugin.getLogger().log(actualLevel, formattedMessage);
        }
    }

    /**
     * Format a message with parameters using SLF4J-style placeholders.
     */
    private String formatMessage(String message, Object... params) {
        if (params == null || params.length == 0) {
            return message;
        }
        
        String result = message;
        for (Object param : params) {
            result = result.replaceFirst("\\{\\}", param != null ? param.toString() : "null");
        }
        return result;
    }
}
