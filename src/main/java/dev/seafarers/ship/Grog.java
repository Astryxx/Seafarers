package dev.seafarers.ship;

import dev.seafarers.registry.ModSounds;
import dev.seafarers.Seafarers;
import dev.seafarers.registry.ModEffects;
import dev.seafarers.ship.item.ShipBucketItem;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Grog and its consequences. Each tankard adds a level of drunkenness (the drunk effect's amplifier, up to 4) for a
 * minute. From level 3 the drinker throws up now and then; holding an empty ship bucket catches it.
 */
@EventBusSubscriber(modid = Seafarers.MODID)
public final class Grog {
    public static final int MAX_LEVEL = 4;
    public static final int DURATION = 20 * 60;

    /** One more drink. Past a couple, the world starts to spin (nausea), longer the drunker you are. */
    public static void drink(LivingEntity drinker) {
        dev.seafarers.ship.Feats.award(drinker, "grog");
        MobEffectInstance now = drinker.getEffect(ModEffects.DRUNK);
        int level = now == null ? 0 : Math.min(MAX_LEVEL, now.getAmplifier() + 1);
        drinker.addEffect(new MobEffectInstance(ModEffects.DRUNK, dev.seafarers.SeafarersConfig.GROG_SECONDS.get() * 20, level, false, false, true));
        if (level >= 2) {
            drinker.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 20 * (8 + 6 * (level - 2)), 0));
        }
    }

    /** Hit by a bucket of vomit: queasy at once, and throwing up very soon. */
    public static void nauseate(LivingEntity target) {
        target.addEffect(new MobEffectInstance(ModEffects.DRUNK, 20 * 8, 3, false, false, true));
        target.getPersistentData().putInt("seafarers_vomit_in", 10);
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity e) || !(e.level() instanceof ServerLevel level)) {
            return;
        }
        var data = e.getPersistentData();
        if (data.contains("seafarers_vomit_in")) {
            int left = data.getInt("seafarers_vomit_in") - 1;
            if (left <= 0) {
                data.remove("seafarers_vomit_in");
                vomit(level, e);
            } else {
                data.putInt("seafarers_vomit_in", left);
            }
            return;
        }
        MobEffectInstance drunk = e.getEffect(ModEffects.DRUNK);
        if (drunk != null && drunk.getAmplifier() >= 3 && e.getRandom().nextInt(20 * 12) == 0) {
            data.putInt("seafarers_vomit_in", 30);
            level.playSound(null, e.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1f, 0.6f);
        }
    }

    /** Throws up. A drinker holding an empty ship bucket catches it in the bucket instead of on the deck. */
    public static void vomit(ServerLevel level, LivingEntity e) {
        dev.seafarers.ship.Feats.award(e, "vomit");
        Vec3 look = e.getLookAngle();
        Vec3 mouth = e.getEyePosition().add(look.scale(0.4)).subtract(0, 0.15, 0);
        level.playSound(null, e.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.2f, 0.4f);
        level.playSound(null, e.blockPosition(), ModSounds.VOMIT.get(), SoundSource.PLAYERS, 1f, 1f);
        if (e instanceof Player player) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack held = player.getItemInHand(hand);
                if (held.getItem() instanceof ShipBucketItem bucket && bucket.content() == ShipBucketItem.Content.EMPTY) {
                    player.setItemInHand(hand, ShipBucketItem.of(ShipBucketItem.Content.VOMIT));
                    Seafarers.LOGGER.info("PROBE vomit caught in bucket");
                    return;
                }
            }
        }
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SLIME_BALL)),
                mouth.x, mouth.y, mouth.z, 25, look.x * 0.2, 0.05, look.z * 0.2, 0.15);
        Seafarers.LOGGER.info("PROBE vomit");
    }

    private Grog() {}
}
