# Git Workflow and Version Control Guidelines

## Git Workflow Integration

### Regular Commit Practices

**Commit frequently with meaningful messages following conventional commit format:**

```bash
# Quest feature implementation
git commit -m "feat(quest): implement piglin escort quest

- Add QuestPiglinFarFromHome with complete state management
- Implement trigger listener for lone piglin detection
- Add objective listener for escort completion validation
- Include quest configuration and documentation

Tests: Added unit tests for state transitions
Docs: Updated ROADMAP.md with quest completion status"
```

### Development Workflow with Git

1. **Start of Development Session**:
   ```bash
   git status
   git pull origin master
   git checkout -b feat/quest-implementation
   ```

2. **During Quest Development**:
   - Commit logical units of work (individual quest features)
   - Include tests with quest implementations
   - Update documentation with quest changes

3. **Quest Feature Completion**:
   ```bash
   git rebase -i HEAD~n
   git push origin feat/quest-implementation
   # Create pull request for quest review
   ```

### Commit Message Standards for RVNKQuests

- **feat(quest)**: New quest implementations or quest feature enhancements
- **feat(command)**: New commands or command enhancements
- **feat(config)**: Configuration system improvements
- **fix(quest)**: Quest bug fixes and state management corrections
- **fix(listener)**: Event listener fixes and improvements
- **docs**: Quest documentation updates and implementation guides
- **test**: Quest test additions or modifications
- **refactor**: Code refactoring without functional changes
- **chore**: Maintenance tasks, dependency updates

### Quest-Specific Commit Examples

```bash
# New quest implementation
git commit -m "feat(quest): add ancient guardian quest

- Implement QuestAncientGuardian with multi-stage progression
- Add monument discovery and guardian defeat objectives
- Include underwater breathing mechanics integration

Closes: #45"

# Quest bug fix
git commit -m "fix(quest): resolve piglin escort state corruption

- Fix listener unregistration causing memory leaks
- Correct state transition validation logic
- Add defensive checks for null quest entities

Fixes: #67"

# Configuration enhancement
git commit -m "feat(config): add quest timeout configuration

- Allow per-quest timeout configuration in config.yml
- Add validation for timeout values
- Update quest manager to respect timeout settings

Docs: Updated configuration documentation"
```

### Branch Strategy for Quest Development

- **master**: Production-ready quest system
- **develop**: Integration branch for quest development
- **feat/quest-[name]**: Individual quest implementation branches
- **fix/quest-[issue]**: Quest bug fixes
- **docs/quest-[topic]**: Quest documentation improvements

## Quality Gates for Git Commits

### Before Committing Quest Code

- **All quest tests passing** before committing quest implementations
- **No exposed credentials** in commit history (not applicable but good practice)
- **Quest documentation updated** for new quest API changes
- **ROADMAP.md updated** for quest milestone completion
- **Quest state transitions validated** and tested
- **Event listeners properly registered/unregistered**

### Quest Code Review Requirements

```bash
# Pre-commit checklist for quest development
git commit -m "feat(quest): implement city prophecy quest

Pre-commit validation:
- ✅ Quest state machine tested
- ✅ Event listeners validated  
- ✅ Configuration schema updated
- ✅ Unit tests passing
- ✅ Integration tests complete
- ✅ Documentation updated
- ✅ ROADMAP.md reflects progress

Technical details:
- Quest ID: first_city_prophecy
- States: 6 states from NOT_STARTED to COMPLETED
- Events: PlayerMoveEvent, PlayerInteractEvent
- Config: Added world and timeout settings"
```

### Git Workflow for LogManager Migration

**Current Priority**: LogManager migration commits should follow this pattern:

```bash
# Individual class migration
git commit -m "refactor(logging): migrate QuestManager to LogManager

- Replace Debug usage with LogManager pattern
- Update all logging calls to use parameterized format
- Add performance timing for quest operations
- Remove deprecated Debug imports

Migration: QuestManager.java complete
Remaining: 15 classes still use Debug pattern"

# Bulk migration commit
git commit -m "refactor(logging): migrate quest listeners to LogManager

Classes updated:
- ListenerPiglinEscort.java
- ListenerFirstCityProphecy.java  
- ListenerAncientGuardian.java

Migration: All quest listeners complete
Progress: 8/15 core classes migrated to LogManager"
```

## Git Integration with Quest Development

### Commit Frequency Guidelines

**Daily Commits** during active quest development:
- End-of-day commits with daily progress
- Feature completion commits for individual quest components
- Bug fix commits as issues are resolved

**Quest Milestone Commits**:
- Complete quest implementation commits
- Major feature addition commits (new quest types, system enhancements)
- Integration milestone commits (RVNKCore integration, etc.)

### Git Commands for Quest Development

```bash
# Check quest development status
git log --oneline --since="1 week ago" --grep="feat(quest)"

# Review quest-related changes
git diff HEAD~1 src/main/java/rvnk/rvnkquests/quest/

# Create quest feature branch
git checkout -b feat/quest-underwater-temple

# Commit quest implementation with full context
git add src/main/java/rvnk/rvnkquests/quest/QuestUnderwaterTemple.java
git add src/main/resources/config.yml
git add docs/quests/underwater-temple.md
git commit -m "feat(quest): implement underwater temple discovery quest

Complete implementation:
- Quest triggers on ocean monument proximity
- Multi-stage progression through temple exploration  
- Guardian defeat mechanics with team coordination
- Treasure discovery and distribution system

Configuration:
- Added quest.underwater_temple section to config.yml
- Configurable detection radius and team size limits
- World restriction and permission requirements

Testing:
- Unit tests for all state transitions
- Integration tests for event listener registration
- Manual testing completed on test server

Docs: Added underwater temple quest documentation
Closes: #89"
```

## Repository Hygiene

### Regular Maintenance Tasks

```bash
# Clean up feature branches after quest completion
git branch -d feat/quest-completed-feature
git push origin --delete feat/quest-completed-feature

# Update develop branch with completed quest features
git checkout develop
git pull origin develop
git merge master

# Rebase feature branch on latest develop
git checkout feat/quest-new-feature
git rebase develop
```

### Commit History Quality

- **Atomic commits**: Each commit represents a single logical change
- **Descriptive messages**: Clear explanation of what quest functionality changed
- **Scope indication**: Use quest/command/config prefixes to categorize changes
- **Issue references**: Link commits to GitHub issues where applicable

---

**Remember**: Git is your safety net for quest development. Commit often, write clear messages that explain quest functionality changes, keep history clean and reviewable.