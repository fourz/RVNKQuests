# RVNKQuests Message Formatting Standards

## Player Message Formatting

### Color and Style Guidelines

Use consistent color coding for different types of messages:

```java
public class QuestMessageFormatter {
    // Standard color scheme for quest messages
    public static final ChatColor SUCCESS = ChatColor.GREEN;
    public static final ChatColor ERROR = ChatColor.RED;
    public static final ChatColor WARNING = ChatColor.YELLOW;
    public static final ChatColor INFO = ChatColor.AQUA;
    public static final ChatColor HIGHLIGHT = ChatColor.GOLD;
    public static final ChatColor NEUTRAL = ChatColor.GRAY;
    
    public void sendSuccessMessage(Player player, String message) {
        player.sendMessage(SUCCESS + message);
    }
    
    public void sendErrorMessage(Player player, String message) {
        player.sendMessage(ERROR + message);
    }
    
    public void sendQuestUpdate(Player player, String questName, String update) {
        player.sendMessage(INFO + "Quest " + HIGHLIGHT + questName + INFO + ": " + update);
    }
}
```

### Quest Progress Messages

```java
public class QuestProgressFormatter {
    
    public void sendProgressUpdate(Player player, String questId, int current, int required) {
        String progressBar = createProgressBar(current, required, 20);
        
        player.sendMessage(ChatColor.AQUA + "Quest Progress: " + 
                          ChatColor.GOLD + questId);
        player.sendMessage(ChatColor.WHITE + progressBar + ChatColor.GRAY + 
                          " (" + current + "/" + required + ")");
    }
    
    private String createProgressBar(int current, int total, int length) {
        int filled = (int) ((double) current / total * length);
        
        StringBuilder bar = new StringBuilder();
        bar.append(ChatColor.GREEN);
        
        // Filled portion
        for (int i = 0; i < filled; i++) {
            bar.append("█");
        }
        
        bar.append(ChatColor.GRAY);
        
        // Empty portion
        for (int i = filled; i < length; i++) {
            bar.append("░");
        }
        
        return bar.toString();
    }
}
```

### Interactive Messages

```java
public class InteractiveQuestMessages {
    
    public void sendClickableQuestInfo(Player player, Quest quest) {
        // Create clickable text component
        TextComponent questName = new TextComponent(quest.getName());
        questName.setColor(net.md_5.bungee.api.ChatColor.GOLD);
        questName.setBold(true);
        
        // Add hover text
        TextComponent hoverText = new TextComponent("Click for quest details");
        hoverText.setColor(net.md_5.bungee.api.ChatColor.GRAY);
        questName.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT, 
            new ComponentBuilder(quest.getDescription()).create()
        ));
        
        // Add click action
        questName.setClickEvent(new ClickEvent(
            ClickEvent.Action.RUN_COMMAND, 
            "/quest info " + quest.getId()
        ));
        
        // Send message
        player.spigot().sendMessage(questName);
    }
    
    public void sendQuestChoiceMenu(Player player, List<Quest> availableQuests) {
        player.sendMessage(ChatColor.GOLD + "═══ Available Quests ═══");
        
        for (int i = 0; i < availableQuests.size(); i++) {
            Quest quest = availableQuests.get(i);
            
            TextComponent option = new TextComponent((i + 1) + ". " + quest.getName());
            option.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
            
            option.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/quest start " + quest.getId()
            ));
            
            option.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(quest.getDescription()).create()
            ));
            
            player.spigot().sendMessage(option);
        }
    }
}
```

## Command Response Formatting

### Command Success Messages

```java
public class CommandResponseFormatter {
    
    public void sendCommandSuccess(CommandSender sender, String action) {
        sender.sendMessage(ChatColor.GREEN + "✓ " + action + " completed successfully.");
    }
    
    public void sendCommandSuccess(CommandSender sender, String action, String details) {
        sender.sendMessage(ChatColor.GREEN + "✓ " + action + " completed successfully.");
        sender.sendMessage(ChatColor.GRAY + "  " + details);
    }
    
    public void sendQuestStateChange(CommandSender sender, String questId, 
                                    QuestState oldState, QuestState newState) {
        sender.sendMessage(ChatColor.GREEN + "Quest state updated:");
        sender.sendMessage(ChatColor.GRAY + "  Quest: " + ChatColor.GOLD + questId);
        sender.sendMessage(ChatColor.GRAY + "  " + oldState + " → " + ChatColor.AQUA + newState);
    }
}
```

### Command Error Messages

```java
public class CommandErrorFormatter {
    
    public void sendUsageError(CommandSender sender, String usage) {
        sender.sendMessage(ChatColor.RED + "Usage: " + ChatColor.YELLOW + usage);
    }
    
    public void sendPermissionError(CommandSender sender, String command) {
        sender.sendMessage(ChatColor.RED + "You don't have permission to use " + command);
    }
    
    public void sendNotFoundError(CommandSender sender, String itemType, String itemName) {
        sender.sendMessage(ChatColor.RED + itemType + " not found: " + ChatColor.YELLOW + itemName);
    }
    
    public void sendConfigurationError(CommandSender sender, String issue) {
        sender.sendMessage(ChatColor.RED + "Configuration Error: " + issue);
        sender.sendMessage(ChatColor.YELLOW + "Please check your configuration and try again.");
    }
}
```

### Help and Information Messages

```java
public class HelpMessageFormatter {
    
    public void sendQuestHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "═══ RVNKQuests Commands ═══");
        
        Map<String, String> commands = new LinkedHashMap<>();
        commands.put("/quest help", "Show this help message");
        commands.put("/quest list", "List all available quests");
        commands.put("/quest info <quest>", "Show detailed quest information");
        commands.put("/quest reload", "Reload quest configuration");
        
        for (Map.Entry<String, String> entry : commands.entrySet()) {
            sender.sendMessage(ChatColor.YELLOW + entry.getKey() + 
                              ChatColor.GRAY + " - " + entry.getValue());
        }
    }
    
    public void sendQuestInfo(CommandSender sender, Quest quest) {
        sender.sendMessage(ChatColor.GOLD + "═══ Quest Information ═══");
        sender.sendMessage(ChatColor.YELLOW + "Name: " + ChatColor.WHITE + quest.getName());
        sender.sendMessage(ChatColor.YELLOW + "ID: " + ChatColor.WHITE + quest.getId());
        sender.sendMessage(ChatColor.YELLOW + "State: " + getStateColor(quest.getCurrentState()) + 
                          quest.getCurrentState());
        sender.sendMessage(ChatColor.YELLOW + "Description: " + ChatColor.WHITE + 
                          quest.getDescription());
    }
    
    private ChatColor getStateColor(QuestState state) {
        switch (state) {
            case NOT_STARTED: return ChatColor.GRAY;
            case TRIGGER_FOUND: return ChatColor.YELLOW;
            case QUEST_ACTIVE: return ChatColor.GREEN;
            case OBJECTIVE_FOUND: return ChatColor.AQUA;
            case COMPLETED: return ChatColor.GOLD;
            default: return ChatColor.WHITE;
        }
    }
}
```

## Narrative and Lore Formatting

### Lore Text Presentation

```java
public class LoreTextFormatter {
    
    public void displayLoreEntry(Player player, String loreContent) {
        // Add atmospheric formatting to lore text
        player.sendMessage("");
        player.sendMessage(ChatColor.DARK_PURPLE + "═══" + ChatColor.LIGHT_PURPLE + 
                          " Ancient Knowledge " + ChatColor.DARK_PURPLE + "═══");
        
        // Split content into lines and format
        String[] lines = loreContent.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                player.sendMessage("");
            } else {
                player.sendMessage(ChatColor.GRAY + "  " + ChatColor.ITALIC + line);
            }
        }
        
        player.sendMessage(ChatColor.DARK_PURPLE + "═══════════════════════");
        player.sendMessage("");
    }
    
    public void displayQuestNarrative(Player player, String questId, String narrative) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "◆ " + ChatColor.YELLOW + "Quest Chronicle" + 
                          ChatColor.GOLD + " ◆");
        player.sendMessage("");
        
        // Format narrative text with proper line wrapping
        List<String> wrappedLines = wrapText(narrative, 50);
        for (String line : wrappedLines) {
            player.sendMessage(ChatColor.YELLOW + line);
        }
        
        player.sendMessage("");
    }
    
    private List<String> wrapText(String text, int lineLength) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > lineLength) {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }
            }
            
            if (currentLine.length() > 0) {
                currentLine.append(" ");
            }
            currentLine.append(word);
        }
        
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        
        return lines;
    }
}
```

### Quest Announcement Formatting

```java
public class QuestAnnouncementFormatter {
    
    public void announceQuestStart(String questName) {
        String message = ChatColor.GOLD + "⚡ " + ChatColor.YELLOW + "A new quest has begun: " +
                        ChatColor.GOLD + questName + ChatColor.YELLOW + "!";
        
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(message);
        Bukkit.broadcastMessage(ChatColor.GRAY + "Players nearby may be able to participate.");
        Bukkit.broadcastMessage("");
    }
    
    public void announceQuestCompletion(String questName, List<String> participants) {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "🎉 Quest Completed: " + 
                              ChatColor.YELLOW + questName);
        
        if (participants.size() <= 3) {
            String participantList = String.join(", ", participants);
            Bukkit.broadcastMessage(ChatColor.GRAY + "Completed by: " + 
                                  ChatColor.WHITE + participantList);
        } else {
            Bukkit.broadcastMessage(ChatColor.GRAY + "Completed by " + 
                                  ChatColor.WHITE + participants.size() + 
                                  ChatColor.GRAY + " brave adventurers!");
        }
        Bukkit.broadcastMessage("");
    }
}
```

## Debug and Administrative Messages

### Debug Information Display

```java
public class DebugMessageFormatter {
    
    public void sendDebugInfo(CommandSender sender, Quest quest) {
        sender.sendMessage(ChatColor.DARK_AQUA + "═══ Quest Debug Information ═══");
        sender.sendMessage(ChatColor.AQUA + "Quest ID: " + ChatColor.WHITE + quest.getId());
        sender.sendMessage(ChatColor.AQUA + "Current State: " + ChatColor.WHITE + 
                          quest.getCurrentState());
        sender.sendMessage(ChatColor.AQUA + "Enabled: " + ChatColor.WHITE + 
                          (quest.isEnabled() ? "Yes" : "No"));
        
        // Show active listeners
        List<Listener> listeners = quest.createListenersForState(quest.getCurrentState());
        sender.sendMessage(ChatColor.AQUA + "Active Listeners: " + ChatColor.WHITE + 
                          listeners.size());
        
        for (Listener listener : listeners) {
            sender.sendMessage(ChatColor.GRAY + "  - " + listener.getClass().getSimpleName());
        }
        
        sender.sendMessage(ChatColor.DARK_AQUA + "══════════════════════════════");
    }
    
    public void sendSystemStatus(CommandSender sender, QuestManager questManager) {
        sender.sendMessage(ChatColor.DARK_GREEN + "═══ Quest System Status ═══");
        
        Map<QuestState, Integer> stateCounts = questManager.getQuestStateCounts();
        for (Map.Entry<QuestState, Integer> entry : stateCounts.entrySet()) {
            sender.sendMessage(ChatColor.GREEN + entry.getKey().toString() + ": " +
                              ChatColor.WHITE + entry.getValue());
        }
        
        sender.sendMessage(ChatColor.GREEN + "Total Active Listeners: " + 
                          ChatColor.WHITE + questManager.getActiveListenerCount());
        sender.sendMessage(ChatColor.DARK_GREEN + "══════════════════════════");
    }
}
```

### Validation and Warning Messages

```java
public class ValidationMessageFormatter {
    
    public void sendValidationResults(CommandSender sender, List<ValidationResult> results) {
        sender.sendMessage(ChatColor.GOLD + "═══ Quest Validation Results ═══");
        
        int errors = 0, warnings = 0, passed = 0;
        
        for (ValidationResult result : results) {
            ChatColor statusColor;
            String statusSymbol;
            
            switch (result.getLevel()) {
                case ERROR:
                    statusColor = ChatColor.RED;
                    statusSymbol = "✗";
                    errors++;
                    break;
                case WARNING:
                    statusColor = ChatColor.YELLOW;
                    statusSymbol = "⚠";
                    warnings++;
                    break;
                case SUCCESS:
                default:
                    statusColor = ChatColor.GREEN;
                    statusSymbol = "✓";
                    passed++;
                    break;
            }
            
            sender.sendMessage(statusColor + statusSymbol + " " + result.getMessage());
        }
        
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GREEN + "Passed: " + passed + 
                          ChatColor.YELLOW + " | Warnings: " + warnings + 
                          ChatColor.RED + " | Errors: " + errors);
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════");
    }
}
```

## Console Output Guidelines

### Clean Console Logging

For all console output (server logs), ensure clean formatting without colors or symbols:

```java
public class ConsoleMessageFormatter {
    
    public static String stripColorsForConsole(String message) {
        return ChatColor.stripColor(message);
    }
    
    public void logToConsole(String message) {
        // Console messages should be clean and readable
        String cleanMessage = stripColorsForConsole(message);
        plugin.getLogger().info(cleanMessage);
    }
    
    public void sendCommandFeedback(CommandSender sender, String message) {
        if (sender instanceof ConsoleCommandSender) {
            // Strip colors for console
            sender.sendMessage(stripColorsForConsole(message));
        } else {
            // Keep colors for players
            sender.sendMessage(message);
        }
    }
}
```

### Message Builder Utility

```java
public class QuestMessageBuilder {
    private final List<String> lines = new ArrayList<>();
    private ChatColor currentColor = ChatColor.WHITE;
    
    public QuestMessageBuilder color(ChatColor color) {
        this.currentColor = color;
        return this;
    }
    
    public QuestMessageBuilder line(String text) {
        lines.add(currentColor + text);
        return this;
    }
    
    public QuestMessageBuilder separator() {
        lines.add(ChatColor.GRAY + "────────────────────────");
        return this;
    }
    
    public QuestMessageBuilder header(String title) {
        lines.add(ChatColor.GOLD + "═══ " + title + " ═══");
        return this;
    }
    
    public void send(CommandSender recipient) {
        for (String line : lines) {
            recipient.sendMessage(line);
        }
    }
    
    public List<String> build() {
        return new ArrayList<>(lines);
    }
}
```