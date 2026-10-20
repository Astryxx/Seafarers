"""Screenshots of the Sloop's fittings and stations, to check them by eye (needs no input; the chart screen is last
and stays open). tools/restart.sh --fresh && python tools/look_fixes.py [name ...]"""
import subprocess
import sys
import time

from test_ship import ROOT, SEA, SURFACE, at, rcon

A = (48, 140)
# camera spots from the helm (x to starboard, y up, z toward the bow; the helm is layout cell 0, 6, 8), yaw off the bow
VIEWS = {
    "lantern_off": "-0.5 0 -1.5 -45 30",     # the aft-deck lantern by the helm, put out
    "bell": "1.5 -2 10.5 -157 5",           # the bell on the mast
    "flag_box": "1.2 14 5.2 -70 35",        # the flag box up in the crow's nest
    "bowsprit": "0 -2 10 0 0",              # along the bowsprit from the deck
    "table": "0 -3 -1.8 180 50",            # the map table in the gallery
    "table_side": "-2.2 -3 -3.6 120 30",    # round its side
}


def shot(name):
    subprocess.run(["um", "win", "shot", "--title", "Minecraft NeoForge", "--scale", "0.5", f"shots/fix_{name}.png"],
                   cwd=ROOT, capture_output=True)
    print(f"  shot shots/fix_{name}.png")


def view(spec):
    rcon(f"execute as Dev positioned {A[0]} {SURFACE + 2} {A[1]} run seafarers ship view {spec}")
    time.sleep(2.5)


def main():
    names = sys.argv[1:]
    for line in subprocess.run(["um", "win", "ps"], capture_output=True, text=True).stdout.splitlines():
        if "Minecraft" in line and "java" in line:
            subprocess.run(["um", "win", "kill", line.split()[0]], capture_output=True)
    rcon("forceload add -16 -16 112 272", f"seafarers test sea {SEA[0]} {SEA[1]} {SEA[2]}", "seafarers wind 0", "time set 6000",
         "gamerule doDaylightCycle false")
    joins = (ROOT / "run" / "server" / "console.log").read_text(errors="replace").count("Dev joined the game")
    subprocess.Popen(["cmd", "/c", str(ROOT / "gradlew.bat"), "runClientJoin", "--console=plain"], cwd=ROOT,
                     stdout=open(ROOT / "run" / "client-console.log", "w"), stderr=subprocess.STDOUT)
    for _ in range(400):
        if (ROOT / "run" / "server" / "console.log").read_text(errors="replace").count("Dev joined the game") > joins:
            break
        time.sleep(1)
    time.sleep(10)
    rcon(f"seafarers launch sloop {A[0]} {SURFACE} {A[1]} north")
    time.sleep(3)
    rcon(at(*A, "ship keep"), at(*A, "ship controls 0 0 0 true"), "gamemode creative Dev")
    time.sleep(2)
    rcon(at(*A, "ship lanterns toggle"), at(*A, "ship maptable"))
    time.sleep(1)
    for n, spec in VIEWS.items():
        if not names or n in names:
            view(spec)
            shot(n)
    if not names or "stove" in names:
        rcon(at(*A, "ship stove 1500"))
        view("0 -4.9 4.5 27 25")
        shot("stove_raw")
        time.sleep(17)
        shot("stove_cooked")
        time.sleep(17)
        shot("stove_burnt")
    if not names or "stations" in names:
        for what in ("helm", "hoist", "cannon", "harpoon"):
            rcon(f"execute as Dev positioned {A[0]} {SURFACE + 2} {A[1]} run seafarers ship take Dev {what}")
            time.sleep(3)
            shot(f"station_{what}")
    if not names or "chart" in names:
        rcon(f"execute as Dev positioned {A[0]} {SURFACE + 2} {A[1]} run seafarers ship open map Dev")
        time.sleep(3)
        shot("chart")


if __name__ == "__main__":
    main()
