<div align="center">

# Aleeve Atlas

**A minimap and world map mod for Minecraft 1.21.11 with waypoints, entity radar, team synchronization, and temporary pings.**

English | [简体中文](README.zh_CN.md)

</div>

## Overview

Aleeve Atlas (`aleeve_atlas`) is a minimap and world map mod for Minecraft 1.21.11 on NeoForge. It records explored terrain, provides in-world and on-map markers, and supports cooperative map and waypoint sharing. Installing it on both the client and server enables synchronized features; a client-only installation retains local mapping features.

## Requirements

- Minecraft 1.21.11.
- NeoForge 21.11.x.
- AnvilLib 2.x (2.0.0 or later) built for Minecraft 1.21.11.
  - `anvillib_config` is required on both the client and server.
  - `anvillib_wheel`, `anvillib_rendering`, and `anvillib_util` are also required on the client.
  - `anvillib_integration` is optional and enables integrations such as FTB Teams.

## Installation

1. Install Minecraft 1.21.11 and NeoForge 21.11.x.
2. Install the required AnvilLib modules in your `mods` folder.
3. Place the Aleeve Atlas jar in the same `mods` folder and start the game.
4. For map synchronization, team features, and shared waypoints on a server, install Aleeve Atlas and `anvillib_config` on the server as well.

## Main Features

- Circular or square minimap with rotation, north locking, zoom, and coordinate, biome, light, and time displays.
- Full-screen world map with dragging, scrolling, waypoint management, and quick access to client settings.
- Incremental map exploration and persistent caches separated by server or save and dimension.
- Team map synchronization and waypoint sharing when the mod is installed on both sides.
- Entity radar with player avatars, configurable entity categories, and visibility rules.
- Custom waypoints, death points, in-world markers, and import/export commands.
- Temporary pings in the world and on maps, with a four-option wheel for danger, warning, general, and go-to pings.
- Territory overlay and external ping APIs for other mods.

## Default Controls

| Input | Action |
| --- | --- |
| `M` | Open or close the full-screen world map. |
| `N` | Create a new waypoint at your current position. |
| `=` / `-` | Zoom the minimap in or out. |
| Hold `Z` | Center and enlarge the minimap; releasing the key restores its previous position and size. |
| Short middle-click in the world or on the world map | Create a general temporary ping at the targeted location. |
| Long middle-click in the world or on the world map | Open the ping wheel; release the button to confirm the selected ping type. |

You can rebind the mod's keyboard controls in Minecraft's Controls settings.

## Configuration and Data

- Client configuration: `<game_dir>/config/aleeve_atlas-client.toml`. You can also open the client settings from the world map.
- Server configuration: `<world_dir>/serverconfig/aleeve_atlas-server.toml`.
- Map caches and waypoint data: `<game_dir>/data/aleeve_atlas/`. Map cache files are stored under `mapdata`, separated by server or save and dimension.

See the [configuration and interaction guide](docs/CONFIG.md) for more details (Chinese).

## Documentation

- [Technical documentation and developer APIs](docs/TECH.md) (Chinese)
- [Configuration and interaction guide](docs/CONFIG.md) (Chinese)

## License

- Code: LGPL-3.0-only; see [LICENSE](LICENSE).
- Assets such as textures and models: all rights reserved; see [ASSETS_LICENSE](ASSETS_LICENSE).
