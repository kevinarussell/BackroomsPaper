# The Backrooms

A PaperMC plugin that generates an infinite, procedural Backrooms dimension. 
Fluorescent lights hum incessantly overhead. The smell of moist carpet fills your lungs. You are alone.

To enter, noclip out of reality by crush damage, long falls, or portal use. Or just use /backrooms.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/backrooms` | `backrooms.enter` | Send yourself in |
| `/backrooms <player>` | `backrooms.send` | Send another player |
| `/backrooms list` | `backrooms.admin` | List players currently inside |
| `/backrooms leave [player]` | `backrooms.admin` | Pull a player out |
| `/backrooms reload` | `backrooms.admin` | Reload configuration |

All permissions default to op.

## Configuration

```yaml
keep-inventory: true

# Lower = more maze-like (0.0–1.0)
wall-open-chance: 0.70

escape-door:
  enabled: true
  rarity: 500

furniture:
  enabled: true
  chance: 0.08

ambient-sounds:
  enabled: true

paranoid-messages:
  enabled: true
  chance: 0.04

levels:
  enabled: true              # false = Hallways only

noclip-trigger:
  enabled: true
  chance: 0.15

long-fall-trigger:
  enabled: true
  min-damage: 8.0
  chance: 0.25

portal-trigger:
  enabled: false
  chance: 0.02
```

Generation settings only affect newly generated chunks.

## Requirements

- **Paper** or **Purpur** for Minecraft 1.21.x
- **Java 21+**

## Building

```bash
./gradlew build    # output: build/libs/TheBackrooms.jar
```
