package org.fourz.RVNKQuests.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@code quest debug fire} gate matrix (#2265). Same rules as RVNKCore's NpcClickGate (#2255).
 */
@DisplayName("QaFireGate matrix (#2265)")
class QaFireGateTest {

    @ParameterizedTest(name = "dev tier ''{0}'' allows any target")
    @ValueSource(strings = {"dev", "test", "DEV", " Test ", "dEv"})
    void devTierAllowsAnyTarget(String tier) {
        assertTrue(QaFireGate.evaluate(tier, true, false).allowed());
        assertTrue(QaFireGate.evaluate(tier, true, true).allowed());
    }

    @ParameterizedTest(name = "tier ''{0}'' needs a QA-subject target")
    @ValueSource(strings = {"event", "nations", "EVENT", "staging", "prod"})
    void nonDevTierNeedsQaSubjectTarget(String tier) {
        QaFireGate.Decision refused = QaFireGate.evaluate(tier, true, false);
        assertFalse(refused.allowed());
        assertTrue(refused.reason().contains(QaFireGate.PERM_QA_SUBJECT), refused.reason());

        QaFireGate.Decision allowed = QaFireGate.evaluate(tier, true, true);
        assertTrue(allowed.allowed());
        assertTrue(allowed.reason().contains(QaFireGate.PERM_QA_SUBJECT), allowed.reason());
    }

    @ParameterizedTest(name = "unknown tier ''{0}'' refuses even a QA subject")
    @NullSource
    @ValueSource(strings = {"", "   ", "local", "LOCAL", " local "})
    void unknownTierRefusesEveryone(String tier) {
        assertTrue(QaFireGate.isUnknownTier(tier));
        QaFireGate.Decision d = QaFireGate.evaluate(tier, true, true);
        assertFalse(d.allowed());
        assertTrue(d.reason().contains("unknown"), d.reason());
    }

    @ParameterizedTest(name = "sender without admin is refused on ''{0}''")
    @ValueSource(strings = {"dev", "test", "event", "nations"})
    void senderNeedsAdminOnEveryTier(String tier) {
        QaFireGate.Decision d = QaFireGate.evaluate(tier, false, true);
        assertFalse(d.allowed());
        assertTrue(d.reason().contains(QaFireGate.PERM_ADMIN), d.reason());
    }

    @Test
    @DisplayName("the sender lacking admin is reported before an unknown tier")
    void adminCheckComesFirst() {
        QaFireGate.Decision d = QaFireGate.evaluate(null, false, true);
        assertFalse(d.allowed());
        assertTrue(d.reason().contains(QaFireGate.PERM_ADMIN), d.reason());
    }

    @Test
    @DisplayName("the permission node is the one RVNKCore's NPC click gate uses")
    void sameNodeAsRvnkCore() {
        assertEquals("rvnkcore.qa.subject", QaFireGate.PERM_QA_SUBJECT);
        assertFalse(QaFireGate.isUnknownTier("event"));
    }
}
