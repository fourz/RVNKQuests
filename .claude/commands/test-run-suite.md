# Test: Run Comprehensive Suite

## Purpose

Execute the comprehensive test suite for RvnkDev FastMCP Server and RVNKQuests integration, with automated report generation and regression detection.

## Available Options

### Environment Selection

- **rvnkdev-local** — RvnkDev local development environment (21 core tools, 3 test suites)
- **rvnkquests-integration** — RVNKQuests VS Code MCP integration testing
- **all-environments** — Run all available test suites

### Test Suites (RvnkDev Local)

- **core-tools** — Core MCP tools validation (21 tools across 5 categories)
- **provider-integration** — SparkedHost and MCSS provider authentication
- **security** — Production server security restrictions
- **all-suites** — Run all test suites sequentially

### RVNKQuests Integration Tests

- **discovery** — VS Code MCP server detection and listing
- **execution** — Tool execution via MCP interface
- **workflows** — Multi-tool workflow validation
- **all-tests** — Run all integration tests

### Report Options

- **json** — Generate JSON report only
- **markdown** — Generate Markdown report only
- **both** — Generate both JSON and Markdown reports (default)
- **compare** — Compare with previous run (regression analysis)

### Output Options

- **verbose** — Show detailed test output
- **summary** — Show summary only
- **quiet** — Minimal output
- **save** — Save results to tracking database

---

## Usage Examples

### Example 1: Quick Local Validation

```
Run RvnkDev local test suite with core tools validation
```

**What it does:**
- Runs the core tools test suite only
- Generates both JSON and Markdown reports
- Skips regression analysis
- Outputs summary to console

**Expected output:**
- Report ID: `rvnkdev-YYYYMMDD-HHMMSS`
- Success rate: ~100% (21/21 tools)
- Duration: ~2-3 minutes

### Example 2: Full Pre-Deployment Validation

```
Run all test suites for rvnkdev-local with verbose output and regression detection
```

**What it does:**
- Executes all three test suites (core tools, providers, security)
- Shows detailed output for each test
- Compares with previous run (detects regressions > 5%)
- Generates comprehensive reports
- Tracks in response history

**Expected output:**
- Total tests: 25
- Success rate: 100%
- No regressions detected
- Reports saved to: `reports/rvnkdev-YYYYMMDD-HHMMSS.*`

### Example 3: RVNKQuests Integration Testing

```
Run rvnkquests-integration with discovery and execution tests
```

**What it does:**
- Validates VS Code MCP server detection
- Tests tool execution via MCP interface
- Verifies all 19 tools are available
- Generates integration report

**Expected output:**
- Server discovered: ✅
- Tools available: 19/19
- Execution success rate: 100%

### Example 4: Focused Test with Comparison

```
Run rvnkdev-local core-tools with compare and verbose options
```

**What it does:**
- Runs core tools suite
- Shows detailed test output
- Compares current vs. previous results
- Highlights any regressions
- Displays trend data (last 10 runs)

**Expected output:**
- Success rate: 100%
- Trend: Stable (no regressions)
- Performance: Consistent
- Comparison chart showing last 10 runs

---

## Technical Details

### Test Runner Integration

The command integrates with the test infrastructure:

**Location**: `../../rvnkdev-mcp-server/metamake/projects/10-test-suite-tracking/test-suites/`

**Runners**:
- `run_rvnkdev_tests.py` — Local development tests (21 core tools, 3 test suites)
- `run_rvnkquests_tests.py` — Integration tests (VS Code MCP validation)
- `run_alternative_installation_tests.py` — Platform-specific installation testing
- `test_report_schema.py` — Report generation and response tracking

### Report Generation

**JSON Format**:
```json
{
  "report_id": "rvnkdev-20251108-143022",
  "project_name": "RvnkDev FastMCP Server",
  "project_version": "2.0.5",
  "generated_at": "2025-11-08T14:30:22",
  "metadata": {
    "environment": "rvnkdev_local",
    "test_runner": "RvnkDevTestRunner",
    "git_commit": "abc123def"
  },
  "test_suites": [...],
  "summary": {
    "total_tests": 25,
    "passed": 25,
    "success_rate": 100.0
  }
}
```

**Markdown Format**:
- Human-readable test results
- Status emoji indicators (✅ pass, ❌ fail)
- Test duration tracking
- Performance summary

### Regression Detection

**Automatic Detection**:
- Compares current success rate with historical baseline
- Alerts if drop > 5% detected
- Tracks performance degradation over time
- Maintains response history in `response_history.json`

**Threshold Customization**:
- Default: 5% regression threshold
- Can adjust with custom commands

---

## Quality Gates

### Success Criteria

- ✅ **Core Tools**: 100% success rate (21/21)
- ✅ **Provider Integration**: 100% authentication
- ✅ **Security**: 100% production restrictions enforced
- ✅ **RVNKQuests Integration**: All 19 tools executable
- ✅ **Response Times**: < 5 seconds per operation

### Performance Benchmarks

- **Server Status**: < 200ms
- **File Listing**: < 500ms
- **Console Output**: < 1000ms
- **Provider Init**: 2-3 seconds (one-time)

---

## Troubleshooting

### Test Won't Start

**Issue**: ModuleNotFoundError or Path errors

**Solution**:
1. Verify Python environment: `python --version` (3.11+)
2. Check working directory: Should be project root
3. Install dependencies: `pip install -r requirements.txt`
4. Verify test files exist: `ls metamake/projects/10-test-suite-tracking/test-suites/`

### Authentication Failures

**Issue**: Bitwarden session expired

**Solution**:
1. Unlock Bitwarden: `bw unlock`
2. Set session: `$env:BW_SESSION = "..."`
3. Verify credentials: `bw list items --search sparkedhost`
4. Retry test execution

### Provider Connection Errors

**Issue**: "Connection refused" or timeout

**Solution**:
1. Check network connectivity: `ping sparkedhost.com`
2. Verify API endpoints: Visit provider dashboard
3. Check credentials are current: `bw get item sparkedhost`
4. Look for firewall/proxy issues: `curl https://api.sparkedhost.com`

---

## Related Commands

- **Test: Show Last Report** — Display latest test results
- **Test: Compare Reports** — Analyze trends and regressions
- **Test: Detect Regressions** — Check for performance drops
- **Archon: Check Task Status** — Verify test automation tasks

---

## Environment Requirements

- Python 3.11+
- Bitwarden CLI configured
- Network access to provider APIs
- 5 credential services in vault:
  - sparkedhost (API)
  - sparkedhost_sftp (SFTP)
  - mcss (API)
  - mcss_sftp (SFTP)
  - mysql (Database)

---

**Last Updated**: December 6, 2025
**Agent**: Test Orchestrator (Enhanced)
**Version**: 2.0

For comprehensive documentation, see:
- **Test Orchestrator Agent**: `.claude/agents/test-orchestrator-enhanced.md`
- **Test Suite Tracking**: `../../rvnkdev-mcp-server/metamake/projects/10-test-suite-tracking/COPILOT-INSTRUCTIONS.md`
- **Deployment Guide**: `../../rvnkdev-mcp-server/docs/RVNKQUESTS_DEPLOYMENT_GUIDE.md`
