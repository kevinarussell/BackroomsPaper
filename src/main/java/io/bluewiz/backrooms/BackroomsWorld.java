package io.bluewiz.backrooms;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class BackroomsWorld {

    public static final String WORLD_NAME = "backrooms";

    private final BackroomsPlugin plugin;
    private final double  wallOpenChance;
    private final boolean escapeEnabled;
    private final int     escapeRarity;
    private final boolean furnitureEnabled;
    private final double  furnitureChance;
    private final boolean levelsEnabled;
    private final boolean keepInventory;
    private World world;

    public BackroomsWorld(BackroomsPlugin plugin, double wallOpenChance, boolean escapeEnabled,
                          int escapeRarity, boolean furnitureEnabled, double furnitureChance,
                          boolean levelsEnabled, boolean keepInventory) {
        this.plugin           = plugin;
        this.wallOpenChance   = wallOpenChance;
        this.escapeEnabled    = escapeEnabled;
        this.escapeRarity     = escapeRarity;
        this.furnitureEnabled = furnitureEnabled;
        this.furnitureChance  = furnitureChance;
        this.levelsEnabled    = levelsEnabled;
        this.keepInventory    = keepInventory;
    }

    public void ensureWorldExists() {
        world = Bukkit.getWorld(WORLD_NAME);
        if (world != null) return;

        WorldCreator creator = new WorldCreator(WORLD_NAME)
                .environment(World.Environment.NORMAL)
                .generator(new BackroomsGenerator(wallOpenChance, escapeEnabled, escapeRarity,
                        furnitureEnabled, furnitureChance, levelsEnabled))
                .generateStructures(false);

        world = creator.createWorld();

        if (world != null) {
            world.setDifficulty(Difficulty.NORMAL);
            world.setTime(6000);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, true);
            world.setGameRule(GameRule.NATURAL_REGENERATION, false);
            world.setGameRule(GameRule.KEEP_INVENTORY, keepInventory);
            plugin.getLogger().info("Backrooms world generated.");
        }
    }

    public World getWorld() {
        return world;
    }

    public Location getSpawnLocation() {
        var rng   = ThreadLocalRandom.current();
        int roomX = rng.nextInt(-200, 201);
        int roomZ = rng.nextInt(-200, 201);
        return new Location(world,
                roomX * BackroomsGenerator.PERIOD + 2.5,
                BackroomsGenerator.FLOOR_Y + 1,
                roomZ * BackroomsGenerator.PERIOD + 2.5);
    }

    /** Returns the zone level at the given world coordinates. */
    public BackroomsGenerator.Level getLevelAt(int worldX, int worldZ) {
        if (world == null || !levelsEnabled) return BackroomsGenerator.Level.HALLWAYS;
        int roomX = Math.floorDiv(worldX, BackroomsGenerator.PERIOD);
        int roomZ = Math.floorDiv(worldZ, BackroomsGenerator.PERIOD);
        int zoneX = Math.floorDiv(roomX, BackroomsGenerator.ZONE_SIZE);
        int zoneZ = Math.floorDiv(roomZ, BackroomsGenerator.ZONE_SIZE);
        return BackroomsGenerator.zoneLevel(world.getSeed(), zoneX, zoneZ);
    }

    /** Returns the expected wall material at the given world coordinates. */
    public Material getWallMaterialAt(int worldX, int worldZ) {
        return BackroomsGenerator.wallFor(getLevelAt(worldX, worldZ));
    }

    // -------------------------------------------------------------------------

    public static class BackroomsGenerator extends ChunkGenerator {

        static final int FLOOR_Y = 64;

        private static final int PIT_DEPTH = 16; // shaft depth; air from FLOOR_Y down to FLOOR_Y-PIT_DEPTH+1
        private static final int SUB_FILL  = 6;  // smooth stone below pit floor before bedrock seal

        static final int PERIOD = 12; // package-visible for pit teleport destination

        // ---- Ceiling modifier constants -----------------------------------------
        // Computed per-room via roomCeilingMod(); controls ceiling height and material.
        private static final int MOD_NORMAL   = 0; // standard 4-block height
        private static final int MOD_LOW      = 1; // 3 blocks — cramped
        private static final int MOD_TALL     = 2; // 7 blocks — open
        private static final int MOD_GRAND    = 3; // 12 blocks — cavernous
        private static final int MOD_VOID     = 4; // 24 blocks, no ceiling panel
        private static final int MOD_INVERTED = 5; // floor/ceiling materials swapped

        // Inner height in blocks (floor block to ceiling block, exclusive)
        private static final int H_LOW    = 3;
        private static final int H_NORMAL = 4;
        private static final int H_TALL   = 7;
        private static final int H_GRAND  = 12;
        private static final int H_VOID   = 24;

        // ---- Zone levels --------------------------------------------------------
        // Each zone (ZONE_SIZE × ZONE_SIZE rooms) picks one level via zoneLevel().
        // Level controls floor/wall/ceiling materials, lighting, and water floor.
        static final int ZONE_SIZE = 40; // rooms per zone side (= 480 blocks)

        enum Level { HALLWAYS, WAREHOUSE, POOLROOMS, OFFICE }

        private static final int TYPE_STANDARD    = 0;
        private static final int TYPE_OPEN        = 1;
        private static final int TYPE_COLUMN_ROW  = 2;
        private static final int TYPE_CLUTTERED   = 3;
        private static final int TYPE_PARTITION   = 4;
        private static final int TYPE_PIT         = 5;

        private final int     wallOpenThreshold;  // 0–256
        private final boolean escapeEnabled;
        private final int     escapeRarity;
        private final boolean furnitureEnabled;
        private final int     furnitureThreshold; // 0–256
        private final boolean levelsEnabled;

        public BackroomsGenerator(double wallOpenChance, boolean escapeEnabled, int escapeRarity,
                                  boolean furnitureEnabled, double furnitureChance, boolean levelsEnabled) {
            this.wallOpenThreshold  = (int) (wallOpenChance * 256);
            this.escapeEnabled      = escapeEnabled;
            this.escapeRarity       = Math.max(1, escapeRarity);
            this.furnitureEnabled   = furnitureEnabled;
            this.furnitureThreshold = (int) (furnitureChance * 256);
            this.levelsEnabled      = levelsEnabled;
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
            // need to suppress the floor tile and carve an air shaft below.
            boolean interior  = !wallX && !wallZ;
            boolean isEscDoor = interior && escapeEnabled && isEscapeRoom(seed, roomX, roomZ);
            int     typeVal   = (interior && !isEscDoor) ? roomType(seed, roomX, roomZ) : TYPE_STANDARD;
            boolean inPitRoom = interior && !isEscDoor && typeVal == TYPE_PIT;
            boolean isPit     = inPitRoom && tileIsPit(seed, roomX, roomZ, modX, modZ);

            // Zone level — all rooms in the same ZONE_SIZE×ZONE_SIZE region share a level
            Level level = levelsEnabled
                    ? zoneLevel(seed, Math.floorDiv(roomX, ZONE_SIZE), Math.floorDiv(roomZ, ZONE_SIZE))
                    : Level.HALLWAYS;

            // Ceiling modifier — escape rooms always use normal height (door frame hardcoded)
            int     mod       = isEscDoor ? MOD_NORMAL : roomCeilingMod(seed, roomX, roomZ);
            int     ceilY     = FLOOR_Y + heightForMod(mod);
            boolean inverted  = mod == MOD_INVERTED;
            boolean voidRoom  = mod == MOD_VOID;

            int pitBottomY  = FLOOR_Y - PIT_DEPTH;     // 48 — black concrete floor of shaft
            int subFloorEnd = pitBottomY - SUB_FILL;   // 42 — bedrock seal layer

            // Poolrooms pits fill with water — a dark column of water in both directions
            Material pitFill = (level == Level.POOLROOMS) ? Material.WATER : Material.AIR;

            // Floor tile — inverted rooms swap to wall material (light tiles handled later)
            Material floorMat;
            if (isPit)         floorMat = pitFill;
            else if (inverted) floorMat = wallFor(level); // may be overwritten in placeRegularInterior
            else if (level == Level.POOLROOMS && interior) {
                // Inset pool: pool-area tiles are water, rim tiles are white concrete
                floorMat = (modX >= 3 && modX <= 9 && modZ >= 3 && modZ <= 9)
                        ? Material.WATER : wallFor(level);
            } else {
                floorMat = floorFor(level);
            }
            chunk.setBlock(lx, FLOOR_Y, lz, floorMat);

            // Sub-floor shaft/fill
            if (isPit) {
                for (int y = FLOOR_Y - 1; y > pitBottomY; y--) {
                    chunk.setBlock(lx, y, lz, pitFill);
                }
                chunk.setBlock(lx, pitBottomY, lz, Material.BLACK_CONCRETE);
            } else {
                // Pit rooms: use wall material so shaft sides match the room
                Material subMat = inPitRoom ? wallFor(level) : subFloorFor(level);
                for (int y = FLOOR_Y - 1; y >= pitBottomY; y--) {
                    chunk.setBlock(lx, y, lz, subMat);
                }
            }

            // Stone seal and bedrock below the pit level — same for every column
            for (int y = pitBottomY - 1; y > subFloorEnd; y--) {
                chunk.setBlock(lx, y, lz, Material.SMOOTH_STONE);
            }
            chunk.setBlock(lx, subFloorEnd, lz, Material.BEDROCK);

            // Walls take the max ceiling height of both adjacent rooms so that a tall
            // room is always fully enclosed on every side, not just north/west.
            // (North/west walls are owned by this room; east/south walls are owned by
            // the neighbour, so without the max they'd only reach the neighbour's height.)
            // Compute adjacent room heights for wall/door sizing.
            // effectiveCeilY (max) determines how tall the wall is.
            // minCeilHeight  (min) caps door height so passages never overshoot
            // the shorter room's ceiling.
            int adjHeightX = wallX ? heightForMod(roomCeilingMod(seed, roomX - 1, roomZ)) : 0;
            int adjHeightZ = wallZ ? heightForMod(roomCeilingMod(seed, roomX, roomZ - 1)) : 0;
            int effectiveCeilY = ceilY;
            if (wallX) effectiveCeilY = Math.max(effectiveCeilY, FLOOR_Y + adjHeightX);
            if (wallZ) effectiveCeilY = Math.max(effectiveCeilY, FLOOR_Y + adjHeightZ);

            if (wallX || wallZ) {
                // Solid wall: fills all the way to effectiveCeilY (no separate ceiling panel)
                chunk.setBlock(lx, effectiveCeilY + 1, lz, Material.BEDROCK);
                chunk.setBlock(lx, effectiveCeilY + 2, lz, Material.BEDROCK);
            } else {
                // Interior: ceiling panel at ceilY, then fill with wall material
                // up to the tallest possible room height.  Without this, a short
                // room next to a tall one would expose the seal layers through the
                // doorway — visible black concrete and bedrock breaking the illusion.
                if (!voidRoom) {
                    chunk.setBlock(lx, ceilY, lz, inverted ? floorFor(level) : ceilBaseFor(level));
                }
                int fillCeil = FLOOR_Y + H_VOID;
                for (int y = ceilY + 1; y <= fillCeil; y++) {
                    chunk.setBlock(lx, y, lz, wallFor(level));
                }
                chunk.setBlock(lx, fillCeil + 1, lz, Material.BEDROCK);
                chunk.setBlock(lx, fillCeil + 2, lz, Material.BEDROCK);
            }

            if (wallX && wallZ) {
                solidWall(chunk, lx, lz, effectiveCeilY, level);

            } else if (wallX) {
                int width         = doorWidth(seed, roomX, roomZ, 0);
                int minCeilHeight = Math.min(ceilY - FLOOR_Y, adjHeightX);
                int height        = doorHeight(seed, roomX, roomZ, 0, minCeilHeight);
                int doorStart     = doorwayStart(seed, roomX, roomZ, 0, width);
                if (isWallOpen(seed, roomX, roomZ, 0) && modZ >= doorStart && modZ < doorStart + width) {
                    openPassage(chunk, lx, lz, height, effectiveCeilY, level);
                } else {
                    solidWall(chunk, lx, lz, effectiveCeilY, level);
                }

            } else if (wallZ) {
                int width         = doorWidth(seed, roomX, roomZ, 1);
                int minCeilHeight = Math.min(ceilY - FLOOR_Y, adjHeightZ);
                int height        = doorHeight(seed, roomX, roomZ, 1, minCeilHeight);
                int doorStart     = doorwayStart(seed, roomX, roomZ, 1, width);
                if (isWallOpen(seed, roomX, roomZ, 1) && modX >= doorStart && modX < doorStart + width) {
                    openPassage(chunk, lx, lz, height, effectiveCeilY, level);
                } else {
                    solidWall(chunk, lx, lz, effectiveCeilY, level);
                }

            } else {
                if (isPit) {
                    for (int y = FLOOR_Y + 1; y < ceilY; y++) {
                        chunk.setBlock(lx, y, lz, Material.AIR);
                    }
                } else if (isEscDoor) {
                    placeEscapeDoorInterior(chunk, lx, lz, seed, roomX, roomZ, modX, modZ, ceilY, level);
                } else {
                    placeRegularInterior(chunk, lx, lz, seed, roomX, roomZ, modX, modZ, typeVal, ceilY, inverted, voidRoom, level);
                }
            }
        }

        // -------------------------------------------------------------------------
        // Interior placement
        // -------------------------------------------------------------------------

        private void placeRegularInterior(ChunkData chunk, int lx, int lz,
                                          long seed, int roomX, int roomZ, int modX, int modZ, int type,
                                          int ceilY, boolean inverted, boolean voidRoom, Level level) {
            boolean isWall = switch (type) {
                case TYPE_STANDARD   -> isStandardPillar(seed, roomX, roomZ, modX, modZ);
                case TYPE_OPEN       -> isOpenStray(seed, roomX, roomZ, modX, modZ);
                case TYPE_COLUMN_ROW -> isColumnRowPillar(seed, roomX, roomZ, modX, modZ);
                case TYPE_CLUTTERED  -> isClutteredPillar(seed, roomX, roomZ, modX, modZ);
                case TYPE_PARTITION  -> isPartitionWall(seed, roomX, roomZ, modX, modZ);
                default              -> false;
            };
            // Pit teleport lands at (2,2) — must always be walkable
            if (isWall && modX == 2 && modZ == 2) isWall = false;

            if (isWall) {
                solidWall(chunk, lx, lz, ceilY, level);
            } else {
                if (inverted) {
                    // Light moves to the floor; ceiling material already placed in placeColumn
                    if (isLightTile(modX, modZ, level)) {
                        setLightBlock(chunk, lx, FLOOR_Y, lz, level, seed, roomX, roomZ);
                    }
                } else if (!voidRoom) {
                    // Normal: light overwrites the base ceiling panel at light-tile positions
                    if (isLightTile(modX, modZ, level)) {
                        setLightBlock(chunk, lx, ceilY, lz, level, seed, roomX, roomZ);
                    }
                }
                // Void rooms: no light, pitch dark above
                for (int y = FLOOR_Y + 1; y < ceilY; y++) {
                    chunk.setBlock(lx, y, lz, Material.AIR);
                }
                // Poolrooms: no furniture (would look odd floating over water)
                if (level != Level.POOLROOMS) {
                    placeFurnitureAt(chunk, lx, lz, seed, roomX, roomZ, modX, modZ);
                }
            }
        }

        // -------------------------------------------------------------------------
        // Furniture
        // -------------------------------------------------------------------------

        private static final int FURN_PLANT_STAND = 0; // fence pedestal + potted plant
        private static final int FURN_SOFA        = 1; // row of oak stairs
        private static final int FURN_DESK        = 2; // fence legs + dark oak slab surface
        private static final int FURN_CABINET     = 3; // stacked chiseled bookshelves
        private static final int FURN_LAMP        = 4; // oak fence post + lantern on top
        private static final int FURN_VASE        = 5; // potted plant directly on floor

        private static final Material[] POTTED_PLANTS = {
            Material.POTTED_FERN,
            Material.POTTED_DEAD_BUSH,
            Material.POTTED_OAK_SAPLING,
            Material.POTTED_WITHER_ROSE,
        };

        private boolean hasFurniture(long seed, int roomX, int roomZ) {
            if (!furnitureEnabled) return false;
            long h = mix(seed ^ ((long) roomX * 0xFEDCBA9876L) ^ ((long) roomZ * 0x123456789L));
            return (h & 0xFF) < furnitureThreshold;
        }

        private int furnitureType(long seed, int roomX, int roomZ) {
            long h = mix(seed ^ ((long) roomX * 0xABCDEF01L) ^ ((long) roomZ * 0x12345678L) ^ 99L);
            return (int) ((h & 0xFF) % 6);
        }

        /**
         * Anchor position within the room interior (modX/modZ 3–7) so that 3-wide
         * furniture pieces (anchor ± 1) never breach the perimeter.
         */
        private int furnitureAnchorX(long seed, int roomX, int roomZ) {
            long h = mix(seed ^ ((long) roomX * 0x11111L) ^ ((long) roomZ * 0x22222L) ^ 1L);
            return 3 + (int) ((h & 0x7FFF_FFFFL) % 5); // 3–7
        }

        private int furnitureAnchorZ(long seed, int roomX, int roomZ) {
            long h = mix(seed ^ ((long) roomX * 0x33333L) ^ ((long) roomZ * 0x44444L) ^ 2L);
            return 3 + (int) ((h & 0x7FFF_FFFFL) % 5); // 3–7
        }

        private void placeFurnitureAt(ChunkData chunk, int lx, int lz,
                                      long seed, int roomX, int roomZ, int modX, int modZ) {
            if (!hasFurniture(seed, roomX, roomZ)) return;
            int type = furnitureType(seed, roomX, roomZ);
            int ax   = furnitureAnchorX(seed, roomX, roomZ);
            int az   = furnitureAnchorZ(seed, roomX, roomZ);
            int dx   = modX - ax;
            int dz   = modZ - az;

            switch (type) {
                case FURN_PLANT_STAND -> placePlantStand(chunk, lx, lz, seed, roomX, roomZ, dx, dz);
                case FURN_SOFA        -> placeSofa(chunk, lx, lz, seed, roomX, roomZ, dx, dz);
                case FURN_DESK        -> placeDesk(chunk, lx, lz, dx, dz);
                case FURN_CABINET     -> placeCabinet(chunk, lx, lz, dx, dz);
                case FURN_LAMP        -> placeLamp(chunk, lx, lz, dx, dz);
                case FURN_VASE        -> placeVase(chunk, lx, lz, seed, roomX, roomZ, dx, dz);
            }
        }

        /** Oak fence pedestal with a potted plant on top. */
        private void placePlantStand(ChunkData chunk, int lx, int lz,
                                     long seed, int roomX, int roomZ, int dx, int dz) {
            if (dx != 0 || dz != 0) return;
            chunk.setBlock(lx, FLOOR_Y + 1, lz, Material.OAK_FENCE);
            long h = mix(seed ^ ((long) roomX * 0x55555L) ^ ((long) roomZ * 0x66666L));
            chunk.setBlock(lx, FLOOR_Y + 2, lz, POTTED_PLANTS[(int) ((h & 0xFF) % POTTED_PLANTS.length)]);
        }

        /** Three oak stair blocks in a row — a recognisable couch silhouette. */
        private void placeSofa(ChunkData chunk, int lx, int lz,
                                long seed, int roomX, int roomZ, int dx, int dz) {
            if (dz != 0 || dx < -1 || dx > 1) return;
            long h = mix(seed ^ ((long) roomX * 0x77777L) ^ ((long) roomZ * 0x88888L));
            Stairs stairs = (Stairs) Bukkit.createBlockData(Material.OAK_STAIRS);
            stairs.setFacing((h & 1) == 0 ? BlockFace.NORTH : BlockFace.SOUTH);
            stairs.setHalf(Bisected.Half.BOTTOM);
            stairs.setShape(Stairs.Shape.STRAIGHT);
            chunk.setBlock(lx, FLOOR_Y + 1, lz, stairs);
        }

        /** Two oak fence legs with a dark oak slab surface — an office desk. */
        private void placeDesk(ChunkData chunk, int lx, int lz, int dx, int dz) {
            if (dz != 0 || dx < 0 || dx > 1) return;
            chunk.setBlock(lx, FLOOR_Y + 1, lz, Material.OAK_FENCE);
            Slab slab = (Slab) Bukkit.createBlockData(Material.DARK_OAK_SLAB);
            slab.setType(Slab.Type.BOTTOM);
            chunk.setBlock(lx, FLOOR_Y + 2, lz, slab);
        }

        /** Two chiseled bookshelves stacked — a filing cabinet. */
        private void placeCabinet(ChunkData chunk, int lx, int lz, int dx, int dz) {
            if (dx != 0 || dz != 0) return;
            chunk.setBlock(lx, FLOOR_Y + 1, lz, Material.CHISELED_BOOKSHELF);
            chunk.setBlock(lx, FLOOR_Y + 2, lz, Material.CHISELED_BOOKSHELF);
        }

        /** Oak fence post with a lantern on top — a floor lamp. */
        private void placeLamp(ChunkData chunk, int lx, int lz, int dx, int dz) {
            if (dx != 0 || dz != 0) return;
            chunk.setBlock(lx, FLOOR_Y + 1, lz, Material.OAK_FENCE);
            chunk.setBlock(lx, FLOOR_Y + 2, lz, Material.LANTERN);
        }

        /** Potted plant sitting directly on the floor — a decorative vase. */
        private void placeVase(ChunkData chunk, int lx, int lz,
                               long seed, int roomX, int roomZ, int dx, int dz) {
            if (dx != 0 || dz != 0) return;
            long h = mix(seed ^ ((long) roomX * 0x99999L) ^ ((long) roomZ * 0xAAAAL));
            chunk.setBlock(lx, FLOOR_Y + 1, lz, POTTED_PLANTS[(int) ((h & 0xFF) % POTTED_PLANTS.length)]);
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
                                              long seed, int roomX, int roomZ, int modX, int modZ,
                                              int ceilY, Level level) {
            int doorX = escapeDoorX(seed, roomX, roomZ);
            int doorZ = escapeDoorZ(seed, roomX, roomZ);

            if (modZ == doorZ) {
                if (modX == doorX - 1 || modX == doorX + 1) {
                    // Side posts — dark oak log from floor to top, replacing air
                    for (int y = FLOOR_Y + 1; y < ceilY; y++) {
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
            if (isLightTile(modX, modZ, level)) {
                setLightBlock(chunk, lx, ceilY, lz, level, seed, roomX, roomZ);
            }
            for (int y = FLOOR_Y + 1; y < ceilY; y++) {
                chunk.setBlock(lx, y, lz, Material.AIR);
            }
        }

        // -------------------------------------------------------------------------
        // Block helpers
        // -------------------------------------------------------------------------

        private void solidWall(ChunkData chunk, int lx, int lz, int wallCeilY, Level level) {
            for (int y = FLOOR_Y; y <= wallCeilY; y++) {
                chunk.setBlock(lx, y, lz, wallFor(level));
            }
        }

        /** Clears the passage opening and fills any remaining height with the wall material (low header). */
        private void openPassage(ChunkData chunk, int lx, int lz, int height, int wallCeilY, Level level) {
            for (int y = FLOOR_Y + 1; y <= wallCeilY; y++) {
                chunk.setBlock(lx, y, lz, y < FLOOR_Y + 1 + height ? Material.AIR : wallFor(level));
            }
        }

        // -------------------------------------------------------------------------
        // Layout helpers
        // -------------------------------------------------------------------------

        // ---- Zone / level helpers -----------------------------------------------

        /**
         * Maps a zone coordinate to one of the four levels.
         * Distribution: HALLWAYS 40% · WAREHOUSE 25% · POOLROOMS 20% · OFFICE 15%
         */
        static Level zoneLevel(long seed, int zoneX, int zoneZ) {
            long h = mixStatic(seed ^ ((long) zoneX * 0x3a4b5c6d7e8f9a0bL)
                               ^ ((long) zoneZ * 0xb0a9f8e7d6c5b4a3L)
                               ^ 0x1f2e3d4c5b6a7980L);
            int v = (int) (h & 0xFF);
            if (v < 102) return Level.HALLWAYS;   // ~40%
            if (v < 166) return Level.WAREHOUSE;  // ~25%
            if (v < 217) return Level.POOLROOMS;  // ~20%
            return Level.OFFICE;                  // ~15%
        }

        private Material floorFor(Level l) {
            return switch (l) {
                case WAREHOUSE -> Material.SMOOTH_STONE;
                case POOLROOMS -> Material.DARK_PRISMARINE;
                case OFFICE    -> Material.DARK_OAK_PLANKS;
                default        -> Material.HORN_CORAL_BLOCK;
            };
        }

        static Material wallFor(Level l) {
            return switch (l) {
                case WAREHOUSE -> Material.STONE_BRICKS;
                case POOLROOMS -> Material.WHITE_CONCRETE;
                case OFFICE    -> Material.DARK_OAK_PLANKS;
                default        -> Material.STRIPPED_BAMBOO_BLOCK;
            };
        }

        private Material ceilBaseFor(Level l) {
            return switch (l) {
                case WAREHOUSE -> Material.GRAY_CONCRETE;
                case POOLROOMS -> Material.WHITE_CONCRETE;
                case OFFICE    -> Material.BROWN_CONCRETE;
                default        -> Material.STRIPPED_BIRCH_LOG; // drop ceiling tile
            };
        }

        /** Sub-floor fill (below FLOOR_Y, not visible through opaque floor blocks). */
        private Material subFloorFor(Level l) {
            return l == Level.POOLROOMS ? Material.PRISMARINE_BRICKS : Material.SMOOTH_STONE;
        }

        /**
         * Places the appropriate lit ceiling (or floor, for inverted) block.
         * Warehouse: lit redstone lamp. Others: level-specific material.
         */
        private void setLightBlock(ChunkData chunk, int lx, int y, int lz,
                                   Level level, long seed, int roomX, int roomZ) {
            if (level == Level.WAREHOUSE) {
                Lightable lamp = (Lightable) Bukkit.createBlockData(Material.REDSTONE_LAMP);
                lamp.setLit(true);
                chunk.setBlock(lx, y, lz, lamp);
            } else {
                chunk.setBlock(lx, y, lz, levelLightMat(level, seed, roomX, roomZ));
            }
        }

        private Material levelLightMat(Level level, long seed, int roomX, int roomZ) {
            return switch (level) {
                case POOLROOMS -> Material.SEA_LANTERN;
                case OFFICE    -> Material.SHROOMLIGHT;
                default        -> lightMaterial(seed, roomX, roomZ); // ochre/3% verdant for HALLWAYS
            };
        }

        /**
         * Per-room ceiling modifier. Uses a hash independent of room type so any
         * combination of room type and ceiling height can occur.
         *
         * Distribution (256 buckets):
         *   VOID      v <  5  → ~2%   no ceiling panel, pitch dark above
         *   INVERTED  v < 10  → ~2%   floor/ceiling materials swapped
         *   GRAND     v < 25  → ~6%   12-block height
         *   TALL      v < 58  → ~13%  7-block height
         *   LOW       v < 91  → ~13%  3-block height (cramped)
         *   NORMAL    else    → ~64%  4-block height (default)
         */
        private int roomCeilingMod(long seed, int roomX, int roomZ) {
            long h = mix(seed ^ ((long) roomX * 0x2a9ef5b4c3d1e607L)
                               ^ ((long) roomZ * 0x8b3f9c2d1a4e7f05L)
                               ^ 0x5c7b3a2d1e4f6890L);
            int v = (int) (h & 0xFF);
            if (v <  5) return MOD_VOID;
            if (v < 10) return MOD_INVERTED;
            if (v < 25) return MOD_GRAND;
            if (v < 58) return MOD_TALL;
            if (v < 91) return MOD_LOW;
            return MOD_NORMAL;
        }

        private int heightForMod(int mod) {
            return switch (mod) {
                case MOD_LOW      -> H_LOW;
                case MOD_TALL     -> H_TALL;
                case MOD_GRAND    -> H_GRAND;
                case MOD_VOID     -> H_VOID;
                default           -> H_NORMAL; // NORMAL and INVERTED both use standard height
            };
        }

        /** ~3% of rooms have green froglights instead of ochre — a bad sign. */
        private Material lightMaterial(long worldSeed, int roomX, int roomZ) {
            long h = mix(worldSeed ^ ((long) roomX * 0xe7037ed1a0b428dbL)
                                   ^ ((long) roomZ * 0x4cd6865f4874e52bL));
            return (h & 0xFF) < 8 ? Material.VERDANT_FROGLIGHT : Material.OCHRE_FROGLIGHT;
        }

        /**
         * Light panel positions. Hallways use four spaced-out 2×1 fluorescent
         * strips (like a real drop ceiling grid). Other levels keep a centred
         * 2×2 cluster.
         */
        private boolean isLightTile(int modX, int modZ, Level level) {
            if (level == Level.HALLWAYS) {
                // Four 2×1 panels in a grid: (3–4, 3), (3–4, 8), (7–8, 3), (7–8, 8)
                return (modZ == 3 || modZ == 8) && (modX >= 3 && modX <= 4 || modX >= 7 && modX <= 8);
            }
            return (modX == 5 || modX == 6) && (modZ == 5 || modZ == 6);
        }

        private boolean isWallOpen(long worldSeed, int roomX, int roomZ, int dir) {
            long h = mix(worldSeed ^ ((long) roomX * 0x9e3779b97f4a7c15L)
                                   ^ ((long) roomZ * 0x6c62272e07bb0142L)
                                   ^ ((long) dir   * 0xd38ca2c3e2e3a99bL));
            return (h & 0xFF) < wallOpenThreshold;
        }

        private int doorwayStart(long worldSeed, int roomX, int roomZ, int dir, int width) {
            long h = mix(worldSeed ^ ((long) roomX * 0x6c62272e07bb0142L)
                                   ^ ((long) roomZ * 0x9e3779b97f4a7c15L)
                                   ^ ((long) dir   * 0xa09e467512804fcbL));
            // Valid start range: 1 .. (PERIOD-1-width), so the door fits within mod 1..PERIOD-2
            return 1 + (int) ((h & 0x7FFF_FFFFL) % (PERIOD - 1 - width));
        }

        /** Width in blocks: 2–5, weighted toward 3–4. */
        private int doorWidth(long worldSeed, int roomX, int roomZ, int dir) {
            long h = mix(worldSeed ^ ((long) roomX * 0x1a36e2b6c4d5f789L)
                                   ^ ((long) roomZ * 0x8f7e6d5c4b3a2190L)
                                   ^ ((long) dir   * 0xc3d4e5f6a7b8c9d0L));
            int v = (int) (h & 0xFF);
            if (v < 51)  return 2; // ~20%
            if (v < 153) return 3; // ~40%
            if (v < 230) return 4; // ~30%
            return 5;              // ~10%
        }

        /**
         * Door height in blocks, scaled to the wall height so passages feel
         * proportional.  Low rooms always get cramped 2-block openings. Normal
         * rooms keep the original 2-or-3 split.  Tall+ rooms get a uniform
         * range — sometimes a tiny slit in a massive wall, sometimes a wide-open
         * archway.  The mismatch is intentional: wrong-sized doors are unsettling.
         *
         * @param wallHeight effective wall height (effectiveCeilY - FLOOR_Y)
         */
        private int doorHeight(long worldSeed, int roomX, int roomZ, int dir, int wallHeight) {
            long h = mix(worldSeed ^ ((long) roomX * 0x2b3c4d5e6f7a8b9cL)
                                   ^ ((long) roomZ * 0x9a8b7c6d5e4f3021L)
                                   ^ ((long) dir   * 0xf1e2d3c4b5a69788L));
            if (wallHeight <= 3) return 2;                          // low: always cramped
            if (wallHeight <= 4) return (h & 0xFF) < 90 ? 2 : 3;   // normal: 35/65 split
            // tall+: uniform over [3, min(wallHeight-1, 10)]
            int maxH = Math.min(wallHeight - 1, 10);
            return 3 + (int) ((h & 0x7FFF_FFFFL) % (maxH - 2));
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

        /**
         * Drifting grid with stray columns and occasional thick pillars.
         *
         * Base positions at (3,3), (3,9), (9,3), (9,9) each drift ±1 per room
         * so the grid is recognisable but never quite the same twice. ~25% of
         * positions actually have a pillar; ~15% of those extend into a 2-wide
         * block.  ~12% of rooms also get a stray column at an unrelated position.
         */
        private boolean isStandardPillar(long worldSeed, int roomX, int roomZ, int modX, int modZ) {
            // Per-room drift hash — same for all four corners so the grid warps together
            long dh = mix(worldSeed ^ ((long) roomX * 0xa1b2c3d4e5f60718L)
                                    ^ ((long) roomZ * 0x18f6e5d4c3b2a190L));
            int[][] bases = {{3, 3}, {3, 9}, {9, 3}, {9, 9}};

            for (int i = 0; i < 4; i++) {
                // ~25% chance this position has a pillar
                long ph = mix(worldSeed ^ ((long) roomX * 0xbf58476d1ce4e5b9L)
                                        ^ ((long) roomZ * 0x94d049bb133111ebL)
                                        ^ (bases[i][0] * 17L) ^ (bases[i][1] * 31L));
                if ((ph & 0xFF) >= 64) continue;

                // Drift: 25% → -1, 50% → 0, 25% → +1 per axis
                int rawX = (int) ((dh >>> (i * 4)) & 3);
                int rawZ = (int) ((dh >>> (i * 4 + 2)) & 3);
                int px = bases[i][0] + (rawX == 0 ? -1 : rawX == 3 ? 1 : 0);
                int pz = bases[i][1] + (rawZ == 0 ? -1 : rawZ == 3 ? 1 : 0);
                px = Math.max(2, Math.min(10, px));
                pz = Math.max(2, Math.min(10, pz));

                if (modX == px && modZ == pz) return true;

                // ~15% of pillars are 2-wide (extends 1 block in a random direction)
                if (((ph >>> 8) & 0xFF) < 38) {
                    int ext = ((ph >>> 16) & 1) == 0 ? 1 : -1;
                    boolean extX = ((ph >>> 17) & 1) != 0;
                    if (extX  && modX == px + ext && modZ == pz) return true;
                    if (!extX && modX == px && modZ == pz + ext) return true;
                }
            }

            // Stray pillar: ~12% of rooms get one extra column at a random spot
            long sh = mix(worldSeed ^ ((long) roomX * 0x1234abcd5678eL)
                                    ^ ((long) roomZ * 0xfedcba987654L) ^ 0xcafebabeL);
            if ((sh & 0xFF) < 31) {
                int sx = 2 + (int) ((sh >>> 8) % 8);   // 2–9
                int sz = 2 + (int) ((sh >>> 12) % 8);  // 2–9
                if (modX == sx && modZ == sz) return true;
            }

            return false;
        }

        // ---- Room type: OPEN (with rare stray) ---------------------------------

        /**
         * ~10% of open rooms contain a single stray column — one pillar
         * standing alone in an otherwise empty room.
         */
        private boolean isOpenStray(long worldSeed, int roomX, int roomZ, int modX, int modZ) {
            long h = mix(worldSeed ^ ((long) roomX * 0x5c3a1b7e9d4f2068L)
                                   ^ ((long) roomZ * 0x8602f4d9e7b1a3c5L)
                                   ^ 0x0f1e2d3c4b5a6978L);
            if ((h & 0xFF) >= 26) return false;  // ~10%
            int sx = 3 + (int) ((h >>> 8) % 6);  // 3–8
            int sz = 3 + (int) ((h >>> 12) % 6); // 3–8
            return modX == sx && modZ == sz;
        }

        // ---- Room type: COLUMN_ROW ----------------------------------------------

        /**
         * Randomised column line. The axis, perpendicular position, column
         * count (2–4), starting offset, and spacing all vary per room.
         * Occasional columns extend into 2-wide blocks perpendicular to the row.
         */
        private boolean isColumnRowPillar(long worldSeed, int roomX, int roomZ, int modX, int modZ) {
            long h = mix(worldSeed ^ ((long) roomX * 0x7a6c3b2d1e0f5a4bL)
                                   ^ ((long) roomZ * 0x4b5a0f1e2d3b6c7aL));
            boolean alongZ = (h & 1) != 0;
            int rowPos  = 4 + (int) ((h >>> 1) % 5);  // 4–8
            int count   = 2 + (int) ((h >>> 4) % 3);  // 2–4
            int start   = 2 + (int) ((h >>> 6) % 3);  // 2–4
            int spacing = 2 + (int) ((h >>> 8) & 1);  // 2–3

            for (int i = 0; i < count; i++) {
                int pos = start + i * spacing;
                if (pos > 9) break;

                int cx = alongZ ? rowPos : pos;
                int cz = alongZ ? pos : rowPos;

                if (modX == cx && modZ == cz) return true;

                // ~16% thick: extend 1 block perpendicular to the row
                long th = mix(h ^ ((long) i * 0x9e3779b9L));
                if ((th & 0xFF) < 41) {
                    int ext = (th & 0x100) != 0 ? 1 : -1;
                    if (alongZ  && modX == cx + ext && modZ == cz) return true;
                    if (!alongZ && modX == cx && modZ == cz + ext) return true;
                }
            }
            return false;
        }

        // ---- Room type: CLUTTERED -----------------------------------------------

        /**
         * Irregular cluster of 5–8 pillars scattered around the room.
         * Positions are hash-derived rather than grid-locked, creating
         * organic arrangements.  ~20% of pillars extend into L-shapes
         * or 2-wide blocks.  A 3×3 safety zone around the room centre
         * is kept clear.
         */
        private boolean isClutteredPillar(long worldSeed, int roomX, int roomZ, int modX, int modZ) {
            long h = mix(worldSeed ^ ((long) roomX * 0xd3a2c1b0e9f87654L)
                                   ^ ((long) roomZ * 0x456789feL));
            int count = 5 + (int) ((h >>> 0) % 4);  // 5–8

            for (int i = 0; i < count; i++) {
                long ph = mix(h ^ ((long) i * 0x9e3779b97f4a7c15L));
                int px = 2 + (int) ((ph & 0x7FFF_FFFFL) % 8);          // 2–9
                int pz = 2 + (int) (((ph >>> 16) & 0x7FFF_FFFFL) % 8); // 2–9

                // Keep centre clear
                if (px >= 5 && px <= 7 && pz >= 5 && pz <= 7) continue;
                // ~70% of candidates materialise
                if (((ph >>> 32) & 0xFF) >= 179) continue;

                if (modX == px && modZ == pz) return true;

                // ~20% extend into L-shape or 2-wide block
                if (((ph >>> 40) & 0xFF) < 51) {
                    int shape = (int) ((ph >>> 48) & 3);
                    switch (shape) {
                        case 0 -> { if (modX == px + 1 && modZ == pz) return true; }
                        case 1 -> { if (modX == px && modZ == pz + 1) return true; }
                        case 2 -> { if (modX == px + 1 && modZ == pz + 1) return true; }
                        case 3 -> { if (modX == px - 1 && modZ == pz) return true; }
                    }
                }
            }
            return false;
        }

        // ---- Room type: PARTITION -----------------------------------------------

        /**
         * Wall stubs and floating segments. The primary partition is a stub
         * attached to one room wall. ~40% of partition rooms also get a
         * secondary feature — either a second stub from the opposite wall
         * or a freestanding wall segment floating mid-room, connected to
         * nothing.
         */
        private boolean isPartitionWall(long worldSeed, int roomX, int roomZ, int modX, int modZ) {
            long h = mix(worldSeed ^ ((long) roomX * 0x9e3779b97f4a7c15L)
                                   ^ ((long) roomZ * 0x6c62272e07bb0142L)
                                   ^ 0xdeadbeefL);
            int side = (int) (h & 3);
            int len  = 3 + (int) ((h >>> 2) & 3);  // 3–6

            if (isStub(side, len, modX, modZ)) return true;

            // ~40% chance of a secondary feature
            if (((h >>> 4) & 0xFF) < 102) {
                long h2 = mix(h ^ 0xdeadbeefcafebabeL);
                boolean floating = (h2 & 1) != 0;

                if (floating) {
                    // Freestanding wall segment — not touching any wall
                    boolean horiz = (h2 & 2) != 0;
                    int pos   = 4 + (int) ((h2 >>> 2) % 4); // 4–7
                    int start = 3 + (int) ((h2 >>> 5) % 4); // 3–6
                    int flen  = 2 + (int) ((h2 >>> 8) & 1); // 2–3
                    if (horiz) return modZ == pos && modX >= start && modX < start + flen;
                    else       return modX == pos && modZ >= start && modZ < start + flen;
                } else {
                    // Second stub from opposite wall
                    int side2 = (side + 2) & 3;
                    int len2  = 2 + (int) ((h2 >>> 2) & 3); // 2–5
                    return isStub(side2, len2, modX, modZ);
                }
            }
            return false;
        }

        private boolean isStub(int side, int len, int modX, int modZ) {
            return switch (side) {
                case 0 -> modX == 5 && modZ >= (PERIOD - 1 - len) && modZ <= PERIOD - 2;
                case 1 -> modX == 6 && modZ >= 1 && modZ <= len;
                case 2 -> modZ == 5 && modX >= (PERIOD - 1 - len) && modX <= PERIOD - 2;
                case 3 -> modZ == 6 && modX >= 1 && modX <= len;
                default -> false;
            };
        }

        // -------------------------------------------------------------------------

        static long mixStatic(long h) {
            h ^= h >>> 30;
            h *= 0xbf58476d1ce4e5b9L;
            h ^= h >>> 27;
            h *= 0x94d049bb133111ebL;
            h ^= h >>> 31;
            return h;
        }

        private long mix(long h) {
            return mixStatic(h);
        }

        @Override public boolean shouldGenerateNoise()       { return false; }
        @Override public boolean shouldGenerateSurface()     { return false; }
        @Override public boolean shouldGenerateCaves()       { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs()        { return false; }
        @Override public boolean shouldGenerateStructures()  { return false; }
    }
}
