# Event, Journal, Notification & Chain API

Covers four service interfaces beyond the core quest/objective/reward surfaces:
- [Bukkit Events](#bukkit-events)
- [IJournalService](#ijournalservice) — quest history and statistics
- [INotificationService](#inotificationservice) — in-game messages and boss bars
- [IQuestChainService](#iquestchainservice) — narrative quest sequences
- [IRepeatableQuestService](#irepeatablequest service) — cooldowns and completion limits

---

## Bukkit Events

### `QuestCompleteEvent`

**Package**: `org.fourz.RVNKQuests.event`

Fired when a player completes a quest. Listen to this event to trigger cross-plugin integrations.

```java
@EventHandler
public void onQuestComplete(QuestCompleteEvent event) {
    Player player   = event.getPlayer();
    String questId  = event.getQuestId();
    String questName = event.getQuestName();

    // Example: grant a lore discovery when a specific quest finishes
    if ("chapter_1_finale".equals(questId)) {
        loreService.discover(player.getUniqueId(), "lore_the_beginning");
    }
}
```

Register your listener normally in `onEnable`:
```java
getServer().getPluginManager().registerEvents(this, this);
```

---

## `IJournalService`

**Package**: `org.fourz.RVNKQuests.service`
**Accessor**: `RVNKQuests#getJournalService()`

Records quest history and computes statistics. The journal is append-only — entries are never modified after creation.

### Recording Events

| Method | When to use |
|--------|-------------|
| `recordQuestStart(UUID, questId)` | Player begins a quest |
| `recordQuestStart(UUID, questId, details)` | With custom note |
| `recordQuestComplete(UUID, questId)` | Quest finished |
| `recordQuestComplete(UUID, questId, details)` | With custom note |
| `recordQuestAbandon(UUID, questId)` | Player abandoned |
| `recordQuestAbandon(UUID, questId, reason)` | With reason string |
| `recordQuestFailed(UUID, questId)` | Quest failed |
| `recordQuestFailed(UUID, questId, reason)` | With reason string |
| `recordObjectiveComplete(UUID, questId, objectiveId)` | Objective done |
| `recordPathChoice(UUID, questId, pathId)` | Branching choice made |
| `recordRewardClaimed(UUID, questId, details)` | Reward delivered |
| `recordAction(UUID, questId, JournalAction, details)` | Custom event type |

All methods return `CompletableFuture<JournalEntryDTO>`.

### Retrieval

```java
// All entries for a player
journalService.getPlayerJournal(uuid)
    .thenAccept(entries -> entries.forEach(e -> log(e.toString())));

// Entries for one quest
journalService.getQuestJournal(uuid, "gather_wool");

// Last 20 entries
journalService.getRecentJournal(uuid, 20);

// Filter by action type
journalService.getJournalByAction(uuid, JournalAction.QUEST_COMPLETE);

// Time range
journalService.getJournalByTimeRange(uuid,
    Instant.now().minus(7, ChronoUnit.DAYS), Instant.now());
```

### Statistics

```java
journalService.getPlayerStatistics(uuid).thenAccept(stats -> {
    getLogger().info("Quests completed: " + stats.questsCompleted());
    getLogger().info("Total objectives: " + stats.objectivesCompleted());
});
```

`getEntryCount(UUID)` returns the raw count as `CompletableFuture<Long>`.

### Maintenance

```java
journalService.clearPlayerJournal(uuid);          // delete all entries
journalService.clearQuestJournal(uuid, questId);  // delete quest entries
journalService.purgeOldEntries(Instant.now().minus(90, ChronoUnit.DAYS)); // age-based purge
```

### `isAvailable() → boolean`

Check if the journal service can write entries (database or fallback storage ready).

---

## `INotificationService`

**Package**: `org.fourz.RVNKQuests.service`
**Accessor**: `RVNKQuests#getNotificationService()`

Sends formatted notifications through multiple channels with player preference support.
All notification methods are synchronous (call from main thread or via `Bukkit.getScheduler()`).

### Quest Event Notifications

```java
notificationService.notifyQuestStart(player, "The Shepherd's Request", "Collect 32 wool.");
notificationService.notifyQuestComplete(player, "The Shepherd's Request");
notificationService.notifyQuestFailed(player, "The Shepherd's Request", "Time expired");
```

### Objective Notifications

```java
// Progress: "Wool: 16/32"
notificationService.notifyObjectiveProgress(player, "Collect Wool", 16, 32);
notificationService.notifyObjectiveComplete(player, "Collect Wool");
```

### Chain and Milestone Notifications

```java
notificationService.notifyQuestAvailable(player, "Chapter 2: The Journey");
notificationService.notifyMilestone(player, "Quest Veteran");
notificationService.notifyChainProgress(player, "The Shepherd's Tale", 2, 5);
```

### Custom Notifications

```java
// Send with explicit type and channels
notificationService.sendNotification(player, NotificationType.QUEST_START,
    "New Quest!", "The Shepherd's Request");

// Send to specific channel only
notificationService.sendToChannel(player, NotificationChannel.CHAT,
    "Your quest has been updated.");
```

### Boss Bar

```java
// Show/update quest boss bar (progress 0.0–1.0)
notificationService.showQuestProgressBar(player, "Collect Wool", 0.5);

// Hide when quest ends
notificationService.hideQuestProgressBar(player);
```

### Player Preferences

```java
// Get which channels are enabled for a player
notificationService.getEnabledChannels(uuid)
    .thenAccept(channels -> channels.forEach(c -> log(c.name())));

// Toggle a channel
notificationService.setChannelEnabled(uuid, NotificationChannel.BOSS_BAR, false);

// Check if a specific type is enabled
boolean bossBarEnabled = notificationService.isNotificationEnabled(uuid, NotificationType.OBJECTIVE_PROGRESS);

// Reset to defaults
notificationService.resetPreferences(uuid);
```

### Cooldown Management

```java
// Prevent notification spam
notificationService.isOnCooldown(uuid, NotificationType.OBJECTIVE_PROGRESS); // → boolean
notificationService.setCooldown(NotificationType.OBJECTIVE_PROGRESS, 3000L);  // 3s cooldown
notificationService.getCooldown(NotificationType.OBJECTIVE_PROGRESS);          // → long ms
```

---

## `IQuestChainService`

**Package**: `org.fourz.RVNKQuests.service`
**Accessor**: `RVNKQuests#getQuestChainService()`

Manages narrative sequences of quests. Supports linear, branching, and parallel chain patterns.

### Chain Registration

```java
questChainService.registerChain(chain);      // CompletableFuture<Boolean>
questChainService.unregisterChain(chainId);  // CompletableFuture<Boolean>
questChainService.getChain(chainId);         // CompletableFuture<Optional<QuestChainDTO>>
questChainService.getAllChains();             // CompletableFuture<List<QuestChainDTO>>
questChainService.getChainsByCategory("main_story");
```

### Player Progress

```java
// Start a chain
questChainService.startChain(uuid, "shepherds_tale")
    .thenAccept(result -> {
        if (result.success()) {
            result.availableQuests().forEach(qid -> log("Available: " + qid));
        } else {
            log("Failed: " + result.message());
        }
    });

// Get progress snapshot
questChainService.getProgress(uuid, "shepherds_tale")
    .thenAccept(p -> {
        log("Status: " + p.status());
        log("Completion: " + p.getCompletionPercentage() + "%");
        log("Active quests: " + p.activeQuests());
    });

// Get all chains the player has started
questChainService.getAllProgress(uuid);

// Called internally on quest completion; also available for manual triggering
questChainService.onQuestComplete(uuid, "gather_wool");

questChainService.resetProgress(uuid, "shepherds_tale");
```

### Prerequisites and Unlocking

```java
questChainService.checkPrerequisites(uuid, "epic_questline")
    .thenAccept(result -> {
        if (!result.satisfied()) {
            result.unmetPrerequisites().forEach(p -> player.sendMessage("Need: " + p));
        }
    });

// Chains the player can start right now
questChainService.getAvailableChains(uuid);

// Next quests in a chain based on current progress
questChainService.getNextQuests(uuid, "shepherds_tale");
```

### Completion Tracking

```java
questChainService.hasCompletedChain(uuid, "shepherds_tale"); // CompletableFuture<Boolean>
questChainService.getCompletedChains(uuid);                  // CompletableFuture<List<String>>
questChainService.getCompletionCount(uuid, "daily_chain");   // CompletableFuture<Integer>
```

### `ChainStatus` enum

| Value | Meaning |
|-------|---------|
| `NOT_STARTED` | Player hasn't begun |
| `IN_PROGRESS` | Active quests underway |
| `COMPLETED` | All quests done |
| `ON_COOLDOWN` | Repeatable chain on cooldown |
| `LOCKED` | Prerequisites not met |

---

## `IRepeatableQuestService`

**Package**: `org.fourz.RVNKQuests.service`
**Accessor**: `RVNKQuests#getRepeatableQuestService()`

Manages cooldown and completion-limit behavior for repeatable quests.

### Configuration

```java
// Get repeat config for a quest
questRepeatService.getRepeatConfig("daily_hunt")
    .thenAccept(opt -> opt.ifPresent(cfg -> {
        log("Cooldown: " + cfg.cooldownSeconds() + "s");
        log("Max completions: " + cfg.maxCompletions());
    }));

questRepeatService.saveRepeatConfig(config);     // CompletableFuture<Boolean>
questRepeatService.deleteRepeatConfig("quest_id"); // CompletableFuture<Boolean>
```

### Player Availability

```java
// Is this quest available right now?
questRepeatService.isQuestAvailable(uuid, "daily_hunt")
    .thenAccept(available -> {
        if (!available) {
            questRepeatService.getRemainingCooldown(uuid, "daily_hunt")
                .thenAccept(secs -> player.sendMessage("Available in " + secs + "s"));
        }
    });

// Can they repeat at all (hasn't hit max)?
questRepeatService.canRepeatQuest(uuid, "daily_hunt");

// How many times have they done it?
questRepeatService.getCompletionCount(uuid, "daily_hunt");
```

### Lifecycle Hooks

```java
// Record a completion (call after quest completes to update cooldown/count)
questRepeatService.recordCompletion(uuid, "daily_hunt")
    .thenAccept(data -> log("Next available: " + data.nextAvailableAt()));

// Reset (admin action)
questRepeatService.resetQuestForPlayer(uuid, "daily_hunt");
```

### `isInFallbackMode() → boolean`
