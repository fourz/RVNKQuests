# Archon MCP Server Integration Guide

**Comprehensive Archon integration and task-driven development workflow for RVNKQuests**

This document provides detailed guidance on using Archon MCP server for knowledge management, task tracking, and project organization in RVNKQuests development.

---

## ⚠️ CRITICAL: ARCHON-FIRST RULE

**BEFORE doing ANYTHING on task-related work:**

1. **STOP** and check if Archon MCP server is available
2. **USE** Archon task management as PRIMARY system
3. **FOLLOW** the task-driven development workflow (see below)
4. **Do NOT** use IDE's local task tracking - Archon is source of truth

**This rule overrides ALL other instructions for task management and project planning.**

See [CLAUDE.md](../../CLAUDE.md) for overview and quick reference.

---

## Archon Integration Workflow

### Core Concept: Task-Driven Development

The foundational principle is **never code without checking tasks first**. Every development session follows this cycle:

```
┌─────────────┐
│ Get Task    │ → Find todo tasks in Archon
├─────────────┤
│ Start Work  │ → Update task status to "doing"
├─────────────┤
│ Research    │ → Use RAG knowledge base for patterns
├─────────────┤
│ Implement   │ → Write code following patterns
├─────────────┤
│ Test        │ → Validate implementation
├─────────────┤
│ Review      │ → Update task status to "review"
├─────────────┤
│ Complete    │ → Mark task as "done"
└─────────────┘
```

### Step 1: Get Task

**Option A: Work on existing todo tasks**
```
find_tasks(filter_by="status", filter_value="todo")
```

**Option B: Get specific task**
```
find_tasks(task_id="task-uuid-here")
```

**Option C: Search for task by keyword**
```
find_tasks(query="quest objective")
```

**Response**: Returns list of tasks with:
- Task ID
- Title
- Description
- Status
- Assignee
- Priority (task_order: 0-100, higher = more important)

### Step 2: Start Work

Update task status to "doing" when beginning work:

```
manage_task("update", task_id="task-123", status="doing")
```

**Best Practice**: Include estimated time in comment if Archon supports it, or mention it in commit message.

### Step 3: Research with RAG

Before writing code, search the knowledge base for relevant patterns:

#### Searching Specific Documentation

**Get available sources:**
```
rag_get_available_sources()
```

Returns list of documentation sources with IDs. Match to RVNKQuests-related sources.

**Search specific source:**
```
rag_search_knowledge_base(
    query="quest state machine",
    source_id="src_rvnkquests",
    match_count=5
)
```

#### General Research

For broad pattern searches without specific source:

```
rag_search_knowledge_base(query="CompletableFuture async", match_count=5)
```

**Key Rule**: Keep queries SHORT and FOCUSED (2-5 keywords only)

✅ GOOD: `"quest trigger patterns"`, `"event listener registration"`, `"quest state transitions"`

❌ BAD: `"how do I implement quest triggers and event listeners for multi-objective quests in RVNKQuests"`

#### Search Code Examples

Find working code examples from knowledge base:

```
rag_search_code_examples(query="quest state machine", match_count=3)
```

Returns code snippets with explanations.

### Step 4: Implement Code

Write code based on research findings, following patterns from:
- Related code in `src/`
- RAG search results
- Relevant instruction files (load via supplemental modules)

### Step 5: Test Implementation

Validate your code:
- Run existing test suite: `mvn test`
- Create new tests for coverage
- Manual testing on dev server
- Validate quest state transitions
- Check console for error messages

### Step 6: Request Review

Update task status to "review":

```
manage_task("update", task_id="task-123", status="review")
```

Code-reviewer agent can be invoked for quality assurance.

### Step 7: Complete Task

Once approved, mark as done:

```
manage_task("update", task_id="task-123", status="done")
```

Commit changes with reference to task:
```
git commit -m "feat(quest): implement quest state machine

Closes: task-123

Description of changes..."
```

---

## Archon Tools Reference

### Project Management

#### List All Projects

```
find_projects()
```

Returns all projects in Archon.

#### Search Projects

```
find_projects(query="RVNKQuests")
```

#### Get Specific Project

```
find_projects(project_id="proj-rvnkquests-001")
```

#### Create New Project

```
manage_project("create",
    title="RVNKQuests Archon Integration",
    description="Implement Archon MCP server integration for task management",
    github_repo="https://github.com/fourz/RVNKQuests.git"
)
```

#### Update Project

```
manage_project("update",
    project_id="proj-rvnkquests-001",
    description="Updated project description"
)
```

### Task Management

#### List All Tasks

```
find_tasks()
```

Returns all tasks with pagination (default: 10 per page).

#### Search Tasks

```
find_tasks(query="quest objective")
```

Full-text search across task titles and descriptions.

#### Get Specific Task

```
find_tasks(task_id="task-abc-123")
```

Returns full task details.

#### Filter by Status

```
find_tasks(filter_by="status", filter_value="todo")
```

**Possible values**: `todo`, `doing`, `review`, `done`

#### Filter by Project

```
find_tasks(filter_by="project", filter_value="proj-rvnkquests-001")
```

#### Filter by Assignee

```
find_tasks(filter_by="assignee", filter_value="User")
```

#### Create New Task

```
manage_task("create",
    project_id="proj-rvnkquests-001",
    title="Implement quest state validation",
    description="Add validation for quest state transitions to prevent invalid states",
    status="todo",
    assignee="User",
    task_order=75  # Priority: 0-100, higher = more important
)
```

#### Update Task

```
manage_task("update",
    task_id="task-abc-123",
    status="doing",
    assignee="User"
)
```

#### Delete Task

```
manage_task("delete", task_id="task-abc-123")
```

### Knowledge Base

#### List Available Sources

```
rag_get_available_sources()
```

Returns list of documentation sources with:
- Source ID (use this for filtering, not URL)
- Title
- URL
- Word count

**Format**:
```json
{
  "success": true,
  "sources": [
    {
      "id": "src_rvnkquests_quest",
      "title": "RVNKQuests Quest Framework",
      "url": "docs/quest-framework.md",
      "word_count": 5000
    },
    ...
  ]
}
```

#### Search Knowledge Base

```
rag_search_knowledge_base(
    query="quest state transitions",
    source_id="src_rvnkquests_quest",  # Optional: filter by source
    match_count=5,  # Default: 5
    return_mode="pages"  # Options: "pages" (default), "chunks"
)
```

**Parameters**:
- `query` (required): 2-5 keyword search string
- `source_id` (optional): Filter to specific source (use ID from rag_get_available_sources)
- `match_count` (optional, default 5): Number of results to return
- `return_mode` (optional, default "pages"): "pages" for full pages, "chunks" for raw text

**Returns**:
```json
{
  "success": true,
  "results": [
    {
      "page_id": "uuid",
      "url": "docs/quest-framework.md",
      "title": "Quest State Machine",
      "preview": "The quest state machine manages...",
      "word_count": 2000,
      "chunk_matches": 3
    },
    ...
  ],
  "return_mode": "pages",
  "reranked": true
}
```

#### Search Code Examples

```
rag_search_code_examples(
    query="CompletableFuture async operations",
    source_id="src_rvnkquests_code",
    match_count=3
)
```

Returns code snippets with explanations.

#### Read Full Page

After searching, get complete page content:

```
rag_read_full_page(page_id="550e8400-e29b-41d4-a716-446655440000")
```

or

```
rag_read_full_page(url="docs/quest-framework.md")
```

**Returns**: Full page content, title, URL, metadata

---

## RVNKQuests-Specific Knowledge

### Project Structure in Archon

**Project**: RVNKQuests Development
**Status**: Active
**Repository**: https://github.com/fourz/RVNKQuests.git

**Key Task Categories**:

1. **Archon Integration** (Current Phase)
   - Setup documentation
   - Knowledge base population
   - Task board configuration

2. **Core Refactoring**
   - Command system modernization
   - Quest state machine improvements
   - Event listener optimization

3. **RVNKCore Migration** (Future)
   - Service extraction from RVNKTools
   - Database abstraction layer
   - Dependency injection framework

4. **Test Coverage Expansion**
   - Unit test suite expansion
   - Integration test framework
   - Mock Bukkit API for offline testing

### Task Priority Guidelines

**High (90-100)**: Blocking other work
- Critical bugs
- Core feature implementation
- Security issues
- Documentation for current phase

**Medium-High (70-89)**: Important for current phase
- Feature enhancements
- Code refactoring
- Test coverage
- Documentation updates

**Medium (50-69)**: Nice to have for current iteration
- Performance optimization
- Code cleanup
- Minor enhancements
- Future planning

**Low (0-49)**: Backlog items
- Enhancement requests
- Tech debt
- Nice-to-have features
- Future initiatives

---

## Quick Reference: Common Task Workflows

### Workflow 1: Start Daily Development Session

```
1. find_tasks(filter_by="status", filter_value="todo")
2. Select a task based on priority
3. find_tasks(task_id="selected-task-id")  # Get full details
4. manage_task("update", task_id="...", status="doing")
5. Begin implementation (see Steps 3-4 in main workflow)
```

### Workflow 2: Research Before Coding

```
1. rag_get_available_sources()
2. rag_search_knowledge_base(query="my-topic", source_id="src_...")
3. rag_read_full_page(page_id="...")
4. Implement based on findings
```

### Workflow 3: Create New Task for Discovered Work

```
1. manage_task("create",
    project_id="proj-rvnkquests",
    title="Fix quest trigger edge case",
    description="Players can trigger quests while quest is active",
    status="todo",
    task_order=75
)
2. Continue work or assign to team member
```

### Workflow 4: Track Documentation Changes

After updating README.md, ROADMAP.md, or instruction files:

```
1. git commit -m "docs: update ROADMAP with new features"
2. find_tasks(query="documentation")  # Check if related task exists
3. manage_task("update", task_id="...", status="review")  # Mark as done if exists
```

---

## Integration with Development Patterns

### Using Archon with Claude Agents

When specialized work is needed:

1. **Code Review Required**: Use code-reviewer agent
   ```
   # In your commit, reference task and ask for review
   Archon task: task-123
   Please review for code quality and pattern compliance
   ```

2. **Complex Architecture**: Use java-architect agent
   ```
   find_tasks(filter_by="status", filter_value="todo")
   # Find architectural planning tasks
   ```

3. **Testing Needed**: Use test-engineer agent
   ```
   # Create task for test coverage
   manage_task("create", title="Add tests for quest state machine", ...)
   ```

### Using Archon with Instruction Files

Load relevant supplemental modules based on task:

- **Quest Framework Task** → Load `.github/supplemental/copilot-instructions.quest.md`
- **Objective Implementation** → Load `.github/supplemental/copilot-instructions.objective.md`
- **Reward System** → Load `.github/supplemental/copilot-instructions.reward.md`
- **Trigger Implementation** → Load `.github/supplemental/copilot-instructions.trigger.md`
- **Event Handling** → Load `.github/supplemental/copilot-instructions.events.md`

### Using Archon with Git Workflow

**Commit Pattern**:
```
type(scope): description

- Change detail 1
- Change detail 2

Closes: task-123

🤖 Generated with Claude Code

Co-Authored-By: Derek Schrishuhn <derek@ravenkraft.dev>
```

**Reference Task in Branch Name**:
```bash
git checkout -b feat/task-123-quest-state-validation
```

---

## Best Practices for Archon Workflow

### DO's ✅

- **Check Archon first** before starting any work
- **Update task status** as you progress through workflow
- **Use RAG search** before writing code
- **Keep queries focused** (2-5 keywords)
- **Document decisions** in task comments
- **Mark tasks complete** when truly done
- **Search code examples** for patterns
- **Cross-reference** related tasks

### DON'Ts ❌

- **Don't skip task checking** - always start with Archon
- **Don't write long, unfocused queries** - search will be less accurate
- **Don't forget to update task status** - keeps team aware
- **Don't mark complete without testing** - code must be validated
- **Don't ignore RAG results** - they contain project wisdom
- **Don't hardcode without pattern research** - understand the pattern first

---

## Troubleshooting Archon Workflow

### Issue: Task Not Found

**Problem**: `find_tasks(task_id="...")` returns empty

**Solution**:
1. Double-check task ID spelling
2. Try searching by keyword: `find_tasks(query="...")`
3. List all tasks: `find_tasks()` and find in results

### Issue: RAG Search Returns No Results

**Problem**: Knowledge base search finds nothing relevant

**Possible causes**:
1. Query too long or unfocused (use 2-5 keywords)
2. Documentation not in knowledge base yet
3. Wrong source_id specified

**Solution**:
1. Shorten query: `"quest state"` instead of `"quest state transitions in RVNKQuests"`
2. Try without source_id to search all sources
3. Use `rag_get_available_sources()` to verify source exists

### Issue: Source ID Not Working

**Problem**: `rag_search_knowledge_base(source_id="...")` fails

**Solution**:
1. Always use ID from `rag_get_available_sources()`, not URL
2. IDs are like "src_abc123", not domain names
3. Verify source exists before using

---

## Documentation & Knowledge Base Integration

### Current Knowledge Base Status

**To Add**: Document what RVNKQuests sources exist or should be added

Populate with:
- Quest framework documentation
- Objective patterns
- Event listener examples
- Reward system details
- Trigger mechanisms
- Migration guides
- Testing strategies

### Adding Documentation to Archon

When writing new documentation:

1. **Create guide in `docs/` directory**
2. **Format in Markdown**
3. **Reference in README.md or ROADMAP.md**
4. **Notify Archon to index** via separate process

---

## Integration Checklist

- [ ] Archon project created for RVNKQuests
- [ ] Task board populated with initial tasks
- [ ] Knowledge base sources configured
- [ ] Code-reviewer agent linked
- [ ] Development workflow documented
- [ ] Team trained on Archon workflow
- [ ] Git commits reference Archon tasks
- [ ] ROADMAP synced with Archon tasks
- [ ] Regular task status reviews scheduled

---

## Next Steps

1. **Initialize Archon Project**: Create RVNKQuests project in Archon
2. **Populate Task Board**: Add initial tasks from IMPLEMENTATION_PLAN.md
3. **Configure Knowledge Base**: Set up documentation sources
4. **Start Task-Driven Development**: Begin workflow with todo tasks
5. **Track Progress**: Monitor task completion and Archon metrics

---

**Last Updated**: November 1, 2025
**Version**: 1.0
**Reference**: RvnkDev MCP Server - Archon Integration Guide
**Adapted For**: RVNKQuests Project

See **[CLAUDE.md](../../CLAUDE.md)** for quick reference and overview.
