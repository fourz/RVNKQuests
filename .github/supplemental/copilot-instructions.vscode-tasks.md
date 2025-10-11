# RVNKQuests VS Code Development Tasks and MCP Integration

## Development Workflow Overview - MCP Server Integration

RVNKQuests development workflow is **fully integrated with RVNKDev MCP server** for automated plugin deployment, server management, and testing operations.

### MCP-Integrated VS Code Tasks Structure

**Updated Tasks (October 11, 2025)** - All server operations now use RVNKDev MCP tools:

```json
{
    "tasks": [
        {
            "label": "Build Plugin",
            "type": "shell",
            "command": "mvn clean package",
            "group": {"kind": "build", "isDefault": true},
            "detail": "Compiles and packages RVNKQuests plugin using Maven."
        },
        {
            "label": "Deploy to Test Server",
            "dependsOn": "Build Plugin",
            "detail": "Deploys RVNKQuests JAR to RVNK Test server (b2bc4d7e) using MCP batch file operations."
        },
        {
            "label": "Restart Test Server",
            "dependsOn": "Deploy to Test Server",
            "detail": "Restarts RVNK Test server (b2bc4d7e) using MCP restart_server tool."
        },
        {
            "label": "Reload Plugins",
            "dependsOn": "Deploy to Test Server",
            "detail": "Reloads plugins on RVNK Test server using MCP send_console_command tool (reload)."
        },
        {
            "label": "Complete Deploy & Restart",
            "dependsOn": ["Clean Server Files", "Restart Test Server"],
            "detail": "Full deployment: Clean → Deploy → Restart RVNK Test server via MCP tools."
        },
        {
            "label": "Complete Deploy & Reload", 
            "dependsOn": ["Clean Server Files", "Reload Plugins"],
            "detail": "Fast deployment: Clean → Deploy → Reload plugins via MCP tools."
        }
    ]
}
```

### MCP Server Configuration

**Target Server**: RVNK Test Server  
**Server ID**: `b2bc4d7e` (SparkedHost)  
**MCP Provider**: RVNKDev MCP Server  
**Configuration File**: `.vscode/rvnkdev-config.json`

```json
{
    "servers": {
        "rvnk-test": {
            "id": "b2bc4d7e",
            "name": "RVNK Test Server", 
            "provider": "sparkedhost",
            "isDefault": true
        }
    },
    "deployment": {
        "pluginPath": "target/RVNKQuests-1.0-SNAPSHOT.jar",
        "serverPluginFolder": "/plugins/",
        "cleanupFiles": [
            "/plugins/RVNKQuests-*.jar",
            "/plugins/RVNKQuests/"
        ]
    }
}
```

### Task Usage Patterns

#### Basic Development Cycle

```java
// 1. Code changes in IDE
// 2. Build and deploy using VS Code tasks
public class DevelopmentWorkflow {
    
    public void standardDevelopmentCycle() {
        // Step 1: Make code changes
        modifyQuestImplementation();
        
        // Step 2: Build plugin (Ctrl+Shift+P -> "Tasks: Run Task" -> "Build Plugin")
        // This runs: mvn clean package
        
        // Step 3: Deploy to server ("Deploy to Test Server" task)
        // This copies JAR to server plugins folder via MCP
        
        // Step 4: Choose reload method:
        // - "Reload Plugins" for plugin reloads (faster)
        // - "Restart Test Server" for full restart (more reliable)
    }
}
```

#### Automated Task Chains

```java
public class TaskChainExamples {
    
    // Clean & Reload: ServerCleanup -> Deploy to Test Server -> Reload Plugins
    public void quickTestCycle() {
        // Use "Complete Deploy & Reload" task for rapid testing
        // - Cleans old plugin files via MCP
        // - Deploys new build via MCP batch operations
        // - Reloads plugins without full restart via MCP console
    }
    
    // Clean & Restart: ServerCleanup -> Deploy to Test Server -> Restart Test Server  
    public void fullTestCycle() {
        // Use "Complete Deploy & Restart" task for comprehensive testing
        // - Cleans old plugin files via MCP
        // - Deploys new build via MCP batch operations
        // - Performs full server restart via MCP
    }
}
```

## RVNKDev MCP Integration

### MCP Tools Available

RVNKDev MCP server provides development tools that replace legacy PowerShell scripts:

```java
public class MCPToolsReference {
    
    // Server Management
    public void serverManagement() {
        // mcp_rvnkdev-minec_server_status - Check RVNK Test server status
        // mcp_rvnkdev-minec_start_server - Start RVNK Test server
        // mcp_rvnkdev-minec_stop_server - Stop RVNK Test server
        // mcp_rvnkdev-minec_restart_server - Restart RVNK Test server
    }
    
    // Console Operations
    public void consoleOperations() {
        // mcp_rvnkdev-minec_send_console_command - Execute commands on server
        // mcp_rvnkdev-minec_get_console_output - Read server console output
    }
    
    // File Management
    public void fileManagement() {
        // mcp_rvnkdev-minec_batch_file_operations - Deploy plugin files
        // mcp_rvnkdev-minec_delete_file - Clean up old plugin files
        // mcp_rvnkdev-minec_write_file - Write configuration files
        // mcp_rvnkdev-minec_read_file - Read server files
    }
}
```

### MCP vs VS Code Tasks

#### When to Use VS Code Tasks

```java
public class VSCodeTaskUseCase {
    
    // Use VS Code tasks for:
    public void preferVSCodeTasks() {
        // 1. Local development operations
        buildPlugin(); // "Build Plugin" task
        
        // 2. Integrated IDE workflow
        runTaskFromCommandPalette(); // Ctrl+Shift+P integration
        
        // 3. Automated task dependencies
        buildThenDeploy(); // Task chains with dependsOn
        
        // 4. Maven build operations
        runMavenBuild(); // Local Maven execution
    }
}
```

#### When to Use MCP Tools

```java
public class MCPToolUseCase {
    
    // Use MCP tools for:
    public void preferMCPTools() {
        // 1. Remote server operations
        checkServerStatus(); // Real-time server monitoring
        
        // 2. Console interaction
        sendQuestCommands(); // Direct command execution
        
        // 3. File synchronization
        batchDeployFiles(); // Efficient multi-file operations
        
        // 4. Runtime debugging
        monitorServerLogs(); // Live log monitoring
    }
}
```

### Hybrid Development Workflow

#### Complete Development Cycle

```java
public class HybridDevelopmentWorkflow {
    
    public void completeDevelopmentCycle() {
        // Phase 1: Local Development (VS Code Tasks)
        step1_buildPlugin(); // VS Code "Build Plugin" task
        
        // Phase 2: Deployment (MCP Tools)  
        step2_deployToServer(); // MCP batch file operations
        
        // Phase 3: Testing (MCP Tools)
        step3_testQuests(); // MCP console commands
        
        // Phase 4: Monitoring (MCP Tools)
        step4_monitorLogs(); // MCP console output
        
        // Phase 5: Iteration (VS Code Tasks)
        step5_makeChanges(); // Back to VS Code for modifications
    }
    
    private void step1_buildPlugin() {
        // Use VS Code task: "Build Plugin"
        // Command: mvn clean package
        // Result: target/RVNKQuests-1.0-SNAPSHOT.jar
    }
    
    private void step2_deployToServer() {
        // Use MCP: mcp_rvnkdev-minec_batch_file_operations
        // - Delete old plugin JAR
        // - Upload new plugin JAR
        // - Verify file transfer
    }
    
    private void step3_testQuests() {
        // Use MCP: mcp_rvnkdev-minec_send_console_command
        // Commands:
        // - "reload" (reload plugins)
        // - "/quest trigger piglin_far_from_home around"
        // - "/quest debug piglin_far_from_home"
    }
    
    private void step4_monitorLogs() {
        // Use MCP: mcp_rvnkdev-minec_get_console_output
        // Monitor for:
        // - Plugin load success/failure
        // - Quest trigger events
        // - Error messages
    }
}
```

## Specific Development Scenarios

### Quest Development and Testing

```java
public class QuestDevelopmentWorkflow {
    
    public void developNewQuest() {
        // 1. Create quest class locally
        createQuestClass();
        
        // 2. Build and deploy (VS Code task)
        runVSCodeTask("Build Plugin");
        
        // 3. Deploy using MCP batch operations
        deployViaMCP();
        
        // 4. Test quest triggers via MCP console
        testQuestTriggers();
        
        // 5. Monitor quest behavior via MCP logs
        monitorQuestBehavior();
    }
    
    private void testQuestTriggers() {
        // MCP console commands for quest testing:
        sendConsoleCommand("/quest trigger piglin_far_from_home around");
        sendConsoleCommand("/quest debug piglin_far_from_home");
        sendConsoleCommand("/quest state piglin_far_from_home QUEST_ACTIVE");
    }
    
    private void monitorQuestBehavior() {
        // Monitor console output for:
        // [INFO] Quest piglin_far_from_home triggered by player
        // [DEBUG] Quest state changed: NOT_STARTED -> TRIGGER_FOUND
        // [ERROR] Failed to load quest listener...
    }
}
```

### Configuration Updates

```java
public class ConfigurationWorkflow {
    
    public void updateConfiguration() {
        // 1. Modify config.yml locally
        updateLocalConfig();
        
        // 2. Deploy config via MCP file operations
        deployConfigViaMCP();
        
        // 3. Reload configuration via MCP console
        reloadConfigViaMCP();
        
        // 4. Verify changes via MCP console output
        verifyConfigChanges();
    }
    
    private void deployConfigViaMCP() {
        // Use mcp_rvnkdev-minec_write_file to update server config
        writeConfigFile("/plugins/RVNKQuests/config.yml", updatedConfigContent);
    }
    
    private void reloadConfigViaMCP() {
        // Use mcp_rvnkdev-minec_send_console_command
        sendConsoleCommand("/quest reload");
    }
}
```

### Debug and Troubleshooting

```java
public class DebuggingWorkflow {
    
    public void debugQuestIssue() {
        // 1. Check server status via MCP
        checkServerStatusViaMCP();
        
        // 2. Enable debug logging via MCP console
        enableDebugLogging();
        
        // 3. Reproduce issue via MCP console commands
        reproduceIssue();
        
        // 4. Collect logs via MCP
        collectDebugLogs();
        
        // 5. Make fixes locally and redeploy via VS Code tasks
        makeFixesAndRedeploy();
    }
    
    private void enableDebugLogging() {
        sendConsoleCommand("/quest config debug true");
    }
    
    private void reproduceIssue() {
        sendConsoleCommand("/quest trigger problematic_quest around");
        sendConsoleCommand("/tp player 100 64 100"); // Move to quest area
        // Wait and observe behavior via console output
    }
    
    private void collectDebugLogs() {
        String logs = getConsoleOutput(100); // Last 100 lines
        analyzeLogsForErrors(logs);
    }
}
```

## MCP Integration Benefits

### Advantages Over Legacy PowerShell Scripts

```java
public class MCPAdvantages {
    
    public void legacyVsMCPComparison() {
        // Legacy PowerShell Approach:
        // - Local file system copying
        // - Manual server restart scripts
        // - Limited remote monitoring
        // - Error-prone file synchronization
        
        // MCP Approach:
        // - Direct server API integration
        // - Reliable batch file operations
        // - Real-time console access
        // - Automated deployment workflows
        // - Production-safe operations
        // - Cross-platform compatibility
    }
    
    public void mcpBenefits() {
        // 1. Reliability: Direct API calls vs file system operations
        // 2. Monitoring: Real-time server status and logs
        // 3. Automation: Batch operations with proper error handling
        // 4. Safety: Production-safe tool validation
        // 5. Integration: Seamless VS Code + MCP workflow
    }
}
```

### Performance and Efficiency

```java
public class TaskOptimization {
    
    public void optimizeForSpeed() {
        // Fast iteration cycle:
        // 1. "Build Plugin" (VS Code task - local Maven)
        // 2. MCP batch file operations (efficient remote copy)
        // 3. MCP console "reload" command (no full restart)
        
        // Total time: ~10-15 seconds for code change to test
    }
    
    public void optimizeForReliability() {
        // Reliable cycle:
        // 1. "Complete Deploy & Restart" (VS Code task chain)
        // 2. MCP server status verification
        // 3. MCP console testing with full state reset
        
        // Total time: ~30-45 seconds but more reliable
    }
}
```

### Monitoring and Feedback

```java
public class DevelopmentMonitoring {
    
    public void setupDevelopmentMonitoring() {
        // 1. VS Code task output for build status
        monitorBuildOutput();
        
        // 2. MCP console output for runtime behavior  
        monitorRuntimeBehavior();
        
        // 3. Combined feedback loop
        establishFeedbackLoop();
    }
    
    private void establishFeedbackLoop() {
        // VS Code Terminal: Build and deployment status
        // MCP Console Output: Runtime quest behavior
        // VS Code Problems Panel: Compilation errors
        // MCP File Operations: Deployment success/failure
    }
}
```

## Best Practices Summary

### Development Workflow Best Practices

```java
public class DevelopmentBestPractices {
    
    // 1. Use VS Code tasks for local operations
    public void useVSCodeForLocal() {
        // Building, Maven operations, local file management
        runTask("Build Plugin");
    }
    
    // 2. Use MCP tools for server operations
    public void useMCPForServer() {
        // File deployment, console commands, log monitoring
        deployWithMCP();
        testWithMCP();
    }
    
    // 3. Establish consistent naming
    public void consistentNaming() {
        // Tasks: "Build Plugin", "Deploy to Test Server", etc.
        // MCP Operations: batch_file_operations, send_console_command
        // Configuration: rvnkdev-config.json structure
    }
    
    // 4. Automate common workflows
    public void automateWorkflows() {
        // Task chains: "Complete Deploy & Restart"
        // MCP batches: Multi-file deployment operations
        // Combined: Hybrid development cycles
    }
}
```

### Migration from Legacy Scripts

```java
public class LegacyMigration {
    
    public void migrationBenefits() {
        // Before (Legacy PowerShell):
        // - copyto-server-DEV.ps1 → Manual file copying
        // - restart-server-DEV.ps1 → Local server restart scripts
        // - reload-server-DEV.ps1 → Limited remote control
        
        // After (MCP Integration):
        // - mcp_rvnkdev-minec_batch_file_operations → Reliable deployment
        // - mcp_rvnkdev-minec_restart_server → Direct server management
        // - mcp_rvnkdev-minec_send_console_command → Full console control
    }
    
    public void backupStrategy() {
        // Legacy scripts preserved in: shared-backup/vscode-tasks-pre-mcp/
        // Available for rollback if needed
        // MCP configuration documented in: .vscode/rvnkdev-config.json
    }
}
```
