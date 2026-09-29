package com.alrex.parcool.mixin.common;

import com.alrex.parcool.common.data.DataKey;
import com.alrex.parcool.common.data.IParCoolDataHolder;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashMap;
import java.util.Map;

/**
 * Backs {@link IParCoolDataHolder} for every entity. This replaces the upstream
 * {@code abstract class EntityMixin extends AttachmentHolder}, which relied on NeoForge's patched
 * {@code Entity extends AttachmentHolder} supertype and therefore could not exist on Fabric.
 * <p>
 * The map is created lazily instead of with a field initialiser: mixin field initialisers run in the
 * mixin's own constructor and are unreliable when the target is deserialised or otherwise bypasses
 * field initialisation.
 */
@Mixin(Entity.class)
public abstract class ParCoolDataHolderMixin implements IParCoolDataHolder {

    @Unique
    private Map<DataKey<?>, Object> parcool$data;

    /**
     * The map is created on first touch, and the first {@code Parkourability} read on that entity
     * therefore allocates all 27 Action instances. That is once per entity, not per access, so it is
     * left as it is: making Parkourability itself lazy would change every accessor on this class, and
     * a null check in each of them is a worse trade than one allocation per player.
     */
    @Override
    public Map<DataKey<?>, Object> parcool$getDataMap() {
        Map<DataKey<?>, Object> map = this.parcool$data;
        if (map == null) {
            map = new HashMap<>();
            this.parcool$data = map;
        }
        return map;
    }
}
