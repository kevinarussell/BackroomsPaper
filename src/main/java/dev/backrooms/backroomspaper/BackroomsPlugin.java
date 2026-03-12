package dev.backrooms.backroomspaper;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class BackroomsPlugin extends JavaPlugin {

    private BackroomsWorld backroomsWorld;
    private NamespacedKey returnLocationKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        returnLocationKey = new NamespacedKey(this, "return_location");

        double wallOpenChance = getConfig().getDouble("wall-open-chance", 0.70);
        boolean escapeEnabled = getConfig().getBoolean("escape-door.enabled", true);
        int     escapeRarity  = getConfig().getInt("escape-door.rarity", 500);

        backroomsWorld = new BackroomsWorld(this, wallOpenChance, escapeEnabled, escapeRarity);
        backroomsWorld.ensureWorldExists();

        var cmd = getCommand("backrooms");
        if (cmd != null) {
            var handler = new BackroomsCommand(this, backroomsWorld);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        Bukkit.getPluginManager().registerEvents(new BackroomsListener(this, backroomsWorld), this);

        // Ambient sound loop — plays only for players currently inside the backrooms.
        Bukkit.getScheduler().runTaskTimer(this, this::tickAmbientSounds, 100L, 280L);

        getLogger().info("The walls are yellow. The carpet is moist. There is no exit.");
    }

    @Override
    public void onDisable() {
        getLogger().info("...or is there?");
    }

    private void tickAmbientSounds() {
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

    public BackroomsWorld getBackroomsWorld() {
        return backroomsWorld;
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
