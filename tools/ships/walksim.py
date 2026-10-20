"""Walks a player around a ship using the game's own collision boxes.

1. In game (dev server): /seafarers ship dumpshapes   (writes run/server/ship_shapes.json)
2. python tools/ships/walksim.py [path]

A player is a 0.6 x 1.8 box (1.5 when crouching). It moves 1/8 block at a time, steps up at most 0.6, drops any
height, and climbs anything climbable. Starting beside the helm, it finds every place a player can get to
standing up, and every place that needs crouching; then it reports, by area, what can't be reached, and which
passages are too low to walk through upright. Positions are in ship coordinates (x to starboard, y up, z toward
the bow), as in the ship scripts.
"""
import json
import pathlib
import sys
from collections import deque

ROOT = pathlib.Path(__file__).resolve().parents[2]
STEP = 0.125
HALF = 0.3
TALL, CROUCH = 1.8, 1.5
EPS = 1e-6


def load(path):
    cells = json.loads(pathlib.Path(path).read_text())
    boxes, climb, blocks = [], set(), {}
    for c in cells:
        x, y, z = c["x"], c["y"], c["z"]
        blocks[(x, y, z)] = c["block"]
        if c["climbable"]:
            climb.add((x, y, z))
        for b in c["boxes"]:
            # ship cells are centred on x (cell x covers x-0.5..x+0.5); y and z from the cell's low corner
            boxes.append((x - 0.5 + b[0], y + b[1], z + b[2], x - 0.5 + b[3], y + b[4], z + b[5]))
    return boxes, climb, blocks


class Space:
    def __init__(self, boxes):
        self.grid = {}
        for b in boxes:
            for gx in range(int(b[0] // 1) - 1, int(b[3] // 1) + 1):
                for gz in range(int(b[2] // 1) - 1, int(b[5] // 1) + 1):
                    self.grid.setdefault((gx, gz), []).append(b)

    def near(self, x, z):
        out = []
        for gx in (int(x // 1) - 1, int(x // 1), int(x // 1) + 1):
            for gz in (int(z // 1) - 1, int(z // 1), int(z // 1) + 1):
                out += self.grid.get((gx, gz), ())
        return out

    def footprint(self, x, z):
        return [b for b in self.near(x, z) if b[0] < x + HALF - EPS and b[3] > x - HALF + EPS
                and b[2] < z + HALF - EPS and b[5] > z - HALF + EPS]

    def support(self, x, z, h, rise=0.6):
        """Where feet come to rest at (x, z) from feet height h: the highest top no more than `rise` above h."""
        tops = [b[4] for b in self.footprint(x, z) if b[4] <= h + rise + EPS]
        return max(tops) if tops else None

    def clear(self, x, z, feet, tall):
        return not any(b[1] < feet + tall - EPS and b[4] > feet + EPS for b in self.footprint(x, z))


def q(v):
    return round(v / STEP) * STEP


def simulate(path, start_xz=(0.0, 7.4)):
    boxes, climb, blocks = load(path)
    sp = Space(boxes)

    def in_ladder(x, z, feet):
        for cx, cy, cz in climb:
            if abs(x - cx) < 0.5 + HALF and cz - HALF < z < cz + 1 + HALF and cy - 1.8 < feet < cy + 1:
                return True
        return False

    # start: on the aft deck by the helm
    sx, sz = start_xz
    h0 = sp.support(sx, sz, 30, rise=0)
    start = (q(sx), q(sz), round(h0, 4))
    # state -> needs crouch (False if reachable standing)
    best = {start: False}
    todo = deque([start])
    while todo:
        x, z, h = todo.popleft()
        crouched = best[(x, z, h)]
        moves = []
        for dx, dz in ((STEP, 0), (-STEP, 0), (0, STEP), (0, -STEP)):
            nx, nz = q(x + dx), q(z + dz)
            ladder = in_ladder(nx, nz, h)
            nh = sp.support(nx, nz, h)
            if nh is None:
                if ladder:
                    nh = h       # hanging on the ladder
                else:
                    continue     # off the edge of the ship
            elif nh < h - 1.5 and not ladder:
                continue         # don't go jumping overboard / down hatches here; stairs and ladders do that
            moves.append((nx, nz, round(nh, 4)))
        if in_ladder(x, z, h):
            for dy in (0.25, -0.25):
                nh = h + dy
                below = sp.support(x, z, nh, rise=0)
                moves.append((x, z, round(max(nh, below) if below is not None and below > nh else nh, 4)))
        for m in moves:
            nx, nz, nh = m
            # the body must fit along the way: at the new spot, with the feet at the higher of the two heights
            feet = max(nh, h) if nh <= h + 0.6 + EPS else nh
            if sp.clear(nx, nz, feet, TALL) and sp.clear(nx, nz, nh, TALL):
                need = crouched
            elif sp.clear(nx, nz, feet, CROUCH) and sp.clear(nx, nz, nh, CROUCH):
                need = True
            else:
                continue
            if m not in best or (best[m] and not need):
                best[m] = need
                todo.append(m)
    return best, blocks, sp


def report(path):
    best, blocks, sp = simulate(path)
    upright = {(x, z, h) for (x, z, h), c in best.items() if not c}
    crouch = {(x, z, h) for (x, z, h), c in best.items() if c}
    print(f"{len(upright)} spots reachable upright, {len(crouch)} only crouching")

    # free floor nobody can get to: walkable spots (player fits standing) not reached at all
    def cell(v):
        return int(v // 1)

    reached_cells = {(round(x), int(h // 1) if h % 1 > 0.01 else int(h) - 1, cell(z)) for x, z, h in upright}
    crouch_cells = {(round(x), int(h // 1) if h % 1 > 0.01 else int(h) - 1, cell(z)) for x, z, h in crouch} - reached_cells
    print("\ncells you can only reach crouching (x, floor cell y, z):")
    for c in sorted(crouch_cells, key=lambda c: (c[1], c[2], c[0])):
        print("  ", c, blocks.get(c, ""))
    # unreachable standable floor: sample every floor cell top
    unreach = []
    for (x, y, z), b in blocks.items():
        for ox, oz in ((0.5, 0.5),):
            px, pz = x, z + 0.5
            h = sp.support(px, pz, y + 1.0, rise=0.0)
            if h is None or abs(h - (y + 1)) > 0.3:
                continue
            if not sp.clear(px, pz, h, TALL):
                continue
            key = (q(px), q(pz), round(h, 4))
            if key not in best and (x, y + 1, z) not in blocks or (x, y + 1, z) in blocks and blocks[(x, y + 1, z)] in ("red_carpet",):
                if key not in best:
                    unreach.append((x, y, z, b))
    print(f"\nfloor you can stand on but can't get to ({len(unreach)}):")
    for u in sorted(set(unreach), key=lambda c: (c[1], c[2], c[0])):
        print("  ", u)
    return best


if __name__ == "__main__":
    report(sys.argv[1] if len(sys.argv) > 1 else ROOT / "run" / "server" / "ship_shapes.json")


AREAS = {  # name: (floor height, z from, z to) in ship coordinates
    "hold": (1.0, 10, 19), "cabin": (3.0, 4, 9.9), "gallery": (3.0, 0, 3.8), "main deck": (4.0, 10, 23),
    "aft deck": (6.0, 3.8, 10), "crow's nest": (20.0, 12, 17),
}


def areas(path):
    best, blocks, sp = simulate(path)
    rows = []
    for name, (floor, z0, z1) in AREAS.items():
        total = up = cr = 0
        missing = []
        x = -4.5
        while x <= 4.5:
            z = z0
            while z <= z1:
                px, pz = q(x), q(z)
                h = sp.support(px, pz, floor + 0.3, rise=0)
                if h is not None and abs(h - floor) < 0.3 and sp.clear(px, pz, h, CROUCH):
                    total += 1
                    key = (px, pz, round(h, 4))
                    if key in best:
                        if best[key]:
                            cr += 1
                        else:
                            up += 1
                    else:
                        missing.append((px, pz))
                z += 0.5
            x += 0.5
        rows.append((name, total, up, cr, missing))
        print(f"{name:12s} floor spots {total:4d}: upright {up:4d}, crouching only {cr:4d}, unreachable {len(missing):4d}"
              + (f"  e.g. {missing[:4]}" if missing else ""))
    return rows
