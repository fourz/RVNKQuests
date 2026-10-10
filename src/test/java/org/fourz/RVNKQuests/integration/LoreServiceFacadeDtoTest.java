package org.fourz.RVNKQuests.integration;

import org.fourz.RVNKQuests.integration.dto.LoreEntryDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for #2212: RVNKLore's LoreEntry.getId() returns a String, and the facade cast it to
 * UUID, so every lore lookup by name became an "Error loading lore" stand-in shown to players.
 */
class LoreServiceFacadeDtoTest {

    enum StubType { GENERIC }

    /** Shape of RVNKLore's LoreEntry as the facade reads it: a String id. */
    public static class StringIdEntry {
        public String getId() { return "796b3f06-c3df-4632-982a-b30d8e1fd4bd"; }
        public String getName() { return "npc_wayfarer_greeter_offer"; }
        public String getDescription() { return "Welcome, traveler."; }
        public StubType getType() { return StubType.GENERIC; }
    }

    /** A UUID id still converts, in case a future LoreEntry returns one. */
    public static class UuidIdEntry {
        public UUID getId() { return UUID.fromString("796b3f06-c3df-4632-982a-b30d8e1fd4bd"); }
        public String getName() { return "n"; }
        public String getDescription() { return "d"; }
        public StubType getType() { return StubType.GENERIC; }
    }

    public static class NoDescriptionMethod {
        public String getId() { return "x"; }
        public String getName() { return "n"; }
    }

    @Test
    @DisplayName("String id (RVNKLore's real shape) converts with the entry's own text")
    void stringIdConverts() throws Exception {
        LoreEntryDTO dto = LoreServiceFacade.toDto(new StringIdEntry());
        assertEquals("796b3f06-c3df-4632-982a-b30d8e1fd4bd", dto.id());
        assertEquals("npc_wayfarer_greeter_offer", dto.name());
        assertEquals("Welcome, traveler.", dto.description());
        assertEquals("GENERIC", dto.type());
    }

    @Test
    @DisplayName("UUID id still converts")
    void uuidIdConverts() throws Exception {
        assertEquals("796b3f06-c3df-4632-982a-b30d8e1fd4bd", LoreServiceFacade.toDto(new UuidIdEntry()).id());
    }

    @Test
    @DisplayName("an unreadable entry throws, so the caller maps it to empty - never a stand-in")
    void unreadableEntryThrows() {
        assertThrows(ReflectiveOperationException.class, () -> LoreServiceFacade.toDto(new NoDescriptionMethod()));
    }
}
