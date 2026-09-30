package com.alrex.parcool.platform;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

/**
 * Fabric network plumbing, built on the id based {@link NetworkManager}, exactly like the NeoForge side.
 *
 * <h2>Why the S2C side is registered differently on a dedicated server</h2>
 * The obvious implementation - {@code NetworkManager.registerReceiver(Side.S2C, id, receiver)} on both
 * sides - crashes a dedicated server at mod init. Reproduced on this tree against
 * {@code architectury-fabric 17.0.8}:
 *
 * <pre>
 * Caused by: java.lang.AbstractMethodError: Receiver class
 *   dev.architectury.networking.fabric.NetworkManagerImpl$1 does not define or inherit an
 *   implementation of the resolved method 'abstract void registerS2C(...)' of interface
 *   dev.architectury.impl.NetworkAggregator$Adaptor
 *   at dev.architectury.impl.NetworkAggregator.registerS2CReceiver(NetworkAggregator.java:119)
 *   at dev.architectury.impl.NetworkAggregator.registerReceiver(NetworkAggregator.java:76)
 *   at dev.architectury.networking.NetworkManager.registerReceiver(NetworkManager.java:93)
 *   at com.alrex.parcool.platform.FabricParCoolNetwork.register(FabricParCoolNetwork.java:45)
 *   at com.alrex.parcool.common.network.NetworkRegistries.registerS2C(NetworkRegistries.java:94)
 *   at com.alrex.parcool.ParCool.init(ParCool.java:78)
 * </pre>
 *
 * <p>{@code javap -v} on {@code NetworkManagerImpl$1} inside {@code architectury-fabric-17.0.8.jar}
 * shows that {@code registerS2C} <b>is</b> declared, with exactly the descriptor the interface
 * declares - so this is not a version skew and not a stale jar. The same {@code javap} also shows
 * why the method is missing at run time: it is the only method of {@code NetworkAggregator.Adaptor}
 * that carries
 *
 * <pre>
 * RuntimeInvisibleAnnotations:
 *   net.fabricmc.api.Environment(value = EnvType.CLIENT)
 * </pre>
 *
 * <p>and the 17.x Architectury source says so explicitly:
 *
 * <pre>
 * public &lt;T extends CustomPacketPayload&gt; void registerS2C(...) {
 *     PayloadTypeRegistry.playS2C().register(type, codec);
 *     ClientPlayNetworking.registerGlobalReceiver(type, new ClientPlayPayloadHandler&lt;&gt;(receiver));
 * }
 * </pre>
 *
 * <p>Fabric Loader's {@code EnvironmentStripper} deletes {@code @Environment(CLIENT)} members when the
 * game runs on a dedicated server, so {@code NetworkManagerImpl$1} arrives at the aggregator without
 * {@code registerS2C} and the first {@code registerS2CReceiver} call throws. <b>That is the whole
 * asymmetry, and it is why the bug was invisible for months:</b> in single player the integrated
 * server lives inside the <i>client</i> JVM, where the stripping pass never runs, and the same happens
 * on a separate client - so the member survives, the call succeeds, and the port looked healthy. Only
 * a dedicated server strips it. The compiler cannot see this either, because the annotated member
 * does satisfy the interface at compile time. <b>Always boot a dedicated server when touching
 * registration on Fabric.</b>
 *
 * <p>{@code registerC2S}, {@code registerS2CType} and both {@code toXxxPacket} methods carry no such
 * annotation and are present on both sides.
 *
 * <p>It is not a workaround to register the S2C receiver lazily, or to register it only once the first
 * player joins: a server never receives a server-to-client packet, so the S2C <i>receiver</i> is dead
 * weight there and its only required job is to make the payload type sendable. Architectury ships
 * exactly that API, and documents the split in its own javadoc on
 * {@code NetworkManager#registerS2CPayloadType}:
 *
 * <blockquote>For S2C types, {@code registerReceiver} should be called on the client side, while
 * {@code registerS2CPayloadType} should be called on the server side.</blockquote>
 *
 * <p>{@code registerS2CPayloadType(id)} fills {@code NetworkAggregator.S2C_TYPE},
 * {@code S2C_CODECS} and {@code S2C_TRANSFORMERS} and reaches the loader through
 * {@code Adaptor.registerS2CType}, which on Fabric is
 * {@code PayloadTypeRegistry.playS2C().register(type, BufCustomPacketPayload.streamCodec(type))} - the
 * unstripped sibling of {@code registerS2C}, minus the client-only
 * {@code ClientPlayNetworking.registerGlobalReceiver} call.
 *
 * <h2>Why the wire format does not change</h2>
 * Both registration shapes hand the loader the same aggregator payload:
 * {@code registerReceiver} stores {@code S2C_TYPE[id] = new Type(id)} and registers
 * {@code BufCustomPacketPayload.streamCodec(type)}, and so does {@code registerS2CPayloadType}
 * ({@code javap -c} on {@code NetworkAggregator} confirms both fill {@code S2C_TYPE},
 * {@code S2C_CODECS} and {@code S2C_TRANSFORMERS} with the same values; only {@code S2C_RECEIVER},
 * which a server never reads, is left empty). The real payload codec only ever runs locally - on send
 * inside {@code NetworkAggregator.collectPackets}, on receive inside the receiver lambda. A packet
 * therefore travels as {@code BufCustomPacketPayload(id, <the bytes ParCool encoded>)} either way, so
 * a server-to-client packet written by a dedicated Fabric server is byte-for-byte what the NeoForge
 * side writes and what the client already decodes. The client path is untouched by this change.
 *
 * <h2>Why not {@code NetworkChannel}</h2>
 * {@code NetworkChannel} is the usual Architectury answer, and it does dodge the crash, but it is
 * <b>wrong for a dedicated server</b>. {@code javap -c} on {@code NetworkChannel#register} shows it
 * wraps the S2C registration in {@code if (Platform.getEnvironment() == Env.CLIENT)}, so on a
 * dedicated server only the C2S half of the aggregator is ever populated;
 * {@code NetworkChannel#sendToPlayer} then calls
 * {@code NetworkManager.toPacket(s2c(), channelId, buf)}, whose
 * {@code NetworkAggregator.collectPackets(sink, S2C, id, buf)} does
 * {@code new BufCustomPacketPayload(S2C_TYPE.get(id), bytes)} with {@code S2C_TYPE.get(id) == null},
 * and the following {@code collectPackets(sink, S2C, payload, access)} dereferences
 * {@code payload.type().id()} and {@code S2C_CODECS.get(...)}. So that port's server starts, and then
 * every single server-to-client packet (limitation snapshot, stamina broadcast, action state, breakfall
 * event) throws {@code NullPointerException}. A green {@code Done} would have hidden it. The id based
 * path with a side split keeps one set of wire ids on both loaders and keeps sending working.
 *
 * <p>This is also what keeps M4 away: registration and sending both address a message by its explicit
 * id, so {@code NetworkAggregator.C2S_TYPE}/{@code S2C_TYPE} - which the id-based send reads - are
 * always populated.
 */
public class FabricParCoolNetwork implements ParCoolNetwork {

    @Override
    public <T extends CustomPacketPayload> void register(
            Class<T> payloadClass,
            CustomPacketPayload.Type<T> type,
            ResourceLocation wireId,
            StreamCodec<?, T> codec,
            boolean clientbound,
            BiConsumer<T, NetworkManager.PacketContext> handler) {
        if (clientbound && Platform.getEnvironment() == Env.SERVER) {
            // A dedicated server only ever *sends* server-to-client traffic, so it needs the payload
            // type to be sendable, not a receiver. registerS2CPayloadType does that through the
            // unstripped Adaptor.registerS2CType; registerReceiver(S2C, ...) would call the
            // @Environment(CLIENT) Adaptor.registerS2C and die with AbstractMethodError. See the class
            // javadoc. The wire format is the same either way, so the client is unaffected.
            NetworkManager.registerS2CPayloadType(wireId);
            return;
        }
        // The id overload hands the raw buffer to the receiver and does the C2S_TYPE/S2C_TYPE
        // bookkeeping that the id-based send below depends on. Architectury's own id overload erases
        // the payload type to BufCustomPacketPayload, so the message is decoded here instead - the
        // payload codecs declare ByteBuf as their supertype, so the encoded bytes are unchanged.
        @SuppressWarnings({"unchecked", "rawtypes"})
        StreamCodec<? super RegistryFriendlyByteBuf, T> erased = (StreamCodec) codec;
        NetworkManager.registerReceiver(
                clientbound ? NetworkManager.Side.S2C : NetworkManager.Side.C2S,
                wireId,
                (buf, context) -> {
                    // The decode must happen HERE, on the network thread, while the buffer is still
                    // alive: registerReceiver hands the receiver the raw buffer and releases it as
                    // soon as this lambda returns - Architectury's NetworkAggregator#registerReceiver
                    // builds the RegistryFriendlyByteBuf, calls the receiver, then calls
                    // buf.release(). Inside context.queue(...) the buffer is already refCnt 0, so the
                    // first VarLong.read() it performs throws IllegalReferenceCountException and
                    // kills the server task. Only the handler is queued, for its thread safety,
                    // never for the decoding.
                    T payload = erased.decode((RegistryFriendlyByteBuf) buf);
                    context.queue(() -> handler.accept(payload, context));
                }
        );
    }

    @Override
    public <T extends CustomPacketPayload> void sendToServer(
            T payload, ResourceLocation wireId, StreamCodec<?, T> codec) {
        NetworkManager.sendToServer(wireId, ParCoolNetwork.encode(codec, payload));
    }

    @Override
    public <T extends CustomPacketPayload> void sendToPlayer(
            ServerPlayer player, T payload, ResourceLocation wireId, StreamCodec<?, T> codec) {
        NetworkManager.sendToPlayer(player, wireId, ParCoolNetwork.encode(codec, payload));
    }

    @Override
    public <T extends CustomPacketPayload> void sendToPlayers(
            Iterable<ServerPlayer> players, T payload, ResourceLocation wireId, StreamCodec<?, T> codec) {
        NetworkManager.sendToPlayers(players, wireId, ParCoolNetwork.encode(codec, payload));
    }
}
