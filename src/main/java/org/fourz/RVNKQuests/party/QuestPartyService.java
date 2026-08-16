package org.fourz.RVNKQuests.party;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.util.ConfigKeys;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns every quest party and answers the one question the fan-out asks: <i>who else qualifies for
 * this beat right now?</i>
 *
 * <p><b>In-memory by design.</b> A restart dissolves all parties. Nothing is lost by that: every
 * member's quest state is persisted per player through the normal progress path, so the only thing
 * a restart costs is the roster itself. Persistence via {@code QuestProgressDTO.metadata} is a
 * filed follow-up, not v1 scope.</p>
 *
 * <p><b>Threading.</b> Mutating calls (invite/accept/leave/disband/quit) come from command
 * execution and quit handling — main thread. {@link #qualifyingMembers} reads Bukkit player
 * locations and is therefore main-thread ONLY; it enforces that itself rather than trusting every
 * future call site. The maps are {@link ConcurrentHashMap} so the async write-chain world can
 * safely hold references without tearing, but the membership protocol is main-thread.</p>
 *
 * @since 1.1.41 (#1982)
 */
public final class QuestPartyService {

    private final RVNKQuests plugin;
    private final LogManager logger;

    private final Map<UUID, QuestParty> partyByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, PendingInvite> pendingInvites = new ConcurrentHashMap<>();

    /** Invite expiry is lazy — checked on read — so there is no scheduler to manage. */
    private record PendingInvite(UUID inviter, QuestParty party, long expiresAtMillis) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }

    public enum PartyResult {
        OK,
        NO_PARTY,
        ALREADY_IN_PARTY,
        TARGET_IN_PARTY,
        PARTY_FULL,
        SELF_INVITE,
        NO_INVITE,
        INVITE_EXPIRED,
        NOT_LEADER,
        DISABLED
    }

    public QuestPartyService(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }

    // ==================== config ====================

    public boolean isEnabled() {
        return getBool(ConfigKeys.PARTY_ENABLED, true);
    }

    public int getMaxSize() {
        return (int) getDouble(ConfigKeys.PARTY_MAX_SIZE, 4);
    }

    public long getInviteTimeoutMillis() {
        return (long) (getDouble(ConfigKeys.PARTY_INVITE_TIMEOUT_SECONDS, 60) * 1000L);
    }

    public double getShareRadiusMultiplier() {
        return getDouble(ConfigKeys.PARTY_SHARE_RADIUS_MULTIPLIER, 5.0);
    }

    public double getMinShareRadius() {
        return getDouble(ConfigKeys.PARTY_MIN_SHARE_RADIUS, 10.0);
    }

    private boolean getBool(String path, boolean def) {
        Object v = plugin.getConfigManager().getConfigValue(path, def);
        return v instanceof Boolean b ? b : def;
    }

    private double getDouble(String path, double def) {
        Object v = plugin.getConfigManager().getConfigValue(path, def);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    // ==================== membership ====================

    /** The player's party, or null. */
    public QuestParty getParty(UUID player) {
        return partyByPlayer.get(player);
    }

    public PartyResult invite(Player inviter, Player target) {
        if (!isEnabled()) return PartyResult.DISABLED;
        if (inviter.getUniqueId().equals(target.getUniqueId())) return PartyResult.SELF_INVITE;
        if (partyByPlayer.containsKey(target.getUniqueId())) return PartyResult.TARGET_IN_PARTY;

        QuestParty party = partyByPlayer.get(inviter.getUniqueId());
        if (party != null && !party.isLeader(inviter.getUniqueId())) return PartyResult.NOT_LEADER;
        if (party != null && party.size() >= getMaxSize()) return PartyResult.PARTY_FULL;

        if (party == null) {
            party = new QuestParty(inviter.getUniqueId());
            partyByPlayer.put(inviter.getUniqueId(), party);
        }

        // Single pending slot per invitee; the newest invite wins.
        pendingInvites.put(target.getUniqueId(), new PendingInvite(
                inviter.getUniqueId(), party, System.currentTimeMillis() + getInviteTimeoutMillis()));
        return PartyResult.OK;
    }

    public PartyResult accept(Player invitee) {
        if (!isEnabled()) return PartyResult.DISABLED;
        if (partyByPlayer.containsKey(invitee.getUniqueId())) return PartyResult.ALREADY_IN_PARTY;

        PendingInvite invite = pendingInvites.remove(invitee.getUniqueId());
        if (invite == null) return PartyResult.NO_INVITE;
        if (invite.expired()) return PartyResult.INVITE_EXPIRED;

        QuestParty party = invite.party();
        // The party may have dissolved or filled while the invite sat pending.
        if (!partyByPlayer.containsValue(party)) return PartyResult.INVITE_EXPIRED;
        if (party.size() >= getMaxSize()) return PartyResult.PARTY_FULL;

        party.add(invitee.getUniqueId());
        partyByPlayer.put(invitee.getUniqueId(), party);
        return PartyResult.OK;
    }

    public PartyResult leave(Player player) {
        return removeMember(player.getUniqueId());
    }

    public PartyResult disband(Player leader) {
        QuestParty party = partyByPlayer.get(leader.getUniqueId());
        if (party == null) return PartyResult.NO_PARTY;
        if (!party.isLeader(leader.getUniqueId())) return PartyResult.NOT_LEADER;
        dissolve(party);
        return PartyResult.OK;
    }

    /** Quit cleanup — same rules as leave, plus the pending-invite slot is dropped. */
    public void handleQuit(UUID player) {
        pendingInvites.remove(player);
        removeMember(player);
    }

    public void shutdown() {
        partyByPlayer.clear();
        pendingInvites.clear();
    }

    private PartyResult removeMember(UUID player) {
        QuestParty party = partyByPlayer.remove(player);
        if (party == null) return PartyResult.NO_PARTY;

        boolean wasLeader = party.isLeader(player);
        party.remove(player);

        if (party.size() < 2) {
            dissolve(party);
            return PartyResult.OK;
        }
        if (wasLeader) {
            UUID promoted = party.promoteNextLeader();
            notifyMembers(party, "§e⚠ §7Party leadership passed to §f" + nameOf(promoted) + "§7.");
        }
        return PartyResult.OK;
    }

    private void dissolve(QuestParty party) {
        for (UUID member : party.getMembers()) {
            partyByPlayer.remove(member);
        }
        notifyMembers(party, "§7Your quest party has dissolved.");
    }

    /** Best-effort broadcast to the (former) roster — used for promotion and dissolution notices. */
    private void notifyMembers(QuestParty party, String message) {
        for (UUID member : party.getMembers()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null) p.sendMessage(message);
        }
    }

    private String nameOf(UUID player) {
        Player p = player == null ? null : Bukkit.getPlayer(player);
        return p != null ? p.getName() : "another member";
    }

    // ==================== the presence rule ====================

    /**
     * Members of the firer's party who qualify for a beat that just fired: online, in the
     * checkpoint's world, and within {@code max(baseRadius, minShareRadius) * multiplier} of it.
     *
     * <p><b>Main-thread only</b> — it reads live Bukkit locations. Every current call site is a
     * sync event handler, but the guard makes the contract self-enforcing rather than a comment.
     * The snapshot is taken synchronously at the fire moment; a member who disconnects during the
     * later async write simply never qualified.</p>
     *
     * <p>Non-qualifying members are skipped silently by design (fall behind, catch up solo) —
     * one debug line each so QA can see the decision.</p>
     */
    public List<UUID> qualifyingMembers(UUID firer, PartyBeatContext ctx) {
        if (!Bukkit.isPrimaryThread()) {
            logger.warning("qualifyingMembers called off the main thread — returning no members");
            return List.of();
        }
        QuestParty party = partyByPlayer.get(firer);
        if (party == null) return List.of();

        double effective = Math.max(ctx.baseRadius(), getMinShareRadius()) * getShareRadiusMultiplier();
        double effSq = effective * effective;

        List<UUID> qualified = new ArrayList<>(party.size() - 1);
        for (UUID member : party.getMembers()) {
            if (member.equals(firer)) continue;
            Player p = Bukkit.getPlayer(member);
            if (p == null) {
                logger.debug("Party fan-out skip " + member + " — offline");
                continue;
            }
            if (!p.getWorld().getName().equalsIgnoreCase(ctx.worldName())) {
                logger.debug("Party fan-out skip " + p.getName() + " — wrong world ("
                        + p.getWorld().getName() + " vs " + ctx.worldName() + ")");
                continue;
            }
            double dx = p.getLocation().getX() - ctx.x();
            double dy = p.getLocation().getY() - ctx.y();
            double dz = p.getLocation().getZ() - ctx.z();
            if (dx * dx + dy * dy + dz * dz > effSq) {
                logger.debug("Party fan-out skip " + p.getName() + " — out of range (>"
                        + (int) effective + " blocks)");
                continue;
            }
            qualified.add(member);
        }
        return List.copyOf(qualified);
    }
}
