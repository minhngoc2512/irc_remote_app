#!/usr/bin/env python3
"""Merge upstream IR databases into one categorized SQLite file for the app.

    python3 build_db.py                       # all sources -> out/irdb.sqlite
    python3 build_db.py --sources flipper     # only CC0 Flipper-IRDB data

Run ./fetch_sources.sh first to clone the upstream repositories, and
irremote/build_ac.sh to generate the IRremoteESP8266 A/C states.
"""
from __future__ import annotations

import argparse
import math
import sqlite3
import subprocess
import time
from collections import Counter, defaultdict
from pathlib import Path

from irc.normalize import CATEGORIES, BrandNames, button_key, infer_category
from irc.sources import SOURCES, Codeset, iter_flipper, iter_irdb, iter_irremoteesp8266

HERE = Path(__file__).resolve().parent
SCHEMA_VERSION = 1
MAX_PATTERN_US = 2_000_000  # ConsumerIrManager.transmit() rejects patterns longer than 2 s
SOURCE_PRIORITY = {"flipper": 0, "irremoteesp8266": 0, "irdb": 1, "flipper_converted": 2}
# Matching-wizard order: curated, recently captured codes first.
SOURCE_RANK_BONUS = {"flipper": 8, "irremoteesp8266": 14, "irdb": 3, "flipper_converted": 0}
FRAME_GAP_US = 20_000


def varint_blob(values: list[int]) -> bytes:
    out = bytearray()
    for v in values:
        while v >= 0x80:
            out.append((v & 0x7F) | 0x80)
            v >>= 7
        out.append(v)
    return bytes(out)


def fingerprint(freq: int, timings: list[int]) -> tuple:
    """Tolerant identity of a signal: first frame, durations bucketed on a log scale."""
    frame = []
    for i, t in enumerate(timings):
        if i % 2 and t >= FRAME_GAP_US:
            break
        frame.append(round(math.log(max(t, 1)) / math.log(1.18)))
    return (round(freq / 2000), tuple(frame))


class Entry:
    def __init__(self, cs: Codeset, brand: str):
        self.cs, self.brand = cs, brand
        self.buttons = []  # (label, key, signal, protocol, params)
        self.fps: set[tuple] = set()
        self.merged = 0
        self.category = cs.category
        self.inferred = False


def git_rev(path: Path) -> str:
    try:
        return subprocess.check_output(["git", "-C", str(path), "rev-parse", "HEAD"], text=True).strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return ""


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--sources", default="flipper,flipper_converted,irdb,irremoteesp8266")
    ap.add_argument("--src-dir", type=Path, default=HERE / "sources")
    ap.add_argument("--out", type=Path, default=HERE / "out" / "irdb.sqlite")
    args = ap.parse_args()
    wanted = set(args.sources.split(","))
    t0 = time.time()

    readers = []
    if wanted & {"flipper", "flipper_converted"}:
        readers.append(iter_flipper(args.src_dir / "flipper-irdb"))
    if "irdb" in wanted:
        readers.append(iter_irdb(args.src_dir / "irdb"))
    if "irremoteesp8266" in wanted:
        readers.append(iter_irremoteesp8266(args.src_dir / "irremoteesp8266", HERE / "build" / "irremoteesp8266_ac.tsv"))

    brands = BrandNames()
    entries: list[Entry] = []
    stats = Counter()
    errors = Counter()
    for reader in readers:
        for cs in reader:
            if cs.source not in wanted:
                continue
            e = Entry(cs, brands.add(cs.brand))
            for b in cs.buttons:
                stats["buttons_in"] += 1
                if b.signal is None:
                    errors[b.error.split(":")[0] + ":" + b.protocol] += 1
                    continue
                if b.label.strip().lower() in ("", "unknown"):
                    stats["dropped_unnamed"] += 1
                    continue
                press = b.signal.press()
                if sum(press) > MAX_PATTERN_US or not press:
                    stats["dropped_too_long"] += 1
                    continue
                e.buttons.append((b.label, b.func_key or button_key(b.label), b.signal, b.protocol, b.params))
                e.fps.add(fingerprint(b.signal.freq, press))
            if not e.buttons:
                stats["codesets_empty"] += 1
                continue
            if e.category is None:
                e.category = infer_category({k for _, k, *_ in e.buttons if k}, [l for l, *_ in e.buttons])
                e.inferred = True
            entries.append(e)
    stats["codesets_in"] = len(entries)
    print(f"parsed {len(entries)} codesets in {time.time() - t0:.0f}s")

    # Drop codesets whose signals are all contained in a bigger codeset of the same brand.
    by_brand: dict[str, list[Entry]] = defaultdict(list)
    for e in entries:
        by_brand[e.brand].append(e)
    kept: list[Entry] = []
    for group in by_brand.values():
        group.sort(key=lambda e: (-len(e.fps), SOURCE_PRIORITY[e.cs.source], e.inferred))
        index: dict[tuple, list[Entry]] = defaultdict(list)
        for e in group:
            some = next(iter(e.fps))
            host = next((k for k in index[some] if e.fps <= k.fps
                         and (k.category == e.category or e.inferred or k.inferred)), None)
            if host:
                host.merged += 1 + e.merged
                if host.inferred and not e.inferred:
                    host.category, host.inferred = e.category, False
                stats["codesets_merged"] += 1
                continue
            kept.append(e)
            for fp in e.fps:
                index[fp].append(e)

    write_db(args, kept, brands, wanted)
    print(f"kept {len(kept)} codesets, {sum(len(e.buttons) for e in kept)} buttons -> {args.out} "
          f"({args.out.stat().st_size / 1e6:.1f} MB) in {time.time() - t0:.0f}s")
    for k, v in sorted(stats.items()):
        print(f"  {k}: {v}")
    print(f"  undecodable signals: {sum(errors.values())}")
    for k, v in errors.most_common(12):
        print(f"    {v:6d}  {k}")
    cat_count = Counter(e.category for e in kept)
    print("  per category: " + ", ".join(f"{c}={cat_count[c]}" for c, *_ in CATEGORIES))


def write_db(args, kept: list[Entry], brands: BrandNames, wanted: set[str]):
    args.out.parent.mkdir(parents=True, exist_ok=True)
    tmp = args.out.with_suffix(".tmp")
    tmp.unlink(missing_ok=True)
    db = sqlite3.connect(tmp)
    db.executescript((HERE / "schema.sql").read_text())

    revs = {"flipper": git_rev(args.src_dir / "flipper-irdb"), "irdb": git_rev(args.src_dir / "irdb"),
            "irremoteesp8266": git_rev(args.src_dir / "irremoteesp8266")}
    revs["flipper_converted"] = revs["flipper"]
    source_ids = {}
    for key in sorted(wanted):
        s = SOURCES[key]
        cur = db.execute("INSERT INTO sources(key,name,url,license,revision) VALUES(?,?,?,?,?)",
                         (key, s["name"], s["url"], s["license"], revs.get(key, "")))
        source_ids[key] = cur.lastrowid
    cat_ids = {}
    for i, (key, vi, en) in enumerate(CATEGORIES):
        cat_ids[key] = db.execute("INSERT INTO categories(key,name_vi,name_en,sort) VALUES(?,?,?,?)",
                                  (key, vi, en, i)).lastrowid
    brand_ids = {}
    for norm in sorted({e.brand for e in kept}, key=lambda n: brands.display(n).lower()):
        brand_ids[norm] = db.execute("INSERT INTO brands(name,norm) VALUES(?,?)",
                                     (brands.display(norm), norm)).lastrowid

    for e in kept:
        keys = {k for _, k, *_ in e.buttons if k}
        has_power = bool(keys & {"POWER", "POWER_ON"})
        rank = (min(e.merged, 3) * 3 + has_power * 5 + min(len(keys), 15) / 3
                + SOURCE_RANK_BONUS[e.cs.source] - 4 * e.inferred)
        cid = db.execute(
            "INSERT INTO codesets(brand_id,category_id,name,source_id,source_path,category_inferred,button_count,rank)"
            " VALUES(?,?,?,?,?,?,?,?)",
            (brand_ids[e.brand], cat_ids[e.category], e.cs.name, source_ids[e.cs.source], e.cs.path,
             int(e.inferred), len(e.buttons), round(rank, 2))).lastrowid
        db.executemany(
            "INSERT INTO buttons(codeset_id,func_key,label,freq,pattern,repeat,protocol,params) VALUES(?,?,?,?,?,?,?,?)",
            [(cid, key, label, sig.freq, varint_blob(sig.press()),
              varint_blob(sig.repeat) if sig.repeat != sig.press() else None, proto or None, params or None)
             for label, key, sig, proto, params in e.buttons])

    db.execute("INSERT INTO category_brands SELECT category_id, brand_id, COUNT(*) FROM codesets GROUP BY 1,2")
    db.executemany("INSERT INTO meta VALUES(?,?)", [
        ("schema_version", str(SCHEMA_VERSION)),
        ("built_at", time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())),
        ("sources", ",".join(sorted(wanted))),
    ])
    db.commit()
    db.execute("VACUUM")
    db.close()
    tmp.replace(args.out)


if __name__ == "__main__":
    main()
