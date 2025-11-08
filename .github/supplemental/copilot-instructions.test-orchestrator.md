# GitHub Copilot: Test Orchestrator Instructions

**Integration guide for GitHub Copilot users working with RvnkDev MCP Server testing**

---

## Quick Start

When you need to test the RvnkDev FastMCP Server:

1. **Ask Copilot:**
   ```
   @copilot Run the test suite for RvnkDev FastMCP Server
   ```

2. **Copilot will:**
   - Navigate to the test suite directory
   - Execute the test runner
   - Collect results
   - Generate reports
   - Show you the summary

## Test Orchestrator Overview

The Test Orchestrator is a specialized testing system for:
- **RvnkDev FastMCP Server** - MCP protocol implementation
- **RVNKQuests Integration** - Real-world usage in VS Code
- **Cross-environment Validation** - Multiple platforms and installation methods

### Test Environments

| Environment | Purpose | Test Suites | Status |
|-------------|---------|-------------|--------|
| **rvnkdev_local** | Development validation | Core Tools, Provider, Security | ✅ Active |
| **rvnkquests_integration** | Real-world integration | Discovery, Execution, Workflow | 🔄 Planning |
| **Alternative Installs** | Platform testing | macOS, Linux, Claude Desktop | 📝 Placeholder |

## Common Test Commands

### Run All Tests

```copilot
@copilot Run comprehensive test suite for RvnkDev with regression analysis
```

**What it does:**
- Executes all test suites (RvnkDev local + integration)
- Generates JSON and Markdown reports
- Compares with historical baseline
- Alerts on regressions
- Saves results to `reports/` directory

**Expected output:**
```
Test Suites: 3 completed
Tests Total: 25 tests
Success Rate: 100% (all passed)
Regressions: None detected
```

### Run Local Tests Only

```copilot
@copilot Run local development tests for RvnkDev FastMCP Server
```

**Validates:**
- Core MCP tools (21 tools)
- Provider authentication (SparkedHost, MCSS)
- Security restrictions (production servers)

### Test Specific Component

```copilot
@copilot Test provider integration for RvnkDev
```

**Tests:**
- Credential management
- Provider authentication
- Provider operation execution
- Resource cleanup

### Show Test Results

```copilot
@copilot Show the latest test results and any regressions
```

**Displays:**
- Latest report summary
- Pass/fail breakdown
- Regression detection
- Performance metrics

## Test Report Schemas

### JSON Report Format

Located in: `reports/rvnkdev-YYYYMMDD-HHMMSS.json`

```json
{
  "report_id": "rvnkdev-20251012-151053",
  "project_name": "RvnkDev FastMCP Server",
  "project_version": "2.0.5",
  "generated_at": "2025-10-12T15:10:53",
  "test_suites": [
    {
      "name": "Core MCP Tools",
      "environment": "rvnkdev_local",
      "summary": {
        "total_tests": 1,
        "passed": 1,
        "success_rate": 100.0
      }
    }
  ]
}
```

### Markdown Report Format

Located in: `reports/rvnkdev-YYYYMMDD-HHMMSS.md`

Human-readable format with:
- Executive summary
- Per-suite results table
- Test case details
- Failure analysis
- Recommendations

## Understanding Test Results

### Success Criteria

**All Green (100% Success):**
```
✅ Core Tools: 1/1 passed
✅ Provider Integration: 4/4 passed
✅ Security: 4/4 passed
Overall: 25/25 tests passed (100%)
```

**Expected for v2.0.5:** 100% success rate across all suites

### Common Failures

| Failure | Cause | Solution |
|---------|-------|----------|
| BW_SESSION expired | Bitwarden session timeout | Run `bw unlock` |
| Provider auth failed | Invalid credentials | Check Bitwarden vault |
| Network timeout | Provider offline | Check API status |
| Import errors | Missing dependencies | Run `pip install -r requirements.txt` |

## Integration with Development Workflow

### Pre-Release Testing

Before releasing a new version:

```copilot
@copilot Validate RvnkDev v2.0.5 for production release with full diagnostics
```

**Checklist:**
- ✅ Run full test suite
- ✅ Compare with previous release
- ✅ Verify no regressions
- ✅ Check security rules
- ✅ Generate release report

### RVNKQuests Integration Testing

After deploying to RVNKQuests:

```copilot
@copilot Test MCP server integration in RVNKQuests environment
```

**Validates:**
- Server discoverable in VS Code
- All 19 tools executable
- Workflows functioning
- Error handling correct

### Regression Detection

To detect performance or functionality degradation:

```copilot
@copilot Analyze test history and identify any regressions in the last 10 runs
```

**Output:**
- Success rate trends
- Performance metrics
- Identified regressions
- Recommendations

## Test Infrastructure

### Test Files Location

```
rvnkdev-mcp-server/
├── metamake/projects/10-test-suite-tracking/
│   ├── test-suites/
│   │   ├── run_rvnkdev_tests.py          # Local tests
│   │   ├── run_rvnkquests_tests.py       # Integration tests
│   │   └── run_alternative_installation_tests.py
│   ├── test_report_schema.py             # Report generation
│   └── reports/
│       ├── *.json                        # JSON reports
│       ├── *.md                          # Markdown reports
│       └── response_history.json         # Historical data
```

### Test Report Schema

**Core Components:**
- `TestReport` - Complete report with metadata
- `TestSuite` - Grouped test cases
- `TestCase` - Individual test result
- `TestStatus` - PASS, FAIL, SKIP, ERROR
- `TestEnvironment` - Environment classification
- `ResponseTracker` - Historical tracking

## Troubleshooting

### "Tests won't start"

**Check:**
```copilot
@copilot Diagnose why tests are failing to start and suggest fixes
```

**Common issues:**
- Python not in PATH
- Working directory incorrect
- Dependencies missing
- Bitwarden session expired

### "Provider authentication failed"

**Check:**
```copilot
@copilot Debug provider authentication failures for RvnkDev
```

**Solutions:**
1. Verify Bitwarden unlock: `bw unlock`
2. Check credentials exist: `bw list items`
3. Test connectivity: `ping sparkedhost.com`

### "Tests hanging"

**Investigate:**
```copilot
@copilot Identify what's causing tests to hang and suggest solutions
```

**Check for:**
- Infinite loops in test logic
- Deadlocks in async operations
- Port conflicts
- Resource exhaustion

## Best Practices

### Running Tests Locally

1. **Prepare environment:**
   ```powershell
   # Unlock Bitwarden
   bw unlock

   # Set session
   $env:BW_SESSION = "your-session-key"

   # Verify Python
   python --version
   ```

2. **Run tests:**
   ```powershell
   cd metamake/projects/10-test-suite-tracking/test-suites
   python run_rvnkdev_tests.py
   ```

3. **Review results:**
   - Check console output for errors
   - Review JSON report in editor
   - Analyze Markdown report
   - Compare with historical data

### Analyzing Test Results

1. **Check Overall Success Rate:**
   - 100% = Release ready
   - 90-99% = Minor issues to fix
   - <90% = Critical issues found

2. **Identify Failures:**
   - Review error messages
   - Check stack traces
   - Look at test metadata

3. **Compare with Baseline:**
   - Use regression detection
   - Check historical trends
   - Identify performance changes

## Advanced Scenarios

### Custom Test Implementation

To add new tests:

```python
# In run_rvnkdev_tests.py or similar
async def run_custom_suite(self) -> TestSuite:
    suite = TestSuite(
        name="Custom Test Suite",
        environment=TestEnvironment.RVNKDEV_LOCAL
    )

    # Add test cases
    suite.test_cases.append(TestCase(
        name="test_name",
        status=TestStatus.PASS,
        duration_ms=100.0
    ))

    return suite
```

### Regression Detection Customization

```python
# Detect with custom threshold (default: 5%)
regressions = tracker.detect_regressions(threshold=3.0)

# Get trend data
trend_data = tracker.get_trend_data(last_n=20)
```

## Related Resources

- **Test Agent:** `.claude/agents/test-orchestrator.md`
- **Deployment Guide:** `shared/derek/repos/rvnkdev-mcp-server/docs/RVNKQUESTS_DEPLOYMENT_GUIDE.md`
- **Test Framework Docs:** `metamake/projects/10-test-suite-tracking/COPILOT-INSTRUCTIONS.md`
- **RVNKQuests CLAUDE.md:** Integration and archon workflow

## Support

For help with testing:

1. **Check documentation:**
   - This file for Copilot guidance
   - `test-orchestrator.md` agent for details
   - Deployment guide for context

2. **Ask Copilot:**
   ```copilot
   @copilot How do I [test task]? Show me examples and best practices.
   ```

3. **Review examples:**
   - `run_rvnkdev_tests.py` - Working implementation
   - `run_rvnkquests_tests.py` - Integration pattern
   - `test_report_schema.py` - Data structures

---

**Last Updated:** November 8, 2025
**Compatible with:** GitHub Copilot, Claude Code
**Test Framework:** Python 3.11+, Pytest, AsyncIO
