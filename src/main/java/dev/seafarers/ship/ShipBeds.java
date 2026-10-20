package dev.seafarers.ship;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Beds aboard ship. A mattress on a ship is a real bed that sails with her:
 * - using it makes it your respawn point (you join the ship's crew if there's room), wherever the ship goes;
 * - at night you sleep in it and count toward skipping the night exactly as in a vanilla bed (it is vanilla's own
 *   sleep; only the bed's position is kept up to date as the ship moves);
 * - ships with a crew stay loaded, so the crew can always respawn aboard, however far she is from anyone.
 * Setting your spawn ashore afterwards (a vanilla bed, an anchor, another mod's) takes precedence until you use
 * a mattress again. It all goes through vanilla's sleep and NeoForge's events (CanPlayerSleep,
 * CanContinueSleeping, PlayerSetSpawn), so other mods' sleep and spawn rules still apply.
 */
@EventBusSubscriber(modid = Seafarers.MODID)
public final class ShipBeds {
    /** Persistent data on a sleeping player: the plot position of the mattress they're in. */
    private static final String SLEEPING = "seafarers:mattress";

    public static void use(ServerLevel level, BlockPos mattress, ServerPlayer player) {
        SubLevel ship = Ships.shipAt(level, mattress);
        HelmBlockEntity helm = ship == null ? null : Ships.helmOf(ship);
        if (helm == null) {
            dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.mattress_ashore"));
            return;
        }
        Crews crews = Crews.get(level);
        UUID id = ship.getUniqueId();
        if (!id.equals(crews.shipOf(player.getUUID()))) {
            if (!crews.join(player, id, helm.maxCrew())) {
                dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.crew_full", helm.maxCrew())
                        .withStyle(ChatFormatting.RED));
                return;
            }
            player.sendSystemMessage(Component.translatable("message.seafarers.joined").withStyle(ChatFormatting.AQUA));
        }
        crews.setBed(player.getUUID(), mattress);
        Seafarers.LOGGER.info("PROBE bed set {} at {}", player.getGameProfile().getName(), mattress.toShortString());

        Vec3 at = Ships.toWorld(level, mattress);
        Player.BedSleepingProblem problem = null;
        if (!level.dimensionType().natural()) {
            problem = Player.BedSleepingProblem.NOT_POSSIBLE_HERE;
        } else if (level.isDay() && !level.isThundering()) {
            problem = Player.BedSleepingProblem.NOT_POSSIBLE_NOW;
        } else if (!player.isCreative() && !level.getEntitiesOfClass(Monster.class, new AABB(at, at).inflate(8, 5, 8),
                m -> m.isPreventingPlayerRest(player)).isEmpty()) {
            problem = Player.BedSleepingProblem.NOT_SAFE;
        }
        problem = EventHooks.canPlayerStartSleeping(player, BlockPos.containing(at),
                problem == null ? com.mojang.datafixers.util.Either.right(net.minecraft.util.Unit.INSTANCE)
                        : com.mojang.datafixers.util.Either.left(problem)).left().orElse(null);
        if (problem != null) {
            // it's still your respawn point, even if you can't sleep now
            Component why = problem.getMessage();
            dev.seafarers.ship.Notify.show(player, why != null && problem != Player.BedSleepingProblem.NOT_POSSIBLE_NOW
                    ? why : Component.translatable("message.seafarers.bed_set"));
            Seafarers.LOGGER.info("PROBE no sleep: {}", problem);
            return;
        }
        player.getPersistentData().putLong(SLEEPING, mattress.asLong());
        player.startSleeping(BlockPos.containing(at));
        follow(level, player, mattress);
        player.awardStat(Stats.SLEEP_IN_BED);
        CriteriaTriggers.SLEPT_IN_BED.trigger(player);
        level.updateSleepingPlayerList();
        dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.bed_set"));
        Seafarers.LOGGER.info("PROBE sleeping {} aboard", player.getGameProfile().getName());
    }

    /** Lays the sleeper on the mattress where it is now (the ship may have moved). */
    private static boolean follow(ServerLevel level, ServerPlayer player, BlockPos mattress) {
        if (!level.getBlockState(mattress).is(ModBlocks.MATTRESS.get()) || Ships.shipAt(level, mattress) == null) {
            return false;
        }
        Vec3 top = Ships.toWorld(level, Vec3.atBottomCenterOf(mattress).add(0, 9 / 16.0, 0));
        player.setPos(top.x, top.y, top.z);
        player.setSleepingPos(BlockPos.containing(top));
        player.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    /** Vanilla wakes a sleeper whose bed block has gone; a ship's mattress is somewhere else in the world every tick. */
    @SubscribeEvent
    public static void onContinue(CanContinueSleepingEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getPersistentData().contains(SLEEPING)
                && (event.getProblem() == null || event.getProblem() == Player.BedSleepingProblem.NOT_POSSIBLE_HERE)) {
            event.setContinueSleeping(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.getPersistentData().contains(SLEEPING)) {
            return;
        }
        BlockPos mattress = BlockPos.of(player.getPersistentData().getLong(SLEEPING));
        if (!player.isSleeping()) {
            player.getPersistentData().remove(SLEEPING); // woke up: morning, or got out
        } else if (!follow(player.serverLevel(), player, mattress)) {
            player.getPersistentData().remove(SLEEPING);
            player.stopSleepInBed(true, true); // the bunk was broken or the ship is gone
        }
    }

    /** A spawn set ashore wins until a mattress is used again. */
    @SubscribeEvent
    public static void onSetSpawn(PlayerSetSpawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getNewSpawn() != null && !event.isForced()) {
            Crews.get(player.serverLevel()).setAshore(player.getUUID(), true);
        }
    }

    /** Where a crew member comes back: their mattress if it's still aboard, else the ship's respawn spot. */
    @Nullable
    public static Vec3 respawnPoint(ServerLevel overworld, UUID player) {
        Crews crews = Crews.get(overworld);
        if (crews.ashore(player)) {
            return null;
        }
        HelmBlockEntity helm = CrewEvents.helmOfShip(overworld, crews.shipOf(player));
        if (helm == null) {
            return null;
        }
        Vec3 brig = Brig.respawnPoint(overworld, player);
        if (brig != null) {
            return brig;   // the brig keeps its prisoner, dead or alive
        }
        BlockPos bed = crews.bedOf(player);
        if (bed != null && overworld.getBlockState(bed).is(ModBlocks.MATTRESS.get())
                && Ships.shipAt(overworld, bed) == Ships.shipAt(overworld, helm.getBlockPos())) {
            return Ships.toWorld(overworld, Vec3.atBottomCenterOf(bed).add(0, 9 / 16.0, 0));
        }
        return helm.respawnWorldPos(overworld.random);
    }

    /** Keeps ships with a crew loaded (Sable unloads ships no player is near), so the crew can always respawn aboard. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        if (level.getGameTime() % 100 != 0 || !dev.seafarers.SeafarersConfig.KEEP_CREWED_SHIPS_LOADED.get()) {
            return;
        }
        Crews crews = Crews.get(level);
        var container = SubLevelContainer.getContainer(level);
        for (SubLevel s : container.getAllSubLevels()) {
            if (s instanceof ServerSubLevel ship && Ships.helmOf(ship) != null) {
                if (crews.crewOf(ship.getUniqueId()).isEmpty()) {
                    container.removeForceLoadTicket(ship, ShipTickets.CREW, net.minecraft.util.Unit.INSTANCE);
                } else {
                    container.addForceLoadTicket(ship, ShipTickets.CREW, net.minecraft.util.Unit.INSTANCE);
                }
            }
        }
    }

    private ShipBeds() {}
}
