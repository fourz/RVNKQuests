package org.fourz.RVNKQuests.reward;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.fourz.RVNKQuests.data.dto.RewardDTO;
import org.fourz.RVNKQuests.data.dto.RewardType;
import org.fourz.RVNKQuests.service.OnceRewards;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Parsing a component's {@code on_advance} list (#2267). */
@DisplayName("on_advance parsing (#2267)")
class OnAdvanceRewardsTest {

    private static Map<String, Object> entry(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    @Test
    @DisplayName("entries sort by reward_id, the order quest_definition_rewards is read in")
    void sortsByRewardId() {
        List<Object> raw = List.of(
                entry("reward_id", "b_xp", "type", "EXPERIENCE", "amount", 50),
                entry("reward_id", "a_key", "type", "COMMAND", "value", "lore item give %player% Lodestone Key"),
                entry("reward_id", "C_item", "type", "item", "value", "TRIPWIRE_HOOK", "amount", 2));
        List<String> problems = new ArrayList<>();
        List<RewardDTO> out = OnAdvanceRewards.parse("obj_gold_door", raw, problems);

        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(List.of("a_key", "b_xp", "C_item"), out.stream().map(RewardDTO::rewardId).toList());
        assertEquals(RewardType.COMMAND, out.get(0).type());
        assertEquals("lore item give %player% Lodestone Key", out.get(0).value());
        assertEquals(50, out.get(1).amount());
        assertEquals(RewardType.ITEM, out.get(2).type());
        assertEquals(2, out.get(2).amount());
    }

    @Test
    @DisplayName("unnamed entries get <component>_NN ids and keep their list order")
    void defaultIdsKeepListOrder() {
        List<Object> raw = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            raw.add(entry("type", "COMMAND", "value", "say " + i));
        }
        List<RewardDTO> out = OnAdvanceRewards.parse("door", raw, null);
        assertEquals(11, out.size());
        for (int i = 0; i < 11; i++) {
            assertEquals("say " + i, out.get(i).value(), "position " + i);
        }
        assertEquals("door_01", out.get(0).rewardId());
        assertEquals("door_11", out.get(10).rewardId());
    }

    @Test
    @DisplayName("a map keyed by reward_id is accepted, like the rewards: section")
    void mapFormAccepted() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("z_last", entry("type", "COMMAND", "value", "say z"));
        raw.put("a_first", entry("type", "COMMAND", "value", "say a"));
        List<RewardDTO> out = OnAdvanceRewards.parse("door", raw, null);
        assertEquals(List.of("a_first", "z_last"), out.stream().map(RewardDTO::rewardId).toList());
    }

    @Test
    @DisplayName("a bad entry is skipped and reported; the rest still load")
    void badEntriesSkipped() {
        List<Object> raw = List.of(
                entry("reward_id", "ok", "type", "COMMAND", "value", "say ok"),
                entry("reward_id", "no_type", "value", "x"),
                entry("reward_id", "bad_type", "type", "TELEPORT"),
                "not a map",
                entry("reward_id", "typo", "type", "COMMAND", "valeu", "x"));
        List<String> problems = new ArrayList<>();
        List<RewardDTO> out = OnAdvanceRewards.parse("door", raw, problems);
        assertEquals(List.of("ok", "typo"), out.stream().map(RewardDTO::rewardId).toList());
        assertEquals(4, problems.size(), problems.toString());
        assertTrue(OnAdvanceRewards.parse("door", "nonsense", problems).isEmpty());
        assertTrue(OnAdvanceRewards.parse("door", null, null).isEmpty());
    }

    @Test
    @DisplayName("once: server becomes the reward metadata flag; another value is ignored and reported")
    void onceFlag() {
        List<String> problems = new ArrayList<>();
        List<RewardDTO> out = OnAdvanceRewards.parse("door", List.of(
                entry("reward_id", "a", "type", "COMMAND", "value", "setblock 1 2 3 air", "once", "server"),
                entry("reward_id", "b", "type", "COMMAND", "value", "say b", "once", "player")), problems);
        assertTrue(OnceRewards.isOnceServer(out.get(0)));
        assertFalse(OnceRewards.isOnceServer(out.get(1)));
        assertEquals(1, problems.size(), problems.toString());
    }

    @Test
    @DisplayName("survives the metadata JSON round trip: Gson reads numbers back as doubles")
    void survivesGsonRoundTrip() {
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("objective_type", "INTERACT");
        component.put("on_advance", List.of(
                entry("reward_id", "a_key", "type", "ITEM", "value", "TRIPWIRE_HOOK", "amount", 3),
                entry("reward_id", "b_xp", "type", "EXPERIENCE", "value", 50, "amount", 50)));
        Gson gson = new Gson();
        Map<String, Object> back = gson.fromJson(gson.toJson(component),
                new TypeToken<Map<String, Object>>() { }.getType());

        List<RewardDTO> before = OnAdvanceRewards.parse("door", component.get("on_advance"), null);
        List<RewardDTO> after = OnAdvanceRewards.parse("door", back.get("on_advance"), null);
        assertEquals(before, after);
        assertEquals("50", after.get(1).value());
    }
}
