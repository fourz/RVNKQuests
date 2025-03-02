package org.fourz.RVNKQuests.objective;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;

import java.util.ArrayList;
import java.util.List;

public class ListenerForgottenSite implements Listener {
    private final Quest quest;
    private final List<Drowned> defenders = new ArrayList<>();
    private static final int TRIGGER_DISTANCE = 30;
    private boolean spawned = false;

    public ListenerForgottenSite(Quest quest) {
        this.quest = quest;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (spawned) return;

        // Only check if player moved to a new block (ignore rotation changes)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
        event.getFrom().getBlockY() == event.getTo().getBlockY() &&
        event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
        return;
            }
        
        Location playerLoc = event.getPlayer().getLocation();
        if (!isNearUnderwaterStructure(playerLoc)) return;

        spawnDefenders(playerLoc);
        spawned = true;
        quest.advanceState(QuestState.OBJECTIVE_FOUND);
    }

    private boolean isNearUnderwaterStructure(Location loc) {        

        for (int x = -TRIGGER_DISTANCE; x <= TRIGGER_DISTANCE; x+= 2) {
            for (int y = -TRIGGER_DISTANCE; y <= TRIGGER_DISTANCE; y += 2) {
                for (int z = -TRIGGER_DISTANCE; z <= TRIGGER_DISTANCE; z += 2) {
                    Location checkLoc = loc.clone().add(x, y, z);
                    Material type = checkLoc.getBlock().getType();
                    if (type == Material.ANCIENT_DEBRIS || type == Material.PRISMARINE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void spawnDefenders(Location center) {
        for (int i = 0; i < 5; i++) {
            Location spawnLoc = center.clone().add(
                Math.random() * 10 - 5,
                0,
                Math.random() * 10 - 5
            );
            Drowned drowned = (Drowned) center.getWorld().spawnEntity(spawnLoc, EntityType.DROWNED);
            drowned.setCustomName("Ancient Defender");
            drowned.setCustomNameVisible(true);
            drowned.getEquipment().setItemInMainHand(new ItemStack(Material.TRIDENT));
            defenders.add(drowned);
        }
    }

    public List<Drowned> getDefenders() {
        return defenders;
    }
}
