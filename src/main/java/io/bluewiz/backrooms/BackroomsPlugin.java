package io.bluewiz.backrooms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public class BackroomsPlugin extends JavaPlugin {

    private BackroomsWorld backroomsWorld;
    private NamespacedKey returnLocationKey;
    private NamespacedKey previousGameModeKey;
    private boolean ambientSoundsEnabled;
    private boolean paranoidMessagesEnabled;
    private double  paranoidMessageChance;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        returnLocationKey    = new NamespacedKey(this, "return_location");
        previousGameModeKey  = new NamespacedKey(this, "previous_gamemode");

        double  wallOpenChance       = getConfig().getDouble("wall-open-chance", 0.70);
        boolean escapeEnabled        = getConfig().getBoolean("escape-door.enabled", true);
        int     escapeRarity         = getConfig().getInt("escape-door.rarity", 500);
        boolean furnitureEnabled     = getConfig().getBoolean("furniture.enabled", true);
        double  furnitureChance      = getConfig().getDouble("furniture.chance", 0.08);
        ambientSoundsEnabled         = getConfig().getBoolean("ambient-sounds.enabled", true);
        paranoidMessagesEnabled      = getConfig().getBoolean("paranoid-messages.enabled", true);
        paranoidMessageChance        = getConfig().getDouble("paranoid-messages.chance", 0.04);

        backroomsWorld = new BackroomsWorld(this, wallOpenChance, escapeEnabled, escapeRarity,
                furnitureEnabled, furnitureChance);
        backroomsWorld.ensureWorldExists();

        var cmd = getCommand("backrooms");
        if (cmd != null) {
            var handler = new BackroomsCommand(this, backroomsWorld);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        Bukkit.getPluginManager().registerEvents(new BackroomsListener(this, backroomsWorld), this);

        Bukkit.getScheduler().runTaskTimer(this, this::tickAmbientSounds, 100L, 280L);
        // 300 ticks (15s) interval, 4% chance per player → avg ~6 min between messages
        Bukkit.getScheduler().runTaskTimer(this, this::tickParanoidMessages, 600L, 300L);

        getLogger().info("The walls are yellow. The carpet is moist. There is no exit.");
    }

    @Override
    public void onDisable() {
        getLogger().info("...or is there?");
    }

    private void tickAmbientSounds() {
        if (!ambientSoundsEnabled) return;
        World world = backroomsWorld.getWorld();
        if (world == null) return;
        for (Player p : world.getPlayers()) {
            // Entity-attached playSound follows the player, so volume is constant while moving.
            p.playSound(p, Sound.BLOCK_BEACON_AMBIENT, 0.12f, 0.40f);
            if (Math.random() < 0.25) {
                p.playSound(p, Sound.AMBIENT_CAVE, 0.06f, 0.55f);
            }
        }
    }

    // Lowercase, terse, no punctuation — just unsettling observations.
    private static final String[] PARANOID_MESSAGES = {
        "what was that?",
        "was that a footstep?",
        "the lights are getting dimmer",
        "you've been here before",
        "something is behind you",
        "the hum changed",
        "your sense of direction is unreliable",
        "how long have you been walking?",
        "the carpet is damp here",
        "this hallway looks familiar",
        "you should keep moving",
        "don't stop, something is coming",
        "the walls are closing in...",
        "you're going in circles",
        "something is wrong with the lights",
        "you can hear breathing",
        "the exit is not in this direction",
        "what is this place?",
        "it noticed you",
        "there's something around the corner",
        "you're not alone",
        "you felt a draft",
        "there's no way out",
        "you're being watched."
    };

    private void tickParanoidMessages() {
        if (!paranoidMessagesEnabled) return;
        World world = backroomsWorld.getWorld();
        if (world == null) return;
        var rng = ThreadLocalRandom.current();
        for (Player p : world.getPlayers()) {
            if (rng.nextDouble() < paranoidMessageChance) {
                String msg = PARANOID_MESSAGES[rng.nextInt(PARANOID_MESSAGES.length)];
                p.sendMessage(Component.text(msg)
                        .color(NamedTextColor.DARK_GRAY)
                        .decorate(TextDecoration.ITALIC));
            }
        }
    }

    public BackroomsWorld getBackroomsWorld() {
        return backroomsWorld;
    }

    /** Saves the player's current gamemode before sending them to the backrooms. */
    public void saveGameMode(Player player) {
        player.getPersistentDataContainer().set(
                previousGameModeKey, PersistentDataType.STRING, player.getGameMode().name());
    }

    /** Restores the saved gamemode, falling back to SURVIVAL if missing. */
    public void restoreGameMode(Player player) {
        var pdc = player.getPersistentDataContainer();
        String saved = pdc.get(previousGameModeKey, PersistentDataType.STRING);
        pdc.remove(previousGameModeKey);
        GameMode mode = GameMode.SURVIVAL;
        if (saved != null) {
            try { mode = GameMode.valueOf(saved); } catch (IllegalArgumentException ignored) {}
        }
        player.setGameMode(mode);
    }

    /**
     * Saves the player's current location to their PersistentDataContainer before
     * sending them to the backrooms. Survives server restarts.
     */
    public void saveReturnLocation(Player player) {
        Location loc = player.getLocation();
        String encoded = loc.getWorld().getName()
                + ":" + loc.getX()
                + ":" + loc.getY()
                + ":" + loc.getZ()
                + ":" + loc.getYaw()
                + ":" + loc.getPitch();
        player.getPersistentDataContainer().set(returnLocationKey, PersistentDataType.STRING, encoded);
    }

    /**
     * Retrieves and removes the player's saved return location.
     * Falls back to overworld spawn if the stored location is missing or unparseable.
     */
    public Location consumeReturnLocation(Player player) {
        var pdc = player.getPersistentDataContainer();
        String encoded = pdc.get(returnLocationKey, PersistentDataType.STRING);
        pdc.remove(returnLocationKey);

        if (encoded != null) {
            try {
                String[] p = encoded.split(":");
                World world = Bukkit.getWorld(p[0]);
                if (world == null) world = Bukkit.getWorlds().get(0);
                return new Location(world,
                        Double.parseDouble(p[1]),
                        Double.parseDouble(p[2]),
                        Double.parseDouble(p[3]),
                        Float.parseFloat(p[4]),
                        Float.parseFloat(p[5]));
            } catch (Exception ignored) {}
        }

        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }
}
