package org.fourz.RVNKQuests.objective.generic;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Generic discover objective — player must find a location or structure.
 * Generalizes ListenerForgottenSite.
 *
 * <h3>Config keys:</h3>
 * <ul>
 *   <li>{@code world} — World name</li>
 *   <li>{@code detection_radius} — Scan radius (default: 30)</li>
 *   <li>{@code detection_materials} — Comma-separated material names to detect structure</li>
 *   <li>{@code min_blocks} — Minimum matching blocks to consider "discovered" (default: 3)</li>
 *   <li>{@code required_state} — QuestState player must be in (default: "QUEST_ACTIVE")</li>
 *   <li>{@code advance_state} — State to advance to (default: "OBJECTIVE_FOUND")</li>
 *   <li>{@code requires_path} — Only active when player's pathChoice matches (optional)</li>
 *   <li>{@code sets_path} — Sets pathChoice on completion (optional)</li>
 * </ul>
 */
public class GenericDiscoverObjective implements Listener {

    private final DataDrivenQuest quest;
    private final LogManager logger;

    private final String worldName;
    private final double detectionRadius;
    private final Set<Material> detectionMaterials;
    private final int minBlocks;
    private final QuestState requiredState;
    private final QuestState advanceState;
    private final String requiresPath;
    private final String setsPath;

    public GenericDiscoverObjective(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericDiscoverObjective");

        this.worldName = QuestComponentFactory.getStringConfig(config, "world", "world");
        this.detectionRadius = QuestComponentFactory.getDoubleConfig(config, "detection_radius", 30.0);
        this.minBlocks = QuestComponentFactory.getIntConfig(config, "min_blocks", 3);
        this.requiredState = parseState(QuestComponentFactory.getStringConfig(config, "required_state", "QUEST_ACTIVE"));
        this.advanceState = parseState(QuestComponentFactory.getStringConfig(config, "advance_state", "OBJECTIVE_FOUND"));
        this.requiresPath = QuestComponentFactory.getStringConfig(config, "requires_path", null);
        this.setsPath = QuestComponentFactory.getStringConfig(config, "sets_path", null);

        // Parse detection materials
        this.detectionMaterials = new HashSet<>();
        String materialsStr = QuestComponentFactory.getStringConfig(config, "detection_materials", "");
        for (String mat : materialsStr.split(",")) {
            mat = mat.trim().toUpperCase();
            if (!mat.isEmpty()) {
                try {
                    detectionMaterials.add(Material.valueOf(mat));
                } catch (IllegalArgumentException e) {
                    logger.warning("Unknown material in detection_materials: " + mat);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        checkDiscover(event.getPlayer());
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        checkDiscover(event.getPlayer());
    }

    private void checkDiscover(Player player) {
        if (quest.getStateForPlayer(player) != requiredState) return;

        // Check path restriction
        if (requiresPath != null) {
            String playerPath = quest.getPathChoiceCached(player);
            if (!requiresPath.equals(playerPath)) return;
        }

        World world = player.getWorld();
        if (!world.getName().equalsIgnoreCase(worldName)) return;

        // If no detection materials, treat as simple reach
        if (detectionMaterials.isEmpty()) {
            if (setsPath != null) quest.setPathChoice(player, setsPath);
            quest.advanceStateForPlayer(player.getUniqueId(), advanceState);
            return;
        }

        // Scan nearby blocks for structure materials
        Location loc = player.getLocation();
        int radius = (int) detectionRadius;
        int found = 0;

        for (int x = -radius; x <= radius && found < minBlocks; x += 2) {
            for (int z = -radius; z <= radius && found < minBlocks; z += 2) {
                for (int y = -5; y <= 5 && found < minBlocks; y++) {
                    Block block = world.getBlockAt(
                        loc.getBlockX() + x, loc.getBlockY() + y, loc.getBlockZ() + z);
                    if (detectionMaterials.contains(block.getType())) {
                        found++;
                    }
                }
            }
        }

        if (found >= minBlocks) {
            quest.setContext("discovered_location", player.getLocation());
            if (setsPath != null) quest.setPathChoice(player, setsPath);
            quest.advanceStateForPlayer(player.getUniqueId(), advanceState);
            logger.debug(player.getName() + " discovered structure for quest " + quest.getId());
        }
    }

    private QuestState parseState(String name) {
        try {
            return QuestState.valueOf(name);
        } catch (IllegalArgumentException e) {
            return QuestState.QUEST_ACTIVE;
        }
    }
}
