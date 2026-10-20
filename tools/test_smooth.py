"""How smoothly a ship moves on screen for a player aboard, in a real ocean (chunks generating as she sails).
Logs the client's frames and measures frame-to-frame jerk of the camera and the ship.

OCEAN=1 tools/restart.sh --fresh && python tools/test_smooth.py [turn]
"""
import re
import statistics
import subprocess
import sys
import time

from test_ship import ROOT, Log, check, rcon, results, status

X, Z = -416, 416
SURFACE = 62
CLIENT_LOG = ROOT / "run" / "client-console.log"


def at(cmd):
    return f"execute positioned {X} {SURFACE + 2} {Z} run seafarers {cmd}"


def frames(n):
    start = CLIENT_LOG.stat().st_size
    rcon(f"seafarers frames Dev {n}")
    time.sleep(n / 40 + 6)
    with CLIENT_LOG.open("rb") as f:
        f.seek(start)
        text = f.read().decode("utf-8", "replace")
    out = []
    for line in text.splitlines():
        m = re.search(r"PROBE frame (\d+) pt=(\S+) ship=(\S+) (\S+) (\S+) (\S+) cam=(\S+) (\S+) (\S+) camYaw=(\S+)", line)
        if m:
            # time: the game's own clock (ticks and the frame's partial tick), in seconds
            out.append([float(m[2]) / 20] + [float(m[i]) for i in range(3, 11)])
    return out


def jerk(series, times):
    """Average and worst change in per-frame velocity, as a share of the velocity (0 = perfectly even)."""
    v = [(b - a) / (tb - ta) for a, b, ta, tb in zip(series, series[1:], times, times[1:]) if tb > ta]
    if len(v) < 3:
        return 0, 0, 0
    mean = statistics.mean(abs(x) for x in v) or 1e-9
    d = [abs(b - a) / mean for a, b in zip(v, v[1:])]
    return statistics.mean(d), max(d), mean


def main():
    turn = len(sys.argv) > 1 and sys.argv[1] == "turn"
    for line in subprocess.run(["um", "win", "ps"], capture_output=True, text=True).stdout.splitlines():
        if "Minecraft" in line and "java" in line:
            subprocess.run(["um", "win", "kill", line.split()[0]], capture_output=True)
    log = Log()
    rcon("time set 6000", "seafarers wind 180", f"forceload add {X - 32} {Z - 32} {X + 32} {Z + 32}")
    joins = (ROOT / "run" / "server" / "console.log").read_text(errors="replace").count("Dev joined the game")
    subprocess.Popen(["cmd", "/c", str(ROOT / "gradlew.bat"), "runClientJoin", "--console=plain"], cwd=ROOT,
                     stdout=open(CLIENT_LOG, "w"), stderr=subprocess.STDOUT)
    for _ in range(400):
        if (ROOT / "run" / "server" / "console.log").read_text(errors="replace").count("Dev joined the game") > joins:
            break
        time.sleep(1)
    time.sleep(10)
    rcon(f"seafarers launch sloop {X} {SURFACE} {Z} north")
    time.sleep(2)
    rcon(at("ship keep"), "gamemode survival Dev", "effect give Dev minecraft:resistance 99999 4 true",
         f"execute as Dev positioned {X} {SURFACE + 2} {Z} run seafarers ship view 0 1 -2 0 0", at("ship controls 0 3 0 false"))
    time.sleep(10)
    if turn:
        rcon(f"execute as Dev at Dev run seafarers ship controls 0.7 3 0 false")
        time.sleep(6)
    f = frames(600)
    if len(f) < 50:
        check("frames logged", False, f"{len(f)} frames")
    else:
        t = [x[0] for x in f]
        fps = len(f) / (t[-1] - t[0])
        import math
        sp = [math.hypot(f[i + 1][1] - f[i][1], f[i + 1][3] - f[i][3]) / (t[i + 1] - t[i]) for i in range(len(f) - 1)]
        cv = statistics.pstdev(sp) / (statistics.mean(sp) or 1e-9)
        print(f"    ship speed on screen {statistics.mean(sp):.2f} blocks/s, frame-to-frame variation {cv * 100:.0f}%")
        check("she glides, not judders", cv < 0.12, f"speed varies {cv * 100:.0f}% frame to frame")
        cam_mean, cam_worst, cam_speed = jerk([x[6] for x in f], t)
        camz_mean, camz_worst, _ = jerk([x[8] for x in f], t)
        ship_mean, ship_worst, ship_speed = jerk([x[3] for x in f], t)
        yaw_mean, yaw_worst, yaw_rate = jerk([x[4] for x in f], t)
        print(f"    {len(f)} frames at {fps:.0f} fps; ship moving {ship_speed:.2f} blocks/s (z), turning {yaw_rate:.2f} deg/s")
        check("the ship moves evenly on screen", ship_mean < 0.25, f"jerk mean {ship_mean:.2f}, worst {ship_worst:.2f}")
        check("the camera aboard moves evenly", camz_mean < 0.25 and cam_mean < 0.6,
              f"jerk z {camz_mean:.2f} (worst {camz_worst:.2f}), x {cam_mean:.2f}")
        if turn:
            check("her heading turns evenly", yaw_mean < 0.25, f"jerk mean {yaw_mean:.2f}, worst {yaw_worst:.2f}")
    passed = sum(1 for _, ok in results if ok)
    print(f"smooth: {passed}/{len(results)} passed")


if __name__ == "__main__":
    main()
