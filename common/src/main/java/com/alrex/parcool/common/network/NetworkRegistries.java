package com.alrex.parcool.common.network;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.network.payload.ActionStateBroadcastPayload;
import com.alrex.parcool.common.network.payload.ActionStatePayload;
import com.alrex.parcool.common.network.payload.ClientInformationPayload;
import com.alrex.parcool.common.network.payload.LimitationPayload;
import com.alrex.parcool.common.network.payload.StaminaBroadcastPayload;
import com.alrex.parcool.common.network.payload.StaminaPayload;
import com.alrex.parcool.common.network.payload.StaminaProcessOnServerPayload;
import com.alrex.parcool.common.network.payload.StartBreakfallEventPayload;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Loader-agnostic replacement for the NeoForge {@code PayloadRegistrar} /
 * {@code PacketDistributor} pair, built on Architectury's {@link NetworkManager}.
 *
 * <h2>Why there is no platform seam here any more</h2>
 * On 1.21.1 the two loaders needed different plumbing (NeoForge payloads are registered by id and
 * refuse a second registration, while the Architectury Fabric backend could not register a receiver
 * at all), so the 1.21.1 tree routed every send through a {@code ParCoolNetwork} interface implemented
 * per loader. Architectury 9.x - the version this Minecraft version uses - exposes the channel-name
 * API, which is exactly what 1.20.1 Fabric and Forge both want: {@code registerReceiver(id)} per
 * direction and {@code sendToServer/sendToPlayer/sendToPlayers(id, buffer)} for the sends. Both 9.2.14
 * backends keep one receiver per (id, direction) in a map, so a bidirectional message under one id
 * works on both loaders.
 *
 * <h2>Server-&gt;client receivers are registered in the client environment only</h2>
 * Architectury's Fabric backend marks its S2C registrar {@code @Environment(EnvType.CLIENT)}: the
 * receiver goes into Fabric's <i>client</i> networking, so Fabric strips that member from the class
 * when the game runs as a dedicated server - and the shared
 * {@code NetworkManager#registerReceiver(Side.S2C, ...)} then dies with
 * {@code NoSuchMethodError: ...registerS2CReceiver(...)} while the server is still starting. That is
 * exactly how this port's first dedicated-server boot failed. {@link #registerPayloads()} therefore
 * registers the C2S receivers unconditionally and the S2C receivers only when
 * {@code Platform#getEnvironment()} is {@code Env.CLIENT}, which is also the only place a client-bound
 * receiver can ever fire. Sending is unaffected: the ids travel inside the payload and the Forge
 * backend keeps the S2C receivers in a plain map, so a skipped registration on a dedicated server
 * costs nothing there either.
 *
 * <h2>Compatibility</h2>
 * The payload ids keep the upstream {@code parcool:payload.*} names for the server-&gt;client
 * direction; the client-&gt;server direction of the three bidirectional messages uses the same name
 * with a {@code .c2s} suffix, as in the 1.21.1 tree. Compatibility is enforced by the ids: a
 * mismatched jar is rejected with an unknown-payload disconnect.
 */
public final class NetworkRegistries {

    /**
     * Architectury's channel has no "every player" primitive, so the broadcasting entry point needs
     * the player list. NeoForge's {@code PacketDistributor.sendToAllPlayers} obtained it implicitly;
     * the server is captured from the Architectury lifecycle events instead.
     */
    @Nullable
    private static MinecraftServer server;

    private static boolean payloadsRegistered;

    private NetworkRegistries() {
    }

    /** Called from the Architectury server lifecycle events. */
    public static void setServer(@Nullable MinecraftServer server) {
        NetworkRegistries.server = server;
    }

    // ------------------------------------------------------------------
    // ids
    // ------------------------------------------------------------------

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ParCool.MOD_ID, path);
    }

    private static ResourceLocation serverbound(String path) {
        return new ResourceLocation(ParCool.MOD_ID, path + ".c2s");
    }

    /** Client -&gt; server message type. */
    public static <T extends ParCoolMessage> MessageType<T> c2s(String path, Function<FriendlyByteBuf, T> reader) {
        return new MessageType<>(serverbound(path), reader);
    }

    /** Server -&gt; client message type. */
    public static <T extends ParCoolMessage> MessageType<T> s2c(String path, Function<FriendlyByteBuf, T> reader) {
        return new MessageType<>(id(path), reader);
    }

    // ------------------------------------------------------------------
    // registration
    // ------------------------------------------------------------------

    private static <T extends ParCoolMessage> void registerC2S(MessageType<T> type, BiConsumer<T, NetworkManager.PacketContext> handler) {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, type.id(),
                (buffer, context) -> handler.accept(type.read(buffer), context));
    }

    private static <T extends ParCoolMessage> void registerS2C(MessageType<T> type, BiConsumer<T, NetworkManager.PacketContext> handler) {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, type.id(),
                (buffer, context) -> handler.accept(type.read(buffer), context));
    }

    public static void registerPayloads() {
        if (payloadsRegistered) return;
        payloadsRegistered = true;

        // --- client -> server only ---
        registerC2S(StaminaProcessOnServerPayload.C2S, StaminaProcessOnServerPayload::handleServer);

        // --- both directions; one id per direction ---
        registerC2S(ActionStatePayload.C2S, ActionStatePayload::handleServer);
        registerC2S(ClientInformationPayload.C2S, ClientInformationPayload::handleServer);
        registerC2S(StaminaPayload.C2S, StaminaPayload::handleServer);

        // Server -> client receivers only exist where those packets arrive; a dedicated server must
        // not install them (see the class documentation for the NoSuchMethodError this caused).
        if (Platform.getEnvironment() != Env.CLIENT) {
            return;
        }

        // --- server -> client only ---
        registerS2C(StartBreakfallEventPayload.S2C, StartBreakfallEventPayload::handleClient);
        registerS2C(LimitationPayload.S2C, LimitationPayload::handleClient);
        registerS2C(StaminaBroadcastPayload.S2C, StaminaBroadcastPayload::handleClient);
        registerS2C(ActionStateBroadcastPayload.S2C, ActionStateBroadcastPayload::handleClient);

        registerS2C(ActionStatePayload.S2C, ActionStatePayload::handleClient);
        registerS2C(ClientInformationPayload.S2C, ClientInformationPayload::handleClient);
        registerS2C(StaminaPayload.S2C, StaminaPayload::handleClient);
    }

    // ------------------------------------------------------------------
    // sending
    // ------------------------------------------------------------------

    /**
     * The 1.20.1 id of each message has to be declared on both sides by the login handshake
     * (Fabric: the channel registration packet; Forge: Architectury's own {@code SYNC_IDS} packet),
     * and {@code NetworkManager.sendToServer} throws when the client is not in a world at all. The
     * guard covers both. A message dropped here is never a lost update: the only client-&gt;server
     * messages are the client's own settings, which {@code Parkourability.trySyncLimitation} re-sends
     * until the server answers, so a race with the Forge handshake only costs one retry.
     */
    public static <T extends ParCoolMessage> void sendToServer(MessageType<T> type, T payload) {
        if (!NetworkManager.canServerReceive(type.id())) return;
        NetworkManager.sendToServer(type.id(), encode(type, payload));
    }

    public static <T extends ParCoolMessage> void sendToPlayer(ServerPlayer player, MessageType<T> type, T payload) {
        NetworkManager.sendToPlayer(player, type.id(), encode(type, payload));
    }

    public static <T extends ParCoolMessage> void sendToPlayers(Iterable<ServerPlayer> players, MessageType<T> type, T payload) {
        NetworkManager.sendToPlayers(players, type.id(), encode(type, payload));
    }

    /** Broadcast, the replacement for {@code PacketDistributor.sendToAllPlayers}. */
    public static <T extends ParCoolMessage> void sendToAllPlayers(MessageType<T> type, T payload) {
        MinecraftServer current = server;
        if (current == null) return;
        sendToPlayers(current.getPlayerList().getPlayers(), type, payload);
    }

    /** Serialises a message into a fresh buffer, using the message type's own writer. */
    private static <T extends ParCoolMessage> FriendlyByteBuf encode(MessageType<T> type, T payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        type.write(buffer, payload);
        return buffer;
    }
}
