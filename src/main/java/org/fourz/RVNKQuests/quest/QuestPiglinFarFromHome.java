package org.fourz.RVNKQuests.quest;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.RVNKQuests;

import java.util.*;

/**
 * EXAMPLE: Hardcoded quest class demonstrating path-choice branching.
 *
 * <p>This class is kept as a reference for how to extend the data-driven quest
 * system when a quest requires runtime branching logic that cannot be expressed
 * purely via metadata. The data-driven version (seeded as "piglin_far_from_home"
 * in QuestDefinitionSeeder) handles the standard quest flow; this class shows
 * the COMBAT_PATH vs ESCORT_PATH pattern as an extension point.</p>
 *
 * <h3>When to use hardcoded quest classes:</h3>
 * <ul>
 *   <li>Player-driven path branching (different objectives based on player action)</li>
 *   <li>Complex inter-component state (one objective's outcome affects another)</li>
 *   <li>Custom game mechanics not covered by generic objectives</li>
 * </ul>
 *
 * <p><strong>NOTE:</strong> This class is NOT registered by QuestManager when
 * the data-driven version exists in the repository. It serves as documentation
 * of the branching pattern only.</p>
 */
public class QuestPiglinFarFromHome extends AbstractQuest {

    private final Map<UUID, QuestPath> playerPaths = new HashMap<>();

    /**
     * Path choices available to the player. The quest branches when the player
     * either kills the quest NPC (COMBAT_PATH) or interacts with it (ESCORT_PATH).
     *
     * <p>In a data-driven implementation, this could be expressed via:</p>
     * <ul>
     *   <li>A "path_choice" field in QuestProgressDTO (already supported)</li>
     *   <li>Conditional components in state_mapping based on path_choice</li>
     *   <li>A future GenericBranchObjective that reads conditions from metadata</li>
     * </ul>
     */
    public enum QuestPath {
        /** Player killed the quest NPC — proceeds through combat objectives. */
        COMBAT_PATH,
        /** Player escorted the quest NPC — proceeds through escort objectives. */
        ESCORT_PATH
    }

    public QuestPiglinFarFromHome(RVNKQuests plugin) {
        super(plugin, "piglin_far_from_home", "Piglin Far From Home");
    }

    @Override
    public void initialize() {
        logger.debug("QuestPiglinFarFromHome initialized (branching example — not actively registered)");
    }

    @Override
    public void cleanup() {
        playerPaths.clear();
    }

    /**
     * BRANCHING PATTERN: Set the quest path for a player based on their action.
     *
     * <p>Called by the objective listener that detects the player's choice.
     * For example, a kill listener sets COMBAT_PATH, while an interact/escort
     * listener sets ESCORT_PATH.</p>
     */
    public void setPlayerPath(Player player, QuestPath path) {
        playerPaths.put(player.getUniqueId(), path);
        // Also persist to DB via the pathChoice field on QuestProgressDTO
        setPathChoice(player, path.name());
        logger.debug(player.getName() + " chose " + path);
    }

    public QuestPath getPlayerPath(Player player) {
        return playerPaths.getOrDefault(player.getUniqueId(), QuestPath.COMBAT_PATH);
    }

    /**
     * BRANCHING PATTERN: Different listeners per state AND per path.
     *
     * <p>This is the key extension point that data-driven quests cannot express yet.
     * The state_mapping in metadata maps states → component IDs, but cannot
     * conditionally include components based on runtime player choices.</p>
     *
     * <p>Example of what a branching state mapping would look like if supported:</p>
     * <pre>
     * "state_mapping": {
     *   "TRIGGER_FOUND": {
     *     "always": ["obj_kill_npc"],
     *     "if_path:ESCORT_PATH": ["obj_escort_npc"]
     *   },
     *   "QUEST_ACTIVE": {
     *     "always": ["obj_portal_encounter"],
     *     "if_path:ESCORT_PATH": ["obj_escort_npc"]
     *   }
     * }
     * </pre>
     */
    @Override
    public List<Listener> createListenersForState(QuestState state) {
        // Intentionally empty — this class is a documentation/example skeleton.
        // The data-driven version (quest ID "piglin_far_from_home") handles
        // the actual quest flow via QuestDefinitionSeeder metadata.
        return List.of();
    }

    @Override
    public Location getStartLocation() {
        return null;
    }

    @Override
    public String getStartTrigger() {
        return "GrotSnout da Lost";
    }

    @Override
    protected boolean onStart(Player player) {
        return true;
    }

    @Override
    protected boolean onComplete(Player player) {
        return true;
    }

    @Override
    public boolean update(Player player) {
        return false;
    }
}
