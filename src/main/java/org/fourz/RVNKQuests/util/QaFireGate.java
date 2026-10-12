package org.fourz.RVNKQuests.util;

import java.util.Locale;
import java.util.Set;

/**
 * The gate for {@code /quest debug fire} (#2265): a pure function, unit-testable without a server.
 *
 * <p>It mirrors RVNKCore's {@code NpcClickGate} (#2255) rule for rule. It is a copy, not a
 * dependency: RVNK Event runs RVNKCore 1.5.101, which does not have that class, and linking it would
 * throw {@code NoClassDefFoundError} the first time anyone typed the command there.</p>
 *
 * <ol>
 *   <li>The sender needs the admin permission ({@code rvnkquests.admin}).</li>
 *   <li>The tier must be known. A null, blank or {@code local} server id refuses, whatever the
 *       permissions. "I do not know which tier this is" reads as the cautious answer.</li>
 *   <li>On the Dev tier ({@code dev}, or {@code test}) any online player may be the target.</li>
 *   <li>On every other tier ({@code event}, {@code nations}, anything else) the TARGET player must
 *       hold {@value #PERM_QA_SUBJECT}. The sender's own permissions never stand in for the
 *       target's.</li>
 * </ol>
 *
 * <p><b>Tier source.</b> {@link ServerTier#resolve()}, which reads RVNKCore's
 * {@code ConfigLoader.getServerId()}: {@code chat-relay.server-id}, then {@code webhook.server-id},
 * else {@code "local"}. RVNK Dev's id is {@code dev} (its chat room is {@code test}); Event is
 * {@code event}; prod is {@code nations}.</p>
 */
public final class QaFireGate {

    /** Permission a target player needs on a non-Dev tier. Same node as RVNKCore's NPC click gate. */
    public static final String PERM_QA_SUBJECT = "rvnkcore.qa.subject";

    /** Permission the sender needs on every tier. */
    public static final String PERM_ADMIN = "rvnkquests.admin";

    /** Server ids that count as the development tier. */
    public static final Set<String> DEV_TIERS = Set.of("dev", "test");

    /** RVNKCore returns this server id when none is configured, so it counts as unknown. */
    public static final String UNSET_TIER = "local";

    private QaFireGate() {
    }

    /**
     * @param allowed true when the fire may run
     * @param reason  why, for the sender and the audit log; never null
     */
    public record Decision(boolean allowed, String reason) {
    }

    /**
     * Decides whether a fire may run.
     *
     * @param tier              this server's id, or null when it could not be read
     * @param senderIsAdmin     whether the sender has {@value #PERM_ADMIN}
     * @param targetIsQaSubject whether the target player has {@value #PERM_QA_SUBJECT}
     * @return the decision; never null
     */
    public static Decision evaluate(String tier, boolean senderIsAdmin, boolean targetIsQaSubject) {
        if (!senderIsAdmin) {
            return new Decision(false, "the sender lacks " + PERM_ADMIN);
        }
        String normalized = normalize(tier);
        if (normalized == null) {
            return new Decision(false, "this server's tier is unknown (server-id is "
                    + (tier == null ? "unreadable" : "'" + tier.trim() + "'")
                    + "); set chat-relay.server-id or webhook.server-id in RVNKCore config.yml");
        }
        if (DEV_TIERS.contains(normalized)) {
            return new Decision(true, "tier '" + normalized + "' is Dev");
        }
        if (targetIsQaSubject) {
            return new Decision(true, "tier '" + normalized + "', target has " + PERM_QA_SUBJECT);
        }
        return new Decision(false, "tier '" + normalized + "' is not Dev and the target player lacks "
                + PERM_QA_SUBJECT + " (add them to the QA group: lp user <player> parent add qa)");
    }

    /** @return true when the tier is unknown, so the gate refuses whoever the target is */
    public static boolean isUnknownTier(String tier) {
        return normalize(tier) == null;
    }

    /** @return the lower-case trimmed tier, or null when it is null, blank or the unset fallback */
    static String normalize(String tier) {
        if (tier == null) {
            return null;
        }
        String normalized = tier.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() || normalized.equals(UNSET_TIER) ? null : normalized;
    }
}
