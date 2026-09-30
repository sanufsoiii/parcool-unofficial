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
 *     <li><b>Fabric</b>: the same reasoning applies, plus a dedicated-server-only asymmetry. Fabric
 *     Loader strips {@code @Environment(EnvType.CLIENT)} members, and architectury-fabric marks
 *     {@code Adaptor#registerS2C} with it, so on a dedicated server the raw
 *     {@code registerReceiver(S2C, …)} throws
 *     {@code AbstractMethodError: … does not define … registerS2C} while the C2S half registers fine.
 *     A client and a single player world both keep the method and never show it.</li>
 *     <li><b>{@code NetworkChannel} is not usable on a dedicated Fabric server at all</b>, and the
 *     failure is silent: it wraps its S2C registration in
 *     {@code if (Platform.getEnvironment() == Env.CLIENT)}, so {@code Registering S2C receiver} is
 *     never logged on the server, {@code NetworkAggregator.S2C_TYPE} stays empty, and
 *     {@code NetworkChannel#sendToPlayer} then throws {@code NullPointerException} for every
 *     server-to-client packet. The server still reaches {@code Done (…)}. See
 *     {@code FabricParCoolNetwork} for the bytecode.</li>
 * </ul>
 *
 * <p>So both loaders address a message by its explicit {@code parcool:payload.*} id (plus the
 * {@code .c2s} variant for the client-to-server direction of a bidirectional message), and
 * registration and sending both go through the id based {@code NetworkManager}. That keeps
 * {@code NetworkAggregator.C2S_TYPE}/{@code S2C_TYPE} — which the id-based send reads — populated on
 * both sides of a connection.
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
