"""Screenshots for the store page and the mod's logo, in the real ocean (seed 5) with the client.

OCEAN=1 tools/restart.sh --fresh && python tools/look_store.py   (writes shots/store_*.png and the logo candidate)
"""
import subprocess
import sys
import time

from PIL import Image

from test_ship import ROOT, rcon

SURFACE = 62


def shot(name, scale="1.0"):
    path = ROOT / "shots" / f"store_{name}.png"
    subprocess.run(["um", "win", "shot", "--title", "Minecraft NeoForge", "--scale", scale, str(path)], cwd=ROOT, capture_output=True)
    print(f"  shot {path.name}")
    return path


def at(x, z, cmd):
    return f"execute positioned {x} {SURFACE + 2} {z} run seafarers {cmd}"


def view(x, z, spec):
    rcon(f"execute as Dev positioned {x} {SURFACE + 2} {z} run seafarers ship view {spec}")
    time.sleep(3)


def main():
    import test_rescue
    # (a fresh world takes a while to generate these chunks: keep asking until it answers)
    for _ in range(10):
        try:
            rcon("forceload add -700 360 -380 470")
            break
        except subprocess.CalledProcessError:
            time.sleep(10)
    rcon("time set 5000", "gamerule doDaylightCycle false", "weather clear", "seafarers wind 90", "gamerule sendCommandFeedback false")
    test_rescue.client()
    rcon("gamemode spectator Dev", "seafarers config showShipChoiceScreen false")
    # the fleet, sailing west in line abreast: galleon in the middle
    ships = {"sloop": (-460, 376), "galleon": (-470, 416), "brigantine": (-465, 458)}
    for name, (x, z) in ships.items():
        rcon(f"seafarers launch {name} {x} {SURFACE} {z} west")
        time.sleep(1)
        # anchored the moment she's launched, her sails set: she stays put for the pictures
        rcon(at(x, z, "ship keep"), at(x, z, "ship controls 0 2 0 true"))
    time.sleep(12)
    import re
    # each ship's helm, found by sweeping along her row and reading which ship answers
    helm = {}
    for name, (x, z) in ships.items():
        for px in range(x + 20, x - 200, -12):
            out = subprocess.run([sys.executable, str(ROOT / "tools" / "rcon.py"), at(px, z, "ship status")], capture_output=True, text=True).stdout
            m = re.search(r"layout=(\w+).*?helmX=([-\d.]+) helmY=([-\d.]+) helmZ=([-\d.]+)", out, re.S)
            if m and m[1] == name:
                helm[name] = (float(m[2]), float(m[3]), float(m[4]))
                break
        print(f"  {name} helm at {helm.get(name)}")
        if name in helm:   # kept loaded however far the camera goes (by her own helm: nearest is her)
            rcon(f"execute positioned {helm[name][0]} {helm[name][1]} {helm[name][2]} run seafarers ship keep")

    def from_helm(name, spec):
        """Puts the camera at (x to starboard, y up, z toward the bow) from her helm, looking yaw off the bow. They all
        head west: the bow is -x, starboard is -z."""
        hx, hy, hz = helm[name]
        x, y, z, yaw, pitch = (float(v) for v in spec.split())
        rcon(f"tp Dev {hx - z:.2f} {hy - 0.5 + y:.2f} {hz - x:.2f} {90 + yaw:.1f} {pitch:.1f}")
        time.sleep(3)

    # 1. the fleet, from off the galleon's port bow, low over the water, looking back along the line
    from_helm("galleon", "-48 5 72 147 4")
    fleet = shot("fleet")
    img = Image.open(fleet)
    w, h = img.size
    side = min(w, h)
    img.crop(((w - side) // 2, 0, (w - side) // 2 + side, side)).resize((512, 512), Image.LANCZOS).save(ROOT / "shots" / "store_logo.png")
    # 2. the galleon's port broadside, gunports and all
    from_helm("galleon", "-30 3 30 105 4")
    shot("galleon_broadside")
    # 3. at the galleon's helm, with the status panel
    rcon("gamemode creative Dev")
    from_helm("galleon", "0 0.2 -2.3 0 10")
    shot("helm")
    # 4. her gun deck
    rcon("gamemode spectator Dev")
    from_helm("galleon", "0 -8 30 180 8")
    shot("gun_deck")
    # 5. the brig, on the brigantine
    from_helm("brigantine", "1 -5 16 -150 10")
    shot("brig")
    # 6. a mermaid off the sloop's starboard side, the sloop behind her
    sx, _, sz = helm["sloop"]
    rcon(f"tp Dev {sx - 10} {SURFACE + 1.6} {sz - 14} 0 4")
    time.sleep(1)
    rcon(f"execute positioned {sx - 10} {SURFACE + 0.92} {sz - 9} run seafarers ship mermaid Dev")   # (she turns to face Dev)
    time.sleep(3)
    shot("mermaid")
    rcon("kill @e[type=seafarers:mermaid]")
    print("store shots done")


if __name__ == "__main__":
    main()
