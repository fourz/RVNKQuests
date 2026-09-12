package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.data.IQuestRepository;
import org.fourz.RVNKQuests.data.QuestYamlRepository;
import org.fourz.RVNKQuests.data.dto.ObjectiveDTO;
import org.fourz.RVNKQuests.data.dto.QuestDTO;
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.quest.Quest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * {@code /quest debug drift <quest>} — diffs the on-disk YAML against the live definition and says
 * what {@code /quest import} would actually do (#2093).
 *
 * <p>Read-only. It opens a second, throwaway {@link QuestYamlRepository} to read the files and
 * never writes through it.</p>
 *
 * <h2>What import really does — replace, not duplicate</h2>
 *
 * <p>The issue behind this command described the risk as import creating <b>both</b> copies of a
 * renamed reward and granting it twice. <b>That is not what the current code does.</b>
 * {@code QuestRepositoryImpl.save()} upserts the definition row, then calls
 * {@code saveObjectivesInternal} and {@code saveRewardsInternal}, and both of those
 * {@code DELETE ... WHERE quest_id = ?} before inserting. An import is therefore a <b>wholesale
 * replace</b> of objectives and rewards from the file.</p>
 *
 * <p>That makes the real hazard the opposite one, and quieter: <b>anything that exists only in the
 * database is deleted.</b> A reward added live with {@code /quest reward add} and never exported
 * vanishes on the next import, with no error and no log line naming it. Duplication announces
 * itself the first time a player is paid twice; silent deletion does not announce itself at all.
 * So DB-only entries are reported here as the loudest finding.</p>
 */
public class QuestDriftSubCommand extends BaseSubCommand {

    /** How a single field or entry differs. */
    private enum Kind {
        DB_ONLY("&c", "DELETED"),
        DISK_ONLY("&a", "added"),
        CHANGED("&e", "changed"),
        SAME("&8", "same");

        final String colour;
        final String label;

        Kind(String colour, String label) {
            this.colour = colour;
            this.label = label;
        }
    }

    private record Diff(Kind kind, String subject, String detail) {
    }

    public QuestDriftSubCommand(RVNKQuests plugin) {
        super(plugin, "drift", "Diff on-disk YAML against the live quest definition",
                "/quest debug drift <quest>", "rvnkquests.admin", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 1)) return true;

        String questId = args[0].endsWith(".yml")
                ? args[0].substring(0, args[0].length() - 4)
                : args[0];

        IQuestRepository live = plugin.getQuestRepository();
        if (live == null) {
            sendErrorMessage(sender, "Quest repository not available.");
            return true;
        }

        // Name the live source. On a tier in fallback mode the "live" definition is itself YAML,
        // which makes a clean diff meaningless rather than reassuring.
        boolean fallback = live.isInFallbackMode();
        QuestYamlRepository disk = new QuestYamlRepository(plugin);

        live.findById(questId).thenCombine(disk.findById(questId), DiffInputs::new)
            .thenAccept(in -> Bukkit.getScheduler().runTask(plugin,
                () -> report(sender, questId, in, fallback)))
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin,
                        () -> sendErrorMessage(sender, "Drift check failed: " + rootMessage(ex)));
                return null;
            });

        return true;
    }

    private record DiffInputs(Optional<QuestDTO> live, Optional<QuestDTO> disk) {
    }

    // ── Report ──────────────────────────────────────────────────────────────────

    private void report(CommandSender sender, String questId, DiffInputs in, boolean fallback) {
        sendMessage(sender, "&6=== drift &f" + questId + " &6===");

        if (fallback) {
            sendMessage(sender, "&eThe live repository is in FALLBACK mode - it is reading YAML too.");
            sendMessage(sender, "&7  A clean diff here does not mean the database agrees;"
                    + " the database is not being read at all.");
        }

        if (in.live().isEmpty() && in.disk().isEmpty()) {
            sendErrorMessage(sender, "Not found in either source - no such quest on disk or in the database.");
            return;
        }
        if (in.disk().isEmpty()) {
            sendMessage(sender, "&eNo file on disk (plugins/RVNKQuests/quests/" + questId + ".yml).");
            sendMessage(sender, "&7  Nothing to import. Run &f/quest export " + questId
                    + "&7 to write the live definition out.");
            return;
        }
        if (in.live().isEmpty()) {
            sendMessage(sender, "&aNot in the live repository - an import would create it whole.");
            sendMessage(sender, "&7  " + in.disk().get().objectives().size() + " objectives, "
                    + in.disk().get().rewards().size() + " rewards would be inserted.");
            return;
        }

        QuestDTO live = in.live().get();
        QuestDTO diskDto = in.disk().get();

        List<Diff> diffs = new ArrayList<>();
        diffFields(live, diskDto, diffs);
        diffRewards(live.rewards(), diskDto.rewards(), diffs);
        diffObjectives(live.objectives(), diskDto.objectives(), diffs);
        diffMetadata(live.metadata(), diskDto.metadata(), diffs);

        List<Diff> real = diffs.stream().filter(d -> d.kind() != Kind.SAME).toList();
        if (real.isEmpty()) {
            sendMessage(sender, "&aNo drift. Disk and live definition agree on every compared field.");
            return;
        }

        for (Diff d : real) {
            sendMessage(sender, "  " + d.kind().colour + String.format("%-9s", d.kind().label)
                    + "&7" + String.format("%-26s", truncate(d.subject(), 25)) + "&f" + d.detail());
        }

        long deleted = real.stream().filter(d -> d.kind() == Kind.DB_ONLY).count();
        long added = real.stream().filter(d -> d.kind() == Kind.DISK_ONLY).count();
        long changed = real.stream().filter(d -> d.kind() == Kind.CHANGED).count();

        sendMessage(sender, "&7");
        sendMessage(sender, (deleted > 0 ? "&c" : "&e") + "/quest import " + questId
                + " would REPLACE objectives and rewards from the file:");
        sendMessage(sender, "&7  " + added + " added, " + changed + " changed, "
                + (deleted > 0 ? "&c" : "&7") + deleted + " DELETED");

        if (deleted > 0) {
            sendMessage(sender, "&c  " + deleted + " entr" + (deleted == 1 ? "y" : "ies")
                    + " exist only in the database and would be lost.");
            sendMessage(sender, "&7  Export first if they are wanted: &f/quest export " + questId);
        }
    }

    // ── Field diffs ─────────────────────────────────────────────────────────────

    private void diffFields(QuestDTO live, QuestDTO disk, List<Diff> diffs) {
        compare(diffs, "name", live.name(), disk.name());
        compare(diffs, "description", live.description(), disk.description());
        compare(diffs, "category", live.category(), disk.category());
        compare(diffs, "repeatable", live.repeatable(), disk.repeatable());
        compare(diffs, "cooldownMinutes", live.cooldownMinutes(), disk.cooldownMinutes());

        // Order-insensitive: prerequisites are a set in meaning, and a reordered list is not drift.
        Set<String> livePre = new LinkedHashSet<>(live.prerequisites());
        Set<String> diskPre = new LinkedHashSet<>(disk.prerequisites());
        if (!livePre.equals(diskPre)) {
            diffs.add(new Diff(Kind.CHANGED, "prerequisites",
                    "live " + livePre + " -> disk " + diskPre));
        }
    }

    private void compare(List<Diff> diffs, String field, Object liveVal, Object diskVal) {
        if (Objects.equals(liveVal, diskVal)) {
            diffs.add(new Diff(Kind.SAME, field, ""));
            return;
        }
        diffs.add(new Diff(Kind.CHANGED, field,
                "live '" + liveVal + "' -> disk '" + diskVal + "'"));
    }

    // ── Reward diffs ────────────────────────────────────────────────────────────

    /**
     * Rewards keyed by {@code rewardId}, which is exactly the key the delete-then-insert replaces
     * on.
     *
     * <p>A reward whose id changed but whose effect did not is reported as one deletion plus one
     * addition rather than as a rename, because that is literally what the replace does — and it is
     * the shape that made the {@code _989 -> _2916} change confusing in the first place.</p>
     */
    private void diffRewards(List<RewardDTO> live, List<RewardDTO> disk, List<Diff> diffs) {
        Map<String, RewardDTO> liveById = new LinkedHashMap<>();
        for (RewardDTO r : live) liveById.put(r.rewardId(), r);
        Map<String, RewardDTO> diskById = new LinkedHashMap<>();
        for (RewardDTO r : disk) diskById.put(r.rewardId(), r);

        for (Map.Entry<String, RewardDTO> e : liveById.entrySet()) {
            RewardDTO diskReward = diskById.get(e.getKey());
            if (diskReward == null) {
                diffs.add(new Diff(Kind.DB_ONLY, "reward " + e.getKey(),
                        describe(e.getValue()) + " - in the database only"));
            } else if (!sameEffect(e.getValue(), diskReward)) {
                diffs.add(new Diff(Kind.CHANGED, "reward " + e.getKey(),
                        describe(e.getValue()) + " -> " + describe(diskReward)));
            }
        }
        for (Map.Entry<String, RewardDTO> e : diskById.entrySet()) {
            if (!liveById.containsKey(e.getKey())) {
                diffs.add(new Diff(Kind.DISK_ONLY, "reward " + e.getKey(),
                        describe(e.getValue()) + " - on disk only"));
            }
        }
    }

    /** Ignores {@code description}, which is prose and drifts harmlessly. */
    private static boolean sameEffect(RewardDTO a, RewardDTO b) {
        return a.type() == b.type()
                && Objects.equals(a.value(), b.value())
                && a.amount() == b.amount();
    }

    private static String describe(RewardDTO r) {
        return r.type() + " value=" + r.value() + " amount=" + r.amount();
    }

    // ── Objective diffs ─────────────────────────────────────────────────────────

    private void diffObjectives(List<ObjectiveDTO> live, List<ObjectiveDTO> disk, List<Diff> diffs) {
        Set<String> liveIds = new LinkedHashSet<>();
        for (ObjectiveDTO o : live) liveIds.add(o.objectiveId());
        Set<String> diskIds = new LinkedHashSet<>();
        for (ObjectiveDTO o : disk) diskIds.add(o.objectiveId());

        for (String id : liveIds) {
            if (!diskIds.contains(id)) {
                diffs.add(new Diff(Kind.DB_ONLY, "objective " + id, "in the database only"));
            }
        }
        for (String id : diskIds) {
            if (!liveIds.contains(id)) {
                diffs.add(new Diff(Kind.DISK_ONLY, "objective " + id, "on disk only"));
            }
        }
        if (live.size() != disk.size()) {
            diffs.add(new Diff(Kind.CHANGED, "objective count",
                    "live " + live.size() + " -> disk " + disk.size()));
        }
    }

    // ── Metadata diffs ──────────────────────────────────────────────────────────

    /**
     * Top-level metadata keys only.
     *
     * <p>{@code components} and {@code state_mapping} are deep nested maps. A structural recursive
     * diff of them belongs in {@code preflight}, which already validates their contents against the
     * live world; here it is enough to say the key changed and point at the command that inspects
     * it properly.</p>
     */
    private void diffMetadata(Map<String, Object> live, Map<String, Object> disk, List<Diff> diffs) {
        Set<String> keys = new LinkedHashSet<>(live.keySet());
        keys.addAll(disk.keySet());
        for (String key : keys) {
            boolean inLive = live.containsKey(key);
            boolean inDisk = disk.containsKey(key);
            if (inLive && !inDisk) {
                diffs.add(new Diff(Kind.DB_ONLY, "metadata." + key, "in the database only"));
            } else if (!inLive && inDisk) {
                diffs.add(new Diff(Kind.DISK_ONLY, "metadata." + key, "on disk only"));
            } else if (!Objects.equals(String.valueOf(live.get(key)), String.valueOf(disk.get(key)))) {
                String hint = ("components".equals(key) || "state_mapping".equals(key))
                        ? " - run /quest debug preflight for the contents"
                        : "";
                diffs.add(new Diff(Kind.CHANGED, "metadata." + key, "differs" + hint));
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** Unwraps wrapper layers to the most useful message for the operator. */
    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg != null ? msg : cur.getClass().getSimpleName();
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length <= 1) {
            String partial = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            for (Quest quest : plugin.getQuestManager().getAllQuests()) {
                if (quest.getId().toLowerCase(Locale.ROOT).startsWith(partial)) out.add(quest.getId());
            }
        }
        return out;
    }

    @Override
    public List<String> getExamples() {
        return List.of(
                "/quest debug drift tfah_ch1_journey",
                "  read-only - runs on Event and prod too",
                "  import REPLACES objectives and rewards from the file (delete-then-insert),",
                "  so a DELETED line means that entry exists only in the DB and import loses it",
                "  export first if you want to keep it: /quest export <quest>");
    }
}
