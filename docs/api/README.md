# RVNKQuests Developer API

RVNKQuests exposes a stable Java API for other plugins to integrate with the quest system.
All service interfaces are in the `org.fourz.RVNKQuests.service` package.

## Contents

| Document | What it covers |
|----------|---------------|
| [quest-api.md](quest-api.md) | `IQuestService` — quest lifecycle, state, player engagement |
| [objective-api.md](objective-api.md) | `IObjectiveService` + `IPlayerQuestService` — objectives, conditions, player progress |
| [reward-api.md](reward-api.md) | `IRewardService` — reward delivery and custom processor registration |
| [event-api.md](event-api.md) | `QuestCompleteEvent`, journal, notifications, chains, repeatable quests |

## Accessing Services

Add RVNKQuests as a soft- or hard-depend in your `plugin.yml`:

```yaml
depend: [RVNKQuests]      # hard-depend (plugin won't load without RVNKQuests)
# — or —
softdepend: [RVNKQuests]  # soft-depend (check for null before use)
```

Retrieve services through the plugin instance at startup:

```java
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.service.*;

public class MyPlugin extends JavaPlugin {
    private IQuestService questService;
    private IRewardService rewardService;
    private IObjectiveService objectiveService;

    @Override
    public void onEnable() {
        Plugin raw = getServer().getPluginManager().getPlugin("RVNKQuests");
        if (raw instanceof RVNKQuests rvnkQuests) {
            questService     = rvnkQuests.getQuestManager();      // IQuestService
            rewardService    = rvnkQuests.getRewardService();
            objectiveService = rvnkQuests.getObjectiveService();
            // also: getJournalService(), getQuestChainService(),
            //       getNotificationService(), getRepeatableQuestService()
        }
    }
}
```

## Async Pattern

All I/O operations return `CompletableFuture`. Never block the main thread:

```java
questService.getQuest("my_quest").thenAccept(opt -> {
    opt.ifPresent(quest -> getLogger().info("Quest: " + quest.name()));
}).exceptionally(ex -> {
    getLogger().severe("Failed: " + ex.getMessage());
    return null;
});
```

Always attach `.exceptionally()` to avoid silently swallowed errors.

## Fallback Mode

When the database is unavailable, services operate in **fallback mode** using in-memory or YAML storage. Check `isInFallbackMode()` on any service to detect this condition. Behavior is functionally equivalent but data may not persist across restarts.
