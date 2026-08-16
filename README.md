# SkyRyn

Client-side Fabric mod for **hunting and shard fusion** on Hypixel SkyBlock.
Everything is read from what the game already shows — chat, scoreboard, GUIs — and
drawn back as overlays. All settings live in one screen: `/sr`.

- **Fusion** — a calculator inside the Fusion Box (cheapest path to the shard you want,
  bazaar prices included), a fusion tracker, `/sr top`.
- **Shards** — a guide to every shard: where it drops, how to get it, what it fuses into.
- **Hunting** — session tracker, mob highlight, waypoints to hunting spots.
- **Foraging** — Torrhus Canyon, Galatea and Critter Safari helpers, honeycomb tracker.
- English and Russian, switched in `/sr`. Everything is off on first launch.

## Building

Requires JDK 25 or newer.

```
./gradlew build
```

The jar lands in `build/libs/`. Drop it into `mods/` next to Fabric API.

## Notes

- Client-side only: nothing is needed on the server.
- The only outgoing request is Hypixel's public bazaar endpoint — no API key,
  no account data, no telemetry.
- Fusion recipe data comes from [SkyShards](https://github.com/Campionnn/SkyShards)
  by Campionnn.

## Licence

MIT — see [LICENSE](LICENSE).
