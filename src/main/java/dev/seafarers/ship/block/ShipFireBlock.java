package dev.seafarers.ship.block;

import com.mojang.serialization.MapCodec;
import dev.seafarers.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Fire burning on a ship. It only ever exists on ships: the helm grows it, spreads it across the ship's own decks
 * and burns holes where it has burned too long (see ShipFire); it never touches the world. A thrown bucket of
 * water puts it out. AGE is how fierce it is (0..3).
 */
public class ShipFireBlock extends Block {
    public static final MapCodec<ShipFireBlock> CODEC = simpleCodec(ShipFireBlock::new);
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);

    public ShipFireBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Block.box(0, 0, 0, 16, 4 + state.getValue(AGE) * 4, 16);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!entity.fireImmune()) {
            entity.igniteForSeconds(4);
            entity.hurt(level.damageSources().inFire(), 1f + state.getValue(AGE) * 0.5f);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(18) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ModSounds.FIRE_BURN.get(),
                    SoundSource.BLOCKS, 0.6f + state.getValue(AGE) * 0.15f, 0.9f + random.nextFloat() * 0.2f, false);
        }
        for (int i = 0; i <= state.getValue(AGE); i++) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 0.6 + random.nextDouble() * 0.5,
                    pos.getZ() + random.nextDouble(), 0, 0.05, 0);
        }
    }
}
