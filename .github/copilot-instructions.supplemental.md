# Supplementary Copilot Instructions Reference

## Purpose

Supplementary instruction modules provide specialized guidance for specific quest development contexts. Load selectively based on current task to optimize token usage.

## Module Organization

### Supplementary Modules Directory: `.github/supplemental/`

**Quest Development**:

- `copilot-instructions.quest.md` - Core quest implementation patterns and state management
- `copilot-instructions.events.md` - Event-driven design patterns for quest listeners
- `copilot-instructions.objective.md` - Quest objective systems and progress tracking
- `copilot-instructions.trigger.md` - Quest trigger implementations and detection logic

**Narrative and Lore**:

- `copilot-instructions.lore.md` - Lore database integration for quest storytelling
- `copilot-instructions.reward.md` - Quest reward systems and distribution logic

**System Integration**:

- `copilot-instructions.rvnkcore.md` - Optional RVNKCore service integration patterns
- `copilot-instructions.migration.md` - Legacy system migration and LogManager transition

**Development Environment**:

- `copilot-instructions.vscode-tasks.md` - VS Code development workflow and MCP integration
- `copilot-instructions.tests.md` - Testing frameworks and quest validation patterns

## Usage Guidelines

### When to Include Supplementary Modules

**Rule of thumb**: Include only modules directly relevant to your current quest development task.

**Quest Implementation Work** → Include quest.md + events.md + objective.md
**Narrative Content** → Include lore.md + reward.md  
**System Integration** → Include rvnkcore.md or migration.md
**Development Workflow** → Include vscode-tasks.md + tests.md
**Legacy Migration** → Include migration.md

### Module Selection Strategy

**Selective Loading Benefits**:

- Reduces token usage by 60-80%
- Improves AI response relevance for quest development
- Focuses guidance on current quest implementation context

**Best Practices**:

1. Load only relevant modules for current quest work
2. Combine related modules when implementing complex quest features
3. Reference module index for complete quest development guidance
4. Update context as quest development focus changes

## Quick Reference

**Complete Module Index**: See module list above

**Module Selection Help**:

- Working on new quest implementation → Include quest.md + events.md
- Working on quest objectives and rewards → Include objective.md + reward.md  
- Working on quest storytelling → Include lore.md + reward.md
- Working on testing quest features → Include tests.md
- Working on deployment workflow → Include vscode-tasks.md
- Working on LogManager migration → Include migration.md

## Quest Development Context Guidelines

### Core Quest Implementation
Load when implementing new quest classes or modifying quest state management:
- `quest.md` - Essential quest patterns and state management
- `events.md` - Event listener patterns for quest triggers and objectives

### Quest Feature Enhancement
Load when adding quest objectives, rewards, or narrative elements:
- `objective.md` - Quest progress tracking and objective systems
- `reward.md` - Quest reward distribution and validation
- `lore.md` - Narrative integration and storytelling features

### Development and Testing
Load when working on quest testing or deployment workflows:
- `tests.md` - Quest testing patterns and validation frameworks  
- `vscode-tasks.md` - Development workflow with RVNKDev MCP integration

### System Integration and Migration
Load when working on RVNKCore integration or legacy system updates:
- `rvnkcore.md` - Optional integration with RVNK ecosystem services
- `migration.md` - LogManager migration from Debug class, compatibility patterns

---

**Remember**: Selective inclusion improves AI assistance while providing comprehensive quest development guidance when needed.