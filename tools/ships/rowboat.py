"""The Rowboat: a small open boat rowed by one pair of oars, as in Sea of Thieves.

Same coordinates as the Sloop (x across the beam, y up from the keel, z from the stern toward the bow). The
floorboards are the top of the y 0 cells, so crew stand in the y 1 cells with the gunwale about knee high.
It is a ship of its own (a Sable sub-level) with oars instead of sails: the oars block is a helm that takes W/S
as well as A/D. The hook at the bow docks it at another ship's ladder.
"""
import math

from shiplib import Ship
from voxel import VoxelGrid

STERN, BOW = 0.0, 6.4
BEAM = 1.45                     # half-beam amidships
FLOOR = 1.0                     # top of the floorboards
SHEER = 1.9                     # top of the gunwale amidships


def plan(z):
    """Half-beam at the gunwale: a small square transom, full in the middle, a pointed bow."""
    if z < 2.4:
        return BEAM * (0.72 + 0.28 * (1 - ((2.4 - z) / 2.4) ** 2))
    if z > 3.4:
        t = (z - 3.4) / (BOW - 3.4)
        return BEAM * max(0.0, 1 - t ** 1.6)
    return BEAM


def keel(z):
    k = 0.25
    if z < 0.6:
        k += 0.4 * (0.6 - z)
    if z > 3.6:
        k += 1.25 * ((z - 3.6) / (BOW - 3.6)) ** 2
    return k


def sheer(z):
    h = SHEER
    if z > 4.0:
        h += 0.45 * ((z - 4.0) / (BOW - 4.0)) ** 2
    return h


def half_width(z, y):
    if z < STERN or z > BOW:
        return -1.0
    k = keel(z)
    if y < k:
        return -1.0
    b = plan(z)
    if y >= FLOOR:
        return b
    t = min(1.0, (y - k) / max(0.3, FLOOR - k))
    return b * (0.45 + 0.55 * (1 - (1 - t) ** 2))


def inside(x, y, z):
    return y <= sheer(z) and abs(x) <= half_width(z, y)


def build_hull():
    g = VoxelGrid()
    d = 0.25
    bounds = ((-2, 2), (-1, 3), (-1, 7))

    def body(x, y, z):
        return abs(x) <= half_width(z, y)

    def skin(x, y, z):
        if not inside(x, y, z):
            return False
        return not all(body(x + dx, y + dy, z + dz)
                       for dx, dy, dz in ((d, 0, 0), (-d, 0, 0), (0, -d, 0), (0, 0, d), (0, 0, -d)))

    def material(x, y, z):
        if y < 0.75:
            return "bottom"
        if y >= sheer(z) - 0.25:
            return "trim"          # a plain wooden gunwale cap, no gilt on a rowboat
        return "planks"

    g.fill(skin, material, bounds)
    # floorboards
    g.fill(lambda x, y, z: FLOOR - d <= y <= FLOOR and inside(x, y, z), lambda *_: "deck", bounds)
    # stern seat across the transom
    g.fill(lambda x, y, z: 0.25 <= z <= 1.0 and FLOOR + 0.75 <= y <= FLOOR + 1.0 and inside(x, y, z),
           lambda *_: "deck", bounds)
    # keel fin
    g.fill(lambda x, y, z: abs(x) < 0.26 and 0.4 <= z <= BOW - 0.6 and keel(z) - 0.25 <= y < keel(z),
           lambda *_: "keel", bounds)
    return g


# Sea of Thieves' three rowboats: plain, with a cannon in the bow, or with a harpoon gun in the bow
WEAPONS = {None: None, "cannon": "seafarers:cannon[facing={bow}]", "harpoon": "seafarers:harpoon[facing={bow}]"}


def build(variant=None):
    s = Ship("rowboat" if variant is None else f"rowboat_{variant}", crew=4)
    s.masts = 0
    # fields as in dev.seafarers.ship.ShipStats; oars=1 makes the helm a pair of oars
    s.stats = {"oars": 1, "maxSpeed": 4.2, "acceleration": 0.9, "coast": 0.6, "maxYawRate": 0.85, "yawAccel": 1.2, "pivotRate": 0.0,
               "wheelSpeed": 0.2, "waterDrag": 2.0, "waterYawDrag": 4.0, "keelGrip": 2.0,
               "floodPerHole": 0.0, "righting": 18.0, "rightingDamp": 6.0}
    cells = build_hull().cells()
    popped = {}

    def put(x, y, z, state, loot=None, data=None):
        lost = cells.pop((x, y, z), None)
        if lost:
            popped[(x, y, z)] = lost
        s.put(x, y, z, state, loot, data)

    put(0, 2, 0, "seafarers:ship_lantern")                    # on the stern seat
    if variant is None:
        put(0, 1, 3, "seafarers:oars[facing={bow}]")          # the rowing bench, oars out over the gunwales
        put(0, 1, 4, "seafarers:rowboat_hook[facing={bow}]")  # bow post with the docking hook
    else:
        # the gun stands on a mount in the bow; the rower sits a cell further aft and the hook moves to the stern
        put(0, 1, 2, "seafarers:oars[facing={bow}]")
        if variant == "cannon":
            # the gun on its carriage is two blocks long: a mount under both, the barrel reaching forward
            put(0, 1, 3, "seafarers:deck_planks")
            put(0, 1, 4, "seafarers:deck_planks")
            put(0, 2, 3, "seafarers:cannon[facing={bow},part=0]")
            put(0, 2, 4, "seafarers:cannon[facing={bow},part=1]")
        else:
            put(0, 1, 4, "seafarers:deck_planks")             # a timber mount, so the gun clears the bow
            put(0, 2, 4, WEAPONS[variant])
        put(0, 1, 1, "seafarers:rowboat_hook[facing={bow}]")

    import fitcheck
    problems = fitcheck.check(s, cells, popped, half_width, {"oars", "rowboat_hook", "ship_lantern", "cannon", "harpoon"}, outside_ok=set())
    print(f"fit check: {len(problems)} problem(s)")
    for p in problems:
        print("  " + p)
    s.problems = problems
    s.set_hull(cells, lambda pos, cell: pos[1] <= 1 and any(m in ("bottom", "planks", "keel", "trim") for m in cell.values()))
    import shiplib
    s.dry = shiplib.dry_cells(inside, range(-2, 3), range(0, 2), range(0, 7))
    s.dry_mesh = shiplib.dry_mesh(inside, -2.5, 2.5, 0, 2.5, 0, 7)
    s.respawn = (0, 1, 1)
    s.hold = (1, 1)
    s.bilge = [(0, 1, 2)]
    return s


if __name__ == "__main__":
    import build_all
    build_all.main()
