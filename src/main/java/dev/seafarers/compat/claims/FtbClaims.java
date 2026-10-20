package dev.seafarers.compat.claims;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/** FTB Chunks and FTB Teams. Only loaded when those mods are installed (see Claims). */
final class FtbClaims {
    static boolean claimedByOthers(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            return false;
        }
        ClaimedChunk chunk = FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(level, pos));
        if (chunk == null) {
            return false;
        }
        return !chunk.getTeamData().isTeamMember(player.getUUID()) && !chunk.getTeamData().isAlly(player.getUUID());
    }

    static boolean sameTeam(UUID a, UUID b) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return false;
        }
        Optional<Team> team = FTBTeamsAPI.api().getManager().getTeamForPlayerID(a);
        // everyone has a team of their own in FTB Teams: only a party team counts as sharing
        return team.isPresent() && team.get().isPartyTeam() && team.get().getMembers().contains(b);
    }

    private FtbClaims() {}
}
