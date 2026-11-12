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

TEX_W, TEX_H = 1024, 512
DENSITY = 2  # texels per model pixel

rng = random.Random(1337)

# ---------------------------------------------------------------------------
# Skeleton definition
# ---------------------------------------------------------------------------

bones = []  # list of dicts in geo format (parents before children)
_bone_index = {}


# Body parts (not legs) are authored at a compact size and scaled up around the body centre so
# the cephalothorax / abdomen read with proper mass against the long legs.
BODY_SCALE = 1.25
BODY_CENTRE = (0.0, 20.0, 0.0)
# The body is authored around y=20 and then lifted: the Gorewidow carries its body slung high
# between tall arched legs, with room for a rider hanging underneath.
LIFT = 12.0
_scaling = False


def _sp_force(p):
    return tuple(c + (v - c) * BODY_SCALE + (LIFT if i == 1 else 0) for i, (v, c) in enumerate(zip(p, BODY_CENTRE)))


def _sp(p):
    if not _scaling:
        return tuple(p)
    return _sp_force(p)


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
BODY_Y = 20.0 + LIFT   # body centre height above the surface (px)
HIP_Y = 19.5 + LIFT

bone("root", None, (0, 0, 0))
_scaling = True
bone("tilt", "root", (0, 20.0, 0))                # procedural pitch / roll from leg heights
bone("body", "tilt", (0, 20.0, 0))                # keyframed bob / breathing

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

# crest: a fan of hooked, bony spines rising from the back of the head
for i, (x, h, lean) in enumerate(((-2.6, 4.0, 14), (-1.3, 5.5, 8), (0.0, 6.5, 0), (1.3, 5.5, -8), (2.6, 4.0, -14))):
    cube("head", (x - 0.5, 24, -9.5), (1.0, h, 1.0), "spike", rotation=(-28, 0, lean), pivot=(x, 24, -9))
    cube("head", (x - 0.6, 23.6 + h * 0.5, -9.1), (1.2, 1.0, 1.2), "joint", rotation=(-28, 0, lean), pivot=(x, 24, -9))
# jaw plates flanking the fangs
for side in (1, -1):
    cube("head", (side * 3.2 - 1, 14.5, -14.5), (2, 3, 4), "plate", rotation=(10, 0, side * -12), pivot=(side * 3.2, 16, -12))

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
    (1, -6.0, 54.0, 30.0, 46.0, 44.0),
    (2, -2.4, 22.0, 28.0, 42.0, 38.0),
    (3, 1.2, -14.0, 28.0, 42.0, 38.0),
    (4, 4.8, -46.0, 30.0, 46.0, 44.0),
]
COXA = 2.0
TARSUS = 7.0
TARSUS_ELEV = -80.0   # tarsus absolute elevation at rest (deg)
HIP_X = 6.0
ARCH = 0.07           # how far femur and tibia bow outward (fraction of their length)

leg_records = []


def arc_y(t, bulge):
    """Height of the bowed limb above its chord at fraction t (parabola, 0 at both ends)."""
    return 4 * bulge * t * (1 - t)


def arc_segments(bone_name, pivot, sx, length, thick0, thick1, bulge, mat, n=4):
    """A limb segment drawn as n short cubes following an outward bow, tapering from thick0 to thick1."""
    for k in range(n):
        t0, t1 = k / n, (k + 1) / n
        x0, y0 = t0 * length, arc_y(t0, bulge)
        x1, y1 = t1 * length, arc_y(t1, bulge)
        seg = math.hypot(x1 - x0, y1 - y0) + 0.6
        ang = math.degrees(math.atan2(y1 - y0, x1 - x0))
        th = thick0 + (thick1 - thick0) * (t0 + t1) * 0.5
        start = (pivot[0] + sx * x0, pivot[1] + y0, pivot[2])
        ox = start[0] if sx > 0 else start[0] - seg
        cube(bone_name, (ox, start[1] - th / 2, start[2] - th / 2), (seg, th, th), mat,
             rotation=(0, 0, -sx * ang), pivot=start)


def membrane(bone_name, pivot, sx, length, bulge, t_from, t_to, h_from, h_to, seed, cols=7):
    """A thin sail hanging inside the arch, from t_from to t_to of the limb, with a ragged edge."""
    r = random.Random(seed)
    for k in range(cols):
        t0 = t_from + (t_to - t_from) * k / cols
        t1 = t_from + (t_to - t_from) * (k + 1) / cols
        tm = (t0 + t1) * 0.5
        h = h_from + (h_to - h_from) * (k + 0.5) / cols + r.uniform(-0.4, 0.4)
        top = pivot[1] + min(arc_y(t0, bulge), arc_y(t1, bulge)) - 0.4
        w = (t1 - t0) * length + 0.5
        x = pivot[0] + sx * t0 * length - (0 if sx > 0 else w)
        cube(bone_name, (x, top - h, pivot[2] - 0.2), (w, h, 0.4), "membrane", faces=("north", "south", "down"))


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
        arc_segments(name + "_femur", fem_pivot, sx, fl, 2.6, 1.0, ARCH * fl, "leg")
        # armoured base of the femur
        x1 = fem_pivot[0] if sx > 0 else fem_pivot[0] - fl * 0.3
        cube(name + "_femur", (x1, fem_pivot[1] - 1.7, fem_pivot[2] - 1.7), (fl * 0.3, 3.4, 3.4), "carapace")
        # silk membrane slung under the arch: grows toward the knee
        membrane(name + "_femur", fem_pivot, sx, fl, ARCH * fl, 0.4, 1.0, 2.0, 9.0, seed=idx * 7 + side)
        tib_pivot = (fem_pivot[0] + sx * fl, fem_pivot[1], fem_pivot[2])
        bone(name + "_tibia", name + "_femur", tib_pivot, rotation=(0, 0, c * math.degrees(et_rel)))
        arc_segments(name + "_tibia", tib_pivot, sx, tl, 2.0, 0.75, ARCH * tl, "leg")
        cube(name + "_tibia", (tib_pivot[0] - 1.7, tib_pivot[1] - 1.7, tib_pivot[2] - 1.7), (3.4, 3.4, 3.4), "joint")
        # knee hook pointing up out of the arch
        cube(name + "_tibia", (tib_pivot[0] - 0.5, tib_pivot[1] + 1.2, tib_pivot[2] - 0.5), (1, 3.5, 1), "spike",
             rotation=(0, 0, -sx * 20), pivot=(tib_pivot[0], tib_pivot[1] + 1.2, tib_pivot[2]))
        membrane(name + "_tibia", tib_pivot, sx, tl, ARCH * tl, 0.0, 0.55, 9.0, 1.5, seed=idx * 13 + side)
        # barbed flap at the end of the membrane
        xb = tib_pivot[0] + sx * tl * 0.55 - (0 if sx > 0 else 2.0)
        yb = tib_pivot[1] + arc_y(0.56, ARCH * tl) - 0.8
        cube(name + "_tibia", (xb, yb - 3.2, tib_pivot[2] - 0.25), (2.0, 3.2, 0.5), "spike",
             rotation=(0, 0, sx * 25), pivot=(xb + (0 if sx > 0 else 2.0), yb, tib_pivot[2]))
        tar_pivot = (tib_pivot[0] + sx * tl, tib_pivot[1], tib_pivot[2])
        bone(name + "_tarsus", name + "_tibia", tar_pivot, rotation=(0, 0, c * math.degrees(es_rel)))
        xs = tar_pivot[0] if sx > 0 else tar_pivot[0] - TARSUS
        cube(name + "_tarsus", (xs, tar_pivot[1] - 0.8, tar_pivot[2] - 0.8), (TARSUS, 1.6, 1.6), "leg")
        # grasping claw: two curved hooks
        xc = tar_pivot[0] + sx * (TARSUS - 0.4)
        for dz in (-0.9, 0.9):
            cube(name + "_tarsus", (xc - (0 if sx > 0 else 2.2), tar_pivot[1] - 0.5, tar_pivot[2] + dz - 0.4), (2.2, 0.9, 0.8), "fang",
                 rotation=(0, -sx * dz * 25, -sx * 30), pivot=(xc, tar_pivot[1], tar_pivot[2] + dz))
        leg_records.append({
            "name": name, "side": side, "idx": idx, "hip": hip, "psi0": psi0,
            "femur": fl, "tibia": tl, "reach": reach, "angle": a,
        })

# --- claspers ----------------------------------------------------------------
# Two pairs of short grasping arms under the thorax. Folded flat against the belly most of the
# time; while ridden they reach down and hook their claws around the rider's shoulders and hips.
CLASPERS = [
    # idx, hip x, hip z, rest angle (deg toward the front)
    (1, 3.2, -4.2, 25.0),
    (2, 3.2, 4.4, -25.0),
]
CLASP_Y = 26.0
CLASP_COXA = 1.0
CLASP_FEMUR = 6.5
CLASP_TIBIA = 7.0
CLASP_TARSUS = 3.0
CLASP_TARSUS_ELEV = -140.0
CLASP_FOLD = (38.0, -150.0, -45.0)   # femur elevation, tibia and tarsus relative (deg)
clasp_records = []
for side, sname in ((1, "l"), (-1, "r")):
    for idx, hx, hz, ang in CLASPERS:
        name = f"clasp_{sname}{idx}"
        hip = (side * hx, CLASP_Y, hz)
        a = math.radians(ang)
        c = -side
        dx_i, dz_i = c * math.cos(a), -math.sin(a)
        psi0 = math.atan2(-c * dz_i, c * dx_i)
        bone(name, "body", hip, rotation=(0, -math.degrees(psi0), 0))
        bone(name + "_coxa", name, hip)
        sx = side
        cube(name + "_coxa", (hip[0] - 1.1, hip[1] - 1.1, hip[2] - 1.1), (2.2, 2.2, 2.2), "joint")
        fp = (hip[0] + sx * CLASP_COXA, hip[1], hip[2])
        bone(name + "_femur", name + "_coxa", fp, rotation=(0, 0, c * CLASP_FOLD[0]))
        cube(name + "_femur", (fp[0] if sx > 0 else fp[0] - CLASP_FEMUR, fp[1] - 1.0, fp[2] - 1.0), (CLASP_FEMUR, 2.0, 2.0), "joint")
        tp = (fp[0] + sx * CLASP_FEMUR, fp[1], fp[2])
        bone(name + "_tibia", name + "_femur", tp, rotation=(0, 0, c * CLASP_FOLD[1]))
        cube(name + "_tibia", (tp[0] - 0.9, tp[1] - 0.9, tp[2] - 0.9), (1.8, 1.8, 1.8), "joint")
        cube(name + "_tibia", (tp[0] if sx > 0 else tp[0] - CLASP_TIBIA, tp[1] - 0.8, tp[2] - 0.8), (CLASP_TIBIA, 1.6, 1.6), "leg")
        # a small spur on the forearm
        sp = tp[0] + sx * CLASP_TIBIA * 0.5
        cube(name + "_tibia", (sp - 0.4, tp[1] + 0.5, tp[2] - 0.4), (0.8, 1.6, 0.8), "spike",
             rotation=(0, 0, -sx * 30), pivot=(sp, tp[1] + 0.5, tp[2]))
        hp = (tp[0] + sx * CLASP_TIBIA, tp[1], tp[2])
        bone(name + "_tarsus", name + "_tibia", hp, rotation=(0, 0, c * CLASP_FOLD[2]))
        # the hand: two hooked fingers
        for dz in (-0.5, 0.5):
            cube(name + "_tarsus", (hp[0] if sx > 0 else hp[0] - CLASP_TARSUS, hp[1] - 0.4, hp[2] + dz - 0.35),
                 (CLASP_TARSUS, 1.0, 0.8), "fang", rotation=(0, -sx * dz * 30, 0), pivot=(hp[0], hp[1], hp[2] + dz))
        clasp_records.append({"name": name, "side": side, "hip": hip, "psi0": psi0})

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
    "membrane": ((92, 12, 22), (150, 26, 36)),
    "buckle": ((150, 150, 158), (205, 205, 214)),
}
GLOW = {"eye": 255, "vein": 150, "membrane": 70}

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
            elif mat == "membrane":
                # stretched skin: a net of dark veins, a few of them faintly lit with blood
                net = ((x + y) % 6 == 0) or ((x - y) % 6 == 0)
                if net:
                    col = lerp((50, 4, 10), (70, 8, 14), n)
                    img[y, x] = (*col, 255)
                    if n > 0.7:
                        gm[y, x] = (170, 20, 30, GLOW["membrane"])
                    continue
                if face == "down":
                    t = 0.2
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
            "visible_bounds_width": 9,
            "visible_bounds_height": 6,
            "visible_bounds_offset": [0, 2.5, 0],
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
lines.append("    /** Grasping arms under the thorax that hold the rider (same chain layout as the legs). */")
lines.append("    public static final String[] CLASP_NAMES = {" + ", ".join(f'"{r["name"]}"' for r in clasp_records) + "};")
lines.append("    public static final int[] CLASP_CHAIN_SIGN = {" + ", ".join(str(-r["side"]) for r in clasp_records) + "};")
lines.append("    public static final float[][] CLASP_HIP = {" + ", ".join(
    f"{{{-r['hip'][0]}f, {r['hip'][1]}f, {r['hip'][2]}f}}" for r in clasp_records) + "};")
lines.append("    public static final float[] CLASP_REST_YAW = {" + ", ".join(f"{r['psi0']:.6f}f" for r in clasp_records) + "};")
lines.append(f"    public static final float CLASP_COXA = {CLASP_COXA}f;")
lines.append(f"    public static final float CLASP_FEMUR = {CLASP_FEMUR}f;")
lines.append(f"    public static final float CLASP_TIBIA = {CLASP_TIBIA}f;")
lines.append(f"    public static final float CLASP_TARSUS = {CLASP_TARSUS}f;")
lines.append(f"    public static final float CLASP_TARSUS_ELEVATION = (float) Math.toRadians({CLASP_TARSUS_ELEV});")
lines.append(f"    public static final float[] CLASP_FOLD = {{(float) Math.toRadians({CLASP_FOLD[0]}), (float) Math.toRadians({CLASP_FOLD[1]}), (float) Math.toRadians({CLASP_FOLD[2]})}};")
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
