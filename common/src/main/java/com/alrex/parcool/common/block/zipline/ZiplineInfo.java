package com.alrex.parcool.common.block.zipline;

import com.alrex.parcool.common.item.zipline.ZiplineRopeItem;
import com.alrex.parcool.common.zipline.ZiplineType;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public class ZiplineInfo {
    public ZiplineInfo(ZiplineType type, int color) {
        this.color = color;
        this.type = type;
    }

    private final ZiplineType type;

    private final int color;

    public int getColor() {
        return color;
    }

    public ZiplineType getType() {
        return type;
    }

    public Tag save() {
        var tag = new CompoundTag();
        tag.putInt("color", color);
        tag.putByte("type", (byte) getType().ordinal());
        return tag;
    }

    public static ZiplineInfo load(@Nullable Tag tag) {
        if (tag instanceof CompoundTag cTag) {
            // 1.21.5: the typed NBT getters return Optional<T>; getXOr(key, default) is the direct
            // replacement for the "contains(...) ? getX(...) : fallback" dance.
            int color = cTag.getIntOr("color", ZiplineRopeItem.DEFAULT_COLOR);
            ZiplineType type = cTag.contains("type")
                    ? ZiplineType.values()[cTag.getByteOr("type", (byte) ZiplineType.LOOSE.ordinal()) % ZiplineType.values().length]
                    : ZiplineType.LOOSE;
            return new ZiplineInfo(type, color);
        }
        return new ZiplineInfo(ZiplineType.LOOSE, ZiplineRopeItem.DEFAULT_COLOR);
    }
}
