package io.bluewiz.backrooms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Art;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.EulerAngle;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class BackroomsListener implements Listener {

    private final BackroomsPlugin plugin;
    private final BackroomsWorld  backroomsWorld;
    private boolean noclipTriggerEnabled;
    private double  noclipTriggerChance;
    private boolean longFallTriggerEnabled;
    private double  longFallMinDamage;
    private double  longFallTriggerChance;
    private boolean portalTriggerEnabled;
    private double  portalTriggerChance;
    // Guards against the move event firing twice before the teleport takes effect
    private final Set<UUID> fallingPlayers = new HashSet<>();

    public BackroomsListener(BackroomsPlugin plugin, BackroomsWorld backroomsWorld,
                             boolean noclipTriggerEnabled, double noclipTriggerChance,
                             boolean longFallTriggerEnabled, double longFallMinDamage, double longFallTriggerChance,
                             boolean portalTriggerEnabled, double portalTriggerChance) {
        this.plugin                 = plugin;
        this.backroomsWorld         = backroomsWorld;
        this.noclipTriggerEnabled   = noclipTriggerEnabled;
        this.noclipTriggerChance    = noclipTriggerChance;
        this.longFallTriggerEnabled = longFallTriggerEnabled;
        this.longFallMinDamage      = longFallMinDamage;
        this.longFallTriggerChance  = longFallTriggerChance;
        this.portalTriggerEnabled   = portalTriggerEnabled;
        this.portalTriggerChance    = portalTriggerChance;
    }

    /** Hot-reloads trigger settings without re-registering the listener. */
    public void reloadConfig(boolean noclipEnabled, double noclipChance,
                             boolean longFallEnabled, double longFallMinDmg, double longFallChance,
                             boolean portalEnabled, double portalChance) {
        this.noclipTriggerEnabled   = noclipEnabled;
        this.noclipTriggerChance    = noclipChance;
        this.longFallTriggerEnabled = longFallEnabled;
        this.longFallMinDamage      = longFallMinDmg;
        this.longFallTriggerChance  = longFallChance;
        this.portalTriggerEnabled   = portalEnabled;
        this.portalTriggerChance    = portalChance;
    }

    // -------------------------------------------------------------------------
    // World decoration — fires once per chunk on first generation
    // -------------------------------------------------------------------------

    /**
     * Each room's (modX=1, modZ=1) block falls in exactly one chunk, so this
     * handler processes every room precisely once when its chunk is first generated.
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!e.isNewChunk()) return;
        if (!e.getWorld().getName().equals(BackroomsWorld.WORLD_NAME)) return;

        World world   = e.getWorld();
        long  seed    = world.getSeed();
        int   period  = BackroomsWorld.BackroomsGenerator.PERIOD;
        int   baseX   = e.getChunk().getX() * 16;
        int   baseZ   = e.getChunk().getZ() * 16;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = baseX + lx;
                int wz = baseZ + lz;
                if (Math.floorMod(wx, period) != 1) continue;
                if (Math.floorMod(wz, period) != 1) continue;
                decorateRoom(world, seed, Math.floorDiv(wx, period), Math.floorDiv(wz, period));
            }
        }
    }

    /**
     * Paintings (~12%) and signs (~5%) placed on the interior face of the west
     * or north wall. Both are placed at modX=1 / modZ=1 so the wall block
     * behind them is always solid — a block closer to the room boundary would
     * risk having open air behind it.
     */
    private void decorateRoom(World world, long seed, int roomX, int roomZ) {
        int period = BackroomsWorld.BackroomsGenerator.PERIOD;
        int floorY = BackroomsWorld.BackroomsGenerator.FLOOR_Y;
        Material wallMat = backroomsWorld.getWallMaterialAt(roomX * period, roomZ * period);

        // ---- Painting -------------------------------------------------------
        long ph = mix(seed ^ ((long) roomX * 0xf1357aea2e62a9c5L)
                           ^ ((long) roomZ * 0x9e3779b97f4a7c15L)
                           ^ 0x7a6c3b2d1e0f5a4bL);
        if ((ph & 0xFF) < 31) { // ~12%
            boolean northWall = (ph & 0x100) != 0;
            int     pos       = 2 + (int) ((ph >>> 9)  % 8); // 2–9 along wall
            Art     art       = PAINTING_ARTS[(int) ((ph >>> 17) % PAINTING_ARTS.length)];

            // Entity placed one block INTO the room; backing block = bamboo wall
            int px = northWall ? roomX * period + pos : roomX * period + 1;
            int pz = northWall ? roomZ * period + 1   : roomZ * period + pos;
            BlockFace face = northWall ? BlockFace.SOUTH : BlockFace.EAST;

            Block backing = world.getBlockAt(
                    northWall ? px : px - 1,
                    floorY + 2,
                    northWall ? pz - 1 : pz);
            if (backing.getType() == wallMat) {
                try {
                    Location loc = new Location(world, px, floorY + 2, pz);
                    world.spawn(loc, Painting.class, p -> {
                        p.setFacingDirection(face);
                        p.setArt(art);
                    });
                } catch (Exception ignored) {} // wrong size for available space, etc.
            }
        }

        // ---- Sign -----------------------------------------------------------
        long sh = mix(seed ^ ((long) roomX * 0x6c62272e07bb0142L)
                           ^ ((long) roomZ * 0xbf58476d1ce4e5b9L)
                           ^ 0x3a1b2c4d5e6f7890L);
        if ((sh & 0xFF) < 13) { // ~5%
            boolean northWall = (sh & 0x100) != 0;
            int     pos       = 2 + (int) ((sh >>> 9)  % 8);
            String[] lines    = SIGN_CONFIGS[(int) ((sh >>> 17) % SIGN_CONFIGS.length)];

            int sx = northWall ? roomX * period + pos : roomX * period + 1;
            int sz = northWall ? roomZ * period + 1   : roomZ * period + pos;
            BlockFace face = northWall ? BlockFace.SOUTH : BlockFace.EAST;

            Block signBlock   = world.getBlockAt(sx, floorY + 2, sz);
            Block signBacking = northWall
                    ? world.getBlockAt(sx, floorY + 2, sz - 1)
                    : world.getBlockAt(sx - 1, floorY + 2, sz);
            if (signBlock.getType() == Material.AIR
                    && signBacking.getType() == wallMat) {
                WallSign data = (WallSign) Bukkit.createBlockData(Material.OAK_WALL_SIGN);
                data.setFacing(face);
                signBlock.setBlockData(data);
                Sign sign = (Sign) signBlock.getState();
                for (int i = 0; i < 4; i++) {
                    sign.getSide(Side.FRONT).line(i, Component.text(lines[i]));
                }
                sign.update();
            }
        }

        // ---- Armor stand (~4%) -----------------------------------------------
        long ah = mix(seed ^ ((long) roomX * 0x4b3a2c1d0e5f6a7bL)
                           ^ ((long) roomZ * 0xc7b6a5d4e3f2019aL)
                           ^ 0x2d3e4f5061728394L);
        if ((ah & 0xFF) < 10) { // ~4%
            int apos = 3 + (int) ((ah >>> 8) % 6);  // 3–8 in both axes
            int bpos = 3 + (int) ((ah >>> 14) % 6);
            double yaw = ((ah >>> 20) % 360);

            Block standFloor = world.getBlockAt(roomX * period + apos, floorY, roomZ * period + bpos);
            if (standFloor.getType() != Material.AIR) {
                Location loc = new Location(world,
                        roomX * period + apos + 0.5, floorY + 1, roomZ * period + bpos + 0.5, (float) yaw, 0);
                try {
                    world.spawn(loc, ArmorStand.class, stand -> {
                        stand.setVisible(true);
                        stand.setGravity(false);
                        stand.setBasePlate(false);
                        stand.setSmall(false);
                        // Slightly off poses — something feels wrong about it
                        double tilt = ((ah >>> 26) & 1) == 0 ? 0.2 : -0.2;
                        stand.setHeadPose(new EulerAngle(tilt, 0, 0));
                        stand.setRightArmPose(new EulerAngle(-0.4, 0.1, 0.3));
                        stand.setLeftArmPose(new EulerAngle(0.1, -0.2, -0.1));
                    });
                } catch (Exception ignored) {}
            }
        }
    }

    // -------------------------------------------------------------------------
    // Decoration data
    // -------------------------------------------------------------------------

    /**
     * All 1×1 classic paintings — guaranteed to fit in a single wall block and
     * present in every version since early Java Edition. They read as generic
     * "office art": abstract shapes, muted landscapes, something slightly wrong
     * about each of them.
     */
    private static final Art[] PAINTING_ARTS = {
        Art.KEBAB, Art.AZTEC, Art.ALBAN, Art.AZTEC2,
        Art.BOMB, Art.PLANT, Art.WASTELAND,
    };

    /**
     * Kane Pixels-style signage: mostly arrows suggesting navigation that leads
     * nowhere, terse room codes, the occasional fragment of instruction. Lines
     * are short and sparse — the signs look functional but are no help at all.
     */
    private static final String[][] SIGN_CONFIGS = {
        // Arrow-heavy
        {"→",      "",        "", ""},
        {"↑",      "",        "", ""},
        {"←",      "",        "", ""},
        {"↓",      "",        "", ""},
        {"→ →",    "",        "", ""},
        {"↑",      "B3",      "", ""},
        {"LVL 0",  "",        "↓",""},
        {"←",      "EXIT",    "", ""},
        {"→",      "114",     "", ""},
        {"↑  ↓",   "",        "", ""},  // contradictory
        {"← B",    "→ A",     "", ""},
        {"→",      "→",       "→",""},  // insistent
        {"↑",      "3F",      "", ""},
        // Sparse text / codes
        {"?",      "",        "", ""},
        {"",       "",        "", ""},  // worn blank
        {"STAFF",  "ONLY",    "→",""},
        {"NO EXIT","",        "", ""},
        {"SECTION","4-A",     "", ""},
        {"WRONG",  "WAY",     "", ""},
        // Rare unsettling fragments
        {"FOLLOW", "THE HUM", "", ""},
        {"LEVEL 0","",        "", ""},
    };

    // -------------------------------------------------------------------------

    private static long mix(long h) {
        h ^= h >>> 30;
        h *= 0xbf58476d1ce4e5b9L;
        h ^= h >>> 27;
        h *= 0x94d049bb133111ebL;
        h ^= h >>> 31;
        return h;
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    /** Prevent horn coral from drying out — it has no water but should stay alive. */
    @EventHandler
    public void onBlockFade(BlockFadeEvent e) {
        if (e.getBlock().getWorld().getName().equals(BackroomsWorld.WORLD_NAME)) {
            e.setCancelled(true);
        }
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

    /** Suppress the death message — no one hears you die in here. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!e.getEntity().getWorld().getName().equals(BackroomsWorld.WORLD_NAME)) return;
        e.deathMessage(null);
    }

    /** Dying in the backrooms respawns you in the backrooms. Dying is not an escape. */
    @EventHandler(priority = EventPriority.HIGH)
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

    // -------------------------------------------------------------------------
    // Noclip / long-fall / portal entry triggers
    // -------------------------------------------------------------------------

    /**
     * Suffocation trigger ("noclip"). Being crushed into a solid block sends
     * the player to the backrooms. The damage is cancelled so they arrive
     * at full health. Only fires outside the backrooms world.
     */
    @EventHandler
    public void onPlayerSuffocate(EntityDamageEvent e) {
        if (!noclipTriggerEnabled) return;
        if (!(e.getEntity() instanceof Player player)) return;
        if (player.getWorld().getName().equals(BackroomsWorld.WORLD_NAME)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION) return;
        if (ThreadLocalRandom.current().nextDouble() >= noclipTriggerChance) return;
        e.setCancelled(true);
        plugin.sendToBackrooms(player);
    }

    /**
     * Long-fall trigger. A fall dealing at least <min-damage> raw damage sends
     * the player to the backrooms instead of hurting them. Threshold defaults
     * to 8 (≈ falling from 11 blocks). Only fires outside the backrooms world.
     */
    @EventHandler
    public void onPlayerLongFall(EntityDamageEvent e) {
        if (!longFallTriggerEnabled) return;
        if (!(e.getEntity() instanceof Player player)) return;
        if (player.getWorld().getName().equals(BackroomsWorld.WORLD_NAME)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (e.getDamage() < longFallMinDamage) return;
        if (ThreadLocalRandom.current().nextDouble() >= longFallTriggerChance) return;
        e.setCancelled(true);
        plugin.sendToBackrooms(player);
    }

    /**
     * Portal trigger. Each portal use has a configurable chance of sending the
     * player to the backrooms instead of the nether/end. Off by default.
     */
    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent e) {
        if (!portalTriggerEnabled) return;
        Player player = e.getPlayer();
        if (player.getWorld().getName().equals(BackroomsWorld.WORLD_NAME)) return;
        if (ThreadLocalRandom.current().nextDouble() >= portalTriggerChance) return;
        e.setCancelled(true);
        plugin.sendToBackrooms(player);
    }

    /** Clean up falling-player guard if a player disconnects mid-fall. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        fallingPlayers.remove(e.getPlayer().getUniqueId());
    }

    /**
     * Pit detection — fires when a player crosses below FLOOR_Y-8, giving a
     * meaningful fall before the teleport kicks in. Teleports them to the safe
     * corner (modX=2, modZ=2) of a random room; that position is guaranteed
     * clear in every room type.
     *
     * The Y check is first so the vast majority of PlayerMoveEvent calls exit immediately.
     */
    @EventHandler
    public void onPlayerFall(PlayerMoveEvent e) {
        if (e.getTo().getY() >= BackroomsWorld.BackroomsGenerator.FLOOR_Y - 8) return;
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
            player.sendMessage(Component.text("you lost track of how far you fell")
                    .color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC));
        }, 30L);
    }
}
