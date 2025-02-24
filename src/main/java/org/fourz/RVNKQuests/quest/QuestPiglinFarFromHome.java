package org.fourz.RVNKQuests.quest;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.trigger.*;
import org.fourz.RVNKQuests.objective.*;

import java.util.ArrayList;
import java.util.List;

public class QuestPiglinFarFromHome implements Quest {
    private final RVNKQuests plugin;
    private QuestState currentState = QuestState.NOT_STARTED;
    private final ListenerLonePiglin lonePiglinListener;
    private final ListenerPiglinPortal piglinPortalListener;

    public QuestPiglinFarFromHome(RVNKQuests plugin) {
        this.plugin = plugin;
        this.lonePiglinListener = new ListenerLonePiglin(this);
        this.piglinPortalListener = new ListenerPiglinPortal(this);
    }

    @Override
    public String getId() {
        return "piglin_far_from_home";
    }

    @Override
    public String getName() {
        return "Piglin Far From Home";
    }

    @Override
    public void initialize() {
        // No initialization needed
    }

    @Override
    public void cleanup() {
        // Remove any remaining entities if needed
    }

    @Override
    public boolean isCompleted(Player player) {
        return currentState == QuestState.COMPLETED;
    }

    @Override
    public QuestState getCurrentState() {
        return currentState;
    }

    @Override
    public void advanceState(QuestState newState) {
        this.currentState = newState;
        plugin.getQuestManager().updateQuestListeners(this);
    }

    @Override
    public Location getLecternLocation() {
        return null; // Not used in this quest
    }

    @Override
    public RVNKQuests getPlugin() {
        return plugin;
    }

    public List<Listener> createListenersForState(QuestState state) {
        List<Listener> listeners = new ArrayList<>();
        
        switch (state) {
            case NOT_STARTED:
                listeners.add(lonePiglinListener);
                break;
            case TRIGGER_FOUND:
                listeners.add(new ListenerLonePiglinDeath(this, lonePiglinListener));
                break;
            case QUEST_ACTIVE:
                listeners.add(piglinPortalListener);
                break;
            case OBJECTIVE_COMPLETE:
                listeners.add(new ListenerPiglinPortalDefeated(this, piglinPortalListener));
                break;
        }
        
        return listeners;
    }
}
