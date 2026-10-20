"""Generates the main item icons (and the mermaid's concept) with fal, one image each (fal-ai/nano-banana-2, about
$0.08 an image). Sources and fal_manifest.jsonl go to art/items (kept out of the jar); tools/make_items.py turns them
into the 16x16 icons and derives the variants (cursed shot, bucket contents, armed rowboats) locally.

python tools/gen_fal_items.py [name ...]   (only missing ones unless named; needs FAL_KEY)
"""
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "art" / "items"
STYLE = ("16-bit pixel art game item icon in the style of Minecraft inventory items, one single object, centered, "
         "crisp dark outline, limited palette, bold readable silhouette at small size, plain flat pure white background, "
         "no shadow, no text, no border")

ITEMS = {
    "cannonball": "a round black cast iron cannonball with a small highlight",
    "chain_shot": "chain shot: two small iron cannonballs joined by a short iron chain",
    "firebomb": "a round glass bottle firebomb full of orange fire, a cloth fuse burning at the neck",
    "banana": "a bunch of three ripe yellow bananas",
    "grog": "a small round green glass bottle of brown grog with a cork",
    "ship_bucket": "an empty wooden bucket with iron bands and a rope handle, seen slightly from above",
    "rowboat": "a small wooden rowboat with two oars, seen from the side",
    "charred_food": "a burnt charred piece of meat, black and smoking",
    "sloop_deed": "a rolled-open parchment ship deed with a red wax seal and a small drawing of a one-masted sailing ship",
    "brigantine_deed": "a rolled-open parchment ship deed with a red wax seal and a small drawing of a two-masted sailing ship",
    "galleon_deed": "a rolled-open parchment ship deed with a red wax seal and a small drawing of a three-masted galleon",
}
MERMAID = ("concept art character sheet of a friendly sea mermaid for a blocky voxel game like Minecraft: front view "
           "and back view side by side, square blocky head with long red hair, light skin, a seashell top, a teal "
           "scaled fish tail ending in a pale fin, simple flat colors, plain white background, no text")


def gen(name, prompt):
    subprocess.run(["um", "fal", "image", prompt, "--out", str(OUT), "--name", name, "--aspect", "1:1", "--res", "1K"],
                   check=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    want = sys.argv[1:]
    jobs = {n: f"{p}, {STYLE}" for n, p in ITEMS.items()}
    jobs["mermaid_concept"] = MERMAID
    for name, prompt in jobs.items():
        if want and name not in want:
            continue
        if not want and any(OUT.glob(f"{name}*.png")):
            continue
        gen(name, prompt)


if __name__ == "__main__":
    main()
