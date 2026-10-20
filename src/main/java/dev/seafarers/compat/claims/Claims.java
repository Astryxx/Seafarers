package dev.seafarers.compat.claims;

import dev.seafarers.Seafarers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.UUID;

/**
 * Land claims and teams from other mods, when they're installed: Open Parties and Claims, and FTB Chunks with
 * FTB Teams. Each mod's own classes are only touched from its own class here, and only when it's loaded, so
 * neither is needed.
 */
public final class Claims {
    private static final boolean OPAC = ModList.get().isLoaded("openpartiesandclaims");
    private static final boolean FTB_CHUNKS = ModList.get().isLoaded("ftbchunks");
    private static final boolean FTB_TEAMS = ModList.get().isLoaded("ftbteams");

    /** Whether pos is in land claimed by someone outside this player's party or team. */
    public static boolean claimedByOthers(ServerPlayer player, ServerLevel level, BlockPos pos) {
        try {
            return (OPAC && OpacClaims.claimedByOthers(player, level, pos)) || (FTB_CHUNKS && FtbClaims.claimedByOthers(player, level, pos));
        } catch (Throwable t) {
            Seafarers.LOGGER.warn("Couldn't check land claims: {}", t.toString());
            return false;
        }
    }

    /** Whether two players share a party (Open Parties and Claims) or a team (FTB Teams). */
    public static boolean sameTeam(MinecraftServer server, UUID a, UUID b) {
        try {
            return (OPAC && OpacClaims.sameParty(server, a, b)) || (FTB_TEAMS && FtbClaims.sameTeam(a, b));
        } catch (Throwable t) {
            Seafarers.LOGGER.warn("Couldn't check teams: {}", t.toString());
            return false;
        }
    }

    /** For tests: claims (or unclaims) a chunk for someone else with Open Parties and Claims. False if it isn't installed. */
    public static boolean testClaim(ServerLevel level, int chunkX, int chunkZ, boolean claim) {
        if (!OPAC) {
            return false;
        }
        OpacClaims.testClaim(level, chunkX, chunkZ, claim);
        return true;
    }

    private Claims() {}
}
