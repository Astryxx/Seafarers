package dev.seafarers.ship;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.seafarers.Seafarers;
import dev.seafarers.network.StationPayload;
import dev.seafarers.registry.ModBlockEntities;
import dev.seafarers.registry.ModBlocks;
import dev.seafarers.registry.ModSounds;
import dev.seafarers.ship.block.BilgeWaterBlock;
import dev.seafarers.ship.block.HelmBlock;
import dev.seafarers.ship.block.SailYardBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The ship's brain. Lives at the helm, travels with the ship (it is a block entity in the ship's plot), and
 * holds everything the crew changes: the wheel, each mast's sail, the anchor, and the water in the hull.
 * How the ship handles comes from her {@link ShipStats}, read from the layout at launch.
 * <p>
 * Movement is applied as velocity changes in Sable's physics tick, so tuning is in m/s and rad/s and does not
 * depend on how heavy the ship is. Sable handles buoyancy, collisions and players standing on deck.
 */
public class HelmBlockEntity extends BlockEntity implements BlockEntitySubLevelActor, Station {
    /** How far one pull on a held rope moves the sail (Minecraft repeats a held use 5 times a second): ~3 s top to bottom. */
    public static final float SAIL_PULL = 0.07f;
    /** Degrees one pull on a held angle rope swings the yard: ~6 s from hard over to hard over. */
    public static final float BRACE_PULL = 3f;
    public static final float MAX_SAIL_ANGLE = 45f;
    public static final float FLOOD_MAX = 100f;
    public static final int ANCHOR_RAISE_MAX = 100;
    /** Ticks the anchor takes to reach the bottom and bite. */
    public static final int ANCHOR_FALL_TICKS = 15;
    /** Stand-in players used by the test commands; they are not in the player list, so the helm keeps them. */
    public static final java.util.Set<UUID> TEST_HANDS = new java.util.HashSet<>();

    /** One mast's sail: how far it is let down (0 furled .. 1 fully set), its angle, the mast's health, and how well it draws. */
    public static final class Sail {
        float deploy;
        float angle;
        /** Where the ropes have set it: the sail rolls and the yard swings toward these at a steady rate. */
        float deployGoal = Float.NaN;
        float angleGoal = Float.NaN;
        int health;
        float fill;

        Sail(int health) {
            this.health = health;
        }

        public float deploy() { return this.deploy; }
        public float angle() { return this.angle; }
        public float deployGoal() { return Float.isNaN(this.deployGoal) ? this.deploy : this.deployGoal; }
        public float angleGoal() { return Float.isNaN(this.angleGoal) ? this.angle : this.angleGoal; }

        /** Sets it there at once (curses, test controls, a broken mast), with the ropes agreeing. */
        void set(float deploy, float angle) {
            this.deploy = this.deployGoal = deploy;
            this.angle = this.angleGoal = angle;
        }
        public int health() { return this.health; }
        public float fill() { return this.fill; }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putFloat("deploy", this.deploy);
            t.putFloat("angle", this.angle);
            t.putInt("health", this.health);
            t.putFloat("fill", this.fill);
            t.putFloat("deployGoal", this.deployGoal());
            t.putFloat("angleGoal", this.angleGoal());
            return t;
        }

        static Sail load(CompoundTag t) {
            Sail s = new Sail(t.getInt("health"));
            s.deploy = t.getFloat("deploy");
            s.angle = t.getFloat("angle");
            s.fill = t.getFloat("fill");
            s.deployGoal = t.contains("deployGoal") ? t.getFloat("deployGoal") : s.deploy;
            s.angleGoal = t.contains("angleGoal") ? t.getFloat("angleGoal") : s.angle;
            return s;
        }
    }

    // ---- ship (set at launch, saved) ----
    private String layout = "sloop";
    private ShipStats stats = new ShipStats();
    private BlockPos respawn = BlockPos.ZERO;
    /** Every spot crew can respawn at (plot space); empty = just {@link #respawn}. */
    private final List<BlockPos> respawns = new ArrayList<>();
    /** The brig (plot space): its two inside corners and its door's lower half; empty if she has none. */
    private final List<BlockPos> brig = new ArrayList<>();
    private final List<BlockPos> bilge = new ArrayList<>();
    /** Cells where the sea is kept out (Sable water occlusion): the inside of the hull, planking included. */
    private final List<BlockPos> dry = new ArrayList<>();
    @Nullable
    private java.util.Set<BlockPos> drySet;
    private int drySetSize;

    // ---- crew-controlled state (saved, synced to clients) ----
    private float wheel;
    private final List<Sail> sails = new ArrayList<>(List.of(new Sail(6)));
    private boolean anchored;
    /** How far the anchor has been heaved up, 0..100 (percent). It falls back if nobody keeps heaving. */
    private float anchorRaise;
    private int anchorTicks;
    /** Last time each crewmate pushed the capstan: a held use repeats every few ticks, so recent = still heaving. */
    private final java.util.Map<UUID, Long> heavers = new java.util.HashMap<>();
    /** Last push from anyone (a click soon after another is the same held click, not a new one). */
    private long lastHeave = -100;
    /** +1 heaving, -1 running back down, 0 still (synced so clients can turn the drum between updates). */
    private int heaveDir;
    private float pivotEnergy;
    /** Game time the anchor was last heaved up; a held click on the capstan can't drop it again straight away. */
    private long anchorWeighedAt = -100;
    private float flood;
    private boolean sinking;
    /** She has had a crew (so she's someone's ship, not a wreck or a test hull). */
    private boolean owned;
    /** Forward speed at the last physics step, and the speed she was doing when she last hit something hard (0 if not). */
    private double lastForward;
    /** The fastest she's gone (signed, along her bow) in the last half second, and how many ticks ago. */
    private double peakForward;
    private int peakAge;
    private int crashCooldown;
    /** Physics steps left to log in detail (debug: /seafarers ship trace). */
    public int trace;
    /** Who last holed her with a cannonball, and when (game time): the credit if she sinks soon after. */
    @Nullable
    private UUID lastAttacker;
    private long lastAttackTime;
    /** Game time her last crew member left her; -1 while she has a crew. */
    private long abandonedSince = -1;
    private int sinkTicks;

    // ---- runtime ----
    @Nullable
    private UUID helmsman;
    /** Liveries: paint on the hull's planking and the colour of the sails (white: bare wood, plain canvas). */
    private int hullColor = 0xFFFFFF;
    private int sailColor = 0xFFFFFF;
    /** Oars: W/S, -1..1. */
    private float rowInput;
    /** Rowboat tied up at another ship's ladder (dockAt in that ship's plot; dockHook is ours). */
    @Nullable
    private UUID dockShip;
    private BlockPos dockAt;
    private BlockPos dockHook;
    /** Client: where the oars are in their stroke (radians). */
    public float oarPhase;
    private double dockGap;
    public float shownRow;
    private float steerInput;
    private final List<BlockPos> holes = new ArrayList<>();
    private final List<BlockPos> fires = new ArrayList<>();
    /** Ticks left on a ballast curse: she sits heavy and slow. */
    private int ballastTicks;
    /** Each mast's yard block, by mast index (server; found by scanning). */
    private final List<BlockPos> yards = new ArrayList<>();
    private int scanTimer;
    private float leak;
    private double speed;
    private int shownWater = -1;
    private boolean syncDirty = true;
    private int syncCooldown;
    private double trimX;
    /** Water drag the helm has measured (per second), along the keel and in yaw; see learnDrag. */
    private double dragEst = Double.NaN;
    private double yawDragEst = Double.NaN;
    private double expectSpeed = Double.NaN;
    private double expectYaw = Double.NaN;
    private double lastYawRate;
    private double speedErrInt;
    private double targetSpeed;
    private double yawErrInt;
    /** The turn rate the helm is asking for, eased toward the wheel's (see yawControl). */
    private double yawDesired;
    /** Whether her stats have been read fresh from her layout since the world loaded. */
    private boolean statsFresh;
    private double trimZ;
    /** Keeps the sea out of the hold: Sable hides water (and stops swimming) inside this region. Not saved. */
    @Nullable
    private dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionRegion dryHold;
    /** The hold's exact shape, as boxes (x0 y0 z0 x1 y1 z1 in plot space): the client masks the sea with it. */
    private float[] dryMesh = new float[0];
    /** dryMesh is relative to this plot block (plot coordinates are too big for float precision). */
    private BlockPos dryMeshBase = BlockPos.ZERO;
    private boolean dryHoldRefreshed;
    /** Client only: the wheel angle the renderer is easing toward {@link #wheel}. */
    public float shownWheel;
    // ---- client: the last update from the server, and when it came ----
    private float syncSteer, syncWheelSpeed = 0.025f, syncWheel, syncRaise;
    private float syncHeaveRate;
    /** Client: a mast's full health on this ship (for drawing torn sails). */
    private int syncMastMax = 6;
    /** Client: how many turns the wheel makes from amidships to hard over (see ShipStats.wheelTurns). */
    private float syncWheelTurns = 0.5f;
    private long syncTime;
    /** Client: the wheel as this client is turning it (frame by frame). */
    public float clientWheel = Float.NaN;

    /**
     * Client: where the wheel is now (wheel position, -1..1). The helmsman's own client turns it from their keys
     * the moment they press them; everyone else's carries on from the last update at the steering the helmsman
     * was giving. Either way it's eased back toward the server's value, so it never drifts.
     */
    public float clientWheel(float partialTick, float frameTicks, float localSteer, boolean localHelmsman) {
        float steer = localHelmsman ? localSteer : this.syncSteer;
        float rate = this.syncWheelSpeed;
        if (Float.isNaN(this.clientWheel)) {
            this.clientWheel = this.wheel;
        }
        if (steer != 0) {
            this.clientWheel = Mth.clamp(this.clientWheel + steer * rate * frameTicks, -1f, 1f);
        } else if (this.rowboat()) {
            this.clientWheel = Math.abs(this.clientWheel) <= rate * frameTicks ? 0 : this.clientWheel - Math.signum(this.clientWheel) * rate * frameTicks;
        }
        float age = this.level == null ? 0 : this.level.getGameTime() + partialTick - this.syncTime;
        float server = this.rowboat() && this.syncSteer == 0
                ? Math.signum(this.syncWheel) * Math.max(0, Math.abs(this.syncWheel) - rate * age)
                : Mth.clamp(this.syncWheel + this.syncSteer * rate * age, -1f, 1f);
        // the helmsman trusts their own hands; onlookers follow the server a little more closely
        this.clientWheel += (server - this.clientWheel) * Math.min(1f, (localHelmsman ? 0.04f : 0.15f) * frameTicks);
        return this.clientWheel;
    }

    /** Client: the anchor's heave now (0..100), carried on from the last update as the crew heave or let go. */
    public float clientRaise(float partialTick) {
        float age = this.level == null ? 0 : Math.min(10, this.level.getGameTime() + partialTick - this.syncTime);
        float r = this.syncRaise;
        if (this.heaveDir > 0) {
            r += this.syncHeaveRate * age;
        } else if (this.heaveDir < 0) {
            r -= (float) HEAVE_FALL * age;
        }
        return Mth.clamp(r, 0, ANCHOR_RAISE_MAX);
    }

    public HelmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HELM.get(), pos, state);
    }

    /** Called once by ShipBuilder after the ship is assembled. Positions are in plot space. */
    public void initShip(String layout, ShipStats stats, int masts, BlockPos respawn, List<BlockPos> bilge, List<BlockPos> dry,
                         BlockPos dryMeshBase, float[] dryMesh, List<BlockPos> respawns, List<BlockPos> brig) {
        this.respawns.clear();
        this.respawns.addAll(respawns);
        this.brig.clear();
        this.brig.addAll(brig);
        this.dryMeshBase = dryMeshBase;
        this.dryMesh = dryMesh;
        this.dry.clear();
        this.dry.addAll(dry);
        this.layout = layout;
        this.stats = stats;
        this.sails.clear();
        for (int i = 0; i < Math.max(1, masts); i++) {
            this.sails.add(new Sail(this.mastHealthMax()));
        }
        this.respawn = respawn;
        this.bilge.clear();
        this.bilge.addAll(bilge);
        this.releaseHold();
        this.keepHoldDry();
        this.setChanged();
        this.markSync();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.keepHoldDry();
    }

    @Override
    public void setRemoved() {
        this.releaseHold();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        this.releaseHold();
        super.onChunkUnloaded();
    }

    /** Registers the hold as a water-occluded region on this side (server and client each keep their own). */
    private void keepHoldDry() {
        List<BlockPos> cells = this.dry.isEmpty() ? this.bilge : this.dry;
        if (this.dryHold != null || this.level == null || cells.isEmpty()) {
            return;
        }
        var container = dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer.getContainer(this.level);
        if (container != null) {
            this.dryHold = container.addRegion(dev.ryanhcode.sable.util.BoundedBitVolume3i.fromBlocks(cells));
        }
    }

    /**
     * Client: registers the dry region again the first time the helm is drawn. Sable's water renderer forgets
     * every region the first frame it sees a new world, which can come after this ship's chunks loaded (the
     * region then still stops swimming, but the sea is drawn inside the hull).
     */
    @Nullable
    public dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionRegion dryHoldRegion() {
        return this.dryHold;
    }

    public float[] dryMesh() {
        return this.dryMesh;
    }

    /** Whether a plot block is inside her dry hold (below the waterline, where the sea is kept out). */
    public boolean isDry(BlockPos plotPos) {
        List<BlockPos> cells = this.dry.isEmpty() ? this.bilge : this.dry;
        if (this.drySet == null || this.drySetSize != cells.size()) {
            this.drySet = new java.util.HashSet<>(cells);
            this.drySetSize = cells.size();
        }
        return this.drySet.contains(plotPos);
    }

    public BlockPos dryMeshBase() {
        return this.dryMeshBase;
    }

    public void refreshDryHoldOnce() {
        if (!this.dryHoldRefreshed) {
            this.dryHoldRefreshed = true;
            this.releaseHold();
            this.keepHoldDry();
        }
    }

    private void releaseHold() {
        if (this.dryHold != null && this.level != null) {
            var container = dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer.getContainer(this.level);
            if (container != null) {
                container.removeRegion(this.dryHold);
            }
        }
        this.dryHold = null;
    }

    private Direction bow() {
        return this.getBlockState().getValue(HelmBlock.FACING);
    }

    // =====================================================================
    // Physics (called by Sable, possibly several times per game tick)
    // =====================================================================

    @Override
    public void sable$physicsTick(ServerSubLevel ship, RigidBodyHandle handle, double dt) {
        if (!(this.level instanceof ServerLevel level)) {
            return;
        }
        this.physicsTicks++;
        ShipStats s = this.stats;
        Pose3dc pose = ship.logicalPose();
        Direction bow = this.bow();
        Vector3d fwd = pose.transformNormal(new Vector3d(bow.getStepX(), 0, bow.getStepZ()));
        fwd.y = 0;
        if (fwd.lengthSquared() < 1e-6) {
            return; // on her side
        }
        fwd.normalize();

        Vector3d vel = handle.getLinearVelocity(new Vector3d());
        Vector3d angVel = handle.getAngularVelocity(new Vector3d());
        double forwardSpeed = vel.dot(fwd);
        Vector3d lateral = new Vector3d(vel.x, 0, vel.z).fma(-forwardSpeed, fwd);
        this.speed = forwardSpeed;
        this.learnDrag(forwardSpeed, angVel.y, dt);
        this.lastForward = forwardSpeed;

        Vector3d dv = new Vector3d();
        if (this.sinking) {
            // past saving: she goes down whatever the crew does
            dv.y = -s.sinkSpeed - vel.y;
            dv.fma(-Math.min(1, 0.8 * dt), new Vector3d(vel.x, 0, vel.z));
            handle.addLinearAndAngularVelocity(dv, this.righting(pose, angVel, dt));
            return;
        }

        double dYaw;
        if (this.anchored) {
            // the anchor falls, bites, and drags her to a stop; with the wheel over she pivots round it
            double bite = Math.min(1, this.anchorTicks / (double) ANCHOR_FALL_TICKS);
            this.speedErrInt = 0;
            dv.fma((-forwardSpeed * 1.6 * bite + this.dragComp() * forwardSpeed) * dt, fwd);
            double pivot = Math.min(1.2, this.pivotEnergy / 5.0) * bite;
            double targetYaw = -this.wheel * (s.pivotRate * dev.seafarers.SeafarersConfig.TURNING_MULTIPLIER.get() * pivot + 0.15);
            dYaw = this.yawControl(targetYaw, angVel.y, dt, 6);   // the anchor biting swings her round hard
        } else {
            // water aboard makes her sluggish: half speed when she is nearly full
            double targetSpeed = s.maxSpeed * dev.seafarers.SeafarersConfig.SPEED_MULTIPLIER.get() * this.drive(level, fwd) * (1 - 0.5 * this.flood / FLOOD_MAX)
                    * (this.ballastTicks > 0 ? 0.6 : 1);
            this.targetSpeed = targetSpeed;
            double rate = forwardSpeed < targetSpeed || this.rowboat() && targetSpeed < 0 ? s.acceleration : s.coast;
            // integral term: trims out whatever steady error the drag estimate leaves (clamped, so it can't wind up)
            if (targetSpeed > 0.5) {
                this.speedErrInt = Mth.clamp(this.speedErrInt + (targetSpeed - forwardSpeed) * dt, -15, 15);
            } else {
                this.speedErrInt *= Math.max(0, 1 - 3 * dt); // no sail drawing: just coast
            }
            double trim = forwardSpeed > targetSpeed * 1.15 + 0.5 ? 0 : 0.4 * this.speedErrInt;
            dv.fma(((targetSpeed - forwardSpeed) * rate + this.dragComp() * forwardSpeed + trim) * dt, fwd);
            // rudder: it needs water flowing past it, so it bites harder at speed
            // (oars turn her just as well standing still, pulling one side and backing the other)
            double authority = this.rowboat() ? 1.0 : Mth.clamp(Math.abs(forwardSpeed) / 3.5, 0.3, 1.0);
            double targetYaw = -this.wheel * s.maxYawRate * dev.seafarers.SeafarersConfig.TURNING_MULTIPLIER.get() * authority * (forwardSpeed < -0.5 && !this.rowboat() ? -1 : 1);
            dYaw = this.yawControl(targetYaw, angVel.y, dt, 1);
        }
        if (this.dockShip != null) {
            Vector3d pull = this.dockPull(level, pose, fwd, vel, angVel, dt);
            if (pull != null) {
                // tied up alongside: the hook line holds her to the ladder and she lies along the other hull
                dv.set(pull.x, 0, pull.z);
                // cancel the water's drag (the usual guard against outrunning the oars doesn't apply: the tow line
                // sets her speed, and its pull is capped)
                dv.fma(0.97 * this.dragEst * forwardSpeed * dt, fwd);
                dYaw = pull.y;
                this.speedErrInt = 0;
                lateral.zero();
            }
        }
        dv.fma(-Math.min(1, s.keelGrip * dt), lateral);
        this.expectSpeed = forwardSpeed + dv.dot(fwd);
        this.expectYaw = angVel.y + dYaw;
        // water aboard: the more she ships, the lower she sits
        dv.y += (s.liftTrim - s.floodSink * (this.flood / FLOOD_MAX) - (this.ballastTicks > 0 ? 3 : 0)) * dt;

        Vector3d right = this.righting(pose, angVel, dt);
        if (this.trace > 0) {
            this.trace--;
            Seafarers.LOGGER.info("PROBE trace dt={} yawRate={} dYaw={} wheel={} speed={} angX={} angZ={} rightY={}", String.format("%.4f", dt),
                    String.format("%.4f", angVel.y), String.format("%.4f", dYaw), String.format("%.3f", this.wheel), String.format("%.2f", forwardSpeed),
                    String.format("%.4f", angVel.x), String.format("%.4f", angVel.z), String.format("%.4f", right.y));
        }
        handle.addLinearAndAngularVelocity(dv, right.add(0, dYaw, 0));
    }

    /**
     * Sable's water drag on the hull depends on how deep she sits, how much water she has shipped and the hull
     * itself, so instead of a fixed number the helm measures it: each physics step, whatever the speed lost
     * beyond our own push is the water's doing. The estimate is smoothed, 97% of it is cancelled, and nothing is
     * cancelled while she goes faster than her top speed or turns faster than her top turn rate, so a bad
     * estimate can never run away with the ship.
     */
    private void learnDrag(double forwardSpeed, double yawRate, double dt) {
        this.lastYawRate = yawRate;
        if (Double.isNaN(this.dragEst)) {
            this.dragEst = this.stats.waterDrag;
            this.yawDragEst = this.stats.waterYawDrag;
        }
        if (dt > 0 && !Double.isNaN(this.expectSpeed)) {
            // only learn the keel's drag going straight: in a turn, speed bleeds off as the bow swings
            if (Math.abs(this.expectSpeed) > 0.5 && Math.abs(yawRate) < 0.04) {
                double k = (this.expectSpeed - forwardSpeed) / (this.expectSpeed * dt);
                this.dragEst += (Mth.clamp(k, 0, 25) - this.dragEst) * 0.02;
            }

        }
    }

    private double dragComp() {
        // safety: never push a ship that is already going faster than her sails should drive her
        return Math.abs(this.speed) > Math.max(this.targetSpeed * 1.15 + 0.5, 1.0) ? 0 : 0.97 * this.dragEst;
    }

    /**
     * Change in yaw rate this step. The helm's own turn rate (yawDesired) moves toward the wheel's target at the
     * ship's yawAccel, so a big ship takes a few seconds to come round and as long to straighten up: she feels
     * heavy. Each step the hull's real turn rate is pulled firmly onto it: part of the gap, the water's drag at
     * that rate fed forward (so nothing feeds back on itself and swings), and a slow integral that holds her
     * course against steady pushes (the sail's pull off-centre, an uneven hull), so she sails straight with the
     * wheel amidships.
     */
    private double yawControl(double targetYaw, double yawRate, double dt, double accelScale) {
        double accel = this.stats.yawAccel * accelScale * dev.seafarers.SeafarersConfig.TURNING_MULTIPLIER.get();
        this.yawDesired += Mth.clamp(targetYaw - this.yawDesired, -accel * dt, accel * dt);
        double err = this.yawDesired - yawRate;
        this.yawErrInt = Mth.clamp(this.yawErrInt + err * dt, -0.3, 0.3);
        return 0.35 * err + 0.8 * this.stats.waterYawDrag * this.yawDesired * dt + 2.0 * this.yawErrInt * dt;
    }

    /** Angular velocity change that rolls and pitches the ship back toward upright. */
    private Vector3d righting(Pose3dc pose, Vector3d angVel, double dt) {
        ShipStats s = this.stats;
        Vector3d up = pose.transformNormal(new Vector3d(0, 1, 0));
        // rotating up toward world up happens about up x Y, by an amount growing with the tilt
        Vector3d axis = up.cross(0, 1, 0, new Vector3d());
        // water in the hold runs forward and she goes down by the head: up to 4 degrees bow-down when full
        if (!this.sinking && this.flood > 0) {
            Direction bow = this.bow();
            Vector3d starboard = pose.transformNormal(new Vector3d(bow.getClockWise().getStepX(), 0, bow.getClockWise().getStepZ()));
            axis.fma(-Math.toRadians(4) * this.flood / FLOOD_MAX, starboard);
        }
        // a slow trim term removes any steady list from uneven loading (stores on one side)
        this.trimX = Mth.clamp(this.trimX + axis.x * s.rightingTrim * dt, -0.6, 0.6);
        this.trimZ = Mth.clamp(this.trimZ + axis.z * s.rightingTrim * dt, -0.6, 0.6);
        return new Vector3d(
                ((axis.x + this.trimX) * s.righting - angVel.x * s.rightingDamp) * dt,
                0,
                ((axis.z + this.trimZ) * s.righting - angVel.z * s.rightingDamp) * dt);
    }

    /** How much of her top speed the sails give, 0..1: each mast's share, by how far it is set and how well it draws. */
    private double drive(ServerLevel level, Vector3d fwd) {
        if (this.rowboat()) {
            return this.rowInput >= 0 ? this.rowInput : this.rowInput * 0.5; // backing water is slower
        }
        double total = 0;
        for (Sail sail : this.sails) {
            double eff = this.sailEfficiency(level, fwd, sail)
                    * (0.5 + 0.5 * Math.min(1.0, sail.health / (double) this.mastHealthMax())); // torn canvas spills wind
            total += eff * sail.deploy;
        }
        return total / this.sails.size();
    }

    /**
     * How much of the wind one sail turns into drive, 0..1. Depends on the point of sail (where the wind comes
     * from relative to the bow) and on trim (how close the sail angle is to the best one for that wind).
     */
    private double sailEfficiency(ServerLevel level, Vector3d fwd, Sail sail) {
        if (sail.health <= 0) {
            sail.fill = 0;
            return 0;
        }
        Vec3 wind = Wind.get(level).direction();
        double windAngle = signedAngleToStarboard(fwd.x, fwd.z, wind.x, wind.z);
        double off = Math.abs(windAngle); // 0 = wind straight behind, 180 = straight into it
        double best;
        ShipStats st = this.stats;
        if (off <= 90) {
            best = Mth.lerp(off / 90, st.sailRun, st.sailBeam);
        } else if (off <= 135) {
            best = Mth.lerp((off - 90) / 45, st.sailBeam, st.sailClose);
        } else {
            best = Mth.lerp((off - 135) / 45, st.sailClose, st.sailIrons);
        }
        // the best sail angle splits the difference between the keel and the wind
        double ideal = Mth.clamp(windAngle / 2, -MAX_SAIL_ANGLE, MAX_SAIL_ANGLE);
        double trim = Math.max(0, Math.cos(Math.toRadians(sail.angle - ideal) * 1.5)); // 45 degrees off keeps about 40%
        sail.fill = (float) (best * (0.25 + 0.75 * trim));
        return best * (this.stats.untrimmed + (1 - this.stats.untrimmed) * trim);
    }

    /**
     * Angle in degrees from direction a to direction b on the water, positive when b is clockwise of a seen
     * from above (toward starboard of a ship heading a). Minecraft: +x east, +z south.
     */
    public static double signedAngleToStarboard(double ax, double az, double bx, double bz) {
        return Math.toDegrees(Math.atan2(ax * bz - az * bx, ax * bx + az * bz));
    }

    // =====================================================================
    // Game tick (once per server tick while the ship is loaded)
    // =====================================================================

    @Override
    public void sable$tick(ServerSubLevel ship) {
        if (!(this.level instanceof ServerLevel level)) {
            return;
        }
        this.gameTicks++;
        if (!this.statsFresh) {
            // her handling comes from the layout file, so a ship saved before a change to it sails as she does now
            this.statsFresh = true;
            ShipLayout layout = this.layout.isEmpty() ? null : ShipLayout.load(level.getServer(), this.layout);
            if (layout != null) {
                this.stats = layout.stats();
            }
        }
        if (--this.scanTimer <= 0) {
            this.scanTimer = 20;
            this.scan(level, ship);
        }
        this.tickHelmsman(level);
        // a sudden stop at speed: she's hit something. Running aground takes a few physics steps, so this compares
        // with the fastest she went in the last half second rather than step by step (see crash for what she hit)
        double now = Math.abs(this.speed), peak = Math.abs(this.peakForward);
        if (now >= peak || ++this.peakAge > 10) {
            this.peakForward = this.speed;
            this.peakAge = 0;
        } else if (peak >= dev.seafarers.SeafarersConfig.CRASH_SPEED.get() && now < 0.4 * peak && peak - now > 2) {
            double heading = this.peakForward;
            this.peakForward = this.speed;
            this.peakAge = 0;
            if (this.crashCooldown == 0 && dev.seafarers.SeafarersConfig.CRASH_DAMAGE.get() && !this.rowboat()) {
                this.crash(level, ship, peak, heading < 0);
            }
        }
        if (this.rowboat() && this.steerInput == 0 && this.wheel != 0) {
            this.wheel = Math.abs(this.wheel) <= this.stats.wheelSpeed ? 0 : this.wheel - Math.signum(this.wheel) * (float) this.stats.wheelSpeed;
            this.markSync();
        }
        if (this.steerInput != 0) {
            float before = this.wheel;
            this.wheel = Mth.clamp(this.wheel + this.steerInput * (float) this.stats.wheelSpeed, -1f, 1f);
            if (before != this.wheel) {
                if (level.getGameTime() % 4 == 0 && !this.rowboat()) {
                    this.sound(ModSounds.WHEEL_TURN.get(), 0.4f, 0.95f + this.wheel * 0.05f);
                }
                this.markSync();
            }
        }
        if (this.ballastTicks > 0) {
            this.ballastTicks--;
        }
        this.tickCapstan(level);
        this.tickSails();
        if (this.rowboat() && (this.rowInput != 0 || this.steerInput != 0) && level.getGameTime() % 22 == 0) {
            this.sound(net.minecraft.sounds.SoundEvents.BOAT_PADDLE_WATER, 0.6f, 0.9f + level.random.nextFloat() * 0.2f);
        }
        if (this.anchored) {
            this.anchorTicks++;
            this.pivotEnergy *= 0.97f;
            if (this.anchorTicks == ANCHOR_FALL_TICKS) {
                this.sound(ModSounds.ANCHOR_BITE.get(), 1f, 1f);
                if (this.helmsman != null && Math.abs(this.wheel) > 0.6f && this.pivotEnergy > 3) {
                    dev.seafarers.ship.Feats.award(level.getServer().getPlayerList().getPlayer(this.helmsman), "anchor_turn");
                }
            }
        }
        // Sable calls this while iterating the ship's block entities, and every block change in the plot edits that
        // list: anything that places blocks or removes the ship runs on the server thread right after the tick
        // (tell() always queues; execute() would run it right here when called on the server thread)
        level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount(), () -> {
            if (!this.isRemoved() && !ship.isRemoved()) {
                this.tickFlooding(level, ship);
                this.showBilge(level);
                if (!this.fires.isEmpty() && level.getGameTime() % 20 == 0) {
                    ShipFire.tick(level, this, List.copyOf(this.fires));
                    this.forceRescan();
                }
            }
        }));
        if (level.getGameTime() % 5 == 0) {
            this.sendHud(level, ship);
        }
        if (level.getGameTime() % 20 == 0) {
            this.markSync(); // keeps the sails' fill fresh on clients
            this.checkAbandoned(level, ship);
            if (this.crashCooldown > 0) {
                this.crashCooldown--;
            }
            if (this.helmsman != null && !this.rowboat()
                    && Math.abs(this.speed) >= 0.9 * this.stats.maxSpeed * dev.seafarers.SeafarersConfig.SPEED_MULTIPLIER.get()) {
                dev.seafarers.ship.Feats.award(level.getServer().getPlayerList().getPlayer(this.helmsman), "full_sail");
            }
        }
        if (this.syncDirty && --this.syncCooldown <= 0) {
            this.syncDirty = false;
            this.syncCooldown = 3;
            level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Re-reads the ship: where the holes and the yards are. Crew can rebuild the ship at any time. */
    private void scan(ServerLevel level, ServerSubLevel ship) {
        this.holes.clear();
        this.fires.clear();
        this.yards.clear();
        BoundingBox3ic b = ship.getPlot().getBoundingBox();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int y = b.minY(); y <= b.maxY(); y++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    BlockState state = level.getBlockState(p.set(x, y, z));
                    if (state.is(ModBlocks.SHIP_FIRE.get())) {
                        this.fires.add(p.immutable());
                    } else if (HullDamage.isHole(state)) {
                        this.holes.add(p.immutable());
                    } else if (state.is(ModBlocks.SAIL_YARD.get())) {
                        int mast = state.getValue(dev.seafarers.ship.block.SailYardBlock.MAST);
                        while (this.yards.size() <= mast) {
                            this.yards.add(null);
                        }
                        this.yards.set(mast, p.immutable());
                    }
                }
            }
        }
        this.leak = 0;
        for (BlockPos hole : this.holes) {
            float size = HullDamage.holeSize(level.getBlockState(hole));
            Vec3 world = ship.logicalPose().transformPosition(Vec3.atCenterOf(hole));
            // only a hole under the water lets the sea in (as in Sea of Thieves, damage to the upper hull doesn't
            // flood her); a hole above the waterline starts leaking if she settles low enough to put it under
            if (level.getFluidState(BlockPos.containing(world)).is(FluidTags.WATER)) {
                this.leak += size;
            }
        }
    }

    private void tickHelmsman(ServerLevel level) {
        if (this.helmsman == null) {
            return;
        }
        if (TEST_HANDS.contains(this.helmsman)) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(this.helmsman);
        if (player == null || !player.isAlive() || player.level() != level || player.position().distanceTo(this.worldPos()) > 4.0) {
            this.releaseHelm(player);
        }
    }

    private void tickFlooding(ServerLevel level, ServerSubLevel ship) {
        if (this.leak > 0 && !this.sinking) {
            // once the water is over her lower deck it pours into the next one faster (the Galleon)
            double rush = this.flood >= this.stats.floodRushAbove * FLOOD_MAX ? this.stats.floodRush : 1;
            this.flood = (float) Math.min(FLOOD_MAX, this.flood + this.leak * this.stats.floodPerHole * rush * dev.seafarers.SeafarersConfig.FLOODING_MULTIPLIER.get());
            this.setChanged();
            if (level.getGameTime() % 8 == 0) {
                for (BlockPos hole : this.holes) {
                    Vec3 w = ship.logicalPose().transformPosition(Vec3.atCenterOf(hole));
                    level.sendParticles(ParticleTypes.SPLASH, w.x, w.y + 0.3, w.z, 10, 0.3, 0.3, 0.3, 0.15);
                    level.sendParticles(ParticleTypes.FALLING_WATER, w.x, w.y, w.z, 4, 0.3, 0.3, 0.3, 0);
                }
            }
            if (this.flood >= FLOOD_MAX) {
                this.startSinking(level, ship);
            }
        }
        if (this.sinking) {
            this.sinkTicks++;
            Vec3 center = ship.logicalPose().transformPosition(Vec3.atCenterOf(this.getBlockPos()));
            if (center.y < level.getSeaLevel() - 16 || this.sinkTicks > dev.seafarers.SeafarersConfig.SECONDS_TO_SINK.get() * 20) {
                this.wreck(level, ship);
            }
        }
    }

    /**
     * A ship her whole crew has left goes down after the config's abandonedShipMinutes. If that time ran out while
     * she was unloaded (nobody near), she's simply gone when she's next loaded rather than sinking in front of
     * whoever came by.
     */
    private void checkAbandoned(ServerLevel level, ServerSubLevel ship) {
        int minutes = dev.seafarers.SeafarersConfig.ABANDONED_SHIP_MINUTES.get();
        if (this.sinking || this.rowboat()) {
            return;
        }
        boolean crewed = !Crews.get(level).crewOf(ship.getUniqueId()).isEmpty();
        if (crewed) {
            if (!this.owned || this.abandonedSince >= 0) {
                this.owned = true;
                this.abandonedSince = -1;
                this.setChanged();
            }
            return;
        }
        if (!this.owned || minutes <= 0) {
            return;
        }
        long now = level.getGameTime();
        if (this.abandonedSince < 0) {
            this.abandonedSince = now;
            this.setChanged();
            Seafarers.LOGGER.info("PROBE abandoned {}", ship.getUniqueId());
            return;
        }
        long over = now - this.abandonedSince - minutes * 1200L;
        if (over < 0) {
            return;
        }
        if (over > 40) {
            Seafarers.LOGGER.info("PROBE abandoned ship removed {}", ship.getUniqueId());
            Crews.get(level).forgetShip(ship.getUniqueId());
            SubLevelContainer.getContainer(level).removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
        } else {
            Seafarers.LOGGER.info("PROBE abandoned ship sinking {}", ship.getUniqueId());
            this.startSinking(level, ship);
        }
    }

    /**
     * She hit something hard at speed. If any of her is pressed against land (not water, not another ship), the hull
     * is holed where she struck: the hull blocks against the rock, or if only her bowsprit or rail touched, the hull
     * nearest the impact. 1 hole at the crash speed, up to 3 going flat out.
     */
    private void crash(ServerLevel level, ServerSubLevel ship, double speed, boolean astern) {
        Vec3 heading = Ships.toWorldNormal(level, this.getBlockPos(), Vec3.atLowerCornerOf(this.bow().getNormal()));
        if (astern) {
            heading = heading.reverse();
        }
        List<BlockPos> hull = new ArrayList<>();
        List<Double> score = new ArrayList<>();
        Vec3 impact = null;
        double impactAlong = -Double.MAX_VALUE;
        BoundingBox3ic b = ship.getPlot().getBoundingBox();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        double[][] dirs = {{0.8, 0, 0}, {-0.8, 0, 0}, {0, 0.8, 0}, {0, -0.8, 0}, {0, 0, 0.8}, {0, 0, -0.8}};
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int y = b.minY(); y <= b.maxY(); y++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    BlockState state = level.getBlockState(p.set(x, y, z));
                    if (state.isAir()) {
                        continue;
                    }
                    boolean isHull = state.is(ModBlocks.VOXEL_HULL.get()) || state.is(SeafarersTags.HULL);
                    Vec3 w = Ships.toWorld(level, Vec3.atCenterOf(p));
                    for (double[] d : dirs) {
                        BlockState there = level.getBlockState(BlockPos.containing(w.x + d[0], w.y + d[1], w.z + d[2]));
                        if (!there.isAir() && there.getFluidState().isEmpty() && there.blocksMotion()) {
                            if (w.dot(heading) > impactAlong) {
                                impactAlong = w.dot(heading);
                                impact = w;
                            }
                            break;
                        }
                    }
                    if (isHull) {
                        hull.add(p.immutable());
                        score.add(0.0); // scored below, once the impact is known
                    }
                }
            }
        }
        if (impact == null || hull.isEmpty()) {
            return; // something else stopped her (another ship, the anchor): no holes
        }
        // the hull blocks nearest where she struck go first
        for (int i = 0; i < hull.size(); i++) {
            score.set(i, Ships.toWorld(level, Vec3.atCenterOf(hull.get(i))).distanceToSqr(impact));
        }
        int holes = (int) Mth.clamp(Math.floor(1 + (speed - dev.seafarers.SeafarersConfig.CRASH_SPEED.get()) / 2.0), 1, 3);
        Integer[] order = new Integer[hull.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (i, j) -> Double.compare(score.get(i), score.get(j)));
        int made = 0;
        for (int i = 0; i < order.length && made < holes; i++) {
            BlockPos at = hull.get(order[i]);
            if (HullDamage.punch(level, at, level.getBlockState(at))) {
                made++;
                level.playSound(null, BlockPos.containing(Ships.toWorld(level, Vec3.atCenterOf(at))), ModSounds.CANNON_HIT_HULL.get(),
                        net.minecraft.sounds.SoundSource.BLOCKS, 1.4f, 0.7f);
            }
        }
        if (made > 0) {
            this.crashCooldown = 40; // grinding on against the rock after the hit doesn't hole her again every tick
            this.forceRescan();
            this.crewMessage(level, ship, Component.translatable("message.seafarers.crashed").withStyle(ChatFormatting.RED));
        }
        Seafarers.LOGGER.info("PROBE crash at {} m/s: struck at {},{},{}, {} holes", String.format("%.1f", speed),
                (int) impact.x, (int) impact.y, (int) impact.z, made);
    }

    /** A cannonball from another ship's gunner hit her. */
    public void hitBy(ServerPlayer gunner) {
        this.lastAttacker = gunner.getUUID();
        this.lastAttackTime = gunner.level().getGameTime();
    }

    /** She's someone's ship from the moment she's launched for them (see checkAbandoned). */
    public void markOwned() {
        this.owned = true;
        this.setChanged();
    }

    private void startSinking(ServerLevel level, ServerSubLevel ship) {
        this.sinking = true;
        this.anchored = false;
        this.releaseHelm(this.helmsman == null ? null : level.getServer().getPlayerList().getPlayer(this.helmsman));
        this.crewMessage(level, ship, Component.translatable("message.seafarers.going_down").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        this.sound(ModSounds.SHIP_SINKING.get(), 3f, 1f);
        Seafarers.LOGGER.info("PROBE sinking {}", ship.getUniqueId());
        // keep her loaded until she is wrecked, even with nobody near, so her crew still gets a new ship
        SubLevelContainer.getContainer(level).addForceLoadTicket(ship, ShipTickets.SINKING, net.minecraft.util.Unit.INSTANCE);
        Vec3 centre = this.worldPos();
        for (UUID id : Crews.get(level).crewOf(ship.getUniqueId())) {
            ServerPlayer member = level.getServer().getPlayerList().getPlayer(id);
            if (member != null && member.level() == level && member.position().distanceTo(centre) < 48) {
                dev.seafarers.ship.Feats.award(member, "went_down");
            }
        }
        // the planks darken as the water takes her
        BoundingBox3ic b = ship.getPlot().getBoundingBox();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int y = b.minY(); y <= b.maxY(); y++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    p.set(x, y, z);
                    HullDamage.flood(level, p.immutable(), level.getBlockState(p));
                }
            }
        }
        this.markSync();
    }

    /** Shows the water aboard as bilge water rising through the lower deck. */
    private void showBilge(ServerLevel level) {
        if (this.bilge.isEmpty()) {
            return;
        }
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos p : this.bilge) {
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
        }
        float fraction = this.sinking ? 1f : this.flood / FLOOD_MAX;
        int eighths = Math.round(fraction * (maxY - minY + 1) * 8);
        if (eighths == this.shownWater) {
            return;
        }
        this.shownWater = eighths;
        for (BlockPos p : this.bilge) {
            int here = Mth.clamp(eighths - (p.getY() - minY) * 8, 0, 8);
            BlockState current = level.getBlockState(p);
            boolean isBilge = current.is(ModBlocks.BILGE_WATER.get());
            if (!current.isAir() && !isBilge) {
                continue; // a barrel or stair the crew put there
            }
            if (here == 0) {
                if (isBilge) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            } else if (!isBilge || current.getValue(BilgeWaterBlock.LEVEL) != here) {
                level.setBlock(p, ModBlocks.BILGE_WATER.get().defaultBlockState().setValue(BilgeWaterBlock.LEVEL, here), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** The ship is lost. If she had a crew, a new ship of the same kind is waiting for them nearby. */
    private void wreck(ServerLevel level, ServerSubLevel ship) {
        UUID oldId = ship.getUniqueId();
        Vector3dc p = ship.logicalPose().position();
        BlockPos where = BlockPos.containing(p.x(), p.y(), p.z());
        Crews crews = Crews.get(level);
        List<UUID> crew = crews.crewOf(oldId);
        if (this.lastAttacker != null && level.getGameTime() - this.lastAttackTime < 20 * 120 && !crew.contains(this.lastAttacker)) {
            dev.seafarers.ship.Feats.award(level.getServer().getPlayerList().getPlayer(this.lastAttacker), "sink_enemy");
        }
        this.crewMessage(level, ship, Component.translatable("message.seafarers.sunk").withStyle(ChatFormatting.DARK_RED));
        Seafarers.LOGGER.info("PROBE wreck {} {} crew={}", ship.getName(), oldId, crew.size());
        this.floatStores(level, ship);
        Brig.forget(oldId);   // votes about her crew start again on the new ship
        SubLevelContainer.getContainer(level).removeSubLevel(ship, SubLevelRemovalReason.REMOVED);

        if (crew.isEmpty()) {
            crews.forgetShip(oldId);
            return;
        }
        BlockPos spot = null;
        ServerSubLevel replacement = null;
        // a few tries: open water can still have another ship in it
        for (int attempt = 0; attempt < 5 && replacement == null && dev.seafarers.SeafarersConfig.REPLACE_SUNK_SHIPS.get(); attempt++) {
            spot = findOpenWater(level, where);
            replacement = spot == null ? null
                    : ShipBuilder.launch(level, this.layout, spot, Direction.Plane.HORIZONTAL.getRandomDirection(level.random));
        }
        if (replacement == null) {
            if (dev.seafarers.SeafarersConfig.REPLACE_SUNK_SHIPS.get()) {
                Seafarers.LOGGER.warn("No open water for a replacement {}", this.layout);
            }
            // lost for good: her captain may choose a new ship straight away
            UUID captain = crews.captainOf(oldId);
            crews.forgetShip(oldId);
            if (captain != null) {
                dev.seafarers.ship.deed.Sailors.get(level.getServer()).clearCooldown(captain);
            }
            for (UUID id : crew) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
                if (player != null) {
                    player.sendSystemMessage(Component.translatable(id.equals(captain) ? "message.seafarers.lost_for_good_captain"
                            : "message.seafarers.lost_for_good").withStyle(ChatFormatting.GOLD));
                }
            }
            return;
        }
        crews.reassign(oldId, replacement.getUniqueId());
        HelmBlockEntity newHelm = Ships.helmOf(replacement);
        if (newHelm != null) {
            newHelm.markOwned();
        }
        int dist = (int) Math.sqrt(spot.distSqr(where));
        Seafarers.LOGGER.info("PROBE replacement {} at {} ({} blocks away)", replacement.getUniqueId(), spot.toShortString(), dist);
        for (UUID id : crew) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.seafarers.new_ship", dist, spot.getX(), spot.getZ())
                        .withStyle(ChatFormatting.AQUA));
            }
        }
    }

    /** What was in her barrels, chests and casks floats up in shipwreck barrels where she went down (config). */
    private void floatStores(ServerLevel level, ServerSubLevel ship) {
        if (!dev.seafarers.SeafarersConfig.SHIPWRECK_LOOT.get()) {
            return;
        }
        List<net.minecraft.world.item.ItemStack> loot = new java.util.ArrayList<>();
        BoundingBox3ic b = ship.getPlot().getBoundingBox();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int y = b.minY(); y <= b.maxY(); y++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    if (level.getBlockEntity(p.set(x, y, z)) instanceof net.minecraft.world.Container c) {
                        for (int i = 0; i < c.getContainerSize(); i++) {
                            if (!c.getItem(i).isEmpty()) {
                                loot.add(c.removeItemNoUpdate(i));
                            }
                        }
                    }
                }
            }
        }
        if (loot.isEmpty()) {
            return;
        }
        Vec3 at = this.worldPos();
        int barrels = 0;
        for (int i = 0; i < loot.size(); i += ShipwreckBarrelEntity.SIZE) {
            double x = at.x + (level.random.nextDouble() - 0.5) * 6, z = at.z + (level.random.nextDouble() - 0.5) * 6;
            Vec3 spot = new Vec3(x, surface(level, x, at.y, z) - 0.5, z);
            level.addFreshEntity(ShipwreckBarrelEntity.of(level, spot, loot.subList(i, Math.min(loot.size(), i + ShipwreckBarrelEntity.SIZE))));
            barrels++;
        }
        Seafarers.LOGGER.info("PROBE shipwreck loot {} stacks in {} barrels", loot.size(), barrels);
    }

    /** The top of the water at x, z near height y (where she went down), or y if there's no water there. */
    private static double surface(ServerLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos p = BlockPos.containing(x, y, z).mutable();
        for (int i = 0; i < 64 && !level.getFluidState(p).is(FluidTags.WATER); i++) {
            p.move(Direction.DOWN);   // she may already be under; the water is around her
        }
        if (!level.getFluidState(p).is(FluidTags.WATER)) {
            return y;
        }
        while (level.getFluidState(p.above()).is(FluidTags.WATER) && p.getY() < level.getMaxBuildHeight()) {
            p.move(Direction.UP);
        }
        return p.getY() + 1;
    }

    /** Looks for a patch of open sea replacementMinDistance..replacementMaxDistance blocks away (config), big enough to launch a ship in. */
    @Nullable
    public static BlockPos findOpenWater(ServerLevel level, BlockPos near) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            int min = dev.seafarers.SeafarersConfig.REPLACEMENT_MIN_BLOCKS.get(), max = Math.max(min + 1, dev.seafarers.SeafarersConfig.REPLACEMENT_MAX_BLOCKS.get());
            int dist = min + level.random.nextInt(max - min);
            int x = near.getX() + (int) (Math.cos(angle) * dist);
            int z = near.getZ() + (int) (Math.sin(angle) * dist);
            if (isOpenWater(level, x, z)) {
                return new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
            }
        }
        return null;
    }

    private static boolean isOpenWater(ServerLevel level, int cx, int cz) {
        for (int dx = -16; dx <= 16; dx += 8) {
            for (int dz = -16; dz <= 16; dz += 8) {
                if (!level.hasChunkAt(new BlockPos(cx + dx, 0, cz + dz))) {
                    level.getChunk((cx + dx) >> 4, (cz + dz) >> 4);
                }
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx + dx, cz + dz) - 1;
                BlockPos p = new BlockPos(cx + dx, top, cz + dz);
                if (!level.getFluidState(p).is(FluidTags.WATER) || !level.getFluidState(p.below(5)).is(FluidTags.WATER)) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<ServerPlayer> aboard(ServerLevel level, ServerSubLevel ship) {
        AABB box = ship.boundingBox().toMojang().inflate(3.0);
        return level.getEntitiesOfClass(ServerPlayer.class, box);
    }

    private void crewMessage(ServerLevel level, ServerSubLevel ship, Component message) {
        List<UUID> crew = Crews.get(level).crewOf(ship.getUniqueId());
        List<ServerPlayer> aboard = this.aboard(level, ship);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (crew.contains(p.getUUID()) || aboard.contains(p)) {
                p.sendSystemMessage(message);
            }
        }
    }

    /** The ship's state for the status panel of everyone aboard (see ShipHudPayload; the client draws it). */
    private void sendHud(ServerLevel level, ServerSubLevel ship) {
        CompoundTag t = new CompoundTag();
        t.putString("name", ship.getName() == null ? "" : ship.getName());
        t.putString("layout", this.layout);
        t.putFloat("speed", (float) this.speed);
        Wind wind = Wind.get(level);
        Vec3 blow = wind.direction();
        Vector3d fwd = ship.logicalPose().transformNormal(new Vector3d(this.bow().getStepX(), 0, this.bow().getStepZ()));
        t.putFloat("wind", (float) signedAngleToStarboard(fwd.x, fwd.z, blow.x, blow.z));
        t.putString("windFrom", wind.fromName());
        t.putFloat("flood", this.flood / FLOOD_MAX * 100f);
        t.putInt("holes", this.holes.size());
        t.putInt("fires", this.fires.size());
        t.putBoolean("anchored", this.anchored);
        t.putInt("raise", Math.round(this.anchorRaise));
        t.putBoolean("sinking", this.sinking);
        t.putFloat("wheel", this.wheel);
        t.putBoolean("rowboat", this.rowboat());
        t.putInt("mastMax", this.mastHealthMax());
        ListTag masts = new ListTag();
        for (Sail s : this.rowboat() ? List.<Sail>of() : this.sails) {
            CompoundTag m = new CompoundTag();
            m.putFloat("d", s.deploy);
            m.putFloat("a", s.angle);
            m.putInt("h", s.health);
            m.putFloat("f", s.fill);
            masts.add(m);
        }
        t.put("masts", masts);
        dev.seafarers.network.ShipHudPayload payload = new dev.seafarers.network.ShipHudPayload(t);
        for (ServerPlayer p : this.aboard(level, ship)) {
            PacketDistributor.sendToPlayer(p, payload);
        }
    }

    // =====================================================================
    // Crew actions (called from blocks, items and packets)
    // =====================================================================

    public void takeHelm(ServerPlayer player) {
        if (this.helmsman != null && !this.helmsman.equals(player.getUUID())
                && (player.server.getPlayerList().getPlayer(this.helmsman) != null || TEST_HANDS.contains(this.helmsman))) {
            dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.station_taken"));
            return;
        }
        this.helmsman = player.getUUID();
        this.steerInput = 0;
        this.rowInput = 0;
        if (!this.rowboat()) {
            Posts.place(player, this.getBlockPos(), this.bow(), 0.85, 8f); // behind the wheel, looking forward
        }
        PacketDistributor.sendToPlayer(player, new StationPayload(this.rowboat() ? StationPayload.OARS : StationPayload.HELM, this.getBlockPos()));
        dev.seafarers.ship.Notify.show(player, Component.translatable(this.rowboat() ? "message.seafarers.oars_hint" : "message.seafarers.helm_hint"));
    }

    public void releaseHelm(@Nullable ServerPlayer player) {
        if (player != null && player.getUUID().equals(this.helmsman)) {
            PacketDistributor.sendToPlayer(player, new StationPayload(StationPayload.NONE, this.getBlockPos()));
        }
        this.helmsman = null;
        this.steerInput = 0;
        if (this.rowInput != 0) {
            this.rowInput = 0;
            this.markSync();
        }
    }

    @Nullable
    public UUID helmsman() {
        return this.helmsman;
    }

    @Override
    public void onStationInput(ServerPlayer player, float steer, boolean fire, boolean letGo) {
        this.onStationInput(player, steer, 0, fire, letGo);
    }

    @Override
    public void onStationInput(ServerPlayer player, float steer, float forward, boolean fire, boolean letGo) {
        if (!player.getUUID().equals(this.helmsman)) {
            return;
        }
        if (letGo) {
            this.releaseHelm(player);
        } else {
            this.steerInput = Mth.clamp(steer, -1f, 1f);
            float row = this.rowboat() ? Mth.clamp(forward, -1f, 1f) : 0;
            if (row != this.rowInput) {
                this.rowInput = row;
                this.markSync();
            }
        }
    }

    // ---------------------------------------------------------------- liveries

    public int hullColor() {
        return this.hullColor;
    }

    public int sailColor() {
        return this.sailColor;
    }

    /**
     * Paints the hull (or dyes the sails) with a dye: a wash over the wood rather than the raw dye, so the grain
     * still shows. White dye takes it back to bare wood and plain canvas.
     */
    public void paint(net.minecraft.world.item.DyeColor dye, boolean sails, @Nullable ServerPlayer who) {
        int raw = dye.getTextureDiffuseColor() & 0xFFFFFF;
        int color = dye == net.minecraft.world.item.DyeColor.WHITE ? 0xFFFFFF : mix(raw, 0xFFFFFF, sails ? 0.25f : 0.45f);
        if (sails) {
            this.sailColor = color;
        } else {
            this.hullColor = color;
        }
        this.setChanged();
        this.markSync();
        this.sound(net.minecraft.sounds.SoundEvents.DYE_USE, 1f, 1f);
        if (who != null) {
            dev.seafarers.ship.Notify.show(who, Component.translatable(sails ? "message.seafarers.sails_dyed" : "message.seafarers.hull_painted",
                    Component.translatable("color.minecraft." + dye.getName())));
        }
        Seafarers.LOGGER.info("PROBE livery {} {}", sails ? "sails" : "hull", Integer.toHexString(color));
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    // ---------------------------------------------------------------- rowboats

    /** The oars block is a helm: the rowboat steers and rows with the same physics as a ship. */
    public boolean rowboat() {
        return this.getBlockState().is(ModBlocks.OARS.get());
    }

    /** How hard the oars are pulling, -1 (backing) .. 1; synced for the rowing animation. */
    public float row() {
        return this.rowInput;
    }

    public boolean docked() {
        return this.dockShip != null;
    }

    /** While docked: how far (blocks, across the water) the hook is from its place at the ladder. */
    public double dockGap() {
        return this.dockShip == null ? -1 : this.dockGap;
    }

    /**
     * Ties up to (or casts off from) the nearest ladder on another ship within range of the hook, as Sea of
     * Thieves rowboats dock at a ship's ladders. Docked, she follows that ship wherever it sails.
     */
    public boolean toggleDock(ServerLevel level, BlockPos hook, @Nullable ServerPlayer who, double range) {
        if (this.dockShip != null) {
            this.dockShip = null;
            this.dockAt = null;
            this.setChanged();
            level.playSound(null, Ships.toWorld(level, hook).x, Ships.toWorld(level, hook).y, Ships.toWorld(level, hook).z,
                    net.minecraft.sounds.SoundEvents.CHAIN_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.2f);
            if (who != null) {
                dev.seafarers.ship.Notify.show(who, Component.translatable("message.seafarers.undocked"));
            }
            Seafarers.LOGGER.info("PROBE rowboat undocked");
            return true;
        }
        SubLevel mine = Ships.shipAt(level, this.getBlockPos());
        Vec3 hookWorld = Ships.toWorld(level, hook);
        SubLevel best = null;
        BlockPos bestLadder = null;
        double bestDist = range;
        for (SubLevel other : dev.ryanhcode.sable.Sable.HELPER.getAllIntersecting(level, new dev.ryanhcode.sable.companion.math.BoundingBox3d(
                hookWorld.x - range, hookWorld.y - range, hookWorld.z - range, hookWorld.x + range, hookWorld.y + range, hookWorld.z + range))) {
            if (other == mine) {
                continue;
            }
            BoundingBox3ic b = other.getPlot().getBoundingBox();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int y = b.minY(); y <= b.maxY(); y++) {
                    for (int z = b.minZ(); z <= b.maxZ(); z++) {
                        // the bottom rung of each ladder, where a boat comes alongside
                        if (level.getBlockState(p.set(x, y, z)).is(ModBlocks.ROPE_LADDER.get())
                                && !level.getBlockState(p.below()).is(ModBlocks.ROPE_LADDER.get())) {
                            double d = other.logicalPose().transformPosition(Vec3.atCenterOf(p)).distanceTo(hookWorld);
                            if (d < bestDist) {
                                bestDist = d;
                                best = other;
                                bestLadder = p.immutable();
                            }
                        }
                    }
                }
            }
        }
        if (best == null) {
            if (who != null) {
                dev.seafarers.ship.Notify.show(who, Component.translatable("message.seafarers.no_dock"));
            }
            return false;
        }
        this.dockShip = best.getUniqueId();
        this.dockAt = bestLadder;
        this.dockHook = hook.immutable();
        this.setChanged();
        level.playSound(null, hookWorld.x, hookWorld.y, hookWorld.z, net.minecraft.sounds.SoundEvents.CHAIN_PLACE,
                net.minecraft.sounds.SoundSource.BLOCKS, 1f, 0.9f);
        if (who != null) {
            dev.seafarers.ship.Notify.show(who, Component.translatable("message.seafarers.docked"));
        }
        Seafarers.LOGGER.info("PROBE rowboat docked at {} ({} blocks)", bestLadder.toShortString(), String.format("%.1f", bestDist));
        return true;
    }

    /**
     * While docked: the horizontal velocity change that pulls the hook to just outside the ladder and matches the
     * other ship's motion (x, z), and in y the yaw change that lays her along the other hull. Null when the other
     * ship is gone (she casts off).
     */
    @Nullable
    private Vector3d dockPull(ServerLevel level, Pose3dc pose, Vector3d fwd, Vector3d vel, Vector3d angVel, double dt) {
        SubLevel other = SubLevelContainer.getContainer(level).getSubLevel(this.dockShip);
        BlockState ladder = other == null ? null : level.getBlockState(this.dockAt);
        if (other == null || other.isRemoved() || !ladder.is(ModBlocks.ROPE_LADDER.get())) {
            this.dockShip = null;
            this.dockAt = null;
            return null;
        }
        Direction out = ladder.getValue(dev.seafarers.ship.block.RopeLadderBlock.FACING);
        Pose3dc theirs = other.logicalPose();
        Vec3 target = theirs.transformPosition(Vec3.atCenterOf(this.dockAt).add(out.getStepX() * 2.3, 0, out.getStepZ() * 2.3));
        Vec3 hook = pose.transformPosition(Vec3.atCenterOf(this.dockHook));
        Vec3 theirVel = dev.ryanhcode.sable.Sable.HELPER.getVelocity(level, Vec3.atCenterOf(this.dockAt));
        double ex = target.x - hook.x, ez = target.z - hook.z;
        this.dockGap = Math.sqrt(ex * ex + ez * ez);
        double ax = 6 * ex + 4 * (theirVel.x - vel.x), az = 6 * ez + 4 * (theirVel.z - vel.z);
        double a = Math.sqrt(ax * ax + az * az), max = 40;
        if (a > max) {
            ax *= max / a;
            az *= max / a;
        }
        // lie along their hull: bow toward whichever end of it she is already closer to pointing at
        Vector3d along = theirs.transformNormal(new Vector3d(out.getStepZ(), 0, -out.getStepX()));
        if (along.dot(fwd) < 0) {
            along.negate();
        }
        double off = signedAngleToStarboard(fwd.x, fwd.z, along.x, along.z);
        double dYaw = this.yawControl(Mth.clamp(-Math.toRadians(off) * 1.5, -0.8, 0.8), angVel.y, dt, 4);
        return new Vector3d(ax * dt, dYaw, az * dt);
    }

    /** One pull on the length cleat (held: repeated). delta +1 lets the sail down, -1 hauls it up. */
    public void changeSails(int mast, int delta, @Nullable ServerPlayer who) {
        Sail sail = this.sail(mast);
        if (sail == null) {
            return;
        }
        if (sail.health <= 0 && delta > 0) {
            if (who != null) {
                dev.seafarers.ship.Notify.show(who, Component.translatable("message.seafarers.mast_broken"));
            }
            return;
        }
        // each pull (a held use pulls every few ticks) sets the sail a little further; tickSails lets it run there
        float next = Mth.clamp(sail.deployGoal() + delta * SAIL_PULL, 0f, 1f);
        if (next != sail.deployGoal()) {
            sail.deployGoal = next;
            this.setChanged();
            this.markSync();
            this.sound(ModSounds.ROPE_PULL.get(), 0.7f, delta > 0 ? 0.9f : 1.15f);
        }
    }

    /** @param toward +1 swings the sail to starboard, -1 to port */
    public void braceSails(int mast, int toward) {
        Sail sail = this.sail(mast);
        if (sail == null) {
            return;
        }
        float next = Mth.clamp(sail.angleGoal() + toward * BRACE_PULL, -MAX_SAIL_ANGLE, MAX_SAIL_ANGLE);
        if (next != sail.angleGoal()) {
            sail.angleGoal = next;
            this.setChanged();
            this.markSync();
            this.sound(ModSounds.PULLEY_CREAK.get(), 0.7f, 1f + toward * 0.05f);
        }
    }

    /** Use on the capstan: drops the anchor, or winds it up a bit if it is down. */
    /**
     * A push on the capstan (holding use pushes every few ticks). With the anchor up, a fresh click lets it go.
     * With it down, pushing heaves: the anchor comes up steadily while anyone keeps pushing, faster with more
     * crew on the bars, and runs back down if everyone lets go before it's up (see tickCapstan).
     */
    public void workAnchor(@Nullable ServerPlayer player) {
        if (this.sinking) {
            return;
        }
        long now = this.level.getGameTime();
        boolean held = now - this.lastHeave <= HEAVE_GAP;
        this.lastHeave = now;
        if (!this.anchored) {
            if (held || now < this.anchorWeighedAt + 20) {
                return; // still holding the bars after heaving her up: don't let the anchor straight back go
            }
            this.anchored = true;
            this.anchorRaise = 0;
            this.anchorTicks = 0;
            this.heavers.clear();
            this.pivotEnergy = (float) Math.abs(this.speed);
            this.sound(ModSounds.ANCHOR_DROP.get(), 2f, 1f);
            this.setChanged();
            this.markSync();
            return;
        }
        UUID who = player == null ? TEST_HEAVER : player.getUUID();
        this.heavers.values().removeIf(t -> now - t > HEAVE_GAP);
        if (!this.heavers.containsKey(who) && this.heavers.size() >= this.stats.capstanPegs) {
            if (player != null) {
                dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.capstan_full", this.stats.capstanPegs));
            }
            return;   // every bar is taken
        }
        this.heavers.put(who, now);
    }

    /** Percent of the heave per tick with this many crew on the bars: more hands heave faster, a heavier anchor slower. */
    private double heaveRate(int heavers) {
        int n = Math.min(heavers, Math.max(1, this.stats.capstanPegs));
        return HEAVE * (1 + 0.6 * Math.max(0, n - 1)) / Math.max(0.1, this.stats.anchorWeight);
    }

    /**
     * How fast a sail rolls (fraction of the sail per tick) and the yard swings (degrees per tick): a little faster
     * than a held rope sets them (one pull every 4 ticks), so the sail keeps up with the crew and moves continuously.
     */
    public static final float HOIST_RATE = SAIL_PULL / 3f, BRACE_RATE = BRACE_PULL / 3f;

    /** Each tick: every sail rolls and swings toward where its ropes have set it. */
    private void tickSails() {
        boolean moved = false;
        for (Sail s : this.sails) {
            float d = approach(s.deploy, s.health <= 0 ? 0 : s.deployGoal(), HOIST_RATE);
            float a = approach(s.angle, s.angleGoal(), BRACE_RATE);
            if (d != s.deploy || a != s.angle) {
                boolean landed = d != s.deploy && (d == 0f || d == 1f);
                s.deploy = d;
                s.angle = a;
                moved = true;
                if (landed) {
                    this.sound(ModSounds.SAIL_SNAP.get(), 1f, 1f);
                }
            }
        }
        if (moved) {
            this.setChanged();
            if (this.level.getGameTime() % 5 == 0) {
                this.markSync();
            }
        }
    }

    private static float approach(float from, float to, float rate) {
        return from < to ? Math.min(to, from + rate) : Math.max(to, from - rate);
    }

    /** A push on the capstan stops counting this many ticks after the last one (vanilla repeats a held use every 4). */
    private static final int HEAVE_GAP = 6;
    /** Heave rate, percent per tick, for one crewmate (the Sloop solo: about five seconds); runs back at FALL. */
    public static double HEAVE = 1.0, HEAVE_FALL = 3.0;
    private static final UUID TEST_HEAVER = new UUID(0, 0);

    /** Each tick: heave the anchor up while someone is on the bars, or let it run back down. */
    private void tickCapstan(ServerLevel level) {
        long now = level.getGameTime();
        this.heavers.values().removeIf(t -> now - t > HEAVE_GAP);
        int dir = 0;
        if (this.anchored && !this.sinking) {
            if (!this.heavers.isEmpty()) {
                dir = 1;
                this.anchorRaise += (float) this.heaveRate(this.heavers.size());
                if (now % 10 == 0) {
                    this.sound(ModSounds.CAPSTAN_PUSH.get(), 0.7f, 0.95f + level.random.nextFloat() * 0.1f);
                }
                if (this.anchorRaise >= ANCHOR_RAISE_MAX) {
                    this.anchored = false;
                    this.anchorRaise = 0;
                    this.anchorWeighedAt = now;
                    this.heavers.clear();
                    dir = 0;
                    this.sound(ModSounds.ANCHOR_UP.get(), 1.5f, 1f);
                }
            } else if (this.anchorRaise > 0) {
                dir = -1;   // nobody on the bars: the weight of the anchor runs the cable back out
                this.anchorRaise = (float) Math.max(0, this.anchorRaise - HEAVE_FALL);
                if (now % 18 == 0) {
                    this.sound(ModSounds.CAPSTAN_UNWIND.get(), 0.8f, 0.95f + level.random.nextFloat() * 0.1f);
                }
            }
        }
        if (dir != this.heaveDir || dir != 0 && now % 5 == 0) {
            this.heaveDir = dir;
            this.setChanged();
            this.markSync();
        }
    }

    // ---- curses ----

    /** Anchor ball: the anchor lets go whether the crew wants it or not. */
    public void forceAnchor() {
        if (!this.anchored && !this.sinking) {
            this.anchored = true;
            this.anchorRaise = 0;
            this.anchorTicks = 0;
            this.pivotEnergy = (float) Math.abs(this.speed);
            this.sound(ModSounds.ANCHOR_DROP.get(), 2f, 1f);
            Seafarers.LOGGER.info("PROBE curse: anchor dropped");
            this.setChanged();
            this.markSync();
        }
    }

    /** Rigging ball: every sail is hauled up. */
    public void furlAllSails() {
        for (Sail sail : this.sails) {
            sail.deployGoal = 0;   // they roll up
        }
        this.sound(ModSounds.SAIL_SNAP.get(), 1.5f, 0.8f);
        Seafarers.LOGGER.info("PROBE curse: sails furled");
        this.setChanged();
        this.markSync();
    }

    /** Ballast ball: she sits heavy and slow for a while. */
    public void addBallast(int ticks) {
        this.ballastTicks = Math.max(this.ballastTicks, ticks);
        Seafarers.LOGGER.info("PROBE curse: ballast for {} ticks", ticks);
    }

    public int fireCount() {
        return this.fires.size();
    }

    public int ballastTicks() {
        return this.ballastTicks;
    }

    /** @return true if water was scooped out */
    public boolean bail() {
        if (this.flood <= 0 || this.sinking) {
            return false;
        }
        this.flood = (float) Math.max(0, this.flood - this.stats.bucket * dev.seafarers.SeafarersConfig.BAILING_MULTIPLIER.get());
        this.setChanged();
        return true;
    }

    public boolean mastDamaged(int mast) {
        Sail sail = this.sail(mast);
        return sail != null && sail.health < this.mastHealthMax();
    }

    public void damageMast(int mast, int amount, String where) {
        Sail sail = this.sail(mast);
        if (sail == null || sail.health <= 0 || amount <= 0) {
            return;
        }
        sail.health = Math.max(0, sail.health - amount);
        Seafarers.LOGGER.info("PROBE mast {} hit ({}) health={}", mast, where, sail.health);
        this.sound(ModSounds.MAST_CRACK.get(), 1.5f, 1f);
        if (sail.health <= 0) {
            sail.set(0, sail.angle);
            this.sound(SoundEvents.WOOD_BREAK, 2f, 0.5f);
            if (this.level instanceof ServerLevel level && Ships.shipAt(level, this.getBlockPos()) instanceof ServerSubLevel ship) {
                this.crewMessage(level, ship, Component.translatable("message.seafarers.mast_down").withStyle(ChatFormatting.RED));
            }
        }
        this.setChanged();
        this.markSync();
    }

    public void repairMast(int mast) {
        Sail sail = this.sail(mast);
        if (sail != null) {
            sail.health = Math.min(this.mastHealthMax(), sail.health + 2);
            this.setChanged();
            this.markSync();
        }
    }

    /** Called when a hole is patched, so the leak stops right away instead of at the next scan. */
    public void forceRescan() {
        this.scanTimer = 0;
    }

    /** Which set sail (mast index) a point in plot space is inside, or -1. Used by chain shot. */
    public int sailAt(Vec3 plotPoint) {
        Direction bow = this.bow();
        Vec3 bowVec = Vec3.atLowerCornerOf(bow.getNormal());
        Vec3 starboard = Vec3.atLowerCornerOf(bow.getClockWise().getNormal());
        for (int m = 0; m < this.yards.size() && m < this.sails.size(); m++) {
            Sail sail = this.sails.get(m);
            BlockPos yardPos = this.yards.get(m);
            if (yardPos == null || sail.deploy < 0.02f || sail.health <= 0
                    || !(this.level.getBlockEntity(yardPos) instanceof SailYardBlockEntity yard)) {
                continue;
            }
            Vec3 mastPos = Vec3.atCenterOf(yardPos).subtract(bowVec);
            // undo the sail's swing (positive angle = clockwise seen from above)
            Vec3 rel = plotPoint.subtract(mastPos).yRot((float) Math.toRadians(sail.angle));
            double ahead = rel.dot(bowVec);
            double across = rel.dot(starboard);
            double bottom = 0.3 - yard.height() * sail.deploy;
            if (Math.abs(across) <= yard.halfWidth() + 0.5 && ahead >= -0.2 && ahead <= yard.forward() + 1.5
                    && rel.y <= 0.3 && rel.y >= bottom) {
                return m;
            }
        }
        return -1;
    }

    private void markSync() {
        this.syncDirty = true;
    }

    private void sound(SoundEvent event, float volume, float pitch) {
        Vec3 at = this.worldPos();
        this.level.playSound(null, at.x, at.y, at.z, event, SoundSource.BLOCKS, volume, pitch);
    }

    public Vec3 worldPos() {
        return Ships.toWorld(this.level, this.getBlockPos());
    }

    /** Where crew respawn aboard, in world space. */
    public Vec3 respawnWorldPos() {
        BlockPos p = this.respawn.equals(BlockPos.ZERO) ? this.getBlockPos().above() : this.respawn;
        return Ships.toWorld(this.level, Vec3.atBottomCenterOf(p).add(0, 0.1, 0));
    }

    /** One of her respawn spots, picked at random (the bigger ships have several, as in Sea of Thieves). */
    public Vec3 respawnWorldPos(net.minecraft.util.RandomSource random) {
        if (this.respawns.isEmpty()) {
            return this.respawnWorldPos();
        }
        return Ships.toWorld(this.level, Vec3.atBottomCenterOf(this.respawns.get(random.nextInt(this.respawns.size()))).add(0, 0.1, 0));
    }

    public int respawnCount() {
        return Math.max(1, this.respawns.size());
    }

    /** Whether she has a brig. */
    public boolean hasBrig() {
        return this.brig.size() == 3;
    }

    /** The inside of the brig, in plot space (whole cells), or null. */
    @Nullable
    public AABB brigBox() {
        if (!this.hasBrig()) {
            return null;
        }
        BlockPos a = this.brig.get(0), b = this.brig.get(1);
        return new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    /** The brig door's lower half (plot space), or null. */
    @Nullable
    public BlockPos brigDoor() {
        return this.hasBrig() ? this.brig.get(2) : null;
    }

    // ---- state for rendering, tests and commands ----
    public float wheel() { return this.wheel; }
    @Nullable
    public Sail sail(int mast) { return mast >= 0 && mast < this.sails.size() ? this.sails.get(mast) : null; }
    public int masts() { return this.sails.size(); }
    /** How far the bow points above the horizon, degrees (negative = down by the head). */
    public double bowUp() {
        var ship = Ships.shipAt(this.level, this.getBlockPos());
        if (ship == null) {
            return 0;
        }
        Direction bow = this.bow();
        Vector3d f = ship.logicalPose().transformNormal(new Vector3d(bow.getStepX(), 0, bow.getStepZ()));
        return Math.toDegrees(Math.asin(Mth.clamp(f.y, -1, 1)));
    }
    public boolean anchored() { return this.anchored; }
    public int anchorRaise() { return Math.round(this.anchorRaise); }
    public float anchorRaiseExact() { return this.anchorRaise; }
    public int heaveDir() { return this.heaveDir; }
    public int heavers() { return this.heavers.size(); }
    public float steerInput() { return this.steerInput; }
    public float wheelSpeed() { return (float) this.stats.wheelSpeed; }
    /** Client: turns of the wheel from amidships to hard over. */
    public float wheelTurns() { return this.syncWheelTurns; }
    public float flood() { return this.flood; }
    public boolean sinking() { return this.sinking; }
    public double speed() { return this.speed; }
    public int holeCount() { return this.holes.size(); }
    /** Debug counters: game ticks and physics ticks seen, and ticks spent sinking. */
    public String tickCounters() { return this.gameTicks + "/" + this.physicsTicks + "/" + this.sinkTicks; }
    /** The water drag the helm has measured, "along/yaw", for tests. */
    public String dragEstimate() { return String.format("%.2f/%.2f", this.dragEst, this.yawErrInt); }
    private long gameTicks;
    private long physicsTicks;
    public ShipStats stats() { return this.stats; }
    public int maxCrew() { return crewSize(this.layout, this.stats.crew); }

    /** How many can crew a ship of this kind (the server's config, else the layout's own number). */
    public static int crewSize(String layout, int fallback) {
        return switch (layout) {
            case "sloop" -> dev.seafarers.SeafarersConfig.SLOOP_CREW.get();
            case "brigantine" -> dev.seafarers.SeafarersConfig.BRIGANTINE_CREW.get();
            case "galleon" -> dev.seafarers.SeafarersConfig.GALLEON_CREW.get();
            default -> fallback;
        };
    }

    /** Hits a mast takes: her own (the Sloop's stands 6, the bigger ships' 3) scaled by the server's mastStrength (6 = as designed). */
    public int mastHealthMax() {
        if (this.level != null && this.level.isClientSide) {
            return this.syncMastMax;   // (the server's config and the ship's stats aren't on the client)
        }
        return Math.max(1, (int) Math.round(this.stats.mastHealth * dev.seafarers.SeafarersConfig.MAST_STRENGTH.get() / 6.0));
    }

    /** The name of a mast, for the crew: the mainmast is 0, then the foremast and the mizzen. */
    public Component mastName(int mast) {
        if (this.sails.size() <= 1) {
            return Component.translatable("mast.seafarers.only");
        }
        return Component.translatable("mast.seafarers." + switch (mast) { case 0 -> "main"; case 1 -> "fore"; default -> "mizzen"; });
    }
    public String layout() { return this.layout; }

    /** How high the sea stands above the lower-deck floor at its deepest point (negative = dry). For tests. */
    public double waterAboveHoldFloor() {
        if (this.bilge.isEmpty()) {
            return Double.NaN;
        }
        int floorY = Integer.MAX_VALUE;
        for (BlockPos p : this.bilge) {
            floorY = Math.min(floorY, p.getY());
        }
        double worst = Double.NEGATIVE_INFINITY;
        for (BlockPos p : this.bilge) {
            if (p.getY() == floorY) {
                worst = Math.max(worst, this.seaAbove(Ships.toWorld(this.level, Vec3.atBottomCenterOf(p))));
            }
        }
        return worst;
    }

    private double seaAbove(Vec3 floor) {
        BlockPos.MutableBlockPos probe = BlockPos.containing(floor.x, floor.y - 3, floor.z).mutable();
        while (this.level.getFluidState(probe.above()).is(FluidTags.WATER) && probe.getY() < floor.y + 6) {
            probe.move(0, 1, 0);
        }
        double surface = probe.getY() + this.level.getFluidState(probe).getHeight(this.level, probe);
        return surface - floor.y;
    }

    /** "filled/cells" for the bilge, for tests. */
    public String bilgeSummary() {
        int filled = 0;
        for (BlockPos p : this.bilge) {
            if (this.level.getBlockState(p).is(ModBlocks.BILGE_WATER.get())) {
                filled++;
            }
        }
        return filled + "/" + this.bilge.size();
    }

    /** Test hook: set the controls directly (sails apply to every mast). */
    public void setControls(float wheel, int sails, float sailAngle, boolean anchored) {
        this.wheel = Mth.clamp(wheel, -1, 1);
        for (Sail sail : this.sails) {
            sail.set(sail.health <= 0 ? 0 : Mth.clamp(sails / 3f, 0f, 1f), Mth.clamp(sailAngle, -MAX_SAIL_ANGLE, MAX_SAIL_ANGLE));
        }
        if (anchored && !this.anchored) {
            this.anchorTicks = 0;
            this.pivotEnergy = (float) Math.abs(this.speed);
        }
        this.anchored = anchored;
        this.anchorRaise = 0;
        this.setChanged();
        this.markSync();
    }

    // =====================================================================
    // Save / load / sync
    // =====================================================================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.writeSynced(tag);
        tag.putString("layout", this.layout);
        tag.putBoolean("owned", this.owned);
        tag.putLong("abandonedSince", this.abandonedSince);
        tag.put("stats", this.stats.save());
        tag.putLong("respawn", this.respawn.asLong());
        tag.putLongArray("respawns", this.respawns.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("brig", this.brig.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("bilge", this.bilge.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("dry", this.dry.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putIntArray("dryMesh", meshBits(this.dryMesh));
        tag.putLong("dryMeshBase", this.dryMeshBase.asLong());
        tag.putFloat("flood", this.flood);
        tag.putInt("sinkTicks", this.sinkTicks);
        tag.putInt("anchorTicks", this.anchorTicks);
        if (this.dockShip != null) {
            tag.putUUID("dockShip", this.dockShip);
            tag.putLong("dockAt", this.dockAt.asLong());
            tag.putLong("dockHook", this.dockHook.asLong());
        }
    }

    private void writeSynced(CompoundTag tag) {
        tag.putFloat("wheel", this.wheel);
        tag.putInt("hullColor", this.hullColor);
        tag.putInt("sailColor", this.sailColor);
        tag.putFloat("row", this.rowInput);
        ListTag sails = new ListTag();
        for (Sail s : this.sails) {
            sails.add(s.save());
        }
        tag.put("sails", sails);
        tag.putBoolean("anchored", this.anchored);
        tag.putFloat("anchorRaise", this.anchorRaise);
        tag.putInt("heaveDir", this.heaveDir);
        tag.putInt("heavers", this.heavers.size());
        tag.putFloat("heaveRate", this.heavers.isEmpty() ? 0 : (float) this.heaveRate(this.heavers.size()));
        tag.putFloat("wheelTurns", (float) this.stats.wheelTurns);
        tag.putInt("mastMax", this.mastHealthMax());
        tag.putFloat("steer", this.steerInput);
        tag.putFloat("wheelSpeed", (float) this.stats.wheelSpeed);
        tag.putBoolean("sinking", this.sinking);
        tag.putInt("anchorTicks", Math.min(this.anchorTicks, 100));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.owned = tag.getBoolean("owned");
        this.abandonedSince = tag.contains("abandonedSince") ? tag.getLong("abandonedSince") : -1;
        this.wheel = tag.getFloat("wheel");
        if (tag.contains("hullColor")) {
            this.hullColor = tag.getInt("hullColor");
            this.sailColor = tag.getInt("sailColor");
        }
        this.rowInput = tag.getFloat("row");
        if (tag.hasUUID("dockShip")) {
            this.dockShip = tag.getUUID("dockShip");
            this.dockAt = BlockPos.of(tag.getLong("dockAt"));
            this.dockHook = BlockPos.of(tag.getLong("dockHook"));
        }
        if (tag.contains("sails", Tag.TAG_LIST)) {
            this.sails.clear();
            for (Tag t : tag.getList("sails", Tag.TAG_COMPOUND)) {
                this.sails.add(Sail.load((CompoundTag) t));
            }
            if (this.sails.isEmpty()) {
                this.sails.add(new Sail(this.mastHealthMax()));
            }
        }
        this.anchored = tag.getBoolean("anchored");
        this.anchorRaise = tag.getFloat("anchorRaise");
        if (this.level != null && this.level.isClientSide) {
            // clients turn the wheel and drum on from here until the next update (see HelmRenderer, CapstanRenderer)
            this.heaveDir = tag.getInt("heaveDir");
            this.syncHeaveRate = tag.contains("heaveRate") ? tag.getFloat("heaveRate") : (float) (HEAVE * (1 + 0.6 * Math.max(0, tag.getInt("heavers") - 1)));
            this.syncWheelTurns = tag.contains("wheelTurns") ? tag.getFloat("wheelTurns") : 0.5f;
            this.syncMastMax = tag.contains("mastMax") ? Math.max(1, tag.getInt("mastMax")) : 6;
            this.syncSteer = tag.getFloat("steer");
            this.syncWheelSpeed = tag.contains("wheelSpeed") ? tag.getFloat("wheelSpeed") : 0.025f;
            this.syncTime = this.level.getGameTime();
            this.syncWheel = this.wheel;
            this.syncRaise = this.anchorRaise;
        }
        this.sinking = tag.getBoolean("sinking");
        if (tag.contains("dryMeshBase")) {
            this.dryMeshBase = BlockPos.of(tag.getLong("dryMeshBase"));
        }
        if (tag.contains("dryMesh")) {
            int[] bits = tag.getIntArray("dryMesh");
            this.dryMesh = new float[bits.length];
            for (int i = 0; i < bits.length; i++) {
                this.dryMesh[i] = Float.intBitsToFloat(bits[i]);
            }
        }
        if (tag.contains("dry")) {
            this.dry.clear();
            for (long l : tag.getLongArray("dry")) {
                this.dry.add(BlockPos.of(l));
            }
        }
        if (tag.contains("bilge") && !tag.contains("layout")) {
            // client: the hold cells arrive with the first sync
            this.bilge.clear();
            for (long l : tag.getLongArray("bilge")) {
                this.bilge.add(BlockPos.of(l));
            }
            this.keepHoldDry();
        }
        if (tag.contains("layout")) {
            this.layout = tag.getString("layout");
            this.stats = ShipStats.load(tag.getCompound("stats"));
            this.respawn = BlockPos.of(tag.getLong("respawn"));
            this.respawns.clear();
            for (long l : tag.getLongArray("respawns")) {
                this.respawns.add(BlockPos.of(l));
            }
            this.brig.clear();
            for (long l : tag.getLongArray("brig")) {
                this.brig.add(BlockPos.of(l));
            }
            this.bilge.clear();
            for (long l : tag.getLongArray("bilge")) {
                this.bilge.add(BlockPos.of(l));
            }
            this.flood = tag.getFloat("flood");
            this.sinkTicks = tag.getInt("sinkTicks");
            this.anchorTicks = tag.contains("anchorTicks") ? tag.getInt("anchorTicks") : ANCHOR_FALL_TICKS;
            this.keepHoldDry();
        }
    }

    /** Sent when a client first loads the ship: includes the hold cells so the client can keep it dry too. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        this.writeSynced(tag);
        tag.putLongArray("bilge", this.bilge.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("dry", this.dry.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putIntArray("dryMesh", meshBits(this.dryMesh));
        tag.putLong("dryMeshBase", this.dryMeshBase.asLong());
        return tag;
    }

    private static int[] meshBits(float[] mesh) {
        int[] bits = new int[mesh.length];
        for (int i = 0; i < mesh.length; i++) {
            bits[i] = Float.floatToRawIntBits(mesh[i]);
        }
        return bits;
    }

    /** Sent while sailing: just what the renderers need. */
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this, (be, registries) -> {
            CompoundTag tag = new CompoundTag();
            ((HelmBlockEntity) be).writeSynced(tag);
            return tag;
        });
    }
}
