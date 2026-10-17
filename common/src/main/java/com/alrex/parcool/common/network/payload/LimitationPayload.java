package com.alrex.parcool.common.network.payload;

import com.alrex.parcool.common.data.Parkourability;
import com.alrex.parcool.common.data.client.LocalStamina;
import com.alrex.parcool.common.info.ServerLimitation;
import com.alrex.parcool.common.network.MessageType;
import com.alrex.parcool.common.network.ParCoolMessage;
import com.alrex.parcool.common.network.NetworkRegistries;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;

public record LimitationPayload(ServerLimitation limitation) implements ParCoolMessage {
    public static final MessageType<LimitationPayload> S2C
            = NetworkRegistries.s2c("payload.limitation", LimitationPayload::read);

    public void write(FriendlyByteBuf buf) {
        limitation().writeTo(buf);
    }

    public static LimitationPayload read(FriendlyByteBuf buf) {
        return new LimitationPayload(ServerLimitation.readFrom(buf));
    }

    public static void handleClient(LimitationPayload payload, NetworkManager.PacketContext context) {
        context.queue(() -> {
            var player = context.getPlayer();
            Parkourability parkourability = Parkourability.get(player);
            parkourability.getActionInfo().setServerLimitation(payload.limitation());
            if (player instanceof LocalPlayer localPlayer) {
                parkourability.getActionInfo().updateStaminaType(LocalStamina.get(localPlayer), localPlayer);
            }
        });
    }

    public static void handleServer(LimitationPayload payload, NetworkManager.PacketContext context) {
        throw new UnsupportedOperationException("This should have been designed not to be called");
    }
}
