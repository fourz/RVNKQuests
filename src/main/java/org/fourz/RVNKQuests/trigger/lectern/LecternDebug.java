package org.fourz.RVNKQuests.trigger.lectern;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.integration.ILoreIntegration;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.util.ItemNameUtil;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Shared author-facing diagnostic for the lectern triggers (#1499).
 *
 * <p>When a trigger's {@code debug} config key is true, this emits one line for every candidate
 * lectern interaction, naming which gate stopped it (world, book, or state). Two things make it
 * usable that the earlier per-trigger logging got wrong, and both are the whole point:</p>
 *
 * <ul>
 *   <li><b>It runs BEFORE the world guard.</b> The previous {@code logBookSeen} sat after the
 *       world check, so a player interacting in the wrong world produced <em>no output at all</em>
 *       — identical silence to a book-name mismatch, which is the exact case the diagnostic is
 *       supposed to disambiguate. Wrong-world is the single most common "nothing happened" cause.</li>
 *   <li><b>It logs at WARNING, not INFO.</b> Servers routinely run at the WARNING global log level
 *       (Event does), where INFO and DEBUG are suppressed. An INFO diagnostic is invisible exactly
 *       when an author needs it. WARNING is always shown.</li>
 * </ul>
 *
 * <p>Off by default. An author sets {@code "debug": true} on the trigger while wiring it, watches
 * the console, then removes it. When off this logs nothing, so it never spams a live server.</p>
 */
final class LecternDebug {

    private LecternDebug() {
    }

    /**
     * @param seenBook the candidate book (on the lectern, in hand, or being taken); null if none
     * @param matched  whether {@code seenBook} matched the trigger's filter (caller computes it,
     *                 so the exact same {@code bookMatches} logic is reported, not a re-derivation)
     */
    static void emit(RVNKQuests plugin, LogManager logger, boolean debug, String triggerType,
                     String questId, Player player, String expectedWorld,
                     ItemStack seenBook, String expectedBookName, String expectedLoreId,
                     boolean matched, QuestState playerState, QuestState requiredState) {
        if (!debug) {
            return;
        }

        boolean worldOk = player.getWorld().getName().equalsIgnoreCase(expectedWorld);
        boolean stateOk = playerState == requiredState;

        String seenName = seenBook == null ? "(no book)" : ItemNameUtil.plainDisplayName(seenBook);
        ILoreIntegration lore = plugin.getLoreIntegration();
        String seenLoreId = (seenBook != null && lore != null) ? lore.resolveItemId(seenBook) : null;
        String expected = expectedLoreId != null ? "lore_book_id=" + expectedLoreId
                                                  : "book_name=" + expectedBookName;

        // Report gates in evaluation order so "blocked_by" names the first failure the trigger
        // would hit, not just any failing gate.
        String blockedBy = !worldOk ? "world"
                : !matched ? "book"
                : !stateOk ? "state"
                : "none — this interaction WOULD fire the trigger";

        logger.warning("[lectern-debug] " + triggerType + " quest=" + questId
                + " player=" + player.getName()
                + " world=" + player.getWorld().getName()
                + " (expected " + expectedWorld + ", ok=" + worldOk + ")"
                + " seen_name=" + seenName + " seen_lore_id=" + seenLoreId
                + " expected " + expected
                + " state=" + playerState + " (required " + requiredState + ", ok=" + stateOk + ")"
                + " matched=" + matched
                + " -> blocked_by=" + blockedBy);
    }
}
