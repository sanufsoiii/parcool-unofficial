package com.alrex.parcool.platform;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

/**
 * The network plumbing, built on Architectury's raw {@code NetworkManager}.
 *
 * <p>Both loaders need it, and both need the <b>id</b> based overloads:
 * <ul>
 *     <li><b>NeoForge</b>: a payload id can only be registered once, and Architectury's
 *     {@code NetworkChannel#register} registered a C2S <i>and</i> an S2C receiver under the same id
 *     ("Cannot register payload … as it is already registered"). A bidirectional message therefore
 *     needs one id per direction (see {@code NetworkRegistries}).</li>
 *     <li><b>Fabric</b>: {@code NetworkChannel} cannot be used on a dedicated server at all — it
 *     registers the S2C receiver only when {@code Platform.getEnvironment() == Env.CLIENT}, so
 *     {@code NetworkAggregator.S2C_TYPE}/{@code S2C_CODECS} stay empty there and every
 *     server-to-client send dies in {@code collectPackets}. The raw id path stays, and
 *     {@code FabricParCoolNetwork} splits the two sides the way Architectury's own javadoc on
 *     {@code NetworkManager#registerS2CPayloadType} prescribes: a receiver on the client,
 *     {@code registerS2CPayloadType} on the dedicated server. Both loaders therefore keep one code
 *     path and one set of ids ({@code parcool:payload.*} plus a {@code .c2s} variant).</li>
 * </ul>
 *
 * <p>Mixing the two overloads is what M4 warns about: the {@code ResourceLocation} one fills
 * {@code NetworkAggregator.C2S_TYPE} / {@code S2C_TYPE}, which the id-based send reads, while the
 * {@code CustomPacketPayload.Type} one leaves those maps empty - the first client -&gt; server packet
 * would then fail inside {@code collectPackets} and NeoForge would drop the connection with
 * "Network Protocol Error".
 *
 * <p>Sending takes the payload <b>object</b> rather than a pre-encoded buffer, because each platform
 * encodes it with its own addressing.
 */
public interface ParCoolNetwork {

    /**
     * Registers a message for one direction.
     *
     * @param payloadClass the concrete payload class
     * @param type         the payload's own {@code Type}, used for its identity
     * @param wireId       the id this message travels under for that direction
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
