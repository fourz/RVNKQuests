package org.fourz.RVNKQuests.service;

import org.bukkit.configuration.file.FileConfiguration;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.config.ConfigManager;
import org.fourz.RVNKQuests.data.DatabaseManager;
import org.fourz.RVNKQuests.data.IQuestProgressRepository;
import org.fourz.RVNKQuests.data.QuestProgressYamlRepository;
import org.fourz.RVNKQuests.data.dto.QuestProgressDTO;
import org.fourz.RVNKQuests.quest.QuestState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Quest progress service: the state-save result (#2277) and the quit-path eviction (#2278).
 * Runs on the YAML repository in a temp folder, with the MySQL repository swapped for a mock
 * where a test needs one.
 */
@DisplayName("QuestProgressServiceImpl")
class QuestProgressServiceImplTest {

    @TempDir
    File dataFolder;

    private QuestProgressServiceImpl service;

    @BeforeEach
    void setUp() {
        RVNKQuests plugin = mock(RVNKQuests.class);
        ConfigManager configManager = mock(ConfigManager.class);
        FileConfiguration config = mock(FileConfiguration.class);
        DatabaseManager databaseManager = mock(DatabaseManager.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getConfigManager()).thenReturn(configManager);
        when(configManager.getConfig()).thenReturn(config);
        when(config.getInt(anyString(), anyInt())).thenReturn(0); // no autosave thread work
        when(databaseManager.isAvailable()).thenReturn(false);    // YAML is the primary
        service = new QuestProgressServiceImpl(plugin, databaseManager);
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    private void setPrimary(IQuestProgressRepository repo) throws Exception {
        Field f = QuestProgressServiceImpl.class.getDeclaredField("primaryRepo");
        f.setAccessible(true);
        f.set(service, repo);
    }

    @Test
    @DisplayName("#2278: a player with no progress is evicted on quit, so offline reads are empty")
    void emptyProgressIsEvictedOnQuit() {
        UUID player = UUID.randomUUID();
        service.loadPlayerProgress(player).join();
        assertTrue(service.getCachedProgress(player).isPresent(), "online: an empty map is cached");

        service.saveAndUnloadPlayerProgress(player).join();

        assertEquals(Optional.empty(), service.getCachedProgress(player),
            "offline: no cache entry, so placeholders show '-' and memory is released");
    }

    @Test
    @DisplayName("#2277: fallback switching on mid-write lands the state in the YAML store")
    void falseSaveDuringFallbackFlipIsRetried() throws Exception {
        AtomicBoolean fallback = new AtomicBoolean(false);
        IQuestProgressRepository mysql = mock(IQuestProgressRepository.class);
        when(mysql.isInFallbackMode()).thenAnswer(inv -> fallback.get());
        when(mysql.getProgress(any(), anyString())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(mysql.saveProgress(any())).thenAnswer(inv -> {
            fallback.set(true); // the repo's own check sees fallback and drops the write
            return CompletableFuture.completedFuture(false);
        });
        when(mysql.flush()).thenReturn(CompletableFuture.completedFuture(null));
        setPrimary(mysql);

        UUID player = UUID.randomUUID();
        QuestProgressDTO saved = service.updateQuestState(player, "q1", QuestState.COMPLETED).join();

        assertEquals(QuestState.COMPLETED, saved.state());
        verify(mysql, times(1)).saveProgress(any()); // the retry went to the YAML store
    }

    @Test
    @DisplayName("#2277: false with no fallback is MySQL's unchanged row, which is stored")
    void unchangedRowCountsAsSaved() throws Exception {
        IQuestProgressRepository mysql = mock(IQuestProgressRepository.class);
        when(mysql.isInFallbackMode()).thenReturn(false);
        when(mysql.getProgress(any(), anyString())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(mysql.saveProgress(any())).thenReturn(CompletableFuture.completedFuture(false));
        when(mysql.flush()).thenReturn(CompletableFuture.completedFuture(null));
        setPrimary(mysql);

        QuestProgressDTO saved = service.updateQuestState(UUID.randomUUID(), "q1", QuestState.QUEST_ACTIVE).join();

        assertEquals(QuestState.QUEST_ACTIVE, saved.state());
        verify(mysql, times(2)).saveProgress(any()); // the write, then one retry
    }

    @Test
    @DisplayName("#2277: a state write stored nowhere fails the future, so no commit is reported")
    void unsavedStateFailsTheFuture() throws Exception {
        IQuestProgressRepository mysql = mock(IQuestProgressRepository.class);
        // getProgress and the write see MySQL; the retry and the final check see fallback.
        when(mysql.isInFallbackMode()).thenReturn(false, false, true);
        when(mysql.getProgress(any(), anyString())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(mysql.saveProgress(any())).thenReturn(CompletableFuture.completedFuture(false));
        when(mysql.flush()).thenReturn(CompletableFuture.completedFuture(null));
        setPrimary(mysql);
        QuestProgressYamlRepository deadYaml = mock(QuestProgressYamlRepository.class);
        when(deadYaml.saveProgress(any())).thenReturn(CompletableFuture.completedFuture(false));
        when(deadYaml.flush()).thenReturn(CompletableFuture.completedFuture(null));
        Field f = QuestProgressServiceImpl.class.getDeclaredField("fallbackRepo");
        f.setAccessible(true);
        f.set(service, deadYaml);

        CompletionException ex = assertThrows(CompletionException.class,
            () -> service.updateQuestState(UUID.randomUUID(), "q1", QuestState.COMPLETED).join());
        assertTrue(ex.getCause() instanceof IllegalStateException, String.valueOf(ex.getCause()));
    }
}
