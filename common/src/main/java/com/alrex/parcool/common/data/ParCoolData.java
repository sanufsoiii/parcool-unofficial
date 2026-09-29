package com.alrex.parcool.common.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Static facade over the per-entity {@link IParCoolDataHolder} store.
 * <p>
 * The call shape is a deliberate 1:1 replacement of the NeoForge
 * {@code entity.getData(Attachments.X)} / {@code entity.setData(Attachments.X, v)} pair so the ~22
 * call sites across the mod port mechanically.
 */
public final class ParCoolData {

    /** Sub-compound of the player NBT that holds every persistent {@link DataKey}. */
    private static final String ROOT_TAG = "ParCool";

    private ParCoolData() {
    }

    /**
     * @return the stored value, or a freshly created default when the slot was never written.
     * Never {@code null} for a non-null entity.
     */
    public static <T> T get(Entity entity, DataKey<T> key) {
        Map<DataKey<?>, Object> map = IParCoolDataHolder.of(entity);
        if (map == null) return key.createDefault();
        Object value = map.get(key);
        if (value == null) {
            value = key.createDefault();
            map.put(key, value);
        }
        return cast(key, value);
    }

    public static <T> void set(Entity entity, DataKey<T> key, T value) {
        Map<DataKey<?>, Object> map = IParCoolDataHolder.of(entity);
        if (map == null) return;
        if (value == null) {
            map.remove(key);
        } else {
            map.put(key, value);
        }
    }

    public static boolean has(Entity entity, DataKey<?> key) {
        Map<DataKey<?>, Object> map = IParCoolDataHolder.of(entity);
        return map != null && map.containsKey(key);
    }

    public static <T> void remove(Entity entity, DataKey<T> key) {
        Map<DataKey<?>, Object> map = IParCoolDataHolder.of(entity);
        if (map != null) map.remove(key);
    }



    // ------------------------------------------------------------------
    // persistence
    // ------------------------------------------------------------------

    /**
     * Writes every {@link DataKey#isPersistent() persistent} slot into the player save tag.
     *
     * <p>1.21.7's {@code Player#addAdditionalSaveData} is {@code ValueOutput} based, so the whole
     * ParCool sub-tree is encoded into one NBT compound and handed over as a single value. The
     * on-disk key ({@code ParCool} -> {@code parcool:...}) is unchanged from 1.21.1.
     */
    private static void applyFromTag(Player player, CompoundTag tag) {
        Map<DataKey<?>, Object> map = IParCoolDataHolder.of(player);
        if (map == null) return;
        CompoundTag root = tag.getCompoundOrEmpty(ROOT_TAG);
        for (String id : root.keySet()) {
            DataKey<?> key = ParCoolDataKeys.byId(id);
            if (key == null || !key.isPersistent()) continue;
            map.put(key, castSerializer(key).read(root.getCompoundOrEmpty(id)));
        }
    }

    public static void saveToOutput(Player player, ValueOutput output) {
        Map<DataKey<?>, Object> map = IParCoolDataHolder.of(player);
        if (map == null) return;
        CompoundTag root = new CompoundTag();
        for (Map.Entry<DataKey<?>, Object> e : map.entrySet()) {
            DataKey<?> key = e.getKey();
            if (!key.isPersistent() || e.getValue() == null) continue;
            DataKey.NbtSerializer<Object> serializer = castSerializer(key);
            CompoundTag child = new CompoundTag();
            serializer.write(e.getValue(), child);
            root.put(key.id(), child);
        }
        if (!root.isEmpty()) output.store(ROOT_TAG, CompoundTag.CODEC, root);
    }

    /** Restores every persistent slot from the player save tag. */
    public static void loadFromInput(Player player, ValueInput input) {
        Map<DataKey<?>, Object> map = IParCoolDataHolder.of(player);
        if (map == null) return;
        input.read(ROOT_TAG, CompoundTag.CODEC).ifPresent(tag -> applyFromTag(player, tag));
    }



    // ------------------------------------------------------------------
    // unchecked plumbing
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static <T> T cast(DataKey<T> key, Object value) {
        return (T) value;
    }

    @SuppressWarnings("unchecked")
    private static DataKey.NbtSerializer<Object> castSerializer(DataKey<?> key) {
        return (DataKey.NbtSerializer<Object>) key.serializer();
    }
}
