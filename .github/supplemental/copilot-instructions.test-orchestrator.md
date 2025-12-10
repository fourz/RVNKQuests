# GitHub Copilot: Test Orchestrator Instructions

**Integration guide for GitHub Copilot users working with RvnkDev MCP Server testing**

**Updated**: November 8, 2025 with enhanced Claude skills integration and command references

---

## Quick Start

When you need to test the RvnkDev FastMCP Server:

### For GitHub Copilot Users:

1. **Ask Copilot:**
   ```
   @copilot Run the comprehensive test suite for RvnkDev FastMCP Server with regression analysis
   ```

2. **Copilot will:**
   - Navigate to the test suite directory
   - Execute the test runner
   - Collect results with metadata
   - Generate JSON and Markdown reports
   - Show regression analysis
   - Display trend data
   - Create Archon tasks if needed

### For Claude Code Users:

1. **Use the Test Orchestrator agent:**
   ```
   Task: Run RvnkDev local test suite with full diagnostics
   ```

2. **Or use slash commands:**
   ```
   /test-run-suite rvnkdev-local all-suites
   /test-analyze-results latest trend
   ```

3. **Claude will:**
   - Execute comprehensive test suite
   - Generate both JSON and Markdown reports
   - Automatically detect regressions
   - Provide trend analysis
   - Create Archon tasks for failures
   - Offer intelligent recommendations

## Claude Skills Integration

### Available Claude Capabilities

The Test Orchestrator leverages Claude's AI capabilities:

**Code Execution & Analysis:**
- ✅ Read and execute Python test runner scripts
- ✅ Parse test output and error messages
- ✅ Handle async operations and resource cleanup
- ✅ Analyze test failures with root cause detection
- ✅ Integration with VS Code IDE diagnostics

**Data Processing & Analytics:**
- ✅ Parse JSON test reports (structured data analysis)
- ✅ Generate Markdown reports from test data
- ✅ Statistical analysis of success rates and trends
- ✅ Automatic regression detection with 5% threshold
- ✅ Performance trend visualization (ASCII charts)
- ✅ Comparative analysis across test runs

**Environment Orchestration:**
- ✅ Coordinate test execution across rvnkdev-local and rvnkquests-integration
- ✅ Manage Bitwarden credential integration
- ✅ Handle provider initialization (SparkedHost, MCSS)
- ✅ Cleanup and resource management

**Knowledge Integration:**
- ✅ Reference deployment guides and test documentation
- ✅ Query Archon task management system
- ✅ Provide intelligent recommendations based on results
- ✅ Create and update Archon tasks for failures

---

## Test Orchestrator Overview

The Test Orchestrator is a specialized testing system for:
- **RvnkDev FastMCP Server** - MCP protocol implementation with 21 core tools
- **RVNKQuests Integration** - Real-world usage in VS Code MCP environment
- **Cross-environment Validation** - Multiple platforms and installation methods

### Test Environments

| Environment | Purpose | Test Suites | Status | Location |
|-------------|---------|-------------|--------|----------|
| **rvnkdev_local** | Development validation | Core Tools (21), Provider (6), Security (4) | ✅ Active | `metamake/projects/10-test-suite-tracking/` |
| **rvnkquests_integration** | Real-world integration | Discovery, Execution, Workflow, Error Handling | 🔄 Planning | External VS Code instance |
| **Alternative Installs** | Platform testing | macOS, Linux, Claude Desktop, PyPI | 📝 Placeholder | Various |

### Test Statistics

- **Total Test Cases**: 25+ (core suite)
- **Core MCP Tools**: 21 (across 5 categories)
- **Provider Support**: 2 (SparkedHost, MCSS)
- **Credential Services**: 5 (API + SFTP for each, MySQL)
- **Success Rate Target**: 100%
- **Average Duration**: 45-60 seconds (full suite)

## Command Integration

### Claude Code Slash Commands

**Test Execution Commands:**

```
/test-run-suite [environment] [test-suites] [options]
```

**Environments:**
- `rvnkdev-local` — RvnkDev development tests (21 core tools, 3 suites)
- `rvnkquests-integration` — RVNKQuests VS Code MCP tests
- `all-environments` — Run all available tests

**Test Suites:**
- `core-tools` — Core MCP tools validation
- `provider-integration` — Provider authentication and operations
- `security` — Production safety restrictions
- `all-suites` — Run all test suites sequentially

**Options:**
- `verbose` — Show detailed test output
- `compare` — Compare with previous run (regression analysis)
- `save` — Save results to tracking database
- `quiet` — Minimal output

**Examples:**
```bash
/test-run-suite rvnkdev-local all-suites verbose compare
/test-run-suite rvnkquests-integration discovery execution
/test-run-suite all-environments core-tools verbose
```

**Analysis Commands:**

```
/test-analyze-results [mode] [type] [format] [options]
```

**Modes:**
- `latest` — Compare with previous run
- `baseline` — Compare with initial baseline
- `trend` — Show trend over last N runs (default: 10)
- `all` — Compare all metrics

**Types:**
- `success-rate` — Test success rate trends
- `performance` — Execution time analysis
- `regressions` — Identify failures
- `providers` — Provider-specific analysis
- `security` — Security test validation

**Formats:**
- `table` — Formatted comparison table
- `chart` — ASCII trend chart
- `csv` — Machine-readable format
- `detailed` — Full analysis with explanations

**Examples:**
```bash
/test-analyze-results latest regressions detailed
/test-analyze-results trend performance chart
/test-analyze-results all success-rate table
```

---

## Common Test Commands

### Run All Tests

**Copilot:**
```copilot
@copilot Run comprehensive test suite for RvnkDev with regression analysis
```

**Claude:**
```
/test-run-suite rvnkdev-local all-suites verbose compare
```

**What it does:**
- Executes all test suites (Core Tools, Providers, Security)
- Generates JSON and Markdown reports
- Compares with historical baseline
- Alerts on regressions
- Saves results to `reports/` directory
- Creates Archon tasks if issues found

**Expected output:**
```
Test Execution Results
═══════════════════════════════════════════════════════
Core MCP Tools: 21/21 ✅ (125.4ms avg)
Provider Integration: 6/6 ✅ (234.2ms avg)
Security Validation: 4/4 ✅ (89.1ms avg)

Total: 25/25 tests passed (100%)
Duration: 45.2 seconds
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

### Test Suite Tracking Project

**Reference Documentation**: `shared/derek/repos/rvnkdev-mcp-server/metamake/projects/10-test-suite-tracking/COPILOT-INSTRUCTIONS.md`

Comprehensive test suite tracking system documentation including:
- Test report schema and structure
- Deployment modes (dev/prod with credential sources)
- Test environment configurations
- Response history tracking
- Report generation patterns

**Key Resources**:
- Test Report Schema: `test_report_schema.py`
- RvnkDev Pytest Runner: `run_rvnkdev_pytest.py` (recommended)
- Test Environment Docs: See tracking project COPILOT-INSTRUCTIONS.md

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

## Agent & Command References

### Claude Code Agents

**Primary Agent:**
- **Test Orchestrator (Enhanced)** - `.claude/agents/test-orchestrator-enhanced.md`
  - Comprehensive testing orchestration
  - Multi-environment coordination
  - Regression detection and analysis
  - Report generation (JSON + Markdown)
  - Archon integration for task management

**Supporting Agents:**
- **Test Engineer** - `.claude/agents/test-engineer.md`
  - Individual test creation and validation
  - Test coverage analysis
  - Provider testing patterns
  - Resource cleanup validation

### Slash Commands

Located in `.claude/commands/`:
- **test-run-suite.md** - Execute test suites with options
- **test-analyze-results.md** - Analyze and compare test results

---

## Related Resources

### Documentation
- **Test Agent (Enhanced):** `.claude/agents/test-orchestrator-enhanced.md`
- **Test Engineer Agent:** `.claude/agents/test-engineer.md`
- **Deployment Guide:** `shared/derek/repos/rvnkdev-mcp-server/docs/RVNKQUESTS_DEPLOYMENT_GUIDE.md`
- **Test Framework Docs:** `metamake/projects/10-test-suite-tracking/COPILOT-INSTRUCTIONS.md`
- **Project 10 Milestones:** `shared/derek/repos/rvnkdev-mcp-server/rvnkdev-fastmcp-server/docs/milestones/`

### Implementation Files
- **Test Runner:** `metamake/projects/10-test-suite-tracking/test-suites/run_rvnkdev_tests.py`
- **Report Schema:** `metamake/projects/10-test-suite-tracking/test-suites/test_report_schema.py`
- **Report Output:** `metamake/projects/10-test-suite-tracking/reports/` (JSON, Markdown, history)

### 🔄 MCP Test Documentation & Resources

Comprehensive guides for RvnkDev MCP Server testing and orchestration:

#### TEST_ORCHESTRATION_GUIDE.md (525 lines)
Complete reference for test system architecture and execution:

**Contents (7 Key Aspects)**:
1. **Overview & Components** - Core concepts and terminology
   - What: Automated test execution system for RvnkDev MCP Server
   - Why: Ensure production reliability and regression prevention
   - How: Agent-based orchestration with multi-environment testing

2. **Test Environments** - Available testing contexts
   - **rvnkdev_local** - Development and rapid iteration (localhost testing)
   - **rvnkquests_integration** - Full integration testing with RVNKQuests plugin
   - **rvnkquests_security** - Security validation testing

3. **Test Execution Commands** - How to run tests
   - Basic execution: `pytest` or agent commands
   - With reports: `pytest --json-report`
   - Specific suites: `pytest tests/core_tools/`
   - See guide for complete command reference

4. **Test Architecture** - System design and flow
   - Agent orchestration (autonomous test execution)
   - Command-based orchestration (GitHub Copilot integration)
   - Reporting pipeline (JSON → Markdown → history)
   - Multi-environment coordination

5. **GitHub Copilot Integration** - Using test commands in Copilot
   - Slash command syntax: `/test-run-suite`, `/test-analyze-results`
   - Integration with development workflow
   - Continuous regression detection

6. **Reports & Metrics** - Understanding test results
   - Test counts and pass rates by suite
   - Coverage analysis and improvement recommendations
   - Regression detection and tracking
   - Performance metrics and bottleneck analysis

7. **Best Practices & Troubleshooting** - Guide to success
   - Pre-commit testing recommendations
   - Common issues and solutions
   - Environment-specific debugging
   - Performance optimization tips

**When to Use**:
- Setting up test infrastructure
- Understanding test architecture
- Running comprehensive test suites
- Analyzing test results and coverage
- Troubleshooting test failures

**Location**: `docs/tests/TEST_ORCHESTRATION_GUIDE.md`

---

#### TEST_AGENT_IMPLEMENTATION_SUMMARY.md (475 lines)
Deep dive into the Enhanced Test Orchestrator Agent:

**Contents (10 Key Aspects)**:
1. **Agent Overview** - What the Enhanced Test Orchestrator does
   - Autonomous test execution and coordination
   - Multi-environment management
   - Comprehensive reporting and analysis

2. **Claude Skills Integration** - Advanced AI capabilities
   - Code execution (Python, Shell)
   - Data processing and analysis
   - Complex reporting and formatting
   - MCP tool integration

3. **Core Slash Commands** - Agent command reference
   - `/test-run-suite [suite_name]` - Execute specific test suite
   - `/test-analyze-results [test_type]` - Analyze test results
   - `/test-report-generation [format]` - Generate formatted reports
   - `/test-regression-detect [baseline]` - Detect regressions
   - `/test-coverage-analysis` - Analyze test coverage

4. **Command Syntax & Examples** - How to use each command
   - Basic usage patterns
   - Advanced parameter options
   - Output format examples
   - Integration patterns

5. **GitHub Copilot Integration** - Using agent in Copilot workflow
   - How to invoke from Copilot chat
   - Slash command syntax in editor
   - Embedding tests in code reviews
   - Continuous integration triggers

6. **Archon Task Automation** - Integration with task management
   - Automatic task creation from failures
   - Regression alerts as Archon tasks
   - Test result tracking in Archon
   - Performance regression monitoring

7. **Environment Management** - Handling multiple test environments
   - Environment detection and selection
   - Configuration per environment
   - Cross-environment test coordination
   - Data consistency across environments

8. **Reporting Engine** - Comprehensive test reporting
   - JSON format (machine-readable)
   - Markdown format (human-readable)
   - HTML generation (dashboard support)
   - Trend analysis and history

9. **Troubleshooting** - For Claude Users
   - Debugging failed test runs
   - Handling environment issues
   - Analyzing unexpected results
   - Performance problem resolution

10. **GitHub Copilot-Specific Guidance** - For Copilot Users
    - Error handling in editor
    - Workspace integration issues
    - Slash command limitations
    - When to use Claude vs Copilot

**When to Use**:
- Understanding agent capabilities
- Running advanced tests through agent
- Analyzing complex test failures
- Setting up Archon automation
- Integrating tests in Copilot workflow

**Location**: `docs/tests/TEST_AGENT_IMPLEMENTATION_SUMMARY.md`

---

#### Documentation Organization

**All test documentation** now organized under `docs/tests/` with comprehensive README:

```
docs/tests/
├── README.md                           # Navigation hub
├── TEST_ORCHESTRATION_GUIDE.md        # System architecture (525 lines)
└── TEST_AGENT_IMPLEMENTATION_SUMMARY.md # Agent guide (475 lines)
```

**Quick Reference Table**:

| Document | Purpose | Audience | Read Time |
|----------|---------|----------|-----------|
| **tests/README.md** | Navigation hub | All users | 5 min |
| **TEST_ORCHESTRATION_GUIDE.md** | System architecture and execution | QA engineers, developers | 30-45 min |
| **TEST_AGENT_IMPLEMENTATION_SUMMARY.md** | Agent capabilities and usage | Developers, architects | 20-30 min |

---

### How to Use MCP Test Documentation

#### Quick Start (5 minutes)
1. Read this section (you're here!)
2. Choose your workflow (Copilot or Claude)
3. See appropriate command examples below

#### Comprehensive Setup (30 minutes)
1. Read `docs/tests/README.md` - Navigation hub (5 min)
2. Read `TEST_ORCHESTRATION_GUIDE.md` - System overview (20 min)
3. Run first test example: `/test-run-suite core_tools` (5 min)

#### Advanced Usage (1-2 hours)
1. Read `TEST_AGENT_IMPLEMENTATION_SUMMARY.md` (30 min)
2. Explore environment-specific testing (20 min)
3. Set up Archon automation for regression detection (30 min)
4. Configure custom test suites (20 min)

#### Troubleshooting (varies)
See **Troubleshooting** sections in both documentation files:
- `TEST_ORCHESTRATION_GUIDE.md` → Section 7 (System issues)
- `TEST_AGENT_IMPLEMENTATION_SUMMARY.md` → Sections 9-10 (Agent issues)

---

### Integration with Development Workflow

**GitHub Copilot Users**:
```
1. During code review: Use `/test-run-suite` slash commands
2. Pre-commit: Run test suite in Copilot chat
3. On failure: Use `/test-analyze-results` for analysis
4. Regression detection: Copilot provides inline alerts
```

**Claude Users**:
```
1. Run comprehensive tests: Use agent `/test-run-suite` command
2. Analyze failures: Use `/test-analyze-results` command
3. Generate reports: Use `/test-report-generation` command
4. Setup Archon: Configure task automation for regression alerts
```

**Both Workflows**:
```
1. Test results → Archon tasks (when regression detected)
2. Archon tasks → Development priority (tracked in ROADMAP.md)
3. Fixed regressions → Verification via `/test-run-suite`
4. Test metrics → Project status (tracked in docs/status/)
```

---

### Archon Integration
- **RVNKQuests CLAUDE.md:** Task management and knowledge base workflow
- **Archon MCP Server:** Task tracking and regression alerts
- **Test Documentation:** Complete MCP testing guides (docs/tests/)

---

## Support

For help with testing:

### GitHub Copilot Users

1. **Ask Copilot directly:**
   ```copilot
   @copilot Run comprehensive tests for RvnkDev and show me any regressions
   ```

2. **Ask for specific guidance:**
   ```copilot
   @copilot How do I [test task]? Show me examples and best practices.
   ```

3. **Debug issues:**
   ```copilot
   @copilot Why are [specific tests] failing? Suggest solutions.
   ```

### Claude Code Users

1. **Use the Test Orchestrator agent:**
   - Simply describe your testing need
   - Agent will handle execution and reporting
   - Automatic Archon integration

2. **Use slash commands:**
   ```
   /test-run-suite rvnkdev-local all-suites verbose
   /test-analyze-results latest regressions detailed
   ```

3. **Get help:**
   - Ask Claude about test patterns
   - Request specific analysis or comparisons
   - Get recommendations for fixing failures

### General Guidance

1. **Check documentation:**
   - This file for Copilot guidance
   - Enhanced agent for detailed information
   - Deployment guide for context
   - Test framework docs for technical details

2. **Review working examples:**
   - `run_rvnkdev_tests.py` - Working test implementation
   - `run_rvnkquests_tests.py` - Integration testing pattern
   - `test_report_schema.py` - Data structures and schemas
   - `reports/` - Sample JSON and Markdown outputs

3. **Troubleshoot systematically:**
   - Check environment setup (Python, Bitwarden, credentials)
   - Verify provider connectivity
   - Review test output and error messages
   - Compare with historical baseline
   - Create Archon task if needed

---

## Integration Workflow

### Typical Testing Cycle

1. **Prepare** → Set up environment (Bitwarden, Python, credentials)
2. **Execute** → Run test suite via command or agent
3. **Analyze** → Review results and compare trends
4. **Report** → Generate JSON and Markdown reports
5. **Track** → Save to response history for regression detection
6. **Action** → Create Archon tasks for failures or improvements

### Automated Workflow (with Archon)

```
Test Execution
    ↓
Report Generation (JSON + Markdown)
    ↓
Regression Detection
    ↓
Archon Task Creation (if failures found)
    ↓
Developer Notification
    ↓
Remediation & Re-test
```

---

**Last Updated:** November 8, 2025
**Compatible with:** GitHub Copilot, Claude Code
**Test Framework:** Python 3.11+, Pytest, AsyncIO
**Agent System:** Enhanced with Claude skills integration
**Status:** Production Ready
