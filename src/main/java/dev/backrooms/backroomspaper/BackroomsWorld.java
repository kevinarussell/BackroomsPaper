package dev.backrooms.backroomspaper;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

public class BackroomsWorld {

    public static final String WORLD_NAME = "backrooms";

    private final BackroomsPlugin plugin;
    private final double wallOpenChance;
    private final boolean escapeEnabled;
    private final int escapeRarity;
    private World world;

    public BackroomsWorld(BackroomsPlugin plugin, double wallOpenChance, boolean escapeEnabled, int escapeRarity) {
        this.plugin        = plugin;
        this.wallOpenChance = wallOpenChance;
        this.escapeEnabled  = escapeEnabled;
        this.escapeRarity   = escapeRarity;
    }

    public void ensureWorldExists() {
        world = Bukkit.getWorld(WORLD_NAME);
        if (world != null) return;

        WorldCreator creator = new WorldCreator(WORLD_NAME)
                .environment(World.Environment.NORMAL)
                .type(WorldType.FLAT)
                .generator(new BackroomsGenerator(wallOpenChance, escapeEnabled, escapeRarity))
                .generateStructures(false);

        world = creator.createWorld();

        if (world != null) {
            world.setDifficulty(Difficulty.NORMAL);
            world.setTime(6000);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, true);
            world.setGameRule(GameRule.NATURAL_REGENERATION, false);
            world.setGameRule(GameRule.KEEP_INVENTORY, false);
            plugin.getLogger().info("Backrooms world generated.");
        }
    }

    public World getWorld() {
        return world;
    }

    public Location getSpawnLocation() {
        return new Location(world, 6.5, BackroomsGenerator.FLOOR_Y + 1, 6.5);
    }

    // -------------------------------------------------------------------------

    public static class BackroomsGenerator extends ChunkGenerator {

        static final int FLOOR_Y = 0;
        static final int CEIL_Y  = 4;

        static final int PERIOD        = 12; // package-visible for pit teleport destination
        private static final int DOOR_WIDTH    = 4;
        private static final int DOOR_START_MAX = PERIOD - 1 - DOOR_WIDTH; // 7

        private static final int TYPE_STANDARD    = 0;
        private static final int TYPE_OPEN        = 1;
        private static final int TYPE_COLUMN_ROW  = 2;
        private static final int TYPE_CLUTTERED   = 3;
        private static final int TYPE_PARTITION   = 4;
        private static final int TYPE_PIT         = 5;

        private final int     wallOpenThreshold; // 0–256
        private final boolean escapeEnabled;
        private final int     escapeRarity;

        public BackroomsGenerator(double wallOpenChance, boolean escapeEnabled, int escapeRarity) {
            this.wallOpenThreshold = (int) (wallOpenChance * 256);
            this.escapeEnabled     = escapeEnabled;
            this.escapeRarity      = Math.max(1, escapeRarity);
        }

        @Override
        public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData chunk) {
            long seed  = info.getSeed();
            int  baseX = chunkX * 16;
            int  baseZ = chunkZ * 16;

            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    placeColumn(chunk, lx, lz, baseX + lx, baseZ + lz, seed);
                }
            }
        }

        private void placeColumn(ChunkData chunk, int lx, int lz, int wx, int wz, long seed) {
            int modX  = Math.floorMod(wx, PERIOD);
            int modZ  = Math.floorMod(wz, PERIOD);
            boolean wallX = modX == 0;
            boolean wallZ = modZ == 0;
            int roomX = Math.floorDiv(wx, PERIOD);
            int roomZ = Math.floorDiv(wz, PERIOD);

            // Pre-compute interior room role before touching the floor, because pit rooms
            // need to suppress the floor and sub-bedrock blocks entirely.
            boolean interior  = !wallX && !wallZ;
            boolean isEscDoor = interior && escapeEnabled && isEscapeRoom(seed, roomX, roomZ);
            int     typeVal   = (interior && !isEscDoor) ? roomType(seed, roomX, roomZ) : TYPE_STANDARD;
            boolean isPit     = interior && !isEscDoor
                                && typeVal == TYPE_PIT
                                && tileIsPit(seed, roomX, roomZ, modX, modZ);

            // Floor — pit tiles are open void; everything else gets birch log + bedrock seal
            if (isPit) {
                chunk.setBlock(lx, FLOOR_Y,     lz, Material.AIR);
                chunk.setBlock(lx, FLOOR_Y - 1, lz, Material.AIR);
                chunk.setBlock(lx, FLOOR_Y - 2, lz, Material.AIR);
            } else {
                chunk.setBlock(lx, FLOOR_Y,     lz, Material.STRIPPED_BIRCH_LOG);
                chunk.setBlock(lx, FLOOR_Y - 1, lz, Material.BEDROCK);
                chunk.setBlock(lx, FLOOR_Y - 2, lz, Material.BEDROCK);
            }

            // Ceiling — always sealed
            chunk.setBlock(lx, CEIL_Y,     lz, Material.YELLOW_CONCRETE);
            chunk.setBlock(lx, CEIL_Y + 1, lz, Material.BEDROCK);
            chunk.setBlock(lx, CEIL_Y + 2, lz, Material.BEDROCK);

            if (wallX && wallZ) {
                solidWall(chunk, lx, lz);

            } else if (wallX) {
                int doorStart = doorwayStart(seed, roomX, roomZ, 0);
                if (isWallOpen(seed, roomX, roomZ, 0) && modZ >= doorStart && modZ < doorStart + DOOR_WIDTH) {
                    openPassage(chunk, lx, lz);
                } else {
                    solidWall(chunk, lx, lz);
                }

            } else if (wallZ) {
                int doorStart = doorwayStart(seed, roomX, roomZ, 1);
                if (isWallOpen(seed, roomX, roomZ, 1) && modX >= doorStart && modX < doorStart + DOOR_WIDTH) {
                    openPassage(chunk, lx, lz);
                } else {
                    solidWall(chunk, lx, lz);
                }

            } else {
                if (isPit) {
                    for (int y = FLOOR_Y + 1; y < CEIL_Y; y++) {
                        chunk.setBlock(lx, y, lz, Material.AIR);
                    }
                } else if (isEscDoor) {
                    placeEscapeDoorInterior(chunk, lx, lz, seed, roomX, roomZ, modX, modZ);
                } else {
                    placeRegularInterior(chunk, lx, lz, seed, roomX, roomZ, modX, modZ, typeVal);
                }
            }
        }

        // -------------------------------------------------------------------------
        // Interior placement
        // -------------------------------------------------------------------------

        private void placeRegularInterior(ChunkData chunk, int lx, int lz,
                                          long seed, int roomX, int roomZ, int modX, int modZ, int type) {
            boolean isWall = switch (type) {
                case TYPE_STANDARD   -> isStandardPillar(seed, roomX, roomZ, modX, modZ);
                case TYPE_OPEN       -> false;
                case TYPE_COLUMN_ROW -> isColumnRowPillar(modX, modZ);
                case TYPE_CLUTTERED  -> isClutteredPillar(seed, roomX, roomZ, modX, modZ);
                case TYPE_PARTITION  -> isPartitionWall(seed, roomX, roomZ, modX, modZ);
                default              -> false;
            };

            if (isWall) {
                solidWall(chunk, lx, lz);
            } else {
                if (isLightTile(modX, modZ)) {
                    chunk.setBlock(lx, CEIL_Y, lz, Material.OCHRE_FROGLIGHT);
                }
                for (int y = FLOOR_Y + 1; y < CEIL_Y; y++) {
                    chunk.setBlock(lx, y, lz, Material.AIR);
                }
            }
        }

        /**
         * Escape door room: a plain open room containing a single freestanding dark oak
         * door frame standing in the middle of the floor.
         *
         * The frame is three columns wide:
         *   (doorX-1, doorZ) — left post: DARK_OAK_LOG full height
         *   (doorX,   doorZ) — opening:   DARK_OAK_DOOR (lower + upper half), log lintel above
         *   (doorX+1, doorZ) — right post: DARK_OAK_LOG full height
         *
         * Everything else in the room is open air, no pillars, no lights in the frame area.
         * The contrast of dark oak against bamboo planks and yellow concrete makes it
         * immediately, wrongly visible from across the room.
         */
        private void placeEscapeDoorInterior(ChunkData chunk, int lx, int lz,
                                              long seed, int roomX, int roomZ, int modX, int modZ) {
            int doorX = escapeDoorX(seed, roomX, roomZ);
            int doorZ = escapeDoorZ(seed, roomX, roomZ);

            if (modZ == doorZ) {
                if (modX == doorX - 1 || modX == doorX + 1) {
                    // Side posts — dark oak log from floor to top, replacing air
                    for (int y = FLOOR_Y + 1; y < CEIL_Y; y++) {
                        chunk.setBlock(lx, y, lz, Material.DARK_OAK_LOG);
                    }
                    return;
                }
                if (modX == doorX) {
                    // Door opening — lower half, upper half, log lintel at top
                    Door lower = (Door) Bukkit.createBlockData(Material.DARK_OAK_DOOR);
                    lower.setFacing(BlockFace.SOUTH);
                    lower.setHalf(Bisected.Half.BOTTOM);
                    lower.setHinge(Door.Hinge.LEFT);
                    lower.setOpen(false);
                    chunk.setBlock(lx, FLOOR_Y + 1, lz, lower);

                    Door upper = (Door) Bukkit.createBlockData(Material.DARK_OAK_DOOR);
                    upper.setFacing(BlockFace.SOUTH);
                    upper.setHalf(Bisected.Half.TOP);
                    upper.setHinge(Door.Hinge.LEFT);
                    upper.setOpen(false);
                    chunk.setBlock(lx, FLOOR_Y + 2, lz, upper);

                    chunk.setBlock(lx, FLOOR_Y + 3, lz, Material.DARK_OAK_LOG);
                    return;
                }
            }

            // All other interior positions — open air, normal lights
            if (isLightTile(modX, modZ)) {
                chunk.setBlock(lx, CEIL_Y, lz, Material.OCHRE_FROGLIGHT);
            }
            for (int y = FLOOR_Y + 1; y < CEIL_Y; y++) {
                chunk.setBlock(lx, y, lz, Material.AIR);
            }
        }

        // -------------------------------------------------------------------------
        // Block helpers
        // -------------------------------------------------------------------------

        private void solidWall(ChunkData chunk, int lx, int lz) {
            for (int y = FLOOR_Y; y < CEIL_Y; y++) {
                chunk.setBlock(lx, y, lz, Material.BAMBOO_PLANKS);
            }
        }

        /** Preserves the floor block — only clears the air above it. */
        private void openPassage(ChunkData chunk, int lx, int lz) {
            for (int y = FLOOR_Y + 1; y < CEIL_Y; y++) {
                chunk.setBlock(lx, y, lz, Material.AIR);
            }
        }

        // -------------------------------------------------------------------------
        // Layout helpers
        // -------------------------------------------------------------------------

        private boolean isLightTile(int modX, int modZ) {
            return (modX == 5 || modX == 6) && (modZ == 5 || modZ == 6);
        }

        private boolean isWallOpen(long worldSeed, int roomX, int roomZ, int dir) {
            long h = mix(worldSeed ^ ((long) roomX * 0x9e3779b97f4a7c15L)
                                   ^ ((long) roomZ * 0x6c62272e07bb0142L)
                                   ^ ((long) dir   * 0xd38ca2c3e2e3a99bL));
            return (h & 0xFF) < wallOpenThreshold;
        }

        private int doorwayStart(long worldSeed, int roomX, int roomZ, int dir) {
            long h = mix(worldSeed ^ ((long) roomX * 0x6c62272e07bb0142L)
                                   ^ ((long) roomZ * 0x9e3779b97f4a7c15L)
                                   ^ ((long) dir   * 0xa09e467512804fcbL));
            return 1 + (int) ((h & 0x7FFF_FFFFL) % DOOR_START_MAX);
        }

        /** 50% STANDARD · 18% OPEN · 12% COLUMN_ROW · 8% CLUTTERED · 5% PARTITION · 7% PIT */
        private int roomType(long worldSeed, int roomX, int roomZ) {
            long h = mix(worldSeed ^ ((long) roomX * 0x517cc1b727220a95L)
                                   ^ ((long) roomZ * 0xa09e467512804fcbL));
            int v = (int) (h & 0xFF);
            if (v < 128) return TYPE_STANDARD;
            if (v < 174) return TYPE_OPEN;
            if (v < 205) return TYPE_COLUMN_ROW;
            if (v < 225) return TYPE_CLUTTERED;
            if (v < 239) return TYPE_PARTITION;
            return TYPE_PIT;
        }

        /**
         * Irregular pit shape: a solid 4×4 core in the center of the room that is always
         * open, plus a probabilistic fringe ring one tile wide for ragged edges.
         * The pit never reaches modX/modZ 1–2 or 9–10, so there is always a walkable
         * ledge around the perimeter — players see it coming before they fall in.
         */
        private boolean tileIsPit(long seed, int roomX, int roomZ, int modX, int modZ) {
            if (modX >= 4 && modX <= 7 && modZ >= 4 && modZ <= 7) return true; // hard core
            if (modX < 3 || modX > 8 || modZ < 3 || modZ > 8) return false;   // outside fringe
            long h = mix(seed ^ ((long) roomX * 0x12345678L)
                               ^ ((long) roomZ * 0x87654321L)
                               ^ (modX * 5L) ^ (modZ * 7L));
            return (h & 0xFF) < 100; // ~39% of fringe tiles extend the pit edge
        }

        /**
         * Approximately 1-in-escapeRarity rooms contain an escape door.
         * Uses a separate hash from roomType so escape rooms can still vary
         * in their base layout (though the door itself replaces any features).
         */
        private boolean isEscapeRoom(long worldSeed, int roomX, int roomZ) {
            long h = mix(worldSeed ^ ((long) roomX * 0xc4ceb9fe1a85ec53L)
                                   ^ ((long) roomZ * 0xff51afd7ed558ccdL)
                                   ^ 0xb5ad4eceda1ce2a9L);
            return (int) ((h & 0x7FFF_FFFFL) % escapeRarity) == 0;
        }

        /** Door center X within room interior, seeded per room. Range 3–8 (frame fits 2–9). */
        private int escapeDoorX(long worldSeed, int roomX, int roomZ) {
            long h = mix(worldSeed ^ ((long) roomX * 0xAAAA_AAAAL) ^ ((long) roomZ * 0xBBBB_BBBL) ^ 1L);
            return 3 + (int) ((h & 0x7FFF_FFFFL) % 6); // 3–8
        }

        /** Door center Z within room interior, seeded per room. Range 3–8. */
        private int escapeDoorZ(long worldSeed, int roomX, int roomZ) {
            long h = mix(worldSeed ^ ((long) roomX * 0xCCCC_CCCCL) ^ ((long) roomZ * 0xDDDD_DDDL) ^ 2L);
            return 3 + (int) ((h & 0x7FFF_FFFFL) % 6); // 3–8
        }

        // ---- Room type: STANDARD ------------------------------------------------

        private boolean isStandardPillar(long worldSeed, int roomX, int roomZ, int modX, int modZ) {
            if (!((modX == 3 || modX == 9) && (modZ == 3 || modZ == 9))) return false;
            long h = mix(worldSeed ^ ((long) roomX * 0xbf58476d1ce4e5b9L)
                                   ^ ((long) roomZ * 0x94d049bb133111ebL)
                                   ^ (modX * 17L) ^ (modZ * 31L));
            return (h & 0xFF) < 64;
        }

        // ---- Room type: COLUMN_ROW ----------------------------------------------

        private boolean isColumnRowPillar(int modX, int modZ) {
            return modX == 6 && (modZ == 2 || modZ == 5 || modZ == 8);
        }

        // ---- Room type: CLUTTERED -----------------------------------------------

        private boolean isClutteredPillar(long worldSeed, int roomX, int roomZ, int modX, int modZ) {
            if (!((modX == 3 || modX == 6 || modX == 9) && (modZ == 3 || modZ == 6 || modZ == 9))) return false;
            if (modX == 6 && modZ == 6) return false; // keep center clear (spawn safety)
            long h = mix(worldSeed ^ ((long) roomX * 0xbf58476d1ce4e5b9L)
                                   ^ ((long) roomZ * 0x94d049bb133111ebL)
                                   ^ (modX * 17L) ^ (modZ * 31L));
            return (h & 0xFF) < 140;
        }

        // ---- Room type: PARTITION -----------------------------------------------

        private boolean isPartitionWall(long worldSeed, int roomX, int roomZ, int modX, int modZ) {
            long h = mix(worldSeed ^ ((long) roomX * 0x9e3779b97f4a7c15L)
                                   ^ ((long) roomZ * 0x6c62272e07bb0142L)
                                   ^ 0xdeadbeefL);
            int side = (int) (h & 3);
            int len  = 4 + (int) ((h >>> 4) & 3);

            return switch (side) {
                case 0 -> modX == 5 && modZ >= (PERIOD - 1 - len) && modZ <= PERIOD - 1;
                case 1 -> modX == 6 && modZ >= 1 && modZ <= len;
                case 2 -> modZ == 5 && modX >= (PERIOD - 1 - len) && modX <= PERIOD - 1;
                case 3 -> modZ == 6 && modX >= 1 && modX <= len;
                default -> false;
            };
        }

        // -------------------------------------------------------------------------

        private long mix(long h) {
            h ^= h >>> 30;
            h *= 0xbf58476d1ce4e5b9L;
            h ^= h >>> 27;
            h *= 0x94d049bb133111ebL;
            h ^= h >>> 31;
            return h;
        }

        @Override public boolean shouldGenerateNoise()       { return false; }
        @Override public boolean shouldGenerateSurface()     { return false; }
        @Override public boolean shouldGenerateCaves()       { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs()        { return false; }
        @Override public boolean shouldGenerateStructures()  { return false; }
    }
}
