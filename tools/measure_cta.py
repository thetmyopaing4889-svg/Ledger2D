#!/usr/bin/env python3
"""
measure_cta.py — locate a baked-in pink CTA pill inside a poster PNG.

Pure python (zlib + struct only, no Pillow): decodes the PNG, scans the
lower half for a wide, contiguous band of saturated pink pill pixels plus
the gold ring / arrow chip, and prints the band's bounding box as
fractions of the image:

    fx0 fx1  fy0 fy1

which map 1:1 to the screen height under ContentScale.Crop when the
poster fits the screen height (portrait phone vs 941x1672 poster), i.e.
the numbers can be pasted straight into WelcomeAnimation.kt as
CTA_FY0 / CTA_FY1 / CTA_FX0 / CTA_FX1.

Usage:
    python3 tools/measure_cta.py <poster.png> [poster2.png ...]

Prints `NO_BAKED_CTA` for a poster without a baked button (e.g. the
English-tiles reference), `BAKED_CTA fx0 fx1 fy0 fy1` otherwise.
"""

import struct
import sys
import zlib

# saturated pill-pink classifier (deep pink -> crimson body).
# Bounds cover both pale pill-pink AND the poster's deep crimson rings;
# discrimination between a solid pill and scattered accents is done by
# the per-row solid-density criterion below, not by these bounds.
PINK_R, PINK_G0, PINK_G1, PINK_B0, PINK_B1, PINK_DIFF = 185, 0, 120, 25, 170, 85
# gold ring / arrow chip classifier (warm gold, includes pale gold ring)
def is_gold(r, g, b):
    return r >= 205 and 140 <= g <= 205 and b <= 150 and (r - b) >= 70


def load_png(path):
    """Return (width, height, rgb bytes) decoding any common 8-bit PNG."""
    d = open(path, "rb").read()
    if d[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit(f"{path}: not a PNG")
    pos, idat, plte = 8, bytearray(), None
    w = h = bitd = ctype = 0
    while pos < len(d):
        (ln,) = struct.unpack(">I", d[pos:pos + 4])
        typ = d[pos + 4:pos + 8]
        chunk = d[pos + 8:pos + 8 + ln]
        if typ == b"IHDR":
            w, h, bitd, ctype = struct.unpack(">IIBB", chunk[:10])
        elif typ == b"IDAT":
            idat += chunk
        elif typ == b"PLTE":
            plte = chunk
        pos += 12 + ln
    if bitd != 8 or ctype not in (2, 6, 3):
        raise SystemExit(f"{path}: unsupported PNG (depth {bitd}, color {ctype})")
    raw = zlib.decompress(bytes(idat))
    ch = 3 if ctype == 2 else 4 if ctype == 6 else 1
    stride = w * ch
    out = bytearray(h * stride)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if f == 1:
            for i in range(ch, stride):
                line[i] = (line[i] + line[i - ch]) & 0xFF
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif f == 3:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif f == 4:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                b = prev[i]
                c = prev[i - ch] if i >= ch else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line
    if ctype == 3 and plte:
        rgb = bytearray(h * w * 3)
        for i, idx in enumerate(out):
            j = idx * 3
            rgb[i * 3] = plte[j]
            rgb[i * 3 + 1] = plte[j + 1]
            rgb[i * 3 + 2] = plte[j + 2]
        return w, h, rgb
    if ctype == 2:
        return w, h, out
    # RGBA -> drop alpha (posters are opaque)
    rgb = bytearray(h * w * 3)
    for i in range(h * w):
        rgb[i * 3] = out[i * 4]
        rgb[i * 3 + 1] = out[i * 4 + 1]
        rgb[i * 3 + 2] = out[i * 4 + 2]
    return w, h, rgb


def analyze(path):
    w, h, rgb = load_png(path)
    step = 2
    rows = {}
    for y in range(int(h * 0.5), h, step):  # CTA only lives in the lower half
        base = y * w * 3
        cnt = 0
        first = last = None
        for x in range(0, w, step):
            i = base + x * 3
            r, g, b = rgb[i], rgb[i + 1], rgb[i + 2]
            pink = (r >= PINK_R and PINK_G0 <= g <= PINK_G1 and
                    PINK_B0 <= b <= PINK_B1 and (r - g) >= PINK_DIFF)
            if pink or is_gold(r, g, b):
                cnt += 1
                if first is None:
                    first = x
                last = x
        if cnt:
            rows[y] = (cnt, first, last)

    # contiguous runs (gap tolerance 8 sampled rows), band must be wide + tall
    runs, cur = [], []
    for y in sorted(rows):
        if cur and y - cur[-1] > 8:
            runs.append(cur)
            cur = []
        cur.append(y)
    if cur:
        runs.append(cur)

    # A real pill is a SOLID band: rows must be densely pill-colored.
    # Scattered petals/accents only reach ~0.3-1.4% of sampled columns,
    # while a solid pill reaches 30%+ per row across its span.
    cols = w // step
    solid = [y for y, (cnt, _, _) in rows.items() if cnt >= 0.10 * cols]
    if solid:
        runs, cur = [], []
        for y in sorted(solid):
            if cur and y - cur[-1] > 8:
                runs.append(cur)
                cur = []
            cur.append(y)
        if cur:
            runs.append(cur)
    else:
        runs = []

    best = None
    for run in runs:
        span_w = max(rows[y][2] for y in run) - min(rows[y][1] for y in run)
        if span_w < 0.45 * w or len(run) < 20:  # >=45% width, >=40px tall
            continue
        strength = sum(rows[y][0] for y in run)
        if best is None or strength > best[0]:
            best = (strength, run)

    if best is None:
        return "NO_BAKED_CTA"
    run = best[1]
    y0, y1 = run[0], run[-1] + step
    fx0 = min(rows[y][1] for y in run) / w
    fx1 = max(rows[y][2] for y in run) / w
    fy0, fy1 = y0 / h, y1 / h
    return (f"BAKED_CTA fx0={fx0:.4f} fx1={fx1:.4f} fy0={fy0:.4f} fy1={fy1:.4f}\n"
            f"  center fy={(fy0 + fy1) / 2:.4f}  pill height fraction={fy1 - fy0:.4f}")


if __name__ == "__main__":
    for path in sys.argv[1:]:
        print(f"{path}: {analyze(path)}")
