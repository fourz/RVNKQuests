package org.fourz.RVNKQuests.command;

import org.bukkit.command.CommandSender;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.fourz.RVNKQuests.quest.Quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /quest debug coords <quest>} — every coordinate a quest declares, with the distances
 * between them (#2093).
 *
 * <p>Read-only, so it runs on every tier including production. It loads nothing and reads no
 * blocks; the numbers come entirely from the quest definition.</p>
 *
 * <h2>The two failures it is built to surface</h2>
 *
 * <p><b>Overlapping trigger volumes.</b> Two components whose radii overlap both evaluate on the
 * same arrival, which is how #1853 happened: a trigger and a REACH objective both read the same
 * stale state and both wrote, so the persisted outcome was whichever landed last. The write chain
 * now serialises them, so the outcome is deterministic — but overlap is still an authoring smell,
 * and #1855 was exactly this shape: a trigger at the wrong anchor whose r50 swallowed another
 * quest's endpoint.</p>
 *
 * <p>The test is {@code distance < r1 + r2}, <b>not</b> centre-to-centre proximity. The first
 * version of this command used a fixed 3-block centre distance and consequently reported
 * {@code 0 co-located} for live Event content that overlapped by 39 blocks. Radii are the whole
 * point; ignoring them made the check cosmetic.</p>
 *
 * <p><b>Tight radii.</b> A small radius on a component a player crosses at speed can simply never
 * fire. This reports the number and warns, without pretending to know whether the component sits
 * on a lectern or on a corridor.</p>
 */
public class QuestCoordsSubCommand extends BaseSubCommand {

    /**
     * Two components this close are the same place, whatever their radii.
     *
     * <p>Not zero. Components authored a block or two apart share a tick on arrival just as surely
     * as components at identical coordinates, and the point is to catch the race, not to check for
     * string equality.</p>
     *
     * <p>This is the <b>narrow</b> case. The one that actually matters is radius overlap — see
     * {@link #overlap}.</p>
     */
    private static final double SAME_PLACE_DISTANCE = 3.0;

    /**
     * Radius at or below which a positional component is flagged as easy to miss.
     *
     * <p><b>Advisory and uncalibrated.</b> A tight radius is correct on a lectern a player must
     * stand at and reckless on a corridor they cross at speed, and nothing here can tell those
     * apart. Earlier versions of this class cited #1855 as the source of the number; that was a
     * misreading — #1855 was a trigger at the wrong anchor with a radius that was too <i>large</i>,
     * not too small. The threshold is a guess, so it warns rather than blocks.</p>
     */
    private static final double TIGHT_RADIUS = 5.0;

    /** One positional component, flattened out of the definition. */
    private record Point(String id, String kind, String world, double x, double y, double z, Double radius) {

        String where() {
            return world + " " + (int) x + "," + (int) y + "," + (int) z;
        }

        boolean sameWorld(Point other) {
            return world.equalsIgnoreCase(other.world);
        }

        double distanceTo(Point other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        /** Radius as a number, treating an absent radius as a point. */
        double r() {
            return radius == null ? 0.0 : radius;
        }

        /**
         * How far the two trigger volumes overlap, or a negative number when they are disjoint.
         *
         * <p><b>This, not centre distance, is the test that matters.</b> An earlier version of this
         * command compared centre-to-centre distance against a fixed 3 blocks and reported
         * {@code 0 co-located} for {@code tfah_zeal_arrival} on Event — whose {@code arr_trigger}
         * (r=30) and {@code arr_reach} (r=15) sit <b>5.9 blocks apart</b> and therefore overlap by
         * roughly 39 blocks. A player entering that area is inside both volumes and both components
         * evaluate on the same arrival, which is the #1853 same-tick race this command exists to
         * surface. It found nothing, on live content, in exactly the shape it was written for.</p>
         *
         * <p>Two radius-0 components at the same point still overlap by 0, which is why the
         * {@link #SAME_PLACE_DISTANCE} check is kept alongside this one rather than replaced by
         * it.</p>
         */
        double overlap(Point other) {
            return (r() + other.r()) - distanceTo(other);
        }
    }

    public QuestCoordsSubCommand(RVNKQuests plugin) {
        super(plugin, "coords", "Show every quest coordinate and the distances between them",
                "/quest debug coords <quest>", "rvnkquests.admin", false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args, 1)) return true;

        String questId = args[0];
        Optional<Quest> found = plugin.getQuestManager().getQuest(questId);
        if (found.isEmpty()) {
            sendErrorMessage(sender, "No quest registered with id '" + questId + "'.");
            return true;
        }
        if (!(found.get() instanceof DataDrivenQuest quest)) {
            sendErrorMessage(sender, "Quest '" + questId + "' is not data-driven; it declares no coordinates.");
            return true;
        }

        List<Point> points = collectPoints(quest.getDefinition().metadata());

        sendMessage(sender, "&6=== coords &f" + questId + " &6===");
        if (points.isEmpty()) {
            sendMessage(sender, "&7No positional components. This quest is driven entirely by"
                    + " world-scoped or inventory-scoped components.");
            return true;
        }

        // Points first, so an operator reading top-down sees the map before the analysis of it.
        for (Point p : points) {
            StringBuilder line = new StringBuilder("&7  &f");
            line.append(String.format("%-22s", truncate(p.id(), 21)));
            line.append("&7").append(p.where());
            if (p.kind() != null) {
                line.append(" &8").append(p.kind());
            }
            if (p.radius() != null) {
                line.append(" &7r=").append(fmt(p.radius()));
                if (p.radius() <= TIGHT_RADIUS) {
                    line.append(" &e(tight - a player moving fast can pass through)");
                }
            }
            sendMessage(sender, line.toString());
        }

        reportPairs(sender, points);
        return true;
    }

    // ── Extraction ──────────────────────────────────────────────────────────────

    /**
     * Flattens every component that declares all three coordinates.
     *
     * <p>Partial coordinates are skipped rather than reported here — {@code preflight} already
     * calls those a blocker, and duplicating the judgement in two commands invites the two to
     * disagree.</p>
     */
    private List<Point> collectPoints(Map<String, Object> metadata) {
        List<Point> points = new ArrayList<>();
        Object componentsObj = metadata == null ? null : metadata.get("components");
        if (!(componentsObj instanceof Map<?, ?> components)) {
            return points;
        }

        for (Map.Entry<?, ?> entry : components.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> config)) continue;

            String world = str(config.get("world"));
            Double x = dbl(config.get("x"));
            Double y = dbl(config.get("y"));
            Double z = dbl(config.get("z"));
            if (world == null || x == null || y == null || z == null) continue;

            // A destination-style component (ESCORT, REACH) may carry its target under
            // "destination" instead of flat x/y/z; those are picked up below as their own point.
            String kind = str(config.get("type"));
            if (kind == null) kind = str(config.get("objective_type"));

            points.add(new Point(String.valueOf(entry.getKey()), kind, world, x, y, z,
                    dbl(config.get("radius"))));

            Object destObj = config.get("destination");
            if (destObj instanceof Map<?, ?> dest) {
                Double dx = dbl(dest.get("x"));
                Double dy = dbl(dest.get("y"));
                Double dz = dbl(dest.get("z"));
                if (dx != null && dy != null && dz != null) {
                    points.add(new Point(entry.getKey() + ":destination", kind, world, dx, dy, dz,
                            dbl(config.get("follow_distance"))));
                }
            }
        }
        return points;
    }

    // ── Analysis ────────────────────────────────────────────────────────────────

    private void reportPairs(CommandSender sender, List<Point> points) {
        if (points.size() < 2) {
            sendMessage(sender, "&a1 point, nothing to compare.");
            return;
        }

        List<String> overlapping = new ArrayList<>();
        List<String> crossWorld = new ArrayList<>();
        List<String> clear = new ArrayList<>();

        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                Point a = points.get(i);
                Point b = points.get(j);
                if (!a.sameWorld(b)) {
                    crossWorld.add(a.id() + " (" + a.world() + ") <-> " + b.id() + " (" + b.world() + ")");
                    continue;
                }
                double d = a.distanceTo(b);
                double over = a.overlap(b);
                boolean samePlace = d <= SAME_PLACE_DISTANCE;

                if (over >= 0 || samePlace) {
                    String why = samePlace && over < 0
                            ? fmt(d) + " blocks apart (same place, both radius-less)"
                            : "centres " + fmt(d) + " apart, r=" + fmt(a.r()) + "+" + fmt(b.r())
                              + " -> overlap " + fmt(over) + " blocks";
                    overlapping.add(String.format("%-22s", truncate(a.id(), 21))
                            + "<-> " + String.format("%-22s", truncate(b.id(), 21)) + why);
                } else {
                    clear.add(String.format("%-22s", truncate(a.id(), 21))
                            + "-> " + String.format("%-22s", truncate(b.id(), 21))
                            + fmt(d) + " (clear by " + fmt(-over) + ")");
                }
            }
        }

        sendMessage(sender, "&7");
        if (!overlapping.isEmpty()) {
            sendMessage(sender, "&cOVERLAPPING (" + overlapping.size()
                    + ") - both evaluate on the same arrival:");
            for (String line : overlapping) {
                sendMessage(sender, "&c  " + line);
            }
            sendMessage(sender, "&7  The write chain serialises them so the outcome is"
                    + " deterministic (#1853) - but check the order is the one you intended,");
            sendMessage(sender, "&7  and that a player cannot satisfy a later beat by walking into"
                    + " an earlier one (#1855).");
        }

        if (!crossWorld.isEmpty()) {
            sendMessage(sender, "&7Cross-world pairs (" + crossWorld.size() + ") - never compared:");
            for (String line : crossWorld) {
                sendMessage(sender, "&8  " + line);
            }
        }

        if (!clear.isEmpty()) {
            sendMessage(sender, "&7Clear pairs:");
            for (String line : clear) {
                sendMessage(sender, "&8  " + line);
            }
        }

        sendMessage(sender, (overlapping.isEmpty() ? "&a" : "&c") + points.size() + " points, "
                + overlapping.size() + " overlapping, " + crossWorld.size() + " cross-world");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.format("%.1f", d);
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
                "/quest debug coords tfah_ch1_journey",
                "  read-only - runs on Event and prod too, loads nothing",
                "  OVERLAPPING means distance < r1+r2, so both evaluate on one arrival",
                "  that is the test - centre distance alone misses an r30 beside an r15",
                "  r= is the trigger radius; a tight one on a corridor can never fire");
    }
}
