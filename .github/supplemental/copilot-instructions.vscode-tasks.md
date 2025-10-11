# RVNKQuests VS Code Development Tasks and MCP Integration

## Development Workflow Overview

RVNKQuests uses a hybrid development workflow combining VS Code tasks and RVNKDev MCP tools for efficient plugin development and testing.

### VS Code Tasks Structure

The following tasks are available in the workspace:

```json
{
    "tasks": [
        {
            "label": "Build Plugin",
            "type": "shell",
            "command": "mvn clean package",
            "group": {"kind": "build", "isDefault": true},
            "detail": "Compiles and packages the Spigot plugin using Maven."
        },
        {
            "label": "Copy to Server",
            "type": "shell", 
            "command": "powershell -ExecutionPolicy Bypass -File ./.vscode/copyto-server-DEV.ps1",
            "dependsOn": "Build Plugin",
            "detail": "Copies the built plugin JAR to the server plugins folder."
        },
        {
            "label": "Restart Server",
            "dependsOn": "Copy to Server",
            "detail": "RESTARTS after copying fresh build to DEV server."
        },
        {
            "label": "Reload Server", 
            "dependsOn": "Copy to Server",
            "detail": "Reloads plugins after copying fresh build to DEV server."
        }
    ]
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
        
        // Step 3: Deploy to server ("Copy to Server" task)
        // This copies JAR to server plugins folder
        
        // Step 4: Choose reload method:
        // - "Reload Server" for plugin reloads (faster)
        // - "Restart Server" for full restart (more reliable)
    }
}
```

#### Automated Task Chains

```java
public class TaskChainExamples {
    
    // Clean & Reload: ServerCleanup -> Copy to Server -> Reload Server
    public void quickTestCycle() {
        // Use "Clean&Reload Server" task for rapid testing
        // - Cleans old plugin files
        // - Copies new build
        // - Reloads plugins without full restart
    }
    
    // Clean & Restart: ServerCleanup -> Copy to Server -> Restart Server  
    public void fullTestCycle() {
        // Use "Clean&Restart Server" task for comprehensive testing
        // - Cleans old plugin files
        // - Copies new build
        // - Performs full server restart
    }
}
```

## RVNKDev MCP Integration

### MCP Tools Available

RVNKDev MCP server provides additional development tools that complement VS Code tasks:

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
        
        // 4. PowerShell script execution
        runDeploymentScript(); // .vscode/*.ps1 files
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

## PowerShell Scripts Integration

### VS Code Task Scripts

The VS Code tasks utilize PowerShell scripts in `.vscode/` folder:

```powershell
# .vscode/copyto-server-DEV.ps1
param(
    [string]$PluginName = "RVNKQuests",
    [string]$SourcePath = "target",
    [string]$ServerPath = "C:\minecraft-servers\rvnk-test\plugins"
)

# Copy built JAR to server plugins folder
$jarFile = Get-ChildItem "$SourcePath\$PluginName*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1

if ($jarFile) {
    Copy-Item $jarFile.FullName -Destination $ServerPath -Force
    Write-Host "Deployed $($jarFile.Name) to $ServerPath"
} else {
    Write-Error "No JAR file found in $SourcePath"
    exit 1
}
```

```powershell
# .vscode/restart-server-DEV.ps1
param(
    [string]$ServerPath = "C:\minecraft-servers\rvnk-test"
)

# Stop server gracefully
Stop-Process -Name "java" -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

# Start server
Set-Location $ServerPath
Start-Process -FilePath "java" -ArgumentList "-jar spigot-1.21.4.jar nogui" -NoNewWindow
```

### MCP Integration with PowerShell

```java
public class MCPPowerShellIntegration {
    
    public void hybridScriptExecution() {
        // 1. Use VS Code task for local PowerShell operations
        runVSCodeTask("Build Plugin"); // Executes local PowerShell
        
        // 2. Use MCP for remote server operations  
        deployViaMCP(); // Remote file operations
        
        // 3. Combine for complete workflow
        executeHybridWorkflow();
    }
    
    private void executeHybridWorkflow() {
        // Local: Build with PowerShell via VS Code task
        // Remote: Deploy with MCP batch operations
        // Remote: Test with MCP console commands
        // Local: Monitor with MCP console output (displayed locally)
    }
}
```

## Performance and Efficiency

### Task Optimization

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
        // 1. "Clean&Restart Server" (VS Code task chain)
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
        // Building, local file operations, script execution
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
        // Tasks: "Build Plugin", "Copy to Server", etc.
        // MCP Operations: batch_file_operations, send_console_command
        // Scripts: copyto-server-DEV.ps1, restart-server-DEV.ps1
    }
    
    // 4. Automate common workflows
    public void automateWorkflows() {
        // Task chains: "Clean&Restart Server"
        // MCP batches: Multi-file deployment operations
        // Combined: Hybrid development cycles
    }
}
```