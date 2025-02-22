package org.fourz.RVNKQuests.quest;

import org.bukkit.entity.Player;    
import org.bukkit.Location;
import org.fourz.RVNKQuests.RVNKQuests;

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
}
