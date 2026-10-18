package com.alrex.parcool.common.network.payload;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.data.Parkourability;
import com.alrex.parcool.common.info.ClientSetting;
import com.alrex.parcool.common.network.MessageType;
import com.alrex.parcool.common.network.ParCoolMessage;
import com.alrex.parcool.common.network.NetworkRegistries;
import com.alrex.parcool.common.network.ServerPayloadGuard;
import com.alrex.parcool.server.limitation.Limitations;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

public record ClientInformationPayload(UUID playerID, boolean requestLimitation,
                                       ClientSetting information) implements ParCoolMessage {
    public static final MessageType<ClientInformationPayload> S2C
            = NetworkRegistries.s2c("payload.client_info", ClientInformationPayload::read);
    public static final MessageType<ClientInformationPayload> C2S
            = NetworkRegistries.c2s("payload.client_info", ClientInformationPayload::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(playerID());
        buf.writeBoolean(requestLimitation());
        information().writeTo(buf);
    }

    public static ClientInformationPayload read(FriendlyByteBuf buf) {
        UUID playerID = buf.readUUID();
        boolean requestLimitation = buf.readBoolean();
        return new ClientInformationPayload(playerID, requestLimitation, ClientSetting.readFrom(buf));
    }

    public static void handleClient(ClientInformationPayload payload, NetworkManager.PacketContext context) {
        context.queue(() -> {
            Level world = context.getPlayer().level();
            var player = world.getPlayerByUUID(payload.playerID());
            if (player == null || player.isLocalPlayer()) return;
            Parkourability parkourability = Parkourability.get(player);
            if (parkourability == null) return;
            parkourability.getActionInfo().setClientSetting(payload.information());
        });
    }

    public static void handleServer(ClientInformationPayload payload, NetworkManager.PacketContext context) {
        context.queue(() -> {
            Player player = context.getPlayer();
            // The client may only describe itself: upstream broadcast the unvalidated playerID and
            // ClientSetting, so a modified client could overwrite every other player's client
            // settings on every client.
            if (!ServerPayloadGuard.isSelf(player, payload.playerID())) {
                ParCool.LOGGER.debug("Rejected client info for foreign player {}", payload.playerID());
                return;
            }
            NetworkRegistries.sendToAllPlayers(ClientInformationPayload.S2C, payload);

            Parkourability parkourability = Parkourability.get(player);
            if (parkourability == null) return;
            if (player instanceof ServerPlayer serverPlayer && payload.requestLimitation()) {
                Limitations.update(serverPlayer);
            }
            parkourability.getActionInfo().setClientSetting(payload.information());
        });
    }
}
