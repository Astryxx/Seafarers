package dev.seafarers.registry;

import dev.seafarers.Seafarers;
import dev.seafarers.ship.block.AnchorBlock;
import dev.seafarers.ship.block.BilgeWaterBlock;
import dev.seafarers.ship.block.CannonBlock;
import dev.seafarers.ship.block.DecorBlock;
import dev.seafarers.ship.block.HelmBlock;
import dev.seafarers.ship.block.HullHoleBlock;
import dev.seafarers.ship.block.HullPlanksBlock;
import dev.seafarers.ship.block.MastBlock;
import dev.seafarers.ship.block.RopeLadderBlock;
import dev.seafarers.ship.block.SparBlock;
import dev.seafarers.ship.block.SailRopeBlock;
import dev.seafarers.ship.block.SailYardBlock;
import dev.seafarers.ship.block.SupplyBarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Seafarers.MODID);

    private static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.0f, 3.0f).sound(SoundType.WOOD);
    }

    private static BlockBehaviour.Properties fitting() {
        return wood().noOcclusion();
    }

    // ---- hull ----
    /** Upper hull planking. Light and buoyant (see data/seafarers/physics_block_properties): it stands in for the air in a real hull. */
    public static final DeferredBlock<HullPlanksBlock> HULL_PLANKS = BLOCKS.register("hull_planks", () -> new HullPlanksBlock(wood()));
    /** Tarred planking below the waterline. */
    public static final DeferredBlock<HullPlanksBlock> HULL_BOTTOM = BLOCKS.register("hull_bottom", () -> new HullPlanksBlock(wood()));
    public static final DeferredBlock<StairBlock> HULL_BOTTOM_STAIRS = BLOCKS.register("hull_bottom_stairs",
            () -> new StairBlock(HULL_BOTTOM.get().defaultBlockState(), wood()));
    /** Gunwale and bulwark trim. */
    public static final DeferredBlock<Block> HULL_TRIM = BLOCKS.registerSimpleBlock("hull_trim", wood());
    public static final DeferredBlock<Block> DECK_PLANKS = BLOCKS.registerSimpleBlock("deck_planks", wood());
    public static final DeferredBlock<StairBlock> DECK_STAIRS = BLOCKS.register("deck_stairs",
            () -> new StairBlock(DECK_PLANKS.get().defaultBlockState(), wood()));
    public static final DeferredBlock<FenceBlock> RAILING = BLOCKS.register("railing", () -> new FenceBlock(wood()));
    public static final DeferredBlock<RotatedPillarBlock> KEEL = BLOCKS.register("keel", () -> new RotatedPillarBlock(wood()));
    /** A thin round spar: bowsprit, booms. */
    public static final DeferredBlock<RotatedPillarBlock> SPAR = BLOCKS.register("spar", () -> new SparBlock(fitting()));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> DECK_SLAB = BLOCKS.register("deck_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(wood()));
    public static final DeferredBlock<RopeLadderBlock> ROPE_LADDER = BLOCKS.register("rope_ladder",
            () -> new RopeLadderBlock(BlockBehaviour.Properties.of().strength(0.4f).sound(SoundType.LADDER).noOcclusion()));
    public static final DeferredBlock<dev.seafarers.ship.block.BowspritBlock> BOWSPRIT = BLOCKS.register("bowsprit",
            () -> new dev.seafarers.ship.block.BowspritBlock(fitting()));
    /** Invisible blocks along the bowsprit, so it can be walked out on (see BowspritSparBlock). */
    public static final DeferredBlock<dev.seafarers.ship.block.BowspritSparBlock> BOWSPRIT_SPAR = BLOCKS.register("bowsprit_spar",
            () -> new dev.seafarers.ship.block.BowspritSparBlock(fitting().noLootTable()));
    /** Voxel hull: one 4x4x4-voxel piece of a hull, its shape picked from the palette (see tools/ships/voxel.py). */
    public static final DeferredBlock<dev.seafarers.ship.block.VoxelHullBlock> VOXEL_HULL = BLOCKS.register("voxel_hull",
            () -> new dev.seafarers.ship.block.VoxelHullBlock(wood().noOcclusion()));
    public static final DeferredBlock<dev.seafarers.ship.block.ShipLanternBlock> SHIP_LANTERN = BLOCKS.register("ship_lantern",
            () -> new dev.seafarers.ship.block.ShipLanternBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.5f).sound(SoundType.LANTERN)
                    .lightLevel(s -> s.getValue(dev.seafarers.ship.block.ShipLanternBlock.LIT) ? 15 : 0).noOcclusion()));
    public static final DeferredBlock<dev.seafarers.ship.block.BucketRackBlock> BUCKET_RACK = BLOCKS.register("bucket_rack",
            () -> new dev.seafarers.ship.block.BucketRackBlock(fitting()));
    public static final DeferredBlock<dev.seafarers.ship.block.ShipFireBlock> SHIP_FIRE = BLOCKS.register("ship_fire",
            () -> new dev.seafarers.ship.block.ShipFireBlock(BlockBehaviour.Properties.of().mapColor(MapColor.FIRE).noCollission()
                    .instabreak().lightLevel(s -> 15).sound(SoundType.WOOL).noLootTable().replaceable()));
    public static final DeferredBlock<MastBlock> MAST = BLOCKS.register("mast", () -> new MastBlock(fitting()));
    /** A cannonball hole. Keeps its collision so crew can still walk on it, but adds no buoyancy and lets water in. */
    public static final DeferredBlock<HullHoleBlock> HULL_HOLE = BLOCKS.register("hull_hole",
            () -> new HullHoleBlock(wood().strength(1.0f).noOcclusion()));
    public static final DeferredBlock<BilgeWaterBlock> BILGE_WATER = BLOCKS.register("bilge_water",
            () -> new BilgeWaterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).noCollission().noOcclusion()
                    .strength(-1f, 3600000f).noLootTable().replaceable()));

    // ---- stations ----
    public static final DeferredBlock<HelmBlock> HELM = BLOCKS.register("helm", () -> new HelmBlock(fitting()));
    /** A rowboat's bench and oars: a helm rowed with W/S. */
    public static final DeferredBlock<dev.seafarers.ship.block.OarsBlock> OARS = BLOCKS.register("oars",
            () -> new dev.seafarers.ship.block.OarsBlock(fitting()));
    public static final DeferredBlock<dev.seafarers.ship.block.RowboatHookBlock> ROWBOAT_HOOK = BLOCKS.register("rowboat_hook",
            () -> new dev.seafarers.ship.block.RowboatHookBlock(fitting().noCollission()));
    public static final DeferredBlock<SailYardBlock> SAIL_YARD = BLOCKS.register("sail_yard", () -> new SailYardBlock(fitting()));
    /** Sail length cleat: lowers or raises the sail. */
    public static final DeferredBlock<SailRopeBlock> SAIL_ROPE = BLOCKS.register("sail_rope",
            () -> new SailRopeBlock(SailRopeBlock.Kind.HOIST, fitting().noCollission()));
    /** Sail angle pulley: swings the sail toward this rope's side of the ship. */
    public static final DeferredBlock<SailRopeBlock> BRACE_ROPE = BLOCKS.register("brace_rope",
            () -> new SailRopeBlock(SailRopeBlock.Kind.BRACE, fitting().noCollission()));
    public static final DeferredBlock<AnchorBlock> ANCHOR = BLOCKS.register("anchor", () -> new AnchorBlock(fitting()));
    public static final DeferredBlock<CannonBlock> CANNON = BLOCKS.register("cannon",
            () -> new CannonBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<SupplyBarrelBlock> SUPPLY_BARREL = BLOCKS.register("supply_barrel",
            () -> new SupplyBarrelBlock(fitting()));

    // ---- fittings ----
    public static final DeferredBlock<dev.seafarers.ship.block.HarpoonBlock> HARPOON = BLOCKS.register("harpoon",
            () -> new dev.seafarers.ship.block.HarpoonBlock(fitting()));
    public static final DeferredBlock<dev.seafarers.ship.block.MapTableBlock> MAP_TABLE = BLOCKS.register("map_table",
            () -> new dev.seafarers.ship.block.MapTableBlock(fitting()));
    public static final DeferredBlock<dev.seafarers.ship.block.VoyageTableBlock> VOYAGE_TABLE = BLOCKS.register("voyage_table",
            () -> new dev.seafarers.ship.block.VoyageTableBlock(fitting()));
    public static final DeferredBlock<DecorBlock> BINNACLE = BLOCKS.register("binnacle",
            () -> new DecorBlock(fitting(), Block.box(4, 0, 4, 12, 15, 12)));
    public static final DeferredBlock<dev.seafarers.ship.block.MattressBlock> MATTRESS = BLOCKS.register("mattress",
            () -> new dev.seafarers.ship.block.MattressBlock(fitting().sound(SoundType.WOOL)));
    public static final DeferredBlock<DecorBlock> FIGUREHEAD = BLOCKS.register("figurehead",
            () -> new DecorBlock(fitting(), Block.box(3, 0, 2, 13, 16, 14)));
    public static final DeferredBlock<dev.seafarers.ship.block.FlagBlock> FLAG = BLOCKS.register("flag",
            () -> new dev.seafarers.ship.block.FlagBlock(fitting().noCollission()));
    public static final DeferredBlock<dev.seafarers.ship.block.FlagBoxBlock> FLAG_BOX = BLOCKS.register("flag_box",
            () -> new dev.seafarers.ship.block.FlagBoxBlock(fitting()));
    public static final DeferredBlock<dev.seafarers.ship.block.StoveBlock> STOVE = BLOCKS.register("stove",
            () -> new dev.seafarers.ship.block.StoveBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(s -> s.getValue(dev.seafarers.ship.block.StoveBlock.LIT) ? 11 : 0)));

    /** Storage aboard, shaped like Sea of Thieves': ordinary containers otherwise. */
    public static final DeferredBlock<dev.seafarers.ship.block.StorageBlock> SEA_CHEST = BLOCKS.register("sea_chest",
            () -> dev.seafarers.ship.block.StorageBlock.chest(fitting()));
    public static final DeferredBlock<dev.seafarers.ship.block.StorageBlock> CASK = BLOCKS.register("cask",
            () -> dev.seafarers.ship.block.StorageBlock.cask(fitting()));
    public static final DeferredBlock<dev.seafarers.ship.block.ShipBellBlock> SHIP_BELL = BLOCKS.register("ship_bell",
            () -> new dev.seafarers.ship.block.ShipBellBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(2.0f)
                    .sound(SoundType.ANVIL).noOcclusion().noCollission()));

    /** The brig's barred door (Brigantine and Galleon): locked while one of her crew is in the brig. */
    public static final DeferredBlock<dev.seafarers.ship.block.BrigDoorBlock> BRIG_DOOR = BLOCKS.register("brig_door",
            () -> new dev.seafarers.ship.block.BrigDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));
    public static final DeferredBlock<dev.seafarers.ship.block.WaterBarrelBlock> WATER_BARREL = BLOCKS.register("water_barrel",
            () -> new dev.seafarers.ship.block.WaterBarrelBlock(fitting()));

    private ModBlocks() {}
}
