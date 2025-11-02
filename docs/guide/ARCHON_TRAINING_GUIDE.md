# Archon Training Guide - New Developer Onboarding

**Purpose**: Quick onboarding guide for new RVNKQuests developers joining the Archon-driven workflow

**Time to Read**: 10-15 minutes

**Prerequisite**: None - start here if you're new!

---

## 🎯 What is Archon? (2 minutes)

Archon is our **task management and knowledge base system** that makes development organized and efficient.

Think of it as:
- 📋 **Task Board** - All development work is a task
- 🔍 **Knowledge Base** - Team documentation searchable and retrievable
- 🤖 **Agent Coordinator** - Specialized agents help with different types of work

**Key Rule**: ✅ **Always use Archon for task management. No exceptions.**

---

## 🚀 Your First 5 Minutes: Get a Task

### Step 1: Open Archon

```
Access: Claude Code → Archon Project → Task Board
Project ID: 50448cbf-5f7e-4904-9158-09b759e16500
```

### Step 2: Find a TODO Task

**Your Mission**: Find a task with status **TODO**

**Look for your role**:
- Are you a **Java developer**? → Look for `plan-`, `feat-`, or `test-` tasks
- Are you a **documentation expert**? → Look for `doc-` tasks
- Are you a **tester/QA**? → Look for `test-` tasks
- Are you a **database/infrastructure**? → Look for `data-` tasks

### Step 3: Claim Your Task

**Update the task**:
- Change status from `todo` → `doing`
- Add yourself as assignee
- Read the description completely

**Example**:
```
Task: plan-04 Command System Design
Status: TODO → DOING
Assignee: YOU
```

✅ **You've claimed your first task!**

---

## 🔍 Your First 15 Minutes: Understand Requirements

### Step 1: Read Task Description

The task description tells you:
- 📝 What to build/document/analyze
- ✅ Success criteria (how to know you're done)
- 🎯 Who this impacts

**Example**:
```
"Document quest system architecture, objective patterns,
reward systems, and event system. Populate Archon knowledge
base with comprehensive documentation and validate RAG
search effectiveness for team research."
```

### Step 2: Break It Down

Ask yourself:
1. **What** needs to be done? (deliverable)
2. **Why** are we doing this? (purpose)
3. **How** should it be done? (approach)
4. **When** is it due? (timeline)
5. **Who** needs this? (audience)

### Step 3: Check for Blockers

**Ask**:
- Do I have all the information?
- Do I need to see existing code/documentation?
- Is there a previous task this depends on?

**If blocked**: Comment on task or ask in team chat

✅ **You understand your task!**

---

## 📚 Your First 30 Minutes: Research Using RAG

RAG = **Retrieval-Augmented Generation** = Search our knowledge base before coding

### Step 1: Identify What You Need to Learn

For quest-related work, you might search:
- "quest lifecycle patterns"
- "state machine implementation"
- "trigger listener architecture"
- "objective event handling"

### Step 2: Search Archon Knowledge Base

Use the RAG search in Claude Code:
```
rag_search_knowledge_base(
  query="quest objective patterns",
  match_count=5
)
```

**Tips**:
- Use 2-5 keywords only (not full sentences!)
- Search for concepts, not questions
- Try multiple searches to find patterns

### Step 3: Review Search Results

Each result shows:
- 📄 Document name and location
- 🔍 Relevant section
- 📝 How it relates to your task

**Example Results**:
```
Found in QUEST_SYSTEM_ARCHITECTURE_ANALYSIS.md:
"Objectives are event-driven listeners that track
quest progression. They register for specific events
and update quest state when conditions met."
```

### Step 4: Review Code Examples

If you need code patterns:
```
rag_search_code_examples(
  query="CompletableFuture async implementation",
  match_count=3
)
```

✅ **You've researched best practices!**

---

## ✍️ Your First 60 Minutes: Implement & Submit

### Step 1: Implementation Pattern

**For Code Tasks**:
1. Create a feature branch: `git checkout -b task/plan-04-command-system`
2. Write code following the patterns you found
3. Test locally
4. Commit with clear message

**For Documentation Tasks**:
1. Create document in project root or docs/
2. Follow documentation style guide
3. Cross-reference related documents
4. Validate with team review

**For Analysis Tasks**:
1. Explore codebase using code-archaeologist agent
2. Document findings
3. Create comprehensive analysis report
4. Prepare recommendations

### Step 2: Update Task Status

When you're ready for review:
```
Change status: doing → review
Add comment: "Ready for review - see PR #123"
```

### Step 3: Submit Work

**Code**: Create a pull request with task ID in title
```
Example: "plan-04: Fluent API command system design"
```

**Documentation**: File created and committed
```
Example: Commit message references task
```

**Analysis**: Report document created and linked
```
Example: QUEST_SYSTEM_ARCHITECTURE_ANALYSIS.md
```

### Step 4: Get Feedback & Finalize

- Respond to code review comments
- Update documentation based on feedback
- Resubmit if needed

When approved:
```
Change status: review → done
```

✅ **Your task is complete!**

---

## 📋 Daily Workflow Checklist

**Start of Day**:
- [ ] Check Archon task board
- [ ] Find your assigned task(s)
- [ ] Update task status to DOING if you haven't yet

**During Work**:
- [ ] Read task description completely
- [ ] Search Archon knowledge base for patterns
- [ ] Follow documented patterns in code
- [ ] Commit frequently with clear messages
- [ ] Update task comments with progress

**Before Submitting**:
- [ ] Does work match task requirements?
- [ ] Did you follow documented patterns?
- [ ] Is documentation updated?
- [ ] Are there any known limitations?

**When Done**:
- [ ] Update task status to REVIEW
- [ ] Create PR/document with clear summary
- [ ] Request review from appropriate team member
- [ ] Respond to feedback
- [ ] Mark task as DONE when approved

---

## ⚠️ Common Mistakes to Avoid

### ❌ Mistake 1: Starting Work Without a Task

**Wrong**: Just start coding whatever you think needs fixing
**Right**: Always get a task from Archon first

### ❌ Mistake 2: Coding Without Research

**Wrong**: Implement from scratch using your own patterns
**Right**: Search Archon knowledge base for patterns first

### ❌ Mistake 3: Ignoring Task Requirements

**Wrong**: Do "similar" work without matching requirements
**Right**: Meet ALL success criteria listed in task

### ❌ Mistake 4: Not Updating Task Status

**Wrong**: Finish work but leave task as TODO
**Right**: Update status: TODO → DOING → REVIEW → DONE

### ❌ Mistake 5: Searching Wrong Way

**Wrong**: "How do I implement a quest objective system?"
**Right**: "quest objective patterns" or "listener architecture"

### ❌ Mistake 6: Not Committing Code

**Wrong**: Keep working but never commit to git
**Right**: Commit frequently (at least end of day)

---

## 🆘 Help & Support

### I Can't Find a Task to Work On

1. Check ARCHON_PROJECT_STATUS_BRIEF.md for priorities
2. Look for HIGH priority tasks first
3. Ask in team chat for task recommendations

### My Search Isn't Returning Results

1. Try shorter, more specific search terms
2. Search for different related concepts
3. Check documentation manually if needed
4. Ask documentation-specialist agent for help

### I'm Stuck on Implementation

1. Search for similar patterns in knowledge base
2. Ask code-review agent for guidance
3. Review existing quest implementations
4. Check commented TODOs in code

### I Don't Understand a Task

1. Read task description again carefully
2. Check referenced documents
3. Ask in team chat for clarification
4. Request task reassignment if out of scope

---

## 🎓 Related Training Materials

After this guide, review these for deeper knowledge:

1. **QUICK_START.md** - 5 minute version of this guide
2. **copilot-instructions.archon.md** - Detailed Archon tools reference
3. **AGENT_ROLE_DESCRIPTIONS.md** - Who to ask for what
4. **CLAUDE.md** - Central reference hub

---

## 📞 Quick Reference

| Need | Action | Where |
|------|--------|-------|
| Find task | Go to Archon task board | Archon MCP Server |
| Learn pattern | Search knowledge base | RAG search (rag_search_knowledge_base) |
| Get unstuck | Ask in team chat | Team Discord/Slack |
| Report bug | Create task | Archon task board |
| Suggest improvement | Create task | Archon task board |
| Understand architecture | Read analysis | QUEST_SYSTEM_ARCHITECTURE_ANALYSIS.md |

---

## ✅ You're Ready!

You now understand:
- ✅ What Archon is and why we use it
- ✅ How to find and claim your first task
- ✅ How to research using RAG
- ✅ How to implement and submit work
- ✅ Common mistakes to avoid

**Next Steps**:
1. Open Archon project
2. Find a TODO task assigned to your role
3. Update status to DOING
4. Read task description
5. Search knowledge base for patterns
6. Start implementing!

**Welcome to the team!** 🚀

---

**Training Material**: doc-03 Team Training Materials
**Created**: November 2, 2025
**Audience**: New developers joining RVNKQuests
