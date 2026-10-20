"""Generates the ship textures with fal, then turns each into a 16x16 Minecraft-style texture.

python tools/gen_fal_textures.py            # generate missing sources, then convert all
python tools/gen_fal_textures.py --convert  # only re-run the 16x16 conversion

Sources (fal "square" size) and fal_manifest.jsonl go to art/fal/ (kept out of the jar). Needs FAL_KEY.
Derived textures (flooded planks, holes, iron bands...) are made from the generated ones.
"""
import pathlib
import subprocess
import sys

from PIL import Image, ImageEnhance, ImageOps

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "art" / "fal"
OUT = ROOT / "src" / "main" / "resources" / "assets" / "seafarers" / "textures"

STYLE = ("seamless painterly stylized game texture, warm saturated colours, large simple shapes, "
         "flat orthographic view straight on, evenly lit, no shadows, no symbols, no icons, no pictures, no text, no border")

# name: (prompt, colours in the 16x16 palette)
TEXTURES = {
    "hull_planks": ("four horizontal weathered warm brown ship hull planks running left to right, dark seams between "
                    "boards, a few iron nail heads", 12),
    "hull_bottom": ("four horizontal tarred dark ship hull planks below the waterline, nearly black brown with a green "
                    "algae tinge, dark seams between boards", 10),
    "hull_trim": ("ship gunwale trim, two horizontal dark mahogany boards with a thin gold painted stripe along the top", 12),
    "hull_trim_top": ("plain dark mahogany wood, straight horizontal grain", 8),
    "deck_planks": ("ship deck seen from above, four vertical light worn honey coloured planks running top to bottom, "
                    "dark caulked seams between planks, scuffed", 12),
    "keel": ("a thick dark oak ship timber seen from the side, vertical wood grain, iron strap", 10),
    "keel_end": ("end grain of a thick square oak ship timber, growth rings, dark bark edge", 10),
    "mast": ("a tall ship mast wood seen from the side, straight vertical pale pine grain, light brown", 10),
    "railing": ("dark polished mahogany wood with vertical grain", 8),
    "sail_cloth": ("plain off-white cream canvas sail cloth, coarse linen weave, one faint vertical stitched seam, "
                   "slightly weathered, completely plain", 8),
    "rope": ("thick braided hemp ship rope coiled tightly, diagonal twisted strands, tan", 8),
    "brass": ("polished brass metal with warm gold highlights, slight wear", 8),
    "cannon_iron": ("plain dark cast iron surface, gunmetal grey, subtle mottling and a few rust specks", 6),
    "carriage": ("dark red-brown painted wood of a cannon carriage, horizontal grain, iron bolt heads", 10),
    "helm_wood": ("plain dark varnished walnut wood, fine straight vertical grain", 8),
    "helm_spoke": ("plain light oak wood, fine straight vertical grain", 8),
    "barrel_side": ("side of a wooden barrel, vertical oak staves with dark seams", 10),
    "barrel_top": ("plain oak boards seen from above, four parallel planks, simple wood grain", 8),
    "map": ("old pirate treasure map on parchment, sea and a small island, dotted path, no writing, seen from above", 14),
    "mattress": ("plain striped mattress ticking fabric, vertical beige and faded blue stripes", 6),
    "figurehead": ("cream painted carved wood with thin gold leaf lines, simple", 8),
    "flag": ("tattered red pirate pennant flag cloth, crimson with darker frayed edges", 8),
    "hull_gilt": ("ship trim board painted warm ochre gold, worn gilded paint over wood grain, horizontal", 8),
    "banana": ("bunch of ripe yellow bananas skin texture, bright yellow with brown flecks", 8),
}


def generate(name, prompt):
    out = SRC / f"{name}.png"
    if out.exists():
        return
    print(f"generating {name}")
    subprocess.run(["um", "fal", "texture", f"{prompt}, {STYLE}", "--out", str(SRC), "--name", name, "--size", "square"],
                   check=True)


# colour grading per texture (saturation, brightness): fal's woods come out orange; this pulls them toward
# weathered ship timber
GRADE = {
    "hull_planks": (0.72, 0.82), "hull_bottom": (0.8, 0.95), "hull_trim": (0.8, 0.85), "hull_trim_top": (0.75, 0.85),
    "deck_planks": (0.62, 0.92), "mast": (0.7, 0.9), "railing": (0.75, 0.85), "keel": (0.8, 0.9),
    "helm_wood": (0.8, 0.9), "helm_spoke": (0.75, 0.95), "barrel_side": (0.75, 0.9), "barrel_top": (0.7, 0.92),
    "carriage": (0.8, 0.85),
}


def to16(img, colours):
    """Average down to 16x16, then snap to a small palette with a little extra contrast."""
    img = img.convert("RGB")
    small = img.resize((16, 16), Image.Resampling.BOX)
    small = ImageEnhance.Contrast(small).enhance(1.15)
    small = ImageEnhance.Color(small).enhance(1.1)
    # very plain sources (canvas, iron) come out flat; give them a little Minecraft-style pixel noise
    from PIL import ImageStat
    import random
    if max(ImageStat.Stat(small).stddev) < 8:
        rng = random.Random(1)
        small = small.point(lambda v: v)
        px = small.load()
        for y in range(16):
            for x in range(16):
                k = 0.93 + 0.12 * rng.random()
                px[x, y] = tuple(max(0, min(255, int(c * k))) for c in px[x, y])
    return small.quantize(colors=colours, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGBA")


def darken(img, k):
    return Image.eval(img.convert("RGB"), lambda v: int(v * k)).convert("RGBA")


def hole(base, seed):
    """Punches a ragged cannonball hole through planks, with splintered pale edges."""
    import random
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


def main():
    SRC.mkdir(parents=True, exist_ok=True)
    if "--convert" not in sys.argv:
        for name, (prompt, _) in TEXTURES.items():
            generate(name, prompt)

    block = OUT / "block"
    block.mkdir(parents=True, exist_ok=True)
    made = {}
    for name, (_, colours) in TEXTURES.items():
        src = SRC / f"{name}.png"
        if not src.exists():
            print(f"missing {src}")
            continue
        img = Image.open(src).convert("RGB")
        sat, bright = GRADE.get(name, (1.0, 1.0))
        img = ImageEnhance.Brightness(ImageEnhance.Color(img).enhance(sat)).enhance(bright)
        made[name] = to16(img, colours)
        made[name].save(block / f"{name}.png")

    # derived textures
    if "hull_planks" in made:
        darken(made["hull_planks"], 0.6).save(block / "hull_planks_flooded.png")
        hole(made["hull_planks"], 3).save(block / "hull_hole.png")
    if "hull_bottom" in made:
        darken(made["hull_bottom"], 0.65).save(block / "hull_bottom_flooded.png")
        hole(made["hull_bottom"], 5).save(block / "hull_hole_bottom.png")
    if "cannon_iron" in made and "mast" in made:
        band = made["mast"].copy()
        iron = made["cannon_iron"]
        for y in (0, 1, 14, 15):
            for x in range(16):
                band.putpixel((x, y), iron.getpixel((x, y)))
        band.save(block / "mast_band.png")
    print(f"wrote {len(made)} textures to {block}")


if __name__ == "__main__":
    main()
