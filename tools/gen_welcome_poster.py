#!/usr/bin/env python3
"""Generate the full-bleed Welcome poster backdrop (drawable-nodpi/welcome_poster_bg.png).

Pure-stdlib painter built to match the NEW reference poster exactly:

  * near-black maroon vertical gradient, warm magenta halo behind the fruit seat
    (SOFT — no big red blobs; the old muddy blobs are gone)
  * blossom canopy cluster TOP-LEFT (layered pink flowers + buds, softly
    blurred deep in the corner) — matches the ref canopy
  * glass digit bubbles at the ref's exact numbers/positions:
    53 / 12 / 07 / 96 (top row), 27 / 35 / 19 (sides), 61 / 42 / 84 / 70
    (left/right edges) — pink glass digits with a crisp ring + gloss arc
  * scattered pink petals all over, denser near the top corners
  * radiating gold light streaks from the fruit seat (ref's comet streaks)
  * bright gold floor light rings under the CTA + a warm floor haze
  * faint lower-corner blossoms

The cherry + its wrapped gold swirl ribbons live in cherry_hero_art.png.

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


def stamp(cx, cy, radius, color, alpha, soft=2.2):
    if radius < 0.7 or alpha <= 0:
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
            d = math.sqrt((px - cx) ** 2 + (py - cy) ** 2) / radius
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
            a = alpha * (1.0 - e) ** 1.5
            if a > 0.01:
                blend(px, py, int(col[0]), int(col[1]), int(col[2]), a)


def blossom(cx, cy, r, phase, alpha, blur=False):
    """Five-petal pink blossom; blur=True for the soft deep-canopy ones."""
    c1, c2, core = (232, 108, 138), (168, 40, 72), (255, 150, 168)
    a_mul = 0.55 if blur else 1.0
    if blur:
        stamp(cx, cy, r * 1.5, (150, 38, 62), 0.30 * a_mul, soft=2.4)
    for k in range(5):
        ang = phase + k * (2.0 * math.pi / 5.0)
        px = cx + math.cos(ang) * r * 0.62
        py = cy + math.sin(ang) * r * 0.62
        if blur:
            stamp(px, py, r * 0.44, (196, 62, 92), 0.5 * a_mul, soft=2.2)
        else:
            petal(px, py, r * 0.46, math.degrees(ang), c1, c2, alpha)
    if not blur:
        stamp(cx, cy, r * 0.20, core, 0.75 * alpha, soft=1.6)
        # tiny gold stamen dots
        for k in range(5):
            ang = phase + k * (2.0 * math.pi / 5.0) + 0.63
            stamp(cx + math.cos(ang) * r * 0.16, cy + math.sin(ang) * r * 0.16,
                  r * 0.06, (255, 214, 140), 0.8 * alpha, soft=1.5)


def arc(cx, cy, radius, a0_deg, a1_deg, width, color, alpha):
    steps = max(24, int(radius * abs(math.radians(a1_deg - a0_deg)) * 1.3))
    for i in range(steps + 1):
        t = i / steps
        a = math.radians(a0_deg + (a1_deg - a0_deg) * t)
        stamp(cx + math.cos(a) * radius, cy + math.sin(a) * radius,
              width, color, alpha, soft=1.6)


# -------------------------------------------------- glass digit bubbles ----
CHAR_SEGS = {
    "0": ["AB", "BC", "CD", "DA"],
    "1": ["BE", "EF"],
    "2": ["AB", "BE", "EF", "DF", "CD"],
    "3": ["AB", "BE", "EF", "CF", "CD"],
    "4": ["AE", "BE", "EF", "CF"],
    "5": ["AB", "AE", "EF", "CF", "CD"],
    "6": ["AB", "AE", "EF", "DF", "CF", "CD"],
    "7": ["AB", "BE", "CF"],
    "8": ["AB", "BC", "CD", "DA", "EF"],
    "9": ["AB", "AE", "EF", "CF", "CD"],
}
SEG_PTS = {
    "A": (-0.62, -1.0), "B": (0.62, -1.0), "C": (0.62, 1.0),
    "D": (-0.62, 1.0), "E": (0.0, 0.0), "F": (0.0, 0.0),
}


def draw_char(ch, x, y, scale, alpha):
    w = 0.34 * scale
    h = 0.52 * scale
    for seg in CHAR_SEGS.get(ch, []):
        p0 = SEG_PTS[seg[0]]
        p1 = SEG_PTS[seg[1]]
        x0, y0 = x + p0[0] * w, y + p0[1] * h
        x1, y1 = x + p1[0] * w, y + p1[1] * h
        dist = math.hypot(x1 - x0, y1 - y0)
        n = max(2, int(dist / 2.4) + 1)
        for i in range(n):
            t = i / (n - 1)
            px, py = lerp(x0, x1, t), lerp(y0, y1, t)
            stamp(px, py, 0.40 * scale, (255, 176, 190), alpha * 0.9)
            stamp(px, py, 1.6 * scale, (255, 96, 128), alpha * 0.12)


def draw_glass_bubble(cx, cy, r, fill_a, ring_a, text=""):
    stamp(cx, cy, r * 1.12, (52, 12, 24), fill_a, soft=1.9)
    stamp(cx - r * 0.26, cy - r * 0.30, r * 0.42, (96, 26, 44), fill_a * 0.7, soft=2.2)
    arc(cx, cy, r, 0, 360, max(1.6, 0.065 * r), (238, 138, 158), ring_a * 0.85)
    arc(cx, cy, r * 0.90, 195, 320, max(1.3, 0.05 * r), (255, 210, 222), ring_a * 0.55)
    # gloss blob upper-left
    stamp(cx - r * 0.34, cy - r * 0.38, r * 0.16, (255, 226, 234), ring_a * 0.55, soft=1.8)
    if text:
        total_w = 0.95 * r * len(text)
        for idx, ch in enumerate(text):
            draw_char(ch, cx - total_w / 2 + 0.95 * r * (idx + 0.5), cy, r * 0.98, ring_a * 1.25)


# ------------------------------------------------------------------ paint --
def paint_gradient():
    print("painting gradient...")
    row_cache = {}
    for y in range(H):
        t = y / (H - 1)
        if t < 0.26:
            f = t / 0.26
            col = (int(16 + (44 - 16) * f), int(4 + (10 - 4) * f), int(9 + (20 - 9) * f))
        elif t < 0.60:
            f = (t - 0.26) / 0.34
            col = (int(44 + (36 - 44) * f), int(10 + (8 - 10) * f), int(20 + (17 - 20) * f))
        else:
            f = (t - 0.60) / 0.40
            col = (int(36 + (16 - 36) * f), int(8 + (3 - 8) * f), int(17 + (9 - 17) * f))
        row_cache[y] = col
    for y in range(H):
        r, g, b = row_cache[y]
        row = y * W
        for x in range(W):
            i = (row + x) * 4
            buf[i], buf[i + 1], buf[i + 2], buf[i + 3] = r, g, b, 255


def paint_glow():
    print("painting soft halo + floor light...")
    # SOFT warm halo behind the fruit seat (small, layered, no muddy blobs)
    stamp(540, 660, 470, (255, 74, 128), 0.10, soft=2.8)
    stamp(540, 660, 330, (255, 116, 162), 0.09, soft=2.6)
    stamp(540, 660, 210, (255, 158, 190), 0.07, soft=2.4)
    # faint warm haze for the logo band
    stamp(540, 1430, 430, (255, 84, 138), 0.045, soft=2.8)
    # floor: warm pool of light under the CTA + reflected magenta
    stamp(540, 2100, 620, (120, 26, 48), 0.22, soft=2.6)
    stamp(540, 2085, 430, (190, 52, 88), 0.16, soft=2.5)
    stamp(540, 2070, 270, (240, 92, 128), 0.10, soft=2.4)
    # cool magenta spill top-right (ref has a subtle one behind 96/19)
    stamp(920, 260, 300, (150, 40, 84), 0.10, soft=2.8)


def paint_canopy():
    print("painting top-left blossom canopy...")
    # dark hanging branch silhouettes from the top-left corner
    branches = [
        ([-40, 60], [140, 150], [300, 235], 1.00),
        ([-40, 180], [120, 260], [265, 330], 0.85),
        ([120, -30], [250, 90], [380, 150], 0.80),
    ]
    for bi, (p0, p1, p2, scale) in enumerate(branches):
        steps = max(50, int(230 * scale))
        for i in range(steps):
            t = i / (steps - 1)
            x, y = quad_bezier(p0, p1, p2, t)
            fade = smoothstep(0.0, 0.08, t) * smoothstep(1.0, 0.88, t)
            stamp(x, y, 3.4, (40, 10, 19), 0.85 * fade)
        for t in [0.2, 0.42, 0.62, 0.82, 1.0]:
            bx, by = quad_bezier(p0, p1, p2, t)
            phase = bi * 1.3 + t * 5.0
            blossom(bx, by, random.uniform(15, 24), phase, random.uniform(0.55, 0.8))
            blossom(bx + 20 * scale, by + 14 * scale, random.uniform(8, 13),
                    phase + 0.7, 0.55)
    # layered soft blossoms deep in the corner (blurred backdrop feel)
    soft = [(40, 80, 30), (120, 40, 26), (220, 90, 30), (60, 200, 28),
            (170, 190, 26), (280, 160, 22), (20, 300, 24), (110, 300, 20)]
    for (cx, cy, r) in soft:
        blossom(cx, cy, r, random.uniform(0, 6), 0.7, blur=True)
    # crisp hero blossoms on the canopy front (bright pink)
    hero = [(105, 105, 26), (215, 130, 22), (320, 190, 20), (150, 220, 24),
            (25, 170, 20), (255, 60, 18), (370, 105, 17), (60, 60, 18),
            (185, 15, 16), (355, 260, 15)]
    for (cx, cy, r) in hero:
        blossom(cx, cy, r, random.uniform(0, 6), random.uniform(0.8, 0.95))
    # small buds
    for (cx, cy, r) in [(260, 210, 9), (330, 120, 8), (100, 280, 9), (400, 170, 7)]:
        stamp(cx, cy, r, (198, 66, 96), 0.85, soft=1.8)
        stamp(cx, cy - r * 0.5, r * 0.45, (255, 160, 185), 0.6, soft=1.8)


def paint_petals():
    print("painting petals...")
    petals = [
        # deliberate petals around the top and sides (ref-like)
        (460, 130, 13, -35), (620, 90, 10, 25), (740, 180, 12, -10),
        (880, 130, 14, 40), (980, 420, 12, 20), (60, 520, 11, -30),
        (150, 760, 13, 15), (940, 880, 12, -25), (120, 1010, 10, 35),
        (830, 1080, 11, -15), (200, 1180, 12, 10), (900, 1260, 13, -40),
        (70, 1420, 12, 25), (1010, 1480, 11, -20), (160, 1680, 12, 15),
        (930, 1740, 12, -30), (90, 1880, 13, 20), (990, 1980, 11, -15),
    ]
    for (x, y, s, rot) in petals:
        c1 = (255, 178, 200)
        c2 = (244, 104, 142)
        petal(x, y, s, rot, c1, c2, random.uniform(0.55, 0.8))
    # small drifting petal specks
    for _ in range(16):
        x = random.uniform(40, W - 40)
        y = random.uniform(150, 1900)
        if 300 < x < 820 and 420 < y < 980:      # keep fruit seat clean
            continue
        petal(x, y, random.uniform(5, 9), random.uniform(-50, 50),
              (250, 150, 180), (238, 92, 132), random.uniform(0.3, 0.55))


def paint_bubbles():
    print("painting glass digit bubbles (ref numbers)...")
    specs = [
        # top row
        (150, 235, 62, 0.13, 0.50, "53"),
        (410, 225, 48, 0.10, 0.40, "12"),
        (665, 215, 46, 0.10, 0.40, "07"),
        (940, 260, 60, 0.11, 0.42, "96"),
        # sides
        (185, 480, 52, 0.11, 0.44, "27"),
        (895, 545, 52, 0.11, 0.44, "35"),
        (1010, 655, 42, 0.09, 0.36, "19"),
        # edges
        (115, 730, 52, 0.11, 0.44, "61"),
        (965, 815, 48, 0.10, 0.40, "42"),
        (110, 890, 56, 0.12, 0.46, "84"),
        (945, 1035, 46, 0.10, 0.40, "70"),
        # blanks (no text, glass only)
        (55, 320, 26, 0.06, 0.18, ""),
        (1030, 430, 24, 0.05, 0.15, ""),
        (70, 1060, 24, 0.05, 0.13, ""),
        (1015, 1180, 26, 0.05, 0.13, ""),
        (985, 130, 22, 0.05, 0.12, ""),
    ]
    for (cx, cy, r, fa, ra, text) in specs:
        draw_glass_bubble(cx, cy, r, fa, ra, text)


def paint_streaks():
    print("painting radiating gold streaks from the fruit seat...")
    streaks = [
        # (angle_deg, length, width, alpha) radiating from (540, 680)
        (208, 480, 3.2, 0.34), (196, 560, 2.6, 0.28), (222, 430, 2.4, 0.26),
        (232, 520, 3.0, 0.30), (150, 500, 2.6, 0.26), (138, 560, 3.0, 0.30),
        (128, 430, 2.2, 0.22), (318, 470, 2.6, 0.24), (332, 540, 3.0, 0.28),
        (345, 430, 2.2, 0.20), (35, 500, 2.8, 0.26), (28, 570, 2.4, 0.24),
        (292, 380, 2.0, 0.20), (68, 420, 2.2, 0.22),
    ]
    for (deg, ln, w, al) in streaks:
        a = math.radians(deg)
        steps = max(30, int(ln / 3))
        for i in range(steps + 1):
            f = i / steps
            d = f * ln
            x = 540 + math.cos(a) * d
            y = 680 + math.sin(a) * d
            fall = (1.0 - f) ** 1.3
            head = f < 0.18
            col = (255, 236, 190) if head else ((255, 204, 128) if f < 0.55 else (240, 158, 92))
            stamp(x, y, w * (1.0 - 0.4 * f), col, al * fall, soft=1.7)
            if i % 9 == 4:
                stamp(x + 3, y - 3, 1.3, (255, 226, 168), al * fall * 0.6, soft=1.5)


def paint_floor():
    print("painting gold floor rings under CTA...")
    rings = [
        dict(cx=540, cy=2085, rx=420, ry=62, rot=-4, w=5.2, a=0.38),
        dict(cx=540, cy=2110, rx=510, ry=82, rot=-3, w=3.6, a=0.26),
        dict(cx=540, cy=2070, rx=320, ry=44, rot=-5, w=3.2, a=0.32),
        dict(cx=540, cy=2135, rx=595, ry=103, rot=-2, w=2.6, a=0.16),
    ]
    for ri, ring in enumerate(rings):
        t = math.radians(ring["rot"])
        ct, st = math.cos(t), math.sin(t)
        n = 230
        for i in range(n):
            f = i / (n - 1)
            a = math.tau * f
            x0 = math.cos(a) * ring["rx"]
            y0 = math.sin(a) * ring["ry"]
            px = ring["cx"] + x0 * ct - y0 * st
            py = ring["cy"] + x0 * st + y0 * ct
            boost = 1.0 + 0.7 * max(0.0, math.sin(a))
            gold = (255, 216 - int(42 * (1 - boost)), 142)
            stamp(px, py, ring["w"] * boost, gold, ring["a"] * boost, soft=1.7)
        if ri == 0:
            hx = ring["cx"] + math.cos(0.7) * ring["rx"]
            hy = ring["cy"] + math.sin(0.7) * ring["ry"]
            stamp(hx, hy, 10, (255, 246, 218), 0.9, soft=1.6)
            stamp(hx, hy, 4, (255, 255, 255), 0.95, soft=1.4)
            hx2 = ring["cx"] + math.cos(2.4) * ring["rx"] * 0.7
            hy2 = ring["cy"] + math.sin(2.4) * ring["ry"] * 0.7
            stamp(hx2, hy2, 7, (255, 238, 190), 0.7, soft=1.6)


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
    paint_glow()
    paint_canopy()
    paint_petals()
    paint_bubbles()
    paint_streaks()
    paint_floor()
    encode_png("app/src/main/res/drawable-nodpi/welcome_poster_bg.png")
