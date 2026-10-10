package org.fourz.RVNKQuests.npc;

/**
 * Checks that the running RVNKCore has the NPC bridge (1.5.99-alpha+, #2213).
 *
 * <p>RVNKQuests compiles against the bridge, but a server can still run an older RVNKCore jar.
 * There, registering a listener for {@code RvnkNpcInteractEvent} throws
 * {@code NoClassDefFoundError}, an {@code Error} that the quest registration path does not catch.
 * Every NPC entry point checks this first and degrades to a warning instead.</p>
 */
public final class NpcApi {

    private static final String EVENT_CLASS = "org.fourz.rvnkcore.api.event.RvnkNpcInteractEvent";
    private static final String SERVICE_CLASS = "org.fourz.rvnkcore.api.service.INpcService";

    private static volatile Boolean present;

    private NpcApi() {}

    /** True when RVNKCore's NPC event and service classes can be loaded. Cached after the first call. */
    public static boolean isPresent() {
        Boolean cached = present;
        if (cached != null) return cached;
        boolean found;
        try {
            ClassLoader loader = NpcApi.class.getClassLoader();
            Class.forName(EVENT_CLASS, false, loader);
            Class.forName(SERVICE_CLASS, false, loader);
            found = true;
        } catch (Throwable t) {
            found = false;
        }
        present = found;
        return found;
    }
}
