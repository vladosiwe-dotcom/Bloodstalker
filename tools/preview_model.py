#!/usr/bin/env python3
"""Software preview of a GeckoLib .geo.json using GeckoLib's own transform conventions.

Mirrors software.bernie.geckolib bone baking (x mirrored, file rotations x/y negated) and the
render order translate(pivot) -> rotZ -> rotY -> rotX -> translate(-pivot) so the picture matches
what the game draws. Textured with a per-quad perspective warp, painter's algorithm.

Usage: python3 tools/preview_model.py OUT.png [yaw_deg] [pitch_deg] [bone_overrides.json]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEO = os.path.join(ROOT, "src/main/resources/assets/gorewidow/geo/entity/gorewidow.geo.json")
TEX = os.path.join(ROOT, "src/main/resources/assets/gorewidow/textures/entity/gorewidow.png")


def rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]])


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]])


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]])


def tr(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def load(overrides=None):
    geo = json.load(open(GEO))["minecraft:geometry"][0]
    bones = {b["name"]: b for b in geo["bones"]}
    overrides = overrides or {}
    mats = {}

    def bone_matrix(name):
        if name in mats:
            return mats[name]
        b = bones[name]
        parent = mats_for(b.get("parent"))
        px, py, pz = b["pivot"]
        pv = (-px, py, pz)
        r = b.get("rotation", [0, 0, 0])
        rot = [math.radians(-r[0]), math.radians(-r[1]), math.radians(r[2])]
        if name in overrides:
            o = overrides[name]
            rot = [o.get("x", rot[0]), o.get("y", rot[1]), o.get("z", rot[2])]
        m = parent @ tr(pv[0] / 16, pv[1] / 16, pv[2] / 16) @ rz(rot[2]) @ ry(rot[1]) @ rx(rot[0]) @ tr(-pv[0] / 16, -pv[1] / 16, -pv[2] / 16)
        mats[name] = m
        return m

    def mats_for(name):
        return np.eye(4) if name is None else bone_matrix(name)

    quads = []
    for name, b in bones.items():
        m = bone_matrix(name)
        if name == "harness" and overrides.get("_hide_harness", True):
            continue
        for c in b.get("cubes", []):
            ox, oy, oz = c["origin"]
            sx, sy, sz = c["size"]
            inf = c.get("inflate", 0)
            o = np.array([-(ox + sx), oy, oz]) / 16
            s = np.array([sx, sy, sz]) / 16
            lo = o - inf / 16
            hi = o + s + inf / 16
            cm = np.eye(4)
            if "rotation" in c:
                cpx, cpy, cpz = c["pivot"]
                cp = np.array([-cpx, cpy, cpz]) / 16
                r = c["rotation"]
                cm = tr(*cp) @ rz(math.radians(r[2])) @ ry(math.radians(-r[1])) @ rx(math.radians(-r[0])) @ tr(*(-cp))
            full = m @ cm
            corners = {}
            for ix, x in enumerate((lo[0], hi[0])):
                for iy, y in enumerate((lo[1], hi[1])):
                    for iz, z in enumerate((lo[2], hi[2])):
                        corners[(ix, iy, iz)] = (full @ np.array([x, y, z, 1]))[:3]
            faces = {
                "north": [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)],
                "south": [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
                "east": [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)],
                "west": [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)],
                "up": [(1, 1, 1), (0, 1, 1), (0, 1, 0), (1, 1, 0)],
                "down": [(1, 0, 0), (0, 0, 0), (0, 0, 1), (1, 0, 1)],
            }
            for fname, idx in faces.items():
                uvd = c.get("uv", {}).get(fname)
                if not uvd:
                    continue
                pts = [corners[i] for i in idx]
                quads.append((pts, uvd["uv"], uvd["uv_size"]))
    return quads


def homography(src, dst):
    a = []
    for (x, y), (u, v) in zip(dst, src):
        a.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        a.append([0, 0, 0, x, y, 1, -v * x, -v * y])
    b = [c for p in src for c in p]
    return np.linalg.solve(np.array(a, float), np.array(b, float))


def render(out, yaw=35, pitch=20, overrides=None, size=(900, 700), scale=150):
    tex = Image.open(TEX).convert("RGBA")
    quads = load(overrides)
    cy, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    light = np.array([0.4, 0.8, -0.45])
    light /= np.linalg.norm(light)

    def project(p):
        # model faces -Z; look from the front-left
        x, y, z = p[0], p[1] - 1.2, p[2]
        x, z = x * cy - z * sy_, x * sy_ + z * cy
        y, z = y * cp + z * sp, -y * sp + z * cp
        return (size[0] / 2 + x * scale, size[1] / 2 - y * scale, z)

    canvas = Image.new("RGBA", size, (34, 38, 46, 255))
    d = ImageDraw.Draw(canvas)
    # ground grid
    for i in range(-3, 4):
        a = project((i, 0, -3)); b = project((i, 0, 3))
        d.line([a[:2], b[:2]], fill=(60, 66, 78))
        a = project((-3, 0, i)); b = project((3, 0, i))
        d.line([a[:2], b[:2]], fill=(60, 66, 78))
    projected = []
    for pts, uv, uvs in quads:
        pp = [project(p) for p in pts]
        n = np.cross(np.array(pts[1]) - pts[0], np.array(pts[3]) - pts[0])
        nn = np.linalg.norm(n)
        shade = 1.0 if nn < 1e-9 else 0.55 + 0.45 * abs(float(np.dot(n / nn, light)))
        depth = sum(p[2] for p in pp) / 4
        projected.append((depth, pp, uv, uvs, shade))
    projected.sort(key=lambda q: -q[0])
    for depth, pp, uv, uvs, shade in projected:
        poly = [(p[0], p[1]) for p in pp]
        xs = [p[0] for p in poly]; ys = [p[1] for p in poly]
        x0, y0, x1, y1 = int(min(xs)), int(min(ys)), int(max(xs)) + 1, int(max(ys)) + 1
        if x1 - x0 < 1 or y1 - y0 < 1:
            continue
        u, v = uv; w, h = uvs
        src = [(u, v), (u + w, v), (u + w, v + h), (u, v + h)]
        local = [(p[0] - x0, p[1] - y0) for p in poly]
        try:
            coeffs = homography(src, local)
        except np.linalg.LinAlgError:
            continue
        patch = tex.transform((x1 - x0, y1 - y0), Image.PERSPECTIVE, tuple(coeffs), Image.NEAREST)
        arr = np.array(patch).astype(float)
        arr[..., :3] *= shade
        patch = Image.fromarray(arr.clip(0, 255).astype(np.uint8), "RGBA")
        mask = Image.new("L", patch.size, 0)
        ImageDraw.Draw(mask).polygon(local, fill=255)
        canvas.paste(patch, (x0, y0), mask)
    canvas.save(out)


if __name__ == "__main__":
    out = sys.argv[1]
    yaw = float(sys.argv[2]) if len(sys.argv) > 2 else 35
    pitch = float(sys.argv[3]) if len(sys.argv) > 3 else 20
    ov = json.load(open(sys.argv[4])) if len(sys.argv) > 4 else None
    render(out, yaw, pitch, ov)
