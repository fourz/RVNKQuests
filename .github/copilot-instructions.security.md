# Security Guidelines for RVNKQuests

## Production Safety Requirements

**CRITICAL PRODUCTION SAFETY RULE**: RVNKQuests operates on live Minecraft servers and must maintain server stability and security at all times.

### Production Server Restrictions

- **ONLY READ OPERATIONS** are permitted on production servers without explicit admin approval
- **NO AUTOMATIC QUEST TRIGGERS** in production without thorough testing
- **NO EXPERIMENTAL FEATURES** on production servers

### Permitted Production Operations

- `quest status <questId>` - View quest state information
- `quest info <questId>` - Display quest configuration details
- `quest player <player> list` - View player quest progress
- Quest completion tracking and logging
- Quest objective validation (read-only)

### Development and Test Environments

- **Test and Development**: Full quest operations permitted
- **Production**: Only approved, tested quest features (100% compliance)
- **Configuration enforcement**: Environment detection prevents unsafe operations in production

## Quest Security Requirements

### Quest State Validation

**CRITICAL**: All quest state changes must be validated to prevent quest system corruption.

**Quest State Security Pattern**:

```java
public class QuestSecurityPattern {
    
    public boolean validateStateTransition(QuestState from, QuestState to) {
        // Validate state transition rules
        switch (from) {
            case NOT_STARTED:
                return to == QuestState.TRIGGER_FOUND;
            case TRIGGER_FOUND:
                return to == QuestState.QUEST_ACTIVE || to == QuestState.COMPLETED;
            case QUEST_ACTIVE:
                return to == QuestState.OBJECTIVE_FOUND || to == QuestState.COMPLETED;
            case OBJECTIVE_FOUND:
                return to == QuestState.COMPLETED;
            default:
                return false;
        }
    }
    
    public void secureStateAdvance(QuestState newState) {
        if (!validateStateTransition(getCurrentState(), newState)) {
            logger.warn("Invalid quest state transition attempted: {} -> {}", 
                getCurrentState(), newState);
            return;
        }
        
        // Proceed with validated state change
        advanceState(newState);
    }
}
```

### Player Permission Validation

```java
public class QuestPermissionSecurity {
    
    public boolean validateQuestAccess(Player player, String questId) {
        // Basic validation
        if (player == null || !player.isOnline()) {
            return false;
        }
        
        // Permission check
        if (!player.hasPermission("rvnkquests.quest." + questId)) {
            logger.warn("Player {} attempted to access quest {} without permission", 
                player.getName(), questId);
            return false;
        }
        
        // World restriction check
        String requiredWorld = config.getString("quests." + questId + ".world");
        if (requiredWorld != null && !player.getWorld().getName().equals(requiredWorld)) {
            logger.debug("Player {} not in required world {} for quest {}", 
                player.getName(), requiredWorld, questId);
            return false;
        }
        
        return true;
    }
}
```

## Configuration Security

### Secure Configuration Loading

**CRITICAL**: This project uses **file-based configuration** with validation for security.

**Configuration Security Benefits**:
- No sensitive credentials stored in config files
- Input validation prevents configuration injection
- Environment-based restrictions for production safety
- Automatic validation on configuration reload

**Secure Configuration Pattern**:

```java
public class SecureConfigManager {
    
    public void loadConfiguration() {
        try {
            plugin.saveDefaultConfig();
            plugin.reloadConfig();
            
            // Validate all configuration values
            validateConfiguration();
            
        } catch (Exception e) {
            logger.error("Failed to load configuration securely", e);
            // Use safe defaults if configuration fails
            useSafeDefaults();
        }
    }
    
    private void validateConfiguration() {
        FileConfiguration config = plugin.getConfig();
        
        // Validate quest configurations
        ConfigurationSection questsSection = config.getConfigurationSection("quests");
        if (questsSection != null) {
            for (String questId : questsSection.getKeys(false)) {
                validateQuestConfig(questId, questsSection.getConfigurationSection(questId));
            }
        }
    }
    
    private void validateQuestConfig(String questId, ConfigurationSection questConfig) {
        // Validate quest world exists
        String worldName = questConfig.getString("world");
        if (worldName != null && plugin.getServer().getWorld(worldName) == null) {
            logger.warn("Quest {} references invalid world: {}", questId, worldName);
        }
        
        // Validate numeric values are within reasonable bounds
        int timeout = questConfig.getInt("timeout", 30);
        if (timeout < 1 || timeout > 3600) {
            logger.warn("Quest {} has invalid timeout value: {}", questId, timeout);
        }
    }
}
```

**Key Configuration Security Principles**:
- **Validate all inputs** from configuration files
- **Use safe defaults** when configuration is invalid
- **Log security-relevant configuration changes**
- **Prevent configuration injection attacks**

### Configuration Input Validation

**NEVER trust configuration input without validation**:

```java
// CORRECT: Validate configuration input
public String getValidatedWorldName(String questId) {
    String worldName = config.getString("quests." + questId + ".world", "world");
    
    // Validate world exists
    if (plugin.getServer().getWorld(worldName) == null) {
        logger.warn("Invalid world {} for quest {}, using default", worldName, questId);
        return "world";
    }
    
    return worldName;
}

// WRONG: Using configuration values directly
public String getUnsafeWorldName(String questId) {
    return config.getString("quests." + questId + ".world"); // Could be null or invalid
}
```

## Production Safety Patterns

### Environment-Based Restrictions

```java
public class EnvironmentSecurity {
    
    public boolean isProductionEnvironment() {
        // Detect production based on server configuration
        return config.getBoolean("production", false) || 
               plugin.getServer().getPort() == 25565;
    }
    
    public boolean validateProductionOperation(String operation) {
        if (!isProductionEnvironment()) {
            return true; // Allow all operations in non-production
        }
        
        // Whitelist of allowed production operations
        Set<String> allowedOps = Set.of(
            "quest.status", "quest.info", "quest.player.list"
        );
        
        return allowedOps.contains(operation);
    }
}
```

### Runtime Safety Checks

```java
public class RuntimeSafety {
    
    public void executeQuestCommand(Player player, String[] args) {
        try {
            // Validate environment safety
            if (!validateProductionSafety(args)) {
                player.sendMessage("§cOperation not permitted in production environment");
                return;
            }
            
            // Validate permissions
            if (!validatePermissions(player, args)) {
                player.sendMessage("§cInsufficient permissions");
                return;
            }
            
            // Execute command safely
            processQuestCommand(player, args);
            
        } catch (Exception e) {
            logger.error("Quest command execution failed", e);
            player.sendMessage("§cQuest command failed: " + e.getMessage());
        }
    }
}
```

## Input Validation Security

### Command Input Validation

```java
public class CommandSecurity {
    
    public boolean validateCommandInput(String[] args) {
        if (args == null || args.length == 0) {
            return false;
        }
        
        // Validate command arguments
        for (String arg : args) {
            if (!isValidArgument(arg)) {
                logger.warn("Invalid command argument detected: {}", arg);
                return false;
            }
        }
        
        return true;
    }
    
    private boolean isValidArgument(String arg) {
        // Prevent command injection
        if (arg.contains("../") || arg.contains("..\\")) {
            return false;
        }
        
        // Limit argument length
        if (arg.length() > 100) {
            return false;
        }
        
        // Allow only alphanumeric, underscore, and hyphen
        return arg.matches("^[a-zA-Z0-9_-]+$");
    }
}
```

## Quest Data Protection

### Player Data Security

```java
public class PlayerDataSecurity {
    
    public CompletableFuture<QuestProgress> getPlayerQuestProgressSecurely(UUID playerId, String questId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate player exists
                if (playerId == null) {
                    throw new SecurityException("Invalid player ID");
                }
                
                // Validate quest exists
                if (!isValidQuest(questId)) {
                    throw new SecurityException("Invalid quest ID: " + questId);
                }
                
                // Load data securely
                return dataService.getQuestProgress(playerId, questId);
                
            } catch (Exception e) {
                logger.error("Failed to load player quest data securely", e);
                return QuestProgress.empty();
            }
        });
    }
}
```

## Security Checklist

Before deploying quest code:

- [ ] No sensitive information in configuration files
- [ ] All configuration values validated on load
- [ ] Production safety checks implemented
- [ ] Input validation for all player commands
- [ ] Quest state transitions validated
- [ ] Player permissions checked before quest access
- [ ] Error handling prevents information disclosure
- [ ] Logging excludes sensitive player information

## Key Security Principles

1. **Defense in Depth**: Multiple layers of security validation
2. **Least Privilege**: Production environments get minimal quest modification permissions
3. **Fail Secure**: Default to denying quest operations when validation fails
4. **Input Validation**: Never trust player input or configuration without validation
5. **Audit Logging**: Log all security-relevant quest operations

## Security Incident Response

If security issues are identified:

1. **Immediately disable** affected quest features
2. **Review logs** for unauthorized quest access or modifications
3. **Validate configuration** for potential security misconfigurations
4. **Update quest validation** rules to prevent similar issues
5. **Test thoroughly** before re-enabling quest features

---

**Remember**: Security is not optional. Every quest operation must be validated against security requirements.