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
}
