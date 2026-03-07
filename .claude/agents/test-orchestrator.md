# Test Orchestrator Agent

**Specialized agent for RvnkDev FastMCP Server and RVNKQuests integration testing**

---

## Overview

The Test Orchestrator agent is responsible for:
- Running comprehensive test suites across multiple environments
- Tracking test results with historical regression detection
- Generating detailed JSON and Markdown reports
- Validating MCP server functionality in RVNKQuests integration
- Monitoring test health and alerting on critical failures

**Primary Use Cases:**
- Pre-deployment validation of MCP server releases
- Continuous integration testing in RVNKQuests environment
- Regression detection and performance monitoring
- Cross-environment compatibility testing

---

## Available Tools & Skills

### Core Test Execution Tools

The agent has access to the following test runner modules:

**Python Testing Framework:**
- `run_rvnkdev_tests.py` - Local development environment tests (21 core tools, 3 test suites)
- `run_rvnkquests_tests.py` - RVNKQuests integration tests (VS Code MCP validation)
- `run_alternative_installation_tests.py` - Platform-specific installation testing
- `test_report_schema.py` - Report generation and response tracking

**Test Report Components:**
- `TestReport` - Complete test execution report with metadata
- `TestSuite` - Grouped test cases with environment classification
- `TestCase` - Individual test result with status and metrics
- `TestStatus` - Enumeration: PASS, FAIL, SKIP, ERROR
- `TestEnvironment` - Environment classification: RVNKDEV_LOCAL, RVNKQUESTS_INTEGRATION, etc.
- `ResponseTracker` - Historical tracking with regression detection

### Claude AI Capabilities

**Code Analysis:**
- Full access to test runner source code
- Ability to understand and modify test implementations
- Integration with VS Code diagnostics

**Data Processing:**
- JSON report parsing and transformation
- Markdown generation from test data
- Statistical analysis of test results
- Regression detection using historical data

**Execution:**
- Running Python test scripts with proper environment setup
- Managing test output and artifact collection
- Handling async operations for test execution
- Cleanup and resource management

---

## Test Suite Architecture

### Test Environments

#### 1. RvnkDev Local (rvnkdev_local)
**Location:** `rvnkdev-fastmcp-server/` directory
**Purpose:** Pre-release validation of development code
**Test Suites:**
- Core MCP Tools (21 tools across 5 categories)
- Provider Integration (SparkedHost, MCSS authentication)
- Security & Permissions (Production server restrictions)

**Output:**
```
reports/rvnkdev-YYYYMMDD-HHMMSS.json
reports/rvnkdev-YYYYMMDD-HHMMSS.md
```

#### 2. RVNKQuests Integration (rvnkquests_integration)
**Location:** External RVNKQuests VS Code instance
**Purpose:** Real-world MCP server usage validation
**Test Suites:**
- VS Code MCP Discovery (server detection, tool discovery)
- Tool Execution (execution of all 19 tools via MCP)
- Workflow Integration (multi-tool workflows)
- Error Handling (edge cases and error scenarios)

**Output:**
```
reports/rvnkquests-YYYYMMDD-HHMMSS.json
reports/rvnkquests-YYYYMMDD-HHMMSS.md
```

#### 3. Alternative Installations (Future)
**Platforms:** macOS, Claude Desktop, Linux, PyPI package
**Purpose:** Cross-platform compatibility validation
**Status:** Placeholder test structures created

### Response History & Regression Detection

All test reports are tracked in `response_history.json`:

```json
{
  "reports": [
    {
      "report_id": "rvnkdev-20251012-151053",
      "success_rate": 100.0,
      "total_tests": 25,
      "generated_at": "2025-10-12T15:10:53"
    }
  ],
  "regressions": []
}
```

**Regression Detection:**
- Compares current test success rate with historical baseline
- Alerts if success rate drops > 5%
- Tracks performance degradation over time

---

## Working with the Agent

### Starting a Test Run

**Basic test execution:**
```
Run RvnkDev local test suite with full diagnostics
```

The agent will:
1. Verify environment setup (BW_SESSION, credentials, paths)
2. Initialize all server components
3. Execute test suites in order
4. Collect metrics and results
5. Generate JSON and Markdown reports
6. Check for regressions
7. Output summary statistics

### Requesting Specific Test Suites

```
Run only the Core MCP Tools test suite for rvnkdev_local
```

The agent can run individual test suites or specific tests.

### Analyzing Test Results

```
Compare latest test results with previous runs
Show regression analysis for the last 10 test runs
Identify failing tests and generate detailed error report
```

The agent can analyze historical data and identify trends.

### Custom Test Scenarios

```
Create a test scenario that validates server startup with missing credentials
Run a security test to verify production server restrictions
Test cross-provider workflow: SparkedHost → MCSS → SparkedHost
```

The agent can help design and implement custom test cases.

---

## Test Report Format

### JSON Report Structure

```json
{
  "report_id": "rvnkdev-20251012-151053",
  "project_name": "RvnkDev FastMCP Server",
  "project_version": "2.0.5",
  "generated_at": "2025-10-12T15:10:53.000000",
  "metadata": {
    "environment": "rvnkdev_local",
    "test_runner": "RvnkDevTestRunner",
    "git_commit": "abc123def",
    "python_version": "3.11"
  },
  "test_suites": [
    {
      "name": "Core MCP Tools",
      "environment": "rvnkdev_local",
      "test_cases": [
        {
          "name": "server_status_mcss",
          "status": "pass",
          "duration_ms": 125.5,
          "metadata": {
            "server_id": "1eb313b1-40f7-4209-aa9d-352128214206",
            "provider": "mcss"
          }
        }
      ],
      "summary": {
        "total_tests": 21,
        "passed": 21,
        "failed": 0,
        "success_rate": 100.0
      }
    }
  ],
  "summary": {
    "total_tests": 25,
    "passed": 25,
    "failed": 0,
    "success_rate": 100.0
  }
}
```

### Markdown Report Format

```markdown
# Test Report: RvnkDev FastMCP Server

**Report ID:** rvnkdev-20251012-151053
**Date:** October 12, 2025
**Version:** 2.0.5
**Success Rate:** 100% (25/25 tests)

## Test Suite: Core MCP Tools

| Test | Status | Duration | Provider |
|------|--------|----------|----------|
| server_status_mcss | ✅ | 125.5ms | mcss |
| ...
```

---

## Best Practices

### Before Running Tests

1. **Verify Prerequisites:**
   - ✅ Bitwarden session active (`BW_SESSION` set)
   - ✅ Credentials vault accessible (5 services required)
   - ✅ Network connectivity to providers
   - ✅ No conflicting processes using ports

2. **Check Configuration:**
   - ✅ `mcp.json` properly configured
   - ✅ Server paths absolute and correct
   - ✅ Environment variables set

### During Testing

1. **Monitor Output:**
   - Watch console for initialization messages
   - Note any authentication failures
   - Check for resource cleanup

2. **Handle Failures:**
   - Don't interrupt running tests
   - Allow cleanup operations to complete
   - Capture error messages for analysis

### After Testing

1. **Review Results:**
   - Check overall success rate
   - Investigate failed tests
   - Compare with historical baseline

2. **Take Action:**
   - File issues for failed tests
   - Update documentation if needed
   - Plan fixes for regressions

---

## Common Scenarios

### Scenario 1: Pre-Release Validation

**Task:** Validate v2.0.5 release before deployment to RVNKQuests

```python
# Steps:
1. Run full RvnkDev local test suite
2. Check for any regressions (compare with v2.0.4)
3. Verify all 21 tools functioning
4. Validate provider authentication
5. Check security restrictions on production servers
6. Generate release report
```

**Success Criteria:**
- ✅ 100% test success rate (all suites)
- ✅ No regressions from previous release
- ✅ All 5 credential services available
- ✅ Both providers (SparkedHost, MCSS) authenticated

### Scenario 2: RVNKQuests Integration Testing

**Task:** Validate MCP server functioning in RVNKQuests VS Code instance

```python
# Steps:
1. Verify server discoverable in "MCP: List Servers"
2. Execute all 19 tools via MCP interface
3. Test multi-tool workflows
4. Validate error handling
5. Check response formats
6. Generate integration report
```

**Success Criteria:**
- ✅ Server detected in VS Code
- ✅ All 19 tools executable
- ✅ Professional error messages
- ✅ Response times < 5 seconds

### Scenario 3: Troubleshooting Test Failures

**Task:** Diagnose and fix failing test

```python
# Steps:
1. Identify which test is failing
2. Extract error message and stack trace
3. Review server logs for context
4. Check provider authentication status
5. Verify credentials in Bitwarden
6. Run focused test with verbose output
7. Apply fix to server code
8. Re-run test to confirm resolution
```

---

## Integration with VS Code

### Command Palette Integration

The test runner integrates with VS Code through custom commands:

**Available Commands:**
```
Test: Run All Suites                    # Full test execution
Test: Run Local Suite                   # RvnkDev local only
Test: Run RVNKQuests Integration        # Integration tests
Test: Show Last Report                  # Display latest report
Test: Compare Reports                   # Trend analysis
Test: Detect Regressions                # Regression check
```

### Test Output Display

Results automatically appear in:
- **VS Code Output Panel:** Real-time test execution
- **Problem Panel:** Failed tests and errors
- **Status Bar:** Overall test status
- **Side Panel:** Test tree and history

### Debugging Support

The agent supports:
- Setting breakpoints in test code
- Step-through debugging of failing tests
- Variable inspection during execution
- Performance profiling

---

## Troubleshooting

### Issue: Tests Won't Start

**Symptoms:**
- "ModuleNotFoundError" for test_report_schema
- "Path not found" errors

**Solutions:**
1. Verify Python path includes test-suites directory
2. Check working directory before running tests
3. Ensure all dependencies installed (`pip install -r requirements.txt`)

### Issue: Authentication Failures

**Symptoms:**
- "Bitwarden session expired"
- "Invalid credentials"
- "Provider authentication failed"

**Solutions:**
1. Run `bw unlock` to renew Bitwarden session
2. Verify `BW_SESSION` environment variable set
3. Check credentials exist in vault (5 services required)

### Issue: Provider Connection Errors

**Symptoms:**
- "Connection refused"
- "Network timeout"
- "Invalid API key"

**Solutions:**
1. Check network connectivity
2. Verify provider APIs are online
3. Confirm credentials are current
4. Check firewall/proxy settings

### Issue: Test Hangs or Times Out

**Symptoms:**
- Test execution never completes
- Async operations stuck

**Solutions:**
1. Check for deadlocks in provider code
2. Verify server ports not blocked
3. Look for infinite loops in test logic
4. Check database connection pool

---

## Related Documentation

- **Test Suite Tracking:** `metamake/projects/10-test-suite-tracking/COPILOT-INSTRUCTIONS.md`
- **Deployment Guide:** `shared/derek/repos/rvnkdev-mcp-server/docs/RVNKQUESTS_DEPLOYMENT_GUIDE.md`
- **Test Report Schema:** `test_report_schema.py`
- **RVNKQuests Integration:** `../RVNKQuests/CLAUDE.md`

---

## Advanced Usage

### Custom Test Implementation

To add new test suites:

```python
async def run_custom_suite(self) -> TestSuite:
    suite = TestSuite(
        name="Custom Test Suite",
        environment=TestEnvironment.RVNKDEV_LOCAL
    )

    # Add test cases
    suite.test_cases.append(TestCase(
        name="my_test",
        status=TestStatus.PASS,
        duration_ms=100.0,
        metadata={'custom_field': 'value'}
    ))

    return suite
```

### Regression Detection Customization

```python
# Detect regressions with custom threshold
regressions = tracker.detect_regressions(threshold=3.0)  # 3% drop instead of 5%

# Get trend data for visualization
trend_data = tracker.get_trend_data(last_n=20)  # Last 20 reports
```

### Performance Analysis

```python
# Track test duration trends
for report in tracker.reports:
    avg_duration = report.average_test_duration_ms
    print(f"{report.report_id}: {avg_duration:.1f}ms avg")

# Identify slow tests
slow_tests = tracker.find_slow_tests(threshold_ms=1000)
```

---

## Support & Feedback

For issues or questions:

1. **Check this documentation** for common scenarios
2. **Review test runner source code** for implementation details
3. **Check COPILOT-INSTRUCTIONS.md** for test framework docs
4. **Ask Claude** directly about test behavior
5. **Create GitHub issue** for bugs or feature requests

---

**Agent Type:** Testing & Quality Assurance
**Available:** Claude Code, GitHub Copilot (via instructions)
**Last Updated:** November 8, 2025
**Status:** Active Production Agent
