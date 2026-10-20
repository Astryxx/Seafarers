"""The Captain's Handbook: the mod's guide, as a Patchouli book (optional: without Patchouli it's simply not there).

python tools/gen_handbook.py

Writes data/seafarers/patchouli_books/handbook/book.json, the chapters and pages under
assets/seafarers/patchouli_books/handbook/en_us/, and a recipe (a book and any boat) that only loads with
Patchouli installed. To change the book, edit the text here and run this again; bump VERSION when pages are added
so Patchouli marks them as new.

Text: $(l)bold$(), $(item)an item$(), $(br) new line, $(br2) new paragraph, $(li) bullet, and $(k:use) for the
player's own key for an action (use, attack, sneak, forward, back, left, right, jump). A text page holds about 300
characters; a page with an item on it about 180.
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
DATA = ROOT / "data" / "seafarers" / "patchouli_books" / "handbook"
BOOK = ROOT / "assets" / "seafarers" / "patchouli_books" / "handbook" / "en_us"
VERSION = 7


def w(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def text(t):
    return {"type": "patchouli:text", "text": t}


def item(i, t, title=None):
    page = {"type": "patchouli:spotlight", "item": i, "text": t}
    if title:
        page["title"] = title
    return page


CATEGORIES = [
    ("getting_started", "Getting Started", "Your ship, and how to get her to sea.", "seafarers:sloop_deed"),
    ("sailing", "Sailing", "The wheel, the sails, the anchor and the wind.", "seafarers:helm"),
    ("afloat", "Keeping Her Afloat", "Holes, water, fire and broken masts.", "seafarers:ship_bucket"),
    ("fighting", "Fighting", "Cannons, shot and harpoons.", "seafarers:cannon"),
    ("aboard", "Life Aboard", "The galley, the stores and everything else on deck.", "seafarers:stove"),
    ("crew", "Crew and Captain", "Sailing with friends, and running your ship.", "seafarers:voyage_table"),
]

# category -> list of (entry id, name, icon, pages)
ENTRIES = {
    "getting_started": [
        ("welcome", "Welcome Aboard", "seafarers:sloop_deed", [
            text("Seafarers puts a real ship under your feet: built of blocks, sailing on the sea, and yours to walk "
                 "while she moves. You steer her, set her sails, fire her guns, bail her hold and, if it comes to it, go "
                 "down with her.$(br2)This book covers everything aboard. Start with $(l)Your First Ship$()."),
            text("The first few times you use something aboard, a $(l)hint$() above your hotbar says how it works. "
                 "Your $(l)advancements$() (the Seafarers tab) are a good list of things to try."),
        ]),
        ("first_ship", "Your First Ship", "seafarers:sloop_deed", [
            text("The first time you join a world you choose your ship: the $(l)Sloop$(), the $(l)Brigantine$() or the "
                 "$(l)Galleon$() (see $(l)The Three Ships$()).$(br2)You can also choose to $(l)join a crew$() and sail "
                 "aboard a friend's ship instead; you can still get a ship of your own later."),
            item("seafarers:sloop_deed", "Choosing a ship gives you her $(item)deed$(). Take it to $(l)open water$(), well "
                 "away from land and other ships (she's big!), look at the water and press $(k:use). She's launched with "
                 "her bow the way you face, and you're her $(l)captain$()."),
            text("$(l)Name her$(): rename the deed in an $(item)anvil$() before you launch her, and she takes that name. "
                 "You can rename her later at her quest table."),
            text("A deed is $(l)hard to lose$(): it won't burn, blow up, despawn or fall out of the world, and it stays "
                 "with you when you die. If it's lost anyway, $(l)/crew reclaim$() gives you a new one, and the lost one "
                 "stops working.$(br2)Deeds can't be crafted or found. Each player captains one ship at a time."),
        ]),
        ("ships", "The Three Ships", "seafarers:galleon_deed", [
            item("seafarers:sloop_deed", "The $(l)Sloop$(): one mast, two cannons, a crew of up to two. Small and nimble, "
                 "quick to turn and to heave her anchor."),
            item("seafarers:brigantine_deed", "The $(l)Brigantine$(): two masts, four cannons, a crew of up to three, and a "
                 "brig. The fastest ship of all with the wind on her beam, but she sinks the quickest: everything below "
                 "is on one deck."),
            item("seafarers:galleon_deed", "The $(l)Galleon$(): three masts, eight cannons on her gun deck, a crew of up to "
                 "four, and a brig. Fastest running downwind, but slow to turn: she needs her whole crew. Hardest "
                 "to sink, until the water reaches her gun deck."),
        ]),
        ("compass", "Finding Your Ship", "seafarers:ships_compass", [
            item("seafarers:ships_compass", "The $(item)Ship's Compass$() points to the ship you crew, wherever she's "
                 "sailed; hold it to see how far she is. You get one with your first deed, and you can craft more from "
                 "a $(item)compass$() and a $(item)copper ingot$()."),
            text("If she's too far away to be tracked, the needle points to where she was last seen. With no ship, it "
                 "spins.$(br2)Sleep in her $(l)bunk$() and you'll respawn aboard her wherever she is, so you can always "
                 "get back to her (see $(l)Life Aboard$())."),
        ]),
    ],
    "sailing": [
        ("wheel", "The Wheel", "seafarers:helm", [
            item("seafarers:helm", "Press $(k:use) on the $(item)wheel$() to take the helm. $(k:left) and $(k:right) "
                 "turn the wheel smoothly; let go of the keys and it stays where you left it. Press $(k:sneak) to step "
                 "away."),
            text("She turns faster the faster she's going, and barely at all when she's stopped. Watch the bar above "
                 "your hotbar: it shows the wind, your speed and how well your sail is drawing."),
        ]),
        ("sails", "The Sails", "seafarers:sail_rope", [
            item("seafarers:sail_rope", "Press $(k:use) on the $(item)Sail Length Cleat$() to take the rope. Hold "
                 "$(k:forward) to let the sail down, $(k:back) to haul it up; $(k:sneak) lets go. Half a sail is "
                 "slower, but easier to handle."),
            item("seafarers:brace_rope", "Take a $(item)Sail Angle Pulley$() the same way and hold $(k:left) or "
                 "$(k:right) to swing the sail round. A sail square to the wind pulls hardest; edge-on, hardly at all."),
            text("The $(l)wind$() shifts every few minutes. With it behind you, set the sail square; with it on your "
                 "beam, angle the sail to match. The status bar tells you how well the sail is drawing, so trim until it "
                 "says it's full."),
            text("The Brigantine and the Galleon have a $(l)cleat and a pulley each side of every mast$(), at its foot: "
                 "the mainmast's, the foremast's and the Galleon's mizzen's. Each sail is set and trimmed on its own, and "
                 "she only makes her best speed with all of them drawing, so share the ropes out among the crew."),
        ]),
        ("anchor", "The Anchor", "seafarers:anchor", [
            item("seafarers:anchor", "Press $(k:use) on the $(item)capstan$() to drop the anchor: she stops dead. Hold "
                 "$(k:use) to heave it back up. Let go before it's all the way up and it unwinds again, so keep "
                 "heaving."),
            text("$(l)The anchor turn:$() drop the anchor at speed with the wheel hard over and she pivots on the spot. "
                 "Handy for a tight turn, or to swing her guns round in a fight."),
            text("The bigger ships' anchors are heavier. Their capstans have more bars: three on the Brigantine, four on "
                 "the Galleon, and every crewmate on a bar heaves it up faster."),
        ]),
        ("status", "Her Status", "minecraft:compass", [
            text("Aboard, her $(l)status$() shows in the top left: her speed, a $(l)wind dial$() (her bow points up; the "
                 "arrow is where the wind blows), and a row for each sail: how far it's let down, its angle, and whether "
                 "it's $(l)full$(), luffing or slack."),
            text("The pips by each sail are her mast's health. Below them are the $(l)wheel$() (where the rudder is), "
                 "then water and holes, fire and the anchor when there's something to tell.$(br2)Turn it off in "
                 "Mods, Seafarers, Config."),
        ]),
        ("rowboats", "Rowboats", "seafarers:rowboat", [
            item("seafarers:rowboat", "$(item)Rowboats$() wait, tied up, along the world's coasts, and anyone can row "
                 "one. Take the $(item)rowing bench$(): $(k:forward) and $(k:back) row, $(k:left) and $(k:right) turn. "
                 "Some carry a cannon or a harpoon gun."),
            item("seafarers:rowboat_hook", "Use the $(item)rowboat hook$() beside your ship's ladder to tie a rowboat "
                 "on; she's towed wherever the ship goes."),
        ]),
    ],
    "afloat": [
        ("holes", "Holes and Water", "minecraft:oak_planks", [
            text("A cannonball through the hull leaves a $(l)hole$(). Holes $(l)below the waterline$() let the sea in; "
                 "holes above it don't, unless she sinks low enough to put them under.$(br2)Water rises in the hold, she "
                 "sits lower and slows, and in the end she goes down.$(br2)Run her aground at speed and the hull is holed "
                 "where she strikes."),
            text("The $(l)Galleon$()'s deep lower deck takes a lot of water, and holes in her gun deck stay dry while she "
                 "rides high. But once the water is over her lower deck it pours in fast. The $(l)Brigantine$() has only "
                 "one deck below, and fills quickest of all."),
            item("minecraft:oak_planks", "Patch a hole by pressing $(k:use) on it with $(item)planks$() in hand. Her "
                 "supply barrels carry planks."),
            item("seafarers:ship_bucket", "Bail with a $(item)bucket$() from the bucket rack: fill it from the water in "
                 "the hold, then throw it over the side with $(k:use). Patch first, then bail!"),
        ]),
        ("fire", "Fire", "seafarers:firebomb", [
            text("A $(item)firebomb$() sets a ship alight. Fire grows, spreads along the deck and in time burns through "
                 "the planks, leaving holes.$(br2)Put it out by throwing water on it: a bucket of water from the hold or "
                 "the water barrel."),
        ]),
        ("masts", "Broken Masts", "seafarers:chain_shot", [
            text("$(item)Chain shot$() tears sails and breaks masts. A broken mast drops her sail, and she can't be "
                 "sailed until it's mended: press $(k:use) on the mast with $(item)planks$(). A torn sail spills some "
                 "wind until it's repaired."),
        ]),
        ("overboard", "Overboard", "minecraft:heart_of_the_sea", [
            text("Fall overboard and get left behind? Keep swimming. Once you're well away from your ship, a $(l)mermaid$() "
                 "comes up beside you. Swim to her, or use her, and she takes you back aboard, wherever your ship has "
                 "sailed."),
            text("She only comes for crew whose ship is still afloat, and goes again once you're back near your ship "
                 "or out of the water."),
        ]),
        ("aground", "Run Aground", "minecraft:sand", [
            text("Stuck on the rocks or a beach and can't sail off? Any of her crew can have her $(l)towed off$() to open "
                 "water nearby: the $(l)Free her$() button on the quest table's Ship tab, or $(l)/crew free$(). She comes "
                 "up level and at rest, and her crew aboard come with her."),
            text("It only works when she's really aground, and once every few minutes. Patch her up first: a crash at "
                 "speed holes the hull."),
        ]),
        ("sinking", "Going Down", "seafarers:sloop_deed", [
            text("When she's full of water there's no saving her: she goes down. Don't despair, sailor: a $(l)new "
                 "ship$() of the same kind will be waiting for her crew nearby, and you'll respawn aboard her.$(br2)A ship "
                 "whose whole crew has left her sinks on her own after a while."),
            item("minecraft:barrel", "Whatever was in her barrels, chests and casks floats up in $(item)shipwreck barrels$(). "
                 "Anyone can open one, or catch it with a harpoon. They sink for good after a while."),
        ]),
    ],
    "fighting": [
        ("cannons", "Cannons", "seafarers:cannon", [
            item("seafarers:cannon", "Load a $(item)cannon$() by pressing $(k:use) with ammunition in hand. Press "
                 "$(k:use) again to man it: the view moves behind the gun and follows your aim. $(k:attack) fires; "
                 "$(k:sneak) steps away."),
            text("Lead your shot: ships move, and cannonballs drop over distance. A hit below the waterline does the "
                 "most harm.$(br2)Man an empty cannon and press $(k:use) with nothing in hand to climb into the barrel, "
                 "then fire to launch yourself across the sea."),
        ]),
        ("ammo", "Ammunition", "seafarers:cannonball", [
            item("seafarers:cannonball", "$(item)Cannonballs$() hole hulls. Her supply barrels start with plenty, and "
                 "you can craft more from iron nuggets."),
            item("seafarers:chain_shot", "$(item)Chain shot$() tears sails and breaks masts, but bounces off hulls."),
            item("seafarers:firebomb", "$(item)Firebombs$() set the ship they hit alight."),
        ]),
        ("cursed", "Cursed Cannonballs", "seafarers:jig_ball", [
            text("Cursed cannonballs put a curse on the ship they hit and anyone they splash:$(br)$(li)$(l)Anchorball$(): "
                 "drops her anchor$(li)$(l)Riggingball$(): furls her sails$(li)$(l)Ballastball$(): weighs her down"
                 "$(li)$(l)Grogball$(): makes you drunk$(li)$(l)Limpball$(): slows you$(li)$(l)Venomball$(): poisons"
                 "$(li)$(l)Wearyball$(): tires you$(li)$(l)Jigball$(): makes you dance"),
        ]),
        ("harpoons", "Harpoon Guns", "seafarers:harpoon", [
            item("seafarers:harpoon", "Man a $(item)harpoon gun$() with $(k:use), aim, and fire with $(k:attack). Hold "
                 "$(k:forward) to reel in. It catches floating loot, or bites into land or another ship and pulls you "
                 "to it."),
        ]),
    ],
    "aboard": [
        ("bunk", "The Bunk", "seafarers:mattress", [
            item("seafarers:mattress", "Sleep in her $(item)bunk$() to make her your home: when you die, you'll respawn "
                 "aboard her wherever she's sailed. Sleeping in a bed ashore makes that your home again."),
        ]),
        ("galley", "The Galley", "seafarers:stove", [
            item("seafarers:stove", "Put raw food in the $(item)galley stove$()'s frying pan (any food that can be cooked). "
                 "Watch it brown, and take it off when the smoke turns white. Leave it too long and it chars."),
            item("seafarers:supply_barrel", "The $(item)grog barrel$() fills a glass bottle or a bucket. Each drink makes "
                 "you drunker. Too many and the world spins, and you'll be sick: catch it in an empty bucket."),
        ]),
        ("stores", "Stores", "seafarers:supply_barrel", [
            item("seafarers:supply_barrel", "Her $(item)supply barrels$() hold cannonballs, planks and food, stocked when "
                 "she's launched. The $(item)sea chest$() and $(item)casks$() are for your own things. They're all ordinary "
                 "containers, so hoppers and storage mods work with them."),
            item("seafarers:water_barrel", "The $(item)water barrel$() works like a cauldron: fill a bucket or a bottle. "
                 "It refills itself in time."),
        ]),
        ("map_table", "The Map Table", "seafarers:map_table", [
            item("seafarers:map_table", "The $(item)map table$() shows a chart of the seas around her, with her arrow "
                 "moving as she sails: a blank sea chart, filled in by her crew. The table shows the chart as "
                 "you left it."),
            text("Use the table to unroll the chart. Drag to move it, scroll to zoom, click to put down an X, right "
                 "click to rub one out, and C to find the ship again.$(br2)With a map mod, it opens that mod's map "
                 "($(k:sneak) for the chart)."),
        ]),
        ("quest_table", "The Quest Table", "seafarers:voyage_table", [
            item("seafarers:voyage_table", "The $(item)quest table$() is the ship's ledger: propose a treasure or explorer "
                 "map as a voyage, and when the whole crew agrees, everyone gets a copy. It shows her crew and state, "
                 "and renames her."),
        ]),
        ("trimmings", "Bells, Lanterns and Colours", "seafarers:ship_bell", [
            item("seafarers:ship_bell", "Ring the $(item)bell$() on the mast to call your crew: they hear it and see who rang."),
            text("Put her $(item)lanterns$() out to sail dark at night. Hoist any banner at the masthead with the "
                 "$(item)flag box$() up in the crow's nest. Use dye on the wheel to paint her hull, or on a sail rope to dye her sails."),
        ]),
    ],
    "crew": [
        ("crews", "Sailing Together", "seafarers:voyage_table", [
            text("A sloop takes a crew of two, a brigantine three and a galleon four. Her $(l)captain$() invites players aboard: at her $(l)quest table$(), on the "
                 "Crew tab, click a player standing nearby and press $(l)Invite$(). They click $(l)[Accept]$() in "
                 "chat.$(br2)Crew respawn aboard, and everyone in a crew can use the ship."),
            text("$(l)Commands$()$(br)$(li)/crew invite <player>$(li)/crew accept$(li)/crew leave$(li)/crew list"
                 "$(li)/crew choose: a ship of your own$(li)/crew reclaim: replace a lost deed"
                 "$(li)/crew brig <player>, /crew release <player>: brig votes$(li)/crew free: off the rocks"),
        ]),
        ("brig", "The Brig", "seafarers:brig_door", [
            item("seafarers:brig_door", "The Brigantine and Galleon have a $(l)brig$(). When $(l)most of the crew$() "
                 "votes for it, a crewmate is locked in. Vote on the quest table's $(l)Crew$() tab or with "
                 "$(l)/crew brig$(); vote again to take it back."),
            text("In the brig you can't work the ship, but you can still bail with a bucket. Explosions don't reach in. "
                 "You respawn there, and you're put straight back if you get out.$(br2)After a few minutes the crew can "
                 "vote you out again ($(l)/crew release <player>$(); your own vote counts). Leaving the crew ends it too."),
        ]),
        ("captain", "The Captain", "seafarers:sloop_deed", [
            text("Whoever launches a ship is her captain, marked with a star on the quest table. On the $(l)Crew$() tab "
                 "the captain can click a crew member to $(l)Remove$() them or $(l)Make captain$() to hand her over. "
                 "When a captain leaves, the next crew member takes command."),
            text("$(l)Locking her$(): on the $(l)Ship$() tab (or $(l)/crew lock$()). A locked ship's wheel, sails, guns, "
                 "stores and bunks work only for her crew, and only they can break or build on her. Anyone can still walk "
                 "aboard. New ships start locked."),
            text("$(l)Swapping her$(): the $(l)Ship$() tab's $(l)Swap ship$() button (or $(l)/crew swap$()) trades her "
                 "for a new ship. She and $(l)everything aboard her$() are gone, so empty her stores first! You get a new "
                 "deed, and your crew joins you on the new ship. There's a wait between free ships."),
        ]),
        ("settings", "For Server Owners", "minecraft:command_block", [
            text("Nearly everything here can be changed: Mods, Seafarers, Config, or the file "
                 "$(l)config/seafarers-common.toml$(). Each setting says what it does.$(br2)Crew sizes, sinking, "
                 "replacement ships, locks, swaps, the wait between ships, a server ship limit, damage, fire, the "
                 "wind and more."),
            text("$(l)/crew admin ships$() lists every ship; $(l)tp$(), $(l)remove$(), $(l)lock$(), $(l)unlock$() and "
                 "$(l)captain$() act on one. Permissions mods can grant $(l)seafarers.admin$(), "
                 "$(l)seafarers.bypass.locks$() and $(l)seafarers.bypass.limits$()."),
        ]),
    ],
}


def main():
    w(DATA / "book.json", {
        "name": "Captain's Handbook",
        "landing_text": "Everything a sailor needs to know: getting your ship to sea, sailing her, fighting, keeping "
                        "her afloat, and running a crew.$(br2)Fair winds!",
        "subtitle": "Seafarers",
        "version": VERSION,
        "use_resource_pack": True,
        "creative_tab": "seafarers:main",
        "model": "patchouli:book_brown",
        "book_texture": "patchouli:textures/gui/book_brown.png",
        "show_progress": False,
    })
    for sort, (cid, name, desc, icon) in enumerate(CATEGORIES):
        w(BOOK / "categories" / f"{cid}.json", {"name": name, "description": desc, "icon": icon, "sortnum": sort})
        for esort, (eid, ename, eicon, pages) in enumerate(ENTRIES[cid]):
            w(BOOK / "entries" / cid / f"{eid}.json", {"name": ename, "icon": eicon, "category": f"seafarers:{cid}",
                                                      "sortnum": esort, "pages": pages})
    # a book and any boat make the handbook, only when Patchouli is installed
    w(ROOT / "data" / "seafarers" / "recipe" / "handbook.json", {
        "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "patchouli"}],
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "minecraft:book"}, {"tag": "minecraft:boats"}],
        "result": {"id": "patchouli:guide_book", "count": 1, "components": {"patchouli:book": "seafarers:handbook"}},
    })
    # the longest pages, so overflowing text is caught here rather than in the book
    for cid, entries in ENTRIES.items():
        for eid, _, _, pages in entries:
            for i, p in enumerate(pages):
                limit = 300 if p["type"] == "patchouli:text" else 210
                plain = len(p["text"].replace("$()", "")) - 4 * p["text"].count("$(")
                if plain > limit:
                    print(f"  long page: {cid}/{eid} page {i + 1}: about {plain} characters (aim for {limit})")
    print(f"handbook: {sum(len(e) for e in ENTRIES.values())} entries in {len(CATEGORIES)} chapters")


if __name__ == "__main__":
    main()
