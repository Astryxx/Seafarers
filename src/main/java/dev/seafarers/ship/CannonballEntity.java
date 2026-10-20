package dev.seafarers.ship;

import dev.seafarers.registry.ModSounds;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.registry.ModBlocks;
import dev.seafarers.registry.ModEntities;
import dev.seafarers.ship.block.HullHoleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A shot from a cannon. Cannonballs knock holes in hulls; chain shot tangles in sails and breaks masts but
 * bounces off hulls. Sable's projectile clipping lets shots hit blocks on moving ships.
 */
public class CannonballEntity extends ThrowableItemProjectile {
    private Ammo ammo = Ammo.CANNONBALL;
    /** The ship it was fired from: the shot clears its own gunwale even if the ship has swung a little. */
    @Nullable
    private java.util.UUID homeShip;
    private static final int CLEAR_OWN_SHIP_TICKS = 20;   // a second of flight: clear of her even as she swings

    public CannonballEntity(EntityType<? extends CannonballEntity> type, Level level) {
        super(type, level);
    }

    public CannonballEntity(Level level, @Nullable Entity shooter, Ammo ammo) {
        super(ModEntities.CANNONBALL.get(), level);
        this.setOwner(shooter);
        this.ammo = ammo;
        this.setItem(new ItemStack(ammo.item()));
    }

    public CannonballEntity firedFrom(@Nullable SubLevel ship) {
        this.homeShip = ship == null ? null : ship.getUniqueId();
        return this;
    }

    public Ammo ammo() {
        return this.ammo;
    }

    @Override
    protected Item getDefaultItem() {
        // called from the superclass constructor, before our fields are set
        return this.ammo == null ? Ammo.CANNONBALL.item() : this.ammo.item();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.03;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
            return;
        }
        level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
        if (this.ammo.cursed() || this.ammo == Ammo.FIREBOMB) {
            int c = this.ammo.color;
            level.sendParticles(this.ammo == Ammo.FIREBOMB ? ParticleTypes.FLAME
                            : new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(
                                    ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f), 1.6f),
                    this.getX(), this.getY(), this.getZ(), 3, 0.1, 0.1, 0.1, 0.01);
        }
        if (this.ammo == Ammo.CHAIN_SHOT && this.tangleInSails(level)) {
            this.discard();
            return;
        }
        if (this.isInWater()) {
            level.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 0.5, this.getZ(), 40, 0.6, 0.3, 0.6, 0.2);
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, this.getX(), this.getY(), this.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
            level.playSound(null, this.blockPosition(), ModSounds.CANNON_SPLASH.get(), SoundSource.NEUTRAL, 1.5f, 1f);
            this.discard();
        } else if (this.tickCount > 200) {
            this.discard();
        }
    }

    /**
     * Chain shot passing through a set sail wraps round the rigging and damages the mast. The whole stretch it flew
     * this tick is checked, half a block at a time: a sail seen edge-on is thinner than one tick's flight.
     */
    private boolean tangleInSails(ServerLevel level) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        Vec3 from = new Vec3(this.xo, this.yo, this.zo), to = this.position();
        AABB here = this.getBoundingBox().minmax(this.getBoundingBox().move(from.subtract(to))).inflate(1.0);
        int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) / 0.5));
        for (SubLevel ship : container.queryIntersecting(new dev.ryanhcode.sable.companion.math.BoundingBox3d(
                here.minX, here.minY, here.minZ, here.maxX, here.maxY, here.maxZ))) {
            HelmBlockEntity helm = Ships.helmOf(ship);
            int mast = -1;
            for (int i = 1; i <= steps && helm != null && mast < 0; i++) {
                mast = helm.sailAt(ship.logicalPose().transformPositionInverse(from.lerp(to, i / (double) steps)));
            }
            if (mast >= 0) {
                helm.damageMast(mast, this.ammo.mastDamage, "sail");
                level.playSound(null, this.blockPosition(), SoundEvents.WOOL_BREAK, SoundSource.NEUTRAL, 2f, 0.6f);
                level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY(), this.getZ(), 12, 0.6, 0.6, 0.6, 0.05);
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!dev.seafarers.SeafarersConfig.CREW_FRIENDLY_FIRE.get() && this.crewmates(result.getEntity())) {
            return; // a crewmate's shot passes harmlessly (friendly fire is off)
        }
        result.getEntity().hurt(dev.seafarers.registry.ModDamage.cannonball(this, this.getOwner()),
                (float) (this.ammo.playerDamage * dev.seafarers.SeafarersConfig.CANNON_PLAYER_DAMAGE_MULTIPLIER.get()));
        if (result.getEntity() instanceof net.minecraft.world.entity.LivingEntity living) {
            this.ammo.curse(living);
            if (this.ammo == Ammo.FIREBOMB) {
                living.igniteForSeconds(6);
            }
        }
        Vec3 push = this.getDeltaMovement().normalize().scale(1.2);
        result.getEntity().push(push.x, 0.4, push.z);
    }

    /** Whether the target is in the same crew as whoever fired this shot (or is the gunner). */
    private boolean crewmates(net.minecraft.world.entity.Entity target) {
        if (!(target instanceof net.minecraft.server.level.ServerPlayer hit) || !(this.getOwner() instanceof net.minecraft.server.level.ServerPlayer gunner)) {
            return false;
        }
        if (hit == gunner) {
            return true;
        }
        java.util.UUID ship = dev.seafarers.ship.Crews.get(hit.serverLevel()).shipOf(gunner.getUUID());
        return ship != null && ship.equals(dev.seafarers.ship.Crews.get(hit.serverLevel()).shipOf(hit.getUUID()));
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (this.level() instanceof ServerLevel level) {
            BlockPos pos = result.getBlockPos();
            SubLevel target = Ships.shipAt(level, pos);
            if (target != null) {
                if (this.getOwner() instanceof net.minecraft.server.level.ServerPlayer gunner
                        && (this.homeShip == null || !this.homeShip.equals(target.getUniqueId()))) {
                    HelmBlockEntity hit = Ships.helmOf(target);
                    if (hit != null) {
                        hit.hitBy(gunner);
                    }
                    dev.seafarers.ship.Feats.award(gunner, "hit_ship");
                    if (this.ammo.cursed()) {
                        dev.seafarers.ship.Feats.award(gunner, "curse");
                    }
                }
                hitShip(level, pos, this.ammo, result.getDirection());
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        if (this.homeShip != null && this.tickCount < CLEAR_OWN_SHIP_TICKS && result instanceof BlockHitResult block) {
            SubLevel ship = Ships.shipAt(this.level(), block.getBlockPos());
            if (ship != null && this.homeShip.equals(ship.getUniqueId())) {
                return; // still leaving its own gun port
            }
        }
        super.onHit(result);
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            Vec3 at = result.getLocation();
            Seafarers.LOGGER.info("PROBE shot landed {} at {} {} {}", result.getType(), (int) at.x, (int) at.y, (int) at.z);
            if (this.ammo == Ammo.FIREBOMB) {
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 30, 0.6, 0.4, 0.6, 0.05);
                level.playSound(null, at.x, at.y, at.z, ModSounds.FIREBOMB_BREAK.get(), SoundSource.NEUTRAL, 1.4f, 1.0f);
            } else if (this.ammo.holes) {
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.2, 0.2, 0.2, 0);
                level.playSound(null, at.x, at.y, at.z, ModSounds.CANNON_HIT_HULL.get(), SoundSource.NEUTRAL, 1.4f, 1.0f);
            } else {
                level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1.4f, 0.8f);
            }
            if (this.ammo.cursed()) {
                // the curse bursts over everyone close by
                for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        new net.minecraft.world.phys.AABB(at, at).inflate(3))) {
                    this.ammo.curse(e);
                }
            }
            this.discard();
        }
    }

    /**
     * What a shot does to a block on a ship: cannonballs hole the hull and chip the mast; chain shot only
     * hurts the mast and rigging. Railings and other fittings get knocked loose.
     */
    public static void hitShip(ServerLevel level, BlockPos plotPos, Ammo ammo, net.minecraft.core.Direction face) {
        BlockState state = level.getBlockState(plotPos);
        Seafarers.LOGGER.info("PROBE {} hit {} at {}", ammo, state.getBlock().getDescriptionId(), plotPos.toShortString());
        HelmBlockEntity helm = Ships.helmOf(level, plotPos);
        int mast = mastAt(level, plotPos, state);
        if (mast >= 0) {
            if (helm != null) {
                helm.damageMast(mast, ammo.mastDamage, state.is(ModBlocks.MAST.get()) ? "mast" : "rigging");
            }
            if (state.is(ModBlocks.MAST.get())) {
                return;
            }
        }
        if (ammo == Ammo.FIREBOMB) {
            if (dev.seafarers.SeafarersConfig.SHIP_FIRES.get()) {
                ShipFire.ignite(level, plotPos, face);
            }
            return;
        }
        ammo.curse(helm);
        if (!ammo.holes || !dev.seafarers.SeafarersConfig.CANNONS_HOLE_SHIPS.get()) {
            return;
        }
        if (HullDamage.punch(level, plotPos, state)) {
            level.playSound(null, plotPos, ModSounds.CANNON_HIT_HULL.get(), SoundSource.BLOCKS, 1.2f, 1f);
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state),
                    plotPos.getX() + 0.5, plotPos.getY() + 0.5, plotPos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.2);
            if (helm != null) {
                helm.forceRescan();
            }
        } else if (state.is(BlockTags.FENCES) || state.is(ModBlocks.RAILING.get())) {
            level.destroyBlock(plotPos, false);
        }
    }

    /**
     * Which mast a hit counts against: the mast itself, or rigging lashed to it (a rope ladder up the mast, the
     * crow's nest floor, the yard), found as a mast block right next to the hit block. -1 if none.
     */
    private static int mastAt(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.MAST.get())) {
            return state.getValue(dev.seafarers.ship.block.MastBlock.MAST);
        }
        if (state.is(SeafarersTags.HULL)) {
            return -1;
        }
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockState next = level.getBlockState(pos.relative(d));
            if (next.is(ModBlocks.MAST.get())) {
                return next.getValue(dev.seafarers.ship.block.MastBlock.MAST);
            }
        }
        return -1;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ammo", this.ammo.ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.ammo = Ammo.of(tag.getInt("ammo"));
    }
}
