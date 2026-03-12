package io.bluewiz.backrooms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Duration;
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
        Player target;

        if (args.length >= 1) {
            if (!sender.hasPermission("backrooms.send")) {
                sender.sendMessage(Component.text("You can't send others. You can barely save yourself.")
                        .color(NamedTextColor.RED));
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found — already lost in the walls?")
                        .color(NamedTextColor.GRAY));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("Usage: /backrooms <player>");
            return true;
        }

        sendToBackrooms(target, sender);
        return true;
    }

    private void sendToBackrooms(Player target, CommandSender sender) {
        // Already here — sending them to spawn would be disorienting and unintended.
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

        var world = backroomsWorld.getWorld();
        if (world == null) {
            sender.sendMessage(Component.text("The Backrooms are unreachable. (World failed to load.)")
                    .color(NamedTextColor.RED));
            return;
        }

        plugin.saveGameMode(target);
        plugin.saveReturnLocation(target);
        target.teleport(backroomsWorld.getSpawnLocation());

        if (sender != target) {
            sender.sendMessage(Component.text(target.getName() + " has been sent to the Backrooms. Godspeed.")
                    .color(NamedTextColor.YELLOW));
        }

        plugin.getLogger().info(target.getName() + " entered the Backrooms.");

        // Title fires after the world-load screen clears.
        // Chat messages during cross-world teleport are silently dropped by the client.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            target.setGameMode(org.bukkit.GameMode.ADVENTURE);
            target.showTitle(Title.title(
                    Component.text("YOU HAVE NO-CLIPPED")
                            .color(NamedTextColor.YELLOW)
                            .decorate(TextDecoration.BOLD),
                    Component.text("there is no escape")
                            .color(NamedTextColor.DARK_GRAY)
                            .decorate(TextDecoration.ITALIC),
                    Title.Times.times(
                            Duration.ofMillis(500),
                            Duration.ofSeconds(3),
                            Duration.ofSeconds(1)
                    )
            ));
            target.sendMessage(Component.empty());
            target.sendMessage(Component.text("The smell of moist carpet fills your lungs.")
                    .color(NamedTextColor.GOLD));
            target.sendMessage(Component.text("The fluorescent lights hum. You are alone.")
                    .color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
            target.sendMessage(Component.empty());
        }, 40L);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("backrooms.send")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
