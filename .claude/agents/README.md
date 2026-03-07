# Claude Agents Directory

Specialized AI agent definitions for RVNKQuests development, following Anthropic's standards for agent creation.

## ⚠️ Important: Archon Integration

All agents operate under the **Archon-first rule**. Before any task-related work:

1. **Check** if Archon MCP server is available
2. **Use** Archon task management as PRIMARY system
3. **Follow** task-driven development workflow

See **[CLAUDE.md](../../CLAUDE.md)** and **[.github/copilot-instructions.archon.md](../../.github/copilot-instructions.archon.md)** for complete guidance.

## Purpose

Each agent file defines a specific role with:
- **Domain**: Area of responsibility
- **Expertise**: Required knowledge and skills (RVNKQuests-specific)
- **Archon Integration**: Task management and knowledge base workflows
- **Autonomous Actions**: What the agent can do without approval
- **Constraints**: What requires user approval
- **Decision Guidelines**: How to make common decisions
- **Quality Standards**: Requirements before marking work complete

## Available Agents

### 🎯 Core Development Agents
*See [Core Development README](_+_README.md) for detailed guidance*

- **[backend-developer.md](backend-developer.md)** — Server-side expert for scalable APIs and microservices
- **[frontend-developer.md](frontend-developer.md)** — UI/UX specialist for React, Vue, and Angular
- **[fullstack-developer.md](fullstack-developer.md)** — End-to-end feature development across the entire stack
- **[python-developer.md](python-developer.md)** — Python code implementation, testing, and quality assurance
- **[python-pro.md](python-pro.md)** — Advanced Python patterns, performance optimization, and architectural design
- **[typescript-pro.md](typescript-pro.md)** — TypeScript expert for type-safe JavaScript development
- **[csharp-developer.md](csharp-developer.md)** — C# and .NET development specialist
- **[java-architect.md](java-architect.md)** — Enterprise Java architecture and design patterns
- **[react-specialist.md](react-specialist.md)** — React ecosystem expert for modern web applications
- **[sql-pro.md](sql-pro.md)** — Database design, query optimization, and SQL expertise

### 🔧 Developer Experience Agents
*See [Developer Experience README](_README.md) for detailed guidance*

- **[build-engineer.md](build-engineer.md)** — Build system optimization and configuration specialist
- **[dependency-manager.md](dependency-manager.md)** — Package and dependency management expert
- **[documentation-engineer.md](documentation-engineer.md)** — Technical documentation and API reference specialist
- **[git-workflow-manager.md](git-workflow-manager.md)** — Version control, commits, branching, and release management
- **[refactoring-specialist.md](refactoring-specialist.md)** — Code refactoring and architectural improvement expert
- **[tooling-engineer.md](tooling-engineer.md)** — Developer tooling and IDE configuration specialist

### 🚀 Framework & Platform Specialists

- **[fastmcp-developer.md](fastmcp-developer.md)** — MCP server implementation, tool creation, provider integration ✨ **PROJECT-SPECIFIC**
- **[mcp-developer.md](mcp-developer.md)** — Model Context Protocol specialist for AI integrations
- **[minecraft-rvnk-admin.md](minecraft-rvnk-admin.md)** — Minecraft server administration using RvnkDev MCP tools (LuckPerms, Dynmap, CoreProtect) ✨ **PROJECT-SPECIFIC**

### 🛡️ Safety & Quality Agents

- **[security-engineer.md](security-engineer.md)** — Credential management, production safety, and secure coding
- **[test-engineer.md](test-engineer.md)** — Test implementation, validation, and quality assurance ✨ **PROJECT-SPECIFIC**
- **[test-orchestrator.md](test-orchestrator.md)** — RvnkDev FastMCP Server and RVNKQuests integration testing ✨ **PROJECT-SPECIFIC**
- **[test-orchestrator-enhanced.md](test-orchestrator-enhanced.md)** — Enhanced test orchestrator with full Claude skills integration, multi-environment coordination, and Archon task management ✨ **PROJECT-SPECIFIC (NEW)**
- **[technical-writer.md](technical-writer.md)** — Code documentation, API references, and inline comments

### 📋 Planning & Architecture Agents

- **[project-architect.md](project-architect.md)** — Complex implementation planning and metamake framework coordination ✨ **PROJECT-SPECIFIC**

## Usage with Claude Desktop

Claude Desktop natively supports loading agents from this directory. Agents are selected automatically based on the task context.

## Usage with GitHub Copilot

GitHub Copilot does not yet support Claude's native agent format. For Copilot users, the corresponding instruction files in `.github/` and `.github/supplemental/` provide equivalent guidance through the traditional instruction file system.

### RvnkDev MCP Server Project Mappings

**Project-specific agents** (marked with ✨):
- `fastmcp-developer.md` → `.github/copilot-instructions.fastmcp.md`
- `test-engineer.md` → `.github/supplemental/copilot-instructions.tests.md`
- `test-orchestrator.md` → `.github/supplemental/copilot-instructions.test-orchestrator.md`
- `project-architect.md` → `.github/supplemental/copilot-instructions.metamake.md`

**General development agents**:
- `python-developer.md` → `.github/copilot-instructions.best-practices.md` + `.github/copilot-instructions.patterns.md`
- `security-engineer.md` → `.github/copilot-instructions.security.md`
- `git-workflow-manager.md` → `.github/copilot-instructions.versioning.md`
- `technical-writer.md` → `.github/copilot-instructions.documentation.md`

## Quick Selection Guide

### By Task Type

| If you need to... | Use this agent |
|-------------------|----------------|
| Build REST/GraphQL APIs | **backend-developer** |
| Create web interfaces | **frontend-developer**, **react-specialist** |
| Develop complete features | **fullstack-developer** |
| Write Python code | **python-developer**, **python-pro** |
| Build TypeScript apps | **typescript-pro** |
| Design databases | **sql-pro** |
| Speed up builds | **build-engineer** |
| Manage dependencies | **dependency-manager** |
| Write documentation | **documentation-engineer**, **technical-writer** |
| Manage version control | **git-workflow-manager** |
| Refactor legacy code | **refactoring-specialist** |
| Build dev tools | **tooling-engineer** |
| Create MCP servers | **fastmcp-developer**, **mcp-developer** |
| Administer Minecraft servers | **minecraft-rvnk-admin** |
| Implement security | **security-engineer** |
| Write tests | **test-engineer** |
| Run test suites and analyze results | **test-orchestrator**, **test-orchestrator-enhanced** |
| Plan complex projects | **project-architect** |

### By Language/Framework

- **Python**: python-developer, python-pro
- **TypeScript/JavaScript**: typescript-pro, react-specialist, frontend-developer
- **C#/.NET**: csharp-developer
- **Java**: java-architect
- **SQL**: sql-pro
- **FastMCP**: fastmcp-developer
- **MCP Protocol**: mcp-developer

## Agent Selection Guidelines

### For RvnkDev MCP Server Development

#### Python Developer
Use when:
- Writing or refactoring Python code
- Adding type hints or docstrings
- Reducing code complexity (CC > 10)
- Implementing new functions or modules

#### FastMCP Framework Developer
Use when:
- Creating new MCP tools
- Integrating with providers
- Updating server configuration
- Working with FastMCP framework
- Configuring Bitwarden credential management
- Setting up VS Code MCP integration

#### Test Engineer
Use when:
- Writing unit or integration tests
- Validating test coverage
- Designing test scenarios
- Debugging test failures
- Working with test frameworks

#### Test Orchestrator
Use when:
- Running comprehensive test suites
- Analyzing test results and trends
- Detecting regressions
- Pre-deployment validation
- Cross-environment testing
- Generating test reports
- Working with RvnkDev FastMCP Server or RVNKQuests integration

#### Test Orchestrator (Enhanced)
Use when:
- Need full Claude skills integration (code analysis, data processing, AI-driven insights)
- Running multi-environment test orchestration (rvnkdev-local, rvnkquests-integration)
- Managing complex test workflows with Archon task automation
- Generating comprehensive reports with regression detection and trend analysis
- Coordinating cross-environment testing with intelligent recommendations
- Integrating with Archon MCP for automated task creation and tracking

#### Project Architect
Use when:
- Planning multi-phase implementations
- Creating feature specifications
- Defining validation criteria
- Structuring complex projects
- Working with Metamake framework

#### Security Engineer
Use when:
- Handling credentials or sensitive data
- Implementing production safety checks
- Validating security requirements
- Working with database queries

#### Git Workflow Manager
Use when:
- Creating commit messages
- Managing branches
- Preparing releases
- Reviewing git changes

#### Technical Writer
Use when:
- Adding or updating docstrings
- Writing inline comments
- Updating documentation
- Clarifying code intent

#### Minecraft Admin
Use when:
- Managing Minecraft server permissions (LuckPerms)
- Investigating grief or theft (CoreProtect)
- Configuring map markers (Dynmap)
- Executing console commands on servers
- Reading or modifying plugin configurations
- Troubleshooting plugin issues
- Administering player permissions and groups

## Conversion Notes

This agent system evolved from the original `agents.md` file into individual, focused agent definitions:

1. **Initial Phase**: Comprehensive agentic guidelines in single `agents.md` file
2. **Specialization**: Split into role-specific agents (python-developer, fastmcp-developer, test-engineer, etc.)
3. **Project Integration**: Created project-specific agents (fastmcp-developer, test-engineer, project-architect) with comprehensive patterns merged from copilot instruction files
4. **Expansion**: Added general development agents from core and developer experience categories

**Migration Status**:
- ✅ Core project agents created (fastmcp-developer, test-engineer, project-architect)
- ✅ Comprehensive patterns merged from copilot instructions
- ✅ General development agents catalog established
- 🔄 Future: Additional supplemental instruction files may be converted to agent format

---

**Last Updated**: October 21, 2025
