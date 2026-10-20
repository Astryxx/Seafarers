package dev.seafarers.ship;

import dev.seafarers.Seafarers;
import dev.seafarers.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A mermaid, as in Sea of Thieves: she comes to a crewmate left in the water far from their ship (see Mermaids) and
 * takes them back aboard. Swim up to her, or use her. She waits at the surface facing them, and goes again if they
 * get back aboard some other way, leave the water, or after a couple of minutes. Only the sailor she came for (or
 * their crewmates) can go with her. She can't be hurt.
 */
public class MermaidEntity extends Entity {
    /** Ticks she waits before diving away. */
    private static final int WAIT = 20 * 120;
    @Nullable
    private UUID sailor;
    private int life;
    /** For pictures (the test commands): she stays put, facing nowhere in particular, until removed. */
    public boolean still;
    /** Client: her tail's swing. */
    public float tailPhase;

    public MermaidEntity(EntityType<? extends MermaidEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static MermaidEntity of(ServerLevel level, Vec3 at, ServerPlayer sailor) {
        MermaidEntity m = new MermaidEntity(ModEntities.MERMAID.get(), level);
        m.sailor = sailor.getUUID();
        m.setPos(at.x, at.y, at.z);
        m.lookAt(sailor.position());
        return m;
    }

    @Nullable
    public UUID sailor() {
        return this.sailor;
    }

    private void lookAt(Vec3 target) {
        double dx = target.x - this.getX(), dz = target.z - this.getZ();
        this.setYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f);
        this.setYHeadRot(this.getYRot());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        if (this.level().isClientSide) {
            this.tailPhase += 0.12f;
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        // ride the surface: her waist at the waterline, her whole upper body out of the water
        double depth = this.getFluidHeight(FluidTags.WATER);
        if (depth > 0.15) {
            this.setPos(this.getX(), this.getY() + 0.03, this.getZ());
        } else if (depth <= 0) {
            this.setPos(this.getX(), this.getY() - 0.03, this.getZ());
        }
        if (this.still) {
            return;
        }
        ServerPlayer player = this.sailor == null ? null : level.getServer().getPlayerList().getPlayer(this.sailor);
        if (player == null || player.level() != level || !player.isAlive() || ++this.life > WAIT || !Mermaids.adrift(player, false)) {
            this.leave(level);
            return;
        }
        this.lookAt(player.position());
        if (this.life % 30 == 0) {
            level.sendParticles(ParticleTypes.BUBBLE, this.getX(), this.getY() + 0.2, this.getZ(), 6, 0.4, 0.1, 0.4, 0.02);
        }
        // swimming right up to her is enough
        if (player.distanceToSqr(this) < 1.6 * 1.6) {
            this.carry(level, player);
        }
    }

    /** Takes the sailor back aboard their ship (to the brig, if that's where they belong). */
    public boolean carry(ServerLevel level, ServerPlayer player) {
        Vec3 to = Mermaids.destination(level, player);
        if (to == null) {
            this.leave(level);
            return false;
        }
        level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.5, player.getZ(), 30, 0.5, 0.3, 0.5, 0.2);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.DOLPHIN_SPLASH, SoundSource.NEUTRAL, 1.2f, 1.1f);
        player.stopRiding();
        player.teleportTo(level, to.x, to.y, to.z, player.getYRot(), player.getXRot());
        level.playSound(null, to.x, to.y, to.z, SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.6f);
        Notify.show(player, Component.translatable("message.seafarers.mermaid_carried").withStyle(ChatFormatting.AQUA));
        Feats.award(player, "mermaid");
        Seafarers.LOGGER.info("PROBE mermaid carried {} to {}", player.getGameProfile().getName(),
                net.minecraft.core.BlockPos.containing(to).toShortString());
        this.discard();
        return true;
    }

    private void leave(ServerLevel level) {
        level.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 0.5, this.getZ(), 20, 0.4, 0.2, 0.4, 0.1);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.DOLPHIN_SWIM, SoundSource.NEUTRAL, 0.8f, 1.2f);
        this.discard();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level() instanceof ServerLevel level && player instanceof ServerPlayer sp && this.mayCarry(level, sp)) {
            this.carry(level, sp);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    /** The sailor she came for, or one of their crewmates. */
    private boolean mayCarry(ServerLevel level, ServerPlayer player) {
        if (player.getUUID().equals(this.sailor)) {
            return true;
        }
        Crews crews = Crews.get(level);
        UUID theirs = this.sailor == null ? null : crews.shipOf(this.sailor);
        return theirs != null && theirs.equals(crews.shipOf(player.getUUID()));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;   // she comes when she's needed; nobody finds one waiting in a saved world
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
