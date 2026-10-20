package dev.seafarers.registry;

import dev.seafarers.Seafarers;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's sounds (generated with tools/gen_sounds.py; listed in assets/seafarers/sounds.json). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Seafarers.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_PULL = register("rope_pull");
    public static final DeferredHolder<SoundEvent, SoundEvent> PULLEY_CREAK = register("pulley_creak");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAIL_SNAP = register("sail_snap");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAIL_LUFF = register("sail_luff");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHEEL_TURN = register("wheel_turn");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPSTAN_PUSH = register("capstan_push");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANCHOR_DROP = register("anchor_drop");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANCHOR_UP = register("anchor_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANNON_FIRE = register("cannon_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANNON_LOAD = register("cannon_load");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANNON_HIT_HULL = register("cannon_hit_hull");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANNON_SPLASH = register("cannon_splash");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHAIN_SHOT_WHIRL = register("chain_shot_whirl");
    public static final DeferredHolder<SoundEvent, SoundEvent> MAST_CRACK = register("mast_crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> HULL_CREAK = register("hull_creak");
    public static final DeferredHolder<SoundEvent, SoundEvent> WATER_RUSH = register("water_rush");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLANK_HAMMER = register("plank_hammer");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUCKET_SCOOP = register("bucket_scoop");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUCKET_THROW = register("bucket_throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> GROG_DRINK = register("grog_drink");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOMIT = register("vomit");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIP_SINKING = register("ship_sinking");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARPOON_FIRE = register("harpoon_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARPOON_REEL = register("harpoon_reel");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_BURN = register("fire_burn");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIREBOMB_BREAK = register("firebomb_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> STOVE_SIZZLE = register("stove_sizzle");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIP_BELL = register("ship_bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPSTAN_UNWIND = register("capstan_unwind");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANCHOR_BITE = register("anchor_bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARPOON_HIT = register("harpoon_hit");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Seafarers.id(name)));
    }

    private ModSounds() {}
}
