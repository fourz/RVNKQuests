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
 * <p><b>Co-location.</b> Two components at the same coordinates fire in the same tick on arrival,
 * which is how #1853 happened: a trigger and a REACH objective both read the same stale state and
 * both wrote, so the persisted outcome was whichever landed last. The write chain now serialises
 * them, but co-location is still an authoring smell worth seeing before a player finds it.</p>
 *
 * <p><b>Missable point-radius triggers.</b> A small radius on a component a player passes at speed
 * simply never fires — the {@code tfah_zeal_tower} lesson (#1855). A tight radius is legitimate on
 * a lectern a player must stand at, and reckless on a corridor, so this reports the number and the
 * distance to its neighbours rather than pronouncing a verdict.</p>
 */
public class QuestCoordsSubCommand extends BaseSubCommand {

    /**
     * Two components within this distance are treated as co-located.
     *
     * <p>Not zero. Components authored a block or two apart share a tick on arrival just as surely
     * as components at identical coordinates, and the point is to catch the race, not to check for
     * string equality.</p>
     */
    private static final double COLOCATION_DISTANCE = 3.0;

    /**
     * Radius at or below which a positional component is flagged as easy to miss.
     *
     * <p>Chosen from {@code tfah_zeal_tower}: its radius was small enough that a player flying past
     * never registered. Deliberately advisory — see the class note on tight radii being correct for
     * a lectern and wrong for a corridor.</p>
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

        List<String> colocated = new ArrayList<>();
        List<String> crossWorld = new ArrayList<>();
        List<String> distances = new ArrayList<>();

        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                Point a = points.get(i);
                Point b = points.get(j);
                if (!a.sameWorld(b)) {
                    crossWorld.add(a.id() + " (" + a.world() + ") <-> " + b.id() + " (" + b.world() + ")");
                    continue;
                }
                double d = a.distanceTo(b);
                if (d <= COLOCATION_DISTANCE) {
                    colocated.add(a.id() + " <-> " + b.id() + " - " + fmt(d) + " blocks apart");
                } else {
                    distances.add(String.format("%-22s", truncate(a.id(), 21))
                            + "-> " + String.format("%-22s", truncate(b.id(), 21)) + fmt(d));
                }
            }
        }

        sendMessage(sender, "&7");
        if (!colocated.isEmpty()) {
            sendMessage(sender, "&cCO-LOCATED (" + colocated.size() + ") - these fire in the same tick:");
            for (String line : colocated) {
                sendMessage(sender, "&c  " + line);
            }
            sendMessage(sender, "&7  The write chain serialises them, so the outcome is"
                    + " deterministic (#1853) - but check the order is the one you intended.");
        }

        if (!crossWorld.isEmpty()) {
            sendMessage(sender, "&7Cross-world pairs (" + crossWorld.size() + ") - never compared:");
            for (String line : crossWorld) {
                sendMessage(sender, "&8  " + line);
            }
        }

        if (!distances.isEmpty()) {
            sendMessage(sender, "&7Distances:");
            for (String line : distances) {
                sendMessage(sender, "&8  " + line);
            }
        }

        sendMessage(sender, (colocated.isEmpty() ? "&a" : "&c") + points.size() + " points, "
                + colocated.size() + " co-located, " + crossWorld.size() + " cross-world");
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
                "  CO-LOCATED means two components fire on the same arrival tick",
                "  r= is the trigger radius; a tight one on a corridor is how #1855 was missed");
    }
}
