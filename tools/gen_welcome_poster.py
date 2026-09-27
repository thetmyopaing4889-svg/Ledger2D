#!/usr/bin/env python3
"""Generate the full-bleed Welcome poster backdrop (drawable-nodpi/welcome_poster_bg.png).

Pure-stdlib painter built to match the reference poster exactly:

  * dark plum-black vertical gradient
  * magenta glow dome behind the fruit seat (center ~30% height)
  * THIN dark vines hanging from the top with SMALL dark-red blossoms
    and buds (no large glows, no big blobs)
  * faint glass digit bubbles: 84 / 61 / 3 / 42 / 70 + blanks
  * sparse tiny falling petals + faint lower-corner flowers
  * concentric gold floor swirl rings under the CTA

The cherry + its swirls live in cherry_hero_art.png.

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
    """SMALL dark-red 5-petal blossom (ref's hanging flowers)."""
    c1, c2, core = (198, 74, 96), (120, 26, 46), (228, 110, 130)
    for k in range(5):
        ang = phase + k * (2.0 * math.pi / 5.0)
        px = cx + math.cos(ang) * r * 0.62
        py = cy + math.sin(ang) * r * 0.62
        petal(px, py, r * 0.46, math.degrees(ang), c1, c2, alpha)
    stamp(cx, cy, r * 0.20, core, 0.75 * alpha, soft=1.6, salt=5)


def arc(cx, cy, radius, a0_deg, a1_deg, width, color, alpha, salt=0):
    steps = max(24, int(radius * abs(math.radians(a1_deg - a0_deg)) * 1.3))
    for i in range(steps + 1):
        t = i / steps
        a = math.radians(a0_deg + (a1_deg - a0_deg) * t)
        stamp(cx + math.cos(a) * radius, cy + math.sin(a) * radius,
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


def draw_char(ch, x, y, scale, alpha):
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
            stamp(px, py, 0.42 * scale, (255, 150, 168), alpha * 0.8)
            stamp(px, py, 1.5 * scale, (255, 90, 120), alpha * 0.13)


def draw_glass_bubble(cx, cy, r, fill_a, ring_a, text=""):
    stamp(cx, cy, r * 1.1, (40, 9, 20), fill_a, soft=1.8, salt=7)
    arc(cx, cy, r, 0, 360, max(1.5, 0.07 * r), (235, 130, 150), ring_a * 0.85, salt=8)
    arc(cx, cy, r * 0.92, 200, 320, max(1.2, 0.05 * r), (255, 205, 218), ring_a * 0.5, salt=9)
    if text:
        total_w = 0.95 * r * len(text)
        for idx, ch in enumerate(text):
            draw_char(ch, cx - total_w / 2 + 0.95 * r * (idx + 0.5), cy, r * 0.95, ring_a * 1.2)


# ------------------------------------------------------------------ paint --
def paint_gradient():
    print("painting gradient...")
    rows = {}
    for y in range(H):
        t = y / (H - 1)
        if t < 0.30:
            f = t / 0.30
            col = (int(24 + (52 - 24) * f), int(5 + (11 - 5) * f), int(13 + (24 - 13) * f))
        elif t < 0.62:
            f = (t - 0.30) / 0.32
            col = (int(52 + (42 - 52) * f), int(11 + (9 - 11) * f), int(24 + (19 - 24) * f))
        else:
            f = (t - 0.62) / 0.38
            col = (int(42 + (20 - 42) * f), int(9 + (4 - 9) * f), int(19 + (10 - 19) * f))
        rows[y] = col
    for y in range(H):
        r, g, b = rows[y]
        row = y * W
        for x in range(W):
            i = (row + x) * 4
            buf[i], buf[i + 1], buf[i + 2], buf[i + 3] = r, g, b, 255


def paint_glow_dome():
    print("painting magenta glow dome...")
    stamp(540, 700, 640, (255, 60, 130), 0.15, soft=2.6, salt=300)
    stamp(540, 700, 420, (255, 120, 170), 0.13, soft=2.4, salt=301)
    stamp(540, 700, 250, (255, 165, 195), 0.11, soft=2.2, salt=302)
    # gentle haze behind the logo band
    stamp(540, 1480, 460, (255, 80, 135), 0.05, soft=2.6, salt=303)


def paint_vines():
    print("painting thin vines + small blossoms...")
    branches = [
        ([-30, 40], [180, 130], [400, 210], 1.00),
        ([530, -20], [640, 100], [750, 170], 0.85),
        ([1110, 40], [950, 140], [820, 240], 0.90),
        ([1100, 230], [980, 310], [880, 400], 0.70),
        ([-20, 200], [70, 280], [180, 340], 0.75),
    ]
    for bi, (p0, p1, p2, scale) in enumerate(branches):
        steps = max(50, int(210 * scale))
        for i in range(steps):
            t = i / (steps - 1)
            x, y = quad_bezier(p0, p1, p2, t)
            fade = smoothstep(0.0, 0.08, t) * smoothstep(1.0, 0.9, t)
            alpha = 0.8 * fade
            if alpha > 0.01:
                stamp(x, y, 3.0, (42, 11, 20), alpha, salt=200 + i % 16)
        # SMALL blossom clusters along each branch (r 9-16 — ref-like)
        for t in [0.16, 0.36, 0.56, 0.76, 0.94, 1.0]:
            bx, by = quad_bezier(p0, p1, p2, t)
            phase = bi * 1.7 + t * 4.0
            blossom(bx, by, random.uniform(9, 15), phase, random.uniform(0.45, 0.7))
            blossom(bx + 14 * scale, by + 10 * scale, random.uniform(5, 8),
                    phase + 0.7, 0.4)
    # A few blossoms right at the top edge (tiny, silhouette)
    for (cx, cy, r) in [(60, 28, 13), (190, 20, 11), (330, 44, 9), (470, 26, 11),
                        (620, 22, 12), (770, 50, 9), (900, 32, 12), (1030, 68, 10)]:
        blossom(cx, cy, r, random.uniform(0, 6), 0.55)


def paint_petals():
    print("painting sparse petals...")
    for _ in range(22):
        x = random.uniform(30, W - 30)
        y = random.uniform(100, 1250)
        if 300 < x < 820 and 400 < y < 960:      # keep fruit seat clean
            continue
        s = random.uniform(6, 13)
        c1 = random.choice([(255, 175, 198), (250, 150, 180)])
        c2 = random.choice([(244, 100, 140), (238, 88, 130)])
        petal(x, y, s, random.uniform(-50, 50), c1, c2, random.uniform(0.25, 0.5))
    # very faint corner flowers (bottom)
    for (cx, cy, r) in [(80, 1950, 34), (1000, 2020, 38), (140, 2260, 30), (940, 2300, 32)]:
        blossom(cx, cy, r * 0.45, random.uniform(0, 6), 0.16)
        stamp(cx, cy, r * 1.6, (255, 120, 160), 0.05, soft=2.6, salt=330)


def paint_bubbles():
    print("painting faint glass digit bubbles...")
    specs = [
        (330, 330, 46, 0.10, 0.36, "84"),
        (110, 545, 50, 0.12, 0.42, "61"),
        (950, 300, 34, 0.08, 0.28, "3"),
        (962, 650, 46, 0.10, 0.38, "42"),
        (905, 855, 40, 0.10, 0.34, "70"),
        (940, 480, 28, 0.06, 0.20, ""),
        (78, 770, 28, 0.06, 0.18, ""),
        (1000, 520, 24, 0.05, 0.15, ""),
        (62, 430, 26, 0.05, 0.15, ""),
        (1005, 1050, 26, 0.05, 0.13, ""),
        (75, 1090, 24, 0.05, 0.12, ""),
    ]
    for (cx, cy, r, fa, ra, text) in specs:
        draw_glass_bubble(cx, cy, r, fa, ra, text)


def paint_floor():
    print("painting gold floor swirls...")
    rings = [
        dict(cx=540, cy=2050, rx=430, ry=64, rot=-4, w=5.0, a=0.32),
        dict(cx=540, cy=2075, rx=520, ry=84, rot=-3, w=3.5, a=0.22),
        dict(cx=540, cy=2035, rx=330, ry=44, rot=-5, w=3.0, a=0.28),
        dict(cx=540, cy=2100, rx=600, ry=105, rot=-2, w=2.5, a=0.15),
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
            boost = 1.0 + 0.7 * max(0.0, math.sin(a))
            gold = (255, 214 - int(40 * (1 - boost)), 140)
            stamp(px, py, ring["w"] * boost, gold, ring["a"] * boost, soft=1.7,
                  salt=500 + i % 10)
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
    paint_glow_dome()
    paint_vines()
    paint_petals()
    paint_bubbles()
    paint_floor()
    encode_png("app/src/main/res/drawable-nodpi/welcome_poster_bg.png")
