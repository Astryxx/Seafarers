"""Checks that a ship's fittings sit properly in and on its voxel hull.

Problems it reports:
- HOLE: a fitting replaced hull voxels (skin, rails, decks) in its cell and nothing makes up for them
- FLOAT: a standing fitting has no deck or block under its middle
- HANG: a hanging lantern has nothing above it to hang from
- OUTSIDE: a fitting meant to be inside the hull pokes out through the side
- LADDER: a side ladder is not against the hull at the top
Used by the ship scripts: build() calls check(ship, popped, half_width) and prints the report.
"""

STAND = {"helm", "anchor", "bucket_rack", "sail_rope", "brace_rope", "cannon", "harpoon", "supply_barrel", "map_table",
         "voyage_table", "binnacle", "mattress", "red_carpet", "chest", "barrel", "smoker", "bowsprit",
         "oars", "rowboat_hook", "stove", "flag_box", "sea_chest", "cask", "water_barrel"}
FULL_BELOW = {"deck_planks", "deck_stairs", "barrel", "chest", "supply_barrel", "smoker", "mast"}
# fittings allowed to stand where the deck was cut away on purpose (hatches, the grating, the door step)
MAY_REPLACE_DECK = {"deck_stairs", "deck_slab", "spruce_trapdoor", "mast", "ship_lantern"}


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


def top_layer_centre(cell):
    return cell and all((a, 3, c) in cell for a in (1, 2) for c in (1, 2))


def bottom_layer_centre(cell):
    return cell and all((a, 0, c) in cell for a in (1, 2) for c in (1, 2))


def check(ship, voxels, popped, half_width, inside_ids, outside_ok):
    """
    ship: Ship with .cells (pos -> state); voxels: remaining hull cells (pos -> {(a,b,c): material});
    popped: pos -> voxels a fitting replaced; inside_ids: fitting ids that must stay inside the hull;
    outside_ok: positions allowed outside (ladders and the like).
    """
    problems = []
    for (x, y, z), state in sorted(ship.cells.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
        bid = block_id(state)
        if bid == "voxel_hull":
            continue
        lost = popped.get((x, y, z), {})
        structural = {k: m for k, m in lost.items() if m != "deck" or k[1] != 3}
        deck_plate = {k for k, m in lost.items() if m == "deck" and k[1] == 3}
        if structural and bid != "mast" and (x, y, z) not in outside_ok:
            problems.append(f"HOLE    {bid:16s} at {x},{y},{z} removed {len(structural)} hull voxels "
                            f"({sorted(set(structural.values()))})")
        if deck_plate and bid not in MAY_REPLACE_DECK and (x, y, z) not in outside_ok:
            problems.append(f"HOLE    {bid:16s} at {x},{y},{z} cut the deck plate")
        if deck_plate and bid in ("mast", "ship_lantern") and prop(state, "deck") != "true":
            problems.append(f"HOLE    {bid:16s} at {x},{y},{z} cut the deck plate (use deck=true)")

        below = (x, y - 1, z)
        if bid in STAND or (bid == "ship_lantern" and prop(state, "hanging") != "true") or (bid == "spar" and prop(state, "axis") == "y"):
            if bid == "deck_stairs" and below in ship.cells:
                pass
            elif not (top_layer_centre(voxels.get(below)) or block_id(ship.cells.get(below, "x:air")) in FULL_BELOW):
                problems.append(f"FLOAT   {bid:16s} at {x},{y},{z} has nothing under it")
        if bid == "ship_lantern" and prop(state, "hanging") == "true" and prop(state, "deck") != "true":
            if not bottom_layer_centre(voxels.get((x, y + 1, z))):
                problems.append(f"HANG    {bid:16s} at {x},{y},{z} has nothing to hang from")
        if bid in inside_ids and (x, y, z) not in outside_ok:
            w = half_width(z + 0.5, y + 0.05)
            if abs(x) + 0.45 > w:
                problems.append(f"OUTSIDE {bid:16s} at {x},{y},{z} (hull half-width there {w:.2f})")
        if bid == "rope_ladder" and abs(x) >= 3 and (x, y + 1, z) not in ship.cells and (x, y, z) not in outside_ok:
            side = 1 if x > 0 else -1
            hull = voxels.get((x - side, y, z), {})
            touching = any(k[0] == (3 if side > 0 else 0) for k in hull)
            if not touching:
                problems.append(f"LADDER  top rung at {x},{y},{z} is not against the hull")
    return problems
