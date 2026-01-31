# RVNKQuests Copilot Instructions

**Parent Hub**: See Ravenkraft-Dev CLAUDE.md for complete ecosystem standards.

## Tool Discovery

**Server Management**: `mcp_rvnkdev-minec_*` tools (console, files, state, db)
**Live Testing**: `/rvnktest [health|services|db|plugins|run all]`
**Agents**: Browse `.claude/agents/` for specialized workflows
**Skills**: Browse `.claude/skills/` for domain capabilities
**Rules Import**: Use `@import ../../.claude/rules/<rule>.md` for shared directives

## Archon Integration

**Board**: `c3d4e5f6-7890-abcd-ef12-345678901234` (RVNKQuests)
**Workflow**: `find_tasks()` → `manage_task("update", status="doing")` → implement → `status="done"`

## Plugin-Specific Standards

### Quest System Architecture
- Quest definitions in YAML configuration
- State machine for quest progression
- NPC interactions via event listeners
- Quest books for player tracking

### Services (via RVNKCore)
- `IQuestService` for quest management
- `IProgressService` for player progress tracking
- Integration with `ILoreService` for lore rewards

### Quest State Flow
`NOT_STARTED` → `IN_PROGRESS` → `COMPLETED` (or `FAILED`)

### Message Prefixes
- `&c▶` usage | `&6⚙` progress | `&a✓` success | `&c✖` error | `&e⚠` warning

### Logging
Use `LogManager.getInstance(plugin, "ClassName")` from RVNKCore.

## References

- **Architecture Patterns**: `docs/architecture/shared-patterns.md`
- **Coding Standards**: `docs/standard/coding-standards.md`
