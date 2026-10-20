"""Builds every ship layout and the voxel shape palette they share.

python tools/ships/build_all.py

To add a ship: write tools/ships/<name>.py with a build() returning a shiplib.Ship (see sloop.py), add its
name to SHIPS, run this, then tools/test_ship.py <name>.
"""
import importlib
import json
import pathlib
import sys

from voxel import key_of, write_palette

SHIPS = ["sloop", "brigantine", "galleon", "rowboat", "rowboat_cannon", "rowboat_harpoon"]


PALETTE = pathlib.Path(__file__).with_name("palette_keys.json")


def main():
    ships = [importlib.import_module(name).build() for name in SHIPS]
    # palette ids are stored in every ship block placed in a world, so they must never change: the palette is
    # append-only (palette_keys.json), and shapes no ship uses any more keep their ids
    keys, index, buoyant = [], {}, set()
    if PALETTE.exists():
        for entry in json.loads(PALETTE.read_text()):
            k = tuple(sorted(((a, b, c), m) for a, b, c, m in entry))
            index[k] = len(keys)
            keys.append(k)
    for ship in ships:
        for pos, cell in sorted(ship.voxels.items()):
            k = key_of(cell)
            if k not in index:
                index[k] = len(keys)
                keys.append(k)
            if pos in ship.buoyant:
                buoyant.add(index[k])
    PALETTE.write_text(json.dumps([[[a, b, c, m] for (a, b, c), m in k] for k in keys], separators=(",", ":")))
    write_palette(keys, buoyant)
    for ship in ships:
        ship.write(index)
    # can the crew get around? every fitting has to be somewhere a player can walk up to
    import walkcheck
    stuck = False
    for ship in ships:
        report = walkcheck.analyse(ship.name, quiet=True)
        tight = sum(t for n, t in report["decks"].values())
        print(f"{ship.name}: walk check: {report['reachable']} places to stand, {tight} single-file, "
              f"unreachable fittings: {report['unreachable'] or 'none'}")
        stuck |= bool(report["unreachable"])
    if any(ship.problems for ship in ships) or stuck:
        sys.exit(1)


if __name__ == "__main__":
    main()
