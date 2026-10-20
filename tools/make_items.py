"""Turns the fal item sources (art/items, from gen_fal_items.py) into 16x16 item icons, locally, and derives the
cursed cannonballs from the cannonball (tinted with each curse's colour, from Ammo.java, and ringed with it).

python tools/make_items.py
"""
import pathlib

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "art" / "items"
OUT = ROOT / "src" / "main" / "resources" / "assets" / "seafarers" / "textures" / "item"

ICONS = ["cannonball", "chain_shot", "firebomb", "banana", "grog", "ship_bucket", "ship_bucket_water", "ship_bucket_vomit",
         "grog_bucket", "rowboat", "rowboat_cannon", "rowboat_harpoon", "charred_food"]
# (the deeds stay as gen_textures.py draws them: at 16 pixels a drawn ship with 1, 2 or 3 masts reads better than art)
CURSES = {"anchor_ball": 0x5F6B78, "rigging_ball": 0xD8C9A3, "ballast_ball": 0x3E6E4C, "grog_ball": 0xC8932F,
          "limp_ball": 0x7F7FA0, "venom_ball": 0x5BAA3A, "weary_ball": 0x3F5AA8, "jig_ball": 0xB64FC4}


def cutout(img):
    """The white background goes, flood-filled in from the edges (white inside the object stays)."""
    img = img.convert("RGBA")
    w, h = img.size
    px = img.load()
    seen = set()
    stack = [(x, y) for x in range(w) for y in (0, h - 1)] + [(x, y) for y in range(h) for x in (0, w - 1)]

    def whiteish(c):
        return c[0] > 225 and c[1] > 225 and c[2] > 225

    while stack:
        x, y = stack.pop()
        if (x, y) in seen or not (0 <= x < w and 0 <= y < h):
            continue
        seen.add((x, y))
        if not whiteish(px[x, y]):
            continue
        px[x, y] = (0, 0, 0, 0)
        stack += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
    return img


def to_icon(img, size=16, margin=0):
    """Crops to the object, pads it square, and shrinks it to size x size with hard alpha and a few colours."""
    box = img.getbbox()
    img = img.crop(box)
    side = max(img.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(img, ((side - img.size[0]) // 2, (side - img.size[1]) // 2))
    inner = size - 2 * margin
    # average each target pixel's area, colour weighted by how much of it is covered
    small = square.resize((inner, inner), Image.BOX)
    px = small.load()
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y in range(inner):
        for x in range(inner):
            r, g, b, a = px[x, y]
            if a >= 110:
                k = 255 / max(a, 1)
                out.putpixel((x + margin, y + margin), (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)), 255))
    # a crisp dark outline on the silhouette's edge, as Minecraft's items have
    o = out.load()
    edge = []
    for y in range(size):
        for x in range(size):
            if o[x, y][3] == 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < size and 0 <= ny < size) or o[nx, ny][3] == 0:
                    edge.append((x, y))
                    break
    for x, y in edge:
        r, g, b, _ = o[x, y]
        o[x, y] = (int(r * 0.45), int(g * 0.45), int(b * 0.45), 255)
    return out.quantize(colors=24, method=Image.Quantize.FASTOCTREE).convert("RGBA") if False else out


def tint(icon, color, k):
    cr, cg, cb = (color >> 16) & 255, (color >> 8) & 255, color & 255
    out = icon.copy()
    px = out.load()
    for y in range(out.size[1]):
        for x in range(out.size[0]):
            r, g, b, a = px[x, y]
            if a:
                lum = (r * 0.3 + g * 0.59 + b * 0.11) / 255
                # keep the shading, take the curse's colour (light balls stay light)
                tr, tg, tb = (int(c * (0.35 + 0.9 * lum)) for c in (cr, cg, cb))
                px[x, y] = (int(r * (1 - k) + min(255, tr) * k), int(g * (1 - k) + min(255, tg) * k),
                            int(b * (1 - k) + min(255, tb) * k), a)
    return out


def ringed(icon, color):
    """A cursed ball: tinted, with a broken ring of the curse's colour glowing round it."""
    out = tint(icon, color, 0.75)
    px = out.load()
    cr, cg, cb = (color >> 16) & 255, (color >> 8) & 255, color & 255
    glow = (min(255, cr + 70), min(255, cg + 70), min(255, cb + 70), 255)
    for y in range(16):
        for x in range(16):
            if px[x, y][3]:
                continue
            near = any(0 <= x + dx < 16 and 0 <= y + dy < 16 and px[x + dx, y + dy][3] == 255 and px[x + dx, y + dy] != glow
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if near and (x + y) % 3 != 0:
                px[x, y] = glow
    return out


def main():
    icons = {}
    for name in ICONS:
        src = SRC / f"{name}.png"
        if not src.exists():
            print(f"  missing {src.name}: kept the old icon")
            continue
        margin = 2 if name == "cannonball" else 0
        icons[name] = to_icon(cutout(Image.open(src)), margin=margin)
        icons[name].save(OUT / f"{name}.png")
    if "cannonball" in icons:
        for name, color in CURSES.items():
            ringed(icons["cannonball"], color).save(OUT / f"{name}.png")
    print(f"items: {len(icons)} from fal sources, {len(CURSES)} cursed shot made from the cannonball")


if __name__ == "__main__":
    main()
