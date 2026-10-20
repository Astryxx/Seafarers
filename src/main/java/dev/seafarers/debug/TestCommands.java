package dev.seafarers.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.seafarers.Seafarers;
import dev.seafarers.registry.ModBlocks;
import dev.seafarers.ship.Ammo;
import dev.seafarers.ship.Crews;
import dev.seafarers.ship.HelmBlockEntity;
import dev.seafarers.ship.HullDamage;
import dev.seafarers.ship.SeafarersTags;
import dev.seafarers.ship.ShipBuilder;
import dev.seafarers.ship.Ships;
import dev.seafarers.ship.Wind;
import dev.seafarers.ship.block.CannonBlock;
import dev.seafarers.ship.block.CannonBlockEntity;
import dev.seafarers.ship.block.HelmBlock;
import dev.seafarers.ship.block.MastBlock;
import dev.seafarers.ship.block.SailRopeBlock;
import dev.seafarers.ship.block.SupplyBarrelBlock;
import dev.seafarers.ship.block.SupplyBarrelBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Test scenes and crew actions for checking ships from the console (op only). Crew actions go through the same
 * code a player's click does, using a stand-in player, so tools/test_ship.py exercises the real paths.
 * Results go to the log as PROBE lines. Ship commands act on the ship whose helm is nearest the command's
 * position (use {@code execute positioned ... run}).
 */
@EventBusSubscriber(modid = Seafarers.MODID)
public final class TestCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (!dev.seafarers.SeafarersConfig.DEBUG_COMMANDS.get()) {
            return; // development commands are off unless the config turns them on
        }
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> ship = Commands.literal("ship")
                .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("probe").then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                        .executes(c -> withHelm(c, (helm, s) -> Probe.start("ship", shipOf(helm), 20, IntegerArgumentType.getInteger(c, "seconds") * 20)))))
                .then(Commands.literal("trace").then(Commands.argument("steps", IntegerArgumentType.integer(1, 2000))
                        .executes(c -> withHelm(c, (helm, s) -> helm.trace = IntegerArgumentType.getInteger(c, "steps")))))
                .then(Commands.literal("keep").executes(c -> withHelm(c, (helm, s) -> SubLevelContainer.getContainer(s.getLevel())
                        .addForceLoadTicket(shipOf(helm), dev.seafarers.ship.ShipTickets.TEST, net.minecraft.util.Unit.INSTANCE))))
                .then(Commands.literal("remove").executes(c -> withHelm(c, (helm, s) -> {
                    Crews.get(s.getLevel()).forgetShip(shipOf(helm).getUniqueId());
                    SubLevelContainer.getContainer(s.getLevel()).removeSubLevel(shipOf(helm), SubLevelRemovalReason.REMOVED);
                    Seafarers.LOGGER.info("PROBE removed ship");
                })))
                .then(Commands.literal("controls")
                        .then(Commands.argument("wheel", FloatArgumentType.floatArg(-1, 1))
                                .then(Commands.argument("sails", IntegerArgumentType.integer(0, 3))
                                        .then(Commands.argument("sailAngle", FloatArgumentType.floatArg(-45, 45))
                                                .then(Commands.argument("anchored", BoolArgumentType.bool())
                                                        .executes(c -> withHelm(c, (helm, s) -> helm.setControls(
                                                                FloatArgumentType.getFloat(c, "wheel"), IntegerArgumentType.getInteger(c, "sails"),
                                                                FloatArgumentType.getFloat(c, "sailAngle"), BoolArgumentType.getBool(c, "anchored")))))))))
                // crew actions through the real blocks
                .then(Commands.literal("hoist").then(Commands.argument("mast", IntegerArgumentType.integer(0, 2))
                        .then(Commands.argument("times", IntegerArgumentType.integer(-30, 30)).executes(TestCommands::hoist))))
                .then(Commands.literal("brace").then(Commands.argument("mast", IntegerArgumentType.integer(0, 2))
                        .then(Commands.argument("side", StringArgumentType.word())
                                .then(Commands.argument("times", IntegerArgumentType.integer(1, 20)).executes(TestCommands::brace)))))
                .then(Commands.literal("capstan").then(Commands.argument("times", IntegerArgumentType.integer(1, 200))
                        .executes(c -> withHelm(c, (helm, src) -> {
                            // like holding use: one push now and one every 4 ticks after, as vanilla repeats it
                            BlockPos cap = find(helm, s -> s.is(ModBlocks.ANCHOR.get())).getFirst();
                            FakePlayer hand = testHand(src.getLevel());
                            hand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                            int times = IntegerArgumentType.getInteger(c, "times");
                            use(src.getLevel(), cap, hand, false);
                            for (int i = 1; i < times; i++) {
                                TestTasks.after(4 * i, () -> use(src.getLevel(), cap, hand, false));
                            }
                        }))))
                .then(Commands.literal("helm").then(Commands.argument("steer", FloatArgumentType.floatArg(-1, 1))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 400)).executes(TestCommands::helm))))
                .then(Commands.literal("hole").then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                        .executes(c -> holes(c.getSource(), IntegerArgumentType.getInteger(c, "count"), 1))
                        .then(Commands.literal("large").executes(c -> holes(c.getSource(), IntegerArgumentType.getInteger(c, "count"), 2)))
                        .then(Commands.literal("upper").executes(c -> upperHoles(c.getSource(), IntegerArgumentType.getInteger(c, "count"))))))
                // a player in creative tries to break the block in front of the helm (unbreakableShips)
                .then(Commands.literal("trybreak").executes(c -> withHelm(c, (helm, src) -> {
                    FakePlayer hand = testHand(src.getLevel());
                    hand.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    BlockPos at = helm.getBlockPos().relative(helm.getBlockState().getValue(HelmBlock.FACING).getOpposite()).below();
                    String before = src.getLevel().getBlockState(at).toString();
                    boolean broke = hand.gameMode.destroyBlock(at);
                    hand.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    Seafarers.LOGGER.info("PROBE trybreak {} broke={} now={}", before, broke, src.getLevel().getBlockState(at).isAir() ? "air" : "kept");
                })))
                .then(Commands.literal("grog").executes(TestCommands::grog))
                .then(Commands.literal("splash").executes(TestCommands::splashFire))
                .then(Commands.literal("bail").then(Commands.argument("buckets", IntegerArgumentType.integer(1, 100)).executes(TestCommands::bail)))
                .then(Commands.literal("patch").executes(TestCommands::patch))
                .then(Commands.literal("mendmast").then(Commands.argument("mast", IntegerArgumentType.integer(0, 2)).executes(TestCommands::mendMast)))
                .then(Commands.literal("fire")
                        .then(Commands.argument("side", StringArgumentType.word())
                                .then(Commands.argument("ammo", StringArgumentType.word())
                                        .then(Commands.argument("pitch", FloatArgumentType.floatArg(-10, 35)).executes(TestCommands::fire)))))
                .then(Commands.literal("fireat")
                        .then(Commands.argument("side", StringArgumentType.word())
                                .then(Commands.argument("ammo", StringArgumentType.word())
                                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg()).executes(TestCommands::fireAt)))))))
                .then(Commands.literal("supplies").executes(TestCommands::supplies))
                .then(Commands.literal("bed").then(Commands.argument("player", EntityArgument.player()).executes(TestCommands::bed)))
                .then(Commands.literal("paint").then(Commands.argument("what", StringArgumentType.word())
                        .then(Commands.argument("dye", StringArgumentType.word()).executes(TestCommands::paint))))
                .then(Commands.literal("ladders").executes(TestCommands::ladders))
                .then(Commands.literal("dumpshapes").executes(TestCommands::dumpShapes))
                .then(Commands.literal("where").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    HelmBlockEntity helm = nearestHelm(c.getSource());
                    if (helm == null) return 0;
                    var player = EntityArgument.getPlayer(c, "player");
                    ServerLevel level = c.getSource().getLevel();
                    Vec3 local = shipOf(helm).logicalPose().transformPositionInverse(player.position())
                            .subtract(Vec3.atBottomCenterOf(helm.getBlockPos()));
                    Direction bow = helm.getBlockState().getValue(HelmBlock.FACING);
                    Direction stbd = bow.getClockWise();
                    double sx = local.x * stbd.getStepX() + local.z * stbd.getStepZ();
                    // in ship coordinates: from the helm's own cell in her layout
                    var layout = dev.seafarers.ship.ShipLayout.load(level.getServer(), helm.layout());
                    var cell = layout == null ? null : layout.cells().stream().filter(k -> k.state().startsWith("seafarers:helm")).findFirst().orElse(null);
                    int hy = cell == null ? 6 : cell.y(), hz = cell == null ? 8 : cell.z();
                    double sz = local.x * bow.getStepX() + local.z * bow.getStepZ() + hz + 0.5;
                    Seafarers.LOGGER.info("PROBE where {} {} {} ground={}", String.format("%.2f", sx), String.format("%.2f", local.y + hy),
                            String.format("%.2f", sz), player.onGround());
                    return 1;
                })))
                .then(Commands.literal("water").executes(TestCommands::water))
                .then(Commands.literal("stovebad").executes(c -> withHelm(c, (helm, src) -> {
                    // a stick isn't food: the stove mustn't take it
                    ServerLevel level = src.getLevel();
                    FakePlayer hand = testHand(level);
                    hand.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.STICK));
                    BlockPos stove = find(helm, s -> s.is(ModBlocks.STOVE.get())).getFirst();
                    use(level, stove, hand, false);
                    boolean refused = ((dev.seafarers.ship.block.StoveBlock.Stove) level.getBlockEntity(stove)).food().isEmpty()
                            && hand.getMainHandItem().is(net.minecraft.world.item.Items.STICK);
                    Seafarers.LOGGER.info("PROBE stove refused={}", refused);
                    hand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                })))
                .then(Commands.literal("voyagegui").executes(TestCommands::voyageGui))
                // a player takes a station as if they'd used it: helm, cannon, harpoon, hoist (the sail rope) or brace
                .then(Commands.literal("take").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("what", StringArgumentType.word()).executes(c -> withHelm(c, (helm, src) -> {
                            ServerLevel level = src.getLevel();
                            net.minecraft.server.level.ServerPlayer player;
                            try {
                                player = EntityArgument.getPlayer(c, "player");
                            } catch (CommandSyntaxException e) {
                                return;
                            }
                            switch (StringArgumentType.getString(c, "what")) {
                                case "helm" -> helm.takeHelm(player);
                                case "cannon" -> {
                                    BlockPos at = find(helm, st -> st.is(ModBlocks.CANNON.get()) && st.getValue(dev.seafarers.ship.block.MultiPart.PART) == 0).getFirst();
                                    ((dev.seafarers.ship.block.CannonBlockEntity) level.getBlockEntity(at)).takeOver(player);
                                }
                                case "harpoon" -> {
                                    BlockPos at = find(helm, st -> st.is(ModBlocks.HARPOON.get())).getFirst();
                                    ((dev.seafarers.ship.block.HarpoonBlockEntity) level.getBlockEntity(at)).takeOver(player);
                                }
                                default -> {
                                    var block = StringArgumentType.getString(c, "what").equals("brace") ? ModBlocks.BRACE_ROPE.get() : ModBlocks.SAIL_ROPE.get();
                                    BlockPos at = find(helm, st -> st.is(block)).getFirst();
                                    dev.seafarers.ship.RopeStations.take(player, at, level.getBlockState(at));
                                }
                            }
                        })))))
                // a button on the quest table's screen, pressed by a player; "hand" in the text means the test hand's UUID
                .then(Commands.literal("table").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("action", IntegerArgumentType.integer(0, 20)).then(Commands.argument("index", IntegerArgumentType.integer())
                                .then(Commands.argument("text", StringArgumentType.word()).executes(c -> {
                                    HelmBlockEntity helm = nearestHelm(c.getSource());
                                    if (helm == null) return 0;
                                    ServerLevel level = c.getSource().getLevel();
                                    BlockPos t = find(helm, st -> st.is(ModBlocks.VOYAGE_TABLE.get())).getFirst();
                                    String text = StringArgumentType.getString(c, "text");
                                    if (text.equals("hand")) {
                                        text = testHand(level).getUUID().toString();
                                    }
                                    ((dev.seafarers.ship.block.VoyageTableBlock.Table) level.getBlockEntity(t)).act(level,
                                            EntityArgument.getPlayer(c, "player"), IntegerArgumentType.getInteger(c, "action"),
                                            IntegerArgumentType.getInteger(c, "index"), text);
                                    Seafarers.LOGGER.info("PROBE table action {} done", IntegerArgumentType.getInteger(c, "action"));
                                    return 1;
                                }))))))
                // what the ship's lock and captain are
                .then(Commands.literal("owner").executes(c -> withHelm(c, (helm, src) -> {
                    var crews = Crews.get(src.getLevel());
                    var id = shipOf(helm).getUniqueId();
                    var b = shipOf(helm).getPlot().getBoundingBox();
                    int blocks = 0;
                    BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
                    for (int x = b.minX(); x <= b.maxX(); x++) {
                        for (int y = b.minY(); y <= b.maxY(); y++) {
                            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                                blocks += src.getLevel().getBlockState(p.set(x, y, z)).isAir() ? 0 : 1;
                            }
                        }
                    }
                    Seafarers.LOGGER.info("PROBE owner name={} blocks={} captain={} locked={} crew={}", String.valueOf(shipOf(helm).getName()).replace(' ', '_'),
                            blocks, crews.captainOf(id), crews.locked(id), crews.crewOf(id));
                })))
                .then(Commands.literal("open").then(Commands.argument("what", StringArgumentType.word())
                        .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                            HelmBlockEntity helm = nearestHelm(c.getSource());
                            if (helm == null) return 0;
                            ServerLevel level = c.getSource().getLevel();
                            var player = EntityArgument.getPlayer(c, "player");
                            boolean voyage = "voyage".equals(StringArgumentType.getString(c, "what"));
                            BlockPos t = find(helm, st -> st.is(voyage ? ModBlocks.VOYAGE_TABLE.get() : ModBlocks.MAP_TABLE.get())).getFirst();
                            if (voyage) {
                                ((dev.seafarers.ship.block.VoyageTableBlock.Table) level.getBlockEntity(t)).open(level, player);
                            } else {
                                ((dev.seafarers.ship.block.MapTableBlock.Table) level.getBlockEntity(t)).show(level, player);
                            }
                            return 1;
                        }))))
                .then(Commands.literal("lanterns").executes(TestCommands::lanterns)
                        .then(Commands.literal("toggle").executes(c -> withHelm(c, (helm, src) -> {
                            FakePlayer hand = testHand(src.getLevel());
                            hand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                            find(helm, s -> s.is(ModBlocks.SHIP_LANTERN.get())).forEach(p -> use(src.getLevel(), p, hand, false));
                        }))))
                .then(Commands.literal("flag").executes(c -> flag(c, true)).then(Commands.literal("lower").executes(c -> flag(c, false))))
                .then(Commands.literal("stove").then(Commands.argument("ticks", IntegerArgumentType.integer(1, 2000)).executes(TestCommands::stove)))
                .then(Commands.literal("maptable").executes(TestCommands::mapTable))
                .then(Commands.literal("voyage").executes(TestCommands::voyage))
                .then(Commands.literal("row").then(Commands.argument("forward", FloatArgumentType.floatArg(-1, 1))
                        .then(Commands.argument("steer", FloatArgumentType.floatArg(-1, 1))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 2000)).executes(TestCommands::row)))))
                .then(Commands.literal("dock").then(Commands.argument("range", DoubleArgumentType.doubleArg(1, 64)).executes(TestCommands::dock)))
                .then(Commands.literal("harpoon")
                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("reelTicks", IntegerArgumentType.integer(0, 2000)).executes(TestCommands::harpoon))))))
                .then(Commands.literal("gunner").then(Commands.argument("side", StringArgumentType.word()).executes(TestCommands::gunner)))
                // the brig: named stand-in crewmates, votes, using a block as a player, and the brig's state
                .then(Commands.literal("hand").then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> crew(c, hand(c.getSource().getLevel(), StringArgumentType.getString(c, "name"))))))
                .then(Commands.literal("brigvote").then(Commands.argument("voter", StringArgumentType.word())
                        .then(Commands.argument("target", StringArgumentType.word()).then(Commands.argument("lock", BoolArgumentType.bool())
                                .executes(c -> {
                                    ServerLevel level = c.getSource().getLevel();
                                    ServerPlayer voter = named(level, StringArgumentType.getString(c, "voter"));
                                    ServerPlayer target = named(level, StringArgumentType.getString(c, "target"));
                                    boolean ok = dev.seafarers.ship.Brig.vote(voter, target.getUUID(), BoolArgumentType.getBool(c, "lock"),
                                            (message, success) -> Seafarers.LOGGER.info("PROBE brig reply ok={} {}", success, message.getString()));
                                    return ok ? 1 : 0;
                                })))))
                .then(Commands.literal("mermaid").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    // a mermaid for pictures, a few blocks off the player's bow side, facing them; she stays until killed
                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                    ServerLevel level = c.getSource().getLevel();
                    Vec3 at = c.getSource().getPosition();
                    var m = dev.seafarers.ship.MermaidEntity.of(level, at, p);
                    m.still = true;
                    level.addFreshEntity(m);
                    return 1;
                })))
                .then(Commands.literal("useat").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("what", StringArgumentType.word()).executes(TestCommands::useAt))))
                .then(Commands.literal("brigstate").executes(TestCommands::brigState))
                .then(Commands.literal("crew")
                        .executes(c -> crew(c, testHand(c.getSource().getLevel())))
                        .then(Commands.argument("player", EntityArgument.player()).executes(c -> crew(c, EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("view")
                        .then(Commands.argument("x", FloatArgumentType.floatArg()).then(Commands.argument("y", FloatArgumentType.floatArg())
                                .then(Commands.argument("z", FloatArgumentType.floatArg()).then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                        .then(Commands.argument("pitch", FloatArgumentType.floatArg()).executes(TestCommands::view)))))));

        d.register(Commands.literal("seafarers").requires(s -> s.hasPermission(2))
                .then(Commands.literal("drive").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 400))
                                .then(Commands.argument("sneak", BoolArgumentType.bool())
                                        .then(Commands.argument("jump", BoolArgumentType.bool()).executes(c -> {
                                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(EntityArgument.getPlayer(c, "player"),
                                                    new dev.seafarers.network.TestDrivePayload(IntegerArgumentType.getInteger(c, "ticks"),
                                                            BoolArgumentType.getBool(c, "sneak"), BoolArgumentType.getBool(c, "jump")));
                                            return 1;
                                        }))))))
                // the client logs this many frames of the ship and camera (see TestDrive)
                .then(Commands.literal("frames").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 5000)).executes(c -> {
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(EntityArgument.getPlayer(c, "player"),
                                    new dev.seafarers.network.TestDrivePayload(-IntegerArgumentType.getInteger(c, "count"), false, false));
                            return 1;
                        }))))
                .then(Commands.literal("test").then(Commands.literal("sea").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(c -> buildSea(c.getSource(), BlockPosArgument.getBlockPos(c, "pos"))))))
                .then(Commands.literal("launch").then(Commands.argument("layout", StringArgumentType.word())
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("bow", StringArgumentType.word())
                                        .executes(c -> launch(c, StringArgumentType.getString(c, "layout"),
                                                Direction.byName(StringArgumentType.getString(c, "bow"))))))))
                .then(Commands.literal("sloop").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(c -> launch(c, "sloop", Direction.NORTH))
                        .then(Commands.argument("bow", StringArgumentType.word())
                                .executes(c -> launch(c, "sloop", Direction.byName(StringArgumentType.getString(c, "bow")))))))
                .then(Commands.literal("wind")
                        .then(Commands.literal("unlock").executes(c -> {
                            Wind.get(c.getSource().getLevel()).set(null);
                            return 1;
                        }))
                        .then(Commands.argument("angle", FloatArgumentType.floatArg(-360, 360)).executes(c -> {
                            Wind wind = Wind.get(c.getSource().getLevel());
                            wind.set(FloatArgumentType.getFloat(c, "angle"));
                            c.getSource().sendSuccess(() -> Component.literal("Wind locked, blowing from " + wind.fromName()), true);
                            return 1;
                        })))
                .then(Commands.literal("respawn").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    var p = EntityArgument.getPlayer(c, "player");
                    var back = p.server.getPlayerList().respawn(p, false, net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                    back.connection.player = back;   // as the game does when you click Respawn
                    Seafarers.LOGGER.info("PROBE respawn done at {}", back.blockPosition().toShortString());
                    return 1;
                })))
                // any setting in seafarers-common.toml, by its name, for this session
                .then(Commands.literal("config").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("value", StringArgumentType.word()).executes(TestCommands::config))))
                // a pick on the ship choice screen (mode 0 first visit, 1 later, 2 swap; choice sloop or crew)
                .then(Commands.literal("pick").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("mode", IntegerArgumentType.integer(0, 2)).then(Commands.argument("choice", StringArgumentType.word())
                                .executes(c -> {
                                    dev.seafarers.ship.deed.ShipChoice.pick(EntityArgument.getPlayer(c, "player"),
                                            IntegerArgumentType.getInteger(c, "mode"), StringArgumentType.getString(c, "choice"));
                                    return 1;
                                })))))
                // ties a natural rowboat up along the coast in the chunk at x z (as when new coast is generated)
                .then(Commands.literal("rowboat").then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer()).then(Commands.argument("sea", IntegerArgumentType.integer()).executes(c -> {
                            var boat = dev.seafarers.ship.Rowboats.place(c.getSource().getLevel(), new net.minecraft.world.level.ChunkPos(
                                    IntegerArgumentType.getInteger(c, "x") >> 4, IntegerArgumentType.getInteger(c, "z") >> 4),
                                    IntegerArgumentType.getInteger(c, "sea"));
                            Seafarers.LOGGER.info("PROBE rowboat placed={}", boat != null);
                            return boat != null ? 1 : 0;
                        })))))
                // claims (or frees) the chunk at x z for someone else, with Open Parties and Claims
                .then(Commands.literal("claim").then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer()).then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                            boolean ok = dev.seafarers.compat.claims.Claims.testClaim(c.getSource().getLevel(),
                                    IntegerArgumentType.getInteger(c, "x") >> 4, IntegerArgumentType.getInteger(c, "z") >> 4, BoolArgumentType.getBool(c, "on"));
                            Seafarers.LOGGER.info("PROBE test claim {}", ok);
                            return ok ? 1 : 0;
                        })))))
                // offers a hint (as using its block would); counted like a real one
                .then(Commands.literal("hint").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("key", StringArgumentType.word()).executes(c -> {
                            dev.seafarers.ship.Hints.offer(EntityArgument.getPlayer(c, "player"), StringArgumentType.getString(c, "key"));
                            return 1;
                        }))))
                // the player uses what's in their main hand, where they're looking (as a right click in the air)
                .then(Commands.literal("useitem").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    var p = EntityArgument.getPlayer(c, "player");
                    var result = p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND);
                    p.setItemInHand(InteractionHand.MAIN_HAND, result.getObject());
                    Seafarers.LOGGER.info("PROBE useitem {} {}", p.getGameProfile().getName(), result.getResult());
                    return 1;
                })))
                // what a player has of their own: their choice, deeds, ship, captaincy and lock
                .then(Commands.literal("deeds").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    var p = EntityArgument.getPlayer(c, "player");
                    var sailors = dev.seafarers.ship.deed.Sailors.get(p.server);
                    var crews = dev.seafarers.ship.Crews.get(p.serverLevel());
                    java.util.UUID valid = sailors.deed(p.getUUID());
                    int carried = 0;
                    boolean hasValid = false;
                    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                        ItemStack st = p.getInventory().getItem(i);
                        if (st.getItem() instanceof dev.seafarers.ship.item.ShipDeedItem) {
                            carried += st.getCount();
                            var deed = st.get(dev.seafarers.registry.ModDataComponents.DEED.get());
                            hasValid |= deed != null && deed.id().equals(valid);
                        }
                    }
                    java.util.UUID mine = crews.shipOf(p.getUUID());
                    java.util.UUID captained = crews.captainedBy(p.getUUID());
                    Seafarers.LOGGER.info("PROBE deeds {} choice={} carried={} valid={} validCarried={} ship={} captain={} locked={} wait={} admin={}",
                            p.getGameProfile().getName(), sailors.choice(p.getUUID()), carried, valid != null, hasValid,
                            mine, captained, mine != null && crews.locked(mine), dev.seafarers.ship.deed.ShipChoice.waitSeconds(p),
                            dev.seafarers.ship.Permissions.has(p, dev.seafarers.ship.Permissions.ADMIN));
                    return 1;
                })))
                .then(Commands.literal("tune").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg()).executes(TestCommands::tune))))
                .then(ship));
    }

    // ---------------------------------------------------------------- helpers

    /** The stand-in crewmate for tests: a fake player that helms, pulls ropes and patches holes like a real one. */
    public static FakePlayer testHand(ServerLevel level) {
        return FakePlayerFactory.getMinecraft(level);
    }

    @Nullable
    private static HelmBlockEntity nearestHelm(CommandSourceStack source) {
        SubLevelContainer container = SubLevelContainer.getContainer(source.getLevel());
        HelmBlockEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (SubLevel ship : container.getAllSubLevels()) {
            HelmBlockEntity helm = Ships.helmOf(ship);
            if (helm != null) {
                double dist = helm.worldPos().distanceToSqr(source.getPosition());
                if (dist < bestDist) {
                    bestDist = dist;
                    best = helm;
                }
            }
        }
        if (best == null) {
            source.sendFailure(Component.literal("No ship with a helm is loaded"));
        }
        return best;
    }

    private static ServerSubLevel shipOf(HelmBlockEntity helm) {
        return (ServerSubLevel) Ships.shipAt(helm.getLevel(), helm.getBlockPos());
    }

    private static int withHelm(CommandContext<CommandSourceStack> c, BiConsumer<HelmBlockEntity, CommandSourceStack> action) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) {
            return 0;
        }
        action.accept(helm, c.getSource());
        return 1;
    }

    /** Every block on the ship (plot positions) matching the predicate, in a stable order. */
    private static List<BlockPos> find(HelmBlockEntity helm, Predicate<BlockState> what) {
        ServerLevel level = (ServerLevel) helm.getLevel();
        BoundingBox3ic b = shipOf(helm).getPlot().getBoundingBox();
        List<BlockPos> out = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int y = b.minY(); y <= b.maxY(); y++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    BlockState st = level.getBlockState(p.set(x, y, z));
                    // a fitting several blocks big counts once, at its main part
                    if (what.test(st) && !(st.hasProperty(dev.seafarers.ship.block.MultiPart.PART)
                            && st.getValue(dev.seafarers.ship.block.MultiPart.PART) != 0)) {
                        out.add(p.immutable());
                    }
                }
            }
        }
        return out;
    }

    /** Uses a block as the stand-in player would (right-click, optionally sneaking). */
    private static void use(ServerLevel level, BlockPos pos, FakePlayer hand, boolean sneak) {
        hand.setShiftKeyDown(sneak);
        BlockState state = level.getBlockState(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        ItemStack held = hand.getMainHandItem();
        // as a real click would: other mods (and the ship's lock) get their say first
        var event = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(hand, InteractionHand.MAIN_HAND, pos, hit));
        if (event.isCanceled()) {
            Seafarers.LOGGER.info("PROBE use refused at {}", pos.toShortString());
        } else if (held.isEmpty() || !state.useItemOn(held, level, hand, InteractionHand.MAIN_HAND, hit).consumesAction()) {
            state.useWithoutItem(level, hand, hit);
        }
        hand.setShiftKeyDown(false);
    }

    private static int useBlock(CommandContext<CommandSourceStack> c, Predicate<BlockState> what, int times, boolean sneak) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        List<BlockPos> found = find(helm, what);
        if (found.isEmpty()) {
            c.getSource().sendFailure(Component.literal("No such block on this ship"));
            Seafarers.LOGGER.info("PROBE use: no such block");
            return 0;
        }
        FakePlayer hand = testHand(c.getSource().getLevel());
        hand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        for (int i = 0; i < times; i++) {
            use(c.getSource().getLevel(), found.getFirst(), hand, sneak);
        }
        return found.size();
    }

    // ---------------------------------------------------------------- scenes

    /** A 96 x 256 glass-bottomed sea, 8 deep, with its surface at pos.y + 8. Big enough to sail and turn. */
    private static int buildSea(CommandSourceStack source, BlockPos origin) {
        ServerLevel level = source.getLevel();
        int sizeX = 96, sizeZ = 256, depth = 8;
        for (int x = -1; x <= sizeX; x++) {
            for (int z = -1; z <= sizeZ; z++) {
                boolean wall = x == -1 || z == -1 || x == sizeX || z == sizeZ;
                for (int y = 0; y <= depth + 1; y++) {
                    BlockState s = wall || y == 0 ? Blocks.GLASS.defaultBlockState()
                            : (y <= depth ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    level.setBlock(origin.offset(x, y, z), s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
        source.sendSuccess(() -> Component.literal("Sea built; surface block y=" + (origin.getY() + depth)), true);
        return 1;
    }

    private static int launch(CommandContext<CommandSourceStack> c, String layout, @Nullable Direction bow) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getBlockPos(c, "pos");
        ServerSubLevel ship = ShipBuilder.launch(c.getSource().getLevel(), layout, pos, bow == null ? Direction.NORTH : bow);
        if (ship == null) {
            c.getSource().sendFailure(Component.literal("Could not launch " + layout + " there"));
            Seafarers.LOGGER.info("PROBE launch failed {}", layout);
            return 0;
        }
        Probe.start("launch", ship, 20, 100);
        return 1;
    }

    // ---------------------------------------------------------------- reports

    /** One machine-readable line of key=value pairs; each mast as m0=deploy/angle/health/fill. */
    private static int status(CommandSourceStack source) {
        HelmBlockEntity helm = nearestHelm(source);
        if (helm == null) return 0;
        SubLevel ship = shipOf(helm);
        Probe.log("status", 0, ship);
        StringBuilder masts = new StringBuilder();
        for (int i = 0; i < helm.masts(); i++) {
            HelmBlockEntity.Sail s = helm.sail(i);
            masts.append(String.format(" m%d=%.2f/%.0f/%d/%.2f", i, s.deploy(), s.angle(), s.health(), s.fill()));
        }
        String s = String.format("layout=%s speed=%.2f wheel=%.2f anchored=%s anchorRaise=%d flood=%.1f holes=%d sinking=%s "
                        + "bilge=%s waterAboveFloor=%.2f helmsman=%s ticks=%s drag=%s bowUp=%.2f fires=%d ballast=%d row=%.2f docked=%s gap=%.2f gt=%d%s",
                helm.layout(), helm.speed(), helm.wheel(), helm.anchored(), helm.anchorRaise(), helm.flood(), helm.holeCount(),
                helm.sinking(), helm.bilgeSummary(), helm.waterAboveHoldFloor(), helm.helmsman() != null, helm.tickCounters(), helm.dragEstimate(), helm.bowUp(), helm.fireCount(), helm.ballastTicks(), helm.row(), helm.docked(), helm.dockGap(), source.getLevel().getGameTime(), masts)
                + String.format(" helmX=%.2f helmY=%.2f helmZ=%.2f", helm.worldPos().x, helm.worldPos().y, helm.worldPos().z)
                + " aground=" + (ship instanceof ServerSubLevel sub && dev.seafarers.ship.Refloat.aground(source.getLevel(), sub));
        Seafarers.LOGGER.info("PROBE status {}", s);
        source.sendSuccess(() -> Component.literal(s), false);
        return 1;
    }

    /** Sets a setting in seafarers-common.toml by its name (true/false or a number), until the server stops. */
    @SuppressWarnings("unchecked")
    private static int config(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "name");
        String value = StringArgumentType.getString(c, "value");
        Object found = find(dev.seafarers.SeafarersConfig.SERVER_SPEC.getValues(), name);
        if (!(found instanceof net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?> v)) {
            c.getSource().sendFailure(Component.literal("No setting " + name));
            return 0;
        }
        Object current = v.get();
        if (current instanceof Boolean) {
            ((net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<Boolean>) v).set(Boolean.parseBoolean(value));
        } else if (current instanceof Integer) {
            ((net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<Integer>) v).set(Integer.parseInt(value));
        } else if (current instanceof Double) {
            ((net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<Double>) v).set(Double.parseDouble(value));
        } else {
            c.getSource().sendFailure(Component.literal("Can't set " + name));
            return 0;
        }
        Seafarers.LOGGER.info("PROBE config {}={}", name, v.get());
        return 1;
    }

    /** A config value by its name, anywhere in the config's sections. */
    private static Object find(com.electronwill.nightconfig.core.UnmodifiableConfig config, String name) {
        for (var entry : config.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof com.electronwill.nightconfig.core.UnmodifiableConfig section) {
                Object inner = find(section, name);
                if (inner != null) {
                    return inner;
                }
            } else if (entry.getKey().equals(name)) {
                return value;
            }
        }
        return null;
    }

    /** Sets a stat on the nearest ship (see ShipStats), or a global test setting (REPLACE_MIN/REPLACE_MAX). */
    private static int tune(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "name");
        double value = DoubleArgumentType.getDouble(c, "value");
        if (name.equals("waterBarrelRefillSeconds")) {
            dev.seafarers.SeafarersConfig.WATER_BARREL_REFILL_SECONDS.set((int) value);
            return 1;
        }
        java.util.Map<String, Class<?>> statics = java.util.Map.of("fire.", dev.seafarers.ship.ShipFire.class,
                "stove.", dev.seafarers.ship.block.StoveBlock.class, "harpoon.", dev.seafarers.ship.block.HarpoonBlockEntity.class);
        String prefix = statics.keySet().stream().filter(name::startsWith).findFirst().orElse(null);
        if (prefix != null) {
            try {
                statics.get(prefix).getField(name.substring(prefix.length())).setDouble(null, value);
            } catch (ReflectiveOperationException e) {
                c.getSource().sendFailure(Component.literal("No fire setting " + name));
                return 0;
            }
        } else if (name.equals("REPLACE_MIN")) {
            dev.seafarers.SeafarersConfig.REPLACEMENT_MIN_BLOCKS.set((int) value);
        } else if (name.equals("REPLACE_MAX")) {
            dev.seafarers.SeafarersConfig.REPLACEMENT_MAX_BLOCKS.set((int) value);
        } else {
            HelmBlockEntity helm = nearestHelm(c.getSource());
            if (helm == null || !helm.stats().set(name, value)) {
                c.getSource().sendFailure(Component.literal("No ship stat " + name));
                return 0;
            }
        }
        Seafarers.LOGGER.info("PROBE tune {}={}", name, value);
        return 1;
    }

    // ---------------------------------------------------------------- crew actions

    private static int hoist(CommandContext<CommandSourceStack> c) {
        int mast = IntegerArgumentType.getInteger(c, "mast");
        int times = IntegerArgumentType.getInteger(c, "times");
        return useBlock(c, s -> s.is(ModBlocks.SAIL_ROPE.get()) && s.getValue(SailRopeBlock.MAST) == mast, Math.abs(times), times < 0);
    }

    private static int brace(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        int mast = IntegerArgumentType.getInteger(c, "mast");
        Direction bow = helm.getBlockState().getValue(HelmBlock.FACING);
        Direction side = sideOf(bow, StringArgumentType.getString(c, "side"));
        return useBlock(c, s -> s.is(ModBlocks.BRACE_ROPE.get()) && s.getValue(SailRopeBlock.MAST) == mast
                && s.getValue(SailRopeBlock.FACING) == side, IntegerArgumentType.getInteger(c, "times"), false);
    }

    /** Takes the wheel with the stand-in player, steers for a number of ticks, then lets go. */
    private static int helm(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        FakePlayer hand = testHand(level);
        HelmBlockEntity.TEST_HANDS.add(hand.getUUID());
        hand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        use(level, helm.getBlockPos(), hand, false);
        float steer = FloatArgumentType.getFloat(c, "steer");
        helm.onStationInput(hand, steer, false, false);
        Seafarers.LOGGER.info("PROBE helm taken={} steering {}", hand.getUUID().equals(helm.helmsman()), steer);
        TestTasks.after(IntegerArgumentType.getInteger(c, "ticks"), () -> {
            helm.onStationInput(hand, 0, false, true);
            Seafarers.LOGGER.info("PROBE helm released wheel={} helmsman={}", helm.wheel(), helm.helmsman() != null);
        });
        return 1;
    }

    /** Punches holes high in the hull (the topsides, well above the waterline), as cannon fire to the upper hull. */
    private static int upperHoles(CommandSourceStack source, int count) {
        HelmBlockEntity helm = nearestHelm(source);
        if (helm == null) return 0;
        ServerLevel level = (ServerLevel) helm.getLevel();
        List<BlockPos> hull = find(helm, s -> s.is(SeafarersTags.HULL) && !HullDamage.isHole(s));
        int deck = helm.getBlockPos().getY() - 2;   // the main deck's level (the helm stands on the raised aft deck)
        int made = 0;
        for (BlockPos p : hull) {
            // the bulwarks beside the main deck, well above the waterline
            if (made < count && p.getY() == deck && HullDamage.punch(level, p, level.getBlockState(p))) {
                made++;
            }
        }
        helm.forceRescan();
        Seafarers.LOGGER.info("PROBE punched {} upper holes", made);
        return made;
    }

    /** Punches holes in the lowest hull blocks, as if hit below the waterline. */
    private static int holes(CommandSourceStack source, int count, int hits) {
        HelmBlockEntity helm = nearestHelm(source);
        if (helm == null) return 0;
        ServerLevel level = (ServerLevel) helm.getLevel();
        List<BlockPos> hull = find(helm, s -> s.is(SeafarersTags.HULL) && !HullDamage.isHole(s));
        hull.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
        int made = 0;
        for (BlockPos p : hull) {
            if (made >= count) break;
            boolean holed = false;
            for (int i = 0; i < hits; i++) {
                holed |= HullDamage.punch(level, p, level.getBlockState(p));
            }
            if (holed) {
                made++;
            }
        }
        helm.forceRescan();
        Seafarers.LOGGER.info("PROBE holed {}", made);
        return made;
    }

    private static int bail(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        FakePlayer hand = testHand(level);
        Vec3 deck = helm.respawnWorldPos();
        hand.moveTo(deck.x, deck.y, deck.z);
        int done = 0;
        for (int i = 0; i < IntegerArgumentType.getInteger(c, "buckets"); i++) {
            hand.setItemInHand(InteractionHand.MAIN_HAND, dev.seafarers.ship.item.ShipBucketItem.of(dev.seafarers.ship.item.ShipBucketItem.Content.EMPTY));
            ItemStack filled = hand.getMainHandItem().use(level, hand, InteractionHand.MAIN_HAND).getObject();
            if (!(filled.getItem() instanceof dev.seafarers.ship.item.ShipBucketItem b) || b.content() != dev.seafarers.ship.item.ShipBucketItem.Content.WATER) {
                break;
            }
            hand.setItemInHand(InteractionHand.MAIN_HAND, filled);
            filled.use(level, hand, InteractionHand.MAIN_HAND);   // and over the side it goes
            done++;
        }
        hand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Seafarers.LOGGER.info("PROBE bailed {}", done);
        return done;
    }

    /** Patches every hole by using planks on it, as a player would. */
    private static int patch(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        FakePlayer hand = testHand(level);
        int done = 0;
        for (BlockPos p : find(helm, HullDamage::isHole)) {
            for (int i = 0; i < 2 && HullDamage.isHole(level.getBlockState(p)); i++) {
                hand.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 4));
                use(level, p, hand, false);
            }
            done += HullDamage.isHole(level.getBlockState(p)) ? 0 : 1;
        }
        hand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helm.forceRescan();
        Seafarers.LOGGER.info("PROBE patched {}", done);
        return done;
    }

    /** Patches a damaged mast with planks until it is whole. */
    private static int mendMast(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        int mast = IntegerArgumentType.getInteger(c, "mast");
        ServerLevel level = c.getSource().getLevel();
        FakePlayer hand = testHand(level);
        List<BlockPos> masts = find(helm, s -> s.is(ModBlocks.MAST.get()) && s.getValue(MastBlock.MAST) == mast);
        for (int i = 0; i < 6 && !masts.isEmpty(); i++) {
            hand.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 4));
            use(level, masts.get(masts.size() / 2), hand, false);
        }
        hand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Seafarers.LOGGER.info("PROBE mast {} mended health={}", mast, helm.sail(mast) == null ? -1 : helm.sail(mast).health());
        return 1;
    }

    /** port, starboard or bow, as a direction for a ship heading bow. */
    private static Direction sideOf(Direction bow, String side) {
        return switch (side) {
            case "starboard" -> bow.getClockWise();
            case "bow" -> bow;
            default -> bow.getCounterClockWise();
        };
    }

    /** Loads and fires the nearest ship's first cannon on one side, aimed straight out at the given elevation. */
    private static int fire(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = (ServerLevel) helm.getLevel();
        Ammo ammo = Ammo.valueOf(StringArgumentType.getString(c, "ammo").toUpperCase());
        float pitch = FloatArgumentType.getFloat(c, "pitch");
        Direction bow = helm.getBlockState().getValue(HelmBlock.FACING);
        Direction side = sideOf(bow, StringArgumentType.getString(c, "side"));
        for (BlockPos p : find(helm, s -> s.is(ModBlocks.CANNON.get()) && s.getValue(CannonBlock.FACING) == side)) {
            if (level.getBlockEntity(p) instanceof CannonBlockEntity cannon) {
                FakePlayer gunner = testHand(level);
                Vec3 out = Ships.toWorldNormal(level, p, Vec3.atLowerCornerOf(side.getNormal()));
                double rad = Math.toRadians(pitch);
                cannon.aimAlong(level, new Vec3(out.x * Math.cos(rad), Math.sin(rad), out.z * Math.cos(rad)));
                cannon.load(gunner, ammo);
                cannon.fire(gunner);
                Seafarers.LOGGER.info("PROBE fired {} yaw={} pitch={}", ammo, cannon.aimYaw(), cannon.aimPitch());
                return 1;
            }
        }
        c.getSource().sendFailure(Component.literal("No cannon on that side"));
        return 0;
    }

    /** The player uses the first mattress aboard, as if they'd clicked it; logs whether they sleep and where. */
    private static int bed(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        var player = EntityArgument.getPlayer(c, "player");
        List<BlockPos> beds = find(helm, s -> s.is(ModBlocks.MATTRESS.get()));
        if (beds.isEmpty()) {
            Seafarers.LOGGER.info("PROBE bed: none aboard");
            return 0;
        }
        dev.seafarers.ship.ShipBeds.use(level, beds.get(0), player);
        Seafarers.LOGGER.info("PROBE bed used sleeping={} mattress={} player={}", player.isSleeping(),
                BlockPos.containing(Ships.toWorld(level, beds.get(0))).toShortString(), player.blockPosition().toShortString());
        return 1;
    }

    /** The stand-in uses a dye on the helm (hull) or a sail cleat (sails). */
    private static int paint(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        boolean sails = "sails".equals(StringArgumentType.getString(c, "what"));
        var dye = net.minecraft.world.item.DyeColor.byName(StringArgumentType.getString(c, "dye"), net.minecraft.world.item.DyeColor.RED);
        BlockPos target = sails ? find(helm, s -> s.is(ModBlocks.SAIL_ROPE.get())).getFirst() : helm.getBlockPos();
        FakePlayer hand = testHand(level);
        hand.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.DyeItem.byColor(dye)));
        use(level, target, hand, false);
        Seafarers.LOGGER.info("PROBE painted hull={} sails={} left={}", Integer.toHexString(helm.hullColor()),
                Integer.toHexString(helm.sailColor()), hand.getMainHandItem().getCount());
        hand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return 1;
    }

    /**
     * Puts the stand-in on every rung of every rope ladder aboard and asks the game whether it's climbing; and
     * for the top of each side ladder, whether a player can step from it through the rail onto the deck (a
     * player-sized space, above step height, free of the ship's blocks).
     */
    private static int ladders(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        List<BlockPos> rungs = find(helm, s -> s.is(ModBlocks.ROPE_LADDER.get()));
        int[] climbable = {0};
        List<String> stuck = new ArrayList<>();
        // the stand-in, placed on each rung in turn (a client's own movement can't be driven from here)
        FakePlayer climber = testHand(level);
        for (BlockPos p : rungs) {
            Direction out = level.getBlockState(p).getValue(net.minecraft.world.level.block.LadderBlock.FACING);
            Vec3 at = Ships.toWorld(level, Vec3.atCenterOf(p).add(out.getStepX() * 0.05, -0.4, out.getStepZ() * 0.05));
            climber.setPos(at.x, at.y, at.z);
            climber.move(net.minecraft.world.entity.MoverType.SELF, Vec3.ZERO); // updates what it's standing in, as a step would
            if (climber.onClimbable()) {
                climbable[0]++;
            } else {
                stuck.add(p.toShortString());
            }
        }
        int tops = 0, clear = 0;
        for (BlockPos p : rungs) {
            Direction out = level.getBlockState(p).getValue(net.minecraft.world.level.block.LadderBlock.FACING);
            // top rungs of the side ladders (the mast ladder goes on up through the crow's nest floor)
            if (level.getBlockState(p.above()).is(ModBlocks.ROPE_LADDER.get()) || out == helm.getBlockState().getValue(HelmBlock.FACING).getOpposite()) {
                continue;
            }
            tops++;
            BlockPos in = p.relative(out.getOpposite());
            net.minecraft.world.phys.AABB body = new net.minecraft.world.phys.AABB(in.getX() + 0.2, p.getY() + 0.6, in.getZ() + 0.2,
                    in.getX() + 0.8, p.getY() + 2.4, in.getZ() + 0.8);
            boolean free = true;
            for (BlockPos q : BlockPos.betweenClosed(in.offset(-1, 0, -1), in.offset(1, 3, 1))) {
                for (var box : level.getBlockState(q).getCollisionShape(level, q).toAabbs()) {
                    if (box.move(q).intersects(body)) {
                        free = false;
                    }
                }
            }
            if (free) {
                clear++;
            } else {
                stuck.add("top " + p.toShortString());
            }
        }
        int topsF = tops, clearF = clear;
        TestTasks.after(3 + 2 * rungs.size(), () -> Seafarers.LOGGER.info("PROBE ladders rungs={} climbable={} tops={} clear={} problems={}",
                rungs.size(), climbable[0], topsF, clearF, stuck));
        return 1;
    }

    /**
     * The water barrel: a bucket takes all three levels, a bottle one, the ship's bucket one; then it refills on
     * its own (tune waterBarrelRefillSeconds low first).
     */
    private static int water(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        List<BlockPos> barrels = find(helm, s -> s.is(ModBlocks.WATER_BARREL.get()));
        if (barrels.isEmpty()) {
            Seafarers.LOGGER.info("PROBE water: no barrel");
            return 0;
        }
        BlockPos b = barrels.getFirst();
        FakePlayer hand = testHand(level);
        StringBuilder log = new StringBuilder();
        java.util.function.Consumer<net.minecraft.world.item.Item> fill = item -> {
            hand.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            use(level, b, hand, false);
            log.append(BuiltInRegistries.ITEM.getKey(item).getPath()).append("->")
                    .append(BuiltInRegistries.ITEM.getKey(hand.getMainHandItem().getItem()).getPath())
                    .append(" level ").append(level.getBlockState(b).getValue(dev.seafarers.ship.block.WaterBarrelBlock.LEVEL)).append("; ");
        };
        fill.accept(net.minecraft.world.item.Items.BUCKET);
        fill.accept(net.minecraft.world.item.Items.WATER_BUCKET);
        fill.accept(net.minecraft.world.item.Items.GLASS_BOTTLE);
        fill.accept(dev.seafarers.registry.ModItems.SHIP_BUCKET.get());
        hand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Seafarers.LOGGER.info("PROBE water {}", log);
        TestTasks.after(60, () -> Seafarers.LOGGER.info("PROBE water later level {}",
                level.getBlockState(b).getValue(dev.seafarers.ship.block.WaterBarrelBlock.LEVEL)));
        return 1;
    }

    /** Builds the voyage table screen's state for the stand-in: what a player would see. */
    private static int voyageGui(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        List<BlockPos> tables = find(helm, s -> s.is(ModBlocks.VOYAGE_TABLE.get()));
        if (tables.isEmpty() || !(level.getBlockEntity(tables.getFirst()) instanceof dev.seafarers.ship.block.VoyageTableBlock.Table table)) {
            return 0;
        }
        FakePlayer hand = testHand(level);
        table.act(level, hand, dev.seafarers.network.VoyagePayloads.Action.RENAME, 0, "The Salty Dog");
        var state = table.state(level, hand);
        Seafarers.LOGGER.info("PROBE voyage gui name={} layout={} masts={} crew={} proposals={}", state.getCompound("ship").getString("name"),
                state.getCompound("ship").getString("layout"), state.getCompound("ship").getIntArray("masts").length,
                state.getList("crew", 10).size(), state.getList("proposals", 10).size());
        return 1;
    }

    /**
     * Writes every block of the ship's real collision boxes to run/server/ship_shapes.json, in ship coordinates (x to
     * starboard, y up, z toward the bow, the helm at its layout cell), so tools/ships/walkcheck.py can test walking
     * against exactly what the game collides with.
     */
    private static int dumpShapes(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        Direction bow = helm.getBlockState().getValue(HelmBlock.FACING);
        Direction stbd = bow.getClockWise();
        BlockPos h = helm.getBlockPos();
        int[] helmCell = {0, 6, 8};
        com.google.gson.JsonArray out = new com.google.gson.JsonArray();
        BoundingBox3ic b = shipOf(helm).getPlot().getBoundingBox();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int y = b.minY(); y <= b.maxY(); y++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    BlockState st = level.getBlockState(p.set(x, y, z));
                    if (st.isAir()) {
                        continue;
                    }
                    int dx = x - h.getX(), dz = z - h.getZ();
                    int sx = dx * stbd.getStepX() + dz * stbd.getStepZ() + helmCell[0];
                    int sz = dx * bow.getStepX() + dz * bow.getStepZ() + helmCell[2];
                    int sy = y - h.getY() + helmCell[1];
                    com.google.gson.JsonArray boxes = new com.google.gson.JsonArray();
                    for (var bb : st.getCollisionShape(level, p).toAabbs()) {
                        // block-local corners into ship axes (local 0..1 measured from the cell's low corner)
                        double[] xs = {bb.minX, bb.maxX}, zs = {bb.minZ, bb.maxZ};
                        double lx0 = 9, lx1 = -9, lz0 = 9, lz1 = -9;
                        for (double ax : xs) {
                            for (double az : zs) {
                                double cx = ax - 0.5, cz = az - 0.5;
                                double u = cx * stbd.getStepX() + cz * stbd.getStepZ() + 0.5;
                                double w = cx * bow.getStepX() + cz * bow.getStepZ() + 0.5;
                                lx0 = Math.min(lx0, u); lx1 = Math.max(lx1, u); lz0 = Math.min(lz0, w); lz1 = Math.max(lz1, w);
                            }
                        }
                        com.google.gson.JsonArray box = new com.google.gson.JsonArray();
                        for (double v : new double[]{lx0, bb.minY, lz0, lx1, bb.maxY, lz1}) {
                            box.add(Math.round(v * 10000) / 10000.0);
                        }
                        boxes.add(box);
                    }
                    com.google.gson.JsonObject cell = new com.google.gson.JsonObject();
                    cell.addProperty("x", sx);
                    cell.addProperty("y", sy);
                    cell.addProperty("z", sz);
                    cell.addProperty("block", BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath());
                    cell.addProperty("climbable", st.is(net.minecraft.tags.BlockTags.CLIMBABLE));
                    cell.add("boxes", boxes);
                    out.add(cell);
                }
            }
        }
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("ship_shapes.json"), out.toString());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        Seafarers.LOGGER.info("PROBE dumped {} blocks", out.size());
        return 1;
    }

    /** Puts out every lantern aboard, then lights them all again; logs how many were lit at each step. */
    private static int lanterns(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        FakePlayer hand = testHand(level);
        hand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        List<BlockPos> lanterns = find(helm, s -> s.is(ModBlocks.SHIP_LANTERN.get()));
        java.util.function.Supplier<Long> lit = () -> lanterns.stream()
                .filter(p -> level.getBlockState(p).getValue(dev.seafarers.ship.block.ShipLanternBlock.LIT)).count();
        long before = lit.get();
        int light = level.getBlockState(lanterns.get(0)).getLightEmission(level, lanterns.get(0));
        lanterns.forEach(p -> use(level, p, hand, false));
        long out = lit.get();
        int dark = level.getBlockState(lanterns.get(0)).getLightEmission(level, lanterns.get(0));
        lanterns.forEach(p -> use(level, p, hand, false));
        Seafarers.LOGGER.info("PROBE lanterns {} lit={} out={} relit={} light={}/{}", lanterns.size(), before, out, lit.get(), light, dark);
        return 1;
    }

    /** The stand-in hoists a patterned banner from the flag box (or strikes it). */
    private static int flag(CommandContext<CommandSourceStack> c, boolean raise) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        FakePlayer hand = testHand(level);
        List<BlockPos> boxes = find(helm, s -> s.is(ModBlocks.FLAG_BOX.get()));
        if (boxes.isEmpty()) {
            Seafarers.LOGGER.info("PROBE flag: no flag box");
            return 0;
        }
        ItemStack banner = ItemStack.EMPTY;
        if (raise) {
            banner = new ItemStack(net.minecraft.world.item.Items.BLACK_BANNER);
            var patterns = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BANNER_PATTERN);
            banner.set(net.minecraft.core.component.DataComponents.BANNER_PATTERNS, new net.minecraft.world.level.block.entity.BannerPatternLayers.Builder()
                    .add(patterns.getHolderOrThrow(net.minecraft.world.level.block.entity.BannerPatterns.SKULL), net.minecraft.world.item.DyeColor.WHITE)
                    .build());
        }
        hand.setItemInHand(InteractionHand.MAIN_HAND, banner);
        use(level, boxes.get(0), hand, false);
        List<BlockPos> flags = find(helm, s -> s.is(ModBlocks.FLAG.get()));
        BlockState flagState = flags.isEmpty() ? null : level.getBlockState(flags.get(0));
        Seafarers.LOGGER.info("PROBE flag state flying={} held={}", flagState != null && flagState.getValue(dev.seafarers.ship.block.FlagBlock.FLYING),
                hand.getMainHandItem().getItem());
        hand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return 1;
    }

    /** Puts raw beef on the galley stove and takes it off the given number of ticks later. */
    private static int stove(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        FakePlayer hand = testHand(level);
        List<BlockPos> stoves = find(helm, s -> s.is(ModBlocks.STOVE.get()));
        if (stoves.isEmpty()) {
            Seafarers.LOGGER.info("PROBE stove: none aboard");
            return 0;
        }
        hand.getInventory().clearContent();
        hand.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.BEEF));
        use(level, stoves.get(0), hand, false);
        TestTasks.after(IntegerArgumentType.getInteger(c, "ticks"), () -> {
            hand.getInventory().clearContent();
            use(level, stoves.get(0), hand, false);
            ItemStack got = ItemStack.EMPTY;
            for (ItemStack s : hand.getInventory().items) {
                if (!s.isEmpty()) {
                    got = s;
                }
            }
            Seafarers.LOGGER.info("PROBE stove gave {}", got.getItem());
        });
        return 1;
    }

    /** Unrolls the chart on the map table and puts a mark on it. */
    private static int mapTable(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        List<BlockPos> tables = find(helm, s -> s.is(ModBlocks.MAP_TABLE.get()));
        if (tables.isEmpty() || !(level.getBlockEntity(tables.get(0)) instanceof dev.seafarers.ship.block.MapTableBlock.Table table)) {
            Seafarers.LOGGER.info("PROBE map table: none aboard");
            return 0;
        }
        FakePlayer hand = testHand(level);
        table.show(level, hand);
        var here = Ships.toWorld(level, table.getBlockPos());
        table.mark(level, hand, here.x + 60, here.z - 40, false, 512);
        table.setView(here.x + 20, here.z - 10, 400);
        Seafarers.LOGGER.info("PROBE map table marks={} view={},{}/{} ship={},{}", table.marks().size(), (int) table.viewX(), (int) table.viewZ(),
                (int) table.viewSpan(), (int) here.x, (int) here.z);
        return 1;
    }

    /** Puts a treasure map on the voyage table and votes it through; logs who got a copy. */
    private static int voyage(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        List<BlockPos> tables = find(helm, s -> s.is(ModBlocks.VOYAGE_TABLE.get()));
        if (tables.isEmpty()) {
            Seafarers.LOGGER.info("PROBE voyage: no table aboard");
            return 0;
        }
        FakePlayer hand = testHand(level);
        hand.getInventory().clearContent();
        BlockPos x = BlockPos.containing(helm.worldPos()).offset(300, 0, 200);
        ItemStack map = net.minecraft.world.item.MapItem.create(level, x.getX(), x.getZ(), (byte) 2, true, true);
        net.minecraft.world.level.saveddata.maps.MapItemSavedData.addTargetDecoration(map, x, "+",
                net.minecraft.world.level.saveddata.maps.MapDecorationTypes.RED_X);
        map.set(net.minecraft.core.component.DataComponents.ITEM_NAME, Component.literal("Test Treasure Map"));
        hand.setItemInHand(InteractionHand.MAIN_HAND, map);
        use(level, tables.get(0), hand, false);
        int copies = 0;
        for (ItemStack s : hand.getInventory().items) {
            if (s.is(net.minecraft.world.item.Items.FILLED_MAP)) {
                copies += s.getCount();
            }
        }
        Seafarers.LOGGER.info("PROBE voyage copies={}", copies);
        return 1;
    }

    /** The stand-in takes the oars and rows (forward, steer) for a while, then lets go. */
    private static int row(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        FakePlayer hand = testHand(level);
        HelmBlockEntity.TEST_HANDS.add(hand.getUUID());
        hand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        use(level, helm.getBlockPos(), hand, false);
        helm.onStationInput(hand, FloatArgumentType.getFloat(c, "steer"), FloatArgumentType.getFloat(c, "forward"), false, false);
        Seafarers.LOGGER.info("PROBE oars taken={} rowboat={} row={}", hand.getUUID().equals(helm.helmsman()), helm.rowboat(), helm.row());
        TestTasks.after(IntegerArgumentType.getInteger(c, "ticks"), () -> {
            helm.onStationInput(hand, 0, 0, false, true);
            Seafarers.LOGGER.info("PROBE oars released");
        });
        return 1;
    }

    /** Uses the rowboat's hook (with a test range) to dock at the nearest ship's ladder, or cast off. */
    private static int dock(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        List<BlockPos> hooks = find(helm, s -> s.is(ModBlocks.ROWBOAT_HOOK.get()));
        if (hooks.isEmpty()) {
            Seafarers.LOGGER.info("PROBE dock: no hook");
            return 0;
        }
        double range = DoubleArgumentType.getDouble(c, "range");
        double old = dev.seafarers.ship.block.RowboatHookBlock.RANGE;
        dev.seafarers.ship.block.RowboatHookBlock.RANGE = range;
        FakePlayer hand = testHand(level);
        hand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        use(level, hooks.get(0), hand, false);
        dev.seafarers.ship.block.RowboatHookBlock.RANGE = old;
        return 1;
    }

    /**
     * The stand-in mans the first harpoon gun, fires it at a world point (allowing for the drop), holds W to reel
     * once it has caught, and lets go after reelTicks.
     */
    private static int harpoon(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        List<BlockPos> guns = find(helm, s -> s.is(ModBlocks.HARPOON.get()));
        Vec3 target = new Vec3(DoubleArgumentType.getDouble(c, "x"), DoubleArgumentType.getDouble(c, "y"), DoubleArgumentType.getDouble(c, "z"));
        // the gun nearest the target
        guns.sort(java.util.Comparator.comparingDouble(p -> Ships.toWorld(level, p).distanceTo(target)));
        if (guns.isEmpty() || !(level.getBlockEntity(guns.get(0)) instanceof dev.seafarers.ship.block.HarpoonBlockEntity gun)) {
            Seafarers.LOGGER.info("PROBE harpoon: no gun");
            return 0;
        }
        FakePlayer hand = testHand(level);
        HelmBlockEntity.TEST_HANDS.add(hand.getUUID());
        gun.takeOver(hand);
        Vec3 from = Ships.toWorld(level, gun.muzzlePlot());
        Vec3 d = target.subtract(from);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        double g = 0.03, v = dev.seafarers.ship.block.HarpoonBlockEntity.SPEED;
        double disc = v * v * v * v - g * (g * flat * flat + 2 * d.y * v * v);
        double angle = disc < 0 ? Math.PI / 4 : Math.atan((v * v - Math.sqrt(disc)) / (g * flat));
        gun.aimAlong(level, new Vec3(d.x / flat * Math.cos(angle), Math.sin(angle), d.z / flat * Math.cos(angle)));
        gun.onStationInput(hand, 0, 0, true, false);
        gun.onStationInput(hand, 0, 1, false, false);
        Seafarers.LOGGER.info("PROBE harpoon fired at {} yaw={} pitch={}", target, gun.aimYaw(), gun.aimPitch());
        TestTasks.after(Math.max(1, IntegerArgumentType.getInteger(c, "reelTicks")), () -> {
            gun.onStationInput(hand, 0, 0, false, true);
            Seafarers.LOGGER.info("PROBE harpoon let go");
        });
        return 1;
    }

    /**
     * The stand-in walks up to a cannon empty handed, uses it twice to climb into the barrel, then fires itself.
     * Logs whether it climbed in (hidden, loaded as PLAYER), where and how fast it left the muzzle, and the ship's
     * recoil (its change in velocity against the barrel a few ticks later).
     */
    private static int gunner(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = (ServerLevel) helm.getLevel();
        Direction bow = helm.getBlockState().getValue(HelmBlock.FACING);
        Direction side = sideOf(bow, StringArgumentType.getString(c, "side"));
        for (BlockPos p : find(helm, s -> s.is(ModBlocks.CANNON.get()) && s.getValue(CannonBlock.FACING) == side)) {
            if (level.getBlockEntity(p) instanceof CannonBlockEntity cannon) {
                FakePlayer gunner = testHand(level);
                gunner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                gunner.setInvisible(false);
                cannon.release(null);
                Vec3 out = Ships.toWorldNormal(level, p, Vec3.atLowerCornerOf(side.getNormal()));
                cannon.aimAlong(level, new Vec3(out.x * 0.94, 0.34, out.z * 0.94));
                cannon.takeOver(gunner);
                cannon.takeOver(gunner);
                boolean inside = cannon.loaded() == Ammo.PLAYER && gunner.isInvisible();
                Seafarers.LOGGER.info("PROBE gunner climbed in={} loaded={}", inside, cannon.loaded());
                Vec3 dir = cannon.barrelDirection();
                Vec3 dirWorld = Ships.toWorldNormal(level, p, dir).normalize();
                Vec3 before = Ships.velocityAt(level, p);
                cannon.fire(gunner);
                Vec3 v = gunner.getDeltaMovement();
                Vec3 muzzle = Ships.toWorld(level, Vec3.atBottomCenterOf(p).add(0, CannonBlockEntity.PIVOT_Y, 0));
                Seafarers.LOGGER.info("PROBE gunner launched speed={} along={} fromMuzzle={} visible={} flying={} loaded={}",
                        String.format("%.2f", v.length()), String.format("%.2f", v.normalize().dot(dirWorld)),
                        String.format("%.2f", gunner.position().distanceTo(muzzle)), !gunner.isInvisible(),
                        gunner.getPersistentData().contains(dev.seafarers.ship.block.CannonFlight.TAG), cannon.loaded());
                gunner.getPersistentData().remove(dev.seafarers.ship.block.CannonFlight.TAG);
                TestTasks.after(3, () -> {
                    Vec3 after = Ships.velocityAt(level, p);
                    double kick = -after.subtract(before).dot(dirWorld);
                    Seafarers.LOGGER.info("PROBE recoil kick={}", String.format("%.3f", kick));
                });
                return 1;
            }
        }
        c.getSource().sendFailure(Component.literal("No cannon on that side"));
        return 0;
    }

    /**
     * Fires the first cannon on one side at a world point, raising the barrel to allow for the shot's drop
     * (a flat-trajectory solution for the cannonball's speed and gravity).
     */
    private static int fireAt(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = (ServerLevel) helm.getLevel();
        Ammo ammo = Ammo.valueOf(StringArgumentType.getString(c, "ammo").toUpperCase());
        Vec3 target = new Vec3(DoubleArgumentType.getDouble(c, "x"), DoubleArgumentType.getDouble(c, "y"), DoubleArgumentType.getDouble(c, "z"));
        Direction bow = helm.getBlockState().getValue(HelmBlock.FACING);
        Direction side = sideOf(bow, StringArgumentType.getString(c, "side"));
        // the gun best placed for the shot (on a long ship the end guns can't train far enough)
        Vec3 out = Ships.toWorldNormal(level, helm.getBlockPos(), Vec3.atLowerCornerOf(side.getNormal()));
        List<BlockPos> guns = new java.util.ArrayList<>(find(helm, s -> s.is(ModBlocks.CANNON.get()) && s.getValue(CannonBlock.FACING) == side));
        guns.sort(java.util.Comparator.comparingDouble(p -> -target.subtract(Ships.toWorld(level, Vec3.atCenterOf(p))).normalize().dot(out)));
        Seafarers.LOGGER.info("PROBE fireat {} helm at {} side {}: {} gun blocks, first at {}", helm.layout(), helm.worldPos(), side,
                guns.size(), guns.isEmpty() ? "-" : Ships.toWorld(level, Vec3.atCenterOf(guns.get(0))));
        for (BlockPos p : guns) {
            if (level.getBlockEntity(p) instanceof CannonBlockEntity cannon) {
                Vec3 from = Ships.toWorld(level, Vec3.atBottomCenterOf(p).add(0, CannonBlockEntity.PIVOT_Y, 0));
                Vec3 d = target.subtract(from);
                double flat = Math.sqrt(d.x * d.x + d.z * d.z);
                // low-angle solution of y = x tan(a) - g x^2 / (2 v^2 cos^2 a), with g and v per tick
                double g = 0.03, v = CannonBlockEntity.MUZZLE_SPEED;
                double disc = v * v * v * v - g * (g * flat * flat + 2 * d.y * v * v);
                double angle = disc < 0 ? Math.PI / 4 : Math.atan((v * v - Math.sqrt(disc)) / (g * flat));
                Vec3 aim = new Vec3(d.x / flat * Math.cos(angle), Math.sin(angle), d.z / flat * Math.cos(angle));
                FakePlayer gunner = testHand(level);
                cannon.aimAlong(level, aim);
                cannon.load(gunner, ammo);
                cannon.fire(gunner);
                Seafarers.LOGGER.info("PROBE fired {} at {} yaw={} pitch={}", ammo, target, cannon.aimYaw(), cannon.aimPitch());
                return 1;
            }
        }
        c.getSource().sendFailure(Component.literal("No cannon on that side"));
        return 0;
    }

    /**
     * The stand-in drinks four tankards from the grog barrel, then throws up while holding an empty bucket. Logs how
     * drunk it got and what the bucket holds.
     */
    private static int grog(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        FakePlayer hand = testHand(level);
        hand.removeAllEffects();
        List<BlockPos> barrels = find(helm, s -> s.is(ModBlocks.SUPPLY_BARREL.get())
                && s.getValue(SupplyBarrelBlock.SUPPLY) == SupplyBarrelBlock.Supply.GROG);
        if (barrels.isEmpty()) {
            Seafarers.LOGGER.info("PROBE grog: no grog barrel");
            return 0;
        }
        String poured = "";
        for (int i = 0; i < 3; i++) {
            hand.getInventory().clearContent();
            hand.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE));
            use(level, barrels.getFirst(), hand, false);
            ItemStack bottle = hand.getMainHandItem();
            poured += BuiltInRegistries.ITEM.getKey(bottle.getItem()).getPath() + " ";
            ItemStack back = bottle.finishUsingItem(level, hand);
            poured += "-> " + BuiltInRegistries.ITEM.getKey(back.getItem()).getPath() + "; ";
        }
        var drunk = hand.getEffect(dev.seafarers.registry.ModEffects.DRUNK);
        boolean nausea = hand.hasEffect(net.minecraft.world.effect.MobEffects.CONFUSION);
        hand.removeAllEffects();
        hand.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.BUCKET));
        use(level, barrels.getFirst(), hand, false);
        ItemStack grogBucket = hand.getMainHandItem();
        poured += BuiltInRegistries.ITEM.getKey(grogBucket.getItem()).getPath() + " -> "
                + BuiltInRegistries.ITEM.getKey(grogBucket.finishUsingItem(level, hand).getItem()).getPath();
        var bucketDrunk = hand.getEffect(dev.seafarers.registry.ModEffects.DRUNK);
        Seafarers.LOGGER.info("PROBE grog poured {} nausea={} bucketLevel={}", poured, nausea, bucketDrunk == null ? -1 : bucketDrunk.getAmplifier());
        hand.removeAllEffects();
        for (int i = 0; i < 4; i++) {
            dev.seafarers.ship.Grog.drink(hand);
        }
        drunk = hand.getEffect(dev.seafarers.registry.ModEffects.DRUNK);
        hand.getInventory().clearContent();
        hand.setItemInHand(InteractionHand.MAIN_HAND, dev.seafarers.ship.item.ShipBucketItem.of(dev.seafarers.ship.item.ShipBucketItem.Content.EMPTY));
        dev.seafarers.ship.Grog.vomit(level, hand);
        String bucket = hand.getMainHandItem().getItem() instanceof dev.seafarers.ship.item.ShipBucketItem b ? b.content().name() : "none";
        Seafarers.LOGGER.info("PROBE grog drunk={} bucket={}", drunk == null ? -1 : drunk.getAmplifier(), bucket);
        hand.removeAllEffects();
        hand.getInventory().clearContent();
        return 1;
    }

    /** Throws a bucket of water down onto the first fire aboard, as a crewmate would. */
    private static int splashFire(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        List<BlockPos> fires = find(helm, s -> s.is(ModBlocks.SHIP_FIRE.get()));
        int thrown = 0;
        for (BlockPos f : fires) {
            if (!level.getBlockState(f).is(ModBlocks.SHIP_FIRE.get())) {
                continue; // already put out by an earlier bucket
            }
            // thrown from wherever a crewmate could stand: above the fire, or beside it where the hull is a wall
            Direction from = Direction.UP;
            for (Direction dir : new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                if (level.getBlockState(f.relative(dir)).isAir() && level.getBlockState(f.relative(dir, 2)).isAir()) {
                    from = dir;
                    break;
                }
            }
            Vec3 start = Ships.toWorld(level, Vec3.atCenterOf(f).add(Vec3.atLowerCornerOf(from.getNormal()).scale(1.5)));
            Vec3 target = Ships.toWorld(level, Vec3.atCenterOf(f));
            dev.seafarers.ship.SplashEntity splash = new dev.seafarers.ship.SplashEntity(level, testHand(level), false);
            splash.setPos(start.x, start.y, start.z);
            splash.setDeltaMovement(target.subtract(start).normalize().scale(0.6));
            level.addFreshEntity(splash);
            thrown++;
        }
        Seafarers.LOGGER.info("PROBE threw {} buckets at {} fires", thrown, fires.size());
        return thrown;
    }

    /** Takes from every supply barrel once with the stand-in player and reports what was handed over. */
    private static int supplies(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        FakePlayer hand = testHand(level);
        int used = 0;
        for (BlockPos p : find(helm, s -> s.is(ModBlocks.SUPPLY_BARREL.get()))) {
            SupplyBarrelBlock.Supply supply = level.getBlockState(p).getValue(SupplyBarrelBlock.SUPPLY);
            if (supply == SupplyBarrelBlock.Supply.GROG) {
                continue;
            }
            var barrel = (SupplyBarrelBlockEntity) level.getBlockEntity(p);
            Seafarers.LOGGER.info("PROBE barrel {} got={} slots={}", supply.getSerializedName(), barrel.stock(), barrel.getContainerSize());
            used++;
        }
        for (BlockPos p : find(helm, s -> s.is(ModBlocks.SEA_CHEST.get()) || s.is(ModBlocks.CASK.get()))) {
            var store = (net.minecraft.world.Container) level.getBlockEntity(p);
            Seafarers.LOGGER.info("PROBE storage {} slots={}", BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath(),
                    store.getContainerSize());
        }
        hand.getInventory().clearContent();
        return used;
    }

    private static int crew(CommandContext<CommandSourceStack> c, ServerPlayer player) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        boolean ok = Crews.get(c.getSource().getLevel()).join(player, shipOf(helm).getUniqueId(), helm.maxCrew());
        Seafarers.LOGGER.info("PROBE crew join {} ok={}", player.getGameProfile().getName(), ok);
        return ok ? 1 : 0;
    }

    /**
     * Puts the executing player at a spot given in ship coordinates relative to the helm (x to starboard, y up,
     * z toward the bow), looking yaw degrees off the bow and pitch down. For repeatable screenshots.
     */
    private static int view(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = (ServerLevel) helm.getLevel();
        Direction bow = helm.getBlockState().getValue(HelmBlock.FACING);
        Direction starboard = bow.getClockWise();
        Vec3 plot = Vec3.atBottomCenterOf(helm.getBlockPos())
                .add(Vec3.atLowerCornerOf(starboard.getNormal()).scale(FloatArgumentType.getFloat(c, "x")))
                .add(0, FloatArgumentType.getFloat(c, "y"), 0)
                .add(Vec3.atLowerCornerOf(bow.getNormal()).scale(FloatArgumentType.getFloat(c, "z")));
        Vec3 world = Ships.toWorld(level, plot);
        Vec3 bowWorld = Ships.toWorldNormal(level, helm.getBlockPos(), Vec3.atLowerCornerOf(bow.getNormal()));
        float bowYaw = (float) Math.toDegrees(Math.atan2(-bowWorld.x, bowWorld.z));
        player.teleportTo(level, world.x, world.y, world.z, bowYaw + FloatArgumentType.getFloat(c, "yaw"),
                FloatArgumentType.getFloat(c, "pitch"));
        return 1;
    }

    private static final java.util.Map<String, FakePlayer> HANDS = new java.util.HashMap<>();

    /** A stand-in crewmate with its own name (counted as online for crew votes). */
    private static FakePlayer hand(ServerLevel level, String name) {
        return HANDS.computeIfAbsent(name, n -> {
            FakePlayer p = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(
                    java.util.UUID.nameUUIDFromBytes(("seafarers-hand-" + n).getBytes(java.nio.charset.StandardCharsets.UTF_8)), n));
            HelmBlockEntity.TEST_HANDS.add(p.getUUID());
            return p;
        });
    }

    /** An online player, or a stand-in made by "ship hand". */
    private static ServerPlayer named(ServerLevel level, String name) {
        ServerPlayer p = level.getServer().getPlayerList().getPlayerByName(name);
        return p != null ? p : hand(level, name);
    }

    /** The player right-clicks a block aboard with an empty hand, as if they'd used it: "door" (the brig's door) or "helm". */
    private static int useAt(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        String what = StringArgumentType.getString(c, "what");
        BlockPos at = "door".equals(what) ? helm.brigDoor() : helm.getBlockPos();
        if (at == null) {
            Seafarers.LOGGER.info("PROBE useat no {}", what);
            return 0;
        }
        var held = player.getMainHandItem();
        var result = player.gameMode.useItemOn(player, level, held, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
        BlockState state = level.getBlockState(at);
        Seafarers.LOGGER.info("PROBE useat {} {} result={} open={}", player.getGameProfile().getName(), what, result,
                state.hasProperty(net.minecraft.world.level.block.DoorBlock.OPEN) ? state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN) : "-");
        return 1;
    }

    /** Logs the brig: whether she has one, its door, who's in it (and whether they're standing in it). */
    private static int brigState(CommandContext<CommandSourceStack> c) {
        HelmBlockEntity helm = nearestHelm(c.getSource());
        if (helm == null) return 0;
        ServerLevel level = c.getSource().getLevel();
        SubLevel ship = shipOf(helm);
        Crews crews = Crews.get(level);
        StringBuilder in = new StringBuilder();
        for (java.util.UUID id : crews.crewOf(ship.getUniqueId())) {
            if (crews.inBrig(id)) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                boolean standing = false;
                var box = helm.brigBox();
                if (p != null && box != null) {
                    standing = box.inflate(0.45, 0.6, 0.45).contains(ship.logicalPose().transformPositionInverse(p.position()));
                }
                in.append(in.isEmpty() ? "" : ",").append(dev.seafarers.ship.Captaincy.name(level.getServer(), id)).append(standing ? ":in" : ":out");
            }
        }
        BlockPos door = helm.brigDoor();
        String doorState = door == null ? "none" : String.valueOf(level.getBlockState(door).getOptionalValue(net.minecraft.world.level.block.DoorBlock.OPEN).orElse(null));
        Seafarers.LOGGER.info("PROBE brigstate has={} doorOpen={} prisoners={} need={}", helm.hasBrig(), doorState, in,
                dev.seafarers.ship.Brig.needed(level.getServer(), crews, ship.getUniqueId()));
        return 1;
    }

    private TestCommands() {}
}
