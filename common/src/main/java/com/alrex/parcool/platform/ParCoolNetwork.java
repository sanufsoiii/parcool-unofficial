package com.alrex.parcool.platform;

import dev.architectury.networking.NetworkManager;
import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * The network plumbing, because Architectury's cross-loader API is not usable for ParCool's message
 * set on either loader as it stands:
 *
 * <ul>
 *     <li><b>NeoForge</b>: a payload id can only be registered once, and Architectury's
 *     {@code NetworkChannel#register} registers a C2S <i>and</i> an S2C receiver under the same id
 *     ("Cannot register payload … as it is already registered"). The raw
 *     {@code NetworkManager.registerReceiver} is correct here, and a bidirectional message needs one
 *     id per direction (see {@code NetworkRegistries}).</li>
 *     <li><b>Fabric</b>: the raw {@code registerReceiver} path throws
 *     {@code AbstractMethodError: NetworkManagerImpl$1 does not define … registerS2C} — architectury-fabric
 *     13.0.11 does not implement the {@code NetworkAggregator.Adaptor} method that the common
 *     architectury artifact calls. {@code NetworkChannel} is the working path there, because on Fabric
 *     a payload type is registered once and serves both directions.</li>
 * </ul>
 *
 * <p>Note that the two loaders therefore put the same logical message under different wire ids
 * ({@code parcool:payload.*} on NeoForge, {@code parcool:main/<hash>} on Fabric). That is by design:
 * Architectury's {@code NetworkAggregator} collects packets on one side and re-sends them on the
 * other, which is what lets a Fabric client and a NeoForge server talk to each other.
 *
 * <p>Sending takes the payload <b>object</b> rather than a pre-encoded buffer, because each platform
 * needs its own addressing: NeoForge sends by explicit id, Fabric by channel lookup.
 */
public interface ParCoolNetwork {

    /**
     * Registers a message for one direction.
     *
     * @param payloadClass the concrete payload class
     * @param type         the payload's own {@code Type}, used for its identity
     * @param wireId       the id this message travels under for that direction (NeoForge)
     * @param clientbound  {@code true} for server -&gt; client, {@code false} for client -&gt; server
     * @param codec        the payload codec, used for inbound decoding
     * @param handler      invoked on the game thread with the decoded payload
     */
    <T extends CustomPacketPayload> void register(
            Class<T> payloadClass,
            CustomPacketPayload.Type<T> type,
            ResourceLocation wireId,
            StreamCodec<?, T> codec,
            boolean clientbound,
            BiConsumer<T, NetworkManager.PacketContext> handler);

    <T extends CustomPacketPayload> void sendToServer(
            T payload, ResourceLocation wireId, StreamCodec<?, T> codec);

    <T extends CustomPacketPayload> void sendToPlayer(
            ServerPlayer player, T payload, ResourceLocation wireId, StreamCodec<?, T> codec);

    <T extends CustomPacketPayload> void sendToPlayers(
            Iterable<ServerPlayer> players, T payload, ResourceLocation wireId, StreamCodec<?, T> codec);

    /** Shared helper: encodes a payload into a fresh buffer for the loaders that send by id. */
    static RegistryFriendlyByteBuf encode(StreamCodec<?, ?> codec, CustomPacketPayload payload) {
        io.netty.buffer.ByteBuf raw = io.netty.buffer.Unpooled.buffer();
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(raw, net.minecraft.core.RegistryAccess.EMPTY);
        @SuppressWarnings({"unchecked", "rawtypes"})
        StreamCodec typed = (StreamCodec) codec;
        typed.encode(buffer, payload);
        return buffer;
    }
}
