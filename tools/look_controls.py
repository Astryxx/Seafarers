"""Records the wheel and capstan turning in the client (server must be running: tools/restart.sh --fresh).

python tools/look_controls.py   -> shots/controls_wheel.mkv, shots/controls_capstan.mkv, contact sheets
"""
import subprocess
import sys
import threading
import time

from test_ship import ROOT, SEA, Log, at, rcon
import test_walk as tw

SHIP = tw.SHIP


def record(name, seconds, action):
    out = str(ROOT / "shots" / f"controls_{name}")
    rec = threading.Thread(target=lambda: subprocess.run(
        ["um", "win", "record", "--title", "Minecraft NeoForge", "--out", out, "--seconds", str(seconds), "--fps", "30", "--no-audio"],
        capture_output=True))
    rec.start()
    time.sleep(1.0)
    action()
    rec.join()
    # a contact sheet: one frame every half second
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", out + ".mkv", "-vf", "fps=2,scale=320:-1,tile=4x3", "-frames:v", "1",
                    out + "_sheet.png"])
    print(out + ".mkv")


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
    rcon("gamemode spectator Dev")
    # the wheel, seen from the helmsman's side: hard to starboard for 3 s, then back to port
    tw.view(0.9, 6.3, 6.6, -35, 25)
    record("wheel", 7, lambda: (rcon(at(*SHIP, "ship helm 1 50")), time.sleep(2.8), rcon(at(*SHIP, "ship helm -1 60"))))
    # the sail: let it all the way down, swing it to starboard, then haul it up
    rcon(at(*SHIP, "ship controls 0 0 0 true"))
    tw.view(-7, 9, 8, 40, -5)
    record("sail", 12, lambda: (rcon(at(*SHIP, "ship hoist 0 15")), time.sleep(3.5), rcon(at(*SHIP, "ship brace 0 starboard 12")),
                                time.sleep(3), rcon(at(*SHIP, "ship hoist 0 -15"))))
    # the map table, from the cabin door, riding along while she sails and turns
    rcon(at(*SHIP, "ship controls 0 0 0 true"))
    time.sleep(3)
    rcon("gamemode survival Dev", "effect give Dev minecraft:resistance 9999 4 true")
    tw.view(0, 3.05, 5.2, 180, 38)
    time.sleep(3)
    rcon(at(*SHIP, "ship controls 0.7 3 0 false"))
    time.sleep(4)
    record("map", 10, lambda: None)
    rcon(at(*SHIP, "ship controls 0 0 0 true"), "gamemode spectator Dev")
    # the capstan: heave for 2.5 s, let go (it runs back), then heave her all the way up
    tw.view(1.8, 7.8, 7.5, -145, 45)
    record("capstan", 14, lambda: (rcon(at(*SHIP, "ship capstan 12")), time.sleep(5), rcon(at(*SHIP, "ship capstan 30"))))


if __name__ == "__main__":
    sys.path.insert(0, str(ROOT / "tools"))
    main()
