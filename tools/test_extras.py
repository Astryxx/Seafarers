"""Advancements, named ships, hints, explosions, friendly fire, death messages and shipwreck loot, with a real
player: needs the dev client (starts it itself).

tools/restart.sh --fresh && python tools/test_extras.py

Screenshots (the hint line, Jade's ship tooltip, floating shipwreck barrels) go to shots/extras_*.png.
"""
import re
import subprocess
import time

from test_ship import ROOT, SEA, SURFACE, Log, at, check, rcon, results, status, wait_for
from test_captains import PLATFORM, deed_components, deeds, launch_from_deed, owner, rcon_out

A = (48, 140)


def shot(name):
    subprocess.run(["um", "win", "shot", "--title", "Minecraft NeoForge", "--scale", "0.5", f"shots/extras_{name}.png"],
                   cwd=ROOT, capture_output=True)
    print(f"  shot shots/extras_{name}.png")


def has(adv):
    return "passed" in rcon_out(f"execute if entity @a[name=Dev,advancements={{seafarers:{adv}=true}}]")


def health():
    m = re.search(r"([\d.]+)f", rcon_out("data get entity Dev Health"))
    return float(m[1]) if m else -1


def main():
    for line in subprocess.run(["um", "win", "ps"], capture_output=True, text=True).stdout.splitlines():
        if "Minecraft NeoForge" in line:
            subprocess.run(["um", "win", "kill", line.split()[0]], capture_output=True)
    log = Log()
    rcon("forceload add -16 -16 112 272", f"seafarers test sea {SEA[0]} {SEA[1]} {SEA[2]}", "seafarers wind 0", "time set 6000",
         "gamerule doDaylightCycle false")
    time.sleep(2)
    subprocess.Popen(["cmd", "/c", str(ROOT / "gradlew.bat"), "runClientJoin", "--console=plain"], cwd=ROOT,
                     stdout=open(ROOT / "run" / "client-console.log", "w"), stderr=subprocess.STDOUT)
    for _ in range(300):
        if "Dev joined the game" in (ROOT / "run" / "server" / "console.log").read_text(errors="replace"):
            break
        time.sleep(1)
    time.sleep(8)
    px, py, pz = PLATFORM
    rcon(f"fill {px - 2} {py - 1} {pz - 2} {px + 2} {py - 1} {pz + 2} glass", "gamemode survival Dev",
         "effect give Dev minecraft:resistance 99999 4 true", "effect give Dev minecraft:water_breathing 99999 0 true",
         f"tp Dev {px} {py} {pz} -90 0", "seafarers config newShipCooldownMinutes 0", "crew reset Dev")
    time.sleep(2)

    # ---- advancements and a named ship ----
    rcon("seafarers pick Dev 1 sloop")
    time.sleep(1.5)
    check("choosing a ship starts the advancements", has("root"), "root")
    comps = deed_components()
    inner = comps[comps.index("{", 1):-1] if comps else ""
    rcon(f"item replace entity Dev weapon.mainhand with seafarers:sloop_deed[seafarers:deed={inner},custom_name='\"The Black Pearl\"']")
    line = launch_from_deed(log, *A)
    time.sleep(1)
    o = owner(log, *A)
    check("a deed renamed in an anvil names the ship", bool(line) and o.get("name") == "The_Black_Pearl", f"name {o.get('name')}")
    check("launching her earns Set Sail", has("set_sail"), "set_sail")
    rcon(f"tp Dev {px} {py} {pz} -90 0")

    # ---- hints ----
    log.new()
    for _ in range(4):
        rcon("seafarers hint Dev helm")
    time.sleep(0.5)
    sent = [l for l in log.new() if "PROBE hint helm" in l]
    check("a hint shows the first few times only", len(sent) == 3, f"{len(sent)} of 4 offers shown")
    rcon("seafarers hint Dev capstan")
    time.sleep(1)
    shot("hint")

    # ---- explosions ----
    st = status(log, *A)
    before = int(owner(log, *A).get("blocks", 0))
    rcon(f"summon tnt {st['x']:.1f} {st['y'] + 2:.1f} {st['z']:.1f} {{fuse:1}}")
    time.sleep(3)
    after = int(owner(log, *A).get("blocks", 0))
    check("a locked ship shrugs off explosions", before > 0 and after == before, f"blocks {before} then {after}")
    rcon("execute as Dev run crew unlock")
    st = status(log, *A)
    rcon(f"summon tnt {st['x']:.1f} {st['y'] + 2:.1f} {st['z']:.1f} {{fuse:1}}")
    time.sleep(3)
    hurt = int(owner(log, *A).get("blocks", 0))
    check("an unlocked ship doesn't", hurt < after, f"blocks {after} then {hurt}")
    rcon("execute as Dev run crew lock", f"tp Dev {px} {py} {pz} -90 0")
    time.sleep(1)

    # ---- friendly fire and death messages ----
    uuid = re.search(r"\[I; ([-\d, ]+)\]", rcon_out("data get entity Dev UUID"))
    rcon("effect clear Dev", "effect give Dev minecraft:water_breathing 99999 0 true")
    time.sleep(0.5)
    h0 = health()
    rcon(f"summon seafarers:cannonball {px + 0.5} {py + 4} {pz + 0.5} {{ammo:1,Motion:[0.0d,-1.5d,0.0d],Owner:[I;{uuid[1]}]}}")
    time.sleep(2)
    h1 = health()
    check("your own crew's shot doesn't hurt you", h0 > 0 and h1 >= h0, f"health {h0} then {h1}")
    rcon(f"summon seafarers:cannonball {px + 0.5} {py + 4} {pz + 0.5} {{ammo:1,Motion:[0.0d,-1.5d,0.0d]}}")
    time.sleep(2)
    h2 = health()
    check("someone else's does", h2 < h1, f"health {h1} then {h2}")
    rcon(f"tp Dev {px + 0.5} {py} {pz + 0.5} -90 0", f"damage Dev {max(0.0, h2 - 1):.0f}")   # back under the shot (the last one knocked Dev aside)
    time.sleep(1.5)   # past the moment of immunity after taking damage
    log.new()
    rcon(f"summon seafarers:cannonball {px + 0.5} {py + 4} {pz + 0.5} {{ammo:1,Motion:[0.0d,-1.5d,0.0d]}}")
    time.sleep(2)
    text = (ROOT / "run" / "server" / "console.log").read_text(errors="replace")
    check("a cannonball death has its own message", "Dev was blasted overboard by a cannonball" in text, "message found"
          if "blasted overboard" in text else "no such message")
    rcon("seafarers respawn Dev")
    time.sleep(2)
    rcon("effect give Dev minecraft:resistance 99999 4 true", "effect give Dev minecraft:water_breathing 99999 0 true")

    # ---- she goes down: shipwreck loot, and the advancements for it ----
    st = status(log, *A)
    rcon("seafarers config replacementMinDistance 40", "seafarers config replacementMaxDistance 70",
         f"tp Dev {st['x'] + 12:.0f} {SURFACE + 1} {st['z']:.0f}", at(*A, "tune floodPerHole 0.6"), at(*A, "ship hole 4"))
    sank = wait_for(log, r"PROBE sinking", 40)
    check("aboard as she sinks: Down With the Ship", bool(sank) and has("went_down"), "went_down" if has("went_down") else "not earned")
    loot = wait_for(log, r"PROBE shipwreck loot", 40)
    repl = wait_for(log, r"PROBE replacement", 5)
    check("her stores float up in shipwreck barrels", bool(loot), (loot or "no loot").split("PROBE ")[-1])
    time.sleep(4)
    rcon(f"execute as Dev at @e[type=seafarers:shipwreck_barrel,limit=1,sort=nearest] run tp Dev ~3 ~1 ~ 90 25")
    time.sleep(2)
    shot("barrels")
    before = rcon_out("data get entity Dev Inventory").count("seafarers:cannonball")
    rcon("damage @e[type=seafarers:shipwreck_barrel,limit=1,sort=nearest] 1 minecraft:player_attack by Dev")
    time.sleep(1)
    after = rcon_out("data get entity Dev Inventory").count("seafarers:cannonball")
    check("opening one gives you what's inside", after > before and has("salvage"), f"cannonball stacks {before} then {after}")

    # ---- Jade: look at the replacement ship ----
    if repl:
        m = re.search(r"at (-?\d+), (-?\d+), (-?\d+)", repl)
        rcon(f"execute as Dev positioned {m[1]} {SURFACE + 2} {m[3]} run seafarers ship view 0 1.5 -2.5 0 35")
        time.sleep(3)
        shot("jade")
        rcon(f"execute positioned {m[1]} {SURFACE + 2} {m[3]} run seafarers ship remove")

    rcon("seafarers config newShipCooldownMinutes 30", "seafarers config replacementMinDistance 150",
         "seafarers config replacementMaxDistance 300", "kill @e[type=seafarers:shipwreck_barrel]")
    errors = [l for l in log.new() if "Exception" in l]
    check("no exceptions", not errors, errors[0][:120] if errors else "")
    passed = sum(1 for _, ok in results if ok)
    print(f"extras: {passed}/{len(results)} passed")


if __name__ == "__main__":
    main()
