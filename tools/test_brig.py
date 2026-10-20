"""The brig: votes lock a crewmate up and let them out, the door stays locked, a prisoner can't work the ship or
get out, explosions don't reach them, they respawn in the brig, and leaving the crew ends it. The Sloop has no brig.

usage: python tools/test_brig.py [brigantine|galleon]   (dev server and client running: the client's player is Dev)
"""
import json
import re
import sys
import time

from test_ship import ROOT, SURFACE, Log, at, check, launch, rcon, results, wait_for

X, Z = 48, 60


def state(log, x=X, z=Z):
    log.new()
    rcon(at(x, z, "ship brigstate"))
    line = wait_for(log, r"PROBE brigstate", 3) or ""
    return dict(re.findall(r"(\w+)=(\S*)", line.split("PROBE brigstate", 1)[-1]))


def vote(log, voter, target, lock, x=X, z=Z):
    log.new()
    rcon(at(x, z, f"ship brigvote {voter} {target} {str(lock).lower()}"))
    time.sleep(0.6)
    return " | ".join(l.split("PROBE ", 1)[1] for l in log.new() if "PROBE brig" in l)


def use(log, what, x=X, z=Z):
    log.new()
    rcon(at(x, z, f"ship useat Dev {what}"))
    line = wait_for(log, r"PROBE useat", 3) or ""
    return line.split("PROBE useat ", 1)[-1]


def main():
    layout = sys.argv[1] if len(sys.argv) > 1 else "brigantine"
    spec = json.loads((ROOT / "src/main/resources/data/seafarers/ships" / f"{layout}.json").read_text())
    helm = next(c for c in spec["cells"] if c[3].startswith("seafarers:helm"))
    stand = spec["respawns"][1]   # a spot on deck
    rel = (stand[0] - helm[0], stand[1] - helm[1], stand[2] - helm[2])
    log = Log()
    import test_rescue
    rcon("forceload add -16 -16 112 272", "seafarers test sea 0 100 0")
    test_rescue.client()   # (Dev on the server: starts the client if need be)
    rcon("forceload add -16 -16 112 272", "seafarers test sea 0 100 0", "seafarers wind 0", "gamemode survival Dev",
         "seafarers config brigMinimumMinutes 5", "effect clear Dev")
    time.sleep(2)
    launch(log, layout, X, Z, "south")
    time.sleep(6)
    on_deck = f"execute as Dev positioned {X} {SURFACE + 2} {Z} run seafarers ship view {rel[0]} {rel[1]} {rel[2]} 0 0"
    rcon(at(X, Z, "ship controls 0 0 0 true"), at(X, Z, "ship crew Dev"), at(X, Z, "ship hand Anne"), at(X, Z, "ship hand Bonny"),
         on_deck)
    time.sleep(1)
    print(f"Testing the brig on the {layout}")
    st = state(log)
    check("she has a brig, empty", st.get("has") == "true" and not st.get("prisoners"), str(st))
    check("most of a crew of three is two", st.get("need") == "2", f"need {st.get('need')}")

    said = vote(log, "Dev", "Dev", True)
    check("nobody votes themselves in", "ok=false" in said, said)
    said = vote(log, "Anne", "Dev", True)
    st = state(log)
    check("one vote isn't enough", "(1/2)" in said and not st.get("prisoners"), said)
    said = vote(log, "Bonny", "Dev", True)
    time.sleep(1)
    st = state(log)
    check("a majority locks them in the brig", "brig locked Dev" in said and st.get("prisoners") == "Dev:in", f"{said} -> {st}")
    check("and shuts the door", st.get("doorOpen") == "false", str(st))

    used = use(log, "door")
    check("the prisoner can't open the door", "open=false" in used, used)
    used = use(log, "helm")
    check("or work the ship", "FAIL" in used, used)

    log.new()
    rcon(on_deck)
    back = wait_for(log, r"PROBE brig returned Dev", 4)
    st = state(log)
    check("out of the brig, they're put straight back", bool(back) and st.get("prisoners") == "Dev:in", str(st))

    log.new()
    rcon("damage Dev 6 minecraft:explosion")
    sheltered = wait_for(log, r"PROBE brig sheltered Dev", 3)
    check("explosions don't reach into the brig", bool(sheltered), sheltered or "hurt")

    log.new()
    rcon("kill Dev")
    time.sleep(1)
    rcon("seafarers respawn Dev")
    time.sleep(2)
    st = state(log)
    check("they respawn in the brig", st.get("prisoners") == "Dev:in", str(st))

    said = vote(log, "Anne", "Dev", False)
    check("the crew can't let them out too soon", "ok=false" in said and "minute" in said, said)
    rcon("seafarers config brigMinimumMinutes 0")
    vote(log, "Anne", "Dev", False)
    said = vote(log, "Dev", "Dev", False)   # the prisoner's own vote counts toward letting them out
    time.sleep(0.5)
    st = state(log)
    check("a majority lets them out, and the door opens", "brig released Dev" in said and not st.get("prisoners")
          and st.get("doorOpen") == "true", f"{said} -> {st}")
    a = use(log, "door")
    b = use(log, "door")
    check("an empty brig's door opens and shuts by hand", "open=false" in a and "open=true" in b, f"{a}; {b}")

    vote(log, "Anne", "Dev", True)
    said = vote(log, "Anne", "Dev", True)
    check("voting again takes the vote back", "withdrawn" in said, said)

    vote(log, "Anne", "Dev", True)
    vote(log, "Bonny", "Dev", True)
    rcon("execute as Dev run crew leave")
    time.sleep(0.5)
    st = state(log)
    check("leaving the crew ends it", not st.get("prisoners"), str(st))
    rcon(at(X, Z, "ship remove"))

    # the Sloop has no brig
    S = (48, 150)
    launch(log, "sloop", *S, "south")
    time.sleep(4)
    rcon(at(*S, "ship crew Dev"), at(*S, "ship hand Anne"))
    said = vote(log, "Anne", "Dev", True, *S)
    check("the Sloop has no brig", "no brig" in said.lower(), said)
    rcon(at(*S, "ship remove"), "seafarers config brigMinimumMinutes 5")

    failed = [n for n, ok in results if not ok]
    print(f"\nbrig ({layout}): {len(results) - len(failed)}/{len(results)} passed" + (f"; failed: {', '.join(failed)}" if failed else ""))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
