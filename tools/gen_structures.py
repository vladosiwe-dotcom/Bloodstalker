#!/usr/bin/env python3
"""Writes the GameTest arena structure (gzipped NBT) used by the Gorewidow game tests."""
import gzip
import os
import struct

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/data/gorewidow/structure/arena.nbt")
DATA_VERSION = 3955  # Minecraft 1.21.1

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def s(name):
    b = name.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def payload(tag, value):
    if tag == TAG_INT:
        return struct.pack(">i", value)
    if tag == TAG_STRING:
        return s(value)
    if tag == TAG_LIST:
        elem_tag, items = value
        out = struct.pack(">bi", elem_tag if items else TAG_END, len(items))
        return out + b"".join(payload(elem_tag, it) for it in items)
    if tag == TAG_COMPOUND:
        out = b""
        for k, (t, v) in value.items():
            out += struct.pack(">b", t) + s(k) + payload(t, v)
        return out + struct.pack(">b", TAG_END)
    raise ValueError(tag)


SIZE = (16, 14, 16)
blocks = []
for x in range(SIZE[0]):
    for z in range(SIZE[2]):
        blocks.append((TAG_COMPOUND, {"pos": (TAG_LIST, (TAG_INT, [x, 0, z])), "state": (TAG_INT, 0)}))

root = {
    "DataVersion": (TAG_INT, DATA_VERSION),
    "size": (TAG_LIST, (TAG_INT, list(SIZE))),
    "palette": (TAG_LIST, (TAG_COMPOUND, [{"Name": (TAG_STRING, "minecraft:stone")}])),
    "blocks": (TAG_LIST, (TAG_COMPOUND, [b[1] for b in blocks])),
    "entities": (TAG_LIST, (TAG_COMPOUND, [])),
}
data = struct.pack(">b", TAG_COMPOUND) + s("") + payload(TAG_COMPOUND, root)
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with gzip.open(OUT, "wb") as f:
    f.write(data)
print("wrote", OUT)
