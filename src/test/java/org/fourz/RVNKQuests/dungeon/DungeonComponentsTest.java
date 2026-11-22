package org.fourz.RVNKQuests.dungeon;

import org.fourz.RVNKQuests.data.dto.ObjectiveType;
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;
import org.fourz.RVNKQuests.data.dto.TriggerType;
import org.fourz.RVNKQuests.service.IRewardService.RewardDeliveryResult;
import org.fourz.RVNKQuests.service.reward.DungeonResetRewardProcessor;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Dungeon quest components (#2371): types, guard, and the reset reward's failure paths. */
class DungeonComponentsTest {

    private static RewardDTO reward(String value) {
        return RewardDTO.builder().rewardId("r_reset").type(RewardType.DUNGEON_RESET).value(value).build();
    }

    @Test
    void typesExist() {
        assertEquals(TriggerType.DUNGEON_ENTER, TriggerType.valueOf("DUNGEON_ENTER"));
        assertEquals(TriggerType.DUNGEON_LEAVE, TriggerType.valueOf("DUNGEON_LEAVE"));
        assertEquals(ObjectiveType.DUNGEON_CLEAR, ObjectiveType.valueOf("DUNGEON_CLEAR"));
        assertEquals(RewardType.DUNGEON_RESET, RewardType.valueOf("DUNGEON_RESET"));
    }

    @Test
    void contractIsOnTheClasspath() {
        assertTrue(DungeonApi.isPresent(), "built against RVNKCore 1.5.112-alpha");
    }

    @Test
    void resetRewardWithoutIdFails() throws Exception {
        RewardDeliveryResult r = new DungeonResetRewardProcessor().deliver(UUID.randomUUID(), reward(" "))
                .get(2, TimeUnit.SECONDS);
        assertFalse(r.success());
        assertEquals("EMPTY_DUNGEON", r.errorCode());
    }

    @Test
    void resetRewardWithoutProviderReportsUnavailable() throws Exception {
        // No RVNKCore instance in a unit test, so Dungeons.resolve() gives the UNAVAILABLE service.
        RewardDeliveryResult r = new DungeonResetRewardProcessor().deliver(UUID.randomUUID(), reward("test_ruin"))
                .get(2, TimeUnit.SECONDS);
        assertFalse(r.success());
        assertEquals("DUNGEON_UNAVAILABLE", r.errorCode());
    }

    @Test
    void preflightWithoutProviderIsABlocker() {
        assertTrue(DungeonPreflight.describe("test_ruin").startsWith("blocker:"));
    }
}
