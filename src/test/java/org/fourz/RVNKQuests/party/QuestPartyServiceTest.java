package org.fourz.RVNKQuests.party;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.config.ConfigManager;
import org.fourz.RVNKQuests.quest.QuestState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Membership lifecycle and the 5x presence rule (#1982).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QuestPartyService (#1982)")
class QuestPartyServiceTest {

    @Mock private RVNKQuests plugin;
    @Mock private ConfigManager configManager;
    @Mock private Server server;

    private QuestPartyService service;

    private Player leader;
    private Player member;
    private Player outsider;

    @BeforeEach
    void setUp() throws Exception {
        when(plugin.getConfigManager()).thenReturn(configManager);
        // Config reads fall through to the caller-supplied default.
        when(configManager.getConfigValue(anyString(), any()))
            .thenAnswer(inv -> inv.getArgument(1));

        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);
        when(server.isPrimaryThread()).thenReturn(true);

        service = new QuestPartyService(plugin);

        leader = onlinePlayer("Leader");
        member = onlinePlayer("Member");
        outsider = onlinePlayer("Outsider");
    }

    private Player onlinePlayer(String name) {
        Player p = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(p.getUniqueId()).thenReturn(id);
        when(p.getName()).thenReturn(name);
        when(server.getPlayer(id)).thenReturn(p);
        return p;
    }

    private void placeAt(Player p, String worldName, double x, double y, double z) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        Location loc = mock(Location.class);
        when(loc.getX()).thenReturn(x);
        when(loc.getY()).thenReturn(y);
        when(loc.getZ()).thenReturn(z);
        when(p.getWorld()).thenReturn(world);
        when(p.getLocation()).thenReturn(loc);
    }

    private void formParty() {
        assertEquals(QuestPartyService.PartyResult.OK, service.invite(leader, member));
        assertEquals(QuestPartyService.PartyResult.OK, service.accept(member));
    }

    // ==================== lifecycle ====================

    @Test
    @DisplayName("invite + accept forms a party with the inviter as leader")
    void inviteAcceptFormsParty() {
        formParty();
        QuestParty party = service.getParty(leader.getUniqueId());
        assertNotNull(party);
        assertSame(party, service.getParty(member.getUniqueId()));
        assertTrue(party.isLeader(leader.getUniqueId()));
        assertEquals(2, party.size());
    }

    @Test
    @DisplayName("self-invite and double-party invites are rejected")
    void inviteRejections() {
        assertEquals(QuestPartyService.PartyResult.SELF_INVITE, service.invite(leader, leader));
        formParty();
        assertEquals(QuestPartyService.PartyResult.TARGET_IN_PARTY, service.invite(outsider, member));
        // Non-leader cannot invite into the party they belong to
        assertEquals(QuestPartyService.PartyResult.NOT_LEADER, service.invite(member, outsider));
    }

    @Test
    @DisplayName("accept with no invite / expired invite fails")
    void acceptRejections() {
        assertEquals(QuestPartyService.PartyResult.NO_INVITE, service.accept(member));

        // Negative timeout puts expiry in the past deterministically.
        when(configManager.getConfigValue(eq("quests.party.invite_timeout_seconds"), any()))
            .thenReturn(-1.0);
        assertEquals(QuestPartyService.PartyResult.OK, service.invite(leader, member));
        assertEquals(QuestPartyService.PartyResult.INVITE_EXPIRED, service.accept(member));
    }

    @Test
    @DisplayName("max_size caps membership")
    void maxSizeEnforced() {
        when(configManager.getConfigValue(eq("quests.party.max_size"), any())).thenReturn(2.0);
        formParty();
        assertEquals(QuestPartyService.PartyResult.PARTY_FULL, service.invite(leader, outsider));
    }

    @Test
    @DisplayName("leader leaving promotes the oldest member; a party of one dissolves")
    void leaderPromotionAndDissolution() {
        Player third = onlinePlayer("Third");
        formParty();
        assertEquals(QuestPartyService.PartyResult.OK, service.invite(leader, third));
        assertEquals(QuestPartyService.PartyResult.OK, service.accept(third));

        assertEquals(QuestPartyService.PartyResult.OK, service.leave(leader));
        QuestParty party = service.getParty(member.getUniqueId());
        assertNotNull(party);
        assertTrue(party.isLeader(member.getUniqueId()), "oldest member becomes leader");

        // Down to one -> dissolves
        assertEquals(QuestPartyService.PartyResult.OK, service.leave(third));
        assertNull(service.getParty(member.getUniqueId()));
    }

    @Test
    @DisplayName("quit removes the player and dissolves a party of two")
    void quitCleansUp() {
        formParty();
        service.handleQuit(member.getUniqueId());
        assertNull(service.getParty(member.getUniqueId()));
        assertNull(service.getParty(leader.getUniqueId()), "party of one dissolves");
    }

    @Test
    @DisplayName("only the leader can disband")
    void disbandLeaderOnly() {
        formParty();
        assertEquals(QuestPartyService.PartyResult.NOT_LEADER, service.disband(member));
        assertEquals(QuestPartyService.PartyResult.OK, service.disband(leader));
        assertNull(service.getParty(leader.getUniqueId()));
    }

    // ==================== the presence rule ====================

    private static final PartyBeatContext CTX =
        new PartyBeatContext("alphac", 0, 64, 0, 20.0, QuestState.NOT_STARTED);

    @Test
    @DisplayName("member inside 5x radius, same world, qualifies")
    void insideRadiusQualifies() {
        formParty();
        // effective = max(20, 10) * 5 = 100
        placeAt(member, "alphac", 60, 64, 0);
        List<UUID> qualified = service.qualifyingMembers(leader.getUniqueId(), CTX);
        assertEquals(List.of(member.getUniqueId()), qualified);
    }

    @Test
    @DisplayName("member beyond 5x radius does not qualify")
    void outsideRadiusSkipped() {
        formParty();
        placeAt(member, "alphac", 150, 64, 0);
        assertTrue(service.qualifyingMembers(leader.getUniqueId(), CTX).isEmpty());
    }

    @Test
    @DisplayName("member in another world does not qualify")
    void wrongWorldSkipped() {
        formParty();
        placeAt(member, "zeal", 10, 64, 0);
        assertTrue(service.qualifyingMembers(leader.getUniqueId(), CTX).isEmpty());
    }

    @Test
    @DisplayName("offline member does not qualify")
    void offlineSkipped() {
        formParty();
        when(server.getPlayer(member.getUniqueId())).thenReturn(null);
        assertTrue(service.qualifyingMembers(leader.getUniqueId(), CTX).isEmpty());
    }

    @Test
    @DisplayName("radius floor governs radius-less beats (kills)")
    void radiusFloorApplies() {
        formParty();
        PartyBeatContext killCtx = new PartyBeatContext("alphac", 0, 64, 0, 0.0, QuestState.QUEST_ACTIVE);
        // effective = max(0, 10) * 5 = 50
        placeAt(member, "alphac", 45, 64, 0);
        assertEquals(1, service.qualifyingMembers(leader.getUniqueId(), killCtx).size());
        placeAt(member, "alphac", 60, 64, 0);
        assertTrue(service.qualifyingMembers(leader.getUniqueId(), killCtx).isEmpty());
    }

    @Test
    @DisplayName("no party -> no members; off-main-thread -> empty")
    void guards() {
        assertTrue(service.qualifyingMembers(leader.getUniqueId(), CTX).isEmpty(), "no party");

        formParty();
        placeAt(member, "alphac", 10, 64, 0);
        when(server.isPrimaryThread()).thenReturn(false);
        assertTrue(service.qualifyingMembers(leader.getUniqueId(), CTX).isEmpty(),
            "location reads are main-thread only");
    }
}
