#!/usr/bin/env python3
"""Generate the full-bleed Welcome poster backdrop (drawable-nodpi/welcome_poster_bg.png).

Pure-stdlib painter that bakes every signature element of the reference
poster into one 1080x2340 RGBA image:

  * deep burgundy vertical gradient with a huge soft center glow pool
  * hanging vine branches with blossoms from the top corners + two big
    blurred foreground blossoms
  * halftone grain dots inside the glow (the ref's dotted texture)
  * TWO tilted organic gold comet swirls around the cherry seat
    (solid ribbons with bright comet heads -- NOT dotted circles)
  * a thin silver-white halo ring around the fruit seat
  * dark glass digit bubbles (61 / 3 / 42 + blanks) with rings
  * gold sparks, bokeh discs, floating petals
  * floor glow beneath the fruit

Blossom/leaf-free center: the photoreal cherry (cherry_hero_art.png) is
drawn on top at runtime, so the backdrop leaves the fruit seat clean.

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


def blossom(cx, cy, r, phase, alpha):
    for k in range(5):
        ang = phase + k * (2.0 * math.pi / 5.0)
        px = cx + math.cos(ang) * r * 0.62
        py = cy + math.sin(ang) * r * 0.62
        petal(px, py, r * 0.46, math.degrees(ang), (255, 200, 216), (248, 138, 168), alpha)
    stamp(cx, cy, r * 0.22, (255, 236, 240), 0.9 * alpha, soft=1.6, salt=5)
    stamp(cx, cy, r * 1.5, (255, 150, 180), 0.14 * alpha, soft=2.4, salt=6)


def arc(cx, cy, radius, a0_deg, a1_deg, width, color, alpha, salt=0):
    steps = max(24, int(radius * abs(math.radians(a1_deg - a0_deg)) * 1.3))
    for i in range(steps + 1):
        t = i / steps
        a = math.radians(a0_deg + (a1_deg - a0_deg) * t)
        stamp(cx + math.cos(a) * radius, cy + math.sin(a) * radius,
              width, color, alpha, soft=1.6, salt=salt + i % 8)


def halftone_dot(px, py, radius, salt):
    if (px + py) % 2 != 0:
        return
    if salt > 0:
        off = (1.0 / 7.0 if (px + py + 2 * salt) % 2 == 0 else 3.4 / 7.0) * radius + 0.30 * radius
    else:
        off = 0.30 * radius
    stamp(px, py, off, (255, 110, 135), 0.16, soft=2.2)


# ------------------------------------------------------------------ paint --
def paint_gradient():
    print("painting gradient...")
    rows = {}
    for y in range(H):
        t = y / (H - 1)
        if t < 0.5:
            f = t / 0.5
            col = (int(26 + (52 - 26) * f), int(5 + (10 - 5) * f), int(13 + (24 - 13) * f))
        else:
            f = (t - 0.5) / 0.5
            col = (int(52 + (30 - 52) * f), int(10 + (7 - 10) * f), int(24 + (16 - 24) * f))
        rows[y] = col
    for y in range(H):
        r, g, b = rows[y]
        row = y * W
        for x in range(W):
            i = (row + x) * 4
            buf[i], buf[i + 1], buf[i + 2], buf[i + 3] = r, g, b, 255


def paint_glow_pool():
    print("painting center glow pool...")
    stamp(540, 700, 700, (255, 150, 175), 0.10, soft=2.6, salt=300)
    stamp(540, 700, 470, (255, 190, 205), 0.10, soft=2.4, salt=301)
    stamp(540, 700, 300, (255, 224, 230), 0.10, soft=2.2, salt=302)


def paint_grain():
    print("painting halftone grain...")
    for gy in range(430, 1000, 4):
        span = int(210 * math.sqrt(max(0.0, 1.0 - ((gy - 700) / 300.0) ** 2)))
        for gx in range(540 - span, 540 + span + 1, 4):
            salt = (gx // 7) % 3
            halftone_dot(gx, gy, 10.0, salt)


def paint_vines(wind_t):
    print("painting vine branches...")
    branches = [
        ([-30, 120], [230, 260], [500, 430], 1.00),
        ([1010, 120], [880, 300], [690, 470], 0.85),
        ([-20, 560], [70, 680], [190, 750], 0.75),
        ([1090, 520], [990, 660], [870, 730], 0.70),
        ([1060, 60], [990, 150], [920, 260], 0.90),
    ]
    for bi, (p0, p1, p2, scale) in enumerate(branches):
        steps = max(60, int(240 * scale))
        for i in range(steps):
            t = i / (steps - 1)
            x, y = quad_bezier(p0, p1, p2, t)
            fade = smoothstep(0.0, 0.06, t) * smoothstep(1.0, 0.94, t)
            alpha = 0.8 * fade
            if alpha > 0.01:
                stamp(x, y, 6.5, (58, 16, 24), alpha, salt=200 + i % 16)
        for t, s in [(0.16, 12), (0.38, 22), (0.64, 17), (0.86, 26)]:
            bx, by = quad_bezier(p0, p1, p2, t)
            phase = wind_t * (0.25 + 0.05 * bi) + bi * 1.3 + t * 3.0
            blossom(bx, by, s * scale, phase, random.uniform(0.55, 0.8))
        blossom(p2[0], p2[1], (30 if scale > 0.9 else 22) * scale,
                wind_t * 0.3 + bi * 2.1, 0.75)


def paint_big_blossoms():
    print("painting big foreground blossoms...")
    blossom(95, 190, 56, 0.4, 0.85)
    blossom(700, 225, 36, 1.2, 0.8)
    blossom(115, 585, 34, 2.1, 0.75)
    blossom(1000, 570, 30, 2.8, 0.7)
    blossom(925, 125, 30, 0.9, 0.75)


def paint_swirls(with_head):
    print("painting gold comet swirls...")
    n = 170
    for i in range(n):
        f = i / (n - 1)
        a = 0.0 - f * 3.9
        wob = 1.0 + 0.045 * math.sin(3.0 * a + 0.8)
        scale = 1.0 - 0.07 * f
        x = 540 + math.cos(a) * 318 * scale * wob
        y = 712 + math.sin(a) * 262 * scale * wob
        rel = (1.0 - f) ** 1.8
        if rel > 0.72:
            core = (255, 248, 225)
        elif rel > 0.35:
            core = (255, 216, 140)
        else:
            core = (255, 164, 96)
        alpha = 0.30 * rel
        if alpha > 0.01:
            stamp(x, y, (6.5 + 15.0 * rel) * wob, core, alpha, soft=1.7, salt=80 + i % 9)
    if with_head:
        hx, hy = 540 + math.cos(0.0) * 318, 712 + math.sin(0.0) * 262
        stamp(hx, hy, 30, (255, 240, 200), 0.5, salt=90)
        stamp(hx, hy, 12, (255, 250, 235), 0.95, salt=91)
        stamp(hx, hy, 5, (255, 255, 255), 1.0, salt=92)
        for k in range(-2, 3):
            stamp(hx + k * 18, hy, 2.4, (255, 255, 240), 0.55, salt=93)
            stamp(hx, hy + k * 18, 2.4, (255, 255, 240), 0.55, salt=94)


def paint_halo_ring():
    print("painting halo ring...")
    arc(540, 700, 335, 0, 360, 3.2, (255, 205, 215), 0.50, salt=400)
    arc(540, 700, 335, 0, 360, 1.6, (255, 255, 255), 0.25, salt=410)


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


def paint_bubbles():
    print("painting glass digit bubbles...")
    specs = [
        (120, 90, 42, 0.20, 0.42, "61", False),
        (1010, 105, 32, 0.10, 0.25, "3", False),
        (760, 250, 27, 0.08, 0.18, "", False),
        (90, 430, 26, 0.12, 0.30, "", False),
        (950, 600, 24, 0.10, 0.22, "", False),
        (55, 790, 38, 0.30, 0.55, "42", True),
        (1030, 850, 27, 0.10, 0.20, "", False),
        (85, 1080, 24, 0.08, 0.16, "", False),
        (995, 1130, 22, 0.06, 0.12, "", False),
        (150, 1420, 26, 0.10, 0.20, "", False),
        (960, 1450, 25, 0.08, 0.15, "", False),
        (70, 1680, 22, 0.06, 0.12, "", False),
        (1010, 1700, 28, 0.08, 0.15, "", False),
        (135, 1980, 24, 0.08, 0.15, "", False),
        (940, 2000, 26, 0.08, 0.14, "", False),
    ]
    for (cx, cy, r, fa, ra, text, ht) in specs:
        draw_glass_bubble(cx, cy, r, fa, ra, text, ht)


def paint_sparks():
    print("painting sparks, bokeh, petals...")
    for _ in range(105):
        ang = random.uniform(0, math.tau)
        rad = random.uniform(140, 500)
        x = 540 + math.cos(ang) * rad * random.uniform(0.72, 1.12)
        y = 700 + math.sin(ang) * rad * random.uniform(0.58, 0.92)
        if not (0 < x < W and 0 < y < 1150):
            continue
        warm = random.random() < 0.7
        col = (255, 232, 170) if warm else (255, 178, 198)
        stamp(x, y, random.uniform(1.6, 4.6), col, random.uniform(0.25, 0.9),
              salt=random.randint(0, 9999))
        if random.random() < 0.22:
            stamp(x, y, random.uniform(6, 12), col, 0.12, salt=random.randint(0, 9999))
    for _ in range(13):
        x = random.uniform(60, W - 60)
        y = random.uniform(120, 1120)
        if abs(x - 540) < 250 and abs(y - 700) < 250:
            continue
        r = random.uniform(16, 44)
        col = random.choice([(255, 170, 190), (255, 205, 215), (255, 190, 160)])
        stamp(x, y, r, col, random.uniform(0.05, 0.12), soft=1.4,
              salt=random.randint(0, 9999))
    for _ in range(12):
        x = random.choice([random.uniform(30, 210), random.uniform(W - 210, W - 30)])
        y = random.uniform(200, 1100)
        s = random.uniform(10, 22)
        c1 = random.choice([(255, 190, 210), (255, 210, 222)])
        c2 = random.choice([(252, 130, 165), (250, 110, 150)])
        petal(x, y, s, random.uniform(-40, 40), c1, c2, random.uniform(0.35, 0.7))


def paint_ground():
    print("painting floor glow...")
    ellipse_stamp(540, 1235, 330, 52, 0, (120, 8, 32), 0.20, soft=2.4, salt=320)
    ellipse_stamp(540, 1235, 200, 32, 0, (255, 130, 155), 0.10, soft=2.2, salt=321)
    stamp(540, 1500, 620, (70, 14, 26), 0.20, soft=2.6, salt=322)


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
    paint_grain()
    paint_vines(wind_t=0.0)
    paint_big_blossoms()
    paint_swirls(with_head=True)
    paint_halo_ring()
    paint_bubbles()
    paint_sparks()
    paint_ground()
    encode_png("app/src/main/res/drawable-nodpi/welcome_poster_bg.png")
