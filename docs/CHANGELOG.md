# Changelog

## 1.1.0

**Server texture pack**
- New mode, Normal: the pack loads the way the server sent it and the mod stays out
  of the way. Switching the feature off no longer means switching off the mod.
- Garden tools, mining gear and fishing nets stay readable with the pack off. Drills
  show as prismarine shards, nets as cobwebs, the rest as the heads they used to be.
- Items with no known old look keep the server texture, as before.

**Calculator**
- Buy-offer mode ignores buy orders that sit far below the offers. Nobody fills those,
  and paths used to be built through such shards as if they were free.
- Prices under 10 coins keep a decimal instead of rounding down to zero.

**Highlight**
- Markers above mobs are gone. Names are drawn instead, and only for mobs that are
  hard to tell apart by sight.
- Pangolin is highlighted while rolled up in its shell.

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
