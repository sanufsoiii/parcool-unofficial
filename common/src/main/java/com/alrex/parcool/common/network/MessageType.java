package com.alrex.parcool.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * One direction of one ParCool message: the channel id it travels under and the reader that turns a
 * received buffer back into the message object. Writing goes through {@link ParCoolMessage}.
 *
 * <p>Replaces the 1.21.1 {@code CustomPacketPayload.Type} + {@code StreamCodec} pair. Minecraft 1.20.1
 * has neither {@code CustomPacketPayload} nor {@code StreamCodec}: Fabric networking is channel-name
 * based and Forge uses a named {@code SimpleChannel}, so Architectury's lowest common denominator is
 * exactly "a {@link ResourceLocation} and a buffer". Architectury's {@code NetworkManager} keeps the
 * two sides apart by direction, which is why every bidirectional message declares two of these: one
 * id for each direction, matching the 1.21.1 layout where the same split was needed because NeoForge
 * refuses to register a payload id twice.
 */
public final class MessageType<T extends ParCoolMessage> {

    private final ResourceLocation id;
    private final Function<FriendlyByteBuf, T> reader;

    public MessageType(ResourceLocation id, Function<FriendlyByteBuf, T> reader) {
        this.id = id;
        this.reader = reader;
    }

    public ResourceLocation id() {
        return id;
    }

    /** Decodes a received buffer into the message. Mirrors the message's own {@code write}. */
    public T read(FriendlyByteBuf buffer) {
        return reader.apply(buffer);
    }

    /** Encodes the message into a buffer. */
    public void write(FriendlyByteBuf buffer, T payload) {
        payload.write(buffer);
    }
}
