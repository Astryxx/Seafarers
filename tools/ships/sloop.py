"""The Sloop, as a voxel ship, laid out like Sea of Thieves' Sloop.

Proportions were measured from a reference model at 8 blocks per model unit (enough for 2 blocks of headroom
below decks); no geometry is copied. The hull is a smooth shape filled with 4-pixel voxels (tools/ships/voxel.py)
and fittings are ordinary blocks in cells.

Ship coordinates: x across the beam (+ starboard), y up from the keel, z from the stern (0) toward the bow.
Facing tokens ({bow}, {stern}, {port}, {starboard}) and axis tokens ({along}, {across}) are resolved at launch.

Levels (block y of each walking surface):
  hold floor 1, cabin floor 3, main deck 5, aft deck 7 (3.75 blocks clear in the hold and the cabin).
Along the ship (z):
  0..3.8   stern gallery: cabin-floor level, open sides, cloth canopy above, map table
  3.8..10  aft cabin under the raised aft deck (helm at its forward edge, capstan and sail controls aft)
  10..22.9 waist and bow: main deck over the hold; mast at z 14, cannons at z 16, harpoons at z 20
"""
import math

from shiplib import Ship
from voxel import VoxelGrid

# ---- measured proportions (blocks) ----
STERN, BOW = 0.0, 22.9
BEAM = 3.5                      # half-beam amidships: the outer planking ends exactly on a block boundary
HOLD_FLOOR, CABIN_FLOOR, MAIN_DECK, AFT_DECK = 1.0, 3.0, 5.0, 7.0
GALLERY_END, CABIN_END = 3.8, 10.0
MAST_Z, MAST_TOP = 14, 27
NEST_Y = 21.0                   # crow's nest floor (top of a block)
NEST_R = 2.25                   # crow's nest radius, to the outside of its rail
YARD_Y = 18
BOWSPRIT_HEEL = (0, 5, 20)       # heel block of the bowsprit on the main deck
BOWSPRIT_RAISE = 16
VALANCE = True                  # (switched off only to rebuild the palette as it was before the valance)
RAIL = 0.75                     # bulwark height above a deck
BILGE_TOP = 4.0                 # where the turn of the bilge meets the straight topsides (the old main deck line)


BOW_ROUND = 17.0                # the Sloop keeps her full beam to here, then rounds off into a bluff bow


def plan(z):
    """Half-beam at the main-deck line: a broad transom, full amidships and well forward, a bluff rounded bow
    (fitted to the reference model's deck-line widths: 2.8 at z 20, 1.9 at 21, 1.1 at 22)."""
    if z < 6:
        return BEAM * (0.78 + 0.22 * (1 - ((6 - z) / 6) ** 2))
    if z > BOW_ROUND:
        t = (z - BOW_ROUND) / (BOW - BOW_ROUND)
        return BEAM * max(0.0, 1 - t ** 2.2)
    return BEAM


def keel(z):
    """Bottom of the hull: flat, with a forefoot sweeping up into the stem."""
    k = 0.25
    if z < 1.2:
        k += 0.5 * (1.2 - z)
    if z > 15:
        k += 5.2 * ((z - 15) / (BOW - 15)) ** 2.4
    return k


def sheer(z):
    """Top of the hull side: low gallery rail, tall aft cabin, low waist, a slightly raised bow."""
    if z < GALLERY_END:
        return CABIN_FLOOR + 1.75
    if z < CABIN_END:
        return AFT_DECK + RAIL
    h = MAIN_DECK + RAIL
    if z > 18:
        h += 0.8 * ((z - 18) / (BOW - 18)) ** 2
    return h


def half_width(z, y):
    if z < STERN or z > BOW:
        return -1.0
    k = keel(z)
    if y < k:
        return -1.0
    b = plan(z)
    top = BILGE_TOP
    if y >= top:
        return b  # straight sides above the deck line
    t = min(1.0, (y - k) / max(0.5, top - k))
    return b * (0.55 + 0.45 * (1 - (1 - t) ** 3))  # flat floor, full round bilge, near-upright hold sides


def inside(x, y, z):
    return y <= sheer(z) and abs(x) <= half_width(z, y)


def build_hull():
    g = VoxelGrid()
    d = 0.25
    bounds = ((-5, 5), (-1, 10), (-1, 24))

    def body(x, y, z):
        """Inside the hull's shape, ignoring how high the rail is (so a rising rail makes no planking across the deck)."""
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
            return "gilt"                      # gilded cap along the rail
        if 2.5 <= y < 2.75:
            return "gilt"                      # lower gold stripe
        if z >= CABIN_END and MAIN_DECK - 0.5 <= y < MAIN_DECK - 0.25:
            return "trim"                      # wale at the main deck
        if GALLERY_END <= z < CABIN_END:
            # gilded corner and middle posts on the cabin sides
            for post in (GALLERY_END + 0.12, 6.9, CABIN_END - 0.12):
                if abs(z - post) < 0.13 and y >= CABIN_FLOOR:
                    return "gilt"
        return "planks"

    g.fill(skin, skin_material, bounds)
    # cap rail: two voxels wide along the top of every bulwark
    g.fill(lambda x, y, z: sheer(z) - 0.25 <= y <= sheer(z) and inside(x, y, z) and abs(x) >= half_width(z, y) - 0.5,
           lambda *_: "gilt", bounds)

    def plate(y_top, z0, z1, material="deck"):
        g.fill(lambda x, y, z: y_top - d <= y <= y_top and z0 <= z <= z1 and inside(x, y, z),
               lambda *_: material, bounds)

    # the bilge is planked over solid up to the hold floor, so no sea shows through the turn of the bilge
    g.fill(lambda x, y, z: y <= HOLD_FLOOR and z >= CABIN_END - 0.25 and inside(x, y, z), lambda *_: "deck", bounds)
    plate(CABIN_FLOOR, 0.3, CABIN_END)
    plate(MAIN_DECK, CABIN_END, BOW)
    plate(AFT_DECK, GALLERY_END, CABIN_END)
    # the cabin's front bulkhead, from the hold floor up to the aft deck
    g.fill(lambda x, y, z: CABIN_END - d <= z <= CABIN_END and HOLD_FLOOR <= y <= AFT_DECK and inside(x, y, z),
           lambda *_: "planks", bounds)
    # rail across the front of the aft deck (the helm rail) and across its back (looking over the gallery)
    for zr in (CABIN_END - d, GALLERY_END):
        g.fill(lambda x, y, z, zr=zr: zr <= z <= zr + d and AFT_DECK <= y <= AFT_DECK + RAIL and inside(x, y, z),
               lambda x, y, z: "gilt" if y >= AFT_DECK + RAIL - 0.25 else "trim", bounds)
    # keel: a two-voxel fin under the flat bottom
    g.fill(lambda x, y, z: abs(x) < 0.26 and 0.8 <= z <= BOW - 1.0 and keel(z) - 0.25 <= y < keel(z),
           lambda *_: "keel", bounds)

    # crow's nest: a round basket on the mast
    cx, cz = 0.0, MAST_Z + 0.5
    # (bigger than the Sloop's in proportion: a Minecraft player is 0.6 wide and needs room to walk round the mast,
    # and the ladder's hole must be well clear of the rim)
    nest_bounds = ((-3, 3), (int(NEST_Y) - 1, int(NEST_Y) + 1), (MAST_Z - 3, MAST_Z + 3))
    g.fill(lambda x, y, z: NEST_Y - d <= y <= NEST_Y and math.hypot(x - cx, z - cz) <= NEST_R,
           lambda *_: "deck", nest_bounds)
    g.fill(lambda x, y, z: NEST_Y < y <= NEST_Y + 1.0 and NEST_R - 0.25 <= math.hypot(x - cx, z - cz) <= NEST_R,
           lambda x, y, z: "gilt" if y > NEST_Y + 0.75 else "trim", nest_bounds)
    # cloth canopy over the stern gallery, sloping down aft
    def canopy_y(z):
        return AFT_DECK + 1.6 - 0.15 * (GALLERY_END - z)

    g.fill(lambda x, y, z: -0.2 <= z <= GALLERY_END + 0.2 and abs(x) <= plan(max(z, 0)) + 0.1
           and abs(y - canopy_y(z)) <= 0.13, lambda *_: "canvas", bounds)
    # a scalloped valance hanging from the canopy's sides and back, as on the Sloop's awning
    def valance(x, y, z):
        if not (-0.2 <= z <= GALLERY_END + 0.2):
            return False
        edge = plan(max(z, 0)) + 0.1
        side = edge - 0.26 <= abs(x) <= edge
        back = z <= 0.05 and abs(x) <= edge
        if not (side or back):
            return False
        along = z if side else x
        depth = 0.5 if int(math.floor(along * 2)) % 2 == 0 else 0.25
        top = canopy_y(z)
        return top - depth - 0.13 <= y < top - 0.13

    if VALANCE:
        g.fill(valance, lambda *_: "canvas", bounds)
    for pz in (0.5, GALLERY_END - 0.4):
        px = plan(pz) - 0.25
        g.fill(lambda x, y, z, pz=pz, px=px: abs(abs(x) - px) < 0.26 and abs(z - pz) < 0.26
               and sheer(pz) <= y < canopy_y(pz), lambda *_: "post", bounds)
    # window shutters: a plank lid propped out above each cabin window
    for side in (-1, 1):
        g.fill(lambda x, y, z, side=side: 6.0 <= z <= 8.6 and 5.8 <= y <= 6.05
               and half_width(z, 6) <= side * x <= half_width(z, 6) + 0.6, lambda *_: "planks", bounds)

    # figurehead: a gilded beak on the front of the stem, under the bowsprit
    def stem_front(y):
        lo, hi = 15.0, BOW
        for _ in range(30):
            mid = (lo + hi) / 2
            if keel(mid) < y:
                lo = mid
            else:
                hi = mid
        return lo

    g.fill(lambda x, y, z: 3.6 <= y <= 5.4 and abs(x) < 0.3 and stem_front(y) - 0.2 <= z <= stem_front(y) + 0.45,
           lambda *_: "gilt", bounds)
    g.fill(lambda x, y, z: 5.1 <= y <= 5.7 and abs(x) < 0.55 and stem_front(5.4) - 0.3 <= z <= stem_front(5.4) + 0.3,
           lambda *_: "gilt", bounds)

    # openings
    rise = math.tan(math.radians(BOWSPRIT_RAISE))
    g.carve(lambda x, y, z: z > BOWSPRIT_HEEL[2] + 0.5 and abs(x) < 0.4
            and abs(y - (BOWSPRIT_HEEL[1] + 0.25 + (z - BOWSPRIT_HEEL[2] - 0.5) * rise)) < 0.35)   # bowsprit notch
    g.carve(lambda x, y, z: abs(x) > 2.2 and MAIN_DECK <= y <= MAIN_DECK + RAIL and 15.9 <= z <= 17.1)     # gunports
    # openings in the aft-deck rail right above the side ladders (ladder cells are z 9..10)
    g.carve(lambda x, y, z: abs(x) > 2.5 and AFT_DECK <= y <= AFT_DECK + RAIL and 9.0 <= z <= 10.0)
    g.carve(lambda x, y, z: abs(x) < 3.5 and 4.25 <= y <= 5.75 and 6.0 <= z <= 8.6 and abs(x) > 2.0)      # cabin windows
    g.carve(lambda x, y, z: -0.5 <= x <= 1.5 and CABIN_FLOOR <= y <= AFT_DECK and CABIN_END - 0.3 <= z <= CABIN_END + 0.1)  # bulkhead door
    # hold hatch over the stairs down, long enough that a player on the stairs clears its forward edge
    g.carve(lambda x, y, z: 0.5 <= x <= 1.5 and MAIN_DECK - 0.3 <= y <= MAIN_DECK + 0.1 and 10 <= z <= 13)
    # slits in the bow bulwarks for the harpoon guns to swing in
    g.carve(lambda x, y, z: abs(x) >= 1.6 and MAIN_DECK + 0.5 <= y <= MAIN_DECK + 2 and 19.0 <= z <= 21.0)
    g.carve(lambda x, y, z: 1.5 <= x <= 2.5 and AFT_DECK + 0.01 <= y <= AFT_DECK + RAIL and 9.0 <= z <= 10.1)  # rail gap at the top of the aft-deck stairs
    g.carve(lambda x, y, z: abs(x) < 0.5 and MAIN_DECK - 0.3 <= y <= MAIN_DECK + 0.1 and 15.75 <= z <= 16.75)  # grating
    return g


def build():
    s = Ship("sloop", crew=2)
    s.masts = 1
    # handling, measured on the test sea (see tools/test_ship.py); fields as in dev.seafarers.ship.ShipStats
    s.stats = {"maxSpeed": 8.5, "acceleration": 0.45, "maxYawRate": 0.16, "yawAccel": 0.07, "pivotRate": 0.7,
               # starting guesses for the water drag; the helm measures the real values as she sails
               "waterDrag": 3.8, "waterYawDrag": 12.0, "floodPerHole": 0.05, "mastHealth": 6,
               "liftTrim": 1.6}   # (the hull grew a block taller for headroom below: this keeps her waterline where it was)
    cells = build_hull().cells()
    popped = {}

    def put(x, y, z, state, loot=None, data=None):
        """A fitting in a cell; it replaces any hull voxels there (fitcheck reports what that breaks)."""
        lost = cells.pop((x, y, z), None)
        if lost:
            popped[(x, y, z)] = lost
        s.put(x, y, z, state, loot, data)

    # facing tokens as a ship launched bow-north sees them, and model offsets {east, up, south} turned to suit
    # (as dev.seafarers.ship.block.MultiPart does): a fitting several blocks big puts each of its parts
    world_facing = {"{bow}": 0, "{starboard}": 1, "{stern}": 2, "{port}": 3}

    def put_multi(x, y, z, block, facing, offsets, props="", loot=None):
        for i, (ex, up, south) in enumerate(offsets):
            dx, dz = ex, south
            for _ in range(world_facing[facing]):
                dx, dz = -dz, dx
            extra = f",{props}" if props else ""
            put(x + dx, y + up, z - dz, f"seafarers:{block}[facing={facing},part={i}{extra}]", loot if i == 0 else None)

    # ---------------- aft deck: helm forward, capstan and sail controls aft ----------------
    put(0, 7, 8, "seafarers:helm[facing={bow}]")
    put(-2, 7, 8, "seafarers:ship_lantern")
    # the Sloop's anchor goes over the stern: cable over the rear rail, through a hawse in the transom
    put(0, 7, 5, "seafarers:anchor[facing={stern}]",
        data={"hawseForward": 5.65, "hawseDown": 2.25, "hawseSide": 0, "fairleadForward": 1.6, "fairleadUp": 0.82, "depth": 9})
    put(-2, 7, 4, "seafarers:sail_rope[facing={port}]")
    put(2, 7, 4, "seafarers:sail_rope[facing={starboard}]")
    put(-2, 7, 5, "seafarers:brace_rope[facing={port}]")
    put(2, 7, 5, "seafarers:brace_rope[facing={starboard}]")
    for side, x in (("port", -4), ("starboard", 4)):
        for y in range(2, 8):
            put(x, y, 9, f"seafarers:rope_ladder[facing={{{side}}}]")
    # stairs from the main deck up to the aft deck, starboard side, on a timber block
    put(2, 5, 11, "seafarers:deck_stairs[facing={stern}]")
    put(2, 5, 10, "seafarers:deck_planks")
    put(2, 6, 10, "seafarers:deck_stairs[facing={stern}]")

    # ---------------- cabin and gallery (cabin floor, y 3) ----------------
    # out through the door and up two steps to the main deck: one inside the cabin, one in the doorway
    put(0, 3, 9, "seafarers:deck_stairs[facing={bow}]")
    put(0, 4, 10, "seafarers:deck_stairs[facing={bow}]")
    for y in (1, 2, 3):
        put(0, y, 10, "seafarers:deck_planks")
    # as on the Sloop (positions from the reference model): the map table right across the gallery, the quest table
    # along the cabin's front wall on the port side, casks against the port side, the ammo chest to starboard
    put_multi(0, 3, 2, "map_table", "{bow}", [(0, 0, 0), (-1, 0, 0), (1, 0, 0), (0, 0, -1), (-1, 0, -1), (1, 0, -1)])
    put_multi(-2, 3, 8, "voyage_table", "{bow}", [(0, 0, 0), (1, 0, 0)])
    put(2, 3, 8, "seafarers:sea_chest[facing={port}]", loot="ammo")
    put(-2, 3, 4, "seafarers:cask[facing={starboard}]")
    put(-2, 3, 5, "seafarers:cask[facing={starboard}]")
    for x in (-1, 0, 1):
        for z in (5, 6):
            put(x, 3, z, "minecraft:red_carpet")
    put(0, 6, 7, "seafarers:ship_lantern[hanging=true,deck=true]")
    # stairs from the cabin down into the hold (starboard of the door, going forward)
    put(1, 2, 10, "seafarers:deck_stairs[facing={stern}]")
    put(1, 1, 10, "seafarers:deck_planks")
    put(1, 1, 11, "seafarers:deck_stairs[facing={stern}]")

    # ---------------- main deck ----------------
    for y in range(1, MAST_TOP + 1):
        put(0, y, MAST_Z, "seafarers:mast[deck=true]" if y in (int(MAIN_DECK) - 1, int(NEST_Y) - 1) else "seafarers:mast")
    for y in range(int(MAIN_DECK), int(NEST_Y)):
        put(0, y, MAST_Z - 1, "seafarers:rope_ladder[facing={stern}]")
    put(0, MAST_TOP + 1, MAST_Z, "seafarers:flag[facing={bow}]")
    # rig sizes, in blocks from the yard block (one forward of the mast); see SailYardBlockEntity
    put(0, YARD_Y, MAST_Z + 1, "seafarers:sail_yard[facing={bow},mast=0]",
        data={"halfWidth": 5.0, "height": 10.0, "yardHalfLength": 5.7, "forward": 0.75,
              "shroudTop": NEST_Y - YARD_Y - 0.4, "shroudTopSpread": NEST_R - 0.15, "shroudDrop": YARD_Y - MAIN_DECK - RAIL + 0.5,
              "shroudSpread": BEAM - 0.25, "masthead": MAST_TOP - YARD_Y - 0.2})
    put(1, int(NEST_Y) - 1, MAST_Z, "seafarers:ship_lantern[hanging=true,deck=true]")
    # the guns on their carriages, run out to the gunports; the harpoons up in the bluff of the bow
    for x, side in ((-2, "port"), (2, "starboard")):
        put_multi(x, 5, 16, "cannon", "{" + side + "}", [(0, 0, 0), (0, 0, -1)])
        put(x, 5, 19, "seafarers:harpoon[facing={bow}]")          # up in the bow against the bulwark (drawn 6 px forward)
        put(x // 2, 5, 20, "seafarers:ship_lantern")              # bow lanterns, right forward
    put(0, 6, 15, "seafarers:ship_bell[facing={stern}]")            # the bell, hung off the front of the mast
    put(0, int(NEST_Y) + 1, MAST_Z + 1, "seafarers:ship_bell[facing={stern}]")   # and one up in the crow's nest
    put(-1, int(NEST_Y), MAST_Z, "seafarers:flag_box[facing={port}]")   # flags to hoist, up at the masthead as in Sea of Thieves
    put(0, 4, 16, "minecraft:spruce_trapdoor[half=top,facing={bow}]")   # grating between the guns
    put(2, 5, 13, "seafarers:bucket_rack[facing={port}]")       # buckets by the hatch to the hold
    put(2, 1, 12, "seafarers:bucket_rack[facing={port}]")       # and in the hold
    put(*BOWSPRIT_HEEL, "seafarers:bowsprit[facing={bow}]",
        data={"length": 9.0, "raise": BOWSPRIT_RAISE, "bobstayDown": 1.65, "bobstayForward": 0.55})
    # something to stand on along it: the spar's top (it pivots a quarter block above the heel, 0.17 thick) through each
    # block it passes, out to its end, wherever the hull doesn't already fill the cell
    hx, hy, hz = BOWSPRIT_HEEL
    rise = math.radians(BOWSPRIT_RAISE)
    for z in range(hz + 1, hz + 10):
        t = (z + 0.5 - (hz + 0.5)) / math.cos(rise)
        if t > 9.0 - 0.3:
            break
        top = hy + 0.25 + t * math.sin(rise) + 0.17 / math.cos(rise)
        y = math.floor(top - 1e-3)
        sixteenths = max(1, min(16, round((top - y) * 16)))
        if (hx, y, z) not in cells and (hx, y, z) not in s.cells:
            put(hx, y, z, f"seafarers:bowsprit_spar[facing={{bow}},top={sixteenths}]")

    # ---------------- hold (floor y 1) ----------------
    # (the hull narrows toward the bow, so things against the sides sit aft where she is widest)
    # everything against the hull, an aisle three blocks wide down the middle
    put_multi(-2, 1, 11, "mattress", "{bow}", [(0, 0, 0), (0, 0, 1)])   # the bunk, where the brig used to be
    put(-2, 1, 12, "seafarers:supply_barrel[supply=grog]")      # grog, on its cradle
    put(-2, 1, 13, "seafarers:supply_barrel[supply=cannonballs]")
    put(-2, 1, 14, "seafarers:supply_barrel[supply=cannonballs]")
    put(-2, 1, 15, "seafarers:sea_chest[facing={starboard}]")   # clothing
    put(-2, 1, 16, "seafarers:sea_chest[facing={starboard}]")   # vanity
    put(2, 1, 13, "seafarers:supply_barrel[supply=planks]")
    put(2, 1, 14, "seafarers:supply_barrel[supply=food]")
    put(2, 1, 15, "seafarers:water_barrel[facing={port},level=3]")   # fresh water: refills itself
    put(2, 1, 16, "seafarers:stove[facing={port}]")              # galley stove
    put(-1, 1, 17, "seafarers:sea_chest[facing={stern}]")       # equipment, at the end of the hold
    put(0, 4, 12, "seafarers:ship_lantern[hanging=true,deck=true]")
    put(0, 4, 18, "seafarers:ship_lantern[hanging=true,deck=true]")

    # ---------------- fit check ----------------
    import fitcheck
    inside_ids = {"chest", "barrel", "stove", "flag_box", "sea_chest", "cask", "water_barrel", "supply_barrel", "bucket_rack", "mattress", "map_table", "voyage_table", "cannon",
                  "harpoon", "helm", "anchor", "sail_rope", "brace_rope", "ship_lantern", "binnacle", "red_carpet"}
    problems = fitcheck.check(s, cells, popped, half_width, inside_ids,
                              outside_ok={(0, int(NEST_Y) - 1, MAST_Z - 1),   # the ladder's way up through the nest floor
                                          (-4, 7, 9), (4, 7, 9),
                                          (-2, 5, 19), (2, 5, 19)})                # the harpoons stand into the rail's corner                  # side ladders' top rungs, in the rail openings
    print(f"fit check: {len(problems)} problem(s)")
    for p in problems:
        print("  " + p)
    s.problems = problems

    # ---------------- the voxel hull (palette ids are given out by build_all) ----------------
    s.set_hull(cells, lambda pos, cell: pos[1] <= 3 and any(m in ("bottom", "planks", "keel", "gilt", "trim")
                                                           for m in cell.values()))

    # hold cells that fill with bilge water: empty cells wholly inside the hull, under the main deck
    for z in range(10, 23):
        for y in range(1, int(MAIN_DECK)):
            for x in range(-3, 4):
                if s.occupied((x, y, z)):
                    continue
                if all(inside(x + dx, y + dy, z + dz) for dx in (-0.5, 0.5) for dy in (0.05, 0.7) for dz in (0.05, 0.95)):
                    s.bilge.append((x, y, z))
    # cells where the sea must not show: the inside of the hull below the main deck. Sable hides water a whole
    # block at a time, so a cell the planking cuts through is dry only if most of it is inside the hull: that
    # keeps the sea out of the hold without leaving a gap round the hull at the waterline.
    import shiplib
    s.dry = shiplib.dry_cells(inside, range(-4, 5), range(0, int(MAIN_DECK) + 1), range(0, 24))
    s.dry_mesh = shiplib.dry_mesh(inside, -4.5, 4.5, 0, MAIN_DECK + 0.5, 0, 24)
    s.respawn = (-1, 7, 7)
    s.hold = (1, int(MAIN_DECK) - 1)
    return s


if __name__ == "__main__":
    import build_all
    build_all.main()
