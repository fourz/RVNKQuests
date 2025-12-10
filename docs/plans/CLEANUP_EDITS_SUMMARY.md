# Instruction Files Cleanup: Edit Summary

**Quick reference for applying cleanup recommendations**
**Priority**: CRITICAL (Priority 1) and HIGH (Priority 2)

---

## Critical Issues & Quick Fixes

### Issue 1: CLAUDE.md Over-Specifies Copilot Files (HIGH IMPACT)

**Location**: CLAUDE.md lines 346-355
**Impact**: Creates 7 direct paths to files, breaks when files reorganize
**Effort**: 5 minutes

**REMOVE** (14 lines):
```markdown
**For GitHub Copilot users:**
- Load relevant Copilot instruction files based on your task:
  - **Working on quest framework?** Load `.github/supplemental/copilot-instructions.quest.md`
  - **Working on objectives?** Load `.github/supplemental/copilot-instructions.objective.md`
  - **Working on rewards?** Load `.github/supplemental/copilot-instructions.reward.md`
  - **Working on triggers?** Load `.github/supplemental/copilot-instructions.trigger.md`
  - **Working on events?** Load `.github/supplemental/copilot-instructions.events.md`
  - **Working on lore integration?** Load `.github/supplemental/copilot-instructions.lore.md`
  - **Testing MCP Server or RVNKQuests integration?** Load `.github/supplemental/copilot-instructions.test-orchestrator.md`
- See [.github/copilot-instructions.md](.github/copilot-instructions.md) for complete navigation hub
```

**REPLACE WITH** (3 lines):
```markdown
**For GitHub Copilot users:**
See [.github/copilot-instructions.md] for the navigation hub and contextual module selection based on your task.
```

---

### Issue 2: CLAUDE.md Duplicates Copilot File List (HIGH IMPACT)

**Location**: CLAUDE.md lines 367-393
**Impact**: Duplication of .github/copilot-instructions.md lines 75-145
**Effort**: 5 minutes

**REMOVE** (27 lines):
```markdown
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
```

**REPLACE WITH** (2 lines):
```markdown
### GitHub Copilot Instructions

See [.github/copilot-instructions.md] for the complete navigation hub and instruction file directory.
```

---

### Issue 3: CLAUDE.md Over-Specifies Agent Details (CRITICAL)

**Location**: CLAUDE.md lines 405-413
**Impact**: Duplicates .claude/agents/README.md structure
**Effort**: 2 minutes

**REMOVE** (9 lines):
```markdown
### Claude Agents

For specialized development tasks, refer to the agent directory:

**[.claude/agents/README.md](.claude/agents/README.md)** provides:
- Complete directory of 28+ agents organized by category
- Quick Selection Guide (by task type and language)
- Detailed Agent Selection Guidelines for RVNKQuests work
- GitHub Copilot instruction file mappings
```

**REPLACE WITH** (2 lines):
```markdown
### Claude Agents

See [.claude/agents/README.md] for agent selection by task and language.
```

---

### Issue 4: CLAUDE.md Duplicates Test Infrastructure Details (HIGH)

**Location**: CLAUDE.md lines 415-434
**Impact**: Duplicates .claude/agents/test-orchestrator-enhanced.md and Copilot instructions
**Effort**: 3 minutes

**REMOVE** (20 lines):
```markdown
### Test Agent & Commands

**Testing RvnkDev MCP Server and RVNKQuests Integration:**

**Claude Code Users:**
- **Agent**: `.claude/agents/test-orchestrator-enhanced.md` - Comprehensive testing orchestration
- **Commands**:
  - `/test-run-suite` - Execute test suites with options
  - `/test-analyze-results` - Analyze and compare test results

**GitHub Copilot Users:**
- **Instructions**: `.github/supplemental/copilot-instructions.test-orchestrator.md`
- **Ask Copilot**: "Run comprehensive test suite for RvnkDev with regression analysis"

**Test Infrastructure:**
- Location: `metamake/projects/10-test-suite-tracking/`
- Test Suites: Core Tools (21), Provider Integration (6), Security (4)
- Report Format: JSON and Markdown with historical tracking
- Environments: rvnkdev_local, rvnkquests_integration, alternative installations
```

**REPLACE WITH** (5 lines):
```markdown
### Testing

**Claude Code**: Use test-orchestrator-enhanced agent (`.claude/agents/test-orchestrator-enhanced.md`)
**GitHub Copilot**: See [.github/supplemental/copilot-instructions.test-orchestrator.md]
**Slash Commands**: `/test-run-suite` and `/test-analyze-results` (see `.claude/commands/`)
```

---

### Issue 5: CLAUDE.md Duplicates Archon Workflow (CRITICAL)

**Location**: CLAUDE.md lines 22-73
**Impact**: Nearly exact copy of .github/copilot-instructions.archon.md workflow
**Effort**: 10 minutes

**REMOVE** (52 lines) - Entire "## Archon Integration & Workflow" section including:
- Archon features list
- Core Workflow (7-step process)
- RAG Workflow (3-step process)
- Task Status Flow
- Archon Quick Reference (with all tool enumerations)

**REPLACE WITH** (8 lines):
```markdown
## Archon Integration

**This project uses Archon MCP server for task management, knowledge base access, and cross-agent collaboration.**

**Quick Start**:
1. Check tasks: `find_tasks(filter_by="status", filter_value="todo")`
2. Start work: `manage_task("update", task_id="...", status="doing")`
3. Research: `rag_search_knowledge_base(query="...")`
4. Complete: `manage_task("update", task_id="...", status="done")`

**Complete workflow documentation**: See [.github/copilot-instructions.archon.md]
```

---

## .claude/agents/README.md Cleanup

### Issue 6: Agents README Over-Specifies Copilot Mappings

**Location**: .claude/agents/README.md lines 81-91
**Impact**: Creates coupling between agent system and Copilot file structure
**Effort**: 5 minutes

**REMOVE** (11 lines):
```markdown
### RvnkDev MCP Server Project Mappings

**Project-specific agents** (marked with ✨):
- `fastmcp-developer.md` → `.github/copilot-instructions.fastmcp.md`
- `test-engineer.md` → `.github/supplemental/copilot-instructions.tests.md`
- `test-orchestrator.md` → `.github/supplemental/copilot-instructions.test-orchestrator.md`
- `project-architect.md` → `.github/supplemental/copilot-instructions.metamake.md`

**General development agents**:
- `python-developer.md` → `.github/copilot-instructions.best-practices.md` + `.github/copilot-instructions.patterns.md`
- [... more mappings ...]
```

**REPLACE WITH** (3 lines):
```markdown
### For GitHub Copilot Users

GitHub Copilot does not support Claude's native agent format. See [.github/copilot-instructions.md] for equivalent instruction files.
```

---

### Issue 7: Agents README Over-References Copilot Instructions

**Location**: .claude/agents/README.md lines 13-14
**Impact**: Redundant references
**Effort**: 2 minutes

**CHANGE FROM**:
```markdown
See **[CLAUDE.md](../../CLAUDE.md)** and **[.github/copilot-instructions.archon.md](../../.github/copilot-instructions.archon.md)** for complete guidance.
```

**CHANGE TO**:
```markdown
See [CLAUDE.md](../../CLAUDE.md) for project context and Archon integration overview.
```

---

## copilot-instructions.test-orchestrator.md Cleanup

### Issue 8: Over-Documentation of Command Parameters

**Location**: Lines 110-173
**Impact**: Duplicates command definition files in `.claude/commands/`
**Effort**: 15 minutes

**REMOVE** (64 lines) - Entire "## Command Integration" section with:
- `/test-run-suite` parameter documentation
- `/test-analyze-results` parameter documentation
- All examples and options

**REPLACE WITH** (8 lines):
```markdown
## Claude Code Commands

For Claude Code users with slash command support:
- `/test-run-suite` - Execute test suites (see `.claude/commands/test-run-suite.md`)
- `/test-analyze-results` - Analyze results (see `.claude/commands/test-analyze-results.md`)

Copilot users: Ask "How do I run tests?" for guidance.
```

---

### Issue 9: Over-Documentation of Agent References

**Location**: Lines 499-584 (Agent & Command References section)
**Impact**: Duplicates what's in agent file headers
**Effort**: 10 minutes

**REMOVE** (86 lines) - Most of "## Agent & Command References" and "## Related Resources" duplication

**REPLACE WITH** (12 lines):
```markdown
## Resources

### Agents & Commands
- **Claude Code Agent**: test-orchestrator-enhanced (`.claude/agents/test-orchestrator-enhanced.md`)
- **Slash Commands**: `/test-run-suite`, `/test-analyze-results` (see `.claude/commands/`)

### Documentation
- Deployment Guide: `shared/derek/repos/rvnkdev-mcp-server/docs/RVNKQUESTS_DEPLOYMENT_GUIDE.md`
- Test Framework: `metamake/projects/10-test-suite-tracking/COPILOT-INSTRUCTIONS.md`
- Project 10: `shared/derek/repos/rvnkdev-mcp-server/rvnkdev-fastmcp-server/docs/milestones/`
```

---

## Files to KEEP AS-IS

✅ **.github/copilot-instructions.archon.md** (667 lines)
- Canonical source for Archon workflow
- Other files reference, don't duplicate
- No changes needed

✅ **.github/copilot-instructions.md** (164 lines)
- Navigation hub for Copilot users
- Already optimized
- No changes needed

✅ **.claude/agents/README.md** (after Issue 6 & 7)
- Master directory of agents
- Already good structure
- Only remove Copilot mappings

---

## Implementation Checklist

### Phase 1: CLAUDE.md (35 minutes total)
- [ ] Apply Issue 1 edit (5 min)
- [ ] Apply Issue 2 edit (5 min)
- [ ] Apply Issue 3 edit (2 min)
- [ ] Apply Issue 4 edit (3 min)
- [ ] Apply Issue 5 edit (10 min)
- [ ] Test all links still work (5 min)
- [ ] Verify file reads cleanly

### Phase 2: .claude/agents/README.md (10 minutes)
- [ ] Apply Issue 6 edit (5 min)
- [ ] Apply Issue 7 edit (2 min)
- [ ] Test all links work (3 min)

### Phase 3: copilot-instructions.test-orchestrator.md (25 minutes)
- [ ] Apply Issue 8 edit (15 min)
- [ ] Apply Issue 9 edit (10 min)
- [ ] Test all links work (5 min)

### Validation (15 minutes)
- [ ] Check for broken links in all 6 core instruction files
- [ ] Verify navigation hub is discoverable from CLAUDE.md
- [ ] Test agent selection from agents/README.md
- [ ] Verify test orchestrator references are correct
- [ ] Read through CLAUDE.md for flow and clarity

---

## Expected Results

### Metrics
- **CLAUDE.md**: 592 → 470 lines (20% reduction)
- **.claude/agents/README.md**: 231 → 215 lines (7% reduction)
- **copilot-instructions.test-orchestrator.md**: 642 → 558 lines (13% reduction)
- **Total**: ~200-250 lines consolidated
- **Redundancy**: 40% → <15%

### User Experience Improvements
✅ Simpler navigation (references to hubs, not specific files)
✅ Clearer "single source of truth" principle
✅ Easier to onboard (less to read in CLAUDE.md)
✅ Reduced maintenance burden (update one file, not three)

### Maintenance Improvements
✅ When Archon workflow changes: Update archon.md only (1 file vs 2)
✅ When Copilot files reorganize: Update copilot-instructions.md only (1 file vs 3)
✅ When test infrastructure changes: Update test-orchestrator.md only (1 file vs 3)

---

## Notes

- **Do NOT delete files** - Only edit and simplify content
- **Do NOT break links** - All references to external files should remain valid
- **Do NOT remove critical rules** - ARCHON-FIRST rule must stay in CLAUDE.md
- **Test after each edit** - Verify links work and navigation makes sense
- **Commit separately** - Each file change should be its own commit

---

**Total Implementation Time**: ~75 minutes (1.25 hours)
**Impact**: High (eliminates major maintenance burden)
**Risk Level**: Low (only removing duplication, no functionality changes)

Ready to apply? Start with Phase 1: CLAUDE.md edits.
