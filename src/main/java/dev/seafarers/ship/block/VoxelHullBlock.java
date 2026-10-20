package dev.seafarers.ship.block;

import dev.seafarers.registry.ModSounds;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.MapCodec;
import dev.seafarers.Seafarers;
import dev.seafarers.ship.HelmBlockEntity;
import dev.seafarers.ship.Ships;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * One block of a voxel hull: a 4x4x4 grid of 4-pixel voxels picked from a palette written by
 * tools/ships/voxel.py (the masks in assets/seafarers/voxel_shapes.json, one model per entry). The collision
 * shape is the exact voxels, so Sable's physics and players on deck both see the real hull.
 * FACING is the ship's bow; HOLE is a cannonball hole, 0 none, 1 small, 2 large (each plank patches it one size
 * down); FLOODED darkens a sinking hull.
 */
public class VoxelHullBlock extends Block {
    public static final MapCodec<VoxelHullBlock> CODEC = simpleCodec(VoxelHullBlock::new);
    private static final int N = 4;
    private static final long[] MASKS = loadMasks();
    public static final IntegerProperty SHAPE = IntegerProperty.create("shape", 0, Math.max(1, MASKS.length - 1));
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty HOLE = IntegerProperty.create("hole", 0, 2);
    public static final BooleanProperty FLOODED = HullPlanksBlock.FLOODED;
    private static final VoxelShape[][] SHAPES = new VoxelShape[4][MASKS.length];

    public VoxelHullBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(SHAPE, 0).setValue(FACING, Direction.NORTH)
                .setValue(HOLE, 0).setValue(FLOODED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHAPE, FACING, HOLE, FLOODED);
    }

    private static long[] loadMasks() {
        try (InputStream in = VoxelHullBlock.class.getResourceAsStream("/assets/seafarers/voxel_shapes.json")) {
            if (in == null) {
                Seafarers.LOGGER.error("voxel_shapes.json missing; run tools/ships/*.py");
                return new long[]{-1L};
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray masks = json.getAsJsonArray("masks");
            long[] out = new long[masks.size()];
            for (int i = 0; i < out.length; i++) {
                // written as unsigned decimal strings; bit 63 may be set
                out[i] = new BigInteger(masks.get(i).getAsString()).longValue();
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Bad voxel_shapes.json", e);
        }
    }

    /** The voxels of a palette entry as a shape, for a ship whose bow points {@code facing}. */
    public static VoxelShape shapeOf(int id, Direction facing) {
        int turns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        VoxelShape cached = SHAPES[turns][id];
        if (cached != null) {
            return cached;
        }
        long mask = MASKS[id];
        int px = 16 / N;
        VoxelShape shape = Shapes.empty();
        for (int a = 0; a < N; a++) {
            for (int b = 0; b < N; b++) {
                for (int c = 0; c < N; c++) {
                    if ((mask >>> (a + N * (b + N * c)) & 1L) == 0) {
                        continue;
                    }
                    // canonical frame: starboard +x, bow north (-z)
                    double x0 = a * px, x1 = x0 + px;
                    double z0 = 16 - (c + 1) * px, z1 = z0 + px;
                    for (int t = 0; t < turns; t++) {
                        // a quarter turn clockwise seen from above: (x, z) -> (16 - z, x)
                        double nx0 = 16 - z1, nx1 = 16 - z0;
                        z0 = x0;
                        z1 = x1;
                        x0 = nx0;
                        x1 = nx1;
                    }
                    shape = Shapes.or(shape, Block.box(x0, b * px, z0, x1, b * px + px, z1));
                }
            }
        }
        shape = shape.optimize();
        SHAPES[turns][id] = shape;
        return shape;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapeOf(state.getValue(SHAPE), state.getValue(FACING));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(HOLE) == 0 || !stack.is(ItemTags.PLANKS)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel server) {
            level.setBlock(pos, state.setValue(HOLE, state.getValue(HOLE) - 1), 3);
            level.playSound(null, pos, ModSounds.PLANK_HAMMER.get(), SoundSource.BLOCKS, 1f, 1f);
            level.playSound(null, pos, SoundEvents.ANVIL_STEP, SoundSource.BLOCKS, 0.5f, 1.4f);
            server.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.1);
            stack.consume(1, player);
            HelmBlockEntity helm = Ships.helmOf(level, pos);
            if (helm != null) {
                helm.forceRescan();
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
