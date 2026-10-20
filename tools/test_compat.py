"""Admin commands, permissions, natural rowboats and the optional mods (JEI, EMI, JourneyMap, Open Parties and
Claims, Simple Voice Chat), with a real player. Loads those mods into the dev game:

COMPAT=1 tools/restart.sh --fresh && python tools/test_compat.py

FTB Chunks and FTB Teams are only compiled against (they need Architectury to run), not run here.
"""
import os
import re
import subprocess
import time

from test_ship import ROOT, SEA, SURFACE, Log, at, check, rcon, results, wait_for
from test_captains import PLATFORM, deeds, launch_from_deed, owner, rcon_out

A = (48, 140)
CLIENT_LOG = ROOT / "run" / "client-console.log"


def client_has(pattern, seconds=30):
    end = time.time() + seconds
    while time.time() < end:
        if re.search(pattern, CLIENT_LOG.read_text(errors="replace")):
            return True
        time.sleep(1)
    return False


def main():
    for line in subprocess.run(["um", "win", "ps"], capture_output=True, text=True).stdout.splitlines():
        if "Minecraft" in line and "java" in line:
            subprocess.run(["um", "win", "kill", line.split()[0]], capture_output=True)
    log = Log()
    rcon("forceload add -16 -16 112 272", f"seafarers test sea {SEA[0]} {SEA[1]} {SEA[2]}", "seafarers wind 0", "time set 6000")
    time.sleep(2)
    args = ["-PcompatTest"] + (["-PnoEmi"] if os.environ.get("NOEMI") else [])
    subprocess.Popen(["cmd", "/c", str(ROOT / "gradlew.bat"), "runClientJoin", *args, "--console=plain"], cwd=ROOT,
                     stdout=open(CLIENT_LOG, "w"), stderr=subprocess.STDOUT)
    for _ in range(400):
        if "Dev joined the game" in (ROOT / "run" / "server" / "console.log").read_text(errors="replace"):
            break
        time.sleep(1)
    time.sleep(10)
    px, py, pz = PLATFORM
    rcon(f"fill {px - 2} {py - 1} {pz - 2} {px + 2} {py - 1} {pz + 2} glass", "gamemode survival Dev",
         "effect give Dev minecraft:resistance 99999 4 true", "effect give Dev minecraft:water_breathing 99999 0 true",
         f"tp Dev {px} {py} {pz}", "seafarers config newShipCooldownMinutes 0", "crew reset Dev")
    time.sleep(2)

    # ---- recipe viewers ----
    if os.environ.get("NOEMI"):
        check("JEI shows the info pages", client_has(r"PROBE jei info pages \d+", 5), "registered" if client_has("PROBE jei", 1) else "no")
    else:
        check("EMI shows the info pages", client_has(r"PROBE emi info pages \d+", 5), "registered" if client_has("PROBE emi", 1) else "no")

    # ---- rowboats: not craftable, found along the coast ----
    out = rcon_out("recipe give Dev seafarers:rowboat")
    check("rowboats can't be crafted", "Unknown recipe" in out or "No recipe" in out or "unknown" in out.lower(), out.strip()[-60:])
    # a stretch of coast in the test sea: a stone bank at the water's surface
    rcon("fill 60 100 52 66 109 75 stone", "fill 60 110 52 66 110 75 grass_block")
    log.new()
    rcon(f"seafarers rowboat 56 64 {SURFACE + 1}")
    line = wait_for(log, r"PROBE natural rowboat|PROBE rowboat placed", 4) or ""
    m = re.search(r"natural (\S+) at (-?\d+), (-?\d+), (-?\d+)", line)
    check("a rowboat ties up along the coast", bool(m), line.split("PROBE ")[-1] if line else "none")
    if m:
        rcon(f"tp Dev {int(m[2]) - 6} {SURFACE + 3} {int(m[4])} -90 20")
        time.sleep(3)
        subprocess.run(["um", "win", "shot", "--title", "Minecraft NeoForge", "--scale", "0.5", "shots/compat_rowboat.png"],
                       cwd=ROOT, capture_output=True)
        rcon(f"execute positioned {m[2]} {SURFACE + 2} {m[4]} run seafarers ship remove")
    rcon(f"tp Dev {px} {py} {pz}")

    # ---- a ship of Dev's: JourneyMap waypoint, voice group, admin commands ----
    rcon("seafarers pick Dev 1 sloop")
    time.sleep(1)
    launched = launch_from_deed(log, *A)
    rcon(f"tp Dev {px} {py} {pz}")
    check("JourneyMap gets a waypoint on her", client_has(r"PROBE journeymap waypoint .* at", 20), "waypoint" if client_has("PROBE journeymap", 1) else "none")
    voice = wait_for(log, r"PROBE voice group", 15)
    check("her crew gets a voice group", bool(voice), (voice or "no group").split("PROBE ")[-1])

    out = rcon_out("crew admin ships")
    ids = re.findall(r"\[([0-9a-f]{8})\]", out)
    check("admins can list every ship", bool(launched) and len(ids) >= 1, out.strip().replace("\n", " | ")[:150])
    # (a command run "as Dev" from the console is checked against the console's permissions, so ask directly)
    admin = deeds(log).get("admin")
    check("players can't use the admin commands", admin == "false", f"Dev has seafarers.admin: {admin}")
    if ids:
        rcon(f"crew admin unlock {ids[0]}")
        time.sleep(0.5)
        unlocked = owner(log, *A).get("locked") == "false"
        rcon(f"crew admin lock {ids[0]}")
        check("admins can unlock and lock any ship", unlocked and owner(log, *A).get("locked") == "true", "")
        rcon(f"crew admin remove {ids[0]}")
        gone = wait_for(log, r"PROBE admin removed", 3)
        d = deeds(log)
        check("admins can remove a ship (her crew are freed)", bool(gone) and d.get("ship") == "null", str(d))

    # ---- land claims (Open Parties and Claims) ----
    rcon("seafarers config newShipCooldownMinutes 0")
    rcon("seafarers pick Dev 1 sloop")
    time.sleep(1)
    rcon(f"tp Dev {A[0]} {SURFACE + 3} {A[1]} 0 90")
    rcon(f"seafarers claim {A[0]} {A[1]} true")
    log.new()
    rcon(f"tp Dev {A[0]} {SURFACE + 3} {A[1]} 0 90", "seafarers useitem Dev")
    used = wait_for(log, r"PROBE useitem Dev", 3) or ""
    check("no launching in land claimed by others", "FAIL" in used, used.split("PROBE ")[-1])
    rcon(f"seafarers claim {A[0]} {A[1]} false")
    line = launch_from_deed(log, *A)
    check("once it's unclaimed, she launches", bool(line), (line or "no launch").split("PROBE ")[-1][:60])

    rcon("seafarers config newShipCooldownMinutes 30")
    errors = [l for l in log.new() if "Exception" in l]
    check("no exceptions", not errors, errors[0][:120] if errors else "")
    passed = sum(1 for _, ok in results if ok)
    print(f"compat: {passed}/{len(results)} passed")


if __name__ == "__main__":
    main()
