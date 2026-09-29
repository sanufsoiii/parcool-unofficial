package com.alrex.parcool.platform;

import dev.architectury.networking.NetworkChannel;
import dev.architectury.networking.NetworkManager;
import java.util.function.BiConsumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import java.util.HashSet;
import java.util.Set;

/**
 * Fabric network plumbing, built on {@link NetworkChannel}.
 *
 * <p>The raw {@code NetworkManager.registerReceiver} path is unusable on architectury-fabric 13.0.11
 * (it throws {@code AbstractMethodError} for {@code registerS2C}), while {@code NetworkChannel} works
 * because on Fabric a payload type is registered once and serves both directions. The channel derives
 * its own per-class ids, so {@code common}'s direction-specific {@code wireId} is ignored here and
 * sending goes through the channel, which knows those ids.
 */
public class FabricParCoolNetwork implements ParCoolNetwork {

    private static final NetworkChannel CHANNEL =
            NetworkChannel.create(ResourceLocation.fromNamespaceAndPath("parcool", "main"));

    /**
     * {@code common} asks for a registration per direction, but a Fabric payload type is registered
     * once for both, so only the first request per payload class is honoured.
     */
    private final Set<Class<?>> registered = new HashSet<>();

    @SuppressWarnings("unchecked")
    @Override
    public <T extends CustomPacketPayload> void register(
            Class<T> payloadClass,
            CustomPacketPayload.Type<T> type,
            ResourceLocation wireId,
            StreamCodec<?, T> codec,
            boolean clientbound,
            BiConsumer<T, NetworkManager.PacketContext> handler) {
        if (!this.registered.add(payloadClass)) return;
        StreamCodec<Object, T> typed = (StreamCodec<Object, T>) codec;
        CHANNEL.register(
                payloadClass,
                (payload, buffer) -> typed.encode((FriendlyByteBuf) buffer, payload),
                buffer -> typed.decode((FriendlyByteBuf) buffer),
                (payload, context) -> context.get().queue(() -> handler.accept(payload, context.get()))
        );
    }

    @Override
    public <T extends CustomPacketPayload> void sendToServer(
            T payload, ResourceLocation wireId, StreamCodec<?, T> codec) {
        CHANNEL.sendToServer(payload);
    }

    @Override
    public <T extends CustomPacketPayload> void sendToPlayer(
            ServerPlayer player, T payload, ResourceLocation wireId, StreamCodec<?, T> codec) {
        CHANNEL.sendToPlayer(player, payload);
    }

    @Override
    public <T extends CustomPacketPayload> void sendToPlayers(
            Iterable<ServerPlayer> players, T payload, ResourceLocation wireId, StreamCodec<?, T> codec) {
        CHANNEL.sendToPlayers(players, payload);
    }
}
