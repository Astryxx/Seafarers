# Changelog

## 1.0.0: first release

The first public release: all three ships, the Sloop, the Brigantine and the Galleon, fully fitted out.
Minecraft 1.21.1, NeoForge 21.1, Sable required, Patchouli optional.

### Ships
- The **Sloop**: a real ship built of blocks that you walk while she sails, with physics by Sable. One mast, two
  cannons, a crew of two.
- The **Brigantine**: two masts, four cannons, a crew of three, one deck below with a brig. Fastest of all with the
  wind on her beam; sinks the quickest.
- The **Galleon**: three masts, eight cannons on her gun deck, a crew of four, a lower deck with the brig, and the
  captain's quarters under her aft deck. Fastest running downwind, slow to turn, with a four-bar capstan and a
  heavy anchor; floods slowly until the water reaches her gun deck, then fast.
- Roomy below decks: 3¾ blocks of headroom on every ship.
- **The brig**: most of a crew votes a crewmate in (`/crew brig`, or the quest table's Crew tab) and out again
  after five minutes (`/crew release`). Prisoners can't work the ship, are kept in, respawn there, and are safe
  from explosions.
- Each ship has her own handling: point-of-sail speeds, turning, wheel turns, capstan bars, anchor weight, mast
  strength, flooding, crew size (configurable per ship), barrel stocks as in Sea of Thieves, and several
  respawn spots on the bigger ships.
- **Choose your ship** the first time you join a world, or join a friend's crew instead.
- **Deeds** (Sloop, Brigantine, Galleon): launch her on open water. It can't be crafted or found; your own deed can't burn, blow up,
  despawn or fall out of the world, stays with you when you die, and can be replaced if lost (`/crew reclaim`).
- **Ship's Compass**: points to your crew's ship and shows how far she is.
- **Rowboats** wait along the world's coasts (they can't be crafted): plain, with a cannon, or with a harpoon gun;
  anyone can row one, and you can tie one to your ship with the rowboat hook.

### Sailing
- A smooth, continuous wheel that turns her slowly and heavily, holding a straight course; sails you work at
  their ropes (W/S to let down or haul up, A/D to swing); a capstan you heave on that springs back if you let go.
- Stations hold you in place: behind the wheel, at the ropes, and behind the guns, where the view follows the
  gun as you aim.
- A blank sea chart on the map table, as in Sea of Thieves: fullscreen, drag to move it, scroll to zoom, X marks
  and her arrow on top; the table (three blocks by two) shows it as you left it.
- Ships sail smoothly on screen: Sable's client smoothing is raised a little (config `smoothShips`).
- Wind that shifts, sail trim and the anchor turn.
- **Her status**, top left while you're aboard: speed, a wind dial, each sail (height, angle, how it's drawing,
  the mast's health), the wheel, water and holes, fire and the anchor (client config `shipHud`).

### Fighting and damage
- Cannons (and climbing into one to be fired across the sea), cannonballs, chain shot, firebombs and eight
  cursed cannonballs; harpoon guns that reel in loot or pull you to land or another ship.
- Running aground at speed holes the hull where she strikes; a ship that's run aground can be towed off to open
  water nearby by her crew (`/crew free`, or the quest table), once every five minutes.
- **Mermaids**: a crewmate left in the water far from their ship is met by a mermaid, who takes them back aboard.
- Chain shot catches any sail it flies through, edge-on included (it's checked along its whole flight).
- A ship can't be launched into another ship.
- A ship's blocks can't be mined or broken, even in creative (config `unbreakableShips`).
- Holes below the waterline flood her (holes above it don't, unless she settles low); patch with planks, bail
  with buckets, put out fires, mend broken masts. A sunk ship's crew gets a new one nearby.

### Aboard
- A walkable bowsprit; the flag box up in the crow's nest; a vanilla bell hung on the mast.
- Bunk (respawn aboard wherever she is), galley stove with a frying pan (watch the food brown, then char), grog barrel, water barrel, supply barrels, sea chest and
  casks, a live map table you can read from across the deck, the quest table, flag box, bells, lanterns, and
  hull and sail colours.
- Sounds for everything: made for the mod with ElevenLabs' sound effects model.

### Crews and captains
- Crews of two on the Sloop, three on the Brigantine and four on the Galleon; the player who launches her is
  her **captain**.
- On the quest table: invite nearby players, remove crew, hand the ship over, lock her (only her crew can use
  her), and swap her for a new ship (her crew comes along).
- One ship per player, a wait between free ships, an optional server-wide ship limit, and abandoned ships sink
  on their own. Locked ships resist creepers and TNT; crew friendly fire is off by default.
- `/crew` commands for all of it, and `/crew admin` for server operators (list, find, lock, hand over or remove
  any ship). Permission nodes for permissions mods.

### Learning and goals
- Hints the first few times you use each thing aboard, with your own keys.
- 23 advancements, from Set Sail to Davy Jones' Locker.
- Cannonball deaths have their own message ("blasted overboard by Alex's cannonball").
- Rename a deed in an anvil to name your ship.
- A sunk ship's stores float up in shipwreck barrels that anyone can open or harpoon.

### Settings
- About 75 settings in `config/seafarers-common.toml` and `seafarers-client.toml`, each explained in plain
  English, also editable in game (Mods, Seafarers, Config).

### Guide
- The **Captain's Handbook**: a Patchouli book given to new players when Patchouli is installed (or craft it
  from a book and any boat).

### Compatibility
- No mixins of its own; vanilla blocks, items and mechanics are left alone.
- Optional support for Patchouli (the handbook), Jade (ship tooltips), JEI and EMI (info pages), JourneyMap (a
  waypoint on your ship), Open Parties and Claims and FTB Chunks/Teams (claims and shared ships), Simple Voice
  Chat (crew voice groups) and permissions mods.
