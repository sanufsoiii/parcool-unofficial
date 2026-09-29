package com.alrex.parcool.common.block.zipline;

import com.alrex.parcool.common.item.zipline.ZiplineRopeItem;
import com.alrex.parcool.common.zipline.ZiplineType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;

public class ZiplineInfo {

    /**
     * 1.21.10's block entity save hooks speak {@code ValueOutput}/{@code ValueInput} rather than
     * {@code CompoundTag}, so the connection entry is stored through a codec. The compound form is
     * still what {@link #save()} and {@link #load(Tag)} produce, keeping the on-disk layout identical.
     */
    public static final Codec<ZiplineInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("color", ZiplineRopeItem.DEFAULT_COLOR).forGetter(ZiplineInfo::getColor),
            ZiplineType.CODEC.optionalFieldOf("type", ZiplineType.LOOSE).forGetter(ZiplineInfo::getType)
    ).apply(instance, (color, type) -> new ZiplineInfo(type, color)));

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
            int color = cTag.getIntOr("color", ZiplineRopeItem.DEFAULT_COLOR);
            ZiplineType type = cTag.contains("type")
                    ? ZiplineType.values()[cTag.getByteOr("type", (byte) ZiplineType.LOOSE.ordinal()) % ZiplineType.values().length]
                    : ZiplineType.LOOSE;
            return new ZiplineInfo(type, color);
        }
        return new ZiplineInfo(ZiplineType.LOOSE, ZiplineRopeItem.DEFAULT_COLOR);
    }
}
