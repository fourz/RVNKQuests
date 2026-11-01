package org.fourz.RVNKQuests.objective.generic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.DataDrivenQuest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #2004: quest mobs carry their quest and owner in persistent data, and the owner's next visit adopts the survivors of
 * a wave from before a restart (re-tagged, capped at spawn_count) instead of stacking a new wave.
 */
@DisplayName("encounter: a wave alive across a restart is adopted, not orphaned (#2004)")
class EncounterAdoptTest {

    private final RVNKQuests plugin = mock(RVNKQuests.class);
    private final DataDrivenQuest quest = mock(DataDrivenQuest.class);
    private final World world = mock(World.class);
    private final UUID owner = UUID.randomUUID();

    private GenericEncounterObjective encounter(int spawnCount) {
        when(quest.getId()).thenReturn("tfah_ch1_archea");
        Map<String, Object> c = new HashMap<>();
        c.put("objective_type", "ENCOUNTER");
        c.put("entity_type", "ZOMBIFIED_PIGLIN");
        c.put("spawn_count", spawnCount);
        c.put("world", "alphac");
        c.put("x", 1502);
        c.put("y", 97);
        c.put("z", 2192);
        return new GenericEncounterObjective(plugin, quest, c);
    }

    /** A mob with persistent data, as it reloads after a restart: no runtime metadata. */
    private Entity mob(EntityType type, String questId, String ownerId, boolean tracked) {
        Entity e = mock(Entity.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(e.getType()).thenReturn(type);
        when(e.isValid()).thenReturn(true);
        when(e.isDead()).thenReturn(false);
        when(e.hasMetadata(anyString())).thenReturn(tracked);
        when(e.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(eq(GenericEncounterObjective.QUEST_KEY), eq(PersistentDataType.STRING))).thenReturn(questId);
        when(pdc.get(eq(GenericEncounterObjective.OWNER_KEY), eq(PersistentDataType.STRING))).thenReturn(ownerId);
        return e;
    }

    @Test
    @DisplayName("adopts only this owner's untracked mobs of this quest and type, up to spawn_count")
    void adoptsTheOwnersSurvivors() {
        GenericEncounterObjective enc = encounter(3);
        String me = owner.toString();
        List<Entity> around = new ArrayList<>(List.of(
                mob(EntityType.ZOMBIFIED_PIGLIN, "tfah_ch1_archea", me, false),      // adopt
                mob(EntityType.ZOMBIFIED_PIGLIN, "tfah_ch1_archea", UUID.randomUUID().toString(), false), // someone else's
                mob(EntityType.ZOMBIFIED_PIGLIN, "other_quest", me, false),          // another quest
                mob(EntityType.ZOMBIE, "tfah_ch1_archea", me, false),                // wrong type
                mob(EntityType.ZOMBIFIED_PIGLIN, "tfah_ch1_archea", me, true),       // already tracked this session
                mob(EntityType.ZOMBIFIED_PIGLIN, "tfah_ch1_archea", me, false),      // adopt
                mob(EntityType.ZOMBIFIED_PIGLIN, "tfah_ch1_archea", me, false),      // adopt
                mob(EntityType.ZOMBIFIED_PIGLIN, "tfah_ch1_archea", me, false)));    // over spawn_count
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble())).thenReturn(around);

        List<Entity> adopted = enc.adopt(world, new Location(world, 1502, 97, 2192), owner);

        assertEquals(3, adopted.size(), "capped at spawn_count");
        assertTrue(adopted.contains(around.get(0)) && adopted.contains(around.get(5)) && adopted.contains(around.get(6)));
        for (Entity e : adopted) {
            verify(e).setMetadata(anyString(), any());                       // tracked again for this session
            verify(e.getPersistentDataContainer()).set(eq(GenericEncounterObjective.OWNER_KEY), eq(PersistentDataType.STRING), eq(me));
        }
        verify(around.get(1), never()).setMetadata(anyString(), any());
        verify(around.get(4), never()).setMetadata(anyString(), any());
    }

    @Test
    @DisplayName("the persistent keys are rvnkquests:quest_mob and rvnkquests:quest_mob_owner")
    void keys() {
        assertEquals(new NamespacedKey("rvnkquests", "quest_mob"), GenericEncounterObjective.QUEST_KEY);
        assertEquals(new NamespacedKey("rvnkquests", "quest_mob_owner"), GenericEncounterObjective.OWNER_KEY);
    }
}
