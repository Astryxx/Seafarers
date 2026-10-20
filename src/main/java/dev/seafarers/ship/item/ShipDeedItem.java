package dev.seafarers.ship.item;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.SeafarersConfig;
import dev.seafarers.registry.ModDataComponents;
import dev.seafarers.ship.Crews;
import dev.seafarers.ship.Notify;
import dev.seafarers.ship.ShipBuilder;
import dev.seafarers.ship.deed.Deed;
import dev.seafarers.ship.deed.Sailors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.UUID;

/**
 * Use on open water to launch a ship, bow pointing the way you face. You become her captain. One use: the deed
 * is spent once she's afloat.
 * <p>
 * A player's own deed (from the ship choice screen) is protected, as far as the game allows: it can't burn, blow
 * up, despawn or fall out of the world, it stays with its owner when they die (see DeedEvents), and if it's lost
 * anyway the owner can have it replaced (the lost one then stops working).
 */
public class ShipDeedItem extends Item {
    private final String layout;
    /** Whether whoever launches it becomes its crew (ships), or it is anybody's (rowboats). */
    private final boolean crewed;

    public ShipDeedItem(String layout, Properties props) {
        this(layout, true, props);
    }

    public ShipDeedItem(String layout, boolean crewed, Properties props) {
        super(props);
        this.layout = layout;
        this.crewed = crewed;
    }

    public String layout() {
        return this.layout;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
            return InteractionResultHolder.pass(stack);
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            Deed deed = stack.get(ModDataComponents.DEED.get());
            Sailors sailors = Sailors.get(server.getServer());
            Crews crews = Crews.get(server);
            if (this.crewed && deed != null) {
                if (!sailors.valid(deed)) {
                    // replaced by /crew reclaim: this one is void
                    Notify.show(player, Component.translatable("message.seafarers.deed_void").withStyle(ChatFormatting.RED));
                    stack.shrink(1);
                    return InteractionResultHolder.fail(stack);
                }
                if (SeafarersConfig.DEEDS_ONLY_FOR_OWNER.get() && !deed.owner().equals(player.getUUID())) {
                    Notify.show(player, Component.translatable("message.seafarers.deed_not_yours", deed.ownerName()).withStyle(ChatFormatting.RED));
                    return InteractionResultHolder.fail(stack);
                }
            }
            if (this.crewed && SeafarersConfig.ONE_SHIP_PER_PLAYER.get() && !player.isCreative()
                    && crews.captainedBy(player.getUUID()) != null) {
                Notify.show(player, Component.translatable("message.seafarers.already_captain").withStyle(ChatFormatting.RED));
                return InteractionResultHolder.fail(stack);
            }
            int cap = SeafarersConfig.MAX_SHIPS.get();
            boolean bypass = dev.seafarers.ship.Permissions.has(sp, dev.seafarers.ship.Permissions.BYPASS_LIMITS);
            if (this.crewed && cap > 0 && crews.captainCount() >= cap && !bypass) {
                Notify.show(player, Component.translatable("message.seafarers.seas_full", cap).withStyle(ChatFormatting.RED));
                return InteractionResultHolder.fail(stack);
            }
            if (SeafarersConfig.RESPECT_CLAIMS.get() && !bypass
                    && dev.seafarers.compat.claims.Claims.claimedByOthers(sp, server, hit.getBlockPos())) {
                Notify.show(player, Component.translatable("message.seafarers.claimed_land").withStyle(ChatFormatting.RED));
                return InteractionResultHolder.fail(stack);
            }
            BlockPos surface = hit.getBlockPos();
            while (level.getFluidState(surface.above()).is(FluidTags.WATER)) {
                surface = surface.above();
            }
            ServerSubLevel ship = ShipBuilder.launch(server, this.layout, surface, player.getDirection());
            if (ship == null) {
                Notify.show(player, Component.translatable("message.seafarers.no_room").withStyle(ChatFormatting.RED));
                return InteractionResultHolder.fail(stack);
            }
            if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
                // a deed renamed in an anvil names the ship
                String name = stack.getHoverName().getString().strip();
                ship.setName(name.length() > 32 ? name.substring(0, 32) : name);
            }
            if (this.crewed) {
                UUID id = ship.getUniqueId();
                if (deed != null && deed.replaces().isPresent()) {
                    crews.reassign(deed.replaces().get(), id); // the crew of the ship she was swapped for
                }
                crews.join(sp, id, Integer.MAX_VALUE);
                crews.setCaptain(id, player.getUUID());
                dev.seafarers.ship.HelmBlockEntity helm = dev.seafarers.ship.Ships.helmOf(ship);
                if (helm != null) {
                    helm.markOwned();
                }
                crews.setLocked(id, SeafarersConfig.ALLOW_LOCKING.get() && SeafarersConfig.NEW_SHIPS_LOCKED.get());
                if (deed != null) {
                    sailors.used(deed);
                }
                for (UUID member : crews.crewOf(id)) {
                    ServerPlayer p = server.getServer().getPlayerList().getPlayer(member);
                    if (p != null && p != sp) {
                        p.sendSystemMessage(Component.translatable("message.seafarers.new_ship_launched", sp.getDisplayName())
                                .withStyle(ChatFormatting.AQUA));
                    }
                }
                Notify.show(player, Component.translatable("message.seafarers.launched").withStyle(ChatFormatting.AQUA));
                dev.seafarers.ship.Feats.award(sp, "launch");
                if (this.layout.equals("brigantine") || this.layout.equals("galleon")) {
                    dev.seafarers.ship.Feats.award(sp, "big_ship");
                }
                Seafarers.LOGGER.info("PROBE {} launched {} {} captain={}", sp.getGameProfile().getName(), this.layout, id, player.getUUID());
            }
            stack.consume(1, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static boolean protectedDeed(ItemStack stack) {
        return stack.has(ModDataComponents.DEED.get()) && SeafarersConfig.PROTECT_DEEDS.get();
    }

    @Override
    public boolean canBeHurtBy(ItemStack stack, DamageSource source) {
        return !protectedDeed(stack) && super.canBeHurtBy(stack, source);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level().isClientSide || !protectedDeed(stack)) {
            return false;
        }
        if (entity.getAge() != -32768) {
            entity.setUnlimitedLifetime(); // never despawns
        }
        Level level = entity.level();
        if (entity.getY() < level.getMinBuildHeight() - 16) {
            // falling out of the world: back to its owner, or to the world's spawn
            Deed deed = stack.get(ModDataComponents.DEED.get());
            ServerPlayer owner = ((ServerLevel) level).getServer().getPlayerList().getPlayer(deed.owner());
            if (owner != null) {
                ItemStack copy = stack.copy();
                if (!owner.getInventory().add(copy)) {
                    owner.drop(copy, false);
                }
                owner.sendSystemMessage(Component.translatable("message.seafarers.deed_rescued").withStyle(ChatFormatting.AQUA));
                entity.discard();
            } else {
                BlockPos spawn = ((ServerLevel) level).getServer().overworld().getSharedSpawnPos();
                ServerLevel overworld = ((ServerLevel) level).getServer().overworld();
                int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ()) + 1;
                if (level == overworld) {
                    entity.teleportTo(spawn.getX() + 0.5, y, spawn.getZ() + 0.5);
                    entity.setDeltaMovement(0, 0, 0);
                } else {
                    ItemEntity moved = new ItemEntity(overworld, spawn.getX() + 0.5, y, spawn.getZ() + 0.5, stack.copy());
                    overworld.addFreshEntity(moved);
                    entity.discard();
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(this.getDescriptionId() + ".tip").withStyle(ChatFormatting.GRAY));
        if (this.crewed) {
            tooltip.add(Component.translatable("item.seafarers.deed.warning").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("item.seafarers.deed.one_use").withStyle(ChatFormatting.GRAY));
        }
        Deed deed = stack.get(ModDataComponents.DEED.get());
        if (deed != null) {
            tooltip.add(Component.translatable("item.seafarers.deed.owner", deed.ownerName()).withStyle(ChatFormatting.AQUA));
            if (deed.replaces().isPresent()) {
                tooltip.add(Component.translatable("item.seafarers.deed.swap").withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
