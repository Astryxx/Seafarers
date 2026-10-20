"""Ships of your own, with a real player: needs the dev client (starts it itself).

tools/restart.sh --fresh && python tools/test_captains.py

Dev chooses a sloop on the ship choice screen and gets a deed that can't be burned, blown up, despawned, lost in
the void or lost on death; a lost deed is replaced and the old one stops working. Dev launches the sloop and is
her captain, can't have a second ship, locks and unlocks her (a stranger can't hoist her sails while she's locked),
removes and promotes crew at the quest table, swaps her for a new sloop (the crew follows), and when she sinks
with replacement ships turned off, may choose again. Screenshots of the choice screen go to shots/.
"""
import re
import subprocess
import sys
import time

from test_ship import ROOT, SEA, SURFACE, Log, at, check, mast, rcon, results, status, wait_for

A = (48, 140)      # Dev's first sloop
B = (48, 205)      # Dev's sloop after choosing again
PLATFORM = (20, SURFACE + 6, 20)


def rcon_out(cmd):
    return subprocess.run([sys.executable, str(ROOT / "tools" / "rcon.py"), cmd], capture_output=True, text=True).stdout


def shot(name):
    subprocess.run(["um", "win", "shot", "--title", "Minecraft NeoForge", "--scale", "0.5", f"shots/captains_{name}.png"],
                   cwd=ROOT, capture_output=True)
    print(f"  shot shots/captains_{name}.png")


def probe(log, cmd, tag):
    """Runs a debug command and returns the key=value pairs of the PROBE line it logs."""
    log.new()
    rcon(cmd)
    line = wait_for(log, rf"PROBE {tag}", 3) or ""
    return dict(re.findall(r"(\w+)=(\S+)", line.split(f"PROBE {tag}", 1)[-1]))


def deeds(log):
    return probe(log, "seafarers deeds Dev", "deeds Dev")


def owner(log, x, z):
    """The ship's captain and lock, and how many players are named (captain and crew, as UUIDs)."""
    log.new()
    rcon(at(x, z, "ship owner"))
    line = wait_for(log, r"PROBE owner", 3) or ""
    out = dict(re.findall(r"(\w+)=(\S+)", line))
    out["names"] = len(re.findall(r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}", line))
    return out


def deed_components():
    """The components of the deed Dev is carrying, as SNBT, for summoning copies of it."""
    raw = rcon_out("data get entity Dev Inventory")
    m = re.search(r'components: (\{"seafarers:deed": \{[^}]*\}\})', raw)
    return m[1] if m else None


def launch_from_deed(log, x, z):
    log.new()
    rcon(f"tp Dev {x} {SURFACE + 3} {z} 0 90", "seafarers useitem Dev")
    line = wait_for(log, r"PROBE Dev launched \w+", 5)
    if line:
        rcon(at(x, z, "ship keep"))
    return line


def main():
    for line in subprocess.run(["um", "win", "ps"], capture_output=True, text=True).stdout.splitlines():
        if "Minecraft NeoForge" in line:
            subprocess.run(["um", "win", "kill", line.split()[0]], capture_output=True)
    log = Log()
    rcon("forceload add -16 -16 112 272", f"seafarers test sea {SEA[0]} {SEA[1]} {SEA[2]}", "seafarers wind 0", "time set 6000")
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
         f"tp Dev {px} {py} {pz}", "seafarers config newShipCooldownMinutes 30")
    time.sleep(2)

    # ---- A: the ship choice screen ----
    rcon("seafarers config showShipChoiceScreen true", "crew reset Dev")
    time.sleep(3)
    shot("choice")
    d = deeds(log)
    check("a new player hasn't chosen yet", d.get("choice") == "NONE", str(d))
    rcon("seafarers pick Dev 0 sloop")
    time.sleep(1.5)
    d = deeds(log)
    check("choosing a sloop gives Dev her deed", d.get("choice") == "SHIP" and d.get("carried") == "1" and d.get("validCarried") == "true", str(d))
    shot("after_pick")
    inv = rcon_out("data get entity Dev Inventory")
    check("a first ship comes with a Ship's Compass", "seafarers:ships_compass" in inv, "compass" if "ships_compass" in inv else "no compass")
    check("and the Captain's Handbook (Patchouli)", "patchouli:guide_book" in inv and "seafarers:handbook" in inv,
          "handbook" if "guide_book" in inv else "no handbook")
    rcon("seafarers pick Dev 0 sloop")
    time.sleep(1)
    d = deeds(log)
    check("choosing twice gives nothing more", d.get("carried") == "1", f"carried {d.get('carried')}")
    out = rcon_out("execute as Dev run crew choose")
    check("no second ship while the deed is unused", "unused" in out, out.strip()[:80])
    rcon("seafarers config showShipChoiceScreen false")
    comps = deed_components()
    check("the deed is Dev's own", comps is not None and 'owner_name: "Dev"' in comps, (comps or "no deed components")[:90])

    # ---- B: the deed can't be lost ----
    if comps:
        rcon("fill 29 113 19 33 113 23 glass", "fill 30 114 20 32 114 22 lava",
             f'summon item 31 115 21 {{Item:{{id:"seafarers:sloop_deed",count:1,components:{comps}}}}}')
        time.sleep(5)
        out = rcon_out("execute if entity @e[type=item,x=31,y=114,z=21,distance=..4]")
        check("a deed in lava doesn't burn", "passed" in out, out.strip()[:60])
        age = rcon_out("data get entity @e[type=item,x=31,y=114,z=21,distance=..4,limit=1] Age")
        check("a dropped deed never despawns", "-32768" in age, age.strip()[-40:])
        rcon("fill 37 113 19 43 113 23 glass",
             f'summon item 40 114 21 {{Item:{{id:"seafarers:sloop_deed",count:1,components:{comps}}}}}',
             "summon tnt 40 114 21 {fuse:2}")
        time.sleep(4)
        out = rcon_out("execute if entity @e[type=item,x=40,y=110,z=21,distance=..10,nbt={Item:{id:\"seafarers:sloop_deed\"}}]")
        check("a deed survives an explosion", "passed" in out, out.strip()[:60])
        rcon(f'summon item 20 -100 20 {{Item:{{id:"seafarers:sloop_deed",count:1,components:{comps}}}}}')
        time.sleep(2)
        d = deeds(log)
        check("a deed falling out of the world comes back", d.get("carried") == "2", f"carried {d.get('carried')}")
        rcon("kill @e[type=item]", "clear Dev seafarers:sloop_deed 1", "fill 29 113 19 43 114 23 water")

    log.new()
    rcon("kill Dev")
    kept = wait_for(log, r"PROBE deed kept for Dev on death", 4)
    time.sleep(1)
    out = rcon_out("execute if entity @e[type=item,nbt={Item:{id:\"seafarers:sloop_deed\"}}]")
    check("dying doesn't drop the deed", bool(kept) and "passed" not in out, "kept" if kept else "not kept")
    rcon("seafarers respawn Dev")
    time.sleep(2)
    rcon(f"tp Dev {px} {py} {pz}")
    d = deeds(log)
    check("the deed is back after respawning", d.get("carried") == "1" and d.get("validCarried") == "true", str(d))

    # a lost deed is replaced; the lost one stops working
    out = rcon_out("execute as Dev run crew reclaim")
    check("no replacement while you're carrying it", "inventory" in out, out.strip()[:60])
    old = deed_components()
    rcon("clear Dev seafarers:sloop_deed", "execute as Dev run crew reclaim")
    time.sleep(1)
    d = deeds(log)
    check("a lost deed is replaced", d.get("valid") == "true" and d.get("validCarried") == "true" and d.get("carried") == "1", str(d))
    # the lost one turns up again: it's in Dev's hand, the new one beside it
    inner = old[old.index("{", 1):-1] if old else ""
    rcon("item replace entity Dev hotbar.6 from entity Dev weapon.mainhand",
         f"item replace entity Dev weapon.mainhand with seafarers:sloop_deed[seafarers:deed={inner}]" if old else "say no deed")
    log.new()
    rcon(f"tp Dev {A[0]} {SURFACE + 3} {A[1] + 30} 0 90", "seafarers useitem Dev")
    used = wait_for(log, r"PROBE useitem Dev", 3) or ""
    time.sleep(0.5)
    d = deeds(log)
    check("the replaced deed no longer works", "FAIL" in used and d.get("carried") == "1" and d.get("captain") == "null",
          f"{used.split('PROBE ')[-1]} carried {d.get('carried')}")
    rcon("item replace entity Dev weapon.mainhand from entity Dev hotbar.6", "item replace entity Dev hotbar.6 with air")

    # ---- C: launching her ----
    line = launch_from_deed(log, *A)
    check("the deed launches a sloop on open water", bool(line), (line or "no launch").split("PROBE ")[-1][:80])
    d = deeds(log)
    check("whoever launches her is captain", d.get("captain", "null") != "null" and d.get("carried") == "0" and d.get("valid") == "false", str(d))
    check("new ships start locked", d.get("locked") == "true", f"locked {d.get('locked')}")
    out = rcon_out("execute as Dev run crew choose")
    check("one ship per player", "already captain" in out, out.strip()[:60])
    # a compass of Dev's own (the first one dropped when Dev died in the deed tests, like any item)
    rcon(f"tp Dev {px} {py} {pz}", "clear Dev seafarers:ships_compass", "item replace entity Dev hotbar.8 with seafarers:ships_compass")
    time.sleep(2.5)
    inv = rcon_out("data get entity Dev Inventory")
    m = re.search(r'"minecraft:lodestone_tracker": [^\]]*pos: \[I; (-?\d+), (-?\d+), (-?\d+)\]', inv)
    check("the Ship's Compass points to her", bool(m) and abs(int(m[1]) - A[0]) < 16 and abs(int(m[3]) - A[1]) < 16,
          f"points to {m[1]}, {m[3]}" if m else "no target: " + ";".join(re.findall(r'\{[^{}]*ships_compass[^{}]*\}', inv)) + " " + str(deeds(log)))
    rcon("give Dev seafarers:sloop_deed")
    log.new()
    rcon(f"tp Dev {A[0]} {SURFACE + 3} {A[1] + 40} 0 90", "seafarers useitem Dev")
    used = wait_for(log, r"PROBE useitem Dev", 3) or ""
    check("a second deed won't launch a second ship", "FAIL" in used, used.split("PROBE ")[-1])
    rcon("clear Dev seafarers:sloop_deed", f"tp Dev {px} {py} {pz}")

    # ---- D: the lock ----
    rcon(at(*A, "ship controls 0 0 0 true"))
    time.sleep(2)
    log.new()
    rcon(at(*A, "ship hoist 0 6"))
    time.sleep(2)
    refused = [l for l in log.new() if "use refused" in l]
    deploy = mast(status(log, *A))[0]
    check("a stranger can't work a locked ship", bool(refused) and deploy < 0.05, f"{len(refused)} refused, sail {deploy:.2f}")
    out = rcon_out("execute as Dev run crew unlock")
    log.new()
    rcon(at(*A, "ship hoist 0 6"))
    time.sleep(3)
    refused = [l for l in log.new() if "use refused" in l]
    deploy = mast(status(log, *A))[0]
    check("anyone can work her once unlocked", not refused and deploy > 0.1, f"{len(refused)} refused, sail {deploy:.2f}")
    rcon(at(*A, "ship table Dev 9 1 x"))
    time.sleep(0.5)
    check("the captain locks her at the quest table", owner(log, *A).get("locked") == "true", "")
    rcon(at(*A, "ship crew"))
    time.sleep(0.5)
    log.new()
    rcon(at(*A, "ship hoist 0 -6"))
    time.sleep(1)
    refused = [l for l in log.new() if "use refused" in l]
    check("her crew can work her while she's locked", not refused, f"{len(refused)} refused")

    # ---- E: the captain's crew controls at the quest table ----
    o = owner(log, *A)
    rcon(at(*A, "ship table Dev 6 0 hand"))
    time.sleep(0.5)
    o2 = owner(log, *A)
    check("the captain removes a crew member", o["names"] == 3 and o2["names"] == 2, f"names on the ship: {o['names']} then {o2['names']}")
    rcon(at(*A, "ship crew"))
    time.sleep(0.5)
    rcon(at(*A, "ship table Dev 7 0 hand"))
    time.sleep(0.5)
    d = deeds(log)
    o = owner(log, *A)
    check("the captain hands the ship to a crew member", d.get("captain") == "null" and o.get("captain") not in (None, "null"), str(o))
    out = rcon_out("execute as Dev run crew lock")
    check("only the captain locks her", "aren't the captain" in out, out.strip()[:60])
    rcon("execute as Dev run crew leave")

    # ---- F: choosing again (after the wait) and swapping ----
    out = rcon_out("execute as Dev run crew choose")
    check("a new ship has to wait", "new ship in" in out, out.strip()[:60])
    rcon("seafarers config newShipCooldownMinutes 0", at(*A, "ship remove"))
    time.sleep(1)
    out = rcon_out("execute as Dev run crew choose")
    time.sleep(1)
    shot("choose_later")
    rcon("seafarers pick Dev 1 sloop")
    time.sleep(1)
    line = launch_from_deed(log, *B)
    check("after the wait, a new ship", bool(line), (line or "no launch").split("PROBE ")[-1][:80])
    rcon(at(*B, "ship crew"))
    time.sleep(0.5)
    out = rcon_out("execute as Dev run crew swap")
    time.sleep(1.5)
    shot("swap")
    log.new()
    rcon("seafarers pick Dev 2 galleon")   # trade the Sloop for a Galleon
    swapped = wait_for(log, r"PROBE Dev swapped", 3)
    d = deeds(log)
    check("swapping takes the old ship and gives a deed", bool(swapped) and d.get("captain") == "null" and d.get("validCarried") == "true", str(d))
    line = launch_from_deed(log, *B)
    o = owner(log, *B)
    check("the swap's deed launches the ship chosen: a Galleon", bool(line) and "launched galleon" in line,
          (line or "no launch").split("PROBE ")[-1][:70])
    check("the crew follows the captain to the new ship", bool(line) and o["names"] == 3, f"names on the ship: {o['names']}")
    rcon("seafarers config newShipCooldownMinutes 30")
    log.new()
    before = deeds(log).get("captain")
    rcon("seafarers pick Dev 2 sloop")
    time.sleep(1)
    check("swapping waits too", deeds(log).get("captain") == before and before != "null", f"captain {before}")

    # ---- G: lost for good ----
    rcon("seafarers config replaceSunkShips false", f"tp Dev {px} {py} {pz}", at(*B, "tune floodPerHole 0.6"), at(*B, "ship hole 4"))
    wreck = wait_for(log, r"PROBE wreck", 90)
    time.sleep(1)
    d = deeds(log)
    check("a sunk ship with replacements off is gone for good", bool(wreck) and d.get("ship") == "null" and d.get("captain") == "null", str(d))
    out = rcon_out("execute as Dev run crew choose")
    check("her captain may choose again straight away", "new ship in" not in out and "already" not in out, out.strip()[:60] or "screen opened")
    rcon("seafarers pick Dev 1 crew", "seafarers config replaceSunkShips true")

    # ---- H: the server's ship limit, and abandoned ships ----
    rcon("seafarers config newShipCooldownMinutes 0", "seafarers config maxShipsOnServer 1")
    rcon("seafarers pick Dev 1 sloop")
    time.sleep(1)
    line = launch_from_deed(log, *A)
    rcon(at(*A, "ship crew"), at(*A, "ship table Dev 7 0 hand"), "execute as Dev run crew leave")   # the hand keeps her
    time.sleep(1)
    rcon("seafarers pick Dev 1 sloop")
    time.sleep(1)
    log.new()
    rcon(f"tp Dev {B[0]} {SURFACE + 3} {B[1]} 0 90", "seafarers useitem Dev")
    used = wait_for(log, r"PROBE useitem Dev", 3) or ""
    check("no launching past the server's ship limit", bool(line) and "FAIL" in used, used.split("PROBE ")[-1])
    rcon("seafarers config maxShipsOnServer 0", "seafarers config abandonedShipMinutes 1")
    line = launch_from_deed(log, *B)
    check("launching once there's room", bool(line), (line or "no launch").split("PROBE ")[-1][:70])
    rcon(f"tp Dev {px} {py} {pz}", "execute as Dev run crew leave")
    gone = wait_for(log, r"PROBE abandoned ship (sinking|removed)", 90)
    wreck = wait_for(log, r"PROBE wreck", 40) if gone and "sinking" in gone else gone
    o = owner(log, *A)
    check("a ship her crew has left sinks after a while", bool(gone) and bool(wreck), (gone or "never").split("PROBE ")[-1][:60])
    check("a ship with a crew doesn't", o.get("captain") not in (None, "null"), f"the hand's ship: captain {o.get('captain')}")
    rcon("seafarers config abandonedShipMinutes 30", "seafarers config newShipCooldownMinutes 30")

    errors = [l for l in log.new() if "Exception" in l]
    check("no exceptions", not errors, errors[0][:120] if errors else "")
    passed = sum(1 for _, ok in results if ok)
    print(f"captains: {passed}/{len(results)} passed")


if __name__ == "__main__":
    main()
