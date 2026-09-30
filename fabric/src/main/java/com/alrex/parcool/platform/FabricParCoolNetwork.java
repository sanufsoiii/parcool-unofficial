package com.alrex.parcool.platform;

import com.alrex.parcool.ParCool;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import java.util.function.BiConsumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric network plumbing, built on the id based {@link NetworkManager}, exactly like the NeoForge side.
 *
 * <h2>Why the S2C side is registered differently on a dedicated server</h2>
 * {@code NetworkChannel} was the previous choice here, and it works on a client and in single player. It
 * is wrong on a dedicated server, and the reason is in Architectury's own bytecode. {@code javap -c} on
 * {@code dev/architectury/networking/NetworkChannel#register} in {@code architectury-fabric-14.0.4.jar}:
 *
 * <pre>
 * 77: invokestatic  NetworkManager.c2s()
 * 87: invokevirtual NetworkManager.registerReceiver(Side, ResourceLocation, NetworkReceiver)
 * 90: invokestatic  Platform.getEnvironment()
 * 93: getstatic     Env.CLIENT
 * 96: if_acmpne     112          // &lt;- the S2C registration is skipped on a dedicated server
 * 99: invokestatic  NetworkManager.s2c()
 * 109: invokevirtual NetworkManager.registerReceiver(Side, ResourceLocation, NetworkReceiver)
 * </pre>
 *
 * <p>So on a dedicated server the channel populates only the C2S half of {@code NetworkAggregator}, and
 * {@code Registering S2C receiver with id …} is never logged. That is survivable at boot but fatal for
 * sending: {@code NetworkChannel#sendToPlayer} calls
 * {@code NetworkManager.toPacket(S2C, channelId, buf)}, and
 * {@code NetworkAggregator.collectPackets(sink, S2C, id, buf)} builds
 * {@code new BufCustomPacketPayload(S2C_TYPE.get(id), bytes)} with {@code S2C_TYPE.get(id) == null};
 * the following {@code collectPackets(sink, S2C, payload, access)} then dereferences
 * {@code payload.type().id()} and {@code S2C_CODECS.get(...)}. Every server-to-client packet -
 * limitation snapshot, stamina broadcast, action state, breakfall event - therefore throws
 * {@code NullPointerException}, the client never gets a snapshot, {@code ParCoolIsActive} stays
 * {@code false} and no action can start. A green {@code Done (…)} hides all of it, because the failure
 * only happens once a player is on the other end.
 *
 * <h2>Why {@code registerReceiver(S2C, …)} is not simply called on both sides</h2>
 * Because architectury-fabric marks the loader adaptor method {@code Adaptor#registerS2C} with
 * {@code @Environment(EnvType.CLIENT)} (verified with {@code javap -v} on
 * {@code NetworkManagerImpl$1}: {@code RuntimeInvisibleAnnotations} =
 * {@code net.fabricmc.api.Environment(value = EnvType.CLIENT)}, and it is the only member of
 * {@code NetworkAggregator.Adaptor} that carries it). Fabric Loader's environment stripper deletes such
 * members when the game runs on a dedicated server, so the aggregator's
 * {@code ADAPTOR.get().registerS2C(…)} dies with
 * {@code AbstractMethodError: … does not define or inherit an implementation of … registerS2C}. On a
 * client the method survives, which is exactly why a client and a single player world never show this.
 *
 * <p>A dedicated server never receives a server-to-client packet, so the S2C <i>receiver</i> is dead
 * weight there; all the server needs is for the payload type to be sendable. Architectury ships that
 * half separately, and documents the split on {@code NetworkManager#registerS2CPayloadType}:
 *
 * <blockquote>For S2C types, {@code registerReceiver} should be called on the client side, while
 * {@code registerS2CPayloadType} should be called on the server side.</blockquote>
 *
 * <p>{@code registerS2CPayloadType(id)} fills {@code S2C_TYPE}, {@code S2C_CODECS} and
 * {@code S2C_TRANSFORMERS} and reaches the loader through the unstripped
 * {@code Adaptor#registerS2CType}, which on Fabric is
 * {@code PayloadTypeRegistry.playS2C().register(type, BufCustomPacketPayload.streamCodec(type))} - the
 * unstripped sibling of {@code registerS2C} minus the client-only
 * {@code ClientPlayNetworking.registerGlobalReceiver} call. {@code registerC2S}, which needs no guard,
 * runs on both sides exactly as before, so the client-to-server direction is untouched.
 *
 * <h2>Why the wire format does not change</h2>
 * Both registration shapes hand the loader the same aggregator payload: {@code registerReceiver} stores
 * {@code S2C_TYPE[id] = new Type(id)} and registers {@code BufCustomPacketPayload.streamCodec(type)},
 * and so does {@code registerS2CPayloadType}. The real payload codec only ever runs locally - on send
 * inside {@code NetworkAggregator.collectPackets}, on receive inside the receiver lambda. A packet
 * therefore travels as {@code BufCustomPacketPayload(id, <the bytes ParCool encoded>)} either way.
 * What does change against the old {@code NetworkChannel} shape is the id itself: the channel derived
 * {@code parcool:main/<uuid-hash-of-the-class-name>}, while the id-based path uses the
 * {@code parcool:payload.*} names {@code NetworkRegistries} hands down. That is the point - it is the
 * same id NeoForge uses, and both ends of a connection run this jar, so client and server still agree.
 *
 * <h2>Why there is no {@code Set<Class<?>> registered} guard</h2>
 * That guard belonged to the {@code NetworkChannel} shape: the channel keys its private {@code encoders}
 * map by payload class, and for a message that travels in both directions {@code NetworkRegistries}
 * asks for one registration per direction - so the second request was silently swallowed by
 * {@code Map.put} and the guard existed to stop that from looking like a success. Here every message has
 * its own wire id per direction ({@code parcool:payload.*} plus the {@code .c2s} variant), so the
 * aggregator maps are keyed by distinct ids and there is nothing to de-duplicate.
 * {@code NetworkRegistries.registerPayloads} already guards re-entry with {@code payloadsRegistered},
 * and Fabric's own {@code PayloadTypeRegistry.register} throws on a duplicate type id, so a double
 * registration cannot pass silently.
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
            // Architectury logs nothing for this half - its "Registering S2C receiver with id" line
            // lives in registerS2C, the method the environment stripper removes - so a dedicated
            // server otherwise says nothing at all about its clientbound payloads. That silence is
            // what hid this defect, so the line is spelled out here instead. Deliberately phrased
            // without Architectury's "Registering … receiver with id" wording so the two cannot be
            // confused when counting registrations in a log.
            ParCool.LOGGER.info(
                    "[parcool] network: clientbound payload {} on a dedicated server: type only, no inbound handler",
                    wireId);
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
                    // The decode has to happen here, on the network thread, while the buffer is still
                    // alive: registerReceiver hands the lambda a raw buffer and releases it as soon as
                    // the lambda returns. Inside context.queue(...) the buffer is already refCnt 0, and
                    // any VarLong.read() then kills the thread with IllegalReferenceCountException. Only
                    // the handler belongs in the queue, for thread safety, not the decoding.
                    T payload = erased.decode((RegistryFriendlyByteBuf) buf);
                    context.queue(() -> handler.accept(payload, context));
                }
        );
        ParCool.LOGGER.info(
                "[parcool] network: {} payload {}: receiver and sendable type both in place",
                clientbound ? "clientbound" : "serverbound", wireId);
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
