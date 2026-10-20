package dev.seafarers.registry;

import dev.seafarers.Seafarers;
import dev.seafarers.ship.deed.Deed;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Seafarers.MODID);

    /** On a deed given out by the ship choice screen: whose it is, and which ledger entry it is. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Deed>> DEED = COMPONENTS.registerComponentType("deed",
            b -> b.persistent(Deed.CODEC).networkSynchronized(Deed.STREAM_CODEC));

    private ModDataComponents() {}
}
