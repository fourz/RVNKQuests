package org.fourz.RVNKQuests.util.log;

import java.util.logging.Level;

/**
 * FZLogger serves as the interface for all logging implementations in RVNKQuests.
 * This interface contains the log level logic and provides a consistent API
 * for different logging strategies.
 * 
 * Implementations can focus on their specific features (performance monitoring,
 * memory optimization, etc.) while maintaining consistent behavior across
 * the plugin.
 */
public interface FZLogger {
    
    // Core logging methods
    void debug(String message);
    void debug(String message, Throwable throwable);
    void info(String message);
    void info(String message, Throwable throwable);
    void warning(String message);
    void warning(String message, Throwable throwable);
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
