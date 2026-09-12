package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.util.ServerTier;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * {@code /quest debug session start|end [player]} — brackets a QA run (#2093).
 *
 * <p><b>Dev only.</b> It writes player quest state on {@code end} and raises the log level for the
 * duration.</p>
 *
 * <h2>Why the log level is raised in memory only</h2>
 *
 * <p>{@code /quest debug loglevel} writes to {@code config.yml} and calls {@code saveConfig()}, so
 * a level set that way <b>persists across restarts</b>. Combined with load, DEBUG is an
 * availability risk: it once queued around 150k log lines on RVNK Dev and blocked shutdown for
 * roughly eleven minutes (#1548). A debugging session left switched on stays on.</p>
 *
 * <p>So this command deliberately does <b>not</b> touch config. It calls
 * {@code LogManager.setPluginLogLevel} and nothing else, which means two things: {@code session end}
 * restores the previous level, and a crash or restart mid-session also restores it, because the
 * elevated level was never written anywhere. The config-file guarantee is therefore structural
 * rather than a cleanup step that can be forgotten.</p>
 *
 * <h2>Snapshot and restore</h2>
 *
 * <p>{@code start} records the player's state for every registered quest; {@code end} writes back
 * any that moved, through {@link org.fourz.RVNKQuests.quest.AbstractQuest#restoreStateForPlayer} —
 * a silent write that skips the monotonic guard <b>and</b> every side effect.</p>
 *
 * <p>The obvious choice, {@code setStateForPlayer}, is wrong here and live QA proved it. It is the
 * admin path, so it moves a quest backwards happily, but it still runs {@code performAdvance},
 * which pays rewards and broadcasts to public chat when the target state is {@code COMPLETED}.
 * Rolling a player back onto a quest they had already finished re-granted two items and announced
 * the completion in chat a second time.</p>
 *
 * <p>Rewards already paid <em>during</em> the session are still <b>not</b> reclaimed — a session
 * makes a run repeatable, not reversible, and the report says so on every {@code end}.</p>
 */
public class QuestSessionSubCommand extends BaseSubCommand {

    /** Level a session runs at. DEBUG is the point — the component-side lines from #1930 are debug. */
    private static final String SESSION_LEVEL = "DEBUG";

    /** One bracketed run. */
    private record Session(UUID playerId, String playerName, Map<String, QuestState> states,
                           String priorLevel, long startedAt) {
    }

    /** Active sessions by player. A player can be in at most one. */
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();

    public QuestSessionSubCommand(RVNKQuests plugin) {
        super(plugin, "session", "Bracket a QA run: snapshot state, raise log level, restore both",
                "/quest debug session <start|end|status> [player]", "rvnkquests.admin", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 1)) return true;

        String action = args[0].toLowerCase(Locale.ROOT);

        if (action.equals("status")) {
            return showStatus(sender);
        }

        String tier = ServerTier.resolve();
        if (!ServerTier.isDev(tier)) {
            sendErrorMessage(sender, "Refused: session writes player quest state and raises the log"
                    + " level; it is Dev only. This tier is '" + (tier == null ? "unknown" : tier) + "'.");
            sendMessage(sender, "&7  Read-only alternatives that run anywhere: &f/quest debug preflight"
                    + "&7, &f/quest debug coords&7, &f/quest debug drift");
            return true;
        }

        Player target = resolveTarget(sender, args.length > 1 ? args[1] : null);
        if (target == null) return true;

        return switch (action) {
            case "start" -> start(sender, target);
            case "end", "stop" -> end(sender, target);
            default -> {
                sendErrorMessage(sender, "Unknown action: " + action);
                sendMessage(sender, "&7Usage: &f/quest debug session <start|end|status> [player]");
                yield true;
            }
        };
    }

    // ── start ───────────────────────────────────────────────────────────────────

    private boolean start(CommandSender sender, Player target) {
        UUID id = target.getUniqueId();
        if (sessions.containsKey(id)) {
            Session existing = sessions.get(id);
            sendErrorMessage(sender, target.getName() + " is already in a session (started "
                    + ((System.currentTimeMillis() - existing.startedAt()) / 1000) + "s ago).");
            sendMessage(sender, "&7  End it first: &f/quest debug session end " + target.getName());
            return true;
        }

        List<Quest> quests = plugin.getQuestManager().getAllQuests();
        if (quests.isEmpty()) {
            sendErrorMessage(sender, "No quests registered - nothing to snapshot.");
            return true;
        }

        // Read every state before touching the log level, so the snapshot is of the pre-session
        // world. States come from the async path; render once they have all landed.
        Map<String, QuestState> snapshot = new LinkedHashMap<>();
        List<CompletableFuture<Void>> reads = new ArrayList<>();
        for (Quest quest : quests) {
            reads.add(quest.getStateForPlayer(id)
                    .thenAccept(state -> snapshot.put(quest.getId(), state)));
        }

        CompletableFuture.allOf(reads.toArray(new CompletableFuture[0]))
            .whenComplete((v, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (ex != null) {
                    sendErrorMessage(sender, "Snapshot failed, session NOT started: " + ex);
                    return;
                }

                String prior = plugin.getConfig().getString("general.logLevel", "INFO");
                // In-memory only — see the class note. Nothing is written to config.yml, so this
                // cannot survive a restart and cannot be forgotten into next week.
                LogManager.setPluginLogLevel(plugin, LogManager.parseLevel(SESSION_LEVEL));

                sessions.put(id, new Session(id, target.getName(), snapshot, prior,
                        System.currentTimeMillis()));

                sendMessage(sender, "&6=== session start &f" + target.getName() + " &6===");
                sendMessage(sender, "&7  snapshot  &f" + snapshot.size() + " quest states recorded");
                long nonDefault = snapshot.values().stream()
                        .filter(s -> s != QuestState.NOT_STARTED).count();
                sendMessage(sender, "&7  in flight &f" + nonDefault
                        + " &7not at NOT_STARTED" + (nonDefault == 0 ? " (clean slate)" : ""));
                sendMessage(sender, "&7  log level &f" + prior + " &8-> &f" + SESSION_LEVEL
                        + " &7(memory only, config.yml untouched)");
                sendMessage(sender, "&a✓ Session running. End with &f/quest debug session end "
                        + target.getName());
                sendMessage(sender, "&7  Rewards paid during the session are NOT reclaimed on end,"
                        + " but the rollback itself fires nothing.");
            }));

        return true;
    }

    // ── end ─────────────────────────────────────────────────────────────────────

    private boolean end(CommandSender sender, Player target) {
        UUID id = target.getUniqueId();
        Session session = sessions.remove(id);
        if (session == null) {
            sendErrorMessage(sender, target.getName() + " is not in a session.");
            return true;
        }

        // Restore the log level first. If a state write then fails, the level is already back —
        // the opposite order leaves DEBUG on precisely when something has gone wrong and the
        // operator's attention is elsewhere.
        LogManager.setPluginLogLevel(plugin, LogManager.parseLevel(session.priorLevel()));

        sendMessage(sender, "&6=== session end &f" + session.playerName() + " &6===");
        sendMessage(sender, "&7  duration  &f"
                + ((System.currentTimeMillis() - session.startedAt()) / 1000) + "s");
        sendMessage(sender, "&7  log level &f" + SESSION_LEVEL + " &8-> &f" + session.priorLevel());

        List<CompletableFuture<Void>> restores = new ArrayList<>();
        List<String> moved = new ArrayList<>();

        for (Map.Entry<String, QuestState> entry : session.states().entrySet()) {
            Quest quest = plugin.getQuestManager().getQuest(entry.getKey()).orElse(null);
            if (quest == null) {
                // A quest unregistered mid-session (a reload, an import) cannot be restored.
                moved.add("&8  " + entry.getKey() + " - no longer registered, not restored");
                continue;
            }
            QuestState want = entry.getValue();
            restores.add(quest.getStateForPlayer(id).thenCompose(now -> {
                if (now == want) {
                    return CompletableFuture.completedFuture(null);
                }
                moved.add("&7  " + entry.getKey() + " &f" + now + " &8-> &f" + want);
                // restoreStateForPlayer, not setStateForPlayer. Both skip the monotonic guard so a
                // backwards restore lands, but setStateForPlayer still runs performAdvance, which
                // fires every completion side effect when the target is COMPLETED.
                //
                // Found in live QA: rolling a player back to a quest they had already completed
                // re-paid its rewards and re-broadcast the completion to public chat. A rollback
                // that pays out and announces itself is not a rollback.
                if (quest instanceof org.fourz.RVNKQuests.quest.AbstractQuest aq) {
                    return aq.restoreStateForPlayer(id, want);
                }
                return quest.setStateForPlayer(id, want);
            }));
        }

        CompletableFuture.allOf(restores.toArray(new CompletableFuture[0]))
            .whenComplete((v, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (moved.isEmpty()) {
                    sendMessage(sender, "&7  states    &fno change during the session");
                } else {
                    sendMessage(sender, "&7  restored  &f" + moved.size() + " quest state(s):");
                    for (String line : moved) sendMessage(sender, line);
                }
                if (ex != null) {
                    sendErrorMessage(sender, "One or more restores failed: " + ex);
                    sendMessage(sender, "&7  Check with &f/quest debug player " + session.playerName());
                    return;
                }
                sendSuccessMessage(sender, "Session ended for " + session.playerName() + ".");
                sendMessage(sender, "&7  States were restored silently - the rollback fired no"
                        + " rewards, notifications, broadcasts or journal entries.");
                sendMessage(sender, "&7  Rewards paid DURING the session are still NOT reclaimed -"
                        + " items, xp and journal entries from the run remain.");
            }));

        return true;
    }

    // ── status ──────────────────────────────────────────────────────────────────

    private boolean showStatus(CommandSender sender) {
        sendMessage(sender, "&6=== session status ===");
        String configured = plugin.getConfig().getString("general.logLevel", "INFO");
        Level effective = LogManager.getInstance(plugin, getClass()).getLogLevel();
        sendMessage(sender, "&7  config.yml log level &f" + configured);
        sendMessage(sender, "&7  effective log level  &f" + effective);
        if (!configured.equalsIgnoreCase(String.valueOf(effective))) {
            sendMessage(sender, "&e  These differ - a session is raising it in memory,"
                    + " or loglevel was changed without saving.");
        }

        if (sessions.isEmpty()) {
            sendMessage(sender, "&7No sessions running.");
            return true;
        }
        for (Session s : sessions.values()) {
            sendMessage(sender, "&7  &f" + s.playerName() + " &7- "
                    + ((System.currentTimeMillis() - s.startedAt()) / 1000) + "s, "
                    + s.states().size() + " states snapshotted, restores to " + s.priorLevel());
        }
        return true;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Resolves the target, defaulting to the sender when they are a player.
     *
     * <p>Console must name a player: a session snapshots one player's states, and there is no
     * sensible default for a sender that has none.</p>
     */
    private Player resolveTarget(CommandSender sender, String name) {
        if (name != null) {
            Player target = Bukkit.getPlayerExact(name);
            if (target == null) {
                sendErrorMessage(sender, "Player not online: " + name);
                return null;
            }
            return target;
        }
        if (sender instanceof Player self) {
            return self;
        }
        sendErrorMessage(sender, "From console you must name the player:"
                + " /quest debug session <start|end> <player>");
        return null;
    }

    /** Ends every session, restoring the log level. Called on plugin disable. */
    public void shutdown() {
        for (Session s : sessions.values()) {
            LogManager.setPluginLogLevel(plugin, LogManager.parseLevel(s.priorLevel()));
        }
        sessions.clear();
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length <= 1) {
            String partial = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            for (String action : List.of("start", "end", "status")) {
                if (action.startsWith(partial)) out.add(action);
            }
            return out;
        }
        if (args.length == 2) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(partial)) out.add(p.getName());
            }
        }
        return out;
    }

    @Override
    public List<String> getExamples() {
        return List.of(
                "/quest debug session start Shad0melt",
                "/quest debug session status",
                "/quest debug session end Shad0melt",
                "  Dev only. Snapshots every quest state, raises the log level to DEBUG,",
                "  restores both on end.",
                "  The level is raised IN MEMORY - config.yml is never written, so a",
                "  crash or restart mid-session also reverts it. That is the #1548 guard.",
                "  Rewards already paid are NOT reclaimed: repeatable, not reversible.");
    }
}
