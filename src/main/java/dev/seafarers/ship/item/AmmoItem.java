package dev.seafarers.ship.item;

import dev.seafarers.ship.Ammo;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Cannon ammunition. Use it on a cannon to load it. */
public class AmmoItem extends Item {
    public final Ammo ammo;

    public AmmoItem(Ammo ammo, Properties props) {
        super(props);
        this.ammo = ammo;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(this.getDescriptionId() + ".tip").withStyle(ChatFormatting.GRAY));
    }
}
