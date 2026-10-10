package org.fourz.RVNKQuests.waypoint;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the {@code waypoint} block of each component, and {@code metadata.waypoints: auto} (#2264).
 *
 * <h3>Authoring</h3>
 * <pre>
 * metadata:
 *   waypoints: auto            # optional; default off
 *   components:
 *     obj_gold_door:
 *       objective_type: INTERACT
 *       waypoint:
 *         world: sotw_sky_0
 *         x: -421
 *         y: 98
 *         z: 19
 *         label: The gold door  # optional; default = description, then the component id
 *         style: bossbar        # bossbar (default) | compass | particles
 * </pre>
 *
 * <p><b>Auto mode.</b> With {@code waypoints: auto}, a {@code LOCATION_PROXIMITY} trigger or a
 * {@code REACH} objective that has no {@code waypoint} block takes one from its own
 * {@code world/x/y/z}, with the same defaults the component uses (world {@code world}, y 64). It
 * needs {@code x} and {@code z}. A {@code REACH} with {@code context_location_key} is skipped: its
 * target is set at runtime. {@code waypoint: off} (or {@code false}/{@code none}) opts a single
 * component out. Auto is off by default, so a quest that does not ask for it gets no waypoint it
 * did not author.</p>
 *
 * <p>Nothing here stops a quest loading. A bad block is reported through {@code problems} (shown by
 * the load log and {@code /quest validate}) and that component simply has no waypoint.</p>
 */
public final class WaypointParser {

    /** The component key. */
    public static final String CONFIG_KEY = "waypoint";
    /** The quest metadata key that turns on auto mode. */
    public static final String AUTO_KEY = "waypoints";

    private WaypointParser() {
    }

    /**
     * True when the quest sets {@code metadata.waypoints: auto}.
     *
     * @param problems receives a line for a value that is neither {@code auto} nor off; may be null
     */
    public static boolean isAuto(Map<String, Object> metadata, List<String> problems) {
        if (metadata == null) return false;
        Object raw = metadata.get(AUTO_KEY);
        if (raw == null || Boolean.FALSE.equals(raw)) return false;
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (s.equals("auto")) return true;
        if (s.equals("off") || s.equals("false") || s.equals("none")) return false;
        if (problems != null) {
            problems.add("metadata.waypoints '" + raw + "' is not 'auto' - ignored, only authored waypoint blocks are used");
        }
        return false;
    }

    /**
     * Every component's waypoint, in component order.
     *
     * @param metadata the quest metadata (may be null)
     * @param problems receives author-facing problems; may be null
     * @return component id to waypoint; empty when the quest has none
     */
    public static Map<String, Waypoint> parseAll(Map<String, Object> metadata, List<String> problems) {
        if (metadata == null || !(metadata.get("components") instanceof Map<?, ?> components)) {
            return Collections.emptyMap();
        }
        boolean auto = isAuto(metadata, problems);
        Map<String, Waypoint> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : components.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> config)) continue;
            String componentId = String.valueOf(e.getKey());
            Waypoint wp = parse(componentId, config, auto, problems);
            if (wp != null) {
                out.put(componentId, wp);
            }
        }
        return out.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(out);
    }

    /**
     * One component's waypoint.
     *
     * @param auto true when the quest sets {@code waypoints: auto}
     * @return the waypoint, or null when the component has none
     */
    public static Waypoint parse(String componentId, Map<?, ?> config, boolean auto, List<String> problems) {
        Object raw = config.get(CONFIG_KEY);
        if (raw != null) {
            if (isOff(raw)) return null;
            if (!(raw instanceof Map<?, ?> block)) {
                report(problems, "component '" + componentId + "' waypoint must be a block with world, x, y, z - ignored");
                return null;
            }
            return parseBlock(componentId, config, block, problems);
        }
        if (auto) {
            return derive(componentId, config);
        }
        return null;
    }

    private static boolean isOff(Object raw) {
        if (Boolean.FALSE.equals(raw)) return true;
        if (raw instanceof String s) {
            String v = s.trim().toLowerCase(Locale.ROOT);
            return v.equals("off") || v.equals("none") || v.equals("false");
        }
        return false;
    }

    private static Waypoint parseBlock(String componentId, Map<?, ?> config, Map<?, ?> block, List<String> problems) {
        Object worldRaw = block.get("world");
        String world = worldRaw == null ? null : String.valueOf(worldRaw).trim();
        if (world == null || world.isEmpty()) {
            report(problems, "component '" + componentId + "' waypoint has no world - ignored");
            return null;
        }
        Double x = number(block.get("x"));
        Double y = number(block.get("y"));
        Double z = number(block.get("z"));
        if (x == null || y == null || z == null) {
            report(problems, "component '" + componentId + "' waypoint needs numeric x, y and z - ignored");
            return null;
        }
        WaypointStyle style = WaypointStyle.BOSSBAR;
        Object styleRaw = block.get("style");
        if (styleRaw != null) {
            WaypointStyle parsed = WaypointStyle.parse(styleRaw);
            if (parsed == null) {
                report(problems, "component '" + componentId + "' waypoint style '" + styleRaw
                    + "' is not bossbar, compass or particles - using bossbar");
            } else {
                style = parsed;
            }
        }
        return new Waypoint(componentId, world, x, y, z, label(componentId, config, block.get("label")), style, false);
    }

    /** Auto mode: a LOCATION_PROXIMITY trigger or a REACH objective with fixed coordinates. */
    private static Waypoint derive(String componentId, Map<?, ?> config) {
        boolean proximity = "LOCATION_PROXIMITY".equalsIgnoreCase(str(config.get("type")));
        boolean reach = "REACH".equalsIgnoreCase(str(config.get("objective_type")));
        if (!proximity && !reach) return null;
        if (reach && config.get("context_location_key") != null) return null;
        Double x = number(config.get("x"));
        Double z = number(config.get("z"));
        if (x == null || z == null) return null;
        Double y = number(config.get("y"));
        String world = str(config.get("world"));
        // The same defaults the components use, so the waypoint is where the component checks.
        return new Waypoint(componentId, world == null || world.isBlank() ? "world" : world.trim(),
            x, y == null ? 64.0 : y, z, label(componentId, config, null), WaypointStyle.BOSSBAR, true);
    }

    private static String label(String componentId, Map<?, ?> config, Object authored) {
        String l = str(authored);
        if (l != null && !l.isBlank()) return l.trim();
        String d = str(config.get("description"));
        if (d != null && !d.isBlank()) return d.trim();
        return componentId;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** A number from YAML (Integer/Double), the database (Gson Double) or a numeric string. */
    static Double number(Object o) {
        if (o instanceof Number n) {
            double d = n.doubleValue();
            return Double.isFinite(d) ? d : null;
        }
        if (o instanceof String s) {
            try {
                double d = Double.parseDouble(s.trim());
                return Double.isFinite(d) ? d : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static void report(List<String> problems, String line) {
        if (problems != null) problems.add(line);
    }
}
