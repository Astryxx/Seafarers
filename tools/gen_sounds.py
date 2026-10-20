"""Generates the mod's sound effects with fal and installs them as Minecraft sounds.

python tools/gen_sounds.py            # generate missing sounds, convert, write sounds.json
python tools/gen_sounds.py --convert  # only convert/install what is already in art/sfx

Sources (mp3) and fal_manifest.jsonl go to art/sfx/ (kept out of the jar); .ogg files go to
assets/seafarers/sounds/ and are registered in assets/seafarers/sounds.json. The Java side registers each
name in SOUNDS as seafarers:<name> (see ModSounds).
"""
import json
import os
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "art" / "sfx"
OUT = ROOT / "src" / "main" / "resources" / "assets" / "seafarers" / "sounds"
FFMPEG = pathlib.Path(os.environ.get("LOCALAPPDATA", "")) / "universal-modder" / "ffmpeg" / "bin" / "ffmpeg.exe"

# name: (prompt, seconds, subtitle, loop)
SOUNDS = {
    # ---- sails and rigging (rope sounds replay every few ticks while a rope is held: short and quiet) ----
    "rope_pull": ("Close-up foley: one hard tug on a thick tarred hemp rope running over a wooden belaying pin, rough rope rasp and a short wooden creak. Isolated game sound effect, close perspective, no music, no voices.", 0.6, "Rope hauled", False),
    "pulley_creak": ("Close-up foley: a heavy rope running through an old wooden ship's pulley block, the wooden sheave squeals and creaks once. Isolated game sound effect, close perspective, no music, no voices.", 0.7, "Pulley creaks", False),
    "sail_snap": ("A large heavy canvas ship's sail suddenly filling with wind and snapping taut: one deep cloth whump with a short rope twang. Isolated game sound effect, close perspective, no music, no voices.", 0.9, "Sail snaps", False),
    "sail_luff": ("Seamless loop: a big loose canvas sail flapping and luffing in a steady strong wind, rippling cloth slaps at an even pace, no gust peaks. No music, no voices.", 4.0, "Sail flaps", True),
    # ---- helm, capstan and anchor ----
    "wheel_turn": ("Close-up foley: an old wooden ship's steering wheel turned a short way, two soft wooden ratchet clicks and a faint timber creak. Quiet. Isolated game sound effect, close perspective, no music, no voices.", 0.5, "Wheel turns", False),
    "capstan_push": ("Close-up foley: sailors heave a heavy wooden ship's capstan round one step, a deep timber groan, a single iron pawl clack and a few chain links rattling. Isolated game sound effect, close perspective, no music, no voices.", 0.7, "Capstan turns", False),
    "capstan_unwind": ("A wooden ship's capstan spinning backwards on its own as the anchor runs back out: a fast run of iron pawl ratchet clicks over a running iron chain. Isolated game sound effect, close perspective, no music, no voices.", 1.0, "Capstan unwinds", False),
    "anchor_drop": ("A heavy iron ship's anchor let go: the chain cable roars out fast through a wooden hawse hole with loud continuous rattling, then the anchor plunges into the sea with a big splash. No music, no voices.", 3.0, "Anchor dropped", False),
    "anchor_bite": ("A ship's anchor biting into the seabed: the iron chain suddenly snubs tight with a heavy jolt and the wooden hull gives a deep groan. Isolated game sound effect, close perspective, no music, no voices.", 1.2, "Anchor bites", False),
    "anchor_up": ("A heavy iron anchor hauled home: it clunks solidly against a wooden ship's hull and the chain gives a final clank. Isolated game sound effect, close perspective, no music, no voices.", 1.0, "Anchor weighed", False),
    # ---- guns ----
    "cannon_fire": ("A large 18th-century black powder ship's cannon firing at close range: a sharp crack and a deep booming blast with a rolling rumble tail and a short wooden recoil thump. No music, no voices.", 2.5, "Cannon fires", False),
    "cannon_load": ("Close-up foley: a heavy iron cannonball dropped down into a cast-iron cannon barrel, a deep hollow metallic clunk as it seats. Isolated game sound effect, close perspective, no music, no voices.", 0.8, "Cannon loaded", False),
    "cannon_hit_hull": ("A cannonball smashing into a wooden ship's hull at close range: a violent crunch of splintering thick oak planks and falling wood debris. No explosion. Isolated game sound effect, close perspective, no music, no voices.", 1.2, "Hull smashed", False),
    "cannon_splash": ("A heavy iron cannonball plunging into the sea at high speed: a deep sharp water plunk and a big splash falling back. Isolated game sound effect, close perspective, no music, no voices.", 1.3, "Splash", False),
    "chain_shot_whirl": ("Chain shot flying past: two iron balls linked by a chain spinning fast through the air, a rhythmic whirring whoosh with chain rattle passing by. Isolated game sound effect, close perspective, no music, no voices.", 1.0, "Chain shot whirls", False),
    "harpoon_fire": ("A mounted harpoon gun firing: a heavy spring releasing with a metallic twang, the harpoon whooshing away and rope whipping out fast. Isolated game sound effect, close perspective, no music, no voices.", 1.0, "Harpoon fires", False),
    "harpoon_hit": ("A heavy iron harpoon striking deep into thick wood: a solid thunk with a short vibrating twang. Isolated game sound effect, close perspective, no music, no voices.", 0.7, "Harpoon strikes", False),
    "harpoon_reel": ("Close-up foley: a winch drum reeling in a wet rope, a short burst of iron ratchet clicks and rope creak. Isolated game sound effect, close perspective, no music, no voices.", 0.8, "Harpoon reels", False),
    # ---- damage ----
    "mast_crack": ("A thick wooden ship's mast breaking under strain: a loud groaning creak, then a sharp splintering crack and the wood tearing apart. No music, no voices.", 2.5, "Mast cracks", False),
    "hull_creak": ("Seamless loop: the inside of a wooden sailing ship at sea, timbers gently creaking and groaning as the hull works in a slow swell, soft and continuous. No water splashes, no music, no voices.", 8.0, "Hull creaks", True),
    "water_rush": ("Seamless loop: seawater gushing steadily in through a hole in a wooden ship's hull, a forceful splashing inflow into a flooding hold. No music, no voices.", 4.0, "Water rushes in", True),
    "plank_hammer": ("Close-up foley: patching a hole, three quick hammer blows nailing a wooden plank onto a wooden hull. Isolated game sound effect, close perspective, no music, no voices.", 1.0, "Plank hammered", False),
    "ship_sinking": ("A wooden sailing ship sinking: deep groaning and cracking timbers under pressure, water rushing in and big bubbles gurgling up. No music, no voices.", 5.0, "Ship sinks", False),
    "fire_burn": ("Seamless loop: a fire burning on a wooden ship's deck, crackling and popping flames with a low steady roar. No music, no voices.", 4.0, "Fire burns", True),
    "firebomb_break": ("A glass firebomb bottle smashing on wooden planks and bursting into flame: glass shatter followed by a fiery whoosh. Isolated game sound effect, close perspective, no music, no voices.", 1.2, "Firebomb bursts", False),
    # ---- crew ----
    "bucket_scoop": ("Close-up foley: a wooden bucket scooped into water and lifted out full, a short slosh. Isolated game sound effect, close perspective, no music, no voices.", 0.7, "Bucket fills", False),
    "bucket_throw": ("A full bucket of water thrown out: a quick whoosh of water through the air and a splash. Isolated game sound effect, close perspective, no music, no voices.", 1.0, "Water thrown", False),
    "grog_drink": ("A pirate gulps from a wooden tankard of ale: three hearty swallowing gulps and a satisfied aah. Comedic, close perspective, no music.", 1.5, "Gulps grog", False),
    "vomit": ("Cartoonish video game sound of a drunk pirate throwing up: a heave, a retching gag and a wet splatter on wooden planks. Comedic and not graphic, no music.", 1.5, "Throws up", False),
    "stove_sizzle": ("Close-up foley: raw meat sizzling on a hot iron stove plate, a steady fat crackle and hiss. Isolated game sound effect, close perspective, no music, no voices.", 2.0, "Food sizzles", False),
    "ship_bell": ("A single strike of a small brass ship's bell: one clear bright ring with a natural decay. Isolated game sound effect, close perspective, no music, no voices.", 2.5, "Bell rings", False),
}


def generate(name, prompt, seconds, loop):
    out = SRC / f"{name}.mp3"
    if out.exists():
        return
    print(f"generating {name}")
    # follow the prompt closely (these are precise game sounds), at full quality
    cmd = ["um", "fal", "sfx", prompt, "--out", str(SRC), "--name", name, "--seconds", str(max(0.5, seconds)),
           "--set", "prompt_influence:=0.6", "--set", "output_format=mp3_44100_192"]
    if loop:
        cmd.append("--loop")
    subprocess.run(cmd, check=True)


def convert(name, loop=False):
    src = SRC / f"{name}.mp3"
    if not src.exists():
        return False
    dst = OUT / f"{name}.ogg"
    subprocess.run([str(FFMPEG), "-y", "-loglevel", "error", "-i", str(src),
                    # level everything to the same loudness, then a limiter so no peak clips
                    "-af", ("loudnorm=I=-18:TP=-1.5,aresample=44100,alimiter=limit=0.7:level=false" if loop else
                            "silenceremove=start_periods=1:start_threshold=-50dB,loudnorm=I=-16:TP=-1.5,areverse,"
                            "silenceremove=start_periods=1:start_threshold=-55dB,areverse,aresample=44100,alimiter=limit=0.7:level=false"),
                    "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", str(dst)], check=True)
    return True


# Until a sound is generated, its event plays a fitting vanilla sound (sounds.json "type": "event").
PLACEHOLDER = {
    "rope_pull": "minecraft:entity.leash_knot.place", "pulley_creak": "minecraft:block.wooden_trapdoor.open",
    "sail_snap": "minecraft:item.armor.equip_leather", "sail_luff": "minecraft:item.elytra.flying",
    "wheel_turn": "minecraft:block.wooden_button.click_on", "capstan_push": "minecraft:block.chain.step",
    "anchor_drop": "minecraft:block.chain.break", "anchor_up": "minecraft:block.anvil.land",
    "cannon_fire": "minecraft:entity.generic.explode", "cannon_load": "minecraft:item.crossbow.loading_end",
    "cannon_hit_hull": "minecraft:entity.zombie.break_wooden_door", "cannon_splash": "minecraft:entity.generic.splash",
    "chain_shot_whirl": "minecraft:item.trident.throw", "mast_crack": "minecraft:entity.zombie.attack_wooden_door",
    "hull_creak": "minecraft:block.wood.step", "water_rush": "minecraft:block.water.ambient",
    "plank_hammer": "minecraft:block.wood.place", "bucket_scoop": "minecraft:item.bucket.fill",
    "bucket_throw": "minecraft:item.bucket.empty", "grog_drink": "minecraft:entity.generic.drink",
    "vomit": "minecraft:entity.player.burp", "ship_sinking": "minecraft:block.bubble_column.upwards_ambient",
    "harpoon_fire": "minecraft:item.crossbow.shoot", "harpoon_reel": "minecraft:entity.fishing_bobber.retrieve",
    "fire_burn": "minecraft:block.fire.ambient", "firebomb_break": "minecraft:entity.splash_potion.break",
    "stove_sizzle": "minecraft:block.campfire.crackle", "ship_bell": "minecraft:block.bell.use",
    "capstan_unwind": "minecraft:block.chain.step", "anchor_bite": "minecraft:block.anvil.land",
    "harpoon_hit": "minecraft:item.trident.hit",
}


def main():
    SRC.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    if "--convert" not in sys.argv:
        for name, (prompt, seconds, _, loop) in SOUNDS.items():
            try:
                generate(name, prompt, seconds, loop)
            except subprocess.CalledProcessError:
                print(f"could not generate {name} (fal credit?); it keeps its placeholder")
    registry = {}
    lang = {}
    generated = 0
    for name, (_, _, subtitle, loop) in SOUNDS.items():
        if convert(name, loop):
            generated += 1
            sound = {"name": f"seafarers:{name}", "stream": False}
        else:
            sound = {"name": PLACEHOLDER[name], "type": "event"}
        registry[name] = {"sounds": [sound], "subtitle": f"subtitles.seafarers.{name}"}
        lang[f"subtitles.seafarers.{name}"] = subtitle
    (OUT.parent / "sounds.json").write_text(json.dumps(registry, indent=2))
    (ROOT / "tools" / "sound_lang.json").write_text(json.dumps(lang, indent=2))
    print(f"{len(registry)} sounds registered, {generated} generated, {len(registry) - generated} vanilla placeholders")


if __name__ == "__main__":
    main()
