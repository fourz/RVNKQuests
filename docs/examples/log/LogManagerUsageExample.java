package org.fourz.RVNKQuests.examples;

import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.debug.LogManager;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.ArrayList;

/**
 * Example class demonstrating proper LogManager usage in RVNKQuests.
 * 
 * This example shows:
 * - Proper LogManager initialization
 * - Standard logging patterns for different scenarios
 * - Performance monitoring with timing
 * - Error handling with proper logging
 * - Migration from Debug class to LogManager
 * 
 * @author Fourz
 */
public class LogManagerUsageExample implements Quest, Listener {
    
    // CORRECT: Use LogManager for all logging
    private final LogManager logger;
    private final RVNKQuests plugin;
    
    // Quest state management
    private QuestState currentState = QuestState.NOT_STARTED;
    
    public LogManagerUsageExample(RVNKQuests plugin) {
        this.plugin = plugin;
        // REQUIRED: Initialize LogManager using getClass() for proper context
        this.logger = LogManager.getInstance(plugin, getClass());
        
        // Log successful initialization
        logger.info("Quest initialized: " + getId());
    }
    
    /**
     * Example of proper error handling and logging
     */
    @Override
    public void advanceState(QuestState newState) {
        logger.debug("Attempting to advance quest state from " + currentState + " to " + newState);
        
        try {
            // Start performance monitoring
            logger.startTiming("state_transition");
            
            // Validate state transition
            if (!isValidStateTransition(currentState, newState)) {
                logger.warning("Invalid state transition attempted: " + currentState + " -> " + newState);
                return;
            }
            
            QuestState previousState = currentState;
            this.currentState = newState;
            
            // Update quest listeners through manager
            plugin.getQuestManager().updateQuestListeners(this);
            
            // Log successful transition
            logger.info("Quest state advanced from " + previousState + " to " + newState);
            
            // End performance monitoring
            long duration = logger.endTiming("state_transition");
            
            // Log performance warning if needed
            if (duration > 100) { // 100ms threshold
                logger.warning("State transition took " + duration + "ms - consider optimization");
            }
            
        } catch (Exception e) {
            // CORRECT: Use logger.error with exception
            logger.error("Failed to advance quest state from " + currentState + " to " + newState, e);
            
            // Additional context for debugging
            logger.error("Quest ID: " + getId() + ", Plugin State: " + plugin.isEnabled());
        }
    }
    
    /**
     * Example of listener creation with proper logging
     */
    @Override
    public List<Listener> createListenersForState(QuestState state) {
        logger.debug("Creating listeners for state: " + state);
        
        List<Listener> listeners = new ArrayList<>();
        
        try {
            switch (state) {
                case NOT_STARTED:
                    // Add trigger listeners
                    listeners.add(this); // This class implements Listener
                    logger.debug("Added trigger listener for NOT_STARTED state");
                    break;
                    
                case QUEST_ACTIVE:
                    // Add objective listeners
                    // listeners.add(new ObjectiveListener(this));
                    logger.debug("Added objective listeners for QUEST_ACTIVE state");
                    break;
                    
                case COMPLETED:
                    // No listeners needed for completed state
                    logger.debug("No listeners needed for COMPLETED state");
                    break;
                    
                default:
                    logger.warning("Unknown quest state: " + state + " - no listeners created");
                    break;
            }
            
            logger.info("Created " + listeners.size() + " listeners for state: " + state);
            
        } catch (Exception e) {
            logger.error("Failed to create listeners for state: " + state, e);
        }
        
        return listeners;
    }
    
    /**
     * Example event handler with proper logging
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Early filtering to avoid unnecessary processing
        if (currentState != QuestState.NOT_STARTED) {
            return;
        }
        
        logger.debug("Processing player join for quest trigger: " + event.getPlayer().getName());
        
        try {
            // Quest-specific logic
            if (shouldTriggerQuest(event)) {
                logger.info("Quest triggered by player: " + event.getPlayer().getName());
                advanceState(QuestState.QUEST_ACTIVE);
            }
            
        } catch (Exception e) {
            logger.error("Error processing player join event for quest trigger", e);
        }
    }
    
    /**
     * Example of configuration loading with logging
     */
    public void loadConfiguration() {
        logger.info("Loading quest configuration");
        
        try {
            logger.startTiming("config_load");
            
            // Simulate configuration loading
            String worldName = plugin.getConfig().getString("quests." + getId() + ".world", "world");
            boolean enabled = plugin.getConfig().getBoolean("quests." + getId() + ".enable", true);
            
            logger.info("Quest configuration loaded - World: " + worldName + ", Enabled: " + enabled);
            
            if (!enabled) {
                logger.warning("Quest is disabled in configuration: " + getId());
            }
            
            logger.endTiming("config_load");
            
        } catch (Exception e) {
            logger.error("Failed to load quest configuration", e);
        }
    }
    
    /**
     * Example of cleanup with proper logging
     */
    @Override
    public void cleanup() {
        logger.info("Cleaning up quest: " + getId());
        
        try {
            logger.startTiming("quest_cleanup");
            
            // Cancel any scheduled tasks
            // plugin.getServer().getScheduler().cancelTasks(plugin);
            
            // Clean up any spawned entities
            // cleanupQuestEntities();
            
            // Remove temporary blocks or structures
            // cleanupQuestStructures();
            
            logger.info("Quest cleanup completed successfully");
            
            long duration = logger.endTiming("quest_cleanup");
            logger.debug("Cleanup took " + duration + "ms");
            
        } catch (Exception e) {
            logger.error("Error during quest cleanup", e);
        }
    }
    
    // Quest interface implementation helpers
    
    @Override
    public String getId() {
        return "example_quest";
    }
    
    @Override
    public QuestState getCurrentState() {
        return currentState;
    }
    
    @Override
    public RVNKQuests getPlugin() {
        return plugin;
    }
    
    // Helper methods
    
    private boolean isValidStateTransition(QuestState from, QuestState to) {
        // Simplified validation logic
        return to.ordinal() > from.ordinal() || to == QuestState.NOT_STARTED;
    }
    
    private boolean shouldTriggerQuest(PlayerJoinEvent event) {
        // Example trigger logic
        return event.getPlayer().hasPermission("rvnkquests.quest.example");
    }
    
    /**
     * Example of legacy Debug class usage that should be migrated
     * 
     * OLD PATTERN (DEPRECATED):
     * private final Debug debug = Debug.createDebugger(plugin, "ExampleClass", Level.INFO);
     * debug.info("Message");
     * debug.error("Error", exception);
     * 
     * NEW PATTERN (REQUIRED):
     * private final LogManager logger = LogManager.getInstance(plugin, getClass());
     * logger.info("Message");
     * logger.error("Error", exception);
     */
    public void demonstrateMigrationPattern() {
        // CORRECT: Use LogManager for all new code
        logger.info("This is the correct way to log messages in RVNKQuests");
        logger.warning("This shows how to log warnings");
        logger.debug("This shows debug logging (only visible when debug is enabled)");
        
        // Performance monitoring
        logger.startTiming("example_operation");
        try {
            // Some operation
            Thread.sleep(10);
        } catch (InterruptedException e) {
            logger.error("Operation interrupted", e);
        }
        logger.endTiming("example_operation");
    }
}
