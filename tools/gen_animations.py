#!/usr/bin/env python3
"""Writes the Gorewidow keyframe animations (GeckoLib / Bedrock animation format).

Leg bones are intentionally absent: legs are driven every frame by the procedural rig
(GorewidowRig) with IK and blended poses. These clips animate body, head, fangs, palps,
abdomen and spinneret.

Rotation values follow Blockbench file conventions (degrees). For this model:
  head/body  +x = nose down, -x = nose up
  abdomen    -x = tip down (tuck under), +x = tip up
  fang_l     -z = open outward;  fang_r +z = open outward
  fangs      -x = tips swing forward
"""
import json
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/gorewidow/animations/entity/gorewidow.animation.json")

anims = {}


def kf(points, easing="easeInOutSine"):
    """points: list of (time, [x, y, z]) -> keyframe dict with easing."""
    out = {}
    for t, v in points:
        out[f"{t:.4f}".rstrip("0").rstrip(".") if t else "0.0"] = {"vector": [round(c, 3) for c in v], "easing": easing.lower()}
    return out


def loop_sine(length, amp, axis, phase=0.0, steps=8, offset=(0, 0, 0), freq=1):
    pts = []
    for i in range(steps + 1):
        t = length * i / steps
        s = math.sin(2 * math.pi * freq * i / steps + phase)
        v = list(offset)
        v[axis] += amp * s
        pts.append((t, v))
    return kf(pts, "linear")


def anim(name, length, loop, bones):
    a = {"animation_length": length, "bones": bones}
    if loop is True:
        a["loop"] = True
    elif loop == "hold":
        a["loop"] = "hold_on_last_frame"
    anims[name] = a


def merge(*dicts):
    out = {}
    for d in dicts:
        for bone, channels in d.items():
            out.setdefault(bone, {}).update(channels)
    return out


# ---------------------------------------------------------------- loops
anim("idle", 3.2, True, merge(
    {"body": {"position": loop_sine(3.2, 0.35, 1)}},
    {"abdomen": {"scale": kf([(0, [1, 1, 1]), (1.6, [1.04, 1.03, 1.035]), (3.2, [1, 1, 1])]),
                 "rotation": kf([(0, [-12, 0, 0]), (1.6, [-10, 1.5, 0]), (3.2, [-12, 0, 0])])}},
    {"head": {"rotation": kf([(0, [0, 0, 0]), (0.9, [0, 0, 0]), (1.3, [2, 5, 0]), (2.2, [2, 5, 0]), (2.6, [0, -3, 0]), (3.2, [0, 0, 0])])}},
    {"palp_l": {"rotation": kf([(0, [30, -10, 0]), (0.7, [30, -10, 0]), (0.85, [18, -14, 0]), (1.05, [30, -10, 0]), (3.2, [30, -10, 0])])}},
    {"palp_r": {"rotation": kf([(0, [30, 10, 0]), (2.0, [30, 10, 0]), (2.15, [18, 14, 0]), (2.35, [30, 10, 0]), (3.2, [30, 10, 0])])}},
    {"fang_l": {"rotation": kf([(0, [0, 0, 0]), (2.4, [0, 0, 0]), (2.55, [0, 0, -6]), (2.75, [0, 0, 0]), (3.2, [0, 0, 0])])}},
    {"fang_r": {"rotation": kf([(0, [0, 0, 0]), (2.4, [0, 0, 0]), (2.55, [0, 0, 6]), (2.75, [0, 0, 0]), (3.2, [0, 0, 0])])}},
))

anim("walk", 0.6, True, merge(
    {"body": {"position": loop_sine(0.6, 0.45, 1, freq=2),
              "rotation": loop_sine(0.6, 1.5, 2)}},
    {"abdomen": {"rotation": kf([(0, [-12, -3, 0]), (0.3, [-10, 3, 0]), (0.6, [-12, -3, 0])])}},
    {"head": {"rotation": loop_sine(0.6, 2, 1, phase=1.0)}},
    {"palp_l": {"rotation": kf([(0, [40, -10, 0]), (0.3, [18, -10, 0]), (0.6, [40, -10, 0])])}},
    {"palp_r": {"rotation": kf([(0, [18, 10, 0]), (0.3, [40, 10, 0]), (0.6, [18, 10, 0])])}},
))

anim("run", 0.36, True, merge(
    {"body": {"position": loop_sine(0.36, 0.9, 1, freq=2, offset=(0, 0.3, 0)),
              "rotation": kf([(0, [4, 0, -2]), (0.18, [3, 0, 2]), (0.36, [4, 0, -2])])}},
    {"abdomen": {"rotation": kf([(0, [-16, -4, 0]), (0.09, [-8, 0, 0]), (0.18, [-16, 4, 0]), (0.27, [-8, 0, 0]), (0.36, [-16, -4, 0])])}},
    {"head": {"rotation": kf([(0, [6, 0, 0]), (0.18, [3, 0, 0]), (0.36, [6, 0, 0])])}},
    {"palp_l": {"rotation": kf([(0, [55, -15, 0]), (0.18, [40, -15, 0]), (0.36, [55, -15, 0])])}},
    {"palp_r": {"rotation": kf([(0, [40, 15, 0]), (0.18, [55, 15, 0]), (0.36, [40, 15, 0])])}},
))

anim("climb", 0.7, True, merge(
    {"body": {"position": loop_sine(0.7, 0.4, 1, freq=2, offset=(0, -0.6, 0)),
              "rotation": loop_sine(0.7, 2.0, 2)}},
    {"abdomen": {"rotation": kf([(0, [-6, -4, 0]), (0.35, [-4, 4, 0]), (0.7, [-6, -4, 0])])}},
    {"head": {"rotation": kf([(0, [-4, 0, 0]), (0.35, [-6, 3, 0]), (0.7, [-4, 0, 0])])}},
    {"palp_l": {"rotation": kf([(0, [10, -20, 0]), (0.35, [30, -10, 0]), (0.7, [10, -20, 0])])}},
    {"palp_r": {"rotation": kf([(0, [30, 10, 0]), (0.35, [10, 20, 0]), (0.7, [30, 10, 0])])}},
))

anim("cling", 2.6, True, merge(
    {"body": {"position": kf([(0, [0, -0.8, 0]), (1.3, [0, -0.5, 0]), (2.6, [0, -0.8, 0])])}},
    {"abdomen": {"scale": kf([(0, [1, 1, 1]), (1.3, [1.03, 1.03, 1.02]), (2.6, [1, 1, 1])]),
                 "rotation": kf([(0, [-6, 0, 0]), (1.3, [-5, 2, 0]), (2.6, [-6, 0, 0])])}},
    {"palp_l": {"rotation": kf([(0, [25, -12, 0]), (1.0, [12, -18, 0]), (1.4, [25, -12, 0]), (2.6, [25, -12, 0])])}},
    {"palp_r": {"rotation": kf([(0, [25, 12, 0]), (1.6, [25, 12, 0]), (2.0, [12, 18, 0]), (2.6, [25, 12, 0])])}},
))

anim("lurk", 4.0, True, merge(
    {"body": {"position": kf([(0, [0, -1.4, 0]), (2.0, [0, -1.2, 0]), (4.0, [0, -1.4, 0])])}},
    {"abdomen": {"scale": kf([(0, [1, 1, 1]), (2.0, [1.02, 1.02, 1.01]), (4.0, [1, 1, 1])]),
                 "rotation": kf([(0, [-4, 0, 0]), (4.0, [-4, 0, 0])])}},
    {"head": {"rotation": kf([(0, [4, 0, 0]), (1.6, [4, 0, 0]), (2.0, [4, 8, 0]), (3.2, [4, 8, 0]), (3.6, [4, 0, 0]), (4.0, [4, 0, 0])])}},
    {"palp_l": {"rotation": kf([(0, [50, -6, 0]), (2.6, [50, -6, 0]), (2.75, [44, -6, 0]), (2.9, [50, -6, 0]), (4.0, [50, -6, 0])])}},
    {"palp_r": {"rotation": kf([(0, [50, 6, 0]), (4.0, [50, 6, 0])])}},
))

anim("hang", 3.0, True, merge(
    {"body": {"rotation": kf([(0, [0, -8, 0]), (1.5, [0, 8, 0]), (3.0, [0, -8, 0])])}},
    {"abdomen": {"rotation": kf([(0, [4, 0, 0]), (1.5, [6, 0, 0]), (3.0, [4, 0, 0])]),
                 "scale": kf([(0, [1, 1, 1]), (1.5, [1.03, 1.03, 1.03]), (3.0, [1, 1, 1])])}},
    {"head": {"rotation": kf([(0, [-10, 0, 0]), (1.5, [-6, 0, 0]), (3.0, [-10, 0, 0])])}},
    {"palp_l": {"rotation": kf([(0, [-20, -10, 0]), (3.0, [-20, -10, 0])])}},
    {"palp_r": {"rotation": kf([(0, [-20, 10, 0]), (3.0, [-20, 10, 0])])}},
))

anim("swing", 1.0, True, merge(
    {"abdomen": {"rotation": kf([(0, [8, 0, 0]), (0.5, [12, 0, 0]), (1.0, [8, 0, 0])]),
                 "scale": kf([(0, [1, 1, 1.06]), (1.0, [1, 1, 1.06])])}},
    {"head": {"rotation": kf([(0, [-12, 0, 0]), (0.5, [-8, 0, 0]), (1.0, [-12, 0, 0])])}},
    {"palp_l": {"rotation": kf([(0, [-10, -20, 0]), (1.0, [-10, -20, 0])])}},
    {"palp_r": {"rotation": kf([(0, [-10, 20, 0]), (1.0, [-10, 20, 0])])}},
))

anim("air", 0.6, True, merge(
    {"body": {"rotation": kf([(0, [-6, 0, 0]), (0.3, [-4, 0, 0]), (0.6, [-6, 0, 0])])}},
    {"abdomen": {"rotation": kf([(0, [4, 0, 0]), (0.6, [4, 0, 0])])}},
    {"palp_l": {"rotation": kf([(0, [-5, -25, 0]), (0.6, [-5, -25, 0])])}},
    {"palp_r": {"rotation": kf([(0, [-5, 25, 0]), (0.6, [-5, 25, 0])])}},
    {"fang_l": {"rotation": kf([(0, [0, 0, -15]), (0.6, [0, 0, -15])])}},
    {"fang_r": {"rotation": kf([(0, [0, 0, 15]), (0.6, [0, 0, 15])])}},
))

anim("web_pull", 0.6, True, merge(
    {"body": {"position": kf([(0, [0, 0, 0]), (0.12, [0, 0.4, 1.2]), (0.6, [0, 0, 0])], "easeOutQuad")}},
    {"abdomen": {"rotation": kf([(0, [-26, 0, 0]), (0.12, [-10, 0, 0]), (0.6, [-26, 0, 0])], "easeOutQuad")}},
    {"spinneret": {"scale": kf([(0, [1, 1, 1]), (0.1, [1.3, 1.3, 1.3]), (0.4, [1, 1, 1]), (0.6, [1, 1, 1])])}},
    {"head": {"rotation": kf([(0, [-6, 0, 0]), (0.6, [-6, 0, 0])])}},
    {"fang_l": {"rotation": kf([(0, [-10, 0, -20]), (0.3, [-10, 0, -26]), (0.6, [-10, 0, -20])])}},
    {"fang_r": {"rotation": kf([(0, [-10, 0, 20]), (0.3, [-10, 0, 26]), (0.6, [-10, 0, 20])])}},
))

anim("drain", 1.0, True, merge(
    {"body": {"position": kf([(0, [0, -1.6, -0.6]), (0.5, [0, -1.2, -0.8]), (1.0, [0, -1.6, -0.6])]),
              "rotation": kf([(0, [8, 0, 0]), (0.5, [10, 0, 0]), (1.0, [8, 0, 0])])}},
    {"head": {"rotation": kf([(0, [18, 0, 0]), (0.25, [24, 0, 0]), (0.5, [18, 0, 0]), (0.75, [24, 0, 0]), (1.0, [18, 0, 0])]),
              "position": kf([(0, [0, 0, -1]), (0.25, [0, -0.4, -1.6]), (0.5, [0, 0, -1]), (0.75, [0, -0.4, -1.6]), (1.0, [0, 0, -1])])}},
    {"fang_l": {"rotation": kf([(0, [-25, 0, 4]), (0.25, [-30, 0, -10]), (0.5, [-25, 0, 4]), (0.75, [-30, 0, -10]), (1.0, [-25, 0, 4])])}},
    {"fang_r": {"rotation": kf([(0, [-25, 0, -4]), (0.25, [-30, 0, 10]), (0.5, [-25, 0, -4]), (0.75, [-30, 0, 10]), (1.0, [-25, 0, -4])])}},
    {"abdomen": {"scale": kf([(0, [1.04, 1.04, 1.04]), (0.3, [1.12, 1.1, 1.08]), (0.6, [1.06, 1.06, 1.05]), (1.0, [1.04, 1.04, 1.04])]),
                 "rotation": kf([(0, [-6, 0, 0]), (0.5, [-2, 0, 0]), (1.0, [-6, 0, 0])])}},
    {"palp_l": {"rotation": kf([(0, [55, 10, 0]), (0.5, [60, 14, 0]), (1.0, [55, 10, 0])])}},
    {"palp_r": {"rotation": kf([(0, [55, -10, 0]), (0.5, [60, -14, 0]), (1.0, [55, -10, 0])])}},
))

anim("sit", 3.0, True, merge(
    {"body": {"position": kf([(0, [0, -4.5, 0]), (1.5, [0, -4.2, 0]), (3.0, [0, -4.5, 0])])}},
    {"abdomen": {"rotation": kf([(0, [-2, 0, 0]), (3.0, [-2, 0, 0])]),
                 "scale": kf([(0, [1, 1, 1]), (1.5, [1.03, 1.02, 1.02]), (3.0, [1, 1, 1])])}},
    {"head": {"rotation": kf([(0, [6, 0, 0]), (3.0, [6, 0, 0])])}},
    {"palp_l": {"rotation": kf([(0, [60, -5, 0]), (3.0, [60, -5, 0])])}},
    {"palp_r": {"rotation": kf([(0, [60, 5, 0]), (3.0, [60, 5, 0])])}},
))

# ---------------------------------------------------------------- one-shots
anim("death", 1.6, "hold", merge(
    {"body": {"position": kf([(0, [0, 0, 0]), (0.25, [0, 1.0, 0]), (0.9, [0, -7, 0]), (1.6, [0, -7.5, 0])], "easeOutBounce"),
              "rotation": kf([(0, [0, 0, 0]), (0.25, [-8, 0, 3]), (1.6, [4, 0, 8])])}},
    {"head": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-20, 0, 0]), (1.6, [28, 0, 6])])}},
    {"abdomen": {"rotation": kf([(0, [-12, 0, 0]), (0.3, [5, 0, 0]), (1.6, [2, 0, -6])]),
                 "scale": kf([(0, [1, 1, 1]), (1.6, [0.92, 0.88, 0.95])])}},
    {"fang_l": {"rotation": kf([(0, [0, 0, 0]), (0.3, [0, 0, -35]), (1.6, [0, 0, -20])])}},
    {"fang_r": {"rotation": kf([(0, [0, 0, 0]), (0.3, [0, 0, 35]), (1.6, [0, 0, 20])])}},
    {"palp_l": {"rotation": kf([(0, [30, -10, 0]), (1.6, [80, 0, 0])])}},
    {"palp_r": {"rotation": kf([(0, [30, 10, 0]), (1.6, [80, 0, 0])])}},
))

anim("bite", 0.45, False, merge(
    {"head": {"position": kf([(0, [0, 0, 0]), (0.1, [0, 0.4, 1.4]), (0.22, [0, -0.6, -3.2]), (0.45, [0, 0, 0])], "easeOutQuad"),
              "rotation": kf([(0, [0, 0, 0]), (0.1, [-16, 0, 0]), (0.22, [22, 0, 0]), (0.45, [0, 0, 0])], "easeOutQuad")}},
    {"body": {"position": kf([(0, [0, 0, 0]), (0.1, [0, 0.5, 0.6]), (0.22, [0, -0.4, -1.2]), (0.45, [0, 0, 0])])}},
    {"fang_l": {"rotation": kf([(0, [0, 0, 0]), (0.12, [-10, 0, -34]), (0.22, [-28, 0, 8]), (0.45, [0, 0, 0])], "easeOutQuad")}},
    {"fang_r": {"rotation": kf([(0, [0, 0, 0]), (0.12, [-10, 0, 34]), (0.22, [-28, 0, -8]), (0.45, [0, 0, 0])], "easeOutQuad")}},
    {"palp_l": {"rotation": kf([(0, [30, -10, 0]), (0.12, [-10, -30, 0]), (0.3, [50, 5, 0]), (0.45, [30, -10, 0])])}},
    {"palp_r": {"rotation": kf([(0, [30, 10, 0]), (0.12, [-10, 30, 0]), (0.3, [50, -5, 0]), (0.45, [30, 10, 0])])}},
))

anim("web_shoot", 0.5, False, merge(
    {"abdomen": {"rotation": kf([(0, [-12, 0, 0]), (0.14, [-34, 0, 0]), (0.26, [-30, 0, 0]), (0.5, [-12, 0, 0])], "easeOutQuad")}},
    {"spinneret": {"scale": kf([(0, [1, 1, 1]), (0.16, [1.7, 1.7, 1.4]), (0.3, [1, 1, 1]), (0.5, [1, 1, 1])], "easeOutQuad")}},
    {"body": {"position": kf([(0, [0, 0, 0]), (0.14, [0, 0.8, 0.5]), (0.5, [0, 0, 0])])}},
    {"head": {"rotation": kf([(0, [0, 0, 0]), (0.14, [-8, 0, 0]), (0.5, [0, 0, 0])])}},
))

anim("hurt", 0.3, False, merge(
    {"body": {"position": kf([(0, [0, 0, 0]), (0.07, [0, 0.6, 1.4]), (0.3, [0, 0, 0])], "easeOutQuad"),
              "rotation": kf([(0, [0, 0, 0]), (0.07, [-7, 3, 0]), (0.3, [0, 0, 0])], "easeOutQuad")}},
    {"abdomen": {"rotation": kf([(0, [-12, 0, 0]), (0.07, [-2, 6, 0]), (0.3, [-12, 0, 0])])}},
    {"head": {"rotation": kf([(0, [0, 0, 0]), (0.07, [-12, -6, 0]), (0.3, [0, 0, 0])])}},
))

anim("screech", 0.9, False, merge(
    {"head": {"rotation": kf([(0, [0, 0, 0]), (0.18, [-28, 0, 0]), (0.3, [-26, 3, 0]), (0.45, [-28, -3, 0]), (0.6, [-26, 0, 0]), (0.9, [0, 0, 0])])}},
    {"body": {"position": kf([(0, [0, 0, 0]), (0.18, [0, 1.6, 0.6]), (0.6, [0, 1.4, 0.6]), (0.9, [0, 0, 0])]),
              "rotation": kf([(0, [0, 0, 0]), (0.18, [-8, 0, 0]), (0.6, [-8, 0, 0]), (0.9, [0, 0, 0])])}},
    {"fang_l": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-10, 0, -38]), (0.3, [-10, 0, -32]), (0.45, [-10, 0, -38]), (0.6, [-10, 0, -32]), (0.9, [0, 0, 0])])}},
    {"fang_r": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-10, 0, 38]), (0.3, [-10, 0, 32]), (0.45, [-10, 0, 38]), (0.6, [-10, 0, 32]), (0.9, [0, 0, 0])])}},
    {"palp_l": {"rotation": kf([(0, [30, -10, 0]), (0.18, [-40, -25, 0]), (0.6, [-40, -25, 0]), (0.9, [30, -10, 0])])}},
    {"palp_r": {"rotation": kf([(0, [30, 10, 0]), (0.18, [-40, 25, 0]), (0.6, [-40, 25, 0]), (0.9, [30, 10, 0])])}},
    {"abdomen": {"rotation": kf([(0, [-12, 0, 0]), (0.18, [6, 0, 0]), (0.6, [6, 0, 0]), (0.9, [-12, 0, 0])])}},
))

anim("tame", 2.0, False, merge(
    {"abdomen": {"rotation": kf([(0, [-12, 0, 0]), (0.25, [-8, 10, 0]), (0.5, [-8, -10, 0]), (0.75, [-8, 10, 0]), (1.0, [-8, -10, 0]),
                                 (1.25, [-8, 10, 0]), (1.5, [-8, -6, 0]), (2.0, [-12, 0, 0])])}},
    {"head": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-6, 0, 8]), (1.0, [-6, 0, -8]), (1.6, [-6, 0, 6]), (2.0, [0, 0, 0])])}},
    {"palp_l": {"rotation": kf([(0, [30, -10, 0])] + [(0.15 * i + 0.15, [55 if i % 2 == 0 else 15, -10, 0]) for i in range(11)] + [(2.0, [30, -10, 0])], "linear")}},
    {"palp_r": {"rotation": kf([(0, [30, 10, 0])] + [(0.15 * i + 0.15, [15 if i % 2 == 0 else 55, 10, 0]) for i in range(11)] + [(2.0, [30, 10, 0])], "linear")}},
    {"body": {"position": kf([(0, [0, 0, 0]), (0.5, [0, -1.2, 0]), (1.0, [0, 0.3, 0]), (1.5, [0, -1.0, 0]), (2.0, [0, 0, 0])])}},
))

anim("mount", 0.8, False, merge(
    {"body": {"position": kf([(0, [0, 0, 0]), (0.25, [0, -5, 0]), (0.5, [0, -4.5, 0]), (0.8, [0, 0, 0])], "easeInOutQuad")}},
    {"abdomen": {"rotation": kf([(0, [-12, 0, 0]), (0.25, [-4, 0, 0]), (0.8, [-12, 0, 0])])}},
    {"head": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-10, 0, 0]), (0.8, [0, 0, 0])])}},
))

anim("land", 0.4, False, merge(
    {"body": {"position": kf([(0, [0, 0, 0]), (0.08, [0, -3.8, 0]), (0.22, [0, 0.6, 0]), (0.4, [0, 0, 0])], "easeOutQuad")}},
    {"abdomen": {"rotation": kf([(0, [-12, 0, 0]), (0.08, [-2, 0, 0]), (0.25, [-16, 0, 0]), (0.4, [-12, 0, 0])])}},
    {"head": {"rotation": kf([(0, [0, 0, 0]), (0.08, [8, 0, 0]), (0.4, [0, 0, 0])])}},
))

anim("leap", 0.5, False, merge(
    {"body": {"position": kf([(0, [0, 0, 0]), (0.12, [0, -3.2, 0.6]), (0.24, [0, 1.2, -0.8]), (0.5, [0, 0, 0])], "easeOutQuad")}},
    {"abdomen": {"rotation": kf([(0, [-12, 0, 0]), (0.12, [-22, 0, 0]), (0.3, [0, 0, 0]), (0.5, [-12, 0, 0])])}},
    {"head": {"rotation": kf([(0, [0, 0, 0]), (0.12, [6, 0, 0]), (0.3, [-12, 0, 0]), (0.5, [0, 0, 0])])}},
))

# GeckoLib adds rotation keyframes on top of the bone's rest rotation from the .geo.json, so the
# clips above (authored as absolute poses for readability) are converted to deltas here.
REST_ROTATION = {"abdomen": [-12, 0, 0], "palp_l": [30, -10, 0], "palp_r": [30, 10, 0]}
for clip in anims.values():
    for bone, rest in REST_ROTATION.items():
        rot = clip["bones"].get(bone, {}).get("rotation")
        if rot:
            for frame in rot.values():
                frame["vector"] = [round(v - r, 3) for v, r in zip(frame["vector"], rest)]

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w") as f:
    json.dump({"format_version": "1.8.0", "animations": anims}, f, indent=1)
print(f"{len(anims)} animations -> {OUT}")
