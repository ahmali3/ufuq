#!/usr/bin/env python3
"""Build Ufuq's offline city database from GeoNames (CC-BY 4.0).

Downloads cities15000 (all places with 15,000+ people), admin1 names and country names,
and writes app/src/main/assets/cities.tsv:

  line 1   UFUQ-CITIES<TAB>1<TAB><count><TAB><build date>
  line 2   #tz<TAB>zone0<TAB>zone1 ...          (IANA zone ids)
  line 3   #country<TAB>name0<TAB>name1 ...     (English country names)
  rows     name, region, countryIdx, lat, lon, elevationM, tzIdx, alt1|alt2|...

Rows are sorted by population (largest first) so search ranks big cities higher.
Usage: python3 tools/build_cities.py [--cache DIR]
"""
import argparse
import datetime
import io
import os
import re
import sys
import urllib.request
import zipfile

BASE = "https://download.geonames.org/export/dump/"
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "cities.tsv")

ARABIC = re.compile(r"[؀-ۿ]")
LATIN_OK = re.compile(r"^[A-Za-z][A-Za-z '\-\.]*$")
MAX_ARABIC = 3
MAX_LATIN = 6


def fetch(name, cache):
    path = os.path.join(cache, name)
    if not os.path.exists(path):
        print(f"downloading {name}", file=sys.stderr)
        with urllib.request.urlopen(BASE + name, timeout=120) as r, open(path, "wb") as f:
            f.write(r.read())
    with open(path, "rb") as f:
        return f.read()


def clean(s):
    return s.replace("\t", " ").replace("|", " ").strip()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", default=os.path.join(os.path.dirname(__file__), ".cache"))
    args = ap.parse_args()
    os.makedirs(args.cache, exist_ok=True)

    countries = {}
    for line in fetch("countryInfo.txt", args.cache).decode("utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        cols = line.split("\t")
        countries[cols[0]] = cols[4]

    admin1 = {}
    for line in fetch("admin1CodesASCII.txt", args.cache).decode("utf-8").splitlines():
        cols = line.split("\t")
        if len(cols) >= 2:
            admin1[cols[0]] = cols[1]

    with zipfile.ZipFile(io.BytesIO(fetch("cities15000.zip", args.cache))) as z:
        text = z.read("cities15000.txt").decode("utf-8")

    rows = []
    for line in text.splitlines():
        c = line.split("\t")
        if len(c) < 18:
            continue
        name, ascii_name, alts = c[1], c[2], c[3]
        lat, lon = float(c[4]), float(c[5])
        cc, a1 = c[8], c[10]
        pop = int(c[14] or 0)
        elev = c[15] or c[16] or "0"
        try:
            elev = max(0, int(float(elev)))
        except ValueError:
            elev = 0
        tz = c[17]
        if not tz:
            continue

        seen = {name.lower()}
        keys = []
        if ascii_name and ascii_name.lower() not in seen:
            keys.append(ascii_name)
            seen.add(ascii_name.lower())
        arabic, latin = [], []
        for a in alts.split(","):
            a = a.strip()
            if not a or a.lower() in seen:
                continue
            if ARABIC.search(a):
                if len(arabic) < MAX_ARABIC:
                    arabic.append(a)
                    seen.add(a.lower())
            elif LATIN_OK.match(a) and len(a) <= 40:
                if len(latin) < MAX_LATIN:
                    latin.append(a)
                    seen.add(a.lower())
        keys += arabic + latin

        rows.append((pop, clean(name), clean(admin1.get(f"{cc}.{a1}", "")),
                     clean(countries.get(cc, cc)), lat, lon, elev, tz,
                     [clean(k) for k in keys if clean(k)]))

    rows.sort(key=lambda r: -r[0])
    zones = sorted({r[7] for r in rows})
    zone_idx = {z: i for i, z in enumerate(zones)}
    names = sorted({r[3] for r in rows})
    country_idx = {n: i for i, n in enumerate(names)}

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write(f"UFUQ-CITIES\t1\t{len(rows)}\t{datetime.date.today().isoformat()}\n")
        f.write("#tz\t" + "\t".join(zones) + "\n")
        f.write("#country\t" + "\t".join(names) + "\n")
        for pop, name, region, country, lat, lon, elev, tz, keys in rows:
            f.write(f"{name}\t{region}\t{country_idx[country]}\t{lat:.4f}\t{lon:.4f}\t{elev}\t"
                    f"{zone_idx[tz]}\t{'|'.join(keys)}\n")

    size = os.path.getsize(OUT)
    print(f"wrote {len(rows)} cities, {len(zones)} zones, {size / 1e6:.2f} MB -> {OUT}", file=sys.stderr)
    for probe in ("Surrey", "Ar Rumaythah", "As Samawah", "Karbala", "Qom", "Tromsø"):
        hit = next((r for r in rows if r[1] == probe), None)
        print(f"  {probe}: {'found' if hit else 'MISSING'}", file=sys.stderr)


if __name__ == "__main__":
    main()
