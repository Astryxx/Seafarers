"""Screenshots aboard a sloop at anchor in a real ocean (normal world): below decks looking up and out, for fog
and water glitches. OCEAN=1 tools/restart.sh --fresh && python tools/look_ocean.py [view ...]"""
import subprocess
import sys
import time

from test_ship import ROOT, rcon

X, Z, SURFACE = -416, 416, 62
VIEWS = {
    # x to starboard, y up, z toward the bow, from the helm (layout cell 0, 6, 8): the hold floor is at y -5,
    # the aft cabin floor at -3, the main deck at -2
    "hatch": "1 -4.5 4 0 -88",       # hold, at the foot of the stairs, looking up through the hatch
    "hatch_high": "1 -3.5 4 0 -88",  # the same a block higher (eye above the sea's surface)
    "lower": "0 -4.5 7 180 -5",      # hold, looking aft
    "mid_port": "0 -3 -1 -90 -5",    # aft cabin, looking out to port
    "mid_star": "0 -3 -1 90 -5",     # aft cabin, looking out to starboard
    "deck": "0 -1.5 4 30 -5",        # main deck
}


def main():
    names = sys.argv[1:] or list(VIEWS)
    for line in subprocess.run(["um", "win", "ps"], capture_output=True, text=True).stdout.splitlines():
        if "Minecraft" in line and "java" in line:
            subprocess.run(["um", "win", "kill", line.split()[0]], capture_output=True)
    rcon("time set 6000", "gamerule doDaylightCycle false", "weather clear", f"forceload add {X - 32} {Z - 32} {X + 32} {Z + 32}")
    joins = (ROOT / "run" / "server" / "console.log").read_text(errors="replace").count("Dev joined the game")
    import os  # noqa
    extra = os.environ.get("CLIENT_ARGS", "").split()
    subprocess.Popen(["cmd", "/c", str(ROOT / "gradlew.bat"), "runClientJoin", *extra, "--console=plain"], cwd=ROOT,
                     stdout=open(ROOT / "run" / "client-console.log", "w"), stderr=subprocess.STDOUT)
    for _ in range(400):
        if (ROOT / "run" / "server" / "console.log").read_text(errors="replace").count("Dev joined the game") > joins:
            break
        time.sleep(1)
    time.sleep(10)
    rcon(f"seafarers launch sloop {X} {SURFACE} {Z} north")
    time.sleep(3)
    at = f"execute positioned {X} {SURFACE + 2} {Z} run seafarers"
    rcon(f"{at} ship keep", f"{at} ship controls 0 0 0 true", "gamemode spectator Dev")
    time.sleep(3)
    for n in names:
        rcon(f"execute as Dev positioned {X} {SURFACE + 2} {Z} run seafarers ship view {VIEWS[n]}")
        time.sleep(3)
        subprocess.run(["um", "win", "shot", "--title", "Minecraft NeoForge", "--scale", "0.5", f"shots/ocean_{n}.png"],
                       cwd=ROOT, capture_output=True)
        print(f"  shot shots/ocean_{n}.png")


if __name__ == "__main__":
    main()
