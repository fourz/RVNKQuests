package org.fourz.RVNKQuests.quest;

import org.bukkit.entity.Player;    
import org.bukkit.Location;
import org.fourz.RVNKQuests.RVNKQuests;
import org.bukkit.event.Listener;
import java.util.List;

public interface Quest {
    String getId();
    String getName();
    void initialize();
    void cleanup();
    boolean isCompleted(Player player);
    QuestState getCurrentState();
    void advanceState(QuestState newState);
    Location getLecternLocation();
    RVNKQuests getPlugin();

    /**
     * Creates and returns a list of listeners appropriate for the current quest state
     * @param state The current state of the quest
     * @return List of listeners for the given state
     */
    List<Listener> createListenersForState(QuestState state);
}
