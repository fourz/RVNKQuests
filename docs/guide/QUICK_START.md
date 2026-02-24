# Quick Start: 5-Minute Setup

**For**: New developers or anyone in a hurry
**Time**: 5 minutes
**Goal**: Understand the Archon workflow at a glance

---

## The 30-Second Version

1. **Get task from Archon** → Update status to DOING
2. **Search knowledge base** → Find patterns for your task
3. **Write/document/analyze** → Follow patterns you found
4. **Submit for review** → Update status to REVIEW
5. **Complete task** → Update status to DONE

**Done!** That's the entire workflow.

---

## The 5-Minute Version

### Minute 1: Open Archon

```
Claude Code → Archon Project
Project ID: 50448cbf-5f7e-4904-9158-09b759e16500
Click: Task Board
```

### Minute 2: Find & Claim Task

1. **Look** for a task with status `TODO`
2. **Read** the title and description
3. **Click** task to open it
4. **Update**: Status `TODO` → `DOING`
5. **Save**

**Tip**: Look for your role
- Java developer? → `plan-`, `feat-`, `test-`
- Documentation? → `doc-`
- Testing? → `test-`
- Database? → `data-`

### Minute 3: Research Patterns

**Search knowledge base** using Archon RAG:

```
rag_search_knowledge_base(query="quest objective patterns")
```

**Tips**:
- Use 2-5 keywords only
- Search for concepts, not full questions
- Try: "state machine", "listener registration", "async implementation"

### Minute 4: Implement

**For code**:
1. Create branch: `git checkout -b task/YOUR-TASK-NAME`
2. Write code (follow patterns you found)
3. Commit frequently
4. Create pull request

**For documentation**:
1. Create markdown file
2. Write comprehensive doc
3. Commit with clear message

**For analysis**:
1. Explore codebase
2. Document findings
3. Create analysis report

### Minute 5: Submit

1. **Update task**: Status `DOING` → `REVIEW`
2. **Add comment**: "Ready for review - see PR #X" (or document location)
3. **Get feedback** (someone will review)
4. **Update**: Status `REVIEW` → `DONE` when approved

---

## The Key Rules

| Rule | Why |
|------|-----|
| Always use Archon for tasks | Single source of truth |
| Always search knowledge base | Consistency & standards |
| Always commit frequently | History and safety |
| Always update task status | Keep team informed |
| Always follow task requirements | Success criteria matter |

---

## Common Tasks (Templates)

### 🔨 Code Implementation

```
1. Get task from Archon
2. Search: [concept] patterns
3. Review found patterns
4. Create feature branch
5. Implement following patterns
6. Commit frequently
7. Create PR with task ID
8. Request review
9. Update task status
```

### 📖 Documentation

```
1. Get task from Archon
2. Search: [topic] documentation
3. Review related docs
4. Create markdown file
5. Write comprehensive doc
6. Add examples/diagrams
7. Commit to git
8. Update task status
```

### 🔍 Analysis/Review

```
1. Get task from Archon
2. Explore codebase
3. Document findings
4. Identify patterns
5. Create analysis report
6. Add recommendations
7. Commit report
8. Update task status
```

---

## Quick Answers

**Q: I don't know where to start?**
A: Read your task description in Archon. It tells you what to do.

**Q: I don't know how to do it?**
A: Search knowledge base for patterns using RAG search.

**Q: What patterns should I follow?**
A: Whatever knowledge base returns for your concept search.

**Q: When is it due?**
A: Check task creation date and priority - ask team if unclear.

**Q: Who reviews my work?**
A: Whoever task is assigned to review (usually code-reviewer or lead).

**Q: I'm stuck!**
A: 1) Search more, 2) Ask in team chat, 3) Request task clarification

---

## Essential Links

| Name | Purpose |
|------|---------|
| AGENT_ROLE_DESCRIPTIONS.md | Who to ask for what |
| CLAUDE.md | Central reference |
| copilot-instructions.archon.md | Detailed Archon guide |

---

## The Loop (Repeat Forever)

```
┌─────────────────────────────────────┐
│ 1. Get Task (Archon)                │
├─────────────────────────────────────┤
│ 2. Search Knowledge Base (RAG)      │
├─────────────────────────────────────┤
│ 3. Implement/Document/Analyze       │
├─────────────────────────────────────┤
│ 4. Commit to Git                    │
├─────────────────────────────────────┤
│ 5. Submit for Review (Update Task)  │
├─────────────────────────────────────┤
│ 6. Get Feedback & Update             │
├─────────────────────────────────────┤
│ 7. Mark Done (Update Task)          │
├─────────────────────────────────────┤
│ ↻ Repeat (Go to Step 1)             │
└─────────────────────────────────────┘
```

---

## You're Ready! 🚀

Start with a TODO task. You've got this.

Need more detail? → Read **CLAUDE.md**

Need to know about agents? → Read **AGENT_ROLE_DESCRIPTIONS.md**

---

**Training Material**: doc-03 Team Training Materials
**Created**: November 2, 2025
**Time to Read**: 5 minutes
