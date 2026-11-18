"""Builds src/main/resources/data/pincodes.csv from the India Post "All India Pincode Directory".

Source: data.gov.in (Department of Posts), mirrored at
https://raw.githubusercontent.com/dropdevrahul/pincodes-india/main/pincode.csv

Each pincode has many post offices; some have bad coordinates (0,0, swapped, or hundreds of km off).
We take the median of the offices (preferring head/sub offices), drop any office more than
OUTLIER_KM from that median, and average what remains.

Usage: python3 scripts/build_pincode_dataset.py <raw_directory.csv> <output.csv>
"""
import csv
import math
import statistics
import sys

INDIA_LAT = (6.0, 37.5)
INDIA_LON = (68.0, 97.5)
OUTLIER_KM = 30.0


def km(a, b):
    r = 6371.0
    dlat, dlon = math.radians(b[0] - a[0]), math.radians(b[1] - a[1])
    h = math.sin(dlat / 2) ** 2 + math.cos(math.radians(a[0])) * math.cos(math.radians(b[0])) * math.sin(dlon / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


def main(src, dst):
    offices = {}
    meta = {}
    with open(src, encoding="utf-8", errors="replace") as f:
        for row in csv.DictReader(f):
            pin = row["Pincode"].strip()
            # Geographic pincodes start with 1-8; 9xxxxx is the Army Postal Service (and the source has a junk 999999).
            if len(pin) != 6 or not pin.isdigit() or pin[0] not in "12345678":
                continue
            meta.setdefault(pin, (row["District"].strip().title(), row["StateName"].strip().title()))
            try:
                lat, lon = float(row["Latitude"]), float(row["Longitude"])
            except ValueError:
                continue
            if INDIA_LAT[0] <= lat <= INDIA_LAT[1] and INDIA_LON[0] <= lon <= INDIA_LON[1]:
                offices.setdefault(pin, []).append((lat, lon, row["OfficeType"].strip()))

    written = 0
    with open(dst, "w", newline="", encoding="utf-8") as out:
        w = csv.writer(out)
        w.writerow(["pincode", "latitude", "longitude", "district", "state"])
        for pin in sorted(offices):
            pts = offices[pin]
            main_offices = [p for p in pts if p[2] in ("HO", "PO")] or pts
            median = (statistics.median(p[0] for p in main_offices), statistics.median(p[1] for p in main_offices))
            kept = [p for p in pts if km(median, p) <= OUTLIER_KM] or main_offices
            lat = round(sum(p[0] for p in kept) / len(kept), 6)
            lon = round(sum(p[1] for p in kept) / len(kept), 6)
            district, state = meta[pin]
            w.writerow([pin, lat, lon, district, state])
            written += 1
    print(f"wrote {written} pincodes to {dst} ({len(meta) - written} without usable coordinates)")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
