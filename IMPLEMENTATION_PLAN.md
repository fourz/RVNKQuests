# RVNKQuests: Archon Integration & Pattern Implementation Plan

**Date**: November 1, 2025
**Objective**: Implement RvnkDev MCP Server patterns into RVNKQuests including Archon integration, supplemental instruction architecture, Claude agents, and metamake integration.

---

## Executive Summary

RVNKQuests will be upgraded to match the sophisticated development infrastructure of RvnkDev MCP Server. This involves implementing:

1. **Archon-First Workflow** - Task-driven development with knowledge base integration
2. **CLAUDE.md** - Central documentation hub for AI assistants
3. **Expanded Instruction Architecture** - Core + Supplemental modules
4. **Claude Agents System** - Specialized roles for targeted development
5. **Metamake Integration** - Structured project management framework
6. **ROADMAP.md Expansion** - Migration tasks and milestones

### Current State
- ✅ **Partially Complete**: 11 Claude agents, 10 supplemental instructions
- ✅ **Partially Complete**: 7 core Copilot instructions
- ❌ **Missing**: CLAUDE.md, Archon integration, Metamake structure, Expanded ROADMAP

---

## Phase 1: Core Infrastructure (Immediate)

### 1.1 Create CLAUDE.md (Root Level)

**Purpose**: Central documentation hub for AI assistants integrating with Archon MCP server

**Location**: `C:\tools\RVNKQuests\CLAUDE.md`

**Content Structure**:
```markdown
# RVNKQuests: AI Assistant Instructions

## ⚠️ CRITICAL: ARCHON-FIRST RULE
1. Check if Archon MCP server is available
2. Use Archon task management as PRIMARY system
3. Follow task-driven development workflow

## Archon Integration & Workflow
- Core Workflow: Task-Driven Development (todo → doing → review → done)
- RAG Workflow: Search knowledge base before implementation
- Quick Reference: Projects, Tasks, Knowledge Base

## RVNK Plugin Ecosystem
- Plugin dependencies and relationships
- Shared architecture patterns
- Tech stack overview

## Development Standards
- Code patterns, security, testing, documentation
- Best practices and constraints

## Reference Materials
- ROADMAP.md (project status)
- .github/ (instruction files)
- .claude/agents/ (specialized agents)
- metamake/ (project management)
```

**Status**: Create with content from RvnkDev MCP Server CLAUDE.md template

**Lines of Code**: ~280 (reference from source)

---

### 1.2 Update copilot-instructions.md (Main Index)

**Purpose**: Update to include Archon integration and navigation

**Location**: `C:\tools\RVNKQuests\.github\copilot-instructions.md`

**Changes Required**:
- ✅ Already has sections: Project Structure, Module Organization
- ❌ Add: CRITICAL ARCHON-FIRST WORKFLOW section (top priority)
- ❌ Add: Link to CLAUDE.md
- ✅ Already references agents and supplemental modules
- ❌ Update: Add specific reference to Archon task board for project status

**Priority**: HIGH - This is the navigation hub

---

### 1.3 Create copilot-instructions.archon.md (Core Module)

**Purpose**: Comprehensive Archon integration and RAG workflow guide

**Location**: `C:\tools\RVNKQuests\.github\copilot-instructions.archon.md`

**Content Scope**:
- Archon-first rule and task-driven development
- RAG workflow (knowledge base search)
- Quick reference for Archon tools
- RVNKQuests project ecosystem context
- Development standards
- Tech stack overview

**Structure**: Based on RvnkDev MCP Server version, adapted for RVNKQuests

**Status**: Adapt from shared rvnkdev-mcp-server version

**Lines of Code**: ~180

---

### 1.4 Update copilot-instructions.supplemental.md

**Purpose**: Guide on using supplemental modules (context-specific)

**Location**: `C:\tools\RVNKQuests\.github\copilot-instructions.supplemental.md`

**Changes Required**:
- ✅ Already exists and is well-structured
- ❌ Update module list to reflect RVNKQuests-specific supplemental modules:
  - ✅ copilot-instructions.quest.md (already exists)
  - ✅ copilot-instructions.objective.md (already exists)
  - ✅ copilot-instructions.reward.md (already exists)
  - ✅ copilot-instructions.trigger.md (already exists)
  - ✅ copilot-instructions.lore.md (already exists)
  - ✅ copilot-instructions.events.md (already exists)
  - ✅ copilot-instructions.migration.md (already exists)
  - ✅ copilot-instructions.rvnkcore.md (already exists)
  - ✅ copilot-instructions.tests.md (already exists)
  - ✅ copilot-instructions.vscode-tasks.md (already exists)

**Status**: Update index and add Archon-related guidance

---

## Phase 2: Agent System & Documentation (Days 1-2)

### 2.1 Verify & Update Claude Agents

**Current State**: ✅ 11 agents already exist in `.claude/agents/`

Agents:
1. ✅ build-engineer.md
2. ✅ code-archaeologist.md
3. ✅ code-reviewer.md
4. ✅ documentation-specialist.md
5. ✅ git-workflow-manager.md
6. ✅ java-architect.md
7. ✅ minecraft-rvnk-admin.md
8. ✅ project-architect.md
9. ✅ sql-pro.md
10. ✅ test-engineer.md
11. ✅ README.md (agent directory guide)

**Action Required**:
- [ ] Review each agent for RVNKQuests-specific context
- [ ] Update minecraft-rvnk-admin.md with RVNKQuests test server details
- [ ] Ensure all agents reference Archon as task management system
- [ ] Add reference to CLAUDE.md in README.md

**Validation**: All agents should have:
- Archon task management reference
- RVNKQuests context (plugins, dependencies, architecture)
- Clear specialization and constraints

---

### 2.2 Update agents/README.md

**Current Content**: ✅ Comprehensive agent directory

**Changes**:
- ❌ Add Archon integration note
- ❌ Update RVNKQuests project context
- ❌ Link to CLAUDE.md for workflow overview

**Status**: Minor updates, existing structure is good

---

## Phase 3: Metamake Integration (Days 2-3)

### 3.1 Explore Current Metamake Structure

**Current State**:
- Metamake referenced in instructions but may not be fully set up in RVNKQuests
- RvnkDev MCP Server has comprehensive `metamake/projects/` structure

**Action**:
1. Check if `C:\tools\RVNKQuests\metamake/` exists
2. If exists, review existing project structure
3. If missing, create minimal metamake for RVNKQuests

**Key Projects to Create** (from RvnkDev pattern):
- **PROJECT: RVNKQuests Core Refactoring** (main project)
- **PROJECT: Archon Integration** (sub-project)
- **PROJECT: RVNKCore Migration** (future phase)
- **PROJECT: Test Coverage Expansion** (ongoing)

---

### 3.2 Create/Update metamake/projects/ Structure

**Directory**: `C:\tools\RVNKQuests\metamake\projects\`

**Structure**:
```
metamake/projects/
├── 1-archon-integration/
│   ├── README.md (project overview)
│   ├── roadmap.md (phased implementation)
│   ├── checklist.md (completion criteria)
│   └── tasks/ (task definitions)
├── 2-core-refactoring/
│   ├── README.md
│   ├── roadmap.md
│   └── tasks/
├── project-status-tracking/
│   ├── data/ (tool inventory, version tracking)
│   ├── validation/ (documentation sync checklist)
│   └── reports/ (status assessment)
└── README.md (metamake index)
```

**Status**: Create if missing, populate with project definitions

---

## Phase 4: Roadmap & Task Management (Days 3-4)

### 4.1 Expand ROADMAP.md

**Current State**: `ROADMAP.md` exists with 401 lines

**Changes Required**:

#### Update Structure:
```markdown
# RVNKQuests Development Roadmap

## Project Status Overview
- Version: 1.0
- Java Version: 21
- Build System: Maven
- Latest Release: [version]

## Active Initiatives

### Initiative 1: Archon Integration (CURRENT)
- Status: In Progress
- Scope: Full AI assistant integration with task management
- Timeline: November 2025
- Deliverables:
  - CLAUDE.md in project root
  - Archon task board with refactoring items
  - Knowledge base integration

### Initiative 2: RVNKCore Migration (PLANNED)
- Status: Planning
- Scope: Integrate RVNKCore services (from migration doc)
- Timeline: Q1 2026
- Dependencies: RVNKCore extraction from RVNKTools

### Initiative 3: Test Coverage Expansion (ONGOING)
- Current Coverage: [%]
- Target Coverage: 80%
- Focus Areas: Critical paths, quest state transitions

## Milestones

### Milestone: Core Infrastructure
- CLAUDE.md created ✅/❌
- Archon integration complete ✅/❌
- Task board populated ✅/❌

### Milestone: RVNKCore Migration
- Service migration framework ✅/❌
- Repository pattern implementation ✅/❌
- Database abstraction layer ✅/❌

## Completed Features
- [List current features from original ROADMAP]

## In Progress
- [Current work items]

## Planned Features
- [Future work items]

## Known Issues
- [Technical debt, bugs, limitations]

## Performance Metrics
- Build time: [measurement]
- Test execution: [measurement]
- Plugin load time: [measurement]
```

**Status**: Expand with phased work and Archon integration items

---

### 4.2 Create Archon Task Definitions

**Action**: Set up in Archon MCP server (after Phase 5 initialization)

**Tasks to Create** (with priorities):

#### High Priority (100-90):
1. **Implement CLAUDE.md** - Central AI documentation hub
2. **Update copilot-instructions.md** - Add Archon-first section
3. **Create copilot-instructions.archon.md** - Comprehensive Archon guide

#### Medium-High Priority (80-60):
4. **Validate Claude agents** - Ensure RVNKQuests context
5. **Setup metamake structure** - Project organization
6. **Update ROADMAP.md** - Migration roadmap

#### Medium Priority (50-40):
7. **Document Archon workflow** - Developer guide
8. **Create quick-start guide** - Onboarding documentation
9. **Update README.md** - Link to new documentation

#### Future Work (Linked to migration document):
10. **Plan RVNKCore migration** - Service extraction (ref: to-rvnkcore.json)
11. **Design test framework** - Comprehensive testing
12. **Refactor command system** - Pattern consistency

---

## Phase 5: Archon Project Setup (External)

### 5.1 Initialize Archon Project

**Action**: Via Archon MCP server (requires external system)

**Project Details**:
```json
{
  "name": "RVNKQuests",
  "description": "Dynamic narrative quest system for Bukkit/Spigot with Archon integration",
  "github_repo": "https://github.com/fourz/RVNKQuests.git",
  "status": "active",
  "owner": "Derek Schrishuhn",
  "team": ["Development Team"]
}
```

**Tasks**: Populated with items from Phase 4.2

**Knowledge Base**: Link RVNKQuests documentation sources

---

### 5.2 Populate Archon Task Board

**Status Workflow**:
```
todo → doing → review → done
```

**Task Categories**:
1. **Archon Integration** (4-6 tasks)
2. **Documentation & Setup** (5-8 tasks)
3. **Code Quality** (3-5 tasks)
4. **Future Work** (RVNKCore migration, etc.)

---

## Phase 6: Validation & Testing (Days 4-5)

### 6.1 Validation Checklist

- [ ] CLAUDE.md created and linked from README.md
- [ ] copilot-instructions.md updated with Archon section
- [ ] copilot-instructions.archon.md created
- [ ] All agents reference Archon and RVNKQuests context
- [ ] Metamake structure complete with project definitions
- [ ] ROADMAP.md updated with migration/refactoring items
- [ ] Archon project initialized with populated task board
- [ ] All cross-references validated (no broken links)
- [ ] Documentation synchronized across all files

### 6.2 Quality Checks

- [ ] Links in all instruction files are valid
- [ ] Agent definitions are complete and RVNKQuests-specific
- [ ] Archon quick reference is accurate
- [ ] Task board is populated and prioritized
- [ ] ROADMAP mirrors Archon tasks

---

## Phase 7: Git Commit & Documentation (Day 5)

### 7.1 Commit Changes

**Branch**: Create feature branch
```bash
git checkout -b feat/archon-integration
```

**Commit Message**:
```
feat(archon): integrate Archon MCP server for task management

- Add CLAUDE.md as central AI documentation hub
- Create archon-specific copilot instructions
- Update instruction architecture with Archon-first workflow
- Populate Archon project board with migration tasks
- Expand ROADMAP with refactoring timeline
- Setup metamake project structure

This commit establishes Archon as the primary task management
system and implements the pattern from RvnkDev MCP Server project.
```

**Files Changed**:
- CLAUDE.md (new)
- .github/copilot-instructions.md (updated)
- .github/copilot-instructions.archon.md (new)
- .github/copilot-instructions.supplemental.md (updated)
- .claude/agents/README.md (updated)
- ROADMAP.md (expanded)
- metamake/projects/ (created/updated)

### 7.2 Update README.md

Add section linking to new documentation:
```markdown
## Development Workflow

This project uses **Archon MCP server** for task management and knowledge base integration.

### Getting Started with Development
1. Read [CLAUDE.md](CLAUDE.md) - Central AI assistant documentation
2. Review [.github/copilot-instructions.md](.github/copilot-instructions.md) - Navigation hub
3. Check [ROADMAP.md](ROADMAP.md) - Current project status
4. Access [Archon Project Board](#archon) - Task management

For detailed instruction files:
- **Primary Modules**: Core patterns and standards in `.github/`
- **Supplemental Modules**: Quest-specific guidance in `.github/supplemental/`
- **Claude Agents**: Specialized roles in `.claude/agents/`
```

---

## Implementation Timeline

```
Phase 1: Core Infrastructure         [1-2 days]
├── CLAUDE.md
├── copilot-instructions.archon.md
└── Update core instruction files

Phase 2: Agent System & Docs          [1 day]
├── Verify Claude agents
└── Update agent README

Phase 3: Metamake Integration         [1-2 days]
├── Create metamake structure
└── Define projects

Phase 4: Roadmap & Tasks              [1-2 days]
├── Expand ROADMAP.md
└── Define task structure

Phase 5: Archon Project Setup         [1 day - External]
├── Initialize Archon project
└── Populate task board

Phase 6: Validation & Testing         [0.5-1 day]
├── Checklist validation
└── Cross-reference validation

Phase 7: Git & Documentation          [0.5 day]
├── Create commits
└── Update README
```

**Total Duration**: 5-8 business days (with Archon system setup)

---

## Success Criteria

### Immediate Goals (Phase 1-2)
- ✅ CLAUDE.md exists and is comprehensive
- ✅ Archon-first workflow documented
- ✅ All instruction files updated

### Phase 3-4 Goals
- ✅ Metamake structure operational
- ✅ ROADMAP aligned with Archon
- ✅ Task definitions complete

### Phase 5-6 Goals
- ✅ Archon project initialized
- ✅ Task board populated and prioritized
- ✅ All validations pass

### Phase 7 Goals
- ✅ Changes committed to feature branch
- ✅ Pull request ready for review
- ✅ Documentation synchronized

---

## Next Steps After Phase 7

1. **Push feature branch and create pull request**
2. **Execute Archon integration tasks from task board**
3. **Begin RVNKCore migration planning** (Phase 2 work)
4. **Populate knowledge base** with quest-specific documentation
5. **Establish development cadence** using Archon workflow

---

## References

- **Source Pattern**: `C:\tools\RVNKQuests\shared\derek\shared\rvnkdev-mcp-server\`
- **Migration Guide**: `shared\derek\metamake\projects\archon-doc-sync\output\migration\to-rvnkcore.json`
- **Current RVNKQuests**: `C:\tools\RVNKQuests\`

---

**Plan Version**: 1.0
**Created**: November 1, 2025
**Status**: Ready for Implementation
