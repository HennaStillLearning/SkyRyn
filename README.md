# SkyRyn

Клиентский Fabric-мод для Hypixel SkyBlock: охота и фьюз шардов.
Мод читает то, что игра и так показывает (чат, скорборд, окна) и рисует поверх
свои плашки. Все настройки в одном экране, команда `/sr`.

*(English below)*

## Что умеет

**Фьюжен.** Калькулятор прямо в окне Fusion Box: считает самый дешёвый путь до
нужного шарда с учётом цен базара. Плюс трекер фьюзов и рейтинг `/sr top`.

**Шарды.** Справочник по всем шардам: откуда падает, как добыть, во что фьюзится.
Поиск и фильтры по стату и семье.

**Охота.** Трекер сессии (мобы, шарды, опыт, коины в час), подсветка выбранных
криттеров контуром, метки спотов с маршрутом и варпом.

**Форагинг.** Помощники по Torrhus Canyon, Galatea и Critter Safari: решатель
тотемов Tiki, трекер захода в сафари с пати-командами, отсчёт до криттера на
помазанных мёдом деревьях.

Интерфейс на русском и английском, переключается в `/sr`. При первом запуске
все функции выключены.

## Сборка

Нужен JDK 25 или новее.

```
./gradlew build
```

Готовый jar появится в `build/libs`. Положить его в `mods` рядом с Fabric API.

## Заметки

Мод работает только на клиенте, на сервере ничего не нужно. Наружу уходит один
запрос: публичный базарный эндпоинт Hypixel, без ключа и без данных аккаунта.
Данные о рецептах фьюза взяты из [SkyShards](https://github.com/Campionnn/SkyShards).

Лицензия: MIT, см. [LICENSE](LICENSE).

---

# SkyRyn (English)

Client-side Fabric mod for Hypixel SkyBlock: hunting and shard fusion.
It reads what the game already shows you (chat, scoreboard, GUIs) and draws its
own overlays on top. All settings live in one screen, the `/sr` command.

**Fusion.** A calculator inside the Fusion Box that works out the cheapest path to
the shard you want, bazaar prices included. Plus a fusion tracker and `/sr top`.

**Shards.** A guide to every shard: where it drops, how to get it, what it fuses
into. Search and filters by stat and family.

**Hunting.** Session tracker (mobs, shards, exp, coins per hour), an outline around
the critters you pick, waypoints to hunting spots with a route and a warp.

**Foraging.** Helpers for Torrhus Canyon, Galatea and Critter Safari: a Tiki totem
solver, a safari run tracker with party commands, and a countdown for critters on
trees you lathered with honeycomb.

English and Russian, switched in `/sr`. Everything is off on first launch.

## Building

Requires JDK 25 or newer.

```
./gradlew build
```

The jar lands in `build/libs`. Drop it into `mods` next to Fabric API.

## Notes

Client-side only, nothing is needed on the server. The only outgoing request is
Hypixel's public bazaar endpoint, with no API key and no account data.
Fusion recipe data comes from [SkyShards](https://github.com/Campionnn/SkyShards).

Licence: MIT, see [LICENSE](LICENSE).
