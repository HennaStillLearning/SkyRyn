# SkyRyn

Client-side Fabric mod for Hypixel SkyBlock, focused on the Foraging islands and the
Hunting mechanics. Works with both Normal and Ironman profiles.

The mod reads what the game already shows you (chat, scoreboard, GUIs) and draws its own
overlays on top. All settings live in one screen, opened with `/sr`.

## Features

**Fusion.** A calculator inside the Fusion Box that works out the cheapest path to the
shard you want, with bazaar prices included. See `/sr shards` and `/sr top`.

**Shards.** A guide to every shard: where it drops, how to get it, what it fuses into.
Includes shard tracking, locations and attribute guides.

**Attribute Helper.** A companion panel for levelling attributes. It shows what to fuse,
how many shards are left and how long the farm takes.

**Hunting.** Fusion tracker, Hunting tracker and Critter Safari tracker.

**Foraging.** Helpers for Torrhus Canyon, Galatea and Critter Safari: a Tiki totem
solver, a Safari run tracker, Honeycomb trackers and more.

**Serverpack disabler.** Turns the server texture pack off completely, or runs in hybrid
mode that keeps the custom items readable.

**Mod menu.** `/sr`.

## Installing

1. Install [Fabric](https://fabricmc.net/use/installer) and
   [Fabric API](https://modrinth.com/mod/fabric-api).
2. Download SkyRyn from [Modrinth](https://modrinth.com/mod/skyryn).
3. Put the jar into your `mods` folder.

## Documentation

- [Commands](docs/COMMANDS.md): every command, key binding and party command.
- [Changelog](docs/CHANGELOG.md)

## Notes

The mod runs on the client only, nothing is needed on the server. The only outgoing
request goes to Hypixel's public bazaar endpoint, without an API key and without any
account data.

Special thanks to the author of [SkyShards](https://github.com/Campionnn/SkyShards)
for the fusion recipe data.

Item look data comes from [NotEnoughUpdates-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO)
(MIT) and [PackDisabler](https://github.com/Noamm9/PackDisabler) (CC0). No resource
pack assets are shipped with the mod.

Licence: GPL-3.0-or-later, see [LICENSE](LICENSE).
