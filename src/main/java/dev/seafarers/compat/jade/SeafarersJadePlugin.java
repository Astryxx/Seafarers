package dev.seafarers.compat.jade;

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.ship.Captaincy;
import dev.seafarers.ship.Crews;
import dev.seafarers.ship.HelmBlockEntity;
import dev.seafarers.ship.Ships;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

import java.util.UUID;

/**
 * Jade (optional): look at any block of a ship to see her name, captain, crew, how much water she's taken on,
 * her holes and whether she's locked. Only loaded when Jade is installed; it can be switched off in Jade's
 * settings like any other tooltip.
 */
@WailaPlugin
public class SeafarersJadePlugin implements IWailaPlugin {
    static final ResourceLocation UID = Seafarers.id("ship");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(ShipData.INSTANCE, Block.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ShipTooltip.INSTANCE, Block.class);
    }

    /** Server side: the ship's state for the tooltip (captains and locks are only known to the server). */
    enum ShipData implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getLevel() instanceof ServerLevel level)) {
                return;
            }
            SubLevel ship = Ships.shipAt(level, accessor.getPosition());
            HelmBlockEntity helm = ship == null ? null : Ships.helmOf(ship);
            if (helm == null) {
                return;
            }
            CompoundTag t = new CompoundTag();
            Crews crews = Crews.get(level);
            UUID captain = crews.captainOf(ship.getUniqueId());
            t.putString("name", ship.getName() == null ? "" : ship.getName());
            t.putString("layout", helm.layout());
            t.putString("captain", captain == null ? "" : Captaincy.name(level.getServer(), captain));
            t.putInt("crew", crews.crewOf(ship.getUniqueId()).size());
            t.putInt("crewMax", helm.maxCrew());
            t.putFloat("water", helm.flood());
            t.putInt("holes", helm.holeCount());
            t.putBoolean("locked", crews.locked(ship.getUniqueId()));
            t.putBoolean("rowboat", helm.rowboat());
            data.put("seafarers_ship", t);
        }

        @Override
        public boolean shouldRequestData(BlockAccessor accessor) {
            return Ships.shipAt(accessor.getLevel(), accessor.getPosition()) != null;
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    /** Client side: the lines under the block's name. */
    enum ShipTooltip implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag t = accessor.getServerData().getCompound("seafarers_ship");
            if (t.isEmpty()) {
                return;
            }
            Component kind = Component.translatable("ship.seafarers." + t.getString("layout"));
            String name = t.getString("name");
            tooltip.add(name.isBlank() || name.equalsIgnoreCase(t.getString("layout")) ? kind.copy().withStyle(ChatFormatting.GOLD)
                    : Component.literal(name).withStyle(ChatFormatting.GOLD).append(Component.literal(" (").append(kind).append(")")
                    .withStyle(ChatFormatting.GRAY)));
            if (t.getBoolean("rowboat")) {
                return;
            }
            String captain = t.getString("captain");
            tooltip.add(captain.isEmpty() ? Component.translatable("jade.seafarers.no_captain").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("jade.seafarers.captain", captain));
            tooltip.add(Component.translatable("jade.seafarers.crew", t.getInt("crew"), t.getInt("crewMax")));
            float water = t.getFloat("water");
            int holes = t.getInt("holes");
            if (water > 0 || holes > 0) {
                tooltip.add(Component.translatable("jade.seafarers.damage", String.format("%.0f%%", water), holes)
                        .withStyle(water > 40 ? ChatFormatting.RED : ChatFormatting.GOLD));
            }
            if (!captain.isEmpty()) {
                tooltip.add(Component.translatable(t.getBoolean("locked") ? "jade.seafarers.locked" : "jade.seafarers.open")
                        .withStyle(t.getBoolean("locked") ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
