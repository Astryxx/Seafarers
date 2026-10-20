"""Procedural 16x16 textures in a Sea-of-Thieves-like palette: original pixel art, no game assets.
Placeholders until tools/gen_fal_textures.py is run; it overwrites the same files.

python tools/gen_textures.py
"""
import pathlib
import random

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "seafarers" / "textures"


def save(img, path):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    img.save(p)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


def boards(rng, base, horizontal=True, seam=0.55, grain=0.12, boards_n=4, nails=None, stripe=None, joints=True):
    """Planking: boards_n boards with dark seams, staggered butt joints and wood grain."""
    img = Image.new("RGBA", (16, 16))
    size = 16 // boards_n
    tones = [0.92 + rng.random() * 0.16 for _ in range(boards_n)]
    for a in range(16):          # along the boards
        for b in range(16):      # across the boards
            board = b // size
            k = tones[board] * (1 - grain / 2 + grain * rng.random())
            if b % size == size - 1:
                k *= seam
            if joints and (a + board * 6) % 16 == 0:
                k *= 0.7
            if (a * 3 + b * 7 + board) % 13 == 0:
                k *= 0.88
            x, y = (a, b) if horizontal else (b, a)
            img.putpixel((x, y), shade(base, k))
    if nails:
        for board in range(boards_n):
            a = (board * 6 + 2) % 16
            for off in (1, size - 2):
                x, y = (a, board * size + off) if horizontal else (board * size + off, a)
                img.putpixel((x, y), nails)
    if stripe:
        for a in range(16):
            x, y = (a, 1) if horizontal else (1, a)
            img.putpixel((x, y), stripe)
    return img


def grain(rng, base, vertical=True, k=0.14, rings=None):
    img = Image.new("RGBA", (16, 16))
    offs = [rng.random() for _ in range(16)]
    for y in range(16):
        for x in range(16):
            line = x if vertical else y
            along = y if vertical else x
            v = 1 - k / 2 + k * offs[line]
            if (line * 5 + along // 4) % 7 == 0:
                v *= 0.85
            img.putpixel((x, y), shade(base, v * (0.97 + 0.06 * rng.random())))
    return img


def end_grain(rng, base, bark):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                img.putpixel((x, y), shade(bark, 0.9 + 0.2 * rng.random()))
                continue
            d = max(abs(x - 7.5), abs(y - 7.5))
            k = 0.85 if int(d) % 2 else 1.05
            img.putpixel((x, y), shade(base, k * (0.95 + 0.1 * rng.random())))
    return img


def noise(rng, base, k=0.2, speck=None, speck_rate=0.0):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = speck if speck and rng.random() < speck_rate else base
            img.putpixel((x, y), shade(c, 1 - k / 2 + k * rng.random()))
    return img


def hole(base, seed):
    rng = random.Random(seed)
    img = base.copy()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 8) ** 2) ** 0.5 + rng.random() * 1.8
            if d < 4.3:
                img.putpixel((x, y), (0, 0, 0, 0))
            elif d < 5.8:
                r, g, b, _ = img.getpixel((x, y))
                img.putpixel((x, y), (min(255, r + 70), min(255, g + 55), min(255, b + 35), 255))
    return img


def darken(img, k):
    return Image.eval(img.convert("RGB"), lambda v: int(v * k)).convert("RGBA")


def sail(rng):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = 0.94 + 0.06 * rng.random()
            if (x * 7 + y * 3) % 5 == 0:
                k *= 0.97  # weave
            img.putpixel((x, y), shade((236, 224, 196), k))
    # one panel seam down the middle of each square
    for y in range(16):
        r, g, b, _ = img.getpixel((8, y))
        img.putpixel((8, y), (int(r * 0.9), int(g * 0.9), int(b * 0.88), 255))
    return img


def rope_ladder(rng):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in (2, 13):
            img.putpixel((x, y), shade((176, 138, 88), 0.85 + 0.25 * rng.random()))
            img.putpixel((x + 1, y), shade((150, 114, 70), 0.85 + 0.2 * rng.random()))
    for y in (2, 7, 12):
        for x in range(2, 15):
            img.putpixel((x, y), shade((132, 92, 56), 0.9 + 0.2 * rng.random()))
            img.putpixel((x, y + 1), shade((96, 66, 40), 0.9 + 0.2 * rng.random()))
    return img


def rope(rng):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = [0.72, 0.95, 1.08, 0.88][(x + y) % 4] * (0.95 + 0.1 * rng.random())
            img.putpixel((x, y), shade((190, 150, 96), k))
    return img


def barrel_top(rng):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d > 6.5:
                c = shade((70, 70, 74), 0.9 + 0.2 * rng.random())
            else:
                c = shade((150, 104, 62), (0.85 if x % 4 == 3 else 1.0) * (0.94 + 0.12 * rng.random()))
            img.putpixel((x, y), c)
    return img


def chart(rng):
    img = noise(rng, (222, 196, 146), 0.12)
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                img.putpixel((x, y), (150, 110, 66, 255))
    for (x, y) in [(5, 6), (6, 6), (7, 6), (5, 7), (6, 7), (7, 7), (8, 7), (6, 8), (7, 8)]:
        img.putpixel((x, y), (120, 160, 80, 255))  # island
    for (x, y) in [(9, 9), (10, 10), (11, 11)]:
        img.putpixel((x, y), (130, 40, 30, 255))  # dotted path
    img.putpixel((12, 11), (200, 30, 30, 255))
    img.putpixel((11, 12), (200, 30, 30, 255))
    img.putpixel((13, 12), (200, 30, 30, 255))
    img.putpixel((12, 13), (200, 30, 30, 255))
    return img


def flag(rng):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = 0.9 + 0.15 * rng.random()
            if (x + y) % 9 == 0:
                k *= 0.8
            img.putpixel((x, y), shade((170, 28, 30), k))
    return img


def mattress(rng):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            base = (196, 178, 140) if (x // 2) % 2 else (84, 104, 140)
            img.putpixel((x, y), shade(base, 0.92 + 0.12 * rng.random()))
    return img


def banana(rng):
    return noise(rng, (236, 202, 48), 0.18, speck=(120, 80, 30), speck_rate=0.06)


def figurehead(rng):
    img = noise(rng, (226, 210, 176), 0.12)
    for y in range(16):
        for x in range(16):
            if (x + 2 * y) % 11 == 0 or (2 * x - y) % 13 == 0:
                img.putpixel((x, y), (204, 158, 54, 255))  # gold-leaf scrollwork
    return img


# ---------- items ----------
def icon_cannonball(rng):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 6:
                light = max(0.0, 1 - (((x - 5) ** 2 + (y - 5) ** 2) ** 0.5) / 8)
                img.putpixel((x, y), shade((40, 42, 46), 0.8 + 1.5 * light))
            elif d < 6.8:
                img.putpixel((x, y), (14, 14, 16, 255))
    return img


def icon_chain_shot(rng):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for cx, cy in ((4, 11), (11, 4)):
        for y in range(16):
            for x in range(16):
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                if d < 3.2:
                    img.putpixel((x, y), shade((44, 46, 50), 1.6 if (x < cx and y < cy) else 1.0))
                elif d < 3.8:
                    img.putpixel((x, y), (14, 14, 16, 255))
    for i in range(6, 10):
        img.putpixel((i, 15 - i), (150, 150, 156, 255))
        img.putpixel((i + 1, 15 - i), (90, 90, 96, 255))
    return img


def icon_banana(rng):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(2, 14):
        y = int(10 - 0.12 * (x - 8) ** 2 * -1) - 4 + int(0.1 * (x - 8) ** 2)
        for t in range(3):
            img.putpixel((x, min(15, y + t)), shade((240, 206, 52), 1.1 - 0.15 * t))
        img.putpixel((x, min(15, y + 3)), (150, 110, 30, 255))
    img.putpixel((13, 5), (90, 60, 20, 255))
    img.putpixel((2, 6), (60, 50, 20, 255))
    return img


def icon_deed(rng, masts=1):
    """A deed: a sheet of paper with the ship on it (one, two or three masts) and a red seal."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    paper = (226, 204, 154)
    for y in range(2, 14):
        for x in range(2, 14):
            img.putpixel((x, y), shade(paper, 0.92 + 0.12 * rng.random()))
    for y in range(2, 14):
        img.putpixel((1, y), shade(paper, 0.7))
        img.putpixel((14, y), shade(paper, 0.7))
    hull = (92, 60, 34, 255)
    left, right = (4, 12) if masts == 1 else (3, 13)
    for x in range(left, right):
        img.putpixel((x, 10), hull)
    for x in range(left + 1, right - 1):
        img.putpixel((x, 11), hull)
    xs = {1: [8], 2: [6, 10], 3: [5, 8, 11]}[masts]
    for i, mx in enumerate(xs):
        tall = mx == 8 or masts == 2 and i == 1
        for y in range(4 if tall else 5, 10):
            img.putpixel((mx, y), (60, 40, 24, 255))
        w = 3 if masts == 1 else 2
        for y in range(5 if tall else 6, 9):
            for x in range(mx - w, mx):
                img.putpixel((x, y), (250, 246, 236, 255))
    img.putpixel((xs[len(xs) // 2], 3), (200, 30, 30, 255))
    img.putpixel((12, 3), (160, 20, 20, 255))
    img.putpixel((12, 4), (160, 20, 20, 255))
    return img


def main():
    rng = random.Random(7)
    t = {}
    t["hull_planks"] = boards(rng, (122, 84, 52), seam=0.62, nails=(58, 44, 34, 255), joints=False)
    t["hull_bottom"] = boards(rng, (62, 52, 42), seam=0.66, grain=0.18, joints=False)
    t["hull_trim"] = boards(rng, (88, 44, 30), boards_n=2, seam=0.7, stripe=(214, 168, 62, 255), joints=False)
    t["hull_trim_top"] = grain(rng, (104, 52, 36), vertical=False)
    t["deck_planks"] = boards(rng, (190, 146, 92), horizontal=False, seam=0.6)
    t["keel"] = grain(rng, (78, 56, 38))
    t["keel_end"] = end_grain(rng, (150, 110, 70), (64, 46, 32))
    t["mast"] = grain(rng, (176, 132, 86))
    t["railing"] = grain(rng, (98, 50, 34))
    t["sail_cloth"] = sail(rng)
    t["rope"] = rope(rng)
    t["brass"] = noise(rng, (204, 160, 70), 0.25)
    t["cannon_iron"] = noise(rng, (50, 52, 58), 0.3, speck=(110, 66, 40), speck_rate=0.04)
    t["carriage"] = boards(rng, (110, 44, 34), boards_n=4, seam=0.6, nails=(40, 40, 44, 255))
    t["helm_wood"] = grain(rng, (92, 58, 36))
    t["helm_spoke"] = grain(rng, (184, 140, 90))
    t["barrel_side"] = boards(rng, (150, 104, 62), horizontal=False)
    t["barrel_top"] = barrel_top(rng)
    t["map"] = chart(rng)
    t["mattress"] = mattress(rng)
    t["figurehead"] = figurehead(rng)
    t["flag"] = flag(rng)
    t["banana"] = banana(rng)
    t["rope_ladder"] = rope_ladder(rng)
    for name, img in t.items():
        save(img, f"block/{name}.png")
    save(darken(t["hull_planks"], 0.6), "block/hull_planks_flooded.png")
    save(darken(t["hull_bottom"], 0.65), "block/hull_bottom_flooded.png")
    save(hole(t["hull_planks"], 3), "block/hull_hole.png")
    save(hole(t["hull_bottom"], 5), "block/hull_hole_bottom.png")
    band = t["mast"].copy()
    for y in (0, 1, 14, 15):
        for x in range(16):
            band.putpixel((x, y), t["cannon_iron"].getpixel((x, y)))
    save(band, "block/mast_band.png")

    save(icon_cannonball(rng), "item/cannonball.png")
    save(icon_chain_shot(rng), "item/chain_shot.png")
    save(icon_banana(rng), "item/banana.png")
    save(icon_deed(rng), "item/sloop_deed.png")
    save(icon_deed(random.Random(11), 2), "item/brigantine_deed.png")
    save(icon_deed(random.Random(12), 3), "item/galleon_deed.png")


if __name__ == "__main__":
    main()
