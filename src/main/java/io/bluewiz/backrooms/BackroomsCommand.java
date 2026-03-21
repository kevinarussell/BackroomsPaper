package io.bluewiz.backrooms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class BackroomsCommand implements CommandExecutor, TabCompleter {

    private final BackroomsPlugin plugin;
    private final BackroomsWorld backroomsWorld;

    public BackroomsCommand(BackroomsPlugin plugin, BackroomsWorld backroomsWorld) {
        this.plugin = plugin;
        this.backroomsWorld = backroomsWorld;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1) {
            switch (args[0].toLowerCase()) {
                case "reload" -> { handleReload(sender); return true; }
                case "leave"  -> { handleLeave(sender, args); return true; }
                case "list"   -> { handleList(sender); return true; }
                default       -> { handleSend(sender, args[0]); return true; }
            }
        }

        // No args — send yourself in
        if (sender instanceof Player p) {
            sendToBackrooms(p, sender);
        } else {
            sender.sendMessage("Usage: /backrooms <player|reload|leave|list>");
        }
        return true;
    }

    private void handleSend(CommandSender sender, String targetName) {
        if (!sender.hasPermission("backrooms.send")) {
            sender.sendMessage(Component.text("You can't send others. You can barely save yourself.")
                    .color(NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found — already lost in the walls?")
                    .color(NamedTextColor.GRAY));
            return;
        }
        sendToBackrooms(target, sender);
    }

    private void sendToBackrooms(Player target, CommandSender sender) {
        if (target.getWorld().getName().equals(BackroomsWorld.WORLD_NAME)) {
            if (sender == target) {
                target.sendMessage(Component.text("You are already here. There is nowhere else to go.")
                        .color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC));
            } else {
                sender.sendMessage(Component.text(target.getName() + " is already in the Backrooms.")
                        .color(NamedTextColor.GRAY));
            }
            return;
        }

        if (backroomsWorld.getWorld() == null) {
            sender.sendMessage(Component.text("The Backrooms are unreachable. (World failed to load.)")
                    .color(NamedTextColor.RED));
            return;
        }

        if (sender != target) {
            sender.sendMessage(Component.text(target.getName() + " has been sent to the Backrooms. Godspeed.")
                    .color(NamedTextColor.YELLOW));
        }

        plugin.sendToBackrooms(target);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("backrooms.admin")) {
            sender.sendMessage(Component.text("No permission.").color(NamedTextColor.RED));
            return;
        }
        plugin.reloadConfiguration();
        sender.sendMessage(Component.text("Backrooms configuration reloaded.")
                .color(NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Note: generation settings (wall density, escape doors, furniture, levels) only affect new chunks.")
                .color(NamedTextColor.GRAY));
    }

    private void handleLeave(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backrooms.admin")) {
            sender.sendMessage(Component.text("No permission.").color(NamedTextColor.RED));
            return;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found.").color(NamedTextColor.RED));
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("Usage: /backrooms leave <player>");
            return;
        }

        if (!target.getWorld().getName().equals(BackroomsWorld.WORLD_NAME)) {
            sender.sendMessage(Component.text(target.getName() + " is not in the Backrooms.")
                    .color(NamedTextColor.GRAY));
            return;
        }

        var returnLocation = plugin.consumeReturnLocation(target);
        target.teleport(returnLocation);
        plugin.restoreGameMode(target);

        target.sendMessage(Component.text("You have been pulled out of the Backrooms.")
                .color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
        if (sender != target) {
            sender.sendMessage(Component.text(target.getName() + " has been removed from the Backrooms.")
                    .color(NamedTextColor.GREEN));
        }
        plugin.getLogger().info(target.getName() + " was pulled out of the Backrooms by " + sender.getName() + ".");
    }

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("backrooms.admin")) {
            sender.sendMessage(Component.text("No permission.").color(NamedTextColor.RED));
            return;
        }

        var world = backroomsWorld.getWorld();
        if (world == null) {
            sender.sendMessage(Component.text("Backrooms world is not loaded.").color(NamedTextColor.RED));
            return;
        }

        var players = world.getPlayers();
        if (players.isEmpty()) {
            sender.sendMessage(Component.text("No players in the Backrooms.").color(NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("Players in the Backrooms (" + players.size() + "):")
                    .color(NamedTextColor.YELLOW));
            for (Player p : players) {
                var level = backroomsWorld.getLevelAt(p.getLocation().getBlockX(), p.getLocation().getBlockZ());
                sender.sendMessage(Component.text("  " + p.getName() + " — " + level.name().toLowerCase())
                        .color(NamedTextColor.GRAY));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new java.util.ArrayList<>(
                    Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                            .collect(Collectors.toList()));
            if (sender.hasPermission("backrooms.admin")) {
                for (String sub : List.of("reload", "leave", "list")) {
                    if (sub.startsWith(args[0].toLowerCase())) {
                        completions.add(sub);
                    }
                }
            }
            return completions;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("leave") && sender.hasPermission("backrooms.admin")) {
            var world = backroomsWorld.getWorld();
            if (world != null) {
                return world.getPlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        return List.of();
    }
}
