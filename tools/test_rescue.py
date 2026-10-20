"""Mermaids, freeing a ship that's run aground, and the ship status panel, with the real client (Dev).

tools/restart.sh --fresh && python tools/test_rescue.py   (starts the client if it isn't running)
"""
import re
import subprocess
import sys
import time

from test_ship import ROOT, SEA, SURFACE, Log, at, check, launch, rcon, results, status, wait_for

A = (48, 60)      # the mermaid test's sloop, heading south (her stern at z ~49, her bow at ~72)
B = (48, 150)     # the one that runs aground


def shot(name):
    subprocess.run(["um", "win", "shot", "--title", "Minecraft NeoForge", "--scale", "0.5", f"shots/rescue_{name}.png"],
                   cwd=ROOT, capture_output=True)
    print(f"  shot shots/rescue_{name}.png")


def where(log, x, z):
    log.new()
    rcon(at(x, z, "ship where Dev"))
    line = wait_for(log, r"PROBE where", 3) or ""
    m = re.search(r"where ([-\d.]+) ([-\d.]+) ([-\d.]+)", line)
    return tuple(float(v) for v in m.groups()) if m else None


def client():
    """Makes sure Dev is on the server: a client left over from before a server restart is closed and started again."""
    online = subprocess.run([sys.executable, str(ROOT / "tools" / "rcon.py"), "list"], capture_output=True, text=True).stdout
    if "Dev" in online.split("online:")[-1]:
        return
    for line in subprocess.run(["um", "win", "ps"], capture_output=True, text=True).stdout.splitlines():
        if "Minecraft NeoForge" in line:
            subprocess.run(["um", "win", "kill", line.split()[0]], capture_output=True)
    joins = (ROOT / "run" / "server" / "console.log").read_text(errors="replace").count("Dev joined the game")
    subprocess.Popen(["cmd", "/c", str(ROOT / "gradlew.bat"), "runClientJoin", "--console=plain"], cwd=ROOT,
                     stdout=open(ROOT / "run" / "client-console.log", "w"), stderr=subprocess.STDOUT)
    for _ in range(400):
        if (ROOT / "run" / "server" / "console.log").read_text(errors="replace").count("Dev joined the game") > joins:
            break
        time.sleep(1)
    time.sleep(10)


def main():
    log = Log()
    rcon("forceload add -16 -16 112 272", f"seafarers test sea {SEA[0]} {SEA[1]} {SEA[2]}", "seafarers wind 0",
         "time set 6000", "gamerule doDaylightCycle false", "weather clear")
    client()
    rcon("gamemode survival Dev", "effect give Dev minecraft:water_breathing 99999 0 true",
         "effect give Dev minecraft:resistance 99999 4 true", "effect give Dev minecraft:saturation 99999 1 true",
         "seafarers config mermaidDelaySeconds 3")

    # ---- mermaids ----
    launch(log, "sloop", *A, "south")
    time.sleep(5)
    rcon(at(*A, "ship controls 0 0 0 true"), at(*A, "ship crew Dev"))
    log.new()
    rcon(f"tp Dev {A[0]} {SURFACE - 0.6} {A[1] + 70} 0 10")   # well astern of her bow, facing on (south)
    came = wait_for(log, r"PROBE mermaid came for Dev", 15)
    check("a mermaid comes for a sailor far from their ship", bool(came), came.split("PROBE ")[1] if came else "never came")
    if came:
        m = re.search(r"at (-?\d+), (-?\d+), (-?\d+)", came)
        rcon(f"tp Dev {int(m[1]) + 0.5} {SURFACE + 0.45} {int(m[3]) - 3.5} 0 12")   # at the surface, looking at her
        time.sleep(2.5)
        shot("mermaid")
        log.new()
        rcon(f"tp Dev {m[1]} {SURFACE - 0.6} {m[3]}")     # swim up to her
        carried = wait_for(log, r"PROBE mermaid carried Dev", 5)
        time.sleep(1)
        w = where(log, *A)
        check("swimming to her takes them back aboard", bool(carried) and w is not None and w[1] > 2,
              f"{carried.split('PROBE ')[1] if carried else 'not carried'}; Dev at {w}")
    log.new()
    rcon(f"tp Dev {A[0] + 9} {SURFACE - 0.6} {A[1]}")      # in the water right beside her
    near = wait_for(log, r"PROBE mermaid came for Dev", 8)
    check("no mermaid for a sailor near their ship", not near, "none" if not near else near.split("PROBE ")[1])
    rcon("execute as Dev run crew leave")
    log.new()
    rcon(f"tp Dev {A[0]} {SURFACE - 0.6} {A[1] + 70}")
    alone = wait_for(log, r"PROBE mermaid came for Dev", 8)
    check("no mermaid for a sailor with no ship", not alone, "none" if not alone else alone.split("PROBE ")[1])
    rcon(at(*A, "ship remove"))

    # ---- freeing a ship that has run aground ----
    launch(log, "sloop", *B, "south")
    time.sleep(5)
    rcon(at(*B, "ship controls 0 0 0 true"), at(*B, "ship crew Dev"), "seafarers config freeShipCooldownMinutes 5")
    log.new()
    rcon(f"execute as Dev positioned {B[0]} {SURFACE + 2} {B[1]} run seafarers ship view -1 0 -1.5 0 0")
    time.sleep(1)
    out = subprocess.run([sys.executable, str(ROOT / "tools" / "rcon.py"), "execute as Dev run crew free"], capture_output=True, text=True).stdout
    check("an afloat ship can't be freed", "not aground" in out, out.strip().splitlines()[-1] if out.strip() else "?")
    # she sails slowly onto a sandbar across her bow, and stays pressed against it
    st = status(log, *B)
    bow = float(st["z"]) + 23   # (clear of her bowsprit)
    bar = f"{B[0] - 8} {SURFACE - 6} {int(bow)} {B[0] + 8} {SURFACE + 1} {int(bow) + 1}"
    rcon(f"fill {bar} minecraft:sand", "seafarers config crashDamage false", at(*B, "ship controls 0 1 0 false"))
    for _ in range(20):
        time.sleep(1)
        st = status(log, *B)
        if st.get("aground") == "true":
            break
    check("sailed onto the sandbar, she's aground", st.get("aground") == "true", f"aground={st.get('aground')} speed {st.get('speed')}")
    before = (float(st["x"]), float(st["z"]))
    rcon(f"execute as Dev positioned {before[0]} {SURFACE + 2} {before[1]} run seafarers ship view -1 0 -1.5 0 0")
    time.sleep(1)
    log.new()
    rcon("execute as Dev run crew free")
    freed = wait_for(log, r"PROBE freed", 4)
    time.sleep(3)
    st = status(log, before[0], before[1])
    moved = ((float(st["x"]) - before[0]) ** 2 + (float(st["z"]) - before[1]) ** 2) ** 0.5
    check("freeing tows her to open water", bool(freed) and st.get("aground") == "false" and moved > 15
          and abs(float(st["roll"])) < 3 and abs(float(st["pitch"])) < 3,
          f"{freed.split('PROBE ')[1] if freed else 'not freed'}; moved {moved:.0f}, aground={st.get('aground')}, roll {float(st['roll']):.1f}")
    w = where(log, float(st["x"]), float(st["z"]))
    check("her crew aboard come with her", w is not None and w[1] > 2 and abs(w[0]) < 5, f"Dev at {w}")
    out = subprocess.run([sys.executable, str(ROOT / "tools" / "rcon.py"), "execute as Dev run crew free"], capture_output=True, text=True).stdout
    check("not again straight away", "minute" in out, out.strip().splitlines()[-1] if out.strip() else "?")
    rcon(at(float(st["x"]), float(st["z"]), "ship controls 0 0 0 true"), f"fill {bar} minecraft:water", "seafarers config crashDamage true")

    # ---- the status panel: a sailing sloop, and a holed galleon ----
    rcon(at(float(st["x"]), float(st["z"]), "ship remove"))
    launch(log, "sloop", *A, "south")
    time.sleep(4)
    rcon(at(*A, "ship crew Dev"), at(*A, "ship controls 0.3 2 20 false"),
         f"execute as Dev positioned {A[0]} {SURFACE + 2} {A[1]} run seafarers ship view 0 0 -1.5 0 10")
    time.sleep(6)
    shot("hud_sloop")
    rcon(at(*A, "ship remove"))
    launch(log, "galleon", *B, "south")
    time.sleep(6)
    rcon(at(*B, "ship controls -0.5 3 -25 false"), at(*B, "tune floodPerHole 0.15"), at(*B, "ship hole 2"),
         f"execute as Dev positioned {B[0]} {SURFACE + 2} {B[1]} run seafarers ship view 0 0.2 -2.3 0 10")
    time.sleep(8)
    shot("hud_galleon")
    rcon(at(*B, "ship remove"), "seafarers config mermaidDelaySeconds 5")

    failed = [n for n, ok in results if not ok]
    print(f"\nrescue: {len(results) - len(failed)}/{len(results)} passed" + (f"; failed: {', '.join(failed)}" if failed else ""))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
