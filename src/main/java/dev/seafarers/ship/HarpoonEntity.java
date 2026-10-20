package dev.seafarers.ship;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.registry.ModEntities;
import dev.seafarers.ship.block.HarpoonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A harpoon on its line. It flies like a bolt, then sticks in land, in a ship, in a creature or in floating loot.
 * The gun (a {@link HarpoonBlockEntity}, maybe on a ship) holds the other end: the line can only be as long as
 * what is paid out, and reeling shortens it. Loot and creatures are dragged in; land and ships pull the gun's ship
 * (see HarpoonBlockEntity's physics tick). Players can't be harpooned.
 */
public class HarpoonEntity extends Projectile {
    private static final EntityDataAccessor<BlockPos> GUN = SynchedEntityData.defineId(HarpoonEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(HarpoonEntity.class, EntityDataSerializers.BOOLEAN);

    private double line = dev.seafarers.SeafarersConfig.HARPOON_ROPE_BLOCKS.get();
    private int wetTicks;
    @Nullable
    private Entity hooked;
    /** Stuck in a ship: which, and where in its plot. */
    @Nullable
    private UUID anchorShip;
    private Vec3 anchorPlot = Vec3.ZERO;
    /** Stuck in land (world space). */
    @Nullable
    private Vec3 anchorWorld;

    public HarpoonEntity(EntityType<? extends HarpoonEntity> type, Level level) {
        super(type, level);
    }

    public HarpoonEntity(Level level, @Nullable Entity shooter, BlockPos gun) {
        super(ModEntities.HARPOON.get(), level);
        this.setOwner(shooter);
        this.entityData.set(GUN, gun);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(GUN, BlockPos.ZERO);
        builder.define(STUCK, false);
    }

    public BlockPos gun() {
        return this.entityData.get(GUN);
    }

    public boolean stuck() {
        return this.entityData.get(STUCK);
    }

    /** Stuck in land or in a ship: the line pulls the gun's ship rather than reeling the harpoon in. */
    public boolean fixed() {
        return this.anchorWorld != null || this.anchorShip != null;
    }

    public double lineLength() {
        return this.line;
    }

    public Vec3 anchorPlot() {
        return this.anchorPlot;
    }

    @Nullable
    public ServerSubLevel anchorShip(ServerLevel level) {
        return this.anchorShip == null ? null
                : SubLevelContainer.getContainer(level).getSubLevel(this.anchorShip) instanceof ServerSubLevel s ? s : null;
    }

    /** World velocity of whatever the harpoon is stuck in. */
    public Vec3 anchorVelocity(ServerLevel level) {
        if (this.anchorShip != null) {
            return dev.ryanhcode.sable.Sable.HELPER.getVelocity(level, this.anchorPlot);
        }
        return this.hooked != null ? this.hooked.getDeltaMovement().scale(20) : Vec3.ZERO;
    }

    /** Takes in some line (blocks). It never goes shorter than 2 blocks. */
    public void reel(double amount) {
        Vec3 gun = this.gunWorld();
        double dist = gun == null ? this.line : this.position().distanceTo(gun);
        this.line = Math.max(2, Math.min(this.line, dist + 0.5) - amount);
    }

    public void faceAlong(Vec3 dir) {
        double flat = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        this.setYRot((float) (Mth.atan2(dir.x, dir.z) * Mth.RAD_TO_DEG));
        this.setXRot((float) (Mth.atan2(dir.y, flat) * Mth.RAD_TO_DEG));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    @Nullable
    private Vec3 gunWorld() {
        BlockPos gun = this.gun();
        if (!(this.level().getBlockEntity(gun) instanceof HarpoonBlockEntity be)) {
            return null;
        }
        return Ships.toWorld(this.level(), be.muzzlePlot());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            if (!this.stuck()) {
                Vec3 v = this.getDeltaMovement();
                this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
            }
            return;
        }
        Vec3 gun = this.gunWorld();
        if (gun == null || !(level.getBlockEntity(this.gun()) instanceof HarpoonBlockEntity be) || be.harpoon(level) != this) {
            this.discard();
            return;
        }
        if (this.hooked != null) {
            this.tickHooked(level, gun);
        } else if (this.anchorShip != null) {
            ServerSubLevel ship = this.anchorShip(level);
            if (ship == null) {
                this.discard();
                return;
            }
            Vec3 w = ship.logicalPose().transformPosition(this.anchorPlot);
            this.setPos(w.x, w.y, w.z);
        } else if (this.anchorWorld == null) {
            this.tickFlying(level, gun);
        }
        if (this.isRemoved()) {
            return;
        }
        // too far: the line runs out and snaps (a ship pulled away, or the harpoon flew past its range)
        if (this.position().distanceTo(gun) > dev.seafarers.SeafarersConfig.HARPOON_ROPE_BLOCKS.get() + 6) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LEASH_KNOT_BREAK, SoundSource.NEUTRAL, 1f, 0.8f);
            Seafarers.LOGGER.info("PROBE harpoon line snapped");
            this.discard();
        }
    }

    private void tickFlying(ServerLevel level, Vec3 gun) {
        Vec3 v = this.getDeltaMovement();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() == HitResult.Type.MISS) {
            // loot isn't pickable, so look for it ourselves along the way
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().expandTowards(v).inflate(this.isInWater() ? 2.0 : 1.0))) {
                hit = new EntityHitResult(item);
                break;
            }
        }
        if (hit.getType() != HitResult.Type.MISS) {
            this.onHit(hit);
            if (this.stuck()) {
                return;
            }
        }
        if (this.isInWater()) {
            // into the sea: it slows and sinks, still feeling for loot, then the line comes back empty
            if (this.wetTicks++ == 0) {
                level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.NEUTRAL, 0.5f, 1.4f);
            }
            this.setDeltaMovement(v.scale(0.3).add(0, -0.04, 0));
            if (this.wetTicks > 10) {
                Seafarers.LOGGER.info("PROBE harpoon splashed down at {} {} {}", (int) this.getX(), String.format("%.2f", this.getY()), (int) this.getZ());
                this.discard();
                return;
            }
        }
        this.setDeltaMovement(this.getDeltaMovement().add(0, -0.03, 0));
        this.faceAlong(this.getDeltaMovement());
        this.setPos(this.position().add(v));
        if (this.position().distanceTo(gun) > this.line) {
            Seafarers.LOGGER.info("PROBE harpoon out of line");
            this.discard();
        }
    }

    private void tickHooked(ServerLevel level, Vec3 gun) {
        Entity e = this.hooked;
        if (e == null || !e.isAlive()) {
            this.discard();
            return;
        }
        Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
        this.setPos(at.x, at.y, at.z);
        Vec3 d = gun.subtract(at);
        double dist = d.length();
        if (dist < 3.5 && e instanceof ShipwreckBarrelEntity barrel) {
            barrel.setDeltaMovement(Vec3.ZERO);
            barrel.setPos(gun.x, gun.y + 0.2, gun.z);
            Seafarers.LOGGER.info("PROBE harpoon reeled in a shipwreck barrel");
            this.discard();
            return;
        }
        if (dist < 3.5 && e instanceof ItemEntity item) {
            // reeled in alongside: the gunner swings it aboard, onto the deck at the gun
            item.setDeltaMovement(Vec3.ZERO);
            item.setPos(gun.x, gun.y + 0.2, gun.z);
            Seafarers.LOGGER.info("PROBE harpoon reeled in {}", item.getItem());
            this.discard();
            return;
        }
        if (dist > this.line) {
            // the line drags it toward the gun (and keeps it from drifting off)
            Vec3 pull = d.scale(1 / dist).scale(Math.min(0.6, 0.15 + 0.2 * (dist - this.line)))
                    .add(0, e instanceof ItemEntity || e instanceof ShipwreckBarrelEntity ? 0.04 : 0, 0);   // lifted out of the water and up the ship's side
            Vec3 v = e.getDeltaMovement();
            double along = v.dot(d.scale(1 / dist));
            e.setDeltaMovement(v.add(pull).subtract(d.scale(1 / dist).scale(Math.min(0, along))));
            e.hurtMarked = true;
            if (e instanceof ItemEntity item) {
                item.setNoGravity(true); // carried along the line, skipping over the water
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return !(target instanceof Player) && !(target instanceof HarpoonEntity) && super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity e = result.getEntity();
        if (e instanceof LivingEntity living) {
            living.hurt(this.damageSources().trident(this, this.getOwner()), 4f);
        }
        this.hooked = e;
        this.stick();
        dev.seafarers.ship.Feats.award(this.getOwner(), "harpoon");
        Seafarers.LOGGER.info("PROBE harpoon hooked {}", e.getType().getDescriptionId());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        BlockPos pos = result.getBlockPos();
        SubLevel ship = Ships.shipAt(this.level(), pos);
        if (ship != null) {
            if (ship == Ships.shipAt(this.level(), this.gun())) {
                return; // its own ship's rail: fly on
            }
            this.anchorShip = ship.getUniqueId();
            this.anchorPlot = result.getLocation();
        } else {
            this.anchorWorld = result.getLocation();
            this.setPos(this.anchorWorld.x, this.anchorWorld.y, this.anchorWorld.z);
        }
        this.stick();
        dev.seafarers.ship.Feats.award(this.getOwner(), "harpoon");
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), dev.seafarers.registry.ModSounds.HARPOON_HIT.get(), SoundSource.NEUTRAL, 1.2f, 1f);
        Seafarers.LOGGER.info("PROBE harpoon stuck in {} {}", ship != null ? "ship" : "land",
                this.level().getBlockState(pos).getBlock().getDescriptionId());
    }

    private void stick() {
        this.entityData.set(STUCK, true);
        this.setDeltaMovement(Vec3.ZERO);
        Vec3 gun = this.gunWorld();
        if (gun != null) {
            this.line = Math.min(this.line, this.position().distanceTo(gun) + 0.5);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 128 * 128;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("gun", this.gun().asLong());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(GUN, BlockPos.of(tag.getLong("gun")));
    }
}
