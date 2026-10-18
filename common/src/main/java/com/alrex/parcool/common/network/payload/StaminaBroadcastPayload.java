package com.alrex.parcool.common.network.payload;

import com.alrex.parcool.common.network.MessageType;
import com.alrex.parcool.common.network.ParCoolMessage;
import com.alrex.parcool.common.network.NetworkRegistries;
import com.alrex.parcool.common.network.PayloadBuffers;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

public record StaminaBroadcastPayload(List<StaminaPayload> staminaList) implements ParCoolMessage {
    public static final MessageType<StaminaBroadcastPayload> S2C
            = NetworkRegistries.s2c("payload.stamina.broadcast", StaminaBroadcastPayload::read);

    public void write(FriendlyByteBuf buf) {
        PayloadBuffers.writeList(buf, staminaList());
    }

    public static StaminaBroadcastPayload read(FriendlyByteBuf buf) {
        return new StaminaBroadcastPayload(PayloadBuffers.readList(buf, StaminaPayload::read));
    }

    public static void handleClient(StaminaBroadcastPayload payload, NetworkManager.PacketContext context) {
        context.queue(() -> {
            for (var elem : payload.staminaList()) {
                elem.processPlayer(context);
            }
        });
    }
}
