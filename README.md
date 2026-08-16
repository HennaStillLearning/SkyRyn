# SkyRyn

Client-side Fabric mod for Hypixel SkyBlock, focused on the Foraging islands and the
Hunting mechanics. Works with both Normal and Ironman profiles.

Everything is read from what the game already shows you: chat, scoreboard and GUIs.
All settings live in one screen, opened with `/sr`.

## Features

**Fusion.** A calculator inside the Fusion Box that works out the cheapest path to the
shard you want, bazaar prices included. See `/sr shards` and `/sr top`.

**Shards.** A guide to every shard: where it drops, how to get it, what it fuses into.
Shard tracking, locations and attribute guides.

**Attribute Helper.** A companion panel for levelling attributes: what to fuse, how many
shards are left and how long it takes.

**Hunting.** Fusion tracker, Hunting tracker and Critter Safari tracker.

**Foraging.** Helpers for Torrhus Canyon, Galatea and Critter Safari: a Tiki totem
solver, a Safari run tracker, Honeycomb trackers and more.

**Serverpack disabler.** Turns the server texture pack off completely, or in hybrid
mode that keeps the custom items readable.

**Mod menu.** `/sr`.

## Installing

1. Install [Fabric](https://fabricmc.net/use/installer) and
   [Fabric API](https://modrinth.com/mod/fabric-api).
2. Download SkyRyn from [Modrinth](https://modrinth.com/mod/skyryn).
3. Put the jar into your `mods` folder.

## Documentation

- [Commands](docs/COMMANDS.md) — every command, key binding and party command.
- [Changelog](docs/CHANGELOG.md)

## Notes

Client-side only, nothing is needed on the server. The only outgoing request is
Hypixel's public bazaar endpoint, with no API key and no account data.

Special thanks to the author of [SkyShards](https://github.com/Campionnn/SkyShards)
for the fusion recipe data.

Licence: GPL-3.0-or-later, see [LICENSE](LICENSE).
