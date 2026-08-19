# -*- coding: utf-8 -*-
"""
Собирает таблицу «чем предмет выглядел до перехода на бумагу».

Зачем. Часть вещей Hypixel теперь шлёт как minecraft:paper и рисует их серверным
паком. Отключишь пак — в руках стопка листов, вещь не опознать. Раньше те же вещи
были головами со скином или ванильными предметами, и этот вид можно вернуть, если
знать, чем предмет БЫЛ. Живой API этого уже не отдаёт: у бумажных записей поля
skin нет, оно осталось только у тех, кто головой и остался.

Дело не только в бумаге. Базу меняли и там, где она осталась предметом: дрели
теперь diamond_pickaxe, а были prismarine_shard — снимешь серверную картинку, и
в руках обычная кирка вместо привычного бура. Поэтому берём всё, чей прежний вид
отличается от нынешней базы.

Откуда данные. Исторический слепок NotEnoughUpdates-REPO (MIT), собранный в
PackDisabler (CC0) — в текущем master NEU эти записи уже переписаны на бумагу,
поэтому берём готовый слепок, а не сам репозиторий.

Берём не всё подряд, а только те семейства, где ванильный вид осмысленнее
серверного: инструменты Garden, снаряжение шахтёра и рыболовные сети. Остальным
бумажным вещам серверную картинку оставляем (см. MissingItemModelMixin).
"""
import json
import re
import urllib.request
import pathlib
import ssl

PD_JSON = "https://raw.githubusercontent.com/Noamm9/PackDisabler/HEAD/src/main/resources/skyblock-items.json"
HY_API = "https://api.hypixel.net/v2/resources/skyblock/items"
OUT = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/skyryn/item-fallback.json"

# Семейства. Мотыгу ищем по краям слова: подстрока HOE ловит ещё и SHOES.
FAMILIES = re.compile(
    r"(^|_)(HOE|HOES)($|_)"
    r"|DICER|FUNGI_CUTTER|CHOPPER|VACUUM|HYDRO|DIAGNOSTIC|SPRAYONATOR"
    r"|LOTUS|GARDEN_CHIP|COMPOST|BASKET_OF_SEEDS|SQUEAKY_MOUSEMAT"
    r"|DRILL|FUEL_TANK|PICKAXE|PICKONIMBUS|GAUNTLET"
    r"|FISHING_NET"
)


# Вещи новее слепка: своей записи нет, но это тиры уже известного инструмента —
# показываем вид базового. Лучше узнаваемый Sprayonator, чем стопка листов.
ALIASES = {
    "JUICY_SPRAYONATOR": "SPRAYONATOR",
    "SALTY_SPRAYONATOR": "SPRAYONATOR",
    "GIGANTIC_FISHING_NET": "BASIC_FISHING_NET",   # сеть как сеть — паутина
}


# Материалы в API названы по-старому, 1.8-стилем. Сводим к нынешним именам, иначе
# GOLD_AXE и golden_axe покажутся разными, и в таблицу попадёт лишняя пустая работа.
LEGACY = {
    "GOLD_AXE": "golden_axe", "GOLD_HOE": "golden_hoe", "GOLD_PICKAXE": "golden_pickaxe",
    "GOLD_SWORD": "golden_sword", "GOLD_SPADE": "golden_shovel",
    "WOOD_AXE": "wooden_axe", "WOOD_HOE": "wooden_hoe", "WOOD_PICKAXE": "wooden_pickaxe",
    "WOOD_SWORD": "wooden_sword", "WOOD_SPADE": "wooden_shovel",
    "IRON_SPADE": "iron_shovel", "DIAMOND_SPADE": "diamond_shovel", "STONE_SPADE": "stone_shovel",
    "SKULL_ITEM": "player_head", "CARROT_STICK": "carrot_on_a_stick",
    "EMPTY_MAP": "map", "INK_SACK": "ink_sac", "SULPHUR": "gunpowder",
    "RAW_FISH": "cod", "WATCH": "clock", "COMPASS": "compass",
}


def modern(material):
    """Нынешний id ванильного предмета по названию материала из API."""
    return "minecraft:" + LEGACY.get(material, material.lower())


def fetch(url):
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    with urllib.request.urlopen(url, context=ctx) as r:
        return json.loads(r.read().decode("utf-8"))


def main():
    old = fetch(PD_JSON)
    live = fetch(HY_API)["items"]

    out, skipped = {}, []
    for it in live:
        sid = it["id"]
        if not FAMILIES.search(sid):
            continue
        was = old.get(sid) or old.get(ALIASES.get(sid, ""))
        if not was or not was.get("model"):
            skipped.append(sid)
            continue
        model = was["model"]
        # Совпало с нынешней базой — записи не нужно: сняли серверную модель, и
        # предмет сам по себе выглядит как надо.
        if model == modern(it.get("material", "")):
            continue
        rec = {"item": model}
        if was.get("texture"):
            rec["skin"] = was["texture"]
        out[sid] = rec

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, indent=1, sort_keys=True), encoding="utf-8")
    heads = sum(1 for v in out.values() if "skin" in v)
    print("записей: %d (из них голов со скином: %d)" % (len(out), heads))
    print("файл: %s (%.1f КБ)" % (OUT, OUT.stat().st_size / 1024))
    if skipped:
        print("нечего возвращать (%d): %s" % (len(skipped), ", ".join(sorted(skipped))))


if __name__ == "__main__":
    main()
