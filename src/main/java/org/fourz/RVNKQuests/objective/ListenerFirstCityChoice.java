package org.fourz.RVNKQuests.objective;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.QuestFirstCityProphecy;
import org.fourz.RVNKQuests.quest.QuestState;

// Listener for the first city choice objective of the First City Prophecy quest

public class ListenerFirstCityChoice implements Listener {
    private final RVNKQuests plugin;
    private final QuestFirstCityProphecy quest;
    private boolean cityChosen = false;

    public ListenerFirstCityChoice(RVNKQuests plugin, QuestFirstCityProphecy quest) {
        this.plugin = plugin;
        this.quest = quest;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (cityChosen) return;
        if (quest.getCurrentState() != QuestState.QUEST_ACTIVE) return;

        Material placed = event.getBlock().getType();
        if (placed != Material.CAMPFIRE && placed != Material.LECTERN) return;

        Location loc = event.getBlock().getLocation();
        if (!quest.isValidSettlementLocation(loc)) {
            event.getPlayer().sendMessage("§cThe spirits whisper... This location does not match the prophecy.");
            return;
        }

        cityChosen = true;
        Player player = event.getPlayer();
        World world = loc.getWorld();

        world.strikeLightningEffect(loc);
        player.sendMessage("§bThe spirits approve... The settlement is chosen!");
        
        world.spawnEntity(loc.clone().add(0, 10, 0), EntityType.PHANTOM);
        quest.advanceState(QuestState.OBJECTIVE_FOUND);
    }
}
