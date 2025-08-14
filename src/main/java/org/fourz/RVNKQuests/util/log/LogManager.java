package org.fourz.RVNKQuests.util.log;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-ready logger for RVNK plugins.
 * 
 * Usage:
 * private final FZLogger logger = LogManager.getInstance(plugin, getClass());
 * 
 * Features:
 * - Optimized for memory usage (single instance per plugin)
 * - Thread-safe logging
 * - Automatic class name detection
 * - Debug level support
 * 
 * @see FZLogger for logging methods
 */
public class LogManager implements FZLogger {
    private static final Map<String, LogManager> instances = new ConcurrentHashMap<>();
    
    private final JavaPlugin plugin;
    private volatile Level logLevel;
    private volatile boolean debugEnabled;
    
    /**
     * Private constructor - use getInstance() methods.
     */
    private LogManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logLevel = Level.INFO; // Default level
        this.debugEnabled = false;
    }
    
    /**
     * Gets or creates a LogManager instance for the specified plugin.
     * Returns the same instance regardless of the class parameter for memory efficiency.
     * 
     * @param plugin The plugin requesting the logger
     * @param clazz The class that will use this logger (used for compatibility only)
     * @return A configured LogManager instance
     */
    public static LogManager getInstance(JavaPlugin plugin, Class<?> clazz) {
        return getInstance(plugin);
    }
    
    /**
     * Gets or creates a LogManager instance for the specified plugin.
     * 
     * @param plugin The plugin requesting the logger
     * @param name Custom logger name (ignored for compatibility)
     * @return A configured LogManager instance
     */
    public static LogManager getInstance(JavaPlugin plugin, String name) {
        return getInstance(plugin);
    }
    
    /**
     * Gets or creates a LogManager instance for the specified plugin.
     * Uses a single instance per plugin to minimize memory usage.
     * 
     * @param plugin The plugin requesting the logger
     * @return A configured LogManager instance
     */
    public static LogManager getInstance(JavaPlugin plugin) {
        return instances.computeIfAbsent(plugin.getName(), k -> new LogManager(plugin));
    }
    
    /**
     * Clears all cached loggers. Used during plugin shutdown.
     * 
     * @param plugin The plugin to clear loggers for
     */
    public static void clearLoggers(JavaPlugin plugin) {
        instances.remove(plugin.getName());
    }
    
    /**
     * Gets the total number of active logger instances (for monitoring purposes).
     */
    public static int getActiveLoggerCount() {
        return instances.size();
    }
    
    // FZLogger interface implementation
    
    @Override
    public void debug(String message) {
        if (shouldLog(Level.FINE)) {
            String className = getCallingClassName();
            String formatted = formatMessage(className, Level.FINE, message);
            logToPlugin(Level.FINE, formatted);
        }
    }
    
    @Override
    public void debug(String message, Throwable throwable) {
        if (shouldLog(Level.FINE)) {
            String className = getCallingClassName();
            String formatted = formatMessage(className, Level.FINE, message);
            logToPlugin(Level.FINE, formatted);
            if (throwable != null) {
                throwable.printStackTrace();
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
                throwable.printStackTrace();
            }
        }
    }
    
    @Override
    public void performance(String section, long timeInNanos) {
        if (shouldLog(Level.FINE)) {
            double timeInMs = timeInNanos / 1_000_000.0;
            String message = String.format("Performance [%s]: %.2fms", section, timeInMs);
            debug(message);
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
    
    // Helper methods
    
    /**
     * Checks if a message at the specified level should be logged.
     * 
     * @param messageLevel The level to check
     * @return true if the message should be logged
     */
    private boolean shouldLog(Level messageLevel) {
        if (logLevel == Level.OFF) {
            return false;
        }
        // Special handling for FINE (debug) level
        if (messageLevel == Level.FINE) {
            return logLevel == Level.FINE;
        }
        return messageLevel.intValue() >= logLevel.intValue();
    }
    
    /**
     * Formats a message with the class name prefix.
     * 
     * @param className The class name to include in the prefix
     * @param level The log level (for debug prefix)
     * @param message The message to format
     * @return Formatted message string
     */
    private String formatMessage(String className, Level level, String message) {
        return String.format("[%s] %s%s", 
            className,
            (level == Level.FINE) ? "[DEBUG] " : "",
            message);
    }
    
    /**
     * Performs the actual logging operation to the plugin logger.
     * 
     * @param level The log level
     * @param formattedMessage The formatted message to log
     */
    private void logToPlugin(Level level, String formattedMessage) {
        // Map FINE to INFO when actually logging to maintain compatibility
        Level actualLevel = (level == Level.FINE) ? Level.INFO : level;
        plugin.getLogger().log(actualLevel, formattedMessage);
    }
    
    /**
     * Extracts the calling class name from the stack trace.
     * This method looks for the first class that isn't LogManager.
     * 
     * @return The simple name of the calling class
     */
    private String getCallingClassName() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        
        // Skip getStackTrace, this method, and the immediate caller (debug/info/etc)
        for (int i = 3; i < stack.length; i++) {
            String className = stack[i].getClassName();
            
            // Skip LogManager classes
            if (!className.contains("LogManager")) {
                // Extract simple class name
                String[] parts = className.split("\\.");
                return parts[parts.length - 1];
            }
        }
        
        return "Unknown";
    }
}
