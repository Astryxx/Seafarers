"""Walks the dev client's player around the Sloop in the real game (needs the client; starts server and client).

tools/restart.sh --fresh && python tools/test_walk.py

The player is put at the foot of each staircase or ladder facing up it and walks forward (the client presses its own
movement keys, see dev.seafarers.client.TestDrive; nothing is sent to the keyboard). Where they end up is read
back in ship coordinates and checked.
"""
import re
import subprocess
import sys
import time

from test_ship import ROOT, SEA, Log, at, check, rcon, results, wait_for

SHIP = (48, 140)


def view(x, y, z, yaw, pitch=0):
    """Teleport Dev to ship coordinates (x, y, z), yaw relative to the bow (0 bow, 180 stern, 90 starboard)."""
    rcon(f"execute as Dev positioned {SHIP[0]} 110 {SHIP[1]} run seafarers ship view {x} {y - 7} {z - 8.5} {yaw} {pitch}")   # (the helm is at 0, 7, 8)


def where(log):
    log.new()
    rcon(at(*SHIP, "ship where Dev"))
    line = wait_for(log, r"PROBE where", 3) or ""
    m = re.search(r"where ([-\d.]+) ([-\d.]+) ([-\d.]+)", line)
    return tuple(float(v) for v in m.groups()) if m else None


def walk(log, start, yaw, ticks, sneak=False, jump=False, pitch=0):
    view(*start, yaw, pitch)
    time.sleep(1.2)
    rcon(f"seafarers drive Dev {ticks} {str(sneak).lower()} {str(jump).lower()}")
    time.sleep(ticks / 20 + 1.5)
    return where(log)


def main():
    for line in subprocess.run(["um", "win", "ps"], capture_output=True, text=True).stdout.splitlines():
        if "Minecraft NeoForge" in line:
            subprocess.run(["um", "win", "kill", line.split()[0]], capture_output=True)
    log = Log()
    rcon("forceload add -16 -16 112 272", f"seafarers test sea {SEA[0]} {SEA[1]} {SEA[2]}", "seafarers wind 0",
         "time set 6000", "gamerule doDaylightCycle false")
    time.sleep(2)
    rcon(f"seafarers sloop {SHIP[0]} 108 {SHIP[1]} west")
    time.sleep(4)
    rcon(at(*SHIP, "ship controls 0 0 0 true"))
    subprocess.Popen(["cmd", "/c", str(ROOT / "gradlew.bat"), "runClientJoin", "--console=plain"], cwd=ROOT,
                     stdout=open(ROOT / "run" / "client-console.log", "w"), stderr=subprocess.STDOUT)
    for _ in range(300):
        if "Dev joined the game" in (ROOT / "run" / "server" / "console.log").read_text(errors="replace"):
            break
        time.sleep(1)
    time.sleep(8)
    rcon("gamemode survival Dev", "effect give Dev minecraft:resistance 99999 4 true", "effect give Dev minecraft:saturation 99999 1 true")

    cases = [
        # name, start, yaw, ticks, sneak, test(end)
        ("main deck up the stairs to the aft deck", (2, 5, 12.6), 180, 40, False, lambda e: e[1] > 6.9),
        ("aft deck down the stairs to the main deck", (2, 7, 9.3), 0, 40, False, lambda e: e[1] < 5.1 and e[2] > 11),
        ("hold up the stairs to the cabin, standing", (1, 1, 13.4), 180, 50, False, lambda e: e[1] > 2.9 and e[2] < 10),
        ("hold up the stairs to the cabin, crouching", (1, 1, 13.4), 180, 120, True, lambda e: e[1] > 2.9 and e[2] < 10),
        ("cabin down the stairs into the hold", (1, 3, 8.5), 0, 50, False, lambda e: e[1] < 2.1 and e[2] > 12),
        ("cabin out through the door to the main deck", (0, 3, 8.2), 0, 40, False, lambda e: e[1] > 4.9 and e[2] > 10.5),
        ("main deck up the mast ladder to the crow's nest", (0, 5, 12.6), 0, 220, False, lambda e: e[1] > 20.9),
        ("side ladder up from the water to the aft deck", (-4.75, 2.6, 9.5), 90, 70, False, lambda e: e[1] > 6.9 and abs(e[0]) < 3.6),
    ]
    for name, start, yaw, ticks, sneak, ok in cases:
        end = walk(log, start, yaw, ticks, sneak)
        check(name, bool(end) and ok(end), f"ended at {end}")

    # the crow's nest: room to walk round the mast
    view(1.0, 21.1, 14.5, 0)   # on the nest floor, starboard of the mast, facing the bow
    time.sleep(1.5)
    a = where(log)
    rcon("seafarers drive Dev 30 false false")
    time.sleep(2.5)
    b = where(log)
    moved = a and b and abs(a[0] - b[0]) + abs(a[2] - b[2]) > 0.8 and b[1] > 20.9
    check("you can walk about in the crow's nest", bool(moved), f"{a} -> {b}")

    # pictures: the hold (water barrel, bilge water after a hole), the harpoon slits
    def shot(name, x, y, z, yaw, pitch):
        rcon("gamemode spectator Dev")
        view(x, y, z, yaw, pitch)
        time.sleep(3)
        subprocess.run(["um", "win", "shot", "--title", "Minecraft NeoForge", "--scale", "0.5", f"shots/walk_{name}.png"],
                       cwd=ROOT, capture_output=True)
    shot("water_barrel", 0.3, 1.6, 13.2, 75, 25)
    shot("harpoon_slit", 0, 5.6, 16.5, 0, 10)
    shot("nest", 0, 24.5, 12.0, 0, 55)
    rcon(at(*SHIP, "tune floodPerHole 0.15"), at(*SHIP, "ship hole 1"))
    time.sleep(10)
    shot("bilge", -0.6, 2.4, 14.6, 180, 40)
    rcon(at(*SHIP, "ship patch"), at(*SHIP, "tune floodPerHole 0.05"))

    failed = [n for n, ok in results if not ok]
    print(f"\nwalk: {len(results) - len(failed)}/{len(results)} passed" + (f"; failed: {', '.join(failed)}" if failed else ""))


if __name__ == "__main__":
    main()
