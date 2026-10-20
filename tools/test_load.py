"""Stress tests on a normal (seed 5) world: many ships sailing at once, then two Galleons ramming head-on.
Reports the server's tick time (/neoforge tps) and whether it kept up.

RCON_PROPS=<server.properties> python tools/test_load.py    (the server needs debugCommands = true)
"""
import re
import subprocess
import sys
import time

ROOT = __import__("pathlib").Path(__file__).resolve().parent.parent
results = []


def r(*cmds):
    return subprocess.run([sys.executable, str(ROOT / "tools" / "rcon.py"), *cmds], capture_output=True, text=True).stdout


def at(x, z, cmd):
    return f"execute positioned {x} 64 {z} run seafarers {cmd}"


def check(name, ok, detail):
    results.append((name, ok))
    print(f"  {'PASS' if ok else 'FAIL'}  {name:40s} {detail}")


def mspt():
    """The overworld's mean tick time, ms (from /neoforge tps)."""
    out = r("neoforge tps")
    m = re.search(r"Overworld: [\d.]+ TPS \(([\d.]+) ms/tick\)", out)
    return float(m.group(1)) if m else float("nan")


def main():
    r("forceload add -900 340 -380 500", "time set 6000", "seafarers wind 90", "gamerule doDaylightCycle false")
    time.sleep(8)
    idle = mspt()
    # eight ships in three rows, all running west before the wind
    fleet = [("galleon", -450, 360), ("galleon", -520, 400), ("galleon", -590, 440),
             ("brigantine", -450, 400), ("brigantine", -520, 440), ("brigantine", -590, 360),
             ("sloop", -450, 440), ("sloop", -520, 360)]
    for name, x, z in fleet:
        r(f"seafarers launch {name} {x} 62 {z} west")
        time.sleep(1)
        r(at(x, z, "ship keep"), at(x, z, "ship controls 0 3 0 false"))
    time.sleep(10)
    samples = []
    for _ in range(6):
        time.sleep(4)
        samples.append(mspt())
    worst = max(samples)
    check("eight ships sailing: the server keeps up", worst < 40, f"tick {idle:.1f} ms idle, {min(samples):.1f}..{worst:.1f} ms sailing (50 = lag)")
    for _ in fleet:
        r(at(-700, 400, "ship remove"))   # (the nearest ship each time, wherever they've sailed)

    # two Galleons ramming head-on
    r("seafarers wind 0")   # (a beam wind: both make way)
    r("seafarers launch galleon -700 62 420 east")
    time.sleep(1)
    r("seafarers launch galleon -560 62 420 west")
    time.sleep(1)
    r(at(-700, 420, "ship keep"), at(-560, 420, "ship keep"), at(-700, 420, "ship controls 0 3 -45 false"),
      at(-560, 420, "ship controls 0 3 45 false"))
    worst, alive = 0.0, True
    t0 = time.time()
    while time.time() - t0 < 50:
        time.sleep(3)
        t = mspt()
        if t != t:   # NaN: no answer
            alive = False
            break
        worst = max(worst, t)
    statuses = r(at(-630, 420, "ship status"))
    log = (ROOT / "run" / "prodtest" / "logs" / "latest.log").read_text(errors="replace") if (ROOT / "run" / "prodtest" / "logs" / "latest.log").exists() else ""
    hung = "ServerHangWatchdog" in log or "single server tick took" in log
    check("two Galleons ramming: no hang", alive and not hung and worst < 50, f"worst tick {worst:.1f} ms; watchdog {'tripped' if hung else 'quiet'}")
    print("   ", statuses.strip().splitlines()[-1][:160] if statuses.strip() else "")
    r(at(-630, 420, "ship remove"), at(-630, 420, "ship remove"))
    failed = [n for n, ok in results if not ok]
    print(f"load: {len(results) - len(failed)}/{len(results)} passed")


if __name__ == "__main__":
    main()
