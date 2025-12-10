# RVNKQuests Instruction Files Cleanup Guide

**Date**: November 8, 2025
**Status**: Audit Complete - Ready for Implementation
**Impact**: Reduce redundancy from 40-50% to <15%, consolidate single source of truth

---

## Overview

This document provides step-by-step cleanup instructions for the RVNKQuests documentation system to eliminate redundancy and establish clear information hierarchies.

**Key Principle**: Each topic should have ONE authoritative source that other files reference.

---

## Cleanup Summary

| File | Current Size | Target Size | Reduction | Priority |
|------|--------------|-------------|-----------|----------|
| CLAUDE.md | 592 lines | 380 lines | 35% | **CRITICAL** |
| .claude/agents/README.md | 231 lines | 200 lines | 13% | HIGH |
| copilot-instructions.test-orchestrator.md | 642 lines | 500 lines | 22% | HIGH |
| copilot-instructions.md | 164 lines | 164 lines | 0% | Keep as-is |
| copilot-instructions.archon.md | 667 lines | 667 lines | 0% | Keep as-is |

**Total Reduction**: ~600-700 lines consolidated (22-23% overall)

---

## Phase 1: CLAUDE.md Cleanup (CRITICAL PRIORITY)

### Edit 1.1: Remove Archon Workflow Duplication

**File**: CLAUDE.md
**Lines to Replace**: 22-73 (52 lines)
**Reason**: Duplicate of archon.md lines 29-171

**CURRENT TEXT** (DELETE THIS):
```markdown
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
```

**REPLACE WITH** (8 lines):
```markdown
## Archon Integration

**Archon MCP server** provides task management, knowledge base access, and cross-agent collaboration.

**Quick Reference**:
1. Check tasks: `find_tasks(filter_by="status", filter_value="todo")`
2. Start work: `manage_task("update", task_id="...", status="doing")`
3. Research: `rag_search_knowledge_base(query="...")`
4. Complete: `manage_task("update", task_id="...", status="done")`

**Complete workflow documentation**: See [.github/copilot-instructions.archon.md](
