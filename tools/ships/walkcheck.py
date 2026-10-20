"""Can the crew get around? A walkability check for ship layouts.

python tools/ships/walkcheck.py [sloop]

Rebuilds each cell's collision from the layout (the hull's quarter-block voxel masks, the fittings' model shapes from
assets/seafarers/fitting_shapes.json, and simple boxes for vanilla blocks), finds every place a player can stand
(1.8 blocks of headroom over a floor, 0.6 wide), joins them up the way a player moves (walking, stepping up at most
0.6, stairs, ladders), and prints a plan of each deck:

  .  somewhere to stand, reachable          o  somewhere to stand, but you can't get there
  #  hull / solid                           letters: fittings (see the key)
  =  ladder / stairs                        ' '  outside the ship

Then it lists fittings nobody can get next to (to use them), and the narrowest points of each deck.
"""
import json
import pathlib
import sys
from collections import deque

ROOT = pathlib.Path(__file__).resolve().parents[2]
RES = ROOT / "src" / "main" / "resources"
N = 4  # voxels per block

FITTING_SHAPES = json.loads((RES / "assets" / "seafarers" / "fitting_shapes.json").read_text())
VOXEL_MASKS = [int(m) for m in json.loads((RES / "assets" / "seafarers" / "voxel_shapes.json").read_text())["masks"]]

# simple collision boxes (pixels, model space facing north) for blocks without a model-based shape
FULL = [[0, 0, 0, 16, 16, 16]]
BOXES = {
    "chest": [[1, 0, 1, 15, 14, 15]], "barrel": FULL, "deck_planks": FULL, "hull_planks": FULL, "hull_trim": FULL,
    "supply_barrel": [[2, 0, 2, 14, 15, 14]], "mast": [[3, 0, 3, 13, 16, 13]], "red_carpet": [[0, 0, 0, 16, 1, 16]],
    "spruce_trapdoor": [[0, 13, 0, 16, 16, 16]], "bowsprit": [[4, 0, 4, 12, 8, 12]], "sail_yard": [],
    "ship_lantern": [[5, 0, 5, 11, 9, 11]], "rope_ladder": [], "sail_rope": [], "brace_rope": [], "rowboat_hook": [],
    "flag": [], "ship_bell": [], "water_barrel": [[1, 0, 1, 15, 15, 15]],
    # the brig's bars and its (shut) door: walls
    "iron_bars": FULL, "brig_door": FULL, "oak_planks": FULL, "spruce_planks": FULL,
}
KEY = {"helm": "W", "anchor": "A", "cannon": "C", "harpoon": "H", "map_table": "M", "voyage_table": "V", "chest": "c",
       "barrel": "b", "supply_barrel": "s", "bucket_rack": "k", "mattress": "B", "stove": "S", "flag_box": "F",
       "sail_rope": "r", "brace_rope": "r", "mast": "|", "binnacle": "N", "red_carpet": "_", "deck_stairs": "=",
       "rope_ladder": "=", "bowsprit": "/", "oars": "O", "ship_lantern": "l", "spruce_trapdoor": "t",
       "sea_chest": "c", "cask": "b", "ship_bell": "!", "water_barrel": "w", "iron_bars": "#", "brig_door": "D"}
USABLE = {"helm", "anchor", "cannon", "harpoon", "map_table", "voyage_table", "chest", "barrel", "supply_barrel",
          "bucket_rack", "mattress", "stove", "flag_box", "sail_rope", "brace_rope", "oars", "sea_chest", "cask", "ship_bell", "water_barrel"}


def block_id(state):
    return state.split("[")[0].split(":")[1]


def prop(state, key):
    if "[" not in state:
        return None
    for kv in state.split("[")[1].rstrip("]").split(","):
        k, _, v = kv.partition("=")
        if k == key:
            return v
    return None


# facing tokens, for a ship launched bow north: layout +z is world north (-z), layout +x is world east
TOKEN_TO_WORLD = {"{bow}": "north", "{stern}": "south", "{starboard}": "east", "{port}": "west"}
TURNS = {"north": 0, "east": 1, "south": 2, "west": 3}


def turn(box, facing):
    x0, y0, z0, x1, y1, z1 = box
    for _ in range(TURNS.get(facing, 0)):
        x0, x1, z0, z1 = 16 - z1, 16 - z0, x0, x1
    return [x0, y0, z0, x1, y1, z1]


# where in a cell a player might stand: a grid of spots, middle first
SPOTS = sorted({(0.3 + 0.1 * i, 0.3 + 0.1 * k) for i in range(5) for k in range(5)}, key=lambda p: abs(p[0] - 0.5) + abs(p[1] - 0.5))

# blocks you walk straight through (no collision in the game)
NO_COLLISION = {"sail_rope", "brace_rope", "rowboat_hook", "ship_bell", "flag", "rope_ladder", "sail_yard"}


def cell_boxes(state):
    """Collision boxes of a cell in layout units (x across from the cell's port side, y up, z toward the bow; 0..1)."""
    bid = block_id(state)
    if bid in NO_COLLISION:
        return []
    if bid == "voxel_hull":
        mask = VOXEL_MASKS[int(prop(state, "shape"))]
        out = []
        for c in range(N):
            for b in range(N):
                for a in range(N):
                    if mask >> (a + N * (b + N * c)) & 1:
                        out.append((a / N, b / N, c / N, (a + 1) / N, (b + 1) / N, (c + 1) / N))
        return out
    if bid == "deck_stairs":
        # a step: the lower half everywhere, the upper half on the side it climbs toward
        facing = TOKEN_TO_WORLD.get(prop(state, "facing"), "north")
        boxes = [[0, 0, 0, 16, 8, 16], turn([0, 8, 0, 16, 16, 8], facing)]
    elif f"{bid}#{prop(state, 'part') or 0}" in FITTING_SHAPES:
        facing = TOKEN_TO_WORLD.get(prop(state, "facing"), "north")
        boxes = [turn(b, facing) for b in FITTING_SHAPES[f"{bid}#{prop(state, 'part') or 0}"]]
    elif bid in FITTING_SHAPES:
        facing = TOKEN_TO_WORLD.get(prop(state, "facing"), "north")
        boxes = [turn(b, facing) for b in FITTING_SHAPES[bid]]
    else:
        boxes = BOXES.get(bid, FULL)
        if bid == "ship_lantern" and prop(state, "deck") == "true":
            boxes = boxes + [[0, 12, 0, 16, 16, 16]]
        if bid == "mast" and prop(state, "deck") == "true":
            boxes = boxes + [[0, 12, 0, 16, 16, 16]]
    # model space (z grows south) to layout space (z grows toward the bow = north)
    return [(b[0] / 16, b[1] / 16, 1 - b[5] / 16, b[3] / 16, b[4] / 16, 1 - b[2] / 16) for b in boxes]


def load(name):
    spec = json.loads((RES / "data" / "seafarers" / "ships" / f"{name}.json").read_text())
    cells = {(c[0], c[1], c[2]): c[3] for c in spec["cells"]}
    return spec, cells


def analyse(name, quiet=False):
    spec, cells = load(name)
    boxes = {pos: cell_boxes(st) for pos, st in cells.items()}
    xs = [p[0] for p in cells]; ys = [p[1] for p in cells]; zs = [p[2] for p in cells]
    PLAYER = 0.3  # half width

    def solids_near(x, y, z):
        out = []
        for dy in (-1, 0, 1, 2):
            for b in boxes.get((x, y + dy, z), ()):
                out.append((b[0], b[1] + dy, b[2], b[3], b[4] + dy, b[5]))
        return out

    # a stand point per cell: the player's feet at the top of whatever is under the cell's middle
    stand = {}
    for x in range(min(xs), max(xs) + 1):
        for z in range(min(zs), max(zs) + 1):
            for y in range(min(ys), max(ys) + 2):
                near = solids_near(x, y, z)
                # a player can stand anywhere in the cell, not just its middle: try the middle and four spots off it
                for ox, oz in SPOTS:
                    cx0, cx1, cz0, cz1 = ox - PLAYER, ox + PLAYER, oz - PLAYER, oz + PLAYER
                    under = [b for b in near if b[0] < cx1 and b[3] > cx0 and b[2] < cz1 and b[5] > cz0 and -0.6 < b[4] <= 0.6]
                    if not under:
                        continue
                    floor = max(b[4] for b in under)
                    body = [b for b in near if b[0] < cx1 and b[3] > cx0 and b[2] < cz1 and b[5] > cz0
                            and b[1] < floor + 1.8 - 1e-6 and b[4] > floor + 1e-6]
                    if not body:
                        stand[(x, y, z)] = y + floor
                        break
    # climbing: a player on a ladder is in the ladder's own cell, at any height up it
    climb = {}
    for p, st in cells.items():
        if block_id(st) != "rope_ladder":
            continue
        near = solids_near(*p)
        for ox, oz in SPOTS:
            if not any(b[0] < ox + PLAYER and b[3] > ox - PLAYER and b[2] < oz + PLAYER and b[5] > oz - PLAYER
                       and b[1] < 1.8 and b[4] > 0 for b in near):
                climb[p] = p[1]
                break
    # the top of a ladder: you can stand on its top rung with your feet at the next floor up
    for p in list(climb):
        top = (p[0], p[1] + 1, p[2])
        if top not in climb and block_id(cells.get(top, "x:air")) != "rope_ladder":
            near = solids_near(*top)
            if any(not any(b[0] < ox + PLAYER and b[3] > ox - PLAYER and b[2] < oz + PLAYER and b[5] > oz - PLAYER
                           and b[1] < 1.8 and b[4] > 0 for b in near) for ox, oz in SPOTS):
                climb[top] = top[1]
    for p, h in climb.items():
        stand.setdefault(p, h)

    def through(p, q, h):
        """A player (feet at world height h) fits through the face between cells p and q somewhere along it."""
        x, y, z = p
        dx, dz = q[0] - x, q[2] - z
        near = solids_near(x, y, z) + [(b[0] + dx, b[1] + (q[1] - y), b[2] + dz, b[3] + dx, b[4] + (q[1] - y), b[5] + dz)
                                        for b in solids_near(*q)]
        for along in (0.5, 0.4, 0.6, 0.3, 0.7):
            cx = 0.5 + dx * 0.5 if dx else along
            cz = 0.5 + dz * 0.5 if dz else along
            foot = [b for b in near if b[0] < cx + PLAYER and b[3] > cx - PLAYER and b[2] < cz + PLAYER and b[5] > cz - PLAYER]
            # the floor right at the crossing (a stair's lower step, a deck edge), no higher than the higher side
            under = [b[4] for b in foot if b[4] <= h - y + 1e-6]
            feet = max(under) if under else h - y
            if not any(b[1] < feet + 1.8 - 1e-6 and b[4] > feet + 0.6 for b in foot):
                return True
        return False

    def neighbours(p):
        x, y, z = p
        h = stand[p]
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (-1, 0, 1):
                q = (x + dx, y + dy, z + dz)
                if q not in stand:
                    continue
                rise = stand[q] - h
                # walk, step up 0.6, drop up to 1.5; on or off a ladder a whole block up or down is fine
                if (-1.5 <= rise <= 0.6 or (p in climb or q in climb) and abs(rise) <= 1.0) and through(p, q, max(h, stand[q])):
                    yield q
        # diagonal steps (round the mast, across a corner): a player has to fit at the corner the cells share
        for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
            for dy in (-1, 0, 1):
                q = (x + dx, y + dy, z + dz)
                if q not in stand or not -1.0 <= stand[q] - h <= 0.6:
                    continue
                feet = max(h, stand[q]) - y
                cx, cz = 0.5 + dx * 0.5, 0.5 + dz * 0.5
                near = []
                for ax, az in ((0, 0), (dx, 0), (0, dz), (dx, dz)):
                    for ddy in (0, q[1] - y):
                        near += [(b[0] + ax, b[1] + ddy, b[2] + az, b[3] + ax, b[4] + ddy, b[5] + az)
                                 for b in solids_near(x + ax, y + ddy, z + az)]
                if not any(b[0] < cx + PLAYER and b[3] > cx - PLAYER and b[2] < cz + PLAYER and b[5] > cz - PLAYER
                           and b[1] < feet + 1.8 - 1e-6 and b[4] > feet + 0.6 for b in near):
                    yield q
        if p in climb:
            for dy in (-1, 1):
                q = (x, y + dy, z)
                if q in stand:
                    yield q

    # start where crew come aboard: the cell inboard of the top of each side ladder, else the helm's
    # start beside the helm (or a rowboat's oars), where the crew are when they're sailing her
    def at_station(q):
        return any(cells.get((q[0] + dx, q[1], q[2] + dz), "").startswith(("seafarers:helm", "seafarers:oars"))
                   for dx, dz in ((0, 1), (0, -1), (1, 0), (-1, 0)))
    starts = [p for p in stand if at_station(p)]
    seen = set(starts)
    todo = deque(starts)
    while todo:
        p = todo.popleft()
        for q in list(neighbours(p)):
            if q not in seen:
                seen.add(q)
                todo.append(q)
    unreachable = []
    for pos, st in sorted(cells.items()):
        bid = block_id(st)
        if bid in USABLE:
            x, y, z = pos
            if not any((x + dx, y + dy, z + dz) in seen for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (0, 0)) for dy in (-1, 0, 1)):
                unreachable.append(f"{bid} at {x},{y},{z}")

    decks = sorted({p[1] for p in seen})
    report = []
    for y in decks:
        rows = []
        tight = 0
        for z in range(max(zs), min(zs) - 1, -1):
            row = ""
            for x in range(min(xs), max(xs) + 1):
                p = (x, y, z)
                st = cells.get(p)
                bid = block_id(st) if st else None
                if p in seen:
                    ch = "."
                    free = sum((x + dx, y, z + dz) in seen for dx, dz in ((1, 0), (-1, 0)))
                    if free == 0 and (x, y, z + 1) in seen and (x, y, z - 1) in seen:
                        tight += 1
                elif p in stand:
                    ch = "o"
                elif bid in KEY:
                    ch = KEY[bid]
                elif bid:
                    ch = "#"
                else:
                    ch = " "
                row += ch
            rows.append(f"  z{z:3d} {row}")
        n = sum(1 for p in seen if p[1] == y)
        report.append((y, n, tight, rows))
    if not quiet:
        print(f"{name}: {len(seen)} places to stand reachable, {len(stand) - len(seen)} cut off")
        for y, n, tight, rows in report:
            print(f"\n deck y={y}: {n} cells, {tight} one-wide (single file) cells   bow ^, port <  > starboard")
            print("\n".join(rows))
        print("\nfittings nobody can get to:", unreachable or "none")
    return {"reachable": len(seen), "cut_off": len(stand) - len(seen), "unreachable": unreachable,
            "decks": {y: (n, tight) for y, n, tight, _ in report}}


if __name__ == "__main__":
    analyse(sys.argv[1] if len(sys.argv) > 1 else "sloop")
