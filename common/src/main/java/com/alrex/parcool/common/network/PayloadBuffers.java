package com.alrex.parcool.common.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * List framing for the two broadcast messages.
 *
 * <p>Replaces {@code ListStreamCodec}, which was a 1.21.1 {@code StreamCodec} implementation; on
 * 1.20.1 the same {@code int count} + elements layout is written straight to the buffer so the wire
 * contents stay identical to upstream.
 */
public final class PayloadBuffers {

    /**
     * Upper bound on the element count read off the wire. The list is replicated to every client, so a
     * single crafted packet with a huge count turns into a multi-gigabyte allocation on every server
     * that receives it.
     */
    public static final int MAX_ENTRIES = 512;

    private PayloadBuffers() {
    }

    public static void writeList(FriendlyByteBuf buffer, List<? extends ParCoolMessage> list) {
        buffer.writeInt(list.size());
        for (ParCoolMessage element : list) {
            element.write(buffer);
        }
    }

    public static <T> List<T> readList(FriendlyByteBuf buffer, Function<FriendlyByteBuf, T> reader) {
        int count = buffer.readInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new DecoderException("Illegal list length: " + count + " (max " + MAX_ENTRIES + ")");
        }
        var list = new ArrayList<T>(count);
        for (int i = 0; i < count; i++) {
            list.add(reader.apply(buffer));
        }
        return list;
    }
}
