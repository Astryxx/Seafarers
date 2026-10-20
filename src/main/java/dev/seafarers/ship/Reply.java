package dev.seafarers.ship;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Where the answer to a player's action goes: back to whoever ran the command (their chat, or the server
 * console for a command run there), or to the player's chat for a button on a screen.
 */
public interface Reply {
    void send(Component message, boolean ok);

    default void ok(Component message) {
        this.send(message, true);
    }

    default void fail(String key, Object... args) {
        this.send(Component.translatable(key, args).withStyle(ChatFormatting.RED), false);
    }

    static Reply to(ServerPlayer player) {
        return (message, ok) -> player.sendSystemMessage(message);
    }

    static Reply to(CommandSourceStack source) {
        return (message, ok) -> {
            if (ok) {
                source.sendSuccess(() -> message, false);
            } else {
                source.sendFailure(message);
            }
        };
    }
}
