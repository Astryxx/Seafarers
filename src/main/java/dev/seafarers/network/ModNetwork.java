package dev.seafarers.network;

import dev.seafarers.Seafarers;
import dev.seafarers.client.StationClient;
import dev.seafarers.ship.Station;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Seafarers.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetwork {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("3");
        registrar.playToClient(StationPayload.TYPE, StationPayload.CODEC,
                (payload, ctx) -> StationClient.onStation(payload));
        registrar.playToClient(TestDrivePayload.TYPE, TestDrivePayload.CODEC,
                (payload, ctx) -> dev.seafarers.client.TestDrive.start(payload));
        registrar.playToClient(MapTablePayloads.Open.TYPE, MapTablePayloads.Open.CODEC,
                (payload, ctx) -> dev.seafarers.client.MapTableScreen.open(payload));
        registrar.playToServer(MapTablePayloads.Action.TYPE, MapTablePayloads.Action.CODEC, (payload, ctx) -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player.level() instanceof net.minecraft.server.level.ServerLevel level && level.isLoaded(payload.pos())
                    && level.getBlockEntity(payload.pos()) instanceof dev.seafarers.ship.block.MapTableBlock.Table table
                    && dev.seafarers.ship.Ships.toWorld(level, payload.pos()).distanceTo(player.position()) < 8) {
                if (payload.action() == MapTablePayloads.Action.VIEW) {
                    table.setView(payload.x(), payload.z(), payload.span());
                } else {
                    table.mark(level, player, payload.x(), payload.z(), payload.action() == MapTablePayloads.Action.UNMARK, payload.span());
                }
            }
        });
        registrar.playToClient(VoyagePayloads.State.TYPE, VoyagePayloads.State.CODEC,
                (payload, ctx) -> dev.seafarers.client.VoyageTableScreen.show(payload));
        registrar.playToServer(VoyagePayloads.Action.TYPE, VoyagePayloads.Action.CODEC, (payload, ctx) -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player.level() instanceof net.minecraft.server.level.ServerLevel level && level.isLoaded(payload.pos())
                    && level.getBlockEntity(payload.pos()) instanceof dev.seafarers.ship.block.VoyageTableBlock.Table table
                    && dev.seafarers.ship.Ships.toWorld(level, payload.pos()).distanceTo(player.position()) < 8) {
                table.act(level, player, payload.action(), payload.index(), payload.text());
            }
        });
        registrar.playToClient(ChoicePayloads.Open.TYPE, ChoicePayloads.Open.CODEC,
                (payload, ctx) -> dev.seafarers.client.ShipChoiceScreen.show(payload));
        registrar.playToClient(ShipPosPayload.TYPE, ShipPosPayload.CODEC, (payload, ctx) -> dev.seafarers.client.ShipPosClient.receive(payload));
        registrar.playToClient(HintPayload.TYPE, HintPayload.CODEC, (payload, ctx) -> dev.seafarers.client.HintsClient.show(payload));
        registrar.playToClient(ShipHudPayload.TYPE, ShipHudPayload.CODEC, (payload, ctx) -> dev.seafarers.client.ShipHud.receive(payload));
        registrar.playToServer(ChoicePayloads.Pick.TYPE, ChoicePayloads.Pick.CODEC,
                (payload, ctx) -> dev.seafarers.ship.deed.ShipChoice.pick((ServerPlayer) ctx.player(), payload.mode(), payload.choice()));
        registrar.playToServer(StationInputPayload.TYPE, StationInputPayload.CODEC, (payload, ctx) -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player.level().isLoaded(payload.pos())
                    && player.level().getBlockEntity(payload.pos()) instanceof Station station) {
                station.onStationInput(player, payload.steer(), payload.forward(), payload.fire(), payload.letGo());
            } else if (player.level().isLoaded(payload.pos())) {
                dev.seafarers.ship.RopeStations.input(player, payload.pos(), payload.steer(), payload.forward(), payload.letGo());
            }
        });
    }

    private ModNetwork() {}
}
