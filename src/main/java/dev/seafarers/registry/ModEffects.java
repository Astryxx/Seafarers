package dev.seafarers.registry;

import dev.seafarers.Seafarers;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Seafarers.MODID);

    /** Grog. The amplifier is how drunk (0..4); the wobble and sway are drawn client side (DrunkClient). */
    public static final DeferredHolder<MobEffect, MobEffect> DRUNK = EFFECTS.register("drunk",
            () -> new MobEffect(MobEffectCategory.NEUTRAL, 0xC8932F) {});

    /** Jig ball: you can't help but dance (spinning, can't walk or use anything). Drawn client side (JigClient). */
    public static final DeferredHolder<MobEffect, MobEffect> JIG = EFFECTS.register("jig",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0xB64FC4) {}.addAttributeModifier(
                    net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, Seafarers.id("jig"), -1.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    private ModEffects() {}
}
