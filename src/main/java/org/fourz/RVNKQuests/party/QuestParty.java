package org.fourz.RVNKQuests.party;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * One quest party: a leader and an ordered member set.
 *
 * <p>Mutable by design — membership changes over the party's life — and therefore confined to the
 * main thread by its owner, {@link QuestPartyService}. Insertion order is meaningful: when the
 * leader leaves, the oldest remaining member is promoted, which is the least surprising rule for
 * players ("whoever has been here longest takes over").</p>
 *
 * @since 1.1.41 (#1982)
 */
public final class QuestParty {

    private final UUID partyId = UUID.randomUUID();
    private UUID leader;
    private final LinkedHashSet<UUID> members = new LinkedHashSet<>();

    QuestParty(UUID leader) {
        this.leader = leader;
        this.members.add(leader);
    }

    public UUID getPartyId() {
        return partyId;
    }

    public UUID getLeader() {
        return leader;
    }

    public boolean isLeader(UUID player) {
        return leader.equals(player);
    }

    public boolean contains(UUID player) {
        return members.contains(player);
    }

    /** Members in join order, leader first by construction. Defensive copy. */
    public List<UUID> getMembers() {
        return List.copyOf(members);
    }

    public int size() {
        return members.size();
    }

    boolean add(UUID player) {
        return members.add(player);
    }

    boolean remove(UUID player) {
        return members.remove(player);
    }

    /**
     * Promotes the oldest remaining member to leader after the current leader left.
     *
     * @return the new leader, or null when the party is empty
     */
    UUID promoteNextLeader() {
        leader = members.isEmpty() ? null : members.iterator().next();
        return leader;
    }
}
