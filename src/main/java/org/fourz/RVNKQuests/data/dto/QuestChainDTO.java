package org.fourz.RVNKQuests.data.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Data Transfer Object for quest chain definitions.
 * Immutable and thread-safe for cross-boundary data transfer.
 *
 * <p>Represents a sequence of quests that form a narrative chain.
 * Supports linear, branching, and parallel chain structures.</p>
 *
 * <h2>Chain Patterns</h2>
 * <ul>
 *   <li><b>Linear</b>: Quest A → Quest B → Quest C</li>
 *   <li><b>Branching</b>: Quest A → (Quest B1 OR Quest B2) → Quest C</li>
 *   <li><b>Parallel</b>: Complete all of [Quest X, Quest Y, Quest Z]</li>
 * </ul>
 *
 * @since 1.0
 */
public record QuestChainDTO(
    String chainId,
    String name,
    String description,
    String category,
    List<ChainNode> nodes,
    List<RewardDTO> completionRewards,
    List<QuestPrerequisite> prerequisites,
    boolean repeatable,
    int cooldownMinutes,
    Instant createdAt,
    Map<String, Object> metadata
) {
    /**
     * Types of chain node structures.
     */
    public enum NodeType {
        /**
         * Single quest node.
         */
        QUEST,
        
        /**
         * All child nodes must be completed.
         */
        ALL,
        
        /**
         * Any single child node completes the group.
         */
        ANY,
        
        /**
         * At least N child nodes must be completed.
         */
        COUNT,
        
        /**
         * Child nodes must be completed in sequence.
         */
        SEQUENCE
    }
    
    /**
     * Represents a node in the quest chain graph.
     * Can be a single quest or a group of child nodes.
     */
    public record ChainNode(
        String nodeId,
        NodeType type,
        String questId,
        List<ChainNode> children,
        int requiredCount,
        List<QuestPrerequisite> localPrerequisites,
        Map<String, String> metadata
    ) {
        /**
         * Compact constructor with validation.
         */
        public ChainNode {
            Objects.requireNonNull(nodeId, "nodeId cannot be null");
            Objects.requireNonNull(type, "type cannot be null");
            
            // Quest nodes require questId
            if (type == NodeType.QUEST && questId == null) {
                throw new IllegalArgumentException("Quest nodes require questId");
            }
            
            // Group nodes require children
            if (type != NodeType.QUEST && (children == null || children.isEmpty())) {
                throw new IllegalArgumentException("Group nodes require children");
            }
            
            children = children == null ? List.of() : List.copyOf(children);
            localPrerequisites = localPrerequisites == null ? List.of() : List.copyOf(localPrerequisites);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            
            if (requiredCount < 0) {
                requiredCount = 0;
            }
        }
        
        /**
         * Creates a single quest node.
         *
         * @param nodeId The node identifier
         * @param questId The quest identifier
         * @return A new ChainNode
         */
        public static ChainNode quest(String nodeId, String questId) {
            return new ChainNode(nodeId, NodeType.QUEST, questId, 
                                 List.of(), 1, List.of(), Map.of());
        }
        
        /**
         * Creates an ALL group node.
         *
         * @param nodeId The node identifier
         * @param children The child nodes (all required)
         * @return A new ChainNode
         */
        public static ChainNode all(String nodeId, ChainNode... children) {
            return new ChainNode(nodeId, NodeType.ALL, null,
                                 List.of(children), children.length, List.of(), Map.of());
        }
        
        /**
         * Creates an ANY group node.
         *
         * @param nodeId The node identifier
         * @param children The child nodes (any one completes)
         * @return A new ChainNode
         */
        public static ChainNode any(String nodeId, ChainNode... children) {
            return new ChainNode(nodeId, NodeType.ANY, null,
                                 List.of(children), 1, List.of(), Map.of());
        }
        
        /**
         * Creates a COUNT group node.
         *
         * @param nodeId The node identifier
         * @param requiredCount The number required to complete
         * @param children The child nodes
         * @return A new ChainNode
         */
        public static ChainNode count(String nodeId, int requiredCount, ChainNode... children) {
            return new ChainNode(nodeId, NodeType.COUNT, null,
                                 List.of(children), requiredCount, List.of(), Map.of());
        }
        
        /**
         * Creates a SEQUENCE group node.
         *
         * @param nodeId The node identifier
         * @param children The child nodes (in order)
         * @return A new ChainNode
         */
        public static ChainNode sequence(String nodeId, ChainNode... children) {
            return new ChainNode(nodeId, NodeType.SEQUENCE, null,
                                 List.of(children), children.length, List.of(), Map.of());
        }
        
        /**
         * Check if this is a leaf (quest) node.
         *
         * @return true if this is a quest node
         */
        public boolean isLeaf() {
            return type == NodeType.QUEST;
        }
        
        /**
         * Get all quest IDs in this node and its children.
         *
         * @return List of all quest IDs
         */
        public List<String> getAllQuestIds() {
            if (isLeaf()) {
                return List.of(questId);
            }
            return children.stream()
                .flatMap(child -> child.getAllQuestIds().stream())
                .toList();
        }
        
        /**
         * Builder for ChainNode.
         */
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String nodeId;
            private NodeType type = NodeType.QUEST;
            private String questId;
            private List<ChainNode> children = List.of();
            private int requiredCount = 1;
            private List<QuestPrerequisite> localPrerequisites = List.of();
            private Map<String, String> metadata = Map.of();
            
            public Builder nodeId(String nodeId) {
                this.nodeId = nodeId;
                return this;
            }
            
            public Builder type(NodeType type) {
                this.type = type;
                return this;
            }
            
            public Builder questId(String questId) {
                this.questId = questId;
                return this;
            }
            
            public Builder children(List<ChainNode> children) {
                this.children = children;
                return this;
            }
            
            public Builder child(ChainNode child) {
                this.children = new java.util.ArrayList<>(this.children);
                this.children.add(child);
                return this;
            }
            
            public Builder requiredCount(int requiredCount) {
                this.requiredCount = requiredCount;
                return this;
            }
            
            public Builder localPrerequisites(List<QuestPrerequisite> prereqs) {
                this.localPrerequisites = prereqs;
                return this;
            }
            
            public Builder metadata(Map<String, String> metadata) {
                this.metadata = metadata;
                return this;
            }
            
            public ChainNode build() {
                return new ChainNode(nodeId, type, questId, children,
                                     requiredCount, localPrerequisites, metadata);
            }
        }
    }
    
    /**
     * Compact constructor with validation and defensive copies.
     */
    public QuestChainDTO {
        Objects.requireNonNull(chainId, "chainId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        
        if (cooldownMinutes < 0) {
            cooldownMinutes = 0;
        }
        
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        completionRewards = completionRewards == null ? List.of() : List.copyOf(completionRewards);
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
    
    // ==================== Factory Methods ====================
    
    /**
     * Creates a simple linear chain.
     *
     * @param chainId The chain identifier
     * @param name The chain name
     * @param questIds The quest IDs in sequence
     * @return A new QuestChainDTO
     */
    public static QuestChainDTO linear(String chainId, String name, String... questIds) {
        List<ChainNode> nodes = new java.util.ArrayList<>();
        for (int i = 0; i < questIds.length; i++) {
            nodes.add(ChainNode.quest("node_" + i, questIds[i]));
        }
        
        return new QuestChainDTO(
            chainId, name, null, null,
            List.of(ChainNode.sequence(chainId + "_seq", nodes.toArray(new ChainNode[0]))),
            List.of(), List.of(), false, 0, Instant.now(), Map.of()
        );
    }
    
    /**
     * Creates a chain with branching paths.
     *
     * @param chainId The chain identifier
     * @param name The chain name
     * @param startQuestId The starting quest
     * @param branchQuestIds The branching quest options
     * @param endQuestId The ending quest
     * @return A new QuestChainDTO
     */
    public static QuestChainDTO branching(String chainId, String name, 
                                          String startQuestId, 
                                          List<String> branchQuestIds,
                                          String endQuestId) {
        ChainNode start = ChainNode.quest("start", startQuestId);
        
        ChainNode[] branches = branchQuestIds.stream()
            .map(qid -> ChainNode.quest("branch_" + qid, qid))
            .toArray(ChainNode[]::new);
        ChainNode branchNode = ChainNode.any("branches", branches);
        
        ChainNode end = ChainNode.quest("end", endQuestId);
        
        ChainNode root = ChainNode.sequence(chainId + "_seq", start, branchNode, end);
        
        return new QuestChainDTO(
            chainId, name, null, null,
            List.of(root), List.of(), List.of(), false, 0, Instant.now(), Map.of()
        );
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Get all quest IDs in this chain.
     *
     * @return List of all quest IDs
     */
    public List<String> getAllQuestIds() {
        return nodes.stream()
            .flatMap(node -> node.getAllQuestIds().stream())
            .distinct()
            .toList();
    }
    
    /**
     * Get total quest count in chain.
     *
     * @return The number of quests
     */
    public int getTotalQuestCount() {
        return getAllQuestIds().size();
    }
    
    /**
     * Check if chain has prerequisites.
     *
     * @return true if prerequisites exist
     */
    public boolean hasPrerequisites() {
        return !prerequisites.isEmpty();
    }
    
    /**
     * Check if chain has completion rewards.
     *
     * @return true if rewards exist
     */
    public boolean hasCompletionRewards() {
        return !completionRewards.isEmpty();
    }
    
    /**
     * Get a metadata value.
     *
     * @param key The metadata key
     * @return Optional containing the value
     */
    public Optional<Object> getMetadata(String key) {
        return Optional.ofNullable(metadata.get(key));
    }
    
    /**
     * Creates a copy with added reward.
     *
     * @param reward The reward to add
     * @return A new QuestChainDTO
     */
    public QuestChainDTO withReward(RewardDTO reward) {
        List<RewardDTO> newRewards = new java.util.ArrayList<>(completionRewards);
        newRewards.add(reward);
        return new QuestChainDTO(chainId, name, description, category, nodes,
                                 newRewards, prerequisites, repeatable, cooldownMinutes,
                                 createdAt, metadata);
    }
    
    /**
     * Creates a copy with added prerequisite.
     *
     * @param prereq The prerequisite to add
     * @return A new QuestChainDTO
     */
    public QuestChainDTO withPrerequisite(QuestPrerequisite prereq) {
        List<QuestPrerequisite> newPrereqs = new java.util.ArrayList<>(prerequisites);
        newPrereqs.add(prereq);
        return new QuestChainDTO(chainId, name, description, category, nodes,
                                 completionRewards, newPrereqs, repeatable, cooldownMinutes,
                                 createdAt, metadata);
    }
    
    // ==================== Builder ====================
    
    /**
     * Builder for constructing QuestChainDTO with fluent API.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder class for QuestChainDTO.
     */
    public static class Builder {
        private String chainId;
        private String name;
        private String description;
        private String category;
        private List<ChainNode> nodes = new java.util.ArrayList<>();
        private List<RewardDTO> completionRewards = new java.util.ArrayList<>();
        private List<QuestPrerequisite> prerequisites = new java.util.ArrayList<>();
        private boolean repeatable = false;
        private int cooldownMinutes = 0;
        private Instant createdAt = Instant.now();
        private Map<String, Object> metadata = new java.util.HashMap<>();
        
        public Builder chainId(String chainId) {
            this.chainId = chainId;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder category(String category) {
            this.category = category;
            return this;
        }
        
        public Builder nodes(List<ChainNode> nodes) {
            this.nodes = new java.util.ArrayList<>(nodes);
            return this;
        }
        
        public Builder node(ChainNode node) {
            this.nodes.add(node);
            return this;
        }
        
        public Builder completionRewards(List<RewardDTO> rewards) {
            this.completionRewards = new java.util.ArrayList<>(rewards);
            return this;
        }
        
        public Builder completionReward(RewardDTO reward) {
            this.completionRewards.add(reward);
            return this;
        }
        
        public Builder prerequisites(List<QuestPrerequisite> prereqs) {
            this.prerequisites = new java.util.ArrayList<>(prereqs);
            return this;
        }
        
        public Builder prerequisite(QuestPrerequisite prereq) {
            this.prerequisites.add(prereq);
            return this;
        }
        
        public Builder repeatable(boolean repeatable) {
            this.repeatable = repeatable;
            return this;
        }
        
        public Builder cooldownMinutes(int cooldownMinutes) {
            this.cooldownMinutes = cooldownMinutes;
            return this;
        }
        
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }
        
        public QuestChainDTO build() {
            return new QuestChainDTO(
                chainId, name, description, category, nodes,
                completionRewards, prerequisites, repeatable, cooldownMinutes,
                createdAt, metadata
            );
        }
    }
}
