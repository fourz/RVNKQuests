# Test: Analyze Results

## Purpose

Analyze test results, compare reports, detect regressions, and generate trend analysis for the RvnkDev FastMCP Server and RVNKQuests integration testing.

## Available Analysis Options

### Comparison Modes

- **latest** — Compare latest run with previous run
- **baseline** — Compare with initial baseline (v2.0.0)
- **trend** — Show trend over last N runs (default: 10)
- **all** — Compare all metrics across all reports

### Analysis Types

- **success-rate** — Test success rate trends
- **performance** — Execution time analysis
- **regressions** — Identify failing tests
- **providers** — Provider-specific analysis
- **security** — Security test validation

### Report Selection

- **rvnkdev-local** — RvnkDev development environment
- **rvnkquests-integration** — RVNKQuests integration
- **all** — All available reports

### Output Format

- **table** — Formatted comparison table
- **chart** — ASCII chart of trends
- **csv** — Machine-readable CSV format
- **detailed** — Full analysis with explanations

---

## Usage Examples

### Example 1: Quick Regression Check

```
Show regressions detected in latest test run
```

**What it does:**
- Compares latest run with previous run
- Highlights any success rate drops
- Shows which tests started failing
- Suggests remediation steps

**Expected output:**
```
Regression Analysis
═══════════════════════════════════════════════════════
Report Comparison: rvnkdev-20251108-143022 vs rvnkdev-20251108-120000

Status: ✅ NO REGRESSIONS (threshold: 5%)
Success Rate: 100% → 100% (change: 0%)

Detailed Results:
- Core Tools: 21/21 ✅ (no change)
- Provider Integration: 6/6 ✅ (no change)
- Security: 4/4 ✅ (no change)

Performance Impact: Neutral
```

### Example 2: Trend Analysis Over 10 Runs

```
Show trend analysis for rvnkdev-local with table format and performance metrics
```

**What it does:**
- Analyzes last 10 test runs
- Shows success rate trend
- Displays performance metrics
- Identifies patterns or issues
- Provides recommendations

**Expected output:**
```
Test Success Rate Trend (Last 10 Runs)
═══════════════════════════════════════════════════════
Date              Success Rate    Avg Duration    Status
───────────────────────────────────────────────────────
2025-11-08 14:30    100%            125.4ms        ✅
2025-11-08 12:00    100%            124.1ms        ✅
2025-11-07 18:30    100%            123.8ms        ✅
2025-11-07 14:15    100%            125.2ms        ✅
2025-11-07 10:45    100%            124.5ms        ✅
───────────────────────────────────────────────────────
Average:            100%            124.6ms        Stable
Trend:              Stable          Stable         ✅
```

### Example 3: Provider-Specific Analysis

```
Analyze provider integration test results with detailed format
```

**What it does:**
- Breaks down results by provider
- Shows authentication status
- Displays provider-specific metrics
- Identifies any provider issues

**Expected output:**
```
Provider Integration Analysis
═══════════════════════════════════════════════════════

SparkedHost Provider
──────────────────────────────────────────────────────
Status: ✅ Online and Ready
Authentication: ✅ PASS
Server Access: ✅ Test Server (b2bc4d7e)
Operations Tested: 5/5 ✅
  - List Servers: ✅
  - Server Status: ✅
  - Get Console Output: ✅
  - File Operations: ✅
  - Bidirectional Moves: ✅

MCSS Provider
──────────────────────────────────────────────────────
Status: ✅ Online and Ready
Authentication: ✅ PASS (Status: 200)
Server Access: ✅ Dev Server (1eb313b1-...)
Operations Tested: 6/6 ✅
  - List Servers: ✅
  - Server Status: ✅
  - Get Console Output: ✅
  - File Operations: ✅
  - Database Operations: ✅
  - Provider Initialization: ✅

Overall: ✅ All providers operational
```

### Example 4: Performance Comparison

```
Compare performance metrics across all reports with chart format
```

**What it does:**
- Shows execution time trends
- Identifies performance regressions
- Displays slowest tests
- Suggests optimization opportunities

**Expected output**:
```
Performance Metrics Over Time
═══════════════════════════════════════════════════════

Average Test Duration (ms)
┌─────────────────────────────────────────────────────┐
│                                                   ▁ │
│           ▃▂▂▃▂▂▃▂▂▃▂▂▃▂▂▃▂▂▃▂▂▃▂▂▃▂▂▃▂▂▃   │
│      ▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁   │
└─────────────────────────────────────────────────────┘
  120  122  124  126  128  130  132

Slowest Tests:
1. provider_initialization (2,843 ms)
2. get_console_output_50_lines (987 ms)
3. list_files_recursive (456 ms)

Performance Trend: Stable ✅
```

### Example 5: Security Test Validation

```
Analyze security tests with detailed format showing all results
```

**What it does:**
- Validates all security tests passed
- Shows production server restrictions
- Confirms read-only enforcement
- Lists all dangerous operations blocked

**Expected output**:
```
Security Test Analysis
═══════════════════════════════════════════════════════

Production Server Restrictions
──────────────────────────────────────────────────────
Server ID: 140324c4 (Production)
Enforcement Level: STRICT (Read-only)

Safe Operations: ✅ All Allowed
  [✅] server_status - Read-only operation
  [✅] get_console_output - Read-only operation
  [✅] list_files - Read-only operation
  [✅] read_file - Read-only operation

Dangerous Operations: ✅ All Blocked
  [✅] start_server - BLOCKED
  [✅] stop_server - BLOCKED
  [✅] restart_server - BLOCKED
  [✅] send_console_command - BLOCKED
  [✅] write_file - BLOCKED
  [✅] delete_file - BLOCKED

Overall Security Status: ✅ COMPLIANT
```

---

## Technical Details

### Analysis Engine

**Location**: `../../rvnkdev-mcp-server/metamake/projects/10-test-suite-tracking/test-suites/test_report_schema.py`

**Components**:
- `TestReport` — Complete test execution report with metadata
- `TestSuite` — Grouped test cases with environment classification
- `TestCase` — Individual test result with status and metrics
- `ResponseTracker` — Historical tracking with regression detection
- `RegressionDetector` — Automatic regression detection (5% threshold)
- `TrendAnalyzer` — Performance trend analysis over time

### Report Sources

**JSON Reports**:
- Location: `../../rvnkdev-mcp-server/metamake/projects/10-test-suite-tracking/reports/*.json`
- Format: Machine-readable with full metadata
- Retention: All reports kept for historical analysis

**Markdown Reports**:
- Location: `../../rvnkdev-mcp-server/metamake/projects/10-test-suite-tracking/reports/*.md`
- Format: Human-readable with emoji status indicators (✅ ❌)
- Use: Documentation and sharing

**Response History**:
- Location: `../../rvnkdev-mcp-server/metamake/projects/10-test-suite-tracking/reports/response_history.json`
- Format: Compressed historical data with baseline tracking
- Purpose: Trend analysis and regression detection

### Regression Threshold

**Default**: 5% drop in success rate
- Threshold can be customized
- Automatic detection on every run
- Historical baseline: Latest 10 runs

---

## Output Examples

### JSON Export

```json
{
  "analysis_type": "regression_detection",
  "timestamp": "2025-11-08T14:30:22",
  "current_report": "rvnkdev-20251108-143022",
  "previous_report": "rvnkdev-20251108-120000",
  "regression_detected": false,
  "success_rate_change": 0.0,
  "details": {
    "core_tools": {
      "previous": 21,
      "current": 21,
      "passed": 21
    },
    "providers": {
      "previous": 6,
      "current": 6,
      "passed": 6
    }
  }
}
```

### CSV Format

```
Date,Suite,Total,Passed,Success_Rate,Duration_ms,Status
2025-11-08T14:30,Core Tools,21,21,100.0,125.4,PASS
2025-11-08T14:30,Providers,6,6,100.0,456.2,PASS
2025-11-08T14:30,Security,4,4,100.0,89.1,PASS
2025-11-08T12:00,Core Tools,21,21,100.0,124.1,PASS
```

---

## Common Use Cases

### Use Case 1: Pre-Release Validation

```
Compare latest run with baseline version v2.0.0
```

**Why**: Ensure release hasn't caused regressions
**Output**: Detailed comparison showing all changes
**Action**: If regressions found, fix before release

### Use Case 2: Continuous Monitoring

```
Show trend analysis with performance metrics and regressions
```

**Why**: Monitor test health over time
**Output**: Trend chart and regression alerts
**Action**: Investigate any performance degradation

### Use Case 3: Performance Optimization

```
Analyze performance metrics with detailed slowest tests list
```

**Why**: Identify optimization opportunities
**Output**: Slowest tests and performance trends
**Action**: Profile and optimize slow tests

### Use Case 4: Provider Health Check

```
Analyze provider integration results with detailed format
```

**Why**: Verify all providers are functioning
**Output**: Per-provider status and operation success
**Action**: Alert if provider has issues

---

## Troubleshooting

### Issue: No Previous Report

**Symptom**: "Cannot compare - no previous report found"

**Solution**:
1. Run test suite first: `/test-run-suite rvnkdev-local all-suites`
2. Run again to generate comparison baseline
3. Future runs will have comparison data

### Issue: Incomplete Historical Data

**Symptom**: "Insufficient data for trend analysis" with < 10 runs

**Solution**:
1. Run tests multiple times to build history
2. Use 3-5 runs minimum for meaningful trends
3. Response history builds over time

### Issue: Invalid Report Format

**Symptom**: "Cannot parse report - invalid format"

**Solution**:
1. Verify report exists: `ls reports/*.json`
2. Check file integrity: `cat reports/latest.json | python -m json.tool`
3. Regenerate reports: `/test-run-suite rvnkdev-local all-suites`

---

## Related Commands

- **Test: Run Comprehensive Suite** — Execute test suites
- **Test: Show Last Report** — Display latest results
- **Archon: Track Test Results** — Persist results to Archon

---

**Last Updated**: December 6, 2025
**Agent**: Test Orchestrator (Enhanced)
**Version**: 2.0

For comprehensive documentation, see:
- **Test Orchestrator Agent**: `.claude/agents/test-orchestrator-enhanced.md`
- **Test Suite Tracking**: `../../rvnkdev-mcp-server/metamake/projects/10-test-suite-tracking/COPILOT-INSTRUCTIONS.md`
- **Deployment Guide**: `../../rvnkdev-mcp-server/docs/RVNKQUESTS_DEPLOYMENT_GUIDE.md`
