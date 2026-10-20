package dev.seafarers.ship;

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.SeafarersConfig;
import dev.seafarers.ship.block.BrigDoorBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The brig, as in Sea of Thieves: the Brigantine and the Galleon have a cell below decks, and when most of a crew
 * votes for it (/crew brig, or the crew page of the voyage table) a crewmate is locked in it. They stay at least
 * brigMinimumMinutes; after that the crew votes them out the same way (the prisoner's own vote counts). In the brig
 * they can't work the ship (no blocks, no fittings) but can use what they carry, bail with a bucket included;
 * explosions don't reach them. Respawning or any other way back to the ship puts them back in the brig, and the
 * door stays locked while anyone is in it. Leaving or being put off the crew ends it.
 * <p>
 * A majority is more than half of the crew who are online. Votes are kept until they pass or the voter takes theirs
 * back (voting again withdraws it); they're not saved, prisoners are (in {@link Crews}).
 */
@EventBusSubscriber(modid = Seafarers.MODID)
public final class Brig {
    /** Ship -> crewmate -> who has voted to lock them up (or, if they're in, to let them out). */
    private static final Map<UUID, Map<UUID, Set<UUID>>> VOTES = new HashMap<>();

    /**
     * A vote to lock {@code target} in the brig (lock) or let them out. Voting again for the same takes it back.
     * @return whether a vote was cast or withdrawn
     */
    public static boolean vote(ServerPlayer voter, UUID target, boolean lock, Reply reply) {
        if (!SeafarersConfig.ALLOW_BRIG.get()) {
            reply.fail("message.seafarers.brig_off");
            return false;
        }
        ServerLevel level = voter.serverLevel();
        Crews crews = Crews.get(level);
        UUID ship = crews.shipOf(voter.getUUID());
        if (ship == null) {
            reply.fail("message.seafarers.no_ship");
            return false;
        }
        if (!crews.crewOf(ship).contains(target)) {
            reply.fail("message.seafarers.not_in_crew");
            return false;
        }
        HelmBlockEntity helm = CrewEvents.helmOfShip(level, ship);
        if (helm == null) {
            reply.fail("message.seafarers.brig_far");
            return false;
        }
        if (!helm.hasBrig()) {
            reply.fail("message.seafarers.no_brig");
            return false;
        }
        boolean in = crews.inBrig(target);
        if (lock && in) {
            reply.fail("message.seafarers.brig_already");
            return false;
        }
        if (!lock && !in) {
            reply.fail("message.seafarers.brig_not_in");
            return false;
        }
        if (lock && target.equals(voter.getUUID())) {
            reply.fail("message.seafarers.brig_self");
            return false;
        }
        if (!lock) {
            long left = crews.brigSince(target) + SeafarersConfig.BRIG_MINUTES.get() * 1200L - level.getGameTime();
            if (left > 0) {
                reply.fail("message.seafarers.brig_too_soon", (left + 1199) / 1200);
                return false;
            }
        }
        Set<UUID> votes = VOTES.computeIfAbsent(ship, s -> new HashMap<>()).computeIfAbsent(target, t -> new HashSet<>());
        MinecraftServer server = voter.server;
        String who = Captaincy.name(server, target);
        if (!votes.add(voter.getUUID())) {
            votes.remove(voter.getUUID());
            reply.ok(Component.translatable("message.seafarers.brig_withdrawn", who));
            Seafarers.LOGGER.info("PROBE brig vote withdrawn {} -> {}", voter.getGameProfile().getName(), who);
            return true;
        }
        int count = tally(server, crews, ship, target), need = needed(server, crews, ship);
        Component said = Component.translatable(lock ? "message.seafarers.brig_vote" : "message.seafarers.brig_vote_release",
                voter.getDisplayName(), who, count, need).withStyle(ChatFormatting.GOLD);
        for (UUID id : crews.crewOf(ship)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                p.sendSystemMessage(said);
            }
        }
        Seafarers.LOGGER.info("PROBE brig vote {} {} {} ({}/{})", voter.getGameProfile().getName(), lock ? "lock" : "release", who, count, need);
        if (count >= need) {
            Set<UUID> voters = Set.copyOf(votes);
            VOTES.get(ship).remove(target);
            if (lock) {
                lockUp(level, helm, ship, target, voters);
            } else {
                letOut(level, helm, ship, target);
            }
        }
        return true;
    }

    /** Votes for this crewmate that still count: from crew who are online. */
    public static int tally(MinecraftServer server, Crews crews, UUID ship, UUID target) {
        Set<UUID> votes = VOTES.getOrDefault(ship, Map.of()).getOrDefault(target, Set.of());
        List<UUID> crew = crews.crewOf(ship);
        int n = 0;
        for (UUID v : votes) {
            if (crew.contains(v) && online(server, v)) {
                n++;
            }
        }
        return n;
    }

    /** More than half of her crew who are online. */
    public static int needed(MinecraftServer server, Crews crews, UUID ship) {
        int online = 0;
        for (UUID id : crews.crewOf(ship)) {
            if (online(server, id)) {
                online++;
            }
        }
        return online / 2 + 1;
    }

    /** Online, or one of the test commands' stand-in crewmates. */
    private static boolean online(MinecraftServer server, UUID id) {
        return server.getPlayerList().getPlayer(id) != null || HelmBlockEntity.TEST_HANDS.contains(id);
    }

    public static boolean voted(UUID ship, UUID target, UUID voter) {
        return VOTES.getOrDefault(ship, Map.of()).getOrDefault(target, Set.of()).contains(voter);
    }

    private static void lockUp(ServerLevel level, HelmBlockEntity helm, UUID ship, UUID target, Set<UUID> voters) {
        Crews crews = Crews.get(level);
        crews.setBrig(target, level.getGameTime());
        setDoor(level, helm, false);
        MinecraftServer server = level.getServer();
        ServerPlayer prisoner = server.getPlayerList().getPlayer(target);
        if (prisoner != null) {
            putInBrig(prisoner, helm);
            prisoner.sendSystemMessage(Component.translatable("message.seafarers.brig_you", SeafarersConfig.BRIG_MINUTES.get())
                    .withStyle(ChatFormatting.RED));
        }
        String name = Captaincy.name(server, target);
        for (UUID id : crews.crewOf(ship)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && !id.equals(target)) {
                p.sendSystemMessage(Component.translatable("message.seafarers.brig_locked", name).withStyle(ChatFormatting.GOLD));
            }
        }
        for (UUID v : voters) {
            Feats.award(server.getPlayerList().getPlayer(v), "brig");
        }
        Seafarers.LOGGER.info("PROBE brig locked {}", name);
    }

    private static void letOut(ServerLevel level, HelmBlockEntity helm, UUID ship, UUID target) {
        Crews crews = Crews.get(level);
        crews.setBrig(target, -1);
        if (!occupied(level, ship)) {
            setDoor(level, helm, true);
        }
        MinecraftServer server = level.getServer();
        String name = Captaincy.name(server, target);
        for (UUID id : crews.crewOf(ship)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                p.sendSystemMessage(Component.translatable(id.equals(target) ? "message.seafarers.brig_free_you" : "message.seafarers.brig_free", name)
                        .withStyle(ChatFormatting.AQUA));
            }
        }
        Seafarers.LOGGER.info("PROBE brig released {}", name);
    }

    /** Whether any of this ship's crew is in her brig. */
    public static boolean occupied(ServerLevel level, UUID ship) {
        Crews crews = Crews.get(level);
        for (UUID id : crews.crewOf(ship)) {
            if (crews.inBrig(id)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the brig this door shuts is locked: someone of her crew is in it. */
    public static boolean doorLocked(ServerLevel level, BlockPos door) {
        SubLevel ship = Ships.shipAt(level, door);
        return ship != null && occupied(level, ship.getUniqueId());
    }

    private static void setDoor(ServerLevel level, HelmBlockEntity helm, boolean open) {
        BlockPos door = helm.brigDoor();
        BlockState state = door == null ? null : level.getBlockState(door);
        if (state != null && state.getBlock() instanceof BrigDoorBlock block) {
            block.setOpen(null, level, state, door, open);
        }
    }

    /** Where a prisoner stands: the middle of the brig's floor, in the world. */
    @Nullable
    public static Vec3 spot(HelmBlockEntity helm) {
        AABB box = helm.brigBox();
        if (box == null || helm.getLevel() == null) {
            return null;
        }
        return Ships.toWorld(helm.getLevel(), new Vec3(box.getCenter().x, box.minY + 0.05, box.getCenter().z));
    }

    private static void putInBrig(ServerPlayer player, HelmBlockEntity helm) {
        Vec3 at = spot(helm);
        if (at == null) {
            return;
        }
        player.stopRiding();
        player.teleportTo((ServerLevel) helm.getLevel(), at.x, at.y, at.z, player.getYRot(), player.getXRot());
    }

    /** The brig, for someone respawning: the prisoner's respawn point, or null if they're not in one. */
    @Nullable
    public static Vec3 respawnPoint(ServerLevel level, UUID player) {
        Crews crews = Crews.get(level);
        if (!crews.inBrig(player)) {
            return null;
        }
        HelmBlockEntity helm = CrewEvents.helmOfShip(level, crews.shipOf(player));
        return helm == null || !helm.hasBrig() ? null : spot(helm);
    }

    /** Is this player in their ship's brig, standing in it? */
    private static boolean inside(ServerPlayer player, HelmBlockEntity helm) {
        AABB box = helm.brigBox();
        SubLevel ship = Ships.shipAt(player.level(), helm.getBlockPos());
        if (box == null || ship == null || player.level() != helm.getLevel()) {
            return false;
        }
        Vec3 local = ship.logicalPose().transformPositionInverse(player.position());
        return box.inflate(0.45, 0.6, 0.45).contains(local);
    }

    /** Twice a second: prisoners who have got out (or come back to the ship some other way) go back in, and the door stays shut. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        if (overworld.getGameTime() % 10 != 0) {
            return;
        }
        Crews crews = Crews.get(overworld);
        for (UUID id : List.copyOf(crews.prisoners())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || !player.isAlive() || player.isSpectator()) {
                continue;
            }
            HelmBlockEntity helm = CrewEvents.helmOfShip(player.serverLevel(), crews.shipOf(id));
            if (helm != null && !helm.hasBrig()) {
                crews.setBrig(id, -1);   // her captain swapped her for a ship with no brig: that's the end of it
                Seafarers.LOGGER.info("PROBE brig gone, freed {}", player.getGameProfile().getName());
                continue;
            }
            if (helm == null || helm.sinking()) {
                continue;
            }
            if (!inside(player, helm)) {
                putInBrig(player, helm);
                Notify.show(player, Component.translatable("message.seafarers.brig_back").withStyle(ChatFormatting.RED));
                Seafarers.LOGGER.info("PROBE brig returned {}", player.getGameProfile().getName());
            }
            BlockPos door = helm.brigDoor();
            BlockState state = player.serverLevel().getBlockState(door);
            if (state.getBlock() instanceof BrigDoorBlock && state.getValue(BrigDoorBlock.OPEN)) {
                setDoor(player.serverLevel(), helm, false);
            }
        }
    }

    /** Prisoners can't work the ship: no blocks aboard but bailing (any bucket), nothing broken. */
    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && Crews.get(player.serverLevel()).inBrig(player.getUUID())
                && Ships.shipAt(player.level(), event.getPos()) != null && !isBucket(event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            Notify.show(player, Component.translatable("message.seafarers.brig_cant"));
        }
    }

    @SubscribeEvent
    public static void onHitBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && Crews.get(player.serverLevel()).inBrig(player.getUUID())
                && Ships.shipAt(player.level(), event.getPos()) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && Crews.get(player.serverLevel()).inBrig(player.getUUID())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Explosions (and cannon fire) don't reach into the brig. */
    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Crews.get(player.serverLevel()).inBrig(player.getUUID())
                && (event.getSource().is(DamageTypeTags.IS_EXPLOSION) || event.getSource().is(dev.seafarers.registry.ModDamage.CANNONBALL))) {
            HelmBlockEntity helm = CrewEvents.helmOfShip(player.serverLevel(), Crews.get(player.serverLevel()).shipOf(player.getUUID()));
            if (helm != null && inside(player, helm)) {
                event.setCanceled(true);
                Seafarers.LOGGER.info("PROBE brig sheltered {} from {}", player.getGameProfile().getName(), event.getSource().getMsgId());
            }
        }
    }

    private static boolean isBucket(ItemStack stack) {
        return stack.getItem() instanceof BucketItem || stack.getItem() instanceof dev.seafarers.ship.item.ShipBucketItem;
    }

    /** Forgets the votes on a ship that's gone. */
    public static void forget(UUID ship) {
        VOTES.remove(ship);
    }

    private Brig() {}
}
