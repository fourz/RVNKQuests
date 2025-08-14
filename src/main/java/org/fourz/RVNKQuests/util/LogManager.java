package org.fourz.RVNKQuests.util;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * LogManager provides centralized logging for the RVNK ecosystem.
 * This class manages RVNKLogger instances and provides a consistent
 * logging interface across all RVNK plugins.
 * 
 * LogManager replaces the legacy Debug class with improved performance,
 * better thread safety, and ecosystem-wide consistency.
 */
public class LogManager {
    private static final Map<String, RVNKLogger> loggers = new ConcurrentHashMap<>();
    
    /**
     * Gets or creates a logger instance for the specified plugin and class.
     * Logger instances are cached and reused for performance.
     * 
     * @param plugin The plugin requesting the logger
     * @param clazz The class that will use this logger
     * @return A configured RVNKLogger instance
     */
    public static RVNKLogger getInstance(JavaPlugin plugin, Class<?> clazz) {
        String key = plugin.getName() + ":" + clazz.getSimpleName();
        return loggers.computeIfAbsent(key, k -> new RVNKLogger(plugin, clazz));
    }
    
    /**
     * Gets or creates a logger instance with a custom name.
     * 
     * @param plugin The plugin requesting the logger
     * @param name Custom logger name
     * @return A configured RVNKLogger instance
     */
    public static RVNKLogger getInstance(JavaPlugin plugin, String name) {
        String key = plugin.getName() + ":" + name;
        return loggers.computeIfAbsent(key, k -> new RVNKLogger(plugin, name));
    }
    
    /**
     * Clears all cached loggers. Used during plugin shutdown.
     * 
     * @param plugin The plugin to clear loggers for
     */
    public static void clearLoggers(JavaPlugin plugin) {
        String prefix = plugin.getName() + ":";
        loggers.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
    }
    
    /**
     * Gets the total number of active loggers (for monitoring purposes).
     */
    public static int getActiveLoggerCount() {
        return loggers.size();
    }
}
