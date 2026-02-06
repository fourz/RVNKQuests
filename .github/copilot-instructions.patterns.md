# RVNKQuests Common Patterns Library

> For async patterns, error handling, logging, and general RVNK conventions, see parent [coding-standards.md](../../../docs/standard/coding-standards.md)

## Quest Implementation Patterns

### Basic Quest Structure

```java
public class ExampleQuest implements Quest {
    private static final String QUEST_ID = "example_quest";

    private final RVNKQuests plugin;
    private final RVNKLogger logger;
    private QuestState currentState = QuestState.NOT_STARTED;

    public ExampleQuest(RVNKQuests plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }

    @Override
    public String getId() { return QUEST_ID; }

    @Override
    public QuestState getCurrentState() { return currentState; }

    @Override
    public List<Listener> createListenersForState(QuestState state) {
        List<Listener> listeners = new ArrayList<>();

        switch (state) {
            case NOT_STARTED -> listeners.add(new ExampleTriggerListener(this));
            case QUEST_ACTIVE -> listeners.add(new ExampleObjectiveListener(this));
        }

        return listeners;
    }
}
```

## Event Handler Patterns

### Quest Event Listener Pattern

```java
public class QuestEventListener implements Listener {
    private final Quest quest;
    private final RVNKLogger logger;

    public QuestEventListener(Quest quest) {
        this.quest = quest;
        this.logger = LogManager.getInstance(quest.getPlugin(), getClass());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEvent(PlayerEvent event) {
        // Early filtering for performance
        if (!isRelevantForQuest(event)) return;

        Player player = event.getPlayer();
        handleQuestEvent(player, event);
    }

    private boolean isRelevantForQuest(PlayerEvent event) {
        return event.getPlayer() != null &&
               isQuestWorld(event.getPlayer().getWorld());
    }
}
```

## Configuration Patterns

### Quest Configuration Management

```java
public boolean getQuestEnabled(String questId) {
    return config.getBoolean("quests." + questId + ".enabled", true);
}

public String getQuestWorld(String questId) {
    return config.getString("quests." + questId + ".world", "world");
}

public void validateConfiguration() {
    ConfigurationSection questsSection = config.getConfigurationSection("quests");
    if (questsSection == null) {
        logger.warn("No quests configured in config.yml");
        return;
    }

    for (String questId : questsSection.getKeys(false)) {
        validateQuestConfiguration(questId);
    }
}
```

## Security Patterns

### Quest Access Validation

```java
public boolean validateQuestAccess(Player player, String questId) {
    if (player == null || !player.isOnline()) return false;

    // World restrictions
    String requiredWorld = getQuestWorld(questId);
    if (!player.getWorld().getName().equals(requiredWorld)) {
        logger.debug("Player {} not in required world {}", player.getName(), requiredWorld);
        return false;
    }

    // Permission check
    if (!player.hasPermission("rvnkquests.quest." + questId)) {
        logger.debug("Player {} lacks permission for quest {}", player.getName(), questId);
        return false;
    }

    return true;
}
```

## Testing Patterns

### Quest State Testing

```java
@Test
public void testQuestStateTransition() {
    Quest quest = new TestQuest(plugin);
    quest.advanceState(QuestState.TRIGGER_FOUND);
    assertEquals(QuestState.TRIGGER_FOUND, quest.getCurrentState());
}

@Test
public void testQuestListenerRegistration() {
    Quest quest = new TestQuest(plugin);
    List<Listener> listeners = quest.createListenersForState(QuestState.QUEST_ACTIVE);
    assertFalse(listeners.isEmpty());
    assertTrue(listeners.stream().anyMatch(l -> l instanceof ObjectiveListener));
}
```

## Key Pattern Principles

1. **Consistency**: Use established patterns across all quest implementations
2. **Early Filtering**: Check relevance before processing events
3. **State-Aware**: Listeners register/unregister based on quest state
4. **Testability**: Write code that can be easily unit tested
