package org.fourz.RVNKQuests.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.fourz.RVNKQuests.RVNKQuests;
import org.fourz.RVNKQuests.quest.Quest;
import org.fourz.RVNKQuests.quest.QuestState;
import org.fourz.RVNKQuests.trigger.generic.GenericLocationProximityTrigger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Subcommand: quest probe — inspect which LOCATION_PROXIMITY triggers cover a coordinate.
 *
 * Usage:
 *   quest probe <player>
 *   quest probe <world> <x> <y> <z> [radius]
 *
 * Console-capable. Admin tool for debugging trigger placement.
 */
public class QuestProbeSubCommand extends BaseSubCommand {

    public QuestProbeSubCommand(RVNKQuests plugin) {
        super(plugin, "probe",
              "Probe a location for LOCATION_PROXIMITY triggers",
              "/quest probe <player> | <world> <x> <y> <z> [radius]",
              "rvnkquests.admin.probe",
              false);
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        String worldName;
        double px, py, pz;
        double probeRadius = 0; // 0 = point check only (use trigger's own radius)

        // Try player form: quest probe <playerName>
        if (args.length == 1 || (args.length == 2 && isNumeric(args[1]) == false && Bukkit.getPlayerExact(args[0]) != null)) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sendErrorMessage(sender, "Player not found or not online: " + args[0]);
                return true;
            }
            worldName = target.getWorld().getName();
            px = target.getLocation().getX();
            py = target.getLocation().getY();
            pz = target.getLocation().getZ();
        } else if (args.length >= 4) {
            // Coordinate form: quest probe <world> <x> <y> <z> [radius]
            worldName = args[0];
            try {
                px = Double.parseDouble(args[1]);
                py = Double.parseDouble(args[2]);
                pz = Double.parseDouble(args[3]);
                if (args.length >= 5) {
                    probeRadius = Double.parseDouble(args[4]);
                }
            } catch (NumberFormatException e) {
                sendErrorMessage(sender, "Invalid coordinates. Usage: /quest probe <world> <x> <y> <z> [radius]");
                return true;
            }
        } else {
            sendUsage(sender);
            return true;
        }

        runProbe(sender, worldName, px, py, pz, probeRadius);
        return true;
    }

    private void runProbe(CommandSender sender, String worldName, double px, double py, double pz, double probeRadius) {
        sender.sendMessage("§6Probing " + worldName + " (" + fmt(px) + ", " + fmt(py) + ", " + fmt(pz) + ")"
                + (probeRadius > 0 ? " r=" + fmt(probeRadius) : "") + "...");

        List<String> hits = new ArrayList<>();
        List<String> misses = new ArrayList<>();

        for (Quest quest : plugin.getQuestManager().getAllQuests()) {
            for (QuestState state : QuestState.values()) {
                List<Listener> listeners = quest.createListenersForState(state);
                for (Listener listener : listeners) {
                    if (!(listener instanceof GenericLocationProximityTrigger)) continue;
                    GenericLocationProximityTrigger trigger = (GenericLocationProximityTrigger) listener;

                    ProbeFields f = readFields(trigger);
                    if (f == null) continue;
                    if (!f.worldName.equalsIgnoreCase(worldName)) continue;

                    double dx = px - f.x;
                    double dy = py - f.y;
                    double dz = pz - f.z;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    double dist = Math.sqrt(distSq);
                    double triggerRadius = Math.sqrt(f.radiusSquared);

                    boolean inRange = probeRadius > 0
                            ? dist <= (triggerRadius + probeRadius)
                            : distSq <= f.radiusSquared;

                    String line = "  §f" + quest.getId()
                            + " §7[" + f.requiredState + "→" + f.advanceState + "]"
                            + " §7@ " + fmt(f.x) + "," + fmt(f.y) + "," + fmt(f.z)
                            + " r=" + fmt(triggerRadius)
                            + " dist=" + fmt(dist);

                    if (inRange) {
                        hits.add("§a✓" + line);
                    } else {
                        misses.add("§c✗" + line);
                    }
                }
            }
        }

        if (hits.isEmpty() && misses.isEmpty()) {
            sender.sendMessage("§7No LOCATION_PROXIMITY triggers found in world '" + worldName + "'.");
            return;
        }

        if (!hits.isEmpty()) {
            sender.sendMessage("§aIn range (" + hits.size() + "):");
            hits.forEach(sender::sendMessage);
        }
        if (!misses.isEmpty()) {
            sender.sendMessage("§cOut of range in this world (" + misses.size() + "):");
            misses.forEach(sender::sendMessage);
        }
    }

    private static class ProbeFields {
        String worldName;
        double x, y, z, radiusSquared;
        QuestState requiredState, advanceState;
    }

    private ProbeFields readFields(GenericLocationProximityTrigger trigger) {
        try {
            ProbeFields f = new ProbeFields();
            f.worldName = (String) getField(trigger, "worldName");
            f.x = (double) getField(trigger, "x");
            f.y = (double) getField(trigger, "y");
            f.z = (double) getField(trigger, "z");
            f.radiusSquared = (double) getField(trigger, "radiusSquared");
            f.requiredState = (QuestState) getField(trigger, "requiredState");
            f.advanceState = (QuestState) getField(trigger, "advanceState");
            return f;
        } catch (Exception e) {
            logger.warning("probe: could not read trigger fields — " + e.getMessage());
            return null;
        }
    }

    private Object getField(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(obj);
    }

    private static boolean isNumeric(String s) {
        try { Double.parseDouble(s); return true; } catch (NumberFormatException e) { return false; }
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.format("%.1f", d);
    }

    private void sendUsage(CommandSender sender) {
        sendInfoMessage(sender, "Usage: /quest probe <player>  — probe player's current location");
        sendInfoMessage(sender, "       /quest probe <world> <x> <y> <z> [radius]  — probe coordinates");
    }

    @Override
    protected List<String> getTabCompletionOptions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> opts = new ArrayList<>();
            Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .forEach(opts::add);
            Bukkit.getWorlds().stream()
                    .map(w -> w.getName())
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .forEach(opts::add);
            return opts;
        }
        return List.of();
    }
}
