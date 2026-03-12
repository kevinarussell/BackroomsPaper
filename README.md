# The Backrooms

A PaperMC plugin that lets you no-clip players into the Backrooms. There is no escape. (There is one escape.)

## Features

- Fully procedural world
- Six room types: standard, open, column rows, cluttered, partitioned, and pit rooms
- Bottomless pits that drop you to a random location somewhere else in the backrooms
- An ambient fluorescent hum to drive you mad
- An escape?
- Adventure mode only
- Dying respawns you back inside. Nice try.
- Configurable wall density, escape door rarity, furniture frequency, and more
- Return location and gamemode saved across server restarts

## Commands

| Command | Description |
|---|---|
| `/backrooms` | Send yourself to the Backrooms |
| `/backrooms <player>` | Send another player (requires `backrooms.send`) |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `backrooms.enter` | op | Use `/backrooms` on yourself |
| `backrooms.send` | op | Use `/backrooms <player>` on others |

## Configuration

```yaml
wall-open-chance: 0.70      # fraction of wall segments with a doorway (lower = more maze-like)

escape-door:
  enabled: true
  rarity: 500               # average rooms between escape doors (higher = rarer)

furniture:
  enabled: true
  chance: 0.08              # fraction of rooms with a piece of furniture

ambient-sounds:
  enabled: true

paranoid-messages:
  enabled: true
  chance: 0.04              # probability per player per ~15 s of receiving a message
```

## Requirements

- PaperMC (or Purpur)
- MC 1.21.11 is the only tested version
- Java 21+


