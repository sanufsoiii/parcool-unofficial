package com.alrex.parcool.common.network.payload;

import com.alrex.parcool.common.network.MessageType;
import com.alrex.parcool.common.network.ParCoolMessage;
import com.alrex.parcool.common.network.NetworkRegistries;
import com.alrex.parcool.common.network.PayloadBuffers;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

public record ActionStateBroadcastPayload(List<ActionStatePayload> payloads) implements ParCoolMessage {
    public static final MessageType<ActionStateBroadcastPayload> S2C
            = NetworkRegistries.s2c("payload.action_state.broadcast", ActionStateBroadcastPayload::read);

    public void write(FriendlyByteBuf buf) {
        PayloadBuffers.writeList(buf, payloads());
    }

    public static ActionStateBroadcastPayload read(FriendlyByteBuf buf) {
        return new ActionStateBroadcastPayload(PayloadBuffers.readList(buf, ActionStatePayload::read));
    }

    public static void handleClient(ActionStateBroadcastPayload payload, NetworkManager.PacketContext context) {
        context.queue(() -> {
            Player player;
            Level world = context.getPlayer().level();
            for (var statePayload : payload.payloads()) {
                player = world.getPlayerByUUID(statePayload.playerID());
                // `continue`, not `return`: upstream aborted the whole batch as soon as one UUID was
                // unresolvable (entity still loading) or referred to the local player, which silently
                // dropped every other player's action state in the same packet.
                if (player == null || player.isLocalPlayer()) continue;

                statePayload.processPlayer(player);
            }
        });
    }
}
