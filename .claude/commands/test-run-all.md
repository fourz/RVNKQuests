# Run All Test Suites

Execute comprehensive test validation across RvnkDev FastMCP Server development and RVNKQuests integration environments.

## What This Does

Executes the complete test suite pipeline:
1. **RvnkDev Local Tests** - Validates core server functionality (21 tools, 3 test suites)
2. **Historical Comparison** - Detects regressions vs. previous runs
3. **Report Generation** - Creates JSON and Markdown reports
4. **Summary Display** - Shows aggregated test results

## Prerequisites

- ✅ Python 3.11+ installed and in PATH
- ✅ BW_SESSION environment variable set (Bitwarden)
- ✅ 5 credential services available in Bitwarden vault
- ✅ Network connectivity to SparkedHost and MCSS APIs
- ✅ pip package manager available (standard with Python)

## Usage

```
Test: Run All Suites
```

This will:
- Run all test suites sequentially
- Collect results in JSON format
- Generate human-readable Markdown reports
- Track historical data for regression detection
- Display summary statistics

## Output

**Report Files:**
- `reports/rvnkdev-YYYYMMDD-HHMMSS.json` - Structured test data
- `reports/rvnkdev-YYYYMMDD-HHMMSS.md` - Human-readable report
- `reports/response_history.json` - Historical tracking (auto-updated)

**Console Output:**
- Test execution progress
- Pass/fail counts per suite
- Overall success rate
- Regression warnings (if applicable)

## Expected Results

**Success Criteria:**
- All 25 core tests pass (100% success rate)
- No regressions from previous baseline
- All provider credentials authenticated
- No encoding or async errors

**Failure Handling:**
- Failed tests are recorded with error messages
- Stack traces included for debugging
- Regression alerts displayed if success rate drops > 5%

## Troubleshooting

**Tests Won't Start:**
- Check BW_SESSION environment variable: `$env:BW_SESSION`
- Verify Python path: `python --version`
- Confirm working directory: `pwd`

**Credential Errors:**
- Unlock Bitwarden: `bw unlock`
- Verify vault access: `bw list items`
- Check 5 required services present

**Provider Connection Issues:**
- Verify network connectivity: `ping sparkedhost.com`
- Check API endpoints responding
- Review firewall/proxy settings

## Related Commands

- **Test: Run Local Suite** - Run only RvnkDev local tests
- **Test: Run RVNKQuests Integration** - Run only integration tests
- **Test: Show Last Report** - Display latest test report
- **Test: Compare Reports** - Trend analysis
- **Test: Detect Regressions** - Regression check
