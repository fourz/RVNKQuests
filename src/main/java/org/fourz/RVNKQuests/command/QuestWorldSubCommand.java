package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.integration.WorldActivationService;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestWorldRequirements;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /quest world <list|ensure> <quest>} — inspect and repair a quest's world requirements
 * (#1877).
 *
 * <p>Worlds are activated automatically at quest load/reload, so this command exists for the two
 * cases that automation does not cover:</p>
 *
 * <ul>
 *   <li><b>ensure</b> — a world was reclaimed or unloaded mid-session and the operator wants it
 *       back without reloading every quest. {@code /quest reload} would work, but it re-reads and
 *       re-registers the whole catalogue to fix one world.</li>
 *   <li><b>list</b> — authoring. {@code required_worlds} is the only declaration that drives
 *       preloading, and a component pointing at an undeclared world is invisible until a player
 *       cannot reach it. This prints declared, referenced and the drift between them side by side
 *       so the gap is obvious before it ships.</li>
 * </ul>
 *
 * <h2>Drift is the point</h2>
 *
 * <p>Two directions, and they fail differently. A world <b>referenced but not declared</b> is the
 * dangerous one — it is never preloaded, so the quest sits dormant exactly the way the Tales From
 * A Hat chain did while {@code quest validate} still reported it {@code [VALID]} (#1874). A world
 * <b>declared but not referenced</b> is only untidy, but it pins a world in memory for no reason,
 * so it is worth naming rather than ignoring.</p>
 *
 * @since 1.1.36
 */
public class QuestWorldSubCommand extends BaseSubCommand {

    public QuestWorldSubCommand(RVNKQuests plugin) {
        super(plugin, "world", "Inspect or repair a quest's required worlds",
                "/quest world <list|ensure> <quest>", "rvnkquests.admin", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 2)) return true;

        String action = args[0].toLowerCase(Locale.ROOT);
        String questId = args[1];

        Optional<Quest> found = plugin.getQuestManager().getQuest(questId);
        if (found.isEmpty()) {
            sendMessage(sender, "&cNo quest registered with id '" + questId + "'");
            return true;
        }
        if (!(found.get() instanceof DataDrivenQuest quest)) {
            sendMessage(sender, "&cQuest '" + questId + "' is not data-driven - it declares no worlds.");
            return true;
        }

        Map<String, Object> metadata = quest.getDefinition().metadata();

        switch (action) {
            case "list" -> listWorlds(sender, questId, metadata);
            case "ensure" -> ensureWorlds(sender, questId, metadata);
            default -> sendMessage(sender, "&cUnknown action '" + action + "'. Use &elist&c or &eensure&c.");
        }
        return true;
    }

    /** Declared / referenced / drift, plus the live state of each world. */
    private void listWorlds(CommandSender sender, String questId, Map<String, Object> metadata) {
        Set<String> declared = QuestWorldRequirements.declared(metadata);
        Set<String> referenced = QuestWorldRequirements.referenced(metadata);
        Set<String> undeclared = QuestWorldRequirements.undeclared(metadata);
        Set<String> unused = QuestWorldRequirements.unusedDeclarations(metadata);

        WorldActivationService worlds = plugin.getWorldActivation();
        boolean canRead = worlds != null && worlds.isAvailable();

        sendMessage(sender, "&6=== worlds for " + questId + " ===");

        if (declared.isEmpty() && referenced.isEmpty()) {
            sendMessage(sender, "&7  This quest names no worlds at all.");
            return;
        }

        Set<String> all = new LinkedHashSet<>();
        all.addAll(declared);
        all.addAll(referenced);

        for (String world : all) {
            boolean isDeclared = declared.contains(world);
            boolean isReferenced = referenced.contains(world);

            String origin;
            if (isDeclared && isReferenced) {
                origin = "&adeclared+used";
            } else if (isDeclared) {
                origin = "&edeclared, unused";
            } else {
                origin = "&cused, NOT declared";
            }

            // Say "unknown" rather than "inactive" when RVNKWorlds is absent. Reporting a world as
            // down because we cannot see it is the same mistake preflight's UNVERIFIED tier exists
            // to avoid - it condemns content that may be perfectly healthy.
            String state = !canRead ? "&7state unknown (RVNKWorlds unavailable)"
                    : worlds.isActive(world) ? "&aACTIVE" : "&cnot active";

            sendMessage(sender, "&7  " + world + " &8- " + origin + " &8- " + state);
        }

        if (!undeclared.isEmpty()) {
            sendMessage(sender, "&c  drift: " + undeclared.size() + " world(s) used but not declared"
                    + " - add to &erequired_worlds&c or they will never be preloaded:");
            sendMessage(sender, "&c    " + String.join(", ", undeclared));
        }
        if (!unused.isEmpty()) {
            sendMessage(sender, "&e  drift: " + unused.size() + " world(s) declared but unused"
                    + " - each is held in memory for nothing: " + String.join(", ", unused));
        }
        if (undeclared.isEmpty() && unused.isEmpty()) {
            sendMessage(sender, "&a  no drift - declarations match usage.");
        }
    }

    /** Activate the quest's declared worlds and report each outcome. */
    private void ensureWorlds(CommandSender sender, String questId, Map<String, Object> metadata) {
        WorldActivationService worlds = plugin.getWorldActivation();
        if (worlds == null || !worlds.isAvailable()) {
            // Absent RVNKWorlds is a no-op by design (#1875) - say so plainly rather than
            // reporting a failure the operator cannot act on.
            sendMessage(sender, "&cRVNKWorlds is not available - cannot activate worlds."
                    + " Quest worlds are managed by RVNKWorlds; nothing was changed.");
            return;
        }

        Set<String> toActivate = QuestWorldRequirements.toActivate(metadata);
        if (toActivate.isEmpty()) {
            Set<String> undeclared = QuestWorldRequirements.undeclared(metadata);
            if (undeclared.isEmpty()) {
                sendMessage(sender, "&7" + questId + " declares no worlds - nothing to ensure.");
            } else {
                // The actionable case: the quest DOES use worlds, it just never declared them, so
                // preloading has nothing to act on. Point at the fix rather than reporting success.
                sendMessage(sender, "&e" + questId + " declares no worlds, but uses "
                        + undeclared.size() + ": " + String.join(", ", undeclared));
                sendMessage(sender, "&e  Add them to &frequired_worlds&e - ensure only activates"
                        + " declared worlds, by design.");
            }
            return;
        }

        sendMessage(sender, "&7Ensuring " + toActivate.size() + " world(s) for " + questId + "...");

        List<CompletableFuture<String>> results = new ArrayList<>();
        for (String world : toActivate) {
            boolean wasActive = worlds.isActive(world);
            results.add(worlds.ensureActive(world)
                    .thenApply(ok -> {
                        if (!ok) return "&c  " + world + " - FAILED to activate";
                        return wasActive
                                ? "&a  " + world + " - already active (hold refreshed)"
                                : "&a  " + world + " - activated";
                    })
                    // A future that completes exceptionally would otherwise swallow the whole
                    // report via allOf, leaving the operator with the "Ensuring..." line and
                    // nothing after it.
                    .exceptionally(ex -> "&c  " + world + " - error: " + rootMessage(ex)));
        }

        CompletableFuture
                .allOf(results.toArray(new CompletableFuture[0]))
                // Back to the main thread before touching the sender: world activation completes
                // on whichever thread RVNKWorlds finishes on.
                .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                    for (CompletableFuture<String> r : results) {
                        sendMessage(sender, r.join());
                    }
                    sendMessage(sender, "&7Done. Holds are released on reload or when the quest"
                            + " no longer declares the world.");
                }));
    }

    private static String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) cause = cause.getCause();
        String msg = cause.getMessage();
        return msg == null ? cause.getClass().getSimpleName() : msg;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("list", "ensure");
        }
        if (args.length == 2) {
            List<String> ids = new ArrayList<>();
            for (Quest q : plugin.getQuestManager().getAllQuests()) {
                ids.add(q.getId());
            }
            return ids;
        }
        return List.of();
    }

    /**
     * Worked examples for {@code /quest help world} (#1981).
     */
    @Override
    public java.util.List<String> getExamples() {
        return java.util.List.of(
                "/quest world list tfah_ch1_journey",
                "  which worlds the quest's components need",
                "/quest world ensure tfah_ch1_journey",
                "  load any world the quest needs that is not active");
    }
}
