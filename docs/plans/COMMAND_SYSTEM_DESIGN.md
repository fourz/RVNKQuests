# RVNKQuests Command System Design

**Document ID**: plan-04
**Version**: 1.0
**Date**: January 31, 2026
**Author**: java-architect
**Status**: COMPLETE

---

## Executive Summary

This document analyzes the current RVNKQuests command system architecture and proposes enhancements including a fluent API for command building. The analysis finds the existing system is **well-designed** with minimal refactoring needs. The recommended approach is **evolutionary enhancement** rather than rewrite.

**Health Score**: 8.5/10 (Good - Minor improvements recommended)

---

## 1. Current Architecture Analysis

### 1.1 Component Overview

```
CommandManager (Singleton)
├── RVNKCommand (Interface)
│   └── BaseCommand (Abstract)
│       └── QuestCommand (Concrete)
└── SubCommand (Interface)
    └── BaseSubCommand (Abstract)
        ├── QuestDebugSubCommand
        ├── QuestStateSubCommand
        ├── QuestItemSubCommand
        ├── QuestMobsSubCommand
        ├── QuestReloadSubCommand
        ├── QuestTriggerSubCommand
        ├── QuestConfigSubCommand
        └── QuestValidateSubCommand
```

### 1.2 File Inventory

| File | Lines | Purpose | Status |
|------|-------|---------|--------|
| `CommandManager.java` | 304 | Centralized command registry | ✅ Good |
| `RVNKCommand.java` | 75 | Command interface contract | ✅ Good |
| `BaseCommand.java` | 291 | Abstract base with common logic | ✅ Good |
| `SubCommand.java` | 75 | Subcommand interface contract | ✅ Good |
| `BaseSubCommand.java` | 285 | Abstract subcommand base | ✅ Good |
| `QuestCommand.java` | 47 | Main /quest command | ✅ Good |
| 8 SubCommands | ~1,500 | Quest management operations | ✅ Good |

**Total**: ~2,577 lines across 14 files

### 1.3 Design Patterns Identified

| Pattern | Usage | Implementation Quality |
|---------|-------|----------------------|
| **Singleton** | CommandManager | ✅ Correct |
| **Template Method** | BaseCommand.execute() | ✅ Correct |
| **Strategy** | SubCommand delegation | ✅ Correct |
| **Composite** | Command → SubCommands | ✅ Correct |
| **Factory** | Permission generation | ✅ Correct |

### 1.4 Strengths ✅

1. **Clean Interface Hierarchy**: `RVNKCommand` → `SubCommand` separation
2. **Permission System**: Auto-generated hierarchical permissions (`rvnkquests.command.quest.debug`)
3. **Console Support**: `isPlayerOnly()` flag enables console execution
4. **Tab Completion**: Integrated via `TabCompleter` interface
5. **Validation Helpers**: `validateArgs()`, `validatePlayer()` reduce boilerplate
6. **Message Formatting**: `sendSuccessMessage()`, `sendErrorMessage()`, `sendInfoMessage()`
7. **Logging**: LogManager integration with debug context

### 1.5 Gaps Identified ⚠️

| Gap | Severity | Impact | Effort |
|-----|----------|--------|--------|
| No fluent builder API | LOW | Verbose command creation | 2-3 days |
| No async command support | MEDIUM | Blocking database calls | 1-2 days |
| No undo/redo capability | LOW | Admin convenience | 3-4 days |
| No command cooldowns | LOW | Spam prevention | 1 day |
| Manual subcommand registration | LOW | Boilerplate code | 1 day |

---

## 2. Fluent API Design

### 2.1 Proposed Builder Pattern

The fluent API enables declarative command definition while maintaining backward compatibility.

```java
/**
 * Fluent builder for creating RVNKQuests commands.
 * 
 * Example usage:
 * <pre>{@code
 * CommandBuilder.create(plugin)
 *     .name("quest")
 *     .description("Main quest command")
 *     .permission("rvnkquests.command.quest")
 *     .subCommand(sub -> sub
 *         .name("debug")
 *         .description("Debug operations")
 *         .permission("rvnkquests.admin")
 *         .playerOnly(false)
 *         .handler(this::handleDebug)
 *         .tabComplete(this::completeDebug)
 *     )
 *     .subCommand(sub -> sub
 *         .name("state")
 *         .description("Quest state management")
 *         .playerOnly(true)
 *         .handler(this::handleState)
 *     )
 *     .build()
 *     .register();
 * }</pre>
 */
public class CommandBuilder {
    
    private final RVNKQuests plugin;
    private String name;
    private String description;
    private String usage;
    private String permission;
    private final List<SubCommandBuilder> subCommands = new ArrayList<>();
    
    private CommandBuilder(RVNKQuests plugin) {
        this.plugin = plugin;
    }
    
    public static CommandBuilder create(RVNKQuests plugin) {
        return new CommandBuilder(plugin);
    }
    
    public CommandBuilder name(String name) {
        this.name = name;
        return this;
    }
    
    public CommandBuilder description(String description) {
        this.description = description;
        return this;
    }
    
    public CommandBuilder usage(String usage) {
        this.usage = usage;
        return this;
    }
    
    public CommandBuilder permission(String permission) {
        this.permission = permission;
        return this;
    }
    
    public CommandBuilder subCommand(Consumer<SubCommandBuilder> builder) {
        SubCommandBuilder subBuilder = new SubCommandBuilder(plugin);
        builder.accept(subBuilder);
        subCommands.add(subBuilder);
        return this;
    }
    
    public FluentCommand build() {
        FluentCommand command = new FluentCommand(plugin, name, description, usage, permission);
        for (SubCommandBuilder subBuilder : subCommands) {
            command.registerSubCommand(subBuilder.getName(), subBuilder.build(command));
        }
        return command;
    }
}
```

### 2.2 SubCommand Builder

```java
/**
 * Fluent builder for subcommands.
 */
public class SubCommandBuilder {
    
    private final RVNKQuests plugin;
    private String name;
    private String description;
    private String usage;
    private String permission;
    private boolean playerOnly = false;
    private BiFunction<CommandSender, String[], Boolean> handler;
    private BiFunction<CommandSender, String[], List<String>> tabCompleter;
    
    SubCommandBuilder(RVNKQuests plugin) {
        this.plugin = plugin;
    }
    
    public SubCommandBuilder name(String name) {
        this.name = name;
        return this;
    }
    
    public SubCommandBuilder description(String description) {
        this.description = description;
        return this;
    }
    
    public SubCommandBuilder usage(String usage) {
        this.usage = usage;
        return this;
    }
    
    public SubCommandBuilder permission(String permission) {
        this.permission = permission;
        return this;
    }
    
    public SubCommandBuilder playerOnly(boolean playerOnly) {
        this.playerOnly = playerOnly;
        return this;
    }
    
    public SubCommandBuilder handler(BiFunction<CommandSender, String[], Boolean> handler) {
        this.handler = handler;
        return this;
    }
    
    public SubCommandBuilder tabComplete(BiFunction<CommandSender, String[], List<String>> completer) {
        this.tabCompleter = completer;
        return this;
    }
    
    public String getName() {
        return name;
    }
    
    FluentSubCommand build(RVNKCommand parent) {
        return new FluentSubCommand(plugin, parent, name, description, usage, permission, playerOnly, handler, tabCompleter);
    }
}
```

### 2.3 Async Command Support

```java
/**
 * Async command handler that returns CompletableFuture.
 * Automatically handles thread synchronization.
 */
public interface AsyncCommandHandler {
    CompletableFuture<CommandResult> execute(CommandSender sender, String[] args);
}

/**
 * Command result with success/failure and optional message.
 */
public record CommandResult(
    boolean success,
    String message,
    Map<String, Object> metadata
) {
    public static CommandResult success() {
        return new CommandResult(true, null, Map.of());
    }
    
    public static CommandResult success(String message) {
        return new CommandResult(true, message, Map.of());
    }
    
    public static CommandResult error(String message) {
        return new CommandResult(false, message, Map.of());
    }
}
```

---

## 3. Migration Strategy

### 3.1 Migration Approach: **Additive (Non-Breaking)**

The recommended strategy is **additive enhancement** - adding new fluent API alongside existing code.

| Phase | Tasks | Breaking Changes | Effort |
|-------|-------|------------------|--------|
| **Phase 1** | Add CommandBuilder, SubCommandBuilder | ❌ None | 2-3 days |
| **Phase 2** | Add FluentCommand, FluentSubCommand | ❌ None | 1-2 days |
| **Phase 3** | Add async support (optional adoption) | ❌ None | 1-2 days |
| **Phase 4** | Migrate existing commands (optional) | ❌ None | 2-3 days |

### 3.2 Compatibility Guarantees

1. **Existing commands continue to work** - No changes to BaseCommand, BaseSubCommand
2. **New commands can use either pattern** - Builder or traditional class
3. **Mixed usage supported** - Some commands fluent, some traditional
4. **No plugin.yml changes** - Same command registration

### 3.3 Migration Order

If migrating existing commands to fluent API (optional):

1. ✅ `QuestReloadSubCommand` - Simple, low risk (10 min)
2. ✅ `QuestConfigSubCommand` - Simple config operations (15 min)
3. ✅ `QuestItemSubCommand` - Item operations (20 min)
4. ⚠️ `QuestDebugSubCommand` - Complex, migrate last (30 min)
5. ⚠️ `QuestStateSubCommand` - State machine, test carefully (30 min)

---

## 4. Breaking Changes Analysis

### 4.1 API Surface

| Component | Public API Changes | Breaking? |
|-----------|-------------------|-----------|
| CommandManager | None | ❌ No |
| RVNKCommand | None | ❌ No |
| BaseCommand | None | ❌ No |
| SubCommand | None | ❌ No |
| BaseSubCommand | None | ❌ No |

### 4.2 Behavior Changes

| Change | Impact | Migration Path |
|--------|--------|----------------|
| Fluent API added | New capability | Use new or old style |
| Async support added | Optional feature | Opt-in per command |
| CommandResult record | New type | Only for fluent commands |

### 4.3 Deprecation Plan

**No deprecations planned**. The existing class-based approach remains valid for complex commands.

---

## 5. Implementation Roadmap

### Phase 1: Core Builders (2-3 days)

**Files to create:**
- `command/builder/CommandBuilder.java`
- `command/builder/SubCommandBuilder.java`
- `command/builder/FluentCommand.java`
- `command/builder/FluentSubCommand.java`

**Deliverables:**
- [ ] CommandBuilder with fluent API
- [ ] SubCommandBuilder with handler support
- [ ] Unit tests for builders
- [ ] Integration with CommandManager

### Phase 2: Async Support (1-2 days)

**Files to create:**
- `command/async/AsyncCommandHandler.java`
- `command/async/CommandResult.java`
- `command/async/AsyncSubCommand.java`

**Deliverables:**
- [ ] AsyncCommandHandler interface
- [ ] CommandResult record
- [ ] Scheduler integration for async execution
- [ ] Unit tests for async operations

### Phase 3: Enhanced Features (Optional, 2-3 days)

**Features:**
- [ ] Command cooldowns
- [ ] Command history/undo
- [ ] Annotation-based registration
- [ ] Auto-generated help

---

## 6. Code Examples

### 6.1 Current Implementation (BaseSubCommand)

```java
// Current: 377 lines for QuestDebugSubCommand
public class QuestDebugSubCommand extends BaseSubCommand {
    
    public QuestDebugSubCommand(RVNKQuests plugin) {
        super(plugin, "debug", "Debug and diagnostics commands",
              "/quest debug <subcommand>", "rvnkquests.admin", false);
    }
    
    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        // 250+ lines of switch statement and handler methods
    }
    
    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        // 50+ lines of completion logic
    }
}
```

### 6.2 Fluent Implementation (Proposed)

```java
// Fluent: ~50 lines for equivalent functionality
CommandBuilder.create(plugin)
    .name("quest")
    .description("Main quest command")
    .subCommand(sub -> sub
        .name("debug")
        .description("Debug and diagnostics commands")
        .permission("rvnkquests.admin")
        .subCommand(nested -> nested
            .name("diagnostics")
            .description("Show system health status")
            .handler((sender, args) -> {
                showDiagnostics(sender);
                return true;
            })
        )
        .subCommand(nested -> nested
            .name("list")
            .description("List all registered quests")
            .handler(this::showQuestList)
        )
        .subCommand(nested -> nested
            .name("player")
            .description("Show player quest progress")
            .handler(this::showPlayerProgress)
            .tabComplete((sender, args) -> 
                args.length == 1 ? getOnlinePlayerNames(args[0]) : List.of()
            )
        )
    )
    .build()
    .register();
```

### 6.3 Async Command Example

```java
// Async database operation with proper thread handling
CommandBuilder.create(plugin)
    .name("questdb")
    .description("Database operations")
    .subCommand(sub -> sub
        .name("query")
        .description("Query quest data")
        .asyncHandler((sender, args) -> {
            return questDatabase.findByPlayer(args[0])
                .thenApply(quests -> {
                    if (quests.isEmpty()) {
                        return CommandResult.error("No quests found");
                    }
                    return CommandResult.success("Found " + quests.size() + " quests");
                });
        })
    )
    .build()
    .register();
```

---

## 7. Success Criteria

### 7.1 Phase 1 (Core Builders)

- [ ] CommandBuilder compiles and works
- [ ] SubCommandBuilder supports all BaseSubCommand features
- [ ] FluentCommand registers with CommandManager
- [ ] Tab completion works with fluent commands
- [ ] Permission checking works correctly
- [ ] Unit test coverage > 80%

### 7.2 Phase 2 (Async Support)

- [ ] Async commands don't block main thread
- [ ] Results delivered on main thread
- [ ] Error handling works correctly
- [ ] Timeout handling implemented
- [ ] Integration tests pass

### 7.3 Phase 3 (Migration - Optional)

- [ ] At least 2 commands migrated to fluent API
- [ ] No regressions in existing functionality
- [ ] Documentation updated
- [ ] Migration guide created

---

## 8. Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Complex commands hard to migrate | LOW | LOW | Keep class-based option |
| Async bugs | MEDIUM | MEDIUM | Comprehensive testing |
| Performance overhead | LOW | LOW | Benchmarking |
| Learning curve | LOW | LOW | Good documentation |

---

## 9. Appendix

### A. Comparison: Class-Based vs Fluent

| Aspect | Class-Based | Fluent |
|--------|-------------|--------|
| **Lines of code** | 100-400 per command | 20-100 per command |
| **Type safety** | Full | Full |
| **IDE support** | Standard | Builder chain |
| **Testing** | Requires mocking | Function testing |
| **Flexibility** | Maximum | Good |
| **Best for** | Complex commands | Simple commands |

### B. Related Documents

- `docs/features/QUEST_SYSTEM_ARCHITECTURE_ANALYSIS.md`
- `docs/README.md`
- `docs/standards/` (coding standards)

### C. Related Tasks

- `test-05`: Command system unit tests
- `test-08`: Integration testing
- `plan-07`: RVNKCore Integration Plan (depends on this)

---

## 10. Decision Record

| Decision | Rationale | Alternatives Considered |
|----------|-----------|------------------------|
| Additive enhancement | No breaking changes | Full rewrite (rejected) |
| Builder pattern | Familiar, type-safe | Annotations (rejected - runtime overhead) |
| Optional async | Not all commands need it | Required async (rejected - complexity) |
| Keep BaseCommand | Complex commands need it | Deprecate (rejected - breaks existing) |

---

**Document Status**: ✅ COMPLETE
**Next Steps**: Implement Phase 1 (CommandBuilder, SubCommandBuilder)
**Assigned**: java-architect
**Related Task**: plan-04 (this document fulfills deliverables)
