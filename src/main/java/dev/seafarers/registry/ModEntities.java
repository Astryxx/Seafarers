package dev.seafarers.registry;

import dev.seafarers.Seafarers;
import dev.seafarers.ship.CannonballEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Seafarers.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CannonballEntity>> CANNONBALL = ENTITIES.register("cannonball",
            () -> EntityType.Builder.<CannonballEntity>of(CannonballEntity::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f).clientTrackingRange(8).updateInterval(1)
                    .build("cannonball"));

    public static final DeferredHolder<EntityType<?>, EntityType<dev.seafarers.ship.SplashEntity>> SPLASH = ENTITIES.register("splash",
            () -> EntityType.Builder.<dev.seafarers.ship.SplashEntity>of(dev.seafarers.ship.SplashEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(6).updateInterval(1).build("splash"));

    public static final DeferredHolder<EntityType<?>, EntityType<dev.seafarers.ship.HarpoonEntity>> HARPOON = ENTITIES.register("harpoon",
            () -> EntityType.Builder.<dev.seafarers.ship.HarpoonEntity>of(dev.seafarers.ship.HarpoonEntity::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f).clientTrackingRange(10).updateInterval(1).build("harpoon"));

    public static final DeferredHolder<EntityType<?>, EntityType<dev.seafarers.ship.ShipwreckBarrelEntity>> SHIPWRECK_BARREL = ENTITIES.register("shipwreck_barrel",
            () -> EntityType.Builder.<dev.seafarers.ship.ShipwreckBarrelEntity>of(dev.seafarers.ship.ShipwreckBarrelEntity::new, MobCategory.MISC)
                    .sized(0.9f, 0.9f).clientTrackingRange(10).updateInterval(2).build("shipwreck_barrel"));

    /** Comes to take a sailor adrift back to their ship (see Mermaids). Her box covers what's above the water. */
    public static final DeferredHolder<EntityType<?>, EntityType<dev.seafarers.ship.MermaidEntity>> MERMAID = ENTITIES.register("mermaid",
            () -> EntityType.Builder.<dev.seafarers.ship.MermaidEntity>of(dev.seafarers.ship.MermaidEntity::new, MobCategory.MISC)
                    .sized(0.8f, 1.4f).clientTrackingRange(10).updateInterval(2).build("mermaid"));

    private ModEntities() {}
}
