package org.fourz.RVNKQuests.quest;

import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.util.Debug;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class QuestManager {
    private static final String CLASS_NAME = "QuestManager";
    private final RVNKQuests plugin;
    private final Debug debugger;
    private final Map<String, Quest> quests = new HashMap<>();
    private final Map<Quest, List<Listener>> activeListeners = new HashMap<>();
    private final Map<String, Integer> scheduledTasks = new HashMap<>();

    public QuestManager(RVNKQuests plugin) {
        this.plugin = plugin;
        this.debugger = Debug.createDebugger(plugin, CLASS_NAME, plugin.getDebugger().getLogLevel());
    }

    /**
     * Updates the debug level for this manager
     * @param level New log level
     */
    public void updateDebugLevel(Level level) {
        debugger.setLogLevel(level);
        debugger.debug("QuestManager log level updated to: " + level.getName());
    }

    public void registerQuest(Quest quest) {
        if (quest == null) {
            debugger.warning("Attempted to register null quest");
            return;
        }
        
        String questId = quest.getId();
        if (questId == null || questId.isEmpty()) {
            debugger.warning("Attempted to register quest with null or empty ID");
            return;
        }
        
        if (quests.containsKey(questId)) {
            debugger.warning("Quest already registered with ID: " + questId);
            return;
        }
        
        debugger.debug("Registering quest: " + questId);
        quests.put(questId, quest);
        
        try {
            quest.initialize();
            debugger.debug("Quest initialized: " + questId);
        } catch (Exception e) {
            debugger.error("Failed to initialize quest: " + questId, e);
        }
        
        try {
            updateQuestListeners(quest);
            debugger.debug("Quest registered and listeners initialized: " + questId);
        } catch (Exception e) {
            debugger.error("Failed to register listeners for quest: " + questId, e);
        }
    }

    public Quest getQuest(String id) {
        Quest quest = quests.get(id);
        debugger.debug("Quest lookup for ID '" + id + "': " + (quest != null ? "found" : "not found"));
        return quest;
    }

    public void initializeQuests() {
        debugger.debug("Beginning quest initialization");
        
        try {
            registerQuestIfEnabled(new QuestPiglinFarFromHome(plugin));
            registerQuestIfEnabled(new QuestAncientGuardian(plugin));
            debugger.debug("Quest initialization complete. Total quests: " + quests.size());
        } catch (Exception e) {
            debugger.error("Error during quest initialization", e);
        }
    }
    
    /**
     * Register a quest if it is enabled in the configuration
     * @param quest The quest to register
     */
    private void registerQuestIfEnabled(Quest quest) {
        String questId = quest.getId();
        boolean enabled = plugin.getConfigManager().isQuestEnabled(questId);
        
        if (enabled) {
            debugger.debug("Registering enabled quest: " + questId);
            registerQuest(quest);
        } else {
            debugger.info("Skipping disabled quest: " + questId);
        }
    }

    public void cleanupQuests() {
        debugger.debug("Starting quest cleanup process");
        
        // Cancel all scheduled tasks
        debugger.debug("Cancelling " + scheduledTasks.size() + " scheduled tasks");
        for (String taskId : new ArrayList<>(scheduledTasks.keySet())) {
            cancelTask(taskId);
        }
        scheduledTasks.clear();
        
        // Unregister all listeners first
        activeListeners.forEach((quest, listeners) -> {
            debugger.debug("Unregistering " + listeners.size() + " listeners for quest: " + quest.getId());
            listeners.forEach(HandlerList::unregisterAll);
        });
        activeListeners.clear();
        
        // Clean up quests
        debugger.debug("Cleaning up " + quests.size() + " quests");
        quests.values().forEach(quest -> {
            debugger.debug("Cleaning up quest: " + quest.getId());
            quest.cleanup();
        });
        quests.clear();
        debugger.debug("Quest cleanup complete");
    }

    /**
     * Fully resets the quest system by cleaning up all existing quests
     * and reinitializing them. This simulates a plugin restart.
     * 
     * Warning: This will lose all in-memory quest progress.
     * Future implementations should preserve progress for players in a database.
     */
    public void resetQuests() {
        debugger.debug("Resetting all quests");
        
        // First clean up all existing quests
        cleanupQuests();
        
        // Then reinitialize quests
        initializeQuests();
        
        debugger.debug("Quest reset complete");
    }

    public void registerQuestListeners(Quest quest, Listener... listeners) {
        debugger.debug("Registering " + listeners.length + " listeners for quest: " + quest.getId());
        for (Listener listener : listeners) {
            debugger.debug("Registering listener: " + listener.getClass().getSimpleName());
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
    }

    public void unregisterQuestListeners(Listener... listeners) {
        debugger.debug("Unregistering " + listeners.length + " listeners");
        for (Listener listener : listeners) {
            debugger.debug("Unregistering listener: " + listener.getClass().getSimpleName());
            HandlerList.unregisterAll(listener);
        }
    }

    public void updateQuestListeners(Quest quest) {
        if (quest == null) {
            debugger.warning("Attempted to update listeners for null quest");
            return;
        }
        
        QuestState currentState = quest.getCurrentState();
        debugger.debug("Updating listeners for quest: " + quest.getId() + " (State: " + currentState + ")");
        
        // Clean up existing listeners for this quest
        if (activeListeners.containsKey(quest)) {
            List<Listener> oldListeners = activeListeners.get(quest);
            debugger.debug("Removing " + oldListeners.size() + " existing listeners");
            unregisterQuestListeners(oldListeners.toArray(new Listener[0]));
            oldListeners.clear();
        }

        List<Listener> newListeners;
        try {
            newListeners = quest.createListenersForState(currentState);
            if (newListeners == null) {
                debugger.warning("Quest returned null listeners for state " + currentState + ": " + quest.getId());
                newListeners = new ArrayList<>();
            }
        } catch (Exception e) {
            debugger.error("Error creating listeners for quest: " + quest.getId(), e);
            newListeners = new ArrayList<>();
        }
        
        // Register all new listeners
        debugger.debug("Registering " + newListeners.size() + " new listeners");
        for (Listener listener : newListeners) {
            if (listener == null) {
                debugger.warning("Null listener in list for quest: " + quest.getId());
                continue;
            }
            
            try {
                debugger.debug("Registering new listener: " + listener.getClass().getSimpleName());
                plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            } catch (Exception e) {
                debugger.error("Failed to register listener: " + listener.getClass().getSimpleName(), e);
            }
        }
        activeListeners.put(quest, newListeners);
        debugger.debug("Listener update complete for quest: " + quest.getId());
    }

    /**
     * Schedules a repeating task with the Bukkit scheduler
     *
     * @param taskId Unique identifier for the task
     * @param task The runnable task to execute
     * @param interval The interval in ticks between executions
     * @return The task ID from Bukkit scheduler
     */
    public int scheduleRepeatingTask(String taskId, Runnable task, long interval) {
        debugger.debug("Scheduling repeating task: " + taskId + " (interval: " + interval + " ticks)");
        int taskNumber = plugin.getServer().getScheduler()
            .scheduleSyncRepeatingTask(plugin, task, 0L, interval);
        
        if (taskNumber != -1) {
            scheduledTasks.put(taskId, taskNumber);
            debugger.debug("Task scheduled successfully: " + taskId + " (task#: " + taskNumber + ")");
        } else {
            debugger.warning("Failed to schedule task: " + taskId);
        }
        
        return taskNumber;
    }

    /**
     * Cancels a scheduled task by its ID
     *
     * @param taskId The ID of the task to cancel
     */
    public void cancelTask(String taskId) {
        Integer taskNumber = scheduledTasks.remove(taskId);
        if (taskNumber != null) {
            debugger.debug("Cancelling task: " + taskId + " (task#: " + taskNumber + ")");
            plugin.getServer().getScheduler().cancelTask(taskNumber);
        }
    }

    /**
     * Gets the IDs of all registered quests
     * 
     * @return A list of quest IDs
     */
    public List<String> getQuestIds() {
        return new ArrayList<>(quests.keySet());
    }

    /**
     * Gets all registered quests
     * 
     * @return A list of quests
     */
    public List<Quest> getAllQuests() {
        return new ArrayList<>(quests.values());
    }

    /**
     * Validates all registered quests to catch configuration issues
     * @return true if all quests are valid
     */
    public boolean validateQuests() {
        debugger.debug("Validating all registered quests...");
        boolean allValid = true;
        
        for (Quest quest : quests.values()) {
            try {
                // Basic validation
                if (quest.getId() == null || quest.getId().isEmpty()) {
                    debugger.warning("Quest has null or empty ID");
                    allValid = false;
                }
                
                if (quest.getName() == null || quest.getName().isEmpty()) {
                    debugger.warning("Quest has null or empty name: " + quest.getId());
                    allValid = false;
                }
                
                // Check listener creation for each state
                for (QuestState state : QuestState.values()) {
                    List<Listener> listeners = quest.createListenersForState(state);
                    if (listeners == null) {
                        debugger.warning(quest.getId() + " returned null listeners for state: " + state);
                        allValid = false;
                    }
                }
                
                debugger.debug("Validated quest: " + quest.getId());
            } catch (Exception e) {
                debugger.error("Exception during validation of quest: " + quest.getId(), e);
                allValid = false;
            }
        }
        
        debugger.debug("Quest validation complete. All valid: " + allValid);
        return allValid;
    }
}
