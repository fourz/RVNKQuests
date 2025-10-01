# RVNKQuests Copilot Instructions

These guidelines should be followed when modifying or creating code for RVNKQuests, a dynamic narrative quest system for Bukkit/Spigot servers. RVNKQuests is part of the RVNK plugin ecosystem and follows ecosystem-wide standards.

## Core RVNK Ecosystem Standards

*See comprehensive framework: [RVNK Plugin Ecosystem Guidelines](shared/derek/.github/instruction-sets/core/.github/copilot-instructions.md)*

RVNKQuests follows all RVNK ecosystem standards including:

- **Command Framework**: Use CommandManager with BaseCommand/BaseSubCommand pat+terns
- **Service Architecture**: ServiceRegistry pattern for dependency injection
- **Asynchronous Operations**: CompletableFuture for database and I/O operations 
- **Logging Standards**: RVNKLogger via LogManager for all logging operations
- **Resource Management**: Proper lifecycle management and cleanup patterns
- **Error Handling**: Meaningful error messages with proper exception handling

## Plugin Overview

RVNKQuests is a narrative-driven quest system that creates immersive, event-based adventures for Minecraft servers. It features:

- **Dynamic Quest System**: Event-triggered quests that adapt to player actions and server events
- **State-Based Quest Management**: Sophisticated quest state tracking with listener management 
- **Narrative Integration**: Optional lore database for rich storytelling
- **Server Event Integration**: Quests that respond to natural server events and player interactions
- **Command Framework**: Comprehensive admin tools for quest management and debugging

## Architecture Overview

RVNKQuests follows a manager-based architecture with clear separation of concerns:

```
RVNKQuests/
├── quest/                # Core quest system
│   ├── Quest.java        # Quest interface
│   ├── QuestManager.java # Central quest coordination
│   ├── QuestState.java   # State enumeration
│   └── [Quest Implementations]
├── command/             # Command system
├── config/              # Configuration management
├── lore/                # Optional narrative database
├── objective/           # Quest objective listeners
├── trigger/             # Quest trigger handlers
├── reward/              # Quest rewards system
└── util/                # Utilities and helpers
```

## Core Development Guidelines

### Quest System Architecture
*See detailed patterns: [Quest Development Guidelines](copilot-instructions.quest.md)*

### Event-Driven Design
*See detailed patterns: [Event-Driven Quest Design](copilot-instructions.events.md)*

### Lore Integration
*See detailed patterns: [Lore Integration Guidelines](copilot-instructions.lore.md)*

### Command Framework
*See detailed instructions: [Command Framework Guidelines](shared/derek/.github/instruction-sets/core/.github/copilot-instructions.commands.md)*

### Logging Standards
*See detailed instructions: [Logging Standards](shared/derek/.github/instruction-sets/core/.github/copilot-instructions.logging.md)*

### RVNKCore Integration
*See detailed patterns: [RVNKCore Integration Guidelines](copilot-instructions.rvnkcore.md)*

### Migration and Compatibility
*See detailed guidelines: [Migration and Compatibility](copilot-instructions.migration.md)*

## Development Workflow 

*See comprehensive workflow documentation: [VS Code Development Tasks](shared/derek/.github/instruction-sets/core/.github/copilot-instructions.vscode-tasks.md)*

RVNKQuests includes optimized development workflow integration:

**Primary Development Tasks:**
- **Build Plugin**: `mvn clean package`
- **Build & Deploy**: Complete sequence with validation
- **Copy to Server**: Deploy to development server  
- **Restart Server**: Full server restart with monitoring

**Quest-Specific Development:**
- **Query Console - Plugin Messages**: Filter for RVNKQuests log entries
- **Send Server Command**: Execute quest commands for testing
- **Clean Database**: Reset quest progress for testing

**Key Bindings:**
- `Ctrl+Shift+-`: Build and copy to local development server
- `Ctrl+Shift+/`: Restart development server
- `Ctrl+Shift++`: Deploy to test server

## Testing Guide

For testing and debugging quests:

```bash
# Test quest triggering
/quest trigger piglin_far_from_home around

# Check quest states
/quest debug piglin_far_from_home

# Manually advance quest state (debugging)
/quest state piglin_far_from_home QUEST_ACTIVE

# Reload configuration
/quest reload
```

## Documentation and Reference Structure

### Primary Documentation Files

- **README.md**: Main project description, features overview, and quest catalog
- **ROADMAP.md**: Current implementation status, development priorities, and timelines
- **docs/**: Comprehensive technical documentation and guides

### Reference Documentation

The following documentation should be referenced only when relevant to specific prompts:

#### Quest Development and Implementation
- `docs/Abstract Quest Implementation.md` - Quest system architecture patterns
- `docs/RVNKQuests Ideas.md` - Quest concepts and implementation strategies
- `docs/implementation/` - Detailed implementation guides

#### Configuration and Integration
- `docs/config.yml` - Configuration schema and examples
- `docs/RVNKQuests Lore Integration.md` - Lore system integration
- `docs/api-reference/` - API documentation

#### Development and Testing
- `docs/RVNKQuests Code Review Plan.md` - Code quality guidelines
- `docs/tests/` - Testing documentation and test cases
- `docs/plans/` - Development plans

### Documentation Usage Guidelines

- **README.md contains project overview** and current quest catalog
- **ROADMAP.md contains implementation status** and timelines
- **Reference docs for specific technical details** as needed
- **Quest development guides for implementation patterns** 

This comprehensive set of standards ensures consistency with the RVNK plugin ecosystem while maintaining RVNKQuests' unique quest-driven architecture and narrative focus.