package com.alrex.parcool.common.network.payload;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.data.client.LocalStamina;
import com.alrex.parcool.common.data.Parkourability;
import com.alrex.parcool.common.info.ServerLimitation;
import io.netty.buffer.ByteBuf;
import dev.architectury.networking.NetworkManager;

import javax.annotation.Nonnull;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;

public record LimitationPayload(ServerLimitation limitation) implements CustomPacketPayload {
    public static final Type<LimitationPayload> TYPE
            = new Type<>(ResourceLocation.fromNamespaceAndPath(ParCool.MOD_ID, "payload.limitation"));
    public static final StreamCodec<ByteBuf, LimitationPayload> CODEC = StreamCodec.composite(
            ServerLimitation.STREAM_CODEC,
            LimitationPayload::limitation,
            LimitationPayload::new
    );

    @Nonnull
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(LimitationPayload payload, NetworkManager.PacketContext context) {
        context.queue(() -> {
            var player = context.getPlayer();
            Parkourability parkourability = Parkourability.get(player);
            parkourability.getActionInfo().setServerLimitation(payload.limitation());
            // isLocalPlayer() + cast rather than a pattern match on LocalPlayer: this payload class
            // is linked on a dedicated server too (the server registers the payload type), and the
            // verifier resolves the target of an `instanceof`, which loads the client-only LocalPlayer
            // and dies with "Cannot load class net.minecraft.client.player.LocalPlayer in environment
            // type SERVER". The test itself is unchanged: Player#isLocalPlayer() is true only on
            // LocalPlayer. The cast is a checkcast, resolved lazily, and handleClient runs on a client.
            if (player.isLocalPlayer()) {
                LocalPlayer localPlayer = (LocalPlayer) player;
                parkourability.getActionInfo().updateStaminaType(LocalStamina.get(localPlayer), localPlayer);
            }
        });
    }

    public static void handleServer(LimitationPayload payload, NetworkManager.PacketContext context) {
        throw new UnsupportedOperationException("This should have been designed not to be called");
    }
}
