package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.party.PartyBeatContext;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.util.ServerTier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /quest debug fire <quest> <component> <player>} — exercise one component's advance from
 * console, without walking or teleporting (#2093).
 *
 * <p><b>Mutating, and Dev only.</b> It writes real quest state and can deliver real rewards, so the
 * tier gate refuses outright rather than asking for a {@code --force} the way {@code preflight}
 * does. {@code preflight} only loads a chunk and puts it back; this pays a player.</p>
 *
 * <h2>What it does and does not prove</h2>
 *
 * <p>It replays the <b>advance</b> a component would perform: the same
 * {@code advanceStateForPlayer} call with the same {@link PartyBeatContext} the component would
 * build from its own config. So it exercises the state machine, the monotonic guard, the
 * prerequisite gate, persistence, the journal, party fan-out and completion side-effects — the
 * whole path behind the component.</p>
 *
 * <p>It does <b>not</b> exercise the component's own detection. Whether a lectern is really a
 * lectern, whether a radius is reachable, whether an event listener is registered at all — none of
 * that is touched here. {@code preflight} checks the first, {@code coords} the second. Firing a
 * component whose block is missing will still succeed, and that is not a bug in either command:
 * they answer different questions.</p>
 *
 * <p>This is what makes the #1853 repro runnable from console. Two co-located components can be
 * fired back to back for the same player with no live player and no teleport, which is the part of
 * that investigation that previously needed a body in the world.</p>
 */
public class QuestFireSubCommand extends BaseSubCommand {

    public QuestFireSubCommand(RVNKQuests plugin) {
        super(plugin, "fire", "Exercise one component's advance for a player (Dev only)",
                "/quest debug fire <quest> <component> <player>", "rvnkquests.admin", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        String tier = ServerTier.resolve();
        if (!ServerTier.isDev(tier)) {
            // Refuse, and say which tier, so a correctly-gated production run is distinguishable
            // from a Dev box whose identity failed to resolve.
            sendErrorMessage(sender, "Refused: fire mutates player quest state and is Dev only."
                    + " This tier is '" + (tier == null ? "unknown" : tier) + "'.");
            sendMessage(sender, "&7  Read-only alternatives that run anywhere: &f/quest debug preflight"
                    + "&7, &f/quest debug coords&7, &f/quest debug drift");
            return true;
        }

        if (!validateArgs(sender, args, 3)) return true;

        String questId = args[0];
        String componentId = args[1];
        String playerName = args[2];

        Optional<Quest> found = plugin.getQuestManager().getQuest(questId);
        if (found.isEmpty()) {
            sendErrorMessage(sender, "No quest registered with id '" + questId + "'.");
            return true;
        }
        if (!(found.get() instanceof DataDrivenQuest quest)) {
            sendErrorMessage(sender, "Quest '" + questId + "' is not data-driven; it has no components.");
            return true;
        }

        // Component before player, deliberately. The component is a static question about the
        // definition and the player is a runtime one, so checking config first means the whole
        // argument-validation path can be exercised from console with nobody online — which is the
        // situation these tools exist for. Player-first made "no such component" unreachable
        // without a body in the world.
        Map<?, ?> component = findComponent(quest, componentId);
        if (component == null) {
            sendErrorMessage(sender, "Quest '" + questId + "' has no component '" + componentId + "'.");
            List<String> known = componentIds(quest);
            if (!known.isEmpty()) {
                sendMessage(sender, "&7  Components: &f" + String.join(", ", known));
            }
            return true;
        }

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sendErrorMessage(sender, "Player not online: " + playerName);
            sendMessage(sender, "&7  Component '" + componentId + "' resolved - only the player is missing.");
            return true;
        }

        // The bucket state is authoritative at runtime (#1764), so resolve required_state the same
        // way the factory does rather than trusting the component's own value. A component read
        // from its config alone can name a state it will never actually be gated on.
        QuestState required = bucketStateFor(quest, componentId);
        if (required == null) {
            required = parseState(str(component.get("required_state")), QuestState.NOT_STARTED);
            sendMessage(sender, "&e  '" + componentId + "' is in no state_mapping bucket - it never"
                    + " fires at runtime. Using its own required_state (" + required + ") for this call.");
        }
        QuestState advance = parseState(str(component.get("advance_state")), QuestState.TRIGGER_FOUND);

        QuestState before = quest.getStateForPlayer(target);

        sendMessage(sender, "&6=== fire &f" + componentId + " &6===");
        sendMessage(sender, "&7  quest     &f" + questId);
        sendMessage(sender, "&7  player    &f" + target.getName() + " &8(" + target.getWorld().getName() + ")");
        sendMessage(sender, "&7  state     &f" + before + " &8-> &f" + advance
                + " &7(gate requires &f" + required + "&7)");

        if (before != required) {
            // Not refused. A component firing from the wrong state is exactly the case the
            // monotonic guard and the out-of-order feedback exist to handle, and being able to
            // provoke it deliberately is half the point of this command.
            sendMessage(sender, "&e  Player is not at the gate state - the advance will be"
                    + " evaluated and probably rejected. That is a valid thing to test.");
        }

        PartyBeatContext ctx = buildContext(component, required, target);
        if (ctx != null) {
            sendMessage(sender, "&7  checkpoint &f" + ctx.worldName() + " "
                    + (int) ctx.x() + "," + (int) ctx.y() + "," + (int) ctx.z()
                    + " &7r=" + fmt(ctx.baseRadius()) + " &8(party fan-out will be evaluated)");
        } else {
            sendMessage(sender, "&7  checkpoint &8none - non-positional, no party fan-out");
        }

        sendMessage(sender, "&7  Tip: &f/quest debug trace " + target.getName()
                + "&7 in another window shows the gate decision as it happens.");

        quest.advanceStateForPlayer(target.getUniqueId(), advance, ctx)
            .whenComplete((v, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (ex != null) {
                    sendErrorMessage(sender, "Advance failed: " + ex);
                    return;
                }
                // Re-read rather than assume. Four of the five exits in applyStateChange complete
                // normally without changing anything, so "the future completed" is not evidence
                // that the state moved — reporting it as success is the ambiguity trace exists for.
                //
                // Compare against BEFORE, not just against the target. Found in live QA: firing a
                // component at a player already holding its advance_state matched `after == advance`
                // and printed "advanced TRIGGER_FOUND -> TRIGGER_FOUND" for a no-op. That is
                // precisely the false-success this command was written to eliminate, reproduced in
                // the command itself.
                QuestState after = quest.getStateForPlayer(target);
                if (after != before) {
                    sendSuccessMessage(sender, componentId + " advanced " + target.getName()
                            + ": " + before + " -> " + after);
                    if (after != advance) {
                        sendMessage(sender, "&e  Note: landed on " + after + ", not the requested "
                                + advance + ".");
                    }
                    return;
                }

                if (after == advance) {
                    sendMessage(sender, "&e✖ No state change. " + target.getName()
                            + " was ALREADY at " + after
                            + " - side effects were deliberately not re-fired.");
                } else {
                    sendMessage(sender, "&e✖ No state change. " + target.getName() + " is still "
                            + after + " - the advance was evaluated and dropped by a gate.");
                }
                sendMessage(sender, "&7  Run &f/quest debug trace " + target.getName()
                        + "&7 and fire again to see which gate and why.");
            }));

        return true;
    }

    // ── Lookup ──────────────────────────────────────────────────────────────────

    private Map<?, ?> findComponent(DataDrivenQuest quest, String componentId) {
        Object componentsObj = quest.getDefinition().metadata().get("components");
        if (!(componentsObj instanceof Map<?, ?> components)) return null;
        for (Map.Entry<?, ?> e : components.entrySet()) {
            if (String.valueOf(e.getKey()).equalsIgnoreCase(componentId)
                    && e.getValue() instanceof Map<?, ?> config) {
                return config;
            }
        }
        return null;
    }

    private List<String> componentIds(DataDrivenQuest quest) {
        Object componentsObj = quest.getDefinition().metadata().get("components");
        List<String> out = new ArrayList<>();
        if (componentsObj instanceof Map<?, ?> components) {
            for (Object key : components.keySet()) out.add(String.valueOf(key));
        }
        return out;
    }

    /**
     * The {@code state_mapping} bucket this component sits in, which is the state the factory
     * injects as {@code required_state} at runtime (#1764), or null if it is in no bucket.
     */
    private QuestState bucketStateFor(DataDrivenQuest quest, String componentId) {
        Object mappingObj = quest.getDefinition().metadata().get("state_mapping");
        if (!(mappingObj instanceof Map<?, ?> mapping)) return null;
        for (Map.Entry<?, ?> e : mapping.entrySet()) {
            QuestState state = parseState(String.valueOf(e.getKey()), null);
            if (state == null) continue;
            if (e.getValue() instanceof List<?> list) {
                for (Object item : list) {
                    if (String.valueOf(item).equalsIgnoreCase(componentId)) return state;
                }
            } else if (String.valueOf(e.getValue()).equalsIgnoreCase(componentId)) {
                return state;
            }
        }
        return null;
    }

    /**
     * Builds the checkpoint the component itself would carry, so party fan-out is exercised on the
     * same terms as a real fire.
     *
     * <p>Falls back to the player's own location for a component that is world-scoped but not
     * positional — a party sharing an item discovery shares it wherever the holder is standing,
     * which is the rule {@code GenericItemDiscoveryTrigger} already applies with radius 0.</p>
     */
    private PartyBeatContext buildContext(Map<?, ?> component, QuestState required, Player target) {
        String world = str(component.get("world"));
        Double x = dbl(component.get("x"));
        Double y = dbl(component.get("y"));
        Double z = dbl(component.get("z"));
        double radius = dbl(component.get("radius")) == null ? 0.0 : dbl(component.get("radius"));

        if (world != null && x != null && y != null && z != null) {
            return new PartyBeatContext(world, x, y, z, radius, required);
        }
        if (world != null) {
            return PartyBeatContext.of(target.getLocation(), 0.0, required);
        }
        return null;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static QuestState parseState(String name, QuestState fallback) {
        if (name == null) return fallback;
        for (QuestState state : QuestState.values()) {
            if (state.name().equalsIgnoreCase(name)) return state;
        }
        return fallback;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Double dbl(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o == null) return null;
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.format("%.1f", d);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length <= 1) {
            String partial = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            for (Quest quest : plugin.getQuestManager().getAllQuests()) {
                if (quest.getId().toLowerCase(Locale.ROOT).startsWith(partial)) out.add(quest.getId());
            }
            return out;
        }
        if (args.length == 2) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            Optional<Quest> quest = plugin.getQuestManager().getQuest(args[0]);
            if (quest.isPresent() && quest.get() instanceof DataDrivenQuest ddq) {
                for (String id : componentIds(ddq)) {
                    if (id.toLowerCase(Locale.ROOT).startsWith(partial)) out.add(id);
                }
            }
            return out;
        }
        if (args.length == 3) {
            String partial = args[2].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(partial)) out.add(p.getName());
            }
        }
        return out;
    }

    @Override
    public List<String> getExamples() {
        return List.of(
                "/quest debug fire tfah_ch1_journey lectern_trigger Shad0melt",
                "  Dev only - it writes real state and can pay real rewards",
                "  exercises the ADVANCE, not the component's own detection:",
                "  a missing lectern still fires. Use preflight for block checks.",
                "  fire two co-located components back to back for the #1853 repro",
                "  pair it with /quest debug trace <player> to see the gate decision");
    }
}
