package com.alrex.parcool.common.network.payload;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.network.MessageType;
import com.alrex.parcool.common.network.ParCoolMessage;
import com.alrex.parcool.common.network.NetworkRegistries;
import com.alrex.parcool.common.network.ServerPayloadGuard;
import com.alrex.parcool.common.stamina.StaminaType;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

public record StaminaProcessOnServerPayload(StaminaType stamina, int value) implements ParCoolMessage {
    public static final MessageType<StaminaProcessOnServerPayload> C2S
            = NetworkRegistries.c2s("payload.custom_stamina", StaminaProcessOnServerPayload::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(stamina().ordinal());
        buf.writeVarInt(value());
    }

    public static StaminaProcessOnServerPayload read(FriendlyByteBuf buf) {
        return new StaminaProcessOnServerPayload(StaminaType.byOrdinal(buf.readVarInt()), buf.readVarInt());
    }

    public static void handleClient(StaminaProcessOnServerPayload payload, NetworkManager.PacketContext context) {
        throw new UnsupportedOperationException("This should have been designed not to be called");
    }

    public static void handleServer(StaminaProcessOnServerPayload payload, NetworkManager.PacketContext context) {
        context.queue(() -> {
            Player player = context.getPlayer();
            if (!ServerPayloadGuard.allowStaminaProcess(player)) {
                ParCool.LOGGER.debug("Dropped stamina process packet from {}: over the rate limit",
                        player.getName().getString());
                return;
            }
            // Upstream let the client choose the StaminaType, i.e. which server-side handler gets
            // instantiated and what it is asked to do, and sent an unbounded amount. The type now has
            // to match what the server offers and the amount is clamped.
            StaminaType forced = ServerPayloadGuard.forcedStaminaType();
            if (!ServerPayloadGuard.isStaminaTypeAllowed(payload.stamina(), forced)) {
                ParCool.LOGGER.debug("Rejected stamina process request for type {} (server offers {})",
                        payload.stamina(), forced);
                return;
            }
            int value = ServerPayloadGuard.clampStaminaProcessValue(payload.value());
            payload.stamina().newHandler(player).processOnServer(player, value);
        });
    }
}
