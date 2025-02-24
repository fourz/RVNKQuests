package org.fourz.RVNKQuests.objective;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Piglin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;

import java.util.ArrayList;
import java.util.List;

public class ListenerPiglinPortal implements Listener {
    private final Quest quest;
    private final List<Piglin> spawnedPiglins = new ArrayList<>();
    private static final int PORTAL_HEIGHT = 85;
    private static final int TRIGGER_DISTANCE = 30;
    private boolean spawned = false;

    public ListenerPiglinPortal(Quest quest) {
        this.quest = quest;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (spawned) return;
        
        Location playerLoc = event.getPlayer().getLocation();
        if (playerLoc.getY() < PORTAL_HEIGHT) return;

        if (isNearLitPortal(playerLoc, TRIGGER_DISTANCE)) {
            spawnPiglinGroup(playerLoc);
            spawned = true;
            quest.advanceState(QuestState.OBJECTIVE_COMPLETE);
        }
    }

    private boolean isNearLitPortal(Location loc, int distance) {
        for (int x = -distance; x <= distance; x++) {
            for (int y = -distance; y <= distance; y++) {
                for (int z = -distance; z <= distance; z++) {
                    Location checkLoc = loc.clone().add(x, y, z);
                    if (checkLoc.getBlock().getType() == Material.NETHER_PORTAL) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void spawnPiglinGroup(Location near) {
        for (int i = 0; i < 5; i++) {
            Location spawnLoc = near.clone().add(
                Math.random() * 10 - 5,
                0,
                Math.random() * 10 - 5
            );
            Piglin piglin = (Piglin) near.getWorld().spawnEntity(spawnLoc, EntityType.PIGLIN);
            piglin.setCustomName("Portal Guard");
            piglin.setCustomNameVisible(true);
            spawnedPiglins.add(piglin);
        }
    }

    public List<Piglin> getSpawnedPiglins() {
        return spawnedPiglins;
    }
}
