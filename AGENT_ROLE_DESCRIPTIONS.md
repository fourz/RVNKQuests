# Claude Agent Roles & How to Work With Them

**Purpose**: Guide for working with specialized Claude agents assigned to RVNKQuests development

**Audience**: Team members requesting agent assistance

---

## 🤖 What Are Agents?

Specialized Claude instances configured with:
- **Expertise**: Deep knowledge in specific domains
- **Tools**: Access to project-specific tools and resources
- **Context**: Understanding of RVNKQuests architecture and patterns
- **Authority**: Ability to execute assigned work autonomously

**Key Principle**: Agents work best when given clear tasks. They handle research, implementation, testing, and documentation autonomously.

---

## 👥 The 6 Assigned Agents

### 1. 📖 **documentation-specialist**

**Expertise**: Creating comprehensive documentation, training materials, knowledge base content

**Handles**:
- ✅ Architecture documentation
- ✅ API documentation
- ✅ Team training materials
- ✅ User guides and tutorials
- ✅ Knowledge base population
- ✅ README and ROADMAP updates

**How to Work With Them**:
```
Task Type: "Create comprehensive documentation on..."
Example: "doc-02 Architecture Documentation"

Provide:
- What topics to document
- Who the audience is
- What format you prefer
- Any specific patterns to highlight

Expected Output:
- Professional markdown documents
- Well-organized sections
- Examples and diagrams where helpful
- Cross-references to related docs
```

**Best For**: Documentation-related tasks (doc-##)

**Current Assignments**:
- doc-02: Architecture Documentation
- doc-03: Team Training Materials

---

### 2. ☕ **java-architect**

**Expertise**: Java design, architecture patterns, refactoring strategies, performance optimization

**Handles**:
- ✅ Architecture planning and design
- ✅ Refactoring strategies
- ✅ Design pattern implementation
- ✅ API design and contracts
- ✅ Code modernization
- ✅ Spring/enterprise patterns

**How to Work With Them**:
```
Task Type: "Design/plan solution for..."
Example: "plan-04 Command System Design"

Provide:
- Current implementation overview
- Problems to solve
- Constraints and requirements
- Preferred technologies/patterns

Expected Output:
- Architecture design document
- Implementation roadmap
- Code structure recommendations
- Migration strategy (if refactoring)
```

**Best For**: Planning and architecture tasks (plan-##, feat-##)

**Current Assignments**:
- plan-04: Command System Design
- plan-07: RVNKCore Integration Plan
- feat-12: Reward System Modernization

---

### 3. 🔍 **code-archaeologist**

**Expertise**: Codebase analysis, legacy code understanding, pattern discovery, technical debt assessment

**Handles**:
- ✅ Codebase exploration and documentation
- ✅ Architecture analysis
- ✅ Code quality assessment
- ✅ Technical debt inventory
- ✅ Pattern identification
- ✅ Risk and impact analysis

**How to Work With Them**:
```
Task Type: "Analyze/audit..."
Example: "arch-06 Quest Architecture Analysis"

Provide:
- Scope of analysis
- Specific questions to answer
- Areas of concern
- Context about system

Expected Output:
- Comprehensive analysis report
- Architecture diagrams
- Quality metrics
- Recommendations with effort estimates
- Risk assessment
```

**Best For**: Architecture analysis and codebase assessment (arch-##)

**Current Assignments**:
- arch-06: Quest Architecture Analysis (COMPLETED)

---

### 4. ✅ **code-reviewer**

**Expertise**: Code review, testing strategy, quality assurance, refactoring validation

**Handles**:
- ✅ Code review and quality assessment
- ✅ Test coverage analysis
- ✅ Performance testing
- ✅ Refactoring validation
- ✅ Pull request review
- ✅ Quality metrics

**How to Work With Them**:
```
Task Type: "Review/test/validate..."
Example: "test-05 Test Foundation Setup"

Provide:
- Code or design to review
- Specific quality criteria
- Testing requirements
- Performance targets

Expected Output:
- Detailed review findings
- Quality metrics
- Recommendations
- Test plans and results
```

**Best For**: Testing, review, and quality tasks (test-##, data-##, feat-##)

**Current Assignments**:
- test-05: Test Foundation Setup
- data-10: Quest Persistence Schema
- feat-11: Objective System Enhancement

---

### 5. 🧪 **test-engineer**

**Expertise**: Testing frameworks, test design, edge cases, test automation, reliability

**Handles**:
- ✅ Test strategy and design
- ✅ Edge case testing
- ✅ Test automation setup
- ✅ Reliability and resilience testing
- ✅ Test coverage planning
- ✅ CI/CD pipeline testing

**How to Work With Them**:
```
Task Type: "Create tests for.../Test strategy for..."
Example: "test-08 State Machine Hardening"

Provide:
- Code/system to test
- Edge cases to cover
- Performance targets
- Reliability requirements

Expected Output:
- Test implementation
- Test coverage metrics
- Edge case documentation
- Reliability validation results
```

**Best For**: Testing and hardening tasks (test-##)

**Current Assignments**:
- test-08: State Machine Hardening
- test-09: Performance Optimization

---

### 6. 🏗️ **build-engineer**

**Expertise**: Build systems, performance optimization, infrastructure, dependency management

**Handles**:
- ✅ Build system optimization
- ✅ Performance profiling
- ✅ Dependency management
- ✅ Maven/Gradle optimization
- ✅ Build pipeline setup
- ✅ Performance benchmarking

**How to Work With Them**:
```
Task Type: "Optimize/improve performance of..."
Example: "test-09 Performance Optimization"

Provide:
- Code to optimize
- Performance targets
- Constraints (memory, CPU, etc)
- Measurement baselines

Expected Output:
- Optimized code
- Performance improvements measured
- Build configuration updates
- Benchmarking results
```

**Best For**: Performance and build tasks (test-##)

**Current Assignments**:
- test-09: Performance Optimization

---

## 🎯 Quick Decision Tree: Who to Ask?

```
Need to...
│
├─ Document something?
│  └─ → documentation-specialist
│
├─ Plan an architecture/design?
│  └─ → java-architect
│
├─ Analyze existing code?
│  └─ → code-archaeologist
│
├─ Review code/test quality?
│  └─ → code-reviewer
│
├─ Test edge cases/hardening?
│  └─ → test-engineer
│
└─ Optimize performance/build?
   └─ → build-engineer
```

---

## 📋 How to Request Agent Work

### Step 1: Create a Task

In Archon, create a task with:
- **Title**: What needs to be done
- **Description**: Clear requirements and context
- **Assignee**: The appropriate agent
- **Priority**: High/Medium/Low based on urgency

### Step 2: Provide Context

Agents work best with context. Provide:
- ✅ Current state/situation
- ✅ Goals and success criteria
- ✅ Constraints and requirements
- ✅ Related documentation
- ✅ Any known issues or patterns

### Step 3: Let Them Work

Agents are autonomous. They will:
1. **Research** using Archon knowledge base
2. **Analyze** the problem
3. **Implement** a solution
4. **Test** their work
5. **Document** results
6. **Update** task when complete

### Step 4: Review Deliverables

When agent marks task as REVIEW or DONE:
1. Check quality of work
2. Provide feedback if needed
3. Approve or request changes
4. Mark task DONE when satisfied

---

## 💬 Communication Patterns

### Best Practices

✅ **Be Specific**
```
Good: "Analyze quest system architecture and identify
technical debt, focusing on persistence and multi-player support"

Bad: "Analyze the code"
```

✅ **Provide Context**
```
Good: "We're preparing for RVNKCore migration in 7-10 weeks.
Current blocking issues are X, Y, Z. Priority: production readiness."

Bad: "Just analyze it"
```

✅ **Set Clear Success Criteria**
```
Good: "Deliverable: Comprehensive analysis document (40+ KB)
showing health score, critical blockers, and 10 recommendations."

Bad: "Analyze the system"
```

### Task Description Template

```markdown
## Objective
[Clear statement of what needs to be done]

## Requirements
- [Specific requirement 1]
- [Specific requirement 2]
- [Specific requirement 3]

## Success Criteria
- ✅ [Measurable success criterion 1]
- ✅ [Measurable success criterion 2]
- ✅ [Measurable success criterion 3]

## Context
[Background, related work, constraints]

## Expected Deliverable
[What format, how much detail, where to save]
```

---

## 🚀 Agent Workflow (What They Do)

Each agent follows this autonomous workflow:

```
┌─────────────────────┐
│ Get Task            │ ← Assigned to agent
├─────────────────────┤
│ Research            │ ← Use RAG knowledge base
├─────────────────────┤
│ Plan Approach       │ ← Design solution
├─────────────────────┤
│ Implement/Execute   │ ← Write code/docs/analysis
├─────────────────────┤
│ Test/Validate       │ ← Verify results
├─────────────────────┤
│ Document Results    │ ← Write summary
├─────────────────────┤
│ Update Task Status  │ ← Mark REVIEW or DONE
└─────────────────────┘
```

Agents are autonomous - they handle all these steps without needing manual input during execution.

---

## ⏱️ Expected Turnaround Times

| Agent | Task Type | Effort | Duration |
|-------|-----------|--------|----------|
| documentation-specialist | Large documentation (10+ KB) | 8-12h | 1-2 days |
| java-architect | Architecture design | 4-8h | 1 day |
| code-archaeologist | Codebase analysis | 6-12h | 1-2 days |
| code-reviewer | Code review + test plan | 4-8h | 1 day |
| test-engineer | Test suite creation | 8-16h | 1-2 days |
| build-engineer | Performance optimization | 4-8h | 1 day |

*Times vary based on complexity - agents will update task with progress*

---

## 🔗 Agent Knowledge & Tools

All agents have access to:

### Archon MCP Server
- Task management (find_tasks, manage_tasks)
- Project management (find_projects, manage_projects)
- Knowledge base search (rag_search_knowledge_base)
- Code examples (rag_search_code_examples)

### RVNKQuests Context
- Architecture analysis and patterns
- Code organization and structure
- Development standards and conventions
- Related documentation (CLAUDE.md, instruction files)

### Development Tools
- File reading and creation
- Git operations and commits
- Code analysis and testing
- Documentation generation

---

## ❓ Common Agent Questions

### Q: Will agent work match our code standards?

**A**: Yes! Agents have context on:
- Code style and naming conventions
- Design patterns we use
- Documentation standards
- Quality requirements

All agents are trained on RVNKQuests-specific patterns.

### Q: Can agents work on multiple tasks simultaneously?

**A**: Agents can be assigned to multiple queued tasks, but typically focus on one at a time. You can assign multiple tasks and they'll prioritize by task_order value.

### Q: What if an agent gets stuck?

**A**: Agents will:
1. Research more thoroughly using RAG
2. Document blockers in task comments
3. Update task status to indicate they need input
4. Request clarification or additional context

### Q: How do agents handle refactoring?

**A**: Refactoring follows this pattern:
1. Analyze current code and architecture
2. Plan refactoring strategy
3. Check for test coverage (tests first!)
4. Execute refactoring incrementally
5. Validate no functionality lost
6. Update documentation

Agents won't refactor without test coverage.

---

## ✅ Agent Best Practices

### Do This

✅ Assign agents to matching tasks (documentation → documentation-specialist)
✅ Provide comprehensive task descriptions
✅ Give context about constraints and goals
✅ Review work and provide feedback
✅ Keep task descriptions updated if requirements change

### Don't Do This

❌ Assign unrelated tasks (code review → java-architect)
❌ Use vague task descriptions
❌ Change requirements mid-task without updating Archon
❌ Assume agents will read your mind - be explicit
❌ Ignore agent progress updates

---

## 📚 Related Documentation

- **copilot-instructions.archon.md** - Detailed Archon workflow
- **CLAUDE.md** - Central reference for all agents
- **.claude/agents/*.md** - Individual agent specifications
- **ARCHON_TRAINING_GUIDE.md** - Developer onboarding

---

## 🎓 Agent Role Summary

| Agent | Best For | Current Tasks | Status |
|-------|----------|---|--------|
| documentation-specialist | Docs & training | doc-02, doc-03 | 🟢 Active |
| java-architect | Architecture & design | plan-04, plan-07, feat-12 | 📋 Ready |
| code-archaeologist | Code analysis | arch-06 | ✅ Done |
| code-reviewer | Code review & testing | test-05, data-10, feat-11 | 📋 Ready |
| test-engineer | Test design & execution | test-08, test-09 | 📋 Ready |
| build-engineer | Performance & build | test-09 | 📋 Ready |

---

**Training Material**: doc-03 Team Training Materials
**Created**: November 2, 2025
**Audience**: All team members coordinating with agents
