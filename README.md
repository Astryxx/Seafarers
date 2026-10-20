# Seafarers

Crew-based pirate sailing for Minecraft 1.21.1 (NeoForge), in the spirit of Sea of Thieves. Your ship is a real
ship made of blocks: you walk her decks (and out along the bowsprit) while she sails, man her guns, bail her hold and go down with her.
Ships are physics bodies thanks to [Sable](https://modrinth.com/mod/sable) (required). With
[Patchouli](https://modrinth.com/mod/patchouli) installed (optional), new players also get the **Captain's
Handbook**, an in-game guide to everything below.

**All three of Sea of Thieves' ships, fully fitted out: the Sloop, the Brigantine and the Galleon.** Voyages,
islands and outposts come later.

Fan-made. Not affiliated with or endorsed by Rare or Microsoft. No Sea of Thieves assets are used.

## Getting a ship

The first time you join a world you **choose your ship**, or choose to join a friend's crew instead. You get
her **deed**: use it on open water, well away from land, and she's launched with her bow the way you face, with
you as her captain. Sleep in her **bunk** to make her
your home: you'll respawn aboard wherever she sails.

Your deed can't be crafted or found, and it's hard to lose: it won't burn, blow up, despawn or fall out of the
world, and it stays with you when you die. If it's lost anyway, `/crew reclaim` gives you a new one (the old
one stops working). Deeds are also in the Creative menu, for server admins.

Rename the deed in an anvil and she takes that name. Your first deed comes with a **Ship's Compass**, which
points to your crew's ship wherever she's sailed; hold it to see how far she is. Craft more from a compass and a
copper ingot.

| | Sloop | Brigantine | Galleon |
|---|---|---|---|
| Crew | 2 | 3 | 4 |
| Masts | 1 | 2 (main, fore) | 3 (main, fore, mizzen) |
| Cannons | 2 | 4, on deck | 8, on her gun deck |
| Decks below | hold | one deck, with the brig | gun deck, and the lower deck with the brig |
| Best point of sail | quick and nimble | fastest of all with the wind on her beam | fastest running downwind; slow to turn |
| Capstan bars | 2 | 3 | 4 (her anchor is the heaviest) |
| Sinking | | fills quickest | fills slowly until the water reaches her gun deck |

Every ship has 3¾ blocks of headroom below deck. Crew respawn at one of several spots aboard the bigger ships
(six on the Brigantine, eight on the Galleon), or on the bunk they last used.

**Rowboats** can't be crafted or found in chests: as in Sea of Thieves, they wait tied up along the world's
coasts, plain or with a cannon or a harpoon gun in the bow, and anyone can row one.

## Sailing

| | |
|---|---|
| **Wheel** | Use it to take the helm: you stand square behind it. A/D turn the wheel; it stays where you leave it, and she answers slowly, like a heavy ship. Sneak to let go. |
| **Sail** | Use a rope to take it. At the length cleat, W lets the sail down and S hauls it up; at an angle pulley, A/D swing it. Sneak to let go. Speed depends on the wind and how well the sail is trimmed. On the Brigantine and Galleon every mast has its own cleat and pulley each side, at its foot. |
| **Anchor** | Use the capstan to drop the anchor; hold use to heave it up, with a crewmate on every bar to heave faster. Drop it at speed with the wheel over to pivot hard. |
| **Rowboat** | Take the oars: W/S to row, A/D to turn. Use the bow hook beside a ship's ladder to dock; she's towed wherever the ship goes. |

**Her status** shows in the top left while you're aboard: her speed, a wind dial (her bow up, the arrow where the
wind blows), a row for each sail (how far it's let down, its angle, whether it's full, luffing or slack, and the
mast's health), the wheel, and water, holes, fire and the anchor when there's something to tell. It can be turned
off (client config `shipHud`).

## Fighting

- **Cannons:** load with ammunition, use to man, look to aim, left click to fire. The view moves out behind the
  gun and swings with it as you aim (this can be turned off). Use an empty cannon again with nothing in hand to
  climb into the barrel and fire yourself.
- **Ammunition:** cannonballs (hole the hull), chain shot (tears sails, breaks masts), firebombs (set her
  alight) and eight cursed cannonballs (anchor, rigging, ballast, grog, limp, venom, weary, jig).
- **Harpoon guns:** fire, hold W to reel. They catch floating loot, or bite into land or another ship and pull
  you to it. Like the cannons, the view follows the gun.

## Keeping her afloat

- **Holes** let the sea in; patch them with planks. Water rises in the hold, she sits lower and slows, and
  she goes down by the bow. Bail with a **bucket** (throw the water over the side).
- **Fire** spreads and burns through the deck: put it out with thrown water.
- **Running aground** at speed holes the hull where she strikes, up to three holes going flat out.
- **A broken mast** drops the sail; mend it with planks.
- A ship's blocks can't be mined or broken by anyone, even in creative (this can be turned off).
- If she sinks, her crew gets a new ship nearby and respawns aboard (this can be turned off in the config).
- **Overboard?** Swim on: once you're well away from your ship (32 blocks), a **mermaid** comes up beside you, as
  in Sea of Thieves. Swim to her, or use her, and she takes you back aboard wherever your ship has sailed (to the
  brig, if that's where you belong). Configs `mermaids`, `mermaidDistance`, `mermaidDelaySeconds`.
- **Run aground?** Any of her crew can have her towed off to open water nearby, level and at rest, with her crew
  aboard: the **Free her** button on the quest table's Ship tab, or `/crew free`. Only when she's really aground,
  and once every five minutes (configs `allowFreeingShips`, `freeShipCooldownMinutes`).

## The brig

The Brigantine and the Galleon have a **brig** below decks. When most of the crew (more than half of those online)
votes for it, a crewmate is locked in: `/crew brig <player>`, or the brig button on the quest table's Crew tab.
Voting again takes your vote back. A prisoner can't work the ship, but can still bail with a bucket; explosions
don't reach into the brig; they respawn there, and are put straight back if they get out. After five minutes
(config `brigMinimumMinutes`) the crew can vote them out the same way (`/crew release <player>`; the prisoner's
own vote counts). Leaving or being put off the crew ends it. Turn it off with `allowBrig`.
- A ship whose whole crew has left her sinks on her own after 30 minutes, so forgotten ships don't pile up.
- When a ship sinks, what was in her stores floats up in **shipwreck barrels**: open one, or catch it with a
  harpoon.

## Aboard

- **Galley stove:** put raw food (vanilla's or any mod's) in the frying pan, and take it off when it's cooked: you
  can see it brown, and the smoke turns white. Leave it and it chars.
- **Grog barrel:** fill a glass bottle or a bucket. Each drink makes you drunker; too many and the world spins
  (and you'll be sick: catch it in an empty bucket).
- **Water barrel:** water like a cauldron (bucket, bottle, or a bucket to throw on fires). It refills itself.
- **Storage:** supply barrels (stocked with cannonballs, planks and food when she's launched), sea chests (a
  double chest's worth) and casks. All ordinary containers: hoppers and storage mods work with them.
- **Map table:** a blank sea chart, as in Sea of Thieves, with her arrow moving on it as she sails. Drag to move
  it, scroll to zoom, click to put an X down, right click to rub one out, C to find the ship again. The chart
  stays on the table as you left it, for the whole crew to read. If a map mod is installed it opens that mod's
  world map instead (sneak-use for the chart).
- **Quest table:** the ship's ledger: voyages on the table (any treasure or explorer map), voting, the crew,
  and the ship's state and name. The captain runs the ship from here (see Crews).
- **Flag box:** up in the crow's nest: hoist any banner at the masthead.
- **Bells, lanterns, liveries:** ring the ship's bell (a vanilla bell, hung on the mast) to call the crew; put lanterns out for a dark ship at night; use
  dye on the wheel to paint the hull, or on a sail rope to dye the sails.

## Learning the ropes

The first few times you use something aboard (the wheel, a cannon, the capstan...), a hint above your hotbar
says how it works, with your own keys. The **Seafarers advancements** (20 of them, from Set Sail to Davy Jones'
Locker) are a list of things to try.

## Crews

Whoever launches a ship is her **captain**. Everything a captain does is on the quest table's Crew and Ship
tabs (or by command):

| | |
|---|---|
| Invite a player nearby | Crew tab: click them, then Invite (`/crew invite <player>`; they click Accept in chat or `/crew accept`) |
| Remove someone from the crew | Crew tab: click them, then Remove (`/crew kick <player>`) |
| Hand the ship to someone | Crew tab: click them, then Make captain (`/crew captain <player>`) |
| Lock or unlock her | Ship tab (`/crew lock`, `/crew unlock`). A locked ship's wheel, sails, cannons, storage and bunks only work for her crew, and only they can place blocks on her. Anyone can still walk aboard and open her doors. New ships start locked. |
| Swap her for another ship | Ship tab, Swap ship (`/crew swap`). She and everything aboard her are gone; you get a new deed, and your crew joins you on the new ship. |

Everyone: `/crew leave` (a leaving captain hands the ship to the next crew member), `/crew list`, `/crew brig`
and `/crew release` (brig votes), and
`/crew choose` to choose a ship of your own later. Each player can captain one ship at a time, and there's a
wait between free ships (30 minutes by default) so nobody can farm them. The Sloop takes a crew of two, the
Brigantine three and the Galleon four (each set in the config). Ships
with a crew stay loaded so the crew can always respawn aboard.

**For server operators:** `/crew admin ships` lists every ship (captain, crew, lock, water, where she is), and
`/crew admin tp|remove|lock|unlock <ship>` and `/crew admin captain <ship> <player>` act on one, named by the
short id in that list. `/crew reset <player>` lets a player choose a ship again.

**Permissions** (for LuckPerms or any permissions mod; without one, the defaults in brackets apply):
`seafarers.command.crew` (everyone), `seafarers.admin` (operators), `seafarers.bypass.locks` (operators) and
`seafarers.bypass.limits` (operators: launch past the ship limit and in others' claims).

## Configuration

Every setting can be changed in game (**Mods**, **Seafarers**, **Config**) or in the files, where each one
says what it does, what it starts at and what it can be set to. Delete a file to put everything back.

`config/seafarers-common.toml`, the rules (on a server, the server's copy counts):

- **Starting ship:** show the ship choice screen; offer "Join a crew"; give the Ship's Compass and the
  Captain's Handbook.
- **Deeds:** protect deeds; deeds only work for their owner; allow replacing lost deeds.
- **Captains:** respect land claims; parties and teams share ships; one ship per player; allow locking; new
  ships start locked; operators ignore locks; only the
  captain invites; allow swaps; the wait between free ships; the most ships the server can have.
- **Crew:** crew size of each ship; the brig, and the shortest time in it; respawn aboard; keep crewed ships loaded; crew voice groups; ship waypoints.
- **Rowboats:** rowboats along the coasts, and how often.
- **Sinking:** replace sunk ships, and how far away; how long she takes to go down; how long before an
  abandoned ship sinks; shipwreck barrels, and how long they float.
- **Sailing:** speed; turning; how often the wind changes.
- **Damage:** cannonballs hole hulls; running aground holes hulls, and how fast that takes; ship blocks can't be
  broken; flooding speed; water per bucket; mast strength; ship fires; fire spread;
  locked ships resist explosions (creepers, TNT).
- **Weapons:** cannonball damage to players; crew friendly fire (off); cannon range; players fired from
  cannons; cursed cannonballs; harpoon rope length.
- **Supplies:** new ships come stocked; water barrel refill; stove cooking time; how long grog lasts.
- **Messages:** how long messages above the hotbar stay up; how many times each hint shows.

`config/seafarers-client.toml`, your own screen: what the map table opens (`AUTO`, `BUILT_IN` or `MAP_MOD`)
and which map mods it looks for (Xaero's World Map, JourneyMap, FTB Chunks, VoxelMap and Antique Atlas are
listed; add any other by the name of its "open map" key binding); drunk view sway; ship sounds; the cannon aim
guide; the gun camera; smoother ships (raises Sable's client smoothing so a sailing ship doesn't judder); hints.

## Compatibility

No mixins of its own: everything goes through NeoForge events and Sable's API. Vanilla items, blocks and
mechanics are left alone (the ship's bucket, fire and food are the mod's own). Storage is plain containers;
the stove takes any mod's food; banners, maps and dyes from other mods work. **Sable is required.**

Works with these, all optional (each part only switches on when that mod is installed):

| Mod | What it adds |
|---|---|
| Patchouli | The Captain's Handbook, the mod's guide book, given to new players |
| Jade | Look at any part of a ship to see her name, captain, crew, water, holes and lock |
| JEI or EMI | Info pages for the mod's items: how you get the deed, what each cannonball does, and so on |
| JourneyMap | A waypoint that marks your crew's ship and follows her as she sails |
| Map mods (Xaero's, JourneyMap, FTB Chunks, VoxelMap, Antique Atlas) | The map table opens your map mod's world map |
| Open Parties and Claims | No launching ships in others' claims; a locked ship works for her captain's party |
| FTB Chunks and FTB Teams | No launching ships in others' claims; a locked ship works for her captain's team |
| Simple Voice Chat | Each crew gets its own voice group, and crew members are put in it |
| LuckPerms (or any permissions mod) | Control who gets the commands and who can bypass locks and limits |

## Credits

Made by Astryxx. Block textures were made with fal (Z-Image Turbo) and hand-graded; item icons and the mermaid's
design with fal (Nano Banana 2), cut down to 16 pixels by hand; the small textures (chart paper, grog, the drunk
icon) with Stable Diffusion 1.5 run locally in ComfyUI. Sounds were made with ElevenLabs' sound effects model via
fal. The sources and their prompts are kept in art/ (not shipped).

For building and testing the mod, see [docs/DEVELOPING.md](docs/DEVELOPING.md).
