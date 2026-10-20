package dev.seafarers.ship;

import dev.seafarers.registry.ModEffects;
import dev.seafarers.registry.ModItems;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * What a cannon can be loaded with. Cannonballs and cursed cannonballs hole hulls; chain shot breaks masts and
 * bounces off hulls; firebombs set ships alight. A cursed ball's curse lands on the ship it hits (its helm) and on
 * anyone it hits or splashes.
 */
public enum Ammo {
    NONE(0, 0, false, 0),
    CANNONBALL(10f, 1, true, 0x2A2C30),
    CHAIN_SHOT(4f, 3, false, 0x3B3D42),
    FIREBOMB(4f, 0, false, 0xD9631E),
    /** The cannon's operator climbed in: fires them across the sea. */
    PLAYER(0, 0, false, 0),
    // ---- cursed cannonballs ----
    ANCHOR_BALL(6f, 0, true, 0x5F6B78),
    RIGGING_BALL(6f, 0, true, 0xD8C9A3),
    BALLAST_BALL(6f, 0, true, 0x3E6E4C),
    GROG_BALL(6f, 0, true, 0xC8932F),
    LIMP_BALL(6f, 0, true, 0x7F7FA0),
    VENOM_BALL(6f, 0, true, 0x5BAA3A),
    WEARY_BALL(6f, 0, true, 0x3F5AA8),
    JIG_BALL(6f, 0, true, 0xB64FC4);

    public final float playerDamage;
    public final int mastDamage;
    /** Knocks holes in hulls. */
    public final boolean holes;
    /** Tint of the shot and its trail. */
    public final int color;

    Ammo(float playerDamage, int mastDamage, boolean holes, int color) {
        this.playerDamage = playerDamage;
        this.mastDamage = mastDamage;
        this.holes = holes;
        this.color = color;
    }

    public boolean cursed() {
        return this.ordinal() >= ANCHOR_BALL.ordinal();
    }

    public Item item() {
        return switch (this) {
            case NONE, PLAYER -> Items.AIR;
            case CANNONBALL -> ModItems.CANNONBALL.get();
            case CHAIN_SHOT -> ModItems.CHAIN_SHOT.get();
            default -> ModItems.AMMO.get(this).get();
        };
    }

    /** The curse's effect on a person it hits or splashes. */
    public void curse(LivingEntity e) {
        if (!dev.seafarers.SeafarersConfig.CURSED_CANNONBALLS.get()) {
            return;
        }
        switch (this) {
            case GROG_BALL -> {
                Grog.drink(e);
                Grog.drink(e);
                Grog.drink(e);
            }
            case LIMP_BALL -> e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 10, 3));
            case VENOM_BALL -> e.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 8, 1));
            case WEARY_BALL -> {
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 6, 0));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 6, 5));
            }
            case JIG_BALL -> e.addEffect(new MobEffectInstance(ModEffects.JIG, 20 * 6, 0, false, true, true));
            default -> {
                return;
            }
        }
        dev.seafarers.Seafarers.LOGGER.info("PROBE cursed {} with {}: {}", e.getType().getDescriptionId(), this,
                e.getActiveEffects().stream().map(i -> i.getEffect().getRegisteredName()).sorted().toList());
    }

    /** The curse's effect on the ship it hits. */
    public void curse(@Nullable HelmBlockEntity ship) {
        if (ship == null || !dev.seafarers.SeafarersConfig.CURSED_CANNONBALLS.get()) {
            return;
        }
        switch (this) {
            case ANCHOR_BALL -> ship.forceAnchor();
            case RIGGING_BALL -> ship.furlAllSails();
            case BALLAST_BALL -> ship.addBallast(20 * 20);
            default -> {
            }
        }
    }

    public static Ammo of(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : NONE;
    }
}
