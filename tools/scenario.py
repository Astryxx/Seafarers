"""Runs a timed list of console commands against the dev server and prints the PROBE lines it produced.

usage: python tools/scenario.py "0:cmd" "2.5:cmd" ... [--wait SECONDS]
Each step is "<seconds after start>:<command>". After the last step it waits --wait seconds (default 5)
and prints every log line containing PROBE (or an exception) written during the run.
"""
import pathlib
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parent.parent
LOG = ROOT / "run" / "server" / "console.log"


def main():
    args = sys.argv[1:]
    wait = 5.0
    if "--wait" in args:
        i = args.index("--wait")
        wait = float(args[i + 1])
        del args[i:i + 2]
    steps = []
    for a in args:
        t, cmd = a.split(":", 1)
        steps.append((float(t), cmd))
    start_size = LOG.stat().st_size
    t0 = time.time()
    for t, cmd in sorted(steps):
        delay = t0 + t - time.time()
        if delay > 0:
            time.sleep(delay)
        subprocess.run([sys.executable, str(ROOT / "tools" / "rcon.py"), cmd], stdout=subprocess.DEVNULL)
    time.sleep(wait)
    with LOG.open("rb") as f:
        f.seek(start_size)
        for line in f.read().decode("utf-8", "replace").splitlines():
            if "PROBE" in line or "Exception" in line or "Caused by" in line:
                print(line.split("]: ", 1)[-1])


if __name__ == "__main__":
    main()
