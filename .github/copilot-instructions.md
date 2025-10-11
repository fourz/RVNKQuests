# GitHub Copilot Instructions for RVNKQuests

This is the main index file for GitHub Copilot instructions. It provides contextual access to specialized instruction modules based on the current development context.

## 📁 Project Structure & Navigation

**CRITICAL for AI Context**: Always validate your working directory and project structure before executing commands.

### Absolute Project Paths (Windows)

```
c:\tools\RVNKQuests\                                # PROJECT_ROOT
├── .github\                                        # AI guidance and workflows
│   ├── copilot-instructions.md                   # This file
│   ├── copilot-instructions.*.md                 # Core modules (always loaded)
│   └── supplemental\                             # Contextual modules (load as needed)
├── README.md                                      # Project overview
├── ROADMAP.md                                     # Current status (PRIMARY REFERENCE)
├── docs\                                          # Documentation
├── src\                                           # MAIN DEVELOPMENT DIRECTORY
│   ├── main\java\rvnk\rvnkquests\               # Source code
│   │   └── RVNKQuests.java                       # Application entry point
│   └── main\resources\                           # Configuration files
├── pom.xml                                        # Maven configuration
├── target\                                        # Build output
├── metamake\                                      # Project management framework
└── BuildTools\                                    # Spigot build tools
```

### Navigation Commands (Always Use These)

```powershell
# Navigate to project root (from anywhere)
Set-Location "c:\tools\RVNKQuests"

# Navigate to main development directory
Set-Location "c:\tools\RVNKQuests\src\main\java\rvnk\rvnkquests"

# Navigate to source code directory
Set-Location "c:\tools\RVNKQuests\src"
```

### Path Validation Before Commands

```powershell
# Verify current location before operations
Get-Location
Get-ChildItem  # Confirm expected files exist

# Test for specific files before referencing
Test-Path "pom.xml"
Test-Path "src\main\java\rvnk\rvnkquests\RVNKQuests.java"
```

## Minimal Core Modules (Always Included)

Keep the core instruction set intentionally small — these files provide essential, project-wide patterns and safety rules that should be loaded for every session.

The minimal set included by default:

- **[Bukkit/Spigot Framework Usage](copilot-instructions.bukkit.md)** — core framework patterns and plugin setup
- **[Common Patterns](copilot-instructions.patterns.md)** — reusable code patterns and best practices
- **[Security Requirements](copilot-instructions.security.md)** — production safety, credential handling, and security checks
- **[Documentation Standards](copilot-instructions.documentation.md)** — documentation placement and ROADMAP maintenance
- **[Git Workflow](copilot-instructions.versioning.md)** — commit/branching standards and release hygiene

**Notes**:
- Keep quest-specific, operation-specific, and deployment guides in supplemental modules
- Load supplemental modules only when contextually relevant to current work

## Supplementary Instruction Modules (Included as Needed)

The following instruction files are stored in **`.github\supplemental\`** and should be **included only when contextually relevant** to reduce token usage:

**Quest Development**:
- **[Quest System Architecture](supplemental/copilot-instructions.quest.md)** - Core quest implementation patterns
- **[Event-Driven Design](supplemental/copilot-instructions.events.md)** - Event listener and trigger patterns
- **[Quest Objectives](supplemental/copilot-instructions.objective.md)** - Objective and reward systems
- **[Quest Triggers](supplemental/copilot-instructions.trigger.md)** - Quest trigger implementations

**Narrative and Lore**:
- **[Lore Integration](supplemental/copilot-instructions.lore.md)** - Narrative database integration
- **[Reward Systems](supplemental/copilot-instructions.reward.md)** - Quest reward implementations

**System Integration**:
- **[RVNKCore Integration](supplemental/copilot-instructions.rvnkcore.md)** - Optional core service integration
- **[Migration and Compatibility](supplemental/copilot-instructions.migration.md)** - Legacy system migration patterns

**Development Environment**:
- **[VS Code Tasks and MCP](supplemental/copilot-instructions.vscode-tasks.md)** - Development workflow integration
- **[Testing Framework](supplemental/copilot-instructions.tests.md)** - Testing patterns and validation

**Complete Guide**: See **[copilot-instructions.supplemental.md](copilot-instructions.supplemental.md)** for comprehensive usage guidelines

## Contextual Usage Guidelines

**CRITICAL**: Only include specific supplementary instruction modules relevant to your current development context.

### Module Selection Strategy

**Always Include (Primary Modules)**:
- Load primary modules for every development session
- These contain essential patterns, security requirements, and core framework guidance
- Total: ~2,500 lines optimized for consistent usage

**Include Selectively (Supplementary Modules)**:
- Choose only supplementary modules relevant to current work
- Each module is ~200-400 lines focused on specific functionality
- Reduces context by 60-80% compared to loading all modules

## Quick Reference

For implementation patterns and examples, refer to the relevant primary instruction files:

- **Plugin Setup**: See [Bukkit/Spigot Framework Usage](copilot-instructions.bukkit.md)
- **Code Patterns**: See [Common Patterns](copilot-instructions.patterns.md)
- **Security Guidelines**: See [Security Requirements](copilot-instructions.security.md)
- **Documentation Standards**: See [Documentation Standards](copilot-instructions.documentation.md)
- **Git Workflow**: See [Git Workflow](copilot-instructions.versioning.md)

For supplementary modules, include only when working on specific functionality:

- **Quest Development**: See [supplemental/copilot-instructions.quest.md](supplemental/copilot-instructions.quest.md)
- **Event Systems**: See [supplemental/copilot-instructions.events.md](supplemental/copilot-instructions.events.md)
- **Testing Framework**: See [supplemental/copilot-instructions.tests.md](supplemental/copilot-instructions.tests.md)
- **VS Code Integration**: See [supplemental/copilot-instructions.vscode-tasks.md](supplemental/copilot-instructions.vscode-tasks.md)

## Project Status and Current State

**All project status, current achievements, roadmap, and development progress information is maintained in `ROADMAP.md`.**

For current project status, development milestones, and implementation progress, refer to:
- **[ROADMAP.md](../ROADMAP.md)** - Primary source for all project status and development timeline
- **[docs/milestones/](../docs/milestones/)** - Detailed completion documentation

## Key Development Reminders

- **Module Selection**: Include only relevant supplemental modules to optimize token usage
- **File Creation**: Be selective - only suggest new files when explicitly requested or clearly necessary
- **Status Updates**: Update `ROADMAP.md` for any project milestone or status changes
- **Documentation**: Follow standards in `copilot-instructions.documentation.md`
- **Security**: Always follow production safety guidelines in `copilot-instructions.security.md`
- **Git Workflow**: Use conventional commit format as specified in `copilot-instructions.versioning.md`

Keep this index file minimal and focused on module organization. Detailed implementation guidance belongs in specialized files.