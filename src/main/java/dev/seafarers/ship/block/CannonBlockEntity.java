package dev.seafarers.ship.block;

import dev.seafarers.registry.ModSounds;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.seafarers.network.StationPayload;
import dev.seafarers.registry.ModBlockEntities;
import dev.seafarers.ship.Ammo;
import dev.seafarers.ship.CannonballEntity;
import dev.seafarers.ship.Ships;
import dev.seafarers.ship.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * A cannon's state: what it's loaded with, where the barrel points (relative to its carriage) and who is
 * operating it. The operator aims by looking; the barrel follows.
 */
public class CannonBlockEntity extends BlockEntity implements Station, BlockEntitySubLevelActor {
    public static final float MAX_YAW = 50f;
    public static final float MIN_PITCH = -10f;
    public static final float MAX_PITCH = 35f;
    public static final double MUZZLE_SPEED = 2.4; // blocks per tick
    public static final float PIVOT_Y = 8.5f / 16f;   // the trunnions
    public static final float BARREL_LENGTH = 1.5f;   // trunnions to muzzle
    /** How fast a gunner leaves the barrel (blocks per tick). */
    public static final double PLAYER_SPEED = 2.2;
    /** How hard firing kicks the ship, as a change in her speed (m/s). */
    public static final double RECOIL = 0.25;

    private Ammo loaded = Ammo.NONE;
    private float aimYaw;
    private float aimPitch = 5f;
    @Nullable
    private UUID operator;
    private int syncCooldown;
    private boolean syncDirty;

    public CannonBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CANNON.get(), pos, state);
    }

    public Ammo loaded() { return this.loaded; }
    public float aimYaw() { return this.aimYaw; }
    public float aimPitch() { return this.aimPitch; }

    private Direction facing() {
        return this.getBlockState().getValue(CannonBlock.FACING);
    }

    // ---------------------------------------------------------------- ticking

    /** Ticked by Sable while on a ship, by the block ticker otherwise. */
    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        this.serverTick();
    }

    public void serverTick() {
        if (!(this.level instanceof ServerLevel level)) {
            return;
        }
        if (this.operator != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(this.operator);
            Vec3 me = Ships.toWorld(level, this.getBlockPos());
            if (player == null || !player.isAlive() || player.level() != level || player.position().distanceTo(me) > 3.5) {
                this.release(player);
            } else {
                this.aimAlong(level, player.getLookAngle());
            }
        }
        if (this.syncDirty && --this.syncCooldown <= 0) {
            this.syncDirty = false;
            this.syncCooldown = 2;
            level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        }
    }

    /** Points the barrel along a world direction, as far as the carriage allows. */
    public void aimAlong(ServerLevel level, Vec3 worldDir) {
        Vec3 local = Ships.toPlotNormal(level, this.getBlockPos(), worldDir);
        // undo the carriage's facing: the barrel model points north (-z) at rest
        Vector3f v = new Quaternionf().rotationY((float) Math.toRadians(facingYRot(this.facing())))
                .transform(new Vector3f((float) local.x, (float) local.y, (float) local.z));
        float yaw = Mth.clamp((float) Math.toDegrees(Math.atan2(v.x, -v.z)), -MAX_YAW, MAX_YAW);
        float pitch = Mth.clamp((float) Math.toDegrees(Math.asin(Mth.clamp(v.y, -1f, 1f))), MIN_PITCH, MAX_PITCH);
        if (Math.abs(yaw - this.aimYaw) > 0.3f || Math.abs(pitch - this.aimPitch) > 0.3f) {
            this.aimYaw = yaw;
            this.aimPitch = pitch;
            this.syncDirty = true;
        }
    }

    /** Model y-rotation of a horizontal facing, as in blockstate files (north 0, east 90, south 180, west 270). */
    public static float facingYRot(Direction facing) {
        return switch (facing) {
            case EAST -> 90f;
            case SOUTH -> 180f;
            case WEST -> 270f;
            default -> 0f;
        };
    }

    /** Barrel direction in the cannon's plot space. */
    public Vec3 barrelDirection() {
        Vector3f d = new Quaternionf()
                .rotationY((float) Math.toRadians(-facingYRot(this.facing())))
                .rotateY((float) Math.toRadians(-this.aimYaw))
                .rotateX((float) Math.toRadians(this.aimPitch))
                .transform(new Vector3f(0, 0, -1));
        return new Vec3(d.x, d.y, d.z);
    }

    // ---------------------------------------------------------------- crew actions

    public boolean load(Player player, Ammo ammo) {
        if (this.loaded != Ammo.NONE) {
            dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.cannon_loaded"));
            return false;
        }
        this.loaded = ammo;
        this.level.playSound(null, this.getBlockPos(), ModSounds.CANNON_LOAD.get(), SoundSource.BLOCKS, 1f, 1f);
        this.setChanged();
        this.syncDirty = true;
        return true;
    }

    public void takeOver(ServerPlayer player) {
        if (this.operator != null && !this.operator.equals(player.getUUID())
                && player.server.getPlayerList().getPlayer(this.operator) != null) {
            dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.station_taken"));
            return;
        }
        // already at an empty cannon and using it again with nothing in hand: climb into the barrel
        if (player.getUUID().equals(this.operator) && this.loaded == Ammo.NONE && player.getMainHandItem().isEmpty()
                && dev.seafarers.SeafarersConfig.CANNONS_LAUNCH_PLAYERS.get()) {
            this.loaded = Ammo.PLAYER;
            player.setInvisible(true);
            this.level.playSound(null, this.getBlockPos(), ModSounds.CANNON_LOAD.get(), SoundSource.PLAYERS, 1f, 0.85f);
            dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.cannon_climb_in"));
            this.setChanged();
            this.syncDirty = true;
            return;
        }
        if (!player.getUUID().equals(this.operator)) {
            dev.seafarers.ship.Posts.place(player, this.getBlockPos(), this.facing(), 1.35, 0f); // behind the breech
        }
        this.operator = player.getUUID();
        PacketDistributor.sendToPlayer(player, new StationPayload(StationPayload.CANNON, this.getBlockPos()));
        dev.seafarers.ship.Notify.show(player, Component.translatable(this.loaded == Ammo.NONE
                ? "message.seafarers.cannon_hint_empty" : "message.seafarers.cannon_hint"));
    }

    public void release(@Nullable ServerPlayer player) {
        if (player != null && player.getUUID().equals(this.operator)) {
            PacketDistributor.sendToPlayer(player, new StationPayload(StationPayload.NONE, this.getBlockPos()));
        }
        if (this.loaded == Ammo.PLAYER) {
            // climbing back out
            this.loaded = Ammo.NONE;
            if (player != null) {
                player.setInvisible(false);
            }
            this.setChanged();
            this.syncDirty = true;
        }
        this.operator = null;
    }

    @Override
    public void onStationInput(ServerPlayer player, float steer, boolean fire, boolean letGo) {
        if (!player.getUUID().equals(this.operator)) {
            return;
        }
        if (letGo) {
            this.release(player);
        } else if (fire) {
            this.fire(player);
        }
    }

    /** Fires whatever is loaded along the barrel. */
    @Nullable
    public CannonballEntity fire(@Nullable Player shooter) {
        if (!(this.level instanceof ServerLevel level)) {
            return null;
        }
        Vec3 muzzle0 = Vec3.atBottomCenterOf(this.getBlockPos()).add(0, PIVOT_Y, 0);
        if (this.loaded == Ammo.NONE) {
            Vec3 w = Ships.toWorld(level, muzzle0);
            level.playSound(null, w.x, w.y, w.z, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 1f, 0.8f);
            return null;
        }
        Vec3 dirPlot = this.barrelDirection();
        Vec3 muzzlePlot = muzzle0.add(dirPlot.scale(BARREL_LENGTH));
        Vec3 muzzle = Ships.toWorld(level, muzzlePlot);
        Vec3 dir = Ships.toWorldNormal(level, this.getBlockPos(), dirPlot).normalize();
        Vec3 shipVelocity = Ships.velocityAt(level, this.getBlockPos());
        this.recoil(level, dirPlot);

        if (this.loaded == Ammo.PLAYER) {
            // fire the gunner: out of the barrel and across the sea, with no fall damage where they land
            this.loaded = Ammo.NONE;
            this.setChanged();
            this.syncDirty = true;
            if (shooter instanceof ServerPlayer gunner) {
                this.release(gunner);
                gunner.setInvisible(false);
                gunner.teleportTo(level, muzzle.x, muzzle.y - 0.5, muzzle.z, gunner.getYRot(), gunner.getXRot());
                gunner.setDeltaMovement(dir.scale(PLAYER_SPEED).add(shipVelocity.scale(1 / 20.0)));
                gunner.hurtMarked = true;
                gunner.getPersistentData().putLong(CannonFlight.TAG, level.getGameTime());
                dev.seafarers.ship.Feats.award(gunner, "human_cannonball");
            }
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, ModSounds.CANNON_FIRE.get(), SoundSource.PLAYERS, 2.0f, 1.15f);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 16, 0.25, 0.25, 0.25, 0.05);
            return null;
        }

        CannonballEntity ball = new CannonballEntity(level, shooter, this.loaded).firedFrom(Ships.shipAt(level, this.getBlockPos()));
        ball.setPos(muzzle.x, muzzle.y, muzzle.z);
        ball.setDeltaMovement(dir.scale(MUZZLE_SPEED * dev.seafarers.SeafarersConfig.CANNON_RANGE_MULTIPLIER.get()).add(shipVelocity.scale(1 / 20.0)));
        level.addFreshEntity(ball);

        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, ModSounds.CANNON_FIRE.get(), SoundSource.PLAYERS, 2.0f, 0.95f + level.random.nextFloat() * 0.1f);
        if (this.loaded == Ammo.CHAIN_SHOT) {
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, ModSounds.CHAIN_SHOT_WHIRL.get(), SoundSource.PLAYERS, 1.5f, 1f);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 16, 0.25, 0.25, 0.25, 0.05);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 8, 0.1, 0.1, 0.1, 0.08);
        level.sendParticles(ParticleTypes.EXPLOSION, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);

        this.loaded = Ammo.NONE;
        this.setChanged();
        this.syncDirty = true;
        return ball;
    }

    /** The shot kicks the ship back, at the cannon (so a broadside also turns her a touch). */
    private void recoil(ServerLevel level, Vec3 dirPlot) {
        if (Ships.shipAt(level, this.getBlockPos()) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel ship) {
            var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship);
            double mass = ship.getMassTracker().getMass();
            if (handle != null && mass > 0) {
                Vec3 impulse = dirPlot.scale(-RECOIL * mass);
                handle.applyImpulseAtPoint(Vec3.atCenterOf(this.getBlockPos()), impulse);
            }
        }
    }

    // ---------------------------------------------------------------- save / sync

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("ammo", this.loaded.ordinal());
        tag.putFloat("yaw", this.aimYaw);
        tag.putFloat("pitch", this.aimPitch);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.loaded = Ammo.of(tag.getInt("ammo"));
        this.aimYaw = tag.getFloat("yaw");
        this.aimPitch = tag.contains("pitch") ? tag.getFloat("pitch") : 5f;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
