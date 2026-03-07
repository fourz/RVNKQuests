# Test Commands Quick Reference

**Quick guide for using test commands in Claude Code**

---

## Available Commands

### 1. Test: Run Comprehensive Suite

**Full Command**:
```
/test-run-suite [environment] [test-suites] [options]
```

**Quick Examples**:

```bash
# Run all local tests with details and regression comparison
/test-run-suite rvnkdev-local all-suites verbose compare

# Quick check - core tools only
/test-run-suite rvnkdev-local core-tools

# RVNKQuests integration testing
/test-run-suite rvnkquests-integration all-tests verbose

# Silent mode with report saving
/test-run-suite rvnkdev-local all-suites quiet save

# Provider integration only
/test-run-suite rvnkdev-local provider-integration verbose

# Security validation
/test-run-suite rvnkdev-local security
```

**Parameters**:

| Parameter | Type | Options | Default |
|-----------|------|---------|---------|
| environment | Required | `rvnkdev-local`, `rvnkquests-integration`, `all-environments` | — |
| test-suites | Required | `core-tools`, `provider-integration`, `security`, `all-suites` | — |
| verbose | Optional | `verbose` or omit | quiet |
| compare | Optional | `compare` or omit | no comparison |
| save | Optional | `save` or omit | don't save |
| quiet | Optional | `quiet` or omit | normal output |

**Output**:
- Console summary with pass/fail counts
- Report files in `reports/` (if saved)
- Regression analysis (if compare enabled)
- Archon task creation (if failures detected)

---

### 2. Test: Analyze Results

**Full Command**:
```
/test-analyze-results [mode] [type] [format] [options]
```

**Quick Examples**:

```bash
# Check for regressions in latest run
/test-analyze-results latest regressions detailed

# Show performance trend over last 10 runs
/test-analyze-results trend performance chart

# Provider health check
/test-analyze-results latest providers detailed

# Success rate trends in table format
/test-analyze-results trend success-rate table

# Compare all metrics with baseline
/test-analyze-results baseline all detailed

# CSV export for external analysis
/test-analyze-results trend all csv

# Security test summary
/test-analyze-results latest security detailed

# Quick summary (table format)
/test-analyze-results latest success-rate table
```

**Parameters**:

| Parameter | Type | Options | Default |
|-----------|------|---------|---------|
| mode | Required | `latest`, `baseline`, `trend`, `all` | — |
| type | Required | `success-rate`, `performance`, `regressions`, `providers`, `security` | — |
| format | Optional | `table`, `chart`, `csv`, `detailed` | `detailed` |

**Output**:
- Comparison tables with metrics
- ASCII charts for trends
- Regression alerts
- Detailed explanations
- Recommendations

---

## Common Workflows

### Workflow 1: Pre-Release Validation

```bash
# Step 1: Run full test suite
/test-run-suite rvnkdev-local all-suites verbose compare

# Step 2: Analyze for regressions
/test-analyze-results latest regressions detailed

# Step 3: Check provider health
/test-analyze-results latest providers detailed

# Step 4: Verify security tests
/test-analyze-results latest security detailed
```

**Expected**: All tests pass, no regressions, all providers online

### Workflow 2: Daily Continuous Integration

```bash
# Quick daily check
/test-run-suite rvnkdev-local core-tools quiet save

# Weekly trend analysis
/test-analyze-results trend performance chart

# Monthly baseline comparison
/test-analyze-results baseline all detailed
```

### Workflow 3: RVNKQuests Deployment

```bash
# Test MCP server discovery
/test-run-suite rvnkquests-integration discovery verbose

# Test tool execution
/test-run-suite rvnkquests-integration execution verbose

# Test complete integration
/test-run-suite rvnkquests-integration all-tests verbose compare
```

### Workflow 4: Performance Analysis

```bash
# Get current performance metrics
/test-analyze-results latest performance detailed

# Check performance trend
/test-analyze-results trend performance chart

# Compare with baseline
/test-analyze-results baseline performance table
```

### Workflow 5: Troubleshooting

```bash
# Run with verbose output
/test-run-suite rvnkdev-local all-suites verbose

# Analyze which tests failed
/test-analyze-results latest regressions detailed

# Check provider-specific issues
/test-analyze-results latest providers detailed

# Review complete history
/test-analyze-results all all detailed
```

---

## Understanding Output

### Test Suite Execution Output

```
Test Execution Results
═══════════════════════════════════════════════════════
Core MCP Tools: 21/21 ✅ (125.4ms avg)
Provider Integration: 6/6 ✅ (234.2ms avg)
Security Validation: 4/4 ✅ (89.1ms avg)

Total: 25/25 tests passed (100%)
Duration: 45.2 seconds
Regressions: None detected ✅

Reports saved:
  - reports/rvnkdev-20251108-143022.json
  - reports/rvnkdev-20251108-143022.md
```

### Regression Analysis Output

```
Regression Analysis
═══════════════════════════════════════════════════════
Status: ✅ NO REGRESSIONS (threshold: 5%)

Success Rate: 100% → 100% (change: 0%)

Core Tools: 21/21 ✅ (no change)
Provider: 6/6 ✅ (no change)
Security: 4/4 ✅ (no change)

Performance: Stable ✅
Trend: Healthy
```

### Performance Trend Output

```
Performance Trend (Last 10 Runs)
═══════════════════════════════════════════════════════

Average Duration (ms)
┌─────────────────────────────────┐
│           ▂▁▂▂▁▂▂▁▂▂▁   │
│       ▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁   │
└─────────────────────────────────┘
  123  125  127  129  131

Current: 124.6ms
Trend: Stable ✅
Baseline: 125.0ms
Delta: -0.4ms (0.3% improvement)
```

---

## Troubleshooting

### Command Not Found

**Error**: `Command '/test-run-suite' not found`

**Solution**:
1. Check that `.claude/commands/test-run-suite.md` exists
2. Ensure commands directory is registered
3. Restart Claude Code if recently updated

### Test Won't Start

**Error**: `ModuleNotFoundError: No module named 'test_report_schema'`

**Solution**:
```bash
# Navigate to test suites directory
cd ../../rvnkdev-mcp-server/metamake/projects/10-test-suite-tracking/test-suites

# Verify files exist
ls -la

# Check Python version
python --version  # Should be 3.11+
```

### No Reports Generated

**Error**: `Reports directory not found`

**Solution**:
1. Create reports directory: `mkdir -p ../../rvnkdev-mcp-server/metamake/projects/10-test-suite-tracking/reports`
2. Verify write permissions: `touch ../../rvnkdev-mcp-server/metamake/projects/10-test-suite-tracking/reports/test.txt`
3. Check disk space: `df -h`

### Regression False Positives

**Error**: `Regression detected when tests actually passed`

**Solution**:
1. Check response_history.json for data integrity
2. Verify compare mode threshold (default 5%)
3. Run baseline comparison: `/test-analyze-results baseline`

---

## Advanced Usage

### Batch Testing

```bash
# Test all environments sequentially
/test-run-suite all-environments all-suites verbose compare

# Generate all reports
/test-run-suite rvnkdev-local all-suites save
/test-run-suite rvnkquests-integration all-tests save
```

### Custom Analysis

```bash
# Analyze specific metrics
/test-analyze-results trend success-rate chart
/test-analyze-results trend performance table
/test-analyze-results all regressions detailed

# Export for external tools
/test-analyze-results all all csv
```

### Regression Monitoring

```bash
# Daily regression check
/test-run-suite rvnkdev-local core-tools compare

# Weekly trend analysis
/test-analyze-results trend all detailed

# Monthly baseline comparison
/test-analyze-results baseline all table
```

---

## Environment Variables

These are automatically set but can be customized:

```bash
# Python path
PYTHONPATH=../../rvnkdev-mcp-server/metamake/projects/10-test-suite-tracking/test-suites

# Bitwarden session (auto-managed)
BW_SESSION=...

# Test configuration
LOG_LEVEL=info
FASTMCP_ENV=development
```

---

## Report File Locations

```
../../rvnkdev-mcp-server/metamake/projects/10-test-suite-tracking/reports/
├── rvnkdev-20251108-143022.json          # JSON report
├── rvnkdev-20251108-143022.md            # Markdown report
├── response_history.json                 # Historical tracking
└── [other reports...]
```

---

## Integration with Archon

Commands automatically:
1. Create Archon tasks for test failures
2. Update task status after remediation
3. Link related issues
4. Add test results as comments
5. Track regression metrics

**Archon Task Creation**:
```
Title: Test Regression Detected: rvnkdev_local - success_rate dropped 12%
Description: [Link to report with affected tests]
Priority: High
Labels: testing, regression, rvnkdev-local
```

---

## Quick Reference Card

| Task | Command | Time |
|------|---------|------|
| Quick test | `/test-run-suite rvnkdev-local core-tools` | 2 min |
| Full test | `/test-run-suite rvnkdev-local all-suites verbose` | 1 min |
| Check regressions | `/test-analyze-results latest regressions detailed` | 10 sec |
| Performance trend | `/test-analyze-results trend performance chart` | 5 sec |
| Provider check | `/test-analyze-results latest providers detailed` | 5 sec |
| RVNKQuests test | `/test-run-suite rvnkquests-integration all-tests` | 2 min |

---

## Support

**Get help**:
1. Check enhanced test-orchestrator agent: `.claude/agents/test-orchestrator-enhanced.md`
2. Review full command docs: `.claude/commands/test-*.md`
3. Ask Claude: "How do I [test task]?"
4. Check Copilot instructions: `.github/supplemental/copilot-instructions.test-orchestrator.md`

**Report issues**:
1. Create GitHub issue with test output
2. Create Archon task with diagnostic info
3. Share report files (JSON + Markdown)
4. Include environment details

---

**Last Updated**: December 6, 2025
**Version**: 2.0
**Status**: Ready for Production

For comprehensive documentation, see:
- **Test Orchestrator Agent**: `.claude/agents/test-orchestrator-enhanced.md`
- **Test Suite Tracking**: `../../rvnkdev-mcp-server/metamake/projects/10-test-suite-tracking/COPILOT-INSTRUCTIONS.md`
- **Deployment Guide**: `../../rvnkdev-mcp-server/docs/RVNKQUESTS_DEPLOYMENT_GUIDE.md`
