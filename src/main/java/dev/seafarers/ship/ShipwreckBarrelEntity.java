package dev.seafarers.ship;

import dev.seafarers.SeafarersConfig;
import dev.seafarers.registry.ModEntities;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A barrel floating where a ship went down, holding what was in her stores. Anyone can open it (use or hit it)
 * to take everything inside; a harpoon catches it and reels it in. It bobs on the surface and drifts, and after
 * the config's shipwreckLootMinutes it sinks for good.
 */
public class ShipwreckBarrelEntity extends Entity {
    public static final int SIZE = 27;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private int age;

    public ShipwreckBarrelEntity(EntityType<? extends ShipwreckBarrelEntity> type, Level level) {
        super(type, level);
    }

    public static ShipwreckBarrelEntity of(ServerLevel level, Vec3 at, List<ItemStack> stacks) {
        ShipwreckBarrelEntity barrel = new ShipwreckBarrelEntity(ModEntities.SHIPWRECK_BARREL.get(), level);
        barrel.setPos(at.x, at.y, at.z);
        barrel.setYRot(level.random.nextFloat() * 360f);
        for (int i = 0; i < Math.min(SIZE, stacks.size()); i++) {
            barrel.items.set(i, stacks.get(i));
        }
        barrel.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.3, 0.3, (level.random.nextDouble() - 0.5) * 0.3);
        return barrel;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = this.getDeltaMovement();
        if (this.isInWater()) {
            // float up to the surface and ride there, drifting slowly
            double depth = this.getFluidHeight(FluidTags.WATER);
            double lift = depth > 0.45 ? 0.05 : depth > 0.3 ? 0.01 : -0.01;
            v = new Vec3(v.x * 0.92, v.y * 0.8 + lift, v.z * 0.92);
        } else if (!this.onGround()) {
            v = v.add(0, -0.04, 0).scale(0.98);
        } else {
            v = v.multiply(0.6, 0, 0.6);
        }
        this.setDeltaMovement(v);
        this.move(MoverType.SELF, v);
        if (!this.level().isClientSide && ++this.age > SeafarersConfig.SHIPWRECK_LOOT_MINUTES.get() * 1200) {
            ((ServerLevel) this.level()).sendParticles(ParticleTypes.BUBBLE, this.getX(), this.getY() + 0.3, this.getZ(), 12, 0.3, 0.2, 0.3, 0.02);
            this.discard(); // gone down for good
        }
    }

    /** Everything inside goes to the player; what won't fit drops at their feet. */
    private void open(Player player) {
        if (this.level().isClientSide || this.isRemoved()) {
            return;
        }
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty() && !player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        this.items.clear();
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BARREL_OPEN, SoundSource.NEUTRAL, 1f, 0.9f);
        ((ServerLevel) this.level()).sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 0.5, this.getZ(), 16, 0.4, 0.2, 0.4, 0.1);
        Feats.award(player, "salvage");
        this.discard();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        this.open(player);
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player player) {
            this.open(player);
            return true;
        }
        return false; // cannonballs, fire and the sea leave it be
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ContainerHelper.loadAllItems(tag, this.items, this.registryAccess());
        this.age = tag.getInt("age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        ContainerHelper.saveAllItems(tag, this.items, this.registryAccess());
        tag.putInt("age", this.age);
    }
}
