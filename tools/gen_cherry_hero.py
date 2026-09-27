#!/usr/bin/env python3
"""Generate the photoreal Cherry hero sprite (drawable-nodpi/cherry_hero_art.png).

Pure-stdlib painter: per-pixel volumetric cherry shading (offset light radial
gradient + subsurface scatter + rim shade + fresnel rim light), soft dual
speculars, contact shadow between the pair, tapered stems, a veined leaf, and
organic gold light swirls with comet trails, spark particles, bokeh and
floating petals. Output is RGBA with transparency, 1024x1024.

Re-run from repo root:  python3 tools/gen_cherry_hero.py
"""

import zlib
import struct
import math
import random

W = H = 1024
random.seed(20260927)

# ---------------------------------------------------------------- buffers --
buf = bytearray(W * H * 4)  # straight RGBA, starts fully transparent


def sample_jitter(x, y, salt=0):
    """Deterministic 4-tap jitter in [-0.5, 0.5) for smooth large gradients."""
    h1 = ((x * 374761393 + y * 668265263 + salt * 2246822519) & 0xFFFFFFFF)
    h2 = ((x * 2654435761 + y * 2246822519 + salt * 3266489917) & 0xFFFFFFFF)
    return ((h1 >> 16) / 65535.0 - 0.5, (h2 >> 16) / 65535.0 - 0.5)


def blend(px, py, r, g, b, a):
    """SrcOver blend of one pixel with clamping."""
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
    """Soft round glow stamp; alpha decays as exp(-(d/r)^soft)."""
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
        row = py * W
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
    """Soft elliptical stamp rotated by rot_deg (screen coords)."""
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
# Geometry: pair spans x 120..955 of 1024; hero center ~ (0.5, 0.62).
LCX, LCY, LR = 392.0, 648.0, 208.0     # left cherry
RCX, RCY, RR = 648.0, 582.0, 184.0     # right cherry
JX, JY = 566.0, 158.0                  # stem junction knot

# Poster color ramp (lit -> deep), light comes from upper-left.
RAMP = [
    (0.00, (255, 138, 158)),
    (0.16, (247, 66, 92)),
    (0.34, (224, 22, 61)),
    (0.55, (166, 8, 43)),
    (0.76, (104, 4, 30)),
    (1.00, (56, 2, 18)),
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


def paint_cherry(cx, cy, r, salt):
    """Per-pixel volumetric sphere: offset light gradient, subsurface, rim."""
    r_int = int(r)
    for py in range(int(cy - r) - 2, int(cy + r) + 3):
        if py < 0 or py >= H:
            continue
        row = py * W
        for px in range(int(cx - r) - 2, int(cx + r) + 3):
            if px < 0 or px >= W:
                continue
            jx, jy = sample_jitter(px, py, salt)
            dx, dy = px - cx + jx, py - cy + jy
            nd = math.sqrt(dx * dx + dy * dy)
            if nd > r:
                continue
            edge = nd / r
            # Soft silhouette edge (2px feather)
            cover = min(1.0, (r - nd) / 2.0 + (1.0 if r - nd > 2 else 0.0))
            if cover <= 0:
                continue

            lx, ly = cx - r * 0.38, cy - r * 0.44
            ldist = math.sqrt((px - lx) ** 2 + (py - ly) ** 2) / (r * 1.62)
            c = ramp_color(ldist)

            # Subsurface scatter: glow bleeding near the lit lower edge
            sub = math.exp(-((edge - 0.72) ** 2) / 0.028) * 0.42
            c = tuple(c[k] + (250 - c[k]) * sub * (0.9 if dy > 0 else 0.45) for k in range(3))

            # Rim shade toward silhouette
            rim = smoothstep(0.78, 1.0, edge)
            c = tuple(c[k] * (1.0 - 0.30 * rim) for k in range(3))

            # Fresnel rim light opposite the light (lower-right) — poster glow
            ang = math.atan2(dy, dx)
            light_ang = math.atan2(ly - cy, lx - cx)
            diff = abs((ang - light_ang + math.pi) % (2 * math.pi) - math.pi)
            fres = smoothstep(2.15, 2.95, diff) * smoothstep(0.62, 0.95, edge)
            c = tuple(c[k] + (255, 120, 140)[k] * fres * 0.38 for k in range(3))

            # Bottom bounce (warm reflected pink from the light pool below)
            if dy > r * 0.35:
                bounce = smoothstep(r * 0.35, r * 0.9, dy) * (1.0 - rim) * 0.22
                c = tuple(c[k] + (255, 90, 110)[k] * bounce for k in range(3))

            blend(px, py, int(c[0]), int(c[1]), int(c[2]), cover)


def smoothstep(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3.0 - 2.0 * t)


def paint_speculars(cx, cy, r):
    """Glossy dual speculars: streak + dot, plus a soft window reflection."""
    ellipse_stamp(cx - r * 0.30, cy - r * 0.52, r * 0.34, r * 0.115, -34,
                  (255, 250, 250), 0.90, soft=1.5, salt=11)
    ellipse_stamp(cx - r * 0.24, cy - r * 0.60, r * 0.16, r * 0.055, -34,
                  (255, 255, 255), 0.95, soft=1.3, salt=12)
    stamp(cx - r * 0.47, cy - r * 0.47, r * 0.062, (255, 255, 255), 0.95, salt=13)
    # Faint secondary bounce highlight lower-right
    ellipse_stamp(cx + r * 0.40, cy + r * 0.44, r * 0.22, r * 0.09, -40,
                  (255, 190, 205), 0.20, soft=2.4, salt=14)
    # Curved reflection line following the silhouette (glass band)
    for t in range(-60, 61, 4):
        a = math.radians(t)
        px = cx + math.cos(a) * r * 0.86
        py = cy - math.sin(a) * r * 0.50 + r * 0.34
        stamp(px, py, r * 0.030, (255, 220, 228), 0.16, soft=1.8, salt=15)


def paint_contact_shadow():
    """Dark crease where the two spheres meet + ground pool glow beneath."""
    mx, my = (LCX + RCX) / 2 + 6, (LCY + RCY) / 2 - 12
    for k in range(3):
        ellipse_stamp(mx + k * 5, my + k * 2, 46 - k * 12, 120 - k * 30, 78,
                      (52, 0, 14), 0.34 - k * 0.08, soft=1.6, salt=20 + k)
    ellipse_stamp(516, 918, 250, 52, 0, (120, 8, 30), 0.22, soft=2.4, salt=23)
    ellipse_stamp(516, 918, 150, 30, 0, (255, 120, 140), 0.10, soft=2.2, salt=24)


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
    # Junction knot + tips
    stamp(JX, JY, 8.5, (120, 84, 52), 0.95, salt=32)
    stamp(JX - 2, JY - 2, 4.2, (196, 150, 102), 0.8, salt=33)


def paint_leaf():
    """Big glossy leaf pointing upper-right from the junction."""
    cx, cy = 742.0, 236.0
    ln, wd = 168.0, 62.0
    rot = -26.0
    t = math.radians(rot)
    ct, st = math.cos(t), math.sin(t)

    def leaf_pt(u, v):
        return (cx + u * ct - v * st, cy + u * st + v * ct)

    # Body: scan the ellipse in leaf space, two-tone green along +u
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
            r = int(46 + 60 * g)
            gg = int(120 + 80 * g)
            b = int(34 + 40 * g)
            # darker toward the leaf edge
            edge = abs(v) / max(1.0, half)
            r = int(r * (1 - 0.35 * edge)); gg = int(gg * (1 - 0.30 * edge)); b = int(b * (1 - 0.30 * edge))
            a = min(1.0, (1.0 - edge) * 6.0)
            if a > 0.01:
                blend(int(px), int(py), r, gg, b, a)
    # Central vein + side veins
    steps = 40
    for i in range(steps):
        t2 = i / (steps - 1)
        u = (t2 - 0.5) * 2 * ln * 0.96
        px, py = leaf_pt(u, 0)
        stamp(px, py, 2.6 - 1.0 * t2, (26, 66, 28), 0.75, soft=1.3, salt=41)
        if i % 4 == 0 and 0.1 < t2 < 0.9:
            side = 1 if i % 8 == 0 else -1
            for k in range(1, 5):
                uu = u + k * 6
                vv = side * (10 + k * 7) * math.sin(math.pi * t2)
                px2, py2 = leaf_pt(uu, vv)
                stamp(px2, py2, 1.4, (30, 78, 32), 0.4, soft=1.4, salt=42)
    # Glossy sheen streak on the leaf
    for i in range(26):
        t2 = i / 25.0
        u = (t2 - 0.42) * ln * 1.3
        px, py = leaf_pt(u, -half_gloss(t2))
        stamp(px, py, 7 - 4 * t2, (210, 255, 200), 0.20, soft=1.8, salt=43)


def half_gloss(t):
    return 14 + 10 * math.sin(t * 3.0)


def paint_swirls():
    """Organic gold light swirls: two tilted elliptical comet rings + spray."""
    rings = [
        dict(cx=516, cy=646, rx=345, ry=118, rot=-16, speed=1.0, head=0.8, w=1.0),
        dict(cx=516, cy=640, rx=418, ry=142, rot=21, speed=-0.72, head=2.9, w=0.8),
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
            # wobble for organic feel
            wob = 1.0 + 0.05 * math.sin(a * 5.0 + ring["rot"])
            core = (255, 244, 214) if rel > 0.75 else ((255, 214, 130) if rel > 0.35 else (255, 158, 84))
            alpha = 0.30 * rel * ring["w"]
            if alpha > 0.01:
                stamp(x, y, (7.0 + 13.0 * rel) * wob, core, alpha, soft=1.8, salt=50 + i % 9)
        # Bright comet head + bloom
        hx, hy = pt(head)
        stamp(hx, hy, 26, (255, 236, 190), 0.5, salt=60)
        stamp(hx, hy, 10, (255, 250, 235), 0.95, salt=61)
        stamp(hx, hy, 4, (255, 255, 255), 1.0, salt=62)
        # Star cross sparkle on the head
        for k in range(-2, 3):
            stamp(hx + k * 9, hy, 2.2, (255, 255, 240), 0.5, salt=63)
            stamp(hx, hy + k * 9, 2.2, (255, 255, 240), 0.5, salt=64)


def paint_sparks():
    """Tiny gold sparks + pink bokeh + floating petals scattered around."""
    for _ in range(110):
        ang = random.uniform(0, math.tau)
        rad = random.uniform(120, 470)
        x = 512 + math.cos(ang) * rad * random.uniform(0.75, 1.15)
        y = 600 + math.sin(ang) * rad * random.uniform(0.6, 0.9)
        if not (0 < x < W and 0 < y < H):
            continue
        warm = random.random() < 0.7
        col = (255, 232, 170) if warm else (255, 178, 198)
        stamp(x, y, random.uniform(1.6, 4.6), col, random.uniform(0.25, 0.9),
              salt=random.randint(0, 9999))
        if random.random() < 0.22:
            stamp(x, y, random.uniform(6, 12), col, 0.12, salt=random.randint(0, 9999))
    # Bokeh discs
    for _ in range(12):
        x = random.uniform(60, W - 60)
        y = random.uniform(120, 760)
        if abs(x - 512) < 240 and abs(y - 620) < 240:
            continue
        r = random.uniform(16, 42)
        col = random.choice([(255, 170, 190), (255, 205, 215), (255, 190, 160)])
        stamp(x, y, r, col, random.uniform(0.05, 0.12), soft=1.4,
              salt=random.randint(0, 9999))
    # Petals: soft pink ovals drifting at the edges
    for _ in range(9):
        x = random.choice([random.uniform(30, 190), random.uniform(W - 190, W - 30)])
        y = random.uniform(160, 900)
        ellipse_stamp(x, y, random.uniform(16, 26), random.uniform(9, 15),
                      random.uniform(0, 180), (255, 158, 180),
                      random.uniform(0.30, 0.6), salt=random.randint(0, 9999))


# ------------------------------------------------------------------ paint --
def main():
    print("painting atmosphere glow...")
    stamp(512, 620, 430, (86, 6, 26), 0.5, soft=1.1, salt=0)          # halo bed
    stamp(500, 580, 300, (120, 10, 34), 0.32, soft=1.3, salt=1)
    paint_contact_shadow()
    print("painting swirls + sparks (behind fruit)...")
    paint_swirls()
    paint_sparks()
    print("painting stems + leaf...")
    paint_stems()
    paint_leaf()
    print("painting cherries (per-pixel)...")
    paint_cherry(RCX, RCY, RR, salt=7)   # right first, left overlaps in front
    paint_cherry(LCX, LCY, LR, salt=8)
    paint_speculars(LCX, LCY, LR)
    paint_speculars(RCX, RCY, RR)
    print("encoding PNG...")
    out = "app/src/main/res/drawable-nodpi/cherry_hero_art.png"
    encode_png(out)
    print("wrote", out)


def encode_png(path):
    raw = bytearray()
    for y in range(H):
        raw.append(0)  # filter none
        row = buf[y * W * 4:(y + 1) * W * 4]
        raw.extend(row)

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", ihdr)
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


if __name__ == "__main__":
    main()
