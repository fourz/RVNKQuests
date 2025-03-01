package org.fourz.RVNKQuests.quest;

import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.util.Debug;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import java.util.ArrayList;
import java.util.List;
import org.fourz.RVNKQuests.quest.*;

public class QuestManager {
    private static final String CLASS_NAME = "QuestManager";
    private final RVNKQuests plugin;
    private final Debug debugger;
    private final Map<String, Quest> quests = new HashMap<>();
    private final Map<Quest, List<Listener>> activeListeners = new HashMap<>();
    private final Map<String, Integer> scheduledTasks = new HashMap<>();

    public QuestManager(RVNKQuests plugin) {
        this.plugin = plugin;
        this.debugger = new Debug(plugin, CLASS_NAME, plugin.getDebugger().getLogLevel()) {};
    }

    public void registerQuest(Quest quest) {
        debugger.debug("Registering quest: " + quest.getId());
        quests.put(quest.getId(), quest);
        quest.initialize();
        updateQuestListeners(quest);
        debugger.debug("Quest registered and initialized: " + quest.getId());
    }

    public Quest getQuest(String id) {
        Quest quest = quests.get(id);
        debugger.debug("Quest lookup for ID '" + id + "': " + (quest != null ? "found" : "not found"));
        return quest;
    }

    public void initializeQuests() {
        debugger.debug("Beginning quest initialization");
        //registerQuest(new QuestFirstCityProphecy(plugin));
        registerQuest(new QuestPiglinFarFromHome(plugin));
        //registerQuest(new QuestAncientGuardian(plugin)); // Add this line
        debugger.debug("Quest initialization complete. Total quests: " + quests.size());
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
        debugger.debug("Updating listeners for quest: " + quest.getId() + " (State: " + quest.getCurrentState() + ")");
        
        // Clean up existing listeners for this quest
        if (activeListeners.containsKey(quest)) {
            List<Listener> oldListeners = activeListeners.get(quest);
            debugger.debug("Removing " + oldListeners.size() + " existing listeners");
            unregisterQuestListeners(oldListeners.toArray(new Listener[0]));
            oldListeners.clear();
        }

        List<Listener> newListeners = quest.createListenersForState(quest.getCurrentState());
        
        // Register all new listeners
        debugger.debug("Registering " + newListeners.size() + " new listeners");
        for (Listener listener : newListeners) {
            debugger.debug("Registering new listener: " + listener.getClass().getSimpleName());
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
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
}
