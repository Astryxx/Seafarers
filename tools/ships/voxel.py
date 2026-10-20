"""Voxel hulls: a ship's hull described as smooth functions, filled with 4-pixel voxels (4 per block edge).

Every block cell the hull passes through becomes a `seafarers:voxel_hull` block whose `shape` property picks
one entry of a shared palette. A palette entry is the cell's 4x4x4 voxels with a material per voxel. For each
entry we write a block model (merged boxes, real textures) and a hole variant, and the Java side gets the
collision masks from assets/seafarers/voxel_shapes.json.

Frames: ship coordinates as in shiplib (x starboard, y up, z toward the bow). A cell (x, y, z) spans
X in [x-0.5, x+0.5], Y in [y, y+1], Z in [z, z+1]. Voxel (a, b, c) of a cell sits at
X = x-0.5+(a+0.5)/4, Y = y+(b+0.5)/4, Z = z+(c+0.5)/4. Models are drawn with the bow toward north (-z).
"""
import json
import math
import pathlib

RES = pathlib.Path(__file__).resolve().parents[2] / "src" / "main" / "resources"
N = 4
V = 1.0 / N

MATERIALS = {  # material id -> texture
    "bottom": "seafarers:block/hull_bottom",
    "planks": "seafarers:block/hull_planks",
    "trim": "seafarers:block/hull_trim",
    "deck": "seafarers:block/deck_planks",
    "keel": "seafarers:block/keel",
    "gilt": "seafarers:block/hull_gilt",
    "canvas": "seafarers:block/sail_cloth",
    "post": "seafarers:block/mast",
}
HOLE_TEXTURE = {"bottom": "seafarers:block/hull_hole_bottom", "keel": "seafarers:block/hull_hole_bottom"}
HOLE_SIZES = {1: "_small", 2: ""}   # hole=1 small, hole=2 large (texture suffix)


class VoxelGrid:
    def __init__(self):
        self.vox = {}  # (cell x, y, z) -> {(a, b, c): material}

    @staticmethod
    def centre(x, y, z, a, b, c):
        return x - 0.5 + (a + 0.5) * V, y + (b + 0.5) * V, z + (c + 0.5) * V

    def set(self, X, Y, Z, material):
        """Sets the voxel containing point (X, Y, Z); None clears it."""
        x, y, z = math.floor(X + 0.5), math.floor(Y), math.floor(Z)
        a = min(N - 1, int((X - (x - 0.5)) * N))
        b = min(N - 1, int((Y - y) * N))
        c = min(N - 1, int((Z - z) * N))
        cell = self.vox.setdefault((x, y, z), {})
        if material is None:
            cell.pop((a, b, c), None)
        else:
            cell[(a, b, c)] = material

    def fill(self, inside, material_of, bounds):
        """Voxelises a region: inside(X,Y,Z) -> bool, material_of(X,Y,Z) -> str or None."""
        (x0, x1), (y0, y1), (z0, z1) = bounds
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    for a in range(N):
                        for b in range(N):
                            for c in range(N):
                                X, Y, Z = self.centre(x, y, z, a, b, c)
                                if inside(X, Y, Z):
                                    m = material_of(X, Y, Z)
                                    if m:
                                        self.vox.setdefault((x, y, z), {})[(a, b, c)] = m

    def carve(self, test):
        """Removes every voxel whose centre satisfies test(X, Y, Z)."""
        for (x, y, z), cell in self.vox.items():
            for (a, b, c) in list(cell):
                if test(*self.centre(x, y, z, a, b, c)):
                    del cell[(a, b, c)]

    def cells(self):
        return {k: v for k, v in self.vox.items() if v}


# ---------------------------------------------------------------- palette, models, shapes

def key_of(cell):
    return tuple(sorted(cell.items()))


def boxes(cell):
    """Greedy-merges voxels of the same material into boxes: [(a0,b0,c0,a1,b1,c1,material)] (exclusive ends)."""
    left = dict(cell)
    out = []
    for (a, b, c) in sorted(cell):
        if (a, b, c) not in left:
            continue
        m = left[(a, b, c)]
        a1 = a + 1
        while (a1, b, c) in left and left[(a1, b, c)] == m:
            a1 += 1
        c1 = c + 1
        while all(left.get((i, b, c1)) == m for i in range(a, a1)):
            c1 += 1
        b1 = b + 1
        while all(left.get((i, b1, k)) == m for i in range(a, a1) for k in range(c, c1)):
            b1 += 1
        for i in range(a, a1):
            for j in range(b, b1):
                for k in range(c, c1):
                    left.pop((i, j, k), None)
        out.append((a, b, c, a1, b1, c1, m))
    return out


# materials the hull livery paints (tint 1); the rest only darken when she floods (tint 0)
PAINTED = {"planks", "trim"}


def element(box, hole):
    """hole: 0 none, 1 small, 2 large."""
    a0, b0, c0, a1, b1, c1, m = box
    px = 16 // N
    # ship +z (bow) is model north (-z)
    frm = [a0 * px, b0 * px, 16 - c1 * px]
    to = [a1 * px, b1 * px, 16 - c0 * px]
    tex = ("#h%d_" % hole) + m if hole else "#" + m
    faces = {}
    for d in ("north", "south", "east", "west", "up", "down"):
        f = {"texture": tex, "tintindex": 1 if m in PAINTED else 0}
        on_edge = {"west": frm[0] == 0, "east": to[0] == 16, "down": frm[1] == 0, "up": to[1] == 16,
                   "north": frm[2] == 0, "south": to[2] == 16}[d]
        if on_edge:
            f["cullface"] = d
        faces[d] = f
    return {"from": frm, "to": to, "faces": faces}


def write_palette(keys, solid_volume):
    """keys: list of palette entries (tuples of ((a,b,c), material)). solid_volume: set of ids counted as buoyant hull."""
    models = RES / "assets" / "seafarers" / "models" / "block" / "voxel"
    models.mkdir(parents=True, exist_ok=True)
    for old in models.glob("*.json"):
        old.unlink()
    variants = {}
    masks = []
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    for i, key in enumerate(keys):
        cell = dict(key)
        bx = boxes(cell)
        used = sorted({m for (_, _, _, _, _, _, m) in bx})
        textures = {m: MATERIALS[m] for m in used}
        for size in (1, 2):
            textures.update({("h%d_" % size) + m: HOLE_TEXTURE.get(m, "seafarers:block/hull_hole") + HOLE_SIZES[size]
                             for m in used})
        textures["particle"] = MATERIALS[used[0]]
        for hole in (0, 1, 2):
            name = f"v{i}" + ("", "_hole_small", "_hole")[hole]
            model = {"parent": "minecraft:block/block", "textures": textures,
                     "elements": [element(b, hole) for b in bx]}
            if hole:
                model["render_type"] = "minecraft:cutout"
            (models / f"{name}.json").write_text(json.dumps(model, separators=(",", ":")))
            for facing, y in rot.items():
                v = {"model": f"seafarers:block/voxel/{name}"}
                if y:
                    v["y"] = y
                variants[f"facing={facing},hole={hole},shape={i}"] = v
        mask = 0
        for (a, b, c) in cell:
            mask |= 1 << (a + N * (b + N * c))
        masks.append(str(mask))
    bs = RES / "assets" / "seafarers" / "blockstates" / "voxel_hull.json"
    bs.write_text(json.dumps({"variants": variants}, separators=(",", ":")))
    # masks in ship frame: bit a + 4*(b + 4*c), a = starboard, b = up, c = bow
    shapes = RES / "assets" / "seafarers" / "voxel_shapes.json"
    shapes.write_text(json.dumps({"size": N, "masks": masks}))

    # physics: buoyant hull cells count as full (they stand in for the air in the hull), the rest by fill
    overrides = {}
    for i, key in enumerate(keys):
        fill = len(key) / N ** 3
        keel = any(m == "keel" for _, m in key)
        mass = 0.04 + 0.1 * fill + (0.8 if keel else 0)
        volume = 0.4 if i in solid_volume else round(fill, 3)
        overrides[f"shape={i}"] = {"sable:mass": round(mass, 3), "sable:volume": volume}
    props = RES / "data" / "seafarers" / "physics_block_properties" / "voxel_hull.json"
    props.write_text(json.dumps({"selector": "seafarers:voxel_hull", "properties": {"sable:mass": 0.1},
                                 "overrides": overrides}, indent=1))
    print(f"voxel palette: {len(keys)} shapes, {sum(len(boxes(dict(k))) for k in keys)} boxes")
