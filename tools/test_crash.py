"""Crash damage and unbreakable ships: sails a Sloop flat out into a stone wall on the test sea and checks the
hull is holed where she hit, then that nobody (not even in creative) can break a ship block.
tools/restart.sh && python tools/test_crash.py"""
import re
import sys
import time

from test_ship import SEA, SURFACE, Log, at, check, launch, rcon, results, status, wait_for

A = (48, 100)
WALL_Z = 160


def main():
    log = Log()
    rcon("forceload add -16 -16 112 272", f"seafarers test sea {SEA[0]} {SEA[1]} {SEA[2]}", "seafarers wind 0")
    rcon(f"fill 36 {SURFACE - 6} {WALL_Z} 60 {SURFACE + 6} {WALL_Z + 2} stone")
    launch(log, "sloop", *A, "south")
    time.sleep(2)
    holes0 = int(status(log, *A).get("holes", 0))
    log.new()
    rcon(at(*A, "ship trybreak"))
    tried = wait_for(log, r"PROBE trybreak", 3) or ""
    check("ship blocks can't be broken, even in creative", "now=kept" in tried, tried.split("trybreak ", 1)[-1])
    rcon(at(*A, "ship controls 0 3 0 false"))
    hit = wait_for(log, r"PROBE crash at", 40)
    m = re.search(r"crash at ([\d.]+) m/s: struck at (\S+), (\d+) holes", hit or "")
    check("crashing into land holes the hull", bool(m) and int(m[3]) >= 1,
          f"{m[1]} m/s, struck at {m[2]}, {m[3]} holes" if m else "no crash seen")
    time.sleep(3)
    st = status(log, 48, WALL_Z - 12)
    check("the holes are there on the ship", int(st.get("holes", 0)) > holes0, f"holes {holes0} -> {st.get('holes')}")
    crashes = len([l for l in log.new() if "PROBE crash at" in l])
    check("grinding on the rock doesn't hole her every tick", crashes <= 1, f"{crashes} more crashes in 3 s")
    rcon(at(st["x"], st["z"], "ship remove"), f"fill 36 {SURFACE - 6} {WALL_Z} 60 {SURFACE + 6} {WALL_Z + 2} air")
    failed = [n for n, ok in results if not ok]
    print(f"{len(results) - len(failed)}/{len(results)} passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
