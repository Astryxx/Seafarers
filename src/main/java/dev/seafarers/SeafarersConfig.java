package dev.seafarers;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Settings, in two files:
 * <ul>
 *   <li>config/seafarers-common.toml: the rules of the game (ships, crews, combat). On a server, only the
 *   server's copy counts.</li>
 *   <li>config/seafarers-client.toml: what you see and hear on your own screen.</li>
 * </ul>
 * Both can also be changed in game: Mods, Seafarers, Config. Every setting says what it does, what it starts at
 * and what it can be set to; deleting a file puts every setting in it back to the start.
 */
public final class SeafarersConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    // ---- starting ship ----
    public static ModConfigSpec.BooleanValue SHOW_SHIP_CHOICE;
    public static ModConfigSpec.BooleanValue OFFER_JOIN_CREW;
    public static ModConfigSpec.BooleanValue GIVE_COMPASS;
    public static ModConfigSpec.BooleanValue GIVE_HANDBOOK;
    // ---- deeds ----
    public static ModConfigSpec.BooleanValue PROTECT_DEEDS;
    public static ModConfigSpec.BooleanValue DEEDS_ONLY_FOR_OWNER;
    public static ModConfigSpec.BooleanValue ALLOW_DEED_RECLAIM;
    // ---- captains ----
    public static ModConfigSpec.BooleanValue ONE_SHIP_PER_PLAYER;
    public static ModConfigSpec.BooleanValue ALLOW_LOCKING;
    public static ModConfigSpec.BooleanValue NEW_SHIPS_LOCKED;
    public static ModConfigSpec.BooleanValue OPS_IGNORE_LOCKS;
    public static ModConfigSpec.BooleanValue ONLY_CAPTAIN_INVITES;
    public static ModConfigSpec.BooleanValue ALLOW_SWAP;
    public static ModConfigSpec.IntValue NEW_SHIP_COOLDOWN_MINUTES;
    public static ModConfigSpec.IntValue MAX_SHIPS;
    public static ModConfigSpec.BooleanValue RESPECT_CLAIMS;
    public static ModConfigSpec.BooleanValue TEAMS_SHARE_SHIPS;
    // ---- crew ----
    public static ModConfigSpec.IntValue SLOOP_CREW;
    public static ModConfigSpec.IntValue BRIGANTINE_CREW;
    public static ModConfigSpec.IntValue GALLEON_CREW;
    public static ModConfigSpec.BooleanValue ALLOW_BRIG;
    public static ModConfigSpec.IntValue BRIG_MINUTES;
    public static ModConfigSpec.BooleanValue MERMAIDS;
    public static ModConfigSpec.IntValue MERMAID_DISTANCE;
    public static ModConfigSpec.IntValue MERMAID_SECONDS;
    public static ModConfigSpec.BooleanValue ALLOW_FREEING;
    public static ModConfigSpec.IntValue FREE_COOLDOWN_MINUTES;
    public static ModConfigSpec.BooleanValue RESPAWN_ABOARD;
    public static ModConfigSpec.BooleanValue KEEP_CREWED_SHIPS_LOADED;
    public static ModConfigSpec.BooleanValue CREW_VOICE_GROUPS;
    public static ModConfigSpec.BooleanValue SHIP_WAYPOINTS;
    // ---- rowboats ----
    public static ModConfigSpec.BooleanValue NATURAL_ROWBOATS;
    public static ModConfigSpec.DoubleValue ROWBOAT_CHANCE;
    // ---- sinking ----
    public static ModConfigSpec.BooleanValue REPLACE_SUNK_SHIPS;
    public static ModConfigSpec.IntValue REPLACEMENT_MIN_BLOCKS;
    public static ModConfigSpec.IntValue REPLACEMENT_MAX_BLOCKS;
    public static ModConfigSpec.IntValue SECONDS_TO_SINK;
    public static ModConfigSpec.IntValue ABANDONED_SHIP_MINUTES;
    public static ModConfigSpec.BooleanValue SHIPWRECK_LOOT;
    public static ModConfigSpec.IntValue SHIPWRECK_LOOT_MINUTES;
    // ---- sailing ----
    public static ModConfigSpec.DoubleValue SPEED_MULTIPLIER;
    public static ModConfigSpec.DoubleValue TURNING_MULTIPLIER;
    public static ModConfigSpec.IntValue WIND_CHANGE_MINUTES;
    // ---- damage ----
    public static ModConfigSpec.BooleanValue CANNONS_HOLE_SHIPS;
    public static ModConfigSpec.DoubleValue FLOODING_MULTIPLIER;
    public static ModConfigSpec.DoubleValue BAILING_MULTIPLIER;
    public static ModConfigSpec.IntValue MAST_STRENGTH;
    public static ModConfigSpec.BooleanValue SHIP_FIRES;
    public static ModConfigSpec.DoubleValue FIRE_SPREAD_MULTIPLIER;
    public static ModConfigSpec.BooleanValue LOCKED_RESIST_EXPLOSIONS;
    public static ModConfigSpec.BooleanValue CRASH_DAMAGE;
    public static ModConfigSpec.DoubleValue CRASH_SPEED;
    public static ModConfigSpec.BooleanValue UNBREAKABLE_SHIPS;
    // ---- weapons ----
    public static ModConfigSpec.DoubleValue CANNON_PLAYER_DAMAGE_MULTIPLIER;
    public static ModConfigSpec.DoubleValue CANNON_RANGE_MULTIPLIER;
    public static ModConfigSpec.BooleanValue CANNONS_LAUNCH_PLAYERS;
    public static ModConfigSpec.BooleanValue CURSED_CANNONBALLS;
    public static ModConfigSpec.BooleanValue CREW_FRIENDLY_FIRE;
    public static ModConfigSpec.IntValue HARPOON_ROPE_BLOCKS;
    // ---- supplies ----
    public static ModConfigSpec.BooleanValue NEW_SHIPS_STOCKED;
    public static ModConfigSpec.IntValue WATER_BARREL_REFILL_SECONDS;
    public static ModConfigSpec.DoubleValue STOVE_COOK_SCALE;
    public static ModConfigSpec.IntValue GROG_SECONDS;
    // ---- messages ----
    public static ModConfigSpec.DoubleValue MESSAGE_SECONDS;
    public static ModConfigSpec.IntValue HINT_TIMES;
    // ---- debug ----
    public static ModConfigSpec.BooleanValue DEBUG_COMMANDS;

    // ---- client ----
    public enum MapMode { AUTO, BUILT_IN, MAP_MOD }

    public static ModConfigSpec.EnumValue<MapMode> MAP_TABLE_MODE;
    public static ModConfigSpec.ConfigValue<List<? extends String>> MAP_MOD_KEYS;
    public static ModConfigSpec.BooleanValue DRUNK_CAMERA;
    public static ModConfigSpec.BooleanValue SHIP_AMBIENCE;
    public static ModConfigSpec.BooleanValue CANNON_HUD;
    public static ModConfigSpec.BooleanValue SHOW_HINTS;
    public static ModConfigSpec.BooleanValue SMOOTH_SHIPS;
    public static ModConfigSpec.BooleanValue GUN_CAMERA;
    public static ModConfigSpec.BooleanValue SHIP_HUD;

    static {
        ModConfigSpec.Builder s = new ModConfigSpec.Builder();

        s.comment("The ship each player starts with.").push("starting_ship");
        SHOW_SHIP_CHOICE = s.comment("Show the 'Choose your ship' screen the first time a player joins the world.",
                        "They pick a ship and get a deed to launch her. Turn this off to hand out deeds yourself (Creative menu or /give).")
                .define("showShipChoiceScreen", true);
        OFFER_JOIN_CREW = s.comment("Put a 'Join a crew' button on that screen, for players who'd rather sail aboard a friend's ship.",
                        "They can still choose a ship of their own later with /crew choose.")
                .define("offerJoinCrew", true);
        GIVE_COMPASS = s.comment("Give a Ship's Compass (it points to your ship) along with a player's first deed.")
                .define("giveShipsCompass", true);
        GIVE_HANDBOOK = s.comment("Give the Captain's Handbook, the mod's guide book, to new players. Needs the Patchouli mod; without it this does nothing.")
                .define("giveHandbook", true);
        s.pop();

        s.comment("Ship deeds: the item that launches a ship.").push("deeds");
        PROTECT_DEEDS = s.comment("A player's starting deed can't burn, blow up, despawn or fall out of the world, and stays with them when they die.",
                        "(Deeds from the Creative menu are only fireproof.)")
                .define("protectDeeds", true);
        DEEDS_ONLY_FOR_OWNER = s.comment("Only the player a deed was given to can launch a ship with it, so a stolen deed is useless.")
                .define("deedsOnlyWorkForOwner", true);
        ALLOW_DEED_RECLAIM = s.comment("A player who loses their unused deed anyway can get a new one with /crew reclaim (the lost one stops working).")
                .define("allowReclaimingLostDeeds", true);
        s.pop();

        s.comment("Captains: the player who launched a ship is her captain.").push("captains");
        ONE_SHIP_PER_PLAYER = s.comment("A player can only captain one ship at a time. (Players in Creative mode can always launch more.)")
                .define("oneShipPerPlayer", true);
        ALLOW_LOCKING = s.comment("Let captains lock their ship. A locked ship's wheel, sails, cannons, storage and bunks only work for her crew,",
                        "and only her crew can break or place blocks on her. Anyone can still walk aboard.",
                        "Turn this off for pirate servers where any ship can be boarded and stolen.")
                .define("allowLocking", true);
        NEW_SHIPS_LOCKED = s.comment("New ships start locked. Captains can unlock them at the ship's quest table or with /crew unlock.")
                .define("newShipsStartLocked", true);
        OPS_IGNORE_LOCKS = s.comment("Server operators can use any ship, locked or not.")
                .define("operatorsIgnoreLocks", true);
        ONLY_CAPTAIN_INVITES = s.comment("Only the captain can invite players to the crew. Turn off to let anyone in the crew invite.")
                .define("onlyCaptainCanInvite", true);
        ALLOW_SWAP = s.comment("Captains can swap their ship for another kind (the old one and everything aboard her is gone; her crew moves to the new one).")
                .define("allowShipSwap", true);
        NEW_SHIP_COOLDOWN_MINUTES = s.comment("Minutes a player must wait between free ships: after swapping, or before choosing again once they've",
                        "given up their ship. Stops players farming new ships for their stores. 0 = no wait. (The first ship is never held up.)")
                .defineInRange("newShipCooldownMinutes", 30, 0, 10080);
        MAX_SHIPS = s.comment("The most player ships the whole server can have at once (each captain's ship counts). Once it's reached,",
                        "deeds won't launch until a ship is sunk, swapped or abandoned. 0 = no limit. Operators can always launch.")
                .defineInRange("maxShipsOnServer", 0, 0, 10000);
        RESPECT_CLAIMS = s.comment("With Open Parties and Claims or FTB Chunks installed: nobody can launch a ship inside land claimed by someone",
                        "outside their party or team. (Operators can.)")
                .define("respectLandClaims", true);
        TEAMS_SHARE_SHIPS = s.comment("With Open Parties and Claims or FTB Teams installed: a locked ship also works for her captain's party or",
                        "team, as if they were crew.")
                .define("partiesShareShips", true);
        s.pop();

        s.comment("Crews.").push("crew");
        SLOOP_CREW = s.comment("How many players can crew a sloop.")
                .defineInRange("sloopCrewSize", 2, 1, 16);
        BRIGANTINE_CREW = s.comment("How many players can crew a brigantine.")
                .defineInRange("brigantineCrewSize", 3, 1, 16);
        GALLEON_CREW = s.comment("How many players can crew a galleon.")
                .defineInRange("galleonCrewSize", 4, 1, 16);
        ALLOW_BRIG = s.comment("The Brigantine and Galleon have a brig: when most of a crew votes for it (/crew brig <player>, or the voyage",
                        "table), that crewmate is locked in it, and the crew votes again to let them out.")
                .define("allowBrig", true);
        BRIG_MINUTES = s.comment("The shortest time a crewmate stays in the brig before the crew can vote to let them out, in minutes.")
                .defineInRange("brigMinimumMinutes", 5, 0, 120);
        MERMAIDS = s.comment("A crewmate left in the water far from their ship is met by a mermaid, who takes them back aboard (as in Sea of",
                        "Thieves): swim to her, or use her.")
                .define("mermaids", true);
        MERMAID_DISTANCE = s.comment("How far from their ship (blocks) a crewmate in the water must be before a mermaid comes.")
                .defineInRange("mermaidDistance", 32, 8, 512);
        MERMAID_SECONDS = s.comment("How long (seconds) they must be in the water that far from their ship before she comes.")
                .defineInRange("mermaidDelaySeconds", 5, 0, 120);
        ALLOW_FREEING = s.comment("A ship that has run aground can be towed off to open water nearby by any of her crew (/crew free, or the",
                        "voyage table).")
                .define("allowFreeingShips", true);
        FREE_COOLDOWN_MINUTES = s.comment("How long (minutes) before the same ship can be freed again.")
                .defineInRange("freeShipCooldownMinutes", 5, 0, 120);
        RESPAWN_ABOARD = s.comment("Crew respawn aboard their ship when they die (on the bunk they last used), unless they've since slept in a bed ashore.")
                .define("respawnAboard", true);
        KEEP_CREWED_SHIPS_LOADED = s.comment("Keep ships that have a crew loaded even when nobody is near, so the crew can always respawn aboard.",
                        "Turn off to save server memory; crew then respawn at their world spawn when their ship is far away.")
                .define("keepCrewedShipsLoaded", true);
        CREW_VOICE_GROUPS = s.comment("With Simple Voice Chat installed: each crew gets its own voice group, and crew members are put in it when they",
                        "join (they can still leave it or pick another group). Players near the ship still hear them as usual.")
                .define("crewVoiceGroups", true);
        SHIP_WAYPOINTS = s.comment("With JourneyMap installed: a waypoint on the map marks where your crew's ship is, and follows her.")
                .define("shipWaypoints", true);
        s.pop();

        s.comment("Rowboats.").push("rowboats");
        NATURAL_ROWBOATS = s.comment("Rowboats wait, tied up, along the world's coasts (as in Sea of Thieves). They can't be crafted or found in chests;",
                        "in Creative they're in the Seafarers tab. Only new parts of the world get them.")
                .define("naturalRowboats", true);
        ROWBOAT_CHANCE = s.comment("The chance that a newly generated stretch of coast (one chunk) gets a rowboat. 0.05 is about one every 20 coastal chunks.")
                .defineInRange("rowboatChance", 0.05, 0.0, 1.0);
        s.pop();

        s.comment("Sinking.").push("sinking");
        REPLACE_SUNK_SHIPS = s.comment("When a crew's ship sinks, a new ship of the same kind is waiting for them nearby (as in Sea of Thieves).",
                        "Turn off and a sunk ship is gone for good: the captain can choose again with /crew choose.")
                .define("replaceSunkShips", true);
        REPLACEMENT_MIN_BLOCKS = s.comment("The closest a replacement ship appears to where the old one sank, in blocks.")
                .defineInRange("replacementMinDistance", 150, 16, 5000);
        REPLACEMENT_MAX_BLOCKS = s.comment("The farthest a replacement ship appears, in blocks. (Kept above the closest.)")
                .defineInRange("replacementMaxDistance", 300, 16, 5000);
        SECONDS_TO_SINK = s.comment("Seconds a ship takes to go down once she's past saving.")
                .defineInRange("secondsToSink", 20, 3, 300);
        ABANDONED_SHIP_MINUTES = s.comment("A ship whose whole crew has left her (with /crew leave, or by being removed) sinks after this many minutes,",
                        "so forgotten ships don't pile up. Crew who are only offline still count as crew. 0 = abandoned ships stay forever.")
                .defineInRange("abandonedShipMinutes", 30, 0, 10080);
        SHIPWRECK_LOOT = s.comment("When a ship sinks, what was in her barrels, chests and casks floats up in shipwreck barrels that anyone",
                        "can open (or catch with a harpoon). Turn off and it goes down with her.")
                .define("shipwreckLoot", true);
        SHIPWRECK_LOOT_MINUTES = s.comment("Minutes a shipwreck barrel floats before it sinks for good.")
                .defineInRange("shipwreckLootMinutes", 20, 1, 1440);
        s.pop();

        s.comment("Sailing. 1.0 is normal; 2.0 is twice as much; 0.5 is half.").push("sailing");
        SPEED_MULTIPLIER = s.comment("How fast ships sail.")
                .defineInRange("speedMultiplier", 1.0, 0.1, 5.0);
        TURNING_MULTIPLIER = s.comment("How fast ships turn.")
                .defineInRange("turningMultiplier", 1.0, 0.1, 5.0);
        WIND_CHANGE_MINUTES = s.comment("Roughly how often the wind changes direction, in minutes.")
                .defineInRange("windChangeMinutes", 6, 1, 120);
        s.pop();

        s.comment("Damage to ships. 1.0 is normal; 2.0 is twice as much; 0.5 is half.").push("damage");
        CANNONS_HOLE_SHIPS = s.comment("Cannonballs knock holes in hulls. Turn off for peaceful sailing: shots still hurt players and masts.")
                .define("cannonsHoleShips", true);
        FLOODING_MULTIPLIER = s.comment("How fast holes below the waterline let the sea in.")
                .defineInRange("floodingMultiplier", 1.0, 0.0, 10.0);
        BAILING_MULTIPLIER = s.comment("How much water one bucket bails out.")
                .defineInRange("bailingMultiplier", 1.0, 0.1, 10.0);
        MAST_STRENGTH = s.comment("Hits a mast takes before it breaks (a cannonball is 1 hit, chain shot 3).")
                .defineInRange("mastStrength", 6, 1, 100);
        SHIP_FIRES = s.comment("Firebombs set ships alight. Turn off and they only burn the players they hit.")
                .define("shipFires", true);
        FIRE_SPREAD_MULTIPLIER = s.comment("How fast fire spreads across a ship and burns through her planks.")
                .defineInRange("fireSpreadMultiplier", 1.0, 0.0, 10.0);
        LOCKED_RESIST_EXPLOSIONS = s.comment("Creepers, TNT and other explosions don't break blocks on a locked ship (cannonballs still do their damage).",
                        "Stops griefing on servers; turn off if you want explosions to hurt every ship.")
                .define("lockedShipsResistExplosions", true);
        CRASH_DAMAGE = s.comment("Running aground or ramming land holes the hull where she hits, like a cannonball would (faster, more holes).")
                .define("crashDamage", true);
        CRASH_SPEED = s.comment("The speed (blocks per second) she must be doing when she hits for it to hole her. Her top speed is about 8.5.")
                .defineInRange("crashSpeed", 3.0, 0.5, 30.0);
        UNBREAKABLE_SHIPS = s.comment("Nobody can mine or break a ship's blocks, not even in Creative: she's one ship, not a pile of blocks.",
                        "Cannonballs, fire and repairs still work. Turn off to let players take ships apart.")
                .define("unbreakableShips", true);
        s.pop();

        s.comment("Cannons and harpoons.").push("weapons");
        CANNON_PLAYER_DAMAGE_MULTIPLIER = s.comment("How much a cannonball hurts the player it hits. 1.0 is normal; 0 = no harm.")
                .defineInRange("cannonPlayerDamageMultiplier", 1.0, 0.0, 10.0);
        CANNON_RANGE_MULTIPLIER = s.comment("How fast (and so how far) cannonballs fly. 1.0 is normal.")
                .defineInRange("cannonRangeMultiplier", 1.0, 0.3, 3.0);
        CANNONS_LAUNCH_PLAYERS = s.comment("A player can climb into a cannon and be fired out of it.")
                .define("cannonsLaunchPlayers", true);
        CURSED_CANNONBALLS = s.comment("Cursed cannonballs put their curse on the ships and players they hit. Turn off and they're plain cannonballs.")
                .define("cursedCannonballs", true);
        CREW_FRIENDLY_FIRE = s.comment("Your own crew's cannonballs can hurt you. Off by default, so a stray shot doesn't kill a crewmate;",
                        "turn on for the full Sea of Thieves chaos.")
                .define("crewFriendlyFire", false);
        HARPOON_ROPE_BLOCKS = s.comment("How far a harpoon's rope reaches, in blocks.")
                .defineInRange("harpoonRopeLength", 40, 8, 128);
        s.pop();

        s.comment("Supplies aboard.").push("supplies");
        NEW_SHIPS_STOCKED = s.comment("New ships (and replacements for sunk ones) come with cannonballs, planks and food in their barrels.")
                .define("newShipsComeStocked", true);
        WATER_BARREL_REFILL_SECONDS = s.comment("Seconds for a water barrel to refill by one level (it holds three, like a cauldron).")
                .defineInRange("waterBarrelRefillSeconds", 90, 1, 3600);
        STOVE_COOK_SCALE = s.comment("How long the galley stove takes to cook something, as a multiple of a smoker's time. It burns after as long again.")
                .defineInRange("stoveCookScale", 3.0, 0.1, 20.0);
        GROG_SECONDS = s.comment("How long one drink of grog lasts, in seconds.")
                .defineInRange("grogSeconds", 60, 5, 1200);
        s.pop();

        s.comment("Messages.").push("messages");
        MESSAGE_SECONDS = s.comment("How long a message above the hotbar (like 'The water barrel is empty') stays up, in seconds.")
                .defineInRange("messageSeconds", 3.5, 1.0, 15.0);
        HINT_TIMES = s.comment("How many times each hint shows (like 'A/D turn the wheel' when you take the helm). Each player can also",
                        "turn hints off in their own client settings. 0 = no hints.")
                .defineInRange("hintTimes", 3, 0, 1000);
        s.pop();

        s.comment("For testing the mod. Leave these alone.").push("debug");
        DEBUG_COMMANDS = s.comment("Register the /seafarers test and ship-tuning commands (operators only).")
                .define("debugCommands", false);
        s.pop();
        SERVER_SPEC = s.build();

        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        c.comment("The map table.").push("mapTable");
        MAP_TABLE_MODE = c.comment("What the map table opens. AUTO: a map mod's world map if one is installed (see mapModKeys), else the",
                        "built-in sea chart. BUILT_IN: always the sea chart. MAP_MOD: always the map mod's (falls back to the chart if none).",
                        "Sneak-use the table to open the built-in chart (to mark it) whatever this says.")
                .defineEnum("mode", MapMode.AUTO);
        MAP_MOD_KEYS = c.comment("Key bindings that open a map mod's full-screen map, tried in order; the first one a loaded mod has registered",
                        "is used. Add your map mod's key binding name here if it isn't listed (see options.txt, key_<name>).")
                .defineListAllowEmpty("mapModKeys", List.of(
                        "gui.xaero_open_map",                 // Xaero's World Map
                        "key.journeymap.map_toggle_alt",      // JourneyMap (full-screen map)
                        "key.journeymap.fullscreen",          // JourneyMap (older/newer naming)
                        "key.ftbchunks.map",                  // FTB Chunks
                        "key.voxelmap.togglefullscreen",      // VoxelMap
                        "key.antiqueatlas.openatlas"          // Antique Atlas
                ), () -> "", o -> o instanceof String);
        c.pop();
        c.comment("Effects on your screen.").push("effects");
        DRUNK_CAMERA = c.comment("Grog makes your view sway and your steps stagger. Turn off if swaying makes you feel sick.")
                .define("drunkSway", true);
        SHIP_AMBIENCE = c.comment("Play the creaking hull, flapping sails and rushing water while you're aboard a ship.")
                .define("shipSounds", true);
        CANNON_HUD = c.comment("Show the aiming guide while you're on a cannon.")
                .define("cannonAimGuide", true);
        GUN_CAMERA = c.comment("At a cannon or harpoon gun, the view moves out behind your shoulder and swings with the gun as you aim.",
                        "Turn off to stay in your own camera (first person, as you had it).")
                .define("gunCamera", true);
        SHIP_HUD = c.comment("Aboard a ship, show her state in the top left: speed, the wind, each sail, water and holes, the anchor and the wheel.")
                .define("shipHud", true);
        SMOOTH_SHIPS = c.comment("Draw ships a little further behind the server (3 ticks, Sable's sub_level_snapshot_interpolation_delay_ticks)",
                        "so they glide instead of juddering. Raises Sable's setting once if it's lower; turn off to leave Sable's setting alone.")
                .define("smoothShips", true);
        SHOW_HINTS = c.comment("Show hints when you use something aboard for the first few times (like 'A/D turn the wheel').")
                .define("showHints", true);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    private SeafarersConfig() {}
}
