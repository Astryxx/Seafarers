"""Paints the mermaid's 64x64 texture (assets/seafarers/textures/entity/mermaid.png) to fit MermaidRenderer's boxes.

python tools/gen_mermaid.py
Each box uses Minecraft's usual unwrapping from its texture offset (u, v) for a w x h x d box:
  top (u+d, v) w x d, bottom (u+d+w, v) w x d, then a row at v+d: right (u, .) d x h, front (u+d, .) w x h,
  left (u+d+w, .) d x h, back (u+2d+w, .) w x h.
"""
import pathlib
import random

from PIL import Image

OUT = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/assets/seafarers/textures/entity/mermaid.png"
rng = random.Random(9)
img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

# (palette from her fal concept, art/items/mermaid_concept.png, recoloured: dark sea-blue hair and a gold shell top)
SKIN = (236, 190, 156)
SKIN_SHADE = (210, 160, 128)
HAIR = (30, 52, 84)
HAIR_LIGHT = (50, 86, 124)
SCALE = (30, 168, 156)
SCALE_DARK = (18, 120, 116)
SCALE_LIGHT = (70, 204, 190)
SHELL = (232, 184, 84)
FIN = (196, 238, 228)
WAIST = (150, 224, 210)


def jitter(c, k=8):
    return tuple(max(0, min(255, v + rng.randint(-k, k))) for v in c) + (255,)


def rect(x, y, w, h, color_fn):
    for j in range(h):
        for i in range(w):
            img.putpixel((x + i, y + j), color_fn(i, j))


def box_faces(u, v, w, h, d):
    """Rectangles of each face: name -> (x, y, w, h)."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def scales(i, j):
    """A checker of three teals, as on her concept: each row offset from the last."""
    k = (i + j + (j // 2)) % 3
    return jitter((SCALE, SCALE_DARK, SCALE_LIGHT)[k], 5)


# ---- head (0, 0) 8x8x8: hair everywhere but the face, which has a fringe, eyes and a smile ----
f = box_faces(0, 0, 8, 8, 8)
for name, (x, y, w, h) in f.items():
    rect(x, y, w, h, lambda i, j: jitter(HAIR_LIGHT if (i + j) % 4 == 0 else HAIR, 6))
x, y, w, h = f["front"]
# her face: a fringe, locks down both sides, big eyes (white over dark blue), a small mouth
rect(x, y, w, h, lambda i, j: jitter(HAIR, 5) if j < 2 or (j < 6 and i in (0, 7)) or (j == 2 and i in (1, 6)) else jitter(SKIN, 4))
for ex in (2, 5):
    img.putpixel((x + ex, y + 3), (250, 250, 250, 255))
    img.putpixel((x + ex, y + 4), (26, 48, 104, 255))
    img.putpixel((x + ex + (1 if ex == 2 else -1), y + 4), (40, 70, 130, 255))
img.putpixel((x + 3, y + 6), (196, 104, 100, 255))
img.putpixel((x + 4, y + 6), (196, 104, 100, 255))
for side in ("right", "left"):   # ears showing through the hair, low down
    sx, sy, sw, sh = f[side]
    rect(sx + 3, sy + 4, 2, 2, lambda i, j: jitter(SKIN_SHADE, 4))
x, y, w, h = f["bottom"]
rect(x, y, w, h, lambda i, j: jitter(SKIN_SHADE, 4))

# ---- long hair down her back (32, 0) 8x14x1 ----
for name, (x, y, w, h) in box_faces(32, 0, 8, 14, 1).items():
    rect(x, y, w, h, lambda i, j: jitter(HAIR_LIGHT if (i * 3 + j) % 7 == 0 else HAIR, 6))

# ---- torso (16, 16) 8x12x4: skin, a shell top, the scales starting at the waist ----
for name, (x, y, w, h) in box_faces(16, 16, 8, 12, 4).items():
    def torso(i, j, name=name, h=h):
        if name in ("top", "bottom"):
            return jitter(SKIN, 4)
        if j == h - 2:
            return jitter(WAIST, 5)   # the pale scalloped edge where her scales begin
        if j >= h - 1:
            return scales(i, j)
        if name == "front" and 3 <= j <= 4 and i in (1, 2, 5, 6):
            return jitter(SHELL, 10)
        if name in ("left", "right", "back") and j == 4:
            return jitter(SHELL, 10)   # the band round her back
        return jitter(SKIN if j > 1 else SKIN_SHADE, 4)
    rect(x, y, w, h, torso)

# ---- arms (40, 16) 3x11x3, with a shell bracelet ----
for name, (x, y, w, h) in box_faces(40, 16, 3, 11, 3).items():
    rect(x, y, w, h, lambda i, j, name=name: jitter(SHELL, 8) if name not in ("top", "bottom") and j == 8 else jitter(SKIN, 4))

# ---- tail: hips (0, 32) 8x8x4, tail (24, 32) 6x8x4, tip (44, 32) 4x6x3 ----
for (u, v, w, h, d) in ((0, 32, 8, 8, 4), (24, 32, 6, 8, 4), (44, 32, 4, 6, 3)):
    for name, (x, y, fw, fh) in box_faces(u, v, w, h, d).items():
        rect(x, y, fw, fh, scales)

# ---- fin (0, 48) 10x4x1: pale, with ribs ----
for name, (x, y, w, h) in box_faces(0, 48, 10, 4, 1).items():
    rect(x, y, w, h, lambda i, j: jitter(SCALE_LIGHT if i % 3 == 0 else FIN, 6))

OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print(f"wrote {OUT}")
