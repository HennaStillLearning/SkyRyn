# Changelog

## 1.1.0

**Server texture pack**
- Third mode, Normal: the pack is left alone and loads exactly as the server sent
  it. Turning the feature off no longer means turning off the mod.
- Garden tools, mining gear and fishing nets get their old look back instead of a
  stack of paper. Hypixel now sends them as paper and draws them with the pack, so
  the mod restores the item they used to be — a head with its skin, a prismarine
  shard for drills, a cobweb for nets.
- Items whose old look is unknown keep the server texture, as before.

**Calculator**
- Buy-offer mode no longer treats dead buy orders as a real price. When the order
  side sits far below the offers, nobody fills it, and the calculator used to build
  paths through such shards as if they were free.
- Prices under 10 coins are no longer rounded down to "0/pc".

**Highlight**
- Names only: the marker above each mob was noise. Names are drawn for mobs you
  cannot recognise by sight.
- Pangolin is highlighted in both states, rolled up in its shell included.

## 1.0.0

First release. Minecraft 26.1.2, Fabric.

**Fusion**
- Calculator inside the Fusion Box: the cheapest path to the shard you want,
  bazaar prices included.
- Warnings on prices held up by a single seller or with no offers behind them.
- Fusion tracker and the `/sr top` rating.

**Shards**
- Guide to every shard: where it drops, how to get it, what it fuses into.
- Search and filters by stat and family, bestiary and Sea Creature Guide plaques.
- Attribute Helper: what to fuse to level an attribute and how long it takes.

**Hunting**
- Session tracker: mobs, shards, hunting exp, coins per hour, Hunter Fortune.
- Mob highlight, drawn only for mobs you can actually see.
- Waypoints to hunting spots with a route line and the warp to get there.

**Foraging**
- Torrhus Canyon: Tiki totem solver, Beeheemoth announce, critter highlights.
- Galatea: highlights for the local critters.
- Critter Safari: run tracker, party commands, announces for gates, sparkling
  critters, Doomspiral and the Wumpa round.
- Honeycomb tracker: countdown per lathered tree, hive refill timer, tree markers.

**Interface**
- English and Russian, switched in `/sr`.
- Every overlay can be dragged and resized; announces have their own position,
  size, colour and text.
- Server texture pack: off completely, or hybrid mode that keeps custom items
  readable while the rest of the game looks vanilla.
- Everything is off on first launch.
