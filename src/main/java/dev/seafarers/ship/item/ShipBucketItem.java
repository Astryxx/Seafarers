package dev.seafarers.ship.item;

import dev.seafarers.registry.ModSounds;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.seafarers.registry.ModItems;
import dev.seafarers.ship.HelmBlockEntity;
import dev.seafarers.ship.Ships;
import dev.seafarers.ship.SplashEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The ship's bucket. Empty: use it aboard a flooding ship to scoop water out of the hold, or on the sea to fill it.
 * Full: use it to throw the water, which puts out fires where it lands. It never places water blocks, so it can't
 * flood a deck or change the world.
 */
public class ShipBucketItem extends Item {
    public enum Content { EMPTY, WATER, VOMIT }

    private final Content content;

    public ShipBucketItem(Content content, Properties props) {
        super(props);
        this.content = content;
    }

    public Content content() {
        return this.content;
    }

    public static ItemStack of(Content content) {
        return new ItemStack(switch (content) {
            case EMPTY -> ModItems.SHIP_BUCKET.get();
            case WATER -> ModItems.SHIP_BUCKET_WATER.get();
            case VOMIT -> ModItems.SHIP_BUCKET_VOMIT.get();
        });
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (this.content != Content.EMPTY) {
            if (level instanceof ServerLevel server) {
                throwOut(server, player, this.content);
            }
            return InteractionResultHolder.sidedSuccess(swap(player, stack, Content.EMPTY), level.isClientSide);
        }
        HelmBlockEntity helm = shipUnderfoot(level, player);
        if (helm != null && helm.flood() > 0) {
            if (!level.isClientSide && helm.bail()) {
                dev.seafarers.ship.Feats.award(player, "bail");
                level.playSound(null, player.blockPosition(), ModSounds.BUCKET_SCOOP.get(), SoundSource.PLAYERS, 1f, 1.1f);
                return InteractionResultHolder.success(swap(player, stack, Content.WATER));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() == HitResult.Type.BLOCK && level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
            level.playSound(null, player.blockPosition(), ModSounds.BUCKET_SCOOP.get(), SoundSource.PLAYERS, 1f, 1f);
            return InteractionResultHolder.sidedSuccess(swap(player, stack, Content.WATER), level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    private static ItemStack swap(Player player, ItemStack held, Content to) {
        ItemStack next = of(to);
        if (player.getAbilities().instabuild) {
            return held.getCount() == 1 ? next : held;
        }
        return next;
    }

    /** The ship the player is standing on or inside, by its helm. */
    @Nullable
    public static HelmBlockEntity shipUnderfoot(Level level, Player player) {
        SubLevel ship = Sable.HELPER.getTrackingSubLevel(player);
        if (ship == null) {
            for (SubLevel s : Sable.HELPER.getAllIntersecting(level, new dev.ryanhcode.sable.companion.math.BoundingBox3d(
                    player.getBoundingBox().inflate(0.5)))) {
                ship = s;
                break;
            }
        }
        return ship == null ? null : Ships.helmOf(ship);
    }

    public static void throwOut(ServerLevel level, Player player, Content content) {
        SplashEntity splash = new SplashEntity(level, player, content == Content.VOMIT);
        Vec3 look = player.getLookAngle();
        splash.setPos(player.getX(), player.getEyeY() - 0.2, player.getZ());
        splash.setDeltaMovement(look.scale(0.85).add(0, 0.12, 0).add(player.getDeltaMovement()));
        level.addFreshEntity(splash);
        level.playSound(null, player.blockPosition(), ModSounds.BUCKET_THROW.get(), SoundSource.PLAYERS, 1f, 1f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.seafarers.ship_bucket.tip." + this.content.name().toLowerCase())
                .withStyle(ChatFormatting.GRAY));
    }
}
