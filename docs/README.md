# RVNKQuests Documentation

**Central index for all RVNKQuests project documentation**

---

## 📚 Documentation Structure

This directory contains all project documentation organized by purpose:

### 🎯 [standards/](./standards/) - Standards & Conventions

Developer standards, naming conventions, and project guidelines.

**Key Documents**:
- **TASK_NAMING_STANDARD.md** - Archon task naming convention (prefix-##, 3-4 word format)

**Purpose**: Ensure consistency across the project

---

### 📖 [guide/](./guide/) - Guides & Training

Team training materials, onboarding guides, and workflow documentation.

**Key Documents**:
- **QUICK_START.md** (5 min) - Quick workflow overview for new developers
- **ARCHON_TRAINING_GUIDE.md** (15 min) - Complete onboarding and training curriculum
- **AGENT_ROLE_DESCRIPTIONS.md** - Guide to working with 6 specialized Claude agents

**Purpose**: Help team members understand workflows and get productive quickly

---

### 🏗️ [features/](./features/) - Architecture & Features

System architecture analysis, design patterns, and technical specifications.

**Key Documents**:
- **QUEST_SYSTEM_ARCHITECTURE_ANALYSIS.md** (40+ KB) - Comprehensive architecture analysis
  - Health score: 7.5/10
  - 3 critical production blockers identified
  - 10 design patterns documented (6 good, 4 missing opportunities)
  - Technical debt inventory with prioritization
  - 10 optimization recommendations
  - 7-10 week critical path to production readiness

- **ARCHITECTURE_FINDINGS_SUMMARY.md** (15 KB) - Executive summary of architecture

**Purpose**: Understand system design, identify improvement opportunities, plan migration

---

### 📊 [reports/](./reports/) - Reports & Status

Project status reports, completion reports, and validation documentation.

**Key Documents**:

**Status Reports**:
- **ARCHON_PROJECT_STATUS_BRIEF.md** - Current project status and priorities (updated Nov 2)
- **ARCHON_VALIDATION_STATUS.md** - Task execution validation report
- **ARCHON_EXECUTION_SUMMARY.md** - Initial project execution summary

**Completion Reports**:
- **FINAL_VALIDATION_REPORT.md** - Project validation sign-off
- **TASK6_COMPLETION_REPORT.md** - Architecture analysis task completion (arch-06)
- **DOC-02_DOC-03_COMPLETION_SUMMARY.md** - Documentation tasks completion summary

**Review Documentation**:
- **APPROVAL_CHECKLIST_DOC02_DOC03.md** - Review checklist for documentation tasks
- **COMPLETION_REVIEW_DOC02_DOC03.md** - Detailed review assessment

**Purpose**: Track project progress, validate work quality, document decisions

---

## 🗺️ Quick Navigation

### For New Team Members

1. **First 5 minutes**: Read [guide/QUICK_START.md](./guide/QUICK_START.md)
2. **Full onboarding** (15 min): Read [guide/ARCHON_TRAINING_GUIDE.md](./guide/ARCHON_TRAINING_GUIDE.md)
3. **Understanding agents**: Read [guide/AGENT_ROLE_DESCRIPTIONS.md](./guide/AGENT_ROLE_DESCRIPTIONS.md)

### For Architecture Review

1. **Quick overview** (5 min): Read [features/ARCHITECTURE_FINDINGS_SUMMARY.md](./features/ARCHITECTURE_FINDINGS_SUMMARY.md)
2. **Deep dive** (1-2 hours): Read [features/QUEST_SYSTEM_ARCHITECTURE_ANALYSIS.md](./features/QUEST_SYSTEM_ARCHITECTURE_ANALYSIS.md)

### For Project Status

1. **Current status**: Read [reports/ARCHON_PROJECT_STATUS_BRIEF.md](./reports/ARCHON_PROJECT_STATUS_BRIEF.md)
2. **Task details**: Read [reports/DOC-02_DOC-03_COMPLETION_SUMMARY.md](./reports/DOC-02_DOC-03_COMPLETION_SUMMARY.md)

### For Validation/Review

1. **Review checklist**: Read [reports/APPROVAL_CHECKLIST_DOC02_DOC03.md](./reports/APPROVAL_CHECKLIST_DOC02_DOC03.md)
2. **Detailed assessment**: Read [reports/COMPLETION_REVIEW_DOC02_DOC03.md](./reports/COMPLETION_REVIEW_DOC02_DOC03.md)

---

## 📈 Documentation Statistics

| Category | Files | Size | Purpose |
|----------|-------|------|---------|
| **Standards** | 1 | 10 KB | Conventions & naming |
| **Guides** | 3 | 20 KB | Training & onboarding |
| **Features** | 2 | 55 KB | Architecture & design |
| **Reports** | 7 | 95 KB | Status & completion |
| **Total** | **13** | **180 KB** | Complete documentation |

---

## 🎯 Documentation Domains & Purpose

### standards/
- **Who**: Team leads, architects
- **Why**: Maintain consistency and standards
- **What**: Naming conventions, patterns, guidelines

### guide/
- **Who**: All team members, new developers
- **Why**: Get productive quickly, understand workflows
- **What**: Training, onboarding, how-to guides

### features/
- **Who**: Architects, senior developers
- **Why**: Understand system design and plan improvements
- **What**: Architecture analysis, design patterns, technical specifications

### reports/
- **Who**: Project managers, architects, team leads
- **Why**: Track progress, validate quality, document decisions
- **What**: Status reports, completion reports, validation

---

## 📚 Cross-References

### By Audience

**New Developers**:
- Start: guide/QUICK_START.md
- Next: guide/ARCHON_TRAINING_GUIDE.md
- Reference: guide/AGENT_ROLE_DESCRIPTIONS.md

**Architects**:
- Core: features/QUEST_SYSTEM_ARCHITECTURE_ANALYSIS.md
- Summary: features/ARCHITECTURE_FINDINGS_SUMMARY.md
- Status: reports/ARCHON_PROJECT_STATUS_BRIEF.md

**Project Managers**:
- Status: reports/ARCHON_PROJECT_STATUS_BRIEF.md
- Validation: reports/FINAL_VALIDATION_REPORT.md
- Metrics: reports/ARCHON_EXECUTION_SUMMARY.md

**Code Reviewers**:
- Checklist: reports/APPROVAL_CHECKLIST_DOC02_DOC03.md
- Assessment: reports/COMPLETION_REVIEW_DOC02_DOC03.md
- Standards: standards/TASK_NAMING_STANDARD.md

---

## 🔍 Finding Documentation

### By Task Type

**Documentation Tasks** (doc-##):
- See: guide/ and reports/

**Architecture Tasks** (arch-##):
- See: features/

**Planning Tasks** (plan-##):
- See: features/QUEST_SYSTEM_ARCHITECTURE_ANALYSIS.md for context

**Testing Tasks** (test-##):
- See: guide/ for workflow understanding
- See: features/ for architecture context

**Data Tasks** (data-##):
- See: features/ for architecture context

**Feature Tasks** (feat-##):
- See: features/ for architecture and patterns

---

## 📝 Maintenance Notes

### Adding New Documentation

1. Determine the category (standards/guide/features/reports)
2. Create file with clear naming: `CATEGORY_DESCRIPTIVE_NAME.md`
3. Add entry to the appropriate subdirectory README
4. Update this main README if adding a new major document

### File Naming Convention

- **Standards**: `STANDARD_NAME.md` (e.g., `TASK_NAMING_STANDARD.md`)
- **Guides**: `GUIDE_TOPIC.md` (e.g., `ARCHON_TRAINING_GUIDE.md`)
- **Features**: `FEATURE_ARCHITECTURE_ANALYSIS.md` (e.g., `QUEST_SYSTEM_ARCHITECTURE_ANALYSIS.md`)
- **Reports**: `REPORT_TYPE_SUBJECT.md` (e.g., `APPROVAL_CHECKLIST_DOC02_DOC03.md`)

---

## 🔗 Related Documentation in Project Root

**Central Hub**:
- `CLAUDE.md` - Central AI assistant instructions and reference

**Project Management**:
- `ROADMAP.md` - Project timeline and goals
- `README.md` - Project overview
- `PRP.md` - Product requirements plan

**Copilot Instructions**:
- `.github/copilot-instructions.md` - Instruction index
- `.github/copilot-instructions.archon.md` - Archon workflow details
- `.github/copilot-instructions.documentation.md` - Documentation standards

---

## 🚀 Getting Started

**New to the project?**
1. Read `../CLAUDE.md` (central reference)
2. Read `guide/QUICK_START.md` (5-minute overview)
3. Read `guide/ARCHON_TRAINING_GUIDE.md` (complete training)
4. Check `reports/ARCHON_PROJECT_STATUS_BRIEF.md` (current status)

**Need to understand architecture?**
1. Read `features/ARCHITECTURE_FINDINGS_SUMMARY.md` (quick overview)
2. Read `features/QUEST_SYSTEM_ARCHITECTURE_ANALYSIS.md` (detailed analysis)

**Reviewing work?**
1. Read `reports/APPROVAL_CHECKLIST_DOC02_DOC03.md` (review process)
2. Read `reports/COMPLETION_REVIEW_DOC02_DOC03.md` (detailed assessment)

---

**Documentation Last Updated**: November 2, 2025, 05:25 UTC
**Total Documentation**: 180+ KB across 13 files
**Status**: ✅ Organized and indexed
