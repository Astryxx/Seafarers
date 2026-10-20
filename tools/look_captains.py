"""Screenshots of the ship choice screen and the quest table's captain controls (drives the client: run only when
nobody is using the PC). Needs a running dev server: tools/restart.sh --fresh && python tools/look_captains.py"""
import subprocess
import time

from test_ship import ROOT, SEA, SURFACE, at, rcon

A = (48, 140)


def shot(name):
    subprocess.run(["um", "win", "shot", "--title", "Minecraft NeoForge", "--scale", "0.5", f"shots/look_{name}.png"],
                   cwd=ROOT, capture_output=True)
    print(f"  shot shots/look_{name}.png")


def drive(*cmds):
    subprocess.run(["um", "win", "drive", "--proc", "java", *cmds], capture_output=True)


def main():
    rcon("forceload add -16 -16 112 272", f"seafarers test sea {SEA[0]} {SEA[1]} {SEA[2]}", "seafarers wind 0", "time set 6000")
    subprocess.Popen(["cmd", "/c", str(ROOT / "gradlew.bat"), "runClientJoin", "--console=plain"], cwd=ROOT,
                     stdout=open(ROOT / "run" / "client-console.log", "w"), stderr=subprocess.STDOUT)
    for _ in range(300):
        if "Dev joined the game" in (ROOT / "run" / "server" / "console.log").read_text(errors="replace"):
            break
        time.sleep(1)
    time.sleep(8)
    rcon("gamemode survival Dev", "effect give Dev minecraft:resistance 99999 4 true", "seafarers config showShipChoiceScreen true",
         "crew reset Dev")
    time.sleep(3)
    shot("choice")
    rcon("seafarers pick Dev 0 sloop", f"tp Dev {A[0]} {SURFACE + 3} {A[1]} 0 90", "seafarers useitem Dev")
    time.sleep(3)
    rcon(at(*A, "ship keep"), at(*A, "ship crew"), at(*A, "ship controls 0 0 0 true"))
    time.sleep(1)
    rcon(at(*A, "ship view 0 1 -3 0 10"))
    time.sleep(2)
    shot("aboard")
    rcon(at(*A, "ship open voyage Dev"))
    time.sleep(2)
    shot("table_voyages")
    # the tabs by keyboard (Tab moves between buttons, Enter presses): Crew is the first active button
    drive("focus", "key 0x09", "key 0x0D")
    time.sleep(1)
    shot("table_crew")
    # from the Crew tab: Voyages, then Ship
    drive("focus", "key 0x09", "key 0x09", "key 0x0D")
    time.sleep(1)
    shot("table_ship")
    drive("key 0x1B")
    rcon("seafarers config showShipChoiceScreen false")   # the setting is saved: leave it as the other tests expect


if __name__ == "__main__":
    main()
