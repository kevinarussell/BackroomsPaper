package dev.backrooms.backroomspaper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.time.Duration;

public class BackroomsListener implements Listener {

    private final BackroomsPlugin plugin;
    private final BackroomsWorld  backroomsWorld;

    public BackroomsListener(BackroomsPlugin plugin, BackroomsWorld backroomsWorld) {
        this.plugin         = plugin;
        this.backroomsWorld = backroomsWorld;
    }

    /**
     * Cancel all natural mob spawning in the backrooms.
     * Flat enclosed worlds spawn mobs everywhere — none of them belong here.
     */
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (!e.getEntity().getWorld().getName().equals(BackroomsWorld.WORLD_NAME)) return;
        SpawnReason reason = e.getSpawnReason();
        if (reason == SpawnReason.NATURAL
                || reason == SpawnReason.DEFAULT) {
            e.setCancelled(true);
        }
    }

    /** Dying in the backrooms respawns you in the backrooms. Dying is not an escape. */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        if (player.getWorld().getName().equals(BackroomsWorld.WORLD_NAME)) {
            e.setRespawnLocation(backroomsWorld.getSpawnLocation());
            player.sendMessage(Component.text("You thought death would save you.")
                    .color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC));
        }
    }

    /**
     * The escape door.
     *
     * A freestanding dark oak door frame generated very rarely (~1 per 500 rooms) in the
     * backrooms world. It stands completely alone in the middle of the room — wrong material,
     * wrong context, wrong everything. Right-clicking it sends you home.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        var block = e.getClickedBlock();
        if (block == null || block.getType() != Material.DARK_OAK_DOOR) return;
        if (!block.getWorld().getName().equals(BackroomsWorld.WORLD_NAME)) return;

        Player player = e.getPlayer();
        e.setCancelled(true); // don't actually toggle the door

        // Eerie, understated title — contrasts with the loud entry title deliberately
        player.showTitle(Title.title(
                Component.text("...").color(NamedTextColor.WHITE),
                Component.text("a door that shouldn't be here")
                        .color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC),
                Title.Times.times(
                        Duration.ofMillis(300),
                        Duration.ofSeconds(2),
                        Duration.ofMillis(500)
                )
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.5f, 0.8f);

        var returnLocation = plugin.consumeReturnLocation(player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.teleport(returnLocation);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("The hum fades. You remember what silence sounds like.")
                        .color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
                player.sendMessage(Component.empty());
            }, 40L);
        }, 30L);

        plugin.getLogger().info(player.getName() + " escaped the Backrooms.");
    }
}
