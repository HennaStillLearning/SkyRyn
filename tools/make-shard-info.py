#!/usr/bin/env python3
"""
Генерит болванку гайда: src/main/resources/skyryn/shard-info.json.

Запуск:
    python tools/make-shard-info.py

Что делает:
  1. берёт все шарды из shards.json
  2. каждому кладёт пустые details/methods
  3. шардам с %-бонусом к УРОНУ сразу вписывает объяснение механики —
     оно одно и то же на всех, вписывать его руками 16 раз незачем

ВАЖНО: это болванка для ПЕРВОГО запуска. Живой файл игрока лежит в
config/skyryn-shards.json, и мод его никогда не перезаписывает. Скрипт
тоже не трогает config — только ресурс в jar.

Если запустить повторно, уже написанный текст в РЕСУРСЕ сотрётся.
Поэтому скрипт спрашивает подтверждение, если файл уже есть.
"""

import json
import os
import re
import sys

DB = os.path.join("src", "main", "resources", "skyryn", "shards.json")
OUT = os.path.join("src", "main", "resources", "skyryn", "shard-info.json")

# Механика %-урона. Замерено в игре: 540 урона без Undead Ruler -> 575 с ним,
# хотя в тултипе +39%. То есть прирост +6.5%, а не +39%.
# Подтверждено автором SkyShards: "its all additive, gets added to the dmg% multiplier".
DMG_EXPLAIN = (
    "АДДИТИВНЫЙ бонус: складывается, а не умножает.\n"
    "\n"
    "Все %-бонусы к урону лежат в одной общей куче — уровень боя, чары, шмот, "
    "другие шарды. Аттрибут просто досыпает туда свои проценты.\n"
    "\n"
    "Пример: у тебя уже +500% урона из другого. Аттрибут даёт +30%. Стало "
    "+530% — и урон вырос всего на 5%.\n"
    "\n"
    "Поэтому чем ты сильнее, тем меньше толку. У игрока с +50% тот же аттрибут "
    "дал бы почти честные +20%."
)

# Механика статов-множителей. Замерено на Etherdrake:
#   Crit Damage 1214.77% -> 1226.92% = ровно x1.01 при +1% на 10 уровне.
MULT_EXPLAIN = (
    "МНОЖИТЕЛЬ: умножает, а не складывается.\n"
    "\n"
    "Этот процент умножает сам стат целиком — вместе со всем, что тебе дают "
    "шмот, петы и рефоржи. На 10 уровне +1% значит x1.01 от твоего стата.\n"
    "\n"
    "Тем и отличается от %-аттрибутов на урон: те досыпаются в общую кучу и "
    "тем слабее, чем ты жирнее. Этот работает от твоего стата, так что чем "
    "больше стат, тем больше прибавка."
)

MULT_MEASURED = "\n\nЗамерено в игре: Crit Damage 1214.77% -> 1226.92%, ровно x1.01."

# Шарды, где % — это НЕ урон, а что-то своё. Механика другая, текст им не подходит.
NOT_DAMAGE = {
    "R42",  # Matcho — длительность поджога
    "R39",  # Wither Specter — стоимость маны
}


def is_damage_percent(sid, desc):
    """Даёт ли шард %-бонус именно к урону, аддитивный."""
    if sid in NOT_DAMAGE or is_stat_multiplier(desc):
        return False
    if not re.search(r"\+\d+(?:\.\d+)?%", desc):
        return False
    return bool(re.search(r"\bDamage\b|damage", desc))


def is_stat_multiplier(desc):
    """
    "Your X is increased by +N%" / "Increases your X by +N%" — множитель стата.

    Оборот не случайный: именно так написан Etherdrake, на котором множитель
    и замерен. У аддитивных к урону оборот другой — "grants +N% more Damage".
    """
    return bool(re.search(r"(is increased by|Increases your .* by)\s*\+\d", desc))


def main():
    if not os.path.exists(DB):
        sys.exit(f"нет файла: {DB} — сначала запусти import-skyshards.py")

    if os.path.exists(OUT) and "--force" not in sys.argv:
        sys.exit(f"{OUT} уже есть. Перезапись сотрёт текст в нём.\n"
                 f"Если правда надо — запусти с --force.")

    with open(DB, encoding="utf-8") as f:
        shards = json.load(f)["shards"]

    doc = {
        "_comment": [
            "ТВОЙ файл. Мод его только читает и НИКОГДА не перезаписывает.",
            "",
            "Ключ = имя шарда в нижнем регистре, как в списке /sr shards.",
            "Все поля необязательные: чего нет — того экран не покажет.",
            "",
            "  details  — секция 'Подробнее': что шард даёт и как работает.",
            "  methods  — способы добычи, в твоём порядке. Пусто = не написано.",
            "      type   — hunting | fusing | trap | chest",
            "      text   — описание. У fusing не нужен: мод сам даёт кнопку",
            "               в калькулятор",
            "      warp   — команда, например '/warp bayou'. Кликается",
            "      coords — координаты спота, например '-274 68 -412'",
            "      images — ссылки на скрины, открываются в браузере",
            "      video  — ссылка на видео",
            "",
            "Переносы строк — обычный \\n.",
            "",
            "details у шардов с %-уроном уже заполнены: механика у них одна",
            "и та же, и она проверена замерами в игре.",
        ]
    }

    dmg = mult = 0
    for key, v in sorted(shards.items()):
        sid = v.get("id", "")
        desc = v.get("attrDesc", "")
        details = ""
        if is_damage_percent(sid, desc):
            details = DMG_EXPLAIN
            dmg += 1
        elif is_stat_multiplier(desc):
            details = MULT_EXPLAIN
            # Замер есть только по Etherdrake — остальным его приписывать нельзя.
            if sid == "L39":
                details += MULT_MEASURED
            mult += 1
        doc[key] = {"details": details, "methods": []}

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)

    print(f"записано: {OUT}")
    print(f"  шардов:              {len(shards)}")
    print(f"  details: аддитивные: {dmg} (%-урон)")
    print(f"  details: множители:  {mult} (статы)")


if __name__ == "__main__":
    main()
