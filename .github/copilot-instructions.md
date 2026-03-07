# RVNKQuests Copilot Instructions

**Dynamic narrative quest system** — Event-driven quests, objectives, chains, journal, leaderboards.

---

## Quick Reference

**Tech**: Java 21, Paper 1.21.4, Maven, RVNKCore dependency
**Standards**: See `docs/standard/` in Ravenkraft-Dev

---

## Tool Discovery

### Live Server Testing
```
/rvnktest health              # Full health check
/rvnktest services            # List registered services (IQuestService, etc.)
/rvnktest db                  # Database connectivity
/rvnkquests                   # Plugin commands
```

### MCP Server Management
`mcp_rvnkdev-minec_*` tools for console commands, file operations, server state.

### Claude Integration
- **Rules**: `.claude/rules/` — Import shared patterns
- **Skills**: `.claude/skills/` — Domain capabilities
- **Agents**: `.claude/agents/` — Specialized workflows

---

## Task Management

**GitHub Issues (primary)**: `gh issue list --repo fourz/Ravenkraft-Dev --label "board:rvnkquests"`

**Status flow**: `open` → in progress (comment) → `closed`

---

## Core Directives

- **Use DatabaseManager** as single entry point for all connections
- **Use ServiceRegistry** for RVNKCore integration (reflection-based, softdepend)
- **Use Repository pattern** with `I` prefix interfaces
- **Use DTOs** for all data transfer
- **Use YAML fallback** (QuestProgressYamlRepository) — not SQLite

---

## Registered Services

RVNKQuests registers these with RVNKCore ServiceRegistry:
- `IQuestService` — Quest definitions and lifecycle (QuestManager)
- `IQuestProgressService` — Player quest state and progress
- `IQuestDatabaseService` — Database access
- `IRewardService` — Reward delivery
- `IQuestChainService` — Quest chain management
- `IObjectiveService` — Objective evaluation
- `IJournalService` — Quest history and statistics
- `IRepeatableQuestService` — Repeatability and cooldowns

---

## Quest State Machine

```
NOT_STARTED → TRIGGER_FOUND → QUEST_ACTIVE → OBJECTIVE_FOUND → COMPLETED
                                                               ↘ ABANDONED
```

States in `QuestState.java` — 6 total. Do not add intermediate states without approval.

---

## Objective Logic

`EnhancedObjectiveDTO` supports AND/OR/XOR condition groups. Evaluation handled by `ConditionEvaluator`. Use `ObjectiveGroup` and `ObjectiveCondition` for structured multi-condition objectives.

---

## Message Formatting

| Type | Prefix |
|------|--------|
| Usage | `&c▶` |
| Progress | `&6⚙` |
| Success | `&a✓` |
| Error | `&c✖` |
| Warning | `&e⚠` |
| Tips | `&7   ` |

**Console**: No emojis, no colors — use LogManager.

---

## Logging Standard

```java
private final LogManager logger;

public MyClass(RVNKQuests plugin) {
    this.logger = LogManager.getInstance(plugin, "MyClass");
}
```

---

## Supplemental Instruction Files

Load these when working on specific subsystems:

| Task Area | File |
|-----------|------|
| Quest framework | `.github/supplemental/copilot-instructions.quest.md` |
| Objectives | `.github/supplemental/copilot-instructions.objective.md` |
| Rewards | `.github/supplemental/copilot-instructions.reward.md` |
| Triggers | `.github/supplemental/copilot-instructions.trigger.md` |
| Events | `.github/supplemental/copilot-instructions.events.md` |
| Lore integration | `.github/supplemental/copilot-instructions.lore.md` |
| RVNKCore integration | `.github/supplemental/copilot-instructions.rvnkcore.md` |
| Testing | `.github/supplemental/copilot-instructions.tests.md` |

---

## Documentation References

- **Coding Standards**: `docs/standard/coding-standards.md`
- **Architecture**: `docs/architecture/shared-patterns.md`
- **RVNKCore Integration**: `docs/standard/rvnkcore-integration.md`
- **Database Patterns**: `docs/standard/database-patterns.md`
