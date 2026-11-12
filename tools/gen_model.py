#!/usr/bin/env python3
"""Generates the Gorewidow GeckoLib model, textures and the shared Java anatomy constants.

Everything that the in-game procedural rig needs to know about the skeleton (hip pivots,
segment lengths, rest foot positions) is written into GorewidowAnatomy.java from the same
numbers that build the .geo.json, so the IK can never drift away from the mesh.

Coordinates are Blockbench/GeckoLib file coordinates in model pixels (16 px = 1 block):
+Y up, the creature faces -Z (north), +X is the creature's left side.

Run:  python3 tools/gen_model.py
"""
import json
import math
import os
import random

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/gorewidow")
JAVA_OUT = os.path.join(ROOT, "src/main/java/com/vladosiwe/gorewidow/entity/GorewidowAnatomy.java")

TEX_W, TEX_H = 512, 512
DENSITY = 2  # texels per model pixel

rng = random.Random(1337)

# ---------------------------------------------------------------------------
# Skeleton definition
# ---------------------------------------------------------------------------

bones = []  # list of dicts in geo format (parents before children)
_bone_index = {}


# Body parts (not legs) are authored at a compact size and scaled up around the body centre so
# the cephalothorax / abdomen read with proper mass against the long legs.
BODY_SCALE = 1.3
BODY_CENTRE = (0.0, 20.0, 0.0)
_scaling = False


def _sp_force(p):
    return tuple(c + (v - c) * BODY_SCALE for v, c in zip(p, BODY_CENTRE))


def _sp(p):
    if not _scaling:
        return tuple(p)
    return tuple(c + (v - c) * BODY_SCALE for v, c in zip(p, BODY_CENTRE))


def bone(name, parent, pivot, rotation=(0, 0, 0)):
    pivot = _sp(pivot)
    b = {"name": name, "pivot": [round(v, 4) for v in pivot]}
    if parent:
        b["parent"] = parent
    if any(abs(r) > 1e-9 for r in rotation):
        b["rotation"] = [round(r, 4) for r in rotation]
    b["cubes"] = []
    bones.append(b)
    _bone_index[name] = b
    return b


def cube(bone_name, origin, size, material, rotation=None, pivot=None, inflate=0.0, faces=None):
    """Adds a cube. Origin is the min corner (file coords), size in px."""
    if _scaling:
        origin = _sp(origin)
        size = tuple(v * BODY_SCALE for v in size)
        if pivot is not None:
            pivot = _sp(pivot)
        inflate *= BODY_SCALE
    c = {"origin": [round(v, 4) for v in origin], "size": [round(v, 4) for v in size], "_mat": material}
    if rotation is not None:
        c["rotation"] = [round(r, 4) for r in rotation]
        c["pivot"] = [round(v, 4) for v in (pivot if pivot is not None else origin)]
    if inflate:
        c["inflate"] = inflate
    if faces:
        c["_faces"] = faces
    _bone_index[bone_name]["cubes"].append(c)
    return c


# --- body -----------------------------------------------------------------
BODY_Y = 20.0          # body centre height above the surface (px)
HIP_Y = 19.5

bone("root", None, (0, 0, 0))
_scaling = True
bone("tilt", "root", (0, BODY_Y, 0))              # procedural pitch / roll from leg heights
bone("body", "tilt", (0, BODY_Y, 0))              # keyframed bob / breathing

# cephalothorax
cube("body", (-5, 16, -7), (10, 8, 13), "carapace")
cube("body", (-4, 24, -6), (8, 1, 11), "plate")
cube("body", (-2.5, 25, -4.5), (5, 1, 7), "plate")
cube("body", (-3.5, 15, -6), (7, 1, 11), "belly")
# sternum ridges / hip sockets
for side in (1, -1):
    cube("body", (5 if side > 0 else -6, 17, -6), (1, 5, 11), "carapace")

# neck (procedural look) and head (keyframed)
bone("neck", "body", (0, 20, -7))
bone("head", "neck", (0, 20, -7))
cube("head", (-4, 16, -14), (8, 7, 7), "carapace")
cube("head", (-4.5, 22, -13.5), (9, 2, 6), "plate")
cube("head", (-3, 23.5, -12), (6, 1, 4), "plate")
cube("head", (-3, 15, -13), (6, 1, 5), "belly")
cube("head", (-2.5, 15.5, -14.6), (5, 2, 1), "mouth")
# eyes: a main pair, a forward pair, and two lateral pairs
cube("head", (-3.2, 19.5, -14.4), (2, 2, 1), "eye")
cube("head", (1.2, 19.5, -14.4), (2, 2, 1), "eye")
cube("head", (-1.5, 21.5, -14.3), (1, 1, 1), "eye")
cube("head", (0.5, 21.5, -14.3), (1, 1, 1), "eye")
cube("head", (-4.4, 20.5, -12.5), (1, 1, 1), "eye")
cube("head", (3.4, 20.5, -12.5), (1, 1, 1), "eye")
cube("head", (-4.4, 21.5, -10.5), (1, 1, 1), "eye")
cube("head", (3.4, 21.5, -10.5), (1, 1, 1), "eye")
# horn-like brow spurs
cube("head", (-4.5, 23, -13), (1, 1, 3), "spike", rotation=(25, 0, 10), pivot=(-4, 23.5, -12))
cube("head", (3.5, 23, -13), (1, 1, 3), "spike", rotation=(25, 0, -10), pivot=(4, 23.5, -12))

# chelicerae / fangs
for side, name in ((1, "fang_l"), (-1, "fang_r")):
    px = side * 1.6
    bone(name, "head", (px, 17, -13))
    cube(name, (px - 1.25, 13.5, -14.25), (2.5, 4, 2.5), "carapace")
    bone(name + "_tip", name, (px, 14, -13))
    cube(name + "_tip", (px - 0.75, 10.5, -13.75), (1.5, 3.5, 1.5), "fang", rotation=(-20, 0, side * 12), pivot=(px, 14, -13))

# pedipalps
for side, name in ((1, "palp_l"), (-1, "palp_r")):
    px = side * 3.5
    bone(name, "head", (px, 17, -12), rotation=(30, side * -10, 0))
    cube(name, (px - 0.75, 16.25, -17.5), (1.5, 1.5, 5.5), "leg")
    bone(name + "_tip", name, (px, 17, -17))
    cube(name + "_tip", (px - 0.65, 13.5, -17.65), (1.3, 3.5, 1.3), "leg", rotation=(-15, 0, 0), pivot=(px, 17, -17))

# abdomen (keyframed: breathing / blood swell)
bone("abdomen", "body", (0, 21, 6), rotation=(-12, 0, 0))
cube("abdomen", (-2.5, 18.5, 4), (5, 4, 3), "belly")                    # pedicel
cube("abdomen", (-7, 15, 7), (14, 13, 16), "abdomen")                   # main sac
cube("abdomen", (-6, 16, 23), (12, 11, 4), "abdomen")                   # rear swell
cube("abdomen", (-4.5, 17, 27), (9, 8, 3), "abdomen")                   # taper
cube("abdomen", (-6, 14, 9), (12, 1, 13), "belly")                      # ventral membrane
cube("abdomen", (-1, 28, 8), (2, 1, 15), "plate")                       # dorsal keel
cube("abdomen", (-7.5, 19, 9), (1, 7, 11), "vein")                      # glowing blood veins (sides)
cube("abdomen", (6.5, 19, 9), (1, 7, 11), "vein")
for i, z in enumerate((9, 13, 17, 21)):
    h = 3 if i in (1, 2) else 2
    cube("abdomen", (3, 27.5, z), (1.5, h, 1.5), "spike", rotation=(-35, 0, -15), pivot=(3.75, 28, z + 0.75))
    cube("abdomen", (-4.5, 27.5, z), (1.5, h, 1.5), "spike", rotation=(-35, 0, 15), pivot=(-3.75, 28, z + 0.75))
bone("spinneret", "abdomen", (0, 20, 30.5))
cube("spinneret", (-1.5, 19, 29.5), (3, 2.5, 1.5), "belly")
cube("spinneret", (-0.75, 19.5, 30.5), (1.5, 1.5, 1), "flesh")

# harness (hidden unless equipped) -- straps over the thorax and a rider frame under the belly
bone("harness", "body", (0, 20, 0))
cube("harness", (-5.5, 15.5, -3), (11, 9, 2), "harness", inflate=0.25)
cube("harness", (-5.5, 15.5, 2), (11, 9, 2), "harness", inflate=0.25)
cube("harness", (-4, 13, -5), (8, 1, 12), "harness")
cube("harness", (-4.5, 12.5, -5.5), (1, 2, 1), "buckle")
cube("harness", (3.5, 12.5, -5.5), (1, 2, 1), "buckle")
cube("harness", (-4.5, 12.5, 6.5), (1, 2, 1), "buckle")
cube("harness", (3.5, 12.5, 6.5), (1, 2, 1), "buckle")
cube("harness", (-1, 25, -3), (2, 1, 7), "buckle")

# rider anchor locator (under the belly, used for debugging/attachments)
bone("rider_anchor", "body", (0, 13, 0))

_scaling = False

# --- legs -------------------------------------------------------------------
# angle a: degrees from the lateral axis toward the front (+) or back (-)
LEGS = [
    # idx, hip z, angle, femur, tibia, reach
    (1, -6.5, 52.0, 20.0, 27.0, 36.0),
    (2, -2.6, 20.0, 19.0, 25.0, 30.0),
    (3, 1.3, -14.0, 19.0, 25.0, 30.0),
    (4, 5.2, -44.0, 21.0, 29.0, 37.0),
]
COXA = 2.0
TARSUS = 6.0
TARSUS_ELEV = -72.0   # tarsus absolute elevation at rest (deg)
HIP_X = 6.3

leg_records = []


def solve_leg(reach_from_hip, hip_y, femur, tibia):
    """Planar knee-up IK used for the rest pose (same maths as the in-game rig)."""
    r = reach_from_hip - COXA
    y = -hip_y
    te = math.radians(TARSUS_ELEV)
    tx = r - math.cos(te) * TARSUS
    ty = y - math.sin(te) * TARSUS
    d = math.hypot(tx, ty)
    d = max(abs(femur - tibia) + 0.01, min(femur + tibia - 0.01, d))
    beta = math.atan2(ty, tx)
    alpha = math.acos((femur * femur + d * d - tibia * tibia) / (2 * femur * d))
    ef = beta + alpha
    kx, ky = femur * math.cos(ef), femur * math.sin(ef)
    et = math.atan2(ty - ky, tx - kx)
    return ef, et - ef, te - et


for side, sname in ((1, "l"), (-1, "r")):
    for idx, hz, ang, fl, tl, reach in LEGS:
        name = f"leg_{sname}{idx}"
        hip = (side * HIP_X, HIP_Y, hz)
        a = math.radians(ang)
        # desired direction in file coords (x left, z back): left legs point +x, forward is -z
        # internal coordinates mirror x. Internal leg chain points along c*X with c = -side.
        c = -side
        # internal direction: (side_int * cos a, 0, -sin a) where side_int = -side
        dx_i, dz_i = c * math.cos(a), -math.sin(a)
        psi0 = math.atan2(-c * dz_i, c * dx_i)        # internal yaw
        rot_file_y = -math.degrees(psi0)
        bone(name, "body", hip, rotation=(0, rot_file_y, 0))
        bone(name + "_coxa", name, hip)
        # chain along file +X for left legs (side=1) and -X for right legs
        sx = side
        cube(name + "_coxa", (hip[0] - 1.75 if sx > 0 else hip[0] - 1.75, hip[1] - 1.75, hip[2] - 1.75), (3.5, 3.5, 3.5), "joint")
        fem_pivot = (hip[0] + sx * COXA, hip[1], hip[2])
        ef, et_rel, es_rel = solve_leg(reach, HIP_Y, fl, tl)
        # file rotation z for this side: internal phi = c * elevation, file z == internal z
        bone(name + "_femur", name + "_coxa", fem_pivot, rotation=(0, 0, c * math.degrees(ef)))
        x0 = fem_pivot[0] if sx > 0 else fem_pivot[0] - fl
        cube(name + "_femur", (x0, fem_pivot[1] - 1.4, fem_pivot[2] - 1.4), (fl, 2.8, 2.8), "leg")
        # femur bulge near the hip
        x1 = fem_pivot[0] if sx > 0 else fem_pivot[0] - fl * 0.45
        cube(name + "_femur", (x1, fem_pivot[1] - 1.6, fem_pivot[2] - 1.6), (fl * 0.45, 3.2, 3.2), "carapace")
        # knee spike
        kx = fem_pivot[0] + sx * (fl - 1.5)
        cube(name + "_femur", (kx - 0.5, fem_pivot[1] + 1.0, fem_pivot[2] - 0.5), (1, 2.5, 1), "spike",
             rotation=(0, 0, -sx * 25), pivot=(kx, fem_pivot[1] + 1.0, fem_pivot[2]))
        tib_pivot = (fem_pivot[0] + sx * fl, fem_pivot[1], fem_pivot[2])
        bone(name + "_tibia", name + "_femur", tib_pivot, rotation=(0, 0, c * math.degrees(et_rel)))
        xt = tib_pivot[0] if sx > 0 else tib_pivot[0] - tl
        cube(name + "_tibia", (xt, tib_pivot[1] - 1.1, tib_pivot[2] - 1.1), (tl, 2.2, 2.2), "leg")
        xj = tib_pivot[0] - 1.4
        cube(name + "_tibia", (xj, tib_pivot[1] - 1.4, tib_pivot[2] - 1.4), (2.8, 2.8, 2.8), "joint")
        # hair tufts on the tibia
        xh = tib_pivot[0] + sx * tl * 0.35 - (0 if sx > 0 else 1)
        cube(name + "_tibia", (xh, tib_pivot[1] + 0.9, tib_pivot[2] - 0.5), (1, 1, 1), "spike")
        tar_pivot = (tib_pivot[0] + sx * tl, tib_pivot[1], tib_pivot[2])
        bone(name + "_tarsus", name + "_tibia", tar_pivot, rotation=(0, 0, c * math.degrees(es_rel)))
        xs = tar_pivot[0] if sx > 0 else tar_pivot[0] - TARSUS
        cube(name + "_tarsus", (xs, tar_pivot[1] - 0.7, tar_pivot[2] - 0.7), (TARSUS, 1.4, 1.4), "leg")
        xc = tar_pivot[0] + sx * (TARSUS - 0.2) - (0 if sx > 0 else 1.6)
        cube(name + "_tarsus", (xc, tar_pivot[1] - 0.5, tar_pivot[2] - 0.5), (1.6, 1, 1), "fang")
        leg_records.append({
            "name": name, "side": side, "idx": idx, "hip": hip, "psi0": psi0,
            "femur": fl, "tibia": tl, "reach": reach, "angle": a,
        })

# ---------------------------------------------------------------------------
# Texture painting
# ---------------------------------------------------------------------------

PALETTE = {
    "carapace": ((36, 10, 13), (74, 20, 24)),
    "plate": ((22, 7, 9), (96, 26, 30)),
    "belly": ((70, 22, 26), (112, 34, 38)),
    "flesh": ((120, 30, 36), (170, 52, 58)),
    "abdomen": ((30, 8, 11), (66, 16, 20)),
    "vein": ((110, 10, 18), (190, 24, 34)),
    "leg": ((26, 9, 11), (58, 18, 21)),
    "joint": ((84, 18, 24), (128, 30, 36)),
    "spike": ((150, 128, 104), (60, 40, 34)),
    "fang": ((222, 210, 182), (58, 40, 30)),
    "eye": ((255, 40, 36), (120, 0, 6)),
    "mouth": ((40, 2, 6), (90, 10, 16)),
    "harness": ((78, 48, 26), (112, 72, 40)),
    "buckle": ((150, 150, 158), (205, 205, 214)),
}
GLOW = {"eye": 255, "vein": 150}

atlas = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)


class Packer:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.x = 0
        self.y = 0
        self.row_h = 0

    def alloc(self, w, h):
        if self.x + w > self.w:
            self.x = 0
            self.y += self.row_h + 1
            self.row_h = 0
        if self.y + h > self.h:
            raise RuntimeError("texture atlas full")
        pos = (self.x, self.y)
        self.x += w + 1
        self.row_h = max(self.row_h, h)
        return pos


packer = Packer(TEX_W, TEX_H)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def paint_face(mat, face, w, h):
    """Returns an RGBA array (h, w, 4) for one face and its glow mask."""
    lo, hi = PALETTE[mat]
    img = np.zeros((h, w, 4), dtype=np.uint8)
    gm = np.zeros((h, w, 4), dtype=np.uint8)
    light = {"up": 1.0, "north": 0.8, "south": 0.7, "east": 0.75, "west": 0.75, "down": 0.45}[face]
    for y in range(h):
        for x in range(w):
            n = rng.random()
            t = 0.35 + 0.4 * n
            if mat in ("carapace", "plate", "abdomen", "leg", "joint"):
                # rim highlight on the upper edge, darker toward the bottom
                if face != "up" and face != "down":
                    t += 0.35 * (1 - y / max(1, h - 1)) - 0.15
                if mat == "plate" and (x == 0 or y == 0 or x == w - 1 or y == h - 1):
                    t = 0.95
                if mat == "abdomen" and face == "up":
                    # crimson chevrons along the back (the Gorewidow marking)
                    cx = abs(x - (w - 1) / 2) / max(1, w / 2)
                    period = 7
                    if (y + int(cx * 6)) % period in (0, 1) and cx < 0.8:
                        col = lerp((150, 18, 26), (205, 34, 40), n)
                        img[y, x] = (*col, 255)
                        if (y + int(cx * 6)) % period == 0:
                            gm[y, x] = (200, 20, 30, 120)
                        continue
                if mat == "abdomen" and face in ("east", "west"):
                    if (x * 3 + y * 5) % 23 == 0:
                        t = 1.2
                if mat == "leg":
                    # segment banding along the length (u axis on side faces)
                    if face in ("north", "south", "up", "down") and (x // 2) % 9 == 0:
                        col = lerp((96, 22, 28), (140, 34, 40), n)
                        img[y, x] = (*col, 255)
                        continue
                    if n > 0.93:
                        t = 1.3  # bristle highlight
            elif mat in ("spike", "fang"):
                # bone fading to a dark tip along v
                t = y / max(1, h - 1) if face not in ("up", "down") else 0.3
                col = lerp(lo, hi, t)
                col = tuple(max(0, min(255, int(c * (0.85 + 0.25 * n)))) for c in col)
                img[y, x] = (*col, 255)
                continue
            elif mat == "eye":
                col = lo if not (x == 0 and y == 0) else (255, 220, 210)
                if face != "north" and face != "up":
                    col = hi
                img[y, x] = (*col, 255)
                gm[y, x] = (*col, GLOW["eye"]) if face == "north" or face == "up" else (0, 0, 0, 0)
                continue
            elif mat == "vein":
                base = lerp((40, 8, 12), (60, 12, 16), n)
                vein = ((x + int(3 * math.sin(y * 0.7))) % 5 == 0) or ((y + x // 3) % 7 == 0)
                if vein:
                    col = lerp(lo, hi, n)
                    img[y, x] = (*col, 255)
                    gm[y, x] = (*col, GLOW["vein"])
                else:
                    img[y, x] = (*base, 255)
                continue
            elif mat == "belly":
                if (x + y * 2) % 11 == 0:
                    t = 1.1
            elif mat == "harness":
                if y == 1 or y == h - 2:
                    col = (196, 170, 120) if x % 3 == 0 else lerp(lo, hi, 0.2)
                    img[y, x] = (*col, 255)
                    continue
            elif mat == "buckle":
                t = 0.3 + 0.7 * (1 - y / max(1, h - 1))
            col = lerp(lo, hi, max(0.0, min(1.3, t)))
            col = tuple(max(0, min(255, int(c * light))) for c in col)
            img[y, x] = (*col, 255)
    return img, gm


FACE_DIMS = {
    # face -> (width axis, height axis) in cube size indices
    "north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2),
}

for b in bones:
    for c in b["cubes"]:
        mat = c.pop("_mat")
        only = c.pop("_faces", None)
        uv = {}
        for face, (wa, ha) in FACE_DIMS.items():
            if only and face not in only:
                continue
            fw = max(1, int(math.ceil(c["size"][wa] * DENSITY)))
            fh = max(1, int(math.ceil(c["size"][ha] * DENSITY)))
            x, y = packer.alloc(fw, fh)
            img, gm = paint_face(mat, face, fw, fh)
            atlas[y:y + fh, x:x + fw] = img
            glow[y:y + fh, x:x + fw] = gm
            uv[face] = {"uv": [x, y], "uv_size": [fw, fh]}
        c["uv"] = uv

# ---------------------------------------------------------------------------
# Write model, textures and anatomy constants
# ---------------------------------------------------------------------------

geo = {
    "format_version": "1.12.0",
    "minecraft:geometry": [{
        "description": {
            "identifier": "geometry.gorewidow",
            "texture_width": TEX_W,
            "texture_height": TEX_H,
            "visible_bounds_width": 6,
            "visible_bounds_height": 4,
            "visible_bounds_offset": [0, 1.5, 0],
        },
        "bones": [{k: v for k, v in b.items() if not (k == "cubes" and not v)} for b in bones],
    }],
}

os.makedirs(os.path.join(ASSETS, "geo/entity"), exist_ok=True)
os.makedirs(os.path.join(ASSETS, "textures/entity"), exist_ok=True)
with open(os.path.join(ASSETS, "geo/entity/gorewidow.geo.json"), "w") as f:
    json.dump(geo, f, indent=1)
Image.fromarray(atlas, "RGBA").save(os.path.join(ASSETS, "textures/entity/gorewidow.png"))
Image.fromarray(glow, "RGBA").save(os.path.join(ASSETS, "textures/entity/gorewidow_glowmask.png"))

# Java constants (internal GeckoLib coordinates: x mirrored, units = model px)
lines = []
lines.append("package com.vladosiwe.gorewidow.entity;")
lines.append("")
lines.append("/**")
lines.append(" * Skeleton measurements shared by the model and the procedural leg rig.")
lines.append(" * GENERATED by tools/gen_model.py - do not edit by hand.")
lines.append(" * Units are model pixels in GeckoLib's internal space (x mirrored relative to the .geo.json).")
lines.append(" */")
lines.append("public final class GorewidowAnatomy {")
lines.append("    private GorewidowAnatomy() {}")
lines.append("")
lines.append(f"    public static final float BODY_HEIGHT = {BODY_Y}f;")
lines.append(f"    public static final float HIP_HEIGHT = {HIP_Y}f;")
lines.append(f"    public static final float COXA = {COXA}f;")
lines.append(f"    public static final float TARSUS = {TARSUS}f;")
lines.append(f"    public static final float TARSUS_ELEVATION = (float) Math.toRadians({TARSUS_ELEV});")
lines.append(f"    public static final int LEG_COUNT = {len(leg_records)};")
names = ", ".join(f'"{r["name"]}"' for r in leg_records)
lines.append(f"    public static final String[] LEG_NAMES = {{{names}}};")
lines.append("    /** -1 for left legs (chain along internal -X), +1 for right legs. */")
lines.append("    public static final int[] LEG_CHAIN_SIGN = {" + ", ".join(str(-r["side"]) for r in leg_records) + "};")
lines.append("    /** Gait group for the alternating tetrapod gait. */")
lines.append("    public static final int[] LEG_GROUP = {" + ", ".join(str((r["idx"] + (0 if r["side"] > 0 else 1)) % 2) for r in leg_records) + "};")
lines.append("    public static final float[][] HIP = {" + ", ".join(
    f"{{{-r['hip'][0]}f, {r['hip'][1]}f, {r['hip'][2]}f}}" for r in leg_records) + "};")
lines.append("    public static final float[] REST_YAW = {" + ", ".join(f"{r['psi0']:.6f}f" for r in leg_records) + "};")
lines.append("    public static final float[] FEMUR = {" + ", ".join(f"{r['femur']}f" for r in leg_records) + "};")
lines.append("    public static final float[] TIBIA = {" + ", ".join(f"{r['tibia']}f" for r in leg_records) + "};")
rest = []
for r in leg_records:
    c = -r["side"]
    dx, dz = c * math.cos(r["angle"]), -math.sin(r["angle"])
    hx, hy, hz = -r["hip"][0], r["hip"][1], r["hip"][2]
    rest.append(f"{{{hx + dx * r['reach']:.4f}f, 0f, {hz + dz * r['reach']:.4f}f}}")
lines.append("    /** Rest foot positions on the surface plane (y = 0). */")
lines.append("    public static final float[][] REST_FOOT = {" + ", ".join(rest) + "};")
lines.append("    /** Spinneret tip (web origin) at rest. */")
sp_tip = _bone_index["spinneret"]["pivot"]
lines.append(f"    public static final float[] SPINNERET = {{0f, {sp_tip[1]}f, {sp_tip[2] + 0.6}f}};")
lines.append("    /** Mouth / fang position at rest. */")
mouth = _sp_force((0, 15, -14.5))
lines.append(f"    public static final float[] MOUTH = {{0f, {mouth[1]:.3f}f, {mouth[2]:.3f}f}};")
lines.append("}")
os.makedirs(os.path.dirname(JAVA_OUT), exist_ok=True)
with open(JAVA_OUT, "w") as f:
    f.write("\n".join(lines) + "\n")

print(f"bones: {len(bones)}, cubes: {sum(len(b['cubes']) for b in bones)}, atlas rows used up to y={packer.y + packer.row_h}")
