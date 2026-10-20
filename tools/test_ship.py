"""Regression test for a ship layout: launches it on the test sea and checks every ship system.

usage: python tools/test_ship.py [layout] [--keep]     (default layout: sloop; dev server must be running)

Crew actions go through the real blocks (ropes, capstan, wheel, planks on holes) using a stand-in player, so
this checks the same code paths a player uses. Prints PASS/FAIL per check and exits non-zero on any failure.
Respawning aboard needs a real player and is checked separately (see README).
"""
import json
import pathlib
import re
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parent.parent
LOG = ROOT / "run" / "server" / "console.log"
SEA = (0, 100, 0)           # test sea origin; surface block y = 108
SURFACE = SEA[1] + 8

results = []
len_half = 11


def rcon(*cmds):
    subprocess.run([sys.executable, str(ROOT / "tools" / "rcon.py"), *cmds], stdout=subprocess.DEVNULL, check=True)


class Log:
    """New PROBE lines since the last call. wait_for() keeps lines it did not match for the next wait_for()."""
    def __init__(self):
        self.pos = LOG.stat().st_size
        self.pending = []

    def read(self):
        with LOG.open("rb") as f:
            f.seek(self.pos)
            data = f.read().decode("utf-8", "replace")
            self.pos = f.tell()
        return [l.split("]: ", 1)[-1] for l in data.splitlines() if "PROBE" in l or "Exception" in l]

    def new(self):
        lines = self.pending + self.read()
        self.pending = []
        return lines


def at(x, z, cmd):
    return f"execute positioned {x} {SURFACE + 2} {z} run seafarers {cmd}"


def status(log, x, z):
    log.new()
    rcon(at(x, z, "ship status"))
    time.sleep(0.4)
    out = {}
    for line in log.new():
        m = re.search(r"pos=\(([-\d.]+), ([-\d.]+), ([-\d.]+)\) yaw=([-\d.]+) pitch=([-\d.]+) roll=([-\d.]+)", line)
        if m and "status t=" in line:
            out.update(x=float(m[1]), y=float(m[2]), z=float(m[3]), yaw=float(m[4]), pitch=float(m[5]), roll=float(m[6]))
        if "PROBE status layout=" in line:
            for k, v in re.findall(r"(\w+)=(\S+)", line.split("PROBE status ", 1)[1]):
                out[k] = v
    return out


def mast(st, i=0):
    deploy, angle, health, fill = st[f"m{i}"].split("/")
    return float(deploy), float(angle), int(health), float(fill)


def check(name, ok, detail):
    results.append((name, ok))
    print(f"  {'PASS' if ok else 'FAIL'}  {name:34s} {detail}")


def launch(log, layout, x, z, bow):
    log.new()
    rcon(f"seafarers launch {layout} {x} {SURFACE} {z} {bow}")
    time.sleep(1)
    rcon(at(x, z, "ship keep"))     # Sable unloads ships with no player near; keep test ships loaded
    lines = log.new()
    if any("launch failed" in l for l in lines):
        raise SystemExit(f"could not launch {layout} at {x},{z}")


def wait_for(log, pattern, seconds):
    """Waits for a PROBE line matching pattern; lines after it stay queued for the next wait."""
    end = time.time() + seconds
    while time.time() < end:
        lines = log.new()
        for i, l in enumerate(lines):
            if re.search(pattern, l):
                log.pending = lines[i + 1:]
                return l
        time.sleep(0.5)
    return None


def yaw_delta(a, b):
    return (b - a + 540) % 360 - 180


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    layout = args[0] if args else "sloop"
    keep = "--keep" in sys.argv
    spec = json.loads((ROOT / "src/main/resources/data/seafarers/ships" / f"{layout}.json").read_text())
    helm_cell = next(c for c in spec["cells"] if c[3].startswith("seafarers:helm"))
    stats = spec.get("stats", {})
    global len_half
    len_half = (max(c[2] for c in spec["cells"]) + 1) // 2
    masts = spec.get("masts", 1)
    print(f"Testing {layout} ({masts} mast(s), crew {spec.get('crew')})")
    check("layout fit check", spec.get("fit_problems", 0) == 0, f"{spec.get('fit_problems', 0)} problem(s)")
    sys.path.insert(0, str(ROOT / "tools" / "ships"))
    import walkcheck
    walk = walkcheck.analyse(layout, quiet=True)
    check("the crew can reach every fitting", not walk["unreachable"],
          f"{walk['reachable']} places to stand; unreachable: {walk['unreachable'] or 'none'}")

    log = Log()
    rcon("forceload add -16 -16 112 272", f"seafarers test sea {SEA[0]} {SEA[1]} {SEA[2]}", "seafarers wind 0",
         "kill @e[type=seafarers:cannonball]")
    time.sleep(2)

    # ---- A: floating, controls, sailing, anchor ----
    A = (20, 40)
    launch(log, layout, *A, "south")
    time.sleep(12)
    st = status(log, *A)
    check("floats level", abs(st["roll"]) < 2 and abs(st["pitch"]) < 2, f"roll {st['roll']:.2f} pitch {st['pitch']:.2f}")
    draft = float(st["waterAboveFloor"])
    lo, hi = spec.get("draft", [1.0, 2.0])
    check("rides at her waterline", lo <= draft <= hi, f"sea {draft:.2f} above the hold floor (want {lo}..{hi}; Sable keeps the hold dry)")

    rcon(at(*A, "ship controls 0 0 0 true"))   # at anchor while the sails are tried, so she doesn't sail off
    for m in range(masts):
        rcon(at(*A, f"ship hoist {m} 7"))     # holding the cleat: 7 pulls lets her about halfway down
        time.sleep(2.5)   # (the sail rolls and swings there steadily)
        half = mast(status(log, *A), m)[0]
        rcon(at(*A, f"ship hoist {m} 10"))
        time.sleep(2.5)   # (the sail rolls and swings there steadily)
        full = mast(status(log, *A), m)[0]
        rcon(at(*A, f"ship hoist {m} -4"))
        time.sleep(2.5)   # (the sail rolls and swings there steadily)
        back = mast(status(log, *A), m)[0]
        check(f"mast {m}: hold the cleat to set/raise the sail", 0.35 < half < 0.65 and full == 1.0 and 0.6 < back < 0.8,
              f"half {half:.2f}, full {full:.2f}, hauled up to {back:.2f}")
        rcon(at(*A, f"ship brace {m} starboard 5"))
        time.sleep(2.5)   # (the sail rolls and swings there steadily)
        right = mast(status(log, *A), m)[1]
        rcon(at(*A, f"ship brace {m} port 10"))
        time.sleep(2.5)   # (the sail rolls and swings there steadily)
        left = mast(status(log, *A), m)[1]
        check(f"mast {m}: angle pulleys swing the sail", right > 0 > left, f"starboard {right:+.0f}, port {left:+.0f}")

    rcon(at(*A, "ship controls 0 3 0 false"))
    time.sleep(12)
    st = status(log, *A)
    top = stats.get("maxSpeed", 8.5)
    check("sails downwind", float(st["speed"]) > 0.75 * top, f"{float(st['speed']):.2f} m/s (top {top})")
    x, z = st["x"], st["z"]

    rcon(at(x, z, "ship capstan 1"))
    time.sleep(4)
    st = status(log, x, z)
    check("capstan drops anchor, ship stops", st["anchored"] == "true" and abs(float(st["speed"])) < 0.6,
          f"anchored {st['anchored']}, speed {float(st['speed']):.2f}")
    # hold the bars a couple of seconds, then let go: she's partway up, then runs back down
    # (a heavier anchor comes up slower: the Brigantine's and the Galleon's take longer than the Sloop's)
    weight = stats.get("anchorWeight", 1.0)
    rcon(at(x, z, "ship capstan 10"))
    time.sleep(2.2)
    mid = int(status(log, x, z)["anchorRaise"])
    time.sleep(2.5)
    st = status(log, x, z)
    back = int(st["anchorRaise"])
    check("heaving brings the anchor up steadily", 25 / weight <= mid <= 60 / weight, f"{mid}% after 2 s of heaving (anchor weight {weight})")
    check("let go too soon and it runs back down", back == 0 and st["anchored"] == "true", f"{back}% and anchored {st['anchored']} 2.5 s later")
    # held for 6 s: she's up after about 5, and the hold carrying on past that mustn't let her go again
    rcon(at(x, z, f"ship capstan {int(30 * weight)}"))
    time.sleep(6.5 * weight)
    st = status(log, x, z)
    check("capstan heaves anchor up (and holding on doesn't drop it again)", st["anchored"] == "false", f"anchored {st['anchored']}")

    # wheel: take it, steer to starboard, let go; then the turn
    rcon(at(x, z, "ship controls 0 2 0 false"))
    log.new()
    rcon(at(x, z, "ship helm 1 40"))
    taken = wait_for(log, r"helm taken=true", 3)
    released = wait_for(log, r"helm released", 5)
    wheel = float(re.search(r"wheel=([-\d.]+)", released)[1]) if released else 0
    reach = min(1.0, 40 * stats.get("wheelSpeed", 0.025))   # the bigger ships' wheels take longer to put over
    check("wheel: take, steer, let go", bool(taken) and wheel > 0.9 * reach and "helmsman=false" in (released or ""),
          f"wheel {wheel:.2f} after 2 s hard to starboard (expect {reach:.2f})")
    st0 = status(log, x, z)
    time.sleep(5)
    st1 = status(log, st0["x"], st0["z"])
    turned = yaw_delta(st0["yaw"], st1["yaw"])
    want = -30 * min(1.0, stats.get("maxYawRate", 0.16) / 0.16) * reach
    check("turns to starboard", turned < want, f"{turned:.0f} degrees in 5 s (negative = starboard; want under {want:.0f})")
    check("stays upright in the turn", abs(st1["roll"]) < 8, f"roll {st1['roll']:.1f}")
    rcon(at(st1["x"], st1["z"], "ship remove"))

    # ---- B: trim in a crosswind (wind blowing toward west, ship heading south) ----
    B = (70, 40)
    launch(log, layout, *B, "south")
    time.sleep(3)
    rcon("seafarers wind 90", at(*B, "ship controls 0 3 45 false"))
    time.sleep(10)
    good = status(log, *B)
    rcon(at(good["x"], good["z"], "ship controls 0 3 -45 false"))
    time.sleep(10)
    bad = status(log, good["x"], good["z"])
    gs, bs = float(good["speed"]), float(bad["speed"])
    check("trim matters (crosswind)", gs > 1.8 * bs and gs > 3, f"trimmed {gs:.2f} m/s, wrong way {bs:.2f} m/s")
    rcon("seafarers wind 0", at(bad["x"], bad["z"], "ship remove"))

    # ---- C: damage control on one ship ----
    C = (48, 60)
    launch(log, layout, *C, "south")
    time.sleep(8)
    # holes in the upper hull, above the waterline, let no water in (as in Sea of Thieves)
    rcon(at(*C, "ship controls 0 0 0 true"), at(*C, "ship hole 3 upper"))
    time.sleep(6)
    st = status(log, *C)
    check("holes above the waterline don't leak", int(st["holes"]) >= 3 and float(st["flood"]) == 0,
          f"{st['holes']} holes, water {float(st['flood']):.1f}%")
    rcon(at(*C, "ship patch"))
    time.sleep(1)
    rcon(at(*C, "ship hole 2"))
    time.sleep(8)
    st = status(log, *C)
    flood1 = float(st["flood"])
    filled = int(st["bilge"].split("/")[0])
    check("holes let water in", int(st["holes"]) >= 2 and flood1 > 2, f"{st['holes']} holes, water {flood1:.1f}%")
    check("bilge water shows in the hold", filled > 0, f"{st['bilge']} hold cells wet")
    rcon(at(*C, "ship bail 3"))
    time.sleep(0.3)
    flood2 = float(status(log, *C)["flood"])
    check("bailing removes water", flood2 < flood1 - min(8, flood1 * 0.8), f"{flood1:.1f}% -> {flood2:.1f}% (3 buckets)")
    rcon(at(*C, "ship patch"))
    time.sleep(1.5)
    a = float(status(log, *C)["flood"])
    time.sleep(3)
    st = status(log, *C)
    check("planks patch the holes", st["holes"] == "0" and float(st["flood"]) <= a + 0.01,
          f"{st['holes']} holes, water {a:.1f}% -> {float(st['flood']):.1f}%")
    rcon(at(*C, "ship bail 30"))
    time.sleep(2)

    # a large hole: twice the leak, two planks to patch; flooded, she goes down by the head
    rcon(at(*C, "tune floodPerHole 0.3"), at(*C, "ship hole 2 large"))
    # (watched until she's well flooded: the Galleon's second deck fills fast, and a ship full to the brim goes down)
    end = time.time() + 12
    while True:
        time.sleep(1)
        st = status(log, *C)
        if float(st["flood"]) > 45 or time.time() > end:
            break
    time.sleep(1.5)
    st = status(log, *C)
    bow_up = float(st["bowUp"])
    check("she goes down by the bow when flooded", bow_up < -0.8 and float(st["flood"]) > 30,
          f"bow {bow_up:+.1f} degrees with {float(st['flood']):.0f}% water")
    rcon(at(*C, "ship patch"), at(*C, "tune floodPerHole 0.05"))
    time.sleep(1.5)
    st = status(log, *C)
    check("large holes take two planks", st["holes"] == "0", f"{st['holes']} holes left")
    rcon(at(*C, "ship bail 30"))
    time.sleep(1)

    log.new()
    rcon(at(*C, "ship grog"))
    poured = wait_for(log, r"PROBE grog poured", 3) or ""
    check("the grog barrel fills bottles, and you get the empty back", poured.count("grog -> glass_bottle") == 3,
          poured.split("PROBE grog ")[-1] or "nothing")
    check("and buckets", "grog_bucket -> bucket" in poured, "bucket of grog drunk, bucket back" if "grog_bucket -> bucket" in poured else "no")
    check("too much grog brings on nausea", "nausea=true" in poured, "nausea after three bottles" if "nausea=true" in poured else "none")
    line = wait_for(log, r"PROBE grog drunk", 3) or ""
    m = re.search(r"drunk=(-?\d+) bucket=(\w+)", line)
    check("grog makes you drunk", bool(m) and int(m[1]) >= 3, f"drunkenness {m[1] if m else '?'} after 4 tankards")
    check("vomit is caught in an empty bucket", bool(m) and m[2] == "VOMIT", f"bucket holds {m[2] if m else '?'}")

    log.new()
    rcon(at(*C, "ship supplies"))
    time.sleep(0.5)
    lines = log.new()
    barrels = [l for l in lines if "PROBE barrel" in l]
    stores = [l for l in lines if "PROBE storage" in l]
    ok = barrels and all(int(re.search(r"got=(\d+)", l)[1]) > 0 and "slots=27" in l for l in barrels)
    got = [" ".join(re.search(r"barrel (\w+) got=(\d+)", l).group(1, 2)) for l in barrels]
    check("supply barrels are stocked storage", bool(ok), f"{len(barrels)} barrels: " + ", ".join(got))
    sizes = sorted({l.split("PROBE storage ")[1] for l in stores})
    check("sea chests hold a double chest, casks a barrel", "sea_chest slots=54" in sizes and "cask slots=27" in sizes, ", ".join(sizes))
    rcon(at(*C, "ship remove"))

    # ---- D: cannonballs into the side of a ship lying alongside, then she is sunk ----
    # gunner heading south: her port side (and its cannon) faces east, at the target's broadside
    G, T = (16, 170), (56, 170)
    launch(log, layout, *G, "south")
    launch(log, layout, *T, "south")
    time.sleep(8)
    rcon(at(*G, "ship controls 0 0 0 true"), at(*T, "ship controls 0 0 0 true"))
    time.sleep(3)
    log.new()
    for pitch in (0, 1, 2):
        rcon(at(*G, f"ship fire port cannonball {pitch}"))
        time.sleep(2.5)
    hits = [l for l in log.new() if "CANNONBALL hit" in l]
    st = status(log, *T)
    check("cannonballs hole the other ship", int(st["holes"]) > 0, f"{len(hits)} hits, target has {st['holes']} holes")

    rcon(at(*T, "ship crew"), "seafarers tune REPLACE_MIN 40", "seafarers tune REPLACE_MAX 70",
         at(*T, "tune floodPerHole 0.6"), at(*T, "ship hole 4"))
    sank = wait_for(log, r"PROBE sinking", 30)
    check("a flooded ship goes down", bool(sank), "sinking" if sank else "never sank")
    wreck = wait_for(log, r"PROBE wreck .* crew=([1-9])", 40)
    check("she is wrecked with her crew noted", bool(wreck), wreck.split("PROBE ")[1] if wreck else "no wreck")
    repl = wait_for(log, r"PROBE replacement", 5)
    check("the crew gets a new ship", bool(repl), repl.split("PROBE ")[1] if repl else "no replacement")
    rcon("seafarers tune REPLACE_MIN 150", "seafarers tune REPLACE_MAX 300")
    if repl:
        m = re.search(r"at (-?\d+), (-?\d+), (-?\d+)", repl)
        rcon(f"execute positioned {m[1]} {SURFACE + 2} {m[3]} run seafarers ship remove")

    # ---- E: chain shot at a ship whose sail faces the guns, then the mast is mended ----
    gun_z = min(c[2] for c in spec["cells"] if c[3].startswith("seafarers:cannon"))
    # heading east, lined up with the gunner's forward cannon; a tall ship (her helm on a deck high over her main deck,
    # as the Galleon's) further off, so a gun deck's guns can reach up to her sails
    tall_stern = helm_cell[1] - next(c[1] for c in spec["cells"] if c[3].startswith("seafarers:harpoon")) >= 3
    S = (min(80, 93 - len_half) if tall_stern else 56, G[1] + gun_z - len_half)
    # (commands for the gunner go to her helm: on a long ship the other ship's helm can be nearer her middle)
    G = (G[0], G[1] - len_half + helm_cell[2])
    launch(log, layout, *S, "east")
    time.sleep(8)
    rcon(at(*S, "ship controls 0 3 0 true"))
    time.sleep(3)
    st = status(log, *S)
    healths0 = [mast(st, m)[2] for m in range(masts)]
    # aim at the lower half of her mainsail: a little forward of her centre (she heads east), up where the sail
    # hangs (the shot flies in over her stern, so on a big ship it may tear an aftermost sail first)
    # aim at her aftmost sail, the first a shot from astern meets (she heads east: her bow is +x)
    yard = min((c for c in spec["cells"] if c[3].startswith("seafarers:sail_yard")), key=lambda c: c[2])
    rig = yard[4]["data"] if len(yard) > 4 else {}
    mid = float(st["helmY"]) + yard[1] - helm_cell[1] - rig.get("height", 10) / 2
    sail_x = float(st["helmX"]) + yard[2] - 1 - helm_cell[2]
    for dx, y in ((0.5, mid), (0.5, mid + 2), (0.5, mid - 2)):
        rcon(at(*G, f"ship fireat port chain_shot {sail_x + dx:.1f} {y:.1f} {float(st['helmZ']):.1f}"))
        time.sleep(2.5)
    healths1 = [mast(status(log, *S), m)[2] for m in range(masts)]
    hit = next((m for m in range(masts) if healths1[m] < healths0[m]), None)
    check("chain shot damages the mast", hit is not None, f"mast health {healths0} -> {healths1}")
    if hit is not None:
        rcon(at(*S, f"ship mendmast {hit}"))
        time.sleep(0.5)
        health2 = mast(status(log, *S), hit)[2]
        check("planks mend the mast", health2 > healths1[hit], f"mast {hit}: {healths1[hit]} -> {health2}")
    rcon("seafarers wind unlock")

    # ---- F: firebombs and cursed cannonballs (the gunner fires at the sail target, now anchored) ----
    rcon(at(*S, "ship patch"), at(*S, "ship controls 0 3 0 false"))
    time.sleep(2)
    st = status(log, *S)
    # where to hit her: her open deck midships (where the harpoons stand), or on a ship with a tall stern (the
    # Galleon's aft deck stands four blocks over her main deck) the deck by her helm, which a shot from astern can reach
    open_deck = next(c[1] for c in spec["cells"] if c[3].startswith("seafarers:harpoon"))
    tall = helm_cell[1] - open_deck >= 3
    deck = float(st["helmY"]) - 0.5 + (0 if tall else open_deck - helm_cell[1])
    aim_x = float(st["helmX"]) + 2 if tall else st["x"]
    aim_z = float(st["helmZ"]) if tall else st["z"]
    def fire_at(ammo, dy, tall_dy=0.1):
        nonlocal_aim = (aim_x, deck + tall_dy, aim_z)
        if tall:   # (she may be under way: aim at where her deck is now)
            now = status(log, *S)
            nonlocal_aim = (float(now["helmX"]) + 2, float(now["helmY"]) - 0.5 + tall_dy, float(now["helmZ"]))
        log.new()
        x, y, z = nonlocal_aim if tall else (aim_x, st['y'] + dy, aim_z)
        rcon(at(*G, f"ship fireat port {ammo} {x:.1f} {y:.1f} {z:.1f}"))
        time.sleep(3)
    fire_at("firebomb", 1.0)
    lit = wait_for(log, r"PROBE fire lit", 2)
    check("firebombs set the ship alight", bool(lit), lit.split("PROBE ")[1] if lit else "no fire")
    rcon(at(*S, "tune fire.BURN_THROUGH 0.6"), at(*S, "tune fire.SPREAD 0.4"))
    time.sleep(14)
    st2 = status(log, *S)
    check("fire spreads", int(st2["fires"]) > 1, f"{st2['fires']} fires after 14 s")
    burned = wait_for(log, r"PROBE fire burned through", 10)
    check("fire left burning eats through the hull", bool(burned), "hole burned" if burned else "no hole")
    rcon("seafarers tune fire.BURN_THROUGH 0.06", "seafarers tune fire.SPREAD 0.12")
    for _ in range(6):
        rcon(at(*S, "ship splash"))
        time.sleep(1.5)
        st3 = status(log, *S)
        if st3["fires"] == "0":
            break
    check("a thrown bucket of water puts fire out", st3["fires"] == "0", f"{st3['fires']} fires left")
    rcon(at(*S, "ship patch"), at(*S, "ship bail 30"))

    rcon(at(*S, "ship controls 0 3 0 false"))
    time.sleep(1)
    for _ in range(5):   # (the gunner's ship drifts from earlier recoil; a shot that drops short is fired again)
        fire_at("anchor_ball", 1.0)
        if status(log, *S)["anchored"] == "true":
            break
    check("anchorball drops her anchor", status(log, *S)["anchored"] == "true", "anchored")
    rcon(at(*S, "ship controls 0 3 0 true"))
    for _ in range(5):
        fire_at("rigging_ball", 1.0)
        if mast(status(log, *S))[0] == 0:
            break
    check("riggingball hauls up her sails", mast(status(log, *S))[0] == 0, f"sail {mast(status(log, *S))[0]:.2f}")
    for _ in range(5):
        fire_at("ballast_ball", 1.0)
        if int(status(log, *S)["ballast"]) > 0:
            break
    check("ballastball weighs her down", int(status(log, *S)["ballast"]) > 0, f"ballast {status(log, *S)['ballast']} ticks")
    rcon(at(*S, "ship patch"))

    # curses on people: a dummy (a villager that stands still) on the target's deck
    for ammo, effect in [("grog_ball", "seafarers:drunk"), ("jig_ball", "seafarers:jig"), ("venom_ball", "minecraft:poison")]:
        rcon("kill @e[tag=dummy]",
             f"summon villager {aim_x:.1f} {deck + 0.05:.1f} {aim_z:.1f} {{NoAI:1b,Tags:[\"dummy\"],Invulnerable:1b}}")
        time.sleep(1)
        fire_at(ammo, deck + 0.6 - st['y'], 1.0)
        hit = wait_for(log, r"PROBE cursed .*villager with " + ammo.upper(), 2)
        check(f"{ammo.replace('_ball', 'ball')} curses whoever it hits", bool(hit) and effect in hit,
              hit.split(": ", 1)[1] if hit else "missed")
    rcon("kill @e[tag=dummy]")

    # firing yourself out of a cannon (the gunner's own ship, so the recoil shows on it)
    log.new()
    rcon(at(*G, "ship gunner port"))
    climbed = wait_for(log, r"PROBE gunner climbed", 3)
    check("climb into an empty cannon", bool(climbed) and "in=true" in climbed, climbed.split("PROBE ")[1] if climbed else "nothing")
    launched = wait_for(log, r"PROBE gunner launched", 3)
    lf = dict(re.findall(r"(\w+)=(\S+)", launched)) if launched else {}
    check("fire yourself out of it", bool(lf) and float(lf["speed"]) > 1.5 and float(lf["along"]) > 0.95
          and lf["visible"] == "true" and lf["flying"] == "true" and lf["loaded"] == "NONE",
          launched.split("PROBE gunner ")[1] if launched else "nothing")
    kick = wait_for(log, r"PROBE recoil", 3)
    check("the shot kicks the ship back", bool(kick) and float(kick.split("=")[1]) > 0.02, kick.split("PROBE ")[1] if kick else "nothing")

    if not keep:
        rcon(at(*G, "ship remove"), at(*S, "ship remove"))

    failed = [n for n, ok in results if not ok]
    print(f"\n{layout}: {len(results) - len(failed)}/{len(results)} passed" + (f"; failed: {', '.join(failed)}" if failed else ""))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
