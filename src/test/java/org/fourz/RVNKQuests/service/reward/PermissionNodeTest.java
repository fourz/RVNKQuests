package org.fourz.RVNKQuests.service.reward;

import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** PERMISSION rewards accept the node in metadata or in value (#2355). */
class PermissionNodeTest {

    private static RewardDTO reward(String value, Map<String, String> meta) {
        return RewardDTO.builder().rewardId("r").type(RewardType.PERMISSION).value(value).metadata(meta).build();
    }

    @Test
    void valueIsUsedWhenMetadataHasNoNode() {
        assertEquals("rvnkquests.title.fragile_tester",
                PermissionRewardProcessor.permissionNode(reward("rvnkquests.title.fragile_tester", Map.of())));
    }

    @Test
    void metadataWins() {
        assertEquals("group.vip",
                PermissionRewardProcessor.permissionNode(reward("ignored", Map.of("permission", "group.vip"))));
    }

    @Test
    void neitherIsNull() {
        assertNull(PermissionRewardProcessor.permissionNode(reward(" ", Map.of())));
    }
}
