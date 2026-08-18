# Commands

## Mod menu

| Command | Opens |
|---|---|
| `/sr` | main menu |
| `/sr shards` | shard list and guide |
| `/sr shards <name>` | the page of that shard |
| `/sr <word>` | settings, already searching for that word |
| `/sr top` | fusion rating |
| `/sr hud` | HUD editing, drag the overlays where you want them |
| `/sr calculator` | calculator settings |
| `/sr fusion` | fusion settings |
| `/sr hunting` | hunting settings |
| `/sr warps` | warps and waypoints |
| `/sr highlight` | mob highlight |
| `/sr foraging` | Torrhus Canyon, Galatea, Critter Safari |
| `/sr interface` | language, overlays, server texture pack |
| `/sr keys` | key bindings |

`/srhighlight` toggles the highlight for one mob without opening the menu:
`/srhighlight foxtrot`. Without an argument it prints the list of mobs.

## Keys

Three bindings, all unbound by default. Set them in `/sr keys` or in the vanilla
Controls screen, section SkyRyn:

- open `/sr top`
- open `/sr shards`
- stop tracking the current shard

## Critter Safari chat commands

Type these in party chat and the mod answers with your numbers. The short form is
in the second column. Commands ending in `r` cover the current run instead of the
whole session.

| Command | Short | Answers |
|---|---|---|
| `#profit` | `#pr` | coins per hour, session |
| `#profitr` | `#prr` | coins per hour, current run |
| `#essence` | `#es` | essence per hour, session |
| `#essencer` | `#esr` | essence per hour, current run |
| `#shards` | `#sh` | shards per hour, session |
| `#shardsr` | `#shr` | shards per hour, current run |
| `#capture` | `#ct` | catches per hour; `#ct <mob>` for one mob only |
| `#capturer` | `#ctr` | catches per hour, current run |
| `#exp` | `#ex` | hunting exp per hour, session |
| `#expr` | `#exr` | hunting exp per hour, current run |
| `#total` | `#tl` | profit for all time |
| `#essencetotal` | `#et` | essence for all time |
| `#shardstotal` | `#shtl` | shards for all time |
| `#capturetotal` | `#cttl` | catches for all time |
| `#expall` | `#ext` | hunting exp for all time |
| `#critterplaytime` | `#cpt` | time in safari, session |
| `#cptall` | | time in safari for all time |
| `#food` | | birdfeeder food in your inventory |
| `#perks` | | your safari perks (open the perks shop once so the mod can read them) |
| `#stats` | | everything about the current run |
| `#help` | | this list, in chat |

Party answers are off by default. Turn them on in `/sr foraging` → Critter Safari.
