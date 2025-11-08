# Show Latest Test Report

Display the most recent test report in human-readable format.

## What This Does

Reads and displays the latest test report that was generated, showing:
- Test execution summary (pass/fail counts, success rate)
- Per-suite results and metrics
- Test case details (name, status, duration)
- Error messages for failed tests
- Metadata and environment information

## Prerequisites

- ✅ At least one test run completed (report files exist)
- ✅ Report files in `reports/` directory

## Usage

```
Test: Show Last Report
```

## Output

Displays:
- Report ID and timestamp
- Overall success rate
- Per-suite summary table
- Individual test results
- Regression status (if applicable)

## Example Output

```
TEST REPORT: rvnkdev-20251012-151053
Generated: 2025-10-12 15:10:53
Success Rate: 100% (25/25 tests)

SUITE: Core MCP Tools
Status: PASS (1/1 tests)
Duration: 245ms

SUITE: Provider Integration
Status: PASS (4/4 tests)
Duration: 1250ms

SUITE: Security & Permissions
Status: PASS (4/4 tests)
Duration: 185ms

TOTAL: 25/25 tests passed
```

## Viewing Reports

**Latest JSON Report:**
```
reports/rvnkdev-LATEST.json
```

**Latest Markdown Report:**
```
reports/rvnkdev-LATEST.md
```

**Historical Tracking:**
```
reports/response_history.json
```

## Related Commands

- **Test: Run All Suites** - Generate new test report
- **Test: Compare Reports** - View trend analysis
- **Test: Detect Regressions** - Check for failures
