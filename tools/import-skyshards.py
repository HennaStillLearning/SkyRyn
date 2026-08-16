#!/usr/bin/env python3
"""
Импорт базы шардов из SkyShards в наш формат.

Запуск:
    python tools/import-skyshards.py                       # качает данные с GitHub
    python tools/import-skyshards.py --repo <путь к клону> # берёт из клона

Что делает:
  1. читает public/fusion-data.json — там и рецепты, и блок shards
     с готовым internal_id
  2. читает src/desc.json — аттрибут каждого шарда: название и что даёт
  3. сверяет каждый internal_id с ЖИВЫМ базаром Hypixel — просто как проверку
  4. пишет src/main/resources/skyryn/shards.json в нашей схеме

ВАЖНО: bazaarId берём ТОЛЬКО из internal_id, выводить его из имени нельзя.
Имена и id расходятся сильнее, чем кажется:
    "Cinderbat"           -> SHARD_CINDER_BAT
    "Bogged"              -> SHARD_SEA_ARCHER
    "Inkling"             -> SHARD_NIGHT_SQUID
    "Loch Emperor"        -> SHARD_SEA_EMPEROR
    "Inferno Demonlord"   -> SHARD_BURNINGSOUL
    "Abyssal Lanternfish" -> SHARD_ABYSSAL_LANTERN

Почему так, а не тянуть данные в рантайме: файл вшивается в jar, мод не ходит
в чужие репозитории и не зависит от их аптайма. Обновление после патча Hypixel —
это перезапуск скрипта и пересборка, а не сюрприз у всех пользователей.

Данные о рецептах — факты об игре, собранные комьюнити на форумах Hypixel.
Источник структуры: https://github.com/Campionnn/SkyShards
"""

import argparse
import json
import os
import re
import subprocess
import sys
import urllib.request

BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar"
OUT_PATH = os.path.join("src", "main", "resources", "skyryn", "shards.json")

# Ветка по умолчанию у SkyShards — master, не main. На main лежит 404.
RAW = "https://raw.githubusercontent.com/Campionnn/SkyShards/master/"
FUSION_DATA = "public/fusion-data.json"
DESC_DATA = "src/desc.json"
PROPS_DATA = "public/fusion-properties.json"
# Скорость прямой добычи, шардов/час, ключ = id SkyShards (C4, U10, L14).
# Нужна для ironman-режима: там оптимальный путь минимизирует ВРЕМЯ фарма
# (стоимость шарда = 1/rate), а не цену базара. 0/нет записи = напрямую не
# фармится (только фьюз). Источник — тот же репозиторий SkyShards.
RATES_DATA = "public/rates.json"

# Шарды без аттрибута: syphon для них невозможен, уровня не существует.
# В данных SkyShards это никак не помечено — у Chameleon там тоже есть title
# и description, хотя "Fusions with Chameleons grant the next 3 IDs as results"
# это правило фьюза, а не аттрибут. Проверено в игре: в Attribute Menu его нет.
NO_ATTRIBUTE = {"L4"}  # Chameleon

# Патчи Hypixel убирают возможность зафьюзить В некоторые шарды (получить их
# фьюзом). Апстрим SkyShards обновляется с задержкой — держим свой список, чтобы
# регенерация не вернула запрещённый рецепт. Ключ = наше имя шарда в нижнем регистре.
NO_FUSE_INTO = {
    "cocoaleech",  # 0.26.1: "You can no longer fuse into the Cocoaleech Shard"
}


def _download(url: str) -> dict:
    """
    Устойчивая загрузка. На Windows urllib часто падает CERTIFICATE_VERIFY_FAILED
    (нет набора корневых сертификатов) — поэтому пробуем обычный SSL-контекст, потом
    без проверки сертификата, и только затем curl. Ошибки печатаем целиком, чтобы
    было видно причину (раньше curl -s глушил её, и падало «молча»).
    """
    import ssl
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 SkyRyn-importer"})
    last = None
    for ctx in (ssl.create_default_context(), ssl._create_unverified_context()):
        try:
            with urllib.request.urlopen(req, timeout=30, context=ctx) as r:
                return json.load(r)
        except Exception as e:
            last = e
    print(f"  urllib не смог ({type(last).__name__}: {last}); пробую curl...")
    # -f: падать на HTTP-ошибке, -S: показывать ошибку, -L: следовать редиректам, -k: без проверки серта.
    out = subprocess.run(["curl", "-fSL", "-k", url], capture_output=True, timeout=60)
    if out.returncode != 0 or not out.stdout:
        sys.exit(f"не удалось скачать {url}: rc={out.returncode} {out.stderr.decode(errors='replace')[:400]}")
    return json.loads(out.stdout)


def fetch_bazaar_ids():
    """Множество id шардов, торгуемых на базаре. Это лишь СВЕРКА — если базар
    недоступен (Hypixel режет/тайм-аут), не валим импорт, а пропускаем проверку."""
    try:
        data = _download(BAZAAR_URL)
        return {pid for pid in data.get("products", {}) if pid.startswith("SHARD_")}
    except SystemExit as e:
        print(f"  базар недоступен — пропускаю сверку ({str(e)[:120]})")
        return None


# Имена цветов Minecraft -> §-коды. Так же красит гайд, так же рендерит мод.
_MC_COLOR = {
    "black": "0", "dark_blue": "1", "dark_green": "2", "dark_aqua": "3",
    "dark_red": "4", "dark_purple": "5", "gold": "6", "gray": "7",
    "dark_gray": "8", "blue": "9", "green": "a", "aqua": "b",
    "red": "c", "light_purple": "d", "yellow": "e", "white": "f",
}


def _strip_glyphs(t):
    """Убрать PUA-глифы (U+E000..U+F8FF): это иконки из кастомного шрифта SkyShards
    (Defense/Farming Fortune и т.п.). В ванильном MC они = квадраты-тофу, текст без
    них читается нормально ("Grants +4 Defense against Animal mobs")."""
    return re.sub(r"[-]", "", t)


def _seg_color(c):
    """Цвет сегмента -> §-код. Имя MC или #rrggbb (hex огрубляем до ближайшего кода)."""
    if not c:
        return "7"  # лор аттрибут-меню по умолчанию серый
    if c in _MC_COLOR:
        return _MC_COLOR[c]
    return "7"  # #hex в §-палитру точно не ложится — оставляем серым


def how_to_hunt_text(lines):
    """
    how_to_hunt из desc.json (массив строк, строка = массив цветных сегментов)
    -> один §-текст в нашем стиле. Пустые/пробельные строки пропускаем, каждую
    строку начинаем с §r, чтобы цвет не тёк с предыдущей.
    """
    if not lines:
        return ""
    out = []
    for segs in lines:
        parts = []
        for s in segs:
            if "range" in s:
                lo, hi = (s["range"] + [None, None])[:2]
                unit = s.get("unit", "")
                t = f"{hi}{unit}" if lo is None else f"{lo}-{hi}{unit}"
            else:
                t = _strip_glyphs(s.get("t", ""))
            if not t:
                continue
            parts.append(f"§{_seg_color(s.get('c'))}{t}")
        line = ("§r" + "".join(parts)).rstrip()
        # схлопнуть ведущие пробелы после кодов: "§r §7- " -> аккуратный буллет
        if line.strip("§r §70"):  # не пустая по смыслу
            out.append(line)
    return "\n".join(out)


def desc_to_attr(segs):
    """
    description из desc.json (сегменты) -> §-цветная строка аттрибута НА 1 УРОВНЕ.

    Формат SkyShards поменялся: раньше description был строкой, теперь массив
    цветных сегментов, где значение — диапазон [ур.1, ур.10]. Мод хранит только
    ур.1 и множит на уровень сам (ShardAttribute ищет "+N" и делает ×10), поэтому
    range пишем как "+N", где N = range[0] (значение на 1 уровне). Цвета — как у
    SkyShards. Символы стата (❤ ✎) в сегментах отсутствуют — их SkyShards рисует
    своим шрифтом, у нас их нет.
    """
    if not segs:
        return ""
    parts = []
    for s in segs:
        if "range" in s:
            r = s.get("range") or []
            val = r[0] if r and r[0] is not None else (r[1] if len(r) > 1 else None)
            if val is None:
                continue
            if isinstance(val, float) and val.is_integer():
                val = int(val)
            sign = "+" if isinstance(val, (int, float)) and val >= 0 else ""
            t = f"{sign}{val}{s.get('unit', '')}"
        else:
            t = _strip_glyphs(s.get("t", ""))
        if not t:
            continue
        parts.append(f"§{_seg_color(s.get('c'))}{t}")
    return re.sub(r" {2,}", " ", "".join(parts)).strip()


def _read(repo, rel):
    """Из клона, если он дан, иначе качаем с GitHub."""
    if repo:
        path = os.path.join(repo, *rel.split("/"))
        if not os.path.exists(path):
            sys.exit(f"нет файла: {path}")
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    print(f"качаю {rel}...")
    return _download(RAW + rel)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", help="путь к клону github.com/Campionnn/SkyShards "
                                   "(без него данные качаются с GitHub)")
    args = ap.parse_args()

    # Прошлый импорт нужен ради одного: НЕ перезаписывать уже записанный howToHunt
    # (заполняем только новым/отсутствующим). attrDesc старых шардов больше НЕ
    # переносим — их тоже синкаем из SkyShards: статы могли поменяться с патчем.
    prev_shards = {}
    if os.path.exists(OUT_PATH):
        try:
            with open(OUT_PATH, encoding="utf-8") as f:
                prev_shards = json.load(f).get("shards", {})
        except Exception:
            prev_shards = {}

    raw = _read(args.repo, FUSION_DATA)
    props = raw["shards"]
    recipes_raw = raw["recipes"]

    # Аттрибуты лежат ОТДЕЛЬНО от fusion-data.json, ключи те же (C1, U7, ...).
    desc = _read(args.repo, DESC_DATA)

    # Ещё один файл, ещё те же ключи. Нужен ради одного признака: пустые
    # input1/input2 = шард ловится напрямую, а не только фьюзится. Именно по
    # нему skyshards.com рисует бейджи Direct/Fuse.
    #
    # Сверено с тем, что знаем наверняка: chameleon, chill, pest, cinderbat —
    # напрямую; grove, mist, flash — только фьюз. 8 из 9 совпало.
    # Исключение: Apex Dragon падает из сундуков данжа, но тут он "только фьюз".
    # Это не ошибка признака: SkyShards — калькулятор фьюза, дроп из сундуков
    # он не описывает вовсе. Значит признак = "ловится охотой", а сундуки
    # остаются на автора гайда.
    fprops = _read(args.repo, PROPS_DATA)

    # Скорость фарма (шардов/час) по id. Для ironman-оптимизатора времени.
    rates = _read(args.repo, RATES_DATA)

    print(f"прочитано: {len(props)} шардов, {len(recipes_raw)} с рецептами, "
          f"{len(desc)} описаний аттрибутов, {len(rates)} ставок фарма")

    print("сверяю internal_id с базаром...")
    bazaar = fetch_bazaar_ids()
    if bazaar is not None:
        print(f"на базаре торгуется {len(bazaar)} шардов")

    # id skyshards (C30) -> наш ключ (имя в нижнем регистре). Имена уникальны — проверено.
    key_by_id = {sid: p["name"].lower() for sid, p in props.items()}

    shards = {}
    missing = []
    for sid, p in props.items():
        key = key_by_id[sid]
        bz = p.get("internal_id")
        # Сверяем только если базар доступен; иначе доверяем internal_id из SkyShards.
        if bazaar is not None and bz not in bazaar:
            missing.append(f"{p['name']} ({bz})")
            bz = None
        # id вида C12/U7/L3 — это id самого Hypixel: в Attribute Menu он написан
        # прямо в лоре ("Source: Chill Shard (C12)"). По нему уровень аттрибута
        # ложится на шард однозначно, без сверки имён.
        entry = {"id": sid, "name": p["name"], "bazaarId": bz,
                 "rarity": p.get("rarity", "common")}
        # source: Global / Hunting / Mining / Foraging / ... — откуда шард родом
        if p.get("type"):
            entry["source"] = p["type"]
        # family: "Bug Family", "Reptile and Croco Family", ... У 58 шардов "Unknown Family" —
        # такую не тащим, в интерфейсе она только шумит.
        fam = p.get("family", "")
        if fam and "Unknown" not in fam:
            entry["family"] = fam.replace(" Family", "")
        # Crocodile умножает выход рецептов, где вход из семейства Reptile.
        # family — строка вида "Reptile and Croco Family", поэтому ищем подстроку.
        if "Reptile" in p.get("family", ""):
            entry["reptile"] = True

        # Аттрибут: название ("Nature Elemental") и что даёт на 1 уровне
        # ("Grants +2 ❤ Health"). Диапазон до 10 уровня НЕ храним — его считает
        # мод, правило одно на всех: каждое +N превращается в +N..+N*10.
        a = desc.get(sid)
        if a and a.get("title"):
            entry["attrTitle"] = a["title"]
        # attrDesc берём из SkyShards ДЛЯ ВСЕХ шардов: с патчем статы аттрибутов
        # могли поменяться и у старых. §-цветная строка в цветах SkyShards;
        # парсеры читают attrDescEnPlain() (§ снят), поиск фраз не рвётся.
        if a and a.get("description"):
            entry["attrDesc"] = desc_to_attr(a["description"])
        if sid in NO_ATTRIBUTE:
            entry["noLevels"] = True

        pr = fprops.get(sid)
        if pr is not None and not pr.get("input1") and not pr.get("input2"):
            entry["direct"] = True
        # rate — скорость прямой добычи, шардов/час. Пишем только если >0:
        # 0 = напрямую не фармится, в коде это и есть значение по умолчанию.
        rate = rates.get(sid, 0)
        if rate and rate > 0:
            entry["rate"] = rate
        # fuse_amount — сколько ЭТОГО шарда берётся за один фьюз (не всегда 5!).
        # Многие (Grove, Aero, King Cobra, Python…) берутся по 2. Без этого числа
        # количества входов раздуваются, и оптимизатор выбирает не те рецепты.
        fa = p.get("fuse_amount", 5)
        if fa and fa != 5:
            entry["fuse"] = fa
        # how_to_hunt из аттрибут-меню — ТОЛЬКО шардам, которых раньше не было.
        # У старых свои гайды написаны руками, чужой текст им не нужен. Правило
        # раньше звучало «новым и отсутствующим», и на первом же импорте под
        # «отсутствующим» попали все 183 старых шарда: поля-то ещё ни у кого не было.
        prev_htt = prev_shards.get(key, {}).get("howToHunt")
        if prev_htt:
            entry["howToHunt"] = prev_htt
        elif key not in prev_shards and a:
            htt = how_to_hunt_text(a.get("how_to_hunt"))
            if htt:
                entry["howToHunt"] = htt
        shards[key] = entry

    out_recipes = {}
    pairs = 0
    skipped = 0
    for out_id, by_qty in recipes_raw.items():
        out_key = key_by_id.get(out_id)
        if out_key is None:
            skipped += 1
            continue
        if out_key in NO_FUSE_INTO:  # фьюз в этот шард запрещён патчем
            skipped += 1
            continue
        lst = []
        for qty_s, plist in by_qty.items():
            qty = int(qty_s)
            for a_id, b_id in plist:
                a, b = key_by_id.get(a_id), key_by_id.get(b_id)
                if a is None or b is None or qty <= 0:
                    skipped += 1
                    continue
                lst.append({"a": a, "b": b, "qty": qty})
                pairs += 1
        if lst:
            out_recipes[out_key] = lst

    doc = {
        "_comment": [
            "СГЕНЕРИРОВАНО tools/import-skyshards.py — руками не править, перезапишется.",
            "Твои описания шардов держи в отдельном файле, иначе переимпорт их сотрёт.",
            "",
            "Модель как в игре: фьюз берёт 5 шардов A + 5 шардов B и даёт qty на выходе.",
            "qty=2 — особый рецепт (в игре пишет 'special fusion recipe! x2 Shards!'),",
            "qty=1 — общий рецепт.",
            "",
            "rarity нужна для XP: фьюз даёт base(редкость) * (1 + hunting wisdom/100),",
            "где база = 75/150/300/500/1000 для common/uncommon/rare/epic/legendary.",
            "Она же задаёт, сколько шардов нужно на 10 уровень аттрибута:",
            "common 96 / uncommon 64 / rare 48 / epic 32 / legendary 24.",
            "",
            "Рецепты — факты об игре, собранные комьюнити на форумах Hypixel.",
            "Структура взята из github.com/Campionnn/SkyShards",
        ],
        "shards": shards,
        "recipes": out_recipes,
    }

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))

    size = os.path.getsize(OUT_PATH)
    print(f"\nзаписано: {OUT_PATH}")
    print(f"  шардов:   {len(shards)}")
    print(f"  рецептов: {len(out_recipes)} ({pairs} пар)")
    print(f"  reptile:  {sum(1 for v in shards.values() if v.get('reptile'))}")
    print(f"  аттрибутов: {sum(1 for v in shards.values() if v.get('attrDesc'))}")
    print(f"  ловятся напрямую: {sum(1 for v in shards.values() if v.get('direct'))}")
    print(f"  со ставкой фарма: {sum(1 for v in shards.values() if v.get('rate'))}")
    print(f"  размер:   {size // 1024} КБ")
    if skipped:
        print(f"  пропущено битых пар: {skipped}")
    if missing:
        print(f"\n  НЕТ НА БАЗАРЕ ({len(missing)}): {', '.join(missing)}")
        print("  у них bazaarId=null — цены не будет, рецепты с ними оптимизатор пропустит")


if __name__ == "__main__":
    main()
