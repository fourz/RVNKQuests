package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.IQuestRepository;
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /quest debug preflight <quest> [--no-load]} — checks every precondition a quest needs
 * before a player can complete it (#1867).
 *
 * <p>Replaces a manual console checklist. Preflighting one chain by hand took roughly a dozen
 * round-trips — world state, dimension key, chunk load, block identity, lore-item page count — and
 * still ended on an unanswered question. That is a checklist, so it belongs in code.</p>
 *
 * <h2>Three outcomes, deliberately</h2>
 *
 * <p>{@link Severity#UNVERIFIED} is not a failure. A component whose chunk cannot be read tells us
 * nothing about whether it is correct, and reporting "not readable" as "broken" condemns healthy
 * content — the same distinction {@code PortalService.verify()} draws (#1859). Only
 * {@link Severity#BLOCKER} means a player would actually be stopped.</p>
 *
 * <h2>What it will and will not load</h2>
 *
 * <p>Chunks are loaded on demand and released afterwards if this command loaded them: cheap,
 * local, and fully reversible.</p>
 *
 * <p>Worlds are <b>not</b> activated. A world's lifecycle (RAW -> IMPORTED -> ACTIVE) belongs to
 * RVNKWorlds and is backed by its own database state; forcing one up from here with
 * {@code Bukkit.createWorld} would load the level while leaving RVNKWorlds' record saying otherwise.
 * An inactive world is reported as a blocker with the exact remedy instead.</p>
 *
 * @since 1.1.15
 */
public class QuestPreflightSubCommand extends BaseSubCommand {

    /** How much a finding matters. Order is significant — worst first in the summary. */
    private enum Severity {
        BLOCKER("&c", "BLOCKER"),
        WARNING("&e", "warning"),
        UNVERIFIED("&6", "unverified"),
        OK("&a", "ok");

        final String colour;
        final String label;

        Severity(String colour, String label) {
            this.colour = colour;
            this.label = label;
        }
    }

    /** One line of the report. */
    private record Finding(Severity severity, String subject, String detail) {
    }

    /**
     * RVNKCore's identifier for the development tier.
     *
     * <p><b>This is the server id, not the chat room name.</b> The two are easy to confuse and do
     * not match: RVNK Dev's chat room is {@code test} while its {@code server-id} is {@code dev}.
     * Guessing {@code test} here made the gate report Dev as a production tier and refuse to load
     * chunks on the one server where loading is free. The other ids are {@code event} and
     * {@code nations}.</p>
     */
    private static final String DEV_SERVER_ID = "dev";

    public QuestPreflightSubCommand(RVNKQuests plugin) {
        super(plugin, "preflight", "Check a quest's world/block/reward preconditions",
                "/quest debug preflight <quest> [--no-load] [--force]", "rvnkquests.admin", false);
    }

    /**
     * Resolves this server's tier identifier, or {@code null} when it cannot be determined.
     *
     * <p>Delegates to RVNKCore's own {@code ConfigLoader.getServerId()} rather than re-reading the
     * config here. Server identity already exists there, it is the same value the chat mesh uses,
     * and it carries a fallback chain ({@code chat-relay.server-id} then {@code webhook.server-id})
     * that a copy would silently drift from — an earlier attempt read only the first key and
     * misidentified Dev as a production tier.</p>
     *
     * <p>A new RVNKQuests config key was rejected for the same reason it usually is: it would not
     * reach servers that already have a {@code config.yml}, since {@code saveDefaultConfig()}
     * writes only when the file is absent. The key would be in the jar, absent everywhere real,
     * and the gate would read as working while doing nothing (#1563).</p>
     */
    private String resolveTier() {
        try {
            org.bukkit.plugin.Plugin core = Bukkit.getPluginManager().getPlugin("RVNKCore");
            if (core == null) return null;
            String id = org.fourz.rvnkcore.config.ConfigLoader.getInstance(core).getServerId();
            return (id == null || id.isBlank()) ? null : id.trim();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @return true when this is the development tier.
     *     <p><b>Unknown resolves to not-Dev.</b> If RVNKCore is missing or the id is unreadable,
     *     the safe reading of "I do not know which tier this is" is the cautious one.</p>
     */
    private boolean isDevTier(String tier) {
        return tier != null && DEV_SERVER_ID.equalsIgnoreCase(tier);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 1)) return true;

        String questId = args[0];

        // Loading a chunk is a mutation, however brief. On Dev that is free; on a tier with players
        // in it the operator decided it must be asked for explicitly (#1867).
        String tier = resolveTier();
        boolean devTier = isDevTier(tier);
        boolean forced = false;
        boolean suppressed = false;
        for (String arg : args) {
            if (arg.equalsIgnoreCase("--no-load")) suppressed = true;
            if (arg.equalsIgnoreCase("--force")) forced = true;
        }
        boolean allowLoad = !suppressed && (devTier || forced);
        if (!devTier && !forced && !suppressed) {
            // Name the tier. "Not Dev" alone leaves an operator unable to tell a correctly-gated
            // production run from a Dev box whose identity failed to resolve.
            sendMessage(sender, "&7Tier is &e" + (tier == null ? "unknown" : tier)
                + "&7 (not Dev) - reporting readiness without loading chunks."
                + " Add &e--force&7 to load and release them.");
        }

        Optional<Quest> found = plugin.getQuestManager().getQuest(questId);
        if (found.isEmpty()) {
            sendErrorMessage(sender, "No quest registered with id '" + questId + "'.");
            return true;
        }
        if (!(found.get() instanceof DataDrivenQuest quest)) {
            sendErrorMessage(sender, "Quest '" + questId + "' is not data-driven; nothing to preflight.");
            return true;
        }

        List<Finding> findings = new ArrayList<>();
        Map<String, Object> metadata = quest.getDefinition().metadata();

        checkComponents(metadata, findings, allowLoad);
        checkStateMapping(metadata, findings);
        checkPrerequisites(quest, findings);

        // Rewards come from the repository asynchronously; render the whole report once they land so
        // the output is not interleaved across ticks.
        IQuestRepository repo = plugin.getQuestRepository();
        if (repo == null) {
            findings.add(new Finding(Severity.WARNING, "rewards", "repository unavailable - not checked"));
            report(sender, questId, findings);
            return true;
        }

        repo.findRewards(questId)
            .thenAccept(rewards -> {
                checkRewards(rewards, findings);
                Bukkit.getScheduler().runTask(plugin, () -> report(sender, questId, findings));
            })
            .exceptionally(ex -> {
                findings.add(new Finding(Severity.WARNING, "rewards", "lookup failed: " + ex));
                Bukkit.getScheduler().runTask(plugin, () -> report(sender, questId, findings));
                return null;
            });

        return true;
    }

    // ── Components ──────────────────────────────────────────────────────────────

    /** Case-insensitive membership — world-name case varies across migrated worlds (#1627). */
    private static boolean containsIgnoreCase(java.util.Set<String> set, String value) {
        for (String item : set) {
            if (item.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private void checkComponents(Map<String, Object> metadata, List<Finding> findings, boolean allowLoad) {
        // Declared worlds (#1876) — needed to tell an authoring gap from a runtime failure below.
        java.util.Set<String> declared =
                org.fourz.RVNKQuests.quest.QuestWorldRequirements.declared(metadata);

        Object componentsObj = metadata.get("components");
        if (!(componentsObj instanceof Map<?, ?> components) || components.isEmpty()) {
            findings.add(new Finding(Severity.BLOCKER, "components",
                    "none defined - the quest can never advance"));
            return;
        }

        for (Map.Entry<?, ?> entry : components.entrySet()) {
            String id = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof Map<?, ?> config)) {
                findings.add(new Finding(Severity.BLOCKER, id, "malformed component definition"));
                continue;
            }
            checkComponent(id, config, findings, allowLoad, declared);
        }

        // Declared-but-unused costs memory: a declared world is activated whether anything uses it.
        java.util.Set<String> unused =
                org.fourz.RVNKQuests.quest.QuestWorldRequirements.unusedDeclarations(metadata);
        if (!unused.isEmpty()) {
            findings.add(new Finding(Severity.WARNING, "required_worlds",
                    "declared but never referenced (activated for nothing): " + String.join(", ", unused)));
        }
    }

    private void checkComponent(String id, Map<?, ?> config, List<Finding> findings, boolean allowLoad,
                                java.util.Set<String> declaredWorlds) {
        String worldName = str(config.get("world"));
        if (worldName == null) {
            // Not every component is positional (item discovery, kill counts). Nothing to check.
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            // Three-way distinction (#1877). "Not active" alone made an authoring gap look like an
            // ops problem: the TFAH Ch1 chain declared nothing at all, and the only hint was a
            // suggestion to run /world load by hand after every restart (#1874).
            boolean isDeclared = containsIgnoreCase(declaredWorlds, worldName);
            String detail = isDeclared
                    ? "world '" + worldName + "' is DECLARED but not active - activation failed, or "
                      + "quests.preload-required-worlds is false. Try: /world load " + worldName
                    : "world '" + worldName + "' is not active and NOT declared - add it to "
                      + "required_worlds so it is activated on load, or run: /world load " + worldName;
            findings.add(new Finding(Severity.BLOCKER, id, detail));
            return;
        }

        Integer x = intOf(config.get("x"));
        Integer y = intOf(config.get("y"));
        Integer z = intOf(config.get("z"));

        // Plenty of components are world-scoped but not positional: LECTERN_BOOK_IN_HAND and
        // ITEM_DISCOVERY match on the item's identity anywhere in the world, and demanding
        // coordinates from them reports a healthy quest as broken. Absent-entirely is fine;
        // PARTIAL coordinates are the real authoring mistake, so those still fail.
        boolean anyCoord = x != null || y != null || z != null;
        boolean allCoords = x != null && y != null && z != null;
        if (!anyCoord) {
            String kind = str(config.get("type"));
            if (kind == null) kind = str(config.get("objective_type"));
            findings.add(new Finding(Severity.OK, id,
                    "world-scoped in '" + worldName + "'" + (kind == null ? "" : " (" + kind + ")")));
            return;
        }
        if (!allCoords) {
            findings.add(new Finding(Severity.BLOCKER, id,
                    "partial coordinates - x/y/z must all be set or all be omitted"));
            return;
        }

        String where = worldName + " " + x + "," + y + "," + z;

        // A block_type component asserts a specific block exists at those coordinates. That is the
        // only claim we can actually falsify here, so it is the only one worth loading a chunk for.
        String blockType = str(config.get("block_type"));
        if (blockType == null) {
            findings.add(new Finding(Severity.OK, id, where));
            return;
        }

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        boolean wasLoaded = world.isChunkLoaded(chunkX, chunkZ);
        if (!wasLoaded && !allowLoad) {
            // Unverified, never blocker: an unread chunk says nothing about whether the block is
            // right, and reporting "not readable" as "broken" condemns healthy content (#1859).
            findings.add(new Finding(Severity.UNVERIFIED, id,
                    where + " - chunk not loaded, block unchecked"));
            return;
        }
        if (!wasLoaded && !world.loadChunk(chunkX, chunkZ, false)) {
            // false = do not generate. A chunk that has never been generated is not a broken quest.
            findings.add(new Finding(Severity.UNVERIFIED, id,
                    where + " - chunk not generated, block unchecked"));
            return;
        }

        try {
            Block block = world.getBlockAt(x, y, z);
            Material expected = Material.matchMaterial(blockType);
            if (expected == null) {
                findings.add(new Finding(Severity.BLOCKER, id,
                        "block_type '" + blockType + "' is not a known material"));
            } else if (block.getType() == expected) {
                findings.add(new Finding(Severity.OK, id, where + " - " + expected.name() + " present"));
            } else {
                findings.add(new Finding(Severity.BLOCKER, id, where + " - expected " + expected.name()
                        + ", found " + block.getType().name()));
            }
        } finally {
            // Only release what we loaded; never unload a chunk a player is standing in.
            if (!wasLoaded) {
                world.unloadChunk(chunkX, chunkZ, false);
            }
        }
    }

    // ── State machine ───────────────────────────────────────────────────────────

    /**
     * A component that no state maps to registers zero listeners and silently never fires — the
     * failure mode behind more than one "the quest loads but does nothing" report.
     */
    private void checkStateMapping(Map<String, Object> metadata, List<Finding> findings) {
        Object mappingObj = metadata.get("state_mapping");
        if (!(mappingObj instanceof Map<?, ?> mapping) || mapping.isEmpty()) {
            findings.add(new Finding(Severity.BLOCKER, "state_mapping",
                    "missing - components register no listeners and the quest never advances"));
            return;
        }

        List<String> mapped = new ArrayList<>();
        for (Object value : mapping.values()) {
            if (value instanceof List<?> list) {
                for (Object item : list) mapped.add(String.valueOf(item));
            } else if (value != null) {
                mapped.add(String.valueOf(value));
            }
        }

        Object componentsObj = metadata.get("components");
        if (componentsObj instanceof Map<?, ?> components) {
            for (Object key : components.keySet()) {
                String id = String.valueOf(key);
                if (!mapped.contains(id)) {
                    findings.add(new Finding(Severity.BLOCKER, "state_mapping",
                            "component '" + id + "' is in no state - it will never fire"));
                }
            }
        }

        for (Object key : mapping.keySet()) {
            String state = String.valueOf(key);
            if (!isValidState(state)) {
                findings.add(new Finding(Severity.BLOCKER, "state_mapping",
                        "'" + state + "' is not a QuestState"));
            }
        }
    }

    private void checkPrerequisites(DataDrivenQuest quest, List<Finding> findings) {
        for (String prereq : quest.getDefinition().prerequisites() == null
                ? List.<String>of() : quest.getDefinition().prerequisites()) {
            if (plugin.getQuestManager().getQuest(prereq).isEmpty()) {
                findings.add(new Finding(Severity.BLOCKER, "prereq",
                        "'" + prereq + "' is not registered - this quest can never start"));
            } else {
                findings.add(new Finding(Severity.OK, "prereq", prereq));
            }
        }
    }

    // ── Rewards ─────────────────────────────────────────────────────────────────

    /**
     * Reports what each reward would actually grant, not its raw fields.
     *
     * <p>EXPERIENCE reads {@code value} as the mode and {@code amount} as the quantity. A quest
     * shipped with {@code value: '800'} / {@code amount: 1} and granted <b>one</b> experience point
     * while its text promised 800; the raw fields looked plausible and nothing surfaced it.</p>
     */
    private void checkRewards(List<RewardDTO> rewards, List<Finding> findings) {
        if (rewards == null || rewards.isEmpty()) {
            findings.add(new Finding(Severity.WARNING, "rewards", "none configured"));
            return;
        }

        for (RewardDTO reward : rewards) {
            String type = String.valueOf(reward.type());
            String value = reward.value();
            int amount = reward.amount();

            if ("EXPERIENCE".equals(type)) {
                boolean numericMode = value != null && value.chars().allMatch(Character::isDigit);
                if (numericMode) {
                    findings.add(new Finding(Severity.BLOCKER, "reward " + reward.rewardId(),
                            "grants " + amount + " xp, not " + value
                                    + " - value is the mode (points|levels), amount is the quantity"));
                } else {
                    findings.add(new Finding(Severity.OK, "reward " + reward.rewardId(),
                            amount + " " + (value == null ? "points" : value)));
                }
                continue;
            }

            findings.add(new Finding(Severity.OK, "reward " + reward.rewardId(),
                    type + " " + (value == null ? "" : value) + (amount > 1 ? " x" + amount : "")));
        }
    }

    // ── Report ──────────────────────────────────────────────────────────────────

    private void report(CommandSender sender, String questId, List<Finding> findings) {
        sendMessage(sender, "&6=== preflight &f" + questId + " &6===");

        Map<Severity, Integer> counts = new LinkedHashMap<>();
        for (Finding f : findings) {
            counts.merge(f.severity(), 1, Integer::sum);
            sendMessage(sender, "  " + f.severity().colour + pad(f.severity().label) + "&7"
                    + pad2(f.subject()) + "&f" + f.detail());
        }

        int blockers = counts.getOrDefault(Severity.BLOCKER, 0);
        int warnings = counts.getOrDefault(Severity.WARNING, 0);
        int unverified = counts.getOrDefault(Severity.UNVERIFIED, 0);

        String summary = blockers + " blocker" + (blockers == 1 ? "" : "s")
                + ", " + warnings + " warning" + (warnings == 1 ? "" : "s")
                + ", " + unverified + " unverified";
        sendMessage(sender, (blockers > 0 ? "&c" : warnings > 0 ? "&e" : "&a") + summary);

        if (unverified > 0) {
            sendMessage(sender, "&7  unverified = could not be read (chunk unloaded or ungenerated),"
                    + " not known to be broken");
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static String pad(String s) {
        return String.format("%-11s", s);
    }

    private static String pad2(String s) {
        return String.format("%-18s", s.length() > 17 ? s.substring(0, 17) : s);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Integer intOf(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return null;
        try {
            return (int) Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isValidState(String name) {
        for (QuestState state : QuestState.values()) {
            if (state.name().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length <= 1) {
            String partial = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            for (Quest quest : plugin.getQuestManager().getAllQuests()) {
                if (quest.getId().toLowerCase(Locale.ROOT).startsWith(partial)) out.add(quest.getId());
            }
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            if ("--no-load".startsWith(partial)) out.add("--no-load");
            if ("--force".startsWith(partial)) out.add("--force");
        }
        return out;
    }
}
