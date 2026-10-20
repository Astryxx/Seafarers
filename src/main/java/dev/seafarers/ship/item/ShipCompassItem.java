package dev.seafarers.ship.item;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.seafarers.ship.Crews;
import dev.seafarers.ship.HelmBlockEntity;
import dev.seafarers.ship.Notify;
import dev.seafarers.ship.Ships;
import dev.seafarers.ship.deed.ShipChoice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A compass whose lodestone is your ship: it points to the ship you crew, wherever she's sailed (her last known
 * place if she's out of reach), and spins if you have no ship. Held in hand, it tells you how far she is.
 * The needle is vanilla's compass (the lodestone tracker), so it behaves as players expect.
 */
public class ShipCompassItem extends Item {
    public ShipCompassItem(Properties props) {
        super(props);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof ServerPlayer player) || level.getGameTime() % 20 != slot % 20) {
            return;
        }
        UUID shipId = Crews.get(player.serverLevel()).shipOf(player.getUUID());
        if (shipId == null) {
            if (stack.has(DataComponents.LODESTONE_TRACKER)) {
                stack.remove(DataComponents.LODESTONE_TRACKER);
            }
            return;
        }
        ServerSubLevel ship = ShipChoice.shipOf(player.server, shipId);
        HelmBlockEntity helm = ship == null ? null : Ships.helmOf(ship);
        if (helm == null) {
            return; // out of reach: keep pointing where she was last seen
        }
        GlobalPos at = GlobalPos.of(ship.getLevel().dimension(), BlockPos.containing(helm.worldPos()));
        LodestoneTracker now = stack.get(DataComponents.LODESTONE_TRACKER);
        if (now == null || now.target().isEmpty() || !now.target().get().equals(at)) {
            stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(at), false));
        }
        boolean inHand = player.getMainHandItem() == stack || player.getOffhandItem() == stack;
        if (inHand && !Notify.busy(player)) {
            Vec3 to = helm.worldPos().subtract(player.position());
            int blocks = (int) Math.round(Math.sqrt(to.x * to.x + to.z * to.z));
            if (blocks > 12) {   // nearer than that, she's in sight (or you're aboard)
                String name = ship.getName() == null || ship.getName().isBlank() ? "" : ship.getName();
                player.displayClientMessage(Component.translatable(name.isEmpty() ? "message.seafarers.compass_distance"
                        : "message.seafarers.compass_distance_named", blocks, name).withStyle(ChatFormatting.AQUA), true);
            }
        }
    }

    /** The needle moving isn't a new item: no hand bob each time her position is updated. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.seafarers.ships_compass.tip").withStyle(ChatFormatting.GRAY));
    }
}
