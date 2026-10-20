"""Screenshots of all three ships, outside and below decks, to check them by eye (needs no input).

tools/restart.sh --fresh && python tools/look_ships.py [name ...]   (starts the client if it isn't running)
"""
import subprocess
import sys
import time

from test_ship import ROOT, SEA, SURFACE, at, rcon

SHIPS = {"sloop": (16, 60), "brigantine": (48, 140), "galleon": (80, 220)}
# camera spots from the helm (x to starboard, y up, z toward the bow), yaw off the bow, pitch down
VIEWS = {
    "sloop_side": ("sloop", "26 0 3 -90 8"),
    "sloop_hold": ("sloop", "0.5 -6 9.3 180 12"),
    "sloop_cabin": ("sloop", "0 -4 -2.5 0 8"),
    "brigantine_side": ("brigantine", "32 2 11 -90 8"),
    "brigantine_bow": ("brigantine", "16 6 40 -150 10"),
    "brigantine_deck": ("brigantine", "0 0.2 -2.3 0 12"),
    "brigantine_below": ("brigantine", "0.5 -5 19.5 180 12"),
    "brigantine_brig": ("brigantine", "1 -5 16 -150 10"),
    "brigantine_quarters": ("brigantine", "0 -5 6 180 5"),
    "galleon_side": ("galleon", "45 3 21 -90 8"),
    "galleon_bow": ("galleon", "22 6 58 -150 10"),
    "galleon_helm": ("galleon", "0 0.2 -2.3 0 15"),
    "galleon_deck": ("galleon", "2 -4 14 0 5"),
    "galleon_gundeck": ("galleon", "0 -8 30 180 8"),
    "galleon_lower": ("galleon", "0 -12 25 -150 12"),
    "galleon_quarters": ("galleon", "0 -4 7.5 180 8"),
}


def shot(name):
    subprocess.run(["um", "win", "shot", "--title", "Minecraft NeoForge", "--scale", "0.5", f"shots/ships_{name}.png"],
                   cwd=ROOT, capture_output=True)
    print(f"  shot shots/ships_{name}.png")


def client_running():
    out = subprocess.run(["um", "win", "ps"], capture_output=True, text=True).stdout
    return any("Minecraft" in line for line in out.splitlines())


def main():
    names = sys.argv[1:]
    rcon("forceload add -16 -16 112 272", f"seafarers test sea {SEA[0]} {SEA[1]} {SEA[2]}", "seafarers wind 0",
         "time set 6000", "gamerule doDaylightCycle false", "weather clear")
    if not client_running():
        joins = (ROOT / "run" / "server" / "console.log").read_text(errors="replace").count("Dev joined the game")
        subprocess.Popen(["cmd", "/c", str(ROOT / "gradlew.bat"), "runClientJoin", "--console=plain"], cwd=ROOT,
                         stdout=open(ROOT / "run" / "client-console.log", "w"), stderr=subprocess.STDOUT)
        for _ in range(400):
            if (ROOT / "run" / "server" / "console.log").read_text(errors="replace").count("Dev joined the game") > joins:
                break
            time.sleep(1)
        time.sleep(10)
    for layout, (x, z) in SHIPS.items():
        rcon(f"seafarers launch {layout} {x} {SURFACE} {z} north")
        time.sleep(2)
        rcon(at(x, z, "ship keep"), at(x, z, "ship controls 0 3 0 true"))
    rcon("gamemode spectator Dev")
    time.sleep(8)
    for n, (layout, spec) in VIEWS.items():
        if names and n not in names:
            continue
        x, z = SHIPS[layout]
        rcon(f"execute as Dev positioned {x} {SURFACE + 2} {z} run seafarers ship view {spec}")
        time.sleep(3)
        shot(n)


if __name__ == "__main__":
    main()
