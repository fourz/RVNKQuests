# Run RvnkDev Local Tests

Execute tests for the local RvnkDev FastMCP Server development environment.

## What This Does

Validates core server functionality in development:
- **Core MCP Tools** (21 tools across 5 categories)
  - Server Management: start, stop, restart, status (4 tools)
  - Console Operations: output, command execution (2 tools)
  - File Operations: list, read, write, delete, batch (5 tools)
  - Database Operations: list, backup, restore, query (4 tools)
  - Diagnostics & Discovery: 1 tool

- **Provider Integration** (SparkedHost, MCSS)
  - Credential availability
  - Provider authentication
  - Provider operation execution

- **Security & Permissions**
  - Production server classification
  - Operation restrictions (dangerous ops blocked)
  - Development server permissions
  - Permission enforcement

## Prerequisites

- ✅ Project structure: `rvnkdev-fastmcp-server/` directory accessible
- ✅ BW_SESSION environment variable set
- ✅ Bitwarden credentials available (sparkedhost, mcss)
- ✅ Network connectivity

## Usage

```
Test: Run Local Suite
```

## Expected Results

**Success Metrics:**
- 21 core tools initialize
- 3 provider credentials verified
- All security rules enforced
- Overall success rate: 100% (25/25 tests)

**Output:**
```
Core MCP Tools: 1/1 passed
Provider Integration: 4/4 passed
Security & Permissions: 4/4 passed
Total: 25/25 tests passed (100%)
```

## Environment Details

**Test Server IDs:**
- MCSS Dev Server: `1eb313b1-40f7-4209-aa9d-352128214206`
- SparkedHost Dev: `b2bc4d7e`
- Production Server (read-only test): `140324c4`

**Providers:**
- SparkedHost: Cloud hosting provider
- MCSS: Minecraft Server Hosting Service

## Test Infrastructure Location

- **Test Suite**: `../../rvnkdev-mcp-server/metamake/projects/10-test-suite-tracking/test-suites/`
- **Runner**: `run_rvnkdev_tests.py`
- **Reports**: `../../rvnkdev-mcp-server/metamake/projects/10-test-suite-tracking/reports/`

---

**Last Updated**: December 6, 2025
**Agent**: Test Orchestrator (Enhanced)
**Version**: 2.0

For comprehensive documentation, see:
- **Test Orchestrator Agent**: `.claude/agents/test-orchestrator-enhanced.md`
- **Test Suite Tracking**: `../../rvnkdev-mcp-server/metamake/projects/10-test-suite-tracking/COPILOT-INSTRUCTIONS.md`
- **Deployment Guide**: `../../rvnkdev-mcp-server/docs/RVNKQUESTS_DEPLOYMENT_GUIDE.md`

## Related Commands

- **Test: Run All Suites** - Run both local and integration tests
- **Test: Run RVNKQuests Integration** - Run integration tests
- **Test: Detect Regressions** - Check for failures vs. baseline
