"""Helpers for writing ship layouts to src/main/resources/data/seafarers/ships/<name>.json.

The JSON is read by dev.seafarers.ship.ShipLayout: metadata (stats, masts, respawn, hold) and a list of cells
[x, y, z, blockstate, extra?] in ship coordinates. extra may hold "loot" (chest stock) and "data" (settings
for a ship part: sail size, bowsprit length, where the anchor cable runs).

A ship's voxel hull is kept apart (set_hull) and turned into voxel_hull blocks by build_all.py, which gives all
ships one shared shape palette.
"""
import json
import pathlib

OUT = pathlib.Path(__file__).resolve().parents[2] / "src" / "main" / "resources" / "data" / "seafarers" / "ships"


def dry_cells(inside, xs, ys, zs, threshold=0.45):
    """Cells (x centred, y and z from the cell's low corner, as in the ship scripts) whose cross-section at
    mid-height is mostly inside the hull, sampled on a 4 x 4 grid."""
    out = []
    offs = (-0.375, -0.125, 0.125, 0.375)
    for z in zs:
        for y in ys:
            for x in xs:
                n = sum(inside(x + a, y + 0.5, z + 0.5 + b) for a in offs for b in offs)
                if n >= threshold * 16:
                    out.append((x, y, z))
    return out


def dry_mesh(inside, x0, x1, y0, y1, z0, z1, step=0.25):
    """The exact dry volume of a hull, as boxes on the voxel grid (step 0.25, like the planking), for the
    client's water mask: water is hidden right up to the hull's outer surface and nowhere else. Boxes are in
    cell-index space (cell x covers [x, x+1) there, i.e. hull x + 0.5; y and z as they are)."""
    nx, ny, nz = (round((b - a) / step) for a, b in ((x0, x1), (y0, y1), (z0, z1)))
    occ = {(i, j, k) for i in range(nx) for j in range(ny) for k in range(nz)
           if inside(x0 + (i + 0.5) * step, y0 + (j + 0.5) * step, z0 + (k + 0.5) * step)}
    # runs along z, then grow them up through y, then across x where they match exactly
    boxes = {}
    for i in range(nx):
        for j in range(ny):
            k = 0
            while k < nz:
                if (i, j, k) in occ:
                    k1 = k
                    while (i, j, k1 + 1) in occ:
                        k1 += 1
                    boxes[(i, i, j, j, k, k1)] = True
                    k = k1 + 1
                else:
                    k += 1
    def grow(boxes, axis):
        out, done = [], set()
        order = sorted(boxes, key=lambda b: (b[0], b[2], b[4]))
        keys = set(order)
        for b in order:
            if b in done:
                continue
            cur = list(b)
            while True:
                nxt = list(cur)
                if axis == "y":
                    nxt[2] = nxt[3] = cur[3] + 1
                else:
                    nxt[0] = nxt[1] = cur[1] + 1
                nb = tuple(nxt[:2] + [cur[2], cur[3]] + cur[4:]) if axis == "x" else tuple(cur[:2] + nxt[2:4] + cur[4:])
                if nb in keys and nb not in done:
                    done.add(nb)
                    if axis == "y":
                        cur[3] += 1
                    else:
                        cur[1] += 1
                else:
                    break
            out.append(tuple(cur))
        return out
    unit = [(b[0], b[0], b[2], b[2], b[4], b[5]) for b in boxes]   # (i0,i1,j0,j1,k0,k1), one layer each
    merged = grow(unit, "y")
    # x-merge needs whole boxes to match: index by everything but x
    by = {}
    for b in merged:
        by.setdefault((b[2], b[3], b[4], b[5]), []).append(b[0])
    final = []
    for (j0, j1, k0, k1), xs in by.items():
        xs.sort()
        start = prev = xs[0]
        for x in xs[1:] + [None]:
            if x is not None and x == prev + 1:
                prev = x
                continue
            final.append((start, prev, j0, j1, k0, k1))
            if x is not None:
                start = prev = x
    r = lambda v: round(v, 3)
    return [[r(x0 + a * step + 0.5), r(y0 + c * step), r(z0 + e * step),
             r(x0 + (b + 1) * step + 0.5), r(y0 + (d + 1) * step), r(z0 + (f + 1) * step)] for a, b, c, d, e, f in final]


class Ship:
    def __init__(self, name, crew):
        self.name = name
        self.crew = crew
        # handling, see dev.seafarers.ship.ShipStats; anything left out keeps the default
        self.stats = {}
        self.masts = 1
        self.cells = {}
        self.extra = {}
        self.voxels = {}           # (x, y, z) -> {(a, b, c): material}
        self.buoyant = set()       # hull cells that count as buoyant planking
        self.respawn = (0, 0, 0)
        # every spot the crew can respawn at (one is picked at random each time); empty = just respawn
        self.respawns = []
        # the brig: its two inside corners and its door's lower half (ship coordinates); empty = no brig
        self.brig = []
        # how high the sea should stand above the lower-deck floor afloat (test_ship.py checks it)
        self.draft = (1.0, 2.0)
        # layers of the lower deck that fill with bilge water as the ship floods
        self.hold = (0, 0)
        # explicit lower-deck cells for bilge water (ship coordinates); empty = let the game work it out
        self.bilge = []
        # cells where Sable must keep the sea out (render and physics); empty = the bilge cells
        self.dry = []
        # the same, exactly (boxes from dry_mesh): what the client masks the water with
        self.dry_mesh = []
        self.problems = []

    def put(self, x, y, z, state, loot=None, data=None):
        self.cells[(x, y, z)] = state
        extra = {}
        if loot:
            extra["loot"] = loot
        if data:
            extra["data"] = data
        if extra:
            self.extra[(x, y, z)] = extra
        else:
            self.extra.pop((x, y, z), None)

    def clear(self, x, y, z):
        self.cells.pop((x, y, z), None)
        self.extra.pop((x, y, z), None)

    def occupied(self, pos):
        return pos in self.cells or pos in self.voxels

    def set_hull(self, voxels, is_buoyant):
        """voxels: (x, y, z) -> voxel cell; is_buoyant(pos, cell) says whether it counts as buoyant hull."""
        self.voxels = dict(voxels)
        self.buoyant = {pos for pos, cell in self.voxels.items() if is_buoyant(pos, cell)}

    def write(self, shape_index):
        """shape_index: voxel key -> palette id (from build_all)."""
        from voxel import key_of
        OUT.mkdir(parents=True, exist_ok=True)
        cells = dict(self.cells)
        for pos, cell in self.voxels.items():
            cells[pos] = f"seafarers:voxel_hull[shape={shape_index[key_of(cell)]},facing={{bow}}]"
        out = []
        for (x, y, z), state in sorted(cells.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            cell = [x, y, z, state]
            if (x, y, z) in self.extra:
                cell.append(self.extra[(x, y, z)])
            out.append(cell)
        data = {"name": self.name, "crew": self.crew, "masts": self.masts, "stats": self.stats,
                "respawn": list(self.respawn), "respawns": [list(c) for c in self.respawns],
                "brig": [list(c) for c in self.brig], "draft": list(self.draft), "hold": list(self.hold), "bilge": [list(c) for c in self.bilge],
                "dry": [list(c) for c in self.dry], "dryMesh": self.dry_mesh,
                "fit_problems": len(self.problems), "cells": out}
        path = OUT / f"{self.name}.json"
        path.write_text(json.dumps(data, separators=(",", ":")))
        print(f"{self.name}: {len(out)} blocks, {len(self.voxels)} voxel cells, fit problems: {len(self.problems)}")
        for p in self.problems:
            print("  " + p)
