# Teleport

A clean & revised teleportation system for Paper servers — one smart `/tp` command that replaces vanilla teleporting with player requests, random teleport, coordinate and entity teleports, all styled with MiniMessage and fully configurable.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25-orange.svg)](https://adoptium.net/)
[![Paper](https://img.shields.io/badge/Server-Paper-blue.svg)](https://papermc.io/)

## Features

- **Unified `/tp` command** — players, coordinates, `random`, entity selectors, and `look` all flow through one command.
- **Request flow** — without force permission, `/tp <player>` and `/tp <player> me` become tpa/tphere requests with clickable `[ Accept ] [ Decline ]` buttons.
- **Random teleport** — per-dimension toggles, configurable radius, cooldowns, and a safe-spot search (including nether air-pocket scanning).
- **Coordinate teleports** — absolute and `~` relative, with vanilla-style block centering.
- **Entity teleports** — full vanilla selector syntax (`/tp @e[type=zombie] me`) plus `/tp look` to grab whatever mob is in your crosshair.
- **Fully configurable** — every message is MiniMessage and editable; features toggle on/off in the config.

## Commands

Everything runs through `/tp` (alias `/teleport`):

| Command | Description |
| --- | --- |
| `/tp <player>` | Teleport to a player (or send a request without force permission) |
| `/tp <player> me` | Bring a player to you (or send a request) |
| `/tp <x> <y> <z>` | Teleport to coordinates (`~` relative supported) |
| `/tp <player> <x y z>` · `/tp <player> <player2>` | Move a player to coordinates or another player |
| `/tp random [dimension]` | Random teleport in the current or named dimension |
| `/tp @e[...] [destination]` | Teleport entities matched by a vanilla selector |
| `/tp look [destination]` | Teleport the mob you're looking at |
| `/tp accept \| deny [player]` | Respond to an incoming request |
| `/tp reload` | Reload the configuration |

## Permissions

| Node | Default | Grants |
| --- | --- | --- |
| `teleport.use` | everyone | Base `/tp` access |
| `teleport.coordinates` | everyone | Teleport yourself to coordinates |
| `teleport.request` | everyone | Send a request to teleport to a player |
| `teleport.summon` | everyone | Send a request to bring a player to you |
| `teleport.rtp` | everyone | Use random teleport |
| `teleport.rtp.dimensions` | everyone | Choose the random-teleport dimension |
| `teleport.rtp.cooldown.bypass` | op | Skip the random-teleport cooldown |
| `teleport.force` | op | Teleport players instantly, without a request |
| `teleport.entities` | op | Teleport entities via selectors / `look` |
| `teleport.reload` | op | Reload the configuration |

## Building from source

**Requirements**

- JDK 25 (e.g. [Temurin](https://adoptium.net/) or [Corretto](https://aws.amazon.com/corretto/))
- Maven 3.9+ (or any IDE with bundled Maven, such as IntelliJ IDEA)

**Build**

```bash
git clone https://github.com/yapalmuffiny/Teleport.git
cd Teleport
mvn clean package
```

The compiled plugin is written to `target/Teleport.jar`. Drop it into your server's `plugins/` folder.

> **Local deploy (maintainers):** the `local-deploy` Maven profile auto-copies the built jar into a server's `plugins/update/` folder. It activates only when that folder exists, so it never affects a normal clone. To use it on your own machine, point the path in the `local-deploy` profile (in `pom.xml`) at your server.

## Project structure

```
src/main/java/ca/zerodev/
  Teleport.java        Plugin entry point — config, service wiring, command registration
  TpCommand.java       The single /tp dispatcher and tab completion
  Messages.java        MiniMessage rendering, backed by config.yml
  RequestManager.java  Pending tpa/tphere requests and expiry sweep
  TeleportRequest.java  Immutable request record
  RtpService.java      Safe-spot search and per-player cooldowns
  Coords.java          Coordinate parsing (absolute and ~relative)

src/main/resources/
  plugin.yml           Plugin metadata, command, and permissions
  config.yml           Default configuration and all messages
```

## Contributing

Contributions are welcome. To keep the codebase consistent:

1. **Fork** the repo and create a branch off `main`.
2. **Build clean** — `mvn clean package` must succeed with no new warnings.
3. **Test** on an actual Paper server before opening a pull request.
4. **Open a PR** against `main` describing the change.

**Code conventions**

- All player-facing text goes through `Messages` (MiniMessage). Add new strings as keys in `config.yml` — they're back-filled as defaults at runtime, so existing configs keep working without a reset.
- Gate optional behaviour behind both a `features.*` config toggle and a permission node.
- Keep method bodies self-documenting (no inline comments); add Javadoc to public classes and any non-obvious public method.
- Match the existing style: four-space indentation, early returns, and the `requirePlayer` / `lacksForce` helpers for common guards.

## License

Released under the [MIT License](LICENSE). © 2026 Zero Development.
