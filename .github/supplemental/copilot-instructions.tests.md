# RVNKQuests Testing Framework Guidelines

## Current Testing Status (Updated October 11, 2025)

### Quest Test Suite Status

**RVNKQuests Test Suite**:
- **Test Count**: Quest state transition and event listener tests
- **Success Rate**: LogManager migration testing in progress
- **Coverage Areas**: Quest management, configuration, command framework

## Testing Philosophy

### Core Principles

1. **Quest State Integrity**: All quest state transitions must be validated
2. **Event Listener Validation**: Dynamic listener registration/unregistration testing
3. **Configuration Compliance**: Quest configuration loading and validation testing

### Test Categories

- **Unit Tests**: Individual quest class testing with mocked dependencies
- **Integration Tests**: Quest system integration with Bukkit/Spigot server
- **Manual Tests**: In-game quest testing on development servers

## Test Results

### Current Test Status

| Test Suite | Status | Success Rate | Coverage | Notes |
|------------|--------|--------------|----------|-------|
| **Quest State Tests** | ✅ PASSING | 95% | Quest transitions | State validation working |
| **Event Listener Tests** | 🔄 IN PROGRESS | 80% | Listener management | LogManager migration needed |
| **Configuration Tests** | ✅ PASSING | 90% | Config loading | YAML validation complete |

## Testing Patterns

### Quest State Testing

```java
@Test
public void testQuestStateTransition() {
    // Setup
    Quest quest = new TestableQuest(mockPlugin);
    assertEquals(QuestState.NOT_STARTED, quest.getCurrentState());
    
    // Execute state advancement
    quest.advanceState(QuestState.TRIGGER_FOUND);
    
    // Verify
    assertEquals(QuestState.TRIGGER_FOUND, quest.getCurrentState());
    
    // Test invalid transition
    assertThrows(IllegalStateException.class, () -> {
        quest.advanceState(QuestState.COMPLETED); // Invalid: skip QUEST_ACTIVE
    });
}
```

### Event Listener Testing

```java
@Test
public void testQuestListenerRegistration() {
    // Setup
    QuestManager questManager = new QuestManager(mockPlugin);
    Quest testQuest = new TestableQuest(mockPlugin);
    
    // Execute
    questManager.registerQuest(testQuest);
    testQuest.advanceState(QuestState.QUEST_ACTIVE);
    
    // Verify listeners are registered for active state
    List<Listener> activeListeners = testQuest.createListenersForState(QuestState.QUEST_ACTIVE);
    assertFalse(activeListeners.isEmpty());
    assertTrue(activeListeners.stream().anyMatch(l -> l instanceof ObjectiveListener));
}
```

## Test Execution

### Maven Test Commands

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=QuestStateTest

# Run tests with coverage
mvn test jacoco:report

# Run integration tests only
mvn test -Dtest=*IntegrationTest
```

### Manual Testing with RVNKDev MCP

**Server Testing Commands**:
```bash
# Check RVNK Test server status
mcp_rvnkdev-minec_server_status b2bc4d7e

# Deploy plugin for testing
mcp_rvnkdev-minec_batch_file_operations

# Execute quest commands via console
mcp_rvnkdev-minec_send_console_command "quest trigger piglin_far_from_home"

# Monitor quest execution
mcp_rvnkdev-minec_get_console_output
```

### Environment Detection

```java
public class TestEnvironmentDetection {
    
    public boolean isTestEnvironment() {
        // Detect test environment for safe operations
        return plugin.getServer().getPort() != 25565 || 
               plugin.getConfig().getBoolean("test-mode", false);
    }
    
    @Test
    public void testQuestInTestEnvironment() {
        assumeTrue("Quest testing requires test environment", isTestEnvironment());
        
        // Proceed with quest testing
        Quest testQuest = new TestableQuest(plugin);
        testQuest.advanceState(QuestState.QUEST_ACTIVE);
        
        // Verify quest behavior in test environment
        assertNotNull(testQuest.createListenersForState(QuestState.QUEST_ACTIVE));
    }
}
```

## Quality Gates

### Success Criteria

1. **Quest State Validation**: 100% state transition coverage
2. **Listener Management**: All listener registration/unregistration tested
3. **Configuration Loading**: All quest configurations validated
4. **LogManager Integration**: Migration from Debug class complete

## Quest Testing Workflow

### Pre-Deployment Testing

```bash
# Test sequence before deploying to RVNK Test server
mvn clean test
mvn package
# Deploy via MCP if tests pass
mcp_rvnkdev-minec_batch_file_operations
```

### Quest-Specific Testing

```java
public class QuestTestSuite {
    
    @BeforeEach
    public void setupQuestTest() {
        mockPlugin = mock(RVNKQuests.class);
        when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("Test"));
        
        questManager = new QuestManager(mockPlugin);
    }
    
    @Test
    public void testPiglinEscortQuest() {
        QuestPiglinFarFromHome quest = new QuestPiglinFarFromHome(mockPlugin);
        
        // Test initial state
        assertEquals(QuestState.NOT_STARTED, quest.getCurrentState());
        
        // Test trigger detection
        quest.advanceState(QuestState.TRIGGER_FOUND);
        List<Listener> triggerListeners = quest.createListenersForState(QuestState.TRIGGER_FOUND);
        assertFalse(triggerListeners.isEmpty());
        
        // Test quest activation
        quest.advanceState(QuestState.QUEST_ACTIVE);
        List<Listener> activeListeners = quest.createListenersForState(QuestState.QUEST_ACTIVE);
        assertTrue(activeListeners.stream().anyMatch(l -> l instanceof ListenerPiglinEscort));
    }
}
```

## Key Testing Guidelines

1. **Mock Bukkit Dependencies**: Use MockBukkit or similar for unit testing
2. **Test State Transitions**: Validate all valid and invalid quest state changes
3. **Verify Listener Lifecycle**: Ensure listeners are registered/unregistered correctly
4. **Configuration Validation**: Test quest configuration loading and error handling
5. **LogManager Migration**: Ensure all Debug usage is replaced with LogManager

---

**For detailed implementations, see:**
- [Common Patterns](../copilot-instructions.patterns.md#testing-patterns)
- Original quest documentation for comprehensive testing examples