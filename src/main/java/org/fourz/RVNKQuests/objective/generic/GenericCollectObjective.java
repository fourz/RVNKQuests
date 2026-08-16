package org.fourz.RVNKQuests.objective.generic;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic collect objective — player must have required items in inventory.
 * When all items are present and the player is within the optional radius,
 * items are optionally consumed and the quest state advances.
 *
 * <h3>Config keys:</h3>
 * <ul>
 *   <li>{@code items} — Map of Material name to required count (e.g., {"SOUL_TORCH": 4, "GHAST_TEAR": 1})</li>
 *   <li>{@code consume} — Whether to remove items from inventory on completion (default: true)</li>
 *   <li>{@code world} — World restriction (optional)</li>
 *   <li>{@code x}, {@code y}, {@code z} — Location center for radius check (optional)</li>
 *   <li>{@code radius} — Radius from location to check inventory (default: 0 = anywhere)</li>
 *   <li>{@code context_location_key} — Runtime context key for location (optional, overrides x/y/z)</li>
 *   <li>{@code required_state} — QuestState player must be in (default: "QUEST_ACTIVE")</li>
 *   <li>{@code advance_state} — State to advance to (default: "OBJECTIVE_FOUND")</li>
 *   <li>{@code requires_path} — Only active when player's pathChoice matches (optional)</li>
 *   <li>{@code sets_path} — Sets pathChoice on completion (optional)</li>
 * </ul>
 */
public class GenericCollectObjective implements Listener {

    private final RVNKQuests plugin;
    private final DataDrivenQuest quest;
    private final LogManager logger;

    private final Map<Material, Integer> requiredItems;
    private final boolean consumeItems;
    private final String worldName;
    private final double locationX;
    private final double locationY;
    private final double locationZ;
    private final double radius;
    private final String contextLocationKey;
    private final QuestState requiredState;
    private final QuestState advanceState;
    private final String requiresPath;
    private final String setsPath;

    /** Cooldown to avoid spamming action bar messages every tick. */
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private static final long MESSAGE_COOLDOWN_MS = 3000;

    @SuppressWarnings("unchecked")
    public GenericCollectObjective(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.plugin = plugin;
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericCollectObjective");

        this.consumeItems = QuestComponentFactory.getBoolConfig(config, "consume", true);
        this.worldName = QuestComponentFactory.getStringConfig(config, "world", null);
        this.locationX = QuestComponentFactory.getDoubleConfig(config, "x", 0);
        this.locationY = QuestComponentFactory.getDoubleConfig(config, "y", 64);
        this.locationZ = QuestComponentFactory.getDoubleConfig(config, "z", 0);
        this.radius = QuestComponentFactory.getDoubleConfig(config, "radius", 0);
        this.contextLocationKey = QuestComponentFactory.getStringConfig(config, "context_location_key", null);
        this.requiredState = parseState(QuestComponentFactory.getStringConfig(config, "required_state", "QUEST_ACTIVE"));
        this.advanceState = parseState(QuestComponentFactory.getStringConfig(config, "advance_state", "OBJECTIVE_FOUND"));
        this.requiresPath = QuestComponentFactory.getStringConfig(config, "requires_path", null);
        this.setsPath = QuestComponentFactory.getStringConfig(config, "sets_path", null);

        // Parse items map
        this.requiredItems = new LinkedHashMap<>();
        Object itemsObj = config.get("items");
        if (itemsObj instanceof Map) {
            Map<String, Object> itemsMap = (Map<String, Object>) itemsObj;
            for (Map.Entry<String, Object> entry : itemsMap.entrySet()) {
                Material mat = parseMaterial(entry.getKey());
                if (mat != null) {
                    int count = 1;
                    if (entry.getValue() instanceof Number) {
                        count = ((Number) entry.getValue()).intValue();
                    } else if (entry.getValue() instanceof String) {
                        try { count = Integer.parseInt((String) entry.getValue()); } catch (NumberFormatException ignored) {}
                    }
                    requiredItems.put(mat, count);
                }
            }
        }

        if (requiredItems.isEmpty()) {
            logger.warning("GenericCollectObjective has no valid items configured for quest " + quest.getId());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        checkCollect(event.getPlayer(), event.getTo());
    }

    /**
     * Arrival by teleport or portal counts as arrival (#1932).
     *
     * <p>{@link org.bukkit.event.player.PlayerTeleportEvent} extends {@link PlayerMoveEvent} but
     * declares its own {@code HandlerList}, so the move handler never sees a teleport. A sited
     * COLLECT objective would therefore not complete for a player who portals in holding the
     * items, until they took a step.</p>
     */
    @EventHandler
    public void onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        checkCollect(event.getPlayer(), event.getTo());
    }

    /** Shared arrival check. {@code arrival} is the destination — on a teleport the player has not moved yet. */
    private void checkCollect(Player player, org.bukkit.Location arrival) {
        if (quest.getStateForPlayer(player) != requiredState) return;

        // Check path restriction
        if (requiresPath != null) {
            String playerPath = quest.getPathChoiceCached(player);
            if (!requiresPath.equals(playerPath)) return;
        }

        // World check
        if (worldName != null && !arrival.getWorld().getName().equalsIgnoreCase(worldName)) return;

        // Location/radius check (if configured)
        if (radius > 0) {
            org.bukkit.Location targetLoc = getTargetLocation(player);
            if (targetLoc == null) return;
            if (targetLoc.getWorld() != null && !arrival.getWorld().equals(targetLoc.getWorld())) return;
            if (arrival.distanceSquared(targetLoc) > radius * radius) return;
        }

        // Check inventory for required items
        Map<Material, Integer> missing = getMissingItems(player);

        if (missing.isEmpty()) {
            // All items present — consume if configured and advance
            if (consumeItems) {
                consumeRequiredItems(player);
            }

            lastMessageTime.remove(player.getUniqueId());
            if (setsPath != null) {
                quest.setPathChoice(player, setsPath);
            }
            // Party fan-out (#1986): prefer the configured/resolved target when the objective has
            // one, because that is the authored checkpoint. Location is optional on a collect —
            // without it the beat is "have these items, anywhere", so fall back to the player at
            // radius 0 and let the service's min_share_radius floor govern.
            org.bukkit.Location checkpoint = getTargetLocation(player);
            quest.advanceStateForPlayer(player.getUniqueId(), advanceState,
                checkpoint != null
                    ? org.fourz.RVNKQuests.party.PartyBeatContext.of(checkpoint, radius, requiredState)
                    : org.fourz.RVNKQuests.party.PartyBeatContext.of(
                        player.getLocation(), 0.0, requiredState));
            logger.debug(player.getName() + " completed collect objective for quest " + quest.getId());
        } else {
            // Show progress message (throttled)
            sendProgressMessage(player, missing);
        }
    }

    /**
     * Gets items the player is still missing.
     */
    private Map<Material, Integer> getMissingItems(Player player) {
        Map<Material, Integer> missing = new LinkedHashMap<>();

        for (Map.Entry<Material, Integer> entry : requiredItems.entrySet()) {
            Material mat = entry.getKey();
            int required = entry.getValue();
            int held = countInInventory(player, mat);
            if (held < required) {
                missing.put(mat, required - held);
            }
        }

        return missing;
    }

    private int countInInventory(Player player, Material material) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private void consumeRequiredItems(Player player) {
        for (Map.Entry<Material, Integer> entry : requiredItems.entrySet()) {
            Material mat = entry.getKey();
            int toRemove = entry.getValue();

            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack != null && stack.getType() == mat && toRemove > 0) {
                    int take = Math.min(stack.getAmount(), toRemove);
                    stack.setAmount(stack.getAmount() - take);
                    toRemove -= take;
                }
            }
        }
    }

    private void sendProgressMessage(Player player, Map<Material, Integer> missing) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastTime = lastMessageTime.get(playerId);
        if (lastTime != null && (now - lastTime) < MESSAGE_COOLDOWN_MS) return;

        lastMessageTime.put(playerId, now);

        StringBuilder msg = new StringBuilder("\u00a7eYou need: ");
        boolean first = true;
        for (Map.Entry<Material, Integer> entry : missing.entrySet()) {
            if (!first) msg.append(", ");
            msg.append("\u00a7f").append(entry.getValue()).append("x ")
               .append(formatMaterialName(entry.getKey()));
            first = false;
        }

        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
            net.md_5.bungee.api.chat.TextComponent.fromLegacy(msg.toString()));
    }

    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        // Capitalize first letter of each word
        StringBuilder result = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!result.isEmpty()) result.append(" ");
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private org.bukkit.Location getTargetLocation(Player player) {
        if (contextLocationKey != null) {
            org.bukkit.Location ctxLoc = quest.getContext(contextLocationKey, org.bukkit.Location.class);
            if (ctxLoc != null) return ctxLoc;
        }

        if (radius > 0) {
            String wName = worldName != null ? worldName : player.getWorld().getName();
            org.bukkit.World world = player.getServer().getWorld(wName);
            if (world == null) return null;
            return new org.bukkit.Location(world, locationX, locationY, locationZ);
        }

        return null;
    }

    private Material parseMaterial(String name) {
        if (name == null) return null;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown material: " + name);
            return null;
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
