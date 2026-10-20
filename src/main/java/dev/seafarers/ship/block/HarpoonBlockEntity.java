package dev.seafarers.ship.block;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.seafarers.network.StationPayload;
import dev.seafarers.registry.ModBlockEntities;
import dev.seafarers.ship.HarpoonEntity;
import dev.seafarers.ship.Ships;
import dev.seafarers.ship.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * A mounted harpoon gun. Take it like a cannon (look to aim), left click to fire the harpoon on its line, hold W
 * to reel in, left click again to cut it loose. A harpoon in loot or a creature drags it to the gun; a harpoon in
 * land or another ship pulls this ship toward it (and that ship toward this one), which is how crews swing round
 * a rock or draw alongside.
 */
public class HarpoonBlockEntity extends BlockEntity implements Station, BlockEntitySubLevelActor {
    public static final float MAX_YAW = 80f;
    public static final float MIN_PITCH = -40f;
    public static final float MAX_PITCH = 50f;
    public static final float PIVOT_Y = 11.5f / 16f;
    /** The gun stands this far forward of its block's centre (see the model). */
    public static final float FORWARD = 6f / 16f;
    public static final float MUZZLE = 1.3f;
    public static final double SPEED = 2.6;          // blocks per tick
    public static final double REEL = 0.2;           // blocks per tick
    /** Line pull on a ship: acceleration per block of stretch (1/s^2), damping (1/s), and the most it gives (m/s^2). */
    public static double PULL_K = 2.5, PULL_C = 1.5, PULL_MAX = 3.0;

    private float aimYaw;
    private float aimPitch = 5f;
    @Nullable
    private UUID operator;
    @Nullable
    private UUID harpoon;
    private float reelInput;
    private boolean syncDirty;
    private int syncCooldown;
    /** Client: whether the harpoon sits in the gun (synced). */
    private boolean loaded = true;

    public HarpoonBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HARPOON.get(), pos, state);
    }

    public float aimYaw() { return this.aimYaw; }
    public float aimPitch() { return this.aimPitch; }
    public boolean loaded() { return this.loaded; }

    private Direction facing() {
        return this.getBlockState().getValue(HarpoonBlock.FACING);
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        this.serverTick();
    }

    public void serverTick() {
        if (!(this.level instanceof ServerLevel level)) {
            return;
        }
        if (this.operator != null && !dev.seafarers.ship.HelmBlockEntity.TEST_HANDS.contains(this.operator)) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(this.operator);
            Vec3 me = Ships.toWorld(level, this.getBlockPos());
            if (player == null || !player.isAlive() || player.level() != level || player.position().distanceTo(me) > 3.5) {
                this.release(player);
            } else {
                this.aimAlong(level, player.getLookAngle());
            }
        }
        HarpoonEntity h = this.harpoon(level);
        if (this.harpoon != null && h == null) {
            this.harpoon = null; // snapped or gone
            this.markSync();
        }
        if (h != null && h.stuck() && this.reelInput > 0) {
            h.reel(REEL * this.reelInput);
            if (level.getGameTime() % 12 == 0) {
                Vec3 w = Ships.toWorld(level, this.getBlockPos());
                level.playSound(null, w.x, w.y, w.z, dev.seafarers.registry.ModSounds.HARPOON_REEL.get(), SoundSource.BLOCKS, 0.8f, 1f);
            }
        }
        boolean nowLoaded = this.harpoon == null;
        if (nowLoaded != this.loaded) {
            this.loaded = nowLoaded;
            this.markSync();
        }
        if (this.syncDirty && --this.syncCooldown <= 0) {
            this.syncDirty = false;
            this.syncCooldown = 2;
            level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        }
    }

    /**
     * A harpoon in land or in another ship: once the line is taut it pulls this ship toward the harpoon at the
     * gun (so she swings round it), and the other ship toward this one.
     */
    @Override
    public void sable$physicsTick(ServerSubLevel ship, RigidBodyHandle handle, double dt) {
        if (!(this.level instanceof ServerLevel level)) {
            return;
        }
        HarpoonEntity h = this.harpoon(level);
        if (h == null || !h.fixed()) {
            return;
        }
        Vec3 muzzlePlot = this.muzzlePlot();
        Vec3 gun = ship.logicalPose().transformPosition(muzzlePlot);
        Vec3 target = h.position();
        Vec3 d = target.subtract(gun);
        double dist = d.length();
        double stretch = dist - h.lineLength();
        if (stretch <= 0 || dist < 1e-3) {
            return;
        }
        Vec3 n = d.scale(1 / dist);
        Vec3 vGun = Ships.velocityAt(level, this.getBlockPos());
        Vec3 vTarget = h.anchorVelocity(level);
        double opening = vTarget.subtract(vGun).dot(n);
        double accel = Mth.clamp(PULL_K * stretch + PULL_C * Math.max(0, opening), 0, PULL_MAX);
        double mass = ship.getMassTracker().getMass();
        Vec3 impulse = n.scale(accel * mass * dt);
        handle.applyImpulseAtPoint(muzzlePlot, Ships.toPlotNormal(level, this.getBlockPos(), impulse));
        ServerSubLevel other = h.anchorShip(level);
        if (other != null && other != ship) {
            RigidBodyHandle theirs = RigidBodyHandle.of(other);
            double theirMass = other.getMassTracker().getMass();
            if (theirs != null && theirMass > 0) {
                Vec3 back = n.scale(-Mth.clamp(accel * mass, 0, PULL_MAX * theirMass) * dt);
                Vec3 at = h.anchorPlot();
                theirs.applyImpulseAtPoint(at, other.logicalPose().transformNormalInverse(back));
            }
        }
    }

    public void aimAlong(ServerLevel level, Vec3 worldDir) {
        Vec3 local = Ships.toPlotNormal(level, this.getBlockPos(), worldDir);
        Vector3f v = new Quaternionf().rotationY((float) Math.toRadians(CannonBlockEntity.facingYRot(this.facing())))
                .transform(new Vector3f((float) local.x, (float) local.y, (float) local.z));
        float yaw = Mth.clamp((float) Math.toDegrees(Math.atan2(v.x, -v.z)), -MAX_YAW, MAX_YAW);
        float pitch = Mth.clamp((float) Math.toDegrees(Math.asin(Mth.clamp(v.y, -1f, 1f))), MIN_PITCH, MAX_PITCH);
        if (Math.abs(yaw - this.aimYaw) > 0.3f || Math.abs(pitch - this.aimPitch) > 0.3f) {
            this.aimYaw = yaw;
            this.aimPitch = pitch;
            this.markSync();
        }
    }

    /** Where the gun points, in plot space. */
    public Vec3 barrelDirection() {
        Vector3f d = new Quaternionf()
                .rotationY((float) Math.toRadians(-CannonBlockEntity.facingYRot(this.facing())))
                .rotateY((float) Math.toRadians(-this.aimYaw))
                .rotateX((float) Math.toRadians(this.aimPitch))
                .transform(new Vector3f(0, 0, -1));
        return new Vec3(d.x, d.y, d.z);
    }

    /** The muzzle in plot space: where the line leaves the gun. */
    public Vec3 muzzlePlot() {
        Direction f = this.facing();
        return Vec3.atBottomCenterOf(this.getBlockPos()).add(f.getStepX() * FORWARD, PIVOT_Y, f.getStepZ() * FORWARD)
                .add(this.barrelDirection().scale(MUZZLE));
    }

    // ---------------------------------------------------------------- crew actions

    public void takeOver(ServerPlayer player) {
        if (this.operator != null && !this.operator.equals(player.getUUID())
                && player.server.getPlayerList().getPlayer(this.operator) != null) {
            dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.station_taken"));
            return;
        }
        if (!player.getUUID().equals(this.operator)) {
            dev.seafarers.ship.Posts.place(player, this.getBlockPos(), this.getBlockState().getValue(HarpoonBlock.FACING), 1.0, 0f);
        }
        this.operator = player.getUUID();
        this.reelInput = 0;
        PacketDistributor.sendToPlayer(player, new StationPayload(StationPayload.HARPOON, this.getBlockPos()));
        dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.harpoon_hint"));
    }

    public void release(@Nullable ServerPlayer player) {
        if (player != null && player.getUUID().equals(this.operator)) {
            PacketDistributor.sendToPlayer(player, new StationPayload(StationPayload.NONE, this.getBlockPos()));
        }
        this.operator = null;
        this.reelInput = 0;
        // nobody on the drum: the line pays out and the harpoon comes loose
        if (this.level instanceof ServerLevel level) {
            this.cutLoose(level);
        }
    }

    @Override
    public void onStationInput(ServerPlayer player, float steer, boolean fire, boolean letGo) {
        this.onStationInput(player, steer, 0, fire, letGo);
    }

    @Override
    public void onStationInput(ServerPlayer player, float steer, float forward, boolean fire, boolean letGo) {
        if (!player.getUUID().equals(this.operator)) {
            return;
        }
        this.reelInput = Math.max(0, forward);
        if (letGo) {
            this.release(player);
        } else if (fire) {
            this.fire(player);
        }
    }

    /** Fires the harpoon, or if it is already out, cuts it loose. */
    @Nullable
    public HarpoonEntity fire(@Nullable Entity shooter) {
        if (!(this.level instanceof ServerLevel level)) {
            return null;
        }
        if (this.harpoon != null) {
            this.cutLoose(level);
            return null;
        }
        Vec3 muzzle = Ships.toWorld(level, this.muzzlePlot());
        Vec3 dir = Ships.toWorldNormal(level, this.getBlockPos(), this.barrelDirection()).normalize();
        Vec3 shipVelocity = Ships.velocityAt(level, this.getBlockPos());
        HarpoonEntity h = new HarpoonEntity(level, shooter, this.getBlockPos());
        h.setPos(muzzle.x, muzzle.y, muzzle.z);
        h.setDeltaMovement(dir.scale(SPEED).add(shipVelocity.scale(1 / 20.0)));
        h.faceAlong(dir);
        level.addFreshEntity(h);
        this.harpoon = h.getUUID();
        this.markSync();
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, dev.seafarers.registry.ModSounds.HARPOON_FIRE.get(), SoundSource.PLAYERS, 1.5f, 1f);
        return h;
    }

    public void cutLoose(ServerLevel level) {
        HarpoonEntity h = this.harpoon(level);
        if (h != null) {
            h.discard();
        }
        if (this.harpoon != null) {
            this.harpoon = null;
            this.markSync();
        }
    }

    @Nullable
    public HarpoonEntity harpoon(ServerLevel level) {
        return this.harpoon != null && level.getEntity(this.harpoon) instanceof HarpoonEntity h && h.isAlive() ? h : null;
    }

    private void markSync() {
        this.syncDirty = true;
        this.setChanged();
    }

    // ---------------------------------------------------------------- save / sync

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("yaw", this.aimYaw);
        tag.putFloat("pitch", this.aimPitch);
        tag.putBoolean("loaded", this.harpoon == null);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.aimYaw = tag.getFloat("yaw");
        this.aimPitch = tag.contains("pitch") ? tag.getFloat("pitch") : 5f;
        this.loaded = !tag.contains("loaded") || tag.getBoolean("loaded");
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
