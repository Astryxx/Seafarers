# Seafarers

Crew-based pirate sailing for Minecraft 1.21.1 (NeoForge), inspired by Sea of Thieves. Ships are real block
structures you walk around on while they sail, powered by [Sable](https://modrinth.com/mod/sable) physics.

Fan-made. Not affiliated with Rare or Microsoft. No Sea of Thieves assets are used; all art is original.

## The Sloop (v0.4)

The layout follows Sea of Thieves' Sloop. Proportions were measured from a reference model at 8 blocks per
model unit; no geometry is copied. It is defined in `tools/ships/sloop.py`.

**Levels**
- Hold floor y 1, cabin floor y 3, main deck y 4, aft deck y 6.

**Aft deck (raised)**
- The wheel at its forward edge behind a gilded rail.
- The capstan at the stern, with the anchor going over the transom.
- A sail length cleat and an angle pulley at each rear corner.
- A ladder up each side beside the helm, and stairs up from the main deck on the starboard side.

**Cabin and gallery (under the aft deck)**
- The cabin has the captain's table, the armoury chest, barrels and a rug, with big side windows and shutters.
- Aft of it is an open stern gallery under a cloth canopy, with the map table.
- One step up through the door to the main deck; stairs down into the hold.

**Main deck**
- The mast stands forward of midships, with a round crow's nest, a rope ladder and pennants.
- A 10x10 square sail with sheets running to the stern corners, plus shrouds and a forestay.
- One cannon per side with a grating between them, two harpoons, and cannonball barrels.
- A long, angled bowsprit and a figurehead.

**Hold**
- The bed (where the brig was), grog, clothing and vanity chests, plank and banana barrels, a stove and the equipment chest.

**Voxel hull**
- The hull is a smooth shape (broad transom, fine bow, flat-bottomed U section, low waist, tall aft cabin) filled with 4-pixel voxels.
- Details: a one-voxel plank skin, gold stripes and cabin posts, and a solid bilge under the hold floor.
- Each distinct block-sized chunk is one entry in a shape palette (`seafarers:voxel_hull[shape=N]`), with a generated model and an exact collision shape.
- `tools/ships/voxel.py` writes the palette, models, collision masks and per-shape mass and buoyancy.
- `tools/ships/preview.py sloop` renders side, top and front views without the game.

| System | How it works | Tested (headless unless noted) |
|---|---|---|
| Launching | Use a ship's **deed** (Sloop, Brigantine, Galleon) on open water; the bow points the way you face. You become her captain. | ✅ + client |
| Floating | Sable buoyancy, with a heavy keel. The hull is tuned so the sea sits just below the lower-deck floor. A self-righting assist means she never capsizes. | ✅ level, waterline −0.16 below the floor |
| Sail | **Length cleat:** use to let the sail down a step, sneak-use to haul it up. The sail is drawn live: it swings, furls, bellies when drawing and flaps when luffing. | ✅ + client |
| Trim and wind | Speed depends on the point of sail (running and reaching are fast, close-hauled slow, a headwind slows you hard) and on trim (the **angle pulleys**, ±45°). The HUD says *full / luffing / slack*. | ✅ 6.9 m/s beam reach trimmed vs 1.8 untrimmed |
| Wheel | Take the wheel; A/D turn it, and it stays where you leave it. It spins one full turn lock to lock. | ✅ real keyboard input in the client |
| Capstan | The drum turns as the crew heave and spins back when the anchor is let go. The cable runs from the capstan to the bow and down into the sea; when weighed, the anchor hangs at the bow. | ✅ client |
| Anchor turn | Drop anchor at speed with the wheel over and she pivots hard round it. Raise it by holding use on the capstan. | ✅ 167° in 6 s from 5.7 m/s |
| Cannons | Load by using ammo on it, use it to take over, look to aim (±50°), left-click to fire. The barrel visibly follows your aim. | ✅ hits a second sloop; real click input fires |
| Chain shot | It tangles in a set sail or hits the mast. Two hits break the mast and drop the sail; patch it with planks on the mast. Bounces off hulls. | ✅ |
| Holes and flooding | Holes in the bottom leak fully; holes just above the waterline leak a little. Bilge water rises visibly through the lower deck. Bail with a bucket anywhere aboard; patch holes with planks. | ✅ + client |
| Sinking | At 100% she goes down and becomes a wreck. Her crew gets a new ship of the same kind 150–300 blocks away and respawns aboard it. | ✅ sank, replacement launched, crew respawned on it |
| Supplies | Cannonball barrels (40 each), plank barrel (35), food barrels (16 bananas): use to take, sneak-use to put back. | ✅ |
| Crew | `/crew invite <player>`, `/crew accept`, `/crew leave`, `/crew list`. The Sloop holds 2 crew, who respawn aboard when they die. | ✅ respawn aboard; invite/accept flow needs two players |

## Adding a ship

(The Brigantine and Galleon were added this way: see `tools/ships/brigantine.py` and `galleon.py`.)

The ship systems are data-driven, so a new ship is mostly a new layout script:

1. **Copy the layout script.** Copy `tools/ships/sloop.py` to `tools/ships/<name>.py` and change the hull shape
   (`plan`, `keel`, `sheer`, the decks) and the fittings. Give every mast, yard and sail rope a `mast=N` index
   (0 is the mainmast). Set `s.masts`, `s.crew` and `s.stats`; the fields are listed in
   `dev.seafarers.ship.ShipStats`.
2. **Give parts their sizes.** Pass `data={...}` to `put()` for the parts whose size is per ship: the yard
   (sail size, shrouds, forestay; see `SailYardBlockEntity`), the bowsprit (`BowspritBlock.Heel`) and the capstan
   (`AnchorBlock.Capstan`).
3. **Build.** Add the name to `SHIPS` in `tools/ships/build_all.py` and run it. Every ship shares one voxel shape
   palette, and the fit checker must report 0 problems.
   The palette (`tools/ships/palette_keys.json`) is append-only: every ship block in a world stores its shape's
   number. Version 1.0.0 shipped with 984 shapes; never remove or reorder those. Shapes left over from trial builds
   after them may be trimmed before a release.
4. **Add a deed.** Register an item in `ModItems`, like `SLOOP_DEED` (`new ShipDeedItem("<name>", ...)`).
5. **Test.** Run `python tools/test_ship.py <name>` against the dev server; all checks must pass. Then tune
   buoyancy (`liftTrim`) and `waterDrag` from what it reports.

`tools/test_ship.py` checks:
- **Afloat:** the fit check, floating level, and a dry hold.
- **Controls:** the cleats and pulleys on every mast, downwind speed, sail trim, the capstan dropping and
  heaving the anchor, and the wheel (take it, steer, let go) and turning.
- **Damage:** holes, bilge water, bailing, patching and supply barrels.
- **Combat:** cannonball hits between two ships, chain shot breaking a mast, and mending it.
- **Sinking:** sinking, the wreck, and a new ship for the crew.

## Developing

```
gradlew runServer        # dev server (RCON on 25575)
gradlew runClientJoin    # client that auto-joins the dev server at 1280x720
gradlew runClient2       # second player ("Deckhand") for crew testing
tools/restart.sh [--fresh]                         # restart the server (optionally on a new world)
tools/look.sh                                      # server + client + a sloop, screenshots into shots/
python tools/scenario.py "0:cmd" "5:cmd" --wait 10 # timed test script; prints PROBE lines from the log
python tools/ships/build_all.py                    # rebuild every ship layout + the shared voxel palette
python tools/test_ship.py sloop                    # regression test: every ship system, PASS/FAIL
python tools/gen_resources.py                      # models, blockstates, lang, physics data
python tools/gen_textures.py                       # placeholder pixel art
python tools/gen_fal_textures.py [--convert]       # fal-generated textures (sources in art/fal, graded to 16x16)
python tools/gen_handbook.py                       # the Captain's Handbook (Patchouli book): edit its text there
python tools/gen_advancements.py                   # the advancement tree (gen_resources.py reads its text)
```

Test commands (all `/seafarers ...`, op only). They're only registered with `debugCommands = true` in
`config/seafarers-common.toml` (the dev runs have it on; a release defaults to off). The suites are
`tools/test_ship.py sloop|brigantine|galleon`, `tools/test_brig.py brigantine|galleon` (the brig: votes, the locked door,
keeping the prisoner in; needs the client), `tools/test_rescue.py` (mermaids, freeing a ship that's run aground, and
screenshots of the status panel; needs the client), `tools/test_interactions.py`, `tools/test_bed.py`, `tools/test_walk.py` and
`tools/test_captains.py`, `tools/test_extras.py` and `tools/test_compat.py` (the last five need the client;
test_compat loads the optional mods: `COMPAT=1 tools/restart.sh --fresh && python tools/test_compat.py`, and
again with `NOEMI=1` on both to check JEI, which EMI otherwise stands in for; test_extras covers
advancements, named ships, hints, explosions, friendly fire, death messages and shipwreck loot; test_captains covers the ship choice screen, deeds, captains,
locks and swaps; `tools/look_captains.py` screenshots those screens; test_walk walks the real player up every stair and ladder and round the crow's nest, using
`/seafarers drive`, which makes the client press its own movement keys). `/seafarers ship dumpshapes` writes the ship's
real collision to `ship_shapes.json` for `tools/ships/walksim.py`.

- `test sea <pos>`, `sloop <pos> <dir>`, `wind <angle>|unlock`, `tune <FIELD> <value>`
- `ship status|probe <s>|controls <wheel> <sails> <angle> <anchored>|hole <n>|bail <n>|repair`
- `ship fire <port|starboard> <cannonball|chain_shot> <pitch>`
- `ship view <x> <y> <z> <yaw> <pitch>` (camera relative to the helm), `ship crew <player>`, `ship supplies <player>`
- `config <name> <value>` (any setting in seafarers-common.toml, for this run), `pick <player> <mode> <sloop|crew>`
  (a pick on the ship choice screen), `useitem <player>`, `deeds <player>`, `ship owner`, `ship table <player> <action> <index> <text>`

The dev server's `run/server/config/seafarers-common.toml` turns the ship choice screen off, since it would stop
the client tests from moving; test_captains turns it on for itself.

Block physics (mass/volume) only reload on a full server restart, not `/reload`.

## Next
- A waving flag; a third-person cannon camera so you can see where you aim.
- Harpoon guns that work, the rowboat dock, cannon-firing yourself out of the cannon.
- The Ferry of the Damned between death and respawn.
- Gunpowder kegs; treasure and somewhere to sell it; skeleton ships; the Galleon's second tier of sails.
- Islands, outposts and voyages.
