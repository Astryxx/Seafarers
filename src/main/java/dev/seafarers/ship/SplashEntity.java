package dev.seafarers.ship;

import dev.seafarers.registry.ModSounds;
import dev.seafarers.Seafarers;
import dev.seafarers.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A bucketful of thrown water (or worse). Where it lands it puts out fire on ships (ship fire and burning
 * players) and soaks anyone in the way. It never places a water block.
 */
public class SplashEntity extends ThrowableProjectile {
    /** Hook for ship fire (added with firebombs): called with the landing point. */
    public static Consumer<SplashEntity> onLand = s -> {
        if (ShipFire.douse((ServerLevel) s.level(), s.position(), 1.8) > 0) {
            dev.seafarers.ship.Feats.award(s.getOwner(), "douse");
        }
    };

    private boolean vomit;

    public SplashEntity(EntityType<? extends SplashEntity> type, Level level) {
        super(type, level);
    }

    public SplashEntity(Level level, @Nullable Entity thrower, boolean vomit) {
        super(ModEntities.SPLASH.get(), level);
        this.setOwner(thrower);
        this.vomit = vomit;
    }

    public boolean isVomit() {
        return this.vomit;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            level.sendParticles(this.vomit ? ParticleTypes.ITEM_SLIME : ParticleTypes.SPLASH,
                    this.getX(), this.getY(), this.getZ(), 6, 0.15, 0.1, 0.15, 0.05);
            if (this.tickCount > 40 || this.isInWater()) {
                this.land(level);
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            this.land(level);
        }
    }

    private void land(ServerLevel level) {
        Vec3 at = this.position();
        level.sendParticles(this.vomit ? ParticleTypes.ITEM_SLIME : ParticleTypes.SPLASH, at.x, at.y + 0.2, at.z, 50, 0.6, 0.3, 0.6, 0.2);
        level.playSound(null, at.x, at.y, at.z, ModSounds.BUCKET_THROW.get(), SoundSource.NEUTRAL, 0.8f, 1.1f);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(1.6))) {
            if (this.vomit) {
                Grog.nauseate(e);
            } else {
                e.clearFire();
            }
        }
        if (!this.vomit) {
            // put out vanilla fire too (in the world or on a ship)
            BlockPos c = BlockPos.containing(at);
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-1, -1, -1), c.offset(1, 1, 1))) {
                if (level.getBlockState(p).getBlock() instanceof BaseFireBlock) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            onLand.accept(this);
        }
        Seafarers.LOGGER.debug("splash landed at {}", at);
        this.discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("vomit", this.vomit);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.vomit = tag.getBoolean("vomit");
    }
}
