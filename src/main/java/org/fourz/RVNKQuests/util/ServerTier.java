package org.fourz.RVNKQuests.util;

import org.bukkit.Bukkit;

/**
 * Which tier this server is, for the debug commands that must behave differently on a tier with
 * players in it (#2093).
 *
 * <p>Extracted from {@code QuestPreflightSubCommand}, which owned a private copy. Four debug
 * commands now need the same answer — {@code preflight} loads chunks, and {@code fire},
 * {@code trace} and {@code session} mutate player state or the log level. Four copies of a tier
 * check is four chances to repeat the mistake the original comment records, so the rule lives
 * here once.</p>
 *
 * <h2>The mistake worth not repeating</h2>
 *
 * <p><b>The server id is not the chat room name.</b> RVNK Dev's chat room is {@code test} while
 * its {@code server-id} is {@code dev}. Guessing {@code test} made the original gate report Dev as
 * a production tier and refuse to load chunks on the one server where loading is free. The other
 * ids are {@code event} and {@code nations}.</p>
 */
public final class ServerTier {

    /** RVNKCore's identifier for the development tier. */
    public static final String DEV_SERVER_ID = "dev";

    private ServerTier() {
    }

    /**
     * This server's tier identifier, or {@code null} when it cannot be determined.
     *
     * <p>Delegates to RVNKCore's {@code ConfigLoader.getServerId()} rather than re-reading config.
     * Server identity already exists there, it is the same value the chat mesh uses, and it carries
     * a fallback chain ({@code chat-relay.server-id} then {@code webhook.server-id}) that a copy
     * would silently drift from.</p>
     *
     * <p>A new RVNKQuests config key was rejected for the usual reason: it would not reach servers
     * that already have a {@code config.yml}, since {@code saveDefaultConfig()} writes only when
     * the file is absent. The key would sit in the jar, be absent everywhere real, and the gate
     * would read as working while doing nothing (#1563).</p>
     */
    public static String resolve() {
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
     * @return true when the given tier id is the development tier.
     *     <p><b>Unknown resolves to not-Dev.</b> If RVNKCore is missing or the id is unreadable,
     *     the safe reading of "I do not know which tier this is" is the cautious one.</p>
     */
    public static boolean isDev(String tier) {
        return tier != null && DEV_SERVER_ID.equalsIgnoreCase(tier);
    }

    /** Convenience for callers that do not need the id itself. */
    public static boolean isDev() {
        return isDev(resolve());
    }

    /** Renders the tier for an operator message, naming the unknown case rather than hiding it. */
    public static String describe() {
        String tier = resolve();
        return tier == null ? "unknown" : tier;
    }
}
