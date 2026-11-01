package org.fourz.RVNKQuests.objective.generic;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One block check of a {@code BLOCK_STATE} objective (#2389): a block position, an optional
 * material and a map of BlockData properties that must all hold at once.
 *
 * <p>Matching works on {@code BlockData.getAsString()}, for example
 * {@code minecraft:lever[face=wall,facing=north,powered=true]}. Any property a block has can be
 * checked without per-type code: {@code powered}, {@code open}, {@code facing}, {@code power},
 * {@code lit} and so on.</p>
 *
 * <p>Pure data and string parsing, so it is unit tested without a server.</p>
 *
 * @param world    world name
 * @param x        block x
 * @param y        block y
 * @param z        block z
 * @param blockKey namespaced material key ({@code minecraft:lever}), or null for any block
 * @param state    property name to expected value, both lower case; may be empty
 */
public record BlockStateCheck(String world, int x, int y, int z, String blockKey, Map<String, String> state) {

    public BlockStateCheck {
        state = state == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(state));
    }

    /**
     * Parses the {@code checks} list of a component config.
     *
     * @param raw          the {@code checks} value (a list of maps from YAML or Gson)
     * @param defaultWorld the component-level {@code world}, used when a check has none; may be null
     * @return the checks, never empty
     * @throws IllegalArgumentException when the list is missing or empty, or a check has no world,
     *                                  a missing or non-numeric coordinate, an unknown block, or
     *                                  neither {@code block} nor {@code state}. The factory records
     *                                  the message, and {@code quest validate} reports the quest
     *                                  INVALID (#1424).
     */
    public static List<BlockStateCheck> parseAll(Object raw, String defaultWorld) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("BLOCK_STATE needs a non-empty 'checks' list");
        }
        List<BlockStateCheck> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Map<?, ?> m)) {
                throw new IllegalArgumentException("BLOCK_STATE check #" + (i + 1)
                    + " must be a block with world, x, y, z and state");
            }
            out.add(parse(m, defaultWorld, i + 1));
        }
        return List.copyOf(out);
    }

    /** One check. {@code index} is 1-based, for messages. */
    static BlockStateCheck parse(Map<?, ?> m, String defaultWorld, int index) {
        String where = "BLOCK_STATE check #" + index;
        Object worldRaw = m.get("world");
        String world = worldRaw != null ? String.valueOf(worldRaw).trim() : defaultWorld;
        if (world == null || world.isEmpty()) {
            throw new IllegalArgumentException(where + " has no world (set it on the check or on the component)");
        }
        int x = coord(m, "x", where);
        int y = coord(m, "y", where);
        int z = coord(m, "z", where);

        String blockKey = null;
        Object blockRaw = m.get("block");
        if (blockRaw != null) {
            blockKey = blockKey(String.valueOf(blockRaw), where);
        }

        Map<String, String> state = new LinkedHashMap<>();
        Object stateRaw = m.get("state");
        if (stateRaw != null) {
            if (!(stateRaw instanceof Map<?, ?> sm)) {
                throw new IllegalArgumentException(where + " state must be a map of property: value");
            }
            for (Map.Entry<?, ?> e : sm.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    throw new IllegalArgumentException(where + " state has an empty property or value");
                }
                state.put(norm(e.getKey()), norm(e.getValue()));
            }
        }
        if (blockKey == null && state.isEmpty()) {
            throw new IllegalArgumentException(where + " needs 'block', 'state' or both - it would match any block");
        }
        return new BlockStateCheck(world, x, y, z, blockKey, state);
    }

    /**
     * {@code LEVER} or {@code minecraft:lever} to {@code minecraft:lever}, checked against
     * {@link Material}. The "is a block" check needs the Bukkit registry, so it is skipped when no
     * server is running (unit tests).
     */
    static String blockKey(String raw, String where) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        String key = s.contains(":") ? s : "minecraft:" + s;
        if (!BLOCK_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException(where + " block '" + raw + "' is not a block name");
        }
        Material material = Material.matchMaterial(key);
        if (material == null) {
            throw new IllegalArgumentException(where + " block '" + raw + "' is not a known material");
        }
        boolean block;
        try {
            block = material.isBlock();
        } catch (LinkageError noServer) {
            // isBlock() reads the Bukkit registry, which only exists on a running server.
            block = true;
        }
        if (!block) {
            throw new IllegalArgumentException(where + " block '" + raw + "' is an item, not a block");
        }
        return material.getKey().toString();
    }

    private static final java.util.regex.Pattern BLOCK_KEY =
        java.util.regex.Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private static int coord(Map<?, ?> m, String key, String where) {
        Object v = m.get(key);
        if (v instanceof Number n && Double.isFinite(n.doubleValue())) {
            return (int) Math.floor(n.doubleValue());
        }
        if (v instanceof String s) {
            try {
                double d = Double.parseDouble(s.trim());
                if (Double.isFinite(d)) return (int) Math.floor(d);
            } catch (NumberFormatException ignored) {
                // falls through to the error below
            }
        }
        throw new IllegalArgumentException(where + (v == null ? " is missing '" : " has a non-numeric '")
            + key + "'");
    }

    private static String norm(Object o) {
        return String.valueOf(o).trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Splits a {@code BlockData.getAsString()} value into its material key and properties.
     *
     * @param blockData e.g. {@code minecraft:lever[face=wall,facing=north,powered=true]}
     * @return the parsed form; an input with no brackets has no properties
     */
    public static Parsed parseBlockData(String blockData) {
        if (blockData == null) return new Parsed(null, Map.of());
        String s = blockData.trim();
        int open = s.indexOf('[');
        if (open < 0) return new Parsed(s.toLowerCase(Locale.ROOT), Map.of());
        String key = s.substring(0, open).toLowerCase(Locale.ROOT);
        int close = s.lastIndexOf(']');
        String body = close > open ? s.substring(open + 1, close) : s.substring(open + 1);
        Map<String, String> props = new LinkedHashMap<>();
        for (String pair : body.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            props.put(pair.substring(0, eq).trim().toLowerCase(Locale.ROOT),
                pair.substring(eq + 1).trim().toLowerCase(Locale.ROOT));
        }
        return new Parsed(key, props);
    }

    /** A parsed block data string. */
    public record Parsed(String key, Map<String, String> properties) {
    }

    /**
     * True when the block data string satisfies this check: same material (when one is set) and
     * every expected property present with the expected value. A property the block does not have
     * is a mismatch, so a typo never matches.
     *
     * @param blockData {@code BlockData.getAsString()} of the block at this position; null = no match
     */
    public boolean matches(String blockData) {
        if (blockData == null) return false;
        Parsed p = parseBlockData(blockData);
        if (blockKey != null && !blockKey.equals(p.key())) return false;
        for (Map.Entry<String, String> e : state.entrySet()) {
            if (!e.getValue().equals(p.properties().get(e.getKey()))) return false;
        }
        return true;
    }

    /**
     * True when the block at {@code (bx, by, bz)} in {@code worldName} is within {@code reach}
     * blocks (on every axis) of this check. Cheap filter for redstone events.
     */
    public boolean isNear(String worldName, int bx, int by, int bz, int reach) {
        return world.equalsIgnoreCase(worldName)
            && Math.abs(bx - x) <= reach && Math.abs(by - y) <= reach && Math.abs(bz - z) <= reach;
    }

    /** One-line form for logs. */
    public String describe() {
        return world + " " + x + "," + y + "," + z + (blockKey != null ? " " + blockKey : "") + " " + state;
    }
}
