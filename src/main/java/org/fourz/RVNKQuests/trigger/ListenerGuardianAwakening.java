package org.fourz.RVNKQuests.trigger;

import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ElderGuardian;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.generator.structure.StructureType;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;

public class ListenerGuardianAwakening implements Listener {
    private final Quest quest;
    private ElderGuardian guardian;
    private static final String EVENT_WORLD = "event";
    private static final int REQUIRED_DISTANCE = 50;

    public ListenerGuardianAwakening(Quest quest) {
        this.quest = quest;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        World world = event.getPlayer().getWorld();
        if (!world.getName().equals(EVENT_WORLD)) return;
        
        Location spawnLoc = world.getSpawnLocation();
        if (world.getPlayers().stream()
                .allMatch(p -> p.getLocation().distance(spawnLoc) <= REQUIRED_DISTANCE) 
                && guardian == null) {
            spawnGuardian(world);
        }
    }

    private void spawnGuardian(World world) {
        Location structure = world.locateNearestStructure(
            world.getSpawnLocation(),
            StructureType.OCEAN_MONUMENT,
            100,
            false
        ).getLocation();
        
        if (structure != null) {
            guardian = (ElderGuardian) world.spawnEntity(structure, EntityType.ELDER_GUARDIAN);
            guardian.setCustomName("Ancient Guardian");
            guardian.setCustomNameVisible(true);
            guardian.setRemoveWhenFarAway(false);
            quest.advanceState(QuestState.TRIGGER_FOUND);
        }
    }

    public ElderGuardian getGuardian() {
        return guardian;
    }
}
