package org.fourz.RVNKQuests.trigger.generic;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Map;
import java.util.Set;

/**
 * Generic structure interaction trigger — triggers when player interacts with a block type.
 * Generalizes ListenerQuestPillarStart.
 *
 * <h3>Config keys:</h3>
 * <ul>
 *   <li>{@code block_type} — Material name (e.g., "LECTERN")</li>
 *   <li>{@code world} — World name restriction (optional)</li>
 *   <li>{@code x} / {@code y} / {@code z} — the block this trigger is sited on (optional)</li>
 *   <li>{@code radius} — how far from x/y/z still counts, in blocks (default {@value #DEFAULT_RADIUS})</li>
 *   <li>{@code required_state} — state the player must be in (default: "NOT_STARTED")</li>
 *   <li>{@code advance_state} — State to advance to (default: "TRIGGER_FOUND")</li>
 * </ul>
 *
 * <h3>Coordinates are optional, and omitting them means world-wide (#1894)</h3>
 * Before #1894 this class parsed <b>only</b> {@code block_type}, {@code world} and
 * {@code advance_state}. Quest YAML had been supplying {@code x}/{@code y}/{@code z} and
 * {@code required_state} for a long time and they were silently discarded, so
 * {@code tfah_ch1_journey} — which declares a start lectern at {@code alphac -315,118,446} — was
 * in fact started by right-clicking <i>any lectern anywhere in {@code alphac}</i>.
 *
 * <p>Coordinates stay optional so quests that genuinely want "any block of this type in this
 * world" keep working unchanged. What is no longer possible is declaring coordinates and
 * silently not getting them.</p>
 */
public class GenericStructureInteractTrigger implements Listener {

    /**
     * Default match radius when coordinates are given without one.
     *
     * <p>Deliberately tight. A trigger sited on a specific block wants that block, give or take a
     * builder placing it a block off — not the room it stands in.</p>
     */
    private static final double DEFAULT_RADIUS = 3.0;

    /** Config keys this component actually reads. Anything else is a typo or a wrong assumption. */
    private static final Set<String> KNOWN_KEYS = buildKnownKeys();

    private static Set<String> buildKnownKeys() {
        Set<String> keys = new java.util.HashSet<>(Set.of(
            "type", "block_type", "world", "x", "y", "z", "radius", "required_state", "advance_state"));
        keys.addAll(org.fourz.RVNKQuests.util.OutOfOrderFeedback.configKeys());
        return Set.copyOf(keys);
    }

    private final RVNKQuests plugin;
    private final DataDrivenQuest quest;
    private final LogManager logger;

    private final Material blockType;
    private final String worldName;
    private final QuestState requiredState;
    private final QuestState advanceState;

    /** Site of the trigger, or null when it is world-wide. */
    private final Double x;
    private final Double y;
    private final Double z;
    private final double radiusSquared;

    /** Explains a right-click that hit the right block at the wrong beat. */
    private final org.fourz.RVNKQuests.util.OutOfOrderFeedback feedback;

    public GenericStructureInteractTrigger(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.plugin = plugin;
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericStructureInteractTrigger");

        this.blockType = parseMaterial(QuestComponentFactory.getStringConfig(config, "block_type", "LECTERN"));
        this.worldName = QuestComponentFactory.getStringConfig(config, "world", null);
        this.requiredState = parseState(
            QuestComponentFactory.getStringConfig(config, "required_state", "NOT_STARTED"),
            QuestState.NOT_STARTED);
        this.advanceState = parseState(
            QuestComponentFactory.getStringConfig(config, "advance_state", "TRIGGER_FOUND"),
            QuestState.TRIGGER_FOUND);

        // All three or none — a partial coordinate is a mistake, not a half-sited trigger.
        boolean sited = config.containsKey("x") && config.containsKey("y") && config.containsKey("z");
        if (!sited && (config.containsKey("x") || config.containsKey("y") || config.containsKey("z"))) {
            logger.warning("Quest '" + quest.getId() + "': STRUCTURE_INTERACT has a partial"
                + " coordinate (needs x, y AND z) — falling back to world-wide matching");
        }
        if (sited) {
            this.x = QuestComponentFactory.getDoubleConfig(config, "x", 0.0);
            this.y = QuestComponentFactory.getDoubleConfig(config, "y", 0.0);
            this.z = QuestComponentFactory.getDoubleConfig(config, "z", 0.0);
        } else {
            this.x = null;
            this.y = null;
            this.z = null;
        }
        double radius = QuestComponentFactory.getDoubleConfig(config, "radius", DEFAULT_RADIUS);
        this.radiusSquared = radius * radius;
        this.feedback = org.fourz.RVNKQuests.util.OutOfOrderFeedback.from(config);

        warnUnknownKeys(config);

        logger.debug("STRUCTURE_INTERACT for '" + quest.getId() + "': " + blockType
            + (worldName == null ? " in any world" : " in " + worldName)
            + (sited ? " at " + x + "," + y + "," + z + " r=" + radius : " (world-wide, no coords)")
            + " " + requiredState + " -> " + advanceState);
    }

    /**
     * Warns about config keys this component does not read.
     *
     * <p>The real defect behind #1894 was not the missing coordinate check — it was that the
     * coordinates were <i>accepted silently</i>. A quest author got no signal at author time, at
     * load time, or from {@code preflight}, which reported the trigger "ok" while half its
     * configuration was discarded.</p>
     */
    private void warnUnknownKeys(Map<String, Object> config) {
        for (String key : config.keySet()) {
            if (!KNOWN_KEYS.contains(key)) {
                logger.warning("Quest '" + quest.getId() + "': STRUCTURE_INTERACT ignores"
                    + " unknown config key '" + key + "' — it will have no effect");
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();

        // Cheapest gates first, and the state check last of the three: reaching it means the player
        // is on the right block in the right place, so a mismatch there is worth explaining rather
        // than swallowing (see OutOfOrderFeedback).
        if (worldName != null && !player.getWorld().getName().equalsIgnoreCase(worldName)) return;

        if (block.getType() != blockType) return;

        if (!onSite(block)) return;

        QuestState currentState = quest.getStateForPlayer(player);
        if (currentState != requiredState) {
            feedback.notifyWrongBeat(player, currentState, requiredState);
            return;
        }

        quest.advanceStateForPlayer(player.getUniqueId(), advanceState);
        logger.debug("Structure interact trigger fired for " + player.getName() + " on " + blockType
            + " at " + block.getX() + "," + block.getY() + "," + block.getZ());
    }

    /** @return true when the block is within {@code radius} of the configured site, or unsited. */
    private boolean onSite(Block block) {
        if (x == null) return true;
        double dx = block.getX() - x;
        double dy = block.getY() - y;
        double dz = block.getZ() - z;
        return (dx * dx + dy * dy + dz * dz) <= radiusSquared;
    }

    private Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown material: " + name + ", defaulting to LECTERN");
            return Material.LECTERN;
        }
    }

    private QuestState parseState(String name, QuestState fallback) {
        try {
            return QuestState.valueOf(name);
        } catch (IllegalArgumentException e) {
            logger.warning("Quest '" + quest.getId() + "': unknown quest state '" + name
                + "' — falling back to " + fallback);
            return fallback;
        }
    }
}
