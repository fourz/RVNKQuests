package org.fourz.RVNKQuests.objective.generic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.fourz.RVNKQuests.party.QuestParty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #2067: an encounter mob's kill earns credit for the wave owner only when the owner or a member of
 * the owner's quest party killed it. A bystander, including a player who already finished the
 * encounter, clears nothing for anyone.
 */
@DisplayName("encounter: kill credit goes to the owner only from the owner or the owner's party (#2067)")
class EncounterKillCreditTest {

    private final UUID owner = UUID.randomUUID();
    private final UUID partyMate = UUID.randomUUID();
    private final UUID bystander = UUID.randomUUID();

    private QuestParty partyOf(UUID... members) {
        QuestParty party = mock(QuestParty.class);
        for (UUID m : members) when(party.contains(m)).thenReturn(true);
        return party;
    }

    @Test
    @DisplayName("the owner's own kill counts, with or without a party")
    void ownerKillCounts() {
        assertTrue(GenericEncounterObjective.killerEarnsOwnerCredit(owner, owner, null));
        assertTrue(GenericEncounterObjective.killerEarnsOwnerCredit(owner, owner, partyOf(owner, partyMate)));
    }

    @Test
    @DisplayName("a party member's kill counts for the owner")
    void partyMemberKillCounts() {
        assertTrue(GenericEncounterObjective.killerEarnsOwnerCredit(owner, partyMate, partyOf(owner, partyMate)));
    }

    @Test
    @DisplayName("a bystander with no party link earns the owner nothing (the Event report)")
    void bystanderKillDoesNotCount() {
        assertFalse(GenericEncounterObjective.killerEarnsOwnerCredit(owner, bystander, null),
                "owner in no party: a COMPLETED player's kill must not clear the owner's wave");
        assertFalse(GenericEncounterObjective.killerEarnsOwnerCredit(owner, bystander, partyOf(owner, partyMate)),
                "owner in a party the killer is not in");
    }
}
