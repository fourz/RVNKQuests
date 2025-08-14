package org.fourz.RVNKQuests.util.log;

import java.util.logging.Level;

/**
 * Standard logging interface for RVNK plugins.
 * 
 * Usage:
 * 1. For production: Use LogManager.getInstance(plugin, getClass())
 * 2. For debugging: Use Debugger.getInstance(plugin, getClass())
 * 
 * Example:
 * private final FZLogger logger = LogManager.getInstance(plugin, getClass());
 * logger.info("Server started");
 * logger.debug("Processing quest state");
 */
public interface FZLogger {
    
    // Core logging methods
    void debug(String message);
    void debug(String message, Throwable throwable);
    void info(String message);
    void warning(String message);
    void error(String message);
    void error(String message, Throwable throwable);
    
    // Performance monitoring
    void performance(String section, long timeInNanos);
    
    // Log level management
    boolean isDebugEnabled();
    void setDebugEnabled(boolean enabled);
    Level getLogLevel();
    void setLogLevel(Level level);
}
