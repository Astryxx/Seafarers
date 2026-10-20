"""The ship's bed with a real player: needs the dev client (starts server and client itself).

tools/restart.sh --fresh && python tools/test_bed.py

Dev sleeps in the sloop's mattress at night (the night is skipped), the sloop sails off, Dev dies and comes
back on the mattress; then Dev dies 2000 blocks away and still wakes aboard (crewed ships stay loaded).
Also takes screenshots of the hull at the waterline, the aft-deck stairs, the flag and the rowboats.
"""
import re
import subprocess
import sys
import time

from test_ship import ROOT, SEA, Log, at, check, rcon, results, status, wait_for


def rcon_out(cmd):
    return subprocess.run([sys.executable, str(ROOT / "tools" / "rcon.py"), cmd], capture_output=True, text=True).stdout


def shot(name, view):
    rcon(f"execute as Dev at Dev run seafarers ship view {view}")
    time.sleep(3)
    subprocess.run(["um", "win", "shot", "--title", "Minecraft NeoForge", "--scale", "0.5", f"shots/bed_{name}.png"],
                   cwd=ROOT, capture_output=True)
    print(f"  shot shots/bed_{name}.png")


def main():
    for line in subprocess.run(["um", "win", "ps"], capture_output=True, text=True).stdout.splitlines():
        if "Minecraft NeoForge" in line:
            subprocess.run(["um", "win", "kill", line.split()[0]], capture_output=True)
    log = Log()
    rcon("forceload add -16 -16 112 272", f"seafarers test sea {SEA[0]} {SEA[1]} {SEA[2]}", "seafarers wind 0",
         "gamerule doDaylightCycle true", "time set 6000")
    time.sleep(2)
    rcon("seafarers sloop 48 108 140 west")
    time.sleep(4)
    rcon(at(48, 140, "ship controls 0 0 0 true"))
    subprocess.Popen(["cmd", "/c", str(ROOT / "gradlew.bat"), "runClientJoin", "--console=plain"], cwd=ROOT,
                     stdout=open(ROOT / "run" / "client-console.log", "w"), stderr=subprocess.STDOUT)
    for _ in range(300):
        if "Dev joined the game" in (ROOT / "run" / "server" / "console.log").read_text(errors="replace"):
            break
        time.sleep(1)
    time.sleep(8)
    rcon("gamemode survival Dev", "effect give Dev minecraft:resistance 99999 4 true", "tp Dev 48 115 140")
    time.sleep(2)

    # sleep at night: the night is skipped
    rcon("time set 18000")
    time.sleep(1)
    log.new()
    rcon(at(48, 140, "ship bed Dev"))
    used = wait_for(log, r"PROBE bed used", 3) or ""
    check("the mattress puts you to sleep at night", "sleeping=true" in used, used.split("PROBE ")[-1] or "nothing")
    time.sleep(7)
    day = int(re.search(r"(\d+)", rcon_out("time query daytime").split("time is")[-1])[1])
    check("sleeping aboard skips the night", day < 12000 or day > 23500, f"daytime {day}")

    # she sails off; Dev dies and comes back on the mattress
    rcon(at(48, 140, "ship controls 0 3 0 false"))
    time.sleep(10)
    st = status(log, 48 - 60, 140)
    st = status(log, st["x"], st["z"])
    log.new()
    rcon("kill Dev")
    time.sleep(1)
    rcon("seafarers respawn Dev")
    back = wait_for(log, r"PROBE respawned Dev aboard at", 5) or ""
    m = re.search(r"at (-?\d+), (-?\d+), (-?\d+)", back)
    d = ((int(m[1]) - st["x"]) ** 2 + (int(m[3]) - st["z"]) ** 2) ** 0.5 if m else 999
    check("you respawn on your bunk, wherever she's sailed", d < 14, f"{d:.1f} blocks from the ship's centre")
    rcon(at(st["x"], st["z"], "ship controls 0 0 0 true"))
    time.sleep(3)

    # far away: the ship is out of everyone's range, but crewed ships stay loaded
    rcon("gamemode spectator Dev", "tp Dev 2500 150 2500")   # spectating: a survival player in mid-air is kicked for flying
    time.sleep(12)
    loaded = "Unknown" not in rcon_out(f"execute positioned {st['x']:.0f} 110 {st['z']:.0f} run seafarers ship status")
    log.new()
    rcon("gamemode survival Dev", "kill Dev")
    time.sleep(1)
    rcon("seafarers respawn Dev")
    back = wait_for(log, r"PROBE respawned Dev aboard at", 5) or ""
    check("respawn aboard from 2500 blocks away", bool(back) and loaded, back.split("PROBE ")[-1] or "not aboard")
    time.sleep(6)

    # ladders: every rung climbable, and a clear way from the top of each side ladder onto the deck
    rcon(f"tp Dev {st['x']:.0f} 115 {st['z']:.0f}")
    time.sleep(1)
    log.new()
    rcon(at(st["x"], st["z"], "ship ladders"))
    lad = wait_for(log, r"PROBE ladders", 6) or ""
    m = re.search(r"rungs=(\d+) climbable=(\d+) tops=(\d+) clear=(\d+)", lad)
    check("every ladder rung can be climbed", bool(m) and m[1] == m[2] and int(m[1]) > 0, lad.split("PROBE ")[-1] or "nothing")
    check("the side ladders lead through the rail onto the deck", bool(m) and m[3] == m[4] == "2", lad.split("PROBE ")[-1] or "nothing")

    # pictures: the waterline, the aft-deck stairs, the flag, the rowboats
    rcon("gamemode spectator Dev", "time set 6000", "gamerule doDaylightCycle false")
    sx, sz = st["x"], st["z"]
    rcon(f"tp Dev {sx:.0f} 115 {sz:.0f}", at(sx, sz, "ship flag"))
    time.sleep(3)
    shot("waterline_bow", "-7 -3 20 135 25")
    shot("waterline_stern", "6 -3 -4 -40 25")
    shot("stairs_top", "1.2 0 0.5 160 70")
    shot("ladder_port", "-9 1 1 90 15")
    shot("flag", "0 16 30 180 -15")
    rcon(f"seafarers launch rowboat_cannon {sx - 10:.0f} 108 {sz + 10:.0f} west",
         f"seafarers launch rowboat_harpoon {sx - 10:.0f} 108 {sz + 16:.0f} west")
    time.sleep(7)
    rcon(f"tp Dev {sx - 10:.0f} 112 {sz + 12:.0f}")
    time.sleep(1)
    shot("rowboats", "4 2.5 -3 -60 25")

    failed = [n for n, ok in results if not ok]
    print(f"\nbed: {len(results) - len(failed)}/{len(results)} passed" + (f"; failed: {', '.join(failed)}" if failed else ""))


if __name__ == "__main__":
    main()
