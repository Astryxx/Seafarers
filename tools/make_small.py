"""The small textures, made locally: from images generated on this PC with ComfyUI (art/local, Stable Diffusion 1.5;
see the comfy_manifest.jsonl there) and from the fal textures already in the mod. No paid generation.

  chart paper  the grain of a generated parchment, laid over the chart's cream, darker toward the edges
  grog         a crop of a generated glass of amber grog
  drunk icon   a generated tankard, cut out and shrunk to the 18x18 effect icon
  mast band    the mast's wood with iron bands from the cannon's iron
  rope ladder  the ladder's shape, in the rope's and the wheel spokes' colours

python tools/make_small.py
"""
import pathlib
import random

from PIL import Image, ImageFilter, ImageOps

ROOT = pathlib.Path(__file__).resolve().parent.parent
ART = ROOT / "art" / "local"
TEX = ROOT / "src" / "main" / "resources" / "assets" / "seafarers" / "textures"
rng = random.Random(4)


def chart_paper():
    grain = ImageOps.autocontrast(Image.open(ART / "chart_paper_1.png").convert("L").resize((256, 256), Image.LANCZOS), cutoff=2)
    base = (232, 214, 172)
    out = Image.new("RGB", (256, 256))
    gp, op = grain.load(), out.load()
    for y in range(256):
        for x in range(256):
            g = (gp[x, y] - 128) / 128.0                     # -1..1 around the paper's middle tone
            edge = min(x, y, 255 - x, 255 - y) / 40.0          # a little darker toward the edges, as old paper is
            shade = 0.86 + 0.14 * min(1.0, edge)
            op[x, y] = tuple(max(0, min(255, int(c * shade * (1 + 0.07 * g)))) for c in base)
    out.save(TEX / "gui" / "chart_paper.png")


def grog():
    img = Image.open(ART / "grog.png").convert("RGB")
    # the liquid in the glass, clear of the foam
    crop = img.crop((280, 330, 360, 410)).resize((16, 16), Image.BOX)
    crop = crop.filter(ImageFilter.SMOOTH)
    crop.save(TEX / "block" / "grog.png")


def drunk_icon():
    img = Image.open(ART / "drunk_icon.png").convert("RGBA")
    w, h = img.size
    px = img.load()
    bg = px[2, 2]
    # the background colour goes everywhere, inside the handles too
    for y in range(h):
        for x in range(w):
            c = px[x, y]
            if sum(abs(c[i] - bg[i]) for i in range(3)) <= 90:
                px[x, y] = (0, 0, 0, 0)
    img = img.crop(img.getbbox())
    side = max(img.size)
    sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    sq.paste(img, ((side - img.size[0]) // 2, (side - img.size[1]) // 2))
    small = sq.resize((16, 16), Image.BOX)
    out = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    sp = small.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = sp[x, y]
            if a >= 110:
                k = 255 / a
                out.putpixel((x + 1, y + 1), (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)), 255))
    op = out.load()
    edge = [(x, y) for y in range(18) for x in range(18) if op[x, y][3]
            and any(not (0 <= x + dx < 18 and 0 <= y + dy < 18) or op[x + dx, y + dy][3] == 0 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for x, y in edge:
        r, g, b, _ = op[x, y]
        op[x, y] = (r // 2, g // 2, b // 2, 255)
    out.save(TEX / "mob_effect" / "drunk.png")


def mast_band():
    wood = Image.open(TEX / "block" / "mast.png").convert("RGBA")
    iron = Image.open(TEX / "block" / "cannon_iron.png").convert("RGBA")
    out = wood.copy()
    op, ip = out.load(), iron.load()
    for y in (0, 1, 14, 15):
        for x in range(16):
            r, g, b, a = ip[x, y]
            if y in (1, 14) and x % 5 == 2:
                r, g, b = min(255, r + 60), min(255, g + 60), min(255, b + 60)   # rivet heads
            op[x, y] = (r, g, b, 255)
    for x in range(16):   # the band's shadow on the wood
        for y in (2, 13):
            r, g, b, a = op[x, y]
            op[x, y] = (int(r * 0.75), int(g * 0.75), int(b * 0.75), a)
    out.save(TEX / "block" / "mast_band.png")


def rope_ladder():
    shape = Image.open(TEX / "block" / "rope_ladder.png").convert("RGBA")
    rope = Image.open(TEX / "block" / "rope.png").convert("RGBA")
    spoke = Image.open(TEX / "block" / "helm_spoke.png").convert("RGBA")
    out = shape.copy()
    op, sp, rp, kp = out.load(), shape.load(), rope.load(), spoke.load()
    for y in range(16):
        for x in range(16):
            if sp[x, y][3] == 0:
                continue
            rail = x <= 2 or x >= 13
            r, g, b, _ = (rp if rail else kp)[x, y]
            op[x, y] = (r, g, b, 255)
    out.save(TEX / "block" / "rope_ladder.png")


def main():
    chart_paper()
    grog()
    drunk_icon()
    mast_band()
    rope_ladder()
    print("small textures: chart paper, grog, drunk icon, mast band, rope ladder")


if __name__ == "__main__":
    main()
