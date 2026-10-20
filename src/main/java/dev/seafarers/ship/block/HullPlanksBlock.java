package dev.seafarers.ship.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Ship hull. Dry planks are very buoyant (they stand in for the air inside a real hull); once a ship is
 * fully flooded every plank turns FLOODED, which has almost no buoyancy, and the ship goes down.
 * See data/seafarers/physics_block_properties/hull_planks.json.
 */
public class HullPlanksBlock extends Block {
    public static final BooleanProperty FLOODED = BooleanProperty.create("flooded");

    public HullPlanksBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(FLOODED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FLOODED);
    }
}
