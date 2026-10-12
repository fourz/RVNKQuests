package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestManager;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.waypoint.TrackRequest;
import org.fourz.RVNKQuests.waypoint.WaypointService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /quest track} (#2264): pick the one quest whose waypoint a player sees.
 *
 * <pre>
 * /quest track                          what is tracked
 * /quest track &lt;quest_id&gt; [--trail]     track a started quest; --trail shows a 5 s particle line
 * /quest track off                      stop tracking
 * ... [player]                          staff/console: act for another player (rvnkquests.admin)
 * </pre>
 *
 * <p>Only a quest the player has started (TRIGGER_FOUND, QUEST_ACTIVE or OBJECTIVE_FOUND) and that
 * has at least one waypoint can be tracked, so tracking never reveals a place the player has not
 * reached in the story.</p>
 */
public class QuestTrackSubCommand extends BaseSubCommand {

    private static final String USAGE = "/quest track [<quest_id> [--trail] | off] [player]";

    public QuestTrackSubCommand(RVNKQuests plugin) {
        super(plugin, "track", "Point a waypoint at a started quest's next objective", USAGE,
            "rvnkquests.track", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        WaypointService svc = plugin.getWaypointService();
        if (svc == null) {
            sendErrorMessage(sender, "Waypoints are not running on this server.");
            return true;
        }
        TrackRequest req = TrackRequest.parse(args);
        if (req.isError()) {
            sendErrorMessage(sender, req.error());
            sendMessage(sender, "&7Usage: " + USAGE);
            return true;
        }

        Player target = resolveTarget(sender, req.player());
        if (target == null) return true;
        boolean self = target == sender;
        String who = self ? "You" : target.getName();

        switch (req.action()) {
            case SHOW -> {
                String tracked = svc.getTracked(target.getUniqueId());
                if (tracked == null) {
                    sendMessage(sender, "&7" + who + (self ? " are" : " is") + " not tracking a quest. &8/quest track <quest_id>");
                } else {
                    sendMessage(sender, "&7Tracking: &e" + displayName(tracked) + " &8(" + tracked + ")"
                        + (svc.isEnabled(target.getUniqueId()) ? "" : " &c- waypoints are off (/quest prefs waypoints on)"));
                }
            }
            case OFF -> {
                svc.untrack(target.getUniqueId());
                sendSuccessMessage(sender, (self ? "Stopped" : "Stopped " + target.getName() + "'s") + " quest tracking.");
            }
            case TRACK -> track(sender, target, self, req, svc);
        }
        return true;
    }

    private void track(CommandSender sender, Player target, boolean self, TrackRequest req, WaypointService svc) {
        DataDrivenQuest quest = findQuest(req.questId());
        if (quest == null) {
            sendErrorMessage(sender, "Unknown quest: " + req.questId());
            return;
        }
        if (!quest.hasWaypoints()) {
            sendErrorMessage(sender, quest.getName() + " has no waypoints to track.");
            return;
        }
        String questId = quest.getId();
        quest.getStateForPlayer(target.getUniqueId()).whenComplete((state, ex) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!target.isOnline()) return;
                if (ex != null) {
                    sendErrorMessage(sender, "Could not read quest state: " + ex.getMessage());
                    return;
                }
                if (!isStarted(state)) {
                    sendErrorMessage(sender, (self ? "You have" : target.getName() + " has") + " not started "
                        + quest.getName() + (state == QuestState.COMPLETED ? " (already completed)." : "."));
                    return;
                }
                svc.track(target, questId, req.trail());
                sendSuccessMessage(sender, (self ? "Tracking " : target.getName() + " is tracking ") + quest.getName() + "."
                    + (req.trail() ? " Trail shown for 5 seconds." : ""));
                if (!svc.isEnabled(target.getUniqueId())) {
                    sendMessage(sender, "&cWaypoints are off. &7Turn them on with &f/quest prefs waypoints on");
                }
                if (!svc.isPersistent()) {
                    sendMessage(sender, "&8(Tracking is not saved on this server - it resets on relog.)");
                }
            }));
    }

    /** TRIGGER_FOUND, QUEST_ACTIVE or OBJECTIVE_FOUND. */
    static boolean isStarted(QuestState state) {
        return state == QuestState.TRIGGER_FOUND || state == QuestState.QUEST_ACTIVE
            || state == QuestState.OBJECTIVE_FOUND;
    }

    private Player resolveTarget(CommandSender sender, String name) {
        if (name == null) {
            if (sender instanceof Player p) return p;
            sendErrorMessage(sender, "Console must name a player: " + USAGE);
            return null;
        }
        if (sender instanceof Player p && p.getName().equalsIgnoreCase(name)) return p;
        if (!sender.hasPermission("rvnkquests.admin")) {
            sendErrorMessage(sender, "You can only change your own tracking.");
            return null;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            sendErrorMessage(sender, "Player not online: " + name);
        }
        return target;
    }

    private DataDrivenQuest findQuest(String id) {
        QuestManager qm = plugin.getQuestManager();
        if (qm == null || id == null) return null;
        Quest q = qm.findQuestQuietly(id);
        if (q == null) {
            for (String known : qm.getQuestIds()) {
                if (known.equalsIgnoreCase(id)) {
                    q = qm.findQuestQuietly(known);
                    break;
                }
            }
        }
        return q instanceof DataDrivenQuest d ? d : null;
    }

    private String displayName(String questId) {
        DataDrivenQuest q = findQuest(questId);
        return q != null ? q.getName() : questId;
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            if ("off".startsWith(partial)) out.add("off");
            QuestManager qm = plugin.getQuestManager();
            if (qm == null) return out;
            for (Quest q : qm.getAllQuests()) {
                if (!(q instanceof DataDrivenQuest d) || !d.hasWaypoints()) continue;
                // A player sees only quests they have started (cached state; no database read here).
                if (sender instanceof Player p && !(d.isStateCached(p) && isStarted(d.getStateForPlayer(p)))) continue;
                if (d.getId().toLowerCase(Locale.ROOT).startsWith(partial)) out.add(d.getId());
            }
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            if (!"off".equalsIgnoreCase(args[0]) && TrackRequest.TRAIL_FLAG.startsWith(partial)) {
                out.add(TrackRequest.TRAIL_FLAG);
            }
            if (sender.hasPermission("rvnkquests.admin")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(partial)) out.add(p.getName());
                }
            }
        }
        return out;
    }

    @Override
    public List<String> getExamples() {
        return List.of(
            "/quest track",
            "  show the quest you are tracking",
            "/quest track tfah_worldforge_aether",
            "  bossbar: label, distance and an arrow to the next objective",
            "/quest track tfah_worldforge_aether --trail",
            "  also a short particle line toward it for 5 seconds",
            "/quest track off",
            "/quest prefs waypoints off",
            "  hide every waypoint without losing the tracked quest");
    }
}
