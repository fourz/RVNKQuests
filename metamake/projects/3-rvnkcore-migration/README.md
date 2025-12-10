# PROJECT 3: RVNKCore Migration

**Status**: PLANNED (Starts Q1 2026)
**Timeline**: Q1-Q2 2026 (estimated 3-4 months)
**Priority**: HIGH - Enables plugin ecosystem consolidation

---

## Overview

Migrate RVNKQuests from standalone services to RVNKCore unified architecture. This aligns RVNKQuests with the broader Ravenkraft plugin ecosystem and enables code sharing with sibling plugins (RVNKLore, RVNKWorlds, BarterShops).

## Context

RVNKCore is a unified core library being extracted from RVNKTools. It provides:

- **ServiceRegistry**: Dependency injection and service management
- **Repository Pattern**: Standardized data access layer
- **Database Abstraction**: MySQL/SQLite with HikariCP pooling
- **DTO Layer**: Cross-boundary data transfer objects
- **Command Framework**: Centralized command management
- **Configuration Management**: YAML-based setup

Reference: `shared/derek/metamake/projects/archon-doc-sync/output/migration/to-rvnkcore.json`

## Objectives

### Primary: Integrate RVNKCore Services
- Migrate custom services to RVNKCore ServiceRegistry
- Replace standalone database code with RVNKCore repositories
- Update configuration management to use RVNKCore ConfigLoader
- Implement RVNKCore DTOs for data structures
- Refactor dependency injection to use ServiceRegistry

### Secondary: Enable Code Sharing
- Create API for other plugins to consume quest data
- Share common patterns with RVNKLore integration
- Prepare codebase for reusable components
- Document shared interfaces and patterns

### Tertiary: Improve Maintainability
- Reduce code duplication across plugins
- Standardize patterns with RVNKCore
- Improve testability through dependency injection
- Simplify configuration management

## Phases

### Phase 1: Analysis & Planning
- Document current RVNKQuests services and dependencies
- Review RVNKCore architecture and patterns
- Identify migration candidates (services, repositories)
- Create detailed implementation plan
- Design RVNKCore integration points

### Phase 2: Service Migration
- Extract services to RVNKCore interfaces
- Implement service registrations with ServiceRegistry
- Migrate database code to repository pattern
- Update dependency injection throughout codebase
- Comprehensive testing of migrated services

### Phase 3: Configuration & Data Access
- Migrate to RVNKCore ConfigLoader
- Update database configuration handling
- Implement RVNKCore repository pattern
- Add async/CompletableFuture operations
- Test database operations (CRUD) thoroughly

### Phase 4: API & Integration
- Create public API for quest data access
- Design DTO layer for cross-plugin data
- Implement API endpoints (future web integration)
- Document plugin integration patterns
- Test with RVNKLore and other plugins

### Phase 5: Testing & Validation
- Create integration tests with RVNKCore
- Test with multiple plugins depending on RVNKQuests
- Performance validation and optimization
- Security review of API and data access
- Full regression testing

### Phase 6: Deployment & Documentation
- Update README.md and documentation
- Create migration guide for plugin developers
- Update ROADMAP.md with completion status
- Prepare release notes
- Deploy to production servers

## Success Criteria

### Code Quality
- All RVNKCore patterns applied consistently
- ServiceRegistry used for all service management
- Repository pattern for all data access
- DTOs for all cross-boundary data transfers
- Test coverage >= 70% for migrated code

### Functionality
- All existing quest features work as before
- New RVNKCore services provide expected functionality
- Database operations (CRUD) verified with multiple databases
- Configuration management works with RVNKCore patterns
- Async operations don't block server thread

### Integration
- Other plugins can access quest data via API
- RVNKCore services integrated with RVNKLore
- Cross-plugin patterns documented with examples
- No performance regressions in quest operations
- Version compatibility with RVNKCore maintained

### Documentation
- RVNKCore integration guide created
- Plugin integration examples provided
- Configuration documentation updated
- Migration notes for future plugin updates
- API documentation complete

## Deliverables

- [ ] Migrated services using RVNKCore patterns
- [ ] Updated database layer with repositories
- [ ] New configuration management system
- [ ] Comprehensive test coverage for migrations
- [ ] Public API for quest data access
- [ ] Integration examples with other plugins
- [ ] Complete documentation and guides
- [ ] Release notes and upgrade instructions

## Dependencies

**Must Complete First**:
- PROJECT 1: Archon Integration (task management)
- PROJECT 2: Core Refactoring (cleaner code for easier migration)
- RVNKCore extraction from RVNKTools (external project)

**External**:
- RVNKCore library (when available)
- RVNKLore plugin (for integration testing)
- Test Minecraft server with multiple plugins

## Prerequisites

### Knowledge Requirements
- Understanding of RVNKCore architecture
- Experience with ServiceRegistry pattern
- Familiarity with async/CompletableFuture
- Database design and SQL fundamentals
- Bukkit plugin API patterns

### Documentation References
- RVNKCore Integration Guide (from to-rvnkcore.json)
- RVNKCore ServiceRegistry documentation
- Repository pattern examples
- DTO design patterns

## Estimated Effort

- **Analysis & Planning**: 40-60 hours
- **Service Migration**: 80-120 hours
- **Configuration & Data Access**: 60-80 hours
- **API & Integration**: 40-60 hours
- **Testing & Validation**: 60-80 hours
- **Documentation & Deployment**: 30-40 hours
- **Total**: 310-440 hours (~2-2.5 months, full-time)

## Risks & Mitigation

| Risk | Impact | Mitigation |
|------|--------|-----------|
| RVNKCore not ready on schedule | HIGH | Work on Phase 1-2 in parallel, maintain timeline flexibility |
| Data migration issues | HIGH | Comprehensive backup, validation tests, rollback plan |
| Breaking plugin integrations | HIGH | Extensive testing, gradual rollout, communication |
| Performance degradation | MEDIUM | Benchmarking, optimization, caching strategies |
| Team unfamiliar with RVNKCore | MEDIUM | Training, documentation, code reviews |

## Related Projects

**Enables**:
- RVNKCore ecosystem consolidation
- Code sharing with RVNKLore, RVNKWorlds, BarterShops
- Unified plugin architecture across RVNK network

**Depends On**:
- PROJECT 1: Archon Integration (task management)
- PROJECT 2: Core Refactoring (cleaner code)
- RVNKCore availability (external project)

**Parallel**:
- Ongoing development of other plugin features

## Key Decision Points

1. **Scope**: Which services to migrate in Phase 1? (Quest, Objective, Reward, Trigger)
2. **Timeline**: When is RVNKCore ready? (Impacts project start date)
3. **API Design**: What should public quest API expose? (Data, queries, mutations)
4. **Backward Compatibility**: Support old config files during migration?
5. **Rollout Strategy**: Big bang or gradual migration? (Affects risk profile)

## References

- **[IMPLEMENTATION_PLAN.md](../../docs/plans/IMPLEMENTATION_PLAN.md)** - Full implementation overview
- **[ROADMAP.md](../../ROADMAP.md)** - Project status and milestones
- **[to-rvnkcore.json](shared/derek/metamake/projects/archon-doc-sync/output/migration/to-rvnkcore.json)** - RVNKCore migration guide (reference)
- **[.github/supplemental/copilot-instructions.rvnkcore.md](.github/supplemental/copilot-instructions.rvnkcore.md)** - RVNKCore patterns

## Status Updates

**Last Updated**: November 1, 2025
**Current Status**: Planning phase
**Blocked By**: RVNKCore availability + PROJECT 1 & 2 completion
**Next Steps**: Begin Phase 1 analysis once PROJECT 2 is complete

---

**Project Owner**: Derek Schrishuhn
**Team**: RVNKQuests Development Team
**Tracker**: Archon Task Board (once initialized)
**Stakeholders**: RVNKLore, RVNKWorlds, BarterShops teams
