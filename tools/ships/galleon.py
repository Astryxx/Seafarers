"""The Galleon, as a voxel ship, laid out like Sea of Thieves' Galleon.

Three masts (mizzen aft, mainmast with the crow's nest, foremast), a crew of four, eight cannons on the gun deck (four
a side), two harpoons in the bow, and three decks below the helm. Built the same way as the Sloop (see sloop.py).

Ship coordinates: x across the beam (+ starboard), y up from the keel, z from the stern (0) toward the bow.

Levels (block y of each walking surface), 3.75 blocks clear between each:
  lower deck 1 (the brig, and the bilge), gun deck 5, main deck 9, aft deck 13 (over the captain's quarters).
Along the ship (z):
  0..13    aft deck: the wheel and the capstan right aft, the mizzen; the captain's quarters below it (the voyage
           table, the captain's bed; a door out to the rowboat ladder over the stern)
  13..46   main deck: stairs up to the aft deck either side, mainmast at 24 (boarding ladders and a plank either side),
           foremast at 36, harpoons and bowsprit in the bow
Gun deck: the guns at z 14, 19, 27 and 32, the map table aft, stores, galley and bunks between the guns.
"""
import math

from shiplib import Ship
from voxel import VoxelGrid

STERN, BOW = 0.0, 45.9
BEAM = 5.5
LOWER, GUN, MAIN, AFT = 1.0, 5.0, 9.0, 13.0
BILGE_TOP = 4.0
AFT_END = 13.0                    # the aft deck's (and the captain's quarters') forward edge
RAIL = 0.75
BOW_ROUND = 34.0

MIZZEN_Z, MAIN_Z, FORE_Z = 8, 24, 36
MIZZEN_TOP, MAIN_TOP, FORE_TOP = 33, 41, 37
MIZZEN_YARD, MAIN_YARD, FORE_YARD = 26, 27, 25
NEST_Y = 31.0
NEST_R = 2.75
BOWSPRIT_HEEL = (0, 9, 43)
BOWSPRIT_RAISE = 14
GUNS_Z = (14, 19, 27, 32)
HARPOON_Z = 40
UP_STAIRS_Z = 13                  # main deck to the aft deck, either side (x = +-4), climbing aft from z 16
DOWN1 = (1, 16)                   # main deck to gun deck: x, first z (four steps going forward)
DOWN2 = (-1, 29)                  # gun deck to lower deck
BRIG = ((-4, 1, 20), (-3, 1, 21))
BRIG_DOOR = (-2, 1, 20)


def plan(z):
    if z < 10:
        return BEAM * (0.82 + 0.18 * (1 - ((10 - z) / 10) ** 2))
    if z > BOW_ROUND:
        t = (z - BOW_ROUND) / (BOW - BOW_ROUND)
        return BEAM * max(0.0, 1 - t ** 2.2)
    return BEAM


def keel(z):
    k = 0.25
    if z < 1.5:
        k += 0.5 * (1.5 - z)
    if z > 32:
        k += 6.6 * ((z - 32) / (BOW - 32)) ** 2.4
    return k


def sheer(z):
    if z < AFT_END:
        return AFT + RAIL
    h = MAIN + RAIL
    if z > 38:
        h += 1.2 * ((z - 38) / (BOW - 38)) ** 2
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
    return b * (0.7 + 0.3 * (1 - (1 - t) ** 3))


def inside(x, y, z):
    return y <= sheer(z) and abs(x) <= half_width(z, y)


def build_hull():
    g = VoxelGrid()
    d = 0.25
    bounds = ((-7, 7), (-1, 15), (-1, 47))

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
        if GUN - 0.5 <= y < GUN - 0.25 or MAIN - 0.5 <= y < MAIN - 0.25:
            return "trim"                                  # wales at the gun deck and the main deck
        if 7.25 <= y < 7.5:
            return "gilt"                                  # a gilt band above the gunports
        if z < AFT_END and AFT - 0.5 <= y < AFT - 0.25:
            return "trim"
        if z < AFT_END and y >= MAIN:
            for post in (0.4, 6.5, AFT_END - 0.12):
                if abs(z - post) < 0.13:
                    return "gilt"
        return "planks"

    g.fill(skin, skin_material, bounds)
    g.fill(lambda x, y, z: sheer(z) - 0.25 <= y <= sheer(z) and inside(x, y, z) and abs(x) >= half_width(z, y) - 0.5,
           lambda *_: "gilt", bounds)

    def plate(y_top, z0, z1, material="deck"):
        g.fill(lambda x, y, z: y_top - d <= y <= y_top and z0 <= z <= z1 and inside(x, y, z),
               lambda *_: material, bounds)

    g.fill(lambda x, y, z: y <= LOWER and z >= 0.25 and inside(x, y, z), lambda *_: "deck", bounds)
    plate(GUN, 0.3, BOW)
    plate(MAIN, 0.3, BOW)
    plate(AFT, 0.3, AFT_END)
    # the captain's quarters' front bulkhead, and the rail along the front of the aft deck (gaps for the stairs)
    g.fill(lambda x, y, z: AFT_END - d <= z <= AFT_END and MAIN <= y <= AFT and inside(x, y, z),
           lambda *_: "planks", bounds)
    g.fill(lambda x, y, z: AFT_END - d <= z <= AFT_END and AFT <= y <= AFT + RAIL and not 3.4 <= abs(x) <= 4.6
           and inside(x, y, z), lambda x, y, z: "gilt" if y >= AFT + RAIL - 0.25 else "trim", bounds)
    g.fill(lambda x, y, z: abs(x) < 0.26 and 0.8 <= z <= BOW - 1.0 and keel(z) - 0.25 <= y < keel(z),
           lambda *_: "keel", bounds)

    # crow's nest on the mainmast
    cx, cz = 0.0, MAIN_Z + 0.5
    nest_bounds = ((-4, 4), (int(NEST_Y) - 1, int(NEST_Y) + 1), (MAIN_Z - 4, MAIN_Z + 4))
    g.fill(lambda x, y, z: NEST_Y - d <= y <= NEST_Y and math.hypot(x - cx, z - cz) <= NEST_R,
           lambda *_: "deck", nest_bounds)
    g.fill(lambda x, y, z: NEST_Y < y <= NEST_Y + 1.0 and NEST_R - 0.25 <= math.hypot(x - cx, z - cz) <= NEST_R,
           lambda x, y, z: "gilt" if y > NEST_Y + 0.75 else "trim", nest_bounds)

    # the stern: a gilded frame round the quarters' windows across the transom
    g.fill(lambda x, y, z: z <= 0.55 and inside(x, y, z) and 10.0 <= y <= 12.1 and abs(x) <= 3.6
           and not (abs(x) <= 3.4 and 10.25 <= y <= 11.85), lambda *_: "gilt", bounds)

    def stem_front(y):
        lo, hi = 32.0, BOW
        for _ in range(30):
            mid = (lo + hi) / 2
            if keel(mid) < y:
                lo = mid
            else:
                hi = mid
        return lo

    g.fill(lambda x, y, z: 4.0 <= y <= 6.6 and abs(x) < 0.3 and stem_front(y) - 0.2 <= z <= stem_front(y) + 0.5,
           lambda *_: "gilt", bounds)
    g.fill(lambda x, y, z: 6.3 <= y <= 6.9 and abs(x) < 0.6 and stem_front(6.6) - 0.3 <= z <= stem_front(6.6) + 0.35,
           lambda *_: "gilt", bounds)

    # openings
    rise = math.tan(math.radians(BOWSPRIT_RAISE))
    g.carve(lambda x, y, z: z > BOWSPRIT_HEEL[2] + 0.5 and abs(x) < 0.4
            and abs(y - (BOWSPRIT_HEEL[1] + 0.25 + (z - BOWSPRIT_HEEL[2] - 0.5) * rise)) < 0.35)
    for gz in GUNS_Z:   # gunports in the sides of the gun deck
        g.carve(lambda x, y, z, gz=gz: abs(x) > 4.2 and GUN + 0.05 <= y <= GUN + 1.6 and gz - 0.05 <= z <= gz + 1.05)
    # the quarters' windows: across the stern (a door to starboard, out to the rowboat ladder) and along the sides
    g.carve(lambda x, y, z: z <= 0.55 and abs(x) <= 3.4 and 10.25 <= y <= 11.85 and not 1.5 <= x <= 2.5)
    g.carve(lambda x, y, z: z <= 0.6 and 1.5 <= x <= 2.5 and MAIN <= y <= 11.85)
    g.carve(lambda x, y, z: abs(x) > 4.5 and 10.25 <= y <= 11.75 and (3.0 <= z <= 5.8 or 9.0 <= z <= 11.5))
    g.carve(lambda x, y, z: -1.0 <= x <= 1.0 and MAIN <= y <= 11.6 and AFT_END - 0.3 <= z <= AFT_END + 0.1)   # quarters door
    # boarding ladders either side of the mainmast, and a plank out over the water beside each
    g.carve(lambda x, y, z: abs(x) > 4.5 and MAIN <= y <= MAIN + RAIL + 0.1 and (MAIN_Z <= z <= MAIN_Z + 1 or 26.9 <= z <= 28.1))
    # hatches over the stairs down
    g.carve(lambda x, y, z: DOWN1[0] - 0.5 <= x <= DOWN1[0] + 0.5 and MAIN - 0.3 <= y <= MAIN + 0.1
            and DOWN1[1] <= z <= DOWN1[1] + 4)
    g.carve(lambda x, y, z: DOWN2[0] - 0.5 <= x <= DOWN2[0] + 0.5 and GUN - 0.3 <= y <= GUN + 0.1
            and DOWN2[1] <= z <= DOWN2[1] + 4)
    g.carve(lambda x, y, z: abs(x) >= 1.6 and MAIN + 0.5 <= y <= MAIN + 2.2 and HARPOON_Z <= z <= HARPOON_Z + 2)
    return g


def build():
    s = Ship("galleon", crew=4)
    s.masts = 3
    s.stats = {"maxSpeed": 10.5, "acceleration": 0.3, "coast": 0.2, "maxYawRate": 0.07, "yawAccel": 0.045, "pivotRate": 0.5,
               "sailRun": 1.0, "sailBeam": 0.75, "sailClose": 0.38, "sailIrons": 0.1,
               "waterDrag": 3.8, "waterYawDrag": 18.0, "floodPerHole": 0.035, "floodRushAbove": 0.43, "floodRush": 2.5,
               "bucket": 2.5, "mastHealth": 3, "capstanPegs": 4, "anchorWeight": 2.0, "wheelSpeed": 0.0125, "wheelTurns": 1.0}
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

    L, G, M, A = int(LOWER), int(GUN), int(MAIN), int(AFT)

    # ---------------- aft deck ----------------
    put(0, A, 4, "seafarers:helm[facing={bow}]")
    put(-2, A, 4, "seafarers:ship_lantern")
    put(0, A, 1, "seafarers:anchor[facing={stern}]",
        data={"hawseForward": 1.65, "hawseDown": 3.6, "hawseSide": -2.2, "fairleadForward": 0.75, "fairleadUp": 0.82,
              "depth": 12, "pegs": 4})
    # stairs up from the main deck either side, climbing aft to the aft deck's front edge
    for x in (-4, 4):
        for i in range(4):
            put(x, M + 3 - i, UP_STAIRS_Z + i, "seafarers:deck_stairs[facing={stern}]")
    for y in range(2, M + 1):
        put(2, y, -1, "seafarers:rope_ladder[facing={stern}]")   # up the stern to the quarters' door, for a rowboat

    # ---------------- masts and rigging ----------------
    def mast(index, z, top, nest):
        decks = {G - 1, M - 1} | ({A - 1} if z < AFT_END else set()) | ({int(NEST_Y) - 1} if nest else set())
        for y in range(1, top + 1):
            put(0, y, z, f"seafarers:mast[deck={'true' if y in decks else 'false'},mast={index}]")
        put(0, top + 1, z, "seafarers:flag[facing={bow}]")

    mast(0, MAIN_Z, MAIN_TOP, True)
    mast(1, FORE_Z, FORE_TOP, False)
    mast(2, MIZZEN_Z, MIZZEN_TOP, False)
    for y in range(M, int(NEST_Y)):
        put(0, y, MAIN_Z - 1, "seafarers:rope_ladder[facing={stern}]")
    put(0, MAIN_YARD, MAIN_Z + 1, "seafarers:sail_yard[facing={bow},mast=0]",
        data={"halfWidth": 7.0, "height": 13.0, "yardHalfLength": 7.7, "forward": 0.8,
              "shroudTop": NEST_Y - MAIN_YARD - 0.4, "shroudTopSpread": NEST_R - 0.15,
              "shroudDrop": MAIN_YARD - MAIN - RAIL + 0.5, "shroudSpread": BEAM - 0.25, "masthead": 0})
    put(0, FORE_YARD, FORE_Z + 1, "seafarers:sail_yard[facing={bow},mast=1]",
        data={"halfWidth": 6.5, "height": 12.0, "yardHalfLength": 7.2, "forward": 0.8,
              "shroudTop": FORE_TOP - FORE_YARD - 2.0, "shroudTopSpread": 0.35,
              "shroudDrop": FORE_YARD - MAIN - RAIL + 0.5, "shroudSpread": plan(FORE_Z) - 0.25,
              "masthead": FORE_TOP - FORE_YARD - 0.2})
    put(0, MIZZEN_YARD, MIZZEN_Z + 1, "seafarers:sail_yard[facing={bow},mast=2]",
        data={"halfWidth": 5.5, "height": 10.0, "yardHalfLength": 6.2, "forward": 0.75,
              "shroudTop": MIZZEN_TOP - MIZZEN_YARD - 2.0, "shroudTopSpread": 0.35,
              "shroudDrop": MIZZEN_YARD - AFT - RAIL + 0.5, "shroudSpread": plan(MIZZEN_Z) - 0.25, "masthead": 0})
    # each mast's ropes at its foot against the bulwarks: length cleats aft of it, angle pulleys forward
    for m, mz, deck in ((0, MAIN_Z, M), (1, FORE_Z, M), (2, MIZZEN_Z, A)):
        for x, side in ((-4, "port"), (4, "starboard")):
            put(x, deck, mz - 2, f"seafarers:sail_rope[facing={{{side}}},mast={m}]")
            put(x, deck, mz + (1 if m == 1 else 2), f"seafarers:brace_rope[facing={{{side}}},mast={m}]")   # (the bow narrows by the foremast)
    put(1, int(NEST_Y) - 1, MAIN_Z, "seafarers:ship_lantern[hanging=true,deck=true]")
    put(0, M + 1, MAIN_Z + 1, "seafarers:ship_bell[facing={stern}]")
    put(0, int(NEST_Y) + 1, MAIN_Z + 1, "seafarers:ship_bell[facing={stern}]")
    put(-1, int(NEST_Y), MAIN_Z, "seafarers:flag_box[facing={port}]")

    # ---------------- main deck ----------------
    for side, x in (("port", -6), ("starboard", 6)):
        for y in range(2, M + 1):
            put(x, y, MAIN_Z, f"seafarers:rope_ladder[facing={{{side}}}]")
        # a plank out over the water, flush with the deck
        for out in (0, 1, 2):
            put(x + (out if x > 0 else -out), M - 1, 27, "seafarers:deck_slab[type=top]")
    for x in (-2, 2):
        put(x, M, HARPOON_Z, "seafarers:harpoon[facing={bow}]")
    for x in (-1, 1):
        put(x, M, HARPOON_Z + 2, "seafarers:ship_lantern")
    put(3, M, DOWN1[1] - 2, "seafarers:bucket_rack[facing={port}]")
    put(*BOWSPRIT_HEEL, "seafarers:bowsprit[facing={bow}]",
        data={"length": 13.0, "raise": BOWSPRIT_RAISE, "bobstayDown": 3.2, "bobstayForward": 0.55})
    hx, hy, hz = BOWSPRIT_HEEL
    r = math.radians(BOWSPRIT_RAISE)
    for z in range(hz + 1, hz + 14):
        t = (z + 0.5 - (hz + 0.5)) / math.cos(r)
        if t > 13.0 - 0.3:
            break
        top = hy + 0.25 + t * math.sin(r) + 0.17 / math.cos(r)
        y = math.floor(top - 1e-3)
        sixteenths = max(1, min(16, round((top - y) * 16)))
        if (hx, y, z) not in cells and (hx, y, z) not in s.cells:
            put(hx, y, z, f"seafarers:bowsprit_spar[facing={{bow}},top={sixteenths}]")

    # ---------------- captain's quarters (main deck level, under the aft deck) ----------------
    put_multi(-3, M, 10, "voyage_table", "{bow}", [(0, 0, 0), (1, 0, 0)])
    put_multi(3, M, 4, "mattress", "{bow}", [(0, 0, 0), (0, 0, 1)])      # the captain's bed
    put(3, M, 5, "seafarers:sea_chest[facing={port}]")
    put(-4, M, 4, "seafarers:cask[facing={starboard}]")
    for x in (-1, 0, 1):
        for z in (5, 6):
            put(x, M, z, "minecraft:red_carpet")
    put(0, A - 1, 5, "seafarers:ship_lantern[hanging=true,deck=true]")

    # ---------------- stairs down ----------------
    for (sx, sz), top in ((DOWN1, M), (DOWN2, G)):
        for i in range(4):
            put(sx, top - 1 - i, sz + i, "seafarers:deck_stairs[facing={stern}]")

    # ---------------- gun deck (floor y 5) ----------------
    for gz in GUNS_Z:
        for x, side in ((-4, "port"), (4, "starboard")):
            put_multi(x, G, gz, "cannon", "{" + side + "}", [(0, 0, 0), (0, 0, -1)])
    # the map table aft, under the helm
    put_multi(0, G, 2, "map_table", "{bow}", [(0, 0, 0), (-1, 0, 0), (1, 0, 0), (0, 0, -1), (-1, 0, -1), (1, 0, -1)])
    # bunks aft, either side
    for x in (-4, 4):
        put_multi(x, G, 6, "mattress", "{bow}", [(0, 0, 0), (0, 0, 1)])
        put_multi(x, G, 9, "mattress", "{bow}", [(0, 0, 0), (0, 0, 1)])
    # stores between the guns: shot and planks to starboard, chests to port
    put(4, G, 16, "seafarers:supply_barrel[supply=cannonballs]", loot="stock:30")
    put(4, G, 17, "seafarers:supply_barrel[supply=cannonballs]", loot="stock:30")
    put(4, G, 22, "seafarers:supply_barrel[supply=cannonballs]", loot="stock:30")
    put(4, G, 23, "seafarers:supply_barrel[supply=planks]", loot="stock:40")
    put(4, G, 24, "seafarers:supply_barrel[supply=grog]")
    put(2, G, 21, "seafarers:sea_chest[facing={stern}]", loot="ammo")    # weapons and ammunition, by the stairs
    put(-4, G, 16, "seafarers:sea_chest[facing={starboard}]")             # clothing
    put(-4, G, 17, "seafarers:sea_chest[facing={starboard}]")             # equipment
    put(-4, G, 22, "seafarers:sea_chest[facing={starboard}]")             # vanity
    put(-4, G, 23, "seafarers:cask[facing={starboard}]")
    put(3, G, 12, "seafarers:bucket_rack[facing={port}]")
    # the galley forward: food barrels, the stove beside them, fresh water
    put(4, G, 35, "seafarers:supply_barrel[supply=food]")
    put(4, G, 36, "seafarers:supply_barrel[supply=food]")
    put(4, G, 37, "seafarers:stove[facing={port}]")
    put(4, G, 38, "seafarers:water_barrel[facing={port},level=3]")
    put(-4, G, 35, "seafarers:cask[facing={starboard}]")
    put(-4, G, 36, "seafarers:cask[facing={starboard}]")
    for z in (4, 12, 22, 30, 38):
        put(0, M - 1, z, "seafarers:ship_lantern[hanging=true,deck=true]")

    # ---------------- lower deck (floor y 1): the brig ----------------
    (bx0, _, bz0), (bx1, _, bz1) = BRIG
    door_x = BRIG_DOOR[0]
    for y in range(1, G - 1):
        for z in range(bz0 - 1, bz1 + 2):
            if (door_x, y, z) not in (BRIG_DOOR, (BRIG_DOOR[0], BRIG_DOOR[1] + 1, BRIG_DOOR[2])):
                put(door_x, y, z, "minecraft:iron_bars")
        for x in range(bx0, bx1 + 1):
            put(x, y, bz0 - 1, "minecraft:iron_bars")
            put(x, y, bz1 + 1, "minecraft:iron_bars")
    put(*BRIG_DOOR, "seafarers:brig_door[facing={port},half=lower,hinge=left,open=false]")
    put(BRIG_DOOR[0], BRIG_DOOR[1] + 1, BRIG_DOOR[2], "seafarers:brig_door[facing={port},half=upper,hinge=left,open=false]")
    put(-3, G - 1, 20, "seafarers:ship_lantern[hanging=true,deck=true]")
    for z in (10, 26, 34):
        put(0, G - 1, z, "seafarers:ship_lantern[hanging=true,deck=true]")
    put(2, L, 12, "seafarers:bucket_rack[facing={port}]")

    # ---------------- fit check ----------------
    import fitcheck
    inside_ids = {"chest", "barrel", "stove", "flag_box", "sea_chest", "cask", "water_barrel", "supply_barrel", "bucket_rack",
                  "mattress", "map_table", "voyage_table", "cannon", "harpoon", "helm", "anchor", "sail_rope", "brace_rope",
                  "ship_lantern", "iron_bars", "brig_door"}
    outside = {(0, int(NEST_Y) - 1, MAIN_Z - 1), (-6, M, MAIN_Z), (6, M, MAIN_Z)}
    outside |= {(x, M - 1, 27) for x in (-8, -7, -6, 6, 7, 8)}
    problems = fitcheck.check(s, cells, popped, half_width, inside_ids, outside_ok=outside)
    print(f"fit check: {len(problems)} problem(s)")
    for p in problems:
        print("  " + p)
    s.problems = problems

    s.set_hull(cells, lambda pos, cell: pos[1] <= 3 and any(m in ("bottom", "planks", "keel", "gilt", "trim")
                                                           for m in cell.values()))
    brig_inside = {(x, y, z) for x in range(BRIG[0][0], BRIG[1][0] + 1) for y in range(1, G)
                   for z in range(BRIG[0][2], BRIG[1][2] + 1)}
    for z in range(0, 46):
        for y in range(1, M):
            for x in range(-5, 6):
                if s.occupied((x, y, z)) or (x, y, z) in brig_inside:
                    continue
                if all(inside(x + dx, y + dy, z + dz) for dx in (-0.5, 0.5) for dy in (0.05, 0.7) for dz in (0.05, 0.95)):
                    s.bilge.append((x, y, z))
    import shiplib
    s.dry = shiplib.dry_cells(inside, range(-6, 7), range(0, M + 1), range(0, 47))
    s.dry_mesh = shiplib.dry_mesh(inside, -6.5, 6.5, 0, MAIN + 0.5, 0, 47)
    s.respawn = (-2, A, 6)
    s.respawns = [(-2, A, 6), (2, M, 30), (-2, M, 18), (1, M, 4), (0, G, 10), (-2, G, 26), (2, G, 30), (0, L, 12)]
    s.brig = [BRIG[0], BRIG[1], BRIG_DOOR]
    s.hold = (1, M - 1)
    s.draft = (1.5, 3.0)
    return s


if __name__ == "__main__":
    import build_all
    build_all.main()
