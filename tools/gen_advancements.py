"""The Seafarers advancements: python tools/gen_advancements.py (gen_resources.py runs it too, for the text).

Each advancement fires on a feat (dev.seafarers.ship.Feats): {"trigger": "seafarers:feat", "conditions": {"event": ...}}.
The tree, its icons and its text are all here.
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "data" / "seafarers" / "advancement"

# id, parent, icon, event, title, description, frame, hidden
ADVANCEMENTS = [
    ("root", None, "seafarers:sloop_deed", "start", "Seafarers", "A pirate's life for you: choose a ship, or join a crew", "task", False),
    ("set_sail", "root", "seafarers:helm", "launch", "Set Sail", "Launch your own ship", "task", False),
    ("big_ship", "set_sail", "seafarers:galleon_deed", "big_ship", "Ship of the Line", "Launch a Brigantine or a Galleon", "task", False),
    ("full_sail", "set_sail", "seafarers:sail_rope", "full_sail", "Full Sail", "Take the wheel at top speed, with the wind in your sail", "task", False),
    ("anchor_turn", "full_sail", "seafarers:anchor", "anchor_turn", "Hard Over", "Drop the anchor at speed with the wheel hard over, and pivot", "goal", False),
    ("chart", "set_sail", "seafarers:map_table", "chart", "X Marks the Spot", "Mark the chart on your map table", "task", False),
    ("human_cannonball", "set_sail", "seafarers:cannon", "human_cannonball", "Human Cannonball", "Climb into a cannon and fire yourself across the sea", "goal", False),
    ("patch", "set_sail", "minecraft:oak_planks", "patch", "Patched Up", "Patch a hole in a ship's hull", "task", False),
    ("bail", "patch", "seafarers:ship_bucket", "bail", "Bail Her Out", "Bail water out of a flooding hold", "task", False),
    ("douse", "patch", "seafarers:ship_bucket_water", "douse", "Firefighter", "Put out a fire aboard with thrown water", "task", False),
    ("mend", "patch", "seafarers:mast", "mend", "Mast Mended", "Mend a broken mast", "task", False),
    ("went_down", "patch", "seafarers:ship_lantern", "went_down", "Down With the Ship", "Be aboard when your ship goes down", "task", False),
    ("hit_ship", "set_sail", "seafarers:cannonball", "hit_ship", "Broadside", "Hit another ship with a cannonball", "task", False),
    ("curse", "hit_ship", "seafarers:jig_ball", "curse", "Cursed!", "Hit another ship with a cursed cannonball", "task", False),
    ("sink_enemy", "hit_ship", "seafarers:chain_shot", "sink_enemy", "Davy Jones' Locker", "Sink another crew's ship", "challenge", False),
    ("salvage", "hit_ship", "minecraft:barrel", "salvage", "Flotsam and Jetsam", "Open a shipwreck barrel", "task", False),
    ("mermaid", "root", "minecraft:heart_of_the_sea", "mermaid", "Friend of the Deep", "Be carried back to your ship by a mermaid", "task", False),
    ("harpoon", "root", "seafarers:harpoon", "harpoon", "Hooked", "Hit something with a harpoon gun", "task", False),
    ("crew", "root", "seafarers:voyage_table", "crew", "Shipmates", "Have a sailor join your crew, or join someone else's", "task", False),
    ("voyage", "crew", "minecraft:filled_map", "voyage", "Fortune Awaits", "Start a voyage that your whole crew agreed on", "goal", False),
    ("brig", "crew", "seafarers:brig_door", "brig", "Mutiny!", "Vote a crewmate into the brig", "task", False),
    ("grog", "root", "seafarers:grog", "grog", "Yo Ho Ho", "Drink some grog", "task", False),
    ("vomit", "grog", "seafarers:ship_bucket_vomit", "vomit", "Sea Legs? Never Heard of 'Em", "Drink until you're sick", "task", True),
]

LANG = {}
for aid, _, _, _, title, desc, _, _ in ADVANCEMENTS:
    LANG[f"advancements.seafarers.{aid}.title"] = title
    LANG[f"advancements.seafarers.{aid}.description"] = desc


def main():
    for aid, parent, icon, event, _, _, frame, hidden in ADVANCEMENTS:
        display = {
            "icon": {"id": icon},
            "title": {"translate": f"advancements.seafarers.{aid}.title"},
            "description": {"translate": f"advancements.seafarers.{aid}.description"},
            "frame": frame, "show_toast": True, "announce_to_chat": aid != "root", "hidden": hidden,
        }
        adv = {"display": display, "criteria": {event: {"trigger": "seafarers:feat", "conditions": {"event": event}}}}
        if parent:
            adv["parent"] = f"seafarers:{parent}"
        else:
            display["background"] = "minecraft:textures/block/spruce_planks.png"
            # the root also comes with any deed, so players handed one (no choice screen) still get the tree
            adv["criteria"]["deed"] = {"trigger": "minecraft:inventory_changed",
                                       "conditions": {"items": [{"items": "#seafarers:deeds"}]}}
            adv["requirements"] = [[event, "deed"]]
        path = ROOT / f"{aid}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(adv, indent=2) + "\n", encoding="utf-8")
    print(f"advancements: {len(ADVANCEMENTS)}")


if __name__ == "__main__":
    main()
