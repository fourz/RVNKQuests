package org.fourz.RVNKQuests.util;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;

/**
 * RVNKLogger provides structured logging for RVNK ecosystem plugins.
 * This class replaces the legacy Debug class with improved performance,
 * standardized formatting, and better integration with the RVNK ecosystem.
 * 
 * Features:
 * - Structured message formatting with class context
 * - Performance-optimized logging with early filtering
 * - Support for parameterized messages (SLF4J style)
 * - Thread-safe operation
 * - Consistent formatting across all RVNK plugins
 */
public class RVNKLogger {
    private final JavaPlugin plugin;
    private final String className;
    private volatile Level logLevel;
    
    /**
     * Creates a new RVNKLogger for the specified plugin and class.
     * 
     * @param plugin The plugin this logger belongs to
     * @param clazz The class that will use this logger
     */
    public RVNKLogger(JavaPlugin plugin, Class<?> clazz) {
        this.plugin = plugin;
        this.className = clazz.getSimpleName();
        this.logLevel = Level.INFO; // Default level
    }
    
    /**
     * Creates a new RVNKLogger with a custom name.
     * 
     * @param plugin The plugin this logger belongs to
     * @param name Custom logger name
     */
    public RVNKLogger(JavaPlugin plugin, String name) {
        this.plugin = plugin;
        this.className = name;
        this.logLevel = Level.INFO; // Default level
    }
    
    /**
     * Logs an informational message.
     * 
     * @param message The message to log
     */
    public void info(String message) {
        log(Level.INFO, message);
    }
    
    /**
     * Logs an informational message with parameters.
     * 
     * @param message Message template with {} placeholders
     * @param params Parameters to substitute
     */
    public void info(String message, Object... params) {
        if (shouldLog(Level.INFO)) {
            log(Level.INFO, formatMessage(message, params));
        }
    }
    
    /**
     * Logs a debug message.
     * 
     * @param message The message to log
     */
    public void debug(String message) {
        log(Level.FINE, message);
    }
    
    /**
     * Logs a debug message with parameters.
     * 
     * @param message Message template with {} placeholders
     * @param params Parameters to substitute
     */
    public void debug(String message, Object... params) {
        if (shouldLog(Level.FINE)) {
            log(Level.FINE, formatMessage(message, params));
        }
    }
    
    /**
     * Logs a warning message.
     * 
     * @param message The message to log
     */
    public void warning(String message) {
        log(Level.WARNING, message);
    }
    
    /**
     * Logs a warning message with parameters.
     * 
     * @param message Message template with {} placeholders
     * @param params Parameters to substitute
     */
    public void warning(String message, Object... params) {
        if (shouldLog(Level.WARNING)) {
            log(Level.WARNING, formatMessage(message, params));
        }
    }
    
    /**
     * Logs an error message.
     * 
     * @param message The message to log
     */
    public void error(String message) {
        log(Level.SEVERE, message);
    }
    
    /**
     * Logs an error message with an exception.
     * 
     * @param message The message to log
     * @param throwable The exception to log
     */
    public void error(String message, Throwable throwable) {
        log(Level.SEVERE, message);
        if (throwable != null && shouldLog(Level.SEVERE)) {
            throwable.printStackTrace();
        }
    }
    
    /**
     * Logs an error message with parameters.
     * 
     * @param message Message template with {} placeholders
     * @param params Parameters to substitute
     */
    public void error(String message, Object... params) {
        if (shouldLog(Level.SEVERE)) {
            log(Level.SEVERE, formatMessage(message, params));
        }
    }
    
    /**
     * Sets the logging level for this logger.
     * 
     * @param level The new logging level
     */
    public void setLogLevel(Level level) {
        this.logLevel = level;
    }
    
    /**
     * Gets the current logging level.
     * 
     * @return Current logging level
     */
    public Level getLogLevel() {
        return logLevel;
    }
    
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
     * Performs the actual logging operation.
     * 
     * @param level The log level
     * @param message The message to log
     */
    private void log(Level level, String message) {
        if (shouldLog(level)) {
            // Map FINE to INFO when actually logging
            Level actualLevel = (level == Level.FINE) ? Level.INFO : level;
            String formattedMessage = String.format("[%s] %s%s", 
                className,
                (level == Level.FINE) ? "[DEBUG] " : "",
                message);
            plugin.getLogger().log(actualLevel, formattedMessage);
        }
    }
    
    /**
     * Formats a message with parameters using SLF4J-style placeholders.
     * 
     * @param message Message template with {} placeholders
     * @param params Parameters to substitute
     * @return Formatted message
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
