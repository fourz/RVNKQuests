package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.util.QuestTrace;
import org.fourz.RVNKQuests.util.ServerTier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /quest debug trace <player>} — live stream of one player's quest state decisions (#2093).
 *
 * <p><b>Dev only.</b> It does not itself mutate anything, but it attaches a sink inside the write
 * chain that serialises every state change, so it is treated as the mutating class of command
 * rather than the read-only one.</p>
 *
 * <h2>The problem it solves</h2>
 *
 * <p>{@code AbstractQuest.applyStateChange} has five exits and only one advances the quest. The
 * other four complete the future <b>normally</b>, so from outside, a silently-dropped advance and a
 * successful one look identical. Each exit does log, but at {@code debug} and interleaved with
 * every other player's traffic.</p>
 *
 * <p>#1853 was diagnosed by inference from two log lines plus the observation that the same input
 * produced different outcomes. This makes it a readout instead: one line per decision, for one
 * player, naming the exit and the reason.</p>
 *
 * <h2>What it covers, and what it does not</h2>
 *
 * <p>Every decision the state machine makes is covered: {@code ADVANCED}, {@code already},
 * {@code not-forward}, {@code party-skip} and {@code prereq-block}. A positional component is
 * identified by the checkpoint it carried, because {@code advanceStateForPlayer} never receives a
 * component id — coordinates are the only attribution available at this layer.</p>
 *
 * <p>What it cannot show is a component that <b>never called in at all</b>: an unregistered
 * listener, a radius the player never entered, an event that did not fire. Silence here means "the
 * state machine was not asked", which is a real and useful answer, but distinguishing "not asked"
 * from "asked and refused" is exactly the line this draws. For the component-side half, the
 * out-of-order call sites log their own decision at debug (#1930) — {@code session} raises the log
 * level for you.</p>
 */
public class QuestTraceSubCommand extends BaseSubCommand {

    public QuestTraceSubCommand(RVNKQuests plugin) {
        super(plugin, "trace", "Stream one player's quest state decisions (Dev only)",
                "/quest debug trace <player|off|status>", "rvnkquests.admin", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 1)) return true;

        String arg = args[0];

        if (arg.equalsIgnoreCase("status")) {
            return showStatus(sender);
        }

        String tier = ServerTier.resolve();
        if (!ServerTier.isDev(tier)) {
            sendErrorMessage(sender, "Refused: trace attaches a sink inside the quest write chain"
                    + " and is Dev only. This tier is '" + (tier == null ? "unknown" : tier) + "'.");
            sendMessage(sender, "&7  Read-only alternatives that run anywhere: &f/quest debug preflight"
                    + "&7, &f/quest debug coords&7, &f/quest debug drift");
            return true;
        }

        if (arg.equalsIgnoreCase("off")) {
            return stopAll(sender);
        }

        Player target = Bukkit.getPlayerExact(arg);
        if (target == null) {
            // An offline player cannot generate a decision, so tracing one is always a mistake
            // rather than a valid pre-arm. Say so instead of accepting a trace that can never fire.
            sendErrorMessage(sender, "Player not online: " + arg);
            sendMessage(sender, "&7  An offline player generates no state changes - nothing to trace.");
            return true;
        }

        UUID id = target.getUniqueId();
        boolean replacing = QuestTrace.isTracing(id);

        QuestTrace.subscribe(id, event -> {
            // Emissions arrive on the async write-chain pool. Hop before touching the Bukkit API:
            // sendMessage to a Player from an arbitrary thread is not safe.
            Bukkit.getScheduler().runTask(plugin, () -> sendMessage(sender, "&8[trace] " + event.render()));
        });

        if (replacing) {
            sendMessage(sender, "&eReplaced the existing trace on " + target.getName() + ".");
        }
        sendSuccessMessage(sender, "Tracing " + target.getName() + ". Decisions stream here.");
        sendMessage(sender, "&7  Stop with &f/quest debug trace off");
        sendMessage(sender, "&7  Silence means the state machine was not asked - an unregistered");
        sendMessage(sender, "&7  listener or a radius never entered shows nothing here.");
        sendMessage(sender, "&7  For the component half, raise the log level:"
                + " &f/quest debug session start " + target.getName());
        return true;
    }

    private boolean showStatus(CommandSender sender) {
        sendMessage(sender, "&6=== trace status ===");
        if (!QuestTrace.isActive()) {
            sendMessage(sender, "&7No traces running.");
            return true;
        }
        for (UUID id : QuestTrace.traced()) {
            Player p = Bukkit.getPlayer(id);
            sendMessage(sender, "&7  &f" + (p != null ? p.getName() : id.toString())
                    + (p == null ? " &8(offline - will produce nothing)" : ""));
        }
        return true;
    }

    private boolean stopAll(CommandSender sender) {
        int stopped = QuestTrace.traced().size();
        QuestTrace.clear();
        if (stopped == 0) {
            sendMessage(sender, "&7No traces were running.");
        } else {
            sendSuccessMessage(sender, "Stopped " + stopped + " trace(s).");
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length <= 1) {
            String partial = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            for (String fixed : List.of("off", "status")) {
                if (fixed.startsWith(partial)) out.add(fixed);
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(partial)) out.add(p.getName());
            }
        }
        return out;
    }

    @Override
    public List<String> getExamples() {
        return List.of(
                "/quest debug trace Shad0melt",
                "/quest debug trace status",
                "/quest debug trace off",
                "  Dev only. Shows WHICH of the five state-machine exits was taken:",
                "  ADVANCED / already / not-forward / party-skip / prereq-block",
                "  four of those five complete normally, so without this they look",
                "  identical to a successful advance from the outside",
                "  silence = the state machine was never asked (listener or radius problem)");
    }
}
