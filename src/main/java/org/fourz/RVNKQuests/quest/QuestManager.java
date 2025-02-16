package org.fourz.RVNKQuests.quest;

import org.fourz.RVNKQuests.RVNKQuests;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import java.util.ArrayList;
import java.util.List;

public class QuestManager {
    private final RVNKQuests plugin;
    private final Map<String, Quest> quests = new HashMap<>();
    private final Map<Quest, List<Listener>> activeListeners = new HashMap<>();

    public QuestManager(RVNKQuests plugin) {
        this.plugin = plugin;
    }

    public void registerQuest(Quest quest) {
        plugin.getDebugger().debug("Registering quest: " + quest.getId());
        quests.put(quest.getId(), quest);
        quest.initialize();
        updateQuestListeners(quest);
        plugin.getDebugger().debug("Quest registered and initialized: " + quest.getId());
    }

    public Quest getQuest(String id) {
        Quest quest = quests.get(id);
        plugin.getDebugger().debug("Quest lookup for ID '" + id + "': " + (quest != null ? "found" : "not found"));
        return quest;
    }

    public void initializeQuests() {
        plugin.getDebugger().debug("Beginning quest initialization");
        registerQuest(new QuestFirstCityProphecy(plugin));
        plugin.getDebugger().debug("Quest initialization complete. Total quests: " + quests.size());
    }

    public void cleanupQuests() {
        plugin.getDebugger().debug("Starting quest cleanup process");
        
        // Unregister all listeners first
        activeListeners.forEach((quest, listeners) -> {
            plugin.getDebugger().debug("Unregistering " + listeners.size() + " listeners for quest: " + quest.getId());
            listeners.forEach(HandlerList::unregisterAll);
        });
        activeListeners.clear();
        
        // Clean up quests
        plugin.getDebugger().debug("Cleaning up " + quests.size() + " quests");
        quests.values().forEach(quest -> {
            plugin.getDebugger().debug("Cleaning up quest: " + quest.getId());
            quest.cleanup();
        });
        quests.clear();
        plugin.getDebugger().debug("Quest cleanup complete");
    }

    public void registerQuestListeners(Quest quest, Listener... listeners) {
        plugin.getDebugger().debug("Registering " + listeners.length + " listeners for quest: " + quest.getId());
        for (Listener listener : listeners) {
            plugin.getDebugger().debug("Registering listener: " + listener.getClass().getSimpleName());
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
    }

    public void unregisterQuestListeners(Listener... listeners) {
        plugin.getDebugger().debug("Unregistering " + listeners.length + " listeners");
        for (Listener listener : listeners) {
            plugin.getDebugger().debug("Unregistering listener: " + listener.getClass().getSimpleName());
            HandlerList.unregisterAll(listener);
        }
    }

    public void updateQuestListeners(Quest quest) {
        plugin.getDebugger().debug("Updating listeners for quest: " + quest.getId() + " (State: " + quest.getCurrentState() + ")");
        
        // Clean up existing listeners for this quest
        if (activeListeners.containsKey(quest)) {
            List<Listener> oldListeners = activeListeners.get(quest);
            plugin.getDebugger().debug("Removing " + oldListeners.size() + " existing listeners");
            unregisterQuestListeners(oldListeners.toArray(new Listener[0]));
            oldListeners.clear();
        }

        List<Listener> newListeners = new ArrayList<>();
        
        if (quest instanceof QuestFirstCityProphecy questFCP) {
            List<Listener> stateListeners = questFCP.createListenersForState(quest.getCurrentState());
            plugin.getDebugger().debug("Created " + stateListeners.size() + " new listeners for current state");
            newListeners.addAll(stateListeners);
        }

        // Register all new listeners
        plugin.getDebugger().debug("Registering " + newListeners.size() + " new listeners");
        for (Listener listener : newListeners) {
            plugin.getDebugger().debug("Registering new listener: " + listener.getClass().getSimpleName());
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
        activeListeners.put(quest, newListeners);
        plugin.getDebugger().debug("Listener update complete for quest: " + quest.getId());
    }
}
