package dev.seafarers.ship;

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.registry.ModBlocks;
import dev.seafarers.ship.block.ShipFireBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Fire aboard ship. A firebomb starts it; each second the helm (see tick) lets every fire grow, spread to the
 * open spots next to it that have a deck or hull to burn, and, once it has burned fierce for a while, burn a hole
 * in the hull under or beside it. Water thrown from a bucket puts it out. It never spreads off the ship.
 */
public final class ShipFire {
    /** Chance per second that a fire grows one stage, spreads, or (at full size) burns a hole. */
    public static double GROW = 0.35;
    public static double SPREAD = 0.12;
    public static double BURN_THROUGH = 0.06;

    /** Lights a fire on the open side of the ship block that was hit. */
    public static boolean ignite(ServerLevel level, BlockPos hitPos, Direction face) {
        for (BlockPos p : List.of(hitPos.relative(face), hitPos.above(), hitPos)) {
            if (canBurnAt(level, p)) {
                level.setBlock(p, ModBlocks.SHIP_FIRE.get().defaultBlockState(), Block.UPDATE_ALL);
                level.playSound(null, p, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.2f, 0.8f);
                Seafarers.LOGGER.info("PROBE fire lit at {}", p.toShortString());
                HelmBlockEntity helm = Ships.helmOf(level, p);
                if (helm != null) {
                    helm.forceRescan();
                }
                return true;
            }
        }
        return false;
    }

    /** An empty spot on a ship with something flammable (hull, deck, fittings) under or beside it. */
    public static boolean canBurnAt(ServerLevel level, BlockPos p) {
        if (Ships.shipAt(level, p) == null || !level.getBlockState(p).isAir()) {
            return false;
        }
        for (Direction d : Direction.values()) {
            if (d != Direction.UP && burns(level.getBlockState(p.relative(d)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean burns(BlockState state) {
        // the hull, decks, stairs, slabs and every other wooden part of a ship
        return state.is(ModBlocks.VOXEL_HULL.get()) || state.is(SeafarersTags.HULL) || state.is(ModBlocks.DECK_PLANKS.get())
                || state.ignitedByLava() || state.getSoundType() == net.minecraft.world.level.block.SoundType.WOOD;
    }

    /** One second of every fire on a ship. */
    public static void tick(ServerLevel level, HelmBlockEntity helm, List<BlockPos> fires) {
        RandomSource rng = level.random;
        for (BlockPos p : fires) {
            BlockState state = level.getBlockState(p);
            if (!state.is(ModBlocks.SHIP_FIRE.get())) {
                continue;
            }
            int age = state.getValue(ShipFireBlock.AGE);
            double pace = dev.seafarers.SeafarersConfig.FIRE_SPREAD_MULTIPLIER.get();
            if (age < 3 && rng.nextDouble() < GROW * pace) {
                level.setBlock(p, state.setValue(ShipFireBlock.AGE, age + 1), Block.UPDATE_CLIENTS);
            }
            if (age >= 1 && rng.nextDouble() < SPREAD * age * pace) {
                Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(rng);
                BlockPos next = p.relative(d);
                for (BlockPos q : List.of(next, next.above(), next.below())) {
                    if (canBurnAt(level, q)) {
                        level.setBlock(q, ModBlocks.SHIP_FIRE.get().defaultBlockState(), Block.UPDATE_ALL);
                        break;
                    }
                }
            }
            if (age == 3 && rng.nextDouble() < BURN_THROUGH * pace) {
                // burned too long: it eats through the planking under or beside it
                for (Direction d : List.of(Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
                    BlockPos hull = p.relative(d);
                    if (HullDamage.punch(level, hull, level.getBlockState(hull))) {
                        Seafarers.LOGGER.info("PROBE fire burned through at {}", hull.toShortString());
                        helm.forceRescan();
                        break;
                    }
                }
            }
        }
    }

    /** Puts out every ship fire within radius of a world point (a thrown bucket of water landing). */
    public static int douse(ServerLevel level, Vec3 world, double radius) {
        int out = 0;
        for (SubLevel ship : dev.ryanhcode.sable.Sable.HELPER.getAllIntersecting(level,
                new dev.ryanhcode.sable.companion.math.BoundingBox3d(world.x - radius, world.y - radius, world.z - radius,
                        world.x + radius, world.y + radius, world.z + radius))) {
            Vec3 local = ship.logicalPose().transformPositionInverse(world);
            BlockPos c = BlockPos.containing(local);
            int r = (int) Math.ceil(radius);
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
                if (level.getBlockState(p).is(ModBlocks.SHIP_FIRE.get()) && Vec3.atCenterOf(p).distanceTo(local) <= radius + 0.5) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    level.sendParticles(ParticleTypes.CLOUD, world.x, world.y, world.z, 6, 0.3, 0.2, 0.3, 0.02);
                    out++;
                }
            }
            HelmBlockEntity helm = Ships.helmOf(ship);
            if (helm != null && out > 0) {
                helm.forceRescan();
            }
        }
        if (out > 0) {
            level.playSound(null, world.x, world.y, world.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 1f);
            Seafarers.LOGGER.info("PROBE doused {} fires", out);
        }
        return out;
    }

    private ShipFire() {}
}
