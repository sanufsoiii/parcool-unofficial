package com.alrex.parcool.common.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * A ParCool message that can serialise itself into a buffer.
 *
 * <p>The 1.21.1 tree got this behaviour from {@code CustomPacketPayload}/{@code StreamCodec}, neither
 * of which exists on 1.20.1. Keeping the writer on the message (rather than passing a
 * {@code BiConsumer} alongside it) also avoids a javac inference problem: a method reference to a
 * record's {@code write} cannot be checked against a {@code BiConsumer<FriendlyByteBuf, T>} whose
 * {@code T} is still an unresolved type variable of the surrounding generic method.
 */
public interface ParCoolMessage {

    void write(FriendlyByteBuf buffer);
}
