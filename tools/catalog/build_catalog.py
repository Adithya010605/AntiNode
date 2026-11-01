#!/usr/bin/env python3
"""
Builds Circuit EQ's offline AutoEQ database from a sparse checkout of
https://github.com/jaakkopasanen/AutoEq (MIT).

    git clone --depth 1 --filter=blob:none --sparse https://github.com/jaakkopasanen/AutoEq.git work/AutoEq
    cd work/AutoEq && git sparse-checkout set --no-cone '/results/INDEX.md' \
        '/results/**/*ParametricEQ.txt' '/measurements/*/data/**/*.csv' '/targets/*.csv'
    python3 build_catalog.py work/AutoEq ../../app/src/main/assets/autoeq

Output (gzip, tab-separated, one record per line):
  catalog.tsv.gz  name, source, rig, type, preamp, filters, measurement
  targets.tsv.gz  name, curve
Curves are 192 log-spaced points from 20 Hz to 20 kHz, int16 centi-dB, little-endian, base64.
Sources that don't publish raw measurements (e.g. crinacle) only get AutoEQ's precomputed filters.
"""
import base64
import csv
import gzip
import math
import re
import struct
import sys
from pathlib import Path
from urllib.parse import unquote

GRID = [20 * 1000 ** (i / 191) for i in range(192)]
TYPES = ("in-ear", "over-ear", "earbud")
SKIP_TARGETS = {"711 5128 delta", "All Harman Targets", "zero", "Innerfidelity transformation 2016 to 2017"}
FILTER_RE = re.compile(r"Filter\s*\d+:\s*ON\s+(PK|LSC|HSC|LS|HS|LP|HP)\s+Fc\s+([\d.]+)\s*Hz\s+Gain\s+(-?[\d.]+)\s*dB\s+Q\s+([\d.]+)")
PREAMP_RE = re.compile(r"Preamp:\s*(-?[\d.]+)\s*dB")
INDEX_RE = re.compile(r"^- \[(.+?)\]\(\./(.+?)\) by (.+?)(?: on (.+))?$")


def read_curve(path: Path):
    freqs, values = [], []
    with path.open(newline="", encoding="utf-8", errors="replace") as f:
        reader = csv.reader(f)
        header = next(reader, None)
        if not header:
            return None
        cols = [h.strip().lower() for h in header]
        fi = cols.index("frequency") if "frequency" in cols else 0
        vi = cols.index("raw") if "raw" in cols else 1
        for row in reader:
            try:
                fr, v = float(row[fi]), float(row[vi])
            except (ValueError, IndexError):
                continue
            if fr > 0 and math.isfinite(v):
                freqs.append(fr)
                values.append(v)
    if len(freqs) < 10:
        return None
    pairs = sorted(zip(freqs, values))
    freqs = [p[0] for p in pairs]
    values = [p[1] for p in pairs]
    out, j = [], 0
    for g in GRID:
        while j < len(freqs) - 2 and freqs[j + 1] < g:
            j += 1
        if g <= freqs[0]:
            out.append(values[0])
        elif g >= freqs[-1]:
            out.append(values[-1])
        else:
            t = (math.log(g) - math.log(freqs[j])) / (math.log(freqs[j + 1]) - math.log(freqs[j]))
            t = min(max(t, 0.0), 1.0)
            out.append(values[j] + t * (values[j + 1] - values[j]))
    return out


def encode(curve):
    data = struct.pack("<192h", *(max(-32768, min(32767, round(v * 100))) for v in curve))
    return base64.b64encode(data).decode()


def clean(s: str) -> str:
    return s.replace("\t", " ").replace("\n", " ").strip()


def main(repo: Path, out: Path):
    out.mkdir(parents=True, exist_ok=True)

    # Measurements: measurements/<source>/data/<type>/[<rig>/]<name>.csv
    measurements = {}
    for p in (repo / "measurements").glob("*/data/**/*.csv"):
        parts = p.relative_to(repo / "measurements").parts
        source, kind = parts[0], parts[2]
        rig = parts[3] if len(parts) == 5 else ""
        measurements[(source.lower(), kind, rig, p.stem)] = p

    rows, with_fr = [], 0
    for line in (repo / "results" / "INDEX.md").read_text(encoding="utf-8").splitlines():
        m = INDEX_RE.match(line.strip())
        if not m:
            continue
        name, rel, source, rig = m.group(1), unquote(m.group(2)), m.group(3), m.group(4) or ""
        folder = repo / "results" / rel
        peq_file = folder / f"{folder.name} ParametricEQ.txt"
        if not peq_file.exists():
            continue
        text = peq_file.read_text(encoding="utf-8", errors="replace")
        pre = PREAMP_RE.search(text)
        filters = [f"{t},{fc},{g},{q}" for t, fc, g, q in FILTER_RE.findall(text)]
        if not filters:
            continue
        group = Path(rel).parts[1]  # "<rig> <type>" or "<type>"
        kind = next((t for t in TYPES if group.endswith(t)), "in-ear")
        rig_dir = group[: -len(kind)].strip()
        mp = measurements.get((source.lower(), kind, rig_dir, folder.name)) or measurements.get((source.lower(), kind, "", folder.name))
        fr = read_curve(mp) if mp else None
        if fr:
            with_fr += 1
        rows.append("\t".join([
            clean(name), clean(source), clean(rig or rig_dir), kind,
            pre.group(1) if pre else "0", ";".join(filters), encode(fr) if fr else "",
        ]))

    with gzip.open(out / "catalog.tsv.gz", "wt", encoding="utf-8", compresslevel=9) as f:
        f.write("\n".join(rows) + "\n")

    targets = []
    for p in sorted((repo / "targets").glob("*.csv")):
        if p.stem in SKIP_TARGETS or "original" in p.stem.lower() or p.stem in ("Innerfidelity 2016", "Innerfidelity 2017"):
            continue
        curve = read_curve(p)
        if curve:
            targets.append(f"{clean(p.stem)}\t{encode(curve)}")
    with gzip.open(out / "targets.tsv.gz", "wt", encoding="utf-8", compresslevel=9) as f:
        f.write("\n".join(targets) + "\n")

    print(f"{len(rows)} profiles ({with_fr} with measurements), {len(targets)} targets -> {out}")


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
