package org.fourz.RVNKQuests.npc;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rules for RVNK NPC keys as quest configs use them (#2214).
 *
 * <p>RVNKCore owns the key format: lower-case {@code [a-z0-9_-]{1,48}}, attached to an NPC with
 * {@code /rvnk npc tag <key> [npcId]}. A quest author may type a key in any case, so every
 * comparison here ignores case and every stored key is lower-cased first.</p>
 *
 * <p>No Bukkit or RVNKCore types, so this class loads and tests on its own.</p>
 */
public final class NpcKeyRules {

    /** The RVNKCore key format, after lower-casing. */
    public static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9_-]{1,48}");

    /** The format as text, for warnings. */
    public static final String KEY_FORMAT = "[a-z0-9_-]{1,48}";

    private NpcKeyRules() {}

    /**
     * Trims and lower-cases a key from config or from an event.
     *
     * @return the normalised key, or null for null or blank input
     */
    public static String normalize(String key) {
        if (key == null) return null;
        String trimmed = key.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    /** True when the key, after {@link #normalize}, matches {@link #KEY_PATTERN}. */
    public static boolean isValid(String key) {
        String normalized = normalize(key);
        return normalized != null && KEY_PATTERN.matcher(normalized).matches();
    }

    /** Case-insensitive key match. A null on either side never matches. */
    public static boolean matches(String configured, String actual) {
        return configured != null && actual != null && configured.equalsIgnoreCase(actual.trim());
    }
}
