package org.fourz.RVNKQuests.data.dto;

import java.util.UUID;

/**
 * Data Transfer Object for persisted quest chain progress.
 *
 * <p>Represents a single player's progress snapshot for one chain.
 * Stored in the {@code quest_chain_progress} table and used as the
 * write-through record alongside the in-memory cache in
 * {@code QuestChainServiceImpl}.</p>
 *
 * <p>Timestamps use epoch milliseconds (consistent with
 * {@code ChainProgressData.startedAt} / {@code lastUpdated} fields).</p>
 *
 * @param playerUuid    The player's UUID
 * @param chainId       The chain identifier
 * @param currentQuestId The active quest ID the player is on, or null if not started
 * @param completed     Whether the chain has been completed at least once
 * @param startedAt     Epoch millis when the chain was first started (0 if not started)
 * @param lastUpdated   Epoch millis of the last progress change
 *
 * @since 1.1
 */
public record QuestChainProgressDTO(
    UUID playerUuid,
    String chainId,
    String currentQuestId,
    boolean completed,
    long startedAt,
    long lastUpdated
) {}
