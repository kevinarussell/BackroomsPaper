# The Backrooms

A PaperMC plugin that generates an infinite, procedural Backrooms dimension. Fluorescent lights hum overhead. The carpet is damp. There is one exit, and it doesn't want to be found.

## Features

- **Procedural world generation** — infinite, deterministic rooms built on a 12-block tile grid
- **Four zone levels** — Hallways, Warehouse, Poolrooms, and Office, each with unique materials and atmosphere
- **Six room types** — standard, open, column rows, cluttered, partitioned, and bottomless pits
- **Variable ceiling heights** — normal, low (cramped), tall, grand (cavernous), void (pitch dark), and inverted
- **Furniture** — plant stands, sofas, desks, cabinets, floor lamps, and vases placed in ~8% of rooms
- **Decorations** — paintings, signs with cryptic directions, and slightly off-pose armor stands
- **Ambient soundscape** — zone-aware fluorescent hum, cave echoes, and water ambience (Poolrooms)
- **Paranoid messages** — terse, unsettling observations whispered in chat
- **Escape doors** — rare freestanding dark oak door frames (~1 per 500 rooms). The only way out.
- **Pit rooms** — bottomless shafts that teleport you to a random room somewhere else
- **Multiple entry triggers** — command, suffocation (noclip), long falls, and portal interception
- **Adventure mode** — no breaking blocks, no placing blocks, no cheating your way out
- **Death is not an escape** — dying respawns you at a random room inside the Backrooms
- **Persistent state** — return location and gamemode survive server restarts
- **Keep inventory** — enabled by default, since dying teleports you away from your items

## Zone Levels

The world is divided into 480-block zones, each assigned one of four aesthetics:

| Level | Weight | Walls | Floor | Ceiling Lights |
|---|---|---|---|---|
| **Hallways** | 40% | Stripped bamboo block | Horn coral block | Ochre froglight panels (3% verdant) |
| **Warehouse** | 25% | Stone bricks | Smooth stone | Lit redstone lamps |
| **Poolrooms** | 20% | White concrete | Dark prismarine + water pools | Sea lanterns |
| **Office** | 15% | Dark oak planks | Dark oak planks | Shroomlight |

## Room Types

| Type | Weight | Description |
|---|---|---|
| Standard | 50% | Basic room with optional corner pillars |
| Open | 18% | No internal walls |
| Column Row | 12% | Line of column obstacles |
| Cluttered | 8% | Dense 3x3 pillar grid |
| Partition | 5% | Partial wall dividers |
| Pit | 7% | Bottomless shaft with irregular edges |

## Entry Triggers

Besides the `/backrooms` command, players can enter the Backrooms through:

- **Noclip** — suffocation damage has a 15% chance of sending you in (on by default)
- **Long fall** — falls dealing 8+ damage have a 25% chance (on by default)
- **Portal** — nether/end portal use has a 2% chance of interception (off by default)

All triggers are configurable and can be disabled independently.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/backrooms` | `backrooms.enter` | Send yourself to the Backrooms |
| `/backrooms <player>` | `backrooms.send` | Send another player |
| `/backrooms list` | `backrooms.admin` | List players currently inside |
| `/backrooms leave [player]` | `backrooms.admin` | Pull yourself or a player out |
| `/backrooms reload` | `backrooms.admin` | Reload configuration |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `backrooms.enter` | op | Use `/backrooms` on yourself |
| `backrooms.send` | op | Send other players to the Backrooms |
| `backrooms.admin` | op | Use `reload`, `leave`, and `list` subcommands |

## Configuration

```yaml
# Whether players keep inventory on death (recommended — items are unrecoverable otherwise)
keep-inventory: true

# Fraction of wall segments with a doorway (0.0–1.0). Lower = more maze-like.
wall-open-chance: 0.70

escape-door:
  enabled: true
  rarity: 500               # average rooms between escape doors

furniture:
  enabled: true
  chance: 0.08               # fraction of rooms with furniture

ambient-sounds:
  enabled: true

paranoid-messages:
  enabled: true
  chance: 0.04               # probability per player per ~15s

levels:
  enabled: true              # false = entire world is Hallways only

noclip-trigger:
  enabled: true
  chance: 0.15               # probability per suffocation hit

long-fall-trigger:
  enabled: true
  min-damage: 8.0            # raw damage threshold (~11 block fall)
  chance: 0.25               # probability per qualifying fall

portal-trigger:
  enabled: false             # off by default
  chance: 0.02               # probability per portal use
```

Reload with `/backrooms reload`. Generation settings (wall density, escape doors, furniture, levels) only affect newly generated chunks.

## Requirements

- **PaperMC** 1.21.x (or Purpur)
- **Java 21+**

## Building

```bash
./gradlew build              # output: build/libs/TheBackrooms.jar
./gradlew clean              # clean build directory
```
