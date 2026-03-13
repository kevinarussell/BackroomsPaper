# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew build        # Compiles and creates the fat JAR (output: build/libs/TheBackrooms.jar)
./gradlew clean        # Cleans build directory
./gradlew shadowJar    # Creates combined JAR with runtime dependencies
./gradlew modrinth     # Publishes to Modrinth (requires MODRINTH_TOKEN env var)
```

There are no tests in this project.

## Architecture

**BackroomsPaper** is a PaperMC plugin (Java 21) that creates a procedural "Backrooms" dimension for Minecraft 1.21.x.

### Core Files (`src/main/java/io/bluewiz/backrooms/`)

- **BackroomsPlugin.java** — Plugin entry point. Loads config, initializes world, schedules two repeating tasks: ambient sounds (every 280 ticks) and paranoid messages (every 300 ticks). Stores/restores player gamemode and return location using PersistentDataContainer.

- **BackroomsWorld.java** — Manages the custom world (dimension) and contains the inner class `BackroomsGenerator` which extends Bukkit's `ChunkGenerator`. This is where all procedural generation logic lives.

- **BackroomsListener.java** — Handles chunk decoration (paintings, signs on chunk load), escape door interaction, pit room teleportation (PlayerMove below Y=56), respawn-in-backrooms, mob spawn blocking, and coral preservation.

- **BackroomsCommand.java** — Handles `/backrooms [player]` to send players in. Shows entry title screen. Requires `backrooms.enter` / `backrooms.send` permissions.

### World Generation (`BackroomsGenerator`)

The generator uses a **12-block period** — the world is divided into 12×12 tile rooms. Room type is determined per-tile using deterministic hashing:

| Room Type | Weight | Description |
|-----------|--------|-------------|
| STANDARD | 50% | Basic room with possible doorways |
| OPEN | 18% | No internal walls |
| COLUMN_ROW | 12% | Column obstacles |
| CLUTTERED | 8% | Dense column grid |
| PARTITION | 5% | Partial wall dividers |
| PIT | 7% | Bottomless shaft (teleports player) |

- **Materials:** Bamboo planks (walls/ceiling), horn coral block (floor, Y=64), ochre froglight (ceiling panels)
- **Green froglight** (verdant) appears in ~3% of rooms as a rare atmospheric variant
- **Escape doors** (~1 per 500 rooms by default) are the only way out — dark oak door frame
- `wall-open-chance` (default 0.70) controls how maze-like walls are

### Player Flow

1. `/backrooms` teleports player in, saves their location + gamemode via PersistentDataContainer
2. Player is set to adventure mode inside the backrooms
3. Dying respawns player back inside the backrooms
4. Finding an escape door right-click teleports them home and restores their state
5. Falling into a pit (below Y=56) teleports to a random safe room

### Configuration (`src/main/resources/config.yml`)

All major behaviors are configurable: wall density, escape door rarity/enable, furniture enable/chance, ambient sounds, paranoid messages.
