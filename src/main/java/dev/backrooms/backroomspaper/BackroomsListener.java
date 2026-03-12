package dev.backrooms.backroomspaper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class BackroomsListener implements Listener {

    private final BackroomsPlugin plugin;
    private final BackroomsWorld  backroomsWorld;
    // Guards against the move event firing twice before the teleport takes effect
    private final Set<UUID> fallingPlayers = new HashSet<>();

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
            // Gamemode is reset by the server after this event fires, so enforce
            // ADVENTURE one tick later once the player is fully respawned.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.setGameMode(org.bukkit.GameMode.ADVENTURE);
                player.sendMessage(Component.text("You thought death would save you.")
                        .color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC));
            }, 1L);
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
            plugin.restoreGameMode(player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("The hum fades. You remember what silence sounds like.")
                        .color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
                player.sendMessage(Component.empty());
            }, 40L);
        }, 30L);

        plugin.getLogger().info(player.getName() + " escaped the Backrooms.");
    }

    /**
     * Pit detection — fires when a player crosses below FLOOR_Y-2, meaning they have
     * fallen into a pit room opening. Teleports them to the safe corner (modX=2, modZ=2)
     * of a random room; that position is guaranteed clear in every room type.
     *
     * The Y check is first so the vast majority of PlayerMoveEvent calls exit immediately.
     */
    @EventHandler
    public void onPlayerFall(PlayerMoveEvent e) {
        if (e.getTo().getY() >= BackroomsWorld.BackroomsGenerator.FLOOR_Y - 2) return;
        Player player = e.getPlayer();
        if (!player.getWorld().getName().equals(BackroomsWorld.WORLD_NAME)) return;
        if (!fallingPlayers.add(player.getUniqueId())) return;

        var rng    = ThreadLocalRandom.current();
        int period = BackroomsWorld.BackroomsGenerator.PERIOD;
        int floorY = BackroomsWorld.BackroomsGenerator.FLOOR_Y;
        int roomX  = rng.nextInt(-100, 101);
        int roomZ  = rng.nextInt(-100, 101);

        // Land at (modX=2, modZ=2) within the room — never a wall, pillar, or pit tile
        Location dest = new Location(backroomsWorld.getWorld(),
                roomX * period + 2.5, floorY + 1, roomZ * period + 2.5);

        player.teleport(dest);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, false, false));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            fallingPlayers.remove(player.getUniqueId());
            player.sendMessage(Component.text("you lost count of how far you fell")
                    .color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC));
        }, 30L);
    }
}
