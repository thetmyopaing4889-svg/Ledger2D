#!/usr/bin/env python3
"""Generate the full-bleed Welcome poster backdrop (drawable-nodpi/welcome_poster_bg.png).

Pure-stdlib painter matching the LATEST reference poster:

  * near-black maroon vertical gradient with a magenta core glow
  * photoreal-ish blossom canopy hanging from the top edge (clusters of
    5-petal blossoms, blurred depth layers, buds) + scattered petals
  * big blurred bokeh discs in the lower half (ref's out-of-focus flowers)
  * glass digit bubbles: 53 12 07 96 / 27 35 19 / 61 42 / 84 70
  * gold sparks + light streaks radiating around the fruit seat
  * floor: concentric gold swirl arcs under the CTA area
  * (cherry + its bright comet swirls live in cherry_hero_art.png)

Re-run from repo root:  python3 tools/gen_welcome_poster.py
"""

import zlib
import struct
import math
import random

W, H = 1080, 2340
random.seed(20260927)

buf = bytearray(W * H * 4)  # straight RGBA


def blend(px, py, r, g, b, a):
    if px < 0 or py < 0 or px >= W or py >= H or a <= 0:
        return
    i = (py * W + px) * 4
    da = buf[i + 3] / 255.0
    sa = min(a, 1.0)
    oa = sa + da * (1.0 - sa)
    if oa <= 0:
        return
    buf[i] = int(min(255, (r * sa + buf[i] * da * (1.0 - sa)) / oa))
    buf[i + 1] = int(min(255, (g * sa + buf[i + 1] * da * (1.0 - sa)) / oa))
    buf[i + 2] = int(min(255, (b * sa + buf[i + 2] * da * (1.0 - sa)) / oa))
    buf[i + 3] = int(oa * 255)


def sample_jitter(x, y, salt=0):
    h1 = ((x * 374761393 + y * 668265263 + salt * 2246822519) & 0xFFFFFFFF)
    h2 = ((x * 2654435761 + y * 2246822519 + salt * 3266489917) & 0xFFFFFFFF)
    return ((h1 >> 16) / 65535.0 - 0.5, (h2 >> 16) / 65535.0 - 0.5)


def stamp(cx, cy, radius, color, alpha, soft=2.2, salt=0):
    if radius < 0.7:
        return
    r = int(radius) + 2
    cx -= 0.5
    cy -= 0.5
    for py in range(int(cy) - r, int(cy) + r + 1):
        if py < 0 or py >= H:
            continue
        dy2 = (py - cy) ** 2
        span = int(math.sqrt(max(0.0, r * r - dy2))) + 1
        for px in range(int(cx) - span, int(cx) + span + 1):
            if px < 0 or px >= W:
                continue
            jx, jy = sample_jitter(px, py, salt)
            d = math.sqrt((px - cx + jx) ** 2 + (py - cy + jy) ** 2) / radius
            if d >= 1.0:
                continue
            a = alpha * math.exp(-(d * d) * soft * 1.35)
            if a > 0.004:
                blend(px, py, color[0], color[1], color[2], a)


def ellipse_stamp(cx, cy, rx, ry, rot_deg, color, alpha, soft=2.0, salt=1):
    if rx < 1 or ry < 1:
        return
    t = math.radians(rot_deg)
    ct, st = math.cos(t), math.sin(t)
    r = int(max(rx, ry)) + 2
    for py in range(int(cy - r), int(cy + r + 1)):
        if py < 0 or py >= H:
            continue
        for px in range(int(cx - r), int(cx + r + 1)):
            if px < 0 or px >= W:
                continue
            jx, jy = sample_jitter(px, py, salt)
            dx, dy = px - cx + jx, py - cy + jy
            ux = dx * ct + dy * st
            uy = -dx * st + dy * ct
            d = math.sqrt((ux / rx) ** 2 + (uy / ry) ** 2)
            if d >= 1.0:
                continue
            a = alpha * math.exp(-(d * d) * soft * 1.35)
            if a > 0.004:
                blend(px, py, color[0], color[1], color[2], a)


def quad_bezier(p0, p1, p2, t):
    mt = 1 - t
    x = mt * mt * p0[0] + 2 * mt * t * p1[0] + t * t * p2[0]
    y = mt * mt * p0[1] + 2 * mt * t * p1[1] + t * t * p2[1]
    return x, y


def lerp(a, b, t):
    return a + (b - a) * t


def smoothstep(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3.0 - 2.0 * t)


def petal(cx, cy, r, rot_deg, c1, c2, alpha):
    t = math.radians(rot_deg)
    ct, st = math.cos(t), math.sin(t)
    ry = r * 0.62
    ir, jr = int(r) + 2, int(ry) + 2
    for py in range(int(cy) - jr, int(cy) + jr + 1):
        if py < 0 or py >= H:
            continue
        for px in range(int(cx) - ir, int(cx) + ir + 1):
            if px < 0 or px >= W:
                continue
            dx, dy = px - cx, py - cy
            ux = dx * ct + dy * st
            uy = -dx * st + dy * ct
            e = math.sqrt((ux / r) ** 2 + (uy / ry) ** 2)
            if e >= 1.0:
                continue
            tt = (ux / r + 1.0) / 2.0
            col = tuple(lerp(c1[k], c2[k], tt) for k in range(3))
            a = alpha * (1.0 - e) ** 1.6
            if a > 0.01:
                blend(px, py, int(col[0]), int(col[1]), int(col[2]), a)


def blossom(cx, cy, r, phase, alpha, dark=False):
    """5-petal blossom; dark=True gives the silhouetted canopy look."""
    if dark:
        c1, c2, core = (214, 106, 132), (150, 48, 76), (230, 150, 168)
    else:
        c1, c2, core = (255, 200, 216), (248, 138, 168), (255, 236, 240)
    for k in range(5):
        ang = phase + k * (2.0 * math.pi / 5.0)
        px = cx + math.cos(ang) * r * 0.62
        py = cy + math.sin(ang) * r * 0.62
        petal(px, py, r * 0.46, math.degrees(ang), c1, c2, alpha)
    stamp(cx, cy, r * 0.22, core, 0.9 * alpha, soft=1.6, salt=5)
    stamp(cx, cy, r * 1.5, (255, 150, 180), 0.14 * alpha, soft=2.4, salt=6)


def arc(cx, cy, radius, a0_deg, a1_deg, width, color, alpha, salt=0, wobble=0.0):
    steps = max(24, int(radius * abs(math.radians(a1_deg - a0_deg)) * 1.3))
    for i in range(steps + 1):
        t = i / steps
        a = math.radians(a0_deg + (a1_deg - a0_deg) * t)
        rr = radius * (1.0 + wobble * math.sin(a * 3.0 + salt))
        stamp(cx + math.cos(a) * rr, cy + math.sin(a) * rr,
              width, color, alpha, soft=1.6, salt=salt + i % 8)


CHAR_SEGS = {
    "0": [("AB"), ("BC"), ("CD"), ("DA")],
    "1": [("BE"), ("EF")],
    "2": [("AB"), ("BE"), ("EF"), ("DF"), ("CD")],
    "3": [("AB"), ("BE"), ("EF"), ("CF"), ("CD")],
    "4": [("AE"), ("BE"), ("EF"), ("CF")],
    "5": [("AB"), ("AE"), ("EF"), ("CF"), ("CD")],
    "6": [("AB"), ("AE"), ("EF"), ("DF"), ("CF"), ("CD")],
    "7": [("AB"), ("BE"), ("CF")],
    "8": [("AB"), ("BC"), ("CD"), ("DA"), ("EF")],
    "9": [("AB"), ("AE"), ("EF"), ("CF"), ("CD")],
}
SEG_PTS = {
    "A": (-0.62, -1.0), "B": (0.62, -1.0), "C": (0.62, 1.0),
    "D": (-0.62, 1.0), "E": (0.0, 0.0), "F": (0.0, 0.0),
}


def draw_char(ch, x, y, scale, alpha, halftone=False):
    w = 0.34 * scale
    h = 0.52 * scale
    for seg in CHAR_SEGS.get(ch, []):
        p0 = SEG_PTS[seg[0]]
        p1 = SEG_PTS[seg[1]]
        x0, y0 = x + p0[0] * w, y + p0[1] * h
        x1, y1 = x + p1[0] * w, y + p1[1] * h
        dist = math.hypot(x1 - x0, y1 - y0)
        n = max(2, int(dist / 2.6) + 1)
        for i in range(n):
            t = i / (n - 1)
            px, py = lerp(x0, x1, t), lerp(y0, y1, t)
            if halftone:
                stamp(px, py, 0.30 * scale, (255, 110, 135), alpha)
            else:
                stamp(px, py, 0.42 * scale, (255, 140, 160), alpha * 0.8)
                stamp(px, py, 1.5 * scale, (255, 80, 110), alpha * 0.15)


def draw_glass_bubble(cx, cy, r, fill_a, ring_a, text="", halftone=False):
    stamp(cx, cy, r * 1.1, (40, 9, 20), fill_a, soft=1.8, salt=7)
    arc(cx, cy, r, 0, 360, max(1.5, 0.07 * r), (238, 120, 142), ring_a * 0.85, salt=8)
    arc(cx, cy, r * 0.92, 200, 320, max(1.2, 0.05 * r), (255, 200, 215), ring_a * 0.5, salt=9)
    if text:
        total_w = 0.95 * r * len(text)
        for idx, ch in enumerate(text):
            draw_char(ch, cx - total_w / 2 + 0.95 * r * (idx + 0.5), cy, r * 0.95, ring_a * 1.3, halftone)


# ------------------------------------------------------------------ paint --
def paint_gradient():
    print("painting gradient...")
    rows = {}
    for y in range(H):
        t = y / (H - 1)
        if t < 0.18:
            f = t / 0.18
            col = (int(22 + (46 - 22) * f), int(4 + (9 - 4) * f), int(11 + (20 - 11) * f))
        elif t < 0.55:
            f = (t - 0.18) / 0.37
            col = (int(46 + (58 - 46) * f), int(9 + (12 - 9) * f), int(20 + (26 - 20) * f))
        else:
            f = (t - 0.55) / 0.45
            col = (int(58 + (24 - 58) * f), int(12 + (6 - 12) * f), int(26 + (14 - 26) * f))
        rows[y] = col
    for y in range(H):
        r, g, b = rows[y]
        row = y * W
        for x in range(W):
            i = (row + x) * 4
            buf[i], buf[i + 1], buf[i + 2], buf[i + 3] = r, g, b, 255


def paint_glow_pool():
    print("painting magenta core glow...")
    stamp(540, 660, 660, (255, 70, 130), 0.14, soft=2.6, salt=300)
    stamp(540, 660, 430, (255, 140, 180), 0.12, soft=2.4, salt=301)
    stamp(540, 660, 270, (255, 200, 220), 0.10, soft=2.2, salt=302)
    # Warm haze behind the logo band
    stamp(540, 1500, 480, (255, 90, 140), 0.06, soft=2.6, salt=303)


def paint_canopy():
    print("painting blossom canopy (top)...")
    # Hanging branch arms
    branches = [
        ([-30, 60], [200, 170], [430, 250], 1.00),
        ([520, -20], [640, 120], [760, 190], 0.85),
        ([1110, 50], [950, 160], [830, 260], 0.90),
        ([1100, 260], [990, 330], [900, 420], 0.70),
        ([-20, 220], [60, 300], [170, 360], 0.75),
    ]
    for bi, (p0, p1, p2, scale) in enumerate(branches):
        steps = max(50, int(220 * scale))
        for i in range(steps):
            t = i / (steps - 1)
            x, y = quad_bezier(p0, p1, p2, t)
            fade = smoothstep(0.0, 0.08, t) * smoothstep(1.0, 0.9, t)
            alpha = 0.85 * fade
            if alpha > 0.01:
                stamp(x, y, 5.5, (44, 12, 22), alpha, salt=200 + i % 16)
        # Dense blossom clusters along each branch
        for t in [0.12, 0.3, 0.48, 0.66, 0.84, 1.0]:
            bx, by = quad_bezier(p0, p1, p2, t)
            phase = bi * 1.7 + t * 4.0
            blossom(bx, by, (26 + 10 * ((bi + t) % 2)) * scale, phase, random.uniform(0.6, 0.95), dark=True)
            # small bud nearby
            blossom(bx + 18 * scale, by + 14 * scale, 9 * scale, phase + 0.7, 0.55, dark=True)
    # Big photoreal-feel blossoms at the very top edge (blurred depth)
    for (cx, cy, r, ph) in [(60, 40, 44, 0.3), (200, 25, 38, 1.1), (340, 60, 30, 2.0),
                            (620, 30, 40, 0.8), (900, 45, 42, 1.6), (1030, 90, 34, 2.4),
                            (760, 70, 26, 0.4), (470, 35, 28, 1.9)]:
        blossom(cx, cy, r, ph, 0.85, dark=True)
        stamp(cx, cy, r * 2.2, (255, 120, 160), 0.10, soft=2.6, salt=310)


def paint_scatter():
    print("painting scattered petals + bokeh...")
    # Petals scattered mid-air (upper 2/3)
    for _ in range(30):
        x = random.uniform(30, W - 30)
        y = random.uniform(80, 1250)
        if 300 < x < 820 and 380 < y < 950:      # keep fruit seat clean
            continue
        s = random.uniform(9, 24)
        c1 = random.choice([(255, 190, 210), (255, 205, 220), (250, 160, 190)])
        c2 = random.choice([(252, 120, 158), (248, 100, 145)])
        petal(x, y, s, random.uniform(-50, 50), c1, c2, random.uniform(0.35, 0.75))
    # Big blurred bokeh blossoms in the lower half (ref's out-of-focus flowers)
    for (cx, cy, r) in [(90, 1560, 60), (980, 1620, 66), (60, 1900, 48),
                        (1010, 1980, 52), (150, 2150, 56), (930, 2200, 60),
                        (500, 2280, 44), (260, 1720, 34), (830, 1800, 36)]:
        stamp(cx, cy, r * 1.8, (255, 130, 170), 0.10, soft=2.2, salt=320)
        blossom(cx, cy, r, random.uniform(0, 6), 0.30, dark=True)
        stamp(cx, cy, r * 2.6, (255, 120, 165), 0.08, soft=2.8, salt=321)
    # Tiny petals drifting near the bottom too
    for _ in range(14):
        x = random.uniform(20, W - 20)
        y = random.uniform(1500, 2300)
        s = random.uniform(8, 18)
        petal(x, y, s, random.uniform(-60, 60),
              (255, 195, 215), (250, 110, 150), random.uniform(0.25, 0.55))


def paint_bubbles():
    print("painting glass digit bubbles...")
    specs = [
        (150, 195, 62, 0.16, 0.55, "53", False),
        (395, 200, 44, 0.10, 0.38, "12", False),
        (665, 185, 44, 0.10, 0.42, "07", False),
        (925, 205, 60, 0.12, 0.50, "96", False),
        (185, 380, 48, 0.12, 0.45, "27", False),
        (855, 405, 44, 0.10, 0.42, "35", False),
        (1000, 510, 40, 0.08, 0.35, "19", False),
        (95, 540, 52, 0.14, 0.50, "61", False),
        (950, 625, 48, 0.12, 0.48, "42", False),
        (85, 700, 54, 0.16, 0.55, "84", False),
        (935, 815, 42, 0.10, 0.40, "70", False),
    ]
    for (cx, cy, r, fa, ra, text, ht) in specs:
        draw_glass_bubble(cx, cy, r, fa, ra, text, ht)


def paint_light_streaks():
    print("painting radiating light streaks...")
    # Gold/pink streaks radiating outward from the fruit seat (ref's light rays)
    for i in range(26):
        ang = random.uniform(0, math.tau)
        x0 = 540 + math.cos(ang) * 90
        y0 = 660 + math.sin(ang) * 70
        length = random.uniform(120, 320)
        x1 = 540 + math.cos(ang) * (90 + length)
        y1 = 660 + math.sin(ang) * (70 + length * 0.8)
        warm = random.random() < 0.55
        col = (255, 225, 170) if warm else (255, 170, 195)
        steps = int(length / 5)
        for k in range(steps):
            t = k / max(1, steps - 1)
            px, py = lerp(x0, x1, t), lerp(y0, y1, t)
            stamp(px, py, 2.2 - 1.4 * t, col, 0.30 * (1 - t * 0.6), soft=1.6,
                  salt=400 + i % 12)


def paint_floor():
    print("painting gold floor swirls...")
    # Concentric tilted elliptical arcs under the CTA (ref's circular light rings)
    rings = [
        dict(cx=540, cy=2050, rx=430, ry=64, rot=-4, w=5.0, a=0.34),
        dict(cx=540, cy=2075, rx=520, ry=84, rot=-3, w=3.5, a=0.24),
        dict(cx=540, cy=2035, rx=330, ry=44, rot=-5, w=3.0, a=0.30),
        dict(cx=540, cy=2100, rx=600, ry=105, rot=-2, w=2.5, a=0.16),
    ]
    for ri, ring in enumerate(rings):
        t = math.radians(ring["rot"])
        ct, st = math.cos(t), math.sin(t)
        n = 210
        for i in range(n):
            f = i / (n - 1)
            a = math.tau * f
            x0 = math.cos(a) * ring["rx"]
            y0 = math.sin(a) * ring["ry"]
            px = ring["cx"] + x0 * ct - y0 * st
            py = ring["cy"] + x0 * st + y0 * ct
            # Brighter on the front (lower) half — light catching the floor
            boost = 1.0 + 0.7 * max(0.0, math.sin(a))
            gold = (255, 214 - int(40 * (1 - boost)) , 140)
            stamp(px, py, ring["w"] * boost, gold, ring["a"] * boost, soft=1.7,
                  salt=500 + i % 10)
        # Comet head on the outermost visible ring
        if ri == 0:
            hx = ring["cx"] + math.cos(0.6) * ring["rx"]
            hy = ring["cy"] + math.sin(0.6) * ring["ry"]
            stamp(hx, hy, 9, (255, 244, 214), 0.85, salt=510)
            stamp(hx, hy, 4, (255, 255, 255), 0.95, salt=511)


def encode_png(path):
    print("encoding PNG...")
    raw = bytearray()
    stride = W * 4
    for y in range(H):
        raw.append(0)
        raw += buf[y * stride:(y + 1) * stride]

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        c += struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        return c

    ihdr = struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)
    print(f"wrote {path} ({W}x{H})")


if __name__ == "__main__":
    paint_gradient()
    paint_glow_pool()
    paint_canopy()
    paint_scatter()
    paint_bubbles()
    paint_light_streaks()
    paint_floor()
    encode_png("app/src/main/res/drawable-nodpi/welcome_poster_bg.png")
