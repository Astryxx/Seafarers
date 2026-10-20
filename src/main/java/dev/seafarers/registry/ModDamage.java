package dev.seafarers.registry;

import dev.seafarers.Seafarers;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** Damage of the mod's own, so deaths read right ("was blasted overboard by Alex's cannonball"). Defined in data/seafarers/damage_type. */
public final class ModDamage {
    public static final ResourceKey<DamageType> CANNONBALL = ResourceKey.create(Registries.DAMAGE_TYPE, Seafarers.id("cannonball"));

    public static DamageSource cannonball(Entity ball, @Nullable Entity shooter) {
        return new DamageSource(ball.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(CANNONBALL), ball, shooter);
    }

    private ModDamage() {}
}
