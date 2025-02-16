package org.fourz.RVNKQuests.trigger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.fourz.RVNKQuests.quest.QuestFirstCityProphecy;
import org.fourz.RVNKQuests.quest.QuestState;


public class ListenerEventPopulated implements Listener {
    private final QuestFirstCityProphecy quest;
    private boolean triggered = false;

    public ListenerEventPopulated(QuestFirstCityProphecy quest) {
        this.quest = quest;
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        checkEventWorldPopulation();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        checkEventWorldPopulation();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        checkEventWorldPopulation();
    }

    private void checkEventWorldPopulation() {
        if (triggered) return;

        World eventWorld = Bukkit.getWorld("event");
        if (eventWorld == null) return;

        boolean allPlayersInEventWorld = Bukkit.getOnlinePlayers().stream()
            .allMatch(player -> player.getWorld().equals(eventWorld));

        if (allPlayersInEventWorld) {
            triggered = true;
            quest.buildQuestBeacon();
            quest.advanceState(QuestState.TRIGGER_FOUND);
        }
    }
}
