# Documentation Guidelines for RVNKQuests

## Documentation Philosophy

All project documentation should be clear, comprehensive, and maintained alongside code changes. RVNKQuests documentation focuses on quest system patterns, implementation guides, and development workflows.

## Documentation Structure

### Primary Documentation Files

**Project Root Documentation**:

- `README.md` - **PRIMARY PROJECT OVERVIEW** with quest catalog and features
- `ROADMAP.md` - **PRIMARY PROJECT STATUS REFERENCE** for current development priorities
- `pom.xml` - Maven configuration and dependencies

**Implementation Documentation** (`docs/`):

- `docs/api/` - Quest API documentation and interfaces
- `docs/architecture/` - Quest system architecture and design patterns
- `docs/milestones/` - Development milestone completion reports

**Documentation Update Process**:

**IMPORTANT**: Update relevant instruction files directly based on project developments.

**Documentation Update Workflow**:
1. Update `.github/copilot-instructions*.md` files when quest patterns change
2. Update `README.md` and `ROADMAP.md` for major quest system changes
3. Create specific documentation in `docs/` for technical quest specifications
4. Keep instruction files focused on AI guidance patterns for quest development

### Milestone Documentation Guidelines

**CRITICAL**: DO NOT create new status/milestone/completion tracking files.

**Instead: UPDATE ROADMAP.md directly**:
- Update "Current Status" section with latest quest achievements
- Update test results and quest validation status
- Update "Short-Term Goals" with next quest development steps
- Move completed quest features to "Recently Completed"
- Keep it concise - ROADMAP.md is the single source of truth for quest development status

**Only create milestone files when explicitly requested for major quest system releases**

## Project Status Tracking

### Primary Status Reference: ROADMAP.md Structure

**ROADMAP.md is the SINGLE SOURCE OF TRUTH for current RVNKQuests status**:
- Current quest development phase and milestone information (last 4-6 weeks + next 4-6 weeks)
- Recent quest system achievements and active issues
- Immediate quest development priorities
- Current test results and quest validation
- Active quest development goals and LogManager migration status

### Copilot Instructions Reference

**Copilot instruction files should REFERENCE ROADMAP.md, not duplicate content**:
- Document where to find quest development status information
- Provide guidelines for creating quest documentation
- Reference quest documentation structure and standards
- Do NOT duplicate project status information
- Focus on quest development patterns, not project content

**Example Reference Pattern**:

```markdown
## Project Status and Current State

**Project status information is maintained in ROADMAP.md.**

For current project status:
- **[ROADMAP.md](../ROADMAP.md)** - Current context and quest development timeline
- **[docs/milestones/](../docs/milestones/)** - Detailed quest system completion documentation
```

## Documentation Update Workflow

### When Quest Code Changes

1. **Update inline documentation**: Quest class docstrings, method comments, type hints
2. **Update API documentation**: If quest interfaces or public methods changed
3. **Update ROADMAP.md**: If quest milestone or development status changed
4. **Create milestone docs**: If major quest feature completed
5. **Update README.md**: If user-facing quest features changed

### Quest Development Documentation Standards

```java
/**
 * Quest implementation documentation pattern
 * 
 * @author Development Team
 * @since Version when quest was added
 */
public class ExampleQuest implements Quest {
    
    /**
     * Unique identifier for this quest
     * @return quest identifier string
     */
    @Override
    public String getId() {
        return QUEST_ID;
    }
    
    /**
     * Creates event listeners appropriate for the current quest state
     * 
     * @param state Current quest state
     * @return List of listeners to register for this state
     */
    @Override
    public List<Listener> createListenersForState(QuestState state) {
        // Implementation with clear logic documentation
    }
}
```

### Git Commit Documentation Standards

```bash
# Quest feature implementation
git commit -m "feat(quest): add piglin escort quest implementation

- Implement QuestPiglinFarFromHome with state management
- Add trigger and objective listeners for piglin escort
- Include quest completion validation and rewards

Docs: Updated ROADMAP.md with quest completion status
Closes: #123"
```

## Quest Documentation Best Practices

### Markdown Standards for Quest Docs

- Use proper heading hierarchy for quest organization
- Include code fences with `java` language identifiers for quest code
- Add blank lines around quest lists and code blocks
- Use descriptive link text for quest references

### Quest Code Documentation

```java
/**
 * Quest documentation pattern for all quest implementations
 * 
 * Explains the quest narrative, objectives, and technical implementation.
 * Includes state transitions, event handlers, and completion criteria.
 * 
 * @param plugin RVNKQuests plugin instance for resource access
 * @return Initialized quest with proper listener management
 * 
 * @see Quest interface for required methods
 * @see QuestState for available quest states
 */
public class QuestDocumentationPattern implements Quest {
    
    /**
     * Quest-specific logging with class identification
     */
    private final RVNKLogger logger = LogManager.getInstance(plugin, getClass());
    
    /**
     * Advances quest to new state with validation
     * 
     * @param newState Target quest state
     * @throws IllegalStateException if state transition is invalid
     */
    public void advanceState(QuestState newState) {
        // Clear documentation of state transition logic
    }
}
```

## Quest System Documentation Categories

### Quest Implementation Guides

**Location**: `docs/implementation/`
**Purpose**: Technical guides for implementing new quests
**Content**: State management, listener patterns, event handling

### Quest API Documentation

**Location**: `docs/api/`
**Purpose**: Interface definitions and method documentation
**Content**: Quest interface, QuestManager API, configuration options

### Quest Architecture Documentation

**Location**: `docs/architecture/`
**Purpose**: High-level quest system design and patterns
**Content**: Manager relationships, event flow, state diagrams

## Documentation Review Checklist

Before committing quest documentation changes:

- [ ] Spell check completed on quest descriptions and technical content
- [ ] Quest code examples tested and verified working
- [ ] Links to quest documentation verified
- [ ] ROADMAP.md updated if quest development status changed
- [ ] Markdown lint warnings addressed
- [ ] Quest API changes documented
- [ ] Quest state transitions clearly explained

## Key Quest Documentation Principles

1. **Single Source of Truth**: ROADMAP.md for quest development status
2. **Specific Naming**: No generic completion document names, use quest-specific names
3. **Comprehensive**: Include all needed information for quest implementation
4. **Timely**: Update quest documentation with code changes
5. **Accurate**: Verify quest examples work and state transitions are correct
6. **Quest-Focused**: Emphasize quest narrative, objectives, and technical patterns

## Quest Documentation Templates

### New Quest Documentation Template

```markdown
# [Quest Name] Implementation Guide

## Quest Overview

**Quest ID**: `quest_id`
**Narrative**: Brief description of quest story and objectives
**Trigger Condition**: What activates this quest
**Completion Criteria**: What constitutes quest completion

## Technical Implementation

### Quest States

- `NOT_STARTED`: Initial state, waiting for trigger
- `TRIGGER_FOUND`: Quest conditions detected
- `QUEST_ACTIVE`: Player participating in quest
- `OBJECTIVE_FOUND`: Quest objectives completed
- `COMPLETED`: Quest finished and rewards given

### Event Listeners

#### Trigger Listener
- **Event**: [EventType]
- **Condition**: [Specific conditions that trigger quest]
- **Action**: Advance to TRIGGER_FOUND state

#### Objective Listener
- **Event**: [EventType]
- **Condition**: [Conditions for quest progress]
- **Action**: Track progress, advance state when complete

### Configuration

```yaml
quests:
  quest_id:
    enabled: true
    world: "world"
    timeout_minutes: 30
```

## Testing

### Manual Testing Steps
1. [Step 1]
2. [Step 2]
3. [Verification step]

### Automated Tests
- State transition validation
- Event listener registration
- Configuration loading
```

---

**Remember**: Documentation is for developers implementing and maintaining quests. Write clearly, provide working examples, keep quest documentation updated with code changes.