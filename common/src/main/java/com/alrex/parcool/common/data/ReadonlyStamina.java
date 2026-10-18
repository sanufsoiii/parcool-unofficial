package com.alrex.parcool.common.data;

import net.minecraft.network.FriendlyByteBuf;


public record ReadonlyStamina(boolean isExhausted, int value, int max) {
    public static ReadonlyStamina createDefault() {
        return new ReadonlyStamina(false, 0, 2000);
    }

    public ReadonlyStamina consumed(int value) {
        if (isExhausted()) return this;
        int newValue = this.value() - value;
        boolean exhausted = false;
        if (newValue < 0) {
            newValue = 0;
            exhausted = true;
        }
        return new ReadonlyStamina(exhausted, newValue, max());
    }

    public ReadonlyStamina recovered(int value) {
        int newValue = this.value() + value;
        boolean exhausted = isExhausted();
        // >=, not >: recovering up to exactly max() left the exhausted flag set, so a player on a full
        // bar was still treated as exhausted and could not start anything until the bar was pushed over
        // the limit by a single point.
        if (newValue >= max) {
            newValue = max;
            exhausted = false;
        }
        return new ReadonlyStamina(exhausted, newValue, max());
    }

    /**
     * Replaces the 1.21.1 {@code Codec}/{@code StreamCodec} pair: 1.20.1 has neither and the stamina
     * only ever travels inside a packet, so the NBT codec had no consumer here anyway.
     */
    public void writeTo(FriendlyByteBuf buffer) {
        buffer.writeBoolean(isExhausted);
        buffer.writeVarInt(value);
        buffer.writeInt(max);
    }

    public static ReadonlyStamina readFrom(FriendlyByteBuf buffer) {
        return new ReadonlyStamina(buffer.readBoolean(), buffer.readVarInt(), buffer.readInt());
    }
}
