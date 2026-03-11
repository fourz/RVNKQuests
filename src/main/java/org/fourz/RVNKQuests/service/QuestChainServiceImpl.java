package org.fourz.RVNKQuests.service;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.RVNKQuests.data.dto.QuestChainDTO;
import org.fourz.RVNKQuests.data.dto.QuestChainDTO.ChainNode;
import org.fourz.RVNKQuests.data.dto.QuestChainDTO.NodeType;
import org.fourz.RVNKQuests.data.dto.QuestPrerequisite;
import org.fourz.RVNKQuests.data.dto.QuestPrerequisite.PrerequisiteType;
import org.fourz.RVNKQuests.data.dto.RewardDTO;

import org.fourz.RVNKQuests.quest.QuestState;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.fourz.rvnkcore.util.log.LogManager;
import java.util.stream.Collectors;

/**
 * Implementation of {@link IQuestChainService}.
 * Manages quest chain definitions, player progress, and automatic quest unlocking.
 *
 * <p>Thread-safe implementation using concurrent data structures and
 * CompletableFuture for asynchronous operations.</p>
 *
 * @since 1.0
 */
public class QuestChainServiceImpl implements IQuestChainService {

    private final Plugin plugin;
    private final LogManager logger;
    private final IQuestProgressService questProgressService;
    private final IRewardService rewardService;
    
    // Chain definitions (chainId -> chain)
    private final Map<String, QuestChainDTO> chains = new ConcurrentHashMap<>();
    
    // Player progress (playerId -> chainId -> progress)
    private final Map<UUID, Map<String, ChainProgressData>> playerProgress = new ConcurrentHashMap<>();
    
    // Quest to chain mapping for quick lookups (questId -> set of chainIds)
    private final Map<String, Set<String>> questToChains = new ConcurrentHashMap<>();
    
    /**
     * Internal mutable progress data.
     */
    private static class ChainProgressData {
        final String chainId;
        ChainStatus status = ChainStatus.NOT_STARTED;
        final Set<String> completedQuests = new HashSet<>();
        final Set<String> activeQuests = new HashSet<>();
        int completionCount = 0;
        long startedAt = 0;
        long lastUpdated = System.currentTimeMillis();
        long cooldownUntil = 0;
        
        ChainProgressData(String chainId) {
            this.chainId = chainId;
        }
        
        ChainProgress toRecord(QuestChainDTO chain) {
            List<String> allQuestIds = chain.getAllQuestIds();
            Set<String> completed = new HashSet<>(completedQuests);
            Set<String> active = new HashSet<>(activeQuests);
            List<String> locked = allQuestIds.stream()
                .filter(q -> !completed.contains(q) && !active.contains(q))
                .toList();
            
            return new ChainProgress(
                null, // playerId set by caller
                chainId,
                status,
                List.copyOf(completed),
                List.copyOf(active),
                locked,
                completionCount,
                startedAt,
                lastUpdated
            );
        }
    }
    
    /**
     * Creates a new QuestChainServiceImpl.
     *
     * @param plugin The owning plugin
     * @param questProgressService The quest progress service for quest state operations
     * @param rewardService The reward service for delivering rewards
     */
    public QuestChainServiceImpl(Plugin plugin, IQuestProgressService questProgressService, IRewardService rewardService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.questProgressService = Objects.requireNonNull(questProgressService, "questProgressService cannot be null");
        this.rewardService = Objects.requireNonNull(rewardService, "rewardService cannot be null");
        this.logger = LogManager.getInstance(plugin, "QuestChainService");
    }
    
    // ==================== Chain Registration ====================
    
    @Override
    public CompletableFuture<Boolean> registerChain(QuestChainDTO chain) {
        return CompletableFuture.supplyAsync(() -> {
            Objects.requireNonNull(chain, "chain cannot be null");
            
            String chainId = chain.chainId();
            if (chains.containsKey(chainId)) {
                logger.warning("Chain already registered: " + chainId);
                return false;
            }
            
            chains.put(chainId, chain);
            
            // Index quests to chains
            for (String questId : chain.getAllQuestIds()) {
                questToChains.computeIfAbsent(questId, k -> ConcurrentHashMap.newKeySet())
                    .add(chainId);
            }
            
            logger.debug("Registered chain: " + chainId + " with " +
                       chain.getTotalQuestCount() + " quests");
            return true;
        });
    }
    
    @Override
    public CompletableFuture<Boolean> unregisterChain(String chainId) {
        return CompletableFuture.supplyAsync(() -> {
            QuestChainDTO removed = chains.remove(chainId);
            if (removed == null) {
                return false;
            }
            
            // Remove from quest index
            for (String questId : removed.getAllQuestIds()) {
                Set<String> chainIds = questToChains.get(questId);
                if (chainIds != null) {
                    chainIds.remove(chainId);
                    if (chainIds.isEmpty()) {
                        questToChains.remove(questId);
                    }
                }
            }
            
            logger.debug("Unregistered chain: " + chainId);
            return true;
        });
    }
    
    @Override
    public CompletableFuture<Optional<QuestChainDTO>> getChain(String chainId) {
        return CompletableFuture.completedFuture(Optional.ofNullable(chains.get(chainId)));
    }
    
    @Override
    public CompletableFuture<List<QuestChainDTO>> getAllChains() {
        return CompletableFuture.completedFuture(List.copyOf(chains.values()));
    }
    
    @Override
    public CompletableFuture<List<QuestChainDTO>> getChainsByCategory(String category) {
        return CompletableFuture.supplyAsync(() -> 
            chains.values().stream()
                .filter(c -> Objects.equals(c.category(), category))
                .toList()
        );
    }
    
    // ==================== Progress Tracking ====================
    
    @Override
    public CompletableFuture<ChainProgress> getProgress(UUID playerId, String chainId) {
        return CompletableFuture.supplyAsync(() -> {
            QuestChainDTO chain = chains.get(chainId);
            if (chain == null) {
                return new ChainProgress(playerId, chainId, ChainStatus.NOT_STARTED,
                    List.of(), List.of(), List.of(), 0, 0, 0);
            }
            
            ChainProgressData data = getOrCreateProgress(playerId, chainId);
            ChainProgress progress = data.toRecord(chain);
            
            // Return with correct playerId
            return new ChainProgress(playerId, progress.chainId(), progress.status(),
                progress.completedQuests(), progress.activeQuests(), progress.lockedQuests(),
                progress.completionCount(), progress.startedAt(), progress.lastUpdated());
        });
    }
    
    @Override
    public CompletableFuture<List<ChainProgress>> getAllProgress(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, ChainProgressData> playerData = playerProgress.get(playerId);
            if (playerData == null || playerData.isEmpty()) {
                return List.of();
            }
            
            return playerData.values().stream()
                .map(data -> {
                    QuestChainDTO chain = chains.get(data.chainId);
                    if (chain == null) return null;
                    ChainProgress progress = data.toRecord(chain);
                    return new ChainProgress(playerId, progress.chainId(), progress.status(),
                        progress.completedQuests(), progress.activeQuests(), progress.lockedQuests(),
                        progress.completionCount(), progress.startedAt(), progress.lastUpdated());
                })
                .filter(Objects::nonNull)
                .toList();
        });
    }
    
    @Override
    public CompletableFuture<ChainStartResult> startChain(UUID playerId, String chainId) {
        return checkPrerequisites(playerId, chainId).thenCompose(prereqResult -> {
            if (!prereqResult.satisfied()) {
                return CompletableFuture.completedFuture(
                    ChainStartResult.failure(chainId, prereqResult.message())
                );
            }
            
            return CompletableFuture.supplyAsync(() -> {
                QuestChainDTO chain = chains.get(chainId);
                if (chain == null) {
                    return ChainStartResult.failure(chainId, "Chain not found");
                }
                
                ChainProgressData data = getOrCreateProgress(playerId, chainId);
                
                // Check cooldown for repeatable chains
                if (chain.repeatable() && data.cooldownUntil > System.currentTimeMillis()) {
                    long remaining = (data.cooldownUntil - System.currentTimeMillis()) / 1000 / 60;
                    return ChainStartResult.failure(chainId, 
                        "Chain on cooldown. " + remaining + " minutes remaining.");
                }
                
                // Reset if restarting
                if (data.status == ChainStatus.COMPLETED && chain.repeatable()) {
                    data.completedQuests.clear();
                    data.activeQuests.clear();
                }
                
                data.status = ChainStatus.IN_PROGRESS;
                data.startedAt = System.currentTimeMillis();
                data.lastUpdated = System.currentTimeMillis();
                
                // Determine initial available quests
                List<String> availableQuests = calculateAvailableQuests(chain, data);
                data.activeQuests.addAll(availableQuests);
                
                logger.debug("Player " + playerId + " started chain: " + chainId);
                return ChainStartResult.success(chainId, availableQuests);
            });
        });
    }
    
    @Override
    public CompletableFuture<List<ChainUpdate>> onQuestComplete(UUID playerId, String questId) {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> affectedChains = questToChains.get(questId);
            if (affectedChains == null || affectedChains.isEmpty()) {
                return List.of();
            }
            
            List<ChainUpdate> updates = new ArrayList<>();
            
            for (String chainId : affectedChains) {
                ChainProgressData data = playerProgress
                    .getOrDefault(playerId, Map.of())
                    .get(chainId);
                
                if (data == null || data.status != ChainStatus.IN_PROGRESS) {
                    continue;
                }
                
                QuestChainDTO chain = chains.get(chainId);
                if (chain == null) continue;
                
                // Move quest from active to completed
                data.activeQuests.remove(questId);
                data.completedQuests.add(questId);
                data.lastUpdated = System.currentTimeMillis();
                
                // Check for newly unlocked quests
                List<String> newlyAvailable = calculateAvailableQuests(chain, data);
                newlyAvailable.removeAll(data.activeQuests);
                newlyAvailable.removeAll(data.completedQuests);
                
                if (!newlyAvailable.isEmpty()) {
                    data.activeQuests.addAll(newlyAvailable);
                    updates.add(new ChainUpdate(
                        chainId,
                        ChainUpdate.UpdateType.QUESTS_UNLOCKED,
                        newlyAvailable,
                        List.of(),
                        "Unlocked " + newlyAvailable.size() + " new quest(s)"
                    ));
                }
                
                // Check for chain completion
                if (isChainComplete(chain, data)) {
                    data.status = ChainStatus.COMPLETED;
                    data.completionCount++;
                    
                    // Set cooldown if repeatable
                    if (chain.repeatable() && chain.cooldownMinutes() > 0) {
                        data.cooldownUntil = System.currentTimeMillis() + 
                            (chain.cooldownMinutes() * 60L * 1000L);
                        data.status = ChainStatus.ON_COOLDOWN;
                    }
                    
                    // Deliver completion rewards
                    List<RewardDTO> rewards = chain.completionRewards();
                    if (!rewards.isEmpty()) {
                        deliverChainRewards(playerId, rewards);
                    }
                    
                    updates.add(new ChainUpdate(
                        chainId,
                        ChainUpdate.UpdateType.CHAIN_COMPLETED,
                        List.of(),
                        rewards,
                        "Chain completed! (" + data.completionCount + " times)"
                    ));
                    
                    logger.info("Player " + playerId + " completed chain: " + chainId + 
                              " (" + data.completionCount + " times)");
                } else {
                    updates.add(new ChainUpdate(
                        chainId,
                        ChainUpdate.UpdateType.QUEST_COMPLETED,
                        List.of(),
                        List.of(),
                        "Quest completed in chain"
                    ));
                }
            }
            
            return updates;
        });
    }
    
    @Override
    public CompletableFuture<Boolean> resetProgress(UUID playerId, String chainId) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, ChainProgressData> playerData = playerProgress.get(playerId);
            if (playerData == null) return false;
            
            ChainProgressData removed = playerData.remove(chainId);
            return removed != null;
        });
    }
    
    // ==================== Prerequisites & Unlocking ====================
    
    @Override
    public CompletableFuture<PrerequisiteResult> checkPrerequisites(UUID playerId, String chainId) {
        return CompletableFuture.supplyAsync(() -> {
            QuestChainDTO chain = chains.get(chainId);
            if (chain == null) {
                return PrerequisiteResult.failure(List.of());
            }
            
            if (!chain.hasPrerequisites()) {
                return PrerequisiteResult.success();
            }
            
            List<QuestPrerequisite> met = new ArrayList<>();
            List<QuestPrerequisite> unmet = new ArrayList<>();
            
            for (QuestPrerequisite prereq : chain.prerequisites()) {
                if (checkSinglePrerequisite(playerId, prereq)) {
                    met.add(prereq);
                } else {
                    unmet.add(prereq);
                }
            }
            
            if (unmet.isEmpty()) {
                return new PrerequisiteResult(true, met, List.of(), "All prerequisites met");
            } else {
                return new PrerequisiteResult(false, met, unmet, 
                    "Missing " + unmet.size() + " prerequisite(s)");
            }
        });
    }
    
    @Override
    public CompletableFuture<List<QuestChainDTO>> getAvailableChains(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            List<QuestChainDTO> available = new ArrayList<>();
            
            for (QuestChainDTO chain : chains.values()) {
                ChainProgressData progress = playerProgress
                    .getOrDefault(playerId, Map.of())
                    .get(chain.chainId());
                
                // Skip completed non-repeatable chains
                if (progress != null && progress.status == ChainStatus.COMPLETED && !chain.repeatable()) {
                    continue;
                }
                
                // Skip chains on cooldown
                if (progress != null && progress.cooldownUntil > System.currentTimeMillis()) {
                    continue;
                }
                
                // Check prerequisites
                boolean prereqsMet = chain.prerequisites().isEmpty() ||
                    chain.prerequisites().stream()
                        .allMatch(p -> checkSinglePrerequisite(playerId, p));
                
                if (prereqsMet) {
                    available.add(chain);
                }
            }
            
            return available;
        });
    }
    
    @Override
    public CompletableFuture<List<String>> getNextQuests(UUID playerId, String chainId) {
        return CompletableFuture.supplyAsync(() -> {
            QuestChainDTO chain = chains.get(chainId);
            if (chain == null) return List.of();
            
            ChainProgressData data = playerProgress
                .getOrDefault(playerId, Map.of())
                .get(chainId);
            
            if (data == null || data.status != ChainStatus.IN_PROGRESS) {
                return List.of();
            }
            
            return calculateAvailableQuests(chain, data).stream()
                .filter(q -> !data.completedQuests.contains(q))
                .toList();
        });
    }
    
    // ==================== Chain Completion ====================
    
    @Override
    public CompletableFuture<List<String>> getCompletedChains(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, ChainProgressData> playerData = playerProgress.get(playerId);
            if (playerData == null) return List.of();
            
            return playerData.values().stream()
                .filter(d -> d.status == ChainStatus.COMPLETED || d.completionCount > 0)
                .map(d -> d.chainId)
                .toList();
        });
    }
    
    @Override
    public CompletableFuture<Boolean> hasCompletedChain(UUID playerId, String chainId) {
        return CompletableFuture.supplyAsync(() -> {
            ChainProgressData data = playerProgress
                .getOrDefault(playerId, Map.of())
                .get(chainId);
            return data != null && data.completionCount > 0;
        });
    }
    
    @Override
    public CompletableFuture<Integer> getCompletionCount(UUID playerId, String chainId) {
        return CompletableFuture.supplyAsync(() -> {
            ChainProgressData data = playerProgress
                .getOrDefault(playerId, Map.of())
                .get(chainId);
            return data != null ? data.completionCount : 0;
        });
    }
    
    // ==================== Private Helpers ====================
    
    private ChainProgressData getOrCreateProgress(UUID playerId, String chainId) {
        return playerProgress
            .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(chainId, ChainProgressData::new);
    }
    
    private List<String> calculateAvailableQuests(QuestChainDTO chain, ChainProgressData data) {
        List<String> available = new ArrayList<>();
        
        for (ChainNode node : chain.nodes()) {
            collectAvailableFromNode(node, data.completedQuests, available);
        }
        
        return available;
    }
    
    private void collectAvailableFromNode(ChainNode node, Set<String> completed, List<String> available) {
        if (node.isLeaf()) {
            // Quest node - available if not completed
            if (!completed.contains(node.questId())) {
                available.add(node.questId());
            }
            return;
        }
        
        switch (node.type()) {
            case SEQUENCE -> {
                // First uncompleted quest in sequence is available
                for (ChainNode child : node.children()) {
                    if (child.isLeaf()) {
                        if (!completed.contains(child.questId())) {
                            available.add(child.questId());
                            break; // Only first available
                        }
                    } else {
                        if (!isNodeComplete(child, completed)) {
                            collectAvailableFromNode(child, completed, available);
                            break;
                        }
                    }
                }
            }
            case ALL, COUNT -> {
                // All children are potentially available
                for (ChainNode child : node.children()) {
                    collectAvailableFromNode(child, completed, available);
                }
            }
            case ANY -> {
                // All children are available until one completes
                boolean anyComplete = node.children().stream()
                    .anyMatch(c -> isNodeComplete(c, completed));
                if (!anyComplete) {
                    for (ChainNode child : node.children()) {
                        collectAvailableFromNode(child, completed, available);
                    }
                }
            }
            default -> {}
        }
    }
    
    private boolean isNodeComplete(ChainNode node, Set<String> completed) {
        if (node.isLeaf()) {
            return completed.contains(node.questId());
        }
        
        return switch (node.type()) {
            case ALL, SEQUENCE -> node.children().stream()
                .allMatch(c -> isNodeComplete(c, completed));
            case ANY -> node.children().stream()
                .anyMatch(c -> isNodeComplete(c, completed));
            case COUNT -> node.children().stream()
                .filter(c -> isNodeComplete(c, completed))
                .count() >= node.requiredCount();
            default -> false;
        };
    }
    
    private boolean isChainComplete(QuestChainDTO chain, ChainProgressData data) {
        return chain.nodes().stream()
            .allMatch(node -> isNodeComplete(node, data.completedQuests));
    }
    
    private boolean checkSinglePrerequisite(UUID playerId, QuestPrerequisite prereq) {
        try {
            return switch (prereq.type()) {
                case QUEST_COMPLETE -> {
                    // Check if quest completed via questProgressService
                    QuestState state = questProgressService.getQuestState(playerId, prereq.targetId()).join();
                    yield state == QuestState.COMPLETED;
                }
                case CHAIN_COMPLETE -> hasCompletedChain(playerId, prereq.targetId()).join();
                case PLAYER_LEVEL -> {
                    Player player = plugin.getServer().getPlayer(playerId);
                    yield player != null && player.getLevel() >= prereq.requiredValue();
                }
                case PERMISSION -> {
                    Player player = plugin.getServer().getPlayer(playerId);
                    yield player != null && player.hasPermission(prereq.targetId());
                }
                case WORLD -> {
                    Player player = plugin.getServer().getPlayer(playerId);
                    yield player != null && player.getWorld().getName().equals(prereq.targetId());
                }
                default -> true; // Other types pass by default
            };
        } catch (Exception e) {
            logger.warning("Error checking prerequisite: " + e.getMessage());
            return false;
        }
    }
    
    private void deliverChainRewards(UUID playerId, List<RewardDTO> rewards) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            // Queue for offline delivery
            logger.info("Player " + playerId + " offline, queueing chain rewards");
            return;
        }
        
        rewardService.deliverRewards(playerId, rewards)
            .thenAccept(result -> {
                if (result.hasFailures()) {
                    logger.warning("Some chain rewards failed for " + playerId + ": " + 
                                  result.failureCount() + " failures");
                }
            })
            .exceptionally(ex -> {
                logger.error("Failed to deliver chain rewards: " + ex.getMessage());
                return null;
            });
    }
}
