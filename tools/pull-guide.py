#!/usr/bin/env python3
"""
Переносит написанный гайд из живого конфига в мод.

Запуск:
    python tools/pull-guide.py                 # из config профиля rynmp
    python tools/pull-guide.py <путь>          # из любого файла

Зачем. Гайд, который ты пишешь, лежит в config/skyryn-shards.json — это твой
файл, мод его только читает. Но игроку он не достанется: у него этот файл
пустой. Чтобы гайд поехал вместе с модом, написанное надо перенести в ресурс
src/main/resources/skyryn/shard-info.json, который вшивается в jar.

Порядок:
    1. пишешь в config/skyryn-shards.json
    2. /sr reload в игре — смотришь, как вышло
    3. перед сборкой запускаешь этот скрипт
    4. пересобираешь мод

Что переносится:
    details — если непустой
    methods — целиком списком, если он есть

methods переносим ЦЕЛИКОМ, а не по полям: порядок методов задаёт автор, и
сборка их по кусочкам этот порядок потеряет.

Пустое в конфиге ничего не стирает в ресурсе: у мода те же правила, и
поведение должно совпадать, иначе "в игре вижу одно, в jar уехало другое".
"""

import json
import os
import shutil
import sys

CONFIG = os.path.join(
    os.environ.get("APPDATA", ""),
    "ModrinthApp", "profiles", "rynmp", "config", "skyryn-shards.json",
)
RES = os.path.join("src", "main", "resources", "skyryn", "shard-info.json")


def described(doc):
    return sum(
        1 for k, v in doc.items()
        if not k.startswith("_") and isinstance(v, dict)
        and ((v.get("details") or "").strip() or v.get("methods"))
    )


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else CONFIG
    if not os.path.exists(src):
        sys.exit(f"нет файла: {src}\nукажи путь аргументом, если он в другом месте")
    if not os.path.exists(RES):
        sys.exit(f"нет ресурса: {RES}")

    with open(src, encoding="utf-8") as f:
        cfg = json.load(f)
    with open(RES, encoding="utf-8") as f:
        res = json.load(f)

    before = described(res)

    # Копия на случай, если перенос окажется не тем, чего ждали. Однажды я
    # запустил старую версию скрипта и молча откатил текст в ресурсе — второй
    # раз этого быть не должно.
    backup = RES + ".bak"
    shutil.copy2(RES, backup)

    moved_details = moved_methods = 0
    for key, v in cfg.items():
        if key.startswith("_") or not isinstance(v, dict):
            continue
        dst = res.setdefault(key, {"details": "", "methods": []})

        text = (v.get("details") or "").strip()
        if text and dst.get("details") != v["details"]:
            dst["details"] = v["details"]
            moved_details += 1

        methods = v.get("methods")
        if isinstance(methods, list) and methods and dst.get("methods") != methods:
            dst["methods"] = methods
            moved_methods += 1

    with open(RES, "w", encoding="utf-8") as f:
        json.dump(res, f, ensure_ascii=False, indent=2)

    after = described(res)
    print(f"перенесено details: {moved_details}")
    print(f"перенесено methods: {moved_methods}")
    print(f"описано шардов:     {before} -> {after} из 189")
    print(f"копия старого:      {backup}")
    if after < before:
        print("\nВНИМАНИЕ: описанных стало МЕНЬШЕ. Похоже, конфиг устарел —")
        print("проверь, прежде чем собирать. Откат: скопируй .bak обратно.")
    else:
        print("\nтеперь пересобери мод — гайд поедет вместе с ним")


if __name__ == "__main__":
    main()
