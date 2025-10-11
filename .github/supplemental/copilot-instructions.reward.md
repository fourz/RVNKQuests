# RVNKQuests Reward System Guidelines

## Quest Reward Architecture

### Reward Distribution System

The reward system should be flexible, configurable, and integrate with the lore system:

```java
public class QuestRewardManager {
    private final RVNKQuests plugin;
    private final FZLogger logger;
    private final RewardConfigManager configManager;
    private final LoreIntegration loreIntegration;
    
    public QuestRewardManager(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
        this.configManager = new RewardConfigManager(plugin);
        this.loreIntegration = new LoreIntegration(plugin);
    }
    
    public CompletableFuture<Void> distributeQuestRewards(String questId, List<UUID> participants) {
        return CompletableFuture.runAsync(() -> {
            try {
                QuestRewardConfig rewardConfig = configManager.getRewardConfig(questId);
                
                for (UUID playerId : participants) {
                    distributePlayerRewards(playerId, questId, rewardConfig);
                }
                
                // Record completion in lore system
                loreIntegration.recordQuestCompletion(questId, participants);
                
                logger.info("Distributed rewards for quest {} to {} participants", 
                           questId, participants.size());
            } catch (Exception e) {
                logger.error("Failed to distribute rewards for quest {}", questId, e);
            }
        });
    }
}
```

### Reward Types and Categories

#### Material Rewards

```java
public class MaterialReward implements QuestReward {
    private final Material material;
    private final int amount;
    private final ItemMeta customMeta;
    
    public MaterialReward(Material material, int amount) {
        this.material = material;
        this.amount = amount;
        this.customMeta = null;
    }
    
    public MaterialReward(Material material, int amount, ItemMeta meta) {
        this.material = material;
        this.amount = amount;
        this.customMeta = meta;
    }
    
    @Override
    public CompletableFuture<Boolean> giveReward(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ItemStack item = new ItemStack(material, amount);
                
                if (customMeta != null) {
                    item.setItemMeta(customMeta);
                }
                
                // Check inventory space
                if (!hasInventorySpace(player, item)) {
                    // Drop at player location if inventory is full
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                    player.sendMessage("Reward dropped at your location - inventory is full!");
                } else {
                    player.getInventory().addItem(item);
                }
                
                logger.debug("Gave material reward {} x{} to player {}", 
                           material, amount, player.getName());
                return true;
            } catch (Exception e) {
                logger.error("Failed to give material reward to player {}", player.getName(), e);
                return false;
            }
        });
    }
}
```

#### Experience Rewards

```java
public class ExperienceReward implements QuestReward {
    private final int expAmount;
    private final boolean isLevels; // true for levels, false for points
    
    @Override
    public CompletableFuture<Boolean> giveReward(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (isLevels) {
                    player.setLevel(player.getLevel() + expAmount);
                } else {
                    player.setTotalExperience(player.getTotalExperience() + expAmount);
                }
                
                // Visual effect
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.sendMessage(String.format("Gained %d %s!", expAmount, 
                                   isLevels ? "levels" : "experience points"));
                
                logger.debug("Gave experience reward {} {} to player {}", 
                           expAmount, isLevels ? "levels" : "points", player.getName());
                return true;
            } catch (Exception e) {
                logger.error("Failed to give experience reward to player {}", player.getName(), e);
                return false;
            }
        });
    }
}
```

#### Lore-Enhanced Rewards

```java
public class LoreEnhancedReward implements QuestReward {
    private final ItemStack baseItem;
    private final String loreKey;
    private final LoreDatabase loreDatabase;
    
    public LoreEnhancedReward(ItemStack baseItem, String loreKey, LoreDatabase loreDb) {
        this.baseItem = baseItem;
        this.loreKey = loreKey;
        this.loreDatabase = loreDb;
    }
    
    @Override
    public CompletableFuture<Boolean> giveReward(Player player) {
        return loreDatabase.getContentAsync(loreKey)
            .thenCompose(loreContent -> {
                // Enhance item with lore
                ItemStack enhancedItem = enhanceItemWithLore(baseItem, loreContent);
                
                // Give enhanced item to player
                return CompletableFuture.supplyAsync(() -> {
                    if (hasInventorySpace(player, enhancedItem)) {
                        player.getInventory().addItem(enhancedItem);
                        
                        // Send lore message
                        player.sendMessage("You have received a legendary item!");
                        player.sendMessage(loreContent);
                        
                        return true;
                    } else {
                        player.getWorld().dropItemNaturally(player.getLocation(), enhancedItem);
                        player.sendMessage("Legendary item dropped - inventory full!");
                        return true;
                    }
                });
            })
            .exceptionally(ex -> {
                logger.error("Failed to retrieve lore for reward", ex);
                // Fallback: give base item without lore
                player.getInventory().addItem(baseItem);
                return false;
            });
    }
    
    private ItemStack enhanceItemWithLore(ItemStack item, String loreContent) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Add quest lore to item
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GOLD + "Quest Artifact");
            
            // Split lore content into lines
            String[] loreLines = loreContent.split("\n");
            for (String line : loreLines) {
                lore.add(ChatColor.GRAY + line);
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
```

## Reward Configuration System

### Configuration-Driven Rewards

```java
public class RewardConfigManager {
    private final ConfigManager configManager;
    private final FZLogger logger;
    
    public QuestRewardConfig getRewardConfig(String questId) {
        String configPath = "quests." + questId + ".rewards";
        ConfigurationSection rewardsSection = configManager.getConfigurationSection(configPath);
        
        if (rewardsSection == null) {
            logger.warn("No reward configuration found for quest: {}", questId);
            return QuestRewardConfig.empty();
        }
        
        return parseRewardConfig(rewardsSection);
    }
    
    private QuestRewardConfig parseRewardConfig(ConfigurationSection config) {
        QuestRewardConfig.Builder builder = QuestRewardConfig.builder();
        
        // Parse material rewards
        if (config.contains("items")) {
            List<Map<?, ?>> items = config.getMapList("items");
            for (Map<?, ?> itemConfig : items) {
                MaterialReward reward = parseMaterialReward(itemConfig);
                builder.addReward(reward);
            }
        }
        
        // Parse experience rewards
        if (config.contains("experience")) {
            int expAmount = config.getInt("experience");
            builder.addReward(new ExperienceReward(expAmount, false));
        }
        
        // Parse lore rewards
        if (config.contains("lore_items")) {
            List<Map<?, ?>> loreItems = config.getMapList("lore_items");
            for (Map<?, ?> loreConfig : loreItems) {
                LoreEnhancedReward reward = parseLoreReward(loreConfig);
                builder.addReward(reward);
            }
        }
        
        return builder.build();
    }
}
```

### Dynamic Reward Scaling

```java
public class DynamicRewardScaler {
    
    public QuestRewardConfig scaleRewards(QuestRewardConfig baseConfig, 
                                          ScalingContext context) {
        QuestRewardConfig.Builder scaledBuilder = QuestRewardConfig.builder();
        
        for (QuestReward reward : baseConfig.getRewards()) {
            QuestReward scaledReward = scaleReward(reward, context);
            scaledBuilder.addReward(scaledReward);
        }
        
        return scaledBuilder.build();
    }
    
    private QuestReward scaleReward(QuestReward reward, ScalingContext context) {
        if (reward instanceof MaterialReward) {
            return scaleMaterialReward((MaterialReward) reward, context);
        } else if (reward instanceof ExperienceReward) {
            return scaleExperienceReward((ExperienceReward) reward, context);
        }
        
        // Default: no scaling
        return reward;
    }
    
    private MaterialReward scaleMaterialReward(MaterialReward reward, ScalingContext context) {
        // Scale based on participant count
        int participantCount = context.getParticipantCount();
        int scaledAmount = Math.max(1, reward.getAmount() / participantCount);
        
        // Scale based on quest difficulty
        double difficultyMultiplier = context.getDifficultyMultiplier();
        scaledAmount = (int) Math.round(scaledAmount * difficultyMultiplier);
        
        return new MaterialReward(reward.getMaterial(), scaledAmount, reward.getCustomMeta());
    }
}
```

## Lore System Integration

### Quest Completion Lore Records

```java
public class QuestLoreIntegration {
    private final LoreDatabase loreDatabase;
    private final FZLogger logger;
    
    public CompletableFuture<Void> recordQuestCompletion(String questId, 
                                                         List<UUID> participants) {
        if (loreDatabase == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                // Create lore entry for quest completion
                QuestCompletionLore loreEntry = new QuestCompletionLore(
                    questId, 
                    participants, 
                    System.currentTimeMillis()
                );
                
                // Store in lore database
                String loreKey = "quest_completions." + questId + "." + 
                                System.currentTimeMillis();
                
                loreDatabase.storeLoreEntry(loreKey, loreEntry.toJson());
                
                // Update quest statistics
                updateQuestStatistics(questId, participants.size());
                
                logger.info("Recorded quest completion lore for quest {} with {} participants", 
                           questId, participants.size());
            } catch (Exception e) {
                logger.error("Failed to record quest completion lore for quest {}", questId, e);
            }
        });
    }
    
    public CompletableFuture<Void> createQuestArtifactLore(ItemStack item, String questId, 
                                                           UUID recipientId) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Generate unique artifact story
                String artifactLore = generateArtifactLore(item, questId, recipientId);
                
                // Store in lore database
                String loreKey = "quest_artifacts." + questId + "." + recipientId;
                loreDatabase.storeLoreEntry(loreKey, artifactLore);
                
                logger.debug("Created artifact lore for item from quest {}", questId);
            } catch (Exception e) {
                logger.error("Failed to create artifact lore for quest {}", questId, e);
            }
        });
    }
}
```

### Player Achievement Tracking

```java
public class PlayerAchievementTracker {
    private final LoreDatabase loreDatabase;
    private final Map<UUID, PlayerAchievements> achievementCache = new ConcurrentHashMap<>();
    
    public CompletableFuture<Void> recordQuestAchievement(UUID playerId, String questId, 
                                                          QuestCompletionData completion) {
        return CompletableFuture.runAsync(() -> {
            try {
                PlayerAchievements achievements = getOrLoadAchievements(playerId);
                achievements.addQuestCompletion(questId, completion);
                
                // Check for milestone achievements
                checkMilestoneAchievements(playerId, achievements);
                
                // Save updated achievements
                saveAchievements(playerId, achievements);
                
                logger.debug("Recorded achievement for player {} completing quest {}", 
                           playerId, questId);
            } catch (Exception e) {
                logger.error("Failed to record achievement for player {} in quest {}", 
                           playerId, questId, e);
            }
        });
    }
    
    private void checkMilestoneAchievements(UUID playerId, PlayerAchievements achievements) {
        int totalQuests = achievements.getCompletedQuestCount();
        
        // Check for quest completion milestones
        List<Integer> milestones = Arrays.asList(1, 5, 10, 25, 50, 100);
        
        for (int milestone : milestones) {
            if (totalQuests == milestone && !achievements.hasMilestone(milestone)) {
                awardMilestoneAchievement(playerId, milestone);
                achievements.addMilestone(milestone);
            }
        }
    }
    
    private void awardMilestoneAchievement(UUID playerId, int milestone) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            // Award special milestone item
            ItemStack milestoneItem = createMilestoneItem(milestone);
            player.getInventory().addItem(milestoneItem);
            
            // Broadcast achievement
            String message = String.format("%s has completed %d quests!", 
                                          player.getName(), milestone);
            Bukkit.broadcastMessage(ChatColor.GOLD + message);
            
            logger.info("Awarded milestone achievement to player {} for {} quests", 
                       player.getName(), milestone);
        }
    }
}
```

## Reward Delivery Systems

### Immediate Delivery

```java
public class ImmediateRewardDelivery implements RewardDelivery {
    
    @Override
    public CompletableFuture<DeliveryResult> deliverRewards(Player player, 
                                                           List<QuestReward> rewards) {
        return CompletableFuture.supplyAsync(() -> {
            List<QuestReward> successfulRewards = new ArrayList<>();
            List<RewardFailure> failures = new ArrayList<>();
            
            for (QuestReward reward : rewards) {
                try {
                    boolean success = reward.giveReward(player).join();
                    if (success) {
                        successfulRewards.add(reward);
                    } else {
                        failures.add(new RewardFailure(reward, "Reward delivery failed"));
                    }
                } catch (Exception e) {
                    failures.add(new RewardFailure(reward, e.getMessage()));
                }
            }
            
            return new DeliveryResult(successfulRewards, failures);
        });
    }
}
```

### Delayed Reward System

```java
public class DelayedRewardDelivery implements RewardDelivery {
    private final Map<UUID, List<PendingReward>> pendingRewards = new ConcurrentHashMap<>();
    
    public void scheduleReward(UUID playerId, QuestReward reward, long delaySeconds) {
        PendingReward pendingReward = new PendingReward(reward, 
                                                       System.currentTimeMillis() + (delaySeconds * 1000));
        
        pendingRewards.computeIfAbsent(playerId, k -> new ArrayList<>()).add(pendingReward);
        
        // Schedule delivery
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            deliverPendingReward(playerId, pendingReward);
        }, delaySeconds * 20); // Convert to ticks
    }
    
    private void deliverPendingReward(UUID playerId, PendingReward pendingReward) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            try {
                pendingReward.getReward().giveReward(player).join();
                
                // Remove from pending list
                List<PendingReward> playerPending = pendingRewards.get(playerId);
                if (playerPending != null) {
                    playerPending.remove(pendingReward);
                }
                
                logger.debug("Delivered delayed reward to player {}", player.getName());
            } catch (Exception e) {
                logger.error("Failed to deliver delayed reward to player {}", player.getName(), e);
            }
        }
    }
}
```

### Reward Persistence

```java
public class RewardPersistenceManager {
    
    public CompletableFuture<Void> saveUnclaimedRewards(UUID playerId, 
                                                        List<QuestReward> rewards) {
        return CompletableFuture.runAsync(() -> {
            try {
                String rewardsJson = serializeRewards(rewards);
                dataService.saveUnclaimedRewards(playerId, rewardsJson);
                
                logger.debug("Saved {} unclaimed rewards for player {}", rewards.size(), playerId);
            } catch (Exception e) {
                logger.error("Failed to save unclaimed rewards for player {}", playerId, e);
            }
        });
    }
    
    public CompletableFuture<List<QuestReward>> loadUnclaimedRewards(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String rewardsJson = dataService.loadUnclaimedRewards(playerId);
                if (rewardsJson != null) {
                    return deserializeRewards(rewardsJson);
                }
                return new ArrayList<>();
            } catch (Exception e) {
                logger.error("Failed to load unclaimed rewards for player {}", playerId, e);
                return new ArrayList<>();
            }
        });
    }
    
    public CompletableFuture<Void> deliverOfflineRewards(UUID playerId) {
        return loadUnclaimedRewards(playerId)
            .thenCompose(rewards -> {
                if (rewards.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                }
                
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    return deliverRewardsToPlayer(player, rewards)
                        .thenCompose(result -> {
                            // Clear delivered rewards
                            return saveUnclaimedRewards(playerId, new ArrayList<>());
                        });
                }
                
                return CompletableFuture.completedFuture(null);
            });
    }
}
```