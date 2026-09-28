#!/usr/bin/env python3
"""Generate the photoreal Cherry hero sprite (drawable-nodpi/cherry_hero_art.png).

Pure-stdlib painter matching the NEW reference poster (deep-red glossy pair):

  * two glossy DEEP-RED spheres — small hot lit spot -> cherry red #E01B3D
    -> deep maroon, strong white dual speculars, transmitted-light glow at
    the lower edge (ref's glowing pass-through light)
  * CRISP smooth hero leaf (NO fuzz/dots): analytic per-pixel shape, smooth
    green gradient, central + side veins, glossy sheen streak
  * tapered olive-brown stems meeting at a junction knot with a tiny red-brown
    bud on top (ref)
  * BOLD gold comet swirl ribbons wrapping the pair (two behind, one bright
    streak crossing IN FRONT of the fruit) with white-gold comet heads
  * 4-point star flares on the comet heads, floating sparkles + gold dust

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
    """Smooth round airbrush stamp (no jitter — jitter caused the halftone look)."""
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


def ellipse_stamp(cx, cy, rx, ry, rot_deg, color, alpha, soft=2.0):
    if rx < 1 or ry < 1 or alpha <= 0:
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
            dx, dy = px - cx, py - cy
            ux = dx * ct + dy * st
            uy = -dx * st + dy * ct
            d = math.sqrt((ux / rx) ** 2 + (uy / ry) ** 2)
            if d >= 1.0:
                continue
            a = alpha * math.exp(-(d * d) * soft * 1.35)
            if a > 0.004:
                blend(px, py, color[0], color[1], color[2], a)


# ------------------------------------------------------------- cherry rig --
LCX, LCY, LR = 392.0, 648.0, 208.0     # left cherry (front)
RCX, RCY, RR = 648.0, 582.0, 184.0     # right cherry
JX, JY = 566.0, 158.0                  # stem junction knot

# Ref ramp: DEEP vibrant red — small hot spot, most of the sphere is #E01B3D
# down to dark maroon. (The old soft-pink ramp made the fruit read washed out.)
RAMP = [
    (0.00, (255, 92, 114)),
    (0.10, (248, 40, 74)),
    (0.30, (226, 22, 61)),
    (0.55, (176, 12, 46)),
    (0.80, (124, 7, 34)),
    (1.00, (76, 3, 22)),
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
    """Per-pixel volumetric sphere: deep red ramp, tiny hot spot, gentle rim."""
    for py in range(int(cy - r) - 2, int(cy + r) + 3):
        if py < 0 or py >= H:
            continue
        for px in range(int(cx - r) - 2, int(cx + r) + 3):
            if px < 0 or px >= W:
                continue
            dx, dy = px - cx, py - cy
            nd = math.sqrt(dx * dx + dy * dy)
            if nd > r:
                continue
            edge = nd / r
            cover = min(1.0, (r - nd) / 1.5 + (1.0 if r - nd > 1.5 else 0.0))
            if cover <= 0:
                continue

            lx, ly = cx - r * 0.36, cy - r * 0.42
            ldist = math.sqrt((px - lx) ** 2 + (py - ly) ** 2) / (r * 1.55)
            c = ramp_color(ldist)

            # Transmitted-light glow band near the lower-left edge (ref's
            # light shining through the fruit)
            ang = math.atan2(dy, dx)
            glow_ang = math.cos(ang - math.radians(125.0))
            if glow_ang > 0:
                band = math.exp(-((edge - 0.80) ** 2) / 0.022) * glow_ang
                c = tuple(min(255, c[k] + (255, 66, 92)[k] * band * 0.55) for k in range(3))

            # Slight rim shade so the silhouette stays crisp on dark bg
            rim = smoothstep(0.86, 1.0, edge)
            c = tuple(c[k] * (1.0 - 0.18 * rim) for k in range(3))

            # Soft pink fresnel at the lower-right rim
            light_ang = math.atan2(ly - cy, lx - cx)
            diff = abs((ang - light_ang + math.pi) % (2 * math.pi) - math.pi)
            fres = smoothstep(2.20, 2.95, diff) * smoothstep(0.70, 0.97, edge)
            c = tuple(min(255, c[k] + (255, 120, 140)[k] * fres * 0.22) for k in range(3))

            blend(px, py, int(c[0]), int(c[1]), int(c[2]), cover)


def quad_bezier(p0, p1, p2, t):
    mt = 1 - t
    x = mt * mt * p0[0] + 2 * mt * t * p1[0] + t * t * p2[0]
    y = mt * mt * p0[1] + 2 * mt * t * p1[1] + t * t * p2[1]
    return x, y


def paint_stems():
    """Tapered olive-brown stems to the junction knot, knot + tiny bud on top."""
    for (sx, sy, r, bend) in [(LCX, LCY, LR, -46), (RCX, RCY, RR, 52)]:
        p0 = (sx + (bend * 0.12), sy - r * 0.94)
        p1 = (sx + bend, sy - r * 1.55)
        p2 = (JX + (3 if bend > 0 else -3), JY + 6)
        steps = 100
        for i in range(steps):
            t = i / (steps - 1)
            x, y = quad_bezier(p0, p1, p2, t)
            wdt = 8.0 * (1.0 - 0.45 * t) + 1.6
            stamp(x, y, wdt, (100, 68, 36), 0.96, soft=1.3)
            stamp(x - wdt * 0.26, y - wdt * 0.22, wdt * 0.40, (168, 126, 74),
                  0.50, soft=1.5)
            stamp(x + wdt * 0.30, y + wdt * 0.18, wdt * 0.34, (62, 40, 24),
                  0.45, soft=1.5)
        # dark cleft dimple where the stem enters the fruit
        stamp(p0[0], p0[1], r * 0.085, (94, 8, 28), 0.55, soft=1.6)
        stamp(p0[0], p0[1], r * 0.045, (58, 3, 18), 0.65, soft=1.5)
    # junction knot
    stamp(JX, JY, 9.5, (118, 76, 42), 0.96, soft=1.5)
    stamp(JX - 2, JY - 2, 4.6, (188, 140, 86), 0.75, soft=1.5)
    # tiny red-brown bud perched on top of the knot (ref detail)
    ellipse_stamp(JX + 1, JY - 11, 6.5, 11.0, 8, (148, 52, 38), 0.95, soft=1.5)
    stamp(JX - 2, JY - 15, 2.6, (198, 98, 62), 0.85, soft=1.4)
    stamp(JX + 1, JY - 19, 2.2, (96, 28, 22), 0.85, soft=1.4)


def paint_leaf():
    """CRISP smooth hero leaf — analytic per-pixel, no fuzz (ref is glossy)."""
    cx, cy = 755.0, 205.0
    ln, wd = 168.0, 76.0
    rot = -32.0
    t = math.radians(rot)
    ct, st = math.cos(t), math.sin(t)

    # petiole: junction knot -> leaf base
    base = (cx - ln * ct, cy - ln * st)
    for i in range(40):
        tt = i / 39.0
        x, y = quad_bezier((JX + 4, JY + 8),
                           ((JX + base[0]) / 2 + 6, (JY + base[1]) / 2),
                           base, tt)
        stamp(x, y, 4.6 - 1.0 * tt, (104, 82, 44), 0.95, soft=1.4)

    R = int(ln + wd)
    for py in range(max(0, int(cy) - R), min(H, int(cy) + R + 1)):
        for px in range(max(0, int(cx) - R), min(W, int(cx) + R + 1)):
            dx, dy = px - cx, py - cy
            u = dx * ct + dy * st
            v = -dx * st + dy * ct
            au = u / ln
            if abs(au) > 1.0:
                continue
            half = wd * (1.0 - au * au) ** 0.68
            if half < 0.5:
                continue
            d = abs(v) / half
            if d > 1.0:
                continue
            aa = min(1.0, (1.0 - d) * max(2.0, half * 0.45))

            # smooth green gradient: darker at base, brighter toward tip
            g = 0.5 + 0.5 * au
            r = 42 + 78 * g
            gr = 120 + 98 * g
            b = 36 + 50 * g

            # darker margins (crisp edge definition)
            edge_dark = 1.0 - 0.40 * smoothstep(0.70, 1.0, d)
            r *= edge_dark; gr *= edge_dark; b *= edge_dark

            # glossy diagonal sheen streak above the centerline
            sheen = (math.exp(-(((au + 0.12) / 0.52) ** 2)) *
                     math.exp(-(((v + half * 0.34) / (half * 0.42)) ** 2)))
            r = min(255, r + 118 * sheen)
            gr = min(255, gr + 122 * sheen)
            b = min(255, b + 96 * sheen)

            blend(px, py, int(r), int(gr), int(b), aa * 0.98)

    # central vein — tapered, smooth
    for i in range(56):
        tt = i / 55.0
        u = (-0.92 + 1.86 * tt) * ln
        x, y = cx + u * ct, cy + u * st
        stamp(x, y, 2.8 - 1.5 * tt, (26, 66, 28), 0.72, soft=1.4)
    # side veins — smooth angled lines toward the tip
    for k in range(9):
        uu = (-0.66 + 0.17 * k)
        for side in (1, -1):
            u0, v0 = uu * ln, 0.0
            half = wd * (1.0 - uu * uu) ** 0.68
            u1, v1 = (uu + 0.13) * ln, side * half * 0.68
            steps = 12
            for i in range(steps):
                tt = i / (steps - 1)
                x = cx + (u0 + (u1 - u0) * tt) * ct - (v0 + (v1 - v0) * tt) * st
                y = cy + (u0 + (u1 - u0) * tt) * st + (v0 + (v1 - v0) * tt) * ct
                stamp(x, y, 1.15, (34, 80, 34), 0.48, soft=1.5)
    # bright specular dot on the sheen
    sxp, syp = cx + (-0.02 * ln) * ct - (-0.30 * wd) * st, cy + (-0.02 * ln) * st + (-0.30 * wd) * ct
    stamp(sxp, syp, 9.0, (232, 255, 226), 0.30, soft=2.0)


def paint_swirl(bcx, bcy, rx, ry, rot_deg, head_deg, trail_deg, width, alpha,
                head_glow=True):
    """Bold gold comet ribbon along an elliptical path, bright white-gold head."""
    t = math.radians(rot_deg)
    ct, st = math.cos(t), math.sin(t)

    def pt(a):
        x = math.cos(a) * rx
        y = math.sin(a) * ry
        return (bcx + x * ct - y * st, bcy + x * st + y * ct)

    head = math.radians(head_deg)
    n = max(40, int(trail_deg * 2.2))
    for i in range(n):
        f = i / (n - 1)
        a = head - math.radians(trail_deg) * f
        x, y = pt(a)
        rel = (1.0 - f) ** 1.45
        wob = 1.0 + 0.06 * math.sin(a * 5.0)
        col = (255, 248, 228) if rel > 0.8 else ((255, 222, 150) if rel > 0.45 else (250, 172, 92))
        al = alpha * rel
        if al > 0.012:
            stamp(x, y, (4.0 + 15.0 * rel) * width * wob, col, al, soft=1.9)
        if i % 7 == 3 and rel > 0.25:
            stamp(x + 6, y - 5, 1.6, (255, 232, 178), al * 0.7, soft=1.5)
    if head_glow:
        hx, hy = pt(head)
        stamp(hx, hy, 26 * width, (255, 226, 168), 0.42, soft=2.2)
        stamp(hx, hy, 12 * width, (255, 244, 214), 0.80, soft=1.8)
        stamp(hx, hy, 5 * width, (255, 255, 252), 0.95, soft=1.5)
    return pt(head)


def paint_flare(x, y, ln, alpha, rot=0.0):
    """4-point star flare."""
    t = math.radians(rot)
    ct, st = math.cos(t), math.sin(t)
    for sx, sy in ((ct, st), (-ct, -st)):
        for i in range(26):
            f = i / 25.0
            d = f * ln
            fall = (1.0 - f) ** 1.7
            stamp(x + sx * d, y + sy * d, 1.2 + 5.5 * fall, (255, 244, 220),
                  alpha * fall * 0.8, soft=1.7)
    for sx, sy in ((-st, ct), (st, -ct)):
        for i in range(14):
            f = i / 13.0
            d = f * ln * 0.42
            fall = (1.0 - f) ** 1.8
            stamp(x + sx * d, y + sy * d, 1.0 + 3.6 * fall, (255, 240, 214),
                  alpha * fall * 0.6, soft=1.7)
    stamp(x, y, 7.0, (255, 250, 240), alpha * 0.9, soft=1.6)
    stamp(x, y, 3.0, (255, 255, 255), alpha, soft=1.4)


def paint_speculars(cx, cy, r):
    """Glossy dual speculars + top rim light + bottom reflected light."""
    # main crisp streak
    ellipse_stamp(cx - r * 0.30, cy - r * 0.50, r * 0.36, r * 0.115, -36,
                  (255, 250, 250), 0.92, soft=1.5)
    ellipse_stamp(cx - r * 0.26, cy - r * 0.56, r * 0.16, r * 0.052, -36,
                  (255, 255, 255), 0.96, soft=1.3)
    stamp(cx - r * 0.48, cy - r * 0.44, r * 0.058, (255, 255, 255), 0.95, soft=1.4)
    # small secondary bounce on the upper-right
    ellipse_stamp(cx + r * 0.36, cy - r * 0.42, r * 0.13, r * 0.048, 40,
                  (255, 200, 210), 0.45, soft=1.8)
    # top rim light arc
    for i in range(24):
        a = math.radians(-150 + i * 5.0)
        stamp(cx + math.cos(a) * r * 0.94, cy + math.sin(a) * r * 0.94,
              r * 0.05, (255, 92, 118), 0.20, soft=1.8)
    # bottom reflected light arc
    for i in range(16):
        a = math.radians(55 + i * 4.5)
        stamp(cx + math.cos(a) * r * 0.88, cy + math.sin(a) * r * 0.88,
              r * 0.055, (255, 120, 142), 0.30, soft=1.8)


def paint_sparkles():
    """Small 4-point sparkles floating around the fruit + gold dust."""
    spots = [
        (355, 590, 7.0, 0.85), (442, 662, 5.0, 0.7), (540, 556, 4.5, 0.6),
        (622, 612, 6.0, 0.75), (706, 545, 5.0, 0.65), (588, 706, 4.0, 0.55),
        (470, 538, 3.5, 0.5), (505, 748, 5.5, 0.6),
    ]
    for (fx, fy, fr, fa) in spots:
        for k in range(-3, 4):
            tt = 1.0 - abs(k) / 4.0
            stamp(fx + k * fr * 0.6, fy, fr * 0.16 * tt + 0.8, (255, 250, 240), fa * tt, soft=1.4)
            stamp(fx, fy + k * fr * 0.6, fr * 0.16 * tt + 0.8, (255, 250, 240), fa * tt, soft=1.4)
        stamp(fx, fy, fr * 0.32, (255, 255, 255), fa, soft=1.6)
    for _ in range(44):
        ang = random.uniform(0, math.tau)
        rad = random.uniform(110, 390)
        x = 512 + math.cos(ang) * rad
        y = 630 + math.sin(ang) * rad * 0.8
        if 0 < x < W and 0 < y < H:
            gold = random.random() < 0.5
            col = (255, 224, 160) if gold else (255, 238, 228)
            stamp(x, y, random.uniform(1.1, 2.4), col,
                  random.uniform(0.2, 0.6), soft=1.6)


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
    print("back glow behind fruit...")
    stamp(515, 640, 400, (255, 70, 110), 0.15, soft=2.8)
    stamp(515, 640, 262, (255, 112, 150), 0.11, soft=2.6)
    print("back gold swirls (behind fruit)...")
    paint_swirl(515, 640, 445, 158, 15, -30, 150, 0.75, 0.32)
    paint_swirl(515, 648, 355, 126, -14, 200, 120, 0.80, 0.30)
    paint_swirl(518, 646, 252, 88, 6, 45, 90, 0.60, 0.20, head_glow=False)
    print("stems + knot + bud, leaf...")
    paint_stems()
    paint_leaf()
    print("cherries (per-pixel, deep red)...")
    paint_cherry(RCX, RCY, RR, salt=7)   # right first, left overlaps in front
    paint_cherry(LCX, LCY, LR, salt=8)
    # transmitted-light inner glow at the lower edge of each fruit
    ellipse_stamp(LCX - LR * 0.34, LCY + LR * 0.28, LR * 0.40, LR * 0.24, 18,
                  (255, 104, 126), 0.34, soft=2.6)
    ellipse_stamp(RCX - RR * 0.30, RCY + RR * 0.24, RR * 0.36, RR * 0.21, 18,
                  (255, 104, 126), 0.30, soft=2.6)
    paint_speculars(LCX, LCY, LR)
    paint_speculars(RCX, RCY, RR)
    print("front light streak crossing the fruit...")
    h = paint_swirl(515, 655, 335, 118, -20, 118, 92, 0.85, 0.50)
    paint_flare(h[0], h[1], 95, 0.95, 18)
    print("flares at comet heads + sparkles...")
    paint_flare(907, 663, 80, 0.90, -12)
    paint_flare(182, 687, 55, 0.75, 40)
    paint_flare(566, 150, 42, 0.70, 84)
    paint_flare(688, 470, 45, 0.55, -30)
    paint_sparkles()
    out = "app/src/main/res/drawable-nodpi/cherry_hero_art.png"
    encode_png(out)
    print("wrote", out)


if __name__ == "__main__":
    main()
