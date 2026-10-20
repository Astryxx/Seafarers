"""How steady she sails in a real ocean (deep water, normal world): yaw wobble going straight, and the turn rate
and wobble in a turn.

OCEAN=1 tools/restart.sh --fresh && python tools/test_ocean.py [sloop|brigantine|galleon]
"""
import json
import math
import sys
import re
import statistics
import time

from test_ship import ROOT, Log, check, rcon, results, status

X, Z = -416, 416   # deep ocean in the seed-5 world
SURFACE = 62


def at(cmd):
    return f"execute positioned {X} {SURFACE + 2} {Z} run seafarers {cmd}"


def sample(log, seconds, every=0.25):
    """Yaw (degrees, unwrapped), speed and position, sampled every so often."""
    out = []
    last = None
    unwrap = 0.0
    end = time.time() + seconds
    while time.time() < end:
        st = status(log, X, Z)
        if "yaw" in st:
            y = st["yaw"]
            if last is not None:
                d = y - last
                if d > 180:
                    unwrap -= 360
                elif d < -180:
                    unwrap += 360
            last = y
            # timed by the server's clock: RCON round trips would add their own jitter to the turn rate
            out.append((int(st["gt"]) / 20.0 if "gt" in st else time.time(), y + unwrap, float(st.get("speed", 0)), float(st.get("roll", 0)), st["x"], st["z"]))
        time.sleep(every)
    return out


def rates(samples):
    r = []
    for (t0, y0, *_), (t1, y1, *_) in zip(samples, samples[1:]):
        if t1 > t0:
            r.append((y1 - y0) / (t1 - t0))
    return r


def wobble(samples):
    """How much the turn rate shakes about its trend (degrees per second), and how often it changes sign. The trend
    is taken out first: a turn rate easing off steadily as she bears away and slows isn't a wobble."""
    r = rates(samples)
    if len(r) < 3:
        return 0.0, 0
    flips = sum(1 for a, b in zip(r, r[1:]) if a * b < 0 and abs(a) > 0.3 and abs(b) > 0.3)
    n = len(r)
    xm, ym = (n - 1) / 2, statistics.mean(r)
    slope = sum((i - xm) * (v - ym) for i, v in enumerate(r)) / sum((i - xm) ** 2 for i in range(n))
    return statistics.pstdev([v - (ym + slope * (i - xm)) for i, v in enumerate(r)]), flips


def main():
    global X, Z
    layout = sys.argv[1] if len(sys.argv) > 1 else "sloop"
    stats = json.loads((ROOT / "src/main/resources/data/seafarers/ships" / f"{layout}.json").read_text()).get("stats", {})
    k = stats.get("maxYawRate", 0.16) / 0.16   # the bigger ships turn slower by design
    print(f"Testing the {layout} in the ocean")
    log = Log()
    # west, with the wind astern: open water for 400 blocks that way (a fast ship sailing north runs aground)
    rcon(f"forceload add {X - 240} {Z - 96} {X + 32} {Z + 64}", "time set 6000", "seafarers wind 90")
    time.sleep(3)
    # kept loaded in the same breath as she's launched: with nobody near, Sable would unload her straight away
    rcon(f"seafarers launch {layout} {X} {SURFACE} {Z} west", at("ship keep"))
    time.sleep(2)
    rcon(at("ship controls 0 1 0 false"))
    print("  getting under way...")
    time.sleep(20)
    st = status(log, X, Z)
    X, Z = int(st["x"]), int(st["z"])
    straight = sample(log, 20)
    X, Z = int(straight[-1][4]), int(straight[-1][5])
    sd, flips = wobble(straight)
    speed = statistics.mean(s[2] for s in straight)
    heading = straight[-1][1] - straight[0][1]
    check("she holds a straight course", sd < 0.6 and flips <= 2,
          f"turn-rate wobble {sd:.2f} deg/s, {flips} reversals, heading drift {heading:+.1f} deg, speed {speed:.1f} m/s")
    rcon(at("ship controls 0.5 1 0 false"))
    time.sleep(6)
    st = status(log, X, Z)
    X, Z = int(st["x"]), int(st["z"])
    turning = sample(log, 12)
    X, Z = int(turning[-1][4]), int(turning[-1][5])
    r = rates(turning)
    sd, flips = wobble(turning)
    rate = statistics.mean(r) if r else 0
    check("she turns smoothly", sd < 0.8 and flips == 0, f"turn rate {abs(rate):.1f} deg/s, wobble {sd:.2f}, {flips} reversals, "
          f"speed {turning[0][2]:.1f} -> {turning[-1][2]:.1f} m/s")
    # (a heavier ship turns slower, and loses more way in a long turn, which slows her turn further)
    check("turns are heavy, not sharp", 2.0 * k ** 1.5 < abs(rate) < 9.0 * k, f"half wheel: {abs(rate):.1f} deg/s")
    rcon(at("ship controls 1 1 0 false"))
    time.sleep(6)
    st = status(log, X, Z)
    X, Z = int(st["x"]), int(st["z"])
    full = sample(log, 8)
    X, Z = int(full[-1][4]), int(full[-1][5])
    r = rates(full)
    print(f"    full wheel: {abs(statistics.mean(r)) if r else 0:.1f} deg/s")
    rcon(at("ship controls 0 1 0 false"))
    t0 = time.time()
    settle = sample(log, 15)
    X, Z = int(settle[-1][4]), int(settle[-1][5])
    r = rates(settle)
    after = [abs(v) for v in r[len(r) // 2:]]
    check("she straightens out without overshooting", after and max(after) < 1.5 and min(r) > -3 if r else False,
          f"turn rate {r[0] if r else 0:.1f} -> {statistics.mean(after) if after else 0:.2f} deg/s, least {min(r) if r else 0:.1f}")
    rcon(at("ship remove"))
    passed = sum(1 for _, ok in results if ok)
    print(f"ocean ({layout}): {passed}/{len(results)} passed")


if __name__ == "__main__":
    main()
