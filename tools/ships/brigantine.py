"""The Brigantine, as a voxel ship, laid out like Sea of Thieves' Brigantine.

Two masts (the mainmast aft, with the crow's nest, and the foremast), a crew of three, four cannons on the main deck
(two a side), two harpoons in the bow, and a single deck below with everything else, the brig among it. Built the
same way as the Sloop (see sloop.py): a smooth hull filled with 4-pixel voxels, fittings in cells.

Ship coordinates: x across the beam (+ starboard), y up from the keel, z from the stern (0) toward the bow.

Levels (block y of each walking surface):
  lower deck 1, main deck 5 (3.75 blocks clear below), helm deck 6 (a step up at the stern).
Along the ship (z):
  0..7     helm deck: the wheel, the capstan behind it (her anchor goes over the stern, as the Sloop's)
  7..33    main deck: guns at z 8 and 16, mainmast at 11, foremast at 21, harpoons and bowsprit in the bow
Below: the captain's quarters under the helm deck (bunks, sea chests), stores along the sides, the brig to port
amidships, the galley forward to starboard, and the map table right forward under the bow.
"""
import math

from shiplib import Ship
from voxel import VoxelGrid

STERN, BOW = 0.0, 32.9
BEAM = 4.5                       # half-beam: the outer planking ends on a block boundary
HOLD_FLOOR, MAIN_DECK, HELM_DECK = 1.0, 5.0, 6.0
BILGE_TOP = 4.0                  # where the turn of the bilge meets the straight topsides
HELM_END = 7.0                   # the helm deck's forward edge
RAIL = 0.75
BOW_ROUND = 24.0

MAIN_Z, FORE_Z = 11, 21
MAIN_TOP, FORE_TOP = 33, 30
MAIN_YARD, FORE_YARD = 21, 20
NEST_Y = 25.0
NEST_R = 2.5
BOWSPRIT_HEEL = (0, 5, 30)
BOWSPRIT_RAISE = 14
GUNS_Z = (8, 16)
HARPOON_Z = 27
STAIRS_X, STAIRS_Z = 1, 13       # the stairs down: from the main deck at z 13 to the lower deck at z 16
BRIG = ((-3, 1, 17), (-2, 1, 18))  # inside corners of the brig (port side, amidships)
BRIG_DOOR = (-1, 1, 17)


def plan(z):
    """Half-beam at the deck line: a broad transom, full through the waist, a rounded bow."""
    if z < 8:
        return BEAM * (0.8 + 0.2 * (1 - ((8 - z) / 8) ** 2))
    if z > BOW_ROUND:
        t = (z - BOW_ROUND) / (BOW - BOW_ROUND)
        return BEAM * max(0.0, 1 - t ** 2.2)
    return BEAM


def keel(z):
    k = 0.25
    if z < 1.5:
        k += 0.5 * (1.5 - z)
    if z > 22:
        k += 5.4 * ((z - 22) / (BOW - 22)) ** 2.4
    return k


def sheer(z):
    if z < HELM_END:
        return HELM_DECK + RAIL
    h = MAIN_DECK + RAIL
    if z > 26:
        h += 1.0 * ((z - 26) / (BOW - 26)) ** 2
    return h


def half_width(z, y):
    if z < STERN or z > BOW:
        return -1.0
    k = keel(z)
    if y < k:
        return -1.0
    b = plan(z)
    if y >= BILGE_TOP:
        return b
    t = min(1.0, (y - k) / max(0.5, BILGE_TOP - k))
    return b * (0.7 + 0.3 * (1 - (1 - t) ** 3))   # a fuller bilge than the Sloop's: room for stores along the sides


def inside(x, y, z):
    return y <= sheer(z) and abs(x) <= half_width(z, y)


def build_hull():
    g = VoxelGrid()
    d = 0.25
    bounds = ((-6, 6), (-1, 9), (-1, 34))

    def body(x, y, z):
        return abs(x) <= half_width(z, y)

    def skin(x, y, z):
        if not inside(x, y, z):
            return False
        return not all(body(x + dx, y + dy, z + dz)
                       for dx, dy, dz in ((d, 0, 0), (-d, 0, 0), (0, -d, 0), (0, 0, d), (0, 0, -d)))

    def skin_material(x, y, z):
        top = sheer(z)
        if y < 1.0:
            return "bottom"
        if y >= top - 0.25:
            return "gilt"
        if 2.5 <= y < 2.75:
            return "gilt"
        if MAIN_DECK - 0.5 <= y < MAIN_DECK - 0.25:
            return "trim"
        if z < HELM_END and abs(z - (HELM_END - 0.12)) < 0.13 and y >= MAIN_DECK:
            return "gilt"
        return "planks"

    g.fill(skin, skin_material, bounds)
    g.fill(lambda x, y, z: sheer(z) - 0.25 <= y <= sheer(z) and inside(x, y, z) and abs(x) >= half_width(z, y) - 0.5,
           lambda *_: "gilt", bounds)

    def plate(y_top, z0, z1, material="deck"):
        g.fill(lambda x, y, z: y_top - d <= y <= y_top and z0 <= z <= z1 and inside(x, y, z),
               lambda *_: material, bounds)

    g.fill(lambda x, y, z: y <= HOLD_FLOOR and z >= 0.25 and inside(x, y, z), lambda *_: "deck", bounds)
    plate(MAIN_DECK, HELM_END, BOW)
    plate(HELM_DECK, 0.3, HELM_END)
    # the helm deck's front: a riser from the main deck up to it, and a rail along it either side of the steps
    g.fill(lambda x, y, z: HELM_END - d <= z <= HELM_END and MAIN_DECK - d <= y <= HELM_DECK and inside(x, y, z),
           lambda *_: "planks", bounds)
    g.fill(lambda x, y, z: HELM_END - d <= z <= HELM_END and HELM_DECK <= y <= HELM_DECK + RAIL and abs(x) > 1.5
           and inside(x, y, z), lambda x, y, z: "gilt" if y >= HELM_DECK + RAIL - 0.25 else "trim", bounds)
    g.fill(lambda x, y, z: abs(x) < 0.26 and 0.8 <= z <= BOW - 1.0 and keel(z) - 0.25 <= y < keel(z),
           lambda *_: "keel", bounds)

    # crow's nest on the mainmast
    cx, cz = 0.0, MAIN_Z + 0.5
    nest_bounds = ((-3, 3), (int(NEST_Y) - 1, int(NEST_Y) + 1), (MAIN_Z - 3, MAIN_Z + 3))
    g.fill(lambda x, y, z: NEST_Y - d <= y <= NEST_Y and math.hypot(x - cx, z - cz) <= NEST_R,
           lambda *_: "deck", nest_bounds)
    g.fill(lambda x, y, z: NEST_Y < y <= NEST_Y + 1.0 and NEST_R - 0.25 <= math.hypot(x - cx, z - cz) <= NEST_R,
           lambda x, y, z: "gilt" if y > NEST_Y + 0.75 else "trim", nest_bounds)

    # stern windows: a gilded frame of lights across the transom, under the helm deck
    g.fill(lambda x, y, z: z <= 0.55 and inside(x, y, z) and 3.25 <= y <= 4.85 and abs(x) <= 2.6
           and not (abs(x) <= 2.4 and 3.5 <= y <= 4.6), lambda *_: "gilt", bounds)

    def stem_front(y):
        lo, hi = 22.0, BOW
        for _ in range(30):
            mid = (lo + hi) / 2
            if keel(mid) < y:
                lo = mid
            else:
                hi = mid
        return lo

    g.fill(lambda x, y, z: 3.6 <= y <= 5.6 and abs(x) < 0.3 and stem_front(y) - 0.2 <= z <= stem_front(y) + 0.45,
           lambda *_: "gilt", bounds)
    g.fill(lambda x, y, z: 5.3 <= y <= 5.9 and abs(x) < 0.55 and stem_front(5.6) - 0.3 <= z <= stem_front(5.6) + 0.3,
           lambda *_: "gilt", bounds)

    # openings
    rise = math.tan(math.radians(BOWSPRIT_RAISE))
    g.carve(lambda x, y, z: z > BOWSPRIT_HEEL[2] + 0.5 and abs(x) < 0.4
            and abs(y - (BOWSPRIT_HEEL[1] + 0.25 + (z - BOWSPRIT_HEEL[2] - 0.5) * rise)) < 0.35)
    for gz in GUNS_Z:
        g.carve(lambda x, y, z, gz=gz: abs(x) > 3.2 and MAIN_DECK <= y <= MAIN_DECK + RAIL and gz - 0.1 <= z <= gz + 1.1)
    # stern windows' glass is open air (the frame stays)
    g.carve(lambda x, y, z: z <= 0.55 and abs(x) <= 2.4 and 3.5 <= y <= 4.6)
    # boarding ladders either side of the mainmast, and the stern ladder where a rowboat ties up
    g.carve(lambda x, y, z: abs(x) > 3.5 and MAIN_DECK <= y <= MAIN_DECK + RAIL + 0.1 and MAIN_Z <= z <= MAIN_Z + 1)
    g.carve(lambda x, y, z: 1.5 <= x <= 2.5 and HELM_DECK <= y <= HELM_DECK + RAIL + 0.1 and z <= 0.6)
    # the hatch over the stairs down
    g.carve(lambda x, y, z: STAIRS_X - 0.5 <= x <= STAIRS_X + 0.5 and MAIN_DECK - 0.3 <= y <= MAIN_DECK + 0.1
            and STAIRS_Z <= z <= STAIRS_Z + 4)
    # slits in the bow bulwarks for the harpoons to swing in
    g.carve(lambda x, y, z: abs(x) >= 2.6 and MAIN_DECK + 0.5 <= y <= MAIN_DECK + 2 and HARPOON_Z <= z <= HARPOON_Z + 2)
    return g


def build():
    s = Ship("brigantine", crew=3)
    s.masts = 2
    s.stats = {"maxSpeed": 11.0, "acceleration": 0.4, "maxYawRate": 0.10, "yawAccel": 0.06, "pivotRate": 0.6,
               "sailRun": 0.85, "sailBeam": 1.0, "sailClose": 0.5, "sailIrons": 0.12,
               "waterDrag": 3.8, "waterYawDrag": 14.0, "floodPerHole": 0.065, "bucket": 3.5, "mastHealth": 3,
               "capstanPegs": 3, "anchorWeight": 1.4, "wheelSpeed": 0.018, "wheelTurns": 0.75}
    cells = build_hull().cells()
    popped = {}

    def put(x, y, z, state, loot=None, data=None):
        lost = cells.pop((x, y, z), None)
        if lost:
            popped[(x, y, z)] = lost
        s.put(x, y, z, state, loot, data)

    world_facing = {"{bow}": 0, "{starboard}": 1, "{stern}": 2, "{port}": 3}

    def put_multi(x, y, z, block, facing, offsets, props="", loot=None):
        for i, (ex, up, south) in enumerate(offsets):
            dx, dz = ex, south
            for _ in range(world_facing[facing]):
                dx, dz = -dz, dx
            extra = f",{props}" if props else ""
            put(x + dx, y + up, z - dz, f"seafarers:{block}[facing={facing},part={i}{extra}]", loot if i == 0 else None)

    M, H = int(MAIN_DECK), int(HELM_DECK)

    # ---------------- helm deck ----------------
    put(0, H, 5, "seafarers:helm[facing={bow}]")
    put(-2, H, 5, "seafarers:ship_lantern")
    put(0, H, 2, "seafarers:anchor[facing={stern}]",
        data={"hawseForward": 2.65, "hawseDown": 2.6, "hawseSide": 0, "fairleadForward": 1.75, "fairleadUp": 0.82,
              "depth": 10, "pegs": 3})
    for x in (-1, 0, 1):
        put(x, M, int(HELM_END), "seafarers:deck_stairs[facing={stern}]")
    for y in range(2, H + 1):
        put(2, y, -1, "seafarers:rope_ladder[facing={stern}]")   # over the stern, for a rowboat

    # ---------------- main deck ----------------
    def mast(z, top, nest):
        for y in range(1, top + 1):
            deck = y in (M - 1,) or (nest and y == int(NEST_Y) - 1)
            put(0, y, z, f"seafarers:mast[deck={'true' if deck else 'false'},mast={0 if nest else 1}]")
        put(0, top + 1, z, "seafarers:flag[facing={bow}]")

    mast(MAIN_Z, MAIN_TOP, True)
    mast(FORE_Z, FORE_TOP, False)
    for y in range(M, int(NEST_Y)):
        put(0, y, MAIN_Z - 1, "seafarers:rope_ladder[facing={stern}]")
    put(0, MAIN_YARD, MAIN_Z + 1, "seafarers:sail_yard[facing={bow},mast=0]",
        data={"halfWidth": 6.0, "height": 11.0, "yardHalfLength": 6.7, "forward": 0.75,
              "shroudTop": NEST_Y - MAIN_YARD - 0.4, "shroudTopSpread": NEST_R - 0.15,
              "shroudDrop": MAIN_YARD - MAIN_DECK - RAIL + 0.5, "shroudSpread": BEAM - 0.25, "masthead": 0})
    put(0, FORE_YARD, FORE_Z + 1, "seafarers:sail_yard[facing={bow},mast=1]",
        data={"halfWidth": 5.5, "height": 10.0, "yardHalfLength": 6.2, "forward": 0.75,
              "shroudTop": FORE_TOP - FORE_YARD - 2.0, "shroudTopSpread": 0.35,
              "shroudDrop": FORE_YARD - MAIN_DECK - RAIL + 0.5, "shroudSpread": BEAM - 0.25,
              "masthead": FORE_TOP - FORE_YARD - 0.2})
    # each mast's ropes at its foot, against the bulwarks: length cleats aft of it, angle pulleys forward
    for m, mz in ((0, MAIN_Z), (1, FORE_Z)):
        for x, side in ((-3, "port"), (3, "starboard")):
            put(x, M, mz - 1, f"seafarers:sail_rope[facing={{{side}}},mast={m}]")
            put(x, M, mz + 1 if m == 1 else mz + 2, f"seafarers:brace_rope[facing={{{side}}},mast={m}]")
    put(1, int(NEST_Y) - 1, MAIN_Z, "seafarers:ship_lantern[hanging=true,deck=true]")
    put(0, M + 1, MAIN_Z + 1, "seafarers:ship_bell[facing={stern}]")
    put(0, int(NEST_Y) + 1, MAIN_Z + 1, "seafarers:ship_bell[facing={stern}]")
    put(-1, int(NEST_Y), MAIN_Z, "seafarers:flag_box[facing={port}]")
    for side, x in (("port", -5), ("starboard", 5)):
        for y in range(2, M + 1):
            put(x, y, MAIN_Z, f"seafarers:rope_ladder[facing={{{side}}}]")
    for gz in GUNS_Z:
        for x, side in ((-3, "port"), (3, "starboard")):
            put_multi(x, M, gz, "cannon", "{" + side + "}", [(0, 0, 0), (0, 0, -1)])
    for x in (-3, 3):
        put(x, M, HARPOON_Z, "seafarers:harpoon[facing={bow}]")
    for x in (-1, 1):
        put(x, M, HARPOON_Z + 2, "seafarers:ship_lantern")
    put(2, M, STAIRS_Z - 2, "seafarers:bucket_rack[facing={port}]")
    put(*BOWSPRIT_HEEL, "seafarers:bowsprit[facing={bow}]",
        data={"length": 11.0, "raise": BOWSPRIT_RAISE, "bobstayDown": 2.4, "bobstayForward": 0.55})
    hx, hy, hz = BOWSPRIT_HEEL
    r = math.radians(BOWSPRIT_RAISE)
    for z in range(hz + 1, hz + 12):
        t = (z + 0.5 - (hz + 0.5)) / math.cos(r)
        if t > 11.0 - 0.3:
            break
        top = hy + 0.25 + t * math.sin(r) + 0.17 / math.cos(r)
        y = math.floor(top - 1e-3)
        sixteenths = max(1, min(16, round((top - y) * 16)))
        if (hx, y, z) not in cells and (hx, y, z) not in s.cells:
            put(hx, y, z, f"seafarers:bowsprit_spar[facing={{bow}},top={sixteenths}]")

    # ---------------- the stairs down ----------------
    for i in range(4):
        put(STAIRS_X, M - 1 - i, STAIRS_Z + i, "seafarers:deck_stairs[facing={stern}]")

    # ---------------- lower deck (floor y 1) ----------------
    # captain's quarters, under the helm deck
    put_multi(-2, 1, 4, "mattress", "{bow}", [(0, 0, 0), (0, 0, 1)])     # the captain's bed
    put_multi(2, 1, 4, "mattress", "{bow}", [(0, 0, 0), (0, 0, 1)])
    put(-3, 1, 5, "seafarers:sea_chest[facing={starboard}]")              # clothing
    put(-3, 1, 6, "seafarers:sea_chest[facing={starboard}]")              # vanity
    put(3, 1, 5, "seafarers:sea_chest[facing={port}]")                    # equipment
    put(3, 1, 6, "seafarers:cask[facing={port}]")
    put(0, 5, 4, "seafarers:ship_lantern[hanging=true,deck=true]")
    # stores along the starboard side
    for z in (8, 9, 10):
        put(3, 1, z, "seafarers:supply_barrel[supply=cannonballs]", loot="stock:30")
    put(3, 1, 11, "seafarers:supply_barrel[supply=planks]")
    put(3, 1, 12, "seafarers:supply_barrel[supply=grog]")
    put(3, 1, 15, "seafarers:sea_chest[facing={port}]", loot="ammo")      # weapons and ammunition, by the stairs
    put(2, 1, 12, "seafarers:bucket_rack[facing={port}]")
    # the voyage table, under the stairs
    put_multi(-1, 1, 14, "voyage_table", "{bow}", [(0, 0, 0), (1, 0, 0)])
    # the galley forward: food barrels, the stove beside them, fresh water
    put(3, 1, 19, "seafarers:supply_barrel[supply=food]")
    put(3, 1, 20, "seafarers:supply_barrel[supply=food]")
    put(3, 1, 21, "seafarers:stove[facing={port}]")
    put(3, 1, 22, "seafarers:water_barrel[facing={port},level=3]")
    put(-3, 1, 21, "seafarers:cask[facing={starboard}]")
    put(-3, 1, 22, "seafarers:cask[facing={starboard}]")
    # the map table, right forward under the bow
    put_multi(0, 1, 24, "map_table", "{bow}", [(0, 0, 0), (-1, 0, 0), (1, 0, 0), (0, 0, -1), (-1, 0, -1), (1, 0, -1)])
    # the brig: bars round a cell against the port side, its door facing in
    (bx0, by, bz0), (bx1, _, bz1) = BRIG
    for y in range(1, M - 1):   # (up to the deck beams: nobody fits through the last gap)
        for z in range(bz0 - 1, bz1 + 2):
            if (-1, y, z) != BRIG_DOOR and (-1, y, z) != (BRIG_DOOR[0], BRIG_DOOR[1] + 1, BRIG_DOOR[2]):
                put(-1, y, z, "minecraft:iron_bars")
        for x in range(bx0, bx1 + 1):
            put(x, y, bz0 - 1, "minecraft:iron_bars")
            put(x, y, bz1 + 1, "minecraft:iron_bars")
    put(*BRIG_DOOR, "seafarers:brig_door[facing={port},half=lower,hinge=left,open=false]")
    put(BRIG_DOOR[0], BRIG_DOOR[1] + 1, BRIG_DOOR[2], "seafarers:brig_door[facing={port},half=upper,hinge=left,open=false]")
    put(-2, M - 1, 17, "seafarers:ship_lantern[hanging=true,deck=true]")
    # lanterns hanging from the main deck's beams
    for z in (10, 20, 26):
        put(0, M - 1, z, "seafarers:ship_lantern[hanging=true,deck=true]")

    # ---------------- fit check ----------------
    import fitcheck
    inside_ids = {"chest", "barrel", "stove", "flag_box", "sea_chest", "cask", "water_barrel", "supply_barrel", "bucket_rack",
                  "mattress", "map_table", "voyage_table", "cannon", "harpoon", "helm", "anchor", "sail_rope", "brace_rope",
                  "ship_lantern", "iron_bars", "brig_door"}
    problems = fitcheck.check(s, cells, popped, half_width, inside_ids,
                              outside_ok={(0, int(NEST_Y) - 1, MAIN_Z - 1), (-5, M, MAIN_Z), (5, M, MAIN_Z)})
    print(f"fit check: {len(problems)} problem(s)")
    for p in problems:
        print("  " + p)
    s.problems = problems

    s.set_hull(cells, lambda pos, cell: pos[1] <= 3 and any(m in ("bottom", "planks", "keel", "gilt", "trim")
                                                           for m in cell.values()))
    brig_inside = {(x, y, z) for x in range(BRIG[0][0], BRIG[1][0] + 1) for y in range(1, M)
                   for z in range(BRIG[0][2], BRIG[1][2] + 1)}
    for z in range(0, 33):
        for y in range(1, M):
            for x in range(-4, 5):
                if s.occupied((x, y, z)) or (x, y, z) in brig_inside:
                    continue   # floodwater doesn't get into the brig
                if all(inside(x + dx, y + dy, z + dz) for dx in (-0.5, 0.5) for dy in (0.05, 0.7) for dz in (0.05, 0.95)):
                    s.bilge.append((x, y, z))
    import shiplib
    s.dry = shiplib.dry_cells(inside, range(-5, 6), range(0, M + 1), range(0, 34))
    s.dry_mesh = shiplib.dry_mesh(inside, -5.5, 5.5, 0, MAIN_DECK + 0.5, 0, 34)
    s.respawn = (-2, H, 3)
    s.respawns = [(-2, H, 3), (2, M, 9), (-2, M, 24), (0, 1, 8), (-1, 1, 20), (1, 1, 4)]
    s.brig = [BRIG[0], BRIG[1], BRIG_DOOR]
    s.hold = (1, M - 1)
    return s


if __name__ == "__main__":
    import build_all
    build_all.main()
