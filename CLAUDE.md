# RVNKQuests: AI Assistant Instructions

**Central documentation and integration hub for RVNKQuests quest system development**

**Purpose**: Dynamic narrative quest system for Bukkit/Spigot servers with Archon MCP server integration for task management and knowledge base access.

---

## ⚠️ CRITICAL: ARCHON-FIRST RULE - READ THIS FIRST

**BEFORE doing ANYTHING else on task-related work:**

1. **CHECK** if Archon MCP server is available
2. **USE** Archon task management as PRIMARY system
3. **FOLLOW** task-driven development workflow
4. **Do NOT** use local task tracking - Archon is our source of truth

**This rule overrides ALL other development patterns and instructions.**

---

## Archon Integration & Workflow

**This project uses Archon MCP server for:**
- **Knowledge Management**: RAG-based documentation search
- **Task Tracking**: Project and task organization
- **Project Management**: Cross-agent collaboration

### Core Workflow: Task-Driven Development

**MANDATORY task cycle before coding:**

1. **Get Task** → `find_tasks(task_id="...")` or `find_tasks(filter_by="status", filter_value="todo")`
2. **Start Work** → `manage_task("update", task_id="...", status="doing")`
3. **Research** → Use RAG knowledge base (`rag_search_knowledge_base`)
4. **Implement** → Write code based on research
5. **Review** → `manage_task("update", task_id="...", status="review")`
6. **Next Task** → `find_tasks(filter_by="status", filter_value="todo")`

**NEVER skip task updates. NEVER code without checking current tasks first.**

### RAG Workflow (Research Before Implementation)

**Searching Specific Documentation:**

1. `rag_get_available_sources()` - Get list with id, title, url
2. Match to documentation (e.g., "RVNKQuests objectives" → "src_abc123")
3. `rag_search_knowledge_base(query="quest objectives", source_id="src_abc123")`

**General Research:**
- `rag_search_knowledge_base(query="quest trigger patterns", match_count=5)` - 2-5 keywords only!
- `rag_search_code_examples(query="CompletableFuture async", match_count=3)` - Find code examples

**Task Status Flow:** `todo` → `doing` → `review` → `done`

### Archon Quick Reference

**Projects:**
- `find_projects(query="...")` - Search projects
- `find_projects(project_id="...")` - Get specific project
- `manage_project("create"/"update"/"delete", ...)` - Manage projects

**Tasks:**
- `find_tasks(query="...")` - Search tasks
- `find_tasks(task_id="...")` - Get specific task
- `find_tasks(filter_by="status"/"project"/"assignee", filter_value="...")` - Filter tasks
- `manage_task("create"/"update"/"delete", ...)` - Manage tasks (task_order: 0-100, higher = more priority)

**Knowledge Base:**
- `rag_get_available_sources()` - List available documentation sources
- `rag_search_knowledge_base(...)` - Search documentation
- `rag_search_code_examples(...)` - Find code examples

---

## RVNKQuests Plugin Overview

### What is RVNKQuests?

Dynamic narrative quest system for Bukkit/Spigot servers that creates immersive, event-driven adventures for players. Features include:

- **Quest Framework** - Event-based quest progression system
- **Lore Integration** - Narrative context from RVNKLore
- **Objectives** - Multiple quest objective types (combat, collection, exploration, etc.)
- **Rewards** - Configurable player rewards (items, XP, currency)
- **Triggers** - Location-based, time-based, and event-based quest activation
- **State Management** - Robust quest state tracking and persistence

### Plugin Architecture

**Core Components:**
- **Quest System** - Quest interface, state management, lifecycle
- **Objective Framework** - Event listeners for quest progression
- **Command System** - Admin and player commands
- **Configuration** - YAML-based quest definitions
- **Event System** - Integration with Bukkit events
- **Lore System** - Narrative integration from RVNKLore

### Key Patterns

**Command Pattern:**
- Centralized `CommandManager` with subcommand routing
- Base command abstraction (`BaseCommand`, `BaseSubCommand`)
- Permission checking and player/console support

**Quest State Machine:**
```
NOT_STARTED → QUEST_ACTIVE → QUEST_COMPLETE → QUEST_REWARD_PENDING → QUEST_FINISHED
```

**Objective Pattern:**
- Event-driven objectives with listener pattern
- State-aware progression
- Async-safe completion tracking

**Configuration Pattern:**
- YAML-based quest definitions
- Plugin-level config management
- Hot-reload support

---

## RVNK Plugin Ecosystem

### Active Plugins

**RVNKQuests** (This Project)
- **Status**: Active Development
- **Version**: 1.0-SNAPSHOT
- **Language**: Java 21
- **Build**: Maven
- **Dependencies**: RVNKLore (narrative), Bukkit/Spigot 1.21.4

**RVNKLore**
- Narrative content system
- Player lore tracking and discovery
- Integration point for quest narratives

**RVNKTools** (RVNKCore Foundation)
- Planned core library extraction
- Shared services, database abstraction
- Dependency injection framework

**BarterShops, RVNKWorlds**
- Sibling plugins in ecosystem
- Will consume RVNKCore when available

### Shared Architecture

**All plugins use:**
- CommandManager framework for centralized command handling
- Service-oriented design with interfaces and ServiceRegistry
- Repository pattern for data access layer
- Async operations via CompletableFuture
- Database abstraction (MySQL/SQLite with HikariCP)
- DTO layer for cross-boundary data transfer

**Dependency Graph:**
```
RVNKCore (planned extraction from RVNKTools)
├── RVNKTools (consumer + provider)
├── RVNKLore (consumer)
├── RVNKQuests (consumer) ← YOU ARE HERE
├── RVNKWorlds (consumer)
└── BarterShops (consumer)
```

---

## Tech Stack

### Minecraft Java Plugins

**Language & Runtime:**
- Java 21 (Temurin JDK)
- Paper/Spigot API (1.21.4)
- Maven (XML configuration)

**Database:**
- MySQL 8+ (primary)
- SQLite 3+ (fallback)
- HikariCP connection pooling

**Serialization:**
- Gson (JSON)
- SnakeYAML (YAML)

**Web & APIs:**
- Jetty (for REST APIs, future expansion)

**Plugins & Integrations:**
- Bukkit/Paper API
- PlaceholderAPI (placeholder expansion)
- LuckPerms (permission management)
- Vault (economic API)
- DynMap (map integration)

### Development Tools

**Build & Testing:**
- Maven (build automation)
- JUnit 5 (testing framework)
- Mockito (mocking)

**Code Quality:**
- SonarLint (static analysis)
- Google Code Style (formatting)
- SpotBugs (bug detection)

**Version Control:**
- Git
- Conventional commits

**AI Integration:**
- Archon MCP server (task management & knowledge base)
- GitHub Copilot (code assistance)
- Claude Code (extended AI assistance)

### Complete Language Enumeration

**Languages Used**: Java, Python (Archon server), JavaScript/TypeScript (future web), YAML (config), JSON (data), XML (Maven), Markdown (docs), PowerShell (automation)

---

## Development Standards

### Code Patterns

**✅ DO:**
- Use `CompletableFuture` for async operations
- Implement Repository pattern for data access
- Use Constructor injection for dependencies
- Design for Minecraft version compatibility (1.20+)
- Follow CommandManager framework for commands
- Support console execution for all commands
- Use immutable records for data transfer objects (DTOs)
- Log comprehensively using LogManager

**❌ DON'T:**
- Block main thread with I/O operations
- Use `I` prefix for interfaces (`PlayerService` not `IPlayerService`)
- Hardcode credentials - use environment variables
- Create deeply nested listener hierarchies
- Mix concerns in single classes
- Ignore quest state transitions
- Assume player is online without checking

### Security Requirements

**Credential Management:**
- Never hardcode API keys, database credentials, or secrets
- Use environment variables or secure configuration files
- Exclude credentials from version control

**Production Safety:**
- Validate all external input
- Sanitize quest configuration before use
- Protect against command injection
- Validate player permissions consistently
- Never expose internal state in error messages

### Testing & Validation

**Testing Strategy:**
- Unit tests for critical quest logic
- Integration tests for event-driven features
- Mock Bukkit API for offline testing
- Test quest state transitions thoroughly
- Validate configuration parsing

**Quality Gates:**
- All tests passing before merge
- Code coverage minimum 70% for core systems
- No hardcoded secrets in codebase
- Documentation up-to-date with implementation

### Documentation

**Code Documentation:**
- Javadoc for all public methods
- Inline comments for complex logic
- Clear parameter and return documentation
- Example usage in class-level documentation

**Project Documentation:**
- README.md - Quick start and overview
- ROADMAP.md - Development status and milestones
- CLAUDE.md - AI assistant instructions (this file)
- Instruction files in `.github/` for Copilot users
- Agent definitions in `.claude/agents/` for Claude users

### Git Workflow

**Commit Format:**
```
type(scope): description

- Bullet point details
- Reference Archon task if applicable

Generated with Claude Code
Co-Authored-By: [Name] <[email]>
```

**Types:** `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `perf`

**Branching:**
- `feat/archon-integration` - Feature branches
- `fix/quest-state-bug` - Bug fixes
- `docs/update-readme` - Documentation
- `master` - Production-ready
- `derek/dev` - Development main branch

---

## 🚀 Quick Start

### 1. Check Archon Availability

First, verify if Archon MCP server is available and check for current tasks:

```
Check Archon: find_tasks(filter_by="status", filter_value="todo")
```

### 2. Select Your Development Approach

Claude will automatically load appropriate context based on your task.

**Development workflow:**
```
1. Task-Driven Development (via Archon MCP tools)
2. Code Implementation (following project patterns)
3. Testing & Validation (comprehensive test coverage)
4. Security Review (production safety checks)
5. Documentation (inline comments, docstrings)
6. Git Workflow (conventional commits)
```

### 3. Load Relevant Instruction Files

Based on your current task, load relevant files:

- **Working on quest framework?** Load `.github/supplemental/copilot-instructions.quest.md`
- **Working on objectives?** Load `.github/supplemental/copilot-instructions.objective.md`
- **Working on rewards?** Load `.github/supplemental/copilot-instructions.reward.md`
- **Working on triggers?** Load `.github/supplemental/copilot-instructions.trigger.md`
- **Working on events?** Load `.github/supplemental/copilot-instructions.events.md`
- **Working on migrations?** Load `.github/supplemental/copilot-instructions.migration.md`
- **Working on lore integration?** Load `.github/supplemental/copilot-instructions.lore.md`
- **Testing MCP Server or RVNKQuests integration?** Use **test-orchestrator** agent (`.claude/agents/test-orchestrator.md`)

---

## 📚 Reference Materials

### Project Documentation

- **[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)** - Archon integration plan and timeline
- **[README.md](README.md)** — Project overview, commands, and API usage
- **[ROADMAP.md](ROADMAP.md)** — Development status and milestones (PRIMARY STATUS REFERENCE)

### GitHub Copilot Instruction Files (Reference)

**Note**: These files are designed for GitHub Copilot users. Claude Desktop provides equivalent guidance through conversation.

**Primary Modules** (Core patterns and standards):

- **Navigation Hub**: See [.github/copilot-instructions.md](.github/copilot-instructions.md)
- **Archon Integration**: See [.github/copilot-instructions.archon.md](.github/copilot-instructions.archon.md) - Comprehensive Archon guide
- **Bukkit Patterns**: See [.github/copilot-instructions.bukkit.md](.github/copilot-instructions.bukkit.md) - Framework patterns
- **Code Patterns**: See [.github/copilot-instructions.patterns.md](.github/copilot-instructions.patterns.md) - Common patterns
- **Security Guidelines**: See [.github/copilot-instructions.security.md](.github/copilot-instructions.security.md) - Security requirements
- **Documentation Standards**: See [.github/copilot-instructions.documentation.md](.github/copilot-instructions.documentation.md) - Documentation standards
- **Git Workflow**: See [.github/copilot-instructions.versioning.md](.github/copilot-instructions.versioning.md) - Git workflow

**Supplementary Modules** (Include only when working on specific functionality):

- **Quest Framework**: See [.github/supplemental/copilot-instructions.quest.md](.github/supplemental/copilot-instructions.quest.md)
- **Objectives**: See [.github/supplemental/copilot-instructions.objective.md](.github/supplemental/copilot-instructions.objective.md)
- **Rewards**: See [.github/supplemental/copilot-instructions.reward.md](.github/supplemental/copilot-instructions.reward.md)
- **Triggers**: See [.github/supplemental/copilot-instructions.trigger.md](.github/supplemental/copilot-instructions.trigger.md)
- **Events**: See [.github/supplemental/copilot-instructions.events.md](.github/supplemental/copilot-instructions.events.md)
- **Lore Integration**: See [.github/supplemental/copilot-instructions.lore.md](.github/supplemental/copilot-instructions.lore.md)
- **Migration & Compatibility**: See [.github/supplemental/copilot-instructions.migration.md](.github/supplemental/copilot-instructions.migration.md)
- **RVNKCore Integration**: See [.github/supplemental/copilot-instructions.rvnkcore.md](.github/supplemental/copilot-instructions.rvnkcore.md)
- **Testing**: See [.github/supplemental/copilot-instructions.tests.md](.github/supplemental/copilot-instructions.tests.md)
- **Test Orchestration**: See [.github/supplemental/copilot-instructions.test-orchestrator.md](.github/supplemental/copilot-instructions.test-orchestrator.md)
- **VS Code Tasks**: See [.github/supplemental/copilot-instructions.vscode-tasks.md](.github/supplemental/copilot-instructions.vscode-tasks.md)

### Project Status

**All project status, current achievements, roadmap, and development progress information is maintained in `ROADMAP.md`.**

For current project status, development milestones, and implementation progress, refer to:

- **[ROADMAP.md](ROADMAP.md)** - Primary source for all project status and development timeline
- **[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)** - Archon integration plan (current phase)
- **[Archon Project Board](#archon)** - Task management and tracking

### Claude Agents

For specialized development tasks, use the appropriate agent from `.claude/agents/`:

- **[java-architect.md](.claude/agents/java-architect.md)** - Enterprise Java architecture & patterns
- **[code-reviewer.md](.claude/agents/code-reviewer.md)** - Code review & quality standards
- **[code-archaeologist.md](.claude/agents/code-archaeologist.md)** - Legacy code analysis & refactoring
- **[test-engineer.md](.claude/agents/test-engineer.md)** - Testing & quality assurance
- **[test-orchestrator.md](.claude/agents/test-orchestrator.md)** - RvnkDev MCP Server & RVNKQuests integration testing
- **[build-engineer.md](.claude/agents/build-engineer.md)** - Build system optimization
- **[git-workflow-manager.md](.claude/agents/git-workflow-manager.md)** - Version control & release management
- **[documentation-specialist.md](.claude/agents/documentation-specialist.md)** - Technical documentation
- **[minecraft-rvnk-admin.md](.claude/agents/minecraft-rvnk-admin.md)** - Minecraft server admin
- **[project-architect.md](.claude/agents/project-architect.md)** - Complex project planning
- **[sql-pro.md](.claude/agents/sql-pro.md)** - Database design & optimization

See [.claude/agents/README.md](.claude/agents/README.md) for complete agent directory and selection guidance.

---

## 💡 Best Practices

### Working with Claude

**✅ DO:**

- Use Archon MCP server for task management
- Follow project patterns and conventions
- Ask clarifying questions before implementation
- Ensure quality gates pass
- Document changes in ROADMAP.md for milestones
- Review IMPLEMENTATION_PLAN.md phases before starting work

**❌ DON'T:**

- Skip Archon task tracking workflow
- Ignore security constraints
- Assume context without verification
- Create unnecessary files or documentation
- Override production safety checks
- Code without understanding quest state flows

### Before Starting Work

1. **Check Archon**: Verify task availability and priority
2. **Research**: Use Archon RAG for relevant patterns
3. **Understand**: Read domain-specific instruction files
4. **Review**: Check constraints and requirements
5. **Verify**: Know completion criteria

### During Work

1. **Follow Patterns**: Apply recommended coding standards
2. **Research**: Use Archon knowledge base for decisions
3. **Validate**: Run tests and checks continuously
4. **Document**: Add comments and docstrings
5. **Track**: Update task status in Archon

### After Completion

1. **Verify Quality**: Confirm all requirements met
2. **Run Tests**: Ensure no regressions
3. **Security**: Check for credential exposure
4. **Documentation**: Update README, inline comments
5. **Git Commit**: Use conventional commit format
6. **Archon**: Mark task complete

### Getting Help

1. **Use Archon**: Search knowledge base for context
2. **Ask Claude**: Direct questions for guidance
3. **Review Docs**: Check ROADMAP.md, README.md, IMPLEMENTATION_PLAN.md
4. **Check Agents**: Review relevant Claude agents
5. **Check Patterns**: Look at similar completed work in `src/`

---

## 🎯 Decision-Making Guidelines

### Autonomous Actions (✅ Can proceed without approval)

- Code implementation following patterns
- Test creation for new functions
- Documentation updates
- Refactoring for clarity
- Format/style fixes
- Bug fixes for reported issues

### Constraints (❓ Ask first)

- Creating new quest types or systems
- Changing quest state machine
- Modifying public APIs
- Creating new dependencies
- Breaking changes
- Major architectural shifts

### Quality Gates (✔️ Must pass before completion)

- All tests passing
- Code style passes Google style guide
- Security validation (no hardcoded secrets)
- Documentation complete
- Performance acceptable (no TPS drops)
- Archon task marked as review/done

---

## Project Architecture

### Directory Structure

```
C:\tools\RVNKQuests/
├── .claude/                          # Claude agent definitions
├── .devcontainer/                    # Docker development environment
├── .github/                          # Copilot instructions & CI/CD
├── .vscode/                          # VS Code configuration
├── src/main/java/org/fourz/RVNKQuests/
│   ├── command/                      # Command framework
│   ├── config/                       # Configuration management
│   ├── lore/                         # Lore integration
│   ├── objective/                    # Quest objectives
│   ├── quest/                        # Quest core system
│   ├── reward/                       # Reward system
│   ├── trigger/                      # Quest triggers
│   └── [other modules]               # Event system, migration, etc.
├── metamake/projects/                # Project organization
├── docs/                             # Symlinked documentation
├── pom.xml                           # Maven configuration
├── README.md                         # Project overview
├── ROADMAP.md                        # Development roadmap
└── CLAUDE.md                         # This file
```

### Key Files

| File | Purpose | Size |
|------|---------|------|
| `pom.xml` | Maven project definition | ~4 KB |
| `README.md` | Plugin documentation | ~19 KB |
| `ROADMAP.md` | Development roadmap | ~16 KB |
| `CLAUDE.md` | AI assistant instructions | ~12 KB |

---

## Contact & Support

### Getting Help

- **Archon Knowledge Base**: Use `rag_search_knowledge_base()` for pattern research
- **Code Review**: Use code-reviewer agent or create issue
- **Questions**: Ask Claude directly about patterns or requirements
- **Issues**: Create GitHub issue or Archon task

### Reporting Problems

1. Create Archon task with bug details
2. Include reproduction steps
3. Reference relevant quest/objective if applicable
4. Note server version and configuration

---

**Last Updated**: November 1, 2025
**Version**: 1.0
**Status**: Archon Integration Phase

**Key Integration**: Archon MCP server for task management and knowledge base
**Pattern Source**: RvnkDev MCP Server project
**Adapted By**: Claude Code AI Assistant

For detailed implementation plan, see **[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)**.
