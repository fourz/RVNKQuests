# RVNKQuests Archon Task Naming Convention

**Date**: November 2, 2025
**Status**: ✅ IMPLEMENTED
**Convention**: `prefix-##` + 3-4 word descriptions

---

## Naming Scheme

All tasks follow the format: `PREFIX-## TASK NAME` where:

- **PREFIX**: Category identifier (doc, arch, plan, test, feat, data)
- **##**: Sequential number (01-12)
- **TASK NAME**: 3-4 word concise description

---

## Task Prefix Categories

| Prefix | Purpose | Examples |
|--------|---------|----------|
| **doc-** | Documentation | doc-01, doc-02, doc-03 |
| **arch-** | Architecture | arch-06 |
| **plan-** | Planning | plan-04, plan-07 |
| **test-** | Testing & Quality | test-05, test-08, test-09 |
| **feat-** | Features & Enhancement | feat-11, feat-12 |
| **data-** | Data & Persistence | data-10 |

---

## All Tasks (Renamed)

### Documentation Phase

| ID | Task | Prefix | Status |
|----|------|--------|--------|
| 01 | **doc-01 Archon Project Setup** | doc | ✅ DONE |
| 02 | **doc-02 Architecture Documentation** | doc | 🟢 DOING |
| 03 | **doc-03 Team Training Materials** | doc | 🟢 DOING |

### Planning Phase

| ID | Task | Prefix | Status |
|----|------|--------|--------|
| 04 | **plan-04 Command System Design** | plan | 📋 TODO |
| 07 | **plan-07 RVNKCore Integration Plan** | plan | 📋 TODO |

### Architecture Phase

| ID | Task | Prefix | Status |
|----|------|--------|--------|
| 06 | **arch-06 Quest Architecture Analysis** | arch | 🟡 REVIEW |

### Testing & Quality Phase

| ID | Task | Prefix | Status |
|----|------|--------|--------|
| 05 | **test-05 Test Foundation Setup** | test | 📋 TODO |
| 08 | **test-08 State Machine Hardening** | test | 📋 TODO |
| 09 | **test-09 Performance Optimization** | test | 📋 TODO |

### Data & Persistence Phase

| ID | Task | Prefix | Status |
|----|------|--------|--------|
| 10 | **data-10 Quest Persistence Schema** | data | 📋 TODO |

### Feature Enhancement Phase

| ID | Task | Prefix | Status |
|----|------|--------|--------|
| 11 | **feat-11 Objective System Enhancement** | feat | 📋 TODO |
| 12 | **feat-12 Reward System Modernization** | feat | 📋 TODO |

---

## Before & After Comparison

| Before | After | Reason |
|--------|-------|--------|
| Archon Project Setup & Validation | doc-01 Archon Project Setup | Documentation, foundational task |
| Architecture Documentation & Knowledge Base Population | doc-02 Architecture Documentation | Documentation focus |
| Team Training & Archon Workflow Documentation | doc-03 Team Training Materials | Documentation phase |
| Command System Refactoring - Planning & Design | plan-04 Command System Design | Planning for refactor |
| Test Suite Foundation & Coverage Analysis | test-05 Test Foundation Setup | Testing infrastructure |
| Quest System Architecture Analysis | arch-06 Quest Architecture Analysis | Architecture analysis |
| RVNKCore Integration Planning | plan-07 RVNKCore Integration Plan | Planning migration |
| State Machine Hardening & Edge Case Testing | test-08 State Machine Hardening | Quality/hardening |
| Performance Benchmarking & Optimization | test-09 Performance Optimization | Quality/performance |
| Database Schema & Persistence Layer Review | data-10 Quest Persistence Schema | Data/persistence |
| Objective System Enhancement & Patterns | feat-11 Objective System Enhancement | Feature enhancement |
| Reward System Modernization | feat-12 Reward System Modernization | Feature enhancement |

---

## Benefits of New Convention

✅ **Clear Categorization** - Immediately understand task type by prefix

✅ **Sequential Ordering** - Easy to track progress through phases

✅ **Concise Naming** - 3-4 words describe task clearly

✅ **Consistency** - All tasks follow same pattern

✅ **Readability** - Easier to scan task board at a glance

✅ **Cross-Project Reusability** - Pattern can be applied to other projects

---

## Usage Examples

### In Archon Project Board

```
Phase 1: Archon Integration & Setup
├── doc-01 Archon Project Setup ✅ DONE
├── doc-02 Architecture Documentation 🟢 DOING
├── doc-03 Team Training Materials 🟢 DOING
└── arch-06 Quest Architecture Analysis 🟡 REVIEW

Phase 2: Core Refactoring & Quality
├── plan-04 Command System Design 📋 TODO
├── test-05 Test Foundation Setup 📋 TODO
├── test-08 State Machine Hardening 📋 TODO
└── test-09 Performance Optimization 📋 TODO

Phase 3: Data & Features
├── data-10 Quest Persistence Schema 📋 TODO
├── feat-11 Objective System Enhancement 📋 TODO
├── plan-07 RVNKCore Integration Plan 📋 TODO
└── feat-12 Reward System Modernization 📋 TODO
```

### In Daily Standup

"I'm working on **doc-02** and **doc-03** this week, then we'll move to **test-05** and **plan-04**."

### In Status Reports

"**doc-01** is complete, **doc-02** and **doc-03** are in progress, **arch-06** is in review."

---

## Naming Rationale

### Why These Prefixes?

- **doc-**: Documentation tasks (essential for onboarding & knowledge base)
- **arch-**: Architecture analysis & design tasks
- **plan-**: Planning & design specification tasks
- **test-**: Testing, quality, hardening tasks
- **feat-**: Feature implementation & enhancement tasks
- **data-**: Data model, persistence, schema tasks

### Why 3-4 Words?

- **Too short** (1-2 words): Not descriptive enough ("Command System" vs "Command System Design")
- **Just right** (3-4 words): Concise yet descriptive ("Quest Persistence Schema")
- **Too long** (5+ words): Harder to read on task board ("Database Schema and Persistence Layer Review")

### Sequential Numbering (01-12)

Allows easy tracking of:
- Total task count (12 in current phase)
- Completion percentage (6/12 = 50%)
- Relative priority and order
- Phase progression

---

## Task Board View

```
RVNKQuests Development - Task Board
=====================================

✅ COMPLETE (1)
  └─ doc-01 Archon Project Setup

🟢 IN PROGRESS (2)
  ├─ doc-02 Architecture Documentation
  └─ doc-03 Team Training Materials

🟡 IN REVIEW (1)
  └─ arch-06 Quest Architecture Analysis

📋 TODO (8)
  ├─ plan-04 Command System Design
  ├─ test-05 Test Foundation Setup
  ├─ test-08 State Machine Hardening
  ├─ test-09 Performance Optimization
  ├─ data-10 Quest Persistence Schema
  ├─ feat-11 Objective System Enhancement
  ├─ plan-07 RVNKCore Integration Plan
  └─ feat-12 Reward System Modernization

Progress: 1 Done | 2 Doing | 1 Review | 8 Todo (25% Complete)
```

---

## Implementation Complete ✅

All 12 tasks in the RVNKQuests Archon project have been renamed to follow the new convention:

- ✅ **doc-01, doc-02, doc-03** - Documentation phase
- ✅ **arch-06** - Architecture analysis
- ✅ **plan-04, plan-07** - Planning tasks
- ✅ **test-05, test-08, test-09** - Testing & quality
- ✅ **data-10** - Data & persistence
- ✅ **feat-11, feat-12** - Features & enhancements

All tasks are now organized, categorized, and easily identifiable for the development team.

---

**Implementation Date**: November 2, 2025
**Convention Version**: 1.0
**Status**: ✅ ACTIVE IN ARCHON PROJECT
