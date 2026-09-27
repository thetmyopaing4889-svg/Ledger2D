#!/usr/bin/env python3
"""Generate the photoreal Cherry hero sprite (drawable-nodpi/cherry_hero_art.png).

Pure-stdlib painter matching the reference poster:

  * two glossy spheres with a soft pink-to-red gradient (ref's lighter,
    rosier look) — light from upper-left, gentle rim shade
  * tiny sparkle particles floating inside/around the fruit
  * tapered brown-green stems meeting at a junction knot
  * BIG soft leaf with fine jitter texture (the ref's fuzzy look)
  * faint thin halo ring around the pair
  * two subtle gold comet swirls peeking from behind the fruit
  * small star glints

Output: RGBA with transparency, 1024x1024.
Re-run from repo root:  python3 tools/gen_cherry_hero.py
"""

import zlib
import struct
import math
import random

W = H = 1024
random.seed(20260927)

buf = bytearray(W * H * 4)  # straight RGBA, starts fully transparent


def sample_jitter(x, y, salt=0):
    h1 = ((x * 374761393 + y * 668265263 + salt * 2246822519) & 0xFFFFFFFF)
    h2 = ((x * 2654435761 + y * 2246822519 + salt * 3266489917) & 0xFFFFFFFF)
    return ((h1 >> 16) / 65535.0 - 0.5, (h2 >> 16) / 65535.0 - 0.5)


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


# ------------------------------------------------------------- cherry rig --
LCX, LCY, LR = 392.0, 648.0, 208.0     # left cherry
RCX, RCY, RR = 648.0, 582.0, 184.0     # right cherry
JX, JY = 566.0, 158.0                  # stem junction knot

# Ref ramp: rosier/lighter than the old one (ref's cherries read soft pink-red)
RAMP = [
    (0.00, (255, 170, 185)),
    (0.18, (250, 105, 130)),
    (0.38, (238, 52, 88)),
    (0.58, (196, 22, 62)),
    (0.78, (140, 10, 44)),
    (1.00, (86, 4, 28)),
]


def ramp_color(t):
    t = min(1.0, max(0.0, t))
    for i in range(len(RAMP) - 1):
        t0, c0 = RAMP[i]
        t1, c1 = RAMP[i + 1]
        if t <= t1:
            f = (t - t0) / max(1e-5, t1 - t0)
            return tuple(c0[k] + (c1[k] - c0[k]) * f for k in range(3))
    return RAMP[-1][1]


def smoothstep(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3.0 - 2.0 * t)


def paint_cherry(cx, cy, r, salt):
    """Per-pixel volumetric sphere: offset light gradient, gentle rim."""
    for py in range(int(cy - r) - 2, int(cy + r) + 3):
        if py < 0 or py >= H:
            continue
        for px in range(int(cx - r) - 2, int(cx + r) + 3):
            if px < 0 or px >= W:
                continue
            jx, jy = sample_jitter(px, py, salt)
            dx, dy = px - cx + jx, py - cy + jy
            nd = math.sqrt(dx * dx + dy * dy)
            if nd > r:
                continue
            edge = nd / r
            cover = min(1.0, (r - nd) / 2.0 + (1.0 if r - nd > 2 else 0.0))
            if cover <= 0:
                continue

            lx, ly = cx - r * 0.36, cy - r * 0.42
            ldist = math.sqrt((px - lx) ** 2 + (py - ly) ** 2) / (r * 1.66)
            c = ramp_color(ldist)

            # Soft glow bleeding near the lit lower edge
            sub = math.exp(-((edge - 0.74) ** 2) / 0.03) * 0.36
            c = tuple(c[k] + (255 - c[k]) * sub * (0.85 if dy > 0 else 0.4) for k in range(3))

            # Rim shade
            rim = smoothstep(0.80, 1.0, edge)
            c = tuple(c[k] * (1.0 - 0.26 * rim) for k in range(3))

            # Fresnel rim light (lower-right)
            ang = math.atan2(dy, dx)
            light_ang = math.atan2(ly - cy, lx - cx)
            diff = abs((ang - light_ang + math.pi) % (2 * math.pi) - math.pi)
            fres = smoothstep(2.15, 2.95, diff) * smoothstep(0.64, 0.96, edge)
            c = tuple(c[k] + (255, 140, 158)[k] * fres * 0.30 for k in range(3))

            blend(px, py, int(c[0]), int(c[1]), int(c[2]), cover)


def quad_bezier(p0, p1, p2, t):
    mt = 1 - t
    x = mt * mt * p0[0] + 2 * mt * t * p1[0] + t * t * p2[0]
    y = mt * mt * p0[1] + 2 * mt * t * p1[1] + t * t * p2[1]
    return x, y


def paint_stems():
    """Tapered brown-green stems from each cherry to the junction knot."""
    for (sx, sy, r, bend) in [(LCX, LCY, LR, -46), (RCX, RCY, RR, 52)]:
        p0 = (sx + (bend * 0.12), sy - r * 0.94)
        p1 = (sx + bend, sy - r * 1.55)
        p2 = (JX + (3 if bend > 0 else -3), JY + 6)
        steps = 90
        for i in range(steps):
            t = i / (steps - 1)
            x, y = quad_bezier(p0, p1, p2, t)
            wdt = 7.5 * (1.0 - 0.45 * t) + 1.5
            stamp(x, y, wdt, (92, 62, 40), 0.95, soft=1.2, salt=30)
            stamp(x - wdt * 0.28, y - wdt * 0.2, wdt * 0.42, (172, 128, 88),
                  0.55, soft=1.4, salt=31)
    stamp(JX, JY, 8.5, (120, 84, 52), 0.95, salt=32)
    stamp(JX - 2, JY - 2, 4.2, (196, 150, 102), 0.8, salt=33)


def paint_leaf():
    """BIG soft leaf with fine jitter texture (ref's fuzzy hero leaf)."""
    cx, cy = 742.0, 236.0
    ln, wd = 205.0, 80.0
    rot = -24.0
    t = math.radians(rot)
    ct, st = math.cos(t), math.sin(t)

    def leaf_pt(u, v):
        return (cx + u * ct - v * st, cy + u * st + v * ct)

    for iu in range(-160, 161, 2):
        u = iu / 160.0 * ln
        half = wd * math.sqrt(max(0.0, 1.0 - (u / ln) ** 2)) * (1.0 if u < ln * 0.2 else 0.96)
        for iv in range(-70, 71, 2):
            v = iv / 70.0 * half
            if v * v > (half * 1.02) ** 2:
                continue
            px, py = leaf_pt(u, v)
            jx, jy = sample_jitter(int(px), int(py), 40)
            g = 0.5 + 0.5 * (u / ln)
            r = int(52 + 66 * g)
            gg = int(128 + 84 * g)
            b = int(38 + 42 * g)
            edge = abs(v) / max(1.0, half)
            r = int(r * (1 - 0.32 * edge)); gg = int(gg * (1 - 0.28 * edge)); b = int(b * (1 - 0.28 * edge))
            a = min(1.0, (1.0 - edge) * 6.0)
            if a > 0.01:
                blend(int(px), int(py), r, gg, b, a)
    # central vein + side veins
    steps = 40
    for i in range(steps):
        t2 = i / (steps - 1)
        u = (t2 - 0.5) * 2 * ln * 0.96
        px, py = leaf_pt(u, 0)
        stamp(px, py, 2.6 - 1.0 * t2, (28, 70, 30), 0.7, soft=1.3, salt=41)
        if i % 4 == 0 and 0.1 < t2 < 0.9:
            side = 1 if i % 8 == 0 else -1
            for k in range(1, 5):
                uu = u + k * 6
                vv = side * (10 + k * 7) * math.sin(math.pi * t2)
                px2, py2 = leaf_pt(uu, vv)
                stamp(px2, py2, 1.3, (32, 82, 34), 0.35, soft=1.4, salt=42)
    # glossy sheen
    def half_gloss(t3):
        return 14 + 10 * math.sin(t3 * 3.0)
    for i in range(26):
        t2 = i / 25.0
        u = (t2 - 0.42) * ln * 1.3
        px, py = leaf_pt(u, -half_gloss(t2))
        stamp(px, py, 7 - 4 * t2, (215, 255, 205), 0.16, soft=1.8, salt=43)


def paint_swirls():
    """Subtle gold comet swirls peeking from behind the fruit (ref: faint)."""
    rings = [
        dict(cx=516, cy=646, rx=345, ry=118, rot=-16, speed=1.0, head=0.8, w=0.7),
        dict(cx=516, cy=640, rx=418, ry=142, rot=21, speed=-0.72, head=2.9, w=0.55),
    ]
    for ring in rings:
        t = math.radians(ring["rot"])
        ct, st = math.cos(t), math.sin(t)

        def pt(a):
            x = math.cos(a) * ring["rx"]
            y = math.sin(a) * ring["ry"]
            return (ring["cx"] + x * ct - y * st, ring["cx"] * 0 + ring["cy"] + x * st + y * ct)

        head = ring["head"]
        trail = 3.4
        n = 150
        for i in range(n):
            f = i / (n - 1)
            a = head - f * trail * (1 if ring["speed"] > 0 else -1)
            x, y = pt(a)
            rel = (1.0 - f) ** 1.6
            wob = 1.0 + 0.05 * math.sin(a * 5.0 + ring["rot"])
            core = (255, 244, 214) if rel > 0.75 else ((255, 214, 130) if rel > 0.35 else (255, 158, 84))
            alpha = 0.22 * rel * ring["w"]
            if alpha > 0.01:
                stamp(x, y, (7.0 + 13.0 * rel) * wob, core, alpha, soft=1.8, salt=50 + i % 9)
        hx, hy = pt(head)
        stamp(hx, hy, 20, (255, 236, 190), 0.4, salt=60)
        stamp(hx, hy, 8, (255, 250, 235), 0.8, salt=61)
        stamp(hx, hy, 3.5, (255, 255, 255), 0.95, salt=62)


def paint_halo_ring():
    """Faint thin halo ring around the fruit (ref's white circle)."""
    n = 260
    for i in range(n):
        f = i / (n - 1)
        a = math.tau * f
        x = 512 + math.cos(a) * 348
        y = 640 + math.sin(a) * 330
        stamp(x, y, 2.0, (255, 225, 232), 0.32, soft=1.5, salt=90 + i % 8)


def paint_sparkles():
    """Tiny 4-point sparkles floating inside/around the fruit (ref detail)."""
    spots = [
        (355, 590, 7.0, 0.85), (442, 660, 5.0, 0.7), (540, 560, 4.5, 0.6),
        (620, 610, 6.0, 0.75), (700, 545, 5.0, 0.65), (588, 700, 4.0, 0.55),
        (470, 540, 3.5, 0.5), (505, 745, 5.5, 0.6), (655, 480, 4.0, 0.5),
    ]
    for (fx, fy, fr, fa) in spots:
        for k in range(-3, 4):
            t = 1.0 - abs(k) / 4.0
            stamp(fx + k * fr * 0.6, fy, fr * 0.16 * t + 0.8, (255, 250, 240), fa * t, soft=1.4, salt=95)
            stamp(fx, fy + k * fr * 0.6, fr * 0.16 * t + 0.8, (255, 250, 240), fa * t, soft=1.4, salt=96)
        stamp(fx, fy, fr * 0.32, (255, 255, 255), fa, soft=1.6, salt=97)
    # dust sparkles
    for _ in range(40):
        ang = random.uniform(0, math.tau)
        rad = random.uniform(100, 380)
        x = 512 + math.cos(ang) * rad
        y = 620 + math.sin(ang) * rad * 0.8
        if 0 < x < W and 0 < y < H:
            stamp(x, y, random.uniform(1.2, 2.6), (255, 240, 230),
                  random.uniform(0.2, 0.6), salt=random.randint(0, 9999))


def paint_speculars(cx, cy, r):
    """Glossy dual speculars: streak + dot."""
    ellipse_stamp(cx - r * 0.30, cy - r * 0.52, r * 0.34, r * 0.115, -34,
                  (255, 250, 250), 0.88, soft=1.5, salt=11)
    ellipse_stamp(cx - r * 0.24, cy - r * 0.60, r * 0.16, r * 0.055, -34,
                  (255, 255, 255), 0.92, soft=1.3, salt=12)
    stamp(cx - r * 0.47, cy - r * 0.47, r * 0.062, (255, 255, 255), 0.92, salt=13)
    ellipse_stamp(cx + r * 0.40, cy + r * 0.44, r * 0.22, r * 0.09, -40,
                  (255, 195, 210), 0.18, soft=2.4, salt=14)


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


def main():
    print("painting halo ring (behind)...")
    paint_halo_ring()
    print("painting swirls (behind fruit)...")
    paint_swirls()
    print("painting stems + leaf...")
    paint_stems()
    paint_leaf()
    print("painting cherries (per-pixel)...")
    paint_cherry(RCX, RCY, RR, salt=7)   # right first, left overlaps in front
    paint_cherry(LCX, LCY, LR, salt=8)
    paint_speculars(LCX, LCY, LR)
    paint_speculars(RCX, RCY, RR)
    print("painting sparkles...")
    paint_sparkles()
    out = "app/src/main/res/drawable-nodpi/cherry_hero_art.png"
    encode_png(out)
    print("wrote", out)


if __name__ == "__main__":
    main()
