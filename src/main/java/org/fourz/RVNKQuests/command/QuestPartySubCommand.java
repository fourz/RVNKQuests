package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.party.QuestParty;
import org.fourz.RVNKQuests.party.QuestPartyService;
import org.fourz.RVNKQuests.party.QuestPartyService.PartyResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code /quest party <invite|accept|leave|list|disband>} — quest party management (#1982).
 *
 * <p>Player-only in v1: every verb acts on "your" party, and the console has none. An admin
 * {@code list <player>} form is a filed follow-up.</p>
 */
public class QuestPartySubCommand extends BaseSubCommand {

    private static final List<String> VERBS = List.of("invite", "accept", "leave", "list", "disband");

    public QuestPartySubCommand(RVNKQuests plugin) {
        super(plugin, "party", "Manage your quest party — share beats with players questing beside you",
              "/quest party <invite <player>|accept|leave|list|disband>", "rvnkquests.party", true);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        Player player = (Player) sender; // playerOnly=true guarantees this
        QuestPartyService parties = plugin.getQuestPartyService();
        if (parties == null || !parties.isEnabled()) {
            sendErrorMessage(sender, "Quest parties are disabled on this server.");
            return true;
        }
        if (!validateArgs(sender, args, 1)) {
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "invite" -> handleInvite(player, parties, args);
            case "accept" -> handleAccept(player, parties);
            case "leave" -> handleLeave(player, parties);
            case "list" -> handleList(player, parties);
            case "disband" -> handleDisband(player, parties);
            default -> sendErrorMessage(sender, "Unknown party action: " + args[0]
                    + " — use " + String.join("/", VERBS));
        }
        return true;
    }

    private void handleInvite(Player player, QuestPartyService parties, String[] args) {
        if (args.length < 2) {
            sendErrorMessage(player, "Usage: /quest party invite <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sendErrorMessage(player, "Player not online: " + args[1]);
            return;
        }
        switch (parties.invite(player, target)) {
            case OK -> {
                sendSuccessMessage(player, "Invited " + target.getName()
                        + " — they have " + (parties.getInviteTimeoutMillis() / 1000) + "s to accept.");
                target.sendMessage("§e⚠ §f" + player.getName()
                        + "§7 invited you to a quest party. §fRun /quest party accept§7 to join.");
            }
            case SELF_INVITE -> sendErrorMessage(player, "You cannot invite yourself.");
            case TARGET_IN_PARTY -> sendErrorMessage(player, target.getName() + " is already in a party.");
            case NOT_LEADER -> sendErrorMessage(player, "Only the party leader can invite.");
            case PARTY_FULL -> sendErrorMessage(player, "Your party is full (max "
                    + parties.getMaxSize() + ").");
            default -> sendErrorMessage(player, "Could not send the invite.");
        }
    }

    private void handleAccept(Player player, QuestPartyService parties) {
        PartyResult result = parties.accept(player);
        switch (result) {
            case OK -> {
                QuestParty party = parties.getParty(player.getUniqueId());
                sendSuccessMessage(player, "You joined the party (" + party.size() + " members).");
                for (UUID member : party.getMembers()) {
                    if (member.equals(player.getUniqueId())) continue;
                    Player p = Bukkit.getPlayer(member);
                    if (p != null) p.sendMessage("§a✓ §f" + player.getName() + "§7 joined the quest party.");
                }
            }
            case ALREADY_IN_PARTY -> sendErrorMessage(player, "You are already in a party — leave it first.");
            case NO_INVITE -> sendErrorMessage(player, "You have no pending invite.");
            case INVITE_EXPIRED -> sendErrorMessage(player, "That invite has expired.");
            case PARTY_FULL -> sendErrorMessage(player, "That party filled up while you decided.");
            default -> sendErrorMessage(player, "Could not join the party.");
        }
    }

    private void handleLeave(Player player, QuestPartyService parties) {
        if (parties.leave(player) == PartyResult.NO_PARTY) {
            sendErrorMessage(player, "You are not in a party.");
        } else {
            sendSuccessMessage(player, "You left the party.");
        }
    }

    private void handleList(Player player, QuestPartyService parties) {
        QuestParty party = parties.getParty(player.getUniqueId());
        if (party == null) {
            sendInfoMessage(player, "You are not in a party. Start one with /quest party invite <player>.");
            return;
        }
        sendInfoMessage(player, "Quest party (" + party.size() + "/" + parties.getMaxSize() + "):");
        for (UUID member : party.getMembers()) {
            Player p = Bukkit.getPlayer(member);
            String memberName = p != null ? p.getName() : member.toString();
            sendMessage(player, "§7 - §f" + memberName + (party.isLeader(member) ? " §6(leader)" : ""));
        }
    }

    private void handleDisband(Player player, QuestPartyService parties) {
        switch (parties.disband(player)) {
            case OK -> sendSuccessMessage(player, "Party disbanded.");
            case NO_PARTY -> sendErrorMessage(player, "You are not in a party.");
            case NOT_LEADER -> sendErrorMessage(player, "Only the party leader can disband.");
            default -> sendErrorMessage(player, "Could not disband the party.");
        }
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return VERBS.stream().filter(v -> v.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("invite") && sender instanceof Player self) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(self) && p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    names.add(p.getName());
                }
            }
            return names;
        }
        return List.of();
    }

    /** Worked examples served by {@code /quest help party} (#1981). */
    @Override
    public java.util.List<String> getExamples() {
        return java.util.List.of(
                "/quest party invite Shad0melt",
                "/quest party accept",
                "/quest party list",
                "/quest party leave",
                "  the leader leaving promotes the oldest member",
                "/quest party disband",
                "  leader only - everyone is released at once",
                "Party advancement needs presence: same world, within 5x the trigger",
                "radius when a member fires the beat. Miss it and you catch up solo",
                "at the normal radius - nothing is lost.",
                "Prerequisites stay personal: an unready member is told so in chat.");
    }
}
