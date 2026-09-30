package com.alrex.parcool.platform;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

/**
 * Fabric network plumbing, built on the raw {@link NetworkManager}, exactly like the NeoForge side.
 *
 * <h2>Why there is no longer a Fabric special case</h2>
 * 1.21.1 had to use {@code NetworkChannel} here: Architectury 13's {@code architectury-fabric} did not
 * implement {@code NetworkAggregator.Adaptor#registerS2C}, so the id-based
 * {@code NetworkManager.registerReceiver} died with
 * {@code AbstractMethodError: NetworkManagerImpl$1 does not define … registerS2C}, and the
 * channel - which registers one id per payload class for both directions - was the only working path.
 * Architectury 19.0.1 removed {@code NetworkChannel} entirely and
 * {@code architectury-fabric 19.0.1}'s adaptor implements {@code registerS2C}, so the id-based API is
 * usable and both loaders now share one code path and one set of wire ids.
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
                    // Декод обязан происходить здесь, на сетевом потоке, пока буфер жив: registerReceiver
                    // отдаёт сырой буфер и освобождает его, как только лямбда вернулась. Внутри
                    // context.queue(...) буфер уже refCnt 0, и любой VarLong.read() роняет поток
                    // IllegalReferenceCountException. В очередь уходит только обработчик — ради
                    // потокобезопасности, а не ради декодирования.
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
