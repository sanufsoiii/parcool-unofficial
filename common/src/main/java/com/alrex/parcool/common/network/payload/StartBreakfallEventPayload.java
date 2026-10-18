package com.alrex.parcool.common.network.payload;

import com.alrex.parcool.common.action.impl.BreakfallReady;
import com.alrex.parcool.common.data.Parkourability;
import com.alrex.parcool.common.network.MessageType;
import com.alrex.parcool.common.network.ParCoolMessage;
import com.alrex.parcool.common.network.NetworkRegistries;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

public record StartBreakfallEventPayload(boolean justTimed) implements ParCoolMessage {
    public static final MessageType<StartBreakfallEventPayload> S2C
            = NetworkRegistries.s2c("payload.start_breakfall_event", StartBreakfallEventPayload::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(justTimed());
    }

    public static StartBreakfallEventPayload read(FriendlyByteBuf buf) {
        return new StartBreakfallEventPayload(buf.readBoolean());
    }

    public static void handleClient(StartBreakfallEventPayload payload, NetworkManager.PacketContext context) {
        context.queue(() -> {
            Player player = context.getPlayer();
            Parkourability parkourability = Parkourability.get(player);
            parkourability.get(BreakfallReady.class).startBreakfall(player, parkourability, payload.justTimed());
        });
    }

    public static void handleServer(StartBreakfallEventPayload payload, NetworkManager.PacketContext context) {
        throw new UnsupportedOperationException("This should have been designed not to be called");
    }
}
