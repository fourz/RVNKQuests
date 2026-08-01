package org.fourz.RVNKQuests.quest;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Works out which worlds a quest needs (#1876).
 *
 * <p>Two sources, and the gap between them matters:</p>
 *
 * <ul>
 *   <li><b>Declared</b> — {@code required_worlds} in quest metadata. Authoritative, and the only
 *       thing that is ever activated. Opt-in on purpose: Event holds 19 worlds in {@code IMPORTED}
 *       state, and activating every world any quest merely mentions would pull {@code alphac},
 *       {@code zothique}, {@code diaspora} and more into memory at boot.</li>
 *   <li><b>Referenced</b> — derived by walking {@code components.*.world}. What the quest actually
 *       touches.</li>
 * </ul>
 *
 * <p><b>Drift</b> is a world that is referenced but not declared. That single condition is the
 * Tales From A Hat Chapter 1 failure (#1767) stated precisely: the chain referenced {@code alphac}
 * throughout, declared nothing, and so sat unplayable behind an {@code IMPORTED} world while
 * {@code quest validate} kept reporting it {@code [VALID]}.</p>
 *
 * <p>All comparison is case-insensitive. World-name case genuinely varies here — Paper 26.2
 * lowercased migrated world names and RVNKWorlds still logs mismatches such as
 * {@code Diaspora_the_end} against a live {@code diaspora_the_end} (#1627) — so a case-sensitive
 * comparison would invent drift that does not exist.</p>
 *
 * @since 1.1.17
 */
public final class QuestWorldRequirements {

    /** Metadata key holding the opt-in list of worlds to keep active. */
    public static final String KEY_REQUIRED_WORLDS = "required_worlds";

    private static final String KEY_COMPONENTS = "components";
    private static final String KEY_WORLD = "world";

    private QuestWorldRequirements() {
    }

    /**
     * Worlds the quest asks to have active.
     *
     * @param metadata Quest definition metadata; null or absent key yields an empty set
     * @return Declared world names, original case preserved, insertion-ordered
     */
    public static Set<String> declared(Map<String, Object> metadata) {
        Set<String> out = new LinkedHashSet<>();
        if (metadata == null) return out;

        Object raw = metadata.get(KEY_REQUIRED_WORLDS);
        if (raw instanceof Collection<?> list) {
            for (Object item : list) {
                addIfUsable(out, item);
            }
        } else if (raw != null) {
            // Tolerate a single scalar — an author writing `required_worlds: alphac` means one world,
            // and silently ignoring that would reproduce the very defect this exists to catch.
            addIfUsable(out, raw);
        }
        return out;
    }

    /**
     * Worlds the quest's components actually point at.
     *
     * @param metadata Quest definition metadata
     * @return Referenced world names, original case preserved, insertion-ordered
     */
    public static Set<String> referenced(Map<String, Object> metadata) {
        Set<String> out = new LinkedHashSet<>();
        if (metadata == null) return out;

        if (!(metadata.get(KEY_COMPONENTS) instanceof Map<?, ?> components)) return out;

        for (Object value : components.values()) {
            if (value instanceof Map<?, ?> config) {
                addIfUsable(out, config.get(KEY_WORLD));
            }
        }
        return out;
    }

    /**
     * Referenced but not declared — worlds the quest needs and never asked for.
     *
     * <p>This is the actionable one: each entry is a world that will be {@code IMPORTED} after a
     * restart with nothing arranging otherwise.</p>
     *
     * @param metadata Quest definition metadata
     * @return Undeclared referenced worlds, case-insensitively sorted
     */
    public static Set<String> undeclared(Map<String, Object> metadata) {
        return difference(referenced(metadata), declared(metadata));
    }

    /**
     * Declared but never referenced — usually a typo or a leftover from an edited quest.
     *
     * <p>Harmless to the quest, but it costs memory: a declared world is activated at load whether
     * or not anything uses it.</p>
     *
     * @param metadata Quest definition metadata
     * @return Unused declared worlds, case-insensitively sorted
     */
    public static Set<String> unusedDeclarations(Map<String, Object> metadata) {
        return difference(declared(metadata), referenced(metadata));
    }

    /**
     * What to actually activate for this quest.
     *
     * <p>Declared worlds only — never the referenced set. Activating everything referenced would
     * make the opt-in meaningless and load worlds no author asked for.</p>
     *
     * @param metadata Quest definition metadata
     * @return Worlds to activate
     */
    public static Set<String> toActivate(Map<String, Object> metadata) {
        return declared(metadata);
    }

    /** Case-insensitive set difference, preserving the left side's original casing. */
    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> lowerRight = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        lowerRight.addAll(right);

        Set<String> out = new LinkedHashSet<>();
        for (String item : left) {
            if (!lowerRight.contains(item)) {
                out.add(item);
            }
        }
        return out;
    }

    /** Adds a non-blank string form of {@code value}, skipping duplicates that differ only in case. */
    private static void addIfUsable(Set<String> target, Object value) {
        if (value == null) return;
        String name = String.valueOf(value).trim();
        if (name.isEmpty()) return;

        for (String existing : target) {
            if (existing.equalsIgnoreCase(name)) return;
        }
        target.add(name);
    }
}
