package dev.seafarers.ship.item;

import dev.seafarers.ship.Grog;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/** A tankard of grog. Drink up. */
/** Grog to drink: a bottle (one drink, the bottle comes back) or a bucket (two drinks, the bucket comes back). */
public class GrogItem extends Item {
    private final int drinks;
    private final java.util.function.Supplier<Item> empty;

    public GrogItem(Properties props) {
        this(props, 1, () -> net.minecraft.world.item.Items.GLASS_BOTTLE);
    }

    public GrogItem(Properties props, int drinks, java.util.function.Supplier<Item> empty) {
        super(props);
        this.drinks = drinks;
        this.empty = empty;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public net.minecraft.sounds.SoundEvent getDrinkingSound() {
        return net.minecraft.sounds.SoundEvents.GENERIC_DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 24;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity drinker) {
        if (!level.isClientSide) {
            for (int i = 0; i < this.drinks; i++) {
                Grog.drink(drinker);
            }
            level.playSound(null, drinker.blockPosition(), dev.seafarers.registry.ModSounds.GROG_DRINK.get(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
        }
        if (drinker instanceof Player p) {
            return net.minecraft.world.item.ItemUtils.createFilledResult(stack, p, new ItemStack(this.empty.get()), false);
        }
        stack.shrink(1);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.seafarers.grog.tip", this.drinks).withStyle(ChatFormatting.GRAY));
    }
}
