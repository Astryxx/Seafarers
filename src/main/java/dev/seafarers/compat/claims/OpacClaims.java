package dev.seafarers.compat.claims;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import java.util.UUID;

/** Open Parties and Claims. Only loaded when that mod is installed (see Claims). */
final class OpacClaims {
    static boolean claimedByOthers(ServerPlayer player, ServerLevel level, BlockPos pos) {
        OpenPACServerAPI api = OpenPACServerAPI.get(level.getServer());
        IPlayerChunkClaimAPI claim = api.getServerClaimsManager().get(level.dimension().location(), pos.getX() >> 4, pos.getZ() >> 4);
        if (claim == null || claim.getPlayerId().equals(player.getUUID())) {
            return false;
        }
        return !sameParty(level.getServer(), player.getUUID(), claim.getPlayerId());
    }

    static boolean sameParty(MinecraftServer server, UUID a, UUID b) {
        IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(a);
        return party != null && party.getMemberInfo(b) != null;
    }

    static void testClaim(ServerLevel level, int chunkX, int chunkZ, boolean claim) {
        var claims = OpenPACServerAPI.get(level.getServer()).getServerClaimsManager();
        if (claim) {
            claims.claim(level.dimension().location(), UUID.nameUUIDFromBytes("seafarers-test-owner".getBytes()), 0, chunkX, chunkZ, false);
        } else {
            claims.unclaim(level.dimension().location(), chunkX, chunkZ);
        }
    }

    private OpacClaims() {}
}
