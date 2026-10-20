package dev.seafarers.registry;

import dev.seafarers.Seafarers;
import dev.seafarers.ship.HelmBlockEntity;
import dev.seafarers.ship.block.CannonBlockEntity;
import dev.seafarers.ship.block.SailYardBlockEntity;
import dev.seafarers.ship.block.SupplyBarrelBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("DataFlowIssue")
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Seafarers.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HelmBlockEntity>> HELM = BLOCK_ENTITIES.register("helm",
            () -> BlockEntityType.Builder.of(HelmBlockEntity::new, ModBlocks.HELM.get(), ModBlocks.OARS.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SailYardBlockEntity>> SAIL_YARD = BLOCK_ENTITIES.register("sail_yard",
            () -> BlockEntityType.Builder.of(SailYardBlockEntity::new, ModBlocks.SAIL_YARD.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CannonBlockEntity>> CANNON = BLOCK_ENTITIES.register("cannon",
            () -> BlockEntityType.Builder.of(CannonBlockEntity::new, ModBlocks.CANNON.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SupplyBarrelBlockEntity>> SUPPLY_BARREL = BLOCK_ENTITIES.register("supply_barrel",
            () -> BlockEntityType.Builder.of(SupplyBarrelBlockEntity::new, ModBlocks.SUPPLY_BARREL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.seafarers.ship.block.BowspritBlock.Heel>> BOWSPRIT = BLOCK_ENTITIES.register("bowsprit",
            () -> BlockEntityType.Builder.of(dev.seafarers.ship.block.BowspritBlock.Heel::new, ModBlocks.BOWSPRIT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.seafarers.ship.block.AnchorBlock.Capstan>> CAPSTAN = BLOCK_ENTITIES.register("capstan",
            () -> BlockEntityType.Builder.of(dev.seafarers.ship.block.AnchorBlock.Capstan::new, ModBlocks.ANCHOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.seafarers.ship.block.HarpoonBlockEntity>> HARPOON = BLOCK_ENTITIES.register("harpoon",
            () -> BlockEntityType.Builder.of(dev.seafarers.ship.block.HarpoonBlockEntity::new, ModBlocks.HARPOON.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.seafarers.ship.block.FlagBlock.Flag>> FLAG = BLOCK_ENTITIES.register("flag",
            () -> BlockEntityType.Builder.of(dev.seafarers.ship.block.FlagBlock.Flag::new, ModBlocks.FLAG.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.seafarers.ship.block.StoveBlock.Stove>> STOVE = BLOCK_ENTITIES.register("stove",
            () -> BlockEntityType.Builder.of(dev.seafarers.ship.block.StoveBlock.Stove::new, ModBlocks.STOVE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.seafarers.ship.block.MapTableBlock.Table>> MAP_TABLE = BLOCK_ENTITIES.register("map_table",
            () -> BlockEntityType.Builder.of(dev.seafarers.ship.block.MapTableBlock.Table::new, ModBlocks.MAP_TABLE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.seafarers.ship.block.VoyageTableBlock.Table>> VOYAGE_TABLE = BLOCK_ENTITIES.register("voyage_table",
            () -> BlockEntityType.Builder.of(dev.seafarers.ship.block.VoyageTableBlock.Table::new, ModBlocks.VOYAGE_TABLE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.seafarers.ship.block.StorageBlock.Storage>> STORAGE = BLOCK_ENTITIES.register("storage",
            () -> BlockEntityType.Builder.of(dev.seafarers.ship.block.StorageBlock.Storage::new, ModBlocks.SEA_CHEST.get(), ModBlocks.CASK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<dev.seafarers.ship.block.WaterBarrelBlock.Barrel>> WATER_BARREL = BLOCK_ENTITIES.register("water_barrel",
            () -> BlockEntityType.Builder.of(dev.seafarers.ship.block.WaterBarrelBlock.Barrel::new, ModBlocks.WATER_BARREL.get()).build(null));

    /** The ship's bell is a vanilla bell: it uses vanilla's bell block entity (and so its renderer and its swing). */
    public static void bellsOnShips(net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent event) {
        event.modify(net.minecraft.world.level.block.entity.BlockEntityType.BELL, ModBlocks.SHIP_BELL.get());
    }

    private ModBlockEntities() {}
}
