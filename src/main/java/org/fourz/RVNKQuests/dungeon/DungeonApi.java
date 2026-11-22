package org.fourz.RVNKQuests.dungeon;

/**
 * Guard for the RVNKCore dungeon contract (#2368), mirroring {@code NpcApi}.
 *
 * <p>The contract arrived in RVNKCore 1.5.112-alpha. On an older core, loading a class that
 * references {@code DungeonEnterEvent} or {@code IDungeonService} throws
 * {@link NoClassDefFoundError}. The factory and the reward processor check this first and skip the
 * dungeon components with a warning instead.</p>
 */
public final class DungeonApi {

    private static final String[] CLASSES = {
        "org.fourz.rvnkcore.api.dungeon.IDungeonService",
        "org.fourz.rvnkcore.api.event.DungeonEnterEvent",
        "org.fourz.rvnkcore.api.event.DungeonClearedEvent",
    };

    private static volatile Boolean present;

    private DungeonApi() { }

    /** True when RVNKCore's dungeon contract can be loaded. Cached after the first call. */
    public static boolean isPresent() {
        Boolean cached = present;
        if (cached != null) return cached;
        boolean found = true;
        ClassLoader loader = DungeonApi.class.getClassLoader();
        for (String c : CLASSES) {
            try {
                Class.forName(c, false, loader);
            } catch (Throwable t) {
                found = false;
                break;
            }
        }
        present = found;
        return found;
    }
}
