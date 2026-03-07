# Standards & Conventions

**Developer standards, naming conventions, and project guidelines**

---

## 📋 Contents

### TASK_NAMING_STANDARD.md
- **Purpose**: Standardized naming convention for Archon tasks
- **Format**: `prefix-## Description` (3-4 words)
- **Status**: ✅ IMPLEMENTED across all 12 RVNKQuests tasks

**Prefix Categories**:
- **doc-** → Documentation tasks (doc-01, doc-02, doc-03, doc-04)
- **arch-** → Architecture analysis tasks (arch-06)
- **plan-** → Planning & design tasks (plan-04, plan-07)
- **test-** → Testing & quality tasks (test-05, test-08, test-09)
- **data-** → Data & persistence tasks (data-10)
- **feat-** → Feature enhancement tasks (feat-11, feat-12)

**Example Task Names**:
- ✅ `doc-02 Architecture Documentation` (GOOD - 3 words)
- ✅ `plan-04 Command System Design` (GOOD - 3 words)
- ✅ `test-05 Test Foundation Setup` (GOOD - 3 words)
- ❌ `Command System` (TOO SHORT - not descriptive)
- ❌ `Document command system design refactoring` (TOO LONG - 5 words)

**Benefits**:
- ✅ Clear categorization by prefix
- ✅ Sequential ordering (01-12)
- ✅ Concise naming (3-4 words)
- ✅ Consistency across project
- ✅ Readability on task board

**When to Use**:
- Creating new Archon tasks
- Naming related documentation
- Organizing project work
- Communicating about tasks

---

## 🎯 Task Naming Examples

### Documentation Phase (doc-##)
- `doc-01 Archon Project Setup` → Project initialization
- `doc-02 Architecture Documentation` → System design docs
- `doc-03 Team Training Materials` → Onboarding guides
- `doc-04 Documentation Structure & Organization` → Doc organization

### Architecture Phase (arch-##)
- `arch-06 Quest Architecture Analysis` → Architecture audit

### Planning Phase (plan-##)
- `plan-04 Command System Design` → Refactoring plan
- `plan-07 RVNKCore Integration Plan` → Migration planning

### Testing Phase (test-##)
- `test-05 Test Foundation Setup` → Test framework
- `test-08 State Machine Hardening` → Edge case testing
- `test-09 Performance Optimization` → Performance testing

### Data Phase (data-##)
- `data-10 Quest Persistence Schema` → Database schema

### Feature Phase (feat-##)
- `feat-11 Objective System Enhancement` → Feature development
- `feat-12 Reward System Modernization` → Feature enhancement

---

## 🔧 How to Use This Standard

### Creating New Tasks

1. **Choose the right prefix**:
   - Is it documentation? → `doc-`
   - Is it architecture analysis? → `arch-`
   - Is it planning/design? → `plan-`
   - Is it testing/quality? → `test-`
   - Is it data/persistence? → `data-`
   - Is it feature development? → `feat-`

2. **Choose the next sequential number**: (01-12 for current phase)

3. **Write 3-4 word description**:
   - Clear and specific
   - Describes the main objective
   - Avoids jargon when possible

4. **Format**: `PREFIX-## Description`

### Examples of Good vs Bad

| Style | Format | Status |
|-------|--------|--------|
| ✅ GOOD | `plan-04 Command System Design` | Clear, specific, 3 words |
| ✅ GOOD | `test-05 Test Foundation Setup` | Clear, specific, 3 words |
| ✅ GOOD | `feat-11 Objective System Enhancement` | Clear, specific, 3 words |
| ❌ BAD | `Command System Refactoring Planning Design` | Too long, 5 words |
| ❌ BAD | `Test` | Too vague, 1 word |
| ❌ BAD | `Documentation Tasks Organization Structure Optimization` | Too long, 5 words |

---

## 📊 Current Task Naming Status

**All RVNKQuests tasks follow the standard**:

| # | Task Name | Prefix | Status |
|---|-----------|--------|--------|
| 1 | doc-01 Archon Project Setup | doc | ✅ DONE |
| 2 | doc-02 Architecture Documentation | doc | 🟡 REVIEW |
| 3 | doc-03 Team Training Materials | doc | 🟡 REVIEW |
| 4 | doc-04 Documentation Structure & Organization | doc | 🟢 DOING |
| 5 | arch-06 Quest Architecture Analysis | arch | ✅ DONE |
| 6 | plan-04 Command System Design | plan | 📋 TODO |
| 7 | plan-07 RVNKCore Integration Plan | plan | 📋 TODO |
| 8 | test-05 Test Foundation Setup | test | 📋 TODO |
| 9 | test-08 State Machine Hardening | test | 📋 TODO |
| 10 | test-09 Performance Optimization | test | 📋 TODO |
| 11 | data-10 Quest Persistence Schema | data | 📋 TODO |
| 12 | feat-11 Objective System Enhancement | feat | 📋 TODO |
| 13 | feat-12 Reward System Modernization | feat | 📋 TODO |

**Conformance**: 100% (all tasks follow standard)

---

## 🎓 Standards Reference

### Prefix Definitions

**doc-** (Documentation)
- Purpose: Create documentation
- Duration: 8-24 hours
- Output: Markdown documents
- Examples: Guides, analysis, training materials

**arch-** (Architecture)
- Purpose: Analyze architecture
- Duration: 12-24 hours
- Output: Architecture reports with findings
- Examples: System analysis, design patterns

**plan-** (Planning)
- Purpose: Plan and design
- Duration: 4-8 hours
- Output: Design documents, specifications
- Examples: API design, refactoring strategy

**test-** (Testing)
- Purpose: Test and verify quality
- Duration: 8-16 hours
- Output: Tests, reports, metrics
- Examples: Test suite, performance testing

**data-** (Data & Persistence)
- Purpose: Data layer design/implementation
- Duration: 4-8 hours
- Output: Schema, migration scripts
- Examples: Database design, persistence layer

**feat-** (Feature)
- Purpose: Implement features
- Duration: 8-16 hours
- Output: Code, tests, documentation
- Examples: New features, enhancements

---

## 📝 Task Description Template

When creating a new task, use this template:

```markdown
## Task: [PREFIX-## TASK NAME]

### Objective
[Clear statement of what needs to be done]

### Requirements
- [Specific requirement 1]
- [Specific requirement 2]
- [Specific requirement 3]

### Success Criteria
- ✅ [Measurable success criterion 1]
- ✅ [Measurable success criterion 2]
- ✅ [Measurable success criterion 3]

### Expected Deliverable
[What format, how much detail, where to save]

### Estimated Effort
[X-Y hours/days]
```

---

## 🔗 Related Standards

**In docs/ directory**:
- **guide/** - Workflow and process standards
- **features/** - Architecture and design standards
- **reports/** - Documentation standards for reports

**Project root**:
- **CLAUDE.md** - Development standards
- **.github/copilot-instructions.documentation.md** - Documentation standards

---

## ✅ Checklist for New Tasks

When creating a new task, verify:

- [ ] Task has appropriate prefix (doc/arch/plan/test/data/feat)
- [ ] Task has sequential number (##)
- [ ] Task description is 3-4 words
- [ ] Task description is clear and specific
- [ ] Task is formatted as: `PREFIX-## Description`
- [ ] Task description fully explains objective
- [ ] Success criteria are defined
- [ ] Assignee is identified
- [ ] Priority is set (high/medium/low)
- [ ] Related documentation is linked

---

## 🚀 Getting Started with This Standard

### For Team Leads
1. Review: This entire README
2. Apply: When creating new tasks
3. Enforce: Consistent naming across all tasks
4. Reference: Point team to this doc when questions arise

### For All Team Members
1. Understand: The 6 prefix categories
2. Recognize: Task types by prefix
3. Follow: Standard when creating/naming tasks
4. Reference: When unclear about naming

### For Documentation
1. Use: Prefix-based organization
2. Create: Separate folders for different task types
3. Organize: By feature/domain when possible
4. Cross-reference: Related tasks and documentation

---

## 📈 Benefits of This Standard

✅ **Clarity**: Task type immediately obvious by prefix
✅ **Organization**: All 12 tasks clearly categorized
✅ **Consistency**: Same format across entire project
✅ **Discoverability**: Easy to find related tasks
✅ **Communication**: Team uses common language
✅ **Scalability**: Pattern works for larger projects
✅ **Professionalism**: Structured and organized approach

---

## 💡 Tips for Using This Standard

### When Naming Tasks
- Start with the most specific prefix
- Use clear, action-oriented verbs
- Keep to 3-4 words maximum
- Avoid abbreviations (spell out full words)
- Make task name searchable

### When Searching Tasks
- Search by prefix (e.g., `doc-` for docs)
- Search by keyword (e.g., `architecture`)
- Filter by status (todo, doing, review, done)
- Sort by priority (high, medium, low)

### When Communicating
- "Working on doc-02" is clearer than "Documentation"
- "Need help with feat-12?" is better than "Features question?"
- Use full task name: "plan-04 Command System Design" not just "plan-04"

---

**Purpose**: Standardize task naming and organization
**Status**: ✅ IMPLEMENTED and ACTIVE
**Conformance**: 100% (all 13 RVNKQuests tasks)
**Last Updated**: November 2, 2025
