"""Checks the ship's fittings and the rowboat on the dev server (run tools/restart.sh first).

python tools/test_interactions.py [--keep]

Lanterns, flag box, galley stove, map table, voyage table, harpoon (loot and ship pull), and the rowboat:
floating, rowing, turning, docking at a sloop's ladder and following her when she sails.
"""
import re
import sys
import time

from test_ship import SEA, Log, at, check, launch, rcon, results, status, wait_for, yaw_delta


def main():
    keep = "--keep" in sys.argv
    log = Log()
    rcon("forceload add -16 -16 112 272", f"seafarers test sea {SEA[0]} {SEA[1]} {SEA[2]}", "seafarers wind 0",
         "kill @e[type=seafarers:harpoon]", "kill @e[type=item]")
    time.sleep(2)

    # ---- fittings aboard a sloop at anchor ----
    G = (16, 150)
    launch(log, "sloop", *G, "south")
    time.sleep(8)
    rcon(at(*G, "ship controls 0 0 0 true"))
    time.sleep(2)

    log.new()
    rcon(at(*G, "ship lanterns"))
    line = wait_for(log, r"PROBE lanterns", 3) or ""
    m = re.search(r"lanterns (\d+) lit=(\d+) out=(\d+) relit=(\d+) light=(\d+)/(\d+)", line)
    check("lanterns go out and light again", bool(m) and m[2] == m[1] and m[3] == "0" and m[4] == m[1] and m[5] == "15" and m[6] == "0",
          line.split("PROBE ")[1] if m else "no lanterns")

    log.new()
    rcon(at(*G, "ship flag"))
    raised = wait_for(log, r"PROBE flag raised", 3)
    state = wait_for(log, r"PROBE flag state", 3) or ""
    check("the flag box hoists a banner", bool(raised) and "flying=true" in state, (raised or "nothing").split("PROBE ")[-1])
    rcon(at(*G, "ship flag lower"))
    state = wait_for(log, r"PROBE flag state", 3) or ""
    check("and strikes it again", "flying=false" in state and "black_banner" in state, state.split("PROBE ")[-1])

    # stove: cooking scaled down so a steak takes 50 ticks (burns at 100)
    rcon("seafarers tune stove.COOK_SCALE 0.5")
    for ticks, want in ((10, "beef"), (70, "cooked_beef"), (130, "charred_food")):
        log.new()
        rcon(at(*G, f"ship stove {ticks}"))
        line = wait_for(log, r"PROBE stove gave", ticks / 20 + 3) or ""
        stage = {"beef": "raw", "cooked_beef": "cooked", "charred_food": "burnt"}[want]
        check(f"stove: taken off {stage}", line.endswith(" " + want) or line.endswith(":" + want), line.split("PROBE ")[-1] or "nothing")
    rcon("seafarers tune stove.COOK_SCALE 0")
    log.new()
    rcon(at(*G, "ship stovebad"))
    bad = wait_for(log, r"PROBE stove refused", 3) or ""
    check("the stove only takes food", "refused=true" in bad, bad.split("PROBE ")[-1] or "nothing")

    rcon("seafarers tune waterBarrelRefillSeconds 2")
    log.new()
    rcon(at(*G, "ship water"))
    water = wait_for(log, r"PROBE water bucket", 3) or ""
    check("water barrel: buckets, bottles and the ship's bucket", "bucket->water_bucket level 0" in water
          and "water_bucket->bucket level 3" in water and "glass_bottle->potion level 2" in water
          and "ship_bucket->ship_bucket_water level 1" in water, water.split("PROBE water ")[-1] or "nothing")
    later = wait_for(log, r"PROBE water later", 6) or ""
    check("and it fills itself again", later.endswith("level 2") or later.endswith("level 3"), later.split("PROBE ")[-1] or "nothing")
    rcon("seafarers tune waterBarrelRefillSeconds 90")

    log.new()
    rcon(at(*G, "ship voyagegui"))
    gui = wait_for(log, r"PROBE voyage gui", 3) or ""
    check("voyage table screen: the ship's state, and renaming her", "name=The Salty Dog layout=sloop masts=1" in gui,
          gui.split("PROBE ")[-1] or "nothing")

    log.new()
    rcon(at(*G, "ship maptable"))
    shown = wait_for(log, r"PROBE map table shown", 5) or ""
    drawn = wait_for(log, r"PROBE map table marks=\d+ view", 3) or ""
    m2 = re.search(r"marks=(\d+) view=(-?\d+),(-?\d+)/(\d+) ship=(-?\d+),(-?\d+)", drawn)
    check("map table unrolls the chart", bool(shown), shown.split("PROBE ")[-1] or "nothing")
    check("and takes the crew's marks", bool(m2) and m2[1] == "1", f"{m2[1] if m2 else '?'} mark(s)")
    check("and stays where it's left", bool(m2) and abs(int(m2[2]) - int(m2[5]) - 20) <= 1 and abs(int(m2[3]) - int(m2[6]) + 10) <= 1
          and m2[4] == "400", drawn.split("PROBE ")[-1] or "nothing")

    log.new()
    rcon(at(*G, "ship paint hull red"))
    hull = wait_for(log, r"PROBE painted", 3) or ""
    rcon(at(*G, "ship paint sails black"))
    sails = wait_for(log, r"PROBE painted", 3) or ""
    check("a dye on the wheel paints the hull", "hull=ffffff" not in hull and "left=0" in hull, hull.split("PROBE ")[-1] or "nothing")
    check("a dye on a sail rope dyes the sails", "sails=ffffff" not in sails and "left=0" in sails, sails.split("PROBE ")[-1] or "nothing")
    rcon(at(*G, "ship paint hull white"))
    back = wait_for(log, r"PROBE painted", 3) or ""
    check("white dye takes her back to bare wood", "hull=ffffff" in back, back.split("PROBE ")[-1] or "nothing")

    log.new()
    rcon(at(*G, "ship voyage"))
    started = wait_for(log, r"PROBE voyage started", 3)
    copies = wait_for(log, r"PROBE voyage copies", 3) or ""
    check("voyage table: propose, vote, start", bool(started) and "copies=1" in copies, (started or "not started").split("PROBE ")[-1])

    # ---- harpoon: loot in the water ahead, reeled in ----
    st = status(log, *G)
    gx, gy, gz = st["x"], st["y"], st["z"]
    # loot floating at the surface (a dropped item sinks a few blocks first and takes a while to bob back up)
    rcon(f"summon item {gx:.1f} {SEA[1] + 8.9} {gz + 22:.1f} {{Item:{{id:\"minecraft:gold_ingot\",count:1}},PickupDelay:32767,NoGravity:1b}}")
    time.sleep(1.5)
    log.new()
    rcon(at(*G, f"ship harpoon {gx:.1f} {SEA[1] + 9.3} {gz + 22:.1f} 300"))
    hooked = wait_for(log, r"PROBE harpoon (hooked|stuck)", 4)
    check("harpoon catches floating loot", bool(hooked) and "item" in hooked, (hooked or "missed").split("PROBE ")[-1])
    reeled = wait_for(log, r"PROBE harpoon reeled in", 15)
    check("and reels it in", bool(reeled), (reeled or "not reeled in").split("PROBE ")[-1])
    rcon("kill @e[type=item]")

    # ---- harpoon: into a rowboat ahead, then reel the two together ----
    R = (gx, gz + 24)
    launch(log, "rowboat", int(R[0]), int(R[1]), "east")
    time.sleep(6)
    rs = status(log, *R)
    rcon(at(*G, "ship controls 0 0 0 false"))
    gap0 = ((rs["x"] - gx) ** 2 + (rs["z"] - gz) ** 2) ** 0.5
    log.new()
    rcon(at(*G, f"ship harpoon {rs['x']:.1f} {rs['y'] + 0.6:.1f} {rs['z']:.1f} 300"))
    stuck = wait_for(log, r"PROBE harpoon (hooked|stuck)", 4)
    check("harpoon bites into another ship", bool(stuck) and "ship" in stuck, (stuck or "missed").split("PROBE ")[-1])
    time.sleep(14)
    rs2 = status(log, rs["x"], rs["z"])
    gs2 = status(log, gx, gz)
    gap1 = ((rs2["x"] - gs2["x"]) ** 2 + (rs2["z"] - gs2["z"]) ** 2) ** 0.5
    check("reeling pulls the ships together", gap1 < gap0 - 4, f"{gap0:.1f} -> {gap1:.1f} blocks apart")
    rcon(at(rs2["x"], rs2["z"], "ship remove"), "kill @e[type=seafarers:harpoon]", at(gs2["x"], gs2["z"], "ship controls 0 0 0 true"))

    # ---- the rowboat on her own ----
    B = (70, 240)
    launch(log, "rowboat", *B, "north")
    time.sleep(8)
    st = status(log, *B)
    check("rowboat floats upright", abs(st["roll"]) < 3 and abs(st["pitch"]) < 3, f"roll {st['roll']:.2f} pitch {st['pitch']:.2f}")
    top = st["y"]
    check("and sits on the water, not under it", SEA[1] + 7 <= top <= SEA[1] + 10, f"centre at y {top:.2f} (sea surface {SEA[1] + 8})")
    log.new()
    rcon(at(*B, "ship row 1 0 120"))
    taken = wait_for(log, r"PROBE oars taken", 3) or ""
    check("take the oars", "taken=true rowboat=true row=1.0" in taken, taken.split("PROBE ")[-1] or "nothing")
    time.sleep(5)
    st = status(log, *B)
    check("rowing moves her along", float(st["speed"]) > 2.5, f"{float(st['speed']):.2f} m/s")
    yaw0 = st["yaw"]
    time.sleep(2)
    rcon(at(st["x"], st["z"], "ship row 0.3 1 80"))
    time.sleep(4.5)
    st = status(log, st["x"], st["z"])
    turned = yaw_delta(yaw0, st["yaw"])
    check("pulling one oar turns her", abs(turned) > 30, f"turned {turned:+.0f} degrees")
    rcon(at(st["x"], st["z"], "ship row -1 0 80"))
    time.sleep(3.5)
    st = status(log, st["x"], st["z"])
    check("backing water rows her astern", float(st["speed"]) < -0.5, f"{float(st['speed']):.2f} m/s")
    time.sleep(1)
    rcon(at(st["x"], st["z"], "ship remove"))

    # ---- the armed rowboats ----
    for v, spot in (("cannon", (30, 235)), ("harpoon", (55, 235))):
        launch(log, f"rowboat_{v}", *spot, "north")
        time.sleep(7)
        st = status(log, *spot)
        check(f"{v} rowboat floats upright", st.get("layout") == f"rowboat_{v}" and abs(st["roll"]) < 3 and abs(st["pitch"]) < 4
              and SEA[1] + 7 <= st["y"] <= SEA[1] + 10, f"roll {st['roll']:.2f} pitch {st['pitch']:.2f} y {st['y']:.2f}")
        log.new()
        if v == "cannon":
            rcon(at(*spot, "ship fire bow cannonball 5"))
            fired = wait_for(log, r"PROBE fired CANNONBALL", 3)
            check("the cannon rowboat fires from her bow", bool(fired), (fired or "no shot").split("PROBE ")[-1])
        else:
            rcon(at(*spot, f"ship harpoon {st['x']:.1f} {SEA[1] + 9.3} {st['z'] - 18:.1f} 40"))
            fired = wait_for(log, r"PROBE harpoon fired", 3)
            check("the harpoon rowboat fires her harpoon", bool(fired), (fired or "no shot").split("PROBE ")[-1])
        time.sleep(2)
        rcon(at(st["x"], st["z"], "ship remove"))

    # ---- docking alongside the sloop, and being towed when she sails ----
    H = (40, 40)
    launch(log, "sloop", *H, "south")
    time.sleep(6)
    rcon(at(*H, "ship controls 0 0 0 true"))
    hs = status(log, *H)
    D = (H[0] + 13, H[1])
    launch(log, "rowboat", *D, "south")
    time.sleep(6)
    log.new()
    rcon(at(*D, "ship dock 20"))
    docked = wait_for(log, r"PROBE rowboat docked", 3)
    check("rowboat docks at the sloop's ladder", bool(docked), (docked or "no ladder found").split("PROBE ")[-1])
    time.sleep(12)
    rs = status(log, *D)
    rs = status(log, rs["x"], rs["z"])
    check("and lies alongside", rs.get("docked") == "true" and 0 <= float(rs["gap"]) < 1.5, f"hook {float(rs['gap']):.2f} blocks from its place")
    rcon(at(hs["x"], hs["z"], "ship controls 0 3 0 false"))
    time.sleep(14)
    hs2 = status(log, hs["x"], hs["z"] + 80)
    hs2 = status(log, hs2["x"], hs2["z"])
    rs = status(log, hs2["x"] + (rs["x"] - hs["x"]), hs2["z"] + (rs["z"] - hs["z"]))
    gs = hs2
    check("docked, she is towed along", rs.get("docked") == "true" and float(rs["gap"]) < 2.5 and float(gs["speed"]) > 5,
          f"sloop {float(gs['speed']):.1f} m/s, rowboat {float(rs['speed']):.1f} m/s, hook {float(rs['gap']):.2f} off")
    log.new()
    rcon(at(rs["x"], rs["z"], "ship dock 20"))
    check("cast off", bool(wait_for(log, r"PROBE rowboat undocked", 3)), "undocked")

    if not keep:
        rcon(at(rs["x"], rs["z"], "ship remove"), at(gs["x"], gs["z"], "ship remove"), at(gx, gz, "ship remove"))
    failed = [n for n, ok in results if not ok]
    print(f"\ninteractions: {len(results) - len(failed)}/{len(results)} passed" + (f"; failed: {', '.join(failed)}" if failed else ""))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
