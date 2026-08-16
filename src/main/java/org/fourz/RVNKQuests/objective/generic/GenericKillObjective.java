package org.fourz.RVNKQuests.objective.generic;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.factory.QuestComponentFactory;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic kill objective — kill N entities of a specified type.
 *
 * <h3>Config keys:</h3>
 * <ul>
 *   <li>{@code entity_type} — EntityType name (e.g., "ZOMBIE")</li>
 *   <li>{@code custom_name} — Optional: only count kills of entities with this name</li>
 *   <li>{@code required_kills} — Number of kills required (default: 1)</li>
 *   <li>{@code quest_mob_only} — Only count quest-tagged mobs (default: false)</li>
 *   <li>{@code required_state} — QuestState player must be in (default: "QUEST_ACTIVE")</li>
 *   <li>{@code advance_state} — State to advance to on completion (default: "OBJECTIVE_FOUND")</li>
 *   <li>{@code context_key} — Runtime context key for kill count (default: "kill_count")</li>
 *   <li>{@code requires_path} — Only active when player's pathChoice matches (optional)</li>
 *   <li>{@code sets_path} — Sets pathChoice on completion (optional)</li>
 * </ul>
 */
public class GenericKillObjective implements Listener {

    private static final String QUEST_MOB_METADATA = "rvnkquests.questmob";

    private final RVNKQuests plugin;
    private final DataDrivenQuest quest;
    private final LogManager logger;

    private final EntityType entityType;
    private final String customName;
    private final int requiredKills;
    private final boolean questMobOnly;
    private final QuestState requiredState;
    private final QuestState advanceState;
    private final String contextKey;
    private final String requiresPath;
    private final String setsPath;

    private final Map<UUID, Integer> killCounts = new ConcurrentHashMap<>();

    public GenericKillObjective(RVNKQuests plugin, DataDrivenQuest quest, Map<String, Object> config) {
        this.plugin = plugin;
        this.quest = quest;
        this.logger = LogManager.getInstance(plugin, "GenericKillObjective");

        this.entityType = parseEntityType(QuestComponentFactory.getStringConfig(config, "entity_type", "ZOMBIE"));
        this.customName = QuestComponentFactory.getStringConfig(config, "custom_name", null);
        this.requiredKills = QuestComponentFactory.getIntConfig(config, "required_kills", 1);
        this.questMobOnly = QuestComponentFactory.getBoolConfig(config, "quest_mob_only", false);
        this.requiredState = parseState(QuestComponentFactory.getStringConfig(config, "required_state", "QUEST_ACTIVE"));
        this.advanceState = parseState(QuestComponentFactory.getStringConfig(config, "advance_state", "OBJECTIVE_FOUND"));
        this.contextKey = QuestComponentFactory.getStringConfig(config, "context_key", "kill_count");
        this.requiresPath = QuestComponentFactory.getStringConfig(config, "requires_path", null);
        this.setsPath = QuestComponentFactory.getStringConfig(config, "sets_path", null);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        if (quest.getStateForPlayer(killer) != requiredState) return;

        // Check path restriction
        if (requiresPath != null) {
            String playerPath = quest.getPathChoiceCached(killer);
            if (!requiresPath.equals(playerPath)) return;
        }

        // Check entity type
        if (entity.getType() != entityType) return;

        // Check custom name if configured
        if (customName != null) {
            String name = entity.getCustomName();
            if (name == null || !name.contains(customName)) return;
        }

        // Check quest mob tag if required
        if (questMobOnly && !entity.hasMetadata(QUEST_MOB_METADATA)) return;

        // Increment kill count
        UUID playerId = killer.getUniqueId();
        int count = killCounts.merge(playerId, 1, Integer::sum);
        quest.setContext(contextKey, count);

        logger.debug(killer.getName() + " killed " + entityType + " (" + count + "/" + requiredKills + ")");

        if (count >= requiredKills) {
            killCounts.remove(playerId);
            // Set path choice if configured
            if (setsPath != null) {
                quest.setPathChoice(killer, setsPath);
            }
            // Party fan-out (#1982): checkpoint = the final kill's location. baseRadius 0 lets
            // the service's min_share_radius floor govern (default 10 x multiplier 5 = 50 blocks).
            // Kill counts stay PER-PLAYER in v1 — a qualifying member advances state without
            // their own count being complete; pooled party counts are a filed follow-up.
            quest.advanceStateForPlayer(playerId, advanceState,
                    org.fourz.RVNKQuests.party.PartyBeatContext.of(killer.getLocation(), 0.0, requiredState));
        }
    }

    private EntityType parseEntityType(String name) {
        try {
            return EntityType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return EntityType.ZOMBIE;
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
