package dev.seafarers.compat.voicechat;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import dev.seafarers.Seafarers;
import dev.seafarers.SeafarersConfig;
import dev.seafarers.ship.Captaincy;
import dev.seafarers.ship.Crews;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simple Voice Chat (optional): each crew gets a voice group of its own, named after the ship and hidden from the
 * group list (with a password nobody knows), and crew members are put in it when they join the crew or come
 * online. They can leave it or pick another group; they aren't pulled back until their crew changes. Players
 * near the ship still hear them as usual. Only loaded when Simple Voice Chat is installed.
 */
@ForgeVoicechatPlugin
public class SeafarersVoicechatPlugin implements VoicechatPlugin {
    private static VoicechatServerApi api;
    /** The ship each player was last put in the voice group of. */
    private static final Map<UUID, UUID> PLACED = new HashMap<>();
    private static boolean listening;

    @Override
    public String getPluginId() {
        return Seafarers.MODID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, e -> {
            api = e.getVoicechat();
            PLACED.clear();
            if (!listening) {
                listening = true;
                NeoForge.EVENT_BUS.addListener(SeafarersVoicechatPlugin::onTick);
            }
        });
    }

    private static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (api == null || server.overworld().getGameTime() % 40 != 0 || !SeafarersConfig.CREW_VOICE_GROUPS.get()) {
            return;
        }
        try {
            Crews crews = Crews.get(server.overworld());
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID ship = crews.shipOf(player.getUUID());
                UUID placed = PLACED.get(player.getUUID());
                VoicechatConnection conn = api.getConnectionOf(player.getUUID());
                if (conn == null || !conn.isInstalled()) {
                    continue;
                }
                if (ship == null) {
                    if (placed != null) {
                        // left the crew: out of its group, if still in it
                        Group now = conn.getGroup();
                        if (now != null && now.getId().equals(placed)) {
                            conn.setGroup(null);
                        }
                        PLACED.remove(player.getUUID());
                    }
                } else if (!ship.equals(placed)) {
                    conn.setGroup(group(server, ship));
                    PLACED.put(player.getUUID(), ship);
                    Seafarers.LOGGER.info("PROBE voice group {} for {}", ship, player.getGameProfile().getName());
                }
            }
        } catch (Throwable t) {
            Seafarers.LOGGER.warn("Couldn't update crew voice groups: {}", t.toString());
        }
    }

    private static Group group(MinecraftServer server, UUID ship) {
        // (getGroup(id) hands back an empty wrapper for a group that doesn't exist, so look through the real ones)
        for (Group g : api.getGroups()) {
            if (ship.equals(g.getId())) {
                return g;
            }
        }
        return api.groupBuilder().setId(ship).setName(Captaincy.shipName(server, ship)).setPassword(UUID.randomUUID().toString())
                .setHidden(true).setPersistent(false).setType(Group.Type.NORMAL).build();
    }
}
